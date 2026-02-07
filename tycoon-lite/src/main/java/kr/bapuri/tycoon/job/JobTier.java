package kr.bapuri.tycoon.job;

/**
 * JobTier - 직업 티어
 * 
 * TIER_1: 기본 직업 (바닐라 기반) - Farmer, Miner, Fisher
 * TIER_2: 상위 직업 (커스텀 기반) - Chef, Artisan, Herbalist
 * 
 * 플레이어는 최대 2개 직업 보유 가능 (Tier 1 하나 + Tier 2 하나)
 */
public enum JobTier {
    
    TIER_1(1, "기본 직업", "바닐라 콘텐츠 기반"),
    TIER_2(2, "상위 직업", "커스텀 콘텐츠 기반");

    private final int level;
    private final String displayName;
    private final String description;

    JobTier(int level, String displayName, String description) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 숫자로 티어 찾기
     */
    public static JobTier fromLevel(int level) {
        for (JobTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return null;
    }

    /**
     * config key (예: "1", "2")로 티어 찾기
     */
    public static JobTier fromConfigKey(String key) {
        try {
            int level = Integer.parseInt(key);
            return fromLevel(level);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

