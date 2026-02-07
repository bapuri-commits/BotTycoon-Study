package kr.bapuri.tycoon.shop.job;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.job.farmer.FarmerExpService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 농부 상점
 * 
 * [Phase 3.B] 농산물 거래
 * [Phase 4.C] FarmerExpService 연동
 * 
 * <h2>설정 파일 기반</h2>
 * <p>품목과 가격은 <b>shops.yml</b>에서 관리됩니다.</p>
 * <p>{@link #initItems()}는 shops.yml에 items 섹션이 없을 때만 사용되는 폴백입니다.</p>
 * 
 * @see kr.bapuri.tycoon.shop.job.JobShop#loadFromYaml
 */
public class FarmerShop extends JobShop {
    
    private FarmerExpService expService;
    
    public FarmerShop(Plugin plugin, EconomyService economyService) {
        super(plugin, economyService, "farmer", "농부 상점");
    }
    
    /**
     * [Phase 4.C] FarmerExpService 설정
     * [Phase 4.E] 부모 클래스의 expService도 함께 설정
     */
    public void setExpService(FarmerExpService expService) {
        this.expService = expService;
        super.expService = expService;  // [Phase 4.E] JobShop 공통 연동
    }
    
    /**
     * [Phase 4.C] 판매 시 경험치 부여
     */
    public void grantSaleExp(Player player, ItemStack item, int amount) {
        if (expService != null && player != null && item != null) {
            expService.addSaleExp(player, item.getType(), amount);
        }
    }
    
    /**
     * [Phase 4.C] 레벨 적용 판매가 조회
     */
    public long getLevelAdjustedSellPrice(ItemStack item, Player player) {
        if (expService != null && player != null && item != null) {
            return expService.getActualPrice(item.getType(), player);
        }
        // 기본 판매가 사용
        return getSellPrice(item);
    }
    
    /**
     * [폴백 전용] shops.yml에 items 섹션이 없을 때만 사용
     * 
     * <p>⚠️ 실제 품목/가격은 shops.yml에서 관리하세요!</p>
     */
    @Override
    protected void initItems() {
        // ========== 폴백 기본값 ==========
        registerItem(Material.WHEAT, 20, 10, true, true);
        registerItem(Material.CARROT, 16, 8, true, true);
        registerItem(Material.POTATO, 16, 8, true, true);
        registerItem(Material.BEETROOT, 18, 9, true, true);
        
        // ========== 특수 작물 ==========
        registerItem(Material.MELON_SLICE, 10, 5, true, true);
        registerItem(Material.PUMPKIN, 30, 15, true, true);
        registerItem(Material.SUGAR_CANE, 14, 7, true, true);
        registerItem(Material.COCOA_BEANS, 24, 12, true, true);
        registerItem(Material.BAMBOO, 8, 4, true, true);
        registerItem(Material.CACTUS, 12, 6, true, true);
        
        // ========== 네더 작물 ==========
        registerItem(Material.NETHER_WART, 40, 20, true, true);
        registerItem(Material.CRIMSON_FUNGUS, 50, 25, true, true);
        registerItem(Material.WARPED_FUNGUS, 50, 25, true, true);
        
        // ========== 씨앗 (구매용) ==========
        registerItem(Material.WHEAT_SEEDS, 4, 2, true, true);
        registerItem(Material.BEETROOT_SEEDS, 8, 4, true, true);
        registerItem(Material.MELON_SEEDS, 20, 10, true, true);
        registerItem(Material.PUMPKIN_SEEDS, 20, 10, true, true);
        
        // ========== 묘목 (구매용) ==========
        registerItem(Material.OAK_SAPLING, 10, 5, true, true);
        registerItem(Material.SPRUCE_SAPLING, 10, 5, true, true);
        registerItem(Material.BIRCH_SAPLING, 10, 5, true, true);
        registerItem(Material.JUNGLE_SAPLING, 20, 10, true, true);
        registerItem(Material.ACACIA_SAPLING, 15, 8, true, true);
        registerItem(Material.DARK_OAK_SAPLING, 15, 8, true, true);
        registerItem(Material.CHERRY_SAPLING, 30, 15, true, true);
        
        // ========== 꽃/장식 (판매만) ==========
        registerItem(Material.DANDELION, -1, 3, false, true);
        registerItem(Material.POPPY, -1, 3, false, true);
        registerItem(Material.BLUE_ORCHID, -1, 8, false, true);
        registerItem(Material.ALLIUM, -1, 5, false, true);
        registerItem(Material.AZURE_BLUET, -1, 5, false, true);
        registerItem(Material.RED_TULIP, -1, 5, false, true);
        registerItem(Material.SUNFLOWER, -1, 10, false, true);
        registerItem(Material.LILAC, -1, 8, false, true);
        registerItem(Material.ROSE_BUSH, -1, 8, false, true);
        registerItem(Material.PEONY, -1, 8, false, true);
    }
}
