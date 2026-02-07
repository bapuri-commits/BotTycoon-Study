package kr.bapuri.tycoon.player;

import kr.bapuri.tycoon.admin.AdminService;
import kr.bapuri.tycoon.job.JobType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PlayerDataManager - 플레이어 데이터 로드/저장 관리
 * 
 * [Stage-3] 주요 기능:
 * - 스키마 버전 관리 및 마이그레이션
 * - 원자적 저장 (temp file → rename)
 * - 값 검증 및 클램핑
 * - 온라인/오프라인 플레이어 지원
 * - 비동기 저장 지원
 */
public class PlayerDataManager {

    private final Plugin plugin;
    private final Logger logger;
    private final Map<UUID, PlayerTycoonData> dataMap = new ConcurrentHashMap<>();
    private final File dataFolder;
    
    // [슈퍼관리자] 새 관리자 초기 자금용 (순환 참조 방지를 위해 setter 주입)
    private AdminService adminService;
    private static final long ADMIN_START_MONEY = 999_999_999L;
    private static final long ADMIN_START_BOTTCOIN = 999_999L;
    
    // [Phase 2] 자동 저장 스케줄러
    private org.bukkit.scheduler.BukkitTask autoSaveTask;
    private int autoSaveIntervalMinutes;
    private boolean autoSaveEnabled;
    
    // [Phase 2] 백업 매니저
    private BackupManager backupManager;

    public PlayerDataManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        
        // [Phase 2] config.yml에서 설정 읽기
        loadConfig();
        
        // [Stage-3] 서버 시작 시 .tmp 파일 복구
        recoverOrphanedTempFiles();
        
