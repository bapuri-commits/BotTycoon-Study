package kr.bapuri.tycoon.enhance.common;

import kr.bapuri.tycoon.enhance.lamp.LampSlotData;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EnhanceItemUtil - 강화 시스템 아이템 유틸리티
 * 
 * PersistentDataContainer(PDC)를 사용하여 커스텀 데이터 관리
 * 
 * Phase 6: 레거시 버전 통합
 */
public final class EnhanceItemUtil {

    private EnhanceItemUtil() {}

    // ========== 커스텀 인챈트 ==========

    /**
     * 커스텀 인챈트 맵 가져오기
     * 형식: "ENCHANT_ID:LEVEL,ENCHANT_ID:LEVEL,..."
     */
    public static Map<String, Integer> getCustomEnchants(ItemStack item) {
        Map<String, Integer> enchants = new HashMap<>();
        if (item == null || !item.hasItemMeta()) return enchants;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        String data = pdc.get(EnhanceConstants.getCustomEnchantsKey(), PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return enchants;

        for (String entry : data.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                try {
                    enchants.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return enchants;
    }

    /**
     * 커스텀 인챈트 맵 저장
     */
    public static void setCustomEnchants(ItemStack item, Map<String, Integer> enchants) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (enchants == null || enchants.isEmpty()) {
            pdc.remove(EnhanceConstants.getCustomEnchantsKey());
        } else {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(entry.getKey()).append(":").append(entry.getValue());
            }
            pdc.set(EnhanceConstants.getCustomEnchantsKey(), PersistentDataType.STRING, sb.toString());
        }

        item.setItemMeta(meta);
    }

    /**
     * 단일 커스텀 인챈트 추가/업데이트
     */
    public static void addCustomEnchant(ItemStack item, String enchantId, int level) {
        Map<String, Integer> enchants = getCustomEnchants(item);
        enchants.put(enchantId, level);
        setCustomEnchants(item, enchants);
    }

    /**
     * 단일 커스텀 인챈트 제거
     */
    public static void removeCustomEnchant(ItemStack item, String enchantId) {
        Map<String, Integer> enchants = getCustomEnchants(item);
        enchants.remove(enchantId);
        setCustomEnchants(item, enchants);
    }

    /**
     * 특정 커스텀 인챈트 보유 여부
     */
    public static boolean hasCustomEnchant(ItemStack item, String enchantId) {
        return getCustomEnchants(item).containsKey(enchantId);
    }

    /**
     * 특정 커스텀 인챈트 레벨
     */
    public static int getCustomEnchantLevel(ItemStack item, String enchantId) {
        return getCustomEnchants(item).getOrDefault(enchantId, 0);
    }

    // ========== 램프 효과 ==========

    /**
     * 램프 효과 ID 가져오기
     */
    public static String getLampEffect(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(EnhanceConstants.getLampEffectKey(), PersistentDataType.STRING);
    }

    /**
     * 램프 효과 설정
     */
    public static void setLampEffect(ItemStack item, String effectId) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (effectId == null || effectId.isEmpty()) {
            pdc.remove(EnhanceConstants.getLampEffectKey());
        } else {
            pdc.set(EnhanceConstants.getLampEffectKey(), PersistentDataType.STRING, effectId);
        }

        item.setItemMeta(meta);
    }

    /**
     * 램프 효과 보유 여부
     */
    public static boolean hasLampEffect(ItemStack item) {
        return getLampEffect(item) != null;
    }

    // ========== 램프 다중 슬롯 시스템 (v2.5) ==========

    /**
     * 램프 슬롯 데이터 전체 가져오기
     * 형식: "effectId:value1:value2,effectId:value1:value2,..."
     */
    public static List<LampSlotData> getLampSlots(ItemStack item) {
        List<LampSlotData> slots = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) {
            return slots;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // 새 시스템 데이터 확인
        String data = pdc.get(EnhanceConstants.getLampSlotsKey(), PersistentDataType.STRING);
        if (data != null && !data.isEmpty()) {
            String[] slotStrings = data.split(",");
            for (String slotStr : slotStrings) {
                slots.add(LampSlotData.fromString(slotStr));
            }
        }
        
        return slots;
    }

