package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Collections;

/**
 * 업적 데이터를 담는 클래스입니다 (v3).
 * 
 * <p>서버로부터 PLAYER_PROFILE 패킷 내 achievements 필드로 수신됩니다.</p>
 * 
 * <pre>
 * {
 *     "totalCount": 50,
 *     "unlockedCount": 12,
 *     "recentUnlocks": [
 *         {
 *             "id": "codex_10",
 *             "name": "초보 수집가",
 *             "description": "도감 10개 등록",
 *             "tier": "NORMAL",
 *             "tierColor": "§f",
 *             "bottCoinReward": 1
 *         }
 *     ]
 * }
 * </pre>
 */
public class AchievementData {
    
    /** 전체 업적 수 */
    @SerializedName("totalCount")
    private int totalCount;
    
    /** 해금된 업적 수 */
    @SerializedName("unlockedCount")
    private int unlockedCount;
    
    /** 최근 해금된 업적 상세 목록 (v3) */
    @SerializedName("recentUnlocks")
    private List<AchievementDetail> recentUnlocks;
    
    // Getters
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public int getUnlockedCount() {
        return unlockedCount;
    }
    
    /**
     * 최근 해금된 업적 상세 목록을 반환합니다.
     */
    public List<AchievementDetail> getRecentUnlocks() {
        return recentUnlocks != null ? recentUnlocks : Collections.emptyList();
    }
    
    /**
     * 진행률을 백분율로 반환합니다.
     * 
     * @return 0 ~ 100 사이의 값
     */
    public int getProgressPercent() {
        if (totalCount <= 0) return 0;
        return (int) ((unlockedCount * 100.0) / totalCount);
    }
    
    /**
     * 표시용 텍스트를 반환합니다.
     * 
     * @return "업적: 12/50 (24%)" 형식
     */
    public String getDisplayText() {
        return String.format("업적: %d/%d (%d%%)", unlockedCount, totalCount, getProgressPercent());
    }
    
    /**
     * 업적 상세 정보 (v3)
     */
    public static class AchievementDetail {
        
        /** 업적 ID */
        @SerializedName("id")
        private String id;
        
        /** 업적 이름 */
        @SerializedName("name")
        private String name;
        
        /** 업적 설명 */
        @SerializedName("description")
        private String description;
        
        /** 티어 (NORMAL, RARE, EPIC, LEGENDARY) */
        @SerializedName("tier")
        private String tier;
        
        /** Minecraft 색상 코드 (§f, §b, §d, §6) */
        @SerializedName("tierColor")
        private String tierColor;
        
        /** BottCoin 보상 */
        @SerializedName("bottCoinReward")
        private int bottCoinReward;
        
        // Getters
        
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getTier() {
            return tier;
        }
        
        public String getTierColor() {
            return tierColor;
        }
        
        public int getBottCoinReward() {
            return bottCoinReward;
        }
        
        /**
         * Minecraft 색상 코드를 ARGB 정수로 변환합니다.
         */
        public int getColorARGB() {
            if (tierColor == null) return 0xFFFFFFFF;
            
            return switch (tierColor) {
                case "§f" -> 0xFFFFFFFF;  // 흰색 (NORMAL)
                case "§b" -> 0xFF55FFFF;  // 하늘색 (RARE)
                case "§d" -> 0xFFFF55FF;  // 분홍색 (EPIC)
                case "§6" -> 0xFFFFAA00;  // 금색 (LEGENDARY)
                case "§a" -> 0xFF55FF55;  // 연두색
                case "§e" -> 0xFFFFFF55;  // 노란색
                case "§c" -> 0xFFFF5555;  // 빨간색
                default -> 0xFFFFFFFF;
            };
        }
        
        /**
         * 티어에 따른 표시 이름을 반환합니다.
         */
        public String getTierDisplayName() {
            if (tier == null) return "";
            
            return switch (tier.toUpperCase()) {
                case "NORMAL" -> "일반";
                case "RARE" -> "희귀";
                case "EPIC" -> "에픽";
                case "LEGENDARY" -> "전설";
                default -> tier;
            };
        }
    }
}
