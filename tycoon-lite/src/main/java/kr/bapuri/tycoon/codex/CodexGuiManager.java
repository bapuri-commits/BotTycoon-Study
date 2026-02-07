package kr.bapuri.tycoon.codex;

import kr.bapuri.tycoon.economy.CurrencyType;
import kr.bapuri.tycoon.enhance.lamp.LampType;
import kr.bapuri.tycoon.enhance.upgrade.ProtectionScrollFactory;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * CodexGuiManager - 도감 GUI 관리 (LITE)
 * 
 * 2탭 구조:
 * - 수집도감: 아이템 목록 (진행도 표시, 클릭으로 등록)
 * - 보상: 마일스톤/카테고리 완성 보상
 * 
 * Phase 7 개선사항:
 * - 카테고리별 자연스러운 그리드 배치 (새 줄에서 시작)
 * - 클릭 쿨다운으로 빠른 클릭 버그 방지
 */
public class CodexGuiManager implements Listener {

    // ========== 텍스트 기반 아이콘 ==========
    private static final String ICON_CHECK = "§a✔";
    private static final String BAR_EMPTY = "§7░";
    private static final String BAR_FILLED = "§a█";
    private static final int PROGRESS_BAR_LENGTH = 10;

    private static final String TITLE_COLLECTION = "§6§l수집도감";
    private static final String TITLE_REWARD = "§d§l보상";
    
    private static final int GUI_SIZE = 54; // 6줄
    
    // 고정 슬롯
    private static final int SLOT_TAB_COLLECTION = 0;
    private static final int SLOT_TAB_REWARD = 1;
    private static final int SLOT_PROGRESS = 4;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final int SLOT_PAGE_INFO = 49;

    // 아이템 영역 (슬롯 9~44, 4줄 36칸)
    private static final int ITEMS_START_SLOT = 9;
    private static final int ITEMS_END_SLOT = 45;
    private static final int ITEMS_PER_ROW = 9;
    private static final int ITEMS_ROWS = 4;

    private final JavaPlugin plugin;
    private final CodexService codexService;
    private final PlayerDataManager dataManager;

    // 열린 GUI 추적
    private final Map<Inventory, GuiSession> openGuis = new HashMap<>();
    private final Map<UUID, Inventory> playerGuis = new HashMap<>();
    
    // 클릭 쿨다운 (빠른 클릭 버그 방지)
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN_MS = 200;

    public CodexGuiManager(JavaPlugin plugin, CodexService codexService, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.codexService = codexService;
        this.dataManager = dataManager;
    }

    /**
     * 도감 GUI 열기
     */
    public void openGui(Player player) {
        openGui(player, TabType.COLLECTION, 1);
    }

    /**
     * 특정 탭/페이지로 GUI 열기
     */
    public void openGui(Player player, TabType tab, int page) {
        // 기존 GUI 정리
        Inventory oldInv = playerGuis.get(player.getUniqueId());
        if (oldInv != null) {
            openGuis.remove(oldInv);
        }

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, 
            tab == TabType.COLLECTION ? TITLE_COLLECTION : TITLE_REWARD);
        
        GuiSession session = new GuiSession(player.getUniqueId(), tab, page);
        openGuis.put(inv, session);
        playerGuis.put(player.getUniqueId(), inv);

