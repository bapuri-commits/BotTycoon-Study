package kr.bapuri.tycoon.job.common;

import kr.bapuri.tycoon.job.JobType;
import org.bukkit.entity.Player;

/**
 * SellPolicy - 판매 정책 인터페이스
 * 
 * QnA 결정: A2) SellService로 분리
 * - Shop ↔ Job 결합도를 낮춤
 * - 직업 상태에 따른 가격/경험치 정책 통제
 * 
 * Shop에서 SellService를 통해 판매를 처리하며,
 * SellService는 이 인터페이스의 구현체를 사용합니다.
 */
public interface SellPolicy {
    
    /**
     * 판매 가격 계산
     * 
     * @param player 판매자
     * @param itemId 아이템 ID
     * @param amount 수량
     * @param basePrice 기준가
     * @return 최종 판매가 (BD)
     */
    long calculatePrice(Player player, String itemId, int amount, long basePrice);
    
    /**
     * 획득 경험치 계산
     * 
     * @param player 판매자
     * @param itemId 아이템 ID
     * @param amount 수량
     * @param baseExp 기준 경험치
     * @return 획득할 경험치
     */
    long calculateExp(Player player, String itemId, int amount, long baseExp);
    
    /**
     * 판매 가능 여부 확인
     * 
     * @param player 판매자
     * @param itemId 아이템 ID
     * @return true if 판매 가능
     */
    boolean canSell(Player player, String itemId);
    
    /**
     * 판매 불가 사유 반환
     * 
     * @param player 판매자
     * @param itemId 아이템 ID
     * @return 판매 불가 사유 (판매 가능하면 null)
     */
    String getCannotSellReason(Player player, String itemId);
    
    /**
     * 해당 직업의 아이템인지 확인
     * 
     * @param itemId 아이템 ID
     * @return 담당 직업 (null이면 일반 아이템)
     */
    JobType getJobForItem(String itemId);
    
    /**
     * 경험치 지급 활성화 여부
     * 
     * @return true if 경험치 지급 활성화
     */
    boolean isExpEnabled();
}
