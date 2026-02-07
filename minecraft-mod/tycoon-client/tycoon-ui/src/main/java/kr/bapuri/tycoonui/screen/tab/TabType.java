package kr.bapuri.tycoonui.screen.tab;

/**
 * 통합 GUI의 탭 타입을 정의하는 열거형입니다.
 */
public enum TabType {
    
    /** 경제 탭 - BD/BC, 거래 내역 */
    ECONOMY("경제", 0),
    
    /** 프로필 탭 - 스킨, 정보 */
    PROFILE("프로필", 1),
    
    /** 직업 탭 - 직업 정보, 승급 */
    JOB("직업", 2),
    
    /** 도감 탭 - 카테고리별 수집 현황 */
    CODEX("도감", 3);
    
    /** 탭 표시 이름 */
    private final String displayName;
    
    /** 탭 인덱스 */
    private final int index;
    
    TabType(String displayName, int index) {
        this.displayName = displayName;
        this.index = index;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getIndex() {
        return index;
    }
    
    /**
     * 인덱스로 TabType을 찾습니다.
     * 
     * @param index 탭 인덱스
     * @return 해당 TabType 또는 ECONOMY (기본값)
     */
    public static TabType fromIndex(int index) {
        for (TabType tab : values()) {
            if (tab.index == index) {
                return tab;
            }
        }
        return ECONOMY;
    }
}

