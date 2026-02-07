package kr.bapuri.tycoon.world;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * WildSpawnManager - 야생 스폰 포인트 및 귀환 NPC 자동 관리
 * 
 * 기능:
 * 1. 야생 리셋 후 첫 텔레포트 시 기반암 5x5 플랫폼 생성
 * 2. 플랫폼 위에 귀환 NPC 자동 스폰 (Citizens 연동)
 * 3. 다중 스폰 포인트 (5개, 분산)
 * 4. 스폰 좌표 저장 (data/wild-spawn.yml)
 * 
 * 설정 위치: config.yml -> wildSpawn
 */
public class WildSpawnManager {

    private final Plugin plugin;
    private final Logger logger;
    private final File dataFile;
    
    // 이미 설정된 월드들
    private final Set<String> setupWorlds = new HashSet<>();
    
    // NPC 설정
    private boolean npcEnabled = true;
    private String returnNpcName = "§e[귀환] §f마을로 돌아가기";
    private String returnTarget = "town_spawn"; // town_spawn 또는 좌표 (x,y,z,world)
    
    // 다중 스폰 포인트 설정
    private boolean multipleSpawnsEnabled = true;
    private int spawnPointCount = 5;
    private int minSpawnDistance = 500;
    private int spreadRadius = 2000;
    
    // 월드별 스폰 포인트 목록
    private final Map<String, List<Location>> worldSpawnPoints = new HashMap<>();
    
    // WorldManager (Town 스폰 좌표 조회용)
    private WorldManager worldManager;

    public WildSpawnManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = Logger.getLogger("TycoonLite.WildSpawnManager");
        this.dataFile = new File(plugin.getDataFolder(), "data/wild-spawn.yml");
        
        loadConfig();
        loadData();
        
