package kr.bapuri.tycoon.shop.job;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.job.miner.MinerExpService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 광부 상점
 * 
 * [Phase 4.B] 광석/광물 거래 + 경험치 연동
 * 
 * <h2>설정 파일 기반</h2>
 * <p>품목과 가격은 <b>shops.yml</b>에서 관리됩니다.</p>
 * <p>{@link #initItems()}는 shops.yml에 items 섹션이 없을 때만 사용되는 폴백입니다.</p>
 * 
 * <h2>경험치 연동</h2>
 * <p>판매 시 {@link MinerExpService#addSaleExp}를 호출하여 경험치를 부여합니다.</p>
 * 
 * @see kr.bapuri.tycoon.shop.job.JobShop#loadFromYaml
 */
public class MinerShop extends JobShop {
    
    // 경험치 서비스 (Phase 4.B 연동)
    private MinerExpService expService;
    
    public MinerShop(Plugin plugin, EconomyService economyService) {
        super(plugin, economyService, "miner", "광부 상점");
    }
    
    /**
     * 경험치 서비스 설정 (Phase 4.B)
     * [Phase 4.E] 부모 클래스의 expService도 함께 설정
     */
    public void setExpService(MinerExpService expService) {
        this.expService = expService;
        super.expService = expService;  // [Phase 4.E] JobShop 공통 연동
        if (expService != null) {
            logger.info("[MinerShop] MinerExpService 연동 완료");
        }
    }
    
    /**
     * 판매 완료 후 경험치 부여 (Phase 4.B)
     * 
     * ShopGuiManager에서 판매 완료 시 호출
     * 
     * @param player 플레이어
     * @param item 판매한 아이템
     * @param amount 판매 수량
     * @return 부여된 경험치 (0이면 미부여)
     */
    public long grantSaleExp(Player player, ItemStack item, int amount) {
        if (expService == null) return 0;
        if (player == null || item == null || amount <= 0) return 0;
        
        // 광부 직업이 있는 경우에만 경험치 부여
        if (!expService.hasJob(player)) return 0;
        
        return expService.addSaleExp(player, item.getType(), amount);
    }
    
    /**
     * 레벨 적용 판매 가격 조회 (Phase 4.B)
     * 
     * 광부 레벨에 따른 보너스 가격 적용
     * 
     * @param item 아이템
     * @param player 플레이어
     * @return 레벨 적용 가격 (직업이 없으면 기본 가격)
     */
    public long getLevelAdjustedSellPrice(ItemStack item, Player player) {
        long basePrice = getSellPrice(item);
        if (basePrice <= 0) return basePrice;
        
        if (expService == null || !expService.hasJob(player)) {
            return basePrice;
        }
        
        // 레벨 보너스 적용
        return expService.getActualPrice(item.getType(), player);
    }
    
    /**
     * [폴백 전용] shops.yml에 items 섹션이 없을 때만 사용
     * 
     * <p>⚠️ 실제 품목/가격은 shops.yml에서 관리하세요!</p>
     */
    @Override
    protected void initItems() {
        // ========== 폴백 기본값 ==========
        // 실제 데이터는 shops.yml에서 관리
        // 이 코드는 설정 파일이 없거나 손상된 경우에만 사용됨
        
        registerItem(Material.COAL, 40, 20, true, true);
        registerItem(Material.COPPER_INGOT, 80, 40, true, true);
        registerItem(Material.IRON_INGOT, 150, 75, true, true);
        registerItem(Material.GOLD_INGOT, 300, 150, true, true);
        registerItem(Material.LAPIS_LAZULI, 80, 40, true, true);
        registerItem(Material.REDSTONE, 64, 32, true, true);
        registerItem(Material.QUARTZ, 90, 45, true, true);
        registerItem(Material.AMETHYST_SHARD, 130, 65, true, true);
        
        // ========== 고급 광물 ==========
        registerItem(Material.DIAMOND, 1000, 500, true, true);
        registerItem(Material.EMERALD, 900, 450, true, true);
        registerItem(Material.NETHERITE_INGOT, 9000, 4500, true, true);
        
        // ========== 광물 블록 (주괴 × 9) ==========
        registerItem(Material.COAL_BLOCK, 360, 180, true, true);
        registerItem(Material.COPPER_BLOCK, 720, 360, true, true);
        registerItem(Material.IRON_BLOCK, 1350, 675, true, true);
        registerItem(Material.GOLD_BLOCK, 2700, 1350, true, true);
        registerItem(Material.LAPIS_BLOCK, 720, 360, true, true);
        registerItem(Material.REDSTONE_BLOCK, 576, 288, true, true);
        registerItem(Material.DIAMOND_BLOCK, 9000, 4500, true, true);
        registerItem(Material.EMERALD_BLOCK, 8100, 4050, true, true);
        registerItem(Material.NETHERITE_BLOCK, 81000, 40500, true, true);
        
        // ========== 산화 구리 (판매만) ==========
        registerItem(Material.EXPOSED_COPPER, -1, 720, false, true);      // 산화 1단계
        registerItem(Material.WEATHERED_COPPER, -1, 1080, false, true);   // 산화 2단계
        registerItem(Material.OXIDIZED_COPPER, -1, 1800, false, true);    // 산화 3단계
        
        // ========== 원석 (판매만, 제련 유도) ==========
        registerItem(Material.RAW_COPPER, -1, 30, false, true);
        registerItem(Material.RAW_IRON, -1, 50, false, true);
        registerItem(Material.RAW_GOLD, -1, 100, false, true);
    }
}
