package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;

/**
 * 체력, 배고픔, 갑옷, 산소 데이터를 담는 클래스입니다.
 * 
 * <p>서버로부터 VITAL_UPDATE 패킷으로 수신됩니다.</p>
 * 
 * <h3>버그 방지</h3>
 * <ul>
 *     <li>모든 ratio 메서드는 0 나누기 방지</li>
 *     <li>음수 값 방지</li>
 *     <li>기본값 보장</li>
 * </ul>
 */
public class VitalData {
    
    /** 스키마 버전 */
    @SerializedName("schema")
    private int schema;
    
    // ================================================================================
    // 체력 (Health)
    // ================================================================================
    
    @SerializedName("health")
    private float health;
    
    @SerializedName("maxHealth")
    private float maxHealth;
    
    // ================================================================================
    // 배고픔 (Hunger)
    // ================================================================================
    
    @SerializedName("foodLevel")
    private int foodLevel;
    
    @SerializedName("maxFoodLevel")
    private int maxFoodLevel = 20;  // [Fix] 기본값 설정 (서버에서 누락 시 대비)
    
    @SerializedName("saturation")
    private float saturation;
    
    // ================================================================================
    // 갑옷 (Armor)
    // ================================================================================
    
    @SerializedName("armor")
    private int armor;
    
    @SerializedName("maxArmor")
    private int maxArmor;
    
    // ================================================================================
    // 산소 (Air)
    // ================================================================================
    
    @SerializedName("air")
    private int air;
    
    @SerializedName("maxAir")
    private int maxAir;
    
    @SerializedName("underwater")
    private boolean underwater;
    
    // ================================================================================
    // 경험치 (Experience)
    // ================================================================================
    
    @SerializedName("level")
    private int level;
    
    @SerializedName("expProgress")
    private float expProgress;
    
    @SerializedName("totalExp")
    private int totalExp;
    
    // ================================================================================
    // 헌터 월드 전용 (v2.2)
    // ================================================================================
    
    /** 헌터 모드 활성화 여부 */
    @SerializedName("hunterMode")
    private boolean hunterMode;
    
    /** HCL (Hunter Combat Level) */
    @SerializedName("hclLevel")
    private int hclLevel;
    
    /** HCL 최대 레벨 */
    @SerializedName("hclMaxLevel")
    private int hclMaxLevel;
    
    /** HCL 경험치 진행도 (0.0 ~ 1.0) */
    @SerializedName("hclProgress")
    private float hclProgress;
    
    /** 자기장 중심까지 거리 비율 (0.0 = 중심, 1.0+ = 밖) */
    @SerializedName("blueZoneDistanceRatio")
    private float blueZoneDistanceRatio;
    
    /** 자기장 중심까지 거리 (블록) - 레거시 호환 */
    @SerializedName("blueZoneDistance")
    private float blueZoneDistance;
    
    /** 자기장 내부 여부 */
    @SerializedName("insideBlueZone")
    private boolean insideBlueZone;
    
    /** 자기장 중심 X */
    @SerializedName("blueZoneCenterX")
    private double blueZoneCenterX;
    
    /** 자기장 중심 Z */
    @SerializedName("blueZoneCenterZ")
    private double blueZoneCenterZ;
    
    /** 자기장 현재 반경 */
    @SerializedName("blueZoneRadius")
    private double blueZoneRadius;
    
    /** 플레이어 X 좌표 */
    @SerializedName("playerX")
    private double playerX;
    
    /** 플레이어 Z 좌표 */
    @SerializedName("playerZ")
    private double playerZ;
    
    /** 자기장 페이즈 */
    @SerializedName("blueZonePhase")
    private int blueZonePhase;
    
    /** 자기장 상태 (WAITING, SHRINKING, FINAL) */
    @SerializedName("blueZoneState")
    private String blueZoneState;
    
    /** 자기장 축소까지 남은 시간 (초) */
    @SerializedName("blueZoneShrinkCountdown")
    private int blueZoneShrinkCountdown;
    
    // ================================================================================
    // [개선] 다음 자기장 정보 (배틀그라운드 스타일) - v2.4
    // WAITING 상태에서 다음 축소 목표를 미리 보여줌
    // ================================================================================
    
    /** 다음 자기장 중심 X */
    @SerializedName("blueZoneNextCenterX")
    private double blueZoneNextCenterX;
    
