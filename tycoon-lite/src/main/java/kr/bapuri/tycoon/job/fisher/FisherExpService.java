package kr.bapuri.tycoon.job.fisher;

import kr.bapuri.tycoon.job.common.AbstractJobExpService;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * FisherExpService - 어부 경험치 관리 서비스
 * 
 * Phase 4.D:
 * - 낚시 시 경험치 부여 (희귀도 배율 적용)
 * - 판매 시 경험치 부여
 * - 레벨에 따른 보너스 계산
 */
public class FisherExpService extends AbstractJobExpService {
    
    private final FisherConfig config;
    
    public FisherExpService(JavaPlugin plugin, PlayerDataManager dataManager, FisherConfig config) {
        super(plugin, dataManager, JobType.FISHER);
        this.config = config;
    }
    
    /**
     * 낚시 시 경험치 부여
     * 
     * @param player 플레이어
     * @param fishMaterial 낚은 물고기 종류
     * @param rarity 희귀도
     * @param count 수량
     * @return 부여된 경험치
     */
    public long addFishingExp(Player player, Material fishMaterial, FishRarity rarity, int count) {
        if (player == null || count <= 0) return 0;
        if (!hasJob(player)) return 0;
        
        long baseXp = config.getExpReward(fishMaterial);
        if (baseXp <= 0) {
            baseXp = 10L;  // 기본 XP
        }
        
        // 희귀도 배율 적용
        double rarityMultiplier = rarity.getXpMultiplier();
        long totalXp = (long) (baseXp * rarityMultiplier * count);
        
        if (totalXp > 0) {
            addExp(player, totalXp);
            recordFished(player.getUniqueId(), fishMaterial, count);
        }
        
        return totalXp;
    }
    
    /**
     * 판매 시 경험치 부여
     * 
     * @param player 플레이어
     * @param material 판매 아이템
     * @param count 수량
     * @return 부여된 경험치
     */
    public long addSaleExp(Player player, Material material, int count) {
        if (player == null || count <= 0) return 0;
        if (!hasJob(player)) return 0;
        
        // 판매 XP는 기본 XP의 50%
        long baseXp = config.getExpReward(material);
        long saleXp = (long) (baseXp * 0.5 * count);
        
        if (saleXp > 0) {
            addExp(player, saleXp);
        }
        
        return saleXp;
    }
    
    /**
     * 총 낚시량 기록
     * 
     * @param uuid 플레이어 UUID
     * @param fishMaterial 낚은 물고기 종류 (추후 종류별 통계 확장용)
     * @param count 수량
     */
    private void recordFished(UUID uuid, Material fishMaterial, int count) {
        PlayerTycoonData data = dataManager.get(uuid);
        if (data == null) return;
        
        // 총 낚시량 기록 (addTotalFished가 markDirty 호출)
        data.addTotalFished(count);
        
        // TODO [v1.1]: 물고기 종류별 통계 기록
        // data.addCaughtFish(fishMaterial.name(), count);
    }
    
    @Override
    public long grantExpForAction(Player player, String actionId, int count) {
        // actionId: "FISH_COD", "FISH_SALMON", etc.
        if (actionId.startsWith("FISH_")) {
            String matName = actionId.substring(5);
            try {
                Material mat = Material.valueOf(matName);
                // 기본 희귀도(COMMON)로 XP 부여
                return addFishingExp(player, mat, FishRarity.COMMON, count);
            } catch (IllegalArgumentException e) {
                return 0;
            }
        }
        return 0;
    }
    
    @Override
    public void reloadConfig() {
        config.loadFromConfig();
        logger.info("[FisherExpService] 설정 리로드 완료");
    }
    
    /**
     * [Phase 4.E] 상점 판매 XP 통합 인터페이스 구현
     * 
     * 어부 직업 전용: 수산물 판매 시 XP 부여 + 판매액 기록
     */
    @Override
    public long addSaleExpFromShop(Player player, Material material, int count, long saleAmount) {
        if (player == null || count <= 0) return 0;
        if (!hasJob(player)) return 0;
        
        // [Phase 4.E] 직업별 판매액 기록
        if (saleAmount > 0) {
            PlayerTycoonData data = dataManager.get(player.getUniqueId());
            if (data != null) {
                data.addFisherSales(saleAmount);
            }
        }
        
        // 기존 addSaleExp 로직 호출
        return addSaleExp(player, material, count);
    }
    
    public FisherConfig getConfig() {
        return config;
    }
    
    /**
     * 레벨 적용 판매가 계산
     */
    public long getActualPrice(Material material, Player player) {
        long basePrice = config.getBasePrice(material);
        if (basePrice <= 0) return 0;
        
        int level = getLevel(player);
        double bonus = 1.0 + (level - 1) * (config.getLevelBonusPercent() / 100.0);
        
        return (long) (basePrice * bonus);
    }
}
