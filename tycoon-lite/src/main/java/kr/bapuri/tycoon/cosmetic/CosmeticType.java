package kr.bapuri.tycoon.cosmetic;

/**
 * 치장 아이템 타입
 */
public enum CosmeticType {
    
    CHAT_COLOR("chat_color", "채팅 색상"),
    PARTICLE("particle", "파티클"),
    GLOW("glow", "발광 효과");
    
    private final String id;
    private final String displayName;
    
    CosmeticType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public static CosmeticType fromId(String id) {
        for (CosmeticType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
