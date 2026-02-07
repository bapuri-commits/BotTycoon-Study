package kr.bapuri.tycoon.achievement;

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
 * AchievementCommand - 업적 플레이어 명령어
 * 
 * 명령어:
 * - /achievement - 전체 진행도
 * - /achievement [카테고리] - 카테고리별 진행도
 * - /achievement list - 해금된 업적 목록
 */
public class AchievementCommand implements CommandExecutor, TabCompleter {
    
    private static final int ITEMS_PER_PAGE = 5;
    
    private final AchievementService achievementService;
    
    public AchievementCommand(AchievementService achievementService) {
        this.achievementService = achievementService;
    }
    
    private static final String USE_PERMISSION = "tycoon.achievement.use";
    
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
        
        // 서브커맨드 처리
        if (args.length >= 1) {
            String sub = args[0].toLowerCase();
            
            // 카테고리 확인
            AchievementType type = parseType(sub);
            if (type != null) {
                return showCategoryProgress(player, type, args.length >= 2 ? parsePageNumber(args[1]) : 1);
            }
            
            if (sub.equals("list")) {
                return showUnlockedList(player, args.length >= 2 ? parsePageNumber(args[1]) : 1);
            }
            
            if (sub.equals("help")) {
                return showHelp(player);
            }
        }
        
        // 전체 진행도
        return showOverallProgress(player);
    }
    
    /**
     * 전체 진행도 표시
     */
    private boolean showOverallProgress(Player player) {
        AchievementRegistry registry = achievementService.getRegistry();
        int unlocked = achievementService.getUnlockedCount(player);
        int total = achievementService.getTotalCount();
        double percent = total > 0 ? (unlocked * 100.0 / total) : 0;
        
        player.sendMessage("§6========== [업적] ==========");
        player.sendMessage("§e전체 진행도: §f" + unlocked + " / " + total 
            + " §7(" + String.format("%.1f", percent) + "%)");
        player.sendMessage("");
        
        // 카테고리별 요약
        for (AchievementType type : AchievementType.values()) {
            List<Achievement> typeAchievements = registry.getByType(type);
            if (typeAchievements.isEmpty()) continue;
            
            int typeTotal = typeAchievements.size();
            int typeUnlocked = 0;
            for (Achievement ach : typeAchievements) {
                if (achievementService.isUnlocked(player, ach.getId())) {
                    typeUnlocked++;
                }
            }
            
            double typePercent = typeTotal > 0 ? (typeUnlocked * 100.0 / typeTotal) : 0;
            String status = typeUnlocked == typeTotal ? "§a✓" : "§7";
            
            player.sendMessage(status + type.getColoredName() + " §f" + typeUnlocked + "/" + typeTotal 
                + " §7(" + String.format("%.0f", typePercent) + "%)");
        }
        
        player.sendMessage("");
        player.sendMessage("§7/achievement <카테고리> - 상세 보기");
        player.sendMessage("§7/achievement list - 해금 목록");
        player.sendMessage("§6==========================");
        
        return true;
    }
    
    /**
     * 카테고리별 상세 진행도
     */
    private boolean showCategoryProgress(Player player, AchievementType type, int page) {
        AchievementRegistry registry = achievementService.getRegistry();
        List<Achievement> achievements = registry.getByType(type);
        
        if (achievements.isEmpty()) {
            player.sendMessage("§c해당 카테고리에 업적이 없습니다.");
            return true;
        }
        
        int totalPages = (int) Math.ceil(achievements.size() / (double) ITEMS_PER_PAGE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        
        int startIdx = (page - 1) * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, achievements.size());
        
        player.sendMessage("§6========== [" + type.getColoredName() + " §6업적] ==========");
        player.sendMessage("§7페이지: " + page + " / " + totalPages);
        player.sendMessage("");
        
        for (int i = startIdx; i < endIdx; i++) {
            Achievement ach = achievements.get(i);
            boolean unlocked = achievementService.isUnlocked(player, ach.getId());
            
            String status;
            String progressText = "";
            
            if (unlocked) {
                status = "§a[완료]";
            } else if (ach.isProgressive()) {
                int progress = achievementService.getProgress(player, ach.getId());
                status = "§e[진행중]";
                progressText = " §7(" + progress + "/" + ach.getTargetValue() + ")";
            } else {
                status = "§7[미완료]";
            }
            
            player.sendMessage(status + " " + ach.getColoredName() + progressText);
            player.sendMessage("  §7" + ach.getDescription());
            
            if (!unlocked && ach.getBottCoinReward() > 0) {
                player.sendMessage("  §8보상: " + ach.getBottCoinReward() + " BottCoin");
            }
        }
        
        player.sendMessage("");
        if (page < totalPages) {
            player.sendMessage("§7다음: /achievement " + type.name().toLowerCase() + " " + (page + 1));
        }
        player.sendMessage("§6================================");
        
        return true;
    }
    
    /**
     * 해금된 업적 목록
     */
    private boolean showUnlockedList(Player player, int page) {
        Set<String> unlocked = achievementService.getUnlockedAchievements(player);
        AchievementRegistry registry = achievementService.getRegistry();
        
        if (unlocked.isEmpty()) {
            player.sendMessage("§7아직 해금된 업적이 없습니다.");
            return true;
        }
        
        List<Achievement> unlockedAchievements = new ArrayList<>();
        for (String id : unlocked) {
            Achievement ach = registry.get(id);
            if (ach != null) {
                unlockedAchievements.add(ach);
            }
        }
        
        int totalPages = (int) Math.ceil(unlockedAchievements.size() / (double) ITEMS_PER_PAGE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        
        int startIdx = (page - 1) * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, unlockedAchievements.size());
        
        player.sendMessage("§6========== [해금된 업적] ==========");
        player.sendMessage("§7" + unlockedAchievements.size() + "개 해금됨 (페이지 " + page + "/" + totalPages + ")");
        player.sendMessage("");
        
        for (int i = startIdx; i < endIdx; i++) {
            Achievement ach = unlockedAchievements.get(i);
            player.sendMessage("§a✓ " + ach.getType().getColoredName() + " §7» " + ach.getColoredName());
        }
        
        player.sendMessage("");
        if (page < totalPages) {
            player.sendMessage("§7다음: /achievement list " + (page + 1));
        }
        player.sendMessage("§6================================");
        
        return true;
    }
    
    /**
     * 도움말
     */
    private boolean showHelp(Player player) {
        player.sendMessage("§6========== [업적 명령어] ==========");
        player.sendMessage("§e/achievement §7- 전체 진행도");
        player.sendMessage("§e/achievement <카테고리> §7- 카테고리별 상세");
        player.sendMessage("§e/achievement list §7- 해금된 업적 목록");
        player.sendMessage("");
        player.sendMessage("§7카테고리: codex, job, pvp, vanilla");
        player.sendMessage("§6================================");
        return true;
    }
    
    private AchievementType parseType(String str) {
        try {
            return AchievementType.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private int parsePageNumber(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            
            // 카테고리
            for (AchievementType type : AchievementType.values()) {
                String typeName = type.name().toLowerCase();
                if (typeName.startsWith(input)) {
                    completions.add(typeName);
                }
            }
            
            // 기타 서브커맨드
            if ("list".startsWith(input)) completions.add("list");
            if ("help".startsWith(input)) completions.add("help");
            
            return completions;
        }
        
        return Collections.emptyList();
    }
}
