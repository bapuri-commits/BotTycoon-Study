package kr.bapuri.tycoon.shop.special;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.enhance.lamp.LampItemFactory;
import kr.bapuri.tycoon.enhance.lamp.LampSlotExpandTicket;
import kr.bapuri.tycoon.enhance.lamp.LampType;
import kr.bapuri.tycoon.enhance.upgrade.ProtectionScrollFactory;
import kr.bapuri.tycoon.item.CoreItemService;
import kr.bapuri.tycoon.item.CoreItemType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
 * SpecialItemShop - 특수 아이템 상점
 * 
 * [2026-01-29] CoreItem + 램프 + 주문서 통합 상점
 * [2026-01-30] 램프 제거권 제거 (덮어쓰기 방식으로 대체)
 * 
 * <h2>포함 아이템</h2>
 * <ul>
 *   <li>CoreItem: 보호권, 귀환서, 뽑기권 등</li>
 *   <li>Lamp: 무기/방어구/도구 램프</li>
 *   <li>LampTicket: 슬롯 확장권</li>
 *   <li>ProtectionScroll: 파괴/하락 방지 주문서</li>
 * </ul>
 */
public class SpecialItemShop implements Listener {
    
    private static final Logger LOGGER = Logger.getLogger("Tycoon.SpecialShop");
    private static final String GUI_TITLE = "§8[상점] §d특수 아이템 상점";
    private static final int GUI_SIZE = 54;
    
    private final Plugin plugin;
    private final EconomyService economyService;
    private final CoreItemService coreItemService;
    private final LampItemFactory lampItemFactory;
    private final LampSlotExpandTicket slotExpandTicket;
    
    // 상점 아이템 목록
    private final List<SpecialShopItem> items = new ArrayList<>();
    
    // 열린 GUI 추적 (슬롯 → 아이템 매핑 포함)
    private final Map<UUID, Map<Integer, SpecialShopItem>> openGuis = new ConcurrentHashMap<>();
    
    public SpecialItemShop(Plugin plugin, EconomyService economyService, 
                           CoreItemService coreItemService,
                           LampItemFactory lampItemFactory,
                           LampSlotExpandTicket slotExpandTicket) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.coreItemService = coreItemService;
        this.lampItemFactory = lampItemFactory;
        this.slotExpandTicket = slotExpandTicket;
        
