package kr.bapuri.tycoon.bcshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BC 상점 관리자 명령어
 * 
 * 관리자 전용 명령어입니다. 일반 플레이어는 NPC로만 상점에 접근합니다.
 * 
 * <h2>명령어</h2>
 * <ul>
 *   <li>/bcshop title - 칭호 상점 열기</li>
 *   <li>/bcshop cosmetic - 치장 상점 열기</li>
 *   <li>/bcshop reload - 설정 리로드</li>
 * </ul>
 */
public class BCShopCommand implements CommandExecutor, TabCompleter {
    
    private static final String PERMISSION = "tycoon.admin.bcshop";
    
    private final BCShopService service;
    
    public BCShopCommand(BCShopService service) {
        this.service = service;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String sub = args[0].toLowerCase();
        
        switch (sub) {
            case "title", "칭호" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
                    return true;
                }
                service.openShop(player, "title");
            }
            case "cosmetic", "치장" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
                    return true;
                }
                service.openShop(player, "cosmetic");
            }
            case "reload" -> {
                service.reload();
                sender.sendMessage("§aBC 상점 설정을 리로드했습니다.");
            }
            case "help" -> showHelp(sender);
            default -> {
                sender.sendMessage("§c알 수 없는 명령어: " + sub);
                showHelp(sender);
            }
        }
        
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6========== [BC 상점 관리] ==========");
        sender.sendMessage("§e/bcshop title §7- 칭호 상점 열기");
        sender.sendMessage("§e/bcshop cosmetic §7- 치장 상점 열기");
        sender.sendMessage("§e/bcshop reload §7- 설정 리로드");
        sender.sendMessage("");
        sender.sendMessage("§7관리자 전용 명령어입니다.");
        sender.sendMessage("§7일반 플레이어는 NPC를 통해 상점에 접근합니다.");
        sender.sendMessage("§6=================================");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            
            for (String sub : List.of("title", "cosmetic", "reload", "help")) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
            
            return completions;
        }
        
        return Collections.emptyList();
    }
}
