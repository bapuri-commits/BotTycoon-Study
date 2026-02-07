package kr.bapuri.tycoon.trade;

import java.util.UUID;

/**
 * TradeRequest - 거래 요청 데이터
 * 
 * 상대방의 수락을 기다리는 대기 중인 거래 요청
 */
public class TradeRequest {

    private final UUID senderId;
    private final UUID targetId;
    private final String senderName;
    private final String targetName;
    private final long createdAt;
    private final long expiresAt;
    
    public TradeRequest(UUID senderId, UUID targetId, String senderName, String targetName, long timeoutSeconds) {
        this.senderId = senderId;
        this.targetId = targetId;
        this.senderName = senderName;
        this.targetName = targetName;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + (timeoutSeconds * 1000);
    }
    
    public UUID getSenderId() {
        return senderId;
    }
    
    public UUID getTargetId() {
        return targetId;
    }
    
    public String getSenderName() {
        return senderName;
    }
    
    public String getTargetName() {
        return targetName;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getExpiresAt() {
        return expiresAt;
    }
    
    /**
     * 만료 여부 확인
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
    
    /**
     * 남은 시간 (초)
     */
    public long getRemainingSeconds() {
        long remaining = (expiresAt - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }
}
