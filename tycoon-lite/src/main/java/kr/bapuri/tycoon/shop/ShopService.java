package kr.bapuri.tycoon.shop;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.shop.price.DynamicPriceTracker;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * 상점 서비스 - Facade
 * 
 * [Phase 3.B] 상점 시스템의 중앙 진입점
 * 
 * <h2>역할</h2>
 * <ul>
 *   <li>상점 등록/관리</li>
 *   <li>구매/판매 처리 위임</li>
 *   <li>동적 가격 추적기 관리</li>
 *   <li>설정 파일(shops.yml) 로드/저장</li>
 * </ul>
 * 
 * <h2>메트릭스 연동 (TODO)</h2>
 * <pre>
 * // 현재: sellMultiplier = 1.0 고정
 * // 나중에: EconomyMetricsService 연동
 * private double getSellMultiplier() {
 *     return 1.0; // TODO: Phase N에서 EconomyMetricsService 연동
 * }
 * </pre>
 * 
 * @see IShop
 * @see AbstractShop
 * @see DynamicPriceTracker
 */
public class ShopService {
    
    private final Plugin plugin;
    private final EconomyService economyService;
    private final Logger logger;
    
    // 등록된 상점
    private final Map<String, IShop> shops = new HashMap<>();
    
    // 동적 가격 추적기
    private DynamicPriceTracker priceTracker;
    
    // GUI 관리자 (나중에 설정)
    private ShopGuiManager guiManager;
    
    // 설정 파일
    private final File shopsFile;
    private YamlConfiguration shopsConfig;
    
    public ShopService(Plugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.logger = plugin.getLogger();
        this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
        
        loadConfig();
        
        logger.info("[ShopService] 초기화 완료");
    }
    
    // ========== 설정 관리 ==========
    
    /**
     * shops.yml 로드
     */
    private void loadConfig() {
        if (!shopsFile.exists()) {
            // 기본 설정 파일 복사
            plugin.saveResource("shops.yml", false);
        }
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        logger.info("[ShopService] shops.yml 로드됨");
    }
    
