package kr.bapuri.tycoon.job.farmer;

import kr.bapuri.tycoon.job.JobGrade;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * FarmerConfig - 농부 직업 설정 로더
 * 
 * Phase 4.C 구현:
 * - jobs.yml의 farmer 섹션에서 설정 로드
 * - 바닐라 작물 중심 (커스텀 작물 제거)
 * - 설정 기반 밸런싱 지원
 */
public class FarmerConfig {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    
    // ===== 기본 설정 =====
    private double levelBonusPercent = 5.5;
    private int maxLevel = 100;
    
    // ===== 농지 제한 =====
    private boolean farmlandLimitEnabled = true;
    private int maxFarmlandPerChunk = 196;
    
    // ===== 등급별 승급 조건 =====
    private final Map<JobGrade, GradeRequirement> gradeRequirements = new EnumMap<>(JobGrade.class);
    
    // ===== XP 보상 =====
    private final Map<Material, Long> expRewards = new EnumMap<>(Material.class);
    
    // ===== 기준 가격 =====
    private final Map<Material, Long> basePrices = new EnumMap<>(Material.class);
    
    // ===== 작물 그레이드 드롭 확률 (stub) =====
    private double normalDropChance = 70.0;
    private double primeDropChance = 25.0;
    private double trophyDropChance = 5.0;
    
