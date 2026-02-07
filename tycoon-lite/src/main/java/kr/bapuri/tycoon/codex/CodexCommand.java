package kr.bapuri.tycoon.codex;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CodexCommand - 도감 플레이어 명령어
 * 
 * 명령어:
 * - /codex - GUI 열기 (기본)
 * - /codex gui - GUI 열기
 * - /codex [페이지] - 페이지별 진행도 (채팅)
 * - /codex register - 손에 든 아이템 등록
 * - /codex scan - 인벤토리 스캔 후 등록
 * - /codex help - 도움말
 */
public class CodexCommand implements CommandExecutor, TabCompleter {

    private static final int CATEGORIES_PER_PAGE = 3;

    private final CodexService codexService;
    private CodexGuiManager guiManager;

    private static final String USE_PERMISSION = "tycoon.codex.use";
    
    public CodexCommand(CodexService codexService) {
        this.codexService = codexService;
    }
    
    /**
     * GUI 매니저 설정 (순환 의존성 방지를 위해 setter)
     */
    public void setGuiManager(CodexGuiManager guiManager) {
        this.guiManager = guiManager;
    }
    
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

        // 인자 없이 /codex - GUI 열기
        if (args.length == 0) {
            return handleGui(player);
        }
        
        // 서브커맨드 처리 (모든 플레이어 사용 가능)
        // 관리자 명령어는 별도의 /codexadmin 사용
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "gui" -> {
                return handleGui(player);
            }
            case "register" -> {
                return handleRegister(player);
            }
            case "scan" -> {
                return handleScan(player);
            }
            case "list", "chat" -> {
                return handleChatList(player, 1);
            }
            case "help" -> {
                return handleHelp(player);
            }
        }
        
        // 숫자면 채팅 리스트 페이지
        try {
            int page = Integer.parseInt(sub);
            return handleChatList(player, page);
        } catch (NumberFormatException ignored) {
            return handleHelp(player);
        }
    }
    
    /**
     * GUI 열기
     */
    private boolean handleGui(Player player) {
        if (guiManager != null) {
            guiManager.openGui(player);
        } else {
            player.sendMessage("§c도감 GUI가 초기화되지 않았습니다. /codex list 사용");
            return handleChatList(player, 1);
        }
        return true;
    }
    
    /**
     * 채팅 기반 목록 보기
     */
    private boolean handleChatList(Player player, int page) {
        CodexRegistry registry = codexService.getRegistry();
        List<String> categories = registry.getCategoryOrder();

        if (categories.isEmpty()) {
            player.sendMessage("§c도감 데이터가 없습니다. codex.yml을 확인하세요.");
            return true;
        }

        if (page < 1) page = 1;

        int totalPages = (int) Math.ceil(categories.size() / (double) CATEGORIES_PER_PAGE);
        if (page > totalPages) page = totalPages;

        int startIdx = (page - 1) * CATEGORIES_PER_PAGE;
        int endIdx = Math.min(startIdx + CATEGORIES_PER_PAGE, categories.size());

        // ---- 전체 진행도 헤더 ----
        int collected = codexService.getCollectedCount(player.getUniqueId());
        int total = registry.getTotalCount();
        double percent = codexService.getProgressPercent(player.getUniqueId());

        player.sendMessage("§6========== [도감] ==========");
        player.sendMessage("§e전체 진행도: §f" + collected + " / " + total
                + " §7(" + String.format("%.1f", percent) + "%)");
        player.sendMessage("§7페이지: " + page + " / " + totalPages);
        player.sendMessage(" ");

        // ---- 카테고리 단위 출력 ----
        for (int i = startIdx; i < endIdx; i++) {
            String catKey = categories.get(i);
            List<CodexRule> rules = registry.getByCategory(catKey);

            int catTotal = rules.size();
            int catUnlocked = 0;
            for (CodexRule r : rules) {
                if (codexService.isUnlocked(player, r.getMaterial())) catUnlocked++;
            }

            double catPercent = (catTotal == 0) ? 0.0 : (catUnlocked * 100.0 / catTotal);
            boolean complete = catUnlocked == catTotal;

            player.sendMessage("§b■ " + catKey
                    + " §f" + catUnlocked + "/" + catTotal
                    + " §7(" + String.format("%.1f", catPercent) + "%)"
                    + (complete ? " §a✓" : ""));

            // 아이템 목록
            for (CodexRule r : rules) {
                Material mat = r.getMaterial();
                boolean unlocked = codexService.isUnlocked(player, mat);

                String color = unlocked ? "§f" : "§7";
                String status = unlocked ? "§a[등록]" : "§8[미등록]";

                int req = Math.max(r.getRequiredCount(), 1);
                boolean consume = r.isConsumeOnRegister();

                String ruleText = "§7(필요 " + req + "개"
                        + (consume ? ", 등록 시 소멸" : ", 소멸 없음") + ")";

                player.sendMessage("  " + status + " " + color + r.getKoreanDisplayName() + " " + ruleText);
            }

            player.sendMessage(" ");
        }

        player.sendMessage("§6==========================");
        if (page < totalPages) {
            player.sendMessage("§7다음 페이지: /codex " + (page + 1));
        }
        if (page > 1) {
            player.sendMessage("§7이전 페이지: /codex " + (page - 1));
        }
        player.sendMessage("§7도움말: /codex help");

        return true;
    }

    /**
     * /codex register - 손에 든 아이템 등록
     */
    private boolean handleRegister(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        
        if (handItem.getType().isAir()) {
            player.sendMessage("§c손에 등록할 아이템을 들어주세요.");
            return true;
        }

        Material mat = handItem.getType();
        CodexRegisterResult result = codexService.tryRegister(player, mat);

        switch (result) {
            case SUCCESS -> {
                // 성공 메시지는 CodexService에서 이미 전송됨
            }
            case NOT_IN_CODEX -> {
                player.sendMessage("§c이 아이템은 도감에 등록할 수 없습니다.");
            }
            case ALREADY_REGISTERED -> {
                player.sendMessage("§e이미 도감에 등록된 아이템입니다.");
            }
            case NOT_ENOUGH_ITEMS -> {
                CodexRule rule = codexService.getRule(mat);
                int need = rule != null ? rule.getRequiredCount() : 1;
                player.sendMessage("§c아이템이 부족합니다. (필요: " + need + "개)");
            }
        }

        return true;
    }

    /**
     * /codex scan - 인벤토리 전체 스캔 후 등록 가능한 아이템 모두 등록
     */
    private boolean handleScan(Player player) {
        player.sendMessage("§e도감 스캔을 시작합니다...");

        List<Material> registered = new ArrayList<>();
        List<Material> notEnough = new ArrayList<>();

        // 인벤토리의 모든 아이템 확인
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;

            Material mat = item.getType();
            CodexRule rule = codexService.getRule(mat);
            
            if (rule == null) continue;
            if (codexService.isUnlocked(player, mat)) continue;

            CodexRegisterResult result = codexService.tryRegister(player, mat);
            
            switch (result) {
                case SUCCESS -> registered.add(mat);
                case NOT_ENOUGH_ITEMS -> {
                    if (!notEnough.contains(mat)) {
                        notEnough.add(mat);
                    }
                }
                default -> {}
            }
        }

        // 결과 출력
        player.sendMessage("§6========== [도감 스캔 결과] ==========");
        
        if (registered.isEmpty() && notEnough.isEmpty()) {
            player.sendMessage("§7등록 가능한 새 아이템이 없습니다.");
        } else {
            if (!registered.isEmpty()) {
                player.sendMessage("§a등록 완료: §f" + registered.size() + "개");
                for (Material mat : registered) {
                    CodexRule rule = codexService.getRule(mat);
                    String name = rule != null ? rule.getKoreanDisplayName() : mat.name();
                    player.sendMessage("  §a✓ §f" + name);
                }
            }
            
            if (!notEnough.isEmpty()) {
                player.sendMessage("§c수량 부족: §f" + notEnough.size() + "개");
                for (Material mat : notEnough) {
                    CodexRule rule = codexService.getRule(mat);
                    String name = rule != null ? rule.getKoreanDisplayName() : mat.name();
                    int need = rule != null ? rule.getRequiredCount() : 1;
                    int have = countVanillaItems(player, mat);
                    player.sendMessage("  §c✗ §7" + name + " §8(" + have + "/" + need + ")");
                }
            }
        }

        player.sendMessage("§6=====================================");
        return true;
    }

    /**
     * 순수 바닐라 아이템만 카운트 (CustomModelData/DisplayName 없는 것만)
     * CodexGuiManager와 동일한 로직으로 일관성 유지
     */
    private int countVanillaItems(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != mat) continue;
            if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) continue;
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) continue;
            count += item.getAmount();
        }
        return count;
    }

    /**
     * /codex help - 도움말 표시
     */
    private boolean handleHelp(Player player) {
        player.sendMessage("§6========== [도감 명령어] ==========");
        player.sendMessage("§e/codex §7- GUI 열기 (기본)");
        player.sendMessage("§e/codex gui §7- GUI 열기");
        player.sendMessage("§e/codex list §7- 채팅으로 진행도 보기");
        player.sendMessage("§e/codex [페이지] §7- 페이지별 진행도");
        player.sendMessage("§e/codex register §7- 손에 든 아이템 등록");
        player.sendMessage("§e/codex scan §7- 인벤토리 스캔 후 등록");
        player.sendMessage("§e/codex help §7- 이 도움말");
        player.sendMessage("§6==================================");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            
            for (String sub : List.of("gui", "list", "register", "scan", "help")) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
