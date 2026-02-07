package kr.bapuri.tycoon.enhance.processing.processors;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.lamp.LampEffect;
import kr.bapuri.tycoon.enhance.lamp.LampSlotData;
import kr.bapuri.tycoon.enhance.processing.EffectProcessor;
import kr.bapuri.tycoon.enhance.processing.ProcessingContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;

/**
 * DeliveryProcessor - 드롭 전달 및 블록 제거 처리기
 * 
 * Priority: 900 (가장 마지막에 실행)
 * 
 * 처리:
 * 1. MIDAS_TOUCH BD 지급
 * 2. TELEKINESIS 인챈트/램프 확인
 * 3. 드롭 아이템 전달 (인벤토리 또는 월드)
 * 4. 블록 제거 (setType(AIR))
 */
public class DeliveryProcessor implements EffectProcessor {
    
    private final EconomyService economyService;
    
    public DeliveryProcessor(EconomyService economyService) {
        this.economyService = economyService;
    }
    
    @Override
    public String getName() {
        return "Delivery";
    }
    
    @Override
    public int getPriority() {
        return 900;
    }
    
    @Override
    public boolean shouldProcess(ProcessingContext ctx) {
        return ctx.getOptions().isAutoDeliver() && !ctx.isDelivered();
    }
    
    @Override
    public void process(ProcessingContext ctx) {
        Player player = ctx.getPlayer();
        Location dropLocation = ctx.getOriginalLocation().clone().add(0.5, 0.5, 0.5);
        
        // MIDAS_TOUCH BD 지급
        if (ctx.hasMetadata("midas_touch_triggered")) {
            int bd = ctx.getMetadata("midas_touch_bd", 0);
            if (bd > 0 && economyService != null) {
                economyService.deposit(player.getUniqueId(), bd);
                player.sendMessage("§6[미다스] §e+" + bd + " BD §7획득!");
            }
        }
        
        // 바닐라 경험치 지급 (광석)
        int vanillaExp = getOreExp(ctx.getOriginalMaterial());
        if (vanillaExp > 0) {
            player.giveExp(vanillaExp);
        }
        
        // TELEKINESIS 확인 (인챈트 또는 램프)
        boolean telekinesis = hasTelekinesis(ctx.getTool());
        
        // 드롭 전달
        for (ItemStack drop : ctx.getDrops()) {
            if (drop == null || drop.getAmount() <= 0) continue;
            
            if (telekinesis) {
                // 인벤토리로 직접 이동
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(drop.clone());
                
                // 인벤토리 가득 차면 월드에 드롭
                for (ItemStack leftover : overflow.values()) {
                    ctx.getBlock().getWorld().dropItemNaturally(dropLocation, leftover);
                }
            } else {
                // 월드에 드롭
                ctx.getBlock().getWorld().dropItemNaturally(dropLocation, drop.clone());
            }
        }
        
        ctx.setDelivered(true);
        
        if (telekinesis) {
            ctx.markEffectApplied("TELEKINESIS");
        }
        ctx.markEffectApplied("DELIVERY");
        
        // 블록 제거
        if (ctx.getOptions().isRemoveBlock() && !ctx.isBlockRemoved()) {
            ctx.getBlock().setType(Material.AIR);
            ctx.setBlockRemoved(true);
        }
    }
    
    /**
     * 광석 파괴 시 바닐라 경험치
     */
    private int getOreExp(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 1;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 1;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 1;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> 2;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 2;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 3;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 5;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 5;
            case NETHER_QUARTZ_ORE -> 2;
            case ANCIENT_DEBRIS -> 0; // Ancient debris는 XP 없음
            default -> 0;
        };
    }
    
    /**
     * TELEKINESIS 효과 보유 여부 확인
     * 인챈트 또는 램프 효과 확인
     */
    private boolean hasTelekinesis(ItemStack tool) {
        if (tool == null) return false;
        
        // 인챈트 확인
        if (EnhanceItemUtil.hasCustomEnchant(tool, "telekinesis")) {
            return true;
        }
        
        // 램프 효과 확인 (MAGNETIC_FIELD - 비활성화됨, 하지만 호환성 위해 체크)
        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(tool);
        if (slots != null) {
            for (LampSlotData slot : slots) {
                if (slot != null && slot.getEffectId() != null) {
                    LampEffect effect = LampEffect.fromId(slot.getEffectId());
                    if (effect == LampEffect.MAGNETIC_FIELD) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
}
