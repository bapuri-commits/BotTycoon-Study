package kr.bapuri.tycoon.enhance.common;

import kr.bapuri.tycoon.enhance.enchant.CustomEnchant;
import kr.bapuri.tycoon.enhance.lamp.LampEffect;
import kr.bapuri.tycoon.enhance.lamp.LampSlotData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EnhanceLoreBuilder - 아이템 설명(Lore) 빌더
 * 
 * 커스텀 인챈트, 램프 효과, 강화 정보를 아이템 Lore에 표시
 * 
 * Phase 6: 레거시 복사
 */
public class EnhanceLoreBuilder {

    private final ItemStack item;
    private List<String> originalLore;
    private List<String> enhanceLore;

    public EnhanceLoreBuilder(ItemStack item) {
        this.item = item;
        this.originalLore = new ArrayList<>();
        this.enhanceLore = new ArrayList<>();
        
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            // 기존 Lore에서 강화 관련 Lore 제거
            List<String> currentLore = item.getItemMeta().getLore();
            boolean inEnhanceSection = false;
            
            for (String line : currentLore) {
                if (line.contains("◆ 커스텀 인챈트") || 
                    line.contains("◆ 램프 효과") || 
                    line.contains("◆ 강화 정보")) {
                    inEnhanceSection = true;
                    continue;
                }
                
                if (inEnhanceSection && (line.startsWith("§") && line.contains("  "))) {
                    continue; // 강화 관련 세부 항목 스킵
                }
                
                if (line.equals(EnhanceConstants.LORE_SEPARATOR)) {
                    inEnhanceSection = false;
                    continue;
                }
                
                if (!inEnhanceSection) {
                    originalLore.add(line);
                }
            }
        }
    }

    /**
     * 커스텀 인챈트 Lore 추가
     */
    public EnhanceLoreBuilder addEnchants(Map<String, Integer> enchants) {
        if (enchants == null || enchants.isEmpty()) return this;

        enhanceLore.add(EnhanceConstants.LORE_ENCHANT_HEADER);
        
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            CustomEnchant enchant = CustomEnchant.fromId(entry.getKey());
            if (enchant != null) {
                String levelStr = toRoman(entry.getValue());
                enhanceLore.add("  " + EnhanceConstants.COLOR_ENCHANT + 
                               enchant.getDisplayName() + " " + levelStr);
            }
        }
        
        return this;
    }

    /**
     * 램프 효과 Lore 추가 (v2.5 다중 슬롯 - 압축 표시)
     */
    public EnhanceLoreBuilder addLampSlots(List<LampSlotData> slots, int slotCount) {
        if (slotCount <= 0) return this;

        int activeCount = 0;
        for (LampSlotData slot : slots) {
            if (!slot.isEmpty()) activeCount++;
        }

        // 헤더: 램프 효과 (활성/최대)
        enhanceLore.add("§d§l◆ 램프 효과 §7(" + activeCount + "/" + slotCount + ")");

        // 슬롯별 압축 표시
        for (int i = 0; i < slotCount; i++) {
            LampSlotData slot = i < slots.size() ? slots.get(i) : LampSlotData.empty();
            String prefix = "  §d[" + (i + 1) + "] ";
            
            if (slot.isEmpty()) {
                // 빈 슬롯
                enhanceLore.add(prefix + "§8빈 슬롯");
            } else {
                // 활성 슬롯: 효과명 + 수치
                enhanceLore.add(prefix + slot.getCompactDisplay());
            }
        }

        // 확장 가능 힌트
        if (slotCount < EnhanceConstants.MAX_LAMP_SLOTS) {
            enhanceLore.add("  §8(슬롯 확장 가능)");
        }

        return this;
    }
    
    /**
     * 램프 효과 Lore 추가 (레거시 호환 - 단일 효과)
     * @deprecated v2.5부터 addLampSlots() 사용
     */
    @Deprecated
    public EnhanceLoreBuilder addLampEffect(String effectId) {
        if (effectId == null || effectId.isEmpty()) return this;

        LampEffect effect = LampEffect.fromId(effectId);
        if (effect == null) return this;

        enhanceLore.add(EnhanceConstants.LORE_LAMP_HEADER);
        enhanceLore.add("  " + EnhanceConstants.COLOR_LAMP + effect.getDisplayName());
        enhanceLore.add("  " + EnhanceConstants.COLOR_INFO + effect.getDescription());
        
        return this;
    }

    /**
     * 강화 레벨 Lore 추가
     */
    public EnhanceLoreBuilder addUpgradeLevel(int level) {
        if (level <= 0) return this;

        enhanceLore.add(EnhanceConstants.LORE_UPGRADE_HEADER);
        enhanceLore.add("  " + EnhanceConstants.COLOR_UPGRADE + "+" + level + " 강화");
        
        // 스탯 보너스 표시
        if (EnhanceItemUtil.isWeapon(item)) {
            double damageBonus = calculateDamageBonus(level);
            enhanceLore.add("  " + EnhanceConstants.COLOR_SUCCESS + 
                           "▸ 공격력 +" + String.format("%.1f", damageBonus) + "%");
        }
        
        if (EnhanceItemUtil.isArmor(item)) {
            double defenseBonus = calculateDefenseBonus(level);
            enhanceLore.add("  " + EnhanceConstants.COLOR_SUCCESS + 
                           "▸ 방어력 +" + String.format("%.1f", defenseBonus) + "%");
        }
        
        // 내구도 보너스 표시 (모든 강화 가능 아이템)
        double durabilityBonus = calculateDurabilityBonus(level);
        enhanceLore.add("  " + EnhanceConstants.COLOR_INFO + 
                       "▸ 내구도 +" + String.format("%.0f", durabilityBonus) + "%");
        
        return this;
    }

    /**
     * 모든 강화 정보를 자동으로 읽어서 Lore 빌드 (v2.5 다중 슬롯)
     */
    public EnhanceLoreBuilder buildFromItem() {
        // 커스텀 인챈트
        Map<String, Integer> enchants = EnhanceItemUtil.getCustomEnchants(item);
        addEnchants(enchants);

        // 레거시 마이그레이션 (구 시스템 데이터 제거)
        EnhanceItemUtil.migrateLegacyLampEffect(item);

        // v2.5: 램프 다중 슬롯
        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(item);
        int slotCount = EnhanceItemUtil.getLampSlotCount(item);
        
        // 슬롯이 1개 이상이거나 효과가 있으면 표시
        if (slotCount > 0 || !slots.isEmpty()) {
            addLampSlots(slots, slotCount);
        }

        // 강화 레벨
        int upgradeLevel = EnhanceItemUtil.getUpgradeLevel(item);
        addUpgradeLevel(upgradeLevel);

        return this;
    }

    /**
     * 최종 Lore 적용
     */
    public void apply() {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> finalLore = new ArrayList<>(originalLore);
        
        if (!enhanceLore.isEmpty()) {
            if (!finalLore.isEmpty()) {
                finalLore.add(EnhanceConstants.LORE_SEPARATOR);
            }
            finalLore.addAll(enhanceLore);
        }

        meta.setLore(finalLore.isEmpty() ? null : finalLore);
        item.setItemMeta(meta);
    }

    /**
     * Lore 완전 새로고침 (아이템의 현재 데이터 기반)
     */
    public static void refreshLore(ItemStack item) {
        if (item == null) return;
        new EnhanceLoreBuilder(item).buildFromItem().apply();
    }

    /**
     * 강화 레벨에 따른 공격력 보너스 계산 (%)
     * 
     * v2.1: UpgradeLevel.java와 동기화
     * 100강 = +4000% (level * 40.0)
     */
    public static double calculateDamageBonus(int level) {
        if (level <= 0) return 0;
        return level * 40.0;
    }

    /**
     * 강화 레벨에 따른 방어력 보너스 계산 (%)
     * 
     * v2.1: UpgradeLevel.java와 동기화
     * 100강 = +3200% (level * 32.0)
     */
    public static double calculateDefenseBonus(int level) {
        if (level <= 0) return 0;
        return level * 32.0;
    }

    /**
     * 강화 레벨에 따른 내구도 보너스 계산 (%)
     * 
     * v2.3: Hunter Remodel - 내구도 시스템
     * 100강 = +200% (level * 2.0)
     */
    public static double calculateDurabilityBonus(int level) {
        if (level <= 0) return 0;
        return level * 2.0;
    }

    /**
     * 숫자를 로마 숫자로 변환 (인챈트 레벨용)
     */
    private static String toRoman(int number) {
        if (number <= 0 || number > 10) return String.valueOf(number);
        
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return romans[number - 1];
    }

    /**
     * 아이템 이름에 강화 레벨 프리픽스 추가
     */
    public static void updateItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        int level = EnhanceItemUtil.getUpgradeLevel(item);

        String currentName = meta.hasDisplayName() ? meta.getDisplayName() : 
                            getDefaultName(item.getType());

        // 기존 강화 프리픽스 제거
        currentName = currentName.replaceAll("§6\\+\\d+ ", "");

        if (level > 0) {
            currentName = EnhanceConstants.COLOR_UPGRADE + "+" + level + " " + currentName;
        }

        meta.setDisplayName(currentName);
        item.setItemMeta(meta);
    }

    /**
     * Material의 기본 이름 가져오기
     */
    private static String getDefaultName(org.bukkit.Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1)).append(" ");
            }
        }
        
        return "§f" + result.toString().trim();
    }
}