    /** 다음 자기장 중심 Z */
    @SerializedName("blueZoneNextCenterZ")
    private double blueZoneNextCenterZ;
    
    /** 다음 자기장 반경 */
    @SerializedName("blueZoneNextRadius")
    private double blueZoneNextRadius;
    
    // ================================================================================
    // 스태미너 (Stamina) - v2.3
    // ================================================================================
    
    /** 현재 스태미너 */
    @SerializedName("stamina")
    private float stamina;
    
    /** 최대 스태미너 */
    @SerializedName("maxStamina")
    private float maxStamina;
    
    /** 스태미너 경고 레벨 (0=정상, 1=경고, 2=위험) */
    @SerializedName("staminaWarning")
    private int staminaWarning;
    
    // ================================================================================
    // 상태 효과 (Status Effects)
    // ================================================================================
    
    @SerializedName("statusEffects")
    private java.util.List<StatusEffect> statusEffects;
    
    @SerializedName("absorptionAmount")
    private float absorptionAmount;
    
    /**
     * 상태 효과 정보
     */
    public static class StatusEffect {
        @SerializedName("effectId")
        public String effectId;
        
        @SerializedName("amplifier")
        public int amplifier;
        
        @SerializedName("duration")
        public int duration;
        
        @SerializedName("showParticles")
        public boolean showParticles;
        
        @SerializedName("customName")
        public String customName;
    }
    
    // ================================================================================
    // 커스텀 효과 (Custom Effects)
    // ================================================================================
    
    @SerializedName("customEffects")
    private java.util.List<CustomEffect> customEffects;
    
    /**
     * 커스텀 효과 정보 (인챈트, 특수 아이템 등)
     */
    public static class CustomEffect {
        @SerializedName("effectId")
        public String effectId;
        
        @SerializedName("displayName")
        public String displayName;
        
        @SerializedName("color")
        public String color;
        
        @SerializedName("duration")
        public int duration;
        
        @SerializedName("level")
        public int level;
    }
    
    // ================================================================================
    // 체력 Getters (안전한 값 반환)
    // ================================================================================
    
    public int getSchema() {
        return schema;
    }
    
    public float getHealth() {
        return Math.max(0, health);
    }
    
    public float getMaxHealth() {
        // 최소 1 보장 (0 나누기 방지)
        return maxHealth > 0 ? maxHealth : 20f;
    }
    
    public float getHealthRatio() {
        float max = getMaxHealth();
        float current = Math.max(0, Math.min(health, max));
        return current / max;
    }
    
    public String getHealthText() {
        return String.format("%.0f/%.0f", getHealth(), getMaxHealth());
    }
    
    /**
     * 체력이 위험 수준인지 확인 (30% 이하)
     */
    public boolean isHealthLow() {
        return getHealthRatio() <= 0.3f;
    }
    
    /**
     * 체력이 매우 위험한 수준인지 확인 (15% 이하)
     */
    public boolean isHealthCritical() {
        return getHealthRatio() <= 0.15f;
    }
    
    // ================================================================================
    // 배고픔 Getters (안전한 값 반환)
    // ================================================================================
    
    public int getFoodLevel() {
        return Math.max(0, foodLevel);
    }
    
    public int getMaxFoodLevel() {
        return maxFoodLevel > 0 ? maxFoodLevel : 20;
    }
    
    public float getSaturation() {
        return Math.max(0, saturation);
    }
    
    public float getFoodRatio() {
        int max = getMaxFoodLevel();
        int current = Math.max(0, Math.min(foodLevel, max));
        return (float) current / max;
    }
    
    public String getFoodText() {
        return String.format("%d/%d", getFoodLevel(), getMaxFoodLevel());
    }
    
    /**
     * 배고픔이 위험 수준인지 확인 (30% 이하)
     */
    public boolean isFoodLow() {
        return getFoodRatio() <= 0.3f;
    }
    
    // ================================================================================
    // 갑옷 Getters (안전한 값 반환)
    // ================================================================================
    
    public int getArmor() {
        return Math.max(0, armor);
    }
    
    public int getMaxArmor() {
        return maxArmor > 0 ? maxArmor : 20;
    }
    
    public float getArmorRatio() {
        int max = getMaxArmor();
        int current = Math.max(0, Math.min(armor, max));
        return (float) current / max;
    }
    
    public String getArmorText() {
        return String.format("%d/%d", getArmor(), getMaxArmor());
    }
    
    public boolean hasArmor() {
        return armor > 0;
    }
    
