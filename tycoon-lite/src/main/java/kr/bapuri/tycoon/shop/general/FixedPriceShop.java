package kr.bapuri.tycoon.shop.general;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.shop.AbstractShop;
import kr.bapuri.tycoon.shop.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 고정 가격 상점 추상 클래스
 * 
 * <h2>특징</h2>
 * <ul>
 *   <li>동적 가격 없음 (DynamicPriceTracker 미연동)</li>
 *   <li>레벨 보너스 없음 (모든 플레이어 동일 가격)</li>
 *   <li>설정 파일(shops.yml)에서 품목/가격 로드</li>
 * </ul>
 * 
 * <h2>사용 사례</h2>
 * <ul>
 *   <li>동물 스폰알 상점 (SpawnEggShop)</li>
 *   <li>기타 고정 가격이 필요한 상점</li>
 * </ul>
 * 
 * <h2>상속 구조</h2>
 * <pre>
 * IShop → AbstractShop → FixedPriceShop → SpawnEggShop
 * </pre>
 * 
 * @see kr.bapuri.tycoon.shop.ShopService
 */
public abstract class FixedPriceShop extends AbstractShop {
    
    // 아이템 목록 (Material → ShopItemEntry)
    protected final Map<Material, ShopItemEntry> items = new LinkedHashMap<>();
    
    protected FixedPriceShop(Plugin plugin, EconomyService economyService, String shopId, String displayName) {
        super(plugin, economyService, shopId, displayName);
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
            logger.warning("[FixedPriceShop] 차익거래 방지 적용: " + material + " sell=" + sellPrice);
        }
        
        ShopItemEntry entry = new ShopItemEntry(material, buyPrice, sellPrice, canBuy, canSell);
        items.put(material, entry);
    }
    
    /**
     * 간편 등록 (구매만 가능)
     */
    protected void registerBuyOnly(Material material, long buyPrice) {
        registerItem(material, buyPrice, -1, true, false);
    }
    
    /**
     * 간편 등록 (판매만 가능)
     */
    protected void registerSellOnly(Material material, long sellPrice) {
        registerItem(material, -1, sellPrice, false, true);
    }
    
    // ========== 설정 파일 로드 ==========
    
    /**
     * shops.yml에서 품목/가격 로드
     * 
     * @param section general_shops.{shopId} 섹션
     * @return 로드 성공 여부
     */
    public boolean loadFromYaml(ConfigurationSection section) {
        // 임시 저장 (롤백용)
        Map<Material, ShopItemEntry> backup = new LinkedHashMap<>(items);
        
        // 기존 아이템 초기화
        items.clear();
        
        if (section == null) {
            logger.warning("[FixedPriceShop] " + shopId + ": 설정 섹션이 없음 - initItems() 폴백 사용");
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
        
        // items 섹션 로드
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection == null) {
            logger.warning("[FixedPriceShop] " + shopId + ": items 섹션이 없음 - initItems() 폴백 사용");
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
                    logger.warning("[FixedPriceShop] " + shopId + ": " + key + " - buy/sell 둘 다 없음, 무시됨");
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
                logger.warning("[FixedPriceShop] " + shopId + ": 알 수 없는 Material - " + key);
                errorCount++;
            }
        }
        
        // 차익거래 오류가 있으면 리로드 실패 + 롤백
        if (!arbitrageErrors.isEmpty()) {
            logger.severe("[FixedPriceShop] " + shopId + ": 차익거래 설정 오류 발견! 리로드 취소");
            for (String err : arbitrageErrors) {
                logger.severe("[FixedPriceShop]   - " + err + " (sell >= buy 불가!)");
            }
            items.clear();
            items.putAll(backup);
            return false;
        }
        
        logger.info("[FixedPriceShop] " + shopId + ": " + loadedCount + "개 아이템 로드 완료" + 
                   (errorCount > 0 ? " (" + errorCount + "개 오류)" : ""));
        return true;
    }
    
    // ========== IShop 구현 ==========
    
    @Override
    public long getBuyPrice(ItemStack item) {
        if (item == null) return -1;
        ShopItemEntry entry = items.get(item.getType());
        return entry != null && entry.canBuy ? entry.buyPrice : -1;
    }
    
    @Override
    public long getSellPrice(ItemStack item) {
        if (item == null) return -1;
        ShopItemEntry entry = items.get(item.getType());
        return entry != null && entry.canSell ? entry.sellPrice : -1;
    }
    
    // 고정 가격이므로 플레이어별 가격 = 기본 가격
    @Override
    public long getBuyPrice(Player player, ItemStack item) {
        return getBuyPrice(item);
    }
    
    @Override
    public long getSellPrice(Player player, ItemStack item) {
        return getSellPrice(item);
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
                result.add(ShopItem.createSafe(entry.material, entry.buyPrice, entry.sellPrice, false));
            }
        }
        return result;
    }
    
    @Override
    public void load() {
        if (items.isEmpty()) {
            logger.info("[FixedPriceShop] " + shopId + ": items 비어있음 - initItems() 폴백");
            initItems();
        }
    }
    
    @Override
    public void save() {
        // 고정 가격이므로 저장할 동적 데이터 없음
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
