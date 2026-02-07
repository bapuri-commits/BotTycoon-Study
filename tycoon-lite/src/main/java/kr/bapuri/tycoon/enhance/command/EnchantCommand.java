package kr.bapuri.tycoon.enhance.command;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.enchant.*;
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
 * EnchantCommand - 커스텀 인챈트 명령어
 * 
 * Phase 6 LITE: 레거시 버전 이식
 * 
 * /enchant list - 인챈트 목록
 * /enchant info <enchantId> - 인챈트 정보
 * /enchant give <player> <enchantId> <level> [amount] - 인챈트 북 지급
 * /enchant apply <enchantId> <level> - 손에 든 아이템에 인챈트 적용 (관리자)
 * /enchant remove <enchantId> - 손에 든 아이템에서 인챈트 제거 (관리자)
 * 
 * v2.5: 탭 완성 및 입력에 한글 displayName 지원
 */
public class EnchantCommand implements CommandExecutor, TabCompleter {

    private final CustomEnchantService enchantService;
    private final CustomEnchantRegistry registry;
    private final EnchantBookFactory bookFactory;

    private static final List<String> SUB_COMMANDS = Arrays.asList("list", "info", "give", "apply", "remove");
    
    // 한글 이름 -> ID 매핑 (탭 완성 및 입력 지원용)
    private static final Map<String, String> KOREAN_TO_ID_MAP = new HashMap<>();
    
    static {
        for (CustomEnchant enchant : CustomEnchant.values()) {
            KOREAN_TO_ID_MAP.put(enchant.getDisplayName(), enchant.getId());
        }
    }

    public EnchantCommand(CustomEnchantService enchantService, CustomEnchantRegistry registry, 
                          EnchantBookFactory bookFactory) {
        this.enchantService = enchantService;
        this.registry = registry;
        this.bookFactory = bookFactory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "give" -> handleGive(sender, args);
            case "apply" -> handleApply(sender, args);
            case "remove" -> handleRemove(sender, args);
            default -> showUsage(sender);
        }