    // ================================================================================
    // 산소 Getters (안전한 값 반환)
    // ================================================================================
    
    public int getAir() {
        return Math.max(0, air);
    }
    
    public int getMaxAir() {
        return maxAir > 0 ? maxAir : 300;
    }
    
    public boolean isUnderwater() {
        return underwater;
    }
    
    public float getAirRatio() {
        int max = getMaxAir();
        int current = Math.max(0, Math.min(air, max));
        return (float) current / max;
    }
    
    public String getAirText() {
        // 틱을 초로 변환 (20틱 = 1초)
        int airSeconds = Math.max(0, air / 20);
        int maxAirSeconds = Math.max(1, getMaxAir() / 20);
        return String.format("%d/%ds", airSeconds, maxAirSeconds);
    }
    
    public boolean shouldShowAirBar() {
        return underwater || air < getMaxAir();
    }
    
    /**
     * 산소가 위험 수준인지 확인 (30% 이하)
     */
    public boolean isAirLow() {
        return getAirRatio() <= 0.3f;
    }
    
    // ================================================================================
    // 경험치 Getters
    // ================================================================================
    
    /**
     * 현재 레벨
     */
    public int getLevel() {
        return Math.max(0, level);
    }
    
    /**
     * 경험치 진행도 (0.0 ~ 1.0)
     */
    public float getExpProgress() {
        return Math.max(0, Math.min(1, expProgress));
    }
    
    /**
     * 총 경험치
     */
    public int getTotalExp() {
        return Math.max(0, totalExp);
    }
    
    /**
     * 경험치 텍스트 (Lv.15 45%)
     */
    public String getExpText() {
        int percent = (int) (getExpProgress() * 100);
        return "Lv." + getLevel() + " " + percent + "%";
    }
    
    /**
     * 짧은 경험치 텍스트 (레벨만)
     */
    public String getLevelText() {
        return "Lv." + getLevel();
    }
    
    // ================================================================================
    // 상태 효과 Getters
    // ================================================================================
    
    /**
     * 흡수 체력 (노란 하트)
     */
    public float getAbsorptionAmount() {
        return Math.max(0, absorptionAmount);
    }
    
    /**
     * 흡수 체력이 있는지 확인
     */
    public boolean hasAbsorption() {
        return absorptionAmount > 0;
    }
    
