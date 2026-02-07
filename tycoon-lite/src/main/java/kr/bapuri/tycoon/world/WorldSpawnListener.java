package kr.bapuri.tycoon.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * WorldSpawnListener - 월드별 스폰/리스폰/재접속 위치 관리
 * 
 * <h2>기능</h2>
 * <ul>
 *   <li>야생/네더/엔드에서 사망 시 마을로 리스폰</li>
 *   <li>야생 차원(네더/엔드)에서 재접속 시 마을로 이동</li>
 *   <li>Town에서 사망 시 Town 스폰으로 리스폰</li>
 * </ul>
 * 
 * <h2>이벤트 우선순위</h2>
 * <ul>
 *   <li>PlayerDeathEvent: MONITOR - 사망 월드 기록 (가장 마지막)</li>
 *   <li>PlayerRespawnEvent: NORMAL - 리스폰 위치 설정 (InventoryProtectionListener.HIGHEST 이후)</li>
 *   <li>PlayerJoinEvent: MONITOR - 위치 확인 및 이동 (다른 리스너가 처리 완료 후)</li>
 * </ul>
 */
public class WorldSpawnListener implements Listener {

    private final Plugin plugin;
    private final WorldManager worldManager;
    private final Logger logger;
    
    // 사망 월드 임시 저장 (리스폰 이벤트에서 사용)
    private final Map<UUID, String> deathWorldCache = new ConcurrentHashMap<>();

    public WorldSpawnListener(Plugin plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.logger = Logger.getLogger("TycoonLite.WorldSpawnListener");
        
        logger.info("[WorldSpawnListener] 초기화 완료");
    }

    /**
     * 사망 시 월드 정보 캐싱
     * 
     * MONITOR 우선순위로 다른 리스너가 처리 완료 후 실행
     * 리스폰 이벤트에서 사용하기 위해 사망 월드를 캐시에 저장
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        
        if (deathLocation.getWorld() != null) {
            String deathWorldName = deathLocation.getWorld().getName();
            deathWorldCache.put(player.getUniqueId(), deathWorldName);
            
            logger.fine("[WorldSpawnListener] " + player.getName() + 
                " 사망 위치 캐시: " + deathWorldName);
        }
    }

    /**
     * 리스폰 위치 처리
     * 
     * - Town에서 사망: Town 스폰으로 리스폰
     * - Wild/네더/엔드에서 사망: Town 스폰으로 리스폰
     * 
     * NORMAL 우선순위로 InventoryProtectionListener(HIGHEST)가 먼저 처리되도록 함
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // 캐시에서 사망 월드 가져오기
        String deathWorldName = deathWorldCache.remove(uuid);
        if (deathWorldName == null) {
            // 캐시에 없으면 현재 리스폰 위치의 월드 사용 (폴백)
            Location respawnLoc = event.getRespawnLocation();
            if (respawnLoc != null && respawnLoc.getWorld() != null) {
                deathWorldName = respawnLoc.getWorld().getName();
            } else {
                return;
            }
        }
        
        // Town 스폰 위치 가져오기
        Location townSpawn = getTownSpawnLocation();
        if (townSpawn == null) {
            logger.warning("[WorldSpawnListener] Town 스폰 위치를 찾을 수 없습니다.");
            return;
        }
        
        // 사망 월드의 타입 확인
        World deathWorld = Bukkit.getWorld(deathWorldName);
        WorldType deathWorldType = deathWorld != null ? worldManager.getWorldType(deathWorld) : null;
        
        // Wild 관련 월드에서 사망 시 Town으로 리스폰
        if (deathWorldType == WorldType.WILD || isWildLinkedWorld(deathWorldName)) {
            event.setRespawnLocation(townSpawn);
            logger.info("[WorldSpawnListener] " + player.getName() + " 리스폰: " + 
                deathWorldName + " → Town 스폰");
            
            // 1틱 후 메시지 전송 (리스폰 완료 후)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§a[시스템] 마을로 리스폰되었습니다.");
                }
            }, 1L);
            return;
        }
        
        // Town에서 사망 시 Town 스폰으로 리스폰
        if (deathWorldType == WorldType.TOWN) {
            event.setRespawnLocation(townSpawn);
            logger.fine("[WorldSpawnListener] " + player.getName() + " Town 내 리스폰");
            return;
        }
        
        // 기타 월드는 기본 동작
    }

    /**
     * 재접속 시 위치 확인 및 조정
     * 
     * - 야생 차원(네더/엔드)에서 접속 시 마을로 이동
     * - 야생 본체는 그대로 유지 (플레이어가 작업 중일 수 있음)
     * 
     * MONITOR 우선순위로 다른 리스너(PlayerSessionListener 등)가 먼저 처리되도록 함
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location currentLocation = player.getLocation();
        
        if (currentLocation.getWorld() == null) {
            return;
        }
        
        String currentWorldName = currentLocation.getWorld().getName();
        
        // 야생 차원(네더/엔드)에서 접속 시 마을로 이동
        // 야생 월드가 리셋되었을 수 있으므로 안전한 위치로 이동
        if (isWildLinkedWorld(currentWorldName)) {
            Location townSpawn = getTownSpawnLocation();
            if (townSpawn != null) {
                // 1틱 후 텔레포트 (접속 처리 완료 후)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(townSpawn);
                        player.sendMessage("§a[시스템] 안전을 위해 마을로 이동되었습니다.");
                        logger.info("[WorldSpawnListener] " + player.getName() + 
                            " 재접속 이동: " + currentWorldName + " → Town");
                    }
                }, 1L);
            }
            return;
        }
        
        // 야생 본체에서 접속 시 위치 유효성 확인
        WorldType worldType = worldManager.getWorldType(currentLocation.getWorld());
        if (worldType == WorldType.WILD) {
            // 위치가 유효한지 확인 (Y좌표가 너무 낮거나 보이드인 경우)
            if (currentLocation.getY() < currentLocation.getWorld().getMinHeight() + 5) {
                Location townSpawn = getTownSpawnLocation();
                if (townSpawn != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.teleport(townSpawn);
                            player.sendMessage("§c[시스템] 위험한 위치에서 접속하여 마을로 이동되었습니다.");
                            logger.info("[WorldSpawnListener] " + player.getName() + 
                                " 위험 위치에서 접속 → Town 이동");
                        }
                    }, 1L);
                }
            }
        }
    }

    /**
     * Wild 연결 차원인지 확인 (네더 또는 엔드)
     */
    private boolean isWildLinkedWorld(String worldName) {
        String wildNetherName = worldManager.getWildNetherName();
        String wildEndName = worldManager.getWildEndName();
        return worldName.equals(wildNetherName) || worldName.equals(wildEndName);
    }

    /**
     * Town 스폰 위치 가져오기
     */
    private Location getTownSpawnLocation() {
        return worldManager.getWorld(WorldType.TOWN)
            .map(World::getSpawnLocation)
            .orElse(null);
    }
    
    /**
     * 플레이어 퇴장 시 캐시 정리
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        deathWorldCache.remove(event.getPlayer().getUniqueId());
    }
}
