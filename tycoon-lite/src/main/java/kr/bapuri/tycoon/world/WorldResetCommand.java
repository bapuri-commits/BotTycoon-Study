package kr.bapuri.tycoon.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.*;

/**
 * WorldResetCommand - 월드 수동 리셋 명령어 (백업 + 크래시 복구 지원)
 * 
 * 사용법:
 *   /worldreset wild          - Wild 리셋 확인 요청
 *   /worldreset wild confirm  - Wild 리셋 실행
 *   /worldreset status        - 리셋 상태 확인
 *   /worldreset restore       - 최신 백업에서 복구
 * 
 * 기능:
 * - 리셋 전 ZIP 백업 (비동기)
 * - 크래시 복구 (wild_reset_state.yml)
 * - WildSpawnManager 연동 (기반암 플랫폼 재설정)
 * - DeathChest 연동 (리셋 전 아이템 이관) - stub
 * 
 * 권한: tycoon.admin.worldreset
 */
public class WorldResetCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final WorldManager worldManager;
    private final Logger logger;
    
    // 확인 대기 (UUID -> 타임스탬프)
    private final Map<UUID, Long> pendingConfirmations = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 30_000; // 30초
    
    // 중복 리셋 방지
    private volatile boolean resetInProgress = false;
    
    // 백업 설정
    private boolean backupEnabled = true;
    private String backupFolder = "plugins/TycoonLite/backups/wild";
    private int keepBackups = 10;
    
    // 상태 파일 (크래시 복구용)
    private static final String STATE_FILE = "plugins/TycoonLite/data/wild_reset_state.yml";
    private static final DateTimeFormatter KST_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.of("Asia/Seoul"));
    
    // 연동 서비스 (setter로 주입)
    private WildSpawnManager wildSpawnManager;
    private DeathChestIntegration deathChestIntegration;
    
    public WorldResetCommand(Plugin plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.logger = Logger.getLogger("TycoonLite.WorldReset");
        
        loadConfig();
    }
    
    /**
     * config.yml에서 백업 설정 로드
     */
    public void loadConfig() {
        backupEnabled = plugin.getConfig().getBoolean("wildReset.backupEnabled", true);
        backupFolder = plugin.getConfig().getString("wildReset.backupFolder", "plugins/TycoonLite/backups/wild");
        keepBackups = plugin.getConfig().getInt("wildReset.keepBackups", 10);
        
        logger.info("[WorldReset] 백업 설정 로드 (enabled=" + backupEnabled + ", keep=" + keepBackups + ")");
    }
    
    // ========== Setter Injection ==========
    
    public void setWildSpawnManager(WildSpawnManager wildSpawnManager) {
        this.wildSpawnManager = wildSpawnManager;
    }
    
    public void setDeathChestIntegration(DeathChestIntegration deathChestIntegration) {
        this.deathChestIntegration = deathChestIntegration;
    }
    
    // ========== 크래시 복구 ==========
    
    /**
     * 서버 시작 시 호출 - 중단된 리셋 복구
     */
    public void checkAndRecoverFromCrash() {
        Path statePath = Paths.get(STATE_FILE);
        if (!Files.exists(statePath)) {
            return;
        }
        
        logger.warning("[WorldReset] 리셋 중 크래시 감지! 복구 시도...");
        
        try {
            FileConfiguration state = YamlConfiguration.loadConfiguration(statePath.toFile());
            String status = state.getString("state", "UNKNOWN");
            
            switch (status) {
                case "IN_PROGRESS", "BACKUP_DONE" -> {
                    // 백업이 있으면 복구
                    logger.info("[WorldReset] 상태: " + status + " - 백업에서 복구 시도");
                    if (backupEnabled) {
                        restoreFromLatestBackup();
                    }
                }
                case "WORLDS_DELETED" -> {
                    // 월드 재생성만 필요
                    logger.info("[WorldReset] 상태: WORLDS_DELETED - 월드 재생성");
                    String[] worlds = state.getStringList("worlds").toArray(new String[0]);
                    if (worlds.length > 0) {
                        loadWorldsSequential(worlds, 0, Bukkit.getConsoleSender());
                    }
                }
                default -> logger.warning("[WorldReset] 알 수 없는 상태: " + status);
            }
            
            clearResetState();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[WorldReset] 복구 실패", e);
        }
    }
    
    /**
     * 리셋 상태 저장 (크래시 복구용)
     */
    private void saveResetState(String state, List<String> worlds) {
        try {
            Path statePath = Paths.get(STATE_FILE);
            Files.createDirectories(statePath.getParent());
            
            FileConfiguration config = new YamlConfiguration();
            config.set("state", state);
            config.set("timestamp", System.currentTimeMillis());
            config.set("worlds", worlds);
            
            config.save(statePath.toFile());
            logger.fine("[WorldReset] 상태 저장: " + state);
        } catch (IOException e) {
            logger.warning("[WorldReset] 상태 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 리셋 상태 제거
     */
    private void clearResetState() {
        try {
            Files.deleteIfExists(Paths.get(STATE_FILE));
        } catch (IOException e) {
            logger.warning("[WorldReset] 상태 파일 삭제 실패: " + e.getMessage());
        }
    }
    
    /**
     * 만료된 확인 요청 정리 (메모리 누수 방지)
     */
    private void cleanupExpiredConfirmations() {
        long now = System.currentTimeMillis();
        pendingConfirmations.entrySet().removeIf(entry -> 
            now - entry.getValue() > CONFIRMATION_TIMEOUT_MS);
    }
    
    // ========== 명령어 처리 ==========
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tycoon.admin.worldreset")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }
        
        String subCmd = args[0].toLowerCase();
        
        switch (subCmd) {
            case "status" -> showStatus(sender);
            case "wild" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                    executeReset(sender);
                } else {
                    requestConfirmation(sender);
                }
            }
            case "restore" -> {
                if (resetInProgress) {
                    sender.sendMessage("§c리셋이 진행 중입니다.");
                } else {
                    executeRestore(sender);
                }
            }
            default -> showUsage(sender);
        }
        
        return true;
    }
    
    private void showUsage(CommandSender sender) {
        sender.sendMessage("§e=== 월드 리셋 명령어 ===");
        sender.sendMessage("§7/worldreset wild §8- Wild 리셋 요청 (확인 필요)");
        sender.sendMessage("§7/worldreset wild confirm §8- Wild 리셋 실행");
        sender.sendMessage("§7/worldreset status §8- 리셋 상태 확인");
        sender.sendMessage("§7/worldreset restore §8- 최신 백업에서 복구");
        sender.sendMessage("");
        sender.sendMessage("§c⚠ 주의: Wild 리셋 시 모든 데이터가 삭제됩니다!");
    }
    
    private void showStatus(CommandSender sender) {
        sender.sendMessage("§e=== 월드 리셋 상태 ===");
        
        // 리셋 진행 상태
        sender.sendMessage("§7리셋 진행 중: " + (resetInProgress ? "§c예" : "§a아니오"));
        
        // Wild 오버월드
        World wildWorld = worldManager.getWorld(WorldType.WILD).orElse(null);
        sender.sendMessage("§7Wild: " + (wildWorld != null ? "§a로드됨" : "§c없음"));
        
        // Wild 네더
        World wildNether = Bukkit.getWorld(worldManager.getWildNetherName());
        sender.sendMessage("§7Wild Nether: " + (wildNether != null ? "§a로드됨" : "§c없음"));
        
        // Wild 엔드
        World wildEnd = Bukkit.getWorld(worldManager.getWildEndName());
        sender.sendMessage("§7Wild End: " + (wildEnd != null ? "§a로드됨" : "§c없음"));
        
        // 각 월드 플레이어 수
        int wildPlayers = (wildWorld != null ? wildWorld.getPlayers().size() : 0)
            + (wildNether != null ? wildNether.getPlayers().size() : 0)
            + (wildEnd != null ? wildEnd.getPlayers().size() : 0);
        sender.sendMessage("§7Wild 내 플레이어: §f" + wildPlayers + "명");
        
        // 백업 정보
        sender.sendMessage("");
        sender.sendMessage("§e=== 백업 정보 ===");
        sender.sendMessage("§7백업 활성화: " + (backupEnabled ? "§a예" : "§c아니오"));
        
        try {
            Path backupPath = Paths.get(backupFolder);
            if (Files.exists(backupPath)) {
                // #9 수정: try-with-resources로 스트림 닫기
                long backupCount;
                try (var stream = Files.list(backupPath)) {
                    backupCount = stream.filter(p -> p.toString().endsWith(".zip")).count();
                }
                sender.sendMessage("§7백업 수: §f" + backupCount + "/" + keepBackups + "개");
                
                // 최신 백업
                Optional<Path> latestBackup;
                try (var stream = Files.list(backupPath)) {
                    latestBackup = stream
                        .filter(p -> p.toString().endsWith(".zip"))
                        .max(Comparator.comparingLong(p -> {
                            try { return Files.getLastModifiedTime(p).toMillis(); } 
                            catch (IOException e) { return 0; }
                        }));
                }
                
                if (latestBackup.isPresent()) {
                    sender.sendMessage("§7최신 백업: §f" + latestBackup.get().getFileName());
                }
            } else {
                sender.sendMessage("§7백업 폴더: §c없음");
            }
        } catch (IOException e) {
            sender.sendMessage("§c백업 정보 조회 실패");
        }
    }
    
    private void requestConfirmation(CommandSender sender) {
        UUID uuid = sender instanceof Player p ? p.getUniqueId() : UUID.nameUUIDFromBytes("CONSOLE".getBytes());
        
        // 만료된 확인 요청 정리 (메모리 누수 방지)
        cleanupExpiredConfirmations();
        
        pendingConfirmations.put(uuid, System.currentTimeMillis());
        
        sender.sendMessage("§c⚠ 경고: Wild 월드 리셋을 요청했습니다!");
        sender.sendMessage("§c다음 월드가 완전히 삭제됩니다:");
        sender.sendMessage("§7 - " + worldManager.getWorldName(WorldType.WILD));
        sender.sendMessage("§7 - " + worldManager.getWildNetherName());
        sender.sendMessage("§7 - " + worldManager.getWildEndName());
        sender.sendMessage("");
        if (backupEnabled) {
            sender.sendMessage("§a✓ 리셋 전 자동 백업이 생성됩니다.");
        }
        sender.sendMessage("§eWild에 있는 플레이어는 마을로 강제 이동됩니다.");
        sender.sendMessage("");
        sender.sendMessage("§a30초 내에 §f/worldreset wild confirm §a을 입력하세요.");
    }
    
    private void executeReset(CommandSender sender) {
        if (resetInProgress) {
            sender.sendMessage("§c리셋이 이미 진행 중입니다.");
            return;
        }
        
        UUID uuid = sender instanceof Player p ? p.getUniqueId() : UUID.nameUUIDFromBytes("CONSOLE".getBytes());
        
        Long requestTime = pendingConfirmations.remove(uuid);
        if (requestTime == null) {
            sender.sendMessage("§c먼저 /worldreset wild 로 확인을 요청하세요.");
            return;
        }
        
        if (System.currentTimeMillis() - requestTime > CONFIRMATION_TIMEOUT_MS) {
            sender.sendMessage("§c확인 시간이 만료되었습니다. 다시 시도해주세요.");
            return;
        }
        
        resetInProgress = true;
        
        String[] worldsToReset = {
            worldManager.getWorldName(WorldType.WILD),
            worldManager.getWildNetherName(),
            worldManager.getWildEndName()
        };
        
        // 상태 저장
        saveResetState("IN_PROGRESS", Arrays.asList(worldsToReset));
        
        Bukkit.broadcastMessage("§c§l[서버] §e야생 월드 리셋이 시작됩니다!");
        Bukkit.broadcastMessage("§7야생에 있는 플레이어는 마을로 이동됩니다.");
        
        // 2초 후 실행
        Bukkit.getScheduler().runTaskLater(plugin, () -> performReset(sender, worldsToReset), 40L);
    }
    
    private void performReset(CommandSender sender, String[] worldsToReset) {
        logger.info("[WorldReset] Wild 리셋 시작 - 요청자: " + sender.getName());
        
        // 1. 플레이어 이동
        World townWorld = worldManager.getWorld(WorldType.TOWN).orElse(null);
        if (townWorld == null) {
            sender.sendMessage("§cTown 월드를 찾을 수 없습니다. 리셋 중단.");
            resetInProgress = false;
            clearResetState();
            return;
        }
        
        int movedCount = 0;
        for (String worldName : worldsToReset) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Player player : new ArrayList<>(world.getPlayers())) {
                    player.teleport(townWorld.getSpawnLocation());
                    player.sendMessage("§e[서버] 야생 리셋으로 인해 마을로 이동되었습니다.");
                    movedCount++;
                }
            }
        }
        logger.info("[WorldReset] 플레이어 " + movedCount + "명 이동 완료");
        
        // 2. DeathChest 이관 (AngelChest 등)
        if (deathChestIntegration != null) {
            int chestCount = deathChestIntegration.expireAllInWorlds(Arrays.asList(worldsToReset));
            if (chestCount > 0) {
                logger.info("[WorldReset] " + chestCount + "개 DeathChest 처리 완료");
            }
        }
        
        // 3. 월드 폴더 경로 저장 (언로드 전)
        Map<String, File> worldFolders = new HashMap<>();
        for (String worldName : worldsToReset) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                worldFolders.put(worldName, world.getWorldFolder());
            }
        }
        
        // 4. 백업 생성 (비동기)
        if (backupEnabled && !worldFolders.isEmpty()) {
            Bukkit.broadcastMessage("§e§l[야생 리셋] §f백업 생성 중...");
            
            // 월드 저장 (data 폴더 없으면 생성 후 저장)
            for (String worldName : worldsToReset) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    try {
                        // data 폴더가 없으면 미리 생성 (raids.dat 에러 방지)
                        File dataFolder = new File(world.getWorldFolder(), "data");
                        if (!dataFolder.exists()) {
                            dataFolder.mkdirs();
                            logger.info("[WorldReset] data 폴더 생성: " + worldName);
                        }
                        world.save();
                        logger.info("[WorldReset] 월드 저장 완료: " + worldName);
                    } catch (Exception e) {
                        logger.warning("[WorldReset] 월드 저장 중 오류 (백업은 계속): " + worldName + " - " + e.getMessage());
                    }
                }
            }
            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean backupSuccess = createBackup(worldFolders);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (backupSuccess) {
                        saveResetState("BACKUP_DONE", Arrays.asList(worldsToReset));
                        continueResetAfterBackup(sender, worldsToReset, worldFolders);
                    } else {
                        sender.sendMessage("§c백업 실패! 리셋을 중단합니다.");
                        Bukkit.broadcastMessage("§c§l[야생 리셋] §e백업 실패로 리셋이 취소되었습니다.");
                        resetInProgress = false;
                        clearResetState();
                    }
                });
            });
        } else {
            continueResetAfterBackup(sender, worldsToReset, worldFolders);
        }
    }
    
    private void continueResetAfterBackup(CommandSender sender, String[] worldsToReset, Map<String, File> worldFolders) {
        // 5. 월드 언로드
        for (String worldName : worldsToReset) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                boolean unloaded = Bukkit.unloadWorld(world, false);
                if (unloaded) {
                    logger.info("[WorldReset] 월드 언로드 완료: " + worldName);
                } else {
                    logger.warning("[WorldReset] 월드 언로드 실패: " + worldName);
                }
            }
        }
        
        saveResetState("WORLDS_DELETED", Arrays.asList(worldsToReset));
        
        // 6. 월드 폴더 삭제 (비동기)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Map.Entry<String, File> entry : worldFolders.entrySet()) {
                File worldFolder = entry.getValue();
                if (worldFolder != null && worldFolder.exists()) {
                    deleteDirectory(worldFolder);
                    logger.info("[WorldReset] 월드 폴더 삭제 완료: " + entry.getKey());
                }
            }
            
            // 7. 메인 스레드로 돌아가서 월드 재생성
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcastMessage("§e§l[야생 리셋] §f월드 재생성 중... (약 15초 소요)");
                loadWorldsSequential(worldsToReset, 0, sender);
            });
        });
    }
    
    // ========== 백업 시스템 ==========
    
    /**
     * ZIP 백업 생성
     * #8 수정: 임시 파일에 먼저 쓰고 완료 후 rename (불완전한 백업 방지)
     * #10 수정: 상세 로그 및 백업 검증 추가
     */
    private boolean createBackup(Map<String, File> worldFolders) {
        Path tempFile = null;
        int totalFiles = 0;
        int includedWorlds = 0;
        
        try {
            Path backupPath = Paths.get(backupFolder);
            Files.createDirectories(backupPath);
            
            String timestamp = KST_FORMATTER.format(Instant.now());
            Path backupFile = backupPath.resolve("wild_backup_" + timestamp + ".zip");
            tempFile = backupPath.resolve("wild_backup_" + timestamp + ".zip.tmp");
            
            logger.info("[WorldReset] 백업 시작 - 대상 월드: " + worldFolders.keySet());
            
            // 임시 파일에 먼저 백업
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile.toFile()))) {
                for (Map.Entry<String, File> entry : worldFolders.entrySet()) {
                    String worldName = entry.getKey();
                    File worldFolder = entry.getValue();
                    
                    if (worldFolder.exists()) {
                        int fileCount = zipDirectory(worldFolder.toPath(), worldName, zos);
                        totalFiles += fileCount;
                        includedWorlds++;
                        logger.info("[WorldReset] 백업 추가: " + worldName + " (" + fileCount + "개 파일)");
                    } else {
                        logger.warning("[WorldReset] 월드 폴더 없음 (스킵): " + worldName + " -> " + worldFolder.getAbsolutePath());
                    }
                }
            }
            
            // 백업 검증: 최소 크기 및 월드 수 확인
            long backupSize = Files.size(tempFile);
            if (includedWorlds == 0) {
                logger.severe("[WorldReset] 백업 실패: 포함된 월드가 없습니다!");
                Files.deleteIfExists(tempFile);
                return false;
            }
            if (backupSize < 1024) { // 최소 1KB
                logger.severe("[WorldReset] 백업 실패: 파일 크기가 너무 작습니다 (" + backupSize + " bytes)");
                Files.deleteIfExists(tempFile);
                return false;
            }
            
            // 완료 후 실제 파일로 rename (원자적 연산)
            Files.move(tempFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            
            String sizeStr = backupSize > 1024 * 1024 
                ? String.format("%.2f MB", backupSize / (1024.0 * 1024.0))
                : String.format("%.2f KB", backupSize / 1024.0);
            logger.info("[WorldReset] 백업 완료: " + backupFile.getFileName() + 
                " (월드 " + includedWorlds + "개, 파일 " + totalFiles + "개, " + sizeStr + ")");
            
            // 오래된 백업 정리
            cleanupOldBackups();
            
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[WorldReset] 백업 생성 실패", e);
            
            // 실패 시 임시 파일 삭제
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
            
            return false;
        }
    }
    
    /**
     * 디렉토리를 ZIP에 추가
     * @return 백업된 파일 수
     */
    private int zipDirectory(Path sourceDir, String baseName, ZipOutputStream zos) throws IOException {
        int[] fileCount = {0}; // lambda에서 사용하기 위해 배열 사용
        
        Files.walk(sourceDir)
            .filter(path -> !Files.isDirectory(path))
            .filter(path -> !path.getFileName().toString().equals("session.lock"))
            .forEach(path -> {
                try {
                    String entryName = baseName + "/" + sourceDir.relativize(path).toString().replace("\\", "/");
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                    fileCount[0]++;
                } catch (IOException e) {
                    logger.warning("[WorldReset] 백업 중 파일 스킵: " + path);
                }
            });
        
        return fileCount[0];
    }
    
    /**
     * 오래된 백업 정리
     */
    private void cleanupOldBackups() {
        try {
            Path backupPath = Paths.get(backupFolder);
            if (!Files.exists(backupPath)) return;
            
            List<Path> backups = new ArrayList<>();
            try (var stream = Files.list(backupPath)) {
                stream.filter(p -> p.toString().endsWith(".zip"))
                      .forEach(backups::add);
            }
            
            // 수정 시간으로 정렬 (오래된 것 먼저)
            backups.sort(Comparator.comparingLong(p -> {
                try { return Files.getLastModifiedTime(p).toMillis(); } 
                catch (IOException e) { return 0; }
            }));
            
            // 초과분 삭제
            while (backups.size() > keepBackups) {
                Path oldBackup = backups.remove(0);
                Files.deleteIfExists(oldBackup);
                logger.info("[WorldReset] 오래된 백업 삭제: " + oldBackup.getFileName());
            }
        } catch (IOException e) {
            logger.warning("[WorldReset] 백업 정리 중 오류: " + e.getMessage());
        }
    }
    
    /**
     * 복구 명령어 실행 (월드 언로드 -> 복구 -> 재로드)
     */
    private void executeRestore(CommandSender sender) {
        sender.sendMessage("§e최신 백업에서 복구를 시작합니다...");
        
        String[] worldsToRestore = {
            worldManager.getWorldName(WorldType.WILD),
            worldManager.getWildNetherName(),
            worldManager.getWildEndName()
        };
        
        // 1. 플레이어 이동
        World townWorld = worldManager.getWorld(WorldType.TOWN).orElse(null);
        if (townWorld == null) {
            sender.sendMessage("§cTown 월드를 찾을 수 없습니다.");
            return;
        }
        
        for (String worldName : worldsToRestore) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Player player : new ArrayList<>(world.getPlayers())) {
                    player.teleport(townWorld.getSpawnLocation());
                    player.sendMessage("§e[서버] 백업 복구로 인해 마을로 이동되었습니다.");
                }
            }
        }
        
        // 2. 월드 언로드
        Map<String, File> worldFolders = new HashMap<>();
        for (String worldName : worldsToRestore) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                worldFolders.put(worldName, world.getWorldFolder());
                Bukkit.unloadWorld(world, false);
                logger.info("[WorldReset] 복구를 위해 월드 언로드: " + worldName);
            }
        }
        
        // 3. 비동기로 복구
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = restoreFromLatestBackup();
            
            // 4. 메인 스레드에서 월드 재로드
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    sender.sendMessage("§a백업 파일 복구 완료. 월드 재로드 중...");
                    loadWorldsSequential(worldsToRestore, 0, sender);
                } else {
                    sender.sendMessage("§c백업 복구 실패. 로그를 확인하세요.");
                }
            });
        });
    }
    
    /**
     * 최신 백업에서 복구 (비동기에서 호출)
     * @return 복구 성공 여부
     */
    private boolean restoreFromLatestBackup() {
        try {
            Path backupPath = Paths.get(backupFolder);
            if (!Files.exists(backupPath)) {
                logger.warning("[WorldReset] 백업 폴더 없음. 복구 불가.");
                return false;
            }
            
            // 최신 백업 찾기
            Optional<Path> latestBackup;
            try (var stream = Files.list(backupPath)) {
                latestBackup = stream
                    .filter(p -> p.toString().endsWith(".zip"))
                    .max(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); } 
                        catch (IOException e) { return 0; }
                    }));
            }
            
            if (latestBackup.isEmpty()) {
                logger.warning("[WorldReset] 복구할 백업 없음.");
                return false;
            }
            
            logger.info("[WorldReset] 백업에서 복구: " + latestBackup.get().getFileName());
            
            // ZIP 압축 해제
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(latestBackup.get().toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path destPath = Paths.get(Bukkit.getWorldContainer().getPath(), entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(destPath);
                    } else {
                        Files.createDirectories(destPath.getParent());
                        Files.copy(zis, destPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            
            logger.info("[WorldReset] 백업 파일 복구 완료!");
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[WorldReset] 백업 복구 실패", e);
            return false;
        }
    }
    
    // ========== 월드 로드/삭제 ==========
    
    /**
     * 월드 순차 로드 (타임아웃 방지)
     */
    private void loadWorldsSequential(String[] worlds, int index, CommandSender sender) {
        if (index >= worlds.length) {
            finishReset(sender);
            return;
        }
        
        String worldName = worlds[index];
        World.Environment env = determineEnvironment(worldName);
        
        logger.info("[WorldReset] 월드 생성 중 (" + (index + 1) + "/" + worlds.length + "): " + worldName);
        
        org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(worldName);
        creator.environment(env);
        creator.generateStructures(true);  // 엔더드래곤 등 구조물 생성 보장
        creator.keepSpawnLoaded(net.kyori.adventure.util.TriState.FALSE);  // 스폰 청크 강제 로드 방지 (리셋 시 지연 감소)
        World world = Bukkit.createWorld(creator);
        
        if (world != null) {
            logger.info("[WorldReset] 월드 생성 완료: " + worldName + " (env=" + env + ")");
        } else {
            logger.warning("[WorldReset] 월드 생성 실패: " + worldName);
        }
        
        // 100틱(5초) 후 다음 월드 로드
        Bukkit.getScheduler().runTaskLater(plugin, 
            () -> loadWorldsSequential(worlds, index + 1, sender), 100L);
    }
    
    private World.Environment determineEnvironment(String worldName) {
        if (worldName.endsWith("_nether")) {
            return World.Environment.NETHER;
        } else if (worldName.endsWith("_the_end")) {
            return World.Environment.THE_END;
        }
        return World.Environment.NORMAL;
    }
    
    private void finishReset(CommandSender sender) {
        // 설정 재적용
        worldManager.reload();
        worldManager.applyWorldSettings();
        
        // WildSpawnManager: 기반암 플랫폼 및 NPC 즉시 설정
        if (wildSpawnManager != null) {
            String wildName = worldManager.getWorldName(WorldType.WILD);
            
            // 기존 데이터 초기화
            wildSpawnManager.resetWorld(wildName);
            
            // 새 월드에 즉시 스폰 포인트 설정
            World wildWorld = Bukkit.getWorld(wildName);
            if (wildWorld != null) {
                Location spawnLoc = wildWorld.getSpawnLocation();
                wildSpawnManager.setupWildSpawn(wildName, spawnLoc);
                logger.info("[WorldReset] WildSpawnManager 스폰 포인트 설정 완료");
            }
        }
        
        // 완료
        Bukkit.broadcastMessage("§a§l[서버] §e야생 월드 리셋이 완료되었습니다!");
        sender.sendMessage("§a[WorldReset] Wild 리셋 완료!");
        logger.info("[WorldReset] Wild 리셋 완료");
        
        clearResetState();
        resetInProgress = false;
    }
    
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
    
    // ========== Tab Complete ==========
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tycoon.admin.worldreset")) {
            return List.of();
        }
        
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String opt : List.of("wild", "status", "restore")) {
                if (opt.startsWith(partial)) {
                    completions.add(opt);
                }
            }
            return completions;
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("wild")) {
            String partial = args[1].toLowerCase();
            if ("confirm".startsWith(partial)) {
                return List.of("confirm");
            }
        }
        
        return List.of();
    }
    
    // ========== Getter ==========
    
    public boolean isResetInProgress() {
        return resetInProgress;
    }
    
    /**
     * 자동 리셋용 직접 실행 (확인 과정 생략)
     * WorldResetScheduler에서 호출
     */
    public void executeDirectReset() {
        if (resetInProgress) {
            logger.warning("[WorldReset] 리셋이 이미 진행 중입니다.");
            return;
        }
        
        resetInProgress = true;
        
        String[] worldsToReset = {
            worldManager.getWorldName(WorldType.WILD),
            worldManager.getWildNetherName(),
            worldManager.getWildEndName()
        };
        
        // 상태 저장
        saveResetState("IN_PROGRESS", Arrays.asList(worldsToReset));
        
        Bukkit.broadcastMessage("§c§l[서버] §e야생 월드 자동 리셋이 시작됩니다!");
        Bukkit.broadcastMessage("§7야생에 있는 플레이어는 마을로 이동됩니다.");
        
        // 즉시 실행
        performReset(Bukkit.getConsoleSender(), worldsToReset);
    }
    
    /**
     * DeathChest 연동 인터페이스 (AngelChest 등)
     */
    public interface DeathChestIntegration {
        /**
         * 지정된 월드의 모든 DeathChest를 만료/이관 처리
         * @return 처리된 체스트 수
         */
        int expireAllInWorlds(List<String> worldNames);
    }
}
