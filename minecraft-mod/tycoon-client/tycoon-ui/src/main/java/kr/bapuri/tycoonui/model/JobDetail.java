package kr.bapuri.tycoonui.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collections;

/**
 * 직업 상세 정보를 담는 클래스입니다.
 * 
 * <p>서버로부터 JOB_DETAIL 패킷으로 수신됩니다.</p>
 * 
 * <h3>Phase 3 확장 필드</h3>
 * <ul>
 *   <li>currentBonuses - 현재 등급 보너스 목록</li>
 *   <li>nextGradeBonuses - 다음 등급 보너스 목록</li>
 *   <li>stats - 직업 통계</li>
 * </ul>
 */
public class JobDetail {
    
    @SerializedName("type")
    @Nullable
    private String type;
    
    @SerializedName("level")
    private int level = 1;
    
    @SerializedName("grade")
    private int grade = 1;
    
    @SerializedName("gradeTitle")
    @Nullable
    private String gradeTitle;
    
    @SerializedName("currentXp")
    private long currentXp = 0;
    
    @SerializedName("nextLevelXp")
    private long nextLevelXp = 100;
    
    @SerializedName("promotionRequirements")
    @Nullable
    private List<Requirement> promotionRequirements;
    
    @SerializedName("canPromote")
    private boolean canPromote = false;
    
    // ========================================================================
    // Phase 3 확장 필드
    // ========================================================================
    
    /** 현재 등급 보너스 목록 (nullable) */
    @SerializedName("currentBonuses")
    @Nullable
    private List<String> currentBonuses;
    
    /** 다음 등급 보너스 목록 (nullable) */
    @SerializedName("nextGradeBonuses")
    @Nullable
    private List<String> nextGradeBonuses;
    
    /** 직업 통계 (nullable) */
    @SerializedName("stats")
    @Nullable
    private JobStats stats;
    
    // ========================================================================
    // Getters (null-safe)
    // ========================================================================
    
    @Nullable
    public String getType() {
        return type;
    }
    
    public int getLevel() {
        return Math.max(1, level);
    }
    
    public int getGrade() {
        return Math.max(0, grade);
    }
    
    public String getGradeTitle() {
        return gradeTitle != null ? gradeTitle : "견습";
    }
    
    public long getCurrentXp() {
        return Math.max(0, currentXp);
    }
    
    public long getNextLevelXp() {
        return Math.max(1, nextLevelXp);
    }
    
    public List<Requirement> getPromotionRequirements() {
        return promotionRequirements != null ? promotionRequirements : Collections.emptyList();
    }
    
    public boolean canPromote() {
        return canPromote;
    }
    
    public List<String> getCurrentBonuses() {
        return currentBonuses != null ? currentBonuses : Collections.emptyList();
    }
    
    public List<String> getNextGradeBonuses() {
        return nextGradeBonuses != null ? nextGradeBonuses : Collections.emptyList();
    }
    
    @Nullable
    public JobStats getStats() {
        return stats;
    }
    
    /**
     * 경험치 진행률 (0.0 ~ 1.0)
     */
    public float getXpProgress() {
        long next = getNextLevelXp();
        if (next <= 0) return 0f;
        return (float) getCurrentXp() / next;
    }
    
    /**
     * 직업 데이터가 유효한지 확인
     */
    public boolean isValid() {
        return type != null && !type.isEmpty();
    }
    
    // ========================================================================
    // 내부 클래스
    // ========================================================================
    
    /**
     * 승급 조건
     */
    public static class Requirement {
        
        @SerializedName("description")
        private String description;
        
        @SerializedName("completed")
        private boolean completed;
        
        /** 현재 값 (선택적) */
        @SerializedName("current")
        private long current = 0;
        
        /** 필요 값 (선택적) */
        @SerializedName("required")
        private long required = 0;
        
        public String getDescription() {
            return description != null ? description : "";
        }
        
        public boolean isCompleted() {
            return completed;
        }
        
        public long getCurrent() {
            return current;
        }
        
        public long getRequired() {
            return required;
        }
        
        /**
         * 진행률 (0.0 ~ 1.0)
         */
        public float getProgress() {
            if (required <= 0) return completed ? 1f : 0f;
            return Math.min(1f, (float) current / required);
        }
    }
    
    /**
     * 직업 통계
     */
    public static class JobStats {
        
        /** 총 활동 횟수 (채굴/수확/낚시) */
        @SerializedName("totalActions")
        private long totalActions = 0;
        
        /** 총 판매액 */
        @SerializedName("totalSellAmount")
        private long totalSellAmount = 0;
        
        /** 현재 등급 총 판매액 (승급 조건용) */
        @SerializedName("currentTotalSell")
        private long currentTotalSell = 0;
        
        public long getTotalActions() {
            return totalActions;
        }
        
        public long getTotalSellAmount() {
            return totalSellAmount;
        }
        
        public long getCurrentTotalSell() {
            return currentTotalSell;
        }
    }
}

