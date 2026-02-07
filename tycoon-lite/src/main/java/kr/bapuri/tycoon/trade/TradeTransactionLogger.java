package kr.bapuri.tycoon.trade;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * TradeTransactionLogger - 거래 트랜잭션 로그 (수동 롤백 지원)
 * 
 * 거래 시작, 화폐 이동, 아이템 이동, 완료/취소를 상세히 기록합니다.
 * 문제 발생 시 관리자가 수동 롤백할 수 있도록 모든 정보를 기록합니다.
 * 
 * 로그 경로: plugins/TycoonLite/logs/trade/YYYY-MM-DD.log
 */
public class TradeTransactionLogger {

    private final Plugin plugin;
    private final Logger logger;
    private final File logDir;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    
    public TradeTransactionLogger(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.logDir = new File(plugin.getDataFolder(), "logs/trade");
        
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }
    
    /**
     * 거래 시작 로그
     */
    public void logTradeStart(TradeSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TRADE START ===\n");
        sb.append("SessionID: ").append(session.getSessionId()).append("\n");
        sb.append("Player1: ").append(session.getPlayer1Name())
          .append(" (").append(session.getPlayer1Id()).append(")\n");
        sb.append("Player2: ").append(session.getPlayer2Name())
          .append(" (").append(session.getPlayer2Id()).append(")\n");
        sb.append("StartTime: ").append(timeFormat.format(new Date())).append("\n");
        sb.append("===================\n");
        
        writeLog(sb.toString());
    }
    
    /**
     * 화폐 이동 전 로그 (롤백용)
     */
    public void logCurrencyTransfer(UUID sessionId, 
                                    UUID fromUuid, String fromName, 
                                    UUID toUuid, String toName,
                                    String currencyType, long amount, 
                                    String txnId, boolean success) {
        StringBuilder sb = new StringBuilder();
        sb.append("[CURRENCY] SessionID: ").append(sessionId).append("\n");
        sb.append("  Type: ").append(currencyType).append("\n");
        sb.append("  From: ").append(fromName).append(" (").append(fromUuid).append(")\n");
        sb.append("  To: ").append(toName).append(" (").append(toUuid).append(")\n");
        sb.append("  Amount: ").append(amount).append("\n");
        sb.append("  TxnID: ").append(txnId).append("\n");
        sb.append("  Result: ").append(success ? "SUCCESS" : "FAILED").append("\n");
        sb.append("  Time: ").append(timeFormat.format(new Date())).append("\n");
        
        writeLog(sb.toString());
    }
    
    /**
     * 아이템 이동 로그 (롤백용)
     */
    public void logItemTransfer(UUID sessionId,
                                UUID fromUuid, String fromName,
                                UUID toUuid, String toName,
                                List<ItemStack> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ITEMS] SessionID: ").append(sessionId).append("\n");
        sb.append("  From: ").append(fromName).append(" (").append(fromUuid).append(")\n");
        sb.append("  To: ").append(toName).append(" (").append(toUuid).append(")\n");
        sb.append("  Items:\n");
        
        int idx = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                sb.append("    [").append(idx++).append("] ")
                  .append(item.getType().name())
                  .append(" x").append(item.getAmount());
                
                // 인챈트/메타 정보
                if (item.hasItemMeta()) {
                    if (item.getItemMeta().hasDisplayName()) {
                        sb.append(" (이름: ").append(item.getItemMeta().getDisplayName()).append(")");
                    }
                    if (item.getItemMeta().hasEnchants()) {
                        sb.append(" [인챈트: ").append(item.getItemMeta().getEnchants().size()).append("개]");
                    }
                }
                sb.append("\n");
            }
        }
        
        sb.append("  Time: ").append(timeFormat.format(new Date())).append("\n");
        
        writeLog(sb.toString());
    }
    
    /**
     * 거래 완료 로그
     */
    public void logTradeComplete(TradeSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TRADE COMPLETE ===\n");
        sb.append("SessionID: ").append(session.getSessionId()).append("\n");
        sb.append("Player1 (").append(session.getPlayer1Name()).append("):\n");
        sb.append("  BD 제공: ").append(session.getPlayer1Bd()).append("\n");
        sb.append("  BC 제공: ").append(session.getPlayer1Bc()).append("\n");
        sb.append("  아이템 제공: ").append(countItems(session.getPlayer1Items())).append("개\n");
        sb.append("Player2 (").append(session.getPlayer2Name()).append("):\n");
        sb.append("  BD 제공: ").append(session.getPlayer2Bd()).append("\n");
        sb.append("  BC 제공: ").append(session.getPlayer2Bc()).append("\n");
        sb.append("  아이템 제공: ").append(countItems(session.getPlayer2Items())).append("개\n");
        sb.append("CompleteTime: ").append(timeFormat.format(new Date())).append("\n");
        sb.append("======================\n");
        
        writeLog(sb.toString());
    }
    
    /**
     * 거래 취소/실패 로그
     */
    public void logTradeCancelled(TradeSession session, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TRADE CANCELLED ===\n");
        sb.append("SessionID: ").append(session.getSessionId()).append("\n");
        sb.append("Reason: ").append(reason).append("\n");
        sb.append("Player1: ").append(session.getPlayer1Name()).append("\n");
        sb.append("Player2: ").append(session.getPlayer2Name()).append("\n");
        sb.append("State: ").append(session.getState()).append("\n");
        sb.append("CancelTime: ").append(timeFormat.format(new Date())).append("\n");
        sb.append("=======================\n");
        
        writeLog(sb.toString());
    }
    
    /**
     * 롤백 필요 알림 로그
     */
    public void logRollbackNeeded(UUID sessionId, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("!!! ROLLBACK NEEDED !!!\n");
        sb.append("SessionID: ").append(sessionId).append("\n");
        sb.append("Message: ").append(message).append("\n");
        sb.append("Time: ").append(timeFormat.format(new Date())).append("\n");
        sb.append("Please review above transactions and perform manual rollback if needed.\n");
        sb.append("!!!!!!!!!!!!!!!!!!!!!!!!\n");
        
        writeLog(sb.toString());
        
        // 콘솔에도 경고
        logger.warning("[Trade] ROLLBACK NEEDED - SessionID: " + sessionId + " - " + message);
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
    
    private void writeLog(String message) {
        String fileName = dateFormat.format(new Date()) + ".log";
        File logFile = new File(logDir, fileName);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("[" + timeFormat.format(new Date()) + "]");
            writer.println(message);
            writer.println();
        } catch (IOException e) {
            logger.warning("[TradeTransactionLogger] 로그 기록 실패: " + e.getMessage());
        }
    }
}
