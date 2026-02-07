package kr.bapuri.tycoon.achievement;

/**
 * Achievement - 업적 정의 클래스
 */
public class Achievement {
    
    private final String id;                 // 고유 ID
    private final String name;               // 표시 이름
    private final String description;        // 설명
    private final AchievementType type;      // 카테고리
    private final AchievementTier tier;      // 등급
    private final int targetValue;           // 달성 목표값 (0 = 즉시 달성형)
    private final long bottCoinReward;       // BottCoin 보상
    private final String titleReward;        // 칭호 보상 (LuckPerms 그룹명, null = 없음)
    
    public Achievement(String id, String name, String description, 
                       AchievementType type, AchievementTier tier, 
                       int targetValue, long bottCoinReward, String titleReward) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.tier = tier;
        this.targetValue = targetValue;
        this.bottCoinReward = bottCoinReward;
        this.titleReward = titleReward;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public AchievementType getType() {
        return type;
    }
    
    public AchievementTier getTier() {
        return tier;
    }
    
    public int getTargetValue() {
        return targetValue;
    }
    
    public long getBottCoinReward() {
        return bottCoinReward;
    }
    
    public String getTitleReward() {
        return titleReward;
    }
    
    public boolean hasTitleReward() {
        return titleReward != null && !titleReward.isEmpty();
    }
    
    /**
     * 진행형 업적인지 확인 (targetValue > 0)
     */
    public boolean isProgressive() {
        return targetValue > 0;
    }
    
    /**
     * 색상이 적용된 이름 반환
     */
    public String getColoredName() {
        return tier.getColor() + name;
    }
}