        logger.info("[WildSpawnManager] 초기화 완료");
    }
    
    public void setWorldManager(WorldManager worldManager) {
        this.worldManager = worldManager;
    }
    
    /**
     * config.yml에서 설정 로드
     */
    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("wildSpawn");
        
        if (section != null) {
            npcEnabled = section.getBoolean("npcEnabled", true);
            returnNpcName = section.getString("returnNpcName", "§e[귀환] §f마을로 돌아가기");
            returnTarget = section.getString("returnTarget", "town_spawn");
            
            ConfigurationSection multiSection = section.getConfigurationSection("multipleSpawns");
            if (multiSection != null) {
                multipleSpawnsEnabled = multiSection.getBoolean("enabled", true);
                spawnPointCount = multiSection.getInt("count", 5);
                minSpawnDistance = multiSection.getInt("minDistance", 500);
                spreadRadius = multiSection.getInt("spreadRadius", 2000);
            }
        }
        
        logger.info("[WildSpawnManager] NPC 자동 생성: " + (npcEnabled ? "활성화" : "비활성화"));
        if (multipleSpawnsEnabled) {
            logger.info("[WildSpawnManager] 다중 스폰: " + spawnPointCount + "개, 거리=" + minSpawnDistance + ", 반경=" + spreadRadius);
        }
    }
    
    /**
     * 저장된 데이터 로드
     * #6 수정: 스폰 포인트 좌표도 복원
     */
    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        if (data.contains("setupWorlds")) {
            List<String> savedWorlds = data.getStringList("setupWorlds");
            
            for (String worldName : savedWorlds) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    // 기반암 확인
                    Location spawn = world.getSpawnLocation();
                    Block block = world.getBlockAt(spawn.getBlockX(), spawn.getBlockY() - 1, spawn.getBlockZ());
                    if (block.getType() == Material.BEDROCK) {
                        setupWorlds.add(worldName);
                    }
                } else {
                    // 월드가 나중에 로드될 수 있으므로 일단 유지
                    setupWorlds.add(worldName);
                }
                
                // 스폰 포인트 좌표 복원
                if (data.contains("spawnPoints." + worldName)) {
                    List<String> pointStrings = data.getStringList("spawnPoints." + worldName);
                    List<Location> points = new ArrayList<>();
                    
                    for (String pointStr : pointStrings) {
                        Location loc = parseLocation(pointStr, world);
                        if (loc != null) {
                            points.add(loc);
                        }
                    }
                    
                    if (!points.isEmpty()) {
                        worldSpawnPoints.put(worldName, points);
                        logger.fine("[WildSpawnManager] " + worldName + " 스폰 포인트 " + points.size() + "개 복원됨");
                    }
                }
            }
            
            logger.info("[WildSpawnManager] 설정된 월드 " + setupWorlds.size() + "개 로드됨");
        }
    }
    
    /**
     * 문자열에서 Location 파싱 (x,y,z 형식)
     */
    private Location parseLocation(String str, World world) {
        if (str == null || world == null) return null;
        
        try {
            String[] parts = str.split(",");
            if (parts.length >= 3) {
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());
                return new Location(world, x, y, z);
            }
        } catch (NumberFormatException e) {
            logger.warning("[WildSpawnManager] 좌표 파싱 실패: " + str);
        }
        return null;
    }
    
    /**
     * 데이터 저장
     * #6 수정: 스폰 포인트 좌표도 저장
     */
    private void saveData() {
        FileConfiguration data = new YamlConfiguration();
        data.set("setupWorlds", new ArrayList<>(setupWorlds));
        
        // 스폰 포인트 좌표 저장
        for (Map.Entry<String, List<Location>> entry : worldSpawnPoints.entrySet()) {
            String worldName = entry.getKey();
            List<Location> points = entry.getValue();
            
            List<String> pointStrings = new ArrayList<>();
            for (Location loc : points) {
                pointStrings.add(loc.getX() + "," + loc.getY() + "," + loc.getZ());
            }
            data.set("spawnPoints." + worldName, pointStrings);
        }
        
        try {
            dataFile.getParentFile().mkdirs();
            data.save(dataFile);
        } catch (IOException e) {
            logger.warning("[WildSpawnManager] 데이터 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 야생 월드 스폰 포인트 설정 (기반암 + NPC)
     * 마을->야생 텔레포트 시 호출
     * 
     * @param worldName 야생 월드 이름
     * @param spawnLocation 초기 스폰 위치
     * @return true if setup completed, false if already setup
     */
    public boolean setupWildSpawn(String worldName, Location spawnLocation) {
        if (setupWorlds.contains(worldName)) {
            return false;
        }
        
        World world = spawnLocation.getWorld();
        if (world == null) {
            logger.warning("[WildSpawnManager] 월드를 찾을 수 없음: " + worldName);
            return false;
        }
        
        setupWorlds.add(worldName);
        
        logger.info("[WildSpawnManager] 야생 스폰 설정 시작: " + worldName);
        
        if (multipleSpawnsEnabled) {
            setupMultipleSpawns(worldName, spawnLocation);
        } else {
            setupSingleSpawn(worldName, spawnLocation);
        }
        
        saveData();
        return true;
    }
    
    /**
     * 단일 스폰 포인트 설정
     */
    private void setupSingleSpawn(String worldName, Location spawnLocation) {
        World world = spawnLocation.getWorld();
        if (world == null) return;
        
        // [Phase 8 버그수정] 기존 NPC 일괄 삭제
        removeExistingNpcs(worldName);
        
        // 청크 로드
        int x = spawnLocation.getBlockX();
        int z = spawnLocation.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.loadChunk(x >> 4, z >> 4);
        }
        
        // 안전한 지면 찾기
        Location groundLocation = findSafeGround(spawnLocation);
        if (groundLocation == null) {
            logger.warning("[WildSpawnManager] 안전한 지면을 찾을 수 없음: " + worldName);
            setupWorlds.remove(worldName);
            return;
        }
        
        // 기반암 플랫폼 설치
        installBedrockPlatform(groundLocation);
        
        // 스폰 위치 설정
        Location adjustedSpawn = new Location(
            world,
            groundLocation.getBlockX() + 0.5,
            groundLocation.getBlockY() + 1,
            groundLocation.getBlockZ() + 0.5
        );
        world.setSpawnLocation(adjustedSpawn);
        
        // 귀환 NPC 스폰
        if (npcEnabled) {
            spawnReturnNpc(adjustedSpawn);
        }
        
        logger.info("[WildSpawnManager] 야생 스폰 설정 완료: " + worldName + 
                   " (" + adjustedSpawn.getBlockX() + ", " + adjustedSpawn.getBlockY() + ", " + adjustedSpawn.getBlockZ() + ")");
    }
    
    /**
     * 다중 스폰 포인트 설정
     * #10 수정: 청크 대량 로드 방지를 위해 순차적 설정 (스케줄러 사용)
     */
    private void setupMultipleSpawns(String worldName, Location initialLocation) {
        World world = initialLocation.getWorld();
        if (world == null) return;
        
        List<Location> spawnPoints = new ArrayList<>();
        worldSpawnPoints.put(worldName, spawnPoints);
        
        // [Phase 8 버그수정] 기존 NPC 일괄 삭제 (각 스폰 포인트에서 삭제하지 않음)
        removeExistingNpcs(worldName);
        
        // 첫 번째 스폰 포인트: 초기 위치 (즉시 설정)
        Location firstSpawn = setupSpawnPoint(worldName, initialLocation, 1);
        if (firstSpawn != null) {
            spawnPoints.add(firstSpawn);
            world.setSpawnLocation(firstSpawn);
        }
        
        // 나머지 스폰 포인트 후보 미리 생성
        List<Location> candidates = generateSpawnCandidates(world, spawnPoints);
        logger.info("[WildSpawnManager] 스폰 포인트 후보 " + candidates.size() + "개 생성됨");
        
        if (candidates.isEmpty()) {
            logger.warning("[WildSpawnManager] 스폰 포인트 후보가 없습니다! 설정을 확인하세요. " +
                          "(minDistance=" + minSpawnDistance + ", spreadRadius=" + spreadRadius + ")");
        }
        
        // 순차적으로 스폰 포인트 설정 (20틱 = 1초 간격)
        setupSpawnPointsSequential(worldName, candidates, spawnPoints, 0);
    }
    
    /**
     * 스폰 포인트 후보 위치 생성
     */
    private List<Location> generateSpawnCandidates(World world, List<Location> existingPoints) {
        List<Location> candidates = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = spawnPointCount * 20;
        
        while (candidates.size() < (spawnPointCount - existingPoints.size()) * 2 && attempts < maxAttempts) {
            attempts++;
            
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            int distance = ThreadLocalRandom.current().nextInt(minSpawnDistance, spreadRadius + 1);
            
            int x = (int) (Math.cos(angle) * distance);
            int z = (int) (Math.sin(angle) * distance);
            
            Location candidate = new Location(world, x, 64, z);
            
            // 기존 포인트와 2D 거리 확인 (Y좌표 무시)
            boolean tooClose = false;
            for (Location existing : existingPoints) {
                double dx = existing.getX() - candidate.getX();
                double dz = existing.getZ() - candidate.getZ();
                if ((dx * dx + dz * dz) < minSpawnDistance * minSpawnDistance) {
                    tooClose = true;
                    break;
                }
            }
            for (Location other : candidates) {
                double dx = other.getX() - candidate.getX();
                double dz = other.getZ() - candidate.getZ();
                if ((dx * dx + dz * dz) < minSpawnDistance * minSpawnDistance) {
                    tooClose = true;
                    break;
                }
            }
            
            if (!tooClose) {
                candidates.add(candidate);
            }
        }
        
        return candidates;
    }
    
    /**
     * 스폰 포인트 순차 설정 (서버 부하 분산)
     */
    private void setupSpawnPointsSequential(String worldName, List<Location> candidates, 
                                            List<Location> spawnPoints, int index) {
        // 목표 개수 도달 또는 후보 소진
        if (spawnPoints.size() >= spawnPointCount || index >= candidates.size()) {
            saveData();
            logger.info("[WildSpawnManager] 다중 스폰 포인트 설정 완료: " + worldName + 
                       " (" + spawnPoints.size() + "/" + spawnPointCount + "개)");
            return;
        }
        
        Location candidate = candidates.get(index);
        
        // 이 스폰 포인트 설정
        Location newSpawn = setupSpawnPoint(worldName, candidate, spawnPoints.size() + 1);
        if (newSpawn != null) {
            spawnPoints.add(newSpawn);
        }
        
        // 20틱(1초) 후 다음 스폰 포인트 설정
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            setupSpawnPointsSequential(worldName, candidates, spawnPoints, index + 1);
        }, 20L);
    }
    
    /**
     * 개별 스폰 포인트 설정
     */
    private Location setupSpawnPoint(String worldName, Location targetLocation, int pointIndex) {
        World world = targetLocation.getWorld();
        if (world == null) return null;
        
        // 청크 로드
        int x = targetLocation.getBlockX();
        int z = targetLocation.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.loadChunk(x >> 4, z >> 4);
        }
        
        // 안전한 지면 찾기
        Location groundLocation = findSafeGround(targetLocation);
        if (groundLocation == null) {
            return null;
        }
        
        // 기반암 플랫폼 설치
        installBedrockPlatform(groundLocation);
        
        // 스폰 위치 조정
        Location adjustedSpawn = new Location(
            world,
            groundLocation.getBlockX() + 0.5,
            groundLocation.getBlockY() + 1,
            groundLocation.getBlockZ() + 0.5
        );
        
        // 귀환 NPC 스폰
        if (npcEnabled) {
            spawnReturnNpc(adjustedSpawn);
        }
        
        logger.info("[WildSpawnManager] 스폰 포인트 #" + pointIndex + " 설정 완료: " + 
                   adjustedSpawn.getBlockX() + ", " + adjustedSpawn.getBlockY() + ", " + adjustedSpawn.getBlockZ());
        
        return adjustedSpawn;
    }
    
    /**
     * 안전한 지면 위치 찾기
     */
    private Location findSafeGround(Location location) {
        World world = location.getWorld();
        if (world == null) return location;
        
        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = location.getBlockY();
        
        // 아래로 탐색
        for (int y = startY; y >= world.getMinHeight() + 1; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            Block aboveTwo = world.getBlockAt(x, y + 2, z);
            
            if (block.getType().isSolid() && 
                !above.getType().isSolid() && 
                !aboveTwo.getType().isSolid()) {
                return new Location(world, x, y, z);
            }
        }
        
        // 못 찾으면 sea level 기준
        int seaLevel = world.getSeaLevel();
        return new Location(world, x, seaLevel, z);
    }
    
    /**
     * 기반암 5x5 플랫폼 설치
     */
    private void installBedrockPlatform(Location groundLocation) {
        World world = groundLocation.getWorld();
        if (world == null) return;
        
        int baseX = groundLocation.getBlockX();
        int baseY = groundLocation.getBlockY();
        int baseZ = groundLocation.getBlockZ();
        
        // 5x5 플랫폼 설치 (-2 ~ +2)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block block = world.getBlockAt(baseX + dx, baseY, baseZ + dz);
                block.setType(Material.BEDROCK);
                
                // 위 2블록 공기로
                world.getBlockAt(baseX + dx, baseY + 1, baseZ + dz).setType(Material.AIR);
                world.getBlockAt(baseX + dx, baseY + 2, baseZ + dz).setType(Material.AIR);
            }
        }
        
        logger.fine("[WildSpawnManager] 기반암 5x5 플랫폼 설치: " + 
                   baseX + ", " + baseY + ", " + baseZ);
    }
    
    /**
     * 귀환 NPC 스폰 (Citizens 연동)
     */
    private void spawnReturnNpc(Location location) {
        // Citizens 확인
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            logger.warning("[WildSpawnManager] Citizens 플러그인이 없어 NPC를 생성할 수 없습니다.");
            return;
        }
        
        try {
            // [Phase 8 버그수정] 기존 NPC 삭제는 setupMultipleSpawns/setupSingleSpawn에서 한 번만 수행
            // 여기서 삭제하면 다중 스폰 시 이전 NPC가 삭제됨
            
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            
            // NPC 위치 (플랫폼 중앙에서 약간 떨어진 곳)
            Location npcLocation = location.clone();
            npcLocation.add(0, 0, 1.0);
            npcLocation.setYaw(180); // 남쪽 보기
            
            // NPC 생성
            NPC npc = registry.createNPC(EntityType.VILLAGER, returnNpcName);
            npc.spawn(npcLocation);
            
            // NPC 설정
            npc.setProtected(true);
            // [Phase 8 버그수정] NPC 영구 저장 - 청크 언로드 시에도 유지
            npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, true);
            npc.data().setPersistent(NPC.Metadata.FLYABLE, true);
            npc.data().setPersistent(NPC.Metadata.DEFAULT_PROTECTED, true);
            // [Phase 8 버그수정] 물에 밀리지 않도록 충돌 비활성화
            npc.data().setPersistent(NPC.Metadata.COLLIDABLE, false);
            
            if (npc.isSpawned() && npc.getEntity() != null) {
                npc.getEntity().setGravity(false);
                // [Phase 8 버그수정] AI 비활성화 - 물/기타 영향으로 이동하지 않음
                if (npc.getEntity() instanceof org.bukkit.entity.LivingEntity livingEntity) {
                    livingEntity.setAI(false);
                }
            }
            
            logger.info("[WildSpawnManager] 귀환 NPC 스폰: " + returnNpcName + 
                       " (" + npcLocation.getBlockX() + ", " + npcLocation.getBlockY() + ", " + npcLocation.getBlockZ() + ")");
            
        } catch (Exception e) {
            logger.warning("[WildSpawnManager] NPC 스폰 실패: " + e.getMessage());
        }
    }
    
    /**
     * 해당 월드의 기존 귀환 NPC 삭제
     */
    private void removeExistingNpcs(String worldName) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            return;
        }
        
        try {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            List<NPC> toRemove = new ArrayList<>();
            
            for (NPC npc : registry) {
                if (npc.getStoredLocation() != null && 
                    npc.getStoredLocation().getWorld() != null &&
                    npc.getStoredLocation().getWorld().getName().equals(worldName)) {
                    String name = npc.getName();
                    if (name != null && (name.contains("귀환") || name.contains("마을로 돌아가기"))) {
                        toRemove.add(npc);
                    }
                }
            }
            
            for (NPC npc : toRemove) {
                npc.destroy();
                logger.fine("[WildSpawnManager] 기존 NPC 삭제: " + npc.getName());
            }
            
        } catch (Exception e) {
            logger.warning("[WildSpawnManager] NPC 삭제 중 오류: " + e.getMessage());
        }
    }
    
    /**
     * 월드 리셋 시 해당 월드의 설정 초기화
     */
    public void resetWorld(String worldName) {
        if (setupWorlds.remove(worldName)) {
            removeExistingNpcs(worldName);
            worldSpawnPoints.remove(worldName);
            saveData();
            logger.info("[WildSpawnManager] 월드 리셋 (NPC 정리 포함): " + worldName);
        }
    }
    
    /**
     * 월드가 이미 설정되었는지 확인
     */
    public boolean isWorldSetup(String worldName) {
        return setupWorlds.contains(worldName);
    }
    
    /**
     * 해당 월드의 랜덤 스폰 포인트 반환
     */
    public Location getRandomSpawnPoint(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        
        if (multipleSpawnsEnabled && worldSpawnPoints.containsKey(worldName)) {
            List<Location> points = worldSpawnPoints.get(worldName);
            if (points != null && !points.isEmpty()) {
                return points.get(ThreadLocalRandom.current().nextInt(points.size())).clone();
            }
        }
        
        return world.getSpawnLocation();
    }
    
    /**
     * 귀환 NPC 클릭 시 이동할 위치 반환
     */
    public Location getReturnLocation() {
        if ("town_spawn".equals(returnTarget)) {
            if (worldManager != null) {
                return worldManager.getWorld(WorldType.TOWN)
                    .map(World::getSpawnLocation)
                    .orElse(null);
            }
        } else {
            // 좌표 파싱 (x,y,z,world)
            try {
                String[] parts = returnTarget.split(",");
                if (parts.length >= 4) {
                    double x = Double.parseDouble(parts[0].trim());
                    double y = Double.parseDouble(parts[1].trim());
                    double z = Double.parseDouble(parts[2].trim());
                    World world = Bukkit.getWorld(parts[3].trim());
                    if (world != null) {
                        return new Location(world, x, y, z);
                    }
                }
            } catch (Exception e) {
                logger.warning("[WildSpawnManager] returnTarget 파싱 실패: " + returnTarget);
            }
        }
        
        return null;
    }
    
    public String getReturnNpcName() {
        return returnNpcName;
    }
}
