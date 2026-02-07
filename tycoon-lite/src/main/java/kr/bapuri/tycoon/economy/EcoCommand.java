package kr.bapuri.tycoon.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * EcoCommand - 경제 관리자 명령어
 * 
 * 사용법:
 * - /eco give <player> <amount> [bd|bottcoin] - 지급
 * - /eco take <player> <amount> [bd|bottcoin] - 차감
 * - /eco set <player> <amount> [bd|bottcoin] - 설정
 * - /eco check <player> - 잔액 확인
 * 
 * 권한: tycoon.admin.eco
 * 
 * [Phase 3.A] 관리자 테스트/관리용 명령어
 */
public class EcoCommand implements CommandExecutor, TabCompleter {

    private final EconomyService economyService;
    
    private static final String PERMISSION = "tycoon.admin.eco";
    private static final List<String> SUB_COMMANDS = Arrays.asList("give", "take", "set", "check");
    private static final List<String> CURRENCY_TYPES = Arrays.asList("bd", "bottcoin");
    
    // Long overflow 방지용 최대값 (int 최대값보다 약간 큰 값)
    private static final long MAX_AMOUNT = 2_000_000_000L;

    public EcoCommand(EconomyService economyService) {
        this.economyService = economyService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "give" -> handleGive(sender, args);
            case "take" -> handleTake(sender, args);
            case "set" -> handleSet(sender, args);
            case "check" -> handleCheck(sender, args);
            default -> sendUsage(sender);
        }
        
