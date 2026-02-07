package kr.bapuri.tycoonhud.model;

/**
 * CountdownData - 카운트다운 데이터
 * 
 * 게임 시작 전 카운트다운 표시를 위한 데이터 클래스
 */
public class CountdownData {
    
    private int secondsRemaining;  // 남은 초
    private boolean active;        // 카운트다운 활성화 여부
    private long lastUpdate;       // 마지막 업데이트 시각
    
    public CountdownData() {
        this.secondsRemaining = 0;
        this.active = false;
        this.lastUpdate = 0;
    }
    
    // ===== Getters/Setters =====
    
    public int getSecondsRemaining() { return secondsRemaining; }
    public void setSecondsRemaining(int secondsRemaining) { 
        this.secondsRemaining = secondsRemaining; 
        this.lastUpdate = System.currentTimeMillis();
    }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    public long getLastUpdate() { return lastUpdate; }
    
    // ===== 유틸리티 =====
    
    /**
     * 마지막 5초인지 (빨간색 + 큰 효과)
     */
    public boolean isFinalCountdown() {
        return active && secondsRemaining <= 5 && secondsRemaining > 0;
    }
    
    /**
     * 표시해야 하는지
     */
    public boolean shouldDisplay() {
        return active && secondsRemaining > 0;
    }
    
    /**
     * 데이터가 최신인지 (3초 이내)
     */
    public boolean isRecent() {
        return System.currentTimeMillis() - lastUpdate < 3000;
    }
    
    @Override
    public String toString() {
        return "CountdownData{" +
                "secondsRemaining=" + secondsRemaining +
                ", active=" + active +
                '}';
    }
}

