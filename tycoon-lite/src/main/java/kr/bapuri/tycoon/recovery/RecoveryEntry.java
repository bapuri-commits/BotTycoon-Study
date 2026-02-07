package kr.bapuri.tycoon.recovery;

import kr.bapuri.tycoon.common.PenaltyReason;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * RecoveryEntry - Town Recovery Storage의 개별 엔트리
 * 
 * [Phase 8] 레거시에서 이식
 * 
 * 데스 체스트(AngelChest) 만료 또는 보이드 사망 시 아이템을 저장하는 단위.
 * YAML로 직렬화 가능.
 */
public class RecoveryEntry implements ConfigurationSerializable {

    private final String entryId;          // 고유 ID (중복 처리 방지)
    private final UUID ownerUUID;
    private final long createdAt;          // 생성 시간 (millis)
    private final PenaltyReason reason;    // 저장 이유
    private String sourceEventId;          // 외부 이벤트 ID (중복 transfer 방지, 재시작 후에도 유지)
    
    // 사망 위치 정보 (옵션)
    private final String deathWorld;
    private final double deathX;
    private final double deathY;
    private final double deathZ;
    
    // PvP 정보 (옵션 - 무료 회수 판단용)
    private UUID killerUUID;
    private String killerName;
    
    // 저장된 아이템
    private final List<ItemStack> items;
    
    // 처리 상태
    private boolean claimed = false;
    private long claimedAt = 0;

