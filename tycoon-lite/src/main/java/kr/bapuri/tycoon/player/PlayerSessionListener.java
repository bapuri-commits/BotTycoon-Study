package kr.bapuri.tycoon.player;

import kr.bapuri.tycoon.TycoonPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 플레이어 세션 리스너
 * 
 * - 접속 시: 플레이어 데이터 로드/생성
 * - 퇴장 시: 플레이어 데이터 저장 후 언로드
 */
public class PlayerSessionListener implements Listener {

    private final Plugin plugin;
    private final PlayerDataManager playerDataManager;
    private final Logger logger;

    public PlayerSessionListener(Plugin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.logger = Logger.getLogger("TycoonLite.PlayerSession");
    }

    /**
     * 플레이어 접속 시 데이터 로드
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 데이터 로드/생성
        PlayerTycoonData data = playerDataManager.get(player);
        
        // 플레이어 이름 업데이트 (닉네임 변경 대응)
        data.setPlayerName(player.getName());
        
        logger.info("[Join] " + player.getName() + " 데이터 로드 완료");
    }

    /**
     * 플레이어 퇴장 시 데이터 저장
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // 비동기로 저장 후 언로드
        playerDataManager.saveAndUnload(player.getUniqueId());
        
        // 세션 기반 효과 메시지 설정 정리
        kr.bapuri.tycoon.TycoonPlugin.getInstance().clearEffectMsgSetting(player.getUniqueId());
        
        logger.info("[Quit] " + player.getName() + " 데이터 저장 완료");
    }

    /**
     * 플레이어 사망 시 위치 저장
     * - 전생의 기억 주문서(REBIRTH_MEMORY_SCROLL)에서 사용
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerTycoonData data = playerDataManager.get(player.getUniqueId());
        
        if (data != null) {
            // 사망 위치 저장 (clone하여 불변성 보장)
            data.setLastDeathLocation(player.getLocation().clone());
            data.markDirty();
            
            logger.fine("[Death] " + player.getName() + " 사망 위치 저장: " + 
                player.getLocation().getWorld().getName() + " " +
                String.format("%.1f, %.1f, %.1f", 
                    player.getLocation().getX(), 
                    player.getLocation().getY(), 
                    player.getLocation().getZ()));
        }
    }
}
