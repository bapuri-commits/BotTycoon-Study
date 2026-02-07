package kr.bapuri.tycoon.title;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * TitleCommand - 칭호 플레이어 명령어
 * 
 * 명령어:
 * - /title - 보유 칭호 목록
 * - /title list - 모든 칭호 목록
 * - /title equip <id> - 칭호 장착
 * - /title unequip - 칭호 해제
 */
public class TitleCommand implements CommandExecutor, TabCompleter {
    
    private final LuckPermsTitleService titleService;
    
    public TitleCommand(LuckPermsTitleService titleService) {
        this.titleService = titleService;
    }
    
    private static final String USE_PERMISSION = "tycoon.title.use";
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        
        // 플레이어 권한 체크
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (!titleService.isEnabled()) {
            player.sendMessage("§c칭호 시스템이 비활성화되어 있습니다.");
            return true;
        }
        
        if (args.length == 0) {
            return showMyTitles(player);
        }
        
        String sub = args[0].toLowerCase();
        
        switch (sub) {
            case "list" -> {
                return showAllTitles(player);
            }
            case "equip" -> {
                if (args.length < 2) {
                    player.sendMessage("§e사용법: /title equip <칭호ID>");
                    return true;
                }
                return handleEquip(player, args[1]);
            }
            case "unequip", "off", "remove" -> {
                return handleUnequip(player);
            }
            case "help" -> {
                return showHelp(player);
            }
            default -> {
                // ID로 직접 장착 시도
                return handleEquip(player, sub);
            }
        }
    }
    
    /**
     * 내 칭호 목록 표시
     */
    private boolean showMyTitles(Player player) {
        Set<String> unlocked = titleService.getUnlockedTitles(player);
        Title equipped = titleService.getEquippedTitle(player);
        TitleRegistry registry = titleService.getRegistry();
        
        player.sendMessage("§6========== [내 칭호] ==========");
        
        if (equipped != null) {
            player.sendMessage("§a현재 장착: " + equipped.getColoredDisplayName());
        } else {
            player.sendMessage("§7현재 장착: 없음");
        }
        
        player.sendMessage("");
        
        if (unlocked.isEmpty()) {
            player.sendMessage("§7해금된 칭호가 없습니다.");
            player.sendMessage("§7업적을 달성하여 칭호를 해금하세요!");
        } else {
            player.sendMessage("§e해금된 칭호 (" + unlocked.size() + "개):");
            for (String titleId : unlocked) {
                Title title = registry.get(titleId);
                if (title != null) {
                    String status = (equipped != null && equipped.getId().equals(titleId)) 
                                   ? " §a[장착중]" 
                                   : "";
                    player.sendMessage("  " + title.getColoredDisplayName() + status);
                    player.sendMessage("    §7" + title.getDescription());
                }
            }
        }
        
        player.sendMessage("");
        player.sendMessage("§7/title equip <ID> - 장착");
        player.sendMessage("§7/title unequip - 해제");
        player.sendMessage("§6============================");
        
        return true;
    }
    
    /**
     * 모든 칭호 목록 표시
     */
    private boolean showAllTitles(Player player) {
        TitleRegistry registry = titleService.getRegistry();
        Set<String> unlocked = titleService.getUnlockedTitles(player);
        
        player.sendMessage("§6========== [전체 칭호] ==========");
        player.sendMessage("§7총 " + registry.getCount() + "개 칭호");
        player.sendMessage("");
        
        for (Title title : registry.getAll()) {
            boolean isUnlocked = unlocked.contains(title.getId());
            String status = isUnlocked ? "§a[해금]" : "§8[미해금]";
            String color = isUnlocked ? "" : "§8";
            
            player.sendMessage(status + " " + color + title.getColoredDisplayName());
            if (isUnlocked) {
                player.sendMessage("  §7" + title.getDescription());
            }
        }
        
        player.sendMessage("");
        player.sendMessage("§6================================");
        
        return true;
    }
    
    /**
     * 칭호 장착
     */
    private boolean handleEquip(Player player, String titleId) {
        return titleService.equipTitle(player, titleId);
    }
    
    /**
     * 칭호 해제
     */
    private boolean handleUnequip(Player player) {
        titleService.unequipTitle(player);
        return true;
    }
    
    /**
     * 도움말
     */
    private boolean showHelp(Player player) {
        player.sendMessage("§6========== [칭호 명령어] ==========");
        player.sendMessage("§e/title §7- 내 칭호 목록");
        player.sendMessage("§e/title list §7- 전체 칭호 목록");
        player.sendMessage("§e/title equip <ID> §7- 칭호 장착");
        player.sendMessage("§e/title unequip §7- 칭호 해제");
        player.sendMessage("§6================================");
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            
            for (String sub : List.of("list", "equip", "unequip", "help")) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
            
            // 해금된 칭호도 추가
            for (String titleId : titleService.getUnlockedTitles(player)) {
                if (titleId.toLowerCase().startsWith(input)) {
                    completions.add(titleId);
                }
            }
            
            return completions;
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("equip")) {
            List<String> completions = new ArrayList<>();
            String input = args[1].toLowerCase();
            
            for (String titleId : titleService.getUnlockedTitles(player)) {
                if (titleId.toLowerCase().startsWith(input)) {
                    completions.add(titleId);
                }
            }
            
            return completions;
        }
        
        return Collections.emptyList();
    }
}