    /**
     * 램프 슬롯 데이터 전체 저장
     */
    public static void setLampSlots(ItemStack item, List<LampSlotData> slots) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (slots == null || slots.isEmpty()) {
            pdc.remove(EnhanceConstants.getLampSlotsKey());
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < slots.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(slots.get(i).toSaveString());
            }
            pdc.set(EnhanceConstants.getLampSlotsKey(), PersistentDataType.STRING, sb.toString());
        }

        item.setItemMeta(meta);
    }

    /**
     * 현재 슬롯 수 가져오기 (기본 1)
     */
    public static int getLampSlotCount(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return EnhanceConstants.DEFAULT_LAMP_SLOTS;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer count = pdc.get(EnhanceConstants.getLampSlotCountKey(), PersistentDataType.INTEGER);
        
        return count != null ? count : EnhanceConstants.DEFAULT_LAMP_SLOTS;
    }

    /**
     * 슬롯 수 설정
     */
    public static void setLampSlotCount(ItemStack item, int count) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        count = Math.max(EnhanceConstants.DEFAULT_LAMP_SLOTS, 
                        Math.min(EnhanceConstants.MAX_LAMP_SLOTS, count));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(EnhanceConstants.getLampSlotCountKey(), PersistentDataType.INTEGER, count);
        item.setItemMeta(meta);
    }

    /**
     * 빈 슬롯 인덱스 찾기 (-1이면 없음)
     */
    public static int findEmptySlot(ItemStack item) {
        List<LampSlotData> slots = getLampSlots(item);
        int slotCount = getLampSlotCount(item);

        for (int i = 0; i < slotCount; i++) {
            if (i >= slots.size() || slots.get(i).isEmpty()) {
                return i;
            }
        }
        return -1; // 모든 슬롯이 차있음
    }

    /**
     * 램프 효과 추가 (빈 슬롯에)
     * @return 추가된 슬롯 인덱스 (-1이면 실패)
     */
    public static int addLampSlot(ItemStack item, LampSlotData data) {
        int emptySlot = findEmptySlot(item);
        if (emptySlot < 0) {
            return -1; // 슬롯 풀
        }

        List<LampSlotData> slots = getLampSlots(item);
        
        // 슬롯 리스트 확장
        while (slots.size() <= emptySlot) {
            slots.add(LampSlotData.empty());
        }
        
        slots.set(emptySlot, data);
        setLampSlots(item, slots);
        
        return emptySlot;
    }

    /**
     * 특정 슬롯의 램프 효과 제거 (슬롯은 유지)
     */
    public static boolean removeLampSlot(ItemStack item, int slotIndex) {
        List<LampSlotData> slots = getLampSlots(item);
        
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return false;
        }

        slots.set(slotIndex, LampSlotData.empty());
        setLampSlots(item, slots);
        
        return true;
    }

    /**
     * 특정 슬롯에 램프 효과 설정 (덮어쓰기)
     */
    public static void setLampSlot(ItemStack item, int slotIndex, LampSlotData data) {
        List<LampSlotData> slots = getLampSlots(item);
        int slotCount = getLampSlotCount(item);
        
        // 슬롯 수 범위 내인지 확인
        if (slotIndex < 0 || slotIndex >= slotCount) {
            return;
        }
        
        // slots 리스트가 부족하면 확장
        while (slots.size() <= slotIndex) {
            slots.add(LampSlotData.empty());
        }
        
        slots.set(slotIndex, data);
        setLampSlots(item, slots);
    }

    /**
     * 특정 효과의 총 합산 값 계산 (같은 효과 중복 시)
     */
    public static double getTotalEffectValue(ItemStack item, String effectId) {
        List<LampSlotData> slots = getLampSlots(item);
        double total = 0;

        for (LampSlotData slot : slots) {
            if (!slot.isEmpty() && effectId.equals(slot.getEffectId())) {
                total += slot.getValue1();
            }
        }

        return total;
    }

    /**
     * 특정 효과의 총 보조값 합산
     */
    public static int getTotalEffectValue2(ItemStack item, String effectId) {
        List<LampSlotData> slots = getLampSlots(item);
        int total = 0;

        for (LampSlotData slot : slots) {
            if (!slot.isEmpty() && effectId.equals(slot.getEffectId())) {
                total += slot.getValue2();
            }
        }

        return total;
    }

    /**
     * 특정 효과가 있는지 확인
     */
    public static boolean hasLampSlotEffect(ItemStack item, String effectId) {
        List<LampSlotData> slots = getLampSlots(item);
        
        for (LampSlotData slot : slots) {
            if (!slot.isEmpty() && effectId.equals(slot.getEffectId())) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 활성화된 램프 효과 개수
     */
    public static int getActiveLampCount(ItemStack item) {
        List<LampSlotData> slots = getLampSlots(item);
        int count = 0;

        for (LampSlotData slot : slots) {
            if (!slot.isEmpty()) {
                count++;
            }
        }

        return count;
    }

    /**
     * 레거시 단일 램프 효과를 새 시스템으로 마이그레이션
     * @return true if migrated, false if already new system or no data
     */
    public static boolean migrateLegacyLampEffect(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // 이미 새 시스템 데이터가 있으면 스킵
        if (pdc.has(EnhanceConstants.getLampSlotsKey(), PersistentDataType.STRING)) {
            return false;
        }
        
        // 레거시 데이터 확인
        String legacyEffect = pdc.get(EnhanceConstants.getLampEffectKey(), PersistentDataType.STRING);
        if (legacyEffect == null || legacyEffect.isEmpty()) {
            return false;
        }
        
        // 마이그레이션 정책: 초기화 (기존 램프 효과 제거)
        // 사용자 결정에 따라 레거시 데이터 삭제
        pdc.remove(EnhanceConstants.getLampEffectKey());
        item.setItemMeta(meta);
        
        return true;
    }

    // ========== 강화 레벨 ==========

    /**
     * 강화 레벨 가져오기
     */
    public static int getUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer level = pdc.get(EnhanceConstants.getUpgradeLevelKey(), PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }

    /**
     * 강화 레벨 설정
     */
    public static void setUpgradeLevel(ItemStack item, int level) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        level = Math.max(EnhanceConstants.MIN_UPGRADE_LEVEL, 
                        Math.min(EnhanceConstants.MAX_UPGRADE_LEVEL, level));
        
        pdc.set(EnhanceConstants.getUpgradeLevelKey(), PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
    }

    /**
     * 강화 아이템인지 확인
     */
    public static boolean isUpgraded(ItemStack item) {
        return getUpgradeLevel(item) > 0;
    }

    // ========== 인챈트 북 식별 ==========

    /**
     * 인챈트 북 ID 가져오기
     * 새 시스템(enchant_book_id) 우선, 레거시(custom_enchants) fallback
     */
    public static String getEnchantBookId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // 새 시스템: enchant_book_id 확인
        String bookId = pdc.get(EnhanceConstants.getEnchantBookIdKey(), PersistentDataType.STRING);
        if (bookId != null) return bookId;
        
        // 레거시 fallback: ENCHANTED_BOOK이고 custom_enchants에 정확히 1개만 있으면 인챈트 북
        if (item.getType() == Material.ENCHANTED_BOOK) {
            Map<String, Integer> enchants = getCustomEnchants(item);
            if (enchants.size() == 1) {
                Map.Entry<String, Integer> entry = enchants.entrySet().iterator().next();
                return entry.getKey() + ":" + entry.getValue();
            }
        }
        
        return null;
    }

    /**
     * 인챈트 북 ID 설정
     */
    public static void setEnchantBookId(ItemStack item, String enchantId, int level) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(EnhanceConstants.getEnchantBookIdKey(), PersistentDataType.STRING, enchantId + ":" + level);
        item.setItemMeta(meta);
    }

    /**
     * 커스텀 인챈트 북인지 확인
     */
    public static boolean isEnchantBook(ItemStack item) {
        return item != null && item.getType() == Material.ENCHANTED_BOOK && getEnchantBookId(item) != null;
    }

    // ========== 램프 아이템 식별 ==========

    /**
     * 램프 아이템 타입 가져오기
     */
    public static String getLampItemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(EnhanceConstants.getLampItemKey(), PersistentDataType.STRING);
    }

    /**
     * 램프 아이템 타입 설정
     */
    public static void setLampItemType(ItemStack item, String lampType) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(EnhanceConstants.getLampItemKey(), PersistentDataType.STRING, lampType);
        item.setItemMeta(meta);
    }

    /**
     * 램프 아이템인지 확인
     */
    public static boolean isLampItem(ItemStack item) {
        return getLampItemType(item) != null;
    }

    // ========== 보호 주문서 ==========

    /**
     * 보호 주문서 타입 가져오기
     */
    public static String getProtectionScrollType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(EnhanceConstants.getProtectionScrollKey(), PersistentDataType.STRING);
    }

    /**
     * 보호 주문서 타입 설정
     */
    public static void setProtectionScrollType(ItemStack item, String scrollType) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(EnhanceConstants.getProtectionScrollKey(), PersistentDataType.STRING, scrollType);
        item.setItemMeta(meta);
    }

    /**
     * 보호 주문서인지 확인
     */
    public static boolean isProtectionScroll(ItemStack item) {
        return getProtectionScrollType(item) != null;
    }

    // ========== 아이템 타입 체크 ==========

    /**
     * 무기인지 확인
     */
    public static boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type.name().endsWith("_SWORD") || 
               type.name().endsWith("_AXE") ||
               type == Material.TRIDENT ||
               type == Material.BOW ||
               type == Material.CROSSBOW;
    }

    /**
     * 방어구인지 확인
     */
    public static boolean isArmor(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type.name().endsWith("_HELMET") ||
               type.name().endsWith("_CHESTPLATE") ||
               type.name().endsWith("_LEGGINGS") ||
               type.name().endsWith("_BOOTS") ||
               type == Material.SHIELD ||
               type == Material.ELYTRA;
    }

    /**
     * 도구인지 확인
     */
    public static boolean isTool(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type.name().endsWith("_PICKAXE") ||
               type.name().endsWith("_SHOVEL") ||
               type.name().endsWith("_HOE") ||
               type.name().endsWith("_AXE") ||
               type == Material.FISHING_ROD ||
               type == Material.SHEARS;
    }

    /**
     * 곡괭이인지 확인
     */
    public static boolean isPickaxe(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_PICKAXE");
    }

    /**
     * 괭이인지 확인
     */
    public static boolean isHoe(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_HOE");
    }

    /**
     * 낚싯대인지 확인
     */
    public static boolean isFishingRod(ItemStack item) {
        if (item == null) return false;
        return item.getType() == Material.FISHING_ROD;
    }

    /**
     * 삽인지 확인
     */
    public static boolean isShovel(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_SHOVEL");
    }

    /**
     * 도끼인지 확인
     */
    public static boolean isAxe(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_AXE");
    }

    /**
     * 검인지 확인
     */
    public static boolean isSword(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_SWORD");
    }

    /**
     * 활/석궁인지 확인
     */
    public static boolean isBow(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.BOW || type == Material.CROSSBOW;
    }

    /**
     * 강화 가능한 아이템인지 확인
     */
    public static boolean isUpgradeable(ItemStack item) {
        return isWeapon(item) || isArmor(item);
    }

    /**
     * 인챈트 가능한 아이템인지 확인
     */
    public static boolean isEnchantable(ItemStack item) {
        return isWeapon(item) || isArmor(item) || isTool(item);
    }
}
