package kr.bapuri.tycoon.codex;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * CodexAdminCommand - 도감 관리자 명령어
 * 
 * 명령어:
 * - /codexadmin add <player> <material> [withReward] - 강제 등록
 * - /codexadmin remove <player> <material> - 등록 해제
 * - /codexadmin reset <player> - 전체 초기화
 * - /codexadmin list <player> [category] - 진행도 조회
 * - /codexadmin completeall <player> [withReward] - 전체 도감 완성
 */
public class CodexAdminCommand implements CommandExecutor, TabCompleter {

    private final CodexService codexService;

    public CodexAdminCommand(CodexService codexService) {
        this.codexService = codexService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("tycoon.admin.codex")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        String playerName = args[1];

        @SuppressWarnings("deprecation")
        OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = off.getUniqueId();

        switch (sub) {
            case "add" -> handleAdd(sender, playerName, uuid, args);
            case "remove" -> handleRemove(sender, playerName, uuid, args);
            case "reset" -> handleReset(sender, playerName, uuid);
            case "list" -> handleList(sender, playerName, uuid, args);
            case "completeall" -> handleCompleteAll(sender, playerName, uuid, args);
            default -> sender.sendMessage("§c알 수 없는 서브 명령입니다.");
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e=== 도감 관리자 명령어 ===");
        sender.sendMessage("§7/codexadmin add <player> <material> [withReward]");
        sender.sendMessage("§7/codexadmin remove <player> <material>");
        sender.sendMessage("§7/codexadmin reset <player>");
        sender.sendMessage("§7/codexadmin list <player> [category]");
        sender.sendMessage("§7/codexadmin completeall <player> [withReward]");
    }

    /**
     * 강제 등록 (withReward 옵션 지원)
     */
    private void handleAdd(CommandSender sender, String playerName, UUID uuid, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e/codexadmin add <player> <material> [withReward]");
            return;
        }

        Material mat;
        try {
            mat = Material.valueOf(args[2].toUpperCase());
        } catch (Exception e) {
            sender.sendMessage("§c잘못된 아이템입니다: " + args[2]);
            return;
        }

        boolean withReward = args.length >= 4 && args[3].equalsIgnoreCase("withReward");

        if (withReward) {
            Player onlinePlayer = Bukkit.getPlayer(uuid);
            if (onlinePlayer == null) {
                sender.sendMessage("§c보상 지급은 온라인 플레이어만 가능합니다.");
                sender.sendMessage("§7보상 없이 등록: /codexadmin add " + playerName + " " + mat);
                return;
            }

            CodexRegisterResult result = codexService.tryRegisterAdmin(onlinePlayer, mat);
            switch (result) {
                case SUCCESS -> sender.sendMessage("§a" + playerName + " 도감 등록 (보상 포함): " + mat);
                case ALREADY_REGISTERED -> sender.sendMessage("§e" + playerName + ": " + mat + " 이미 등록됨");
                case NOT_IN_CODEX -> sender.sendMessage("§c" + mat + "은(는) 도감 대상이 아닙니다.");
                default -> sender.sendMessage("§c등록 실패: " + result);
            }
        } else {
            boolean added = codexService.forceRegister(uuid, mat);
            if (added) {
                sender.sendMessage("§a" + playerName + " 도감 등록: " + mat);
            } else {
                sender.sendMessage("§e" + playerName + ": " + mat + " 이미 등록됨");
            }
        }
    }

    /**
     * 등록 해제
     */
    private void handleRemove(CommandSender sender, String playerName, UUID uuid, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§e/codexadmin remove <player> <material>");
            return;
        }

        Material mat;
        try {
            mat = Material.valueOf(args[2].toUpperCase());
        } catch (Exception e) {
            sender.sendMessage("§c잘못된 아이템입니다: " + args[2]);
            return;
        }

        boolean removed = codexService.forceUnregister(uuid, mat);
        if (removed) {
            sender.sendMessage("§a" + playerName + " 도감 해제: " + mat);
        } else {
            sender.sendMessage("§e" + playerName + ": " + mat + " 등록되지 않음");
        }
    }

    /**
     * 전체 초기화
     */
    private void handleReset(CommandSender sender, String playerName, UUID uuid) {
        int cleared = codexService.forceResetAll(uuid);
        sender.sendMessage("§a" + playerName + " 도감 전체 초기화: " + cleared + "개 항목 삭제");
        sender.sendMessage("§7(마일스톤/카테고리 보상 수령 상태도 초기화됨)");
    }

    /**
     * 진행도 조회
     */
    private void handleList(CommandSender sender, String playerName, UUID uuid, String[] args) {
        // 카테고리 지정 시 해당 카테고리만 표시
        if (args.length >= 3) {
            String category = args[2];
            int progress = codexService.getCategoryProgress(uuid, category);
            List<CodexRule> rules = codexService.getRegistry().getByCategory(category);
            int total = rules != null ? rules.size() : 0;
            
            if (total == 0) {
                sender.sendMessage("§c존재하지 않는 카테고리: " + category);
                return;
            }
            
            boolean complete = codexService.isCategoryComplete(uuid, category);
            sender.sendMessage("§e" + playerName + " - " + category + ":");
            sender.sendMessage("§7진행: " + progress + " / " + total + (complete ? " §a(완성!)" : ""));
            return;
        }

        // 전체 진행도
        int count = codexService.getCollectedCount(uuid);
        int total = codexService.getTotalCount();
        double progress = codexService.getProgressPercent(uuid);

        sender.sendMessage("§e" + playerName + " 도감 진행도:");
        sender.sendMessage("§7수집: " + count + " / " + total);
        sender.sendMessage(String.format("§7진행도: %.2f%%", progress));
        
        // 카테고리별 요약
        sender.sendMessage("§7--- 카테고리별 ---");
        for (String cat : codexService.getRegistry().getCategoryOrder()) {
            int catProgress = codexService.getCategoryProgress(uuid, cat);
            int catTotal = codexService.getRegistry().getByCategory(cat).size();
            String status = catProgress == catTotal ? "§a✓" : "§7";
            sender.sendMessage(status + cat + ": " + catProgress + "/" + catTotal);
        }
    }
    
    /**
     * 전체 도감 완성
     */
    private void handleCompleteAll(CommandSender sender, String playerName, UUID uuid, String[] args) {
        boolean withReward = args.length >= 3 && args[2].equalsIgnoreCase("withReward");
        
        int registered = codexService.forceRegisterAll(uuid, withReward);
        
        if (registered > 0) {
            sender.sendMessage("§a" + playerName + " 도감 전체 완료: " + registered + "개 새로 등록됨" 
                + (withReward ? " (보상 포함)" : ""));
        } else {
            sender.sendMessage("§e" + playerName + ": 이미 모든 도감이 등록되어 있습니다.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tycoon.admin.codex")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = Arrays.asList("add", "remove", "reset", "list", "completeall");
            return filterStartsWith(subs, args[0]);
        }

        if (args.length == 2) {
            // 플레이어 이름
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            return filterStartsWith(players, args[1]);
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("remove")) {
                // Material 이름
                List<String> materials = new ArrayList<>();
                for (Material mat : codexService.getRegistry().getAllMaterials()) {
                    materials.add(mat.name());
                }
                return filterStartsWith(materials, args[2]);
            }
            if (sub.equals("list")) {
                // 카테고리 이름
                return filterStartsWith(codexService.getRegistry().getCategoryOrder(), args[2]);
            }
            if (sub.equals("completeall")) {
                return filterStartsWith(List.of("withReward"), args[2]);
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("add")) {
                return filterStartsWith(List.of("withReward"), args[3]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }
}
