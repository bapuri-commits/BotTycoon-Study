package kr.bapuri.tycoon.job;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * JobData - 직업 정의 데이터
 * 
 * config.yml에서 로드되는 직업 설정 데이터
 */
public class JobData {

    // ========== 전역 상수 ==========
    
    /** [Level/Grade 통합] Tier 1 최대 레벨 */
    public static final int MAX_LEVEL_TIER1 = 100;
    
    /** [Level/Grade 통합] Tier 2 최대 레벨 */
    public static final int MAX_LEVEL_TIER2 = 70;
    
    /** [Level/Grade 통합] Tier 1 최대 등급 (1차~4차) */
    public static final int MAX_GRADE_TIER1 = 4;
    
    /** [Level/Grade 통합] Tier 2 최대 등급 (1차~3차) */
    public static final int MAX_GRADE_TIER2 = 3;
    
    /** 최대 경험치 - 오버플로우 방지용 상한 */
    public static final long MAX_EXP = 100_000_000L;
    
    /** @deprecated 하위 호환용 - getMaxLevelFor(JobType) 사용 권장 */
    @Deprecated
    public static final int ABSOLUTE_MAX_LEVEL = MAX_LEVEL_TIER1;

    private final JobType jobType;
    
    // 기본 정보 (JobType에서 가져오거나 config에서 오버라이드)
    private String displayName;
    private String description;
    
    // 레벨 시스템 - [Level/Grade 통합] 직업 타입에 따라 기본값 설정
    private int maxLevel;
    private final Map<Integer, Long> levelExpRequirements = new HashMap<>(); // 레벨 -> 필요 경험치
    
    // 해금 조건
    private int unlockCodexCount = 0;           // 필요 도감 수
    private long unlockMoney = 0;               // 필요 돈
    private Set<JobType> requiredTier1Jobs = new HashSet<>();  // Tier 2용: 필요한 Tier 1 직업

    public JobData(JobType jobType) {
        this.jobType = jobType;
        this.displayName = jobType.getDisplayName();
        this.description = jobType.getDescription();
        
        // [Level/Grade 통합] 직업 타입에 따른 최대 레벨 설정
        this.maxLevel = getMaxLevelFor(jobType);
        
        // 기본 레벨업 경험치 설정 (레거시, JobExpCalculator 사용 권장)
        levelExpRequirements.put(2, 1000L);
        levelExpRequirements.put(3, 5000L);
        levelExpRequirements.put(4, 20000L);
    }

    // ========== Getters ==========

    public JobType getJobType() {
        return jobType;
    }

    public String getId() {
        return jobType.getId();
    }