        // [Phase 2] 백업 매니저 초기화 (config 전달)
        this.backupManager = new BackupManager(plugin);
    }
    
    /**
     * [Phase 2] config.yml에서 설정 읽기
     */
    private void loadConfig() {
        org.bukkit.configuration.file.FileConfiguration config = 
            ((org.bukkit.plugin.java.JavaPlugin) plugin).getConfig();
        
        // 자동 저장 설정
        this.autoSaveEnabled = config.getBoolean("playerdata.auto-save.enabled", true);
        this.autoSaveIntervalMinutes = config.getInt("playerdata.auto-save.interval-minutes", 5);
        
        logger.info("[PlayerDataManager] 설정 로드: autoSave=" + autoSaveEnabled + 
                    ", interval=" + autoSaveIntervalMinutes + "분");
    }
    
    /**
     * [슈퍼관리자] AdminService 설정 (순환 참조 방지)
     */
    public void setAdminService(AdminService adminService) {
        this.adminService = adminService;
    }
    
    /**
     * [Stage-3] 서버 시작 시 고아 .tmp / .bak 파일 복구
     * 
     * 시나리오:
     * - 서버가 저장 중간에 크래시됨
     * - .yml.tmp 파일은 존재하지만 .yml 파일이 없거나 손상됨
     * - .yml.bak 파일만 존재 (저장 실패 후 원본도 손실)
     * 
     * 정책:
     * - .tmp만 존재 (원본 없음): .tmp를 .yml로 승격
     * - .bak만 존재 (원본 없음): .bak를 .yml로 승격
     * - 둘 다 존재: .tmp/.bak 삭제 (원본 우선)
     * - .yml만 존재: 정상 상태
     */
    private void recoverOrphanedTempFiles() {
        int recovered = 0;
        int cleaned = 0;
        
        // .tmp 파일 처리
        File[] tempFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml.tmp"));
        if (tempFiles != null) {
            for (File tempFile : tempFiles) {
                String baseName = tempFile.getName().replace(".yml.tmp", "");
                File originalFile = new File(dataFolder, baseName + ".yml");
                
                try {
                    if (!originalFile.exists()) {
                        // 원본 없음 → .tmp를 원본으로 승격
                        Path tempPath = tempFile.toPath();
                        Path targetPath = originalFile.toPath();
                        
                        Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        logger.warning("[PlayerDataManager] 복구됨: " + baseName + ".yml (from .tmp)");
                        recovered++;
                    } else {
                        // 둘 다 존재 → .tmp 삭제 (원본 우선)
                        if (tempFile.delete()) {
                            cleaned++;
                        }
                    }
                } catch (IOException e) {
                    logger.warning("[PlayerDataManager] .tmp 복구 실패: " + tempFile.getName() + " - " + e.getMessage());
                }
            }
        }
        
        // [FIX] .bak 파일 처리 (원본이 없을 때만 복구)
        File[] bakFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml.bak"));
        if (bakFiles != null) {
            for (File bakFile : bakFiles) {
                String baseName = bakFile.getName().replace(".yml.bak", "");
                File originalFile = new File(dataFolder, baseName + ".yml");
                
                try {
                    if (!originalFile.exists()) {
                        // 원본 없음 → .bak를 원본으로 승격
                        Path bakPath = bakFile.toPath();
                        Path targetPath = originalFile.toPath();
                        
                        Files.copy(bakPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        logger.warning("[PlayerDataManager] 복구됨: " + baseName + ".yml (from .bak)");
                        recovered++;
                    }
                    // .bak는 삭제하지 않음 (다음 저장 시 새로 만들어짐)
                } catch (IOException e) {
                    logger.warning("[PlayerDataManager] .bak 복구 실패: " + bakFile.getName() + " - " + e.getMessage());
                }
            }
        }
        
        if (recovered > 0 || cleaned > 0) {
            logger.info("[PlayerDataManager] 시작 정리: 복구=" + recovered + ", 정리=" + cleaned);
        }
    }
    
    // ========== [Stage-3] 마이그레이션 ==========
    
    /**
     * v1 → v2 마이그레이션
     * - 기존 키를 새 구조로 이동
     * - 누락된 필드에 기본값 설정
     */
    private void migrateV1toV2(YamlConfiguration c) {
        // 경제: money → economy.bd
        if (c.contains("money") && !c.contains("economy.bd")) {
            c.set("economy.bd", c.getLong("money", 0));
        }
        if (c.contains("bottCoin") && !c.contains("economy.bottCoin")) {
            c.set("economy.bottCoin", c.getLong("bottCoin", 0));
        }
        
        // 도감: unlockedCodex → codex.unlocked
        if (c.contains("unlockedCodex") && !c.contains("codex.unlocked")) {
            c.set("codex.unlocked", c.getStringList("unlockedCodex"));
        }
        
        // 플롯: ownedPlots → plots.owned
        if (c.contains("ownedPlots") && !c.contains("plots.owned")) {
            c.set("plots.owned", c.getStringList("ownedPlots"));
        }
        
        // 기본값 설정 (신규 필드)
        if (!c.contains("economy.lifetimeEarned")) c.set("economy.lifetimeEarned", 0L);
        if (!c.contains("economy.lifetimeSpent")) c.set("economy.lifetimeSpent", 0L);
        if (!c.contains("economy.lastTxnId")) c.set("economy.lastTxnId", "");
        // [DROP] hunter, duel, dungeon 마이그레이션 제거됨 (Phase 1.5)
        
        logger.info("[PlayerDataManager] v1 → v2 마이그레이션 완료: " + c.getString("uuid"));
    }
    
    /**
     * 스키마 마이그레이션 실행
     */
    private boolean runMigrations(YamlConfiguration c) {
        int version = c.getInt("schemaVersion", 1);
        boolean migrated = false;
        
        if (version < 2) {
            migrateV1toV2(c);
            migrated = true;
        }
        
        if (migrated) {
            c.set("schemaVersion", PlayerTycoonData.CURRENT_SCHEMA_VERSION);
        }
        
        return migrated;
    }

    // ========== 온라인 플레이어 전용 ==========

    /**
     * 온라인 플레이어 데이터 가져오기 (캐시됨)
     */
    public PlayerTycoonData get(Player player) {
        return dataMap.computeIfAbsent(player.getUniqueId(), this::loadOrCreate);
    }

    /**
     * UUID로 데이터 가져오기
     * - 온라인이면 캐시에서
     * - 오프라인이면 파일에서 로드 (주의: 메모리에 남음)
     */
    public PlayerTycoonData get(UUID uuid) {
        return dataMap.computeIfAbsent(uuid, this::loadOrCreate);
    }

    /**
     * 온라인 플레이어인지 확인 후 데이터 가져오기
     * 오프라인이면 null 반환
     */
    public PlayerTycoonData getIfOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return null;
        }
        return get(uuid);
    }

    /**
     * 플레이어가 온라인인지 확인
     */
    public boolean isOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    /**
     * 데이터가 캐시에 있는지 확인
     */
    public boolean isLoaded(UUID uuid) {
        return dataMap.containsKey(uuid);
    }

    // ========== 오프라인 플레이어 전용 ==========

    /**
     * 오프라인 플레이어 데이터 로드 (일시적)
     * 작업 후 반드시 saveAndUnload() 호출 필요
     */
    public PlayerTycoonData loadOffline(UUID uuid) {
        // 이미 온라인이면 캐시에서 반환
        if (isOnline(uuid)) {
            return get(uuid);
        }
        // 오프라인이면 파일에서 로드
        return loadOrCreate(uuid);
    }

    /**
     * 오프라인 플레이어 데이터 저장 후 메모리에서 제거
     */
    public void saveAndUnload(UUID uuid) {
        // 온라인 플레이어는 언로드하지 않음
        if (isOnline(uuid)) {
            return;
        }
        
        PlayerTycoonData data = dataMap.get(uuid);
        if (data != null) {
            saveSync(uuid, data);
            dataMap.remove(uuid);
        }
    }

    // ========== 라이프사이클 ==========

    /**
     * 플레이어 접속 시 호출
     */
    public void onJoin(Player player) {
        get(player);
    }

    /**
     * 플레이어 퇴장 시 호출
     */
    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerTycoonData data = dataMap.get(uuid);
        if (data != null) {
            saveSync(uuid, data);
            dataMap.remove(uuid);
        }
    }

    /**
     * 서버 종료 시 모든 데이터 저장
     */
    public void saveAll() {
        for (Map.Entry<UUID, PlayerTycoonData> entry : dataMap.entrySet()) {
            saveSync(entry.getKey(), entry.getValue());
        }
        plugin.getLogger().info("[PlayerDataManager] 모든 플레이어 데이터 저장 완료: " + dataMap.size() + "명");
    }
    
    // ========== [Phase 2] 자동 저장 ==========
    
    /**
     * 주기적 자동 저장 시작
     * 변경된(dirty) 데이터만 저장하여 성능 최적화
     */
    public void startAutoSave() {
        // [Phase 2] config에서 비활성화된 경우 스킵
        if (!autoSaveEnabled) {
            logger.info("[AutoSave] 자동 저장 비활성화됨 (config)");
            return;
        }
        
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        
        long intervalTicks = autoSaveIntervalMinutes * 60 * 20L;
        
        autoSaveTask = org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int count = 0;
            for (Map.Entry<UUID, PlayerTycoonData> entry : dataMap.entrySet()) {
                PlayerTycoonData data = entry.getValue();
                if (data.isDirty()) {
                    saveAsync(entry.getKey());  // 기존 메서드 사용
                    data.setDirty(false);
                    count++;
                }
            }
            if (count > 0) {
                logger.info("[AutoSave] " + count + "명 데이터 저장 완료");
            }
        }, intervalTicks, intervalTicks);
        
        logger.info("[AutoSave] 자동 저장 시작됨 (간격: " + autoSaveIntervalMinutes + "분)");
    }
    
    /**
     * 주기적 자동 저장 중지
     */
    public void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
            logger.info("[AutoSave] 자동 저장 중지됨");
        }
    }
    
    /**
     * 자동 저장 간격 설정
     */
    public void setAutoSaveInterval(int minutes) {
        this.autoSaveIntervalMinutes = Math.max(1, minutes);
        // 이미 실행 중이면 재시작
        if (autoSaveTask != null) {
            startAutoSave();
        }
    }
    
    /**
     * 스냅샷 백업 스케줄러 시작
     */
    public void startSnapshotScheduler() {
        backupManager.startSnapshotScheduler(this);
    }
    
    /**
     * 스냅샷 백업 스케줄러 중지
     */
    public void stopSnapshotScheduler() {
        backupManager.stopSnapshotScheduler();
    }
    
    /**
     * 온라인 플레이어 UUID 목록 반환
     */
    public Set<UUID> getOnlinePlayerUUIDs() {
        return new HashSet<>(dataMap.keySet());
    }
    
    /**
     * BackupManager 반환
     */
    public BackupManager getBackupManager() {
        return backupManager;
    }

    // ========== 저장 메서드 ==========

    /**
     * 동기 저장 (UUID로)
     */
    public void save(UUID uuid) {
        PlayerTycoonData data = dataMap.get(uuid);
        if (data != null) {
            saveSync(uuid, data);
        }
    }

    /**
     * [Stage-3] 비동기 저장 (원자적 저장 사용)
     */
    public void saveAsync(UUID uuid) {
        PlayerTycoonData data = dataMap.get(uuid);
        if (data == null) return;

        // [Stage-3] 모든 데이터 복사본 생성 (스레드 안전)
        final String playerName = data.getPlayerName();
        final long money = data.getMoney();
        final long bottCoin = data.getBottCoin();
        final long lifetimeEarned = data.getLifetimeEarned();
        final long lifetimeSpent = data.getLifetimeSpent();
        // [5.2 Fix] recentTxnIds 사용
        final List<String> recentTxnIds = data.getRecentTxnIds();
        final Set<String> codex = new HashSet<>(data.getUnlockedCodex());
        final Map<String, Long> cooldowns = new HashMap<>(data.getCooldowns());
        final Map<String, Integer> ticketConsumed = new HashMap<>(data.getTicketConsumed());
        
        // 직업 데이터 복사
        final String tier1JobId = data.getTier1Job() != null ? data.getTier1Job().getId() : null;
        final int tier1JobLevel = data.getTier1JobLevel();
        final long tier1JobExp = data.getTier1JobExp();
        final String tier2JobId = data.getTier2Job() != null ? data.getTier2Job().getId() : null;
        final int tier2JobLevel = data.getTier2JobLevel();
        final long tier2JobExp = data.getTier2JobExp();
        
        // [Phase 4.B] 직업 통계 복사
        final long totalMined = data.getTotalMined();
        final long totalHarvested = data.getTotalHarvested();
        final long totalFished = data.getTotalFished();
        final long totalSales = data.getTotalSales();
        
        // 플롯 데이터 복사
        final Set<String> ownedPlots = new HashSet<>(data.getOwnedPlots());
        
        // 야생 데이터 복사
        final Location lastDeathLoc = data.getLastDeathLocation();
        final String lastDeathWorld = lastDeathLoc != null && lastDeathLoc.getWorld() != null 
            ? lastDeathLoc.getWorld().getName() : null;
        final double lastDeathX = lastDeathLoc != null ? lastDeathLoc.getX() : 0;
        final double lastDeathY = lastDeathLoc != null ? lastDeathLoc.getY() : 0;
        final double lastDeathZ = lastDeathLoc != null ? lastDeathLoc.getZ() : 0;
        
        // [Phase 8] 텔레포트 위치 복사
        final Location lastTeleportLoc = data.getLastTeleportLocation();
        final String lastTeleportWorld = lastTeleportLoc != null && lastTeleportLoc.getWorld() != null 
            ? lastTeleportLoc.getWorld().getName() : null;
        final double lastTeleportX = lastTeleportLoc != null ? lastTeleportLoc.getX() : 0;
        final double lastTeleportY = lastTeleportLoc != null ? lastTeleportLoc.getY() : 0;
        final double lastTeleportZ = lastTeleportLoc != null ? lastTeleportLoc.getZ() : 0;
        
        // [DROP] 헌터/듀얼/채무/던전 데이터 복사 제거됨 (Phase 1.5)
        
        // [Stage-5] 인벤토리 보호 상태 복사 (스레드 안전)
        final boolean universalInventorySaveActive = data.isUniversalInventorySaveActive();
        final String pendingCoreItemAction = data.getPendingCoreItemAction();
        final String pendingCoreItemId = data.getPendingCoreItemId();
        final long pendingCoreItemTime = data.getPendingCoreItemTime();
        
        // [Anti-Exploit] 주민 거래 횟수 복사
        final int villagerTradeCount = data.getVillagerTradeCount();
        
        // [세금 시스템] 세금 필드 복사
        final long dailyIncome = data.getDailyIncome();
        final long lastDailyReset = data.getLastDailyReset();
        final long lastOnlineTime = data.getLastOnlineTime();
        final long lifetimeEarnedSnapshot = data.getLifetimeEarnedSnapshot();
        
        // [v2.7] 개인 설정 복사
        final boolean showEffectMessages = data.isShowEffectMessages();
        
        // [DROP] 카지노 데이터 복사 제거됨 (Phase 1.5)

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Path filePath = dataFolder.toPath().resolve(uuid.toString() + ".yml");
            Path tempPath = dataFolder.toPath().resolve(uuid.toString() + ".yml.tmp");
            
            YamlConfiguration c = new YamlConfiguration();

            // [Stage-3] 스키마 버전
            c.set("schemaVersion", PlayerTycoonData.CURRENT_SCHEMA_VERSION);
            c.set("uuid", uuid.toString());
            c.set("playerName", playerName);
            
            // 경제 데이터 (v2)
            c.set("economy.bd", money);
            c.set("economy.bottCoin", bottCoin);
            c.set("economy.lifetimeEarned", lifetimeEarned);
            c.set("economy.lifetimeSpent", lifetimeSpent);
            // [5.2 Fix] recentTxnIds 리스트로 저장 (하위 호환을 위해 lastTxnId도 유지)
            c.set("economy.recentTxnIds", recentTxnIds);
            c.set("economy.lastTxnId", recentTxnIds.isEmpty() ? "" : recentTxnIds.get(recentTxnIds.size() - 1));
            
            // 도감 데이터 (v2)
            c.set("codex.unlocked", new ArrayList<>(codex));
            
            // 쿨다운 저장
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
                if (entry.getValue() > now) {
                    c.set("cooldowns." + entry.getKey(), entry.getValue());
                }
            }
            
            // 티켓 소비 통계 저장
            for (Map.Entry<String, Integer> entry : ticketConsumed.entrySet()) {
                if (entry.getValue() > 0) {
                    c.set("tickets.consumed." + entry.getKey(), entry.getValue());
                }
            }
            
            // 직업 데이터 저장
            c.set("jobs.tier1.id", tier1JobId);
            c.set("jobs.tier1.level", tier1JobLevel);
            c.set("jobs.tier1.exp", tier1JobExp);
            c.set("jobs.tier2.id", tier2JobId);
            c.set("jobs.tier2.level", tier2JobLevel);
            c.set("jobs.tier2.exp", tier2JobExp);
            
            // [Phase 4.B] 직업 통계 저장
            c.set("jobs.stats.totalMined", totalMined);
            c.set("jobs.stats.totalHarvested", totalHarvested);
            c.set("jobs.stats.totalFished", totalFished);
            c.set("jobs.stats.totalSales", totalSales);
            
            // [Phase 4.D] 직업별 판매액 저장
            c.set("jobs.stats.minerSales", data.getTotalMinerSales());
            c.set("jobs.stats.farmerSales", data.getTotalFarmerSales());
            c.set("jobs.stats.fisherSales", data.getTotalFisherSales());
            
            // 플롯 데이터 저장 (v2)
            c.set("plots.owned", new ArrayList<>(ownedPlots));
            
            // 야생 데이터 저장
            if (lastDeathWorld != null) {
                c.set("wild.lastDeath.world", lastDeathWorld);
                c.set("wild.lastDeath.x", lastDeathX);
                c.set("wild.lastDeath.y", lastDeathY);
                c.set("wild.lastDeath.z", lastDeathZ);
            }
            
            // [Phase 8] 텔레포트 위치 저장
            if (lastTeleportWorld != null) {
                c.set("wild.lastTeleport.world", lastTeleportWorld);
                c.set("wild.lastTeleport.x", lastTeleportX);
                c.set("wild.lastTeleport.y", lastTeleportY);
                c.set("wild.lastTeleport.z", lastTeleportZ);
            }
            
            // [DROP] 헌터/듀얼/채무/던전/카지노 저장 제거됨 (Phase 1.5)
            
            // [Stage-5] 인벤토리 보호 상태 저장 (복사된 값 사용 - 스레드 안전)
            c.set("inventorySave.universalActive", universalInventorySaveActive);
            c.set("inventorySave.pendingAction", pendingCoreItemAction);
            c.set("inventorySave.pendingItemId", pendingCoreItemId);
            c.set("inventorySave.pendingTime", pendingCoreItemTime);
            
            // [Anti-Exploit] 주민 거래 횟수 저장
            c.set("antiExploit.villagerTradeCount", villagerTradeCount);
            
            // [세금 시스템] 세금 필드 저장
            c.set("tax.dailyIncome", dailyIncome);
            c.set("tax.lastDailyReset", lastDailyReset);
            c.set("tax.lastOnlineTime", lastOnlineTime);
            c.set("tax.lifetimeEarnedSnapshot", lifetimeEarnedSnapshot);
            // [v2] 3시간 간격 세금
            c.set("tax.intervalIncome", data.getIntervalIncome());
            c.set("tax.lastIntervalReset", data.getLastIntervalReset());
            
            // [v2.7] 개인 설정 저장
            c.set("settings.showEffectMessages", showEffectMessages);

            try {
                // [Stage-3] 원자적 저장
                c.save(tempPath.toFile());
                Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                logger.log(Level.WARNING, "[PlayerDataManager] 비동기 저장 실패: " + uuid, e);
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
            }
        });
    }

    /**
     * 수동 언로드 (주의: 온라인 플레이어에게 사용하면 데이터 손실!)
     */
    public void unload(UUID uuid) {
        if (!isOnline(uuid)) {
            dataMap.remove(uuid);
        }
    }

    // ========== 내부 메서드 ==========
    
    /**
     * [Phase 2] 백업에서 복구 시도, 실패 시 새 데이터 생성
     * 
     * 주의: 이 메서드는 loadOrCreate에서만 호출되어야 함 (무한 루프 방지)
     */
    private PlayerTycoonData recoverFromBackupOrCreate(UUID uuid) {
        // 스냅샷에서 복구 시도
        List<String> snapshots = backupManager.listSnapshots(uuid);
        
        for (String timestamp : snapshots) {
            try {
                Path snapshotPath = plugin.getDataFolder().toPath()
                    .resolve("backups").resolve("playerdata")
                    .resolve(uuid.toString()).resolve(timestamp + ".yml");
                
                YamlConfiguration c = YamlConfiguration.loadConfiguration(snapshotPath.toFile());
                
                if (!c.getKeys(false).isEmpty()) {
                    // 유효한 스냅샷 발견 - 복원
                    logger.info("[PlayerDataManager] 스냅샷에서 복구: " + uuid + " <- " + timestamp);
                    
                    // 원본 파일로 복사
                    Path originalPath = dataFolder.toPath().resolve(uuid.toString() + ".yml");
                    Files.copy(snapshotPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    // 전체 데이터 로드 (loadOrCreate의 로드 부분 재사용)
                    return loadFullDataFromConfig(uuid, c);
                }
            } catch (Exception e) {
                logger.warning("[PlayerDataManager] 스냅샷 복구 실패 (" + timestamp + "): " + e.getMessage());
            }
        }
        
        // 모든 스냅샷 복구 실패 - 새 데이터 생성
        logger.warning("[PlayerDataManager] 모든 복구 실패, 새 데이터 생성: " + uuid);
        PlayerTycoonData newData = createNewPlayerData(uuid);
        saveSync(uuid, newData);
        return newData;
    }
    
    /**
     * [Phase 2] 새 플레이어 데이터 생성
     */
    private PlayerTycoonData createNewPlayerData(UUID uuid) {
        PlayerTycoonData data = new PlayerTycoonData(uuid);
        
        // [슈퍼관리자] 새 관리자 초기 자금
        if (adminService != null && adminService.isSuperAdmin(uuid)) {
            data.setMoney(ADMIN_START_MONEY);
            data.setBottCoin(ADMIN_START_BOTTCOIN);
            logger.info("[PlayerDataManager] 새 슈퍼관리자 초기 자금 지급: " + uuid);
        }
        
        return data;
    }
    
    /**
     * [Phase 2] YamlConfiguration에서 전체 PlayerTycoonData 로드
     * 
     * 이 메서드는 손상 복구 시 사용됨. loadOrCreate와 동일한 로직 사용.
     */
    private PlayerTycoonData loadFullDataFromConfig(UUID uuid, YamlConfiguration c) {
        PlayerTycoonData data = new PlayerTycoonData(uuid);
        
        // 마이그레이션 실행
        runMigrations(c);
        
        // 플레이어 이름 (디버그용)
        data.setPlayerName(c.getString("playerName", ""));
        
        // 경제 데이터 (v2 구조 우선, v1 폴백)
        data.setMoney(c.getLong("economy.bd", c.getLong("money", 0L)));
        data.setBottCoin(c.getLong("economy.bottCoin", c.getLong("bottCoin", 0L)));
        data.setLifetimeEarned(c.getLong("economy.lifetimeEarned", 0L));
        data.setLifetimeSpent(c.getLong("economy.lifetimeSpent", 0L));
        
        // recentTxnIds 로드
        List<String> recentTxnIds = c.getStringList("economy.recentTxnIds");
        if (recentTxnIds.isEmpty()) {
            String lastTxnId = c.getString("economy.lastTxnId", "");
            if (!lastTxnId.isEmpty()) {
                recentTxnIds = new ArrayList<>();
                recentTxnIds.add(lastTxnId);
            }
        }
        data.setRecentTxnIds(recentTxnIds);

        // 도감 데이터
        List<String> unlocked = c.getStringList("codex.unlocked");
        if (unlocked.isEmpty()) {
            unlocked = c.getStringList("unlockedCodex");
        }
        data.getUnlockedCodex().addAll(unlocked);
        
        // 도감 마일스톤/카테고리 보상
        List<Integer> milestones = c.getIntegerList("codex.claimedMilestones");
        data.getClaimedCodexMilestones().addAll(milestones);
        List<String> categories = c.getStringList("codex.claimedCategories");
        data.getClaimedCodexCategories().addAll(categories);
        
        // [2026-02-01] 도감 보상 버전 (소급적용용)
        data.setCodexRewardVersion(c.getInt("codex.rewardVersion", 0));
        
        // 칭호 시스템
        List<String> titles = c.getStringList("titles.unlocked");
        data.getUnlockedTitles().addAll(titles);
        data.setEquippedTitle(c.getString("titles.equipped", null));
        
        // 업적 시스템
        List<String> achievements = c.getStringList("achievements.unlocked");
        data.getUnlockedAchievements().addAll(achievements);
        ConfigurationSection progressSection = c.getConfigurationSection("achievements.progress");
        if (progressSection != null) {
            for (String key : progressSection.getKeys(false)) {
                data.setAchievementProgress(key, progressSection.getInt(key));
            }
        }
        
        // 쿨다운 데이터
        ConfigurationSection cdSection = c.getConfigurationSection("cooldowns");
        if (cdSection != null) {
            for (String key : cdSection.getKeys(false)) {
                data.getCooldowns().put(key, cdSection.getLong(key));
            }
        }
        
        // 티켓 소비 통계
        ConfigurationSection ticketSection = c.getConfigurationSection("ticketConsumed");
        if (ticketSection != null) {
            for (String key : ticketSection.getKeys(false)) {
                data.getTicketConsumed().put(key, ticketSection.getInt(key));
            }
        }
        
        // 플롯 데이터
        List<String> plots = c.getStringList("plots.owned");
        if (plots.isEmpty()) {
            plots = c.getStringList("ownedPlots");
        }
        data.getOwnedPlots().addAll(plots);
        
        // 직업 데이터 (tier1, tier2)
        String tier1JobId = c.getString("job.tier1.type", c.getString("tier1Job", null));
        if (tier1JobId != null && !tier1JobId.isEmpty()) {
            data.setTier1Job(JobType.fromId(tier1JobId));
        }
        data.setTier1JobLevel(c.getInt("job.tier1.level", c.getInt("tier1JobLevel", 1)));
        data.setTier1JobExp(c.getLong("job.tier1.exp", c.getLong("tier1JobExp", 0L)));
        
        String tier2JobId = c.getString("job.tier2.type", c.getString("tier2Job", null));
        if (tier2JobId != null && !tier2JobId.isEmpty()) {
            data.setTier2Job(JobType.fromId(tier2JobId));
        }
        data.setTier2JobLevel(c.getInt("job.tier2.level", c.getInt("tier2JobLevel", 1)));
        data.setTier2JobExp(c.getLong("job.tier2.exp", c.getLong("tier2JobExp", 0L)));
        
        // [Phase 4.B] 직업 통계 로드
        data.setTotalMined(c.getLong("jobs.stats.totalMined", 0L));
        data.setTotalHarvested(c.getLong("jobs.stats.totalHarvested", 0L));
        data.setTotalFished(c.getLong("jobs.stats.totalFished", 0L));
        data.setTotalSales(c.getLong("jobs.stats.totalSales", 0L));
        
        // [Phase 4.D] 직업별 판매액 로드
        data.setTotalMinerSales(c.getLong("jobs.stats.minerSales", 0L));
        data.setTotalFarmerSales(c.getLong("jobs.stats.farmerSales", 0L));
        data.setTotalFisherSales(c.getLong("jobs.stats.fisherSales", 0L));
        
        // 인벤토리 보호 상태
        data.setUniversalInventorySaveActive(c.getBoolean("inventory.universalSaveActive", false));
        String pendingAction = c.getString("inventory.pendingAction", "");
        String pendingItemId = c.getString("inventory.pendingItemId", "");
        if (!pendingAction.isEmpty()) {
            data.setPendingCoreItemAction(pendingAction, pendingItemId);
        }
        
        // 밀수품 데이터
        data.setContrabandCount(c.getInt("contraband.count", 0));
        data.setLastContrabandTime(c.getLong("contraband.lastTime", 0L));
        
        // 검증 및 클램핑
        boolean needsSave = data.validateAndClamp();
        if (needsSave) {
            logger.warning("[PlayerDataManager] 잘못된 값 수정됨 (복구 중): " + uuid);
        }
        
        data.setDirty(false);
        return data;
    }

    private PlayerTycoonData loadOrCreate(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".yml");
        PlayerTycoonData data = new PlayerTycoonData(uuid);
        boolean needsSave = false;

        if (file.exists()) {
            YamlConfiguration c;
            
            // [Phase 2] 손상 감지 및 복구
            try {
                c = YamlConfiguration.loadConfiguration(file);
                
                // 기본 무결성 확인
                if (c.getKeys(false).isEmpty()) {
                    logger.warning("[PlayerDataManager] 빈 파일 감지: " + uuid);
                    return recoverFromBackupOrCreate(uuid);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[PlayerDataManager] YAML 파싱 실패: " + uuid, e);
                return recoverFromBackupOrCreate(uuid);
            }
            
            // [Stage-3] 마이그레이션 실행
            if (runMigrations(c)) {
                needsSave = true;
            }
            
            // 플레이어 이름 (디버그용)
            data.setPlayerName(c.getString("playerName", ""));
            
            // 경제 데이터 (v2 구조 우선, v1 폴백)
            data.setMoney(c.getLong("economy.bd", c.getLong("money", 0L)));
            data.setBottCoin(c.getLong("economy.bottCoin", c.getLong("bottCoin", 0L)));
            data.setLifetimeEarned(c.getLong("economy.lifetimeEarned", 0L));
            data.setLifetimeSpent(c.getLong("economy.lifetimeSpent", 0L));
            // [5.2 Fix] recentTxnIds 리스트로 로드 (하위 호환)
            List<String> recentTxnIds = c.getStringList("economy.recentTxnIds");
            if (recentTxnIds.isEmpty()) {
                // 하위 호환: 기존 lastTxnId를 리스트로 마이그레이션
                String lastTxnId = c.getString("economy.lastTxnId", "");
                if (!lastTxnId.isEmpty()) {
                    recentTxnIds = new ArrayList<>();
                    recentTxnIds.add(lastTxnId);
                }
            }
            data.setRecentTxnIds(recentTxnIds);

            // 도감 데이터 (v2 구조 우선, v1 폴백)
            List<String> unlocked = c.getStringList("codex.unlocked");
            if (unlocked.isEmpty()) {
                unlocked = c.getStringList("unlockedCodex");
            }
            data.getUnlockedCodex().addAll(unlocked);
            
            // [Stage-11 FIX] 도감 마일스톤/카테고리 보상 수령 상태 로드
            List<Integer> milestones = c.getIntegerList("codex.claimedMilestones");
            for (Integer m : milestones) {
                data.claimCodexMilestone(m);
            }
            List<String> categories = c.getStringList("codex.claimedCategories");
            for (String cat : categories) {
                data.claimCodexCategory(cat);
            }
            
            // [Stage-11 FIX] 업적 데이터 로드
            List<String> unlockedAchievements = c.getStringList("achievements.unlocked");
            for (String achId : unlockedAchievements) {
                data.unlockAchievement(achId);
            }
            ConfigurationSection achProgressSec = c.getConfigurationSection("achievements.progress");
            if (achProgressSec != null) {
                for (String achId : achProgressSec.getKeys(false)) {
                    int progress = achProgressSec.getInt(achId, 0);
                    if (progress > 0) {
                        data.setAchievementProgress(achId, progress);
                    }
                }
            }
            
            // [Stage-11 FIX] 칭호 데이터 로드
            List<String> unlockedTitles = c.getStringList("titles.unlocked");
            for (String titleId : unlockedTitles) {
                data.unlockTitle(titleId);
            }
            data.setEquippedTitle(c.getString("titles.equipped", null));
            
            // [Stage-3] 범용 쿨다운 로드
            ConfigurationSection cooldownSec = c.getConfigurationSection("cooldowns");
            if (cooldownSec != null) {
                for (String key : cooldownSec.getKeys(false)) {
                    long endTime = cooldownSec.getLong(key, 0);
                    if (endTime > System.currentTimeMillis()) {
                        data.getCooldowns().put(key, endTime);
                    }
                }
            }
            
            // [Stage-3] 티켓 소비 통계 로드
            ConfigurationSection ticketSec = c.getConfigurationSection("tickets.consumed");
            if (ticketSec != null) {
                for (String key : ticketSec.getKeys(false)) {
                    int count = ticketSec.getInt(key, 0);
                    if (count > 0) {
                        data.getTicketConsumed().put(key, count);
                    }
                }
            }
            
            // 직업 데이터 - Tier 1
            // [2026-01-23 FIX] 레벨/경험치도 함께 로드하도록 수정
            String tier1JobId = c.getString("jobs.tier1.id");
            if (tier1JobId != null && !tier1JobId.isEmpty()) {
                JobType tier1Job = JobType.fromId(tier1JobId);
                if (tier1Job != null && tier1Job.isTier1()) {
                    // [FIX] 먼저 레벨/경험치를 직업별 필드에 복원한 후 직업 설정
                    // setTier1Job()이 syncTier1JobData()를 호출하므로 순서 중요
                    int tier1Level = c.getInt("jobs.tier1.level", 1);
                    long tier1Exp = c.getLong("jobs.tier1.exp", 0);
                    data.setJobLevel(tier1Job, tier1Level);
                    data.setJobExp(tier1Job, tier1Exp);
                    data.setTier1Job(tier1Job);
                    
                    logger.fine("[PlayerDataManager] Tier1 직업 로드: " + tier1Job.getId() + 
                            " Lv." + tier1Level + " (exp: " + tier1Exp + ")");
                }
            }
            
            // 직업 데이터 - Tier 2
            // [2026-01-23 FIX] 레벨/경험치도 함께 로드하도록 수정
            String tier2JobId = c.getString("jobs.tier2.id");
            if (tier2JobId != null && !tier2JobId.isEmpty()) {
                JobType tier2Job = JobType.fromId(tier2JobId);
                if (tier2Job != null && tier2Job.isTier2()) {
                    // [FIX] 먼저 레벨/경험치를 직업별 필드에 복원한 후 직업 설정
                    int tier2Level = c.getInt("jobs.tier2.level", 1);
                    long tier2Exp = c.getLong("jobs.tier2.exp", 0);
                    data.setJobLevel(tier2Job, tier2Level);
                    data.setJobExp(tier2Job, tier2Exp);
                    data.setTier2Job(tier2Job);
                    
                    logger.fine("[PlayerDataManager] Tier2 직업 로드: " + tier2Job.getId() + 
                            " Lv." + tier2Level + " (exp: " + tier2Exp + ")");
                }
            }
            
            // [2026-02-02 FIX] 직업 등급 로드 (서버 재시작 시 등급 유지)
            data.setMinerGrade(c.getInt("jobs.grades.miner", 1));
            data.setFarmerGrade(c.getInt("jobs.grades.farmer", 1));
            data.setFisherGrade(c.getInt("jobs.grades.fisher", 1));
            
            // [Phase 4.B] 직업 통계 로드
            data.setTotalMined(c.getLong("jobs.stats.totalMined", 0L));
            data.setTotalHarvested(c.getLong("jobs.stats.totalHarvested", 0L));
            data.setTotalFished(c.getLong("jobs.stats.totalFished", 0L));
            data.setTotalSales(c.getLong("jobs.stats.totalSales", 0L));
            
            // [Phase 4.D] 직업별 판매액 로드
            data.setTotalMinerSales(c.getLong("jobs.stats.minerSales", 0L));
            data.setTotalFarmerSales(c.getLong("jobs.stats.farmerSales", 0L));
            data.setTotalFisherSales(c.getLong("jobs.stats.fisherSales", 0L));
            
            // 플롯 데이터 (v2 구조 우선, v1 폴백)
            List<String> plots = c.getStringList("plots.owned");
            if (plots.isEmpty()) {
                plots = c.getStringList("ownedPlots");
            }
            data.getOwnedPlots().addAll(plots);
            
            // 야생 데이터 - 마지막 사망 위치
            String lastDeathWorld = c.getString("wild.lastDeath.world");
            if (lastDeathWorld != null && !lastDeathWorld.isEmpty()) {
                World world = Bukkit.getWorld(lastDeathWorld);
                if (world != null) {
                    double x = c.getDouble("wild.lastDeath.x", 0);
                    double y = c.getDouble("wild.lastDeath.y", 0);
                    double z = c.getDouble("wild.lastDeath.z", 0);
                    data.setLastDeathLocation(new Location(world, x, y, z));
                }
            }
            
            // [Phase 8] 야생 데이터 - 마지막 텔레포트 위치
            String lastTeleportWorld = c.getString("wild.lastTeleport.world");
            if (lastTeleportWorld != null && !lastTeleportWorld.isEmpty()) {
                World world = Bukkit.getWorld(lastTeleportWorld);
                if (world != null) {
                    double x = c.getDouble("wild.lastTeleport.x", 0);
                    double y = c.getDouble("wild.lastTeleport.y", 0);
                    double z = c.getDouble("wild.lastTeleport.z", 0);
                    data.setLastTeleportLocation(new Location(world, x, y, z));
                }
            }
            
            // [DROP] 헌터/듀얼/채무/던전/카지노 데이터 로드 제거됨 (Phase 1.5)
            
            // [Stage-5] 인벤토리 보호 상태 로드
            data.setUniversalInventorySaveActive(c.getBoolean("inventorySave.universalActive", false));
            String pendingAction = c.getString("inventorySave.pendingAction", "");
            String pendingItemId = c.getString("inventorySave.pendingItemId", "");
            if (!pendingAction.isEmpty()) {
                data.setPendingCoreItemAction(pendingAction, pendingItemId);
            }
            
            // [Anti-Exploit] 주민 거래 횟수 로드
            data.setVillagerTradeCount(c.getInt("antiExploit.villagerTradeCount", 0));
            
            // [세금 시스템] 세금 필드 로드
            data.resetDailyIncome(); // 먼저 리셋
            long savedDailyIncome = c.getLong("tax.dailyIncome", 0);
            if (savedDailyIncome > 0) {
                data.addDailyIncome(savedDailyIncome);
            }
            data.setLastDailyReset(c.getLong("tax.lastDailyReset", 0));
            data.setLastOnlineTime(c.getLong("tax.lastOnlineTime", 0));
            data.setLifetimeEarnedSnapshot(c.getLong("tax.lifetimeEarnedSnapshot", 0));
            // [v2] 3시간 간격 세금
            data.setIntervalIncome(c.getLong("tax.intervalIncome", 0));
            data.setLastIntervalReset(c.getLong("tax.lastIntervalReset", 0));
            
            // [v2.7] 개인 설정 로드
            data.setShowEffectMessages(c.getBoolean("settings.showEffectMessages", true));
            
            // [Stage-3] 데이터 검증 및 클램핑
            if (data.validateAndClamp()) {
                logger.warning("[PlayerDataManager] 잘못된 값 수정됨: " + uuid);
                needsSave = true;
            }
            
            // 마이그레이션 또는 수정이 있었으면 즉시 저장
            if (needsSave) {
                saveSync(uuid, data);
            }
        } else {
            // ========== 새 플레이어 초기화 ==========
            
            // [Admin] 새로 접속한 Admin에게 초기 자금 지급 (경제 메트릭스 영향 없음)
            if (adminService != null && adminService.isSuperAdmin(uuid)) {
                data.setMoney(ADMIN_START_MONEY);
                data.setBottCoin(ADMIN_START_BOTTCOIN);
                logger.info("[PlayerDataManager] Admin 초기 자금 지급: " + uuid + " -> " + ADMIN_START_MONEY + " BD, " + ADMIN_START_BOTTCOIN + " BottCoin");
            } else {
                // [Vanilla Plus] 일반 신규 플레이어 초기 자금 지급
                long startingBD = plugin.getConfig().getLong("newPlayer.startingBD", 5000L);
                data.setMoney(startingBD);
                logger.info("[PlayerDataManager] 신규 플레이어 초기 자금 지급: " + uuid + " -> " + startingBD + " BD");
            }
            
            // 새 플레이어는 즉시 저장
            saveSync(uuid, data);
        }
        
        // [Phase 2 FIX] 로드 후 dirty 초기화 (불필요한 첫 저장 방지)
        data.setDirty(false);
        return data;
    }

    /**
     * [Stage-3] 동기 저장 (원자적: temp file → rename)
     * 
     * [2026-01-24 FIX] 데이터 손상 방지를 위한 백업 추가
     */
    private void saveSync(UUID uuid, PlayerTycoonData data) {
        Path filePath = dataFolder.toPath().resolve(uuid.toString() + ".yml");
        Path tempPath = dataFolder.toPath().resolve(uuid.toString() + ".yml.tmp");
        Path backupPath = dataFolder.toPath().resolve(uuid.toString() + ".yml.bak");
        
        // [FIX] 기존 파일 백업 (저장 실패 시 복구용)
        try {
            if (Files.exists(filePath)) {
                Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.warning("[PlayerDataManager] 백업 실패 (무시): " + uuid + " - " + e.getMessage());
        }
        
        YamlConfiguration c = new YamlConfiguration();

        // [Stage-3] 스키마 버전
        c.set("schemaVersion", PlayerTycoonData.CURRENT_SCHEMA_VERSION);
        c.set("uuid", uuid.toString());
        c.set("playerName", data.getPlayerName());
        
        // 경제 데이터 (v2 구조)
        c.set("economy.bd", data.getMoney());
        c.set("economy.bottCoin", data.getBottCoin());
        c.set("economy.lifetimeEarned", data.getLifetimeEarned());
        c.set("economy.lifetimeSpent", data.getLifetimeSpent());
        // [5.2 Fix] recentTxnIds 리스트로 저장
        List<String> recentTxnIds = data.getRecentTxnIds();
        c.set("economy.recentTxnIds", recentTxnIds);
        c.set("economy.lastTxnId", recentTxnIds.isEmpty() ? "" : recentTxnIds.get(recentTxnIds.size() - 1));
        
        // 도감 데이터 (v2 구조)
        c.set("codex.unlocked", new ArrayList<>(data.getUnlockedCodex()));
        
        // [Stage-11 FIX] 도감 마일스톤/카테고리 보상 수령 상태 저장
        c.set("codex.claimedMilestones", new ArrayList<>(data.getClaimedCodexMilestones()));
        c.set("codex.claimedCategories", new ArrayList<>(data.getClaimedCodexCategories()));
        
        // [2026-02-01] 도감 보상 버전 저장 (소급적용용)
        c.set("codex.rewardVersion", data.getCodexRewardVersion());
        
        // [Stage-11 FIX] 업적 데이터 저장
        c.set("achievements.unlocked", new ArrayList<>(data.getUnlockedAchievements()));
        for (Map.Entry<String, Integer> entry : data.getAchievementProgressMap().entrySet()) {
            if (entry.getValue() > 0) {
                c.set("achievements.progress." + entry.getKey(), entry.getValue());
            }
        }
        
        // [Stage-11 FIX] 칭호 데이터 저장
        c.set("titles.unlocked", new ArrayList<>(data.getUnlockedTitles()));
        c.set("titles.equipped", data.getEquippedTitle());
        
        // [Stage-3] 범용 쿨다운 저장 (만료되지 않은 것만)
        data.cleanupExpiredCooldowns();
        for (Map.Entry<String, Long> entry : data.getCooldowns().entrySet()) {
            c.set("cooldowns." + entry.getKey(), entry.getValue());
        }
        
        // [Stage-3] 티켓 소비 통계 저장
        for (Map.Entry<String, Integer> entry : data.getTicketConsumed().entrySet()) {
            if (entry.getValue() > 0) {
                c.set("tickets.consumed." + entry.getKey(), entry.getValue());
            }
        }
        
        // 직업 데이터 저장 - Tier 1
        // [Level/Grade 통합] 범용 getter 사용, 직업별 필드에서 값 조회
        if (data.getTier1Job() != null) {
            JobType job = data.getTier1Job();
            c.set("jobs.tier1.id", job.getId());
            c.set("jobs.tier1.level", data.getJobLevel(job));
            c.set("jobs.tier1.exp", data.getJobExp(job));
        } else {
            c.set("jobs.tier1.id", null);
            c.set("jobs.tier1.level", 1);
            c.set("jobs.tier1.exp", 0);
        }
        
        // 직업 데이터 저장 - Tier 2
        // [Level/Grade 통합] 범용 getter 사용, 직업별 필드에서 값 조회
        if (data.getTier2Job() != null) {
            JobType job = data.getTier2Job();
            c.set("jobs.tier2.id", job.getId());
            c.set("jobs.tier2.level", data.getJobLevel(job));
            c.set("jobs.tier2.exp", data.getJobExp(job));
        } else {
            c.set("jobs.tier2.id", null);
            c.set("jobs.tier2.level", 1);
            c.set("jobs.tier2.exp", 0);
        }
        
        // [2026-02-02 FIX] 직업 등급 저장 (서버 재시작 시 등급 유지)
        c.set("jobs.grades.miner", data.getMinerGrade());
        c.set("jobs.grades.farmer", data.getFarmerGrade());
        c.set("jobs.grades.fisher", data.getFisherGrade());
        
        // [Phase 4.B] 직업 통계 저장
        c.set("jobs.stats.totalMined", data.getTotalMined());
        c.set("jobs.stats.totalHarvested", data.getTotalHarvested());
        c.set("jobs.stats.totalFished", data.getTotalFished());
        c.set("jobs.stats.totalSales", data.getTotalSales());
        
        // [Phase 4.D] 직업별 판매액 저장
        c.set("jobs.stats.minerSales", data.getTotalMinerSales());
        c.set("jobs.stats.farmerSales", data.getTotalFarmerSales());
        c.set("jobs.stats.fisherSales", data.getTotalFisherSales());
        
        // 플롯 데이터 저장 (v2 구조)
        c.set("plots.owned", new ArrayList<>(data.getOwnedPlots()));
        
        // 야생 데이터 저장 - 마지막 사망 위치
        Location lastDeath = data.getLastDeathLocation();
        if (lastDeath != null && lastDeath.getWorld() != null) {
            c.set("wild.lastDeath.world", lastDeath.getWorld().getName());
            c.set("wild.lastDeath.x", lastDeath.getX());
            c.set("wild.lastDeath.y", lastDeath.getY());
            c.set("wild.lastDeath.z", lastDeath.getZ());
        }
        
        // [Phase 8] 야생 데이터 저장 - 마지막 텔레포트 위치
        Location lastTeleport = data.getLastTeleportLocation();
        if (lastTeleport != null && lastTeleport.getWorld() != null) {
            c.set("wild.lastTeleport.world", lastTeleport.getWorld().getName());
            c.set("wild.lastTeleport.x", lastTeleport.getX());
            c.set("wild.lastTeleport.y", lastTeleport.getY());
            c.set("wild.lastTeleport.z", lastTeleport.getZ());
        }
        
        // [DROP] 헌터/듀얼/채무/던전 데이터 저장 제거됨 (Phase 1.5)
        
        // [Stage-5] 인벤토리 보호 상태 저장
        c.set("inventorySave.universalActive", data.isUniversalInventorySaveActive());
        c.set("inventorySave.pendingAction", data.getPendingCoreItemAction());
        c.set("inventorySave.pendingItemId", data.getPendingCoreItemId());
        c.set("inventorySave.pendingTime", data.getPendingCoreItemTime());
        
        // [Anti-Exploit] 주민 거래 횟수 저장
        c.set("antiExploit.villagerTradeCount", data.getVillagerTradeCount());
        
        // [세금 시스템] 세금 필드 저장
        c.set("tax.dailyIncome", data.getDailyIncome());
        c.set("tax.lastDailyReset", data.getLastDailyReset());
        c.set("tax.lastOnlineTime", data.getLastOnlineTime());
        c.set("tax.lifetimeEarnedSnapshot", data.getLifetimeEarnedSnapshot());
        // [v2] 3시간 간격 세금
        c.set("tax.intervalIncome", data.getIntervalIncome());
        c.set("tax.lastIntervalReset", data.getLastIntervalReset());
        
        // [v2.7] 개인 설정 저장
        c.set("settings.showEffectMessages", data.isShowEffectMessages());
        
        // [DROP] 카지노 데이터 저장 제거됨 (Phase 1.5)

        try {
            // [Stage-3] 원자적 저장: temp file → rename
            c.save(tempPath.toFile());
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            // [FIX] 저장 성공 시 백업 파일 삭제 (공간 절약)
            try {
                Files.deleteIfExists(backupPath);
            } catch (IOException ignored) {}
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "[PlayerDataManager] 저장 실패: " + uuid, e);
            
            // temp 파일 정리
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
            
            // [FIX] 백업에서 복구 시도
            try {
                if (Files.exists(backupPath) && !Files.exists(filePath)) {
                    Files.copy(backupPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("[PlayerDataManager] 백업에서 복구됨: " + uuid);
                }
            } catch (IOException recoverE) {
                logger.warning("[PlayerDataManager] 백업 복구 실패: " + uuid + " - " + recoverE.getMessage());
            }
        }
    }
    
    // ========== [H-1] 리셋 메서드 (devMode 전용) ==========
    
    /**
     * 모든 플레이어 데이터 완전 삭제
     * @return 삭제된 파일 수
     */
    public int resetAllPlayers() {
        // 메모리 캐시 클리어
        dataMap.clear();
        
        // 파일 삭제
        int count = 0;
        Path dataPath = dataFolder.toPath();
        try {
            if (Files.exists(dataPath)) {
                try (var files = Files.list(dataPath)) {
                    for (Path file : files.toList()) {
                        if (file.toString().endsWith(".yml")) {
                            Files.delete(file);
                            count++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "[PlayerDataManager] 리셋 중 오류", e);
        }
        
        logger.warning("[PlayerDataManager] RESET_ALL_PLAYERS: " + count + " files deleted");
        return count;
    }
    
    /**
     * 모든 플레이어의 경제 데이터만 리셋 (돈, 봇코인, 채무)
     * @return 영향받은 플레이어 수
     */
    public int resetAllEconomy() {
        int count = 0;
        
        // 캐시된 데이터 리셋
        for (PlayerTycoonData data : dataMap.values()) {
            data.setMoney(0);
            data.setBottCoin(0);
            // [DROP] clearGarnishDebt 제거됨 (Phase 1.5)
            count++;
        }
        
        // 저장된 파일 수정
        Path dataPath = dataFolder.toPath();
        try {
            if (Files.exists(dataPath)) {
                try (var files = Files.list(dataPath)) {
                    for (Path file : files.toList()) {
                        if (file.toString().endsWith(".yml")) {
                            resetEconomyInFile(file);
                            if (!dataMap.containsKey(extractUuidFromPath(file))) {
                                count++;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "[PlayerDataManager] 경제 리셋 중 오류", e);
        }
        
        // 캐시된 데이터 저장
        saveAll();
        
        logger.warning("[PlayerDataManager] RESET_ALL_ECONOMY: " + count + " players affected");
        return count;
    }
    
    /**
     * 모든 플레이어의 직업 데이터만 리셋
     * @return 영향받은 플레이어 수
     */
    public int resetAllJobs() {
        int count = 0;
        
        // 캐시된 데이터 리셋
        for (PlayerTycoonData data : dataMap.values()) {
            data.clearTier1Job();
            data.clearTier2Job();
            count++;
        }
        
        // 저장된 파일 수정
        Path dataPath2 = dataFolder.toPath();
        try {
            if (Files.exists(dataPath2)) {
                try (var files = Files.list(dataPath2)) {
                    for (Path file : files.toList()) {
                        if (file.toString().endsWith(".yml")) {
                            resetJobsInFile(file);
                            if (!dataMap.containsKey(extractUuidFromPath(file))) {
                                count++;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "[PlayerDataManager] 직업 리셋 중 오류", e);
        }
        
        // 캐시된 데이터 저장
        saveAll();
        
        logger.warning("[PlayerDataManager] RESET_ALL_JOBS: " + count + " players affected");
        return count;
    }
    
    /**
     * 모든 데이터 리셋 (완전 초기화)
     * @return 삭제된 파일 수
     */
    public int resetAll() {
        return resetAllPlayers();
    }
    
    private void resetEconomyInFile(Path file) {
        try {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(file.toFile());
            c.set("economy.money", 0L);
            c.set("economy.bottCoin", 0L);
            c.set("economy.garnishDebt", 0L);
            c.set("economy.lifetimeEarned", 0L);
            c.set("economy.lifetimeSpent", 0L);
            c.save(file.toFile());
        } catch (IOException e) {
            logger.log(Level.WARNING, "[PlayerDataManager] 파일 경제 리셋 실패: " + file, e);
        }
    }
    
    private void resetJobsInFile(Path file) {
        try {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(file.toFile());
            c.set("job.tier1.id", null);
            c.set("job.tier1.level", 1);
            c.set("job.tier1.exp", 0L);
            c.set("job.tier1.grade", 1);
            c.set("job.tier2.id", null);
            c.set("job.tier2.level", 1);
            c.set("job.tier2.exp", 0L);
            c.set("job.tier2.grade", 1);
            c.set("job.miner", null);
            c.set("job.fisher", null);
            c.set("job.farmer", null);
            c.save(file.toFile());
        } catch (IOException e) {
            logger.log(Level.WARNING, "[PlayerDataManager] 파일 직업 리셋 실패: " + file, e);
        }
    }
    
    private UUID extractUuidFromPath(Path file) {
        String filename = file.getFileName().toString();
        if (filename.endsWith(".yml")) {
            String uuidStr = filename.substring(0, filename.length() - 4);
            try {
                return UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    // ========== [v2] 전체 플레이어 조회 ==========

    /**
     * [v2] 저장된 모든 플레이어 UUID 조회
     * - 캐시에 있는 플레이어 + 디스크에 저장된 플레이어 파일
     * 
     * @return 모든 플레이어 UUID Set
     */
    public Set<UUID> getAllPlayerIds() {
        Set<UUID> allIds = new HashSet<>(dataMap.keySet());
        
        // 디스크에 저장된 파일에서 추가 UUID 수집
        if (dataFolder.exists() && dataFolder.isDirectory()) {
            File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String filename = file.getName();
                    String uuidStr = filename.substring(0, filename.length() - 4);
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        allIds.add(uuid);
                    } catch (IllegalArgumentException ignored) {
                        // 유효하지 않은 UUID 파일명 무시
                    }
                }
            }
        }
        
        return allIds;
    }

    /**
     * [v2] 캐시에 있는 플레이어 UUID만 조회
     */
    public Set<UUID> getCachedPlayerIds() {
        return new HashSet<>(dataMap.keySet());
    }
}
