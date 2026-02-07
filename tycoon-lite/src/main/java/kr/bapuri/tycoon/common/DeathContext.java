package kr.bapuri.tycoon.common;

import kr.bapuri.tycoon.world.WorldType;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * DeathContext - 사망 컨텍스트 (확장점)
 * 
 * 사망 상황에 대한 모든 메타데이터를 담는 객체.
 * 향후 간접 킬/비난 추적 등을 위해 확장 가능.
 */
public class DeathContext {

    private final UUID playerId;
    private final String playerName;
    private final WorldType worldType;
    private final Location deathLocation;
    private final long timestamp;
    
    // 사망 원인 분류
    private final DeathCause cause;
    
    // PvP 관련 (옵션)
    private UUID killerId;        // PvP 킬러 UUID (null이면 PvP 아님)
    private String killerName;
    
    // 특수 상황
    private boolean wasVoidDeath;
    private boolean wasTicketProtected;
    
    // 고유 이벤트 ID (중복 처리 방지용)
    private final String eventId;

    public DeathContext(Player player, WorldType worldType, DeathCause cause) {
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.worldType = worldType;
        this.deathLocation = player.getLocation().clone();
        this.timestamp = System.currentTimeMillis();
        this.cause = cause;
        this.eventId = generateEventId();
    }

    private String generateEventId() {
        return playerId.toString().substring(0, 8) + "_" + timestamp;
    }

    // ===== Getters =====

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public WorldType getWorldType() { return worldType; }
    public Location getDeathLocation() { return deathLocation; }
    public long getTimestamp() { return timestamp; }
    public DeathCause getCause() { return cause; }
    public UUID getKillerId() { return killerId; }
    public String getKillerName() { return killerName; }
    public boolean isWasVoidDeath() { return wasVoidDeath; }
    public boolean isWasTicketProtected() { return wasTicketProtected; }
    public String getEventId() { return eventId; }

    // ===== Setters (Builder-like) =====

    public DeathContext withKiller(Player killer) {
        if (killer != null) {
            this.killerId = killer.getUniqueId();
            this.killerName = killer.getName();
        }
        return this;
    }

    public DeathContext withKiller(UUID killerId, String killerName) {
        this.killerId = killerId;
        this.killerName = killerName;
        return this;
    }

    public DeathContext markVoidDeath() {
        this.wasVoidDeath = true;
        return this;
    }

    public DeathContext markTicketProtected() {
        this.wasTicketProtected = true;
        return this;
    }

    // ===== Utility =====

    public boolean isPvpDeath() {
        return killerId != null;
    }

    public boolean isWildPvpDeath() {
        return worldType == WorldType.WILD && isPvpDeath();
    }

    /**
     * 사망 원인 분류
     */
    public enum DeathCause {
        ENVIRONMENT,   // 환경 (낙사, 익사, 용암 등)
        MOB,          // 몹에 의한 사망
        PVP,          // 다른 플레이어에 의한 사망
        VOID,         // 보이드 사망
        SUICIDE,      // 자살 (/kill 등)
        UNKNOWN       // 알 수 없음
    }

    @Override
    public String toString() {
        return "DeathContext{" +
                "player=" + playerName +
                ", world=" + worldType +
                ", cause=" + cause +
                ", killer=" + (killerName != null ? killerName : "none") +
                ", eventId=" + eventId +
                '}';
    }
}

