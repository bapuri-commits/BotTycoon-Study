package kr.bapuri.tycoon.world;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PvpDamageCommand - PvP 데미지 배율 토글 명령어
 * 
 * 사용법:
 *   /pvpdamage <off|10|50|100>
 *   /pvpdamage status
 * 
 * 권한: tycoon.admin.pvpdamage
 */
public class PvpDamageCommand implements CommandExecutor, TabCompleter {

    private final WorldManager worldManager;
    
    private static final List<String> OPTIONS = Arrays.asList("off", "10", "50", "100", "status");
    
    public PvpDamageCommand(WorldManager worldManager) {
        this.worldManager = worldManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tycoon.admin.pvpdamage")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }
        
        String option = args[0].toLowerCase();
        
        switch (option) {
            case "status" -> showStatus(sender);
            case "off", "0" -> setMultiplier(sender, 0.0, "OFF");
            case "10" -> setMultiplier(sender, 0.1, "10%");
            case "50" -> setMultiplier(sender, 0.5, "50%");
            case "100" -> setMultiplier(sender, 1.0, "100%");
            default -> {
                // 숫자로 파싱 시도 (예: 25 -> 25%)
                try {
                    int percent = Integer.parseInt(option);
                    if (percent < 0 || percent > 100) {
                        sender.sendMessage("§c0~100 사이의 값을 입력하세요.");
                        return true;
                    }
                    setMultiplier(sender, percent / 100.0, percent + "%");
                } catch (NumberFormatException e) {
                    showUsage(sender);
                }
            }
        }
        
        return true;
    }
    
    private void showUsage(CommandSender sender) {
        sender.sendMessage("§e=== PvP 데미지 배율 설정 ===");
        sender.sendMessage("§7/pvpdamage off §8- PvP 비활성화 (데미지 0%)");
        sender.sendMessage("§7/pvpdamage 10 §8- 10% 데미지 (기본값)");
        sender.sendMessage("§7/pvpdamage 50 §8- 50% 데미지");
        sender.sendMessage("§7/pvpdamage 100 §8- 100% 데미지 (바닐라)");
        sender.sendMessage("§7/pvpdamage status §8- 현재 상태 확인");
        sender.sendMessage("");
        showStatus(sender);
    }
    
    private void showStatus(CommandSender sender) {
        double multiplier = worldManager.getPvpDamageMultiplier();
        int percent = (int)(multiplier * 100);
        
        String statusColor = percent == 0 ? "§a" : (percent <= 50 ? "§e" : "§c");
        String statusText = percent == 0 ? "비활성화" : percent + "%";
        
        sender.sendMessage("§7현재 PvP 데미지 배율: " + statusColor + statusText);
        sender.sendMessage("§8적용 월드: Wild, Wild_Nether, Wild_End");
    }
    
    private void setMultiplier(CommandSender sender, double multiplier, String displayText) {
        worldManager.setPvpDamageMultiplier(multiplier);
        
        // 전체 서버 알림
        String message = "§6[PvP] §e" + sender.getName() + "§7님이 PvP 데미지를 §f" + displayText + "§7로 설정했습니다.";
        Bukkit.broadcastMessage(message);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tycoon.admin.pvpdamage")) {
            return List.of();
        }
        
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String opt : OPTIONS) {
                if (opt.startsWith(partial)) {
                    completions.add(opt);
                }
            }
            return completions;
        }
        
        return List.of();
    }
}
