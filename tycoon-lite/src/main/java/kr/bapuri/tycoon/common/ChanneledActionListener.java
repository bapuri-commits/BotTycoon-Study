package kr.bapuri.tycoon.common;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * ChanneledActionListener - 채널링 이벤트 리스너
 * 
 * 피격, 퇴장 이벤트 감지 후 채널링 취소 처리
 */
public class ChanneledActionListener implements Listener {

    private final ChanneledActionManager actionManager;

    public ChanneledActionListener(ChanneledActionManager actionManager) {
        this.actionManager = actionManager;
    }

    /**
     * 플레이어 피격 시 채널링 취소
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        
        if (actionManager.isChanneling(player)) {
            actionManager.onPlayerDamaged(player.getUniqueId());
        }
    }

    /**
     * 플레이어 퇴장 시 채널링 정리
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        actionManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}

