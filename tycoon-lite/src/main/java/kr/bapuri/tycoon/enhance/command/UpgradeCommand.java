package kr.bapuri.tycoon.enhance.command;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.upgrade.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * UpgradeCommand - 강화 명령어
 * 
 * Phase 6 LITE: 레거시 버전 이식
 * 
 * /upgrade - 강화 GUI 열기
 * /upgrade info - 손에 든 아이템 강화 정보
 * /upgrade stats - 내 강화 스탯 정보
 * /upgrade set <level> - 손에 든 아이템 강화 레벨 설정 (관리자)
 * /upgrade scroll <type> [player] [amount] - 보호 주문서 지급 (관리자)
 * /upgrade table [level] - 확률 테이블 확인
 */
public class UpgradeCommand implements CommandExecutor, TabCompleter {

    private final UpgradeService upgradeService;
    private final UpgradeGui upgradeGui;
    private final UpgradeStatCalculator statCalculator;

    private static final List<String> SUB_COMMANDS = Arrays.asList("info", "stats", "set", "scroll", "table");

    public UpgradeCommand(UpgradeService upgradeService, UpgradeGui upgradeGui, 
                          UpgradeStatCalculator statCalculator) {
        this.upgradeService = upgradeService;
        this.upgradeGui = upgradeGui;
        this.statCalculator = statCalculator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return true;
        }

        if (args.length == 0) {
            // 강화 GUI 열기 - 관리자만 명령어로 접근 가능
            // 일반 유저는 NPC를 통해서만 접근
            if (!player.hasPermission("tycoon.admin.upgrade")) {
                player.sendMessage("§c강화 NPC를 통해 이용해주세요.");
                return true;
            }
            upgradeGui.openGui(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> handleInfo(player);
            case "stats" -> handleStats(player);
            case "set" -> handleSet(player, args);
            case "scroll" -> handleScroll(player, args);
            case "table" -> handleTable(player, args);
            default -> showUsage(player);
        }

        return true;
    }

    private void handleInfo(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item.getType() == Material.AIR) {
            player.sendMessage("§c손에 아이템을 들고 있어야 합니다.");
            return;
        }

        if (!EnhanceItemUtil.isUpgradeable(item)) {
            player.sendMessage("§c강화할 수 없는 아이템입니다.");
            return;
        }

        int level = EnhanceItemUtil.getUpgradeLevel(item);
        
        player.sendMessage("§6§l===== 강화 정보 =====");
        player.sendMessage("§7현재 강화: §6+" + level);
        
        if (level < EnhanceConstants.MAX_UPGRADE_LEVEL) {
            player.sendMessage("");
            player.sendMessage("§7다음 강화 (+§6" + (level + 1) + "§7):");
            player.sendMessage(upgradeService.getUpgradeChanceInfo(level));
            player.sendMessage("§7비용: §e" + upgradeService.getUpgradeCost(level) + "원");
        } else {
            player.sendMessage("§a최대 강화 레벨입니다!");
        }

