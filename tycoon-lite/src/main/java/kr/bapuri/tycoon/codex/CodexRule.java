package kr.bapuri.tycoon.codex;

import org.bukkit.Material;

/**
 * CodexRule - 도감 아이템 규칙 정의
 * 
 * 각 아이템의 도감 등록 조건과 이름 정보를 담고 있습니다.
 */
public class CodexRule {

    private final Material material;
    private final String category;
    private final int requiredCount;
    private final boolean consumeOnRegister;
    private final String displayName;        // 내부 식별용 (영문 Material 이름)
    private final String koreanDisplayName;  // 플레이어에게 표시할 한글 이름
    private final Long rewardOverride;       // 카테고리별 보상 오버라이드 (null = 기본값)

    /**
     * 기존 생성자 (하위 호환성 유지)
     */
    public CodexRule(Material material, String category, int requiredCount, 
                     boolean consumeOnRegister, String displayName) {
        this(material, category, requiredCount, consumeOnRegister, displayName, null, null);
    }
    
    /**
     * 한글 이름을 포함하는 생성자
     */
    public CodexRule(Material material, String category, int requiredCount, 
                     boolean consumeOnRegister, String displayName, String koreanDisplayName) {
        this(material, category, requiredCount, consumeOnRegister, displayName, koreanDisplayName, null);
    }
    
    /**
     * 전체 생성자 (rewardOverride 포함)
     */
    public CodexRule(Material material, String category, int requiredCount, 
                     boolean consumeOnRegister, String displayName, 
                     String koreanDisplayName, Long rewardOverride) {
        this.material = material;
        this.category = category;
        this.requiredCount = requiredCount;
        this.consumeOnRegister = consumeOnRegister;
        this.displayName = displayName;
        this.koreanDisplayName = (koreanDisplayName != null && !koreanDisplayName.isBlank()) 
                                  ? koreanDisplayName 
                                  : displayName;
        this.rewardOverride = rewardOverride;
    }

    public Material getMaterial() { return material; }
    public String getCategory() { return category; }
    public int getRequiredCount() { return requiredCount; }
    public boolean isConsumeOnRegister() { return consumeOnRegister; }
    
    /** 내부 식별용 영문 이름 */
    public String getDisplayName() { return displayName; }
    
    /** 플레이어에게 표시할 한글 이름 */
    public String getKoreanDisplayName() { return koreanDisplayName; }
    
    /** 
     * 카테고리별 보상 오버라이드 
     * @return null이면 기본값, 값이 있으면 해당 보상 사용
     */
    public Long getRewardOverride() { return rewardOverride; }
    
    /**
     * 보상 오버라이드가 있는지 확인
     */
    public boolean hasRewardOverride() { return rewardOverride != null; }
}
