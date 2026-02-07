package kr.bapuri.tycoon.shop.market;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import kr.bapuri.tycoon.shop.AbstractShop;
import kr.bapuri.tycoon.shop.ShopItem;
import kr.bapuri.tycoon.shop.price.DynamicPriceTracker;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 마켓 상점 추상 클래스
 * 
 * <h2>특징</h2>
 * <ul>
 *   <li>동적 가격 시스템 (DynamicPriceTracker 연동)</li>
 *   <li>비대칭 레벨 보너스 (구매가 증가율 > 판매가 증가율)</li>
 *   <li>설정 파일(shops.yml)에서 품목/가격 로드</li>
 * </ul>
 * 
 * <h2>비대칭 레벨 보너스</h2>
 * <ul>
 *   <li>판매 보너스: 레벨당 +5% (낮게 - 판매에 유리)</li>
 *   <li>구매 보너스: 레벨당 +10% (높게 - 고레벨일수록 비싸게 구매)</li>
 *   <li>효과: 직접 획득(직업 활동) 유도, 상점 구매 억제</li>
 * </ul>
 * 
 * <h2>상속 구조</h2>
 * <pre>
 * IShop → AbstractShop → MarketShop → FoodShop/LootShop
 * </pre>
 * 
 * @see kr.bapuri.tycoon.shop.ShopService
 * @see DynamicPriceTracker
 */
public abstract class MarketShop extends AbstractShop {
    
    // 동적 가격 추적기
    protected DynamicPriceTracker priceTracker;
    
    // 플레이어 데이터 (레벨 조회용)
    protected PlayerDataManager playerDataManager;
    
    // 비대칭 레벨 보너스 (shops.yml에서 로드)
    protected double sellBonusPercent = 5.0;   // 판매: 레벨당 +5%
    protected double buyBonusPercent = 10.0;   // 구매: 레벨당 +10%
    
    // 아이템 목록 (Material → ShopItemEntry)
    protected final Map<Material, ShopItemEntry> items = new LinkedHashMap<>();
    
    protected MarketShop(Plugin plugin, EconomyService economyService, String shopId, String displayName) {
        super(plugin, economyService, shopId, displayName);
    }
    
    /**
     * PlayerDataManager 설정 (레벨 조회용)
     */
    public void setPlayerDataManager(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }
    
    /**
     * 동적 가격 추적기 설정
     * 이미 등록된 아이템들도 추적기에 등록
     */
    public void setPriceTracker(DynamicPriceTracker priceTracker) {
        this.priceTracker = priceTracker;
        
        // 이미 등록된 아이템들을 추적기에 등록
        if (priceTracker != null && !items.isEmpty()) {
            for (ShopItemEntry entry : items.values()) {
                if (entry.canBuy || entry.canSell) {
                    long basePrice = entry.canBuy ? entry.buyPrice : entry.sellPrice;
                    if (basePrice > 0) {
                        long minPrice = (long)(basePrice * 0.3);
                        long maxPrice = (long)(basePrice * 1.7);
                        priceTracker.registerItem(entry.material.name(), basePrice, minPrice, maxPrice);
                    }
                }
            }
            logger.info("[MarketShop] " + shopId + ": " + items.size() + "개 아이템을 DynamicPriceTracker에 등록");
        }
    }
    
    // ========== 아이템 초기화 ==========
    
    /**
     * 기본 아이템 초기화 (서브클래스에서 구현)
     */
    protected abstract void initItems();
    
    /**
     * 아이템 등록
     */
    protected void registerItem(Material material, long buyPrice, long sellPrice, 
                                boolean canBuy, boolean canSell) {
        // 차익거래 방지
        if (canBuy && canSell && sellPrice >= buyPrice) {
            sellPrice = (long)(buyPrice * 0.5);
            logger.warning("[MarketShop] 차익거래 방지 적용: " + material + " sell=" + sellPrice);
        }
        
        ShopItemEntry entry = new ShopItemEntry(material, buyPrice, sellPrice, canBuy, canSell);
        items.put(material, entry);
        
        // 동적 가격 추적기에 등록
        if (priceTracker != null && (canBuy || canSell)) {
            long basePrice = canBuy ? buyPrice : sellPrice;
            long minPrice = (long)(basePrice * 0.3);
            long maxPrice = (long)(basePrice * 1.7);
            priceTracker.registerItem(material.name(), basePrice, minPrice, maxPrice);
        }
    }
    
    /**
     * 간편 등록 (구매+판매 모두 가능, 판매가 = 구매가 × 0.5)
     */
    protected void registerItem(Material material, long basePrice) {
        registerItem(material, basePrice, (long)(basePrice * 0.5), true, true);
    }
    
