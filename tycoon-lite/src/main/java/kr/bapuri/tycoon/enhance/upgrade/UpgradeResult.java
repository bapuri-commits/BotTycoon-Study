package kr.bapuri.tycoon.enhance.upgrade;

/**
 * UpgradeResult - 강화 결과
 * 
 * Phase 6 LITE: 레거시 버전 이식
 */
public enum UpgradeResult {
    
    SUCCESS("§a§l성공!", "강화 레벨이 상승했습니다!"),
    DOWNGRADE("§e§l하락!", "강화 레벨이 1 감소했습니다."),
    MAINTAIN("§7§l유지", "강화 레벨이 유지되었습니다."),
    DESTROY("§c§l파괴!", "아이템이 파괴되었습니다!");

    private final String title;
    private final String description;

    UpgradeResult(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    public boolean isDestroy() {
        return this == DESTROY;
    }
}
