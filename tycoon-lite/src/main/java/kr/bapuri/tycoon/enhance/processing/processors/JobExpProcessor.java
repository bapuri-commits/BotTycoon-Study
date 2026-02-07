package kr.bapuri.tycoon.enhance.processing.processors;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.processing.EffectProcessor;
import kr.bapuri.tycoon.enhance.processing.ProcessingContext;
import kr.bapuri.tycoon.job.JobRegistry;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.farmer.FarmerExpService;
import kr.bapuri.tycoon.job.miner.MinerExpService;
import kr.bapuri.tycoon.world.WorldManager;
import kr.bapuri.tycoon.world.WorldType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * JobExpProcessor - 직업 경험치 처리기
 * 
 * Priority: 500 (모든 드롭 계산 완료 후)
 * 
 * 최종 드롭량 기준으로 직업 경험치 부여
 * 등급별 xpMulti 적용
 * 
 * WISDOM 인챈트 보너스 적용:
 * - Lv1: +5%, Lv2: +10%, Lv3: +15%, Lv4: +20%, Lv5: +25%
 */
public class JobExpProcessor implements EffectProcessor {
    
    private final JobRegistry jobRegistry;
    private final WorldManager worldManager;
    
    // 광석 재료
    private static final Set<Material> ORE_MATERIALS = EnumSet.of(
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS
    );
    
    // 작물 재료
    private static final Set<Material> CROP_MATERIALS = EnumSet.of(
        Material.WHEAT, Material.CARROTS, Material.POTATOES,
        Material.BEETROOTS, Material.NETHER_WART,
        Material.COCOA, Material.MELON, Material.PUMPKIN,
        Material.SUGAR_CANE, Material.BAMBOO, Material.CACTUS,
        Material.SWEET_BERRY_BUSH
    );
    
    public JobExpProcessor(JobRegistry jobRegistry, WorldManager worldManager) {
        this.jobRegistry = jobRegistry;
        this.worldManager = worldManager;
    }
    
    @Override
    public String getName() {
        return "JobExp";
    }
    
    @Override
    public int getPriority() {
        return 500;
    }
    
    @Override
    public boolean shouldProcess(ProcessingContext ctx) {
        if (!ctx.getOptions().isGrantJobExp()) return false;
        if (jobRegistry == null) return false;
        
        // Wild 월드에서만 직업 경험치 부여
        if (worldManager != null) {
            WorldType worldType = worldManager.getWorldType(ctx.getBlock().getWorld());
            if (worldType != WorldType.WILD) {
                return false;
            }
        }
        
        Material material = ctx.getOriginalMaterial();
        return ORE_MATERIALS.contains(material) || CROP_MATERIALS.contains(material);
    }
    
    @Override
    public void process(ProcessingContext ctx) {
        Material material = ctx.getOriginalMaterial();
        Player player = ctx.getPlayer();
        
        // WISDOM 보너스 계산
        double wisdomMultiplier = getWisdomMultiplier(ctx.getTool());
        
        if (ORE_MATERIALS.contains(material)) {
            processMinerExp(ctx, player, wisdomMultiplier);
        } else if (CROP_MATERIALS.contains(material)) {
            processFarmerExp(ctx, player, wisdomMultiplier);
        }
    }
    
    /**
     * 광부 경험치 처리
     * 최종 드롭 수량 기준으로 XP 계산
     * 
     * 주의: addMiningExp 내부에서 등급별 xpMulti가 이미 적용됨
     * 따라서 단일 호출로 처리해야 함 (루프 호출 시 중복 적용)
     */
    private void processMinerExp(ProcessingContext ctx, Player player, double wisdomMultiplier) {
        MinerExpService expService = (MinerExpService) jobRegistry.getExpService(JobType.MINER);
        if (expService == null || !expService.hasJob(player)) return;
        
        // 최종 드롭 수량
        int dropCount = ctx.getTotalDropCount();
        if (dropCount <= 0) return;
        
        // 단일 호출로 XP 계산 (내부적으로 등급별 xpMulti 적용됨)
        long baseXp = expService.addMiningExp(player, ctx.getOriginalMaterial(), dropCount);
        
        // WISDOM 보너스 XP 추가 지급
        long totalXp = baseXp;
        if (wisdomMultiplier > 1.0 && baseXp > 0) {
            long wisdomBonus = (long) (baseXp * (wisdomMultiplier - 1.0));
            // WISDOM 보너스를 실제로 지급
            expService.addExp(player, wisdomBonus);
            totalXp += wisdomBonus;
        }
        
        ctx.addJobExp(JobType.MINER, totalXp);
        ctx.setMetadata("job_exp_type", "MINER");
        ctx.setMetadata("wisdom_multiplier", wisdomMultiplier);
        ctx.markEffectApplied("JOB_EXP_MINER");
    }
    
    /**
     * 농부 경험치 처리
     * 최종 드롭 수량 기준으로 XP 계산
     */
    private void processFarmerExp(ProcessingContext ctx, Player player, double wisdomMultiplier) {
        FarmerExpService expService = (FarmerExpService) jobRegistry.getExpService(JobType.FARMER);
        if (expService == null || !expService.hasJob(player)) return;
        
        // 최종 드롭 수량 계산
        int dropCount = ctx.getTotalDropCount();
        
        // addHarvestExp(player, material, count)
        long baseXp = expService.addHarvestExp(player, ctx.getOriginalMaterial(), dropCount);
        
        // WISDOM 보너스 XP 추가 지급
        long totalXp = baseXp;
        if (wisdomMultiplier > 1.0 && baseXp > 0) {
            long wisdomBonus = (long) (baseXp * (wisdomMultiplier - 1.0));
            // WISDOM 보너스를 실제로 지급
            expService.addExp(player, wisdomBonus);
            totalXp += wisdomBonus;
        }
        
        ctx.addJobExp(JobType.FARMER, totalXp);
        ctx.setMetadata("job_exp_type", "FARMER");
        ctx.setMetadata("wisdom_multiplier", wisdomMultiplier);
        ctx.markEffectApplied("JOB_EXP_FARMER");
    }
    
    /**
     * WISDOM 인챈트 배율 계산
     */
    private double getWisdomMultiplier(ItemStack tool) {
        if (tool == null) return 1.0;
        
        int wisdomLevel = EnhanceItemUtil.getCustomEnchantLevel(tool, "wisdom");
        if (wisdomLevel <= 0) return 1.0;
        
        // Lv1: 1.05, Lv2: 1.10, Lv3: 1.15, Lv4: 1.20, Lv5: 1.25
        return 1.0 + (wisdomLevel * 0.05);
    }
}
