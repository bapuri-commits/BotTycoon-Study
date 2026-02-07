package kr.bapuri.tycoon.common;

/**
 * PenaltyReason - 패널티 이유 코드 (확장점)
 * 
 * 벌금, 채무, 아이템 손실 등의 패널티를 부과할 때 사용되는 이유 코드.
 * 로깅, 통계, 이의제기 시스템에서 활용 가능.
 */
public enum PenaltyReason {
    
    // ===== 사망 관련 =====
    WILD_DEATH_NORMAL("야생 일반 사망"),
    WILD_DEATH_VOID("야생 보이드 사망"),
    WILD_DEATH_PVP("야생 PvP 사망"),
    
    // ===== 데스 체스트 관련 =====
    DEATHCHEST_EXPIRED("데스 체스트 만료"),
    DEATHCHEST_CLAIM_FEE("데스 체스트 회수 수수료"),
    
    // ===== 강화/장비 관련 =====
    UPGRADE_DESTROY("강화 파괴"),
    
    // ===== 기타 =====
    ADMIN_PENALTY("관리자 패널티"),
    UNKNOWN("알 수 없음");
    
    // [DROP] DUNGEON_DEATH, COMBAT_LOGOUT_HUNTER, COMBAT_LOGOUT_DUEL 제거됨 (Phase 1.5)

    private final String displayName;

    PenaltyReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 무료 회수 대상인지 (PvP 사망)
     */
    public boolean isFreeClaim() {
        return this == WILD_DEATH_PVP;
    }

    /**
     * 채무 생성 가능한 패널티인지
     * [DROP] 헌터/듀얼 전투 로그아웃 제거됨 - 현재 채무 생성 케이스 없음
     */
    public boolean canCreateDebt() {
        return false;
    }
}