        // 스탯 보너스
        if (EnhanceItemUtil.isWeapon(item)) {
            player.sendMessage("§7공격력 보너스: §a+" + String.format("%.1f", upgradeService.getDamageBonus(item)) + "%");
        }
        if (EnhanceItemUtil.isArmor(item)) {
            player.sendMessage("§7방어력 보너스: §a+" + String.format("%.1f", upgradeService.getDefenseBonus(item)) + "%");
        }
    }

    private void handleStats(Player player) {
        player.sendMessage(statCalculator.getPlayerUpgradeStats(player));
    }

    private void handleSet(Player player, String[] args) {
        if (!player.hasPermission("tycoon.admin.upgrade")) {
            player.sendMessage("§c권한이 없습니다.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c사용법: /upgrade set <레벨>");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage("§c손에 아이템을 들고 있어야 합니다.");
            return;
        }

        if (!EnhanceItemUtil.isUpgradeable(item)) {
            player.sendMessage("§c강화할 수 없는 아이템입니다.");
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c레벨은 숫자여야 합니다.");
            return;
        }

        if (upgradeService.setUpgradeLevel(item, level)) {
            player.sendMessage("§a강화 레벨을 +" + level + "로 설정했습니다.");
        } else {
            player.sendMessage("§c강화 레벨 설정에 실패했습니다.");
        }
    }

    private void handleScroll(Player player, String[] args) {
        if (!player.hasPermission("tycoon.admin.upgrade")) {
            player.sendMessage("§c권한이 없습니다.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c사용법: /upgrade scroll <타입> [플레이어] [수량]");
            player.sendMessage("§7타입: destroy, downgrade, all");
            return;
        }

        ProtectionScrollFactory.ProtectionType type = ProtectionScrollFactory.ProtectionType.fromId(args[1]);
        if (type == null) {
            player.sendMessage("§c유효하지 않은 타입입니다: " + args[1]);
            player.sendMessage("§7사용 가능: destroy, downgrade, all");
            return;
        }

        Player target = player;
        int amount = 1;

        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
                return;
            }
        }

        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c수량은 숫자여야 합니다.");
                return;
            }
        }

        ItemStack scroll = ProtectionScrollFactory.createScrolls(type, amount);
        if (scroll == null) {
            player.sendMessage("§c보호 주문서 생성에 실패했습니다.");
            return;
        }

        target.getInventory().addItem(scroll);
        player.sendMessage("§a" + target.getName() + "님에게 " + type.getDisplayName() + 
                         " §a" + amount + "개를 지급했습니다.");
        if (target != player) {
            target.sendMessage(EnhanceConstants.PREFIX_UPGRADE + "보호 주문서를 받았습니다!");
        }
    }

    private void handleTable(Player player, String[] args) {
        int level = 0;
        if (args.length >= 2) {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c레벨은 숫자여야 합니다.");
                return;
            }
        }

        player.sendMessage("§6§l===== 강화 확률 테이블 =====");
        
        int start = Math.max(0, level - 2);
        int end = Math.min(EnhanceConstants.MAX_UPGRADE_LEVEL, level + 10);

        player.sendMessage("§7레벨  | 성공  | 유지  | 하락  | 파괴  | 비용");
        player.sendMessage("§8------|-------|-------|-------|-------|--------");

        for (int lv = start; lv <= end; lv++) {
            UpgradeLevel config = upgradeService.getConfig().getLevel(lv);
            if (config == null) continue;

            String line = String.format("§7+%3d  | §a%5.1f | §f%5.1f | §e%5.1f | §c%5.1f | §e%s",
                    lv,
                    config.getSuccessRate() * 100,
                    config.getMaintainRate() * 100,
                    config.getDowngradeRate() * 100,
                    config.getDestroyRate() * 100,
                    formatCost(config.getCost()));
            player.sendMessage(line);
        }
    }

    private String formatCost(long cost) {
        if (cost >= 1000000) {
            return String.format("%.1fM", cost / 1000000.0);
        } else if (cost >= 1000) {
            return String.format("%.1fK", cost / 1000.0);
        }
        return String.valueOf(cost);
    }

    private void showUsage(Player player) {
        player.sendMessage("§6§l[강화 명령어]");
        player.sendMessage("§e/upgrade §7- 강화 GUI 열기");
        player.sendMessage("§e/upgrade info §7- 손에 든 아이템 강화 정보");
        player.sendMessage("§e/upgrade stats §7- 내 강화 스탯 정보");
        player.sendMessage("§e/upgrade table [레벨] §7- 확률 테이블 확인");
        if (player.hasPermission("tycoon.admin.upgrade")) {
            player.sendMessage("§e/upgrade set <레벨> §7- 강화 레벨 설정");
            player.sendMessage("§e/upgrade scroll <타입> [플레이어] [수량] §7- 보호 주문서 지급");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUB_COMMANDS.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("scroll")) {
                return Arrays.asList("destroy", "downgrade", "all").stream()
                        .filter(t -> t.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("scroll")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
