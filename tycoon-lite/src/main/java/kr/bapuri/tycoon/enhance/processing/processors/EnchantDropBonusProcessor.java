package kr.bapuri.tycoon.enhance.processing.processors;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.processing.EffectProcessor;
import kr.bapuri.tycoon.enhance.processing.ProcessingContext;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * EnchantDropBonusProcessor - 인챈트 드롭 보너스 처리기
 * 
 * Priority: 150 (Fortune 다음)
 * 
 * 처리하는 인챈트:
 * - EXPERTISE: Fortune 추가 (+1/+2/+3)
 * - LUCKY_HAND: 희귀 드롭 확률 (+5%/+10%/+15%)
 * - DEEP_MINER: Y < 0 광석 채굴 시 추가 드롭 (+10%/+20%/+30%)
 */
public class EnchantDropBonusProcessor implements EffectProcessor {
    
    @Override
    public String getName() {
        return "EnchantDropBonus";
    }
    
    @Override
    public int getPriority() {
        return 150;
    }
    
    @Override
    public boolean shouldProcess(ProcessingContext ctx) {
        if (!ctx.getOptions().isApplyEnchantBonus()) return false;
        
        ItemStack tool = ctx.getTool();
        if (tool == null) return false;
        
        // EXPERTISE, LUCKY_HAND, DEEP_MINER 중 하나라도 있는지 확인
        int expertiseLevel = EnhanceItemUtil.getCustomEnchantLevel(tool, "expertise");
        int luckyHandLevel = EnhanceItemUtil.getCustomEnchantLevel(tool, "lucky_hand");
        int deepMinerLevel = EnhanceItemUtil.getCustomEnchantLevel(tool, "deep_miner");
        
        return expertiseLevel > 0 || luckyHandLevel > 0 || deepMinerLevel > 0;
    }
    
    @Override
    public void process(ProcessingContext ctx) {
        ItemStack tool = ctx.getTool();
        
        // EXPERTISE 처리 (Fortune 추가)
        int expertiseLevel = EnhanceItemUtil.getCustomEnchantLevel(tool, "expertise");
        if (expertiseLevel > 0) {
            applyExpertise(ctx, expertiseLevel);
        }
        
        // LUCKY_HAND 처리 (희귀 드롭 확률)
        int luckyHandLevel = EnhanceItemUtil.getCustomEnchantLevel(tool, "lucky_hand");
        if (luckyHandLevel > 0) {
            applyLuckyHand(ctx, luckyHandLevel);
        }
        
        // DEEP_MINER 처리 (Y < 0 광석 추가 드롭)
        int deepMinerLevel = EnhanceItemUtil.getCustomEnchantLevel(tool, "deep_miner");
        if (deepMinerLevel > 0) {
            applyDeepMiner(ctx, deepMinerLevel);
        }
    }
    
    /**
     * EXPERTISE 효과 적용
     * 33% 확률로 Fortune 레벨만큼 추가 드롭
     */
    private void applyExpertise(ProcessingContext ctx, int level) {
        // 33% 확률로 발동
        if (ThreadLocalRandom.current().nextDouble() > 0.33) {
            return;
        }
        
        // Fortune 레벨만큼 추가 드롭 (최대 level개)
        int bonusAmount = level; // +1/+2/+3
        
        for (ItemStack drop : ctx.getDrops()) {
            drop.setAmount(drop.getAmount() + bonusAmount);
        }
        
        ctx.setMetadata("expertise_bonus", bonusAmount);
        ctx.markEffectApplied("EXPERTISE");
    }
    
    /**
     * LUCKY_HAND 효과 적용
     * 레벨당 5% 확률로 드롭 2배
     */
    private void applyLuckyHand(ProcessingContext ctx, int level) {
        double chance = level * 0.05; // 5%/10%/15%
        
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            // 모든 드롭 2배
            for (ItemStack drop : ctx.getDrops()) {
                drop.setAmount(drop.getAmount() * 2);
            }
            
            ctx.setMetadata("lucky_hand_triggered", true);
            ctx.markEffectApplied("LUCKY_HAND");
        }
    }
    
    /**
     * DEEP_MINER 효과 적용
     * Y < 0 (딥슬레이트 레벨)에서 광석 채굴 시 추가 드롭
     */
    private void applyDeepMiner(ProcessingContext ctx, int level) {
        // Y 좌표 확인 (블록 위치에서)
        if (ctx.getOriginalLocation() == null) return;
        if (ctx.getOriginalLocation().getBlockY() >= 0) return;
        
        // 광석인지 확인
        if (!isOre(ctx.getOriginalMaterial())) return;
        
        // 레벨당 10% 확률
        double chance = level * 0.10; // 10%/20%/30%
        
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            // 드롭량 +1
            for (ItemStack drop : ctx.getDrops()) {
                drop.setAmount(drop.getAmount() + 1);
            }
            
            ctx.setMetadata("deep_miner_bonus", 1);
            ctx.markEffectApplied("DEEP_MINER");
        }
    }
    
    /**
     * 광석 블록 여부 확인
     */
    private boolean isOre(org.bukkit.Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_ORE") || material == org.bukkit.Material.ANCIENT_DEBRIS;
    }
}
