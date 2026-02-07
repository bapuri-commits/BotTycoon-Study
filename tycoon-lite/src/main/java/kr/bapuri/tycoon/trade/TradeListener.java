package kr.bapuri.tycoon.trade;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * TradeListener - 거래 관련 이벤트 리스너 (LITE)
 * 
 * - 채팅 수락/거절 처리 (y/n)
 * - 화폐 입력 처리 (TradeGui)
 * - 플레이어 로그아웃 처리
 * - 거래 수락 시 GUI 열기
 */
public class TradeListener implements Listener {

    private final Plugin plugin;
    private final TradeService tradeService;
    private final TradeGui tradeGui;
    
    public TradeListener(Plugin plugin, TradeService tradeService, TradeGui tradeGui) {
        this.plugin = plugin;
        this.tradeService = tradeService;
        this.tradeGui = tradeGui;
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String message = event.getMessage().trim();
        
        // 1. 화폐 입력 대기 중인지 확인 (GUI에서 BD/BC 직접 입력)
        if (tradeGui.isPendingCurrencyInput(playerId)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                tradeGui.handleCurrencyInput(player, message);
            });
            return;
        }
        
        String lowerMsg = message.toLowerCase();
        
        // 2. 거래 수락/거절 확인 (채팅으로)
        if (lowerMsg.equals("y") || lowerMsg.equals("ㅛ")) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                boolean accepted = tradeService.acceptRequest(player);
                if (accepted) {
                    // 수락 성공 시 양쪽 플레이어에게 GUI 열기
                    TradeSession session = tradeService.getSession(playerId);
                    if (session != null) {
                        Player player1 = plugin.getServer().getPlayer(session.getPlayer1Id());
                        Player player2 = plugin.getServer().getPlayer(session.getPlayer2Id());
                        
                        if (player1 != null) {
                            tradeGui.openTradeGui(player1, session);
                        }
                        if (player2 != null) {
                            tradeGui.openTradeGui(player2, session);
                        }
                    }
                }
            });
            return;
        }
        
        if (lowerMsg.equals("n") || lowerMsg.equals("ㅜ")) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                tradeService.declineRequest(player);
            });
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        tradeService.handlePlayerQuit(player.getUniqueId());
    }
}
