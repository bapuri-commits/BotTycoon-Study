package kr.bapuri.tycoon.enhance.durability;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.common.EnhanceLoreBuilder;
import kr.bapuri.tycoon.enhance.enchant.CustomEnchant;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * DurabilityListener - 내구도 시스템 통합 리스너
 * 
 * Phase 6 LITE: 레거시 버전 이식
 * - LampEffect 의존성 제거 (vanilla-plus 전용)
 * 
 * 내구도 소모 감소 효과 처리:
 * 1. 강화 레벨 보너스 (레벨당 +2% 최대 내구도 → 소모 감소로 환산)
 * 2. 내구도 달인 인챈트 (50% 소모 감소)
 */
public class DurabilityListener implements Listener {

    private final Plugin plugin;
    private final Random random = new Random();

    public DurabilityListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        int originalDamage = event.getDamage();
        double reductionRate = 0.0;

        // 1. 강화 레벨 보너스 (내구도 증가를 소모 감소로 환산)
        // 100강 = +200% 내구도 → 약 66.7% 소모 감소 (1 / 3 = 0.333 소모)
        int upgradeLevel = EnhanceItemUtil.getUpgradeLevel(item);
        if (upgradeLevel > 0) {
            double durabilityBonus = EnhanceLoreBuilder.calculateDurabilityBonus(upgradeLevel);
            // 내구도 +X% → 소모율 = 1 / (1 + X/100)
            // 예: +200% → 소모율 = 1/3 = 0.333 → 감소율 = 0.667
            double multiplier = 1.0 / (1.0 + durabilityBonus / 100.0);
            reductionRate += (1.0 - multiplier);
        }

        // 2. 내구도 달인 인챈트 (50% 소모 감소)
        if (EnhanceItemUtil.hasCustomEnchant(item, CustomEnchant.DURABILITY.getId())) {
            // 50% 확률로 내구도 소모 무시
            if (random.nextDouble() < 0.5) {
                event.setCancelled(true);
                return;
            }
        }

        // 최종 소모량 계산
        if (reductionRate > 0) {
            int reducedDamage = (int) Math.round(originalDamage * (1 - reductionRate));
            reducedDamage = Math.max(0, reducedDamage);
            
            if (reducedDamage == 0) {
                event.setCancelled(true);
            } else {
                event.setDamage(reducedDamage);
            }
        }
    }
}
