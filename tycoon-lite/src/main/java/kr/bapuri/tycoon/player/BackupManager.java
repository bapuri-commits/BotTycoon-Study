package kr.bapuri.tycoon.player;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * BackupManager - 플레이어 데이터 스냅샷 백업 관리
 * 
 * [Phase 2] 주요 기능:
 * - 타임스탬프 기반 스냅샷 생성
 * - 플레이어별 최대 N개 스냅샷 유지
 * - 스냅샷에서 복원
 * - 주기적 백업 스케줄러
 */
public class BackupManager {

    private final Plugin plugin;
    private final Logger logger;
    private final Path backupRoot;
    private final Path playerDataFolder;
    
    // 설정 (config.yml에서 로드)
    private int maxSnapshots;
    private int snapshotIntervalMinutes;
    private boolean backupOnQuit;
    private boolean backupEnabled;
    
    // 스케줄러
    private BukkitTask snapshotTask;
    
    // 타임스탬프 형식
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public BackupManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.backupRoot = plugin.getDataFolder().toPath()
            .resolve("backups").resolve("playerdata");
        this.playerDataFolder = plugin.getDataFolder().toPath()
            .resolve("playerdata");
        
        // [Phase 2] config.yml에서 설정 읽기
        loadConfig();
        
        // 백업 폴더 생성
        try {
            Files.createDirectories(backupRoot);
        } catch (IOException e) {
            logger.log(Level.WARNING, "[BackupManager] 백업 폴더 생성 실패", e);
        }
    }
    
    /**
     * [Phase 2] config.yml에서 설정 읽기
     * 설정 경로: playerdata.backup.*
     */
    private void loadConfig() {
        org.bukkit.configuration.file.FileConfiguration config = 
            ((org.bukkit.plugin.java.JavaPlugin) plugin).getConfig();
        
        // playerdata.backup 섹션에서 로드 (전체 플레이어 데이터 스냅샷 백업)
        this.backupEnabled = config.getBoolean("playerdata.backup.enabled", true);
        this.snapshotIntervalMinutes = config.getInt("playerdata.backup.interval-minutes", 30);
        this.maxSnapshots = config.getInt("playerdata.backup.max-snapshots", 10);
        this.backupOnQuit = config.getBoolean("playerdata.backup.backup-on-quit", true);
        
        logger.info("[BackupManager] 설정 로드: enabled=" + backupEnabled + 
                    ", interval=" + snapshotIntervalMinutes + "분, maxSnapshots=" + maxSnapshots);
    }

    // ========== 스냅샷 생성 ==========

    /**
     * 플레이어 데이터 스냅샷 생성
     * 
     * @param uuid 플레이어 UUID
     * @return 생성 성공 여부
     */
    public boolean createSnapshot(UUID uuid) {
        try {
            Path playerDir = backupRoot.resolve(uuid.toString());
            Files.createDirectories(playerDir);
            
            // 현재 데이터 파일
            Path current = playerDataFolder.resolve(uuid.toString() + ".yml");
            
            if (!Files.exists(current)) {
                return false;
            }
            
            // 타임스탬프 파일명
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            Path snapshot = playerDir.resolve(timestamp + ".yml");
            
            // 복사
            Files.copy(current, snapshot, StandardCopyOption.REPLACE_EXISTING);
            
            // 오래된 스냅샷 정리
            cleanupOldSnapshots(playerDir);
            
            return true;
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "[BackupManager] 스냅샷 생성 실패: " + uuid, e);
            return false;
        }
    }

    /**
     * 모든 온라인 플레이어 스냅샷 생성
     * 
     * @param onlineUUIDs 온라인 플레이어 UUID 목록
     * @return 생성된 스냅샷 수
     */
    public int createSnapshotsForAll(Collection<UUID> onlineUUIDs) {
        int count = 0;
        for (UUID uuid : onlineUUIDs) {
            if (createSnapshot(uuid)) {
                count++;
            }
        }
        return count;
    }

    // ========== 스냅샷 조회 ==========

    /**
     * 플레이어의 스냅샷 목록 조회
     * 
     * @param uuid 플레이어 UUID
     * @return 스냅샷 타임스탬프 목록 (최신순)
     */
    public List<String> listSnapshots(UUID uuid) {
        Path playerDir = backupRoot.resolve(uuid.toString());
        
        if (!Files.exists(playerDir)) {
            return Collections.emptyList();
        }
        
        try (Stream<Path> paths = Files.list(playerDir)) {
            return paths
                .filter(p -> p.toString().endsWith(".yml"))
                .map(p -> p.getFileName().toString().replace(".yml", ""))
                .sorted(Comparator.reverseOrder())  // 최신순
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.log(Level.WARNING, "[BackupManager] 스냅샷 목록 조회 실패: " + uuid, e);
            return Collections.emptyList();
        }
    }

    /**
     * 플레이어의 스냅샷 개수
     */
    public int getSnapshotCount(UUID uuid) {
        return listSnapshots(uuid).size();
    }

    /**
     * 가장 최근 스냅샷 타임스탬프
     */
    public String getLatestSnapshot(UUID uuid) {
        List<String> snapshots = listSnapshots(uuid);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    // ========== 스냅샷 복원 ==========

    /**
     * 스냅샷에서 복원
     * 
     * @param uuid 플레이어 UUID
     * @param timestamp 복원할 스냅샷 타임스탬프
     * @return 복원 성공 여부
     */
    public boolean restoreFromSnapshot(UUID uuid, String timestamp) {
        Path snapshot = backupRoot.resolve(uuid.toString())
            .resolve(timestamp + ".yml");
        
        if (!Files.exists(snapshot)) {
            logger.warning("[BackupManager] 스냅샷 없음: " + uuid + "/" + timestamp);
            return false;
        }
        
        Path current = playerDataFolder.resolve(uuid.toString() + ".yml");
        
        try {
            // 복원 전 현재 상태 백업
            createSnapshot(uuid);
            
            // 스냅샷에서 복원
            Files.copy(snapshot, current, StandardCopyOption.REPLACE_EXISTING);
            
            logger.info("[BackupManager] 복원 완료: " + uuid + " <- " + timestamp);
            return true;
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "[BackupManager] 복원 실패: " + uuid, e);
            return false;
        }
    }

    /**
     * 가장 최근 스냅샷에서 복원 (손상 복구용)
     */
    public boolean restoreFromLatest(UUID uuid) {
        String latest = getLatestSnapshot(uuid);
        if (latest == null) {
            return false;
        }
        return restoreFromSnapshot(uuid, latest);
    }

    // ========== 스냅샷 정리 ==========

    /**
     * 오래된 스냅샷 정리
     */
    private void cleanupOldSnapshots(Path playerDir) {
        try (Stream<Path> paths = Files.list(playerDir)) {
            List<Path> snapshots = paths
                .filter(p -> p.toString().endsWith(".yml"))
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                .collect(Collectors.toList());
            
            // maxSnapshots 초과 시 오래된 것 삭제
            for (int i = maxSnapshots; i < snapshots.size(); i++) {
                try {
                    Files.delete(snapshots.get(i));
                } catch (IOException e) {
                    logger.warning("[BackupManager] 오래된 스냅샷 삭제 실패: " + snapshots.get(i));
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "[BackupManager] 스냅샷 정리 실패: " + playerDir, e);
        }
    }

    /**
     * 특정 스냅샷 삭제
     */
    public boolean deleteSnapshot(UUID uuid, String timestamp) {
        Path snapshot = backupRoot.resolve(uuid.toString())
            .resolve(timestamp + ".yml");
        
        try {
            return Files.deleteIfExists(snapshot);
        } catch (IOException e) {
            logger.log(Level.WARNING, "[BackupManager] 스냅샷 삭제 실패: " + uuid + "/" + timestamp, e);
            return false;
        }
    }

    // ========== 스케줄러 ==========

    /**
     * 주기적 스냅샷 스케줄러 시작
     * 
     * @param dataManager 온라인 플레이어 UUID 조회용
     */
    public void startSnapshotScheduler(PlayerDataManager dataManager) {
        // [Phase 2] config에서 비활성화된 경우 스킵
        if (!backupEnabled) {
            logger.info("[Snapshot] 스냅샷 백업 비활성화됨 (config)");
            return;
        }
        
        if (snapshotTask != null) {
            snapshotTask.cancel();
        }
        
        long intervalTicks = snapshotIntervalMinutes * 60 * 20L;
        
        snapshotTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Set<UUID> onlineUUIDs = dataManager.getOnlinePlayerUUIDs();
            int count = createSnapshotsForAll(onlineUUIDs);
            if (count > 0) {
                logger.info("[Snapshot] " + count + "명 스냅샷 백업 완료");
            }
        }, intervalTicks, intervalTicks);
        
        logger.info("[Snapshot] 스냅샷 스케줄러 시작됨 (간격: " + snapshotIntervalMinutes + "분)");
    }
    
    /**
     * [Phase 2] 퇴장 시 백업 여부 확인
     */
    public boolean isBackupOnQuit() {
        return backupEnabled && backupOnQuit;
    }

    /**
     * 스케줄러 중지
     */
    public void stopSnapshotScheduler() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
            logger.info("[Snapshot] 스냅샷 스케줄러 중지됨");
        }
    }

    // ========== 설정 ==========

    public void setMaxSnapshots(int maxSnapshots) {
        this.maxSnapshots = Math.max(1, maxSnapshots);
    }

    public void setSnapshotIntervalMinutes(int minutes) {
        this.snapshotIntervalMinutes = Math.max(5, minutes);
    }

    public void setBackupOnQuit(boolean backupOnQuit) {
        this.backupOnQuit = backupOnQuit;
    }

    public int getMaxSnapshots() {
        return maxSnapshots;
    }

    public int getSnapshotIntervalMinutes() {
        return snapshotIntervalMinutes;
    }

    // ========== 통계 ==========

    /**
     * 백업 폴더 총 크기 (바이트)
     */
    public long getTotalBackupSize() {
        try (Stream<Path> paths = Files.walk(backupRoot)) {
            return paths
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 총 스냅샷 파일 수
     */
    public int getTotalSnapshotCount() {
        try (Stream<Path> paths = Files.walk(backupRoot)) {
            return (int) paths
                .filter(p -> p.toString().endsWith(".yml"))
                .count();
        } catch (IOException e) {
            return 0;
        }
    }
}
