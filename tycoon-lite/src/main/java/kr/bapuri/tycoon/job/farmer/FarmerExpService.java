package kr.bapuri.tycoon.job.farmer;

import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.common.AbstractJobExpService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * FarmerExpService - 농부 경험치/레벨 서비스
 * 
 * Phase 4.C 구현:
 * - AbstractJobExpService 상속
 * - 수확/판매 경험치 부여
 * - FarmerConfig 기반 XP 보상
 */
public class FarmerExpService extends AbstractJobExpService {
    
    private final FarmerConfig config;
    
    public FarmerExpService(JavaPlugin plugin, PlayerDataManager dataManager, FarmerConfig config) {
        super(plugin, dataManager, JobType.FARMER);
        this.config = config;
    }
    
    /**
     * 수확 시 경험치 부여
     * 
     * @param player 플레이어
     * @param cropMaterial 수확한 작물 Material
     * @param count 수량
     * @return 획득한 경험치
     */
    public long addHarvestExp(Player player, Material cropMaterial, int count) {
        if (player == null || count <= 0) return 0;
        if (!hasJob(player)) return 0;
        
        long xpPerCrop = config.getExpReward(cropMaterial);
        if (xpPerCrop <= 0) return 0;
        
        long totalXp = xpPerCrop * count;
        addExp(player, totalXp);
        
        // 수확량 기록
        recordHarvested(player.getUniqueId(), cropMaterial, count);
        
        return totalXp;
    }
    
    /**
     * 판매 시 경험치 부여
     * 
     * @param player 플레이어
     * @param material 판매한 Material
     * @param count 수량
     * @return 획득한 경험치
     */
    public long addSaleExp(Player player, Material material, int count) {
        if (player == null || count <= 0) return 0;
        if (!hasJob(player)) return 0;
        
        // 판매 경험치는 수확의 50%
        long xpPerItem = config.getExpReward(material) / 2;
        if (xpPerItem <= 0) {
            // 작물이 아닌 경우 기준 가격 기반 XP 계산
            long basePrice = config.getBasePrice(material);
            xpPerItem = basePrice / 10;  // 10 BD당 1 XP
        }
        
        if (xpPerItem <= 0) return 0;
        
        long totalXp = xpPerItem * count;
        addExp(player, totalXp);
        
        return totalXp;
    }
    
    /**
     * 수확량 기록
     */
    private void recordHarvested(UUID uuid, Material material, int count) {
        PlayerTycoonData data = getDataSafe(uuid);
        if (data == null) return;
        
        // 총 수확량 기록 (addTotalHarvested가 markDirty 호출)
        data.addTotalHarvested(count);
    }
    
    // ===== AbstractJobExpService 구현 =====
    
    @Override
    public long grantExpForAction(Player player, String actionId, int count) {
        if (player == null || actionId == null || count <= 0) return 0;
        
        // actionId가 Material 이름인 경우 처리
        try {
            Material material = Material.valueOf(actionId.toUpperCase());
            
            // 작물인 경우 수확 XP
            if (config.isCrop(material)) {
                return addHarvestExp(player, material, count);
            }
            
            // 작물이 아닌 경우 판매 XP
            return addSaleExp(player, material, count);
            
        } catch (IllegalArgumentException e) {
            // 유효하지 않은 Material
            logger.warning("[FarmerExpService] 알 수 없는 actionId: " + actionId);
            return 0;
        }
    }
    
    @Override
    public void reloadConfig() {
        config.loadFromConfig();
        logger.info("[FarmerExpService] 설정 리로드 완료");
    }
    
    /**
     * [Phase 4.E] 상점 판매 XP 통합 인터페이스 구현
     * 
     * 농부 직업 전용: 작물 판매 시 XP 부여 + 판매액 기록
     */
    @Override
    public long addSaleExpFromShop(Player player, Material material, int count, long saleAmount) {
        if (player == null || count <= 0) return 0;
        if (!hasJob(player)) return 0;
        
        // [Phase 4.E] 직업별 판매액 기록
        if (saleAmount > 0) {
            PlayerTycoonData data = getDataSafe(player.getUniqueId());
            if (data != null) {
                data.addFarmerSales(saleAmount);
            }
        }
        
        // 기존 addSaleExp 로직 호출
        return addSaleExp(player, material, count);
    }
    
    // ===== 유틸리티 =====
    
    /**
     * FarmerConfig 접근
     */
    public FarmerConfig getConfig() {
        return config;
    }
    
    /**
     * 레벨 적용 가격 조회 (SellService 연동용)
     */
    public long getActualPrice(Material material, Player player) {
        int level = getLevel(player);
        return config.getActualPrice(material, level);
    }
}
