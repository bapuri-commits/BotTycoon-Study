package kr.bapuri.tycoon.shop.price;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 동적 가격 추적 시스템
 * 
 * [Phase 3.B] 거래량 기반 가격 조정
 * 
 * <h2>핵심 규칙</h2>
 * <ul>
 *   <li>가격 변동 범위: 기준가 ±70%</li>
 *   <li>주기당 최대 변동폭: ±10%</li>
 *   <li>갱신 주기: 10분 (config 설정 가능)</li>
 *   <li>역전 방지: 판매가 = 구매가 × 50% 이하</li>
 *   <li>대량 거래 영향력 감소: ManipulationGuard 연동</li>
 * </ul>
 * 
 * <h2>메트릭스 연동 (TODO)</h2>
 * <pre>
 * // 현재: sellMultiplier = 1.0 고정
 * // 나중에: EconomyMetricsService에서 주입
 * </pre>
 * 
 * @see ManipulationGuard
 */
public class DynamicPriceTracker {
    
    private static final Logger logger = Logger.getLogger("Tycoon.DynamicPrice");
    
    // ========== 가격 설정 (config에서 로드) ==========
    private static final double PRICE_SMOOTHING = 0.3;         // 스무딩 (급변 방지) - 고정값
    
    private double maxPriceRange = 0.70;        // 기준가 ±70% (dynamicPrice.maxPriceChangePercent)
    private double maxChangePerCycle = 0.10;    // 주기당 ±10% (dynamicPrice.maxChangePerUpdate)
    private double minSellSpread = 0.50;        // 역전 방지 (dynamicPrice.sellSpreadRatio)
    
    private final Plugin plugin;
    private final Map<String, ItemPriceData> priceData = new ConcurrentHashMap<>();
    private final Map<String, TradeVolume> currentCycleVolume = new ConcurrentHashMap<>();
    
    private int updateIntervalMinutes = 10;
    private BukkitTask updateTask;
    
    // 시장 조작 방지
    private ManipulationGuard manipulationGuard;
    
    // [TODO] 글로벌 인플레이션 배율 (EconomyMetricsService 연동)
    private double globalSellMultiplier = 1.0;
    
    public DynamicPriceTracker(Plugin plugin) {
        this.plugin = plugin;
        this.manipulationGuard = new ManipulationGuard(plugin);
        
        loadConfig();
        logger.info("[DynamicPriceTracker] 초기화 완료 (주기: " + updateIntervalMinutes + "분)");
    }
    
    private void loadConfig() {
        // 경로 fallback: dynamicPrice.* 우선, 없으면 shop.dynamicPrice.* 사용
        int intervalConfig = plugin.getConfig().getInt("dynamicPrice.updateIntervalMinutes", -1);
        if (intervalConfig == -1) {
            intervalConfig = plugin.getConfig().getInt("shop.dynamicPrice.updateIntervalMinutes", 10);
        }
        updateIntervalMinutes = intervalConfig;
        
        // 가격 변동 범위 (% → 소수)
        maxPriceRange = plugin.getConfig().getDouble("dynamicPrice.maxPriceChangePercent", 70) / 100.0;
        
        // 주기당 최대 변동폭 (% → 소수)
        maxChangePerCycle = plugin.getConfig().getDouble("dynamicPrice.maxChangePerUpdate", 10) / 100.0;
        
        // 판매가/구매가 스프레드 비율
        minSellSpread = plugin.getConfig().getDouble("dynamicPrice.sellSpreadRatio", 0.5);
        
        logger.info("[DynamicPriceTracker] 설정 로드: maxRange=" + (maxPriceRange * 100) + 
                   "%, maxChange=" + (maxChangePerCycle * 100) + "%, spread=" + minSellSpread);
    }
    
    /**
     * [TODO] 글로벌 인플레이션 배율 설정
     * EconomyMetricsService에서 주기적으로 호출
     */
    public void setGlobalSellMultiplier(double multiplier) {
        this.globalSellMultiplier = Math.max(0.5, Math.min(1.5, multiplier));
    }
    
    // ========== 아이템 등록 ==========
    
    /**
     * 아이템 가격 데이터 등록
     */
    public void registerItem(String itemId, long basePrice, long minPrice, long maxPrice) {
        String id = itemId.toLowerCase();
        long initialSellPrice = (long)(basePrice * minSellSpread);
        ItemPriceData data = new ItemPriceData(id, basePrice, minPrice, maxPrice, initialSellPrice);
        priceData.put(id, data);
        currentCycleVolume.put(id, new TradeVolume());
        logger.fine("[DynamicPrice] 아이템 등록: " + itemId + " (base=" + basePrice + ")");
    }
    
