package kr.bapuri.tycoon.bcshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * BC 상점 GUI 관리자
 * 
 * 기존 ShopGuiManager 패턴을 따르되, BC 상점 전용으로 단순화했습니다.
 * 
 * <h2>클릭 동작</h2>
 * <ul>
 *   <li>좌클릭: 구매 (이미 보유 시 장착)</li>
 *   <li>우클릭: 장착 해제</li>
 * </ul>
 */
public class BCShopGuiManager implements Listener {
    
    private static final Logger LOGGER = Logger.getLogger("Tycoon.BCShopGui");
    private static final String GUI_TITLE_PREFIX = "§8[BC상점] §f";
    private static final int GUI_SIZE = 54; // 6줄
    
    private final Plugin plugin;
    
    // 열린 상점 세션 (UUID -> ShopSession)
    private final Map<UUID, ShopSession> openSessions = new ConcurrentHashMap<>();
    
    public BCShopGuiManager(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        LOGGER.info("[BCShopGuiManager] 초기화 완료");
    }
    
    /**
     * 상점 GUI 열기
     */
    public void openShop(Player player, AbstractBCShop shop) {
        if (!shop.isEnabled()) {
            player.sendMessage("§c이 상점은 현재 비활성화되어 있습니다.");
            return;
        }
        
        // 기존 세션 정리
        closeSession(player.getUniqueId());
        
        // GUI 생성
        String title = GUI_TITLE_PREFIX + shop.getDisplayName();
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);
        
        // 아이템 배치
        List<BCShopItem> items = shop.getItems();
        int slot = 0;
        for (BCShopItem item : items) {
            if (slot >= GUI_SIZE - 9) break; // 마지막 줄은 도움말용
            gui.setItem(slot++, createDisplayItem(player, item, shop));
        }
        
        // 도움말 아이템
        gui.setItem(GUI_SIZE - 5, createHelpItem());
        
        // 세션 등록
        ShopSession session = new ShopSession(shop, items);
        openSessions.put(player.getUniqueId(), session);
        
        // GUI 열기
        player.openInventory(gui);
        LOGGER.fine("[BCShopGui] 열림: " + player.getName() + " -> " + shop.getShopId());
    }
    
    /**
     * 표시용 아이템 생성
     */
    private ItemStack createDisplayItem(Player player, BCShopItem shopItem, AbstractBCShop shop) {
        ItemStack display = new ItemStack(shopItem.getIcon());
        ItemMeta meta = display.getItemMeta();
        
        if (meta != null) {
            boolean owned = shop.hasItem(player, shopItem);
            boolean active = shop.isItemActive(player, shopItem);
            
            // 이름
            String statusPrefix = active ? "§a✔ " : (owned ? "§e● " : "§f");
            meta.setDisplayName(statusPrefix + shopItem.getName());
            
            // 가격 정보 (lore)
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7" + shopItem.getDescription());
            lore.add("");
            
            if (owned) {
                lore.add("§a✓ 보유 중");
                if (active) {
                    lore.add("§a✓ 활성화됨");
                    lore.add("");
                    lore.add("§e우클릭: §f해제");
                } else {
                    lore.add("");
                    lore.add("§e좌클릭: §f장착");
                    lore.add("§e우클릭: §f해제");
                }
            } else {
                lore.add("§e가격: §f" + shopItem.getPriceDisplay());
                lore.add("");
                lore.add("§e좌클릭: §f구매");
            }
            
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        
        return display;
    }
    
    /**
     * 도움말 아이템
     */
    private ItemStack createHelpItem() {
        ItemStack help = new ItemStack(Material.BOOK);
        ItemMeta meta = help.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e도움말");
            List<String> lore = Arrays.asList(
                "",
                "§f좌클릭: §7구매 / 장착",
                "§f우클릭: §7장착 해제",
                "",
                "§7BC는 업적 달성, 이벤트 참여 등으로",
                "§7획득할 수 있습니다."
            );
            meta.setLore(lore);
            help.setItemMeta(meta);
        }
        return help;
    }
    
    // ========== 이벤트 처리 ==========
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ShopSession session = openSessions.get(player.getUniqueId());
        if (session == null) return;
        
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;
        
        // 모든 클릭 차단
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= session.items.size()) return;
        
        BCShopItem shopItem = session.items.get(slot);
        if (shopItem == null) return;
        
        AbstractBCShop shop = session.shop;
        ClickType click = event.getClick();
        
        switch (click) {
            case LEFT, SHIFT_LEFT -> {
                if (shop.hasItem(player, shopItem)) {
                    // 이미 보유 -> 장착
                    shop.handleToggle(player, shopItem);
                } else {
                    // 미보유 -> 구매
                    shop.handlePurchase(player, shopItem);
                }
            }
            case RIGHT, SHIFT_RIGHT -> {
                // 장착 해제
                if (shop.hasItem(player, shopItem) && shop.isItemActive(player, shopItem)) {
                    shop.handleToggle(player, shopItem);
                }
            }
            default -> { }
        }
        
        // GUI 새로고침
        refreshGui(player, session);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ShopSession session = openSessions.get(player.getUniqueId());
        if (session == null) return;
        
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;
        
        // 드래그 차단
        for (int slot : event.getRawSlots()) {
            if (slot < GUI_SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            closeSession(player.getUniqueId());
        }
    }
    
    /**
     * GUI 새로고침
     */
    private void refreshGui(Player player, ShopSession session) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        for (int i = 0; i < session.items.size() && i < GUI_SIZE - 9; i++) {
            BCShopItem shopItem = session.items.get(i);
            if (shopItem != null) {
                inv.setItem(i, createDisplayItem(player, shopItem, session.shop));
            }
        }
    }
    
    private void closeSession(UUID uuid) {
        openSessions.remove(uuid);
    }
    
    /**
     * 종료
     */
    public void shutdown() {
        for (UUID uuid : new ArrayList<>(openSessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        openSessions.clear();
        LOGGER.info("[BCShopGuiManager] 종료됨");
    }
    
    // ========== 내부 클래스 ==========
    
    private static class ShopSession {
        final AbstractBCShop shop;
        final List<BCShopItem> items;
        
        ShopSession(AbstractBCShop shop, List<BCShopItem> items) {
            this.shop = shop;
            this.items = items;
        }
    }
}
