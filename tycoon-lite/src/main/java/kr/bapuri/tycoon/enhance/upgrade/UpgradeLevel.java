package kr.bapuri.tycoon.enhance.upgrade;

/**
 * UpgradeLevel - 강화 레벨별 설정 데이터
 * 
 * Phase 6 LITE: 레거시 버전 이식
 * 
 * v3.0 밸런스 (vanilla-plus):
 * - 성공률: 99% → 5% (완만한 곡선, 초반 쉽고 후반 급경사)
 * - 하락률: +10부터 시작, 최대 25%
 * - 파괴율: +20부터 시작, 최대 10%
 * - 비용: 500 × 1.06^level + 500 (가파른 상승 + 평행이동)
 * - 공격력: level × 45% (100강 = +4500%)
 * - 방어력: level × 36% (100강 = +3600%)
 * - 체력: level × 0.25% (100강 풀셋 = +100% HP)
 */
public class UpgradeLevel {

    private final int level;
    
    // 확률 (0.0 ~ 1.0)
    private double successRate;      // 성공 확률
    private double downgradeRate;    // 하락 확률
    private double destroyRate;      // 파괴 확률
    // 나머지 = 유지 확률
    
    // 비용
    private long cost;               // 강화 비용
    
    // 스탯 보너스
    private double damageBonus;      // 공격력 보너스 (%)
    private double defenseBonus;     // 방어력 보너스 (%)
    private double healthBonus;      // 체력 보너스 (%)

    public UpgradeLevel(int level) {
        this.level = level;
        setDefaultValues();
    }

    /**
     * 기본값 설정 (레벨 기반 자동 계산)
     */
    private void setDefaultValues() {
        double t = level / 100.0;  // 0.0 ~ 1.0 정규화
        
        // ===== 성공률: 완만한 곡선 (99% → 5%, 초반 쉽고 후반 급경사) =====
        successRate = 0.99 - 0.94 * Math.pow(t, 1.5);
        successRate = Math.max(0.05, successRate); // 최소 5%

        // ===== 하락률: +10부터 시작, 매끄럽게 증가, 최대 25% =====
        if (level < 10) {
            downgradeRate = 0;
        } else {
            downgradeRate = 0.25 * Math.pow((level - 10) / 90.0, 1.2);
        }
        downgradeRate = Math.min(0.25, downgradeRate); // 최대 25%

        // ===== 파괴율: +20부터 시작, 매끄럽게 증가, 최대 10% =====
        if (level < 20) {
            destroyRate = 0;
        } else {
            destroyRate = 0.10 * Math.pow((level - 20) / 80.0, 1.3);
        }
        destroyRate = Math.min(0.10, destroyRate); // 최대 10%

        // ===== 비용: 가파른 상승 + 평행이동 =====
        cost = (long) (500 * Math.pow(1.06, level) + 500);

        // ===== 스탯 보너스 =====
        damageBonus = level * 45.0;   // level × 45%
        defenseBonus = level * 36.0;  // level × 36%
        healthBonus = level * 0.25;   // level × 0.25%
    }

    // ===== Getters =====

    public int getLevel() {
        return level;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public double getDowngradeRate() {
        return downgradeRate;
    }

    public double getDestroyRate() {
        return destroyRate;
    }

    public double getMaintainRate() {
        return Math.max(0, 1.0 - successRate - downgradeRate - destroyRate);
    }

    public long getCost() {
        return cost;
    }

    public double getDamageBonus() {
        return damageBonus;
    }

    public double getDefenseBonus() {
        return defenseBonus;
    }

    public double getHealthBonus() {
        return healthBonus;
    }

    // ===== Setters =====

    public void setSuccessRate(double successRate) {
        this.successRate = Math.min(1.0, Math.max(0, successRate));
    }

    public void setDowngradeRate(double downgradeRate) {
        this.downgradeRate = Math.min(1.0, Math.max(0, downgradeRate));
    }

    public void setDestroyRate(double destroyRate) {
        this.destroyRate = Math.min(1.0, Math.max(0, destroyRate));
    }

    public void setCost(long cost) {
        this.cost = Math.max(0, cost);
    }

    public void setDamageBonus(double damageBonus) {
        this.damageBonus = damageBonus;
    }

    public void setDefenseBonus(double defenseBonus) {
        this.defenseBonus = defenseBonus;
    }

    public void setHealthBonus(double healthBonus) {
        this.healthBonus = healthBonus;
    }

    /**
     * 확률 문자열 (소수점 2자리 %)
     */
    public String getSuccessRateString() {
        return String.format("%.2f%%", successRate * 100);
    }

    public String getDowngradeRateString() {
        return String.format("%.2f%%", downgradeRate * 100);
    }

    public String getDestroyRateString() {
        return String.format("%.2f%%", destroyRate * 100);
    }

    public String getMaintainRateString() {
        return String.format("%.2f%%", getMaintainRate() * 100);
    }

    @Override
    public String toString() {
        return "UpgradeLevel{" +
                "level=" + level +
                ", success=" + getSuccessRateString() +
                ", cost=" + cost +
                '}';
    }
}
