package kr.bapuri.tycoon.world;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * WorldManager - 월드 타입 관리 및 월드 설정
 * 
 * config.yml의 worlds 섹션을 읽어서 Bukkit World와 WorldType을 매핑합니다.
 * 
 * 기능:
 * - 월드 타입 매핑 (WorldType <-> Bukkit World)
 * - Wild 연결 차원 관리 (네더/엔드)
 * - PvP 데미지 배율 관리
 * - Town 포탈 격리 및 몹 스폰 차단
 */
public class WorldManager {

    private final Plugin plugin;
    private final Logger logger;
    
    // 월드 이름 -> WorldType 매핑
    private final Map<String, WorldType> worldNameToType = new HashMap<>();
    
    // WorldType -> Bukkit World 매핑 (캐시)
    private final Map<WorldType, World> typeToWorld = new EnumMap<>(WorldType.class);
    
    // WorldType -> 월드 이름 매핑 (config 기반)
    private final Map<WorldType, String> typeToWorldName = new EnumMap<>(WorldType.class);
    
    // Wild 연결 차원 이름
    private String wildNetherName;
    private String wildEndName;
    
    // PvP 데미지 배율 (런타임 토글 가능)
    private double pvpDamageMultiplier = 0.1; // 기본 10%
    
    public WorldManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = Logger.getLogger("TycoonLite.WorldManager");
        reload();
    }
    
    /**
     * config.yml에서 월드 설정 리로드
     */
    public void reload() {
        worldNameToType.clear();
        typeToWorld.clear();
        typeToWorldName.clear();
        
        ConfigurationSection worldsSection = plugin.getConfig().getConfigurationSection("worlds");
        if (worldsSection == null) {
            logger.warning("config.yml에 worlds 섹션이 없습니다.");
            return;
        }
        
        int loaded = 0;
        for (String key : worldsSection.getKeys(false)) {
            WorldType type = WorldType.fromConfigKey(key);
            if (type == null) {
                // Lite에서 사용하지 않는 월드 타입 (hunter, duel 등)은 무시
                continue;
            }
            
            String worldName = worldsSection.getString(key + ".name");
            if (worldName == null || worldName.isEmpty()) {
                logger.warning("월드 이름 누락: worlds." + key + ".name");
                continue;
            }
            
            worldNameToType.put(worldName, type);
            typeToWorldName.put(type, worldName);
            loaded++;
            
            // Bukkit World 캐시 (서버 시작 시점에는 null일 수 있음)
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                typeToWorld.put(type, world);
            }
        }
        
        // Wild 연결 차원 로드
        wildNetherName = worldsSection.getString("wild.nether", "world_wild_nether");
        wildEndName = worldsSection.getString("wild.the_end", "world_wild_the_end");
        
        // 연결 차원도 매핑에 추가 (WILD 타입으로)
        worldNameToType.put(wildNetherName, WorldType.WILD);
        worldNameToType.put(wildEndName, WorldType.WILD);
        
        // PvP 데미지 배율 로드
        pvpDamageMultiplier = worldsSection.getDouble("wild.pvpDamageMultiplier", 0.1);
        
        logger.info("월드 설정 로드 완료: " + loaded + "개 (Wild 연결 차원: " + wildNetherName + ", " + wildEndName + ")");
    }
    
    /**
     * 월드 설정 적용 (서버 시작 후 호출)
     * WorldType enum의 기본값 + config.yml 오버라이드 적용
     */
    public void applyWorldSettings() {
        // 각 WorldType에 대해 설정 적용
        for (WorldType type : WorldType.values()) {
            getWorld(type).ifPresent(world -> applyWorldTypeSettings(world, type));
        }
        
        // Wild 연결 차원 (네더/엔드) - Wild 설정 상속
        applyLinkedDimensionSettings();
    }
    
    /**
     * 개별 월드에 WorldType 설정 적용
     */
    private void applyWorldTypeSettings(World world, WorldType type) {
        String configBase = "worlds." + type.getConfigKey() + ".";
        
        // 난이도 (config 오버라이드 가능)
        String difficultyStr = plugin.getConfig().getString(configBase + "difficulty");
        Difficulty difficulty = difficultyStr != null ? 
            parseDifficulty(difficultyStr) : type.getDefaultDifficulty();
        world.setDifficulty(difficulty);
        
        // PvP
        boolean pvp = plugin.getConfig().getBoolean(configBase + "pvp", type.isDefaultPvp());
        world.setPVP(pvp);
        
        // 몹/동물 스폰
        boolean mobSpawn = plugin.getConfig().getBoolean(configBase + "mobSpawning", type.isDefaultMobSpawning());
        boolean animalSpawn = plugin.getConfig().getBoolean(configBase + "animalSpawning", type.isDefaultAnimalSpawning());
        world.setGameRule(GameRule.DO_MOB_SPAWNING, mobSpawn);
        world.setSpawnFlags(mobSpawn, animalSpawn);
        
        // 로그
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type.getDisplayName()).append("] ").append(world.getName());
        sb.append(": ").append(difficulty.name());
        sb.append(", PvP ").append(pvp ? "ON" : "OFF");
        sb.append(", 몹 ").append(mobSpawn ? "ON" : "OFF");
        sb.append(", 동물 ").append(animalSpawn ? "ON" : "OFF");
        
        if (type == WorldType.WILD && pvp) {
            sb.append(" (데미지 ").append((int)(pvpDamageMultiplier * 100)).append("%)");
        }
        
        logger.info(sb.toString());
    }
    
    /**
     * Wild 연결 차원 설정 (네더/엔드)
     * Wild 설정을 상속받음
     */
    private void applyLinkedDimensionSettings() {
        WorldType wildType = WorldType.WILD;
        Difficulty wildDifficulty = wildType.getDefaultDifficulty();
        boolean wildPvp = plugin.getConfig().getBoolean("worlds.wild.pvp", wildType.isDefaultPvp());
        
        // Wild 네더
        World wildNether = Bukkit.getWorld(wildNetherName);
        if (wildNether != null) {
            wildNether.setDifficulty(wildDifficulty);
            wildNether.setPVP(wildPvp);
            logger.info("[야생 네더] " + wildNether.getName() + ": " + wildDifficulty.name() + ", PvP " + (wildPvp ? "ON" : "OFF"));
        }
        
        // Wild 엔드
        World wildEnd = Bukkit.getWorld(wildEndName);
        if (wildEnd != null) {
            wildEnd.setDifficulty(wildDifficulty);
            wildEnd.setPVP(wildPvp);
            logger.info("[야생 엔드] " + wildEnd.getName() + ": " + wildDifficulty.name() + ", PvP " + (wildPvp ? "ON" : "OFF"));
        }
    }
    
    /**
     * 문자열을 Difficulty로 파싱
     */
    private Difficulty parseDifficulty(String str) {
        try {
            return Difficulty.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("잘못된 난이도 값: " + str + " (PEACEFUL, EASY, NORMAL, HARD 중 선택)");
            return Difficulty.NORMAL;
        }
    }
    
    /**
     * Wild 월드 및 연결된 차원 로드 (본체/네더/엔드)
     * 이미 존재하면 로드, 없으면 생성
     * 
     * [Fix] generateStructures(true) 명시적 설정으로 구조물(엔더드래곤 등) 생성 보장
     */
    public void loadLinkedDimensions() {
        // Wild 본체 월드 먼저 로드
        String wildWorldName = typeToWorldName.get(WorldType.WILD);
        if (wildWorldName != null && Bukkit.getWorld(wildWorldName) == null) {
            WorldCreator creator = new WorldCreator(wildWorldName);
            creator.environment(World.Environment.NORMAL);
            creator.generateStructures(true);
            Bukkit.createWorld(creator);
            logger.info("Wild 월드 생성/로드: " + wildWorldName);
        }
        
        // Wild 네더
        if (Bukkit.getWorld(wildNetherName) == null) {
            WorldCreator creator = new WorldCreator(wildNetherName);
            creator.environment(World.Environment.NETHER);
            creator.generateStructures(true);
            Bukkit.createWorld(creator);
            logger.info("Wild 네더 월드 생성/로드: " + wildNetherName);
        }
        
        // Wild 엔드
        if (Bukkit.getWorld(wildEndName) == null) {
            WorldCreator creator = new WorldCreator(wildEndName);
            creator.environment(World.Environment.THE_END);
            creator.generateStructures(true);  // 엔더드래곤 스폰 보장
            Bukkit.createWorld(creator);
            logger.info("Wild 엔드 월드 생성/로드: " + wildEndName);
        }
    }
    
    /**
     * Bukkit World -> WorldType
     * @return WorldType 또는 null (알 수 없는 월드)
     */
    public WorldType getWorldType(World world) {
        if (world == null) return null;
        return worldNameToType.get(world.getName());
    }
    
    /**
     * 월드 이름 -> WorldType
     */
    public WorldType getWorldType(String worldName) {
        return worldNameToType.get(worldName);
    }
    
    /**
     * WorldType -> Bukkit World (Optional)
     */
    public Optional<World> getWorld(WorldType type) {
        // 캐시된 월드 확인
        World cached = typeToWorld.get(type);
        if (cached != null) {
            return Optional.of(cached);
        }
        
        // 월드 이름으로 찾기
        String worldName = typeToWorldName.get(type);
        if (worldName == null) {
            return Optional.empty();
        }
        
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            typeToWorld.put(type, world);
        }
        return Optional.ofNullable(world);
    }
    
    /**
     * 월드 이름 반환 (config 기반)
     */
    public String getWorldName(WorldType type) {
        return typeToWorldName.get(type);
    }
    
    // ===== 편의 메서드 =====
    
    public boolean isTownWorld(World world) {
        return getWorldType(world) == WorldType.TOWN;
    }
    
    public boolean isWildWorld(World world) {
        return getWorldType(world) == WorldType.WILD;
    }
    
    // [DROP] isHunterWorld, isDuelWorld, isDungeonWorld 제거됨 (Phase 1.5)
    
    /**
     * Wild 연결 월드인지 확인 (네더 또는 엔드)
     */
    public boolean isWildLinkedWorld(World world) {
        if (world == null) return false;
        String name = world.getName();
        return name.equals(wildNetherName) || name.equals(wildEndName);
    }
    
    /**
     * 포탈 격리가 필요한 월드인지 확인
     * WorldType 기본값 + config 오버라이드
     */
    public boolean isPortalIsolated(World world) {
        WorldType type = getWorldType(world);
        if (type == null) return false;
        
        return plugin.getConfig().getBoolean(
            "worlds." + type.getConfigKey() + ".portalIsolation",
            type.isDefaultPortalIsolation()
        );
    }
    
    /**
     * PvP가 가능한 월드인지 확인
     */
    public boolean isPvpWorld(World world) {
        WorldType type = getWorldType(world);
        if (type == null) return false;
        
        // config에서 오버라이드 확인
        Boolean configPvp = plugin.getConfig().getBoolean(
            "worlds." + type.getConfigKey() + ".pvp", 
            type.isDefaultPvp()
        );
        return configPvp;
    }
    
    /**
     * keepInventory가 활성화된 월드인지 확인
     */
    public boolean isKeepInventoryWorld(World world) {
        WorldType type = getWorldType(world);
        if (type == null) return false;
        
        return plugin.getConfig().getBoolean(
            "worlds." + type.getConfigKey() + ".keepInventory",
            type.isDefaultKeepInventory()
        );
    }
    
    /**
     * 몹 스폰이 활성화된 월드인지 확인
     */
    public boolean isMobSpawningWorld(World world) {
        WorldType type = getWorldType(world);
        if (type == null) return true; // 알 수 없는 월드는 기본 ON
        
        return plugin.getConfig().getBoolean(
            "worlds." + type.getConfigKey() + ".mobSpawning",
            type.isDefaultMobSpawning()
        );
    }
    
    /**
     * 동물 스폰이 활성화된 월드인지 확인
     */
    public boolean isAnimalSpawningWorld(World world) {
        WorldType type = getWorldType(world);
        if (type == null) return true; // 알 수 없는 월드는 기본 ON
        
        return plugin.getConfig().getBoolean(
            "worlds." + type.getConfigKey() + ".animalSpawning",
            type.isDefaultAnimalSpawning()
        );
    }
    
    /**
     * 월드의 기본 난이도 반환
     */
    public Difficulty getWorldDifficulty(World world) {
        WorldType type = getWorldType(world);
        if (type == null) return Difficulty.NORMAL;
        
        String configDiff = plugin.getConfig().getString(
            "worlds." + type.getConfigKey() + ".difficulty"
        );
        
        return configDiff != null ? parseDifficulty(configDiff) : type.getDefaultDifficulty();
    }
    
    // ===== PvP 데미지 배율 관리 =====
    
    /**
     * 현재 PvP 데미지 배율 반환
     */
    public double getPvpDamageMultiplier() {
        return pvpDamageMultiplier;
    }
    
    /**
     * PvP 데미지 배율 설정 (런타임 토글)
     * @param multiplier 0.0 = OFF, 0.1 = 10%, 0.5 = 50%, 1.0 = 100%
     */
    public void setPvpDamageMultiplier(double multiplier) {
        this.pvpDamageMultiplier = Math.max(0.0, Math.min(1.0, multiplier));
        logger.info("[PvP] 데미지 배율 변경: " + (int)(pvpDamageMultiplier * 100) + "%");
    }
    
    /**
     * PvP가 완전히 비활성화되었는지 확인
     */
    public boolean isPvpDisabled() {
        return pvpDamageMultiplier <= 0.0;
    }
    
    // ===== Wild 연결 차원 Getters =====
    
    public String getWildNetherName() {
        return wildNetherName;
    }
    
    public String getWildEndName() {
        return wildEndName;
    }
    
    public Optional<World> getWildNether() {
        return Optional.ofNullable(Bukkit.getWorld(wildNetherName));
    }
    
    public Optional<World> getWildEnd() {
        return Optional.ofNullable(Bukkit.getWorld(wildEndName));
    }
}