        // 이벤트 리스너 등록
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        LOGGER.info("[SpecialItemShop] 초기화 완료");
    }
    
    /**
     * config.yml에서 설정 로드
     */
    public void loadFromConfig(ConfigurationSection section) {
        items.clear();
        
        if (section == null) {
            LOGGER.warning("[SpecialItemShop] 설정 섹션이 null입니다.");
            loadDefaultItems();
            return;
        }
        
        List<?> itemList = section.getList("items");
        if (itemList == null) {
            LOGGER.warning("[SpecialItemShop] items 목록이 없습니다.");
            loadDefaultItems();
            return;
        }
        
        for (Object obj : itemList) {
            if (obj instanceof Map<?, ?> map) {
                try {
                    String id = (String) map.get("id");
                    String type = (String) map.get("type");
                    long buyPrice = ((Number) map.get("buyPrice")).longValue();
                    long sellPrice = map.get("sellPrice") != null ? 
                            ((Number) map.get("sellPrice")).longValue() : -1;
                    
                    SpecialShopItem item = createShopItem(id, type, map, buyPrice, sellPrice);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    LOGGER.warning("[SpecialItemShop] 아이템 로드 실패: " + obj + " - " + e.getMessage());
                }
            }
        }
        
        LOGGER.info("[SpecialItemShop] " + items.size() + "개 아이템 로드됨");
    }
    
    /**
     * 기본 아이템 목록 로드 (설정 없을 때)
     */
    private void loadDefaultItems() {
        // CoreItem
        items.add(new SpecialShopItem("universal_inventory_save", "coreitem", 
                CoreItemType.UNIVERSAL_INVENTORY_SAVE, 5000, 2500));
        items.add(new SpecialShopItem("return_scroll", "coreitem", 
                CoreItemType.RETURN_SCROLL, 2000, 1000));
        items.add(new SpecialShopItem("teleport_scroll", "coreitem", 
                CoreItemType.TELEPORT_SCROLL, 10000, 5000));
        items.add(new SpecialShopItem("rebirth_memory_scroll", "coreitem", 
                CoreItemType.REBIRTH_MEMORY_SCROLL, 10000, 5000));
        items.add(new SpecialShopItem("basic_enchant_lottery", "coreitem", 
                CoreItemType.BASIC_ENCHANT_LOTTERY, 50000, -1));
        items.add(new SpecialShopItem("special_enchant_lottery", "coreitem", 
                CoreItemType.SPECIAL_ENCHANT_LOTTERY, 100000, -1));
        
        // Lamp
        items.add(new SpecialShopItem("lamp_weapon", "lamp", LampType.WEAPON_LAMP, 100000, -1));
        items.add(new SpecialShopItem("lamp_armor", "lamp", LampType.ARMOR_LAMP, 100000, -1));
        items.add(new SpecialShopItem("lamp_tool", "lamp", LampType.TOOL_LAMP, 100000, -1));
        
        // Lamp Ticket
        items.add(new SpecialShopItem("lamp_slot_expand", "lamp_ticket", "SLOT_EXPAND", 200000, -1));
        // [Phase 8] lamp_remove_ticket (슬롯 제거권) 제거 - 불필요
        
        // Protection Scroll
        items.add(new SpecialShopItem("scroll_destroy", "protection_scroll", 
                ProtectionScrollFactory.ProtectionType.DESTROY, 5000, -1));
        items.add(new SpecialShopItem("scroll_downgrade", "protection_scroll", 
                ProtectionScrollFactory.ProtectionType.DOWNGRADE, 5000, -1));
        
        LOGGER.info("[SpecialItemShop] 기본 " + items.size() + "개 아이템 로드됨");
    }
    
    /**
     * 설정에서 상점 아이템 생성
     */
    private SpecialShopItem createShopItem(String id, String type, Map<?, ?> map, 
                                           long buyPrice, long sellPrice) {
        return switch (type) {
            case "coreitem" -> {
                String coreTypeName = (String) map.get("coreItemType");
                CoreItemType coreType = CoreItemType.valueOf(coreTypeName);
                yield new SpecialShopItem(id, type, coreType, buyPrice, sellPrice);
            }
            case "lamp" -> {
                String lampTypeName = (String) map.get("lampType");
                LampType lampType = LampType.valueOf(lampTypeName);
                yield new SpecialShopItem(id, type, lampType, buyPrice, sellPrice);
            }
            case "lamp_ticket" -> {
                String ticketType = (String) map.get("ticketType");
                yield new SpecialShopItem(id, type, ticketType, buyPrice, sellPrice);
            }
            case "protection_scroll" -> {
                String scrollType = (String) map.get("scrollType");
                ProtectionScrollFactory.ProtectionType pType = 
                        ProtectionScrollFactory.ProtectionType.valueOf(scrollType);
                yield new SpecialShopItem(id, type, pType, buyPrice, sellPrice);
            }
            default -> {
                LOGGER.warning("[SpecialItemShop] 알 수 없는 타입: " + type);
                yield null;
            }
        };
    }
    
    /**
     * 상점 GUI 열기
     */
    public void openShop(Player player) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        
        // [Fix v2] null 아이템 건너뛰고 슬롯 연속 배치 + 매핑 저장
        Map<Integer, SpecialShopItem> slotMapping = new HashMap<>();
        int slot = 0;
        for (int i = 0; i < items.size() && slot < GUI_SIZE - 9; i++) {
            SpecialShopItem shopItem = items.get(i);
            ItemStack display = createDisplayItem(shopItem);
            if (display != null) {
                gui.setItem(slot, display);
                slotMapping.put(slot, shopItem);
                slot++;
            }
        }
        
        // 도움말 아이템
        gui.setItem(GUI_SIZE - 5, createHelpItem());
        
        openGuis.put(player.getUniqueId(), slotMapping);
        player.openInventory(gui);
        
        LOGGER.fine("[SpecialShop] 열림: " + player.getName() + " (아이템 " + slot + "개)");
    }
    
    /**
     * 표시용 아이템 생성
     */
    private ItemStack createDisplayItem(SpecialShopItem shopItem) {
        ItemStack actualItem = createActualItem(shopItem);
        if (actualItem == null) return null;
        
        ItemStack display = actualItem.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return null;
        
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        
        // 가격 정보 추가
        lore.add("");
        lore.add("§8─────────────────────");
        lore.add("§a구매가: §f" + formatPrice(shopItem.buyPrice) + " BD");
        if (shopItem.sellPrice > 0) {
            lore.add("§c판매가: §f" + formatPrice(shopItem.sellPrice) + " BD");
        } else {
            lore.add("§7판매 불가");
        }
        lore.add("§8─────────────────────");
        lore.add("§e좌클릭: §f1개 구매");
        if (shopItem.sellPrice > 0) {
            lore.add("§e우클릭: §f1개 판매");
        }
        
        meta.setLore(lore);
        display.setItemMeta(meta);
        
        return display;
    }
    
    /**
     * 실제 아이템 생성
     */
    private ItemStack createActualItem(SpecialShopItem shopItem) {
        return switch (shopItem.type) {
            case "coreitem" -> coreItemService.createItem((CoreItemType) shopItem.data, 1);
            case "lamp" -> lampItemFactory.createLamp((LampType) shopItem.data);
            case "lamp_ticket" -> {
                String ticketType = (String) shopItem.data;
                if ("SLOT_EXPAND".equals(ticketType)) {
                    yield slotExpandTicket.createTicket(1);
                }
                // [Phase 8 정리] REMOVE 케이스 제거 - lamp_remove_ticket 삭제
                yield null;
            }
            case "protection_scroll" -> ProtectionScrollFactory.createScroll(
                    (ProtectionScrollFactory.ProtectionType) shopItem.data);
            default -> null;
        };
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
                "§f좌클릭: §71개 구매",
                "§f우클릭: §71개 판매 (가능한 경우)",
                "",
                "§7특수 아이템은 상점에서만 구매 가능합니다."
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
        
        Map<Integer, SpecialShopItem> slotMapping = openGuis.get(player.getUniqueId());
        if (slotMapping == null) return;
        
        String title = event.getView().getTitle();
        if (!title.equals(GUI_TITLE)) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        SpecialShopItem shopItem = slotMapping.get(slot);
        if (shopItem == null) return;  // 빈 슬롯 또는 매핑 없음
        
        ClickType click = event.getClick();
        
        switch (click) {
            case LEFT, SHIFT_LEFT -> handleBuy(player, shopItem);
            case RIGHT, SHIFT_RIGHT -> handleSell(player, shopItem);
            default -> { }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openGuis.containsKey(player.getUniqueId())) return;
        
        String title = event.getView().getTitle();
        if (!title.equals(GUI_TITLE)) return;
        
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
            openGuis.remove(player.getUniqueId());
        }
    }
    
    // ========== 거래 처리 ==========
    
    private void handleBuy(Player player, SpecialShopItem shopItem) {
        long price = shopItem.buyPrice;
        
        if (!economyService.hasBalance(player, price)) {
            player.sendMessage("§c잔액이 부족합니다. (필요: " + formatPrice(price) + " BD)");
            return;
        }
        
        ItemStack item = createActualItem(shopItem);
        if (item == null) {
            player.sendMessage("§c아이템 생성에 실패했습니다.");
            return;
        }
        
        // 인벤토리 공간 확인
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c인벤토리가 가득 찼습니다.");
            return;
        }
        
        // 결제
        economyService.withdraw(player, price);
        player.getInventory().addItem(item);
        
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() 
                ? item.getItemMeta().getDisplayName() 
                : shopItem.id;
        player.sendMessage("§a" + itemName + " §f구매 완료! (-" + formatPrice(price) + " BD)");
        
        LOGGER.info("[SpecialShop] BUY player=" + player.getName() + " item=" + shopItem.id + " price=" + price);
    }
    
    private void handleSell(Player player, SpecialShopItem shopItem) {
        if (shopItem.sellPrice <= 0) {
            player.sendMessage("§c이 아이템은 판매할 수 없습니다.");
            return;
        }
        
        // 플레이어 인벤토리에서 해당 아이템 찾기
        // TODO: 판매 로직 구현 (CoreItem 인증 필요)
        player.sendMessage("§7판매 기능은 추후 구현 예정입니다.");
    }
    
    // ========== 유틸리티 ==========
    
    private String formatPrice(long price) {
        return String.format("%,d", price);
    }
    
    public void shutdown() {
        openGuis.clear();
        LOGGER.info("[SpecialItemShop] 종료됨");
    }
    
    // ========== 내부 클래스 ==========
    
    /**
     * 상점 아이템 정보
     */
    private static class SpecialShopItem {
        final String id;
        final String type;
        final Object data;
        final long buyPrice;
        final long sellPrice;
        
        SpecialShopItem(String id, String type, Object data, long buyPrice, long sellPrice) {
            this.id = id;
            this.type = type;
            this.data = data;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }
    }
}
