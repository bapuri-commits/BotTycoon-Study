package kr.bapuri.tycoon.enhance.lamp;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * LampType - 램프 타입 정의
 * 
 * 각 램프는 특정 도구 타입에만 적용 가능
 * 
 * Phase 6: 레거시 복사
 */
public enum LampType {

    // 리소스팩 연동을 위해 모든 램프를 Material.PAPER로 통일
    // CustomModelData로 텍스처 구분 (paper.json 참조)
    
    WEAPON_LAMP("weapon", "§c무기 램프", Material.PAPER, 
                "무기에 강력한 효과를 부여합니다."),
    
    ARMOR_LAMP("armor", "§9방어구 램프", Material.PAPER,
               "방어구에 강력한 효과를 부여합니다."),
    
    TOOL_LAMP("tool", "§a도구 램프", Material.PAPER,
              "도구에 강력한 효과를 부여합니다."),
    
    PICKAXE_LAMP("pickaxe", "§7곡괭이 램프", Material.PAPER,
                 "곡괭이에 강력한 효과를 부여합니다."),
    
    HOE_LAMP("hoe", "§e괭이 램프", Material.PAPER,
             "괭이에 강력한 효과를 부여합니다."),
    
    FISHING_LAMP("fishing", "§3낚싯대 램프", Material.PAPER,
                 "낚싯대에 강력한 효과를 부여합니다."),
    
    SHOVEL_LAMP("shovel", "§f삽 램프", Material.PAPER,
                "삽에 강력한 효과를 부여합니다."),
    
    AXE_LAMP("axe", "§6도끼 램프", Material.PAPER,
             "도끼에 강력한 효과를 부여합니다."),
    
    UNIVERSAL_LAMP("universal", "§d만능 램프", Material.PAPER,
                   "모든 장비에 적용 가능한 램프입니다.");

    private final String id;
    private final String displayName;
    private final Material material;
    private final String description;

    LampType(String id, String displayName, Material material, String description) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDescription() {
        return description;
    }

    /**
     * ID 또는 enum 이름으로 램프 타입 찾기
     * 
     * 예: "tool" 또는 "TOOL_LAMP" 모두 TOOL_LAMP 반환
     */
    public static LampType fromId(String id) {
        if (id == null) return null;
        
        // 1. id로 찾기 (예: "tool")
        for (LampType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        
        // 2. enum 이름으로 찾기 (예: "TOOL_LAMP")
        try {
            return LampType.valueOf(id.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 아이템에 적용 가능한지 확인
     */
    public boolean canApplyTo(ItemStack item) {
        if (item == null) return false;
        
        return switch (this) {
            case WEAPON_LAMP -> EnhanceItemUtil.isWeapon(item);
            case ARMOR_LAMP -> EnhanceItemUtil.isArmor(item);
            case TOOL_LAMP -> EnhanceItemUtil.isTool(item);
            case PICKAXE_LAMP -> EnhanceItemUtil.isPickaxe(item);
            case HOE_LAMP -> EnhanceItemUtil.isHoe(item);
            case FISHING_LAMP -> EnhanceItemUtil.isFishingRod(item);
            case SHOVEL_LAMP -> EnhanceItemUtil.isShovel(item);
            case AXE_LAMP -> EnhanceItemUtil.isAxe(item);
            case UNIVERSAL_LAMP -> EnhanceItemUtil.isEnchantable(item);
        };
    }

    /**
     * 적용 가능한 아이템 설명
     */
    public String getApplicableDescription() {
        return switch (this) {
            case WEAPON_LAMP -> "검, 도끼, 활, 석궁, 삼지창";
            case ARMOR_LAMP -> "투구, 갑옷, 레깅스, 부츠, 방패";
            case TOOL_LAMP -> "곡괭이, 삽, 괭이, 낚싯대, 도끼";
            case PICKAXE_LAMP -> "곡괭이";
            case HOE_LAMP -> "괭이";
            case FISHING_LAMP -> "낚싯대";
            case SHOVEL_LAMP -> "삽";
            case AXE_LAMP -> "도끼";
            case UNIVERSAL_LAMP -> "모든 무기, 방어구, 도구";
        };
    }
}
