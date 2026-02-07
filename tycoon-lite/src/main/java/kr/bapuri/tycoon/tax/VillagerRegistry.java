package kr.bapuri.tycoon.tax;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * VillagerRegistry - 마을별 주민 등록 관리
 * 
 * 기능:
 * - 마을별 주민 수 등록/제거 (관리자 전용)
 * - Frozen(정지) 상태 관리
 * - YAML 파일 저장/로드
 */
public class VillagerRegistry {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final File dataFile;

    // landName -> LandTaxData
    private final Map<String, LandTaxData> landData;

    public VillagerRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFile = new File(plugin.getDataFolder(), "data/land_villagers.yml");
        this.landData = new ConcurrentHashMap<>();
    }

    /**
     * 데이터 로드
     */
    public void load() {
        landData.clear();

        if (!dataFile.exists()) {
            logger.info("[VillagerRegistry] 데이터 파일이 없습니다. 새로 생성됩니다.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection landsSection = config.getConfigurationSection("lands");

        if (landsSection == null) {
            return;
        }

        for (String landName : landsSection.getKeys(false)) {
            ConfigurationSection landSection = landsSection.getConfigurationSection(landName);
            if (landSection == null) continue;

            String ownerUuidStr = landSection.getString("owner", "");
            UUID ownerUuid = null;
            if (!ownerUuidStr.isEmpty()) {
                try {
                    ownerUuid = UUID.fromString(ownerUuidStr);
                } catch (IllegalArgumentException ignored) {}
            }

            int villagers = landSection.getInt("villagers", 0);
            boolean frozen = landSection.getBoolean("frozen", false);
            long lastPaid = landSection.getLong("lastPaid", 0);
            long lastLifetimeSnapshot = landSection.getLong("lastLifetimeSnapshot", 0);

            LandTaxData data = new LandTaxData(landName, ownerUuid, villagers, frozen, lastPaid, lastLifetimeSnapshot);
            landData.put(landName.toLowerCase(), data);
        }

        logger.info("[VillagerRegistry] " + landData.size() + "개 마을 데이터 로드됨");
    }

    /**
     * 데이터 저장
     */
    public void save() {
        YamlConfiguration config = new YamlConfiguration();

        for (LandTaxData data : landData.values()) {
            String path = "lands." + data.getLandName();
            config.set(path + ".owner", data.getOwnerUuid() != null ? data.getOwnerUuid().toString() : "");
            config.set(path + ".villagers", data.getVillagers());
            config.set(path + ".frozen", data.isFrozen());
            config.set(path + ".lastPaid", data.getLastPaid());
            config.set(path + ".lastLifetimeSnapshot", data.getLastLifetimeSnapshot());
        }

        try {
            // 디렉토리 생성
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            config.save(dataFile);
        } catch (IOException e) {
            logger.severe("[VillagerRegistry] 데이터 저장 실패: " + e.getMessage());
        }
    }

    // ===== 주민 관리 =====

    /**
     * 주민 등록
     * 
     * @param landName 마을 이름
     * @param count 추가할 주민 수
     * @param ownerUuid 마을 소유자 UUID (없으면 null)
     * @return 등록 후 총 주민 수
     */
    public int addVillagers(String landName, int count, UUID ownerUuid) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.computeIfAbsent(key, k -> 
            new LandTaxData(landName, ownerUuid, 0, false, 0, 0));
        
        data.setVillagers(data.getVillagers() + count);
        if (ownerUuid != null) {
            data.setOwnerUuid(ownerUuid);
        }
        
        save();
        return data.getVillagers();
    }

    /**
     * 주민 제거
     * 
     * @param landName 마을 이름
     * @param count 제거할 주민 수
     * @return 제거 후 총 주민 수 (-1이면 마을 없음)
     */
    public int removeVillagers(String landName, int count) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.get(key);
        
        if (data == null) {
            return -1;
        }
        
        int newCount = Math.max(0, data.getVillagers() - count);
        data.setVillagers(newCount);
        
        save();
        return newCount;
    }

    /**
     * 주민 수 조회
     */
    public int getVillagers(String landName) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.get(key);
        return data != null ? data.getVillagers() : 0;
    }

    // ===== Frozen 관리 =====

    /**
     * 마을 정지
     */
    public void freeze(String landName) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.get(key);
        
        if (data != null) {
            data.setFrozen(true);
            save();
        }
    }

    /**
     * 마을 정지 해제
     */
    public void unfreeze(String landName) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.get(key);
        
        if (data != null) {
            data.setFrozen(false);
            save();
        }
    }

    /**
     * 정지 상태 확인
     */
    public boolean isFrozen(String landName) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.get(key);
        return data != null && data.isFrozen();
    }

    // ===== 세금 납부 기록 =====

    /**
     * 마지막 납부 시간 기록
     */
    public void recordPayment(String landName) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.get(key);
        
        if (data != null) {
            data.setLastPaid(System.currentTimeMillis());
            save();
        }
    }

    /**
     * 마지막 납부 시간 조회
     */
    public long getLastPaid(String landName) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.get(key);
        return data != null ? data.getLastPaid() : 0;
    }

    // ===== 소득 스냅샷 =====

    /**
     * 소득 스냅샷 저장
     */
    public void setLifetimeSnapshot(String landName, long snapshot) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.get(key);
        
        if (data != null) {
            data.setLastLifetimeSnapshot(snapshot);
        }
    }

    /**
     * 소득 스냅샷 조회
     */
    public long getLifetimeSnapshot(String landName) {
        String key = landName.toLowerCase();
        LandTaxData data = landData.get(key);
        return data != null ? data.getLastLifetimeSnapshot() : 0;
    }

    // ===== 유틸리티 =====

    /**
     * 모든 마을 데이터 조회
     */
    public Collection<LandTaxData> getAllLandData() {
        return Collections.unmodifiableCollection(landData.values());
    }

    /**
     * 마을 데이터 조회
     */
    public Optional<LandTaxData> getLandData(String landName) {
        return Optional.ofNullable(landData.get(landName.toLowerCase()));
    }

    /**
     * 마을 데이터 생성/업데이트
     */
    public LandTaxData getOrCreate(String landName, UUID ownerUuid) {
        String key = landName.toLowerCase();
        return landData.computeIfAbsent(key, k -> 
            new LandTaxData(landName, ownerUuid, 0, false, 0, 0));
    }

    /**
     * 종료 시 저장
     */
    public void shutdown() {
        save();
    }

    // ===== 데이터 클래스 =====

    /**
     * 마을 세금 데이터
     */
    public static class LandTaxData {
        private final String landName;
        private UUID ownerUuid;
        private int villagers;
        private boolean frozen;
        private long lastPaid;
        private long lastLifetimeSnapshot;

        public LandTaxData(String landName, UUID ownerUuid, int villagers, 
                          boolean frozen, long lastPaid, long lastLifetimeSnapshot) {
            this.landName = landName;
            this.ownerUuid = ownerUuid;
            this.villagers = villagers;
            this.frozen = frozen;
            this.lastPaid = lastPaid;
            this.lastLifetimeSnapshot = lastLifetimeSnapshot;
        }

        public String getLandName() { return landName; }
        public UUID getOwnerUuid() { return ownerUuid; }
        public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }
        public int getVillagers() { return villagers; }
        public void setVillagers(int villagers) { this.villagers = villagers; }
        public boolean isFrozen() { return frozen; }
        public void setFrozen(boolean frozen) { this.frozen = frozen; }
        public long getLastPaid() { return lastPaid; }
        public void setLastPaid(long lastPaid) { this.lastPaid = lastPaid; }
        public long getLastLifetimeSnapshot() { return lastLifetimeSnapshot; }
        public void setLastLifetimeSnapshot(long lastLifetimeSnapshot) { 
            this.lastLifetimeSnapshot = lastLifetimeSnapshot; 
        }
    }
}
