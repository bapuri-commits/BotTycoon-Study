package kr.bapuri.tycoon.job;

import kr.bapuri.tycoon.job.common.unlock.CodexCountCondition;
import kr.bapuri.tycoon.job.common.unlock.MoneyCondition;
import kr.bapuri.tycoon.job.common.UnlockCondition;
import kr.bapuri.tycoon.player.PlayerDataManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * JobsConfigLoader - jobs.yml 설정 로더
 * 
 * 역할:
 * - jobs.yml 로드/저장
 * - 직업별 설정 파싱
 * - 해금 조건 파싱 (UnlockCondition 생성)
 * - 가격 정책 로드
 */
public class JobsConfigLoader {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final File configFile;
    private final PlayerDataManager dataManager;
    private YamlConfiguration config;
    
    // 캐시된 설정
    private boolean tier2JobsEnabled = false;
    private boolean expFromActionsEnabled = true;
    private boolean expFromSalesEnabled = true;
    
    public JobsConfigLoader(JavaPlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataManager = dataManager;
        this.configFile = new File(plugin.getDataFolder(), "jobs.yml");
    }
    
    /**
     * 설정 로드
     */
    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource("jobs.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // 기본값 병합 (리소스)
        try (InputStream is = plugin.getResource("jobs.yml")) {
            if (is != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(is, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
            }
        } catch (IOException e) {
            logger.warning("[JobsConfigLoader] 기본 설정 로드 실패: " + e.getMessage());
        }
        
        // 글로벌 설정 로드
        loadGlobalSettings();
        
        logger.info("[JobsConfigLoader] jobs.yml 로드 완료");
    }
    
    /**
     * 설정 리로드
     */
    public void reload() {
        load();
    }
    
    /**
     * 글로벌 설정 로드
     */
    private void loadGlobalSettings() {
        ConfigurationSection global = config.getConfigurationSection("global");
        if (global == null) {
            logger.warning("[JobsConfigLoader] global 섹션이 없습니다. 기본값 사용.");
            return;
        }
        
        tier2JobsEnabled = global.getBoolean("tier2_jobs_enabled", false);
        expFromActionsEnabled = global.getBoolean("exp_from_actions", true);
        expFromSalesEnabled = global.getBoolean("exp_from_sales", true);
    }
    
    // ===== 글로벌 설정 조회 =====
    
    public boolean isTier2JobsEnabled() {
        return tier2JobsEnabled;
    }
    
    public boolean isExpFromActionsEnabled() {
        return expFromActionsEnabled;
    }
    
    public boolean isExpFromSalesEnabled() {
        return expFromSalesEnabled;
    }
    
    // ===== 직업별 설정 조회 =====
    
    /**
     * 직업 설정 섹션 가져오기
     * (jobs.yml에서 직업 키는 최상위에 위치: miner, farmer, fisher 등)
     */
    public ConfigurationSection getJobSection(JobType jobType) {
        String key = jobType.getConfigKey();
        return config.getConfigurationSection(key);
    }
    
    /**
     * 직업 표시 이름 가져오기
     */
    public String getDisplayName(JobType jobType) {
        ConfigurationSection section = getJobSection(jobType);
        if (section == null) return jobType.getDisplayName();
        return section.getString("display_name", jobType.getDisplayName());
    }
    
    /**
     * 직업 최대 레벨 가져오기
     */
    public int getMaxLevel(JobType jobType) {
        ConfigurationSection section = getJobSection(jobType);
        if (section == null) return jobType.getMaxLevel();
        return section.getInt("max_level", jobType.getMaxLevel());
    }
    
    /**
     * 직업 해금 조건 파싱
     */
    public List<UnlockCondition> getUnlockConditions(JobType jobType) {
        List<UnlockCondition> conditions = new ArrayList<>();
        
        ConfigurationSection section = getJobSection(jobType);
        if (section == null) return conditions;
        
        ConfigurationSection unlockSection = section.getConfigurationSection("unlock");
        if (unlockSection == null) return conditions;
        
        // codex_count 조건
        if (unlockSection.contains("codex_count")) {
            int required = unlockSection.getInt("codex_count", 0);
            if (required > 0) {
                conditions.add(new CodexCountCondition(required, dataManager));
            }
        }
        
        // money 조건
        if (unlockSection.contains("money")) {
            long required = unlockSection.getLong("money", 0);
            if (required > 0) {
                conditions.add(new MoneyCondition(required, dataManager));
            }
        }
        
        return conditions;
    }
    
    /**
     * 등급별 승급 비용 (BD)
     */
    public long getPromotionCost(JobType jobType, JobGrade grade) {
        ConfigurationSection section = getJobSection(jobType);
        if (section == null) return getDefaultPromotionCost(grade);
        
        ConfigurationSection promotionSection = section.getConfigurationSection("promotion");
        if (promotionSection == null) return getDefaultPromotionCost(grade);
        
        String key = grade.getConfigKey();
        return promotionSection.getLong(key + ".cost", getDefaultPromotionCost(grade));
    }
    
    /**
     * 등급별 필요 레벨
     */
    public int getPromotionLevel(JobType jobType, JobGrade grade) {
        ConfigurationSection section = getJobSection(jobType);
        if (section == null) return grade.getRequiredLevel();
        
        ConfigurationSection promotionSection = section.getConfigurationSection("promotion");
        if (promotionSection == null) return grade.getRequiredLevel();
        
        String key = grade.getConfigKey();
        return promotionSection.getInt(key + ".level", grade.getRequiredLevel());
    }
    
    /**
     * 기본 승급 비용
     */
    private long getDefaultPromotionCost(JobGrade grade) {
        return switch (grade) {
            case GRADE_1 -> 0;
            case GRADE_2 -> 10000;
            case GRADE_3 -> 50000;
            case GRADE_4 -> 200000;
        };
    }
    
    // ===== 경험치 공식 설정 =====
    
    /**
     * 경험치 공식 섹션 가져오기
     * JobExpCalculator.loadFromConfig()에 전달용
     */
    public ConfigurationSection getExpFormulaSection() {
        return config.getConfigurationSection("exp_formula");
    }
    
    /**
     * 경험치 공식 기본값 가져오기 (레거시 호환)
     */
    public int getExpFormulaBase() {
        return config.getInt("exp_formula.base", 100);
    }
    
    public double getExpFormulaMultiplier() {
        return config.getDouble("exp_formula.multiplier", 1.15);
    }
    
    // ===== 가격 정책 설정 =====
    
    /**
     * 직업 없는 플레이어 판매 배율
     */
    public double getNoJobSellMultiplier() {
        return config.getDouble("pricing_policy.no_job_sell_multiplier", 0.7);
    }
    
    /**
     * 직업 없는 플레이어 구매 배율
     */
    public double getNoJobBuyMultiplier() {
        return config.getDouble("pricing_policy.no_job_buy_multiplier", 1.3);
    }
    
    /**
     * 레벨당 가격 보너스 (%)
     */
    public double getLevelBonusPercent() {
        return config.getDouble("pricing_policy.level_bonus_percent", 0.5);
    }
    
    // ===== 아이템별 경험치 설정 =====
    
    /**
     * 아이템별 판매 경험치
     */
    public long getSellExp(JobType jobType, String itemId) {
        ConfigurationSection section = getJobSection(jobType);
        if (section == null) return 0;
        
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection == null) return 0;
        
        String normalizedId = itemId.toLowerCase().replace("minecraft:", "");
        return itemsSection.getLong(normalizedId + ".sell_exp", 0);
    }
    
    /**
     * 아이템별 행동 경험치
     */
    public long getActionExp(JobType jobType, String itemId) {
        ConfigurationSection section = getJobSection(jobType);
        if (section == null) return 0;
        
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection == null) return 0;
        
        String normalizedId = itemId.toLowerCase().replace("minecraft:", "");
        return itemsSection.getLong(normalizedId + ".action_exp", 0);
    }
    
    /**
     * 아이템별 기본 가격
     */
    public long getBasePrice(JobType jobType, String itemId) {
        ConfigurationSection section = getJobSection(jobType);
        if (section == null) return 0;
        
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection == null) return 0;
        
        String normalizedId = itemId.toLowerCase().replace("minecraft:", "");
        return itemsSection.getLong(normalizedId + ".base_price", 0);
    }
    
    /**
     * 설정 파일 직접 접근
     */
    public YamlConfiguration getConfig() {
        return config;
    }
}
