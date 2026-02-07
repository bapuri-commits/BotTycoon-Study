package kr.bapuri.tycoon.enhance.upgrade;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static kr.bapuri.tycoon.enhance.common.EnhanceConstants.*;

/**
 * ProtectionScrollFactory - 보호 주문서 생성
 * 
 * Phase 6 LITE: 레거시 버전 이식
 */
public class ProtectionScrollFactory {

    /**
     * 보호 주문서 타입
     * 
     * [Phase 8 변경] ALL 타입 제거됨 - 파괴방지/하락방지 2슬롯 분리 방식으로 변경
     */
    public enum ProtectionType {
        DESTROY("destroy", "§c§l파괴 방지 주문서", Material.PAPER,
                "강화 실패 시 아이템 파괴를 방지합니다."),
        DOWNGRADE("downgrade", "§e§l하락 방지 주문서", Material.PAPER,
                  "강화 실패 시 레벨 하락을 방지합니다.");
        
        // [Phase 8] ALL 타입 제거됨 - 2슬롯 분리 방식 사용
        // ALL("all", "§6§l완전 보호 주문서", Material.ENCHANTED_BOOK,
        //     "강화 실패 시 파괴와 하락 모두 방지합니다.");

        private final String id;
        private final String displayName;
        private final Material material;
        private final String description;

        ProtectionType(String id, String displayName, Material material, String description) {
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

        public static ProtectionType fromId(String id) {
            if (id == null) return null;
            for (ProtectionType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 보호 주문서 생성
     */
    public static ItemStack createScroll(ProtectionType type) {
        if (type == null) return null;

        ItemStack scroll = new ItemStack(type.getMaterial());
        ItemMeta meta = scroll.getItemMeta();
        if (meta == null) return null;

        meta.setDisplayName(type.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + type.getDescription());
        lore.add("");
        lore.add("§8─────────────────────");
        lore.add("§e강화 GUI에서 보호 슬롯에 넣어 사용");
        lore.add("§c실패 시에만 소모됩니다.");

        meta.setLore(lore);
        
        // CustomModelData 설정 (리소스팩 연동)
        int customModelData = getCustomModelDataForType(type);
        meta.setCustomModelData(customModelData);
        
        scroll.setItemMeta(meta);

        // PDC에 타입 저장
        EnhanceItemUtil.setProtectionScrollType(scroll, type.getId());

        return scroll;
    }
    
    /**
     * 보호 스크롤 타입에 따른 CustomModelData 반환
     * 
     * [Phase 8] ALL 타입 제거됨
     */
    private static int getCustomModelDataForType(ProtectionType type) {
        return switch (type) {
            case DESTROY -> CMD_DESTROY_PROTECTION_1;    // 파괴 방지
            case DOWNGRADE -> CMD_PROTECTION_SCROLL_1;   // 하락 방지
            // [Phase 8] ALL 타입 제거됨
            // case ALL -> CMD_PROTECTION_SCROLL_2;
        };
    }

    /**
     * 보호 주문서 여러 개 생성
     */
    public static ItemStack createScrolls(ProtectionType type, int amount) {
        ItemStack scroll = createScroll(type);
        if (scroll != null) {
            scroll.setAmount(Math.min(64, Math.max(1, amount)));
        }
        return scroll;
    }

    /**
     * 보호 주문서인지 확인
     */
    public static boolean isProtectionScroll(ItemStack item) {
        return EnhanceItemUtil.isProtectionScroll(item);
    }

    /**
     * 보호 주문서 타입 가져오기
     */
    public static ProtectionType getType(ItemStack item) {
        String typeId = EnhanceItemUtil.getProtectionScrollType(item);
        return ProtectionType.fromId(typeId);
    }
}
