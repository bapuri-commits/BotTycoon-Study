package kr.bapuri.tycoon.job.farmer;

/**
 * 작물 그룹 (LOCKED)
 * 
 * 승급 조건은 그룹별 누적 판매액으로 판정
 * 단일 판매·우연적 판매는 인정하지 않음
 * 
 * 참고: 사과는 별도 취급으로 승급 판정 대상에서 제외
 */
public enum CropGroup {
    GROUP_A("기본 작물", "바닐라 판매 가능 작물", "A"),
    GROUP_B("주식/대량", "쌀, 밀, 감자 등 대량 생산 작물", "B"),
    GROUP_C("채소/과채", "배추, 양배추, 당근 등 채소류", "C"),
    GROUP_D("과일/베리", "포도, 체리, 블루베리 등 과일류", "D"),
    GROUP_E("고급/특수", "고급 요리/약재 원료", "E");

    private final String displayName;
    private final String description;
    private final String shortCode;

    CropGroup(String displayName, String description, String shortCode) {
        this.displayName = displayName;
        this.description = description;
        this.shortCode = shortCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getShortCode() {
        return shortCode;
    }

    public static CropGroup fromShortCode(String code) {
        for (CropGroup group : values()) {
            if (group.shortCode.equalsIgnoreCase(code)) {
                return group;
            }
        }
        return null;
    }

    public static CropGroup fromName(String name) {
        for (CropGroup group : values()) {
            if (group.name().equalsIgnoreCase(name)) {
                return group;
            }
        }
        return null;
    }
}
