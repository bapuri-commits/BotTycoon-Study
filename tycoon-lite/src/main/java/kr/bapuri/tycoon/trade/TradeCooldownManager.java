package kr.bapuri.tycoon.trade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TradeCooldownManager - 거래 요청 쿨타임 관리
 * 
 * 스팸 방지를 위해 두 가지 쿨타임 적용:
 * - 전체 쿨타임: 누구에게든 거래 요청 후 N초 대기
 * - 대상별 쿨타임: 같은 사람에게 거래 요청 후 M초 대기
 */
public class TradeCooldownManager {

    // 전체 쿨타임 (playerUUID -> 마지막 요청 시간)
    private final Map<UUID, Long> globalCooldowns = new ConcurrentHashMap<>();
    
    // 대상별 쿨타임 (senderUUID:targetUUID -> 마지막 요청 시간)
    private final Map<String, Long> perTargetCooldowns = new ConcurrentHashMap<>();
    
    // 설정값 (초 단위)
    private long globalCooldownSeconds = 60;
    private long perTargetCooldownSeconds = 120;
    
    public TradeCooldownManager() {
    }
    
    public void setGlobalCooldown(long seconds) {
        this.globalCooldownSeconds = seconds;
    }
    
    public void setPerTargetCooldown(long seconds) {
        this.perTargetCooldownSeconds = seconds;
    }
    
    /**
     * 거래 요청 가능 여부 확인
     * @return 남은 쿨타임 (초). 0이면 요청 가능
     */
    public long checkCooldown(UUID senderId, UUID targetId) {
        long now = System.currentTimeMillis();
        
        // 전체 쿨타임 체크
        Long lastGlobal = globalCooldowns.get(senderId);
        if (lastGlobal != null) {
            long elapsedSeconds = (now - lastGlobal) / 1000;
            if (elapsedSeconds < globalCooldownSeconds) {
                return globalCooldownSeconds - elapsedSeconds;
            }
        }
        
        // 대상별 쿨타임 체크
        String key = makeKey(senderId, targetId);
        Long lastPerTarget = perTargetCooldowns.get(key);
        if (lastPerTarget != null) {
            long elapsedSeconds = (now - lastPerTarget) / 1000;
            if (elapsedSeconds < perTargetCooldownSeconds) {
                return perTargetCooldownSeconds - elapsedSeconds;
            }
        }
        
        return 0;
    }
    
    public void recordRequest(UUID senderId, UUID targetId) {
        long now = System.currentTimeMillis();
        globalCooldowns.put(senderId, now);
        perTargetCooldowns.put(makeKey(senderId, targetId), now);
    }
    
    public void clearCooldown(UUID playerId) {
        globalCooldowns.remove(playerId);
        String prefix = playerId.toString() + ":";
        perTargetCooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    public void clearAll() {
        globalCooldowns.clear();
        perTargetCooldowns.clear();
    }
    
    public void cleanup() {
        long now = System.currentTimeMillis();
        long maxAge = Math.max(globalCooldownSeconds, perTargetCooldownSeconds) * 1000 + 60000;
        
        globalCooldowns.entrySet().removeIf(entry -> (now - entry.getValue()) > maxAge);
        perTargetCooldowns.entrySet().removeIf(entry -> (now - entry.getValue()) > maxAge);
    }
    
    private String makeKey(UUID senderId, UUID targetId) {
        return senderId.toString() + ":" + targetId.toString();
    }
    
    public long getGlobalCooldownSeconds() {
        return globalCooldownSeconds;
    }
    
    public long getPerTargetCooldownSeconds() {
        return perTargetCooldownSeconds;
    }
}
