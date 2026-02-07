package kr.bapuri.tycoon.enhance.processing.processors;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.processing.EffectProcessor;
import kr.bapuri.tycoon.enhance.processing.ProcessingContext;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * FortuneProcessor - Fortune 효과 처리기
 * 
 * Priority: 100 (가장 먼저 실행)
 * 
 * 바닐라 Fortune I~III + 커스텀 fortune_extended IV~V 통합 처리
 * 
 * 배율:
 * - Fortune I: 1.4배
 * - Fortune II: 1.8배
 * - Fortune III: 2.2배
 * - Fortune IV: 3.0배 (커스텀)
 * - Fortune V: 4.0배 (커스텀)
 */
public class FortuneProcessor implements EffectProcessor {
    
    // Fortune 적용 가능한 블록
    private static final Set<Material> FORTUNE_MATERIALS = EnumSet.of(
        // 광석
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE,
        Material.AMETHYST_CLUSTER,
        // 작물
        Material.WHEAT, Material.CARROTS, Material.POTATOES, 
        Material.BEETROOTS, Material.NETHER_WART,
        // 기타
        Material.GRAVEL, Material.GLOWSTONE, Material.SEA_LANTERN, 
        Material.MELON, Material.SWEET_BERRY_BUSH
    );
    
    // Fortune 배율 상수
    private static final double FORTUNE_IV_MULTIPLIER = 3.0;
    private static final double FORTUNE_V_MULTIPLIER = 4.0;
    
    @Override
    public String getName() {
        return "Fortune";
    }
    
    @Override
    public int getPriority() {
        return 100;
    }
    
    @Override
    public boolean shouldProcess(ProcessingContext ctx) {
        if (!ctx.getOptions().isApplyFortune()) return false;
        if (!FORTUNE_MATERIALS.contains(ctx.getOriginalMaterial())) return false;
        
        ItemStack tool = ctx.getTool();
        if (tool == null) return false;
        
        // Silk Touch가 있으면 Fortune 미적용
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return false;
        
        // Fortune이 있는지 확인
        boolean hasVanilla = tool.containsEnchantment(Enchantment.LOOT_BONUS_BLOCKS);
        boolean hasCustom = EnhanceItemUtil.getCustomEnchantLevel(tool, "fortune_extended") > 0;
        
        return hasVanilla || hasCustom;
    }
    
    @Override
    public void process(ProcessingContext ctx) {
        ItemStack tool = ctx.getTool();
        
        int vanillaLevel = tool.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
        int customLevel = EnhanceItemUtil.getCustomEnchantLevel(tool, "fortune_extended");
        
        double multiplier = calculateMultiplier(vanillaLevel, customLevel);
        
        // 배율 적용
        ctx.multiplyDrops(multiplier);
        
        // 메타데이터 기록
        ctx.setMetadata("fortune_multiplier", multiplier);
        ctx.setMetadata("fortune_level", Math.max(vanillaLevel, customLevel));
        ctx.markEffectApplied("FORTUNE");
    }
    
    /**
     * Fortune 배율 계산
     * 커스텀 레벨이 높으면 커스텀 우선 적용
     */
    private double calculateMultiplier(int vanillaLevel, int customLevel) {
        // 커스텀 Fortune IV/V가 더 높으면 커스텀 사용
        if (customLevel >= 5) {
            return FORTUNE_V_MULTIPLIER; // 4배
        }
        if (customLevel >= 4) {
            return FORTUNE_IV_MULTIPLIER; // 3배
        }
        
        // 바닐라 Fortune 계산 (레벨당 +40%)
        if (vanillaLevel > 0) {
            return 1.0 + (vanillaLevel * 0.4);
        }
        
        return 1.0;
    }
}
