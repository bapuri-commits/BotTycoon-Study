package kr.bapuri.tycoon.enhance.upgrade;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * UpgradeStatCalculator - 강화 스탯 계산기
 * 
 * 강화 레벨에 따른 공격력/방어력 보너스 계산
 * 
 * Phase 6 LITE: 레거시 버전 이식
 */
public class UpgradeStatCalculator {

    private final UpgradeConfig config;

    public UpgradeStatCalculator(UpgradeConfig config) {
        this.config = config;
    }

    /**
     * 무기 공격력 보너스 (%)
     */
    public double getWeaponDamageBonus(ItemStack weapon) {
        if (weapon == null || !EnhanceItemUtil.isWeapon(weapon)) return 0;

        int level = EnhanceItemUtil.getUpgradeLevel(weapon);
        UpgradeLevel upgradeLevel = config.getLevel(level);
        
        return upgradeLevel != null ? upgradeLevel.getDamageBonus() : 0;
    }

    /**
     * 방어구 방어력 보너스 총합 (%)
     */
    public double getTotalArmorDefenseBonus(Player player) {
        double totalBonus = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && EnhanceItemUtil.isArmor(armor)) {
                int level = EnhanceItemUtil.getUpgradeLevel(armor);
                UpgradeLevel upgradeLevel = config.getLevel(level);
                if (upgradeLevel != null) {
                    totalBonus += upgradeLevel.getDefenseBonus();
                }
            }
        }

        return totalBonus;
    }

    /**
     * 개별 방어구 방어력 보너스 (%)
     */
    public double getArmorDefenseBonus(ItemStack armor) {
        if (armor == null || !EnhanceItemUtil.isArmor(armor)) return 0;

        int level = EnhanceItemUtil.getUpgradeLevel(armor);
        UpgradeLevel upgradeLevel = config.getLevel(level);
        
        return upgradeLevel != null ? upgradeLevel.getDefenseBonus() : 0;
    }

    /**
     * 피해량 계산 (기본 피해 * 보너스 적용)
     */
    public double calculateDamage(double baseDamage, ItemStack weapon) {
        double bonus = getWeaponDamageBonus(weapon);
        return baseDamage * (1 + bonus / 100.0);
    }

    /**
     * 받는 피해 계산 (기본 피해 - 방어력 보너스)
     */
    public double calculateDamageReduction(double baseDamage, Player player) {
        double bonus = getTotalArmorDefenseBonus(player);
        double reduction = baseDamage * (bonus / 100.0);
        return Math.max(0, baseDamage - reduction);
    }

    /**
     * 방어구 체력 보너스 총합 (%)
     */
    public double getTotalArmorHealthBonus(Player player) {
        double totalBonus = 0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && EnhanceItemUtil.isArmor(armor)) {
                int level = EnhanceItemUtil.getUpgradeLevel(armor);
                UpgradeLevel upgradeLevel = config.getLevel(level);
                if (upgradeLevel != null) {
                    totalBonus += upgradeLevel.getHealthBonus();
                }
            }
        }

        return totalBonus;
    }

    /**
     * 개별 방어구 체력 보너스 (%)
     */
    public double getArmorHealthBonus(ItemStack armor) {
        if (armor == null || !EnhanceItemUtil.isArmor(armor)) return 0;

        int level = EnhanceItemUtil.getUpgradeLevel(armor);
        UpgradeLevel upgradeLevel = config.getLevel(level);
        
        return upgradeLevel != null ? upgradeLevel.getHealthBonus() : 0;
    }

    /**
     * 플레이어 최대 체력 계산 (기본 20 + 보너스)
     */
    public double calculateMaxHealth(Player player) {
        double baseHealth = 20.0; // 바닐라 기본 체력
        double bonus = getTotalArmorHealthBonus(player);
        return baseHealth * (1 + bonus / 100.0);
    }

    /**
     * 플레이어의 총 강화 스탯 정보
     */
    public String getPlayerUpgradeStats(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6§l===== 강화 스탯 =====\n");

        // 무기
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (EnhanceItemUtil.isWeapon(weapon)) {
            int level = EnhanceItemUtil.getUpgradeLevel(weapon);
            double bonus = getWeaponDamageBonus(weapon);
            sb.append("§7무기: §6+").append(level);
            sb.append(" §7(공격력 §a+").append(String.format("%.1f", bonus)).append("%§7)\n");
        }

        // 방어구
        ItemStack[] armors = player.getInventory().getArmorContents();
        String[] armorNames = {"부츠", "레깅스", "갑옷", "투구"};
        
        for (int i = 0; i < armors.length; i++) {
            if (armors[i] != null && EnhanceItemUtil.isArmor(armors[i])) {
                int level = EnhanceItemUtil.getUpgradeLevel(armors[i]);
                double bonus = getArmorDefenseBonus(armors[i]);
                sb.append("§7").append(armorNames[i]).append(": §6+").append(level);
                sb.append(" §7(방어력 §a+").append(String.format("%.1f", bonus)).append("%§7)\n");
            }
        }

        // 총 방어력 보너스
        double totalDefense = getTotalArmorDefenseBonus(player);
        sb.append("\n§7총 방어력 보너스: §a+").append(String.format("%.1f", totalDefense)).append("%");

        return sb.toString();
    }
}
