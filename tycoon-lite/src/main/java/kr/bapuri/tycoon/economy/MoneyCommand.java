package kr.bapuri.tycoon.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MoneyCommand - 플레이어용 잔액 확인 명령어
 * 
 * 사용법:
 * - /money           - 본인 잔액 확인
 * - /money <player>  - 다른 플레이어 잔액 확인 (관리자만)
 * 
 * 별칭: /돈, /잔액, /bal, /balance
 */
public class MoneyCommand implements CommandExecutor, TabCompleter {

    private final EconomyService economyService;
    
    private static final String ADMIN_PERMISSION = "tycoon.admin.eco";

    public MoneyCommand(EconomyService economyService) {
        this.economyService = economyService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 콘솔에서 실행 시 플레이어 지정 필요
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage("§c사용법: /money <player>");
            return true;
        }
        
        // /money (본인 잔액)
        if (args.length == 0) {
            Player player = (Player) sender;
            showBalance(sender, player.getUniqueId(), player.getName());
            return true;
        }
        
        // /money <player> (다른 플레이어 잔액 - 관리자만)
        if (args.length >= 1) {
            // 관리자가 아니면 다른 플레이어 조회 불가
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage("§c다른 플레이어의 잔액을 조회할 권한이 없습니다.");
                return true;
            }
            
            UUID uuid = getPlayerUuid(args[0]);
            if (uuid == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[0]);
                return true;
            }
            
            showBalance(sender, uuid, args[0]);
            return true;
        }
        
        return true;
    }
    
    /**
     * 잔액 표시
     */
    private void showBalance(CommandSender sender, UUID uuid, String playerName) {
        long bd = economyService.getMoney(uuid);
        long bottCoin = economyService.getBottCoin(uuid);
        
        sender.sendMessage("");
        sender.sendMessage("§e§l[ " + playerName + " 잔액 ]");
        sender.sendMessage("");
        sender.sendMessage("  §7BD: §f§l" + formatNumber(bd) + " §7BD");
        sender.sendMessage("  §7BottCoin: §d§l" + formatNumber(bottCoin) + " §7BC");
        sender.sendMessage("");
    }
    
    /**
     * 숫자 포맷팅 (천 단위 콤마)
     */
    private String formatNumber(long number) {
        return String.format("%,d", number);
    }
    
    /**
     * 플레이어 UUID 조회
     */
    private UUID getPlayerUuid(String name) {
        // 온라인 플레이어 먼저 확인
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        
        // 오프라인 플레이어 확인
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        // 관리자만 플레이어 자동완성
        if (args.length == 1 && sender.hasPermission(ADMIN_PERMISSION)) {
            String partial = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        }
        
        return completions;
    }
}
