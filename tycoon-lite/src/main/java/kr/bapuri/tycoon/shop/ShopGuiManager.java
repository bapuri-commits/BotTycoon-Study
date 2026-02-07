package kr.bapuri.tycoon.shop;

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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 상점 GUI 관리자
 * 
 * [Phase 3.B] 상점 인벤토리 GUI 처리
 * 
 * <h2>기능</h2>
 * <ul>
 *   <li>상점 메인 GUI (아이템 목록)</li>
 *   <li>좌클릭: 1개 구매</li>
 *   <li>우클릭: 1개 판매</li>
 *   <li>Shift+좌클릭: 64개 구매</li>
 *   <li>Shift+우클릭: 전체 판매</li>
 * </ul>
 * 
 * <h2>pendingActions 타임아웃</h2>
 * 30초 후 자동 정리로 메모리 누수 방지
 */
public class ShopGuiManager implements Listener {
    
    private static final Logger LOGGER = Logger.getLogger("Tycoon.ShopGui");
    private static final long PENDING_TIMEOUT_MS = 30_000; // 30초 타임아웃
    
    private static final String GUI_TITLE_PREFIX = "§8[상점] §f";
    private static final int GUI_SIZE = 54; // 6줄
    
    private final Plugin plugin;
    private final ShopService shopService;
    
    // 열린 상점 GUI 추적 (UUID -> ShopSession)
    private final Map<UUID, ShopSession> openSessions = new ConcurrentHashMap<>();
    
    // 정리 태스크
    private BukkitTask cleanupTask;
    
