package kr.bapuri.tycoon.trade;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * TradeService - 거래 시스템 핵심 서비스
 * 
 * 거래 요청, 수락, 진행, 완료, 취소를 관리합니다.
 * [Phase 5 개선] 트랜잭션 로깅 지원 (수동 롤백용)
 */
public class TradeService {

    private final Plugin plugin;
    private final Logger logger;
    private final EconomyService economyService;
    private final PlayerDataManager playerDataManager;
    private final TradeCooldownManager cooldownManager;
    private final TradeHistoryManager historyManager;
    private final TradeTransactionLogger txnLogger;
    
    // 대기 중인 거래 요청 (targetUUID -> request)
    private final Map<UUID, TradeRequest> pendingRequests = new ConcurrentHashMap<>();
    
    // 진행 중인 거래 세션 (playerUUID -> session)
    private final Map<UUID, TradeSession> activeSessions = new ConcurrentHashMap<>();
    
    private long requestTimeoutSeconds = 60;
    private boolean enabled = true;
    
    public TradeService(Plugin plugin, EconomyService economyService, 
                        PlayerDataManager playerDataManager,
                        TradeCooldownManager cooldownManager,
                        TradeHistoryManager historyManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.economyService = economyService;
        this.playerDataManager = playerDataManager;
        this.cooldownManager = cooldownManager;
        this.historyManager = historyManager;
        this.txnLogger = new TradeTransactionLogger(plugin);
        
        startCleanupTask();
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setRequestTimeout(long seconds) {
        this.requestTimeoutSeconds = seconds;
    }
    
    // ================================================================================
    // 거래 요청
    // ================================================================================
    
    public TradeRequestResult sendRequest(Player sender, Player target) {
        if (!enabled) {
            return TradeRequestResult.DISABLED;
        }
        
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        
        if (senderId.equals(targetId)) {
            return TradeRequestResult.SELF_TRADE;
        }
        
        if (isInTrade(senderId)) {
            return TradeRequestResult.ALREADY_IN_TRADE;
        }
        if (isInTrade(targetId)) {
            return TradeRequestResult.TARGET_IN_TRADE;
        }
        
        long cooldown = cooldownManager.checkCooldown(senderId, targetId);
        if (cooldown > 0) {
            return TradeRequestResult.COOLDOWN;
        }
        
        if (hasPendingRequest(targetId, senderId)) {
            return TradeRequestResult.ALREADY_REQUESTED;
        }
        
        TradeRequest request = new TradeRequest(senderId, targetId, 
                sender.getName(), target.getName(), requestTimeoutSeconds);
        pendingRequests.put(targetId, request);
        
        cooldownManager.recordRequest(senderId, targetId);
        
        target.sendMessage("§e[거래] §f" + sender.getName() + "§e님이 거래를 요청했습니다.");
        target.sendMessage("§a채팅에 y§7로 수락, §c채팅에 n§7으로 거절 (§e" + requestTimeoutSeconds + "초§7 내)");
        
        sender.sendMessage("§e[거래] §f" + target.getName() + "§e님에게 거래를 요청했습니다.");
        sender.sendMessage("§7상대방의 응답을 기다리는 중... (" + requestTimeoutSeconds + "초)");
        
        logger.info("[거래 요청] " + sender.getName() + " -> " + target.getName());
        
        return TradeRequestResult.SUCCESS;
    }
    
    public boolean acceptRequest(Player target) {
        UUID targetId = target.getUniqueId();
        TradeRequest request = pendingRequests.remove(targetId);
        
        if (request == null) {
            target.sendMessage("§c[거래] 대기 중인 거래 요청이 없습니다.");
            return false;
        }
        
        if (request.isExpired()) {
            target.sendMessage("§c[거래] 거래 요청이 만료되었습니다.");
            return false;
        }
        
        Player sender = Bukkit.getPlayer(request.getSenderId());
        if (sender == null || !sender.isOnline()) {
            target.sendMessage("§c[거래] 상대방이 오프라인입니다.");
            return false;
        }
        
        if (isInTrade(request.getSenderId()) || isInTrade(targetId)) {
            target.sendMessage("§c[거래] 이미 다른 거래가 진행 중입니다.");
            return false;
        }
        
        TradeSession session = new TradeSession(sender, target);
        activeSessions.put(request.getSenderId(), session);
        activeSessions.put(targetId, session);
        
        // 트랜잭션 로그: 거래 시작
        txnLogger.logTradeStart(session);
        
        sender.sendMessage("§a[거래] " + target.getName() + "님이 거래를 수락했습니다!");
        target.sendMessage("§a[거래] " + sender.getName() + "님과의 거래를 시작합니다!");
        
        logger.info("[거래 수락] " + sender.getName() + " <-> " + target.getName());
        
        return true;
    }
    
    public boolean declineRequest(Player target) {
        UUID targetId = target.getUniqueId();
        TradeRequest request = pendingRequests.remove(targetId);
        
        if (request == null) {
            target.sendMessage("§c[거래] 대기 중인 거래 요청이 없습니다.");
            return false;
        }
        
        Player sender = Bukkit.getPlayer(request.getSenderId());
        if (sender != null && sender.isOnline()) {
            sender.sendMessage("§c[거래] " + target.getName() + "님이 거래를 거절했습니다.");
        }
        
        target.sendMessage("§7[거래] 거래 요청을 거절했습니다.");
        
        return true;
    }
    
    // ================================================================================
    // 거래 진행
    // ================================================================================
    
    public TradeSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }
    
