package kr.bapuri.tycoon.job.common.unlock;

import kr.bapuri.tycoon.job.common.UnlockCondition;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;

/**
 * CodexCountCondition - 도감 등록 수 조건
 * 
 * 플레이어가 특정 개수 이상의 도감을 등록했는지 확인합니다.
 */
public class CodexCountCondition implements UnlockCondition {
    
    private final int requiredCount;
    private final PlayerDataManager dataManager;
    
    public CodexCountCondition(int requiredCount, PlayerDataManager dataManager) {
        this.requiredCount = Math.max(0, requiredCount);
        this.dataManager = dataManager;
    }
    
    @Override
    public boolean check(Player player) {
        if (player == null) return false;
        if (requiredCount <= 0) return true;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return false;
        
        return data.getCodexCount() >= requiredCount;
    }
    
    @Override
    public String getFailureMessage(Player player) {
        int current = 0;
        if (player != null) {
            PlayerTycoonData data = dataManager.get(player.getUniqueId());
            if (data != null) {
                current = data.getCodexCount();
            }
        }
        
        return String.format("§c도감 등록이 부족합니다. §7(%d/%d)", current, requiredCount);
    }
    
    @Override
    public String getDescription() {
        return String.format("도감 %d개 등록", requiredCount);
    }
    
    @Override
    public String getId() {
        return "codexCount";
    }
    
    @Override
    public long getRequiredValue() {
        return requiredCount;
    }
    
    @Override
    public long getCurrentValue(Player player) {
        if (player == null) return 0;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return 0;
        
        return data.getCodexCount();
    }
}
