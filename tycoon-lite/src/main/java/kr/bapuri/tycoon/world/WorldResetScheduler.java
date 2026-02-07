package kr.bapuri.tycoon.world;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * WorldResetScheduler - 월드 자동 리셋 스케줄러
 * 
 * 기능:
 * - N일마다 자동으로 Wild 리셋
 * - 리셋 전 사전 알림 (10분, 5분, 1분 전)
 * - 리셋 카운트다운 (10초)
 * - WorldResetCommand 연동 (실제 리셋 로직 재사용)
 * 
 * 설정 위치: config.yml -> wildReset
 * 
 * 주의: 기본값 enabled=false (수동으로 활성화 필요)
 */
public class WorldResetScheduler {

    private final Plugin plugin;
    private final WorldManager worldManager;
    private final Logger logger;
    
    // 설정
    private boolean enabled = false;
    private int intervalDays = 7;
    private List<Integer> announceMinutes = Arrays.asList(10, 5, 1);
    private int countdownSeconds = 10;
    
    // 상태
    private BukkitTask scheduledResetTask;
    private final List<BukkitTask> announceTasks = new ArrayList<>();  // #7 수정: 알림 태스크 목록
    private long nextResetTimeMillis = 0;
    
    // 마지막 리셋 시간 파일
    private static final String LAST_RESET_FILE = "plugins/TycoonLite/data/wild_last_reset.txt";
    private static final DateTimeFormatter KST_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));
    
    // WorldResetCommand 연동
    private WorldResetCommand worldResetCommand;
    
    public WorldResetScheduler(Plugin plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.logger = Logger.getLogger("TycoonLite.WorldResetScheduler");
        
        loadConfig();
    }
    
    /**
     * WorldResetCommand 연동 설정
     */
    public void setWorldResetCommand(WorldResetCommand worldResetCommand) {
        this.worldResetCommand = worldResetCommand;
    }
    
    /**
     * 설정 로드
     */
    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("wildReset.enabled", false);
        intervalDays = plugin.getConfig().getInt("wildReset.intervalDays", 7);
        
        List<Integer> configAnnounce = plugin.getConfig().getIntegerList("wildReset.announceMinutes");
        if (!configAnnounce.isEmpty()) {
            announceMinutes = configAnnounce;
        }
        
        countdownSeconds = plugin.getConfig().getInt("wildReset.countdownSeconds", 10);
        
        logger.info("[WorldResetScheduler] 설정 로드 (enabled=" + enabled + ", interval=" + intervalDays + "일)");
    }
    
    /**
     * 스케줄러 시작
     */
    public void start() {
        if (!enabled) {
            logger.info("[WorldResetScheduler] 자동 리셋 비활성화 상태");
            return;
        }
        
        // 마지막 리셋 시간 로드
        long lastResetMillis = loadLastResetTime();
        if (lastResetMillis == 0) {
            // 첫 실행: 서버 시작 시간 기준
            lastResetMillis = System.currentTimeMillis();
            saveLastResetTime(lastResetMillis);
        }
        
        // 다음 리셋 시간 계산
        nextResetTimeMillis = lastResetMillis + (intervalDays * 24L * 60 * 60 * 1000);
        long delayMillis = nextResetTimeMillis - System.currentTimeMillis();
        
        if (delayMillis <= 0) {
            // 이미 리셋 시간 지남 -> 1분 후 리셋
            logger.info("[WorldResetScheduler] 리셋 시간이 지났습니다. 1분 후 리셋 시작...");
            delayMillis = 60_000;
            nextResetTimeMillis = System.currentTimeMillis() + delayMillis;
        }
        
        // 알림 스케줄링
        scheduleAnnouncements(delayMillis);
        
        // 리셋 스케줄링
        long delayTicks = delayMillis / 50;
        scheduledResetTask = Bukkit.getScheduler().runTaskLater(plugin, this::startResetSequence, delayTicks);
        
        Instant nextReset = Instant.ofEpochMilli(nextResetTimeMillis);
        logger.info("[WorldResetScheduler] 다음 리셋 예정: " + KST_FORMATTER.format(nextReset));
    }
    
    /**
     * 스케줄러 정지
     * #7 수정: 모든 알림 태스크도 취소
     */
    public void stop() {
        if (scheduledResetTask != null) {
            scheduledResetTask.cancel();
            scheduledResetTask = null;
        }
        
        // 모든 알림 태스크 취소
        for (BukkitTask task : announceTasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        announceTasks.clear();
        
        logger.info("[WorldResetScheduler] 스케줄러 정지");
    }
    
    /**
     * 알림 스케줄링
     * #7 수정: 태스크를 목록에 저장하여 stop() 시 취소 가능
     */
    private void scheduleAnnouncements(long delayMillis) {
        // 기존 알림 태스크 정리
        announceTasks.clear();
        
        for (int minutes : announceMinutes) {
            long announceDelayMillis = delayMillis - (minutes * 60L * 1000);
            if (announceDelayMillis > 0) {
                long delayTicks = announceDelayMillis / 50;
                final int min = minutes;
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    broadcastWarning(min);
                }, delayTicks);
                announceTasks.add(task);
            }
        }
    }
    
    /**
     * 리셋 경고 브로드캐스트
     */
    private void broadcastWarning(int minutes) {
        String message = "§c§l[야생 리셋] §e야생 월드가 §c" + minutes + "분 §e후에 리셋됩니다! 마을로 이동하세요!";
        Bukkit.broadcastMessage(message);
    }
    
    /**
     * 리셋 시퀀스 시작 (카운트다운)
     */
    private void startResetSequence() {
        if (worldResetCommand != null && worldResetCommand.isResetInProgress()) {
            logger.warning("[WorldResetScheduler] 리셋이 이미 진행 중입니다.");
            return;
        }
        
        logger.info("[WorldResetScheduler] 자동 리셋 시퀀스 시작...");
        
        // 카운트다운
        for (int i = countdownSeconds; i > 0; i--) {
            final int sec = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Bukkit.broadcastMessage("§c§l[야생 리셋] §e" + sec + "초 후 리셋!");
            }, (countdownSeconds - i) * 20L);
        }
        
        // 카운트다운 후 리셋 실행
        Bukkit.getScheduler().runTaskLater(plugin, this::executeReset, countdownSeconds * 20L);
    }
    
    /**
     * 실제 리셋 실행
     */
    private void executeReset() {
        logger.info("[WorldResetScheduler] 자동 리셋 실행");
        
        // 마지막 리셋 시간 저장
        saveLastResetTime(System.currentTimeMillis());
        
        if (worldResetCommand != null) {
            // 직접 리셋 실행 (확인 과정 생략)
            worldResetCommand.executeDirectReset();
        } else {
            logger.warning("[WorldResetScheduler] WorldResetCommand가 설정되지 않았습니다.");
        }
        
        // 다음 리셋 스케줄 (리셋 완료 후 충분한 시간 대기)
        Bukkit.getScheduler().runTaskLater(plugin, this::start, 20L * 60 * 2); // 2분 후 재스케줄
    }
    
    // ========== 마지막 리셋 시간 관리 ==========
    
    private long loadLastResetTime() {
        try {
            Path timePath = Paths.get(LAST_RESET_FILE);
            if (Files.exists(timePath)) {
                return Long.parseLong(Files.readString(timePath).trim());
            }
        } catch (Exception e) {
            logger.warning("[WorldResetScheduler] 마지막 리셋 시간 로드 실패");
        }
        return 0;
    }
    
    private void saveLastResetTime(long timeMillis) {
        try {
            Path timePath = Paths.get(LAST_RESET_FILE);
            Files.createDirectories(timePath.getParent());
            Files.writeString(timePath, String.valueOf(timeMillis));
        } catch (IOException e) {
            logger.warning("[WorldResetScheduler] 마지막 리셋 시간 저장 실패");
        }
    }
    
    // ========== 상태 조회 ==========
    
    /**
     * 다음 리셋까지 남은 시간 (초)
     * @return -1 = 비활성화
     */
    public long getSecondsUntilNextReset() {
        if (!enabled || nextResetTimeMillis == 0) return -1;
        
        long remaining = nextResetTimeMillis - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public int getIntervalDays() {
        return intervalDays;
    }
    
    /**
     * 상태 정보 문자열
     */
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== Wild Reset Scheduler ===\n");
        sb.append("§7활성화: ").append(enabled ? "§a예" : "§c아니오").append("\n");
        
        if (enabled && nextResetTimeMillis > 0) {
            long remaining = nextResetTimeMillis - System.currentTimeMillis();
            if (remaining > 0) {
                long hours = remaining / (1000 * 60 * 60);
                long minutes = (remaining / (1000 * 60)) % 60;
                sb.append("§7다음 리셋: ").append(hours).append("시간 ").append(minutes).append("분 후\n");
            }
            sb.append("§7예정 시각: ").append(KST_FORMATTER.format(Instant.ofEpochMilli(nextResetTimeMillis)));
        }
        
        return sb.toString();
    }
}
