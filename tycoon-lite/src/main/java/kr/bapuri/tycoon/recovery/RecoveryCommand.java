package kr.bapuri.tycoon.recovery;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * RecoveryCommand - /recovery 명령어 (관리자 전용)
 * 
 * [Phase 8] 레거시에서 이식 및 리팩토링
 * 
 * 관리자용 Town 보관소 관리 명령어.
 * 일반 플레이어는 NPC를 통해서만 접근 가능.
 * 
 * 권한: tycoon.admin.recovery
 * 
 * 사용법:
 * - /recovery - GUI 열기 (관리자)
 * - /recovery list - 목록 확인 (관리자)
 * - /recovery count - 개수 확인 (관리자)
 * - /recovery <플레이어> - 다른 플레이어 보관소 열기 (관리자)
 */
public class RecoveryCommand implements CommandExecutor, TabCompleter {

    private final RecoveryGui recoveryGui;
    private final RecoveryStorageManager storageManager;

    public RecoveryCommand(RecoveryGui recoveryGui, RecoveryStorageManager storageManager) {
        this.recoveryGui = recoveryGui;
        this.storageManager = storageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        // [Phase 8] 모든 /recovery 명령어는 관리자 전용
        // 일반 플레이어는 NPC를 통해서만 접근 가능
        if (!player.hasPermission("tycoon.admin.recovery")) {
            player.sendMessage(ChatColor.RED + "이 명령어를 사용할 권한이 없습니다.");
            player.sendMessage(ChatColor.GRAY + "Town의 보관소 NPC를 통해 접근해주세요.");
            return true;
        }

        // 서브커맨드 처리
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            
            if (sub.equals("list") || sub.equals("목록")) {
                listEntries(player);
                return true;
            }
            
            if (sub.equals("count") || sub.equals("개수")) {
                int count = storageManager.getUnclaimedCount(player.getUniqueId());
                player.sendMessage(ChatColor.GOLD + "[Town 보관소] " + ChatColor.WHITE + 
                        "보관된 아이템: " + count + "개 엔트리");
                return true;
            }
            
            // 다른 플레이어 보관소 열기 (관리자 기능)
            Player target = org.bukkit.Bukkit.getPlayer(args[0]);
            if (target != null) {
                player.sendMessage(ChatColor.GOLD + "[Town 보관소] " + ChatColor.WHITE + 
                        target.getName() + "님의 보관소를 엽니다.");
                recoveryGui.openFor(player, target.getUniqueId());
                return true;
            }
            
            // 알 수 없는 서브커맨드
            player.sendMessage(ChatColor.RED + "알 수 없는 명령어입니다.");
            player.sendMessage(ChatColor.GRAY + "/recovery - 자신의 보관소 열기");
            player.sendMessage(ChatColor.GRAY + "/recovery <플레이어> - 다른 플레이어 보관소 열기");
            player.sendMessage(ChatColor.GRAY + "/recovery list - 목록 확인");
            player.sendMessage(ChatColor.GRAY + "/recovery count - 개수 확인");
            return true;
        }
        
        // 기본: GUI 열기 (관리자)
        recoveryGui.open(player);
        return true;
    }

    private void listEntries(Player player) {
        var entries = storageManager.getEntries(player.getUniqueId());
        
        if (entries.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "[Town 보관소] 보관된 아이템이 없습니다.");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "===== Town 보관소 (" + entries.size() + "개) =====");
        
        int index = 1;
        for (RecoveryEntry entry : entries) {
            String reasonName = entry.getReason() != null ? entry.getReason().getDisplayName() : "알 수 없음";
            long cost = storageManager.calculateClaimCost(player, entry);
            String costStr = cost > 0 ? ChatColor.YELLOW + "(" + cost + "원)" : ChatColor.GREEN + "(무료)";
            
            player.sendMessage(ChatColor.WHITE + "" + index + ". " + 
                    ChatColor.GRAY + reasonName + " - " +
                    ChatColor.WHITE + entry.getTotalItemCount() + "개 아이템 " +
                    ChatColor.GRAY + entry.getAgeString() + " " +
                    costStr);
            index++;
        }
        
        player.sendMessage(ChatColor.GRAY + "/recovery 로 GUI를 열어 회수하세요.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1 && sender.hasPermission("tycoon.admin.recovery")) {
            String partial = args[0].toLowerCase();
            for (String sub : List.of("list", "count", "목록", "개수")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        }
        
        return completions;
    }
}
