package kr.bapuri.tycoon.trade;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TradeCommand - /trade 명령어
 * 
 * 사용법:
 * - /trade <플레이어> - 거래 요청
 * - /trade accept - 거래 수락
 * - /trade deny - 거래 거절
 * - /trade cancel - 거래 취소
 * - /trade confirm - 거래 확정
 * - /trade history - 거래 기록 보기
 * 
 * [Phase 8 버그수정] TradeGui 의존성 추가 - 수락 시 GUI 열기
 */
public class TradeCommand implements CommandExecutor, TabCompleter {

    private final TradeService tradeService;
    private TradeGui tradeGui;
    
    public TradeCommand(TradeService tradeService) {
        this.tradeService = tradeService;
    }
    
    /**
     * TradeGui setter (순환 의존성 해결용)
     * TycoonPlugin에서 TradeGui 생성 후 주입
     */
    public void setTradeGui(TradeGui tradeGui) {
        this.tradeGui = tradeGui;
    }
    
    private static final String USE_PERMISSION = "tycoon.trade.use";
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능한 명령어입니다.");
            return true;
        }
        
        // 플레이어 권한 체크
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "accept", "수락", "y" -> {
                boolean accepted = tradeService.acceptRequest(player);
                // [Phase 8 버그수정] 수락 성공 시 양쪽 플레이어에게 GUI 열기
                if (accepted && tradeGui != null) {
                    TradeSession session = tradeService.getSession(player.getUniqueId());
                    if (session != null) {
                        Player player1 = Bukkit.getPlayer(session.getPlayer1Id());
                        Player player2 = Bukkit.getPlayer(session.getPlayer2Id());
                        
                        if (player1 != null) {
                            tradeGui.openTradeGui(player1, session);
                        }
                        if (player2 != null) {
                            tradeGui.openTradeGui(player2, session);
                        }
                    }
                }
            }
            case "deny", "거절", "n" -> {
                tradeService.declineRequest(player);
            }
            case "cancel", "취소" -> {
                if (tradeService.isInTrade(player.getUniqueId())) {
                    tradeService.cancelTrade(player.getUniqueId(), "플레이어가 취소했습니다");
                } else {
                    player.sendMessage("§c[거래] 진행 중인 거래가 없습니다.");
                }
            }
            case "confirm", "확정" -> {
                if (tradeService.isInTrade(player.getUniqueId())) {
                    tradeService.toggleConfirm(player);
                } else {
                    player.sendMessage("§c[거래] 진행 중인 거래가 없습니다.");
                }
            }
            case "complete", "완료" -> {
                tradeService.completeTrade(player);
            }
            case "history", "기록" -> {
                showHistory(player);
            }
            case "help", "도움말" -> {
                sendUsage(player);
            }
            default -> {
                // 플레이어에게 거래 요청
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage("§c[거래] 플레이어 '" + args[0] + "'을(를) 찾을 수 없습니다.");
                    return true;
                }
                
                TradeService.TradeRequestResult result = tradeService.sendRequest(player, target);
                
                switch (result) {
                    case SUCCESS -> { /* 메시지는 TradeService에서 전송 */ }
                    case DISABLED -> player.sendMessage("§c[거래] 거래 시스템이 비활성화되어 있습니다.");
                    case SELF_TRADE -> player.sendMessage("§c[거래] 자기 자신에게는 거래를 요청할 수 없습니다.");
                    case ALREADY_IN_TRADE -> player.sendMessage("§c[거래] 이미 거래가 진행 중입니다.");
                    case TARGET_IN_TRADE -> player.sendMessage("§c[거래] 상대방이 이미 다른 거래를 진행 중입니다.");
                    case COOLDOWN -> {
                        long remaining = tradeService.getCooldownManager().checkCooldown(
                                player.getUniqueId(), target.getUniqueId());
                        player.sendMessage("§c[거래] 쿨타임 중입니다. §e" + remaining + "초§c 후에 다시 시도하세요.");
                    }
                    case ALREADY_REQUESTED -> player.sendMessage("§c[거래] 이미 해당 플레이어에게 거래를 요청했습니다.");
                }
            }
        }
        
        return true;
    }
    
    private void sendUsage(Player player) {
        player.sendMessage("§6[거래 도움말]");
        player.sendMessage("§e/trade <플레이어> §7- 거래 요청");
        player.sendMessage("§e/trade accept §7- 거래 수락");
        player.sendMessage("§e/trade deny §7- 거래 거절");
        player.sendMessage("§e/trade cancel §7- 거래 취소");
        player.sendMessage("§e/trade confirm §7- 거래 확정 토글");
        player.sendMessage("§e/trade complete §7- 거래 완료");
        player.sendMessage("§e/trade history §7- 거래 기록 보기");
    }
    
    private void showHistory(Player player) {
        List<TradeHistoryEntry> history = tradeService.getHistoryManager()
                .getPlayerHistory(player.getUniqueId(), 10);
        
        if (history.isEmpty()) {
            player.sendMessage("§7[거래] 거래 기록이 없습니다.");
            return;
        }
        
        player.sendMessage("§6[최근 거래 기록]");
        
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
        
        for (TradeHistoryEntry entry : history) {
            String otherName = entry.getOtherPlayerName(player.getUniqueId());
            String timeStr = sdf.format(new Date(entry.getTimestamp()));
            
            boolean isPlayer1 = entry.getPlayer1Id().equals(player.getUniqueId());
            long myBd = isPlayer1 ? entry.getPlayer1Bd() : entry.getPlayer2Bd();
            long myBc = isPlayer1 ? entry.getPlayer1Bc() : entry.getPlayer2Bc();
            List<String> myItems = isPlayer1 ? entry.getPlayer1ItemDescriptions() : entry.getPlayer2ItemDescriptions();
            
            long otherBd = isPlayer1 ? entry.getPlayer2Bd() : entry.getPlayer1Bd();
            long otherBc = isPlayer1 ? entry.getPlayer2Bc() : entry.getPlayer1Bc();
            List<String> otherItems = isPlayer1 ? entry.getPlayer2ItemDescriptions() : entry.getPlayer1ItemDescriptions();
            
            player.sendMessage("§7" + timeStr + " §e" + otherName);
            player.sendMessage("  §c→ 줌: §f" + formatTradeContent(myBd, myBc, myItems));
            player.sendMessage("  §a← 받음: §f" + formatTradeContent(otherBd, otherBc, otherItems));
        }
    }
    
    private String formatTradeContent(long bd, long bc, List<String> items) {
        List<String> parts = new ArrayList<>();
        
        if (bd > 0) {
            parts.add(String.format("%,d BD", bd));
        }
        if (bc > 0) {
            parts.add(bc + " BC");
        }
        if (!items.isEmpty()) {
            parts.add("아이템 " + items.size() + "종");
        }
        
        if (parts.isEmpty()) {
            return "(없음)";
        }
        
        return String.join(", ", parts);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            
            List<String> completions = new ArrayList<>();
            completions.add("accept");
            completions.add("deny");
            completions.add("cancel");
            completions.add("confirm");
            completions.add("complete");
            completions.add("history");
            completions.add("help");
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equals(sender.getName())) {
                    completions.add(p.getName());
                }
            }
            
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        
        return List.of();
    }
}
