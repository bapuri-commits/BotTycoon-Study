package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 헌터 맵 데이터 - 서버에서 전송받는 게임 지역 맵
 * 
 * <h3>패킷 종류</h3>
 * <ul>
 *     <li>HUNTER_MAP_FULL - 전체 맵 데이터 (게임 시작 시 1회)</li>
 *     <li>HUNTER_MAP_UPDATE - 블록 변경 업데이트 (0.5초마다)</li>
 * </ul>
 * 
 * @since v2.5 Hunter Map System
 */
public class HunterMapData {

    /** 게임 지역 ID (예: "area_1500_2000") */
    @SerializedName("areaId")
    private String areaId;
    
    /** 맵 중심 X 좌표 */
    @SerializedName("centerX")
    private int centerX;
    
    /** 맵 중심 Z 좌표 */
    @SerializedName("centerZ")
    private int centerZ;
    
    /** 맵 반경 (블록) */
    @SerializedName("radius")
    private int radius;
    
    /** 맵 해상도 (픽셀) */
    @SerializedName("resolution")
    private int resolution;
    
    /** 맵 데이터 (Base64 인코딩된 ARGB 배열 또는 PNG) */
    @SerializedName("mapData")
    private String mapData;
    
    /** 데이터 포맷 ("RAW" = ARGB 배열, "PNG" = PNG 이미지) */
    @SerializedName("format")
    private String format;
    
    /** 맵 업데이트 목록 (HUNTER_MAP_UPDATE 용) */
    @SerializedName("updates")
    private List<MapUpdate> updates;
    
    /** 패킷 타입 */
    @SerializedName("packetType")
    private String packetType;
    
    // ================================================================================
    // 업데이트 클래스
    // ================================================================================
    
    /**
     * 개별 블록 업데이트
     */
    public static class MapUpdate {
        @SerializedName("x")
        private int x;
        
        @SerializedName("z")
        private int z;
        
        @SerializedName("color")
        private int color;
        
        public int getX() { return x; }
        public int getZ() { return z; }
        public int getColor() { return color; }
    }
    
    // ================================================================================
    // Getters & Setters
    // ================================================================================
    
    public String getAreaId() {
        return areaId;
    }
    
    public void setAreaId(String areaId) {
        this.areaId = areaId;
    }
    
    public int getCenterX() {
        return centerX;
    }
    
    public void setCenterX(int centerX) {
        this.centerX = centerX;
    }
    
    public int getCenterZ() {
        return centerZ;
    }
    
    public void setCenterZ(int centerZ) {
        this.centerZ = centerZ;
    }
    
    public int getRadius() {
        return radius > 0 ? radius : 600;
    }
    
    public void setRadius(int radius) {
        this.radius = radius;
    }
    
    public int getResolution() {
        return resolution > 0 ? resolution : 600;
    }
    
    public void setResolution(int resolution) {
        this.resolution = resolution;
    }
    
    public String getMapData() {
        return mapData;
    }
    
    public void setMapData(String mapData) {
        this.mapData = mapData;
        // 수동 설정 시 FULL 타입으로 간주
        if (this.packetType == null) {
            this.packetType = "FULL";
        }
    }
    
    public String getFormat() {
        return format != null ? format : "RAW";
    }
    
    public void setFormat(String format) {
        this.format = format;
    }
    
    public List<MapUpdate> getUpdates() {
        return updates;
    }
    
    public String getPacketType() {
        return packetType;
    }
    
    public boolean isFullMap() {
        return "FULL".equals(packetType);
    }
    
    public boolean isUpdate() {
        return "UPDATE".equals(packetType);
    }
    
    /**
     * 맵 데이터가 유효한지 확인
     */
    public boolean isValid() {
        if (isFullMap()) {
            return mapData != null && !mapData.isEmpty() && resolution > 0;
        } else if (isUpdate()) {
            return updates != null && !updates.isEmpty();
        }
        return false;
    }
    
    /**
     * 1픽셀당 블록 수
     */
    public double getBlocksPerPixel() {
        return (double) (radius * 2) / resolution;
    }
    
    @Override
    public String toString() {
        return String.format("HunterMapData[area=%s, center=(%d,%d), radius=%d, res=%d, type=%s]",
                areaId, centerX, centerZ, radius, resolution, packetType);
    }
}
