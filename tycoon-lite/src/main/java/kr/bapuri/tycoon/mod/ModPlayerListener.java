package kr.bapuri.tycoon.mod;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 * ModPlayerListener - 클라이언트 모드 연동을 위한 플레이어 이벤트 리스너
 * 
 * <h2>역할</h2>
 * 플레이어의 주요 상태 변화를 감지하여 ModDataService를 통해 클라이언트 모드에 전송합니다.
 * 
 * <h2>감지 이벤트</h2>
 * <ul>
 *   <li>PlayerJoinEvent - 접속 시 PLAYER_PROFILE 전송</li>
 *   <li>PlayerChangedWorldEvent - 월드 이동 시 PLOT_UPDATE 전송</li>
 *   <li>EntityRegainHealthEvent - 체력 회복 시 VITAL_UPDATE 전송</li>
 *   <li>EntityDamageEvent - 피해 시 VITAL_UPDATE 전송</li>
 *   <li>FoodLevelChangeEvent - 배고픔 변화 시 VITAL_UPDATE 전송</li>
 * </ul>
 * 
 * @see ModDataService
 */
public class ModPlayerListener implements Listener {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private ModDataService modDataService;
    
    // 활성화 여부 (config에서 로드)
    private boolean enabled = false;
    
    public ModPlayerListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    // ========================================================================
    // Setter Injection
    // ========================================================================
    
    public void setModDataService(ModDataService modDataService) {
        this.modDataService = modDataService;
    }
    
    /**
     * 초기화 (활성화 상태 로드)
     */
    public void initialize() {
        enabled = plugin.getConfig().getBoolean("mod_integration.enabled", false);
        
        if (enabled) {
            logger.info("[ModPlayerListener] 활성화됨 (플레이어 이벤트 감지)");
        } else {
            logger.info("[ModPlayerListener] 비활성화 상태");
        }
    }
    
    // ========================================================================
    // 접속/월드 이벤트
    // ========================================================================
    
    /**
     * 플레이어 접속 시 프로필 전송
     * 
     * <p>접속 직후에는 모드가 아직 채널 등록을 완료하지 않았을 수 있으므로
     * 1초 지연 후 전송합니다.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || modDataService == null) return;
        
        Player player = event.getPlayer();
        
        // 1초 후 프로필 전송 (모드 채널 등록 대기)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    modDataService.sendPlayerProfile(player);
                    modDataService.sendVitalUpdate(player);
                    modDataService.sendCurrentPlotUpdate(player);
                    logger.fine("[ModPlayerListener] 접속 데이터 전송: " + player.getName());
                }
            }
        }.runTaskLater(plugin, 20L); // 1초 = 20틱
    }
    
    /**
     * 월드 이동 시 플롯 업데이트 전송
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!enabled || modDataService == null) return;
        
        Player player = event.getPlayer();
        modDataService.sendCurrentPlotUpdate(player);
        logger.fine("[ModPlayerListener] 월드 이동 플롯 업데이트: " + player.getName());
    }
    
    // ========================================================================
    // Vital 이벤트 (체력/배고픔)
    // ========================================================================
    
    /**
     * 체력 회복 시 Vital 업데이트 전송
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (!enabled || modDataService == null) return;
        if (!(event.getEntity() instanceof Player player)) return;
        
        // 다음 틱에 전송 (체력 값이 반영된 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    modDataService.sendVitalUpdate(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }
    
    /**
     * 피해 시 Vital 업데이트 전송
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!enabled || modDataService == null) return;
        if (!(event.getEntity() instanceof Player player)) return;
        
        // 다음 틱에 전송 (체력 값이 반영된 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    modDataService.sendVitalUpdate(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }
    
    /**
     * 배고픔 변화 시 Vital 업데이트 전송
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!enabled || modDataService == null) return;
        if (!(event.getEntity() instanceof Player player)) return;
        
        // 다음 틱에 전송 (배고픔 값이 반영된 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    modDataService.sendVitalUpdate(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }
    
    // ========================================================================
    // 설정
    // ========================================================================
    
    public void reload() {
        enabled = plugin.getConfig().getBoolean("mod_integration.enabled", false);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
}
