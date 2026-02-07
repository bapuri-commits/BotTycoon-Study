package kr.bapuri.tycoon.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * TradeSession - 거래 세션 데이터
 * 
 * 양쪽 플레이어의 거래 상태를 추적합니다.
 */
public class TradeSession {

    private final UUID sessionId;
    private final long createdAt;
    
    // 참가자
    private final UUID player1Id;
    private final UUID player2Id;
    private final String player1Name;
    private final String player2Name;
    
    // GUI
    private Inventory player1Gui;
    private Inventory player2Gui;
    
    // Player1이 줄 것
    private final List<ItemStack> player1Items = new ArrayList<>();
    private long player1Bd = 0;
    private long player1Bc = 0;
    private boolean player1Confirmed = false;
    
    // Player2가 줄 것
    private final List<ItemStack> player2Items = new ArrayList<>();
    private long player2Bd = 0;
    private long player2Bc = 0;
    private boolean player2Confirmed = false;
    
    // 상태
    private TradeState state = TradeState.ACTIVE;
    
    public TradeSession(Player player1, Player player2) {
        this.sessionId = UUID.randomUUID();
        this.createdAt = System.currentTimeMillis();
        this.player1Id = player1.getUniqueId();
        this.player2Id = player2.getUniqueId();
        this.player1Name = player1.getName();
        this.player2Name = player2.getName();
    }
    
    // ================================================================================
    // Getters
    // ================================================================================
    
    public UUID getSessionId() {
        return sessionId;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public UUID getPlayer1Id() {
        return player1Id;
    }
    
    public UUID getPlayer2Id() {
        return player2Id;
    }
    
    public String getPlayer1Name() {
        return player1Name;
    }
    
    public String getPlayer2Name() {
        return player2Name;
    }
    
    public Inventory getPlayer1Gui() {
        return player1Gui;
    }
    
    public Inventory getPlayer2Gui() {
        return player2Gui;
    }
    
    public void setPlayer1Gui(Inventory gui) {
        this.player1Gui = gui;
    }
    
    public void setPlayer2Gui(Inventory gui) {
        this.player2Gui = gui;
    }
    
    // ================================================================================
    // Player1 거래 내용
    // ================================================================================
    
    public List<ItemStack> getPlayer1Items() {
        return player1Items;
    }
    
    public long getPlayer1Bd() {
        return player1Bd;
    }
    
    public void setPlayer1Bd(long bd) {
        this.player1Bd = bd;
        if (player1Confirmed) {
            player1Confirmed = false;
        }
    }
    
    public long getPlayer1Bc() {
        return player1Bc;
    }
    
    public void setPlayer1Bc(long bc) {
        this.player1Bc = bc;
        if (player1Confirmed) {
            player1Confirmed = false;
        }
    }
    
    public boolean isPlayer1Confirmed() {
        return player1Confirmed;
    }
    
    public void setPlayer1Confirmed(boolean confirmed) {
        this.player1Confirmed = confirmed;
    }
    
    // ================================================================================
    // Player2 거래 내용
    // ================================================================================
    
    public List<ItemStack> getPlayer2Items() {
        return player2Items;
    }
    
    public long getPlayer2Bd() {
        return player2Bd;
    }
    
    public void setPlayer2Bd(long bd) {
        this.player2Bd = bd;
        if (player2Confirmed) {
            player2Confirmed = false;
        }
    }
    
    public long getPlayer2Bc() {
        return player2Bc;
    }
    
    public void setPlayer2Bc(long bc) {
        this.player2Bc = bc;
        if (player2Confirmed) {
            player2Confirmed = false;
        }
    }
    
    public boolean isPlayer2Confirmed() {
        return player2Confirmed;
    }
    
    public void setPlayer2Confirmed(boolean confirmed) {
        this.player2Confirmed = confirmed;
    }
    
    // ================================================================================
    // 상태 관리
    // ================================================================================
    
    public TradeState getState() {
        return state;
    }
    
    public void setState(TradeState state) {
        this.state = state;
    }
    
    public boolean isBothConfirmed() {
        return player1Confirmed && player2Confirmed;
    }
    
    public boolean isPlayer1(UUID playerId) {
        return player1Id.equals(playerId);
    }
    
    public boolean isPlayer2(UUID playerId) {
        return player2Id.equals(playerId);
    }
    
    public boolean isParticipant(UUID playerId) {
        return player1Id.equals(playerId) || player2Id.equals(playerId);
    }
    
    public UUID getOtherPlayer(UUID playerId) {
        if (player1Id.equals(playerId)) {
            return player2Id;
        } else if (player2Id.equals(playerId)) {
            return player1Id;
        }
        return null;
    }
    
    public String getOtherPlayerName(UUID playerId) {
        if (player1Id.equals(playerId)) {
            return player2Name;
        } else if (player2Id.equals(playerId)) {
            return player1Name;
        }
        return null;
    }
    
    public void onPlayer1ItemsChanged() {
        if (player1Confirmed) {
            player1Confirmed = false;
        }
    }
    
    public void onPlayer2ItemsChanged() {
        if (player2Confirmed) {
            player2Confirmed = false;
        }
    }
    
    public enum TradeState {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
}