    /**
     * 아이템 등록 여부
     */
    public boolean isRegistered(String itemId) {
        return priceData.containsKey(itemId.toLowerCase());
    }
    
    // ========== 거래 기록 ==========
    
    /**
     * 구매 기록 (수요 증가 → 가격 상승 압력)
     */
    public void recordBuy(UUID playerUuid, String playerName, String itemId, int amount) {
        String id = itemId.toLowerCase();
        TradeVolume vol = currentCycleVolume.get(id);
        if (vol == null) return;
        
        // 시장 조작 탐지
        boolean isManipulation = manipulationGuard.recordAndCheck(
                playerUuid, playerName, itemId, amount, true);
        
        if (!isManipulation) {
            double impact = manipulationGuard.calculateVolumeImpact(amount);
            vol.addBuy(amount * impact);
        }
    }
    
    /**
     * 판매 기록 (공급 증가 → 가격 하락 압력)
     */
    public void recordSell(UUID playerUuid, String playerName, String itemId, int amount) {
        String id = itemId.toLowerCase();
        TradeVolume vol = currentCycleVolume.get(id);
        if (vol == null) return;
        
        // 시장 조작 탐지
        boolean isManipulation = manipulationGuard.recordAndCheck(
                playerUuid, playerName, itemId, amount, false);
        
        if (!isManipulation) {
            double impact = manipulationGuard.calculateVolumeImpact(amount);
            vol.addSell(amount * impact);
        }
    }
    
    // ========== 가격 조회 ==========
    
    /**
     * 현재 구매가 조회 (플레이어가 NPC에서 살 때)
     */
    public long getBuyPrice(String itemId) {
        ItemPriceData data = priceData.get(itemId.toLowerCase());
        if (data == null) return -1L;
        return data.currentBuyPrice;
    }
    
    /**
     * 현재 판매가 조회 (플레이어가 NPC에 팔 때)
     * [TODO] 글로벌 인플레이션 배율 적용
     */
    public long getSellPrice(String itemId) {
        ItemPriceData data = priceData.get(itemId.toLowerCase());
        if (data == null) return -1L;
        
        // 글로벌 인플레이션 배율 적용
        long adjustedPrice = (long)(data.currentSellPrice * globalSellMultiplier);
        return Math.max(1, adjustedPrice);
    }
    
    /**
     * 기준가 조회
     */
    public long getBasePrice(String itemId) {
        ItemPriceData data = priceData.get(itemId.toLowerCase());
        if (data == null) return -1L;
        return data.basePrice;
    }
    
    // ========== 가격 갱신 ==========
    
    /**
     * 주기적 가격 갱신 시작
     */
    public void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        long intervalTicks = updateIntervalMinutes * 60L * 20L;
        
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updatePrices();
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
        