    public boolean isInTrade(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }
    
    public boolean hasPendingRequest(UUID targetId, UUID senderId) {
        TradeRequest request = pendingRequests.get(targetId);
        return request != null && request.getSenderId().equals(senderId) && !request.isExpired();
    }
    
    public void toggleConfirm(Player player) {
        UUID playerId = player.getUniqueId();
        TradeSession session = activeSessions.get(playerId);
        
        if (session == null) {
            return;
        }
        
        boolean isPlayer1 = session.isPlayer1(playerId);
        boolean currentlyConfirmed = isPlayer1 ? session.isPlayer1Confirmed() : session.isPlayer2Confirmed();
        
        if (currentlyConfirmed) {
            if (isPlayer1) {
                session.setPlayer1Confirmed(false);
            } else {
                session.setPlayer2Confirmed(false);
            }
            player.sendMessage("§7[거래] 확정을 취소했습니다.");
        } else {
            if (isPlayer1) {
                session.setPlayer1Confirmed(true);
            } else {
                session.setPlayer2Confirmed(true);
            }
            player.sendMessage("§a[거래] 거래 내용을 확정했습니다.");
            
            Player other = Bukkit.getPlayer(session.getOtherPlayer(playerId));
            if (other != null) {
                other.sendMessage("§e[거래] " + player.getName() + "님이 거래를 확정했습니다.");
            }
        }
    }
    
