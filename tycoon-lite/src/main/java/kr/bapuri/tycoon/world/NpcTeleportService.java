package kr.bapuri.tycoon.world;

import kr.bapuri.tycoon.integration.CitizensIntegration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * NpcTeleportService - NPC 클릭 시 텔레포트 처리
 * 
 * config.yml의 npcTeleport 섹션에서 설정 로드:
 * - npcMappings: NPC 이름 → 포인트 ID 매핑
 * - points: 포인트별 텔레포트 설정
 */
public class NpcTeleportService {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final CitizensIntegration citizens;
    private final WorldManager worldManager;
    
    // NPC 이름 → 포인트 ID 매핑
    private final Map<String, String> npcMappings = new HashMap<>();
    
    // 포인트 ID → 텔레포트 설정
    private final Map<String, TeleportPoint> teleportPoints = new HashMap<>();
    
    // 쿨다운 관리
    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();
    
    private boolean enabled = false;
    
    public NpcTeleportService(JavaPlugin plugin, CitizensIntegration citizens, WorldManager worldManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.citizens = citizens;
        this.worldManager = worldManager;
        
        loadConfig();
        
        if (enabled) {
            registerNpcHandlers();
            logger.info("[NpcTeleportService] 초기화 완료 - " + npcMappings.size() + "개 NPC 매핑, " + teleportPoints.size() + "개 포인트");
        } else {
            logger.info("[NpcTeleportService] 비활성화됨 (npcTeleport.enabled=false)");
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * config.yml에서 설정 로드
     */
    public void loadConfig() {
        npcMappings.clear();
        teleportPoints.clear();
        enabled = false;
        
        ConfigurationSection npcTeleportSection = plugin.getConfig().getConfigurationSection("npcTeleport");
        if (npcTeleportSection == null) {
            logger.warning("[NpcTeleportService] npcTeleport 섹션이 없습니다.");
            return;
        }
        
        // enabled 플래그 확인
        enabled = npcTeleportSection.getBoolean("enabled", true);
        if (!enabled) {
            return;
        }
        
        // NPC 매핑 로드
        ConfigurationSection mappingsSection = npcTeleportSection.getConfigurationSection("npcMappings");
        if (mappingsSection != null) {
            for (String npcName : mappingsSection.getKeys(false)) {
                String pointId = mappingsSection.getString(npcName);
                npcMappings.put(stripColor(npcName).toLowerCase(), pointId);
            }
        }
        
        // 포인트 설정 로드
        ConfigurationSection pointsSection = npcTeleportSection.getConfigurationSection("points");
        if (pointsSection != null) {
            for (String pointId : pointsSection.getKeys(false)) {
                ConfigurationSection pointSection = pointsSection.getConfigurationSection(pointId);
                if (pointSection != null) {
                    TeleportPoint point = parsePoint(pointId, pointSection);
                    if (point != null) {
                        teleportPoints.put(pointId, point);
                    }
                }
            }
        }
        
        logger.info("[NpcTeleportService] 설정 로드 완료 - NPC: " + npcMappings.size() + ", 포인트: " + teleportPoints.size());
    }
    
    /**
     * 포인트 설정 파싱
     */
    private TeleportPoint parsePoint(String id, ConfigurationSection section) {
        String type = section.getString("type", "TELEPORT");
        
        // GUI 타입은 스킵 (별도 처리 필요)
        if (type.startsWith("GUI_")) {
            logger.info("[NpcTeleportService] GUI 포인트 스킵: " + id + " (type=" + type + ")");
            return null;
        }
        
        String targetWorldName = section.getString("targetWorld");
        if (targetWorldName == null) {
            logger.warning("[NpcTeleportService] 포인트 " + id + ": targetWorld 누락");
            return null;
        }
        
        // 타겟 위치 파싱
        Location targetLocation = parseLocation(section, "targetLocation", targetWorldName);
        
        String message = section.getString("message", "§a텔레포트합니다...");
        int cooldownSeconds = section.getInt("cooldownSeconds", 3);
        String permission = section.getString("permission", "");
        
        return new TeleportPoint(id, type, targetWorldName, targetLocation, message, cooldownSeconds, permission);
    }
    
    /**
     * 위치 파싱 (좌표 또는 WORLD_SPAWN)
     */
    private Location parseLocation(ConfigurationSection section, String key, String worldName) {
        Object locationObj = section.get(key);
        
        if (locationObj == null) {
            return null;
        }
        
        // "WORLD_SPAWN" 문자열인 경우
        if (locationObj instanceof String) {
            String locationStr = (String) locationObj;
            if ("WORLD_SPAWN".equalsIgnoreCase(locationStr)) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    return world.getSpawnLocation();
                }
            }
            return null;
        }
        
        // {x, y, z} 맵인 경우
        if (locationObj instanceof ConfigurationSection) {
            ConfigurationSection locSection = (ConfigurationSection) locationObj;
            double x = locSection.getDouble("x", 0);
            double y = locSection.getDouble("y", 64);
            double z = locSection.getDouble("z", 0);
            
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                return new Location(world, x, y, z);
            }
        }
        
