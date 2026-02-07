package kr.bapuri.tycoon.achievement;

import kr.bapuri.tycoon.TycoonPlugin;
import kr.bapuri.tycoon.codex.CodexService;
import kr.bapuri.tycoon.job.JobService;
import kr.bapuri.tycoon.job.JobType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * AchievementListener - 업적 트리거 리스너
 * 
 * Codex, Job, PvP 이벤트를 감지하여 업적 진행/해금
 * 
 * [버그수정] 레거시 방식 적용:
 * - TycoonPlugin 참조로 다른 서비스에서 이 리스너 접근 가능
 * - PlayerJoinEvent에서 소급 체크 수행
 * - CodexService, JobService에서 업적 트리거 메서드 호출
 */
public class AchievementListener implements Listener {
    
    private final TycoonPlugin plugin;
    private final AchievementService achievementService;
    
    // Optional dependencies (setter 주입)
    private CodexService codexService;
    private JobService jobService;
    
    public AchievementListener(TycoonPlugin plugin, AchievementService achievementService) {
        this.plugin = plugin;
        this.achievementService = achievementService;
    }
    
    public void setCodexService(CodexService codexService) {
        this.codexService = codexService;
    }
    
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }
    
    // ========== PlayerJoin 소급 체크 ==========
    
    /**
     * 플레이어 조인 시 소급 체크 (레거시 방식)
     * 10틱 지연 후 실행하여 데이터 로드 완료 보장
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 10틱 지연 후 소급 체크 (데이터 로드 대기)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                checkRetroactiveAchievements(player);
            }
        }, 10L);
    }
    
    // ========== PvP 이벤트 ==========
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer == null || killer.equals(victim)) {
            return;
        }
        
        // PvP 킬 업적
        if (!achievementService.isUnlocked(killer, "pvp_first_kill")) {
            achievementService.tryUnlock(killer, "pvp_first_kill");
        }
        
        // 진행형 PvP 업적
        achievementService.addProgress(killer, "pvp_10_kills", 1);
        achievementService.addProgress(killer, "pvp_50_kills", 1);
        achievementService.addProgress(killer, "pvp_100_kills", 1);
    }
    
    // ========== Codex 연동 (외부 호출용) ==========
    
    /**
     * Codex 등록 후 호출 - 도감 업적 진행도 갱신 (내부용)
     * CodexService에서 등록 성공 시 호출해야 함
     */
    public void onCodexRegistered(Player player) {
        if (codexService == null) return;
        
        int collected = codexService.getCollectedCount(player);
        onCodexRegister(player, collected);
    }
    
    /**
     * Codex 등록 시 호출 (레거시 호환 - CodexService에서 직접 호출)
     * @param player 플레이어
     * @param newCount 도감 등록 후 총 개수
     */
    public void onCodexRegister(Player player, int newCount) {
        // 진행형 업적 갱신
        achievementService.setProgress(player, "codex_10", newCount);
        achievementService.setProgress(player, "codex_25", newCount);
        achievementService.setProgress(player, "codex_50", newCount);
        achievementService.setProgress(player, "codex_100", newCount);
    }
    
    // ========== Job 연동 (외부 호출용) ==========
    
    /**
     * 직업 선택 후 호출
     */
    public void onJobSelected(Player player, JobType jobType) {
        achievementService.tryUnlock(player, "first_job");
    }
    
    /**
     * 직업 레벨업 후 호출
     */
    public void onJobLevelUp(Player player, JobType jobType, int newLevel) {
        // 레벨 10 업적
        achievementService.setProgress(player, "job_level_10", newLevel);
        
        // 레벨 25 업적
        achievementService.setProgress(player, "job_level_25", newLevel);
        
        // 만렙 체크 (50 가정)
        if (newLevel >= 50) {
            achievementService.tryUnlock(player, "job_max_level");
        }
    }
    
    /**
     * 직업 승급 후 호출
     */
    public void onJobGradeUp(Player player, JobType jobType) {
        switch (jobType) {
            case MINER -> achievementService.tryUnlock(player, "miner_grade_up");
            case FARMER -> achievementService.tryUnlock(player, "farmer_grade_up");
            case FISHER -> achievementService.tryUnlock(player, "fisher_grade_up");
            default -> {} // Tier 2 직업 등 다른 직업은 현재 승급 업적 없음
        }
    }
    
    // ========== 주기적 체크 (TycoonPlugin에서 호출) ==========
    
    /**
     * 플레이어 로그인 시 소급 체크
     */
    public void checkRetroactiveAchievements(Player player) {
        // Codex 진행도 체크
        if (codexService != null) {
            onCodexRegistered(player);
        }
        
        // Job 레벨 체크
        if (jobService != null) {
            JobType currentJob = jobService.getTier1Job(player);
            if (currentJob != null) {
                int level = jobService.getLevel(player, currentJob);
                onJobLevelUp(player, currentJob, level);
            }
        }
    }
}
