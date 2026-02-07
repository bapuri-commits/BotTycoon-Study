package kr.bapuri.tycoon.achievement;

/**
 * AchievementType - 업적 카테고리
 * 
 * Phase 5: DUNGEON 제거 (던전 시스템 미포함)
 */
public enum AchievementType {
    CODEX("도감", "§a"),      // 도감 관련
    JOB("직업", "§e"),        // 직업 관련
    PVP("PvP", "§4"),         // PvP 관련
    VANILLA("마인크래프트", "§b"); // 마인크래프트 바닐라 업적
    
    private final String displayName;
    private final String color;
    
    AchievementType(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
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
}