        return true;
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("tycoon.admin.enchant")) {
            sender.sendMessage("§c관리자만 사용 가능한 명령어입니다.");
            sender.sendMessage("§7인챈트 정보는 서버 공지/문서를 참고해주세요.");
            return;
        }
        
        sender.sendMessage("§b§l===== 커스텀 인챈트 목록 (관리자 전용) =====");
        
        for (CustomEnchant.EnchantCategory category : CustomEnchant.EnchantCategory.values()) {
            sender.sendMessage("");
            sender.sendMessage("§e§l[" + category.getDisplayName() + "]");
            
            for (CustomEnchant enchant : CustomEnchant.getByCategory(category)) {
                CustomEnchantData data = registry.getData(enchant);
                String status = data != null && data.isEnabled() ? "§a" : "§c";
                sender.sendMessage("  " + status + enchant.getId() + " §7- " + enchant.getDisplayName() + 
                                 " (최대 Lv." + enchant.getMaxLevel() + ")");
            }
        }
    }

    /**
     * 한글 이름 또는 ID를 실제 ID로 변환
     */
    private String resolveEnchantId(String input) {
        // 한글 이름인 경우 ID로 변환
        String resolved = KOREAN_TO_ID_MAP.get(input);
        if (resolved != null) {
            return resolved;
        }
        // 이미 ID인 경우 그대로 반환
        return input.toLowerCase();
    }
    
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /enchant info <인챈트ID>");
            return;
        }

        String enchantId = resolveEnchantId(args[1]);
        CustomEnchant enchant = CustomEnchant.fromId(enchantId);
        
        if (enchant == null) {
            sender.sendMessage("§c존재하지 않는 인챈트입니다: " + enchantId);
            return;
        }

        CustomEnchantData data = registry.getData(enchant);

        sender.sendMessage("§b§l===== " + enchant.getDisplayName() + " =====");
        sender.sendMessage("§7ID: §f" + enchant.getId());
        sender.sendMessage("§7카테고리: §f" + enchant.getCategory().getDisplayName());
        sender.sendMessage("§7최대 레벨: §f" + enchant.getMaxLevel());
        sender.sendMessage("§7설명: §f" + enchant.getDescription());
        sender.sendMessage("§7적용 가능: §f" + String.join(", ", enchant.getApplicableTypes()));
        
        if (data != null) {
            sender.sendMessage("§7상태: " + (data.isEnabled() ? "§a활성화" : "§c비활성화"));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tycoon.admin.enchant")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        if (args.length < 4) {
            sender.sendMessage("§c사용법: /enchant give <플레이어> <인챈트ID> <레벨> [수량]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
            return;
        }

        String enchantId = resolveEnchantId(args[2]);
        int level;
        int amount = 1;

        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c레벨은 숫자여야 합니다.");
            return;
        }

        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c수량은 숫자여야 합니다.");
                return;
            }
        }

        ItemStack book = bookFactory.createBooks(enchantId, level, amount);
        if (book == null) {
            sender.sendMessage("§c인챈트 북 생성에 실패했습니다. ID와 레벨을 확인하세요.");
            return;
        }

        target.getInventory().addItem(book);
        sender.sendMessage("§a" + target.getName() + "님에게 " + enchantId + " Lv." + level + 
                         " 인챈트 북 " + amount + "개를 지급했습니다.");
        target.sendMessage(EnhanceConstants.PREFIX_ENCHANT + "인챈트 북을 받았습니다!");
    }

    private void handleApply(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tycoon.admin.enchant")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c사용법: /enchant apply <인챈트ID> <레벨>");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            sender.sendMessage("§c손에 아이템을 들고 있어야 합니다.");
            return;
        }

        String enchantId = resolveEnchantId(args[1]);
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c레벨은 숫자여야 합니다.");
            return;
        }

        CustomEnchantService.ApplyResult result = enchantService.applyEnchant(item, enchantId, level);
        sender.sendMessage(result.getMessage());
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tycoon.admin.enchant")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c사용법: /enchant remove <인챈트ID>");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            sender.sendMessage("§c손에 아이템을 들고 있어야 합니다.");
            return;
        }

        String enchantId = resolveEnchantId(args[1]);
        boolean removed = enchantService.removeEnchant(item, enchantId);
        
        if (removed) {
            sender.sendMessage("§a인챈트 '" + enchantId + "'를 제거했습니다.");
        } else {
            sender.sendMessage("§c해당 인챈트가 없거나 제거에 실패했습니다.");
        }
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage("§b§l[인챈트 명령어]");
        sender.sendMessage("§e/enchant list §7- 인챈트 목록");
        sender.sendMessage("§e/enchant info <ID> §7- 인챈트 정보");
        if (sender.hasPermission("tycoon.admin.enchant")) {
            sender.sendMessage("§e/enchant give <플레이어> <ID> <레벨> [수량] §7- 인챈트 북 지급");
            sender.sendMessage("§e/enchant apply <ID> <레벨> §7- 손에 든 아이템에 적용");
            sender.sendMessage("§e/enchant remove <ID> §7- 손에 든 아이템에서 제거");
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
            if (sub.equals("info") || sub.equals("apply") || sub.equals("remove")) {
                return getEnchantSuggestions(args[1]);
            }
            if (sub.equals("give")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return getEnchantSuggestions(args[2]);
        }

        return Collections.emptyList();
    }
    
    /**
     * 인챈트 ID와 한글 이름 모두 포함한 탭 완성 목록
     */
    private List<String> getEnchantSuggestions(String input) {
        String lowerInput = input.toLowerCase();
        List<String> suggestions = new ArrayList<>();
        
        for (CustomEnchant enchant : CustomEnchant.values()) {
            if (enchant.isDisabled()) continue;
            
            String id = enchant.getId();
            String displayName = enchant.getDisplayName();
            
            // ID로 검색
            if (id.startsWith(lowerInput)) {
                suggestions.add(id);
            }
            // 한글 이름으로 검색
            if (displayName.contains(input)) {
                suggestions.add(displayName);
            }
        }
        
        return suggestions;
    }
}
