package kr.bapuri.tycoon.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * AngelChestProtectionBlocker - 인벤토리 보호권 발동 시 AngelChest 생성 차단
 * 
 * <h2>문제</h2>
 * 보호권 발동 시 keepInventory=true로 설정되지만, AngelChest도 동시에 생성되어
 * 아이템이 복제(인벤토리 + 체스트)되는 버그 발생
 * 
 * <h2>해결</h2>
 * AngelChestSpawnPrepareEvent에서 keepInventory=true인 경우 체스트 생성 취소
 * (리플렉션 기반으로 API 없이 동작)
 * 
 * <h2>우선순위</h2>
 * HIGHEST - 다른 플러그인이 이벤트를 처리한 후 마지막에 판단
 * 
 * @since Phase 8
 */
public class AngelChestProtectionBlocker implements Listener {

    private static final Logger LOGGER = Logger.getLogger("Tycoon.AngelChest");
    
    private final Plugin plugin;
    private boolean enabled = false;
    
    // 리플렉션 캐시
    private Class<?> spawnPrepareEventClass;
    private Method getPlayerMethod;
    private Method getPlayerDeathEventMethod;
    private Method setCancelledMethod;
    
    public AngelChestProtectionBlocker(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 리스너 등록 시도
     * AngelChest 플러그인이 없으면 자동으로 비활성화
     * 
     * @return 등록 성공 여부
     */
    public boolean tryRegister() {
        // AngelChest 플러그인 확인
        Plugin angelChest = Bukkit.getPluginManager().getPlugin("AngelChest");
        if (angelChest == null || !angelChest.isEnabled()) {
            LOGGER.info("[AngelChestBlocker] AngelChest 플러그인 없음 - 비활성화");
            return false;
        }
        
        try {
            // 이벤트 클래스 찾기
            spawnPrepareEventClass = Class.forName("de.jeff_media.angelchest.events.AngelChestSpawnPrepareEvent");
            
            // 메서드 캐싱
            getPlayerMethod = spawnPrepareEventClass.getMethod("getPlayer");
            getPlayerDeathEventMethod = spawnPrepareEventClass.getMethod("getPlayerDeathEvent");
            setCancelledMethod = spawnPrepareEventClass.getMethod("setCancelled", boolean.class);
            
            // 리스너 등록 (리플렉션 기반)
            registerReflectionListener(angelChest);
            
            this.enabled = true;
            LOGGER.info("[AngelChestBlocker] 보호권-AngelChest 충돌 방지 리스너 등록 완료 (리플렉션)");
            return true;
            
        } catch (ClassNotFoundException e) {
            LOGGER.warning("[AngelChestBlocker] AngelChestSpawnPrepareEvent 클래스를 찾을 수 없음 - 비활성화");
            return false;
        } catch (Exception e) {
            LOGGER.warning("[AngelChestBlocker] 초기화 실패: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 리플렉션을 사용한 이벤트 리스너 등록
     */
    private void registerReflectionListener(Plugin angelChest) {
        // AngelChestSpawnPrepareEvent를 직접 리스닝하기 위해
        // 리플렉션으로 이벤트 핸들러 등록
        try {
            @SuppressWarnings("unchecked")
            Class<? extends org.bukkit.event.Event> eventClass = 
                (Class<? extends org.bukkit.event.Event>) spawnPrepareEventClass;
            
            Bukkit.getPluginManager().registerEvent(
                eventClass,
                new Listener() {},
                EventPriority.HIGHEST,
                (listener, event) -> handleSpawnPrepareEvent(event),
                plugin,
                false
            );
        } catch (Exception e) {
            LOGGER.warning("[AngelChestBlocker] 이벤트 등록 실패: " + e.getMessage());
        }
    }
    
    /**
     * AngelChestSpawnPrepareEvent 처리 (리플렉션)
     * 
     * [v2.2] keepInventory 체크 + InventoryProtectionListener 상태 확인
     * 이벤트 순서에 관계없이 정확히 차단
     */
    private void handleSpawnPrepareEvent(Object event) {
        if (!enabled) return;
        if (!spawnPrepareEventClass.isInstance(event)) return;
        
        try {
            // 플레이어 정보
            Player player = (Player) getPlayerMethod.invoke(event);
            
            // [v2.2] InventoryProtectionListener의 static 상태 확인
            // 이벤트 순서에 관계없이 보호권 발동 여부 정확히 판단
            boolean isProtected = kr.bapuri.tycoon.item.InventoryProtectionListener
                    .isCurrentlyProtected(player.getUniqueId());
            
            // 또는 keepInventory가 true인 경우 (다른 방식으로 보호됐을 수 있음)
            PlayerDeathEvent deathEvent = (PlayerDeathEvent) getPlayerDeathEventMethod.invoke(event);
            boolean keepInventory = deathEvent.getKeepInventory();
            
            if (isProtected || keepInventory) {
                // 체스트 생성 취소
                setCancelledMethod.invoke(event, true);
                
                LOGGER.info("[AngelChestBlocker] " + player.getName() + 
                        " - 보호권 발동으로 AngelChest 생성 취소 (isProtected=" + isProtected + 
                        ", keepInventory=" + keepInventory + ")");
            }
        } catch (Exception e) {
            LOGGER.warning("[AngelChestBlocker] 이벤트 처리 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
}
