package kr.bapuri.tycoon.job.common;

import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.JobType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * GradeBonusConfig - 등급별 보너스 설정 관리
 * 
 * jobs.yml의 gradeBonus 섹션에서 로드
 * 각 직업별, 등급별 보너스 수치를 관리
 * 
 * 보너스 종류:
 * - yieldMulti: 채집량 배율 (광부: 추가 드롭, 농부: 수확량, 어부: 더블캐치)
 * - seedMulti: 씨앗 배율 (농부 전용, 작물보다 낮은 배율)
 * - xpMulti: 경험치 배율 (행동 + 판매 XP)
 * - primeChance: 프라임 등급 확률 보너스 (농부)
 * - trophyChance: 트로피 등급 확률 보너스 (농부)
 * - rareChanceBonus: 희귀도 확률 보너스 (어부)
 * - lureBonus: Lure 인챈트 레벨 추가 (어부)
 * - miningEfficiency: 채굴 효율 인챈트 레벨 (광부)
 */
public class GradeBonusConfig {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    
    // 직업 → 등급 → 보너스 데이터
    private final Map<JobType, Map<JobGrade, GradeBonus>> bonusData = new EnumMap<>(JobType.class);
    
    public GradeBonusConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadFromConfig();
    }
    
    /**
     * 설정 로드/리로드
     */
    public void loadFromConfig() {
        bonusData.clear();
        
        File jobsFile = new File(plugin.getDataFolder(), "jobs.yml");
        if (!jobsFile.exists()) {
            logger.warning("[GradeBonusConfig] jobs.yml이 없습니다. 기본값을 사용합니다.");
            setDefaults();
            return;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(jobsFile);
        ConfigurationSection bonusSection = config.getConfigurationSection("gradeBonus");
        
        if (bonusSection == null) {
            logger.info("[GradeBonusConfig] gradeBonus 섹션이 없습니다. 기본값을 사용합니다.");
            setDefaults();
            return;
        }
        
        // 각 직업별 로드
        loadJobBonus(bonusSection, "miner", JobType.MINER);
        loadJobBonus(bonusSection, "farmer", JobType.FARMER);
        loadJobBonus(bonusSection, "fisher", JobType.FISHER);
        
        // 누락된 항목 기본값 채우기
        setDefaults();
        
        logger.info("[GradeBonusConfig] 로드 완료 - 직업 수: " + bonusData.size());
    }
    
    /**
     * 리로드 메서드 (외부 호출용)
     */
    public void reload() {
        loadFromConfig();
    }
    
    /**
     * 개별 직업 보너스 로드
     */
    private void loadJobBonus(ConfigurationSection parent, String key, JobType jobType) {
        ConfigurationSection jobSection = parent.getConfigurationSection(key);
        if (jobSection == null) return;
        
        Map<JobGrade, GradeBonus> gradeMap = new EnumMap<>(JobGrade.class);
        
        for (String gradeKey : jobSection.getKeys(false)) {
            JobGrade grade = JobGrade.fromConfigKey(gradeKey);
            if (grade == null) {
                logger.warning("[GradeBonusConfig] 알 수 없는 등급: " + gradeKey);
                continue;
            }
            
            ConfigurationSection gradeSection = jobSection.getConfigurationSection(gradeKey);
            if (gradeSection == null) continue;
            
            GradeBonus bonus = new GradeBonus();
            bonus.yieldMulti = gradeSection.getDouble("yieldMulti", 1.0);
            bonus.seedMulti = gradeSection.getDouble("seedMulti", 1.0);
            bonus.xpMulti = gradeSection.getDouble("xpMulti", 1.0);
            bonus.primeChance = gradeSection.getDouble("primeChance", 0.0);
            bonus.trophyChance = gradeSection.getDouble("trophyChance", 0.0);
            bonus.rareChanceBonus = gradeSection.getDouble("rareChance", 0.0);
            bonus.lureBonus = gradeSection.getInt("lureBonus", 0);
            bonus.miningEfficiency = gradeSection.getInt("miningEfficiency", 0);
            
            gradeMap.put(grade, bonus);
        }
        
        bonusData.put(jobType, gradeMap);
    }
    
    /**
     * 기본값 설정 (누락된 항목 채우기)
     */
    private void setDefaults() {
        // 모든 직업에 대해 GRADE_1은 기본값 (보너스 없음)
        for (JobType job : new JobType[]{JobType.MINER, JobType.FARMER, JobType.FISHER}) {
            bonusData.computeIfAbsent(job, k -> new EnumMap<>(JobGrade.class));
            bonusData.get(job).putIfAbsent(JobGrade.GRADE_1, GradeBonus.DEFAULT);
        }
    }
    
    // ===== Getters =====
    
    /**
     * 특정 직업/등급의 보너스 데이터 조회
     */
    public GradeBonus getBonus(JobType jobType, JobGrade grade) {
        Map<JobGrade, GradeBonus> gradeMap = bonusData.get(jobType);
        if (gradeMap == null) return GradeBonus.DEFAULT;
        return gradeMap.getOrDefault(grade, GradeBonus.DEFAULT);
    }
    
    /**
     * 채집량 배율 조회 (1.0 = 100%, 1.05 = 105%)
     */
    public double getYieldMultiplier(JobType jobType, JobGrade grade) {
        return getBonus(jobType, grade).yieldMulti;
    }
    
    /**
     * 씨앗 배율 조회 (농부 전용)
     */
    public double getSeedMultiplier(JobGrade grade) {
        return getBonus(JobType.FARMER, grade).seedMulti;
    }
    
    /**
     * XP 배율 조회
     */
    public double getXpMultiplier(JobType jobType, JobGrade grade) {
        return getBonus(jobType, grade).xpMulti;
    }
    
    /**
     * 채굴 효율 인챈트 레벨 조회 (광부 전용)
     */
    public int getMiningEfficiency(JobGrade grade) {
        return getBonus(JobType.MINER, grade).miningEfficiency;
    }
    
    /**
     * Lure 보너스 레벨 조회 (어부 전용)
     */
    public int getLureBonus(JobGrade grade) {
        return getBonus(JobType.FISHER, grade).lureBonus;
    }
    
    /**
     * 프라임 확률 보너스 조회 (농부 전용)
     */
    public double getPrimeChanceBonus(JobGrade grade) {
        return getBonus(JobType.FARMER, grade).primeChance;
    }
    
    /**
     * 트로피 확률 보너스 조회 (농부 전용)
     */
    public double getTrophyChanceBonus(JobGrade grade) {
        return getBonus(JobType.FARMER, grade).trophyChance;
    }
    
    /**
     * 희귀도 확률 보너스 조회 (어부 전용)
     */
    public double getRareChanceBonus(JobGrade grade) {
        return getBonus(JobType.FISHER, grade).rareChanceBonus;
    }
    
    // ===== Inner Class =====
    
    /**
     * 등급별 보너스 데이터
     */
    public static class GradeBonus {
        /** 기본값 (보너스 없음) */
        public static final GradeBonus DEFAULT = new GradeBonus();
        
        /** 채집량 배율 (1.0 = 기본) */
        public double yieldMulti = 1.0;
        
        /** 씨앗 배율 (농부 전용, 1.0 = 기본) */
        public double seedMulti = 1.0;
        
        /** XP 배율 (1.0 = 기본) */
        public double xpMulti = 1.0;
        
        /** 프라임 확률 보너스 (농부, 0.0 = 기본) */
        public double primeChance = 0.0;
        
        /** 트로피 확률 보너스 (농부, 0.0 = 기본) */
        public double trophyChance = 0.0;
        
        /** 희귀도 확률 보너스 (어부, 0.0 = 기본) */
        public double rareChanceBonus = 0.0;
        
        /** Lure 레벨 추가 (어부, 0 = 기본) */
        public int lureBonus = 0;
        
        /** 채굴 효율 인챈트 레벨 (광부, 0 = 기본) */
        public int miningEfficiency = 0;
        
        @Override
        public String toString() {
            return String.format("GradeBonus{yield=%.2f, seed=%.2f, xp=%.2f, prime=%.2f, trophy=%.2f, rare=%.2f, lure=%d, mining=%d}",
                    yieldMulti, seedMulti, xpMulti, primeChance, trophyChance, rareChanceBonus, lureBonus, miningEfficiency);
        }
    }
}
