package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * 직업 정보를 담는 데이터 클래스입니다.
 * 
 * <p>서버로부터 수신되는 직업 데이터의 구조:</p>
 * <pre>
 * {
 *     "type": "MINER",
 *     "level": 35,
 *     "grade": 2,
 *     "gradeTitle": "숙련",
 *     "currentXp": 1500,
 *     "nextLevelXp": 3600
 * }
 * </pre>
 * 
 * <h3>Null 처리</h3>
 * <ul>
 *   <li>type: nullable - 직업 미선택 시 null</li>
 *   <li>gradeTitle: nullable - 기본값 "견습"</li>
 *   <li>숫자 필드: 기본값 0 또는 1</li>
 * </ul>
 */
public class JobData {
    
    /** 직업 타입 (MINER, FARMER, FISHER, CHEF, ARTISAN, ENGINEER) - nullable */
    @SerializedName("type")
    @Nullable
    private String type;
    
    /** 현재 레벨 (기본값: 1) */
    @SerializedName("level")
    private int level = 1;
    
    /** 등급 (0: 초보, 1: 견습, 2: 숙련, 3: 전문, 4: 장인, 5: 달인) (기본값: 1) */
    @SerializedName("grade")
    private int grade = 1;
    
    /** 등급 명칭 (예: "숙련") - nullable, 기본값 "견습" */
    @SerializedName("gradeTitle")
    @Nullable
    private String gradeTitle;
    
    /** 현재 경험치 (기본값: 0) */
    @SerializedName("currentXp")
    private long currentXp = 0;
    
    /** 다음 레벨까지 필요한 경험치 (기본값: 100) */
    @SerializedName("nextLevelXp")
    private long nextLevelXp = 100;
    
    /** 최대 레벨 여부 (기본값: false) */
    @SerializedName("isMaxLevel")
    private boolean isMaxLevel = false;
    
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
        return gradeTitle != null ? gradeTitle : getDefaultGradeTitle(grade);
    }
    
    public long getCurrentXp() {
        return Math.max(0, currentXp);
    }
    
    public long getNextLevelXp() {
        return Math.max(1, nextLevelXp);
    }
    
    public boolean isMaxLevel() {
        return isMaxLevel;
    }
    
    // ========================================================================
    // Setters (실시간 업데이트용)
    // ========================================================================
    
    public void setCurrentXp(long currentXp) {
        this.currentXp = currentXp;
    }
    
    public void setNextLevelXp(long nextLevelXp) {
        this.nextLevelXp = nextLevelXp;
    }
    
    public void setLevel(int level) {
        this.level = level;
    }
    
    public void setGrade(int grade) {
        this.grade = grade;
    }
    
    public void setGradeTitle(String gradeTitle) {
        this.gradeTitle = gradeTitle;
    }
    
    // ========================================================================
    // 헬퍼 메소드
    // ========================================================================
    
    /**
     * 등급 기본 칭호 반환
     */
    private static String getDefaultGradeTitle(int grade) {
        return switch (grade) {
            case 0 -> "초보";
            case 1 -> "견습";
            case 2 -> "숙련";
            case 3 -> "전문";
            case 4 -> "장인";
            case 5 -> "달인";
            default -> "Lv." + grade;
        };
    }
    
    /**
     * 직업 타입을 한글로 변환합니다.
     * 
     * @return 한글 직업명 (예: "광부")
     */
    public String getLocalizedType() {
        if (type == null) return "없음";
        
        return switch (type.toUpperCase()) {
            case "MINER" -> "광부";
            case "FARMER" -> "농부";
            case "FISHER" -> "어부";
            case "CHEF" -> "요리사";
            case "ARTISAN" -> "장인";
            case "ENGINEER" -> "기술자";
            default -> type;
        };
    }
    
    /**
     * 경험치 진행률을 0.0 ~ 1.0 사이 값으로 반환합니다.
     * 
     * @return 경험치 진행률
     */
    public float getXpProgress() {
        if (nextLevelXp <= 0) return 0f;
        return (float) currentXp / nextLevelXp;
    }
}

