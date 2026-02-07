package kr.bapuri.tycoon.job.common;

import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * PricingPolicy - 가격 정책
 * 
 * QnA 결정: B3) 가격/수수료 통제
 * - 직업 없으면 판매가 50%, 구매가 150%
 * - 직업 있으면 레벨에 따라 보너스
 * 
 * 설정 (jobs.yml 또는 shops.yml):
 * pricing_policy:
 *   no_job_sell_multiplier: 0.5
 *   no_job_buy_multiplier: 1.5
 *   level_bonus_percent: 7.0  # 레벨당 +7%
 */
public class PricingPolicy {
    
    // 기본값
    private static final double DEFAULT_NO_JOB_SELL_MULT = 0.5;
    private static final double DEFAULT_NO_JOB_BUY_MULT = 1.5;
    private static final double DEFAULT_LEVEL_BONUS_PERCENT = 7.0;
    
    private final PlayerDataManager dataManager;
    
    // 설정값
    private double noJobSellMultiplier = DEFAULT_NO_JOB_SELL_MULT;
    private double noJobBuyMultiplier = DEFAULT_NO_JOB_BUY_MULT;
    private double levelBonusPercent = DEFAULT_LEVEL_BONUS_PERCENT;
    
    public PricingPolicy(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }
    
    /**
     * 설정 로드
     */
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        
        this.noJobSellMultiplier = config.getDouble("no_job_sell_multiplier", DEFAULT_NO_JOB_SELL_MULT);
        this.noJobBuyMultiplier = config.getDouble("no_job_buy_multiplier", DEFAULT_NO_JOB_BUY_MULT);
        this.levelBonusPercent = config.getDouble("level_bonus_percent", DEFAULT_LEVEL_BONUS_PERCENT);
    }
    
    /**
     * 판매 가격 계산
     * 
     * @param player 판매자
     * @param basePrice 기준가
     * @param jobType 해당 아이템의 직업 (null이면 일반)
     * @return 최종 판매가
     */
    public long calculateSellPrice(Player player, long basePrice, JobType jobType) {
        if (basePrice <= 0) return 0;
        if (player == null) return basePrice;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return basePrice;
        
        double multiplier;
        
        if (jobType == null) {
            // 일반 아이템: 배율 없음
            multiplier = 1.0;
        } else if (!data.hasJob(jobType)) {
            // 해당 직업 없음: 페널티 배율
            multiplier = noJobSellMultiplier;
        } else {
            // 해당 직업 있음: 레벨 보너스 (복리 계산)
            int level = data.getJobLevel(jobType);
            multiplier = Math.pow(1.0 + levelBonusPercent / 100.0, level);
        }
        
        return Math.max(1, Math.round(basePrice * multiplier));
    }
    
    /**
     * 구매 가격 계산
     * 
     * @param player 구매자
     * @param basePrice 기준가
     * @param jobType 해당 아이템의 직업 (null이면 일반)
     * @return 최종 구매가
     */
    public long calculateBuyPrice(Player player, long basePrice, JobType jobType) {
        if (basePrice <= 0) return 0;
        if (player == null) return basePrice;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return basePrice;
        
        double multiplier;
        
        if (jobType == null) {
            // 일반 아이템: 배율 없음
            multiplier = 1.0;
        } else if (!data.hasJob(jobType)) {
            // 해당 직업 없음: 페널티 배율
            multiplier = noJobBuyMultiplier;
        } else {
            // 해당 직업 있음: 정가 (또는 할인 가능)
            multiplier = 1.0;
        }
        
        return Math.max(1, Math.round(basePrice * multiplier));
    }
    
    /**
     * 레벨 보너스 배율 반환
     * 
     * @param player 플레이어
     * @param jobType 직업 타입
     * @return 배율 (1.0 = 100%)
     */
    public double getLevelBonusMultiplier(Player player, JobType jobType) {
        if (player == null || jobType == null) return 1.0;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null || !data.hasJob(jobType)) return 1.0;
        
        int level = data.getJobLevel(jobType);
        return Math.pow(1.0 + levelBonusPercent / 100.0, level);
    }
    
    /**
     * 비직업자 여부 확인
     */
    public boolean isNoJobPlayer(Player player, JobType jobType) {
        if (player == null) return true;
        if (jobType == null) return false;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return true;
        
        return !data.hasJob(jobType);
    }
    
    // ===== Getters =====
    
    public double getNoJobSellMultiplier() {
        return noJobSellMultiplier;
    }
    
    public double getNoJobBuyMultiplier() {
        return noJobBuyMultiplier;
    }
    
    public double getLevelBonusPercent() {
        return levelBonusPercent;
    }
    
    // ===== Setters (테스트/관리자용) =====
    
    public void setNoJobSellMultiplier(double multiplier) {
        this.noJobSellMultiplier = Math.max(0.0, Math.min(2.0, multiplier));
    }
    
    public void setNoJobBuyMultiplier(double multiplier) {
        this.noJobBuyMultiplier = Math.max(0.5, Math.min(5.0, multiplier));
    }
    
    public void setLevelBonusPercent(double percent) {
        this.levelBonusPercent = Math.max(0.0, Math.min(100.0, percent));
    }
}
