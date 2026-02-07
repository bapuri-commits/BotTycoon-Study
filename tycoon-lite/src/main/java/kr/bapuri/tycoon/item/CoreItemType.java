package kr.bapuri.tycoon.item;

/**
 * CoreItemType - 핵심 아이템 유형
 * 
 * 핵심 아이템 특성:
 * - PDC 기반 식별 (core_item_type)
 * - 바닐라 기능 차단 대상
 * - Phase 5+에서 각 타입별 핸들러 구현
 * 
 * 레거시 호환:
 * - configKey가 레거시와 동일해야 기존 아이템 인식 가능
 */
public enum CoreItemType {
    
    // ========== PROTECTION (보호권) ==========
    // [Phase 8 정리] 강화 보호권은 ProtectionScrollFactory로 대체됨
    
    /**
     * 범용 인벤토리 보호권
     * - 마을, 야생에서 유효
     * - 사망 시 인벤토리 보호
     */
    UNIVERSAL_INVENTORY_SAVE("universal_inventory_save", "범용 인벤토리 보호권", Category.PROTECTION, true),
    
    // ========== SCROLL (주문서) ==========
    
    /**
     * 귀환 주문서
     * - 마을 스폰으로 텔레포트
     */
    RETURN_SCROLL("return_scroll_v2", "귀환 주문서", Category.SCROLL, true),
    
    /**
     * 텔레포트 주문서
     * - 좌표 입력 후 이동
     */
    TELEPORT_SCROLL("teleport_scroll", "텔레포트 주문서", Category.SCROLL, true),
    
    /**
     * 전생의 기억 주문서
     * - 마지막 사망 위치로 텔레포트
     */
    REBIRTH_MEMORY_SCROLL("rebirth_memory_scroll", "전생의 기억 주문서", Category.SCROLL, true),
    
    // ========== VOUCHER (바우처) ==========
    
    /**
     * BD 수표
     * - 개인간 BD 거래용 물리적 화폐
     */
    BD_CHECK("bd_check", "수표", Category.VOUCHER, false),
    
    /**
     * BottCoin 바우처
     * - 개인간 BottCoin 거래용
     */
    BOTTCOIN_VOUCHER("bottcoin_voucher", "BottCoin 바우처", Category.VOUCHER, false),
    
    // ========== LOTTERY (뽑기권) ==========
    
    /**
     * 기본 인챈트 뽑기권
     * - 바닐라 인챈트북 랜덤 획득
     */
    BASIC_ENCHANT_LOTTERY("basic_enchant_lottery", "기본 인챈트 뽑기권", Category.LOTTERY, true),
    
    /**
     * 특수 인챈트 뽑기권
     * - 커스텀 인챈트북 랜덤 획득
     */
    SPECIAL_ENCHANT_LOTTERY("special_enchant_lottery", "특수 인챈트 뽑기권", Category.LOTTERY, true),
    
    // ========== LAMP_TICKET (램프 티켓) ==========
    
    /**
     * 램프 슬롯 확장권
     * - 아이템의 램프 슬롯 1개 추가 (최대 4슬롯)
     * - 비용: 슬롯별 상이 (25K -> 75K -> 250K)
     */
    LAMP_SLOT_TICKET("lamp_slot_ticket", "램프 슬롯 확장권", Category.LAMP_TICKET, true);
    
    // ========== 필드 ==========
    
    private final String configKey;
    private final String displayName;
    private final Category category;
    private final boolean stackable;
    
    /**
     * 현재 아이템 버전 (마이그레이션용)
     */
    public static final int CURRENT_VERSION = 1;
    
    CoreItemType(String configKey, String displayName, Category category, boolean stackable) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.category = category;
        this.stackable = stackable;
    }
    
    public String getConfigKey() {
        return configKey;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public Category getCategory() {
        return category;
    }
    
    /**
     * 스택 가능 여부
     */
    public boolean isStackable() {
        return stackable;
    }
    
    public boolean isProtection() {
        return category == Category.PROTECTION;
    }
    
    public boolean isScroll() {
        return category == Category.SCROLL;
    }
    
    public boolean isVoucher() {
        return category == Category.VOUCHER;
    }
    
    public boolean isLottery() {
        return category == Category.LOTTERY;
    }
    
    /**
     * [Phase 8] 램프 티켓인지 확인
     */
    public boolean isLampTicket() {
        return category == Category.LAMP_TICKET;
    }
    
    /**
     * configKey로 타입 찾기
     */
    public static CoreItemType fromConfigKey(String key) {
        if (key == null) return null;
        for (CoreItemType type : values()) {
            if (type.configKey.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Core Item 카테고리
     * [Phase 8 정리] DOCUMENT, TICKET 제거 (미사용)
     */
    public enum Category {
        PROTECTION("보호권"),
        SCROLL("주문서"),
        VOUCHER("바우처"),
        LOTTERY("뽑기권"),
        LAMP_TICKET("램프 티켓");
        
        private final String displayName;
        
        Category(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
