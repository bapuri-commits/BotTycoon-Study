package kr.bapuri.tycoon.job.farmer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * CropGrade - 작물 등급 시스템
 * 
 * [Phase 승급효과] 농부 승급 보너스로 획득하는 고급 작물
 * 
 * 등급별 효과:
 * - NORMAL: 기본 (표시 없음)
 * - PRIME: 판매가 +20%, 녹색 로어
 * - TROPHY: 판매가 +50%, 금색 로어
 */
public enum CropGrade {
    NORMAL("일반", 1.0, NamedTextColor.WHITE, false),
    PRIME("프라임", 1.2, NamedTextColor.GREEN, true),
    TROPHY("트로피", 1.5, NamedTextColor.GOLD, true);

    private final String displayName;
    private final double priceMultiplier;
    private final TextColor textColor;
    private final boolean showLore;

    // PDC 키 (정적 초기화)
    private static NamespacedKey gradeKey;
    
    // 등급 적용 가능한 작물 (씨앗 제외)
    private static final Set<Material> GRADEABLE_CROPS = EnumSet.of(
        // 기본 작물
        Material.WHEAT,
        Material.CARROT,
        Material.POTATO,
        Material.BEETROOT,
        Material.NETHER_WART,
        Material.COCOA_BEANS,
        // 특수 작물
        Material.MELON_SLICE,
        Material.PUMPKIN,
        Material.SUGAR_CANE,
        Material.BAMBOO,
        Material.CACTUS,
        Material.KELP,
        Material.BROWN_MUSHROOM,
        Material.RED_MUSHROOM,
        Material.CHORUS_FRUIT,
        Material.SWEET_BERRIES,
        Material.GLOW_BERRIES,
        // 가공 결과물 (굽기 시 등급 유지용)
        Material.BAKED_POTATO,
        Material.DRIED_KELP,
        Material.POPPED_CHORUS_FRUIT
    );

    CropGrade(String displayName, double priceMultiplier, TextColor textColor, boolean showLore) {
        this.displayName = displayName;
        this.priceMultiplier = priceMultiplier;
        this.textColor = textColor;
        this.showLore = showLore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getPriceMultiplier() {
        return priceMultiplier;
    }

    public TextColor getTextColor() {
        return textColor;
    }
    
    public String getColor() {
        return switch (this) {
            case NORMAL -> "§f";
            case PRIME -> "§a";
            case TROPHY -> "§6";
        };
    }
    
    public String getColoredName() {
        return getColor() + displayName;
    }
    
    public boolean shouldShowLore() {
        return showLore;
    }
    
    // ===== 정적 초기화 =====
    
    /**
     * 플러그인 초기화 시 호출
     */
    public static void init(JavaPlugin plugin) {
        gradeKey = new NamespacedKey(plugin, "crop_grade");
    }
    
    /**
     * PDC 키 조회
     */
    public static NamespacedKey getKey() {
        return gradeKey;
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 문자열에서 등급 파싱
     */
    public static CropGrade fromString(String name) {
        if (name == null) return NORMAL;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
    
    /**
     * 아이템이 등급 적용 가능한 작물인지 확인
     */
    public static boolean isGradeableCrop(Material material) {
        return GRADEABLE_CROPS.contains(material);
    }
    
    // ===== 아이템 등급 관리 =====
    
    /**
     * 아이템에 등급 적용
     */
    public static void applyGrade(ItemStack item, CropGrade grade) {
        if (item == null || item.getType().isAir()) return;
        if (gradeKey == null) return;
        if (grade == NORMAL) return; // NORMAL은 저장하지 않음
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // PDC에 등급 저장
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(gradeKey, PersistentDataType.STRING, grade.name());
        
        // 로어 추가
        if (grade.shouldShowLore()) {
            updateLore(meta, grade);
        }
        
        item.setItemMeta(meta);
    }
    
    /**
     * 아이템의 등급 조회
     */
    public static CropGrade getGrade(ItemStack item) {
        if (item == null || item.getType().isAir()) return NORMAL;
        if (gradeKey == null) return NORMAL;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return NORMAL;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String gradeName = pdc.get(gradeKey, PersistentDataType.STRING);
        
        return fromString(gradeName);
    }
    
    /**
     * 아이템에서 등급 제거
     */
    public static void removeGrade(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        if (gradeKey == null) return;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(gradeKey, PersistentDataType.STRING)) {
            pdc.remove(gradeKey);
            removeLore(meta);
            item.setItemMeta(meta);
        }
    }
    
    /**
     * 로어 업데이트
     */
    private static void updateLore(ItemMeta meta, CropGrade grade) {
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);
        
        // 기존 등급 로어 제거
        lore.removeIf(c -> {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(c);
            return plain.contains("등급:") || plain.contains("프라임") || plain.contains("트로피");
        });
        
        // 새 등급 로어 추가 (첫 번째 줄)
        Component gradeComponent = Component.text("등급: ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(grade.getDisplayName())
                        .color(grade.getTextColor())
                        .decoration(TextDecoration.ITALIC, false));
        lore.add(0, gradeComponent);
        
        // 가격 보너스 표시
        if (grade.getPriceMultiplier() > 1.0) {
            int bonusPercent = (int) ((grade.getPriceMultiplier() - 1.0) * 100);
            Component bonusComponent = Component.text("판매가 +" + bonusPercent + "%")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false);
            lore.add(1, bonusComponent);
        }
        
        meta.lore(lore);
    }
    
    /**
     * 등급 로어 제거
     */
    private static void removeLore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        if (lore == null) return;
        
        lore = new ArrayList<>(lore);
        lore.removeIf(c -> {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(c);
            return plain.contains("등급:") || plain.contains("판매가 +");
        });
        
        meta.lore(lore.isEmpty() ? null : lore);
    }
}
