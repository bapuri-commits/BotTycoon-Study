package kr.bapuri.tycoon.job.common;

import org.bukkit.entity.Player;

/**
 * UnlockCondition - 직업 해금 조건 인터페이스
 * 
 * QnA 결정: C) 외부 단순 + 내부 일반화
 * - YAML 설정은 단순 유지 (codexCount, money)
 * - 내부적으로 인터페이스로 일반화하여 확장 가능
 * 
 * 구현체:
 * - CodexCountCondition: 도감 등록 수 조건
 * - MoneyCondition: BD 보유 조건
 * - Tier2UnlockCondition: Tier 1 직업 보유 조건 (stub)
 */
public interface UnlockCondition {
    
    /**
     * 조건 충족 여부 확인
     * 
     * @param player 대상 플레이어
     * @return true if 조건 충족
     */
    boolean check(Player player);
    
    /**
     * 조건 미충족 시 표시할 메시지
     * 
     * @param player 대상 플레이어
     * @return 실패 사유 메시지
     */
    String getFailureMessage(Player player);
    
    /**
     * 조건 설명 (GUI 표시용)
     * 
     * @return 조건 설명 문자열
     */
    String getDescription();
    
    /**
     * 조건 ID (설정 키)
     * 
     * @return 조건 식별자 (예: "codexCount", "money")
     */
    String getId();
    
    /**
     * 필요 값 반환 (숫자 조건용)
     * 
     * @return 필요 값 (해당 없으면 0)
     */
    default long getRequiredValue() {
        return 0;
    }
    
    /**
     * 현재 값 반환 (진행률 표시용)
     * 
     * @param player 대상 플레이어
     * @return 현재 값 (해당 없으면 0)
     */
    default long getCurrentValue(Player player) {
        return 0;
    }
    
    /**
     * 진행률 반환 (0.0 ~ 1.0)
     * 
     * @param player 대상 플레이어
     * @return 진행률
     */
    default double getProgress(Player player) {
        long required = getRequiredValue();
        if (required <= 0) return check(player) ? 1.0 : 0.0;
        
        long current = getCurrentValue(player);
        return Math.min(1.0, (double) current / required);
    }
}
