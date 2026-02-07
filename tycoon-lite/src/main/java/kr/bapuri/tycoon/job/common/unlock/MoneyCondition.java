package kr.bapuri.tycoon.job.common.unlock;

import kr.bapuri.tycoon.job.common.UnlockCondition;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;

/**
 * MoneyCondition - BD 보유 조건
 * 
 * 플레이어가 특정 금액 이상의 BD를 보유하고 있는지 확인합니다.
 * 
 * 주의: 이 조건은 "보유 확인"만 하며, 실제 차감은 JobService에서 처리합니다.
 */
public class MoneyCondition implements UnlockCondition {
    
    private final long requiredMoney;
    private final PlayerDataManager dataManager;
    
    public MoneyCondition(long requiredMoney, PlayerDataManager dataManager) {
        this.requiredMoney = Math.max(0, requiredMoney);
        this.dataManager = dataManager;
    }
    
    @Override
    public boolean check(Player player) {
        if (player == null) return false;
        if (requiredMoney <= 0) return true;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return false;
        
        return data.getMoney() >= requiredMoney;
    }
    
    @Override
    public String getFailureMessage(Player player) {
        long current = 0;
        if (player != null) {
            PlayerTycoonData data = dataManager.get(player.getUniqueId());
            if (data != null) {
                current = data.getMoney();
            }
        }
        
        return String.format("§cBD가 부족합니다. §7(%,d/%,d BD)", current, requiredMoney);
    }
    
    @Override
    public String getDescription() {
        return String.format("%,d BD 보유", requiredMoney);
    }
    
    @Override
    public String getId() {
        return "money";
    }
    
    @Override
    public long getRequiredValue() {
        return requiredMoney;
    }
    
    @Override
    public long getCurrentValue(Player player) {
        if (player == null) return 0;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return 0;
        
        return data.getMoney();
    }
}