    public FarmerConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadFromConfig();  // [Phase 4.E] 생성자에서 자동 로드
    }
    
    /**
     * [Phase 4.E] 설정 리로드 (하이브리드 패턴)
     */
    public void reload() {
        loadFromConfig();
        logger.info("[FarmerConfig] 설정 리로드 완료");
    }
    
    /**
     * 설정 로드
     */
    public void loadFromConfig() {
        gradeRequirements.clear();
        expRewards.clear();
        basePrices.clear();
        
        // jobs.yml 로드
        File jobsFile = new File(plugin.getDataFolder(), "jobs.yml");
        if (!jobsFile.exists()) {
            logger.warning("[FarmerConfig] jobs.yml이 없습니다. 기본값을 사용합니다.");
            setDefaults();
            return;
        }
        
        YamlConfiguration jobsConfig = YamlConfiguration.loadConfiguration(jobsFile);
        ConfigurationSection farmerSection = jobsConfig.getConfigurationSection("farmer");
        
        if (farmerSection == null) {
            logger.warning("[FarmerConfig] 'farmer' 섹션이 없습니다. 기본값을 사용합니다.");
            setDefaults();
            return;
        }
        
        // 기본 설정
        levelBonusPercent = farmerSection.getDouble("levelBonusPercent", 5.5);
        maxLevel = farmerSection.getInt("maxLevel", 100);
        
        // 농지 제한 설정
        farmlandLimitEnabled = farmerSection.getBoolean("farmlandLimitEnabled", true);
        maxFarmlandPerChunk = farmerSection.getInt("maxFarmlandPerChunk", 196);
        
        // 작물 그레이드 드롭 확률
        ConfigurationSection gradeDropSection = farmerSection.getConfigurationSection("gradeDropChance");
        if (gradeDropSection != null) {
            normalDropChance = gradeDropSection.getDouble("normal", 70.0);
            primeDropChance = gradeDropSection.getDouble("prime", 25.0);
            trophyDropChance = gradeDropSection.getDouble("trophy", 5.0);
        }
        
        // 등급별 승급 조건
        loadGradeRequirements(farmerSection.getConfigurationSection("grades"));
        
        // XP 보상
        loadExpRewards(farmerSection.getConfigurationSection("expRewards"));
        
        // 기준 가격
        loadBasePrices(farmerSection.getConfigurationSection("basePrices"));
        
        // 기본값으로 빈 항목 채우기
        setDefaults();
        
        logger.info(String.format("[FarmerConfig] 로드 완료 - levelBonus: %.1f%%, grades: %d, expRewards: %d, basePrices: %d",
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
            req.requiredTotalHarvested = gradeSection.getLong("requiredTotalHarvested", 0);
            req.requiredTotalSoldAmount = gradeSection.getLong("requiredTotalSoldAmount", 0);
            
            gradeRequirements.put(grade, req);
        }
    }
    
    private void loadExpRewards(ConfigurationSection section) {
        if (section == null) return;
        
        for (String key : section.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());
                long xp = section.getLong(key);
                expRewards.put(material, xp);
            } catch (IllegalArgumentException e) {
                // 커스텀 작물 ID는 무시 (Lite에서는 미지원)
            }
        }
    }
    
    private void loadBasePrices(ConfigurationSection section) {
        if (section == null) return;
        
        for (String key : section.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());
                long price = section.getLong(key);
                basePrices.put(material, price);
            } catch (IllegalArgumentException e) {
                // 커스텀 작물 ID는 무시
            }
        }
    }
    
    /**
     * 기본값 설정 (설정 파일에 없는 항목)
     */
    private void setDefaults() {
        // 등급별 기본 승급 조건 (농부는 T2=Lv25, T3=Lv50, T4=Lv90)
        if (!gradeRequirements.containsKey(JobGrade.GRADE_2)) {
            GradeRequirement req = new GradeRequirement();
            req.requiredLevel = 25;
            req.requiredBD = 50000;
            req.requiredTotalSales = 100000;
            gradeRequirements.put(JobGrade.GRADE_2, req);
        }
        if (!gradeRequirements.containsKey(JobGrade.GRADE_3)) {
            GradeRequirement req = new GradeRequirement();
            req.requiredLevel = 50;
            req.requiredBD = 200000;
            req.requiredTotalSales = 500000;
            gradeRequirements.put(JobGrade.GRADE_3, req);
        }
        if (!gradeRequirements.containsKey(JobGrade.GRADE_4)) {
            GradeRequirement req = new GradeRequirement();
            req.requiredLevel = 90;
            req.requiredBD = 1000000;
            req.requiredTotalSales = 2000000;
            gradeRequirements.put(JobGrade.GRADE_4, req);
        }
        
        // 기본 XP 보상 (바닐라 작물)
        setDefaultExpReward(Material.WHEAT, 5);
        setDefaultExpReward(Material.BEETROOT, 4);
        setDefaultExpReward(Material.CARROT, 5);
        setDefaultExpReward(Material.POTATO, 5);
        setDefaultExpReward(Material.MELON_SLICE, 3);
        setDefaultExpReward(Material.PUMPKIN, 10);
        setDefaultExpReward(Material.BROWN_MUSHROOM, 3);
        setDefaultExpReward(Material.RED_MUSHROOM, 3);
        setDefaultExpReward(Material.COCOA_BEANS, 4);
        setDefaultExpReward(Material.SUGAR_CANE, 2);
        setDefaultExpReward(Material.NETHER_WART, 8);
        setDefaultExpReward(Material.SWEET_BERRIES, 3);
        setDefaultExpReward(Material.GLOW_BERRIES, 5);
        setDefaultExpReward(Material.CHORUS_FRUIT, 12);
        setDefaultExpReward(Material.BAMBOO, 1);
        setDefaultExpReward(Material.CACTUS, 2);
        setDefaultExpReward(Material.KELP, 2);
    }
    
    private void setDefaultExpReward(Material material, long xp) {
        if (!expRewards.containsKey(material)) {
            expRewards.put(material, xp);
        }
    }
    
    // ===== Getters =====
    
    public double getLevelBonusPercent() {
        return levelBonusPercent;
    }
    
    public int getMaxLevel() {
        return maxLevel;
    }
    
    public boolean isFarmlandLimitEnabled() {
        return farmlandLimitEnabled;
    }
    
    public void setFarmlandLimitEnabled(boolean enabled) {
        this.farmlandLimitEnabled = enabled;
    }
    
    public int getMaxFarmlandPerChunk() {
        return maxFarmlandPerChunk;
    }
    
    /**
     * 작물 Material에 대한 XP 보상
     */
    public long getExpReward(Material material) {
        return expRewards.getOrDefault(material, 0L);
    }
    
    /**
     * 해당 Material이 XP 보상이 있는 작물인지
     */
    public boolean isCrop(Material material) {
        return expRewards.containsKey(material);
    }
    
    /**
     * 기준 가격 조회
     */
    public long getBasePrice(Material material) {
        return basePrices.getOrDefault(material, 0L);
    }
    
    /**
     * 레벨 적용 가격 계산
     * 공식: basePrice × (1 + levelBonusPercent/100)^level (복리)
     */
    public long getActualPrice(Material material, int level) {
        long basePrice = getBasePrice(material);
        if (basePrice <= 0) return 0;
        
        double multiplier = Math.pow(1.0 + levelBonusPercent / 100.0, level);
        return Math.round(basePrice * multiplier);
    }
    
    /**
     * 등급별 승급 조건 조회
     */
    public GradeRequirement getGradeRequirement(JobGrade grade) {
        return gradeRequirements.get(grade);
    }
    
    // ===== 작물 그레이드 드롭 확률 Getters =====
    
    public double getNormalDropChance() {
        return normalDropChance;
    }
    
    public double getPrimeDropChance() {
        return primeDropChance;
    }
    
    public double getTrophyDropChance() {
        return trophyDropChance;
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
        public long requiredTotalHarvested = 0;      // 총 수확량
        public long requiredTotalSoldAmount = 0;     // 직업별 판매액 (통일된 필드명)
        
        @Override
        public String toString() {
            return String.format("GradeReq{level=%d, bd=%d, sales=%d, harvested=%d, soldAmount=%d}",
                    requiredLevel, requiredBD, requiredTotalSales, requiredTotalHarvested, requiredTotalSoldAmount);
        }
    }
}