    public JobTier getTier() {
        return jobType.getTier();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * 특정 레벨로 올리기 위해 필요한 총 경험치
     */
    public long getExpRequiredForLevel(int level) {
        return levelExpRequirements.getOrDefault(level, Long.MAX_VALUE);
    }

    public int getUnlockCodexCount() {
        return unlockCodexCount;
    }

    public long getUnlockMoney() {
        return unlockMoney;
    }

    public Set<JobType> getRequiredTier1Jobs() {
        return requiredTier1Jobs;
    }

    // ========== Setters ==========

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setMaxLevel(int maxLevel) {
        // [Level/Grade 통합] 직업 타입에 맞는 최대값 적용
        int absoluteMax = getMaxLevelFor(this.jobType);
        this.maxLevel = Math.min(Math.max(1, maxLevel), absoluteMax);
    }

    public void setLevelExpRequirement(int level, long exp) {
        levelExpRequirements.put(level, exp);
    }

    public void setUnlockCodexCount(int unlockCodexCount) {
        this.unlockCodexCount = unlockCodexCount;
    }

    public void setUnlockMoney(long unlockMoney) {
        this.unlockMoney = unlockMoney;
    }

    public void setRequiredTier1Jobs(Set<JobType> requiredTier1Jobs) {
        this.requiredTier1Jobs = requiredTier1Jobs;
    }

    public void addRequiredTier1Job(JobType job) {
        if (job != null && job.isTier1()) {
            this.requiredTier1Jobs.add(job);
        }
    }

    // ========== 유틸리티 ==========

    /**
     * 현재 경험치로 달성 가능한 레벨 계산
     * [Level/Grade 통합] 직업 타입에 맞는 최대값 적용
     */
    public int calculateLevel(long currentExp) {
        int level = 1;
        int effectiveMaxLevel = Math.min(maxLevel, getMaxLevelFor(this.jobType));
        
        for (int lvl = 2; lvl <= effectiveMaxLevel; lvl++) {
            if (currentExp >= getExpRequiredForLevel(lvl)) {
                level = lvl;
            } else {
                break;
            }
        }
        return Math.min(level, getMaxLevelFor(this.jobType));
    }
    
    /**
     * 플레이어가 최대 레벨인지 확인
     * [Level/Grade 통합] 직업 타입에 맞는 최대값 적용
     */
    public boolean isMaxLevel(int currentLevel) {
        return currentLevel >= Math.min(maxLevel, getMaxLevelFor(this.jobType));
    }
    
    /**
     * 경험치 값을 안전한 범위로 clamp
     */
    public static long clampExp(long exp) {
        return Math.max(0, Math.min(exp, MAX_EXP));
    }
    
    /**
     * 특정 직업 타입의 최대 레벨 조회
     * [Level/Grade 통합] Tier 1: 100, Tier 2: 70
     */
    public static int getMaxLevelFor(JobType job) {
        if (job == null) return MAX_LEVEL_TIER1;
        return job.isTier1() ? MAX_LEVEL_TIER1 : MAX_LEVEL_TIER2;
    }
    
    /**
     * 특정 직업 타입의 최대 등급 조회
     * [Level/Grade 통합] Tier 1: 4, Tier 2: 3
     */
    public static int getMaxGradeFor(JobType job) {
        if (job == null) return MAX_GRADE_TIER1;
        return job.isTier1() ? MAX_GRADE_TIER1 : MAX_GRADE_TIER2;
    }
    
    /**
     * 레벨 값을 직업 타입에 맞는 범위로 clamp
     * [Level/Grade 통합]
     */
    public static int clampLevel(JobType job, int level) {
        int maxLevel = getMaxLevelFor(job);
        return Math.max(1, Math.min(level, maxLevel));
    }
    
    /**
     * @deprecated 직업 타입 없이 clamp할 경우 Tier 1 기준 적용
     * clampLevel(JobType, int) 사용 권장
     */
    @Deprecated
    public static int clampLevel(int level) {
        return Math.max(1, Math.min(level, MAX_LEVEL_TIER1));
    }

    /**
     * 다음 레벨까지 필요한 경험치
     */
    public long getExpToNextLevel(int currentLevel, long currentExp) {
        if (currentLevel >= maxLevel) {
            return 0; // 이미 최대 레벨
        }
        long required = getExpRequiredForLevel(currentLevel + 1);
        return Math.max(0, required - currentExp);
    }

    /**
     * 현재 레벨에서의 진행률 (0.0 ~ 1.0)
     */
    public double getLevelProgress(int currentLevel, long currentExp) {
        if (currentLevel >= maxLevel) {
            return 1.0;
        }
        
        long prevRequired = currentLevel > 1 ? getExpRequiredForLevel(currentLevel) : 0;
        long nextRequired = getExpRequiredForLevel(currentLevel + 1);
        long range = nextRequired - prevRequired;
        
        if (range <= 0) return 1.0;
        
        long progress = currentExp - prevRequired;
        return Math.min(1.0, Math.max(0.0, (double) progress / range));
    }

    @Override
    public String toString() {
        return "JobData{" +
                "type=" + jobType +
                ", displayName='" + displayName + '\'' +
                ", maxLevel=" + maxLevel +
                '}';
    }
}
