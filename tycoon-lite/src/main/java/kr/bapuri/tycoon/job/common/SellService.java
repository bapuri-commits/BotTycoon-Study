package kr.bapuri.tycoon.job.common;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * SellService - 통합 판매 서비스
 * 
 * QnA 결정: A2) SellService로 분리
 * - Shop과 Job의 결합도를 낮춤
 * - 판매 정책(가격/경험치)을 중앙에서 관리
 * 
 * 주요 기능:
 * 1. 판매 가격 계산 (레벨 보너스 + PricingPolicy)
 * 2. 경험치 지급 (expEnabled 토글)
 * 3. BD 지급 (EconomyService 호출)
 * 4. 판매 기록 (PlayerTycoonData)
 */
public class SellService implements SellPolicy {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final PlayerDataManager dataManager;
    private final EconomyService economyService;
    private final PricingPolicy pricingPolicy;
    private final ItemRequirement itemRequirement;
    
    // 경험치 지급 활성화 토글
    private boolean expEnabled = true;
    
    // 아이템 → 직업 매핑 (캐싱)
    private final Map<String, JobType> itemJobMapping = new HashMap<>();
    
    // 아이템 → 기준가 매핑
    private final Map<String, Long> basePrices = new HashMap<>();
    
    // 아이템 → 기준 경험치 매핑
    private final Map<String, Long> baseExpRewards = new HashMap<>();
    
    // [Phase 8] 직업 변경 콜백 (모드 연동용)
    private java.util.function.Consumer<Player> jobChangeCallback;
    