    /**
     * 새 엔트리 생성
     */
    public RecoveryEntry(UUID ownerUUID, PenaltyReason reason, Location deathLocation, List<ItemStack> items) {
        this.entryId = generateEntryId(ownerUUID);
        this.ownerUUID = ownerUUID;
        this.createdAt = System.currentTimeMillis();
        this.reason = reason;
        
        if (deathLocation != null && deathLocation.getWorld() != null) {
            this.deathWorld = deathLocation.getWorld().getName();
            this.deathX = deathLocation.getX();
            this.deathY = deathLocation.getY();
            this.deathZ = deathLocation.getZ();
        } else {
            this.deathWorld = null;
            this.deathX = 0;
            this.deathY = 0;
            this.deathZ = 0;
        }
        
        this.items = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null) {
                    this.items.add(item.clone());
                }
            }
        }
    }

    /**
     * YAML에서 복원용 생성자
     */
    private RecoveryEntry(String entryId, UUID ownerUUID, long createdAt, PenaltyReason reason,
                          String deathWorld, double deathX, double deathY, double deathZ,
                          UUID killerUUID, String killerName, String sourceEventId,
                          List<ItemStack> items, boolean claimed, long claimedAt) {
        this.entryId = entryId;
        this.ownerUUID = ownerUUID;
        this.createdAt = createdAt;
        this.reason = reason;
        this.deathWorld = deathWorld;
        this.deathX = deathX;
        this.deathY = deathY;
        this.deathZ = deathZ;
        this.killerUUID = killerUUID;
        this.killerName = killerName;
        this.sourceEventId = sourceEventId;
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.claimed = claimed;
        this.claimedAt = claimedAt;
    }

    private static String generateEntryId(UUID ownerUUID) {
        return ownerUUID.toString().substring(0, 8) + "_" + System.currentTimeMillis();
    }

    // ===== Getters =====

    public String getEntryId() { return entryId; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public long getCreatedAt() { return createdAt; }
    public PenaltyReason getReason() { return reason; }
    public String getDeathWorld() { return deathWorld; }
    public double getDeathX() { return deathX; }
    public double getDeathY() { return deathY; }
    public double getDeathZ() { return deathZ; }
    public UUID getKillerUUID() { return killerUUID; }
    public String getKillerName() { return killerName; }
    public String getSourceEventId() { return sourceEventId; }
    public List<ItemStack> getItems() { return new ArrayList<>(items); }
    public boolean isClaimed() { return claimed; }
    public long getClaimedAt() { return claimedAt; }

    // ===== Setters =====

    public RecoveryEntry withKiller(UUID killerUUID, String killerName) {
        this.killerUUID = killerUUID;
        this.killerName = killerName;
        return this;
    }

    public RecoveryEntry withSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
        return this;
    }

    public void markClaimed() {
        this.claimed = true;
        this.claimedAt = System.currentTimeMillis();
    }

    // ===== Utility =====

    /**
     * PvP 사망인지 (무료 회수 대상)
     */
    public boolean isPvpDeath() {
        return killerUUID != null;
    }

    /**
     * 무료 회수 가능한지
     */
    public boolean isFreeClaim() {
        return reason != null && reason.isFreeClaim();
    }

    /**
     * 아이템 총 개수
     */
    public int getTotalItemCount() {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * 사망 위치 문자열 (표시용)
     */
    public String getDeathLocationString() {
        if (deathWorld == null) return "알 수 없음";
        return String.format("%s (%.0f, %.0f, %.0f)", deathWorld, deathX, deathY, deathZ);
    }

    /**
     * 경과 시간 문자열 (표시용)
     */
    public String getAgeString() {
        long elapsed = System.currentTimeMillis() - createdAt;
        long minutes = elapsed / (60 * 1000);
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) return days + "일 전";
        if (hours > 0) return hours + "시간 전";
        if (minutes > 0) return minutes + "분 전";
        return "방금 전";
    }

    // ===== ConfigurationSerializable =====

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entryId", entryId);
        map.put("ownerUUID", ownerUUID.toString());
        map.put("createdAt", createdAt);
        map.put("reason", reason.name());
        
        if (deathWorld != null) {
            map.put("deathWorld", deathWorld);
            map.put("deathX", deathX);
            map.put("deathY", deathY);
            map.put("deathZ", deathZ);
        }
        
        if (killerUUID != null) {
            map.put("killerUUID", killerUUID.toString());
            map.put("killerName", killerName);
        }
        
        if (sourceEventId != null) {
            map.put("sourceEventId", sourceEventId);
        }
        
        map.put("items", items);
        map.put("claimed", claimed);
        map.put("claimedAt", claimedAt);
        
        return map;
    }

    @SuppressWarnings("unchecked")
    public static RecoveryEntry deserialize(Map<String, Object> map) {
        String entryId = (String) map.get("entryId");
        UUID ownerUUID = UUID.fromString((String) map.get("ownerUUID"));
        long createdAt = ((Number) map.get("createdAt")).longValue();
        
        PenaltyReason reason;
        try {
            reason = PenaltyReason.valueOf((String) map.get("reason"));
        } catch (Exception e) {
            reason = PenaltyReason.UNKNOWN;
        }
        
        String deathWorld = (String) map.get("deathWorld");
        double deathX = map.containsKey("deathX") ? ((Number) map.get("deathX")).doubleValue() : 0;
        double deathY = map.containsKey("deathY") ? ((Number) map.get("deathY")).doubleValue() : 0;
        double deathZ = map.containsKey("deathZ") ? ((Number) map.get("deathZ")).doubleValue() : 0;
        
        UUID killerUUID = map.containsKey("killerUUID") ? UUID.fromString((String) map.get("killerUUID")) : null;
        String killerName = (String) map.get("killerName");
        String sourceEventId = (String) map.get("sourceEventId");
        
        List<ItemStack> items = (List<ItemStack>) map.get("items");
        boolean claimed = map.containsKey("claimed") && (boolean) map.get("claimed");
        long claimedAt = map.containsKey("claimedAt") ? ((Number) map.get("claimedAt")).longValue() : 0;
        
        return new RecoveryEntry(entryId, ownerUUID, createdAt, reason,
                deathWorld, deathX, deathY, deathZ,
                killerUUID, killerName, sourceEventId, items, claimed, claimedAt);
    }

    @Override
    public String toString() {
        return "RecoveryEntry{" +
                "id=" + entryId +
                ", reason=" + reason +
                ", items=" + getTotalItemCount() +
                ", age=" + getAgeString() +
                (claimed ? ", claimed" : "") +
                '}';
    }
}
