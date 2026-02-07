package kr.bapuri.tycoon.job.common.unlock;

import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.common.UnlockCondition;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Tier2UnlockCondition - Tier 2 직업 해금 조건 (Tier 1 보유 필수)
 * 
 * [STUB] Lite에서는 Tier 2 직업이 비활성화되어 있으므로 기반만 구현
 * 
 * Tier 2 직업 해금 조건:
 * - 기본: Tier 1 직업 중 하나 이상 보유
 * - 특정 직업 요구 시: 해당 Tier 1 직업 보유
 * 
 * 예: Chef → Farmer 또는 Fisher 필요
 *     Artisan → Miner 필요
 */
public class Tier2UnlockCondition implements UnlockCondition {
    
    private final Set<JobType> requiredTier1Jobs;
    private final PlayerDataManager dataManager;
    private final boolean anyTier1Allowed;
    
    /**
     * 특정 Tier 1 직업 요구
     * 
     * @param requiredTier1Jobs 필요한 Tier 1 직업 목록 (하나라도 있으면 OK)
     * @param dataManager 플레이어 데이터 매니저
     */
    public Tier2UnlockCondition(Set<JobType> requiredTier1Jobs, PlayerDataManager dataManager) {
        this.requiredTier1Jobs = requiredTier1Jobs;
        this.dataManager = dataManager;
        this.anyTier1Allowed = requiredTier1Jobs == null || requiredTier1Jobs.isEmpty();
    }
    
    /**
     * 아무 Tier 1 직업이든 OK
     * 
     * @param dataManager 플레이어 데이터 매니저
     */
    public Tier2UnlockCondition(PlayerDataManager dataManager) {
        this.requiredTier1Jobs = Set.of();
        this.dataManager = dataManager;
        this.anyTier1Allowed = true;
    }
    
    @Override
    public boolean check(Player player) {
        if (player == null) return false;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return false;
        
        // Tier 1 직업 보유 여부 확인
        if (!data.hasTier1Job()) return false;
        
        // 아무 Tier 1이든 OK
        if (anyTier1Allowed) return true;
        
        // 특정 Tier 1 직업 요구
        JobType tier1Job = data.getTier1Job();
        return requiredTier1Jobs.contains(tier1Job);
    }
    
    @Override
    public String getFailureMessage(Player player) {
        if (anyTier1Allowed) {
            return "§cTier 1 직업이 필요합니다. §7(농부/광부/어부 중 하나)";
        }
        
        StringBuilder sb = new StringBuilder("§cTier 1 직업이 필요합니다: §7");
        boolean first = true;
        for (JobType job : requiredTier1Jobs) {
            if (!first) sb.append(", ");
            sb.append(job.getDisplayName());
            first = false;
        }
        return sb.toString();
    }
    
    @Override
    public String getDescription() {
        if (anyTier1Allowed) {
            return "Tier 1 직업 보유";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (JobType job : requiredTier1Jobs) {
            if (!first) sb.append("/");
            sb.append(job.getDisplayName());
            first = false;
        }
        sb.append(" 직업 보유");
        return sb.toString();
    }
    
    @Override
    public String getId() {
        return "tier1Job";
    }
    
    @Override
    public long getRequiredValue() {
        return 1; // 1개 이상
    }
    
    @Override
    public long getCurrentValue(Player player) {
        if (player == null) return 0;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return 0;
        
        return data.hasTier1Job() ? 1 : 0;
    }
}