    public boolean completeTrade(Player player) {
        UUID playerId = player.getUniqueId();
        TradeSession session = activeSessions.get(playerId);
        
        if (session == null) {
            player.sendMessage("§c[거래] 진행 중인 거래가 없습니다.");
            return false;
        }
        
        if (!session.isBothConfirmed()) {
            player.sendMessage("§c[거래] 양쪽 모두 확정해야 거래를 완료할 수 있습니다.");
            return false;
        }
        
        Player player1 = Bukkit.getPlayer(session.getPlayer1Id());
        Player player2 = Bukkit.getPlayer(session.getPlayer2Id());
        
        if (player1 == null || player2 == null) {
            cancelTrade(playerId, "상대방이 오프라인입니다.");
            return false;
        }
        
        // 인벤토리 공간 확인
        int player1NeedsSlots = countItems(session.getPlayer2Items());
        int player2NeedsSlots = countItems(session.getPlayer1Items());
        
        int player1EmptySlots = countEmptySlots(player1);
        int player2EmptySlots = countEmptySlots(player2);
        
        int player1GivingSlots = countItems(session.getPlayer1Items());
        int player2GivingSlots = countItems(session.getPlayer2Items());
        
        if (player1EmptySlots + player1GivingSlots < player1NeedsSlots) {
            player1.sendMessage("§c[거래] 인벤토리 공간이 부족합니다!");
            player2.sendMessage("§c[거래] 상대방의 인벤토리 공간이 부족합니다.");
            return false;
        }
        
        if (player2EmptySlots + player2GivingSlots < player2NeedsSlots) {
            player2.sendMessage("§c[거래] 인벤토리 공간이 부족합니다!");
            player1.sendMessage("§c[거래] 상대방의 인벤토리 공간이 부족합니다.");
            return false;
        }
        
        // 잔액 확인
        PlayerTycoonData data1 = playerDataManager.get(session.getPlayer1Id());
        PlayerTycoonData data2 = playerDataManager.get(session.getPlayer2Id());
        
        if (data1.getMoney() < session.getPlayer1Bd()) {
            player1.sendMessage("§c[거래] BD가 부족합니다!");
            return false;
        }
        if (data1.getBottCoin() < session.getPlayer1Bc()) {
            player1.sendMessage("§c[거래] BottCoin이 부족합니다!");
            return false;
        }
        if (data2.getMoney() < session.getPlayer2Bd()) {
            player2.sendMessage("§c[거래] BD가 부족합니다!");
            return false;
        }
        if (data2.getBottCoin() < session.getPlayer2Bc()) {
            player2.sendMessage("§c[거래] BottCoin이 부족합니다!");
            return false;
        }
        
        // 거래 실행 (원자성 보장)
        String baseTxnId = "trade_" + session.getSessionId() + "_" + System.currentTimeMillis();
        boolean allSuccess = true;
        
        // Player1 BD → Player2
        if (session.getPlayer1Bd() > 0) {
            String txnId1 = baseTxnId + "_p1_bd";
            boolean withdrawn = economyService.withdrawIdempotent(
                    session.getPlayer1Id(), session.getPlayer1Bd(), txnId1,
                    "P2P거래", player2.getName() + "에게");
            txnLogger.logCurrencyTransfer(session.getSessionId(), 
                    session.getPlayer1Id(), player1.getName(),
                    session.getPlayer2Id(), player2.getName(),
                    "BD", session.getPlayer1Bd(), txnId1, withdrawn);
            if (withdrawn) {
                String txnId2 = baseTxnId + "_p1_bd_recv";
                economyService.depositIdempotent(
                        session.getPlayer2Id(), session.getPlayer1Bd(), txnId2,
                        "P2P거래", player1.getName() + "로부터");
            } else {
                allSuccess = false;
            }
        }
        
        // Player2 BD → Player1
        if (allSuccess && session.getPlayer2Bd() > 0) {
            String txnId1 = baseTxnId + "_p2_bd";
            boolean withdrawn = economyService.withdrawIdempotent(
                    session.getPlayer2Id(), session.getPlayer2Bd(), txnId1,
                    "P2P거래", player1.getName() + "에게");
            txnLogger.logCurrencyTransfer(session.getSessionId(),
                    session.getPlayer2Id(), player2.getName(),
                    session.getPlayer1Id(), player1.getName(),
                    "BD", session.getPlayer2Bd(), txnId1, withdrawn);
            if (withdrawn) {
                String txnId2 = baseTxnId + "_p2_bd_recv";
                economyService.depositIdempotent(
                        session.getPlayer1Id(), session.getPlayer2Bd(), txnId2,
                        "P2P거래", player2.getName() + "로부터");
            } else {
                allSuccess = false;
            }
        }
        
        // Player1 BC → Player2
        if (allSuccess && session.getPlayer1Bc() > 0) {
            String txnId1 = baseTxnId + "_p1_bc";
            boolean withdrawn = economyService.withdrawBottCoinIdempotent(
                    session.getPlayer1Id(), session.getPlayer1Bc(), txnId1,
                    "P2P거래", player2.getName() + "에게");
            txnLogger.logCurrencyTransfer(session.getSessionId(),
                    session.getPlayer1Id(), player1.getName(),
                    session.getPlayer2Id(), player2.getName(),
                    "BottCoin", session.getPlayer1Bc(), txnId1, withdrawn);
            if (withdrawn) {
                economyService.depositBottCoin(
                        session.getPlayer2Id(), session.getPlayer1Bc(),
                        "P2P거래", player1.getName() + "로부터");
            } else {
                allSuccess = false;
            }
        }
        
        // Player2 BC → Player1
        if (allSuccess && session.getPlayer2Bc() > 0) {
            String txnId1 = baseTxnId + "_p2_bc";
            boolean withdrawn = economyService.withdrawBottCoinIdempotent(
                    session.getPlayer2Id(), session.getPlayer2Bc(), txnId1,
                    "P2P거래", player1.getName() + "에게");
            txnLogger.logCurrencyTransfer(session.getSessionId(),
                    session.getPlayer2Id(), player2.getName(),
                    session.getPlayer1Id(), player1.getName(),
                    "BottCoin", session.getPlayer2Bc(), txnId1, withdrawn);
            if (withdrawn) {
                economyService.depositBottCoin(
                        session.getPlayer1Id(), session.getPlayer2Bc(),
                        "P2P거래", player2.getName() + "로부터");
            } else {
                allSuccess = false;
            }
        }
        
        if (!allSuccess) {
            txnLogger.logRollbackNeeded(session.getSessionId(), 
                    "화폐 교환 중 오류 발생 - 일부 트랜잭션 완료됨, 수동 롤백 필요 가능");
            cancelTrade(playerId, "화폐 교환 중 오류 발생");
            return false;
        }
        
        // 아이템 교환 로그 (교환 전)
        if (countItems(session.getPlayer1Items()) > 0) {
            txnLogger.logItemTransfer(session.getSessionId(),
                    session.getPlayer1Id(), player1.getName(),
                    session.getPlayer2Id(), player2.getName(),
                    session.getPlayer1Items());
        }
        if (countItems(session.getPlayer2Items()) > 0) {
            txnLogger.logItemTransfer(session.getSessionId(),
                    session.getPlayer2Id(), player2.getName(),
                    session.getPlayer1Id(), player1.getName(),
                    session.getPlayer2Items());
        }
        
        // 아이템 교환
        for (ItemStack item : session.getPlayer1Items()) {
            if (item != null && !item.getType().isAir()) {
                HashMap<Integer, ItemStack> leftover = player2.getInventory().addItem(item.clone());
                for (ItemStack drop : leftover.values()) {
                    player2.getWorld().dropItemNaturally(player2.getLocation(), drop);
                }
            }
        }
        for (ItemStack item : session.getPlayer2Items()) {
            if (item != null && !item.getType().isAir()) {
                HashMap<Integer, ItemStack> leftover = player1.getInventory().addItem(item.clone());
                for (ItemStack drop : leftover.values()) {
                    player1.getWorld().dropItemNaturally(player1.getLocation(), drop);
                }
            }
        }
        
        // 거래 기록 저장
        session.setState(TradeSession.TradeState.COMPLETED);
        historyManager.saveTradeHistory(new TradeHistoryEntry(session));
        
        // 트랜잭션 로그: 거래 완료
        txnLogger.logTradeComplete(session);
        
        // 세션 정리
        activeSessions.remove(session.getPlayer1Id());
        activeSessions.remove(session.getPlayer2Id());
        
        player1.closeInventory();
        player2.closeInventory();
        
        player1.sendMessage("§a[거래] " + player2.getName() + "님과의 거래가 완료되었습니다!");
        player2.sendMessage("§a[거래] " + player1.getName() + "님과의 거래가 완료되었습니다!");
        
        logger.info("[거래 완료] " + player1.getName() + " <-> " + player2.getName());
        
        return true;
    }
    
