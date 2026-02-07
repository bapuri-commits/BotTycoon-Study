package kr.bapuri.tycoon.job.common;

import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ItemRequirement - 아이템 구매/판매 조건
 * 
 * [STUB] 현재는 미구현, 향후 확장 시 활용
 * 
 * QnA 결정: 현재는 해당 아이템 없음 (고급 장비 판매 계획 없음)
 *          단, 조건을 걸 수 있는 코드/인터페이스는 준비
 * 
 * 예시:
 * - NETHERITE_PICKAXE: 광부 직업 필요
 * - ENCHANTED_GOLDEN_APPLE: 셰프 직업 필요
 */
public class ItemRequirement {
    
    private final PlayerDataManager dataManager;
    
    // 직업 필수 아이템 목록 (현재는 비어있음)
    private final Set<String> jobRequiredItems = new HashSet<>();
    
    public ItemRequirement(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }
    
    /**
     * 아이템 구매 가능 여부 확인
     * 
     * @param player 구매자
     * @param itemId 아이템 ID (Material name 또는 커스텀 ID)
     * @param requiredJob 필요 직업 (null이면 제한 없음)
     * @return true if 구매 가능
     */
    public boolean canBuy(Player player, String itemId, JobType requiredJob) {
        // [STUB] 현재는 모든 구매 허용
        if (requiredJob == null) return true;
        if (player == null) return false;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return false;
        
        return data.hasJob(requiredJob);
    }
    
    /**
     * 아이템 판매 가능 여부 확인
     * 
     * @param player 판매자
     * @param itemId 아이템 ID
     * @param requiredJob 필요 직업 (null이면 제한 없음)
     * @return true if 판매 가능
     */
    public boolean canSell(Player player, String itemId, JobType requiredJob) {
        // [STUB] 현재는 모든 판매 허용
        // 가격 배율로 통제 (PricingPolicy)
        return true;
    }
    
    /**
     * 아이템이 직업 필수인지 확인
     * 
     * @param itemId 아이템 ID
     * @return true if 직업 필수 아이템
     */
    public boolean isJobRequired(String itemId) {
        return jobRequiredItems.contains(itemId);
    }
    
    /**
     * 직업 필수 아이템 목록 반환
     */
    public Set<String> getJobRequiredItems() {
        return Collections.unmodifiableSet(jobRequiredItems);
    }
    
    /**
     * 직업 필수 아이템 추가 (관리자용)
     * 
     * @param itemId 아이템 ID
     */
    public void addJobRequiredItem(String itemId) {
        if (itemId != null && !itemId.isEmpty()) {
            jobRequiredItems.add(itemId.toUpperCase());
        }
    }
    
    /**
     * 직업 필수 아이템 제거 (관리자용)
     * 
     * @param itemId 아이템 ID
     */
    public void removeJobRequiredItem(String itemId) {
        if (itemId != null) {
            jobRequiredItems.remove(itemId.toUpperCase());
        }
    }
    
    /**
     * 구매 불가 사유 반환
     * 
     * @param player 구매자
     * @param itemId 아이템 ID
     * @param requiredJob 필요 직업
     * @return 불가 사유 (구매 가능하면 null)
     */
    public String getBuyFailureReason(Player player, String itemId, JobType requiredJob) {
        if (canBuy(player, itemId, requiredJob)) return null;
        
        if (requiredJob != null) {
            return String.format("§c이 아이템은 %s 직업이 필요합니다.", requiredJob.getDisplayName());
        }
        
        return "§c이 아이템을 구매할 수 없습니다.";
    }
}