    // ========== 설정 파일 로드 ==========
    
    /**
     * shops.yml에서 품목/가격 로드
     * 
     * @param section market_shops.{shopId} 섹션
     * @return 로드 성공 여부
     */
    public boolean loadFromYaml(ConfigurationSection section) {
        // 임시 저장 (롤백용)
        Map<Material, ShopItemEntry> backup = new LinkedHashMap<>(items);
        
        // 기존 아이템 초기화
        items.clear();
        
        if (section == null) {
            logger.warning("[MarketShop] " + shopId + ": 설정 섹션이 없음 - initItems() 폴백 사용");
            initItems();
            return true;
        }
        
        // enabled 체크
        enabled = section.getBoolean("enabled", true);
        
        // display_name (옵션)
        String configDisplayName = section.getString("display_name");
        if (configDisplayName != null && !configDisplayName.isEmpty()) {
            this.displayName = configDisplayName;
        }
        
        // 비대칭 보너스 퍼센트 로드
        this.sellBonusPercent = section.getDouble("sell_bonus_percent", 5.0);
        this.buyBonusPercent = section.getDouble("buy_bonus_percent", 10.0);
        
        // items 섹션 로드
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection == null) {
            logger.warning("[MarketShop] " + shopId + ": items 섹션이 없음 - initItems() 폴백 사용");
            initItems();
            return true;
        }
        
        int loadedCount = 0;
        int errorCount = 0;
        List<String> arbitrageErrors = new ArrayList<>();
        
        for (String key : itemsSection.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());
                ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                
                long buy, sell;
                
                if (itemSec != null) {
                    buy = itemSec.getLong("buy", -1);
                    sell = itemSec.getLong("sell", -1);
                } else {
                    buy = -1;
                    sell = -1;
                }
                
                boolean canBuy = buy > 0;
                boolean canSell = sell > 0;
                
                if (!canBuy && !canSell) {
                    logger.warning("[MarketShop] " + shopId + ": " + key + " - buy/sell 둘 다 없음, 무시됨");
                    errorCount++;
                    continue;
                }
                
                // 차익거래 설정 오류 감지
                if (canBuy && canSell && sell >= buy) {
                    arbitrageErrors.add(key + " (buy=" + buy + ", sell=" + sell + ")");
                    errorCount++;
                    continue;
                }
                
