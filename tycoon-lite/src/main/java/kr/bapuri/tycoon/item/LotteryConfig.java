package kr.bapuri.tycoon.item;

import kr.bapuri.tycoon.enhance.enchant.CustomEnchant;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * LotteryConfig - 뽑기권 설정 로더
 * 
 * config.yml의 lottery 섹션에서 등급별 가중치와 인챈트 풀 로드
 * 
 * <h2>설정 구조</h2>
 * <pre>
 * lottery:
 *   basic:
 *     tiers:
 *       S:
 *         weight: 3
 *         enchants:
 *           - "MENDING:1"
 *           - "INFINITY:1"
 *       A:
 *         weight: 12
 *         enchants:
 *           - "FORTUNE:3"
 * </pre>
 */
public class LotteryConfig {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    
    // 기본 뽑기 설정
    private final List<TierConfig> basicTiers = new ArrayList<>();
    private double basicTotalWeight = 0;
    
    // 특수 뽑기 설정
    private final List<TierConfig> specialTiers = new ArrayList<>();
    private double specialTotalWeight = 0;
    
    public LotteryConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        loadConfig();
    }
    
    /**
     * config.yml에서 lottery 설정 로드
     */
    public void loadConfig() {
        basicTiers.clear();
        specialTiers.clear();
        basicTotalWeight = 0;
        specialTotalWeight = 0;
        
        ConfigurationSection lotterySection = plugin.getConfig().getConfigurationSection("lottery");
        if (lotterySection == null) {
            logger.warning("[LotteryConfig] lottery 섹션이 없습니다. 기본값 사용");
            loadDefaultConfig();
            return;
        }
        
        // basic 로드
        ConfigurationSection basicSection = lotterySection.getConfigurationSection("basic.tiers");
        if (basicSection != null) {
            basicTotalWeight = loadTiers(basicSection, basicTiers, "basic");
        }
        
        // special 로드
        ConfigurationSection specialSection = lotterySection.getConfigurationSection("special.tiers");
        if (specialSection != null) {
            specialTotalWeight = loadTiers(specialSection, specialTiers, "special");
        }
        
        // fallback
        if (basicTiers.isEmpty()) {
            loadDefaultBasic();
        }
        if (specialTiers.isEmpty()) {
            loadDefaultSpecial();
        }
        
        logger.info("[LotteryConfig] 설정 로드: basic=" + basicTiers.size() + "등급, special=" + specialTiers.size() + "등급");
    }
    
    /**
     * 등급 설정 로드
     * [버그수정] getDouble 사용하여 소수점 가중치 지원
     */
    private double loadTiers(ConfigurationSection section, List<TierConfig> tierList, String type) {
        double totalWeight = 0;
        
        for (String tierName : section.getKeys(false)) {
            ConfigurationSection tierSection = section.getConfigurationSection(tierName);
            if (tierSection == null) continue;
            
            // [버그수정] getInt -> getDouble 변경 (0.5 같은 소수점 지원)
            double weight = tierSection.getDouble("weight", 1.0);
            List<String> enchantStrings = tierSection.getStringList("enchants");
            
            List<EnchantEntry> enchants = new ArrayList<>();
            for (String enchStr : enchantStrings) {
                EnchantEntry entry = parseEnchantString(enchStr);
                if (entry != null) {
                    enchants.add(entry);
                }
            }
            
            if (!enchants.isEmpty()) {
                tierList.add(new TierConfig(tierName, weight, enchants));
                totalWeight += weight;
                logger.info("[LotteryConfig] " + type + " 등급 " + tierName + ": weight=" + weight + ", enchants=" + enchants.size());
            }
        }
        
        return totalWeight;
    }
    
    /**
     * 인챈트 문자열 파싱 ("MENDING:1" -> EnchantEntry)
     * 바닐라 인챈트를 못 찾으면 커스텀 인챈트로 시도
     */
    private EnchantEntry parseEnchantString(String str) {
        if (str == null || str.isEmpty()) return null;
        
        String[] parts = str.split(":");
        String enchantName = parts[0].trim();
        int level = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
        
        // 1. 바닐라 인챈트 시도 (대문자로 변환)
        Enchantment enchant = getEnchantmentByName(enchantName.toUpperCase());
        if (enchant != null) {
            return new EnchantEntry(enchant, level, null);
        }
        
        // 2. 커스텀 인챈트 시도 (소문자로 변환)
        CustomEnchant custom = CustomEnchant.fromId(enchantName.toLowerCase());
        if (custom != null) {
            return new EnchantEntry(null, level, custom);
        }
        
        logger.warning("[LotteryConfig] 알 수 없는 인챈트: " + enchantName);
        return null;
    }
    
    /**
     * 인챈트 이름으로 Enchantment 찾기
     */
    private Enchantment getEnchantmentByName(String name) {
        // 직접 매핑 (일반적인 이름)
        switch (name) {
            case "MENDING": return Enchantment.MENDING;
            case "INFINITY": return Enchantment.ARROW_INFINITE;
            case "SILK_TOUCH": return Enchantment.SILK_TOUCH;
            case "FORTUNE": return Enchantment.LOOT_BONUS_BLOCKS;
            case "SHARPNESS": return Enchantment.DAMAGE_ALL;
            case "PROTECTION": return Enchantment.PROTECTION_ENVIRONMENTAL;
            case "POWER": return Enchantment.ARROW_DAMAGE;
            case "LOOTING": return Enchantment.LOOT_BONUS_MOBS;
            case "UNBREAKING": return Enchantment.DURABILITY;
            case "EFFICIENCY": return Enchantment.DIG_SPEED;
            case "FEATHER_FALLING": return Enchantment.PROTECTION_FALL;
            case "FIRE_ASPECT": return Enchantment.FIRE_ASPECT;
            case "KNOCKBACK": return Enchantment.KNOCKBACK;
            case "SMITE": return Enchantment.DAMAGE_UNDEAD;
            case "BANE_OF_ARTHROPODS": return Enchantment.DAMAGE_ARTHROPODS;
            case "THORNS": return Enchantment.THORNS;
            case "RESPIRATION": return Enchantment.OXYGEN;
            case "AQUA_AFFINITY": return Enchantment.WATER_WORKER;
            case "DEPTH_STRIDER": return Enchantment.DEPTH_STRIDER;
            case "FROST_WALKER": return Enchantment.FROST_WALKER;
            case "SOUL_SPEED": return Enchantment.SOUL_SPEED;
            case "SWIFT_SNEAK": return Enchantment.SWIFT_SNEAK;
            case "SWEEPING": case "SWEEPING_EDGE": return Enchantment.SWEEPING_EDGE;
            case "PUNCH": return Enchantment.ARROW_KNOCKBACK;
            case "FLAME": return Enchantment.ARROW_FIRE;
            case "LUCK_OF_THE_SEA": return Enchantment.LUCK;
            case "LURE": return Enchantment.LURE;
            case "CHANNELING": return Enchantment.CHANNELING;
            case "RIPTIDE": return Enchantment.RIPTIDE;
            case "LOYALTY": return Enchantment.LOYALTY;
            case "IMPALING": return Enchantment.IMPALING;
            case "MULTISHOT": return Enchantment.MULTISHOT;
            case "PIERCING": return Enchantment.PIERCING;
            case "QUICK_CHARGE": return Enchantment.QUICK_CHARGE;
            default:
                // Registry에서 찾기 시도
                try {
                    return Enchantment.getByKey(NamespacedKey.minecraft(name.toLowerCase()));
                } catch (Exception e) {
                    return null;
                }
        }
    }
    
    /**
     * 기본 뽑기에서 랜덤 인챈트 선택
     */
    public EnchantEntry rollBasicEnchant() {
        return rollEnchant(basicTiers, basicTotalWeight);
    }
    
    /**
     * 특수 뽑기에서 랜덤 인챈트 선택
     */
    public EnchantEntry rollSpecialEnchant() {
        return rollEnchant(specialTiers, specialTotalWeight);
    }
    
    /**
     * 가중치 기반 랜덤 선택
     * [버그수정] double 사용하여 소수점 가중치 지원
     */
    private EnchantEntry rollEnchant(List<TierConfig> tiers, double totalWeight) {
        if (tiers.isEmpty() || totalWeight <= 0) {
            // fallback: 기본 UNBREAKING 3
            return new EnchantEntry(Enchantment.DURABILITY, 3);
        }
        
        // [버그수정] nextDouble 사용
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        
        for (TierConfig tier : tiers) {
            cumulative += tier.weight;
            if (roll < cumulative) {
                // 해당 등급에서 랜덤 인챈트 선택
                List<EnchantEntry> enchants = tier.enchants;
                return enchants.get(ThreadLocalRandom.current().nextInt(enchants.size()));
            }
        }
        
        // fallback
        TierConfig lastTier = tiers.get(tiers.size() - 1);
        return lastTier.enchants.get(0);
    }
    
    /**
     * 기본 설정 로드 (config 없을 때)
     */
    private void loadDefaultConfig() {
        loadDefaultBasic();
        loadDefaultSpecial();
    }
    
    private void loadDefaultBasic() {
        // S등급
        basicTiers.add(new TierConfig("S", 3, List.of(
            new EnchantEntry(Enchantment.MENDING, 1),
            new EnchantEntry(Enchantment.ARROW_INFINITE, 1),
            new EnchantEntry(Enchantment.SILK_TOUCH, 1)
        )));
        // A등급
        basicTiers.add(new TierConfig("A", 12, List.of(
            new EnchantEntry(Enchantment.LOOT_BONUS_BLOCKS, 3),
            new EnchantEntry(Enchantment.DAMAGE_ALL, 5),
            new EnchantEntry(Enchantment.DURABILITY, 3)
        )));
        // B등급
        basicTiers.add(new TierConfig("B", 35, List.of(
            new EnchantEntry(Enchantment.LOOT_BONUS_BLOCKS, 2),
            new EnchantEntry(Enchantment.DAMAGE_ALL, 3),
            new EnchantEntry(Enchantment.DIG_SPEED, 3)
        )));
        // C등급
        basicTiers.add(new TierConfig("C", 50, List.of(
            new EnchantEntry(Enchantment.LOOT_BONUS_BLOCKS, 1),
            new EnchantEntry(Enchantment.DAMAGE_ALL, 1),
            new EnchantEntry(Enchantment.DIG_SPEED, 1)
        )));
        
        basicTotalWeight = 100;
        logger.info("[LotteryConfig] 기본 basic 설정 로드됨 (4등급)");
    }
    
    private void loadDefaultSpecial() {
        // S등급
        specialTiers.add(new TierConfig("S", 5, List.of(
            new EnchantEntry(Enchantment.MENDING, 1),
            new EnchantEntry(Enchantment.ARROW_INFINITE, 1)
        )));
        // A등급
        specialTiers.add(new TierConfig("A", 20, List.of(
            new EnchantEntry(Enchantment.LOOT_BONUS_BLOCKS, 3),
            new EnchantEntry(Enchantment.DAMAGE_ALL, 5),
            new EnchantEntry(Enchantment.ARROW_DAMAGE, 5)
        )));
        // B등급
        specialTiers.add(new TierConfig("B", 75, List.of(
            new EnchantEntry(Enchantment.DURABILITY, 3),
            new EnchantEntry(Enchantment.PROTECTION_ENVIRONMENTAL, 4),
            new EnchantEntry(Enchantment.DIG_SPEED, 5)
        )));
        
        specialTotalWeight = 100;
        logger.info("[LotteryConfig] 기본 special 설정 로드됨 (3등급)");
    }
    
    /**
     * 리로드
     */
    public void reload() {
        loadConfig();
    }
    
    // ========== 데이터 클래스 ==========
    
    public static class TierConfig {
        final String name;
        final double weight;  // [버그수정] int -> double
        final List<EnchantEntry> enchants;
        
        TierConfig(String name, double weight, List<EnchantEntry> enchants) {
            this.name = name;
            this.weight = weight;
            this.enchants = enchants;
        }
    }
    
    public static class EnchantEntry {
        public final Enchantment enchantment;  // 바닐라 인챈트 (null이면 커스텀)
        public final int level;
        public final CustomEnchant customEnchant;  // 커스텀 인챈트 (null이면 바닐라)
        
        public EnchantEntry(Enchantment enchantment, int level) {
            this(enchantment, level, null);
        }
        
        public EnchantEntry(Enchantment enchantment, int level, CustomEnchant customEnchant) {
            this.enchantment = enchantment;
            this.level = level;
            this.customEnchant = customEnchant;
        }
        
        public boolean isCustom() {
            return customEnchant != null;
        }
    }
}
