package kr.bapuri.tycoon.enhance.processing.processors;

import kr.bapuri.tycoon.enhance.processing.EffectProcessor;
import kr.bapuri.tycoon.enhance.processing.ProcessingContext;
import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.JobRegistry;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.common.AbstractJobExpService;
import kr.bapuri.tycoon.job.common.AbstractJobGradeService;
import kr.bapuri.tycoon.job.common.GradeBonusConfig;
import kr.bapuri.tycoon.world.WorldManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GradeBonusProcessor - 직업 등급 보너스 처리기
 * 
 * Priority: 200 (Fortune, EnchantBonus 다음)
 * 
 * 직업 등급별 yieldMulti 적용:
 * - 광부: 광석 채굴 시 yieldMulti 확률로 +1 드롭
 * - 농부: 작물 수확 시 yieldMulti 확률로 +1 드롭
 */
public class GradeBonusProcessor implements EffectProcessor {
    
    private final JobRegistry jobRegistry;
    private final WorldManager worldManager;
    private final GradeBonusConfig gradeBonusConfig;
    
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
        Material.NETHER_QUARTZ_ORE
    );
    
    // 작물 재료
    private static final Set<Material> CROP_MATERIALS = EnumSet.of(
        Material.WHEAT, Material.CARROTS, Material.POTATOES,
        Material.BEETROOTS, Material.NETHER_WART,
        Material.COCOA, Material.MELON, Material.PUMPKIN,
        Material.SUGAR_CANE, Material.BAMBOO, Material.CACTUS,
        Material.SWEET_BERRY_BUSH
    );
    
    public GradeBonusProcessor(JobRegistry jobRegistry, WorldManager worldManager, GradeBonusConfig gradeBonusConfig) {
        this.jobRegistry = jobRegistry;
        this.worldManager = worldManager;
        this.gradeBonusConfig = gradeBonusConfig;
    }
    
    @Override
    public String getName() {
        return "GradeBonus";
    }
    
    @Override
    public int getPriority() {
        return 200;
    }
    
    @Override
    public boolean shouldProcess(ProcessingContext ctx) {
        if (!ctx.getOptions().isApplyGradeBonus()) return false;
        if (jobRegistry == null || gradeBonusConfig == null) return false;
        
        Material material = ctx.getOriginalMaterial();
        
        // 광석 또는 작물인지 확인
        boolean isOre = ORE_MATERIALS.contains(material);
        boolean isCrop = CROP_MATERIALS.contains(material);
        
        if (!isOre && !isCrop) return false;
        
        // 해당 직업인지 확인
        Player player = ctx.getPlayer();
        if (isOre) {
            AbstractJobExpService expService = jobRegistry.getExpService(JobType.MINER);
            return expService != null && expService.hasJob(player);
        } else {
            AbstractJobExpService expService = jobRegistry.getExpService(JobType.FARMER);
            return expService != null && expService.hasJob(player);
        }
    }
    
    @Override
    public void process(ProcessingContext ctx) {
        Material material = ctx.getOriginalMaterial();
        Player player = ctx.getPlayer();
        
        if (ORE_MATERIALS.contains(material)) {
            processMinerBonus(ctx, player);
        } else if (CROP_MATERIALS.contains(material)) {
            processFarmerBonus(ctx, player);
        }
    }
    
    /**
     * 광부 등급 보너스 처리
     */
    private void processMinerBonus(ProcessingContext ctx, Player player) {
        AbstractJobGradeService gradeService = jobRegistry.getGradeService(JobType.MINER);
        if (gradeService == null) return;
        
        // 등급 조회
        JobGrade grade = gradeService.getGrade(player);
        if (grade == null) return;
        
        // GradeBonusConfig에서 yieldMulti 가져오기 (예: 1.05 = 5% 추가 확률)
        double yieldMulti = gradeBonusConfig.getYieldMultiplier(JobType.MINER, grade);
        if (yieldMulti <= 1.0) return;
        
        double bonusChance = yieldMulti - 1.0;
        
        // 각 드롭에 확률적 보너스
        for (ItemStack drop : ctx.getDrops()) {
            if (ThreadLocalRandom.current().nextDouble() < bonusChance) {
                drop.setAmount(drop.getAmount() + 1);
            }
        }
        
        ctx.setMetadata("grade_yield_multi", yieldMulti);
        ctx.markEffectApplied("GRADE_BONUS_MINER");
    }
    
    /**
     * 농부 등급 보너스 처리
     */
    private void processFarmerBonus(ProcessingContext ctx, Player player) {
        AbstractJobGradeService gradeService = jobRegistry.getGradeService(JobType.FARMER);
        if (gradeService == null) return;
        
        // 등급 조회
        JobGrade grade = gradeService.getGrade(player);
        if (grade == null) return;
        
        // yieldMulti (작물)
        double yieldMulti = gradeBonusConfig.getYieldMultiplier(JobType.FARMER, grade);
        if (yieldMulti > 1.0) {
            double bonusChance = yieldMulti - 1.0;
            
            for (ItemStack drop : ctx.getDrops()) {
                if (ThreadLocalRandom.current().nextDouble() < bonusChance) {
                    drop.setAmount(drop.getAmount() + 1);
                }
            }
        }
        
        ctx.setMetadata("grade_yield_multi", yieldMulti);
        ctx.markEffectApplied("GRADE_BONUS_FARMER");
    }
}
