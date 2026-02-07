package kr.bapuri.tycoon.item;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CoreItemCommand - 핵심 아이템 관리 명령어
 * 
 * <h2>명령어 구조</h2>
 * <ul>
 *   <li>/coreitem give &lt;player&gt; &lt;type&gt; [amount] - 아이템 지급</li>
 *   <li>/coreitem list - 사용 가능한 타입 목록</li>
 * </ul>
 * 
 * <h2>권한</h2>
 * <ul>
 *   <li>tycoon.admin.coreitem - 모든 하위 명령어</li>
 * </ul>
 */
public class CoreItemCommand implements CommandExecutor, TabCompleter {
    
    private final CoreItemService coreItemService;
    
    public CoreItemCommand(CoreItemService coreItemService) {
        this.coreItemService = coreItemService;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tycoon.admin.coreitem")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "give":
                return handleGive(sender, args);
            case "list":
                return handleList(sender);
            default:
                showHelp(sender);
                return true;
        }
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 핵심 아이템 관리 ===");
        sender.sendMessage(ChatColor.YELLOW + "/coreitem give <player> <type> [amount]" + 
                ChatColor.GRAY + " - 아이템 지급");
        sender.sendMessage(ChatColor.YELLOW + "/coreitem list" + 
                ChatColor.GRAY + " - 사용 가능한 타입 목록");
    }
    
    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "사용법: /coreitem give <player> <type> [amount]");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "플레이어를 찾을 수 없습니다: " + args[1]);
            return true;
        }
        
        CoreItemType type = CoreItemType.fromConfigKey(args[2]);
        if (type == null) {
            sender.sendMessage(ChatColor.RED + "알 수 없는 아이템 타입: " + args[2]);
            sender.sendMessage(ChatColor.GRAY + "/coreitem list 로 타입 목록 확인");
            return true;
        }
        
        // VOUCHER는 별도 명령어 사용
        if (type.getCategory() == CoreItemType.Category.VOUCHER) {
            sender.sendMessage(ChatColor.RED + "VOUCHER 타입은 /coreitem give로 지급할 수 없습니다.");
            sender.sendMessage(ChatColor.YELLOW + "대신 다음 명령어를 사용하세요:");
            if (type == CoreItemType.BD_CHECK) {
                sender.sendMessage(ChatColor.WHITE + "  /check issue <player> <BD금액>");
            } else if (type == CoreItemType.BOTTCOIN_VOUCHER) {
                sender.sendMessage(ChatColor.WHITE + "  /bottcoin issue <player> <BC수량>");
            }
            return true;
        }
        // [Phase 8 정리] TICKET, DUNGEON_*, TOWN_LAND_DEED 차단 로직 제거 (타입 자체가 삭제됨)
        
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                amount = Math.max(1, Math.min(1000, amount));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "잘못된 수량: " + args[3]);
                return true;
            }
        }
        
        // 아이템 지급
        if (coreItemService.giveItem(target, type, amount)) {
            sender.sendMessage(ChatColor.GREEN + target.getName() + "에게 " + 
                    type.getDisplayName() + " x" + amount + " 지급 완료");
            target.sendMessage(ChatColor.GOLD + "[시스템] " + ChatColor.WHITE + 
                    type.getDisplayName() + " x" + amount + "을(를) 받았습니다!");
        } else {
            sender.sendMessage(ChatColor.RED + "아이템 지급 실패");
        }
        
        return true;
    }
    
    private boolean handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 사용 가능한 아이템 타입 ===");
        
        // 카테고리별 그룹화
        for (CoreItemType.Category category : CoreItemType.Category.values()) {
            List<CoreItemType> types = Arrays.stream(CoreItemType.values())
                    .filter(t -> t.getCategory() == category)
                    .collect(Collectors.toList());
            
            if (types.isEmpty()) continue;
            
            // 카테고리별 상태 표시
            String categoryStatus = "";
            if (category == CoreItemType.Category.VOUCHER) {
                categoryStatus = ChatColor.RED + " (별도 명령어)";
            }
            
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "[" + category.getDisplayName() + "]" + categoryStatus);
            
            for (CoreItemType type : types) {
                // VOUCHER만 별도 명령어 필요
                boolean unavailable = category == CoreItemType.Category.VOUCHER;
                
                String stackInfo = type.isStackable() ? ChatColor.GREEN + " [스택]" : "";
                String unavailableInfo = unavailable ? ChatColor.DARK_GRAY + " (별도 명령어)" : "";
                
                ChatColor nameColor = unavailable ? ChatColor.DARK_GRAY : ChatColor.WHITE;
                sender.sendMessage(nameColor + "  - " + type.getConfigKey() + 
                        ChatColor.GRAY + " (" + type.getDisplayName() + ")" + stackInfo + unavailableInfo);
            }
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tycoon.admin.coreitem")) {
            return List.of();
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // /coreitem <...>
            List<String> options = Arrays.asList("give", "list");
            String partial = args[0].toLowerCase();
            for (String opt : options) {
                if (opt.startsWith(partial)) {
                    completions.add(opt);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // /coreitem give <player>
            return null; // 플레이어 목록 자동 완성
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // /coreitem give <player> <type>
            // VOUCHER만 별도 명령어 필요
            String partial = args[2].toLowerCase();
            for (CoreItemType type : CoreItemType.values()) {
                // VOUCHER는 별도 명령어 사용
                if (type.getCategory() == CoreItemType.Category.VOUCHER) continue;
                
                if (type.getConfigKey().startsWith(partial)) {
                    completions.add(type.getConfigKey());
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // /coreitem give <player> <type> <amount>
            completions.addAll(Arrays.asList("1", "5", "10", "64"));
        }
        
        return completions;
    }
}
