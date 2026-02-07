package kr.bapuri.tycoon.common;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * ChanneledAction - 채널링 액션 데이터
 * 
 * 일정 시간 동안 가만히 있어야 실행되는 액션을 표현합니다.
 * 피격, 이동, 수동 취소 시 채널링이 취소됩니다.
 */
public class ChanneledAction {

    private final UUID playerUuid;
    private final String actionId;           // 액션 식별자 (예: "hunter_exit", "teleport")
    private final String displayName;        // 표시명
    private final long startTime;            // 시작 시간 (millis)
    private final int durationSeconds;       // 채널링 시간 (초)
    private final Location startLocation;    // 시작 위치 (이동 감지용)
    
    private final Consumer<Player> onComplete;  // 완료 시 콜백
    private final Consumer<Player> onCancel;    // 취소 시 콜백
    
    private boolean cancelled = false;
    private int taskId = -1;                 // BukkitScheduler Task ID

    public ChanneledAction(Player player, String actionId, String displayName, 
                           int durationSeconds, Consumer<Player> onComplete, Consumer<Player> onCancel) {
        this.playerUuid = player.getUniqueId();
        this.actionId = actionId;
        this.displayName = displayName;
        this.startTime = System.currentTimeMillis();
        this.durationSeconds = durationSeconds;
        this.startLocation = player.getLocation().clone();
        this.onComplete = onComplete;
        this.onCancel = onCancel;
    }

    // ========== Getters ==========

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getActionId() {
        return actionId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getStartTime() {
        return startTime;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public Consumer<Player> getOnComplete() {
        return onComplete;
    }

    public Consumer<Player> getOnCancel() {
        return onCancel;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    // ========== 유틸리티 ==========

    /**
     * 남은 채널링 시간 (초)
     */
    public int getRemainingSeconds() {
        long elapsed = System.currentTimeMillis() - startTime;
        int remaining = durationSeconds - (int)(elapsed / 1000);
        return Math.max(0, remaining);
    }

    /**
     * 채널링 완료 시간이 지났는지
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= startTime + (durationSeconds * 1000L);
    }

    /**
     * 플레이어가 시작 위치에서 이동했는지
     * @param tolerance 허용 오차 (블록 단위)
     */
    public boolean hasMovedFrom(Location currentLocation, double tolerance) {
        if (startLocation == null || currentLocation == null) return true;
        if (!startLocation.getWorld().equals(currentLocation.getWorld())) return true;
        
        double dx = currentLocation.getX() - startLocation.getX();
        double dy = currentLocation.getY() - startLocation.getY();
        double dz = currentLocation.getZ() - startLocation.getZ();
        
        return Math.abs(dx) > tolerance || Math.abs(dy) > tolerance || Math.abs(dz) > tolerance;
    }
}

