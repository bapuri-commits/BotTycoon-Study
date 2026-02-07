package kr.bapuri.tycoon.job.miner;

import kr.bapuri.tycoon.job.JobGrade;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * MinerConfig - 광부 직업 설정 로더
 * 
 * Phase 4.B 리팩토링:
 * - jobs.yml의 miner 섹션에서 설정 로드
 * - 레거시 대비 간소화 (커스텀 광물 제거)
 * - 설정 기반 밸런싱 지원
 */
public class MinerConfig {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    
    // ===== 기본 설정 =====
    private double levelBonusPercent = 7.0;
    private int maxLevel = 100;
    
    // ===== 등급별 승급 조건 =====
    private final Map<JobGrade, GradeRequirement> gradeRequirements = new EnumMap<>(JobGrade.class);
    
    // ===== XP 보상 =====
    private final Map<Material, Long> expRewards = new EnumMap<>(Material.class);
    
    // ===== 기준 가격 =====
    private final Map<Material, Long> basePrices = new EnumMap<>(Material.class);
    
    // ===== 산화 구리 설정 =====
    private boolean oxidationEnabled = true;
    private int oxidationCheckIntervalMinutes = 5;
    private double oxidationChance = 0.10;  // 10%
    private int farmPreventionRadius = 4;
    
    // ===== 정제소 설정 (stub) =====
    private boolean refineryEnabled = false;
    
