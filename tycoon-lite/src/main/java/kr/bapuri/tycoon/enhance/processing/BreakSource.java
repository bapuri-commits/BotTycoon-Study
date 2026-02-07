package kr.bapuri.tycoon.enhance.processing;

/**
 * BreakSource - 블록 파괴 원인 정의
 * 
 * 어떤 시스템에서 블록 파괴를 요청했는지 추적
 */
public enum BreakSource {
    
    /**
     * 일반 블록 파괴 (플레이어 직접 파괴)
     */
    NORMAL("일반"),
    
    /**
     * 광맥 채굴 인챈트 (VEIN_MINER)
     * 연결된 광석 동시 채굴
     */
    VEIN_MINER("광맥 채굴"),
    
    /**
     * 광역 채굴 램프 (MULTI_MINE, MULTI_MINE_2)
     * 주변 블록 동시 채굴
     */
    MULTI_MINE("광역 채굴"),
    
    /**
     * 벌목꾼 램프 (TREE_FELLER_1/2/3)
     * 위쪽 원목 동시 채굴
     */
    TREE_FELLER("벌목꾼"),
    
    /**
     * 수확 인챈트 (HARVEST)
     * 작물 자동 재파종
     */
    HARVEST("수확");
    
    private final String displayName;
    
    BreakSource(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