        return null;
    }
    
    /**
     * Citizens NPC 핸들러 등록
     */
    private void registerNpcHandlers() {
        if (citizens == null || !citizens.isAvailable()) {
            logger.warning("[NpcTeleportService] Citizens 연동 불가 - NPC 핸들러 등록 스킵");
            return;
        }
        
        for (Map.Entry<String, String> entry : npcMappings.entrySet()) {
            String npcName = entry.getKey();
            String pointId = entry.getValue();
            
            citizens.registerTeleportNPC(npcName, player -> {
                teleportToPoint(player, pointId);
            });
        }
        
        logger.info("[NpcTeleportService] " + npcMappings.size() + "개 NPC 텔레포트 핸들러 등록 완료");
    }
    
    /**
     * 플레이어를 포인트로 텔레포트
     */
    public boolean teleportToPoint(Player player, String pointId) {
        TeleportPoint point = teleportPoints.get(pointId);
        if (point == null) {
            player.sendMessage("§c알 수 없는 텔레포트 포인트입니다: " + pointId);
            return false;
        }
        
        // 권한 확인
        if (point.permission != null && !point.permission.isEmpty()) {
            if (!player.hasPermission(point.permission)) {
                player.sendMessage("§c이 텔레포트를 사용할 권한이 없습니다.");
                return false;
            }
        }
        
        // 쿨다운 확인
        if (isOnCooldown(player, pointId, point.cooldownSeconds)) {
            long remaining = getRemainingCooldown(player, pointId, point.cooldownSeconds);
            player.sendMessage("§c쿨다운 중입니다. " + remaining + "초 후에 다시 시도하세요.");
            return false;
        }
        
        // 타겟 위치 결정
        Location target = point.targetLocation;
        if (target == null) {
            // WORLD_SPAWN 재시도
            World world = Bukkit.getWorld(point.targetWorldName);
            if (world != null) {
                target = world.getSpawnLocation();
            }
        }
        
        if (target == null || target.getWorld() == null) {
            player.sendMessage("§c텔레포트 대상 월드가 존재하지 않습니다: " + point.targetWorldName);
            return false;
        }
        
        // 텔레포트 실행
        player.sendMessage(point.message);
        player.teleport(target);
        
        // 쿨다운 설정
        setCooldown(player, pointId);
        
        return true;
    }
    
    // ========== 쿨다운 관리 ==========
    
    private boolean isOnCooldown(Player player, String pointId, int cooldownSeconds) {
        Map<String, Long> cooldowns = playerCooldowns.get(player.getUniqueId());
        if (cooldowns == null) return false;
        
        Long lastUse = cooldowns.get(pointId);
        if (lastUse == null) return false;
        
        return System.currentTimeMillis() - lastUse < cooldownSeconds * 1000L;
    }
    
    private long getRemainingCooldown(Player player, String pointId, int cooldownSeconds) {
        Map<String, Long> cooldowns = playerCooldowns.get(player.getUniqueId());
        if (cooldowns == null) return 0;
        
        Long lastUse = cooldowns.get(pointId);
        if (lastUse == null) return 0;
        
        long elapsed = System.currentTimeMillis() - lastUse;
        long remaining = (cooldownSeconds * 1000L - elapsed) / 1000;
        return Math.max(0, remaining);
    }
    
    private void setCooldown(Player player, String pointId) {
        playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
            .put(pointId, System.currentTimeMillis());
    }
    
    public void clearCooldown(Player player) {
        playerCooldowns.remove(player.getUniqueId());
    }
    
    // ========== 유틸 ==========
    
    private String stripColor(String input) {
        if (input == null) return "";
        String result = input.replaceAll("§[0-9a-fk-or]", "");
        result = result.replaceAll("&[0-9a-fk-or]", "");
        return result.trim();
    }
    
    /**
     * 리로드
     */
    public void reload() {
        loadConfig();
        // 핸들러는 이미 등록되어 있으므로 재등록 불필요 (매핑만 갱신됨)
        logger.info("[NpcTeleportService] 리로드 완료");
    }
    
    // ========== 데이터 클래스 ==========
    
    private static class TeleportPoint {
        final String id;
        final String type;
        final String targetWorldName;
        final Location targetLocation;
        final String message;
        final int cooldownSeconds;
        final String permission;
        
        TeleportPoint(String id, String type, String targetWorldName, Location targetLocation,
                      String message, int cooldownSeconds, String permission) {
            this.id = id;
            this.type = type;
            this.targetWorldName = targetWorldName;
            this.targetLocation = targetLocation;
            this.message = message;
            this.cooldownSeconds = cooldownSeconds;
            this.permission = permission;
        }
    }
}
