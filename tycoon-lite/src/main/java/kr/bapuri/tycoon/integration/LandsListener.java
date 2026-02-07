package kr.bapuri.tycoon.integration;

import kr.bapuri.tycoon.bootstrap.ServiceRegistry;
import kr.bapuri.tycoon.tax.VillagerRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * LandsListener - Lands 연동 이벤트 리스너
 * 
 * 기능:
 * 1. 블록 이벤트에서 Lands 권한 체크 (WorldGuard 없을 때 보조)
 * 2. 플레이어 이동 시 땅 변경 감지 → 모드 연동용 PLOT_UPDATE
 * 
 * Note: Lands 플러그인 자체가 보호 기능을 가지고 있어서
 * 이 리스너는 "추가 검증" 및 "모드 연동" 목적입니다.
 */
public class LandsListener implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ServiceRegistry services;
    
    // 플레이어별 마지막 땅 이름 (변경 감지용)
    private final Map<UUID, String> lastLandName = new HashMap<>();
    
    // 땅 변경 콜백 (ModDataService에서 등록)
    private LandChangeCallback landChangeCallback;
    
    // [세금 시스템] Frozen 마을 체크용 (지연 주입)
    private VillagerRegistry villagerRegistry;
    
    public LandsListener(JavaPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.services = services;
    }
    
    /**
     * VillagerRegistry 설정 (세금 시스템 초기화 시 호출)
     */
    public void setVillagerRegistry(VillagerRegistry villagerRegistry) {
        this.villagerRegistry = villagerRegistry;
    }
    
    /**
     * 땅 변경 콜백 설정 (ModDataService에서 호출)
     */
    public void setLandChangeCallback(LandChangeCallback callback) {
        this.landChangeCallback = callback;
    }
    
    /**
     * 블록 파괴 시 Lands 권한 체크 + Frozen 마을 체크
     * 
     * Note: Lands 플러그인이 이미 보호하지만,
     * Lands가 느리게 처리되거나 비활성화된 경우 백업용
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Lands 비활성화 시 무시 (다른 플러그인이 처리)
        Optional<LandsIntegration> landsOpt = services.getLandsIntegration();
        if (landsOpt.isEmpty()) {
            return;
        }
        
        LandsIntegration lands = landsOpt.get();
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        
        // 관리자는 통과
        if (player.hasPermission("tycoon.admin.bypass")) {
            return;
        }
        
        // [세금 시스템] Frozen 마을 체크
        if (isLocationInFrozenLand(lands, location)) {
            event.setCancelled(true);
            player.sendMessage(getFrozenMessage());
            return;
        }
        
        // Lands 권한 체크
        if (!lands.canBuild(player, location)) {
            event.setCancelled(true);
            player.sendMessage("§c이 땅에서 블록을 파괴할 권한이 없습니다.");
        }
    }
    
    /**
     * 블록 설치 시 Lands 권한 체크 + Frozen 마을 체크
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Optional<LandsIntegration> landsOpt = services.getLandsIntegration();
        if (landsOpt.isEmpty()) {
            return;
        }
        
        LandsIntegration lands = landsOpt.get();
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        
        if (player.hasPermission("tycoon.admin.bypass")) {
            return;
        }
        
        // [세금 시스템] Frozen 마을 체크
        if (isLocationInFrozenLand(lands, location)) {
            event.setCancelled(true);
            player.sendMessage(getFrozenMessage());
            return;
        }
        
        if (!lands.canBuild(player, location)) {
            event.setCancelled(true);
            player.sendMessage("§c이 땅에서 블록을 설치할 권한이 없습니다.");
        }
    }
    
    /**
     * 상호작용 시 Frozen 마을 체크
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        
        Optional<LandsIntegration> landsOpt = services.getLandsIntegration();
        if (landsOpt.isEmpty()) {
            return;
        }
        
        LandsIntegration lands = landsOpt.get();
        Player player = event.getPlayer();
        Location location = event.getClickedBlock().getLocation();
        
        if (player.hasPermission("tycoon.admin.bypass")) {
            return;
        }
        
        // [세금 시스템] Frozen 마을 체크
        if (isLocationInFrozenLand(lands, location)) {
            event.setCancelled(true);
            player.sendMessage(getFrozenMessage());
        }
    }
    
    // ===== [세금 시스템] Frozen 마을 헬퍼 메서드 =====
    
    /**
     * 위치가 Frozen 마을 내에 있는지 확인
     */
    private boolean isLocationInFrozenLand(LandsIntegration lands, Location location) {
        if (villagerRegistry == null) {
            return false;
        }
        
        Optional<LandsIntegration.PlotInfo> plotOpt = lands.getPlotAt(location);
        if (plotOpt.isEmpty()) {
            return false;
        }
        
        String landName = plotOpt.get().getName();
        return villagerRegistry.isFrozen(landName);
    }
    
    /**
     * Frozen 마을 메시지
     */
    private String getFrozenMessage() {
        return "§c[토지세] 이 마을은 세금 미납으로 정지되어 있습니다. 세금을 납부해주세요.";
    }
    
    /**
     * 플레이어 이동 시 땅 변경 감지
     * 
     * 성능 최적화: 블록 단위 이동만 체크
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // 블록 단위 이동이 아니면 무시
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && 
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        
        Optional<LandsIntegration> landsOpt = services.getLandsIntegration();
        if (landsOpt.isEmpty()) {
            return;
        }
        
        LandsIntegration lands = landsOpt.get();
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // 현재 땅 이름 조회
        String currentLandName = lands.getCurrentLandName(player);
        String previousLandName = lastLandName.get(playerId);
        
        // 땅 변경 감지
        if (!currentLandName.equals(previousLandName)) {
            lastLandName.put(playerId, currentLandName);
            
            // 콜백 호출 (ModDataService에서 모드로 전송)
            if (landChangeCallback != null) {
                Optional<LandsIntegration.PlotInfo> plotInfo = lands.getPlotAt(to);
                landChangeCallback.onLandChange(player, plotInfo.orElse(null));
            }
        }
    }
    
    /**
     * 플레이어 접속 해제 시 캐시 정리
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastLandName.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * 땅 변경 콜백 인터페이스
     */
    @FunctionalInterface
    public interface LandChangeCallback {
        void onLandChange(Player player, LandsIntegration.PlotInfo newPlotInfo);
    }
}
