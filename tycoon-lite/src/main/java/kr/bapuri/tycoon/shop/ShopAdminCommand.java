package kr.bapuri.tycoon.shop;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 상점 관리자 명령어
 * 
 * [Phase 3.B] /shopadmin 명령어
 * 
 * <h2>사용법</h2>
 * <ul>
 *   <li>/shopadmin open [shopId] [player] - 상점 GUI 열기</li>
 *   <li>/shopadmin list - 상점 목록 보기</li>
 *   <li>/shopadmin reload - 설정 다시 로드</li>
 *   <li>/shopadmin info [shopId] - 상점 정보 보기</li>
 * </ul>
 * 
 * <h2>권한</h2>
 * tycoon.admin.shop
 */
public class ShopAdminCommand implements CommandExecutor, TabCompleter {
    
    private static final Logger LOGGER = Logger.getLogger("Tycoon.ShopAdmin");
    private static final String PERMISSION = "tycoon.admin.shop";
    
    private final ShopService shopService;
    
    public ShopAdminCommand(ShopService shopService) {
        this.shopService = shopService;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "open" -> handleOpen(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender, args);
            default -> sendHelp(sender);
        }
        
        return true;
    }
    
    /**
     * /shopadmin open [shopId] [player]
     */
    private void handleOpen(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /shopadmin open <shopId> [player]");
            return;
        }
        
        String shopId = args[1];
        IShop shop = shopService.getShop(shopId);
        
        if (shop == null) {
            sender.sendMessage("§c존재하지 않는 상점: " + shopId);
            sender.sendMessage("§7/shopadmin list 로 상점 목록 확인");
            return;
        }
        
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("§c콘솔에서는 플레이어를 지정해야 합니다.");
            return;
        }
        
        boolean success = shopService.openShopGui(target, shopId);
        if (success) {
            sender.sendMessage("§a" + target.getName() + "에게 " + shop.getDisplayName() + " 열림");
            LOGGER.info("[ShopAdmin] " + sender.getName() + " -> " + target.getName() + " opened " + shopId);
        }
    }
    
    /**
     * /shopadmin list
     */
    private void handleList(CommandSender sender) {
        sender.sendMessage("§e=== 상점 목록 ===");
        
        for (IShop shop : shopService.getAllShops()) {
            String status = shop.isEnabled() ? "§a[활성]" : "§c[비활성]";
            sender.sendMessage(String.format(" %s §f%s §7(%s) - %d개 아이템",
                    status, shop.getDisplayName(), shop.getShopId(), shop.getItems().size()));
        }
        
        if (shopService.getAllShops().isEmpty()) {
            sender.sendMessage("§7등록된 상점이 없습니다.");
        }
    }
    
    /**
     * /shopadmin reload
     * 
     * [주의] saveAll() 호출 금지!
     * - 외부에서 수정한 shops.yml을 읽어오는 것이 목적
     * - 메모리 데이터를 저장하면 수정 내용이 덮어씌워짐
     */
    private void handleReload(CommandSender sender) {
        sender.sendMessage("§e상점 설정을 다시 로드합니다...");
        
        try {
            int[] result = shopService.reload();
            int success = result[0];
            int failed = result[1];
            
            if (failed == 0) {
                sender.sendMessage("§a상점 설정 리로드 완료! (" + success + "개 상점)");
                LOGGER.info("[ShopAdmin] " + sender.getName() + " reloaded shop config");
            } else {
                sender.sendMessage("§c리로드 부분 실패!");
                sender.sendMessage("§a  성공: " + success + "개");
                sender.sendMessage("§c  실패: " + failed + "개 (차익거래 설정 오류)");
                sender.sendMessage("§e  서버 로그에서 상세 내용을 확인하세요.");
                sender.sendMessage("§7  실패한 상점은 이전 설정을 유지합니다.");
                LOGGER.warning("[ShopAdmin] " + sender.getName() + " reload partial failure");
            }
        } catch (Exception e) {
            sender.sendMessage("§c리로드 실패: " + e.getMessage());
            LOGGER.warning("[ShopAdmin] reload failed: " + e.getMessage());
        }
    }
    
    /**
     * /shopadmin info [shopId]
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /shopadmin info <shopId>");
            return;
        }
        
        String shopId = args[1];
        IShop shop = shopService.getShop(shopId);
        
        if (shop == null) {
            sender.sendMessage("§c존재하지 않는 상점: " + shopId);
            return;
        }
        
        sender.sendMessage("§e=== " + shop.getDisplayName() + " ===");
        sender.sendMessage("§7ID: §f" + shop.getShopId());
        sender.sendMessage("§7상태: " + (shop.isEnabled() ? "§a활성" : "§c비활성"));
        sender.sendMessage("§7아이템 수: §f" + shop.getItems().size());
        
        // 가격 범위
        List<ShopItem> items = shop.getItems();
        if (!items.isEmpty()) {
            long minBuy = items.stream()
                    .filter(ShopItem::canBuy)
                    .mapToLong(ShopItem::getBaseBuyPrice)
                    .min().orElse(0);
            long maxBuy = items.stream()
                    .filter(ShopItem::canBuy)
                    .mapToLong(ShopItem::getBaseBuyPrice)
                    .max().orElse(0);
            sender.sendMessage(String.format("§7구매가 범위: §f%,d ~ %,d BD", minBuy, maxBuy));
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e=== /shopadmin 도움말 ===");
        sender.sendMessage("§f/shopadmin open <shopId> [player] §7- 상점 GUI 열기");
        sender.sendMessage("§f/shopadmin list §7- 상점 목록");
        sender.sendMessage("§f/shopadmin info <shopId> §7- 상점 정보");
        sender.sendMessage("§f/shopadmin reload §7- 설정 다시 로드");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("open", "list", "info", "reload"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("open") || sub.equals("info")) {
                // 상점 ID 자동완성
                completions.addAll(shopService.getAllShops().stream()
                        .map(IShop::getShopId)
                        .collect(Collectors.toList()));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
            // 플레이어 이름 자동완성
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
        }
        
        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
