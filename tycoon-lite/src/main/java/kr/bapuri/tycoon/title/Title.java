package kr.bapuri.tycoon.title;

/**
 * Title - 칭호 정의
 * 
 * LuckPerms 그룹과 매핑되어 TAB 플러그인에서 표시됩니다.
 */
public class Title {
    
    private final String id;           // 고유 ID (achievements.yml에서 참조)
    private final String displayName;  // 표시 이름 (예: "[수집가]")
    private final String description;  // 설명
    private final String color;        // 색상 코드 (예: "§a")
    private final String luckpermsGroup; // LuckPerms 그룹명 (null = ID와 동일)
    
    public Title(String id, String displayName, String description, String color) {
        this(id, displayName, description, color, null);
    }
    
    public Title(String id, String displayName, String description, String color, String luckpermsGroup) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.luckpermsGroup = (luckpermsGroup != null && !luckpermsGroup.isEmpty()) 
                              ? luckpermsGroup 
                              : id;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getColor() {
        return color;
    }
    
    /**
     * 색상이 적용된 표시 이름
     */
    public String getColoredDisplayName() {
        return color + displayName;
    }
    
    /**
     * LuckPerms 그룹명 반환
     */
    public String getLuckpermsGroup() {
        return luckpermsGroup;
    }
    
    /**
     * 채팅/TAB에 표시될 접두사
     */
    public String getPrefix() {
        return color + displayName + " §r";
    }
}
