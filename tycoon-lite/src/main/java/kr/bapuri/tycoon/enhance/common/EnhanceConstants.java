package kr.bapuri.tycoon.enhance.common;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * EnhanceConstants - 강화 시스템 공통 상수
 * 
 * Phase 5.E: 구조 준비 단계
 * - NamespacedKey (PDC용)
 * - 색상 코드
 * - 설정 상수
 * - CustomModelData
 */
public final class EnhanceConstants {

    private static JavaPlugin plugin;

    // ===== NamespacedKey (PDC용) =====
    private static NamespacedKey CUSTOM_ENCHANTS_KEY;
    private static NamespacedKey LAMP_EFFECT_KEY;
    private static NamespacedKey LAMP_TYPE_KEY;
    private static NamespacedKey UPGRADE_LEVEL_KEY;
    private static NamespacedKey ENCHANT_BOOK_ID_KEY;
    private static NamespacedKey LAMP_ITEM_KEY;
    private static NamespacedKey PROTECTION_SCROLL_KEY;
    
    // 램프 다중 슬롯 시스템
    private static NamespacedKey LAMP_SLOTS_KEY;
    private static NamespacedKey LAMP_SLOT_COUNT_KEY;
    
    // 램프 관련 상수
    public static final int DEFAULT_LAMP_SLOTS = 1;
    public static final int MAX_LAMP_SLOTS = 4;

    // ===== 색상 코드 =====
    public static final String COLOR_ENCHANT = "§b";
    public static final String COLOR_LAMP = "§d";
    public static final String COLOR_UPGRADE = "§6";
    public static final String COLOR_SUCCESS = "§a";
    public static final String COLOR_FAIL = "§c";
    public static final String COLOR_WARNING = "§e";
    public static final String COLOR_INFO = "§7";

    // ===== 메시지 프리픽스 =====
    public static final String PREFIX_ENCHANT = "§b§l[인챈트] §r";
    public static final String PREFIX_LAMP = "§d§l[램프] §r";
    public static final String PREFIX_UPGRADE = "§6§l[강화] §r";

    // ===== 강화 시스템 설정 =====
    public static final int MAX_UPGRADE_LEVEL = 100;
    public static final int MIN_UPGRADE_LEVEL = 0;
    
    // ===== CustomModelData (리소스팩 연동) =====
    
    // 강화 램프 (2000-2005)
    public static final int CMD_ENHANCE_LAMP_1 = 2000;  // WEAPON_LAMP
    public static final int CMD_ENHANCE_LAMP_2 = 2001;  // ARMOR_LAMP
    public static final int CMD_ENHANCE_LAMP_3 = 2002;  // TOOL_LAMP
    public static final int CMD_ENHANCE_LAMP_4 = 2003;  // UNIVERSAL_LAMP
    // [2026-01-31] SHOVEL_LAMP, AXE_LAMP 중복 CustomModelData 수정
    public static final int CMD_ENHANCE_LAMP_5 = 2004;  // SHOVEL_LAMP
    public static final int CMD_ENHANCE_LAMP_6 = 2005;  // AXE_LAMP
    
    // 축복의 램프 (2010-2012) - LampItemFactory에서 사용
    public static final int CMD_BLESSED_LAMP_1 = 2010;
    public static final int CMD_BLESSED_LAMP_2 = 2011;
    public static final int CMD_BLESSED_LAMP_3 = 2012;
    
    // 보호 스크롤 - 하락 방지 (ProtectionScrollFactory)
    public static final int CMD_PROTECTION_SCROLL_1 = 2020;
    
    // 파괴 방지 보호권 (ProtectionScrollFactory)
    public static final int CMD_DESTROY_PROTECTION_1 = 2040;
    