    /**
     * shops.yml 저장
     */
    public void saveConfig() {
        try {
            shopsConfig.save(shopsFile);
            logger.fine("[ShopService] shops.yml 저장됨");
        } catch (IOException e) {
            logger.warning("[ShopService] shops.yml 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 상점 설정 섹션 가져오기 (직업 상점)
     */
    public ConfigurationSection getShopConfig(String shopId) {
        return shopsConfig.getConfigurationSection("job_shops." + shopId);
    }
    
    /**
     * 마켓 상점 설정 섹션 가져오기 (동적 가격 + 비대칭 레벨 보너스)
     */
    public ConfigurationSection getMarketShopConfig(String shopId) {
        return shopsConfig.getConfigurationSection("market_shops." + shopId);
    }
    
    /**
     * 일반 상점 설정 섹션 가져오기 (고정 가격)
     */
    public ConfigurationSection getGeneralShopConfig(String shopId) {
        return shopsConfig.getConfigurationSection("general_shops." + shopId);
    }
    
    // ========== 동적 가격 추적기 ==========
    
    /**
     * 동적 가격 추적기 설정
     */
    public void setPriceTracker(DynamicPriceTracker priceTracker) {
        this.priceTracker = priceTracker;
    }
    
    public DynamicPriceTracker getPriceTracker() {
        return priceTracker;
    }
    
    // ========== GUI 관리자 ==========
    
    /**
     * GUI 관리자 설정
     */
    public void setGuiManager(ShopGuiManager guiManager) {
        this.guiManager = guiManager;
    }
    
    public ShopGuiManager getGuiManager() {
        return guiManager;
    }
    
    // ========== 메트릭스 연동 (Stub) ==========
    
    /**
     * [TODO: Phase N] 글로벌 판매 배율 조회
     * 
     * EconomyMetricsService 연동 시 교체:
     * - 인플레이션 시 < 1.0 (판매 수익 감소)
     * - 디플레이션 시 > 1.0 (판매 수익 증가)
     * 
     * @return 판매 배율 (현재 1.0 고정)
     */
    public double getSellMultiplier() {
        // TODO: Phase N에서 EconomyMetricsService 연동
        // return metricsService != null ? metricsService.getSellMultiplier() : 1.0;
        return 1.0;
    }
    
    // ========== 상점 등록/관리 ==========
    
    /**
     * 상점 등록
     */
    public void registerShop(IShop shop) {
        String id = shop.getShopId().toLowerCase(Locale.ROOT);
        shops.put(id, shop);
        logger.info("[ShopService] 상점 등록: " + id + " (" + shop.getDisplayName() + ")");
    }
    
    /**
     * 상점 조회
     */
    public IShop getShop(String shopId) {
        if (shopId == null) return null;
        return shops.get(shopId.toLowerCase(Locale.ROOT));
    }
    
    /**
     * [Phase 4.B] MinerShop 조회 (편의 메서드)
     */
    public kr.bapuri.tycoon.shop.job.MinerShop getMinerShop() {
        IShop shop = getShop("miner");
        if (shop instanceof kr.bapuri.tycoon.shop.job.MinerShop) {
            return (kr.bapuri.tycoon.shop.job.MinerShop) shop;
        }
        return null;
    }
    
    /**
     * [Phase 4.C] FarmerShop 조회 (편의 메서드)
     */
    public kr.bapuri.tycoon.shop.job.FarmerShop getFarmerShop() {
        IShop shop = getShop("farmer");
        if (shop instanceof kr.bapuri.tycoon.shop.job.FarmerShop) {
            return (kr.bapuri.tycoon.shop.job.FarmerShop) shop;
        }
        return null;
    }
    
    /**
     * [Phase 4.D] FisherShop 조회 (편의 메서드)
     */
    public kr.bapuri.tycoon.shop.job.FisherShop getFisherShop() {
        IShop shop = getShop("fisher");
        if (shop instanceof kr.bapuri.tycoon.shop.job.FisherShop) {
            return (kr.bapuri.tycoon.shop.job.FisherShop) shop;
        }
        return null;
    }
    
    /**
     * FoodShop 조회 (편의 메서드)
     */
    public kr.bapuri.tycoon.shop.market.FoodShop getFoodShop() {
        IShop shop = getShop("food");
        if (shop instanceof kr.bapuri.tycoon.shop.market.FoodShop) {
            return (kr.bapuri.tycoon.shop.market.FoodShop) shop;
        }
        return null;
    }
    
    /**
     * LootShop 조회 (편의 메서드)
     */
    public kr.bapuri.tycoon.shop.market.LootShop getLootShop() {
        IShop shop = getShop("loot");
        if (shop instanceof kr.bapuri.tycoon.shop.market.LootShop) {
            return (kr.bapuri.tycoon.shop.market.LootShop) shop;
        }
        return null;
    }
    
    /**
     * SpawnEggShop 조회 (편의 메서드)
     */
    public kr.bapuri.tycoon.shop.general.SpawnEggShop getSpawnEggShop() {
        IShop shop = getShop("spawnegg");
        if (shop instanceof kr.bapuri.tycoon.shop.general.SpawnEggShop) {
            return (kr.bapuri.tycoon.shop.general.SpawnEggShop) shop;
        }
        return null;
    }
    
    /**
     * 모든 상점 조회
     */
    public Collection<IShop> getAllShops() {
        return Collections.unmodifiableCollection(shops.values());
    }
    
    /**
     * 상점 존재 여부
     */
    public boolean hasShop(String shopId) {
        return shops.containsKey(shopId.toLowerCase(Locale.ROOT));
    }
    
    // ========== 거래 처리 (위임) ==========
    
    /**
     * 상점 GUI 열기
     * 
     * @param player 플레이어
     * @param shopId 상점 ID
     * @return true면 성공
     */
    public boolean openShopGui(Player player, String shopId) {
        IShop shop = getShop(shopId);
        if (shop == null) {
            player.sendMessage("§c존재하지 않는 상점입니다: " + shopId);
            return false;
        }
        if (!shop.isEnabled()) {
            player.sendMessage("§c현재 이용할 수 없는 상점입니다: " + shop.getDisplayName());
            return false;
        }
        
        if (guiManager != null) {
            guiManager.openShop(player, shop);
            return true;
        } else {
            player.sendMessage("§c상점 GUI가 초기화되지 않았습니다.");
            logger.warning("[ShopService] GuiManager is null");
            return false;
        }
    }
    
    /**
     * 구매 처리
     * 
     * @param player 구매자
     * @param shopId 상점 ID
     * @param item 구매할 아이템
     * @return 거래 결과
     */
    public AbstractShop.TransactionResult processBuy(Player player, String shopId, ItemStack item) {
        IShop shop = getShop(shopId);
        if (shop == null) {
            return AbstractShop.TransactionResult.VALIDATION_FAILED;
        }
        
        if (shop instanceof AbstractShop abstractShop) {
            AbstractShop.TransactionResult result = abstractShop.executeBuy(player, item);
            
            // 동적 가격 기록
            if (result == AbstractShop.TransactionResult.SUCCESS && priceTracker != null) {
                priceTracker.recordBuy(player.getUniqueId(), player.getName(), 
                        item.getType().name(), item.getAmount());
            }
            
            return result;
        }
        
        return AbstractShop.TransactionResult.UNKNOWN_ERROR;
    }
    
    /**
     * 판매 처리
     * 
     * @param player 판매자
     * @param shopId 상점 ID
     * @param item 판매할 아이템
     * @return 거래 결과
     */
    public AbstractShop.TransactionResult processSell(Player player, String shopId, ItemStack item) {
        IShop shop = getShop(shopId);
        if (shop == null) {
            return AbstractShop.TransactionResult.VALIDATION_FAILED;
        }
        
        if (shop instanceof AbstractShop abstractShop) {
            AbstractShop.TransactionResult result = abstractShop.executeSell(player, item);
            
            // 동적 가격 기록
            if (result == AbstractShop.TransactionResult.SUCCESS && priceTracker != null) {
                priceTracker.recordSell(player.getUniqueId(), player.getName(),
                        item.getType().name(), item.getAmount());
            }
            
            return result;
        }
        
        return AbstractShop.TransactionResult.UNKNOWN_ERROR;
    }
    
    /**
     * [Phase 승급효과] 실제 아이템으로 판매 처리
     * 
     * CropGrade 등 ItemMeta가 있는 아이템을 정확히 처리합니다.
     * 기존 processSell과 동일하지만, 호출 의도를 명확히 합니다.
     * 
     * @param player 판매자
     * @param shopId 상점 ID
     * @param item 실제 인벤토리의 아이템 (ItemMeta 포함)
     * @return 거래 결과
     */
    public AbstractShop.TransactionResult processSellActual(Player player, String shopId, ItemStack item) {
        // processSell과 동일 - Bukkit의 removeItem()이 isSimilar()로 ItemMeta까지 비교함
        return processSell(player, shopId, item);
    }
    
    // ========== 라이프사이클 ==========
    
    /**
     * 모든 상점 저장
     */
    public void saveAll() {
        for (IShop shop : shops.values()) {
            shop.save();
        }
        saveConfig();
        
        if (priceTracker != null) {
            priceTracker.save();
        }
    }
    
    /**
     * 설정 다시 로드
     * 
     * @return 성공한 상점 수, 실패한 상점 수를 담은 배열 [success, failed]
     */
    public int[] reload() {
        // 열린 상점 GUI 모두 닫기 (오래된 가격 표시 방지)
        if (guiManager != null) {
            guiManager.closeAllShops();
        }
        
        // 설정 파일 다시 로드
        shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
        
        int successCount = 0;
        int failedCount = 0;
        
        // 각 상점 리로드 (shops.yml에서 설정 다시 읽기)
        for (IShop shop : shops.values()) {
            boolean success = false;
            
            // JobShop (직업 상점)
            if (shop instanceof kr.bapuri.tycoon.shop.job.JobShop jobShop) {
                ConfigurationSection section = getShopConfig(shop.getShopId());
                success = jobShop.loadFromYaml(section);
            }
            // MarketShop (마켓 상점 - 동적 가격 + 비대칭 보너스)
            else if (shop instanceof kr.bapuri.tycoon.shop.market.MarketShop marketShop) {
                ConfigurationSection section = getMarketShopConfig(shop.getShopId());
                success = marketShop.loadFromYaml(section);
            }
            // FixedPriceShop (일반 상점 - 고정 가격)
            else if (shop instanceof kr.bapuri.tycoon.shop.general.FixedPriceShop fixedShop) {
                ConfigurationSection section = getGeneralShopConfig(shop.getShopId());
                success = fixedShop.loadFromYaml(section);
            }
            else {
                // 다른 타입의 상점은 리로드 미지원 (성공으로 처리)
                success = true;
            }
            
            if (success) {
                successCount++;
            } else {
                failedCount++;
                logger.warning("[ShopService] 상점 리로드 실패: " + shop.getShopId());
            }
        }
        
        if (failedCount == 0) {
            logger.info("[ShopService] 설정 리로드 완료 (" + successCount + "개 상점)");
        } else {
            logger.warning("[ShopService] 리로드 부분 실패 (성공: " + successCount + ", 실패: " + failedCount + ")");
        }
        
        return new int[]{successCount, failedCount};
    }
    
    /**
     * 서비스 종료
     */
    public void shutdown() {
        saveAll();
        
        if (priceTracker != null) {
            priceTracker.stopUpdateTask();
        }
        
        logger.info("[ShopService] 종료됨");
    }
    
    // ========== Getter ==========
    
    public Plugin getPlugin() {
        return plugin;
    }
    
    public EconomyService getEconomyService() {
        return economyService;
    }
}
