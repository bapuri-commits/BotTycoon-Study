package kr.bapuri.tycoon.achievement;

/**
 * AchievementTier - 업적 등급
 */
public enum AchievementTier {
    NORMAL("일반", "§f", 1),
    RARE("희귀", "§b", 5),
    EPIC("영웅", "§d", 15),
    LEGENDARY("전설", "§6", 25);
    
    private final String displayName;
    private final String color;
    private final long defaultBottCoinReward;
    
    AchievementTier(String displayName, String color, long defaultBottCoinReward) {
        this.displayName = displayName;
        this.color = color;
        this.defaultBottCoinReward = defaultBottCoinReward;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getColor() {
        return color;
    }
    
    public String getColoredName() {
        return color + displayName;
    }
    
    public long getDefaultBottCoinReward() {
        return defaultBottCoinReward;
    }
}
