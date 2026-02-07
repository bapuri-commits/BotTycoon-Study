package kr.bapuri.tycoon.shop.market;

import kr.bapuri.tycoon.economy.EconomyService;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

/**
 * 음식 상점
 * 
 * <h2>특징</h2>
 * <ul>
 *   <li>동적 가격 시스템 적용</li>
 *   <li>비대칭 레벨 보너스 (구매가 증가율 > 판매가 증가율)</li>
 *   <li>바닐라 음식 아이템 거래</li>
 * </ul>
 * 
 * <h2>설정 파일 기반</h2>
 * <p>품목과 가격은 <b>shops.yml</b>의 market_shops.food에서 관리됩니다.</p>
 * <p>{@link #initItems()}는 shops.yml에 items 섹션이 없을 때만 사용되는 폴백입니다.</p>
 * 
 * @see MarketShop
 */
public class FoodShop extends MarketShop {
    
    public FoodShop(Plugin plugin, EconomyService economyService) {
        super(plugin, economyService, "food", "음식 상점");
    }
    
    /**
     * [폴백 전용] shops.yml에 items 섹션이 없을 때만 사용
     * 
     * <p>⚠️ 실제 품목/가격은 shops.yml에서 관리하세요!</p>
     */
    @Override
    protected void initItems() {
        // ========== 조리된 고기 (구매+판매) ==========
        registerItem(Material.COOKED_BEEF, 40, 20, true, true);
        registerItem(Material.COOKED_PORKCHOP, 40, 20, true, true);
        registerItem(Material.COOKED_CHICKEN, 30, 15, true, true);
        registerItem(Material.COOKED_MUTTON, 35, 17, true, true);
        registerItem(Material.COOKED_RABBIT, 35, 17, true, true);
        registerItem(Material.COOKED_COD, 35, 17, true, true);
        registerItem(Material.COOKED_SALMON, 45, 22, true, true);
        
        // ========== 빵/곡물 가공품 ==========
        registerItem(Material.BREAD, 20, 10, true, true);
        registerItem(Material.BAKED_POTATO, 15, 7, true, true);
        registerItem(Material.COOKIE, 10, 5, true, true);
        registerItem(Material.PUMPKIN_PIE, 50, 25, true, true);
        registerItem(Material.CAKE, 150, -1, true, false);  // 구매만
        
        // ========== 스튜/복합 음식 ==========
        registerItem(Material.MUSHROOM_STEW, 60, 30, true, true);
        registerItem(Material.RABBIT_STEW, 80, 40, true, true);
        registerItem(Material.BEETROOT_SOUP, 40, 20, true, true);
        registerItem(Material.SUSPICIOUS_STEW, 100, 50, true, true);
        
        // ========== 특수 음식 ==========
        registerItem(Material.GOLDEN_CARROT, 100, 50, true, true);
        registerItem(Material.GOLDEN_APPLE, 500, 250, true, true);
        registerItem(Material.ENCHANTED_GOLDEN_APPLE, 50000, 25000, true, true);
        
        // ========== 기타 음식 ==========
        registerItem(Material.APPLE, 15, 7, true, true);
        registerItem(Material.MELON_SLICE, 10, 5, true, true);
        registerItem(Material.SWEET_BERRIES, 8, 4, true, true);
        registerItem(Material.GLOW_BERRIES, 20, 10, true, true);
        registerItem(Material.DRIED_KELP, 8, 4, true, true);
        registerItem(Material.HONEY_BOTTLE, 80, 40, true, true);
        
        // ========== 생고기 (판매만 - 조리 유도) ==========
        registerItem(Material.BEEF, -1, 10, false, true);
        registerItem(Material.PORKCHOP, -1, 10, false, true);
        registerItem(Material.CHICKEN, -1, 7, false, true);
        registerItem(Material.MUTTON, -1, 8, false, true);
        registerItem(Material.RABBIT, -1, 8, false, true);
    }
}
