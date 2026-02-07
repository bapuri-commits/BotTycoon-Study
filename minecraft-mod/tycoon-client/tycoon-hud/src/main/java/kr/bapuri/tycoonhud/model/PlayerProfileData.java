package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;

/**
 * 플레이어 프로필 데이터를 담는 클래스입니다.
 * 
 * <p>서버로부터 PLAYER_PROFILE 패킷으로 수신됩니다.</p>
 * 
 * <pre>
 * {
 *     "schema": 1,
 *     "uuid": "UUID",
 *     "name": "이름",
 *     "bd": 50000,
 *     "bottcoin": 25,
 *     "title": "칭호",
 *     "playtimeSeconds": 3600,
 *     "primaryJob": { ... },
 *     "secondaryJob": null,
 *     "currentWorld": "town",
 *     "plotInfo": { ... }
 * }
 * </pre>
 */
public class PlayerProfileData {
    
    /** 스키마 버전 (호환성 체크용) */
    @SerializedName("schema")
    private int schema;
    
    /** 플레이어 UUID */
    @SerializedName("uuid")
    private String uuid;
    
    /** 플레이어 이름 */
    @SerializedName("name")
    private String name;
    
    /** BD (기본 화폐) */
    @SerializedName("bd")
    private long bd;
    
    /** BottCoin (프리미엄 화폐) */
    @SerializedName("bottcoin")
    private int bottcoin;
    
    /** 칭호 (없으면 null) */
    @SerializedName("title")
    private String title;
    
    /** 총 플레이타임 (초) */
    @SerializedName("playtimeSeconds")
    private long playtimeSeconds;
    
    /** 주 직업 */
    @SerializedName("primaryJob")
    private JobData primaryJob;
    
    /** 부 직업 (없으면 null) */
    @SerializedName("secondaryJob")
    private JobData secondaryJob;
    
    /** 현재 월드 (town, wild, hunter, duel, dungeon) */
    @SerializedName("currentWorld")
    private String currentWorld;
    
    /** 플롯 정보 (town 월드에서만 유효) */
    @SerializedName("plotInfo")
    private PlotInfo plotInfo;
    
    /** 업적 데이터 (v2) */
    @SerializedName("achievements")
    private AchievementData achievements;
    
    // Getters
    
    public int getSchema() {
        return schema;
    }
    
    public String getUuid() {
        return uuid;
    }
    
    public String getName() {
        return name;
    }
    
    public long getBd() {
        return bd;
    }
    
    public int getBottcoin() {
        return bottcoin;
    }
    
    public String getTitle() {
        return title;
    }
    
    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }
    
    public JobData getPrimaryJob() {
        return primaryJob;
    }
    
    public JobData getSecondaryJob() {
        return secondaryJob;
    }
    
    public String getCurrentWorld() {
        return currentWorld;
    }
    
    public PlotInfo getPlotInfo() {
        return plotInfo;
    }
    
    public AchievementData getAchievements() {
        return achievements;
    }
    
    // ===== Setters for real-time updates =====
    
    /**
     * BD를 업데이트합니다 (ECONOMY_UPDATE용).
     */
    public void setBd(long bd) {
        this.bd = bd;
    }
    
    /**
     * BottCoin을 업데이트합니다 (ECONOMY_UPDATE용).
     */
    public void setBottcoin(int bottcoin) {
        this.bottcoin = bottcoin;
    }
    
    /**
     * 주 직업을 업데이트합니다 (JOB_DATA용).
     */
    public void setPrimaryJob(JobData job) {
        this.primaryJob = job;
    }
    
    /**
     * 부 직업을 업데이트합니다 (JOB_DATA용).
     */
    public void setSecondaryJob(JobData job) {
        this.secondaryJob = job;
    }
    
    /**
     * [2026-01-24] 현재 월드를 업데이트합니다 (PLOT_UPDATE용).
     */
    public void setCurrentWorld(String world) {
        this.currentWorld = world;
    }
    
    /**
     * [2026-01-24] 플롯 정보를 업데이트합니다 (PLOT_UPDATE용).
     */
    public void setPlotInfo(PlotInfo plotInfo) {
        this.plotInfo = plotInfo;
    }
    
    /**
     * 표시할 이름을 반환합니다 (칭호 포함).
     * 
     * @return "[칭호] 이름" 형식 또는 칭호가 없으면 이름만
     */
    public String getDisplayName() {
        if (title != null && !title.isEmpty()) {
            return "[" + title + "] " + name;
        }
        return name;
    }
    
    /**
     * 현재 월드를 한글로 변환합니다.
     * 
     * @return 한글 월드명 (예: "마을")
     */
    public String getLocalizedWorld() {
        if (currentWorld == null) return "알 수 없음";
        
        return switch (currentWorld.toLowerCase()) {
            case "town" -> "마을";
            case "wild" -> "야생";
            case "hunter" -> "사냥터";
            case "duel" -> "듀얼";
            case "dungeon" -> "던전";
            default -> currentWorld;
        };
    }
    
    /**
     * 플레이타임을 "시간:분" 형식으로 반환합니다.
     * 
     * @return 포맷된 플레이타임
     */
    public String getFormattedPlaytime() {
        long hours = playtimeSeconds / 3600;
        long minutes = (playtimeSeconds % 3600) / 60;
        return String.format("%d시간 %d분", hours, minutes);
    }
    
    /**
     * 현재 월드가 town인지 확인합니다.
     * 
     * @return town 월드이면 true
     */
    public boolean isInTown() {
        return "town".equalsIgnoreCase(currentWorld);
    }
    
    /**
     * 현재 월드가 hunter인지 확인합니다. (v2.2)
     * 
     * @return hunter 월드이면 true
     */
    public boolean isInHunter() {
        return "hunter".equalsIgnoreCase(currentWorld);
    }
}

