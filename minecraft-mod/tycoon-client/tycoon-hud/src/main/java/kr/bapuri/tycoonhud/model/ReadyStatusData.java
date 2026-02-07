package kr.bapuri.tycoonhud.model;

/**
 * ReadyStatusData - 레디 상태 데이터
 * 
 * 로비에서 레디 상태를 표시하기 위한 데이터 클래스
 */
public class ReadyStatusData {
    
    private boolean isReady;       // 본인 레디 여부
    private int readyCount;        // 레디한 플레이어 수
    private int totalInLobby;      // 로비 총 인원
    private String state;          // 게임 상태 (WAITING, COUNTDOWN, IN_GAME, ENDING)
    
    public ReadyStatusData() {
        this.isReady = false;
        this.readyCount = 0;
        this.totalInLobby = 0;
        this.state = "WAITING";
    }
    
    // ===== Getters/Setters =====
    
    public boolean isReady() { return isReady; }
    public void setReady(boolean ready) { isReady = ready; }
    
    public int getReadyCount() { return readyCount; }
    public void setReadyCount(int readyCount) { this.readyCount = readyCount; }
    
    public int getTotalInLobby() { return totalInLobby; }
    public void setTotalInLobby(int totalInLobby) { this.totalInLobby = totalInLobby; }
    
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    
    // ===== 유틸리티 =====
    
    /**
     * 로비 상태인지 (WAITING 또는 COUNTDOWN)
     */
    public boolean isLobbyState() {
        return "WAITING".equals(state) || "COUNTDOWN".equals(state);
    }
    
    /**
     * 카운트다운 중인지
     */
    public boolean isCountdownState() {
        return "COUNTDOWN".equals(state);
    }
    
    /**
     * 레디 텍스트 (예: "2/3")
     */
    public String getReadyText() {
        return readyCount + "/" + totalInLobby;
    }
    
    /**
     * 데이터 유효성
     */
    public boolean isValid() {
        return totalInLobby > 0;
    }
    
    @Override
    public String toString() {
        return "ReadyStatusData{" +
                "isReady=" + isReady +
                ", readyCount=" + readyCount +
                ", totalInLobby=" + totalInLobby +
                ", state='" + state + '\'' +
                '}';
    }
}

