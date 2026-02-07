package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * 던전 맵 데이터 모델
 * 서버에서 전송하는 로그라이크 던전 노드 맵 정보
 */
public class DungeonMapData {

    /**
     * 현재 플레이어가 위치한 노드 ID
     */
    @SerializedName("currentNodeId")
    private String currentNodeId;

    /**
     * 현재 층 번호
     */
    @SerializedName("currentFloor")
    private int currentFloor;

    /**
     * 탐험된 노드 목록 (Fog of War 적용)
     */
    @SerializedName("exploredNodes")
    private List<MapNode> exploredNodes = new ArrayList<>();

    /**
     * 선택 가능한 다음 노드 ID 목록
     */
    @SerializedName("availableNodeIds")
    private List<String> availableNodeIds = new ArrayList<>();

    /**
     * 던전 활성화 여부
     */
    @SerializedName("active")
    private boolean active;

    // ================================================================================
    // Getters
    // ================================================================================

    public String getCurrentNodeId() { return currentNodeId; }
    public int getCurrentFloor() { return currentFloor; }
    public List<MapNode> getExploredNodes() { return exploredNodes != null ? exploredNodes : new ArrayList<>(); }
    public List<String> getAvailableNodeIds() { return availableNodeIds != null ? availableNodeIds : new ArrayList<>(); }
    public boolean isActive() { return active; }

    /**
     * 특정 노드 ID로 노드 찾기
     */
    public MapNode getNode(String nodeId) {
        if (nodeId == null || exploredNodes == null) return null;
        return exploredNodes.stream()
                .filter(n -> nodeId.equals(n.getNodeId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 현재 노드 가져오기
     */
    public MapNode getCurrentNode() {
        return getNode(currentNodeId);
    }

    /**
     * 노드가 선택 가능한지 확인
     */
    public boolean isNodeAvailable(String nodeId) {
        return availableNodeIds != null && availableNodeIds.contains(nodeId);
    }

    // ================================================================================
    // 내부 클래스
    // ================================================================================

    /**
     * 개별 노드 정보
     */
    public static class MapNode {
        @SerializedName("nodeId")
        private String nodeId;

        @SerializedName("type")
        private String type;  // START, COMBAT, ELITE, SHOP, REST, BOSS 등

        @SerializedName("depth")
        private int depth;  // 층 내 깊이 (0: 시작)

        @SerializedName("index")
        private int index;  // 같은 깊이에서의 인덱스

        @SerializedName("state")
        private String state;  // LOCKED, AVAILABLE, ACTIVE, CLEARED, SKIPPED

        @SerializedName("connections")
        private List<String> connections = new ArrayList<>();  // 연결된 다음 노드 ID들

        public String getNodeId() { return nodeId; }
        public String getType() { return type; }
        public int getDepth() { return depth; }
        public int getIndex() { return index; }
        public String getState() { return state; }
        public List<String> getConnections() { return connections != null ? connections : new ArrayList<>(); }

        public boolean isCleared() { return "CLEARED".equals(state); }
        public boolean isActive() { return "ACTIVE".equals(state); }
        public boolean isAvailable() { return "AVAILABLE".equals(state); }
        public boolean isLocked() { return "LOCKED".equals(state); }
    }
}
