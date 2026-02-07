package kr.bapuri.tycoon.enhance.enchant;

import java.util.HashMap;
import java.util.Map;

/**
 * CustomEnchantData - 커스텀 인챈트 설정 데이터
 * 
 * config.yml에서 로드되는 인챈트별 상세 설정
 * 
 * Phase 6: 레거시 복사
 */
public class CustomEnchantData {

    private final CustomEnchant enchant;
    
    // 기본 정보
    private String displayName;
    private String description;
    private int maxLevel;
    
    // 상점 가격 (레벨별)
    private final Map<Integer, Long> prices = new HashMap<>();
    
    // 효과 수치 (레벨별)
    private final Map<Integer, Double> effectValues = new HashMap<>();
    
    // 확률 (레벨별, 0.0 ~ 1.0)
    private final Map<Integer, Double> chances = new HashMap<>();
    
    // 활성화 여부
    private boolean enabled = true;

    public CustomEnchantData(CustomEnchant enchant) {
        this.enchant = enchant;
        this.displayName = enchant.getDisplayName();
        this.description = enchant.getDescription();
        this.maxLevel = enchant.getMaxLevel();
        
        // 기본 가격/효과 설정
        for (int level = 1; level <= maxLevel; level++) {
            prices.put(level, 1000L * level * level);
            effectValues.put(level, level * 5.0);
            chances.put(level, Math.min(0.5, level * 0.1));
        }
    }

    // ===== Getters =====

    public CustomEnchant getEnchant() {
        return enchant;
    }

    public String getId() {
        return enchant.getId();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 특정 레벨의 가격
     */
    public long getPrice(int level) {
        return prices.getOrDefault(level, 0L);
    }

    /**
     * 특정 레벨의 효과 수치 (%, 데미지 등)
     */
    public double getEffectValue(int level) {
        return effectValues.getOrDefault(level, 0.0);
    }

    /**
     * 특정 레벨의 발동 확률
     */
    public double getChance(int level) {
        return chances.getOrDefault(level, 0.0);
    }

    // ===== Setters =====

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPrice(int level, long price) {
        prices.put(level, price);
    }

    public void setEffectValue(int level, double value) {
        effectValues.put(level, value);
    }

    public void setChance(int level, double chance) {
        chances.put(level, Math.min(1.0, Math.max(0.0, chance)));
    }

    // ===== 유틸리티 =====

    /**
     * 모든 레벨의 가격 맵
     */
    public Map<Integer, Long> getAllPrices() {
        return new HashMap<>(prices);
    }

    /**
     * 모든 레벨의 효과값 맵
     */
    public Map<Integer, Double> getAllEffectValues() {
        return new HashMap<>(effectValues);
    }

    /**
     * 모든 레벨의 확률 맵
     */
    public Map<Integer, Double> getAllChances() {
        return new HashMap<>(chances);
    }

    @Override
    public String toString() {
        return "CustomEnchantData{" +
                "id=" + enchant.getId() +
                ", displayName='" + displayName + '\'' +
                ", maxLevel=" + maxLevel +
                ", enabled=" + enabled +
                '}';
    }
}
