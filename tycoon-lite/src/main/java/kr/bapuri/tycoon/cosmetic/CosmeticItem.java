package kr.bapuri.tycoon.cosmetic;

import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * 치장 아이템 정의
 * 
 * 채팅 색상, 파티클, 발광 효과 등 치장 아이템을 정의합니다.
 */
public class CosmeticItem {
    
    private final String id;           // 고유 ID
    private final String name;         // 표시 이름
    private final CosmeticType type;   // 타입
    private final long price;          // BC 가격
    private final Material icon;       // GUI 아이콘
    
    // 타입별 추가 데이터
    private String colorCode;          // 채팅 색상 코드 (§c, §6 등)
    private Particle particleType;     // 파티클 타입
    private String glowColor;          // 발광 색상 (WHITE, RED 등)
    
    public CosmeticItem(String id, String name, CosmeticType type, long price, Material icon) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.price = price;
        this.icon = icon;
    }
    
    // ========== 기본 정보 ==========
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public CosmeticType getType() {
        return type;
    }
    
    public long getPrice() {
        return price;
    }
    
    public Material getIcon() {
        return icon;
    }
    
    public String getPriceDisplay() {
        return String.format("%,d BC", price);
    }
    
    // ========== 타입별 데이터 ==========
    
    public String getColorCode() {
        return colorCode;
    }
    
    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }
    
    public Particle getParticleType() {
        return particleType;
    }
    
    public void setParticleType(Particle particleType) {
        this.particleType = particleType;
    }
    
    public String getGlowColor() {
        return glowColor;
    }
    
    public void setGlowColor(String glowColor) {
        this.glowColor = glowColor;
    }
    
    /**
     * 설명 생성
     */
    public String getDescription() {
        return switch (type) {
            case CHAT_COLOR -> colorCode + "채팅 메시지가 이 색상으로 표시됩니다";
            case PARTICLE -> "머리 위에 " + name + " 효과가 나타납니다";
            case GLOW -> "몸 전체가 " + name + " 효과로 빛납니다";
        };
    }
}