        return true;
    }
    
    private void handleGive(CommandSender sender, String[] args) {
        // /eco give <player> <amount> [bd|bottcoin]
        if (args.length < 3) {
            sender.sendMessage("§c사용법: /eco give <player> <amount> [bd|bottcoin]");
            return;
        }
        
        UUID uuid = getPlayerUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
            return;
        }
        
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount <= 0) {
                sender.sendMessage("§c금액은 양수여야 합니다.");
                return;
            }
            if (amount > MAX_AMOUNT) {
                sender.sendMessage(String.format("§c최대 금액은 %,d입니다.", MAX_AMOUNT));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 숫자를 입력하세요: " + args[2]);
            return;
        }
        
        CurrencyType currency = getCurrency(args, 3);
        String reason = "관리자 지급 by " + sender.getName();
        
        String currencyName = currency == CurrencyType.BD ? "BD" : "BottCoin";
        
        if (currency == CurrencyType.BD) {
            economyService.depositAdmin(uuid, amount, reason);
        } else {
            economyService.depositBottCoinAdmin(uuid, amount, reason);
        }
        
        sender.sendMessage(String.format("§a%s에게 %,d %s를 지급했습니다.", args[1], amount, currencyName));
        notifyTarget(uuid, String.format("§a[경제] 관리자로부터 %,d %s를 받았습니다.", amount, currencyName));
        
        // 오프라인 플레이어일 수 있으므로 즉시 저장
        economyService.savePlayer(uuid);
    }
    
    private void handleTake(CommandSender sender, String[] args) {
        // /eco take <player> <amount> [bd|bottcoin]
        if (args.length < 3) {
            sender.sendMessage("§c사용법: /eco take <player> <amount> [bd|bottcoin]");
            return;
        }
        
        UUID uuid = getPlayerUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
            return;
        }
        
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount <= 0) {
                sender.sendMessage("§c금액은 양수여야 합니다.");
                return;
            }
            if (amount > MAX_AMOUNT) {
                sender.sendMessage(String.format("§c최대 금액은 %,d입니다.", MAX_AMOUNT));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 숫자를 입력하세요: " + args[2]);
            return;
        }
        
        CurrencyType currency = getCurrency(args, 3);
        String reason = "관리자 차감 by " + sender.getName();
        String currencyName = currency == CurrencyType.BD ? "BD" : "BottCoin";
        
        boolean success = currency == CurrencyType.BD
            ? economyService.withdrawAdmin(uuid, amount, reason, false)
            : economyService.withdrawBottCoinAdmin(uuid, amount, reason, false);
        
        if (success) {
            sender.sendMessage(String.format("§a%s에게서 %,d %s를 차감했습니다.", args[1], amount, currencyName));
            notifyTarget(uuid, String.format("§c[경제] 관리자에 의해 %,d %s가 차감되었습니다.", amount, currencyName));
            economyService.savePlayer(uuid);
        } else {
            sender.sendMessage("§c차감에 실패했습니다.");
        }
    }
    
    private void handleSet(CommandSender sender, String[] args) {
        // /eco set <player> <amount> [bd|bottcoin]
        if (args.length < 3) {
            sender.sendMessage("§c사용법: /eco set <player> <amount> [bd|bottcoin]");
            return;
        }
        
        UUID uuid = getPlayerUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
            return;
        }
        
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0) {
                sender.sendMessage("§c금액은 0 이상이어야 합니다.");
                return;
            }
            if (amount > MAX_AMOUNT) {
                sender.sendMessage(String.format("§c최대 금액은 %,d입니다.", MAX_AMOUNT));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 숫자를 입력하세요: " + args[2]);
            return;
        }
        
        CurrencyType currency = getCurrency(args, 3);
        String reason = "관리자 설정 by " + sender.getName();
        String currencyName = currency == CurrencyType.BD ? "BD" : "BottCoin";
        
        if (currency == CurrencyType.BD) {
            economyService.setMoneyAdmin(uuid, amount, reason);
        } else {
            economyService.setBottCoinAdmin(uuid, amount, reason);
        }
        
        sender.sendMessage(String.format("§a%s의 %s를 %,d로 설정했습니다.", args[1], currencyName, amount));
        notifyTarget(uuid, String.format("§e[경제] 관리자에 의해 %s 잔액이 %,d로 설정되었습니다.", currencyName, amount));
        
        // 오프라인 플레이어일 수 있으므로 즉시 저장
        economyService.savePlayer(uuid);
    }
    
    private void handleCheck(CommandSender sender, String[] args) {
        // /eco check <player>
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /eco check <player>");
            return;
        }
        
        UUID uuid = getPlayerUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
            return;
        }
        
        long bd = economyService.getMoney(uuid);
        long bottCoin = economyService.getBottCoin(uuid);
        
        sender.sendMessage("§e=== " + args[1] + " 잔액 ===");
        sender.sendMessage(String.format("§7BD: §f%,d", bd));
        sender.sendMessage(String.format("§7BottCoin: §d%,d", bottCoin));
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e=== /eco 명령어 ===");
        sender.sendMessage("§7/eco give <player> <amount> [bd|bottcoin] §8- 지급");
        sender.sendMessage("§7/eco take <player> <amount> [bd|bottcoin] §8- 차감");
        sender.sendMessage("§7/eco set <player> <amount> [bd|bottcoin] §8- 설정");
        sender.sendMessage("§7/eco check <player> §8- 잔액 확인");
    }
    
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
    
    private CurrencyType getCurrency(String[] args, int index) {
        if (args.length > index) {
            String type = args[index].toLowerCase();
            if (type.equals("bottcoin") || type.equals("bc")) {
                return CurrencyType.BOTTCOIN;
            }
        }
        return CurrencyType.BD; // 기본값
    }
    
    /**
     * 대상 플레이어에게 알림 (온라인인 경우만)
     */
    private void notifyTarget(UUID uuid, String message) {
        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            target.sendMessage(message);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return null;
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // /eco <subcommand>
            String partial = args[0].toLowerCase();
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            // /eco <sub> <player>
            String partial = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("check")) {
            // /eco give/take/set <player> <amount>
            completions.addAll(Arrays.asList("100", "1000", "10000", "100000"));
        } else if (args.length == 4 && !args[0].equalsIgnoreCase("check")) {
            // /eco give/take/set <player> <amount> [bd|bottcoin]
            String partial = args[3].toLowerCase();
            for (String type : CURRENCY_TYPES) {
                if (type.startsWith(partial)) {
                    completions.add(type);
                }
            }
        }
        
        return completions;
    }
}
