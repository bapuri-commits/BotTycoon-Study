package kr.bapuri.tycoon.job.fisher;

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
 * FishRarity - 물고기 희귀도 정의
 * 
 * Phase 4.D: 어부 직업의 낚시 결과 희귀도
 * - Town/Wild 환경에 따라 다른 확률 적용
 * - 희귀도에 따라 XP 보상 차등 지급
 * 
 * [Phase 승급효과] 아이템에 희귀도 저장 + 상점 가격 보너스
 */
public enum FishRarity {
    
    COMMON("일반", "§f", NamedTextColor.WHITE, 1.0, 1.0, false),
    UNCOMMON("고급", "§a", NamedTextColor.GREEN, 1.5, 1.2, true),
    RARE("희귀", "§9", NamedTextColor.BLUE, 2.5, 1.5, true),
    EPIC("영웅", "§5", NamedTextColor.DARK_PURPLE, 4.0, 2.0, true),
    LEGENDARY("전설", "§6", NamedTextColor.GOLD, 8.0, 3.0, true);
    
    private final String displayName;
    private final String colorCode;
    private final TextColor textColor;
    private final double xpMultiplier;
    private final double priceMultiplier;
    private final boolean showLore;
    
    // PDC 키 (정적 초기화)
    private static NamespacedKey rarityKey;
    
    // 희귀도 적용 가능한 물고기/수산물
    private static final Set<Material> FISH_MATERIALS = EnumSet.of(
        // 물고기
        Material.COD,
        Material.SALMON,
        Material.TROPICAL_FISH,
        Material.PUFFERFISH,
        // 조리된 물고기
        Material.COOKED_COD,
        Material.COOKED_SALMON,
        // 해양 전리품
        Material.INK_SAC,
        Material.GLOW_INK_SAC,
        Material.NAUTILUS_SHELL,
        Material.PRISMARINE_SHARD,
        Material.PRISMARINE_CRYSTALS
    );
    
    FishRarity(String displayName, String colorCode, TextColor textColor, 
               double xpMultiplier, double priceMultiplier, boolean showLore) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.textColor = textColor;
        this.xpMultiplier = xpMultiplier;
        this.priceMultiplier = priceMultiplier;
        this.showLore = showLore;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getColorCode() {
        return colorCode;
    }
    
    public TextColor getTextColor() {
        return textColor;
    }
    
    /**
     * 희귀도에 따른 XP 배율
     */
    public double getXpMultiplier() {
        return xpMultiplier;
    }
    
    /**
     * [Phase 승급효과] 희귀도에 따른 가격 배율
     */
    public double getPriceMultiplier() {
        return priceMultiplier;
    }
    
    public boolean shouldShowLore() {
        return showLore;
    }
    
    /**
     * 컬러 코드가 적용된 표시 이름
     */
    public String getColoredName() {
        return colorCode + displayName;
    }
    
    /**
     * 다음 등급 반환 (최대면 null)
     */
    public FishRarity next() {
        return switch (this) {
            case COMMON -> UNCOMMON;
            case UNCOMMON -> RARE;
            case RARE -> EPIC;
            case EPIC -> LEGENDARY;
            case LEGENDARY -> null;
        };
    }
    
    // ===== 정적 초기화 =====
    
    /**
     * 플러그인 초기화 시 호출
     */
    public static void init(JavaPlugin plugin) {
        rarityKey = new NamespacedKey(plugin, "fish_rarity");
    }
    
    /**
     * PDC 키 조회
     */
    public static NamespacedKey getKey() {
        return rarityKey;
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 문자열에서 희귀도 파싱
     */
    public static FishRarity fromString(String name) {
        if (name == null) return COMMON;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
    
    /**
     * 아이템이 희귀도 적용 가능한 물고기인지 확인
     */
    public static boolean isFishMaterial(Material material) {
        return FISH_MATERIALS.contains(material);
    }
    
    // ===== 아이템 희귀도 관리 =====
    
    /**
     * 아이템에 희귀도 적용
     */
    public static void applyRarity(ItemStack item, FishRarity rarity) {
        if (item == null || item.getType().isAir()) return;
        if (rarityKey == null) return;
        if (rarity == COMMON) return; // COMMON은 저장하지 않음
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        // PDC에 희귀도 저장
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(rarityKey, PersistentDataType.STRING, rarity.name());
        
        // 로어 추가
        if (rarity.shouldShowLore()) {
            updateLore(meta, rarity);
        }
        
        item.setItemMeta(meta);
    }
    
    /**
     * 아이템의 희귀도 조회
     */
    public static FishRarity getRarity(ItemStack item) {
        if (item == null || item.getType().isAir()) return COMMON;
        if (rarityKey == null) return COMMON;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return COMMON;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String rarityName = pdc.get(rarityKey, PersistentDataType.STRING);
        
        return fromString(rarityName);
    }
    
    /**
     * 로어 업데이트
     */
    private static void updateLore(ItemMeta meta, FishRarity rarity) {
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);
        
        // 기존 희귀도 로어 제거
        lore.removeIf(c -> {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(c);
            return plain.contains("희귀도:") || plain.contains("판매가 +");
        });
        
        // 새 희귀도 로어 추가 (첫 번째 줄)
        Component rarityComponent = Component.text("희귀도: ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(rarity.getDisplayName())
                        .color(rarity.getTextColor())
                        .decoration(TextDecoration.ITALIC, false));
        lore.add(0, rarityComponent);
        
        // 가격 보너스 표시
        if (rarity.getPriceMultiplier() > 1.0) {
            int bonusPercent = (int) ((rarity.getPriceMultiplier() - 1.0) * 100);
            Component bonusComponent = Component.text("판매가 +" + bonusPercent + "%")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false);
            lore.add(1, bonusComponent);
        }
        
        meta.lore(lore);
    }
}
