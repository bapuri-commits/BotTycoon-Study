package kr.bapuri.tycoon.job.fisher;

import kr.bapuri.tycoon.job.JobGrade;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * FisherConfig - 어부 직업 설정 로더
 * 
 * Phase 4.D:
 * - jobs.yml의 fisher 섹션에서 설정 로드
 * - Town/Wild별 희귀도 분포 설정
 * - 승급 조건: level + BD + totalFished + fisherSales
 */
public class FisherConfig {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    
    // ===== 기본 설정 =====
    private double levelBonusPercent = 6.0;
    private int maxLevel = 100;
    
    // ===== 등급별 승급 조건 =====
    private final Map<JobGrade, GradeRequirement> gradeRequirements = new EnumMap<>(JobGrade.class);
    
    // ===== XP 보상 =====
    private final Map<Material, Long> expRewards = new EnumMap<>(Material.class);
    
    // ===== 기준 가격 =====
    private final Map<Material, Long> basePrices = new EnumMap<>(Material.class);
    
    // ===== Town 희귀도 분포 (%) =====
    private double townCommonChance = 60.0;
    private double townUncommonChance = 25.0;
    private double townRareChance = 10.0;
    private double townEpicChance = 4.0;
    private double townLegendaryChance = 1.0;
    
    // ===== Wild 희귀도 분포 (%) =====
    private double wildCommonChance = 40.0;
    private double wildUncommonChance = 30.0;
    private double wildRareChance = 18.0;
    private double wildEpicChance = 9.0;
    private double wildLegendaryChance = 3.0;
    
    // ===== Pity 설정 (stub) =====
    private boolean pityEnabled = false;
    private int pityRareThreshold = 50;
    
