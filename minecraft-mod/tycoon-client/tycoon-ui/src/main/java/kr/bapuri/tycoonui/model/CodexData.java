package kr.bapuri.tycoonui.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * 도감 데이터를 담는 클래스입니다.
 * 
 * <p>서버로부터 CODEX_DATA 패킷으로 수신됩니다.</p>
 * 
 * <pre>
 * {
 *   "type": "CODEX_DATA",
 *   "totalCount": 150,
 *   "collectedCount": 45,
 *   "progressPercent": 30.0,
 *   "categories": [...],
 *   "items": [...]
 * }
 * </pre>
 */
public class CodexData {
    
    /** 전체 도감 아이템 수 */
    @SerializedName("totalCount")
    private int totalCount;
    
    /** 수집한 도감 아이템 수 */
    @SerializedName("collectedCount")
    private int collectedCount;
    
    /** 진행률 (0.0 ~ 100.0) */
    @SerializedName("progressPercent")
    private double progressPercent;
    
    /** 카테고리 목록 */
    @SerializedName("categories")
    private List<Category> categories;
    
    /** 모든 아이템 목록 */
    @SerializedName("items")
    private List<Item> items;
    
    // ========================================================================
    // Phase 4 확장 필드 - 마일스톤 정보
    // ========================================================================
    
    /** 다음 마일스톤 정보 */
    @SerializedName("nextMilestone")
    private Milestone nextMilestone;
    
    // ===== Legacy compatibility fields =====
    @SerializedName("totalCollected")
    private Integer totalCollectedLegacy;
    
    @SerializedName("totalItems")
    private Integer totalItemsLegacy;
    
    // Getters
    
    public int getTotalCount() {
        // 새 필드 우선, 없으면 레거시 필드 사용
        if (totalCount > 0) return totalCount;
        return totalItemsLegacy != null ? totalItemsLegacy : 0;
    }
    
    public int getCollectedCount() {
        // 새 필드 우선, 없으면 레거시 필드 사용
        if (collectedCount > 0) return collectedCount;
        return totalCollectedLegacy != null ? totalCollectedLegacy : 0;
    }
    
    public double getProgressPercent() {
        if (progressPercent > 0) return progressPercent;
        // 레거시: 계산
        int total = getTotalCount();
        if (total <= 0) return 0;
        return (getCollectedCount() * 100.0) / total;
    }
    
    public List<Category> getCategories() {
        return categories != null ? categories : Collections.emptyList();
    }
    
    public List<Item> getAllItems() {
        return items != null ? items : Collections.emptyList();
    }
    
    /**
     * [Phase 4] 다음 마일스톤 정보 반환
     */
    public Milestone getNextMilestone() {
        return nextMilestone;
    }
    
    /**
     * 특정 카테고리의 아이템들을 반환합니다.
     */
    public List<Item> getItemsByCategory(String categoryName) {
        if (items == null || categoryName == null) return Collections.emptyList();
        
        List<Item> result = new ArrayList<>();
        for (Item item : items) {
            if (categoryName.equals(item.getCategory())) {
                result.add(item);
            }
        }
        return result;
    }
    
    // ===== Legacy compatibility =====
    
    public int getTotalCollected() {
        return getCollectedCount();
    }
    
    public int getTotalItems() {
        return getTotalCount();
    }
    
    /**
     * 도감 카테고리
     */
    public static class Category {
        
        /** 카테고리 이름 (한글) */
        @SerializedName("name")
        private String name;
        
        /** 전체 아이템 수 */
        @SerializedName("total")
        private int total;
        
        /** 수집된 아이템 수 */
        @SerializedName("collected")
        private int collected;
        
        /** 카테고리 완료 여부 */
        @SerializedName("complete")
        private boolean complete;
        
        /** [Phase 4] 카테고리 완성 보상 (BottCoin) */
        @SerializedName("reward")
        private long reward;
        
        // Legacy: items field (제거됨 - 이제 상위 items에서 category로 필터링)
        @SerializedName("items")
        private List<Item> legacyItems;
        
        public String getName() {
            return name;
        }
        
        public int getTotal() {
            return total;
        }
        
        public int getCollected() {
            return collected;
        }
        
        public boolean isComplete() {
            return complete;
        }
        
        /**
         * Legacy compatibility - 이전 버전과의 호환성
         */
        public List<Item> getItems() {
            return legacyItems != null ? legacyItems : Collections.emptyList();
        }
        
        /**
         * 진행률 반환 (0.0 ~ 1.0)
         */
        public float getProgress() {
            if (total <= 0) return 0f;
            return (float) collected / total;
        }
        
        /**
         * [Phase 4] 카테고리 완성 보상 반환
         */
        public long getReward() {
            return reward;
        }
    }
    
    /**
     * [Phase 4] 마일스톤 정보
     */
    public static class Milestone {
        
        /** 마일스톤 달성 목표 (수집 개수) */
        @SerializedName("target")
        private int target;
        
        /** 현재 수집 개수 */
        @SerializedName("current")
        private int current;
        
        /** BottCoin 보상 */
        @SerializedName("bottcoinReward")
        private long bottcoinReward;
        
        /** BD 보상 */
        @SerializedName("bdReward")
        private long bdReward;
        
        public int getTarget() {
            return target;
        }
        
        public int getCurrent() {
            return current;
        }
        
        public long getBottcoinReward() {
            return bottcoinReward;
        }
        
        public long getBdReward() {
            return bdReward;
        }
        
        public float getProgress() {
            if (target <= 0) return 0f;
            return Math.min(1f, (float) current / target);
        }
    }
    
    /**
     * 도감 아이템
     */
    public static class Item {
        
        /** 아이템 ID (Material 이름) */
        @SerializedName("id")
        private String id;
        
        /** 한글 표시 이름 */
        @SerializedName("name")
        private String name;
        
        /** 소속 카테고리 (한글) */
        @SerializedName("category")
        private String category;
        
        /** 수집 여부 */
        @SerializedName("collected")
        private boolean collected;
        
        /** 아이콘용 Minecraft Material */
        @SerializedName("iconMaterial")
        private String iconMaterial;
        
        /** 최초 수집 BottCoin 보상 */
        @SerializedName("reward")
        private long reward;
        
        /** 등록에 필요한 수량 */
        @SerializedName("requiredCount")
        private int requiredCount;
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public String getCategory() {
            return category;
        }
        
        public boolean isCollected() {
            return collected;
        }
        
        public String getIconMaterial() {
            return iconMaterial;
        }
        
        public long getReward() {
            return reward;
        }
        
        public int getRequiredCount() {
            return requiredCount;
        }
    }
}