    public ShopGuiManager(Plugin plugin, ShopService shopService) {
        this.plugin = plugin;
        this.shopService = shopService;
        
        // 이벤트 리스너 등록
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // 정리 태스크 시작
        startCleanupTask();
        
        LOGGER.info("[ShopGuiManager] 초기화 완료");
    }
    
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredPending, 
                20 * 60, 20 * 60); // 1분마다
    }
    
    /**
     * 상점 GUI 열기
     */
    public void openShop(Player player, IShop shop) {
        // 기존 세션 정리
        closeSession(player.getUniqueId());
        
        // GUI 생성
        String title = GUI_TITLE_PREFIX + shop.getDisplayName();
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);
        
        // 아이템 배치 [Fix] 플레이어별 레벨 보너스 가격 표시
        List<ShopItem> items = shop.getItems();
        int slot = 0;
        for (ShopItem shopItem : items) {
            if (slot >= GUI_SIZE - 9) break; // 마지막 줄은 도움말용
            
            ItemStack display = createDisplayItem(player, shopItem, shop);
            gui.setItem(slot++, display);
        }
        
        // 도움말 아이템 (마지막 줄)
        gui.setItem(GUI_SIZE - 5, createHelpItem());
        
        // 세션 등록
        ShopSession session = new ShopSession(shop.getShopId(), items);
        openSessions.put(player.getUniqueId(), session);
        
        // GUI 열기
        player.openInventory(gui);
        LOGGER.fine("[ShopGui] 열림: " + player.getName() + " -> " + shop.getShopId());
    }
    
    /**
     * 표시용 아이템 생성
     * [Fix] 플레이어 파라미터 추가 - 레벨 보너스 적용 가격 표시
     */
    private ItemStack createDisplayItem(Player player, ShopItem shopItem, IShop shop) {
        Material material = shopItem.getMaterial();
        ItemStack display = new ItemStack(material);
        ItemMeta meta = display.getItemMeta();
        
        if (meta != null) {
            // 이름
            meta.setDisplayName("§f" + formatMaterialName(material));
            
            // 가격 정보 (lore)
            List<String> lore = new ArrayList<>();
            lore.add("");
            
            if (shopItem.canBuy()) {
                // [Fix] 플레이어별 레벨 보너스 적용 가격 조회
                ItemStack tempBuyItem = new ItemStack(material);
                long buyPrice = shop.getBuyPrice(player, tempBuyItem);
                
                // 기본 가격과 비교하여 보너스 표시
                long baseBuyPrice = shopItem.getBaseBuyPrice();
                if (shopService.getPriceTracker() != null) {
                    long dynamic = shopService.getPriceTracker().getBuyPrice(material.name());
                    if (dynamic > 0) baseBuyPrice = dynamic;
                }
                
                if (buyPrice > baseBuyPrice) {
                    // 레벨 보너스가 적용된 경우
                    lore.add(String.format("§a구매가: §f%s BD §c(+%d%%)", 
                            formatPrice(buyPrice), 
                            Math.round((double)(buyPrice - baseBuyPrice) / baseBuyPrice * 100)));
                } else {
                    lore.add("§a구매가: §f" + formatPrice(buyPrice) + " BD");
                }
            } else {
                lore.add("§7구매 불가");
            }
            
            if (shopItem.canSell()) {
                // [Fix] 플레이어별 레벨 보너스 적용 가격 조회
                ItemStack tempItem = new ItemStack(material);
                long sellPrice = shop.getSellPrice(player, tempItem);
                
                // 기본 가격과 비교하여 보너스 표시
                long basePrice = shopItem.getBaseSellPrice();
                if (shopService.getPriceTracker() != null) {
                    long dynamic = shopService.getPriceTracker().getSellPrice(material.name());
                    if (dynamic > 0) basePrice = dynamic;
                }
                
                if (sellPrice > basePrice) {
                    // 레벨 보너스가 적용된 경우
                    lore.add(String.format("§c판매가: §f%s BD §a(+%d%%)", 
                            formatPrice(sellPrice), 
                            Math.round((double)(sellPrice - basePrice) / basePrice * 100)));
                } else {
                    lore.add("§c판매가: §f" + formatPrice(sellPrice) + " BD");
                }
            } else {
                lore.add("§7판매 불가");
            }
            
            lore.add("");
            lore.add("§e좌클릭: §f1개 구매");
            lore.add("§e우클릭: §f1개 판매");
            lore.add("§eShift+좌: §f64개 구매");
            lore.add("§eShift+우: §f전체 판매");
            
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
                "§f좌클릭: §71개 구매",
                "§f우클릭: §71개 판매",
                "§fShift+좌클릭: §764개 구매",
                "§fShift+우클릭: §7인벤토리 전체 판매",
                "",
                "§7가격은 거래량에 따라 변동됩니다."
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
        
        // 상점 GUI인지 확인
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;
        
        // [수정] 상점 GUI가 열려있으면 모든 클릭 차단 (드래그앤드롭, Shift 이동 방지)
        event.setCancelled(true);
        
        // 클릭한 슬롯
        int slot = event.getRawSlot();
        
        // 상점 GUI 영역 (상위 인벤토리)만 거래 처리
        // 하위 인벤토리(플레이어 인벤토리)는 클릭만 차단하고 거래 처리 안함
        if (slot < 0 || slot >= GUI_SIZE) return;
        
        // 아이템 슬롯 범위 확인
        if (slot >= session.items.size()) return;
        
        ShopItem shopItem = session.items.get(slot);
        if (shopItem == null) return;
        
        // 클릭 타입에 따른 처리
        ClickType click = event.getClick();
        Material material = shopItem.getMaterial();
        
        switch (click) {
            case LEFT -> handleBuy(player, session.shopId, material, 1);
            case SHIFT_LEFT -> handleBuy(player, session.shopId, material, 64);
            case RIGHT -> handleSell(player, session.shopId, material, 1);
            case SHIFT_RIGHT -> handleSellAll(player, session.shopId, material);
            default -> { }
        }
        
        // GUI 새로고침 (가격 변동 반영)
        refreshGui(player, session);
    }
    
    /**
     * [수정] 드래그 이벤트 차단 - 상점 GUI에서 드래그앤드롭 방지
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ShopSession session = openSessions.get(player.getUniqueId());
        if (session == null) return;
        
        // 상점 GUI인지 확인
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;
        
        // 드래그가 상위 인벤토리(상점 GUI)에 영향을 주면 차단
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
    
    // ========== 거래 처리 ==========
    
    private void handleBuy(Player player, String shopId, Material material, int amount) {
        ItemStack item = new ItemStack(material, amount);
        AbstractShop.TransactionResult result = shopService.processBuy(player, shopId, item);
        
        switch (result) {
            case SUCCESS -> player.sendMessage("§a" + formatMaterialName(material) + " x" + amount + " 구매 완료!");
            case INSUFFICIENT_FUNDS -> player.sendMessage("§c잔액이 부족합니다.");
            case INVENTORY_FULL -> player.sendMessage("§c인벤토리가 가득 찼습니다.");
            case ITEM_NOT_TRADEABLE -> player.sendMessage("§c구매할 수 없는 아이템입니다.");
            default -> player.sendMessage("§c구매 실패.");
        }
    }
    
    private void handleSell(Player player, String shopId, Material material, int amount) {
        // [Phase 승급효과] 인벤토리에서 실제 아이템을 찾아서 판매 (CropGrade 보너스 적용)
        ItemStack actualItem = findFirstItemInInventory(player, material);
        if (actualItem == null) {
            player.sendMessage("§c아이템이 부족합니다.");
            return;
        }
        
        // 실제 아이템으로 판매 처리 (등급 보너스 적용)
        ItemStack sellItem = actualItem.clone();
        sellItem.setAmount(Math.min(amount, actualItem.getAmount()));
        
        // 판매 전 가격 조회 (성공 메시지용)
        IShop shop = shopService.getShop(shopId);
        long unitPrice = shop != null ? shop.getSellPrice(player, sellItem) : 0;
        long totalPrice = unitPrice * sellItem.getAmount();
        
        AbstractShop.TransactionResult result = shopService.processSellActual(player, shopId, sellItem);
        
        switch (result) {
            case SUCCESS -> {
                String gradeInfo = getGradeDisplayInfo(sellItem);
                player.sendMessage(String.format(
                    "§a%s%s x%d 판매 완료! §e+%,d BD", 
                    formatMaterialName(material), gradeInfo, sellItem.getAmount(), totalPrice));
            }
            case INSUFFICIENT_ITEMS -> player.sendMessage("§c아이템이 부족합니다.");
            case ITEM_NOT_TRADEABLE -> player.sendMessage("§c판매할 수 없는 아이템입니다.");
            default -> player.sendMessage("§c판매 실패.");
        }
    }
    
    private void handleSellAll(Player player, String shopId, Material material) {
        // [Phase 승급효과] 등급별로 분리해서 판매 (각각 다른 가격 적용)
        IShop shop = shopService.getShop(shopId);
        if (shop == null) {
            player.sendMessage("§c상점을 찾을 수 없습니다.");
            return;
        }
        
        // 인벤토리에서 해당 Material의 모든 아이템 수집 (등급별로 분리)
        java.util.List<ItemStack> itemsToSell = new java.util.ArrayList<>();
        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot != null && slot.getType() == material) {
                itemsToSell.add(slot.clone());
            }
        }
        
        if (itemsToSell.isEmpty()) {
            player.sendMessage("§c판매할 " + formatMaterialName(material) + "이(가) 없습니다.");
            return;
        }
        
        // 각 아이템별로 판매 (등급별 가격 적용)
        long totalEarned = 0;
        int totalSold = 0;
        
        // 농부 등급 카운트
        int normalCropCount = 0;
        int primeCount = 0;
        int trophyCount = 0;
        
        // 어부 희귀도 카운트
        int commonFishCount = 0;
        int uncommonFishCount = 0;
        int rareFishCount = 0;
        int epicFishCount = 0;
        int legendaryFishCount = 0;
        
        for (ItemStack item : itemsToSell) {
            long unitPrice = shop.getSellPrice(player, item);
            long itemTotal = unitPrice * item.getAmount();
            
            AbstractShop.TransactionResult result = shopService.processSellActual(player, shopId, item);
            if (result == AbstractShop.TransactionResult.SUCCESS) {
                totalEarned += itemTotal;
                totalSold += item.getAmount();
                
                // 농부 등급 카운트
                kr.bapuri.tycoon.job.farmer.CropGrade cropGrade = 
                    kr.bapuri.tycoon.job.farmer.CropGrade.getGrade(item);
                if (cropGrade != kr.bapuri.tycoon.job.farmer.CropGrade.NORMAL) {
                    switch (cropGrade) {
                        case PRIME -> primeCount += item.getAmount();
                        case TROPHY -> trophyCount += item.getAmount();
                        default -> {}
                    }
                } else {
                    // 어부 희귀도 카운트
                    kr.bapuri.tycoon.job.fisher.FishRarity fishRarity = 
                        kr.bapuri.tycoon.job.fisher.FishRarity.getRarity(item);
                    switch (fishRarity) {
                        case UNCOMMON -> uncommonFishCount += item.getAmount();
                        case RARE -> rareFishCount += item.getAmount();
                        case EPIC -> epicFishCount += item.getAmount();
                        case LEGENDARY -> legendaryFishCount += item.getAmount();
                        default -> {
                            // 둘 다 일반이면 일반 카운트
                            if (cropGrade == kr.bapuri.tycoon.job.farmer.CropGrade.NORMAL) {
                                normalCropCount += item.getAmount();
                            }
                        }
                    }
                }
            }
        }
        
        if (totalSold > 0) {
            // 등급별 판매 결과 메시지
            StringBuilder msg = new StringBuilder();
            msg.append("§a").append(formatMaterialName(material)).append(" 전체 판매 완료!");
            
            // 일반 (농부/어부 공통)
            int normalTotal = normalCropCount + commonFishCount;
            if (normalTotal > 0) msg.append(" §f일반 x").append(normalTotal);
            
            // 농부 등급
            if (primeCount > 0) msg.append(" §a프라임 x").append(primeCount);
            if (trophyCount > 0) msg.append(" §6트로피 x").append(trophyCount);
            
            // 어부 희귀도
            if (uncommonFishCount > 0) msg.append(" §a고급 x").append(uncommonFishCount);
            if (rareFishCount > 0) msg.append(" §9희귀 x").append(rareFishCount);
            if (epicFishCount > 0) msg.append(" §5영웅 x").append(epicFishCount);
            if (legendaryFishCount > 0) msg.append(" §6전설 x").append(legendaryFishCount);
            
            msg.append(" §e+").append(String.format("%,d", totalEarned)).append(" BD");
            player.sendMessage(msg.toString());
        } else {
            player.sendMessage("§c판매 실패.");
        }
    }
    
    /**
     * [Phase 승급효과] 인벤토리에서 해당 Material의 첫 번째 아이템 찾기
     */
    private ItemStack findFirstItemInInventory(Player player, Material material) {
        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot != null && slot.getType() == material) {
                return slot;
            }
        }
        return null;
    }
    
    /**
     * [Phase 승급효과] 등급 표시 정보 (작물 또는 물고기)
     */
    private String getGradeDisplayInfo(ItemStack item) {
        if (item == null) return "";
        
        // 작물 등급 확인
        kr.bapuri.tycoon.job.farmer.CropGrade cropGrade = 
            kr.bapuri.tycoon.job.farmer.CropGrade.getGrade(item);
        if (cropGrade != kr.bapuri.tycoon.job.farmer.CropGrade.NORMAL) {
            return switch (cropGrade) {
                case PRIME -> " §a(프라임)";
                case TROPHY -> " §6(트로피)";
                default -> "";
            };
        }
        
        // 물고기 희귀도 확인
        kr.bapuri.tycoon.job.fisher.FishRarity fishRarity = 
            kr.bapuri.tycoon.job.fisher.FishRarity.getRarity(item);
        if (fishRarity != kr.bapuri.tycoon.job.fisher.FishRarity.COMMON) {
            return " " + fishRarity.getColorCode() + "(" + fishRarity.getDisplayName() + ")";
        }
        
        return "";
    }
    
    /**
     * GUI 새로고침
     * [Fix] 플레이어별 레벨 보너스 가격 표시
     */
    private void refreshGui(Player player, ShopSession session) {
        IShop shop = shopService.getShop(session.shopId);
        if (shop == null) return;
        
        Inventory inv = player.getOpenInventory().getTopInventory();
        for (int i = 0; i < session.items.size() && i < GUI_SIZE - 9; i++) {
            ShopItem shopItem = session.items.get(i);
            if (shopItem != null) {
                inv.setItem(i, createDisplayItem(player, shopItem, shop));
            }
        }
    }
    
    // ========== 유틸리티 ==========
    
    private void closeSession(UUID uuid) {
        openSessions.remove(uuid);
    }
    
    /**
     * 열린 모든 상점 GUI 닫기 (리로드 시 사용)
     */
    public void closeAllShops() {
        for (UUID uuid : new ArrayList<>(openSessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
                player.sendMessage("§e상점 설정이 변경되어 GUI가 닫혔습니다.");
            }
        }
        openSessions.clear();
        LOGGER.info("[ShopGuiManager] 모든 상점 GUI 닫힘 (리로드)");
    }
    
    /**
     * 만료된 세션 정리
     */
    public void cleanupExpiredPending() {
        long now = System.currentTimeMillis();
        openSessions.entrySet().removeIf(entry -> 
            now - entry.getValue().createdAt > PENDING_TIMEOUT_MS);
    }
    
    /**
     * Material 이름 포맷팅 (각 단어 첫 글자 대문자)
     * 예: IRON_INGOT -> "Iron Ingot"
     */
    private String formatMaterialName(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (result.length() > 0) result.append(" ");
                result.append(Character.toUpperCase(word.charAt(0)));
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }
    
    /**
     * 가격 포맷팅 (천 단위 콤마)
     */
    private String formatPrice(long price) {
        return String.format("%,d", price);
    }
    
    /**
     * 종료
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        openSessions.clear();
        LOGGER.info("[ShopGuiManager] 종료됨");
    }
    
    // ========== 내부 클래스 ==========
    
    private static class ShopSession {
        final String shopId;
        final List<ShopItem> items;
        final long createdAt;
        
        ShopSession(String shopId, List<ShopItem> items) {
            this.shopId = shopId;
            this.items = items;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
