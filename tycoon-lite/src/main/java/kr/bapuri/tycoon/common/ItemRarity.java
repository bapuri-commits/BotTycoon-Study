package kr.bapuri.tycoon.common;

import org.bukkit.ChatColor;

/**
 * 공통 아이템 희귀도 등급
 * Fisher, Treasure Chest 등 다양한 시스템에서 재사용
 */
public enum ItemRarity {
    COMMON(1, "일반", ChatColor.WHITE, 1.0),
    UNCOMMON(2, "고급", ChatColor.GREEN, 1.5),
    RARE(3, "희귀", ChatColor.BLUE, 2.5),
    EPIC(4, "영웅", ChatColor.DARK_PURPLE, 5.0),
    LEGENDARY(5, "전설", ChatColor.GOLD, 15.0);

    private final int tier;
    private final String displayName;
    private final ChatColor color;
    private final double baseMultiplier; // 기본 가치 배율

    ItemRarity(int tier, String displayName, ChatColor color, double baseMultiplier) {
        this.tier = tier;
        this.displayName = displayName;
        this.color = color;
        this.baseMultiplier = baseMultiplier;
    }

    public int getTier() {
        return tier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColor() {
        return color;
    }

    public double getBaseMultiplier() {
        return baseMultiplier;
    }

    public String getColoredName() {
        return color + displayName;
    }

    public static ItemRarity fromTier(int tier) {
        for (ItemRarity rarity : values()) {
            if (rarity.tier == tier) {
                return rarity;
            }
        }
        return COMMON;
    }

    public static ItemRarity fromName(String name) {
        for (ItemRarity rarity : values()) {
            if (rarity.name().equalsIgnoreCase(name)) {
                return rarity;
            }
        }
        return null;
    }
}

