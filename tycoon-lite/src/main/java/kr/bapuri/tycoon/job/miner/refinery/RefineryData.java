package kr.bapuri.tycoon.job.miner.refinery;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RefineryData - 정제소 데이터 (Stub)
 * 
 * [Phase 4.B] 정제소 데이터 구조 정의
 * 
 * <p>Lite에서는 비활성화. 향후 확장을 위한 데이터 구조만 정의.</p>
 */
public class RefineryData {
    
    private final UUID ownerUuid;
    
    // 정제소 레벨
    private int level = 1;
    
    // 업그레이드 슬롯 (부품)
    private boolean hasFan = false;
    private boolean hasHeater = false;
    
    // 현재 정제 중인 아이템
    private String currentOreId = null;
    private int currentAmount = 0;
    private long refineStartTime = 0;
    
    // 정제 완료된 아이템 (수집 대기)
    private final Map<String, Integer> completedItems = new HashMap<>();
    
    public RefineryData(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }
    
    // ===== Getters =====
    
    public UUID getOwnerUuid() {
        return ownerUuid;
    }
    
    public int getLevel() {
        return level;
    }
    
    public boolean hasFan() {
        return hasFan;
    }
    
    public boolean hasHeater() {
        return hasHeater;
    }
    
    public String getCurrentOreId() {
        return currentOreId;
    }
    
    public int getCurrentAmount() {
        return currentAmount;
    }
    
    public long getRefineStartTime() {
        return refineStartTime;
    }
    
    public Map<String, Integer> getCompletedItems() {
        return new HashMap<>(completedItems);
    }
    
    // ===== Setters =====
    
    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }
    
    public void equipFan() {
        this.hasFan = true;
    }
    
    public void equipHeater() {
        this.hasHeater = true;
    }
    
    // ===== 정제 로직 (Stub) =====
    
    /**
     * [STUB] 정제 시작
     */
    public boolean startRefining(String oreId, int amount) {
        if (currentOreId != null) {
            return false;  // 이미 정제 중
        }
        
        this.currentOreId = oreId;
        this.currentAmount = amount;
        this.refineStartTime = System.currentTimeMillis();
        return true;
    }
    
    /**
     * [STUB] 정제 완료 여부
     */
    public boolean isRefiningComplete() {
        if (currentOreId == null) return false;
        
        long elapsed = System.currentTimeMillis() - refineStartTime;
        long requiredTime = getRefineTime();
        
        return elapsed >= requiredTime;
    }
    
    /**
     * [STUB] 정제 시간 계산 (밀리초)
     */
    public long getRefineTime() {
        // 기본 5분, 업그레이드로 감소
        long baseTime = 5 * 60 * 1000L;
        
        double speedBonus = getTotalSpeedBonus();
        return (long) (baseTime / (1 + speedBonus));
    }
    
    /**
     * [STUB] 총 속도 보너스
     */
    public double getTotalSpeedBonus() {
        double bonus = 0.0;
        
        if (hasFan) bonus += 0.15;      // 팬: +15%
        if (hasHeater) bonus += 0.20;   // 히터: +20%
        bonus += (level - 1) * 0.05;    // 레벨당: +5%
        
        return Math.min(bonus, 0.50);   // 최대 +50%
    }
    
    /**
     * [STUB] 정제 완료 처리
     */
    public void completeRefining() {
        if (currentOreId == null) return;
        
        // 결과물 추가
        int outputAmount = calculateOutput();
        completedItems.merge(getOutputItemId(), outputAmount, Integer::sum);
        
        // 초기화
        currentOreId = null;
        currentAmount = 0;
        refineStartTime = 0;
    }
    
    private int calculateOutput() {
        // 기본: 입력량의 90%
        return (int) (currentAmount * 0.9);
    }
    
    private String getOutputItemId() {
        // [STUB] 원석 → 주괴 매핑
        return switch (currentOreId) {
            case "raw_iron" -> "iron_ingot";
            case "raw_copper" -> "copper_ingot";
            case "raw_gold" -> "gold_ingot";
            default -> currentOreId + "_refined";
        };
    }
    
    // ===== 직렬화 =====
    
    /**
     * [STUB] 직렬화
     */
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("level", level);
        data.put("hasFan", hasFan);
        data.put("hasHeater", hasHeater);
        data.put("currentOreId", currentOreId);
        data.put("currentAmount", currentAmount);
        data.put("refineStartTime", refineStartTime);
        data.put("completedItems", completedItems);
        return data;
    }
    
    /**
     * [STUB] 역직렬화
     */
    @SuppressWarnings("unchecked")
    public static RefineryData deserialize(UUID ownerUuid, Map<String, Object> data) {
        RefineryData refineryData = new RefineryData(ownerUuid);
        
        if (data == null) return refineryData;
        
        refineryData.level = (int) data.getOrDefault("level", 1);
        refineryData.hasFan = (boolean) data.getOrDefault("hasFan", false);
        refineryData.hasHeater = (boolean) data.getOrDefault("hasHeater", false);
        refineryData.currentOreId = (String) data.get("currentOreId");
        refineryData.currentAmount = (int) data.getOrDefault("currentAmount", 0);
        refineryData.refineStartTime = ((Number) data.getOrDefault("refineStartTime", 0L)).longValue();
        
        Map<String, Integer> completed = (Map<String, Integer>) data.get("completedItems");
        if (completed != null) {
            refineryData.completedItems.putAll(completed);
        }
        
        return refineryData;
    }
}
