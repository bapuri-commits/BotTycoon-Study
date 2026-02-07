package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * 미니맵 데이터 모델
 * 서버에서 전송하는 헌터 월드 미니맵 정보
 */
public class MinimapData {

    /**
     * 본인 위치
     */
    @SerializedName("playerX")
    private double playerX;

    @SerializedName("playerZ")
    private double playerZ;

    /**
     * 자기장 정보
     */
    @SerializedName("blueZoneCenterX")
    private double blueZoneCenterX;

    @SerializedName("blueZoneCenterZ")
    private double blueZoneCenterZ;

    @SerializedName("blueZoneRadius")
    private double blueZoneRadius;

    /**
     * 주변 플레이어 목록
     */
    @SerializedName("players")
    private List<PlayerMarker> players = new ArrayList<>();

    /**
     * 팀원 UUID 목록
     */
    @SerializedName("teamMemberUuids")
    private List<String> teamMemberUuids = new ArrayList<>();

    /**
     * 에어드랍 위치 목록
     */
    @SerializedName("airdrops")
    private List<AirdropMarker> airdrops = new ArrayList<>();

    // ================================================================================
    // Getters
    // ================================================================================

    public double getPlayerX() { return playerX; }
    public double getPlayerZ() { return playerZ; }
    public double getBlueZoneCenterX() { return blueZoneCenterX; }
    public double getBlueZoneCenterZ() { return blueZoneCenterZ; }
    public double getBlueZoneRadius() { return blueZoneRadius; }
    public List<PlayerMarker> getPlayers() { return players != null ? players : new ArrayList<>(); }
    public List<String> getTeamMemberUuids() { return teamMemberUuids != null ? teamMemberUuids : new ArrayList<>(); }
    public List<AirdropMarker> getAirdrops() { return airdrops != null ? airdrops : new ArrayList<>(); }

    /**
     * 플레이어 마커
     */
    public static class PlayerMarker {
        @SerializedName("uuid")
        private String uuid;

        @SerializedName("name")
        private String name;

        @SerializedName("x")
        private double x;

        @SerializedName("z")
        private double z;

        @SerializedName("isTeammate")
        private boolean isTeammate;

        public String getUuid() { return uuid; }
        public String getName() { return name; }
        public double getX() { return x; }
        public double getZ() { return z; }
        public boolean isTeammate() { return isTeammate; }
    }

    /**
     * 에어드랍 마커
     */
    public static class AirdropMarker {
        @SerializedName("x")
        private double x;

        @SerializedName("z")
        private double z;

        @SerializedName("looted")
        private boolean looted;

        public double getX() { return x; }
        public double getZ() { return z; }
        public boolean isLooted() { return looted; }
    }
}

