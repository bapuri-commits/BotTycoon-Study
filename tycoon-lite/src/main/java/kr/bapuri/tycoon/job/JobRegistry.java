package kr.bapuri.tycoon.job;

import kr.bapuri.tycoon.job.common.AbstractJobExpService;
import kr.bapuri.tycoon.job.common.AbstractJobGradeService;
import kr.bapuri.tycoon.job.common.UnlockCondition;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * JobRegistry - 직업 서비스 레지스트리
 * 
 * 역할:
 * - 직업별 ExpService/GradeService 등록 및 조회
 * - 해금 조건 관리
 * - 중앙화된 직업 서비스 접근
 */
public class JobRegistry {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final JobsConfigLoader configLoader;
    
    // 서비스 레지스트리
    private final Map<JobType, AbstractJobExpService> expServices = new EnumMap<>(JobType.class);
    private final Map<JobType, AbstractJobGradeService> gradeServices = new EnumMap<>(JobType.class);
    
    // 해금 조건 캐시
    private final Map<JobType, List<UnlockCondition>> unlockConditions = new EnumMap<>(JobType.class);
    
    public JobRegistry(JavaPlugin plugin, JobsConfigLoader configLoader) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configLoader = configLoader;
    }
    
    // ===== 서비스 등록 =====
    
    /**
     * ExpService 등록
     */
    public void registerExpService(JobType jobType, AbstractJobExpService service) {
        expServices.put(jobType, service);
        logger.info("[JobRegistry] ExpService 등록: " + jobType.getConfigKey());
    }
    
    /**
     * GradeService 등록
     */
    public void registerGradeService(JobType jobType, AbstractJobGradeService service) {
        gradeServices.put(jobType, service);
        logger.info("[JobRegistry] GradeService 등록: " + jobType.getConfigKey());
    }
    
    // ===== 서비스 조회 =====
    
    /**
     * ExpService 조회
     */
    public AbstractJobExpService getExpService(JobType jobType) {
        return expServices.get(jobType);
    }
    
    /**
     * GradeService 조회
     */
    public AbstractJobGradeService getGradeService(JobType jobType) {
        return gradeServices.get(jobType);
    }
    
    /**
     * 등록된 모든 JobType
     */
    public Set<JobType> getRegisteredJobTypes() {
        return Collections.unmodifiableSet(expServices.keySet());
    }
    
    /**
     * Tier 1 직업만 조회
     */
    public Set<JobType> getTier1JobTypes() {
        Set<JobType> tier1 = EnumSet.noneOf(JobType.class);
        for (JobType type : expServices.keySet()) {
            if (type.isTier1()) {
                tier1.add(type);
            }
        }
        return tier1;
    }
    
    /**
     * Tier 2 직업만 조회
     */
    public Set<JobType> getTier2JobTypes() {
        Set<JobType> tier2 = EnumSet.noneOf(JobType.class);
        for (JobType type : expServices.keySet()) {
            if (type.isTier2()) {
                tier2.add(type);
            }
        }
        return tier2;
    }
    
    // ===== 해금 조건 =====
    
    /**
     * 해금 조건 로드 (configLoader에서)
     */
    public void loadUnlockConditions() {
        unlockConditions.clear();
        
        for (JobType jobType : JobType.values()) {
            List<UnlockCondition> conditions = configLoader.getUnlockConditions(jobType);
            unlockConditions.put(jobType, conditions);
        }
        
        logger.info("[JobRegistry] 해금 조건 로드 완료");
    }
    
    /**
     * 해금 조건 조회
     */
    public List<UnlockCondition> getUnlockConditions(JobType jobType) {
        return unlockConditions.getOrDefault(jobType, Collections.emptyList());
    }
    
    /**
     * 해금 가능 여부 확인
     */
    public boolean canUnlock(Player player, JobType jobType) {
        List<UnlockCondition> conditions = getUnlockConditions(jobType);
        for (UnlockCondition condition : conditions) {
            if (!condition.check(player)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 해금 실패 사유 조회
     */
    public String getUnlockFailureReason(Player player, JobType jobType) {
        List<UnlockCondition> conditions = getUnlockConditions(jobType);
        for (UnlockCondition condition : conditions) {
            if (!condition.check(player)) {
                return condition.getFailureMessage(player);
            }
        }
        return null;
    }
    
    /**
     * 해금 조건 설명 (전체)
     */
    public String getUnlockRequirementsString(Player player, JobType jobType) {
        List<UnlockCondition> conditions = getUnlockConditions(jobType);
        if (conditions.isEmpty()) {
            return "§a해금 조건이 없습니다.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§6[%s 해금 조건]§r\n", jobType.getDisplayName()));
        
        for (UnlockCondition condition : conditions) {
            boolean met = condition.check(player);
            String status = met ? "§a✓" : "§c✗";
            sb.append(String.format("%s %s\n", status, condition.getDescription()));
        }
        
        return sb.toString();
    }
    
    // ===== 직업 상태 조회 =====
    
    /**
     * 플레이어의 현재 Tier 1 직업
     */
    public JobType getCurrentTier1Job(PlayerTycoonData data) {
        if (data == null) return null;
        
        for (JobType type : getTier1JobTypes()) {
            if (data.hasJob(type)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * 플레이어의 현재 Tier 2 직업
     */
    public JobType getCurrentTier2Job(PlayerTycoonData data) {
        if (data == null) return null;
        
        for (JobType type : getTier2JobTypes()) {
            if (data.hasJob(type)) {
                return type;
            }
        }
        return null;
    }
    
    // ===== 설정 리로드 =====
    
    /**
     * 전체 리로드
     */
    public void reload() {
        loadUnlockConditions();
        
        // 등록된 서비스들 리로드
        for (AbstractJobExpService service : expServices.values()) {
            service.reloadConfig();
        }
        for (AbstractJobGradeService service : gradeServices.values()) {
            service.reloadConfig();
        }
        
        logger.info("[JobRegistry] 리로드 완료");
    }
}
