package kr.bapuri.tycoon.enhance.upgrade;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * UpgradeConfig - 강화 시스템 설정
 * 
 * config.yml에서 강화 확률 테이블 로드
 * 
 * Phase 6 LITE: 레거시 버전 이식
 */
public class UpgradeConfig {

    private final JavaPlugin plugin;
    private final Logger logger;

    // 레벨별 설정 (0 ~ 100)
    private final Map<Integer, UpgradeLevel> levelConfigs = new HashMap<>();

    // 전역 설정
    private boolean enabled = true;
    private long baseCost = 100;
    private double costMultiplier = 1.15;

    public UpgradeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * config.yml에서 강화 설정 로드
     */
    public void loadFromConfig() {
        levelConfigs.clear();
        FileConfiguration config = plugin.getConfig();

        // 기본 레벨 설정 생성 (0 ~ 100)
        for (int i = 0; i <= EnhanceConstants.MAX_UPGRADE_LEVEL; i++) {
            levelConfigs.put(i, new UpgradeLevel(i));
        }

        ConfigurationSection upgradeSection = config.getConfigurationSection("enhance.upgrade");
        if (upgradeSection == null) {
            logger.info("[UpgradeConfig] config에 'enhance.upgrade' 없음. 기본값 사용.");
            return;
        }

        // 전역 설정
        enabled = upgradeSection.getBoolean("enabled", true);
        baseCost = upgradeSection.getLong("baseCost", 100);
        costMultiplier = upgradeSection.getDouble("costMultiplier", 1.15);

        // 레벨별 오버라이드
        ConfigurationSection levelsSection = upgradeSection.getConfigurationSection("levels");
        if (levelsSection != null) {
            for (String levelKey : levelsSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(levelKey);
                    if (level < 0 || level > EnhanceConstants.MAX_UPGRADE_LEVEL) continue;

                    ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);
                    if (levelSection != null) {
                        UpgradeLevel upgradeLevel = levelConfigs.get(level);
                        loadLevelConfig(upgradeLevel, levelSection);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 범위 설정 (ranges)
        ConfigurationSection rangesSection = upgradeSection.getConfigurationSection("ranges");
        if (rangesSection != null) {
            for (String rangeKey : rangesSection.getKeys(false)) {
                loadRangeConfig(rangeKey, rangesSection.getConfigurationSection(rangeKey));
            }
        }

        logger.info("[UpgradeConfig] 강화 설정 로드 완료 (0~" + EnhanceConstants.MAX_UPGRADE_LEVEL + " 레벨)");
    }

    private void loadLevelConfig(UpgradeLevel upgradeLevel, ConfigurationSection section) {
        if (section.contains("successRate")) {
            upgradeLevel.setSuccessRate(section.getDouble("successRate"));
        }
        if (section.contains("downgradeRate")) {
            upgradeLevel.setDowngradeRate(section.getDouble("downgradeRate"));
        }
        if (section.contains("destroyRate")) {
            upgradeLevel.setDestroyRate(section.getDouble("destroyRate"));
        }
        if (section.contains("cost")) {
            upgradeLevel.setCost(section.getLong("cost"));
        }
        if (section.contains("damageBonus")) {
            upgradeLevel.setDamageBonus(section.getDouble("damageBonus"));
        }
        if (section.contains("defenseBonus")) {
            upgradeLevel.setDefenseBonus(section.getDouble("defenseBonus"));
        }
    }

    private void loadRangeConfig(String rangeKey, ConfigurationSection section) {
        if (section == null) return;

        // "0-10", "11-30" 형식 파싱
        String[] parts = rangeKey.split("-");
        if (parts.length != 2) return;

        try {
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);

            for (int level = start; level <= end && level <= EnhanceConstants.MAX_UPGRADE_LEVEL; level++) {
                UpgradeLevel upgradeLevel = levelConfigs.get(level);
                if (upgradeLevel != null) {
                    loadLevelConfig(upgradeLevel, section);
                }
            }
        } catch (NumberFormatException ignored) {}
    }

    // ========== 조회 메서드 ==========

    /**
     * 특정 레벨의 설정 가져오기
     */
    public UpgradeLevel getLevel(int level) {
        level = Math.max(0, Math.min(EnhanceConstants.MAX_UPGRADE_LEVEL, level));
        return levelConfigs.get(level);
    }

    /**
     * 다음 레벨 강화 시 설정 (현재 레벨 -> 현재 레벨 + 1)
     */
    public UpgradeLevel getNextLevelConfig(int currentLevel) {
        return getLevel(currentLevel + 1);
    }

    /**
     * 강화 비용 (현재 레벨에서 다음 레벨로)
     */
    public long getUpgradeCost(int currentLevel) {
        UpgradeLevel next = getNextLevelConfig(currentLevel);
        return next != null ? next.getCost() : Long.MAX_VALUE;
    }

    /**
     * 성공 확률
     */
    public double getSuccessRate(int currentLevel) {
        UpgradeLevel next = getNextLevelConfig(currentLevel);
        return next != null ? next.getSuccessRate() : 0;
    }

    /**
     * 하락 확률
     */
    public double getDowngradeRate(int currentLevel) {
        UpgradeLevel next = getNextLevelConfig(currentLevel);
        return next != null ? next.getDowngradeRate() : 0;
    }

    /**
     * 파괴 확률
     */
    public double getDestroyRate(int currentLevel) {
        UpgradeLevel next = getNextLevelConfig(currentLevel);
        return next != null ? next.getDestroyRate() : 0;
    }

    /**
     * 유지 확률
     */
    public double getMaintainRate(int currentLevel) {
        UpgradeLevel next = getNextLevelConfig(currentLevel);
        return next != null ? next.getMaintainRate() : 1.0;
    }

    /**
     * 강화 가능 여부
     */
    public boolean canUpgrade(int currentLevel) {
        return enabled && currentLevel < EnhanceConstants.MAX_UPGRADE_LEVEL;
    }

    /**
     * 시스템 활성화 여부
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 모든 레벨 설정 맵
     */
    public Map<Integer, UpgradeLevel> getAllLevels() {
        return new HashMap<>(levelConfigs);
    }
}