    // 인챈트북 (2060-2064) - EnchantBookFactory에서 레벨별 사용
    public static final int CMD_ENCHANT_BOOK_1 = 2060;
    public static final int CMD_ENCHANT_BOOK_2 = 2061;
    public static final int CMD_ENCHANT_BOOK_3 = 2062;
    public static final int CMD_ENCHANT_BOOK_4 = 2063;
    public static final int CMD_ENCHANT_BOOK_5 = 2064;

    // ===== Lore 구분선 =====
    public static final String LORE_SEPARATOR = "§8§m                              ";
    public static final String LORE_ENCHANT_HEADER = "§b§l◆ 커스텀 인챈트";
    public static final String LORE_LAMP_HEADER = "§d§l◆ 램프 효과";
    public static final String LORE_UPGRADE_HEADER = "§6§l◆ 강화 정보";

    private EnhanceConstants() {}

    // 레거시 호환을 위한 고정 네임스페이스 (절대 변경 금지!)
    // plugin.getName()을 사용하면 "tycoonlite"가 되어 레거시 아이템 데이터를 읽지 못함
    private static final String LEGACY_NAMESPACE = "tycoon";
    
    /**
     * 플러그인 초기화 시 호출
     * 
     * [중요] 모든 NamespacedKey는 레거시 호환을 위해 "tycoon" 네임스페이스를 사용해야 함
     * new NamespacedKey(plugin, ...) 사용 금지! → new NamespacedKey(LEGACY_NAMESPACE, ...) 사용
     */
    @SuppressWarnings("deprecation")
    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        
        // 레거시 호환: "tycoon" 네임스페이스 고정 사용
        CUSTOM_ENCHANTS_KEY = new NamespacedKey(LEGACY_NAMESPACE, "custom_enchants");
        LAMP_EFFECT_KEY = new NamespacedKey(LEGACY_NAMESPACE, "lamp_effect");
        LAMP_TYPE_KEY = new NamespacedKey(LEGACY_NAMESPACE, "lamp_type");
        UPGRADE_LEVEL_KEY = new NamespacedKey(LEGACY_NAMESPACE, "upgrade_level");
        ENCHANT_BOOK_ID_KEY = new NamespacedKey(LEGACY_NAMESPACE, "enchant_book_id");
        LAMP_ITEM_KEY = new NamespacedKey(LEGACY_NAMESPACE, "lamp_item");
        PROTECTION_SCROLL_KEY = new NamespacedKey(LEGACY_NAMESPACE, "protection_scroll");
        
        LAMP_SLOTS_KEY = new NamespacedKey(LEGACY_NAMESPACE, "lamp_slots");
        LAMP_SLOT_COUNT_KEY = new NamespacedKey(LEGACY_NAMESPACE, "lamp_slot_count");
        
        plugin.getLogger().info("[EnhanceConstants] 초기화 완료 (namespace=" + LEGACY_NAMESPACE + ")");
    }

    // ===== Key Getters =====

    public static NamespacedKey getCustomEnchantsKey() {
        return CUSTOM_ENCHANTS_KEY;
    }

    public static NamespacedKey getLampEffectKey() {
        return LAMP_EFFECT_KEY;
    }

    public static NamespacedKey getLampTypeKey() {
        return LAMP_TYPE_KEY;
    }

    public static NamespacedKey getUpgradeLevelKey() {
        return UPGRADE_LEVEL_KEY;
    }

    public static NamespacedKey getEnchantBookIdKey() {
        return ENCHANT_BOOK_ID_KEY;
    }

    public static NamespacedKey getLampItemKey() {
        return LAMP_ITEM_KEY;
    }

    public static NamespacedKey getProtectionScrollKey() {
        return PROTECTION_SCROLL_KEY;
    }
    
    public static NamespacedKey getLampSlotsKey() {
        return LAMP_SLOTS_KEY;
    }
    
    public static NamespacedKey getLampSlotCountKey() {
        return LAMP_SLOT_COUNT_KEY;
    }

    public static JavaPlugin getPlugin() {
        return plugin;
    }
    
    public static boolean isInitialized() {
        return plugin != null;
    }
}
