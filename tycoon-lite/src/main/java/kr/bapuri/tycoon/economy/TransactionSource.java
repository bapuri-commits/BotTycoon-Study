package kr.bapuri.tycoon.economy;

/**
 * TransactionSource - 경제 트랜잭션의 출처
 * 
 * 용도:
 * - ADMIN 트랜잭션은 경제 메트릭에서 제외
 * - ADMIN 트랜잭션은 별도 로깅
 * - 슈퍼관리자의 무한 돈 기능과 분리
 */
public enum TransactionSource {
    
    /**
     * 플레이어 행동으로 발생한 트랜잭션
     * 예: 상점 구매/판매, 직업 보상, 거래
     * → 경제 메트릭에 포함
     */
    PLAYER("player", true),
    
    /**
     * 시스템(플러그인)이 자동으로 발생시킨 트랜잭션
     * 예: 던전 보상, 퀘스트 보상, 스케줄된 지급
     * → 경제 메트릭에 포함
     */
    SYSTEM("system", true),
    
    /**
     * 관리자 명령어로 발생한 트랜잭션
     * 예: /eco give, /eco take, 관리자 수표 발행
     * → 경제 메트릭에서 제외
     * → 별도 로깅
     */
    ADMIN("admin", false);
    
    private final String key;
    private final boolean includeInMetrics;
    
    TransactionSource(String key, boolean includeInMetrics) {
        this.key = key;
        this.includeInMetrics = includeInMetrics;
    }
    
    public String getKey() {
        return key;
    }
    
    /**
     * 이 소스의 트랜잭션이 경제 메트릭에 포함되는지 여부
     */
    public boolean isIncludeInMetrics() {
        return includeInMetrics;
    }
}