        logger.info("[DynamicPrice] 가격 갱신 태스크 시작 (주기: " + updateIntervalMinutes + "분)");
    }
    
    /**
     * 태스크 정지
     */
    public void stopUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (manipulationGuard != null) {
            manipulationGuard.shutdown();
        }
    }
    
    /**
     * 가격 갱신 로직
     */
    private void updatePrices() {
        for (Map.Entry<String, ItemPriceData> entry : priceData.entrySet()) {
            String itemId = entry.getKey();
            ItemPriceData data = entry.getValue();
            TradeVolume vol = currentCycleVolume.get(itemId);
            
            if (vol == null) continue;
            
            // 순 수요 계산 (동기화된 getter 사용)
            double buyVol = vol.getBuyVolume();
            double sellVol = vol.getSellVolume();
            double netDemand = buyVol - sellVol;
            double totalVolume = buyVol + sellVol;
            double changeRatio = 0.0;
            
            if (totalVolume > 0) {
                double demandRatio = netDemand / totalVolume;
                changeRatio = demandRatio * maxChangePerCycle;
            }
            
            // 구매가 조정
            long targetBuyPrice = (long)(data.currentBuyPrice * (1.0 + changeRatio));
            
            // 판매가 조정 (반대 방향)
            long targetSellPrice = (long)(data.currentSellPrice * (1.0 - changeRatio));
            
            // 스무딩 적용
            long newBuyPrice = (long)(data.currentBuyPrice * (1.0 - PRICE_SMOOTHING) + targetBuyPrice * PRICE_SMOOTHING);
            long newSellPrice = (long)(data.currentSellPrice * (1.0 - PRICE_SMOOTHING) + targetSellPrice * PRICE_SMOOTHING);
            
            // 범위 클램프
            long buyMin = (long)(data.basePrice * (1.0 - maxPriceRange));
            long buyMax = (long)(data.basePrice * (1.0 + maxPriceRange));
            newBuyPrice = Math.max(buyMin, Math.min(buyMax, newBuyPrice));
            newBuyPrice = Math.max(data.minPrice, Math.min(data.maxPrice, newBuyPrice));
            
            // 역전 방지
            long maxSellPrice = (long)(newBuyPrice * minSellSpread);
            newSellPrice = Math.min(newSellPrice, maxSellPrice);
            newSellPrice = Math.max(1, newSellPrice);
            
            // 적용
            data.currentBuyPrice = newBuyPrice;
            data.currentSellPrice = newSellPrice;
            
            // 로깅
            if (Math.abs(changeRatio) > 0.001) {
                logger.fine(String.format("[DynamicPrice] %s: buy=%d, sell=%d, change=%.2f%%",
                        itemId, newBuyPrice, newSellPrice, changeRatio * 100));
            }
        }
        
        // 거래량 초기화
        for (TradeVolume vol : currentCycleVolume.values()) {
            vol.reset();
        }
        
        save();
    }
    
    // ========== 저장/로드 ==========
    
    public void save() {
        File dataFile = new File(plugin.getDataFolder(), "dynamic_prices.yml");
        YamlConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<String, ItemPriceData> entry : priceData.entrySet()) {
            String path = "prices." + entry.getKey();
            ItemPriceData data = entry.getValue();
            config.set(path + ".currentBuyPrice", data.currentBuyPrice);
            config.set(path + ".currentSellPrice", data.currentSellPrice);
        }
        
        try {
            config.save(dataFile);
            logger.fine("[DynamicPrice] 가격 데이터 저장 완료");
        } catch (IOException e) {
            logger.warning("[DynamicPrice] 저장 실패: " + e.getMessage());
        }
    }
    
    public void load() {
        File dataFile = new File(plugin.getDataFolder(), "dynamic_prices.yml");
        if (!dataFile.exists()) return;
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection pricesSection = config.getConfigurationSection("prices");
        if (pricesSection == null) return;
        
        for (String itemId : pricesSection.getKeys(false)) {
            ItemPriceData data = priceData.get(itemId.toLowerCase());
            if (data != null) {
                ConfigurationSection sec = pricesSection.getConfigurationSection(itemId);
                if (sec != null) {
                    data.currentBuyPrice = sec.getLong("currentBuyPrice", data.basePrice);
                    data.currentSellPrice = sec.getLong("currentSellPrice", (long)(data.basePrice * minSellSpread));
                    logger.fine("[DynamicPrice] 로드: " + itemId + " (buy=" + data.currentBuyPrice + ")");
                }
            }
        }
    }
    
    // ========== 내부 클래스 ==========
    
    /**
     * 아이템 가격 데이터
     * 
     * [동시성] currentBuyPrice/currentSellPrice는 volatile로 선언
     * - 메인 스레드: 가격 조회 (getBuyPrice, getSellPrice)
     * - 타이머 스레드: 가격 갱신 (updatePrices)
     */
    private static class ItemPriceData {
        final String itemId;
        final long basePrice;
        final long minPrice;
        final long maxPrice;
        volatile long currentBuyPrice;
        volatile long currentSellPrice;
        
        ItemPriceData(String itemId, long basePrice, long minPrice, long maxPrice, long initialSellPrice) {
            this.itemId = itemId;
            this.basePrice = basePrice;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.currentBuyPrice = basePrice;
            this.currentSellPrice = initialSellPrice;
        }
    }
    
    /**
     * 거래량 데이터
     * 
     * [동시성] synchronized 메서드로 접근
     */
    private static class TradeVolume {
        private double buyVolume = 0;
        private double sellVolume = 0;
        
        synchronized void addBuy(double amount) {
            buyVolume += amount;
        }
        
        synchronized void addSell(double amount) {
            sellVolume += amount;
        }
        
        synchronized double getBuyVolume() {
            return buyVolume;
        }
        
        synchronized double getSellVolume() {
            return sellVolume;
        }
        
        synchronized void reset() {
            buyVolume = 0;
            sellVolume = 0;
        }
    }
}