                registerItem(material, buy, sell, canBuy, canSell);
                loadedCount++;
                
            } catch (IllegalArgumentException e) {
                logger.warning("[MarketShop] " + shopId + ": 알 수 없는 Material - " + key);
                errorCount++;
            }
        }
        
        // 차익거래 오류가 있으면 리로드 실패 + 롤백
        if (!arbitrageErrors.isEmpty()) {
            logger.severe("[MarketShop] " + shopId + ": 차익거래 설정 오류 발견! 리로드 취소");
            for (String err : arbitrageErrors) {
                logger.severe("[MarketShop]   - " + err + " (sell >= buy 불가!)");
            }
            items.clear();
            items.putAll(backup);
            return false;
        }
        
        logger.info("[MarketShop] " + shopId + ": " + loadedCount + "개 아이템 로드 완료" + 
                   (errorCount > 0 ? " (" + errorCount + "개 오류)" : "") +
                   " (sell+" + sellBonusPercent + "%/lv, buy+" + buyBonusPercent + "%/lv)");
        return true;
    }
    
    // ========== 레벨 조회 ==========
    
    /**
     * 플레이어의 최고 직업 레벨 조회
     * 
     * 보유 직업 중 최고 레벨 반환
     */
    protected int getPlayerLevel(Player player) {
        if (playerDataManager == null || player == null) return 0;
        
        PlayerTycoonData data = playerDataManager.get(player.getUniqueId());
        if (data == null) return 0;
        
        // 보유 직업 중 최고 레벨 반환
        int maxLevel = 0;
        
        // Tier 1 직업 레벨
        if (data.getTier1Job() != null) {
            int tier1Level = data.getTier1JobLevel();
            if (tier1Level > maxLevel) maxLevel = tier1Level;
        }
        
        // Tier 2 직업 레벨
        if (data.getTier2Job() != null) {
            int tier2Level = data.getTier2JobLevel();
            if (tier2Level > maxLevel) maxLevel = tier2Level;
        }
        
        return maxLevel;
    }
    
    // ========== IShop 구현 ==========
    
    @Override
    public long getBuyPrice(ItemStack item) {
        if (item == null) return -1;
        ShopItemEntry entry = items.get(item.getType());
        if (entry == null || !entry.canBuy) return -1;
        
        // 동적 가격 적용
        if (priceTracker != null && priceTracker.isRegistered(item.getType().name())) {
            long dynamicPrice = priceTracker.getBuyPrice(item.getType().name());
            if (dynamicPrice > 0) return dynamicPrice;
        }
        
        return entry.buyPrice;
    }
    
    @Override
    public long getSellPrice(ItemStack item) {
        if (item == null) return -1;
        ShopItemEntry entry = items.get(item.getType());
        if (entry == null || !entry.canSell) return -1;
        
        // 동적 가격 적용
        if (priceTracker != null && priceTracker.isRegistered(item.getType().name())) {
            long dynamicPrice = priceTracker.getSellPrice(item.getType().name());
            if (dynamicPrice > 0) return dynamicPrice;
        }
        
        return entry.sellPrice;
    }
    
    /**
     * 플레이어별 구매가 조회 (비대칭 레벨 보너스 적용)
     * 
     * 공식: basePrice × (1 + buyBonusPercent/100)^level
     * 고레벨일수록 비싸게 구매 (상점 구매 억제)
     */
    @Override
    public long getBuyPrice(Player player, ItemStack item) {
        long basePrice = getBuyPrice(item);
        if (basePrice <= 0) return basePrice;
        
        int level = getPlayerLevel(player);
        if (level <= 0) return basePrice;
        
        // 높은 보너스율 적용 (고레벨일수록 비싸게)
        double multiplier = Math.pow(1.0 + buyBonusPercent / 100.0, level);
        return Math.round(basePrice * multiplier);
    }
    
    /**
     * 플레이어별 판매가 조회 (비대칭 레벨 보너스 + 등급 보너스 적용)
     * 
     * 공식: basePrice × (1 + sellBonusPercent/100)^level × gradeMultiplier
     * 낮은 보너스율 적용 (판매에 유리)
     * 
     * [2026-02-02] CropGrade 보너스 추가 (BAKED_POTATO, DRIED_KELP, POPPED_CHORUS_FRUIT)
     */
    @Override
    public long getSellPrice(Player player, ItemStack item) {
        long basePrice = getSellPrice(item);
        if (basePrice <= 0) return basePrice;
        
        double multiplier = 1.0;
        
        // 레벨 보너스 적용
        int level = getPlayerLevel(player);
        if (level > 0) {
            multiplier = Math.pow(1.0 + sellBonusPercent / 100.0, level);
        }
        
        // [2026-02-02] CropGrade 보너스 적용 (가공품 등급 유지)
        if (item != null) {
            kr.bapuri.tycoon.job.farmer.CropGrade cropGrade = 
                kr.bapuri.tycoon.job.farmer.CropGrade.getGrade(item);
            if (cropGrade != null && cropGrade.getPriceMultiplier() > 1.0) {
                multiplier *= cropGrade.getPriceMultiplier();
            }
        }
        
        return Math.round(basePrice * multiplier);
    }
    
    @Override
    public boolean canBuy(Player player, ItemStack item) {
        if (item == null) return false;
        ShopItemEntry entry = items.get(item.getType());
        return entry != null && entry.canBuy && entry.buyPrice > 0;
    }
    
    @Override
    public boolean canSell(Player player, ItemStack item) {
        if (item == null) return false;
        ShopItemEntry entry = items.get(item.getType());
        return entry != null && entry.canSell && entry.sellPrice > 0;
    }
    
    @Override
    public List<ShopItem> getItems() {
        List<ShopItem> result = new ArrayList<>();
        for (ShopItemEntry entry : items.values()) {
            if (entry.canBuy || entry.canSell) {
                result.add(ShopItem.createSafe(entry.material, entry.buyPrice, entry.sellPrice, true));
            }
        }
        return result;
    }
    
    @Override
    public void load() {
        if (items.isEmpty()) {
            logger.info("[MarketShop] " + shopId + ": items 비어있음 - initItems() 폴백");
            initItems();
        }
        if (priceTracker != null) {
            priceTracker.load();
        }
    }
    
    @Override
    public void save() {
        if (priceTracker != null) {
            priceTracker.save();
        }
    }
    
    // ========== 내부 클래스 ==========
    
    /**
     * 상점 아이템 정보
     */
    protected static class ShopItemEntry {
        final Material material;
        final long buyPrice;
        final long sellPrice;
        final boolean canBuy;
        final boolean canSell;
        
        ShopItemEntry(Material material, long buyPrice, long sellPrice, boolean canBuy, boolean canSell) {
            this.material = material;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.canBuy = canBuy;
            this.canSell = canSell;
        }
    }
}
