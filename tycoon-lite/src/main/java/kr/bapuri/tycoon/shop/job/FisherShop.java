package kr.bapuri.tycoon.shop.job;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.job.fisher.FisherExpService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 어부 상점
 * 
 * [Phase 3.B] 수산물 거래
 * [Phase 4.D] FisherExpService 연동 - 레벨 보너스/판매 XP
 * 
 * <h2>설정 파일 기반</h2>
 * <p>품목과 가격은 <b>shops.yml</b>에서 관리됩니다.</p>
 * <p>{@link #initItems()}는 shops.yml에 items 섹션이 없을 때만 사용되는 폴백입니다.</p>
 * 
 * @see kr.bapuri.tycoon.shop.job.JobShop#loadFromYaml
 */
public class FisherShop extends JobShop {
    
    private FisherExpService expService;
    
    public FisherShop(Plugin plugin, EconomyService economyService) {
        super(plugin, economyService, "fisher", "어부 상점");
    }
    
    /**
     * [Phase 4.D] FisherExpService 설정
     * [Phase 4.E] 부모 클래스의 expService도 함께 설정
     */
    public void setExpService(FisherExpService expService) {
        this.expService = expService;
        super.expService = expService;  // [Phase 4.E] JobShop 공통 연동
    }
    
    /**
     * [Phase 4.D] 판매 시 경험치 부여
     */
    public void grantSaleExp(Player player, ItemStack item, int amount) {
        if (expService != null && player != null && item != null) {
            expService.addSaleExp(player, item.getType(), amount);
        }
    }
    
    /**
     * [Phase 4.D] 레벨 적용 판매가 조회
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
        registerItem(Material.COD, 30, 15, true, true);
        registerItem(Material.SALMON, 40, 20, true, true);
        registerItem(Material.TROPICAL_FISH, 100, 50, true, true);
        registerItem(Material.PUFFERFISH, 80, 40, true, true);
        
        // ========== 조리된 생선 ==========
        registerItem(Material.COOKED_COD, 50, 25, true, true);
        registerItem(Material.COOKED_SALMON, 70, 35, true, true);
        
        // ========== 낚시 전리품 (판매만) ==========
        registerItem(Material.INK_SAC, -1, 20, false, true);
        registerItem(Material.GLOW_INK_SAC, -1, 80, false, true);
        registerItem(Material.NAUTILUS_SHELL, -1, 500, false, true);
        registerItem(Material.HEART_OF_THE_SEA, -1, 5000, false, true);
        
        // ========== 해양 생물 (판매만) ==========
        registerItem(Material.KELP, -1, 5, false, true);
        registerItem(Material.DRIED_KELP, -1, 8, false, true);
        registerItem(Material.SEA_PICKLE, -1, 30, false, true);
        registerItem(Material.SEAGRASS, -1, 3, false, true);
        
        // ========== 쓰레기 (낮은 가격) ==========
        registerItem(Material.LILY_PAD, -1, 10, false, true);
        registerItem(Material.BOWL, -1, 2, false, true);
        registerItem(Material.LEATHER_BOOTS, -1, 15, false, true);
        
        // ========== 낚싯대/도구 (구매만) ==========
        registerItem(Material.FISHING_ROD, 100, -1, true, false);
        
        // ========== 프리즈머린 (판매만) ==========
        registerItem(Material.PRISMARINE_SHARD, -1, 40, false, true);
        registerItem(Material.PRISMARINE_CRYSTALS, -1, 60, false, true);
        registerItem(Material.PRISMARINE, -1, 200, false, true);
        registerItem(Material.DARK_PRISMARINE, -1, 250, false, true);
        registerItem(Material.SEA_LANTERN, -1, 400, false, true);
    }
}
