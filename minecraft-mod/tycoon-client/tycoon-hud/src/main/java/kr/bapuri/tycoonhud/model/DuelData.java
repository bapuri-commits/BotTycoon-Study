package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;

/**
 * 듀얼 세션 데이터를 담는 클래스입니다.
 * 
 * <p>서버로부터 DUEL_SESSION 패킷으로 수신됩니다.</p>
 * 
 * <pre>
 * {
 *     "inDuel": true,
 *     "state": "IN_PROGRESS",
 *     "opponentName": "Player2",
 *     "opponentUuid": "550e8400-e29b-41d4-a716-446655440000",
 *     "remainingSeconds": 542,
 *     "totalSeconds": 600,
 *     "myBet": 1000,
 *     "totalBet": 2000,
 *     "type": "RANKED"
 * }
 * </pre>
 */
public class DuelData {
    
    /** 듀얼 중 여부 */
    @SerializedName("inDuel")
    private boolean inDuel;
    
    /** 듀얼 상태: "COUNTDOWN", "IN_PROGRESS", "FINISHED" */
    @SerializedName("state")
    private String state;
    
    /** 상대방 닉네임 */
    @SerializedName("opponentName")
    private String opponentName;
    
    /** 상대방 UUID */
    @SerializedName("opponentUuid")
    private String opponentUuid;
    
    /** 남은 시간 (초) */
    @SerializedName("remainingSeconds")
    private long remainingSeconds;
    
    /** 총 매치 시간 (초) */
    @SerializedName("totalSeconds")
    private long totalSeconds;
    
    /** 본인 베팅금 */
    @SerializedName("myBet")
    private int myBet;
    
    /** 총 베팅금 */
    @SerializedName("totalBet")
    private int totalBet;
    
    /** 듀얼 타입: "RANKED", "UNRANKED" */
    @SerializedName("type")
    private String duelType;
    
    // Getters
    
    public boolean isInDuel() {
        return inDuel;
    }
    
    public String getState() {
        return state;
    }
    
    public String getOpponentName() {
        return opponentName;
    }
    
    public String getOpponentUuid() {
        return opponentUuid;
    }
    
    public long getRemainingSeconds() {
        return remainingSeconds;
    }
    
    public long getTotalSeconds() {
        return totalSeconds;
    }
    
    public int getMyBet() {
        return myBet;
    }
    
    public int getTotalBet() {
        return totalBet;
    }
    
    public String getDuelType() {
        return duelType;
    }
    
    /**
     * 시간 비율을 0.0 ~ 1.0 사이 값으로 반환합니다.
     * 
     * @return 시간 비율 (남은시간/총시간)
     */
    public float getTimeRatio() {
        if (totalSeconds <= 0) return 0f;
        return Math.min(1f, (float) remainingSeconds / totalSeconds);
    }
    
    /**
     * 남은 시간을 "분:초" 형식으로 반환합니다.
     * 
     * @return 예: "9:02"
     */
    public String getFormattedTime() {
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    /**
     * 듀얼 상태에 따른 한글 텍스트를 반환합니다.
     * 
     * @return 상태 텍스트
     */
    public String getLocalizedState() {
        if (state == null) return "";
        return switch (state) {
            case "COUNTDOWN" -> "카운트다운";
            case "IN_PROGRESS" -> "진행 중";
            case "FINISHED" -> "종료됨";
            default -> state;
        };
    }
    
    /**
     * 듀얼 타입에 따른 한글 텍스트를 반환합니다.
     * 
     * @return 타입 텍스트
     */
    public String getLocalizedType() {
        if (duelType == null) return "";
        return switch (duelType) {
            case "RANKED" -> "랭크전";
            case "UNRANKED" -> "일반전";
            default -> duelType;
        };
    }
    
    /**
     * 듀얼이 활성화 상태인지 (표시 필요) 확인합니다.
     * 
     * @return COUNTDOWN 또는 IN_PROGRESS 상태이면 true
     */
    public boolean isActive() {
        return inDuel && ("COUNTDOWN".equals(state) || "IN_PROGRESS".equals(state));
    }
}

