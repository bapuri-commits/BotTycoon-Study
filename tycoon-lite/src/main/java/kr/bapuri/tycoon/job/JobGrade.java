package kr.bapuri.tycoon.job;

/**
 * JobGrade - 직업 등급 (1차~4차)
 * 
 * Tier 1 직업: 1차~4차 (4등급)
 * Tier 2 직업: 1차~3차 (3등급)
 * 
 * 등급별 요구 레벨:
 * - GRADE_1: Lv1 (시작)
 * - GRADE_2: Lv20
 * - GRADE_3: Lv40
 * - GRADE_4: Lv80 (Tier 1만)
 */
public enum JobGrade {
    
    GRADE_1(1, "1차", 1, "기초 단계"),
    GRADE_2(2, "2차", 20, "중급 단계"),
    GRADE_3(3, "3차", 40, "고급 단계"),
    GRADE_4(4, "4차", 80, "마스터 단계");
    
    private final int value;
    private final String displayName;
    private final int requiredLevel;
    private final String description;
    
    JobGrade(int value, String displayName, int requiredLevel, String description) {
        this.value = value;
        this.displayName = displayName;
        this.requiredLevel = requiredLevel;
        this.description = description;
    }
    
    public int getValue() {
        return value;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getRequiredLevel() {
        return requiredLevel;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 다음 등급 반환 (최대면 null)
     */
    public JobGrade next() {
        return switch (this) {
            case GRADE_1 -> GRADE_2;
            case GRADE_2 -> GRADE_3;
            case GRADE_3 -> GRADE_4;
            case GRADE_4 -> null;
        };
    }
    
    /**
     * 이전 등급 반환 (최소면 null)
     */
    public JobGrade previous() {
        return switch (this) {
            case GRADE_1 -> null;
            case GRADE_2 -> GRADE_1;
            case GRADE_3 -> GRADE_2;
            case GRADE_4 -> GRADE_3;
        };
    }
    
    /**
     * 최대 등급인지 확인
     */
    public boolean isMaxGrade() {
        return this == GRADE_4;
    }
    
    /**
     * 최소 등급인지 확인
     */
    public boolean isMinGrade() {
        return this == GRADE_1;
    }
    
    /**
     * 값으로 등급 찾기
     */
    public static JobGrade fromValue(int value) {
        for (JobGrade grade : values()) {
            if (grade.value == value) {
                return grade;
            }
        }
        return GRADE_1; // 기본값
    }
    
    /**
     * 레벨에 해당하는 최대 등급 반환
     * (해당 레벨에서 달성 가능한 최고 등급)
     */
    public static JobGrade maxGradeForLevel(int level) {
        if (level >= GRADE_4.requiredLevel) return GRADE_4;
        if (level >= GRADE_3.requiredLevel) return GRADE_3;
        if (level >= GRADE_2.requiredLevel) return GRADE_2;
        return GRADE_1;
    }
    
    /**
     * 직업 타입에 따른 최대 등급 반환
     * Tier 1: GRADE_4, Tier 2: GRADE_3
     */
    public static JobGrade maxGradeFor(JobType jobType) {
        if (jobType == null) return GRADE_4;
        return jobType.isTier1() ? GRADE_4 : GRADE_3;
    }
    
    /**
     * 설정 키로 등급 찾기 (GRADE_1, GRADE_2 등)
     */
    public static JobGrade fromConfigKey(String key) {
        if (key == null) return null;
        try {
            return valueOf(key.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 숫자만 있는 경우 (1, 2, 3, 4)
            try {
                int value = Integer.parseInt(key.replace("GRADE_", "").trim());
                return fromValue(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }
    
    /**
     * 특정 등급 이상인지 확인
     */
    public boolean isAtLeast(JobGrade other) {
        if (other == null) return true;
        return this.value >= other.value;
    }
    
    /**
     * 특정 등급 이하인지 확인
     */
    public boolean isAtMost(JobGrade other) {
        if (other == null) return false;
        return this.value <= other.value;
    }
    
    /**
     * 설정 파일용 키 (GRADE_1 -> grade_1)
     */
    public String getConfigKey() {
        return name().toLowerCase();
    }
}