    public void cancelTrade(UUID playerId, String reason) {
        TradeSession session = activeSessions.get(playerId);
        
        if (session == null) {
            return;
        }
        
        Player player1 = Bukkit.getPlayer(session.getPlayer1Id());
        Player player2 = Bukkit.getPlayer(session.getPlayer2Id());
        
        if (player1 != null) {
            returnItems(player1, session.getPlayer1Items());
            player1.closeInventory();
            player1.sendMessage("§c[거래] 거래가 취소되었습니다. §7(" + reason + ")");
        }
        
        if (player2 != null) {
            returnItems(player2, session.getPlayer2Items());
            player2.closeInventory();
            player2.sendMessage("§c[거래] 거래가 취소되었습니다. §7(" + reason + ")");
        }
        
        session.setState(TradeSession.TradeState.CANCELLED);
        
        // 트랜잭션 로그: 거래 취소
        txnLogger.logTradeCancelled(session, reason);
        
        activeSessions.remove(session.getPlayer1Id());
        activeSessions.remove(session.getPlayer2Id());
        
        logger.info("[거래 취소] " + session.getPlayer1Name() + " <-> " + session.getPlayer2Name() + " | 사유: " + reason);
    }
    
    // ================================================================================
    // 유틸리티
    // ================================================================================
    
    private void returnItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
    }
    
    private int countItems(List<ItemStack> items) {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }
    
    private int countEmptySlots(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }
    
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                pendingRequests.entrySet().removeIf(entry -> {
                    TradeRequest request = entry.getValue();
                    if (request.isExpired()) {
                        Player sender = Bukkit.getPlayer(request.getSenderId());
                        if (sender != null) {
                            sender.sendMessage("§c[거래] " + request.getTargetName() + "님에게 보낸 거래 요청이 만료되었습니다.");
                        }
                        return true;
                    }
                    return false;
                });
                
                cooldownManager.cleanup();
            }
        }.runTaskTimer(plugin, 20L * 10, 20L * 10);
    }
    
    public void handlePlayerQuit(UUID playerId) {
        pendingRequests.values().removeIf(request -> 
                request.getSenderId().equals(playerId) || request.getTargetId().equals(playerId));
        
        if (isInTrade(playerId)) {
            cancelTrade(playerId, "플레이어가 로그아웃했습니다");
        }
    }
    
    public TradeCooldownManager getCooldownManager() {
        return cooldownManager;
    }
    
    public TradeHistoryManager getHistoryManager() {
        return historyManager;
    }
    
    public long getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }
    
    public enum TradeRequestResult {
        SUCCESS,
        DISABLED,
        SELF_TRADE,
        ALREADY_IN_TRADE,
        TARGET_IN_TRADE,
        COOLDOWN,
        ALREADY_REQUESTED
    }
}