    /**
     * 특정 상태 효과가 활성화되어 있는지 확인
     */
    public boolean hasEffect(String effectId) {
        if (statusEffects == null) return false;
        for (StatusEffect effect : statusEffects) {
            if (effect.effectId != null && effect.effectId.equalsIgnoreCase(effectId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 독 효과 (체력바 녹색)
     */
    public boolean isPoisoned() {
        return hasEffect("POISON");
    }
    
    /**
     * 시듦 효과 (체력바 검은색)
     */
    public boolean isWithered() {
        return hasEffect("WITHER");
    }
    
    /**
     * 재생 효과 (체력바 핑크 반짝임)
     */
    public boolean isRegenerating() {
        return hasEffect("REGENERATION");
    }
    
    /**
     * 허기/식중독 효과 (배고픔바 녹색)
     */
    public boolean hasHungerEffect() {
        return hasEffect("HUNGER");
    }
    
    /**
     * 수중호흡 효과
     */
    public boolean hasWaterBreathing() {
        return hasEffect("WATER_BREATHING");
    }
    
    /**
     * 화염저항 효과
     */
    public boolean hasFireResistance() {
        return hasEffect("FIRE_RESISTANCE");
    }
    
    // ================================================================================
    // 커스텀 효과 Getters
    // ================================================================================
    
    /**
     * 커스텀 효과 목록 가져오기
     */
    public java.util.List<CustomEffect> getCustomEffects() {
        return customEffects != null ? customEffects : new java.util.ArrayList<>();
    }
    
    /**
     * 특정 커스텀 효과가 활성화되어 있는지 확인
     */
    public boolean hasCustomEffect(String effectId) {
        if (customEffects == null) return false;
        for (CustomEffect effect : customEffects) {
            if (effect.effectId != null && effect.effectId.equalsIgnoreCase(effectId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 출혈 효과 (커스텀)
     */
    public boolean isBleeding() {
        return hasCustomEffect("bleed");
    }
    
    /**
     * 서리 효과 (커스텀)
     */
    public boolean isFrosted() {
        return hasCustomEffect("frost");
    }
    
    /**
     * 흡혈 회복 효과 (커스텀)
     */
    public boolean hasVampireHeal() {
        return hasCustomEffect("vampire");
    }
    
    /**
     * 트루 데미지 효과 (커스텀)
     */
    public boolean hasTrueDamage() {
        return hasCustomEffect("true_damage");
    }
    
    /**
     * 최대 체력 증가 효과 (커스텀)
     */
    public boolean hasMaxHealthBoost() {
        return hasCustomEffect("max_health_boost");
    }
    
    /**
     * 화상 효과 (커스텀)
     */
    public boolean isBurning() {
        return hasCustomEffect("burn");
    }
    
    // ================================================================================
    // 헌터 월드 Getters (v2.2)
    // ================================================================================
    
    /**
     * 헌터 모드 활성화 여부
     */
    public boolean isHunterMode() {
        return hunterMode;
    }
    
    /**
     * HCL 레벨
     */
    public int getHclLevel() {
        return Math.max(0, hclLevel);
    }
    
    /**
     * HCL 최대 레벨
     */
    public int getHclMaxLevel() {
        return hclMaxLevel > 0 ? hclMaxLevel : 15;
    }
    
    /**
     * HCL 진행도 (0.0 ~ 1.0)
     */
    public float getHclProgress() {
        return Math.max(0, Math.min(1, hclProgress));
    }
    
    /**
     * HCL 비율 (현재 레벨 / 최대 레벨)
     */
    public float getHclRatio() {
        return (float) getHclLevel() / getHclMaxLevel();
    }
    
    /**
     * HCL 텍스트
     */
    public String getHclText() {
        return String.format("Lv %d/%d", getHclLevel(), getHclMaxLevel());
    }
    
    /**
     * 자기장 중심까지 거리
     */
    public float getBlueZoneDistance() {
        return Math.max(0, blueZoneDistance);
    }
    
    /**
     * 자기장 거리 비율 (0.0 = 중심, 1.0+ = 밖)
     */
    public float getBlueZoneDistanceRatio() {
        return blueZoneDistanceRatio;
    }
    
    /**
     * 자기장 내부 여부
     */
    public boolean isInsideBlueZone() {
        return insideBlueZone || blueZoneDistanceRatio < 1.0f;
    }
    
    /**
     * 자기장 위험 여부 (밖에 있음)
     */
    public boolean isBlueZoneDanger() {
        return hunterMode && blueZoneDistanceRatio >= 1.0f;
    }
    
    /**
     * 자기장 중심 X
     */
    public double getBlueZoneCenterX() {
        return blueZoneCenterX;
    }
    
    /**
     * 자기장 중심 Z
     */
    public double getBlueZoneCenterZ() {
        return blueZoneCenterZ;
    }
    
    /**
     * 자기장 현재 반경
     */
    public double getBlueZoneRadius() {
        return blueZoneRadius;
    }
    
    /**
     * 플레이어 X 좌표
     */
    public double getPlayerX() {
        return playerX;
    }
    
    /**
     * 플레이어 Z 좌표
     */
    public double getPlayerZ() {
        return playerZ;
    }
    
    /**
     * 자기장 페이즈
     */
    public int getBlueZonePhase() {
        return blueZonePhase;
    }
    
    /**
     * 자기장 상태
     */
    public String getBlueZoneState() {
        return blueZoneState != null ? blueZoneState : "WAITING";
    }
    
    /**
     * 자기장 축소까지 남은 시간 (초)
     */
    public int getBlueZoneShrinkCountdown() {
        return Math.max(0, blueZoneShrinkCountdown);
    }
    
    /**
     * 자기장 축소까지 남은 시간 (포맷팅)
     */
    public String getBlueZoneCountdownText() {
        int seconds = getBlueZoneShrinkCountdown();
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }
    
    // ================================================================================
    // [개선] 다음 자기장 Getters (v2.4 - 배틀그라운드 스타일)
    // ================================================================================
    
    /**
     * 다음 자기장 중심 X
     */
    public double getBlueZoneNextCenterX() {
        return blueZoneNextCenterX;
    }
    
    /**
     * 다음 자기장 중심 Z
     */
    public double getBlueZoneNextCenterZ() {
        return blueZoneNextCenterZ;
    }
    
    /**
     * 다음 자기장 반경
     */
    public double getBlueZoneNextRadius() {
        return blueZoneNextRadius;
    }
    
    /**
     * 다음 자기장 정보가 유효한지 확인
     * (WAITING 상태에서만 다음 자기장 표시)
     */
    public boolean hasNextZone() {
        return hunterMode && blueZoneNextRadius > 0 && blueZoneNextRadius < blueZoneRadius;
    }

    // ================================================================================
    // 스태미너 Getters (v2.3)
    // ================================================================================
    
    /**
     * 현재 스태미너
     */
    public float getStamina() {
        return Math.max(0, stamina);
    }
    
    /**
     * 최대 스태미너
     */
    public float getMaxStamina() {
        return maxStamina > 0 ? maxStamina : 100f;
    }
    
    /**
     * 스태미너 비율 (0.0 ~ 1.0)
     */
    public float getStaminaRatio() {
        float max = getMaxStamina();
        float current = Math.max(0, Math.min(stamina, max));
        return current / max;
    }
    
    /**
     * 스태미너 텍스트
     */
    public String getStaminaText() {
        return String.format("%.0f/%.0f", getStamina(), getMaxStamina());
    }
    
    /**
     * 스태미너 경고 레벨 (0=정상, 1=경고(30% 이하), 2=위험(6% 이하))
     */
    public int getStaminaWarning() {
        return staminaWarning;
    }
    
    /**
     * 스태미너가 위험 수준인지 확인 (30% 이하)
     */
    public boolean isStaminaLow() {
        return getStaminaRatio() <= 0.30f || staminaWarning >= 1;
    }
    
    /**
     * 스태미너가 매우 위험한 수준인지 확인 (6% 이하)
     */
    public boolean isStaminaCritical() {
        return getStaminaRatio() <= 0.06f || staminaWarning >= 2;
    }
    
    // ================================================================================
    // 데이터 유효성 검증
    // ================================================================================
    
    /**
     * 데이터가 유효한지 확인
     */
    public boolean isValid() {
        return maxHealth > 0 && maxFoodLevel > 0;
    }
    
    // ================================================================================
    // [FIX] 로컬 데이터 동기화 - 즉시 반영을 위해 클라이언트 로컬 값 사용
    // ================================================================================
    
    /**
     * 로컬 플레이어 데이터로 체력/배고픔/갑옷/산소/경험치 업데이트
     * 서버 패킷 지연 문제 해결을 위해 매 프레임 렌더링 전 호출
     * 
     * <p>서버 데이터는 상태 효과, 커스텀 효과, 헌터 모드 등 로컬에서 알 수 없는 정보에만 사용</p>
     * 
     * @param localHealth 로컬 체력
     * @param localMaxHealth 로컬 최대 체력
     * @param localFood 로컬 배고픔
     * @param localMaxFood 로컬 최대 배고픔 (보통 20)
     * @param localSaturation 로컬 포만감
     * @param localArmor 로컬 방어력
     * @param localAir 로컬 산소
     * @param localMaxAir 로컬 최대 산소
     * @param localUnderwater 로컬 수중 여부
     * @param localLevel 로컬 레벨
     * @param localExpProgress 로컬 경험치 진행도
     * @param localAbsorption 로컬 흡수 체력
     */
    public void updateFromLocal(
            float localHealth, float localMaxHealth,
            int localFood, int localMaxFood, float localSaturation,
            int localArmor,
            int localAir, int localMaxAir, boolean localUnderwater,
            int localLevel, float localExpProgress,
            float localAbsorption
    ) {
        // 체력
        this.health = localHealth;
        this.maxHealth = localMaxHealth;
        
        // 배고픔
        this.foodLevel = localFood;
        this.maxFoodLevel = localMaxFood;
        this.saturation = localSaturation;
        
        // 방어력
        this.armor = localArmor;
        // maxArmor는 서버 값 유지 (기본 20)
        
        // 산소
        this.air = localAir;
        this.maxAir = localMaxAir;
        this.underwater = localUnderwater;
        
        // 경험치
        this.level = localLevel;
        this.expProgress = localExpProgress;
        
        // 흡수 체력
        this.absorptionAmount = localAbsorption;
    }
    
    @Override
    public String toString() {
        return String.format("VitalData[HP=%.0f/%.0f, Food=%d/%d, Armor=%d, Air=%d/%d, underwater=%s, effects=%d]",
                health, maxHealth, foodLevel, maxFoodLevel, armor, air, maxAir, underwater,
                statusEffects != null ? statusEffects.size() : 0);
    }
}