        renderGui(player, inv, session);
        player.openInventory(inv);
    }

    /**
     * GUI 렌더링
     */
    private void renderGui(Player player, Inventory inv, GuiSession session) {
        inv.clear();

        // 테두리 채우기
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        // 탭 버튼
        renderTabs(inv, session.tab);

        // 진행도 표시
        renderProgress(player, inv);

        // 탭별 컨텐츠
        if (session.tab == TabType.COLLECTION) {
            renderCollectionTab(player, inv, session);
        } else {
            renderRewardTab(player, inv, session);
        }
    }

    /**
     * 탭 버튼 렌더링
     */
    private void renderTabs(Inventory inv, TabType currentTab) {
        boolean isCollection = (currentTab == TabType.COLLECTION);
        ItemStack collectionTab = createItem(
            isCollection ? Material.WRITABLE_BOOK : Material.BOOK,
            isCollection ? "§a§l▶ 수집도감" : "§7수집도감",
            Arrays.asList("", "§7클릭하여 도감 목록 보기")
        );
        inv.setItem(SLOT_TAB_COLLECTION, collectionTab);

        boolean isReward = (currentTab == TabType.REWARD);
        ItemStack rewardTab = createItem(
            isReward ? Material.CHEST : Material.ENDER_CHEST,
            isReward ? "§d§l▶ 보상" : "§7보상",
            Arrays.asList("", "§7클릭하여 보상 목록 보기")
        );
        inv.setItem(SLOT_TAB_REWARD, rewardTab);
    }

    /**
     * 진행도 표시
     */
    private void renderProgress(Player player, Inventory inv) {
        int collected = codexService.getCollectedCount(player);
        int total = codexService.getTotalCount();
        double percent = codexService.getProgressPercent(player.getUniqueId());

        String progressBar = buildProgressBar(percent);

        ItemStack progress = createItem(
            Material.CLOCK,
            "§e도감 진행도 §7(" + collected + "/" + total + ")",
            Arrays.asList(
                "",
                progressBar,
                "",
                "§f진행률: §a" + String.format("%.1f", percent) + "%"
            )
        );
        inv.setItem(SLOT_PROGRESS, progress);
    }
    
    private String buildProgressBar(double percent) {
        int filled = (int) Math.round(percent / 100.0 * PROGRESS_BAR_LENGTH);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < PROGRESS_BAR_LENGTH; i++) {
            bar.append(i < filled ? BAR_FILLED : BAR_EMPTY);
        }
        return bar.toString();
    }

    /**
     * 수집도감 탭 렌더링 (카테고리별 자연스러운 배치)
     * 
     * 개선사항: 각 카테고리가 새 줄에서 시작하여 시각적 구분
     */
    private void renderCollectionTab(Player player, Inventory inv, GuiSession session) {
        CodexRegistry registry = codexService.getRegistry();
        
        // 카테고리별로 아이템 + 슬롯 위치 계산
        List<SlotItem> allItems = new ArrayList<>();
        int currentRow = 0;
        int currentCol = 0;
        
        for (String category : registry.getCategoryOrder()) {
            List<CodexRule> rules = registry.getByCategory(category);
            if (rules.isEmpty()) continue;
            
            // 새 카테고리는 새 줄에서 시작 (첫 카테고리 제외)
            if (!allItems.isEmpty() && currentCol > 0) {
                currentRow++;
                currentCol = 0;
            }
            
            for (CodexRule rule : rules) {
                if (currentRow >= ITEMS_ROWS) {
                    // 페이지 넘어감
                    currentRow = 0;
                    currentCol = 0;
                }
                
                int slot = ITEMS_START_SLOT + (currentRow * ITEMS_PER_ROW) + currentCol;
                allItems.add(new SlotItem(slot, rule, category));
                
                currentCol++;
                if (currentCol >= ITEMS_PER_ROW) {
                    currentCol = 0;
                    currentRow++;
                }
            }
        }

        // 페이지 계산
        int itemsPerPage = ITEMS_ROWS * ITEMS_PER_ROW; // 36
        int totalPages = (int) Math.ceil(allItems.size() / (double) itemsPerPage);
        if (totalPages < 1) totalPages = 1;
        if (session.page > totalPages) session.page = totalPages;
        if (session.page < 1) session.page = 1;

        // 현재 페이지 아이템만 렌더링
        int startIdx = (session.page - 1) * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, allItems.size());

        for (int i = startIdx; i < endIdx; i++) {
            SlotItem si = allItems.get(i);
            int displaySlot = ITEMS_START_SLOT + (i - startIdx);
            
            CodexRule rule = si.rule;
            Material mat = rule.getMaterial();
            boolean unlocked = codexService.isUnlocked(player, mat);
            int required = Math.max(rule.getRequiredCount(), 1);
            int playerHas = countVanillaItems(player, mat);

            ItemStack item = new ItemStack(mat, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = rule.getKoreanDisplayName();
                if (unlocked) {
                    meta.setDisplayName(ICON_CHECK + " §a" + name);
                } else {
                    meta.setDisplayName("§7" + name);
                }

                List<String> lore = new ArrayList<>();
                lore.add("§8카테고리: " + rule.getCategory());
                lore.add("");
                
                if (unlocked) {
                    lore.add("§a§l등록 완료!");
                    // 등록된 아이템에 인챈트 글로우 효과 추가
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                } else {
                    int progress = Math.min(playerHas, required);
                    String progressColor = progress >= required ? "§a" : "§c";
                    lore.add("§f진행도: " + progressColor + progress + "§7/" + required);
                    
                    if (rule.isConsumeOnRegister()) {
                        lore.add("§7(등록 시 아이템 소멸)");
                    } else {
                        lore.add("§7(등록 시 아이템 유지)");
                    }
                    
                    lore.add("");
                    if (progress >= required) {
                        lore.add("§e클릭하여 등록!");
                    } else {
                        lore.add("§c아이템이 부족합니다.");
                    }
                }

                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }

            inv.setItem(displaySlot, item);
        }

        // 페이지 네비게이션
        renderPageNavigation(inv, session.page, totalPages);
        
        // 세션에 페이지 정보 저장
        session.totalPages = totalPages;
    }

    /**
     * 보상 탭 렌더링
     */
    private void renderRewardTab(Player player, Inventory inv, GuiSession session) {
        UUID uuid = player.getUniqueId();
        PlayerTycoonData data = dataManager.get(player);
        int collected = codexService.getCollectedCount(player);
        CodexRegistry registry = codexService.getRegistry();
        
        int slot = 9;
        
        // ===== 마일스톤 보상 섹션 =====
        ItemStack milestoneHeader = createItem(
            Material.GOLD_INGOT,
            "§6§l마일스톤 보상",
            Arrays.asList("", "§7도감 수집 개수에 따른 보상")
        );
        inv.setItem(slot++, milestoneHeader);
        slot++; // 공백
        
        // 마일스톤 목록 (정렬)
        List<Integer> sortedMilestones = new ArrayList<>(registry.getMilestones().keySet());
        Collections.sort(sortedMilestones);
        
        for (int milestone : sortedMilestones) {
            if (slot >= 27) break; // 2줄까지 표시 (슬롯 9-26)
            
            CodexRegistry.MilestoneReward reward = registry.getMilestones().get(milestone);
            boolean claimed = data.hasClaimedCodexMilestone(milestone);
            boolean canClaim = collected >= milestone;
            
            Material mat;
            String status;
            if (claimed) {
                mat = Material.LIME_DYE;
                status = ICON_CHECK + " §a수령 완료";
            } else if (canClaim) {
                mat = Material.YELLOW_DYE;
                status = "§e달성! (자동 지급됨)";
            } else {
                mat = Material.GRAY_DYE;
                status = "§7" + collected + "/" + milestone;
            }
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(status);
            lore.add("");
            if (reward != null) {
                lore.add("§d보상:");
                if (reward.getBottcoin() > 0) {
                    lore.add("  §e" + CurrencyType.BOTTCOIN.format(reward.getBottcoin()));
                }
                if (reward.getBd() > 0) {
                    lore.add("  §a" + CurrencyType.BD.format(reward.getBd()));
                }
                // [2026-02-01] 아이템 보상 표시 추가
                if (reward.hasItems()) {
                    lore.add("");
                    lore.add("§b아이템:");
                    for (String itemDef : reward.getItems()) {
                        String displayName = getItemDisplayName(itemDef);
                        lore.add("  §f• " + displayName);
                    }
                }
            }
            
            ItemStack item = createItem(mat, "§e" + milestone + "개 마일스톤", lore);
            inv.setItem(slot++, item);
        }
        
        // ===== 카테고리 완성 보상 섹션 =====
        slot = 36; // 4번째 줄부터 (마일스톤이 2줄 차지)
        
        ItemStack categoryHeader = createItem(
            Material.DIAMOND,
            "§b§l카테고리 완성 보상",
            Arrays.asList("", "§7카테고리별 전체 수집 보상")
        );
        inv.setItem(slot++, categoryHeader);
        slot++; // 공백
        
        for (String category : registry.getCategoryOrder()) {
            if (slot >= 45) break;
            
            List<CodexRule> rules = registry.getByCategory(category);
            int catTotal = rules.size();
            int catProgress = codexService.getCategoryProgress(uuid, category);
            long catReward = codexService.getCategoryCompleteReward(category);
            
            boolean categoryClaimd = data.hasClaimedCodexCategory(category);
            boolean complete = catProgress == catTotal;
            
            Material mat;
            String status;
            if (categoryClaimd) {
                mat = Material.LIME_STAINED_GLASS_PANE;
                status = ICON_CHECK + " §a수령 완료";
            } else if (complete) {
                mat = Material.YELLOW_STAINED_GLASS_PANE;
                status = "§e완성! (자동 지급됨)";
            } else {
                mat = Material.GRAY_STAINED_GLASS_PANE;
                status = "§7" + catProgress + "/" + catTotal;
            }
            
            ItemStack item = createItem(mat, "§b" + category,
                Arrays.asList(
                    "",
                    status,
                    "",
                    "§d보상: " + CurrencyType.BOTTCOIN.format(catReward)
                )
            );
            inv.setItem(slot++, item);
        }

        // 페이지 네비게이션 (현재 1페이지만)
        renderPageNavigation(inv, 1, 1);
    }

    /**
     * 페이지 네비게이션 렌더링
     */
    private void renderPageNavigation(Inventory inv, int currentPage, int totalPages) {
        // 이전 페이지
        if (currentPage > 1) {
            ItemStack prev = createItem(
                Material.ARROW,
                "§e◀ 이전 페이지",
                Arrays.asList("§7페이지 " + (currentPage - 1))
            );
            inv.setItem(SLOT_PREV_PAGE, prev);
        }

        // 페이지 정보
        ItemStack pageInfo = createItem(
            Material.PAPER,
            "§f페이지 " + currentPage + " / " + totalPages,
            null
        );
        inv.setItem(SLOT_PAGE_INFO, pageInfo);

        // 다음 페이지
        if (currentPage < totalPages) {
            ItemStack next = createItem(
                Material.ARROW,
                "§e다음 페이지 ▶",
                Arrays.asList("§7페이지 " + (currentPage + 1))
            );
            inv.setItem(SLOT_NEXT_PAGE, next);
        }
    }

    // ========== 이벤트 핸들러 ==========

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        GuiSession session = openGuis.get(inv);
        if (session == null) return;

        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) return;

        event.setCancelled(true);
        
        // 클릭 쿨다운 체크
        long now = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(player.getUniqueId());
        if (lastClick != null && now - lastClick < CLICK_COOLDOWN_MS) {
            return;
        }
        lastClickTime.put(player.getUniqueId(), now);

        // 상단 인벤토리만 처리
        if (event.getClickedInventory() == null || 
            !event.getClickedInventory().equals(inv)) {
            return;
        }

        int slot = event.getSlot();

        // 탭 클릭
        if (slot == SLOT_TAB_COLLECTION && session.tab != TabType.COLLECTION) {
            openGui(player, TabType.COLLECTION, 1);
            return;
        }
        if (slot == SLOT_TAB_REWARD && session.tab != TabType.REWARD) {
            openGui(player, TabType.REWARD, 1);
            return;
        }

        // 페이지 네비게이션
        if (slot == SLOT_PREV_PAGE && session.page > 1) {
            session.page--;
            renderGui(player, inv, session);
            return;
        }
        if (slot == SLOT_NEXT_PAGE && session.page < session.totalPages) {
            session.page++;
            renderGui(player, inv, session);
            return;
        }

        // 수집도감 탭에서 아이템 클릭 (등록 시도)
        if (session.tab == TabType.COLLECTION && slot >= ITEMS_START_SLOT && slot < ITEMS_END_SLOT) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;

            Material mat = clicked.getType();
            
            // 이미 등록된 아이템은 무시
            if (codexService.isUnlocked(player, mat)) {
                player.sendMessage("§e이미 도감에 등록된 아이템입니다.");
                return;
            }

            // 등록 시도
            CodexRegisterResult result = codexService.tryRegister(player, mat);
            
            switch (result) {
                case SUCCESS -> {
                    // 성공 메시지는 CodexService에서 전송됨
                    renderGui(player, inv, session);
                }
                case NOT_ENOUGH_ITEMS -> {
                    CodexRule rule = codexService.getRule(mat);
                    int need = rule != null ? rule.getRequiredCount() : 1;
                    int have = countVanillaItems(player, mat);
                    player.sendMessage("§c아이템이 부족합니다. (" + have + "/" + need + ")");
                }
                case NOT_IN_CODEX -> {
                    player.sendMessage("§c이 아이템은 도감에 등록할 수 없습니다.");
                }
                case ALREADY_REGISTERED -> {
                    player.sendMessage("§e이미 도감에 등록된 아이템입니다.");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        GuiSession session = openGuis.remove(inv);
        if (session != null) {
            playerGuis.remove(session.playerUuid);
            lastClickTime.remove(session.playerUuid);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (!openGuis.containsKey(topInv)) return;

        // 도감 GUI 슬롯에 드래그하려는 경우 취소
        for (int slot : event.getRawSlots()) {
            if (slot < topInv.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ========== 유틸리티 ==========

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 순수 바닐라 아이템만 카운트 (CustomModelData/DisplayName 없는 것만)
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

    // ========== 내부 타입 ==========

    public enum TabType {
        COLLECTION,
        REWARD
    }

    private static class GuiSession {
        final UUID playerUuid;
        TabType tab;
        int page;
        int totalPages = 1;

        GuiSession(UUID playerUuid, TabType tab, int page) {
            this.playerUuid = playerUuid;
            this.tab = tab;
            this.page = page;
        }
    }
    
    /**
     * 슬롯-아이템 매핑용 내부 클래스
     */
    private static class SlotItem {
        final int slot;
        final CodexRule rule;
        final String category;
        
        SlotItem(int slot, CodexRule rule, String category) {
            this.slot = slot;
            this.rule = rule;
            this.category = category;
        }
    }
    
    /**
     * 아이템 정의에서 표시 이름 추출
     * [2026-02-01] 마일스톤 보상 GUI 표시용 추가
     */
    private String getItemDisplayName(String itemDef) {
        String[] parts = itemDef.split(":");
        if (parts.length < 3) return itemDef;
        
        String type = parts[0].toLowerCase();
        String param = parts[1];
        int amount = 1;
        try {
            amount = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {}
        
        String name = switch (type) {
            case "lamp" -> {
                LampType lampType = LampType.fromId(param);
                yield lampType != null ? lampType.getDisplayName() : param;
            }
            case "enchant_random" -> "§d랜덤 " + param + "등급 인챈트북";
            case "scroll" -> {
                ProtectionScrollFactory.ProtectionType scrollType = 
                    ProtectionScrollFactory.ProtectionType.fromId(param);
                yield scrollType != null ? scrollType.getDisplayName() : param;
            }
            default -> param;
        };
        
        return name + (amount > 1 ? " x" + amount : "");
    }
}