    public MinerConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadFromConfig();  // [Phase 4.E] 생성자에서 자동 로드
    }
    
    /**
     * [Phase 4.E] 설정 리로드 (하이브리드 패턴)
     */
    public void reload() {
        loadFromConfig();
        logger.info("[MinerConfig] 설정 리로드 완료");
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
            logger.warning("[MinerConfig] jobs.yml이 없습니다. 기본값을 사용합니다.");
            setDefaults();
            return;
        }
        
        YamlConfiguration jobsConfig = YamlConfiguration.loadConfiguration(jobsFile);
        ConfigurationSection minerSection = jobsConfig.getConfigurationSection("miner");
        
        if (minerSection == null) {
            logger.warning("[MinerConfig] 'miner' 섹션이 없습니다. 기본값을 사용합니다.");
            setDefaults();
            return;
        }
        
        // 기본 설정
        levelBonusPercent = minerSection.getDouble("levelBonusPercent", 7.0);
        maxLevel = minerSection.getInt("maxLevel", 100);
        
        // 등급별 승급 조건
        loadGradeRequirements(minerSection.getConfigurationSection("grades"));
        
        // XP 보상
        loadExpRewards(minerSection.getConfigurationSection("expRewards"));
        
        // 기준 가격
        loadBasePrices(minerSection.getConfigurationSection("basePrices"));
        
        // 산화 구리 설정
        loadOxidationConfig(minerSection.getConfigurationSection("oxidation"));
        
        // 정제소 설정
        ConfigurationSection refinerySection = minerSection.getConfigurationSection("refinery");
        if (refinerySection != null) {
            refineryEnabled = refinerySection.getBoolean("enabled", false);
        }
        
        // 기본값으로 빈 항목 채우기
        setDefaults();
        
        logger.info(String.format("[MinerConfig] 로드 완료 - levelBonus: %.1f%%, grades: %d, expRewards: %d, basePrices: %d",
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
            req.requiredBD = gradeSection.getLong("requiredBD", 
                    gradeSection.getLong("requiredMoney", 0));
            req.requiredTotalSales = gradeSection.getLong("requiredTotalSales", 0);
            
            // [Phase 4.E] 행동량 + 직업별 판매액 조건 로드
            req.requiredTotalMined = gradeSection.getLong("requiredTotalMined", 0);
            req.requiredTotalSoldAmount = gradeSection.getLong("requiredTotalSoldAmount", 0);
            
            gradeRequirements.put(grade, req);
        }
    }
    
    private void loadExpRewards(ConfigurationSection section) {
        if (section == null) return;
        
        for (String key : section.getKeys(false)) {
            // Material 파싱 시도 (대문자)
            try {
                Material material = Material.valueOf(key.toUpperCase());
                long xp = section.getLong(key);
                expRewards.put(material, xp);
            } catch (IllegalArgumentException e) {
                // 커스텀 광물 ID는 무시 (Lite에서는 미지원)
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
                // 커스텀 광물 ID는 무시
            }
        }
    }
    
    private void loadOxidationConfig(ConfigurationSection section) {
        if (section == null) return;
        
        oxidationEnabled = section.getBoolean("enabled", true);
        oxidationCheckIntervalMinutes = section.getInt("check_interval_minutes", 5);
        oxidationChance = section.getDouble("oxidation_chance", 0.10);
        farmPreventionRadius = section.getInt("farm_prevention_radius", 4);
    }
    
    /**
     * 기본값 설정 (설정 파일에 없는 항목)
     */
    private void setDefaults() {
        // 등급별 기본 승급 조건
        // [2026-02-01] 밸런스 조정 - 농부/어부와 동일하게 상향
        if (!gradeRequirements.containsKey(JobGrade.GRADE_2)) {
            GradeRequirement req = new GradeRequirement();
            req.requiredLevel = 20;
            req.requiredBD = 50000;
            req.requiredTotalSales = 100000;
            gradeRequirements.put(JobGrade.GRADE_2, req);
        }
        if (!gradeRequirements.containsKey(JobGrade.GRADE_3)) {
            GradeRequirement req = new GradeRequirement();
            req.requiredLevel = 40;
            req.requiredBD = 200000;
            req.requiredTotalSales = 500000;
            gradeRequirements.put(JobGrade.GRADE_3, req);
        }
        if (!gradeRequirements.containsKey(JobGrade.GRADE_4)) {
            GradeRequirement req = new GradeRequirement();
            req.requiredLevel = 80;
            req.requiredBD = 1000000;
            req.requiredTotalSales = 2000000;
            gradeRequirements.put(JobGrade.GRADE_4, req);
        }
        
        // 기본 XP 보상 (설정에 없는 경우)
        setDefaultExpReward(Material.COAL_ORE, 3);
        setDefaultExpReward(Material.DEEPSLATE_COAL_ORE, 4);
        setDefaultExpReward(Material.COPPER_ORE, 5);
        setDefaultExpReward(Material.DEEPSLATE_COPPER_ORE, 6);
        setDefaultExpReward(Material.IRON_ORE, 7);
        setDefaultExpReward(Material.DEEPSLATE_IRON_ORE, 8);
        setDefaultExpReward(Material.GOLD_ORE, 12);
        setDefaultExpReward(Material.DEEPSLATE_GOLD_ORE, 14);
        setDefaultExpReward(Material.NETHER_GOLD_ORE, 10);
        setDefaultExpReward(Material.REDSTONE_ORE, 8);
        setDefaultExpReward(Material.DEEPSLATE_REDSTONE_ORE, 10);
        setDefaultExpReward(Material.LAPIS_ORE, 14);
        setDefaultExpReward(Material.DEEPSLATE_LAPIS_ORE, 16);
        setDefaultExpReward(Material.DIAMOND_ORE, 30);
        setDefaultExpReward(Material.DEEPSLATE_DIAMOND_ORE, 35);
        setDefaultExpReward(Material.EMERALD_ORE, 40);
        setDefaultExpReward(Material.DEEPSLATE_EMERALD_ORE, 48);
        setDefaultExpReward(Material.NETHER_QUARTZ_ORE, 7);
        setDefaultExpReward(Material.ANCIENT_DEBRIS, 80);
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
    
    /**
     * 광석 Material에 대한 XP 보상
     */
    public long getExpReward(Material material) {
        return expRewards.getOrDefault(material, 0L);
    }
    
    /**
     * 해당 Material이 XP 보상이 있는 광석인지
     */
    public boolean isOre(Material material) {
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
    
    // ===== 산화 구리 설정 Getters =====
    
    public boolean isOxidationEnabled() {
        return oxidationEnabled;
    }
    
    public int getOxidationCheckIntervalMinutes() {
        return oxidationCheckIntervalMinutes;
    }
    
    public double getOxidationChance() {
        return oxidationChance;
    }
    
    public int getFarmPreventionRadius() {
        return farmPreventionRadius;
    }
    
    // ===== 정제소 설정 Getters =====
    
    public boolean isRefineryEnabled() {
        return refineryEnabled;
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
        public long requiredTotalMined = 0;          // 총 채굴량
        public long requiredTotalSoldAmount = 0;     // 직업별 판매액 (통일된 필드명)
        
        @Override
        public String toString() {
            return String.format("GradeReq{level=%d, bd=%d, sales=%d, mined=%d, soldAmount=%d}",
                    requiredLevel, requiredBD, requiredTotalSales, requiredTotalMined, requiredTotalSoldAmount);
        }
    }
}
