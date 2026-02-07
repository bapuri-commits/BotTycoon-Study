package kr.bapuri.tycoon.trade;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * TradeHistoryEntry - 거래 기록 엔트리
 * 
 * 완료된 거래의 기록을 저장합니다.
 */
public class TradeHistoryEntry {

    private final UUID tradeId;
    private final long timestamp;
    
    private final UUID player1Id;
    private final UUID player2Id;
    private final String player1Name;
    private final String player2Name;
    
    private final List<String> player1ItemDescriptions;
    private final long player1Bd;
    private final long player1Bc;
    
    private final List<String> player2ItemDescriptions;
    private final long player2Bd;
    private final long player2Bc;
    
    public TradeHistoryEntry(TradeSession session) {
        this.tradeId = session.getSessionId();
        this.timestamp = System.currentTimeMillis();
        
        this.player1Id = session.getPlayer1Id();
        this.player2Id = session.getPlayer2Id();
        this.player1Name = session.getPlayer1Name();
        this.player2Name = session.getPlayer2Name();
        
        this.player1ItemDescriptions = itemsToDescriptions(session.getPlayer1Items());
        this.player1Bd = session.getPlayer1Bd();
        this.player1Bc = session.getPlayer1Bc();
        
        this.player2ItemDescriptions = itemsToDescriptions(session.getPlayer2Items());
        this.player2Bd = session.getPlayer2Bd();
        this.player2Bc = session.getPlayer2Bc();
    }
    
    public TradeHistoryEntry(UUID tradeId, long timestamp,
                              UUID player1Id, UUID player2Id,
                              String player1Name, String player2Name,
                              List<String> player1ItemDescriptions, long player1Bd, long player1Bc,
                              List<String> player2ItemDescriptions, long player2Bd, long player2Bc) {
        this.tradeId = tradeId;
        this.timestamp = timestamp;
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.player1Name = player1Name;
        this.player2Name = player2Name;
        this.player1ItemDescriptions = player1ItemDescriptions;
        this.player1Bd = player1Bd;
        this.player1Bc = player1Bc;
        this.player2ItemDescriptions = player2ItemDescriptions;
        this.player2Bd = player2Bd;
        this.player2Bc = player2Bc;
    }
    
    private List<String> itemsToDescriptions(List<ItemStack> items) {
        List<String> descriptions = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                String name = item.getType().name();
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    name = item.getItemMeta().getDisplayName();
                }
                descriptions.add(name + " x" + item.getAmount());
            }
        }
        return descriptions;
    }
    
    public UUID getTradeId() { return tradeId; }
    public long getTimestamp() { return timestamp; }
    public UUID getPlayer1Id() { return player1Id; }
    public UUID getPlayer2Id() { return player2Id; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Name() { return player2Name; }
    public List<String> getPlayer1ItemDescriptions() { return player1ItemDescriptions; }
    public long getPlayer1Bd() { return player1Bd; }
    public long getPlayer1Bc() { return player1Bc; }
    public List<String> getPlayer2ItemDescriptions() { return player2ItemDescriptions; }
    public long getPlayer2Bd() { return player2Bd; }
    public long getPlayer2Bc() { return player2Bc; }
    
    public boolean isParticipant(UUID playerId) {
        return player1Id.equals(playerId) || player2Id.equals(playerId);
    }
    
    public String getOtherPlayerName(UUID playerId) {
        if (player1Id.equals(playerId)) {
            return player2Name;
        } else if (player2Id.equals(playerId)) {
            return player1Name;
        }
        return null;
    }
}
