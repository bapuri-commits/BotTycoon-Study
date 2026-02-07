package kr.bapuri.tycoon.job;

/**
 * JobType - 6개 직업 타입
 * 
 * Tier 1 (바닐라 기반):
 * - FARMER: 농부 - 작물 재배/수확
 * - MINER: 광부 - 광석 채굴
 * - FISHER: 어부 - 낚시
 * 
 * Tier 2 (커스텀 기반):
 * - CHEF: 셰프 - 요리 (Farmer/Fisher 필요)
 * - ARTISAN: 장인 - 제작 (Miner 필요)
 * - HERBALIST: 약초사 - 포션/약초 (Farmer 필요)
 */
public enum JobType {
    
    // Tier 1 직업
    FARMER("farmer", "농부", JobTier.TIER_1, "작물을 재배하고 수확합니다."),
    MINER("miner", "광부", JobTier.TIER_1, "광석을 채굴합니다."),
    FISHER("fisher", "어부", JobTier.TIER_1, "물고기를 낚습니다."),
    
    // Tier 2 직업
    CHEF("chef", "셰프", JobTier.TIER_2, "요리를 만들어 판매합니다."),
    ARTISAN("artisan", "장인", JobTier.TIER_2, "도구와 장비를 제작합니다."),
    HERBALIST("herbalist", "약초사", JobTier.TIER_2, "포션과 약초를 조합합니다."),
    ENGINEER("engineer", "공학자", JobTier.TIER_2, "기계와 설비를 설계하고 제작합니다.");

    private final String id;
    private final String displayName;
    private final JobTier tier;
    private final String description;

    JobType(String id, String displayName, JobTier tier, String description) {
        this.id = id;
        this.displayName = displayName;
        this.tier = tier;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public JobTier getTier() {
        return tier;
    }

    public String getDescription() {
        return description;
    }

    /**
     * ID로 JobType 찾기
     */
    public static JobType fromId(String id) {
        if (id == null) return null;
        for (JobType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 특정 티어의 직업 목록
     */
    public static JobType[] getByTier(JobTier tier) {
        return java.util.Arrays.stream(values())
                .filter(job -> job.tier == tier)
                .toArray(JobType[]::new);
    }

    /**
     * Tier 1 직업인지
     */
    public boolean isTier1() {
        return tier == JobTier.TIER_1;
    }

    /**
     * Tier 2 직업인지
     */
    public boolean isTier2() {
        return tier == JobTier.TIER_2;
    }
    
    /**
     * 설정 파일용 키 (id와 동일)
     */
    public String getConfigKey() {
        return id;
    }
    
    /**
     * 최대 레벨 반환
     * Tier 1: 100, Tier 2: 70
     */
    public int getMaxLevel() {
        return isTier1() ? 100 : 70;
    }
}

