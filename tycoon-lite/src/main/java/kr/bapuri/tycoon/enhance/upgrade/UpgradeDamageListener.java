package kr.bapuri.tycoon.enhance.upgrade;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * UpgradeDamageListener - 강화 스탯 적용 리스너
 * 
 * 전투 시 강화 레벨에 따른 공격력/방어력 보너스 적용
 * 
 * Phase 6 LITE: 레거시 버전 이식
 * [버그수정] 활/석궁 원거리 공격 시에도 강화 보너스 적용
 */
public class UpgradeDamageListener implements Listener {

    private final UpgradeStatCalculator statCalculator;

    public UpgradeDamageListener(UpgradeStatCalculator statCalculator) {
        this.statCalculator = statCalculator;
    }

    /**
     * 플레이어 공격 시 공격력 보너스 적용
     * 
     * [버그수정] 화살/투사체 공격 시에도 활/석궁의 강화 레벨 적용
     * [버그수정] 자기 자신을 공격하는 경우 스킵 (자해 버그 방지)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        Player player;
        ItemStack weapon;
        
        // 직접 공격 (근접)
        if (event.getDamager() instanceof Player directAttacker) {
            player = directAttacker;
            weapon = player.getInventory().getItemInMainHand();
        }
        // 투사체 공격 (화살, 삼지창 등)
        else if (event.getDamager() instanceof Projectile projectile && 
                 projectile.getShooter() instanceof Player shooter) {
            player = shooter;
            // 활/석궁 확인 - 손에 들고 있는 무기 사용
            weapon = player.getInventory().getItemInMainHand();
            Material type = weapon.getType();
            // 활/석궁이 아니면 오프핸드 확인
            if (type != Material.BOW && type != Material.CROSSBOW && type != Material.TRIDENT) {
                weapon = player.getInventory().getItemInOffHand();
                type = weapon.getType();
                if (type != Material.BOW && type != Material.CROSSBOW && type != Material.TRIDENT) {
                    return; // 활/석궁/삼지창이 없으면 스킵
                }
            }
        }
        else {
            return;
        }
        
        // [버그수정] 자기 자신을 공격하는 경우 스킵 (자해 버그 방지)
        if (event.getEntity().equals(player)) {
            return;
        }

        if (weapon == null || weapon.getType() == Material.AIR) return;

        // 강화 아이템이 아니면 스킵
        if (!EnhanceItemUtil.isUpgradeable(weapon)) return;
        if (EnhanceItemUtil.getUpgradeLevel(weapon) <= 0) return;

        // 공격력 보너스 적용
        double baseDamage = event.getDamage();
        double newDamage = statCalculator.calculateDamage(baseDamage, weapon);
        
        event.setDamage(newDamage);
    }

    /**
     * 플레이어 피격 시 방어력 보너스 적용
     * 
     * [Phase 8 버그수정] 방어구가 막을 수 없는 데미지 타입 제외
     * - 낙하 데미지, 익사, 질식, 독, 마법, 배고픔 등
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        
        // [Phase 8] 방어구가 막을 수 없는 데미지 타입 제외
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (isArmorBypassDamage(cause)) return;

        // 강화된 방어구가 있는지 확인
        boolean hasUpgradedArmor = false;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && EnhanceItemUtil.isArmor(armor) && 
                EnhanceItemUtil.getUpgradeLevel(armor) > 0) {
                hasUpgradedArmor = true;
                break;
            }
        }

        if (!hasUpgradedArmor) return;

        // 방어력 보너스 적용
        double baseDamage = event.getDamage();
        double newDamage = statCalculator.calculateDamageReduction(baseDamage, player);
        
        event.setDamage(newDamage);
    }
    
    /**
     * [Phase 8] 방어구가 막을 수 없는 데미지 타입인지 확인
     * 
     * 마인크래프트에서 방어구는 다음 데미지 타입을 막을 수 없음:
     * - FALL: 낙하 데미지 (Feather Falling 인챈트로만 감소)
     * - DROWNING: 익사
     * - SUFFOCATION: 질식 (블록에 끼임)
     * - STARVATION: 배고픔
     * - POISON: 독
     * - WITHER: 위더 효과
     * - MAGIC: 순수 마법 데미지
     * - VOID: 보이드 데미지
     * - FIRE_TICK: 화염 지속 데미지 (일부 방어 가능하지만 여기서는 제외)
     */
    private boolean isArmorBypassDamage(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case FALL, DROWNING, SUFFOCATION, STARVATION, 
                 POISON, WITHER, MAGIC, VOID, FIRE_TICK -> true;
            default -> false;
        };
    }
}
