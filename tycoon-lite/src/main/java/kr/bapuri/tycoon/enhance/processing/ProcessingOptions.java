package kr.bapuri.tycoon.enhance.processing;

/**
 * ProcessingOptions - 블록 처리 옵션
 * 
 * 어떤 효과를 적용할지 제어
 */
public class ProcessingOptions {
    
    private boolean applyFortune = true;
    private boolean applyEnchantBonus = true;
    private boolean applyGradeBonus = true;
    private boolean applyLampEffects = true;
    private boolean grantJobExp = true;
    private boolean autoDeliver = true;
    private boolean removeBlock = true;
    
    // ===============================================================
    // 프리셋
    // ===============================================================
    
    /**
     * 모든 효과 활성화 (기본값)
     */
    public static ProcessingOptions allEnabled() {
        return new ProcessingOptions();
    }
    
    /**
     * 드롭 계산만 (XP 없음, 블록 제거 없음)
     */
    public static ProcessingOptions dropsOnly() {
        return new ProcessingOptions()
            .setGrantJobExp(false)
            .setRemoveBlock(false);
    }
    
    /**
     * 최소 처리 (Fortune과 드롭만)
     */
    public static ProcessingOptions minimal() {
        return new ProcessingOptions()
            .setApplyEnchantBonus(false)
            .setApplyGradeBonus(false)
            .setApplyLampEffects(false)
            .setGrantJobExp(false);
    }
    
    // ===============================================================
    // Getters
    // ===============================================================
    
    public boolean isApplyFortune() {
        return applyFortune;
    }
    
    public boolean isApplyEnchantBonus() {
        return applyEnchantBonus;
    }
    
    public boolean isApplyGradeBonus() {
        return applyGradeBonus;
    }
    
    public boolean isApplyLampEffects() {
        return applyLampEffects;
    }
    
    public boolean isGrantJobExp() {
        return grantJobExp;
    }
    
    public boolean isAutoDeliver() {
        return autoDeliver;
    }
    
    public boolean isRemoveBlock() {
        return removeBlock;
    }
    
    // ===============================================================
    // Builder-style Setters
    // ===============================================================
    
    public ProcessingOptions setApplyFortune(boolean applyFortune) {
        this.applyFortune = applyFortune;
        return this;
    }
    
    public ProcessingOptions setApplyEnchantBonus(boolean applyEnchantBonus) {
        this.applyEnchantBonus = applyEnchantBonus;
        return this;
    }
    
    public ProcessingOptions setApplyGradeBonus(boolean applyGradeBonus) {
        this.applyGradeBonus = applyGradeBonus;
        return this;
    }
    
    public ProcessingOptions setApplyLampEffects(boolean applyLampEffects) {
        this.applyLampEffects = applyLampEffects;
        return this;
    }
    
    public ProcessingOptions setGrantJobExp(boolean grantJobExp) {
        this.grantJobExp = grantJobExp;
        return this;
    }
    
    public ProcessingOptions setAutoDeliver(boolean autoDeliver) {
        this.autoDeliver = autoDeliver;
        return this;
    }
    
    public ProcessingOptions setRemoveBlock(boolean removeBlock) {
        this.removeBlock = removeBlock;
        return this;
    }
}