    public FisherConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadFromConfig();  // [Phase 4.E] 생성자에서 자동 로드
    }
    
    /**
     * [Phase 4.E] 설정 리로드 (하이브리드 패턴)
     */
    public void reload() {
        loadFromConfig();
        logger.info("[FisherConfig] 설정 리로드 완료");
    }
    
    /**
     * 설정 로드
     */
    public void loadFromConfig() {
        gradeRequirements.clear();
        expRewards.clear();
        basePrices.clear();
        
        File jobsFile = new File(plugin.getDataFolder(), "jobs.yml");
        if (!jobsFile.exists()) {
            logger.warning("[FisherConfig] jobs.yml이 없습니다. 기본값을 사용합니다.");
            setDefaults();
            return;
        }
        
        YamlConfiguration jobsConfig = YamlConfiguration.loadConfiguration(jobsFile);
        ConfigurationSection fisherSection = jobsConfig.getConfigurationSection("fisher");
        
        if (fisherSection == null) {
            logger.warning("[FisherConfig] 'fisher' 섹션이 없습니다. 기본값을 사용합니다.");
            setDefaults();
            return;
        }
        
        // 기본 설정
        levelBonusPercent = fisherSection.getDouble("levelBonusPercent", 6.0);
        maxLevel = fisherSection.getInt("maxLevel", 100);
        
        // Town 희귀도 분포
        ConfigurationSection townRarity = fisherSection.getConfigurationSection("rarityDistribution.town");
        if (townRarity != null) {
            townCommonChance = townRarity.getDouble("common", 60.0);
            townUncommonChance = townRarity.getDouble("uncommon", 25.0);
            townRareChance = townRarity.getDouble("rare", 10.0);
            townEpicChance = townRarity.getDouble("epic", 4.0);
            townLegendaryChance = townRarity.getDouble("legendary", 1.0);
        }
        
        // Wild 희귀도 분포
        ConfigurationSection wildRarity = fisherSection.getConfigurationSection("rarityDistribution.wild");
        if (wildRarity != null) {
            wildCommonChance = wildRarity.getDouble("common", 40.0);
            wildUncommonChance = wildRarity.getDouble("uncommon", 30.0);
            wildRareChance = wildRarity.getDouble("rare", 18.0);
            wildEpicChance = wildRarity.getDouble("epic", 9.0);
            wildLegendaryChance = wildRarity.getDouble("legendary", 3.0);
        }
        
        // Pity 설정 (stub)
        ConfigurationSection pitySection = fisherSection.getConfigurationSection("pity");
        if (pitySection != null) {
            pityEnabled = pitySection.getBoolean("enabled", false);
            pityRareThreshold = pitySection.getInt("rareThreshold", 50);
        }
        
        // 등급별 승급 조건
        loadGradeRequirements(fisherSection.getConfigurationSection("grades"));
        
        // XP 보상
        loadExpRewards(fisherSection.getConfigurationSection("expRewards"));
        
        // 기준 가격
        loadBasePrices(fisherSection.getConfigurationSection("basePrices"));
        
        // 기본값으로 빈 항목 채우기
        setDefaults();
        
        logger.info(String.format("[FisherConfig] 로드 완료 - levelBonus: %.1f%%, grades: %d, expRewards: %d, basePrices: %d",
                levelBonusPercent, gradeRequirements.size(), expRewards.size(), basePrices.size()));
    }
    
    private void loadGradeRequirements(ConfigurationSection section) {
        if (section == null) return;
        
        for (String gradeKey : section.getKeys(false)) {
            JobGrade grade = JobGrade.fromConfigKey(gradeKey);
            if (grade == null || grade == JobGrade.GRADE_1) continue;
            
            ConfigurationSection gradeSection = section.getConfigurationSection(gradeKey);
            if (gradeSection == null) continue;
            
            GradeRequirement req = new GradeRequirement();
            req.requiredLevel = gradeSection.getInt("requiredLevel", grade.getRequiredLevel());
            req.requiredBD = gradeSection.getLong("requiredMoney", 
                    gradeSection.getLong("requiredBD", 0));
            req.requiredTotalSales = gradeSection.getLong("requiredTotalSales", 0);
            
            // [Phase 4.E] 행동량 + 직업별 판매액 조건 로드
            req.requiredTotalFished = gradeSection.getLong("requiredTotalFished", 0);
            req.requiredTotalSoldAmount = gradeSection.getLong("requiredTotalSoldAmount", 0);
            
            gradeRequirements.put(grade, req);
        }
    }
    
    private void loadExpRewards(ConfigurationSection section) {
        if (section == null) return;
        
        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                long xp = section.getLong(key, 0);
                if (xp > 0) {
                    expRewards.put(mat, xp);
                }
            } catch (IllegalArgumentException e) {
                logger.warning("[FisherConfig] 알 수 없는 Material: " + key);
            }
        }
    }
    
    private void loadBasePrices(ConfigurationSection section) {
        if (section == null) return;
        
        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                long price = section.getLong(key, 0);
                if (price > 0) {
                    basePrices.put(mat, price);
                }
            } catch (IllegalArgumentException e) {
                logger.warning("[FisherConfig] 알 수 없는 Material: " + key);
            }
        }
    }
    
    /**
     * 기본값 설정 (설정 파일에 없는 항목 채우기)
     */
    private void setDefaults() {
        // 기본 XP 보상
        expRewards.putIfAbsent(Material.COD, 10L);
        expRewards.putIfAbsent(Material.SALMON, 15L);
        expRewards.putIfAbsent(Material.TROPICAL_FISH, 25L);
        expRewards.putIfAbsent(Material.PUFFERFISH, 20L);
        
        // 기본 가격
        basePrices.putIfAbsent(Material.COD, 5L);
        basePrices.putIfAbsent(Material.SALMON, 8L);
        basePrices.putIfAbsent(Material.TROPICAL_FISH, 15L);
        basePrices.putIfAbsent(Material.PUFFERFISH, 12L);
    }
    
    // ===== Getters =====
    
    public double getLevelBonusPercent() {
        return levelBonusPercent;
    }
    
    public int getMaxLevel() {
        return maxLevel;
    }
    
    public GradeRequirement getGradeRequirement(JobGrade grade) {
        return gradeRequirements.get(grade);
    }
    
    public long getExpReward(Material material) {
        return expRewards.getOrDefault(material, 0L);
    }
    
    public long getBasePrice(Material material) {
        return basePrices.getOrDefault(material, 0L);
    }
    
    public Map<Material, Long> getBasePrices() {
        return Collections.unmodifiableMap(basePrices);
    }
    
    // ===== 희귀도 분포 Getters =====
    
    public double getTownChance(FishRarity rarity) {
        return switch (rarity) {
            case COMMON -> townCommonChance;
            case UNCOMMON -> townUncommonChance;
            case RARE -> townRareChance;
            case EPIC -> townEpicChance;
            case LEGENDARY -> townLegendaryChance;
        };
    }
    
    public double getWildChance(FishRarity rarity) {
        return switch (rarity) {
            case COMMON -> wildCommonChance;
            case UNCOMMON -> wildUncommonChance;
            case RARE -> wildRareChance;
            case EPIC -> wildEpicChance;
            case LEGENDARY -> wildLegendaryChance;
        };
    }
    
    // ===== Pity Getters (stub) =====
    
    public boolean isPityEnabled() {
        return pityEnabled;
    }
    
    public int getPityRareThreshold() {
        return pityRareThreshold;
    }
    
    // ===== Inner Classes =====
    
    /**
     * 등급별 승급 조건
     * [Phase 4.E] 필드명 통일: requiredTotalSoldAmount
     */
    public static class GradeRequirement {
        public int requiredLevel = 0;
        public long requiredBD = 0;
        public long requiredTotalSales = 0;          // 총 판매액 (레거시 호환)
        public long requiredTotalFished = 0;         // 총 낚시량
        public long requiredTotalSoldAmount = 0;     // 직업별 판매액 (통일된 필드명)
        
        @Override
        public String toString() {
            return String.format("GradeReq{level=%d, bd=%d, sales=%d, fished=%d, soldAmount=%d}",
                    requiredLevel, requiredBD, requiredTotalSales, requiredTotalFished, requiredTotalSoldAmount);
        }
    }
}
