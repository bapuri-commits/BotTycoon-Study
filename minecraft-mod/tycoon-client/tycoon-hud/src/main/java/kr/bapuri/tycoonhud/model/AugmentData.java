package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 증강 선택 데이터 모델
 * 서버에서 전송하는 증강 선택지 데이터
 */
public class AugmentData {

    /**
     * 증강 선택 UI 표시 여부
     */
    @SerializedName("showSelection")
    private boolean showSelection;
    
    /**
     * 증강 선택 대기 중 (알림 표시용)
     * showSelection=false, augmentPending=true → 알림만 표시, UI 안 열림
     * showSelection=true → UI 열기
     */
    @SerializedName("augmentPending")
    private boolean augmentPending;

    /**
     * 선택 가능한 증강 목록 (3개)
     */
    @SerializedName("choices")
    private List<AugmentChoice> choices;

    /**
     * 현재 HCL 레벨
     */
    @SerializedName("currentHclLevel")
    private int currentHclLevel;

    /**
     * 선택 제한 시간 (초)
     */
    @SerializedName("timeLimit")
    private int timeLimit;

    /**
     * 남은 선택 시간 (초)
     */
    @SerializedName("remainingTime")
    private int remainingTime;

    // ================================================================================
    // Getters
    // ================================================================================

    public boolean isShowSelection() {
        return showSelection;
    }
    
    public boolean isAugmentPending() {
        return augmentPending;
    }

    public List<AugmentChoice> getChoices() {
        return choices;
    }

    public int getCurrentHclLevel() {
        return currentHclLevel;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    // ================================================================================
    // 증강 선택지 클래스
    // ================================================================================

    public static class AugmentChoice {
        /**
         * 증강 ID
         */
        @SerializedName("id")
        private String id;

        /**
         * 증강 이름
         */
        @SerializedName("name")
        private String name;

        /**
         * 증강 설명
         */
        @SerializedName("description")
        private String description;

        /**
         * 증강 등급 (SILVER, GOLD, PRISMATIC)
         */
        @SerializedName("tier")
        private String tier;

        /**
         * 아이콘 (아이템 ID 또는 커스텀 텍스처 ID)
         */
        @SerializedName("icon")
        private String icon;

        /**
         * 전제 조건 증강 ID (트리 구조)
         */
        @SerializedName("prerequisite")
        private String prerequisite;

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
            return tier != null ? tier : "SILVER";
        }

        public String getIcon() {
            return icon;
        }

        public String getPrerequisite() {
            return prerequisite;
        }

        /**
         * 등급별 색상 반환
         */
        public int getTierColor() {
            return switch (getTier().toUpperCase()) {
                case "GOLD" -> 0xFFFFD700;      // 골드
                case "PRISMATIC" -> 0xFFDA70D6; // 프리즘 (보라/분홍)
                default -> 0xFFC0C0C0;          // 실버
            };
        }

        /**
         * 등급별 배경색 반환
         */
        public int getTierBgColor() {
            return switch (getTier().toUpperCase()) {
                case "GOLD" -> 0x40FFD700;      // 반투명 골드
                case "PRISMATIC" -> 0x40DA70D6; // 반투명 프리즘
                default -> 0x40C0C0C0;          // 반투명 실버
            };
        }

        /**
         * 등급별 테두리 색상 반환
         */
        public int getTierBorderColor() {
            return switch (getTier().toUpperCase()) {
                case "GOLD" -> 0xFFB8860B;      // 진한 골드
                case "PRISMATIC" -> 0xFFBA55D3; // 진한 프리즘
                default -> 0xFF808080;          // 진한 실버
            };
        }

        /**
         * 등급 한글명
         */
        public String getTierDisplayName() {
            return switch (getTier().toUpperCase()) {
                case "GOLD" -> "골드";
                case "PRISMATIC" -> "프리즘";
                default -> "실버";
            };
        }
    }
}

