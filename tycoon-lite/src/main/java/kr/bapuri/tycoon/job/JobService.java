package kr.bapuri.tycoon.job;

import kr.bapuri.tycoon.TycoonPlugin;
import kr.bapuri.tycoon.job.common.AbstractJobExpService;
import kr.bapuri.tycoon.job.common.AbstractJobGradeService;
import kr.bapuri.tycoon.job.common.UnlockCondition;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * JobService - 직업 시스템 통합 서비스
 * 
 * 역할:
 * - 직업 선택/해금
 * - 직업 정보 조회
 * - 직업 변경
 * - 하위 서비스 위임 (ExpService, GradeService)
 * 
 * 클라이언트 코드는 이 서비스를 통해 직업 시스템에 접근
 */
public class JobService {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final PlayerDataManager dataManager;
    private final JobsConfigLoader configLoader;
    private final JobRegistry registry;
    
    public JobService(JavaPlugin plugin, 
                      PlayerDataManager dataManager,
                      JobsConfigLoader configLoader,
                      JobRegistry registry) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataManager = dataManager;
        this.configLoader = configLoader;
        this.registry = registry;
    }
    
    // ===== 직업 선택/해금 =====
    
    /**
     * 직업 선택 결과
     */
    public record JobSelectResult(
        boolean success,
        JobType jobType,
        String message
    ) {
        public static JobSelectResult success(JobType type) {
            return new JobSelectResult(true, type, null);
        }
        
        public static JobSelectResult failure(String reason) {
            return new JobSelectResult(false, null, reason);
        }
    }
    
    /**
     * Tier 1 직업 선택
     */
    public JobSelectResult selectTier1Job(Player player, JobType jobType) {
        if (player == null) {
            return JobSelectResult.failure("§c플레이어가 유효하지 않습니다.");
        }
        
        if (!jobType.isTier1()) {
            return JobSelectResult.failure("§c해당 직업은 Tier 1이 아닙니다.");
        }
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) {
            return JobSelectResult.failure("§c플레이어 데이터를 찾을 수 없습니다.");
        }
        
        // 이미 Tier 1 직업이 있는지 확인
        JobType currentTier1 = registry.getCurrentTier1Job(data);
        if (currentTier1 != null) {
            return JobSelectResult.failure(
                    String.format("§c이미 Tier 1 직업(%s)이 있습니다.", currentTier1.getDisplayName()));
        }
        
        // 해금 조건 확인
        String failReason = registry.getUnlockFailureReason(player, jobType);
        if (failReason != null) {
            return JobSelectResult.failure(failReason);
        }
        
        // 직업 부여
        // [Phase 8 버그수정] try-catch 추가 - Internal Error 추적용
        try {
            grantJob(data, jobType);
            data.markDirty();
        } catch (Exception e) {
            logger.severe(String.format("[JobService] 직업 부여 중 오류: player=%s, job=%s, error=%s",
                    player.getName(), jobType.getConfigKey(), e.getMessage()));
            e.printStackTrace();
            return JobSelectResult.failure("§c직업 부여 중 오류가 발생했습니다. 관리자에게 문의하세요.");
        }
        
        logger.info(String.format("[JobService] %s - Tier 1 직업 선택: %s", 
                player.getName(), jobType.getConfigKey()));
        
        // 업적 트리거 (버그수정: 첫 직업 업적 연동)
        TycoonPlugin tycoonPlugin = TycoonPlugin.getInstance();
        if (tycoonPlugin != null && tycoonPlugin.getAchievementListener() != null) {
            tycoonPlugin.getAchievementListener().onJobSelected(player, jobType);
        }
        
        return JobSelectResult.success(jobType);
    }
    
    /**
     * Tier 2 직업 선택 (Tier 2 활성화 시에만 동작)
     */
    public JobSelectResult selectTier2Job(Player player, JobType jobType) {
        if (!configLoader.isTier2JobsEnabled()) {
            return JobSelectResult.failure("§cTier 2 직업 시스템이 비활성화되어 있습니다.");
        }
        
        if (player == null) {
            return JobSelectResult.failure("§c플레이어가 유효하지 않습니다.");
        }
        
        if (!jobType.isTier2()) {
            return JobSelectResult.failure("§c해당 직업은 Tier 2가 아닙니다.");
        }
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) {
            return JobSelectResult.failure("§c플레이어 데이터를 찾을 수 없습니다.");
        }
        
        // Tier 1 직업이 있어야 함
        JobType currentTier1 = registry.getCurrentTier1Job(data);
        if (currentTier1 == null) {
            return JobSelectResult.failure("§cTier 1 직업이 없습니다. 먼저 Tier 1 직업을 선택하세요.");
        }
        
        // 이미 Tier 2 직업이 있는지 확인
        JobType currentTier2 = registry.getCurrentTier2Job(data);
        if (currentTier2 != null) {
            return JobSelectResult.failure(
                    String.format("§c이미 Tier 2 직업(%s)이 있습니다.", currentTier2.getDisplayName()));
        }
        
        // 해금 조건 확인
        String failReason = registry.getUnlockFailureReason(player, jobType);
        if (failReason != null) {
            return JobSelectResult.failure(failReason);
        }
        
        // 직업 부여
        grantJob(data, jobType);
        data.markDirty();
        
        logger.info(String.format("[JobService] %s - Tier 2 직업 선택: %s", 
                player.getName(), jobType.getConfigKey()));
        
        return JobSelectResult.success(jobType);
    }
    
    // ===== 직업 정보 조회 =====
    
    /**
     * 플레이어가 특정 직업을 가지고 있는지
     */
    public boolean hasJob(Player player, JobType jobType) {
        if (player == null) return false;
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return false;
        return data.hasJob(jobType);
    }
    
    /**
     * 플레이어가 직업을 가지고 있는지 (아무 직업이나)
     */
    public boolean hasAnyJob(Player player) {
        if (player == null) return false;
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return false;
        
        for (JobType type : registry.getRegisteredJobTypes()) {
            if (data.hasJob(type)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 플레이어가 Tier 1 직업을 가지고 있는지
     */
    public boolean hasTier1Job(Player player) {
        if (player == null) return false;
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        return registry.getCurrentTier1Job(data) != null;
    }
    
    /**
     * 플레이어의 Tier 1 직업 반환
     */
    public JobType getTier1Job(Player player) {
        if (player == null) return null;
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        return registry.getCurrentTier1Job(data);
    }
    
    /**
     * 플레이어의 Tier 2 직업 반환
     */
    public JobType getTier2Job(Player player) {
        if (player == null) return null;
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        return registry.getCurrentTier2Job(data);
    }
    
    // ===== 직업 레벨/경험치 =====
    
    /**
     * 경험치 추가
     */
    public int addExp(Player player, JobType jobType, long amount) {
        AbstractJobExpService expService = registry.getExpService(jobType);
        if (expService == null) return 0;
        return expService.addExp(player, amount);
    }
    
    /**
     * 레벨 조회
     */
    public int getLevel(Player player, JobType jobType) {
        AbstractJobExpService expService = registry.getExpService(jobType);
        if (expService == null) return 1;
        return expService.getLevel(player);
    }
    
    /**
     * 경험치 조회
     */
    public long getExp(Player player, JobType jobType) {
        AbstractJobExpService expService = registry.getExpService(jobType);
        if (expService == null) return 0;
        return expService.getExp(player);
    }
    
    // ===== 직업 등급 =====
    
    /**
     * 등급 조회
     */
    public JobGrade getGrade(Player player, JobType jobType) {
        AbstractJobGradeService gradeService = registry.getGradeService(jobType);
        if (gradeService == null) return JobGrade.GRADE_1;
        return gradeService.getGrade(player);
    }
    
    /**
     * 승급 시도
     */
    public AbstractJobGradeService.PromotionResult promote(Player player, JobType jobType) {
        AbstractJobGradeService gradeService = registry.getGradeService(jobType);
        if (gradeService == null) {
            return AbstractJobGradeService.PromotionResult.failure("§c해당 직업의 등급 서비스를 찾을 수 없습니다.");
        }
        
        AbstractJobGradeService.PromotionResult result = gradeService.promote(player);
        
        // 업적 트리거 (버그수정: 직업 승급 업적 연동)
        if (result.success()) {
            TycoonPlugin tycoonPlugin = TycoonPlugin.getInstance();
            if (tycoonPlugin != null && tycoonPlugin.getAchievementListener() != null) {
                tycoonPlugin.getAchievementListener().onJobGradeUp(player, jobType);
            }
        }
        
        return result;
    }
    
    // ===== 직업 정보 문자열 =====
    
    /**
     * 직업 요약 정보
     * [Fix] 경험치 정보 추가 (현재 XP, 다음 레벨까지 필요 XP, 진행률)
     */
    public String getJobSummary(Player player) {
        if (player == null) return "";
        
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== 직업 정보 ===§r\n");
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) {
            sb.append("§c데이터를 찾을 수 없습니다.");
            return sb.toString();
        }
        
        // Tier 1 직업
        JobType tier1 = registry.getCurrentTier1Job(data);
        if (tier1 != null) {
            AbstractJobExpService expService = registry.getExpService(tier1);
            AbstractJobGradeService gradeService = registry.getGradeService(tier1);
            
            sb.append(String.format("§e[Tier 1] %s§r\n", tier1.getDisplayName()));
            
            if (expService != null) {
                // [Fix] getInfoString()을 사용하여 레벨 + 경험치 정보 표시
                sb.append(String.format("  %s\n", expService.getInfoString(player)));
            }
            if (gradeService != null) {
                sb.append(String.format("  §7등급: %s\n", gradeService.getGrade(player).getDisplayName()));
            }
        } else {
            sb.append("§7[Tier 1] 직업 없음\n");
        }
        
        // Tier 2 직업 (활성화 시에만)
        if (configLoader.isTier2JobsEnabled()) {
            JobType tier2 = registry.getCurrentTier2Job(data);
            if (tier2 != null) {
                AbstractJobExpService expService = registry.getExpService(tier2);
                AbstractJobGradeService gradeService = registry.getGradeService(tier2);
                
                sb.append(String.format("§e[Tier 2] %s§r\n", tier2.getDisplayName()));
                
                if (expService != null) {
                    // [Fix] getInfoString()을 사용하여 레벨 + 경험치 정보 표시
                    sb.append(String.format("  %s\n", expService.getInfoString(player)));
                }
                if (gradeService != null) {
                    sb.append(String.format("  §7등급: %s\n", gradeService.getGrade(player).getDisplayName()));
                }
            } else {
                sb.append("§7[Tier 2] 직업 없음\n");
            }
        }
        
        return sb.toString();
    }
    
    // ===== 관리자 기능 =====
    
    /**
     * 직업 강제 부여 (관리자용)
     */
    public boolean grantJobAdmin(UUID uuid, JobType jobType) {
        PlayerTycoonData data = dataManager.get(uuid);
        if (data == null) return false;
        
        // [Phase 8 버그수정] try-catch 추가 - 오류 추적용
        try {
            grantJob(data, jobType);
            data.markDirty();
            return true;
        } catch (Exception e) {
            logger.severe(String.format("[JobService] 관리자 직업 부여 중 오류: uuid=%s, job=%s, error=%s",
                    uuid, jobType.getConfigKey(), e.getMessage()));
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 직업 제거 (관리자용)
     */
    public boolean removeJobAdmin(UUID uuid, JobType jobType) {
        PlayerTycoonData data = dataManager.get(uuid);
        if (data == null) return false;
        
        // Tier 1/2에 따라 적절한 clear 호출
        if (jobType.isTier1()) {
            data.clearTier1Job();
        } else {
            data.clearTier2Job();
        }
        data.markDirty();
        return true;
    }
    
    /**
     * 레벨 설정 (관리자용)
     */
    public boolean setLevelAdmin(UUID uuid, JobType jobType, int level) {
        AbstractJobExpService expService = registry.getExpService(jobType);
        if (expService == null) return false;
        return expService.setLevel(uuid, level);
    }
    
    /**
     * 경험치 설정 (관리자용)
     */
    public boolean setExpAdmin(UUID uuid, JobType jobType, long exp) {
        AbstractJobExpService expService = registry.getExpService(jobType);
        if (expService == null) return false;
        return expService.setExp(uuid, exp);
    }
    
    // ===== 내부 메서드 =====
    
    /**
     * 직업 부여 (내부)
     */
    private void grantJob(PlayerTycoonData data, JobType jobType) {
        // Tier 1/2에 따라 적절한 setter 호출
        if (jobType.isTier1()) {
            data.setTier1Job(jobType);
        } else {
            data.setTier2Job(jobType);
        }
        // 레벨, 경험치, 등급 초기화 (setTier1Job/setTier2Job 내부에서 처리되지만 명시적으로 호출)
        data.setJobLevel(jobType, 1);
        data.setJobExp(jobType, 0);
        setJobGrade(data, jobType, 1);
    }
    
    /**
     * 직업 등급 설정 (내부)
     */
    private void setJobGrade(PlayerTycoonData data, JobType jobType, int grade) {
        switch (jobType) {
            case MINER -> data.setMinerGrade(grade);
            case FARMER -> data.setFarmerGrade(grade);
            case FISHER -> data.setFisherGrade(grade);
            case ARTISAN -> data.setArtisanGrade(grade);
            case CHEF -> data.setChefGrade(grade);
            case HERBALIST -> data.setHerbalistGrade(grade);
            case ENGINEER -> data.setEngineerGrade(grade);
        }
    }
    
    // ===== 설정 리로드 =====
    
    /**
     * 설정 리로드
     */
    public void reload() {
        registry.reload();
        logger.info("[JobService] 리로드 완료");
    }
    
    // ===== Getter =====
    
    public JobsConfigLoader getConfigLoader() {
        return configLoader;
    }
    
    public JobRegistry getRegistry() {
        return registry;
    }
}