    public SellService(JavaPlugin plugin, 
                      PlayerDataManager dataManager, 
                      EconomyService economyService) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataManager = dataManager;
        this.economyService = economyService;
        this.pricingPolicy = new PricingPolicy(dataManager);
        this.itemRequirement = new ItemRequirement(dataManager);
    }
    
    // ================================================================================
    // [Phase 8] 직업 변경 콜백 (모드 연동)
    // ================================================================================
    
    /**
     * 직업 변경 콜백 설정 (ModDataService에서 호출)
     * 
     * @param callback Player를 받아 해당 플레이어에게 JOB_DATA 전송
     */
    public void setJobChangeCallback(java.util.function.Consumer<Player> callback) {
        this.jobChangeCallback = callback;
        logger.info("[SellService] 직업 변경 콜백 등록됨 (모드 연동)");
    }
    
    /**
     * 직업 변경 알림 (콜백 호출)
     */
    private void notifyJobChange(Player player) {
        if (jobChangeCallback != null && player != null && player.isOnline()) {
            jobChangeCallback.accept(player);
        }
    }
    
    /**
     * 설정 로드 (jobs.yml에서 자동으로 로드)
     */
    public void loadConfig() {
        // jobs.yml 파일 존재 확인
        java.io.File jobsFile = new java.io.File(plugin.getDataFolder(), "jobs.yml");
        if (!jobsFile.exists()) {
            logger.warning("[SellService] jobs.yml 파일이 존재하지 않습니다. 기본값 사용.");
            return;
        }
        
        // jobs.yml에서 pricing_policy 로드
        org.bukkit.configuration.file.YamlConfiguration jobsConfig = 
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(jobsFile);
        
        // global 섹션
        ConfigurationSection globalConfig = jobsConfig.getConfigurationSection("global");
        if (globalConfig != null) {
            this.expEnabled = globalConfig.getBoolean("exp_from_sales", true);
        }
        
        // pricing_policy 섹션
        ConfigurationSection pricingConfig = jobsConfig.getConfigurationSection("pricing_policy");
        if (pricingConfig != null) {
            pricingPolicy.loadConfig(pricingConfig);
        }
        
        logger.info("[SellService] 설정 로드 완료 (expEnabled=" + expEnabled + ")");
    }
    
    /**
     * 설정 로드 (외부 설정 전달)
     * 
     * @param globalConfig jobs.yml의 global 섹션
     * @param pricingConfig jobs.yml의 pricing_policy 섹션
     */
    public void loadConfig(ConfigurationSection globalConfig, ConfigurationSection pricingConfig) {
        if (globalConfig != null) {
            this.expEnabled = globalConfig.getBoolean("exp_from_sales", true);
        }
        
        if (pricingConfig != null) {
            pricingPolicy.loadConfig(pricingConfig);
        }
    }
    
    /**
     * 직업별 가격/경험치 설정 등록
     * 
     * @param jobType 직업 타입
     * @param pricesConfig basePrices 섹션
     * @param expConfig expRewards 섹션
     */
    public void registerJobPrices(JobType jobType, 
                                  ConfigurationSection pricesConfig, 
                                  ConfigurationSection expConfig) {
        if (pricesConfig != null) {
            for (String itemId : pricesConfig.getKeys(false)) {
                long price = pricesConfig.getLong(itemId, 0);
                if (price > 0) {
                    basePrices.put(itemId.toUpperCase(), price);
                    itemJobMapping.put(itemId.toUpperCase(), jobType);
                }
            }
        }
        
        if (expConfig != null) {
            for (String itemId : expConfig.getKeys(false)) {
                long exp = expConfig.getLong(itemId, 0);
                if (exp > 0) {
                    baseExpRewards.put(itemId.toUpperCase(), exp);
                    // 경험치만 있고 가격은 없을 수도 있음
                    if (!itemJobMapping.containsKey(itemId.toUpperCase())) {
                        itemJobMapping.put(itemId.toUpperCase(), jobType);
                    }
                }
            }
        }
    }
    
    // ===== SellPolicy 구현 =====
    
    @Override
    public long calculatePrice(Player player, String itemId, int amount, long basePrice) {
        if (amount <= 0 || basePrice <= 0) return 0;
        
        String normalizedId = itemId.toUpperCase();
        JobType jobType = itemJobMapping.get(normalizedId);
        
        long unitPrice = pricingPolicy.calculateSellPrice(player, basePrice, jobType);
        return unitPrice * amount;
    }
    
    @Override
    public long calculateExp(Player player, String itemId, int amount, long baseExp) {
        if (!expEnabled) return 0;
        if (amount <= 0 || baseExp <= 0) return 0;
        if (player == null) return 0;
        
        String normalizedId = itemId.toUpperCase();
        JobType jobType = itemJobMapping.get(normalizedId);
        
        // 해당 직업이 없으면 경험치 없음
        if (jobType == null) return 0;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null || !data.hasJob(jobType)) return 0;
        
        return baseExp * amount;
    }
    
    @Override
    public boolean canSell(Player player, String itemId) {
        // 현재는 모든 판매 허용 (가격 배율로 통제)
        return true;
    }
    
    @Override
    public String getCannotSellReason(Player player, String itemId) {
        if (canSell(player, itemId)) return null;
        return "§c이 아이템을 판매할 수 없습니다.";
    }
    
    @Override
    public JobType getJobForItem(String itemId) {
        if (itemId == null) return null;
        return itemJobMapping.get(itemId.toUpperCase());
    }
    
    @Override
    public boolean isExpEnabled() {
        return expEnabled;
    }
    
    // ===== 판매 실행 =====
    
    /**
     * 판매 실행 결과
     */
    public record SellResult(
        boolean success,
        long earnedBD,
        long earnedExp,
        String message
    ) {
        public static SellResult success(long earnedBD, long earnedExp) {
            return new SellResult(true, earnedBD, earnedExp, null);
        }
        
        public static SellResult failure(String reason) {
            return new SellResult(false, 0, 0, reason);
        }
    }
    
    /**
     * 아이템 판매 실행
     * 
     * @param player 판매자
     * @param itemId 아이템 ID (Material name)
     * @param amount 수량
     * @return 판매 결과
     */
    public SellResult sell(Player player, String itemId, int amount) {
        if (player == null) {
            return SellResult.failure("§c플레이어가 유효하지 않습니다.");
        }
        
        if (itemId == null || itemId.isEmpty()) {
            return SellResult.failure("§c아이템이 유효하지 않습니다.");
        }
        
        if (amount <= 0) {
            return SellResult.failure("§c수량이 유효하지 않습니다.");
        }
        
        // 판매 가능 여부 확인
        if (!canSell(player, itemId)) {
            return SellResult.failure(getCannotSellReason(player, itemId));
        }
        
        String normalizedId = itemId.toUpperCase();
        
        // 기준가 조회
        long basePrice = basePrices.getOrDefault(normalizedId, 0L);
        if (basePrice <= 0) {
            return SellResult.failure("§c이 아이템은 판매할 수 없습니다.");
        }
        
        // 최종 가격 계산
        long totalPrice = calculatePrice(player, itemId, amount, basePrice);
        if (totalPrice <= 0) {
            return SellResult.failure("§c판매 가격을 계산할 수 없습니다.");
        }
        
        // BD 지급
        economyService.deposit(
            player.getUniqueId(), 
            totalPrice, 
            "SHOP_SELL:" + normalizedId,
            "SellService"
        );
        
        // 경험치 계산 및 지급
        long baseExp = baseExpRewards.getOrDefault(normalizedId, 0L);
        long earnedExp = calculateExp(player, itemId, amount, baseExp);
        
        if (earnedExp > 0) {
            JobType jobType = itemJobMapping.get(normalizedId);
            if (jobType != null) {
                PlayerTycoonData data = dataManager.get(player.getUniqueId());
                if (data != null && data.hasJob(jobType)) {
                    data.addJobExp(jobType, earnedExp);
                    data.markDirty();  // 자동 저장 시스템이 처리
                    
                    // [Phase 8] 모드에 직업 변경 알림
                    notifyJobChange(player);
                }
            }
        }
        
        // 판매 기록 (직업별 통계)
        recordSale(player, normalizedId, amount, totalPrice);
        
        return SellResult.success(totalPrice, earnedExp);
    }
    
    /**
     * 판매 기록 저장
     */
    private void recordSale(Player player, String itemId, int amount, long totalPrice) {
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return;
        
        JobType jobType = itemJobMapping.get(itemId);
        if (jobType == null) return;
        
        // 총 판매액 기록 (모든 직업 공통)
        data.addTotalSales(totalPrice);
        
        // 직업별 판매 통계 기록
        switch (jobType) {
            case MINER -> {
                data.recordSold(itemId, amount);
                data.addMinerSales(totalPrice);
            }
            case FARMER -> {
                data.recordSoldCrop(itemId, amount);
                data.addFarmerSales(totalPrice);
            }
            case FISHER -> {
                data.recordSoldSeafood(itemId, amount);
                data.addFisherSales(totalPrice);
            }
            default -> {
                // Tier 2 직업은 별도 처리 (Lite에서는 미사용)
            }
        }
        
        data.markDirty();  // 자동 저장 시스템이 처리
    }
    
    // ===== 가격 조회 =====
    
    /**
     * 아이템 기준가 조회
     */
    public long getBasePrice(String itemId) {
        if (itemId == null) return 0;
        return basePrices.getOrDefault(itemId.toUpperCase(), 0L);
    }
    
    /**
     * 아이템 판매가 조회 (플레이어 기준)
     */
    public long getSellPrice(Player player, String itemId, int amount) {
        long basePrice = getBasePrice(itemId);
        return calculatePrice(player, itemId, amount, basePrice);
    }
    
    /**
     * 아이템 기준 경험치 조회
     */
    public long getBaseExp(String itemId) {
        if (itemId == null) return 0;
        return baseExpRewards.getOrDefault(itemId.toUpperCase(), 0L);
    }
    
    // ===== Getters =====
    
    public PricingPolicy getPricingPolicy() {
        return pricingPolicy;
    }
    
    public ItemRequirement getItemRequirement() {
        return itemRequirement;
    }
    
    public void setExpEnabled(boolean enabled) {
        this.expEnabled = enabled;
    }
    
    /**
     * 등록된 판매 가능 아이템 수
     */
    public int getRegisteredItemCount() {
        return basePrices.size();
    }
}
