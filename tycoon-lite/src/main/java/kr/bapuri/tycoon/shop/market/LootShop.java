package kr.bapuri.tycoon.shop.market;

import kr.bapuri.tycoon.economy.EconomyService;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

/**
 * 몬스터 전리품 상점
 * 
 * <h2>특징</h2>
 * <ul>
 *   <li>동적 가격 시스템 적용</li>
 *   <li>비대칭 레벨 보너스 (구매가 증가율 > 판매가 증가율)</li>
 *   <li>대부분 판매 전용 (몬스터 드롭 아이템)</li>
 * </ul>
 * 
 * <h2>동적 가격 효과</h2>
 * <ul>
 *   <li>대량 판매 시 가격 하락</li>
 *   <li>판매 없으면 기준가로 회복</li>
 *   <li>시장 조작 방지 (ManipulationGuard)</li>
 * </ul>
 * 
 * <h2>설정 파일 기반</h2>
 * <p>품목과 가격은 <b>shops.yml</b>의 market_shops.loot에서 관리됩니다.</p>
 * 
 * @see MarketShop
 */
public class LootShop extends MarketShop {
    
    public LootShop(Plugin plugin, EconomyService economyService) {
        super(plugin, economyService, "loot", "전리품 상점");
    }
    
    /**
     * [폴백 전용] shops.yml에 items 섹션이 없을 때만 사용
     * 
     * <p>⚠️ 실제 품목/가격은 shops.yml에서 관리하세요!</p>
     */
    @Override
    protected void initItems() {
        // ========== 오버월드 일반 몬스터 (판매만) ==========
        registerItem(Material.ROTTEN_FLESH, -1, 5, false, true);
        registerItem(Material.BONE, -1, 10, false, true);
        registerItem(Material.SPIDER_EYE, -1, 15, false, true);
        registerItem(Material.STRING, -1, 8, false, true);
        registerItem(Material.GUNPOWDER, -1, 20, false, true);
        registerItem(Material.ARROW, -1, 5, false, true);
        registerItem(Material.SLIME_BALL, -1, 25, false, true);
        registerItem(Material.PHANTOM_MEMBRANE, -1, 100, false, true);
        registerItem(Material.LEATHER, -1, 15, false, true);
        registerItem(Material.FEATHER, -1, 5, false, true);
        registerItem(Material.EGG, -1, 3, false, true);
        
        // ========== 특수 오버월드 드롭 ==========
        registerItem(Material.ENDER_PEARL, -1, 150, false, true);
        registerItem(Material.RABBIT_FOOT, -1, 200, false, true);
        registerItem(Material.RABBIT_HIDE, -1, 20, false, true);
        registerItem(Material.SPIDER_EYE, -1, 15, false, true);
        registerItem(Material.FERMENTED_SPIDER_EYE, -1, 50, false, true);
        
        // ========== 네더 몬스터 ==========
        registerItem(Material.BLAZE_ROD, -1, 100, false, true);
        registerItem(Material.BLAZE_POWDER, -1, 60, false, true);
        registerItem(Material.GHAST_TEAR, -1, 200, false, true);
        registerItem(Material.MAGMA_CREAM, -1, 80, false, true);
        registerItem(Material.WITHER_SKELETON_SKULL, -1, 5000, false, true);
        registerItem(Material.NETHER_WART, -1, 20, false, true);
        
        // ========== 엔드 ==========
        registerItem(Material.SHULKER_SHELL, -1, 1000, false, true);
        registerItem(Material.DRAGON_BREATH, -1, 500, false, true);
        registerItem(Material.CHORUS_FRUIT, -1, 30, false, true);
        registerItem(Material.POPPED_CHORUS_FRUIT, -1, 50, false, true);
        
        // ========== 보스 드롭 ==========
        registerItem(Material.TOTEM_OF_UNDYING, -1, 10000, false, true);
        registerItem(Material.NETHER_STAR, -1, 50000, false, true);
        registerItem(Material.DRAGON_EGG, -1, 100000, false, true);
        
        // ========== 트라이던트 & 기타 ==========
        registerItem(Material.TRIDENT, -1, 15000, false, true);
        registerItem(Material.NAUTILUS_SHELL, -1, 500, false, true);
        registerItem(Material.HEART_OF_THE_SEA, -1, 5000, false, true);
        
        // ========== 경험치 (구매+판매) ==========
        registerItem(Material.EXPERIENCE_BOTTLE, 50, 25, true, true);
    }
}
