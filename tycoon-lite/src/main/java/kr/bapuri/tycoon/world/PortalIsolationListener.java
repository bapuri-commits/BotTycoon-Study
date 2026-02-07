package kr.bapuri.tycoon.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.logging.Logger;

/**
 * PortalIsolationListener - 월드별 포탈 제어
 * 
 * Town 월드 (world):
 * - 포탈 이동 완전 차단
 * - 포탈 생성 차단
 * 
 * Wild 월드 (world_wild):
 * - 네더 포탈 → world_wild_nether
 * - 엔드 포탈 → world_wild_the_end
 * - Wild 네더/엔드에서 돌아올 때 → world_wild
 */
public class PortalIsolationListener implements Listener {

    private final WorldManager worldManager;
    private final Logger logger;
    
    public PortalIsolationListener(WorldManager worldManager, Logger logger) {
        this.worldManager = worldManager;
        this.logger = logger;
    }
    
    /**
     * 포탈 이동 처리
     * - Town: 차단
     * - Wild: 올바른 월드로 리다이렉트
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getFrom() == null || event.getFrom().getWorld() == null) {
            return;
        }
        
        World fromWorld = event.getFrom().getWorld();
        String fromWorldName = fromWorld.getName();
        TeleportCause cause = event.getCause();
        
        // ===== Town 월드: 포탈 완전 차단 =====
        if (worldManager.isPortalIsolated(fromWorld)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[마을] 이 월드에서는 포탈을 사용할 수 없습니다.");
            event.getPlayer().sendMessage("§7야생으로 이동한 후 포탈을 이용해주세요.");
            logger.fine("[PortalIsolation] " + event.getPlayer().getName() + " 포탈 이동 차단 (Town)");
            return;
        }
        
        // ===== Wild 월드 및 연결 차원: 리다이렉트 =====
        String wildWorldName = worldManager.getWorldName(WorldType.WILD);
        String wildNetherName = worldManager.getWildNetherName();
        String wildEndName = worldManager.getWildEndName();
        
        // Wild 본체에서 네더 포탈 사용 → world_wild_nether
        if (fromWorldName.equals(wildWorldName) && cause == TeleportCause.NETHER_PORTAL) {
            World targetWorld = Bukkit.getWorld(wildNetherName);
            if (targetWorld != null) {
                Location newTo = calculateNetherCoords(event.getFrom(), targetWorld);
                event.setTo(newTo);
                logger.fine("[PortalIsolation] " + event.getPlayer().getName() + " Wild → Wild Nether");
            }
            return;
        }
        
        // Wild 본체에서 엔드 포탈 사용 → world_wild_the_end
        if (fromWorldName.equals(wildWorldName) && cause == TeleportCause.END_PORTAL) {
            World targetWorld = Bukkit.getWorld(wildEndName);
            if (targetWorld != null) {
                // 엔드 스폰 위치로 이동
                Location endSpawn = targetWorld.getSpawnLocation();
                event.setTo(endSpawn);
                logger.fine("[PortalIsolation] " + event.getPlayer().getName() + " Wild → Wild End");
            }
            return;
        }
        
        // Wild 네더에서 포탈 사용 → world_wild
        if (fromWorldName.equals(wildNetherName) && cause == TeleportCause.NETHER_PORTAL) {
            World targetWorld = Bukkit.getWorld(wildWorldName);
            if (targetWorld != null) {
                Location newTo = calculateOverworldCoords(event.getFrom(), targetWorld);
                event.setTo(newTo);
                logger.fine("[PortalIsolation] " + event.getPlayer().getName() + " Wild Nether → Wild");
            }
            return;
        }
        
        // Wild 엔드에서 리턴 포탈 사용 (드래곤 처치 후) → Town (안전한 귀환)
        if (fromWorldName.equals(wildEndName) && cause == TeleportCause.END_PORTAL) {
            // Town 스폰으로 이동 (엔드에서 나오면 마을로 귀환)
            World townWorld = worldManager.getWorld(WorldType.TOWN).orElse(null);
            if (townWorld != null) {
                Location townSpawn = townWorld.getSpawnLocation();
                event.setTo(townSpawn);
                event.getPlayer().sendMessage("§a[시스템] 엔드에서 마을로 귀환합니다.");
                logger.fine("[PortalIsolation] " + event.getPlayer().getName() + " Wild End → Town (END_PORTAL)");
            } else {
                // Town이 없으면 Wild 스폰으로 폴백
                World targetWorld = Bukkit.getWorld(wildWorldName);
                if (targetWorld != null) {
                    event.setTo(targetWorld.getSpawnLocation());
                    logger.fine("[PortalIsolation] " + event.getPlayer().getName() + " Wild End → Wild (폴백)");
                }
            }
            return;
        }
        
        // Wild 엔드에서 엔드 게이트웨이 사용 (아우터 엔드 ↔ 메인 아일랜드)
        // END_GATEWAY는 엔드 내부 이동이므로 그대로 허용 (같은 월드 내 이동)
        if (fromWorldName.equals(wildEndName) && cause == TeleportCause.END_GATEWAY) {
            // 게이트웨이 목적지가 null이거나 다른 월드로 가려고 하면 Wild 엔드 내로 제한
            Location to = event.getTo();
            if (to == null || to.getWorld() == null || !to.getWorld().getName().equals(wildEndName)) {
                World wildEnd = Bukkit.getWorld(wildEndName);
                if (wildEnd != null) {
                    // 엔드 스폰으로 리다이렉트
                    event.setTo(wildEnd.getSpawnLocation());
                    logger.fine("[PortalIsolation] " + event.getPlayer().getName() + " END_GATEWAY 리다이렉트 → Wild End spawn");
                }
            }
            // 같은 월드 내 이동은 그대로 허용
            return;
        }
        
        // 기본 월드(world_nether, world_the_end) 관련 포탈은 차단
        // (Town에서 포탈이 이미 차단되어 여기까지 오지 않음)
        // 하지만 혹시 누군가가 world_nether에 있다면 차단
        if (fromWorldName.equals("world_nether") || fromWorldName.equals("world_the_end")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[시스템] 이 차원에서는 포탈을 사용할 수 없습니다.");
            logger.fine("[PortalIsolation] " + event.getPlayer().getName() + " 기본 네더/엔드에서 포탈 차단");
        }
    }
    
    /**
     * 오버월드 → 네더 좌표 변환 (x/8, z/8)
     */
    private Location calculateNetherCoords(Location from, World netherWorld) {
        return new Location(
            netherWorld,
            from.getX() / 8.0,
            Math.min(Math.max(from.getY(), 32), 100), // 네더 높이 제한
            from.getZ() / 8.0,
            from.getYaw(),
            from.getPitch()
        );
    }
    
    /**
     * 네더 → 오버월드 좌표 변환 (x*8, z*8)
     */
    private Location calculateOverworldCoords(Location from, World overworldWorld) {
        return new Location(
            overworldWorld,
            from.getX() * 8.0,
            Math.max(from.getY(), 64), // 오버월드 지상 높이
            from.getZ() * 8.0,
            from.getYaw(),
            from.getPitch()
        );
    }
    
    /**
     * 포탈 생성 차단 (Town만)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getWorld() == null) return;
        
        if (!worldManager.isPortalIsolated(event.getWorld())) {
            return;
        }
        
        // Town에서 포탈 생성 차단
        event.setCancelled(true);
        
        // 플레이어가 생성한 경우 메시지
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            player.sendMessage("§c[마을] 이 월드에서는 포탈을 생성할 수 없습니다.");
        }
        
        logger.fine("[PortalIsolation] 포탈 생성 차단 (Town): " + event.getReason());
    }
}
