package kr.bapuri.tycoon.enhance.enchant;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * VanillaEnchantExtension - 바닐라 인챈트 확장
 * 
 * 바닐라 인챈트 레벨 확장:
 * - Unbreaking IV/V: 내구도 소모 확률 감소 (15%/10%)
 * - Fortune IV/V: 드랍 배율 증가 (평균 3배/4배)
 * 
 * 커스텀 인챈트로 구현하여 바닐라 레벨 제한 우회
 * 
 * Phase 6: 레거시 복사
 */
public class VanillaEnchantExtension implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();
    
    // Unbreaking 확장 수치
    private static final double UNBREAKING_IV_CHANCE = 0.15;
    private static final double UNBREAKING_V_CHANCE = 0.10;
    
    // Fortune 확장 수치
    private static final double FORTUNE_IV_MULTIPLIER = 3.0;
    private static final double FORTUNE_V_MULTIPLIER = 4.0;

    public VanillaEnchantExtension(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ===============================================================
    // Unbreaking IV/V 확장
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        
        // 커스텀 Unbreaking 레벨 확인 (IV = 4, V = 5)
        int customUnbreakingLevel = EnhanceItemUtil.getCustomEnchantLevel(item, "unbreaking_extended");
        if (customUnbreakingLevel <= 0) return;
        
        // 확장 레벨 확인 (4 또는 5)
        double damageChance;
        if (customUnbreakingLevel == 5) {
            damageChance = UNBREAKING_V_CHANCE; // 10%
        } else if (customUnbreakingLevel == 4) {
            damageChance = UNBREAKING_IV_CHANCE; // 15%
        } else {
            return; // 1-3 레벨은 바닐라 처리
        }
        
        // 확률에 따라 내구도 소모 무시
        if (random.nextDouble() >= damageChance) {
            event.setCancelled(true);
        }
    }

    // ===============================================================
    // Fortune IV/V 확장
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return;
        
        // 커스텀 Fortune 레벨 확인 (IV = 4, V = 5)
        int customFortuneLevel = EnhanceItemUtil.getCustomEnchantLevel(tool, "fortune_extended");
        
        if (customFortuneLevel <= 0) return;
        
        // Silk Touch가 있으면 무시
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        
        Block block = event.getBlock();
        if (!isFortuneApplicable(block.getType())) return;
        
        // 확장 레벨 확인 (4 또는 5)
        double multiplier;
        if (customFortuneLevel == 5) {
            multiplier = FORTUNE_V_MULTIPLIER; // 4배
        } else if (customFortuneLevel == 4) {
            multiplier = FORTUNE_IV_MULTIPLIER; // 3배
        } else {
            return; // 1-3 레벨은 바닐라 처리
        }
        
        // 바닐라 Fortune III 효과 제거 (있는 경우)
        int vanillaFortuneLevel = tool.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
        double vanillaMultiplier = vanillaFortuneLevel > 0 ? 1.0 + (vanillaFortuneLevel * 0.4) : 1.0;
        
        // 추가 드랍 계산 (확장 배율 - 바닐라 배율)
        double extraMultiplier = multiplier / vanillaMultiplier;
        if (extraMultiplier <= 1.0) return;
        
        // 드랍 아이템 추가
        for (ItemStack drop : block.getDrops(tool)) {
            int extraAmount = (int) ((extraMultiplier - 1.0) * drop.getAmount());
            if (extraAmount > 0) {
                ItemStack extraDrop = drop.clone();
                extraDrop.setAmount(extraAmount);
                block.getWorld().dropItemNaturally(block.getLocation(), extraDrop);
            }
        }
    }

    /**
     * Fortune 적용 가능한 블록인지 확인
     */
    private boolean isFortuneApplicable(Material material) {
        return switch (material) {
            // 광석
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 NETHER_QUARTZ_ORE,
                 AMETHYST_CLUSTER -> true;
            // 작물
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART -> true;
            // 기타
            case GRAVEL, GLOWSTONE, SEA_LANTERN, MELON, SWEET_BERRY_BUSH -> true;
            default -> false;
        };
    }

    // ===============================================================
    // 유틸리티: 확장 인챈트 적용
    // ===============================================================

    /**
     * 아이템에 Unbreaking IV 적용
     */
    public static void applyUnbreakingIV(ItemStack item) {
        EnhanceItemUtil.addCustomEnchant(item, "unbreaking_extended", 4);
    }

    /**
     * 아이템에 Unbreaking V 적용
     */
    public static void applyUnbreakingV(ItemStack item) {
        EnhanceItemUtil.addCustomEnchant(item, "unbreaking_extended", 5);
    }

    /**
     * 아이템에 Fortune IV 적용
     */
    public static void applyFortuneIV(ItemStack item) {
        EnhanceItemUtil.addCustomEnchant(item, "fortune_extended", 4);
    }

    /**
     * 아이템에 Fortune V 적용
     */
    public static void applyFortuneV(ItemStack item) {
        EnhanceItemUtil.addCustomEnchant(item, "fortune_extended", 5);
    }
}
