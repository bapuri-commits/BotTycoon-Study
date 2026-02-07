package kr.bapuri.tycoon.economy;

/**
 * CurrencyType - 화폐 타입 정의
 * 
 * BD: 기본 화폐 (상점, 던전 보상, 일반 게임플레이)
 * BOTTCOIN: 특수 화폐 (도감 완성, 던전 보스, 업적 달성 보상)
 */
public enum CurrencyType {
    
    BD("BD", "§e", "원", "기본 화폐"),
    BOTTCOIN("BottCoin", "§d", "BC", "특수 화폐");

    private final String displayName;
    private final String color;
    private final String suffix;
    private final String description;

    CurrencyType(String displayName, String color, String suffix, String description) {
        this.displayName = displayName;
        this.color = color;
        this.suffix = suffix;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getSuffix() {
        return suffix;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 금액을 포맷팅된 문자열로 반환
     * 예: "§e1,000원" 또는 "§d500BC"
     */
    public String format(long amount) {
        return color + String.format("%,d", amount) + suffix;
    }

    /**
     * 색상 없는 포맷팅
     * 예: "1,000원" 또는 "500BC"
     */
    public String formatPlain(long amount) {
        return String.format("%,d", amount) + suffix;
    }

    /**
     * ID로 CurrencyType 찾기
     */
    public static CurrencyType fromId(String id) {
        if (id == null) return null;
        for (CurrencyType type : values()) {
            if (type.name().equalsIgnoreCase(id) || 
                type.displayName.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}


