package kr.bapuri.tycoon.trade;

import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TradeGui - 거래 Chest GUI 관리 (LITE)
 * 
 * 54칸 GUI 레이아웃:
 * - Row 0: 정보 (내 프로필, 상태, 상대 프로필)
 * - Row 1-3: 아이템 (좌: 내가 줄 것, 우: 상대가 줄 것)
 * - Row 4: 화폐 (BD/BC 버튼)
 * - Row 5: 버튼 (취소, 확정, 완료)
 */
public class TradeGui implements Listener {

    private final JavaPlugin plugin;
    private final TradeService tradeService;
    private final PlayerDataManager playerDataManager;
    
    private static final String GUI_TITLE = "§6거래";
    
    // GUI -> 세션 매핑
    private final Map<Inventory, TradeSession> guiToSession = new ConcurrentHashMap<>();
    
    // 채팅 입력 대기 (playerUUID -> 입력 타입)
    private final Map<UUID, CurrencyInputType> pendingCurrencyInput = new ConcurrentHashMap<>();
    
    // 화폐 프리셋
    private long[] bdPresets = {100, 1000, 10000, 100000};
    private int[] bcPresets = {1, 5, 10, 50};
    
    // 슬롯 정의
    private static final int SLOT_MY_PROFILE = 0;
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_OTHER_PROFILE = 8;
    
    // 내 아이템 슬롯 (12칸)
    private static final int[] MY_ITEM_SLOTS = {9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    // 상대 아이템 슬롯 (12칸, 읽기전용)
    private static final int[] OTHER_ITEM_SLOTS = {14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    // 구분선 슬롯
    private static final int[] DIVIDER_SLOTS = {13, 22, 31};
    
    // 화폐 슬롯 (내가 줄 금액)
    private static final int SLOT_BD_MINUS = 36;
    private static final int SLOT_BD_DISPLAY = 37;
    private static final int SLOT_BD_PLUS = 38;
    private static final int SLOT_BC_MINUS = 40;
    private static final int SLOT_BC_DISPLAY = 41;
    private static final int SLOT_BC_PLUS = 42;
    
    // 상대방 화폐 표시 슬롯
    private static final int SLOT_OTHER_BD_DISPLAY = 43;
    private static final int SLOT_OTHER_BC_DISPLAY = 44;
    
    // 버튼 슬롯
    private static final int SLOT_CANCEL = 45;
    private static final int SLOT_CONFIRM = 48;
    private static final int SLOT_COMPLETE = 53;
    
    public TradeGui(JavaPlugin plugin, TradeService tradeService, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.tradeService = tradeService;
        this.playerDataManager = playerDataManager;
    }
    
    /**
     * 화폐 프리셋 설정
     */
    public void setBdPresets(long[] presets) {
        this.bdPresets = presets;
    }
    
    public void setBcPresets(int[] presets) {
        this.bcPresets = presets;
    }
    
    /**
     * 거래 GUI 열기
     */
    public void openTradeGui(Player player, TradeSession session) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        
        // GUI 등록
        if (session.isPlayer1(player.getUniqueId())) {
            session.setPlayer1Gui(gui);
        } else {
            session.setPlayer2Gui(gui);
        }
        guiToSession.put(gui, session);
        
        // 초기 레이아웃 설정
        setupLayout(gui, player, session);
        
        player.openInventory(gui);
    }
    
    /**
     * 초기 레이아웃 설정
     */
    private void setupLayout(Inventory gui, Player player, TradeSession session) {
        UUID playerId = player.getUniqueId();
        boolean isPlayer1 = session.isPlayer1(playerId);
        
        // 빈 슬롯 채우기
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, glass);
        }
        
        // 구분선
        ItemStack divider = createItem(Material.BLACK_STAINED_GLASS_PANE, "§8|");
        for (int slot : DIVIDER_SLOTS) {
            gui.setItem(slot, divider);
        }
        
        // 프로필
        gui.setItem(SLOT_MY_PROFILE, createProfileHead(player, true));
        Player other = Bukkit.getPlayer(session.getOtherPlayer(playerId));
        if (other != null) {
            gui.setItem(SLOT_OTHER_PROFILE, createProfileHead(other, false));
        }
        
        // 상태
        updateStatus(gui, session);
        
        // 아이템 슬롯 비우기 (내 슬롯만 편집 가능)
        for (int slot : MY_ITEM_SLOTS) {
            gui.setItem(slot, null);
        }
        
        // 상대 아이템 슬롯 (읽기전용 표시)
        ItemStack lockedSlot = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7상대방 아이템");
        for (int slot : OTHER_ITEM_SLOTS) {
            gui.setItem(slot, lockedSlot);
        }
        
        // 화폐 버튼
        updateCurrencyDisplay(gui, player, session);
        
        // 버튼
        gui.setItem(SLOT_CANCEL, createItem(Material.BARRIER, "§c거래 취소"));
        updateConfirmButton(gui, isPlayer1 ? session.isPlayer1Confirmed() : session.isPlayer2Confirmed());
        updateCompleteButton(gui, session.isBothConfirmed());
    }
    
    /**
     * 프로필 머리 아이템 생성
     */
    private ItemStack createProfileHead(Player player, boolean isMe) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName((isMe ? "§a내 정보: " : "§e상대방: ") + player.getName());
            
            PlayerTycoonData data = playerDataManager.get(player.getUniqueId());
            NumberFormat nf = NumberFormat.getInstance(Locale.KOREA);
            
            List<String> lore = new ArrayList<>();
            lore.add("§7BD: §f" + nf.format(data.getMoney()));
            lore.add("§7BC: §f" + nf.format(data.getBottCoin()));
            meta.setLore(lore);
            
            head.setItemMeta(meta);
        }
        return head;
    }
    
    /**
     * 화폐 표시 업데이트
     */
    private void updateCurrencyDisplay(Inventory gui, Player player, TradeSession session) {
        boolean isPlayer1 = session.isPlayer1(player.getUniqueId());
        long myBd = isPlayer1 ? session.getPlayer1Bd() : session.getPlayer2Bd();
        long myBc = isPlayer1 ? session.getPlayer1Bc() : session.getPlayer2Bc();
        
        // 상대방 화폐
        long otherBd = isPlayer1 ? session.getPlayer2Bd() : session.getPlayer1Bd();
        long otherBc = isPlayer1 ? session.getPlayer2Bc() : session.getPlayer1Bc();
        String otherName = isPlayer1 ? session.getPlayer2Name() : session.getPlayer1Name();
        
        NumberFormat nf = NumberFormat.getInstance(Locale.KOREA);
        
        // 내가 줄 BD 버튼
        gui.setItem(SLOT_BD_MINUS, createItem(Material.RED_CONCRETE, "§c- BD", 
                "§7클릭: -" + nf.format(bdPresets[0]),
                "§7Shift+클릭: -" + nf.format(bdPresets[1])));
        gui.setItem(SLOT_BD_DISPLAY, createItem(Material.GOLD_INGOT, "§6내가 줄 BD: " + nf.format(myBd),
                "§7클릭하여 직접 입력"));
        gui.setItem(SLOT_BD_PLUS, createItem(Material.LIME_CONCRETE, "§a+ BD",
                "§7클릭: +" + nf.format(bdPresets[0]),
                "§7Shift+클릭: +" + nf.format(bdPresets[1])));
        
        // 내가 줄 BC 버튼
        gui.setItem(SLOT_BC_MINUS, createItem(Material.RED_CONCRETE, "§c- BC",
                "§7클릭: -" + bcPresets[0],
                "§7Shift+클릭: -" + bcPresets[1]));
        gui.setItem(SLOT_BC_DISPLAY, createItem(Material.DIAMOND, "§b내가 줄 BC: " + nf.format(myBc),
                "§7클릭하여 직접 입력"));
        gui.setItem(SLOT_BC_PLUS, createItem(Material.LIME_CONCRETE, "§a+ BC",
                "§7클릭: +" + bcPresets[0],
                "§7Shift+클릭: +" + bcPresets[1]));
        
        // 상대방이 줄 화폐 표시 (읽기 전용)
        gui.setItem(SLOT_OTHER_BD_DISPLAY, createItem(Material.GOLD_NUGGET, 
                "§e" + otherName + " BD: §6" + nf.format(otherBd),
                "§7상대방이 줄 BD"));
        gui.setItem(SLOT_OTHER_BC_DISPLAY, createItem(Material.LAPIS_LAZULI, 
                "§e" + otherName + " BC: §b" + nf.format(otherBc),
                "§7상대방이 줄 BottCoin"));
    }
    
    /**
     * 상태 표시 업데이트
     */
    private void updateStatus(Inventory gui, TradeSession session) {
        String status;
        Material material;
        
        if (session.isBothConfirmed()) {
            status = "§a양쪽 확정됨!";
            material = Material.EMERALD_BLOCK;
        } else if (session.isPlayer1Confirmed() || session.isPlayer2Confirmed()) {
            status = "§e한쪽 확정됨";
            material = Material.GOLD_BLOCK;
        } else {
            status = "§7거래 진행 중";
            material = Material.IRON_BLOCK;
        }
        
        gui.setItem(SLOT_STATUS, createItem(material, status));
    }
    
    /**
     * 확정 버튼 업데이트
     */
    private void updateConfirmButton(Inventory gui, boolean confirmed) {
        if (confirmed) {
            gui.setItem(SLOT_CONFIRM, createItem(Material.LIME_WOOL, "§a✓ 확정됨", "§7클릭하여 확정 취소"));
        } else {
            gui.setItem(SLOT_CONFIRM, createItem(Material.YELLOW_WOOL, "§e확정하기", "§7클릭하여 거래 확정"));
        }
    }
    
    /**
     * 완료 버튼 업데이트
     */
    private void updateCompleteButton(Inventory gui, boolean canComplete) {
        if (canComplete) {
            gui.setItem(SLOT_COMPLETE, createItem(Material.EMERALD, "§a거래 완료", "§7클릭하여 거래 완료"));
        } else {
            gui.setItem(SLOT_COMPLETE, createItem(Material.GRAY_DYE, "§7거래 완료", "§c양쪽 모두 확정해야 합니다"));
        }
    }
    
    /**
     * 상대방 아이템 및 화폐 동기화
     */
    public void syncOtherItems(TradeSession session) {
        NumberFormat nf = NumberFormat.getInstance(Locale.KOREA);
        
        // Player1의 GUI에 Player2 아이템 표시
        Inventory gui1 = session.getPlayer1Gui();
        if (gui1 != null) {
            List<ItemStack> otherItems = session.getPlayer2Items();
            for (int i = 0; i < OTHER_ITEM_SLOTS.length; i++) {
                if (i < otherItems.size() && otherItems.get(i) != null) {
                    ItemStack display = otherItems.get(i).clone();
                    gui1.setItem(OTHER_ITEM_SLOTS[i], display);
                } else {
                    gui1.setItem(OTHER_ITEM_SLOTS[i], createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7빈 슬롯"));
                }
            }
            
            // 상대방 화폐 업데이트 (Player2 → Player1에게 표시)
            gui1.setItem(SLOT_OTHER_BD_DISPLAY, createItem(Material.GOLD_NUGGET, 
                    "§e" + session.getPlayer2Name() + " BD: §6" + nf.format(session.getPlayer2Bd()),
                    "§7상대방이 줄 BD"));
            gui1.setItem(SLOT_OTHER_BC_DISPLAY, createItem(Material.LAPIS_LAZULI, 
                    "§e" + session.getPlayer2Name() + " BC: §b" + nf.format(session.getPlayer2Bc()),
                    "§7상대방이 줄 BottCoin"));
            
            updateStatus(gui1, session);
            updateConfirmButton(gui1, session.isPlayer1Confirmed());
            updateCompleteButton(gui1, session.isBothConfirmed());
        }
        
        // Player2의 GUI에 Player1 아이템 표시
        Inventory gui2 = session.getPlayer2Gui();
        if (gui2 != null) {
            List<ItemStack> otherItems = session.getPlayer1Items();
            for (int i = 0; i < OTHER_ITEM_SLOTS.length; i++) {
                if (i < otherItems.size() && otherItems.get(i) != null) {
                    ItemStack display = otherItems.get(i).clone();
                    gui2.setItem(OTHER_ITEM_SLOTS[i], display);
                } else {
                    gui2.setItem(OTHER_ITEM_SLOTS[i], createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7빈 슬롯"));
                }
            }
            
            // 상대방 화폐 업데이트 (Player1 → Player2에게 표시)
            gui2.setItem(SLOT_OTHER_BD_DISPLAY, createItem(Material.GOLD_NUGGET, 
                    "§e" + session.getPlayer1Name() + " BD: §6" + nf.format(session.getPlayer1Bd()),
                    "§7상대방이 줄 BD"));
            gui2.setItem(SLOT_OTHER_BC_DISPLAY, createItem(Material.LAPIS_LAZULI, 
                    "§e" + session.getPlayer1Name() + " BC: §b" + nf.format(session.getPlayer1Bc()),
                    "§7상대방이 줄 BottCoin"));
            
            updateStatus(gui2, session);
            updateConfirmButton(gui2, session.isPlayer2Confirmed());
            updateCompleteButton(gui2, session.isBothConfirmed());
        }
    }
    
    /**
     * 화폐 동기화
     */
    public void syncCurrency(Player player, TradeSession session) {
        Inventory gui = session.isPlayer1(player.getUniqueId()) ? session.getPlayer1Gui() : session.getPlayer2Gui();
        if (gui != null) {
            updateCurrencyDisplay(gui, player, session);
        }
        
        // 상대방 GUI도 업데이트
        syncOtherItems(session);
    }
    
    // ================================================================================
    // 이벤트 핸들러
    // ================================================================================
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();
        
        TradeSession session = guiToSession.get(topInv);
        if (session == null) {
            return;
        }
        
        int slot = event.getRawSlot();
        boolean isShift = event.isShiftClick();
        
        // 상단 인벤토리 (거래 GUI) 클릭
        if (clickedInv == topInv) {
            // 내 아이템 슬롯인지 확인
            boolean isMyItemSlot = isInArray(slot, MY_ITEM_SLOTS);
            
            if (!isMyItemSlot) {
                // 아이템 슬롯이 아니면 취소
                event.setCancelled(true);
            }
            
            // 버튼 처리
            handleButtonClick(player, session, slot, isShift);
            
            // 내 아이템 슬롯이면 허용 (변경 후 동기화)
            if (isMyItemSlot) {
                // 1틱 후 아이템 동기화
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    collectMyItems(player, session);
                    syncOtherItems(session);
                }, 1L);
            }
            
        } else if (clickedInv == player.getInventory()) {
            // 하단 인벤토리 (플레이어 인벤토리) 클릭
            if (isShift) {
                // Shift 클릭으로 아이템 이동 시
                event.setCancelled(true);
                
                // 직접 아이템 이동 처리
                ItemStack item = event.getCurrentItem();
                if (item != null && !item.getType().isAir()) {
                    // 빈 슬롯 찾기
                    for (int mySlot : MY_ITEM_SLOTS) {
                        ItemStack existing = topInv.getItem(mySlot);
                        if (existing == null || existing.getType().isAir()) {
                            topInv.setItem(mySlot, item.clone());
                            event.setCurrentItem(null);
                            
                            // 동기화
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                collectMyItems(player, session);
                                syncOtherItems(session);
                            }, 1L);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        Inventory topInv = event.getView().getTopInventory();
        TradeSession session = guiToSession.get(topInv);
        
        if (session == null) {
            return;
        }
        
        // 드래그된 슬롯 확인
        for (int slot : event.getRawSlots()) {
            if (slot < 54 && !isInArray(slot, MY_ITEM_SLOTS)) {
                event.setCancelled(true);
                return;
            }
        }
        
        // 1틱 후 동기화
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            collectMyItems(player, session);
            syncOtherItems(session);
        }, 1L);
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        
        Inventory inv = event.getInventory();
        TradeSession session = guiToSession.remove(inv);
        
        if (session == null) {
            return;
        }
        
        // 화폐 입력 대기 중이면 거래 취소하지 않음
        if (isPendingCurrencyInput(player.getUniqueId())) {
            return;
        }
        
        // 다른 GUI도 닫혀있는지 확인 (둘 다 닫히면 거래 취소)
        if (session.getState() == TradeSession.TradeState.ACTIVE) {
            // 1틱 후 체크 (거래 완료로 인한 닫힘인지 확인)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (session.getState() == TradeSession.TradeState.ACTIVE) {
                    tradeService.cancelTrade(player.getUniqueId(), "GUI를 닫았습니다");
                }
            }, 1L);
        }
    }
    
    /**
     * 버튼 클릭 처리
     */
    private void handleButtonClick(Player player, TradeSession session, int slot, boolean isShift) {
        UUID playerId = player.getUniqueId();
        boolean isPlayer1 = session.isPlayer1(playerId);
        
        switch (slot) {
            case SLOT_CANCEL -> {
                tradeService.cancelTrade(playerId, "거래 취소");
            }
            case SLOT_CONFIRM -> {
                tradeService.toggleConfirm(player);
                // GUI 업데이트
                Inventory gui = isPlayer1 ? session.getPlayer1Gui() : session.getPlayer2Gui();
                boolean confirmed = isPlayer1 ? session.isPlayer1Confirmed() : session.isPlayer2Confirmed();
                updateConfirmButton(gui, confirmed);
                updateStatus(gui, session);
                updateCompleteButton(gui, session.isBothConfirmed());
                // 상대방 GUI도 업데이트
                syncOtherItems(session);
            }
            case SLOT_COMPLETE -> {
                if (session.isBothConfirmed()) {
                    tradeService.completeTrade(player);
                } else {
                    player.sendMessage("§c[거래] 양쪽 모두 확정해야 거래를 완료할 수 있습니다.");
                }
            }
            case SLOT_BD_MINUS -> {
                long amount = isShift ? bdPresets[1] : bdPresets[0];
                adjustCurrency(player, session, -amount, 0);
            }
            case SLOT_BD_PLUS -> {
                long amount = isShift ? bdPresets[1] : bdPresets[0];
                adjustCurrency(player, session, amount, 0);
            }
            case SLOT_BD_DISPLAY -> {
                // 채팅 입력 모드
                pendingCurrencyInput.put(playerId, CurrencyInputType.BD);
                player.closeInventory();
                player.sendMessage("§e[거래] 줄 BD 금액을 채팅에 입력하세요. (취소: 'c')");
            }
            case SLOT_BC_MINUS -> {
                int amount = isShift ? bcPresets[1] : bcPresets[0];
                adjustCurrency(player, session, 0, -amount);
            }
            case SLOT_BC_PLUS -> {
                int amount = isShift ? bcPresets[1] : bcPresets[0];
                adjustCurrency(player, session, 0, amount);
            }
            case SLOT_BC_DISPLAY -> {
                pendingCurrencyInput.put(playerId, CurrencyInputType.BC);
                player.closeInventory();
                player.sendMessage("§e[거래] 줄 BottCoin 수량을 채팅에 입력하세요. (취소: 'c')");
            }
        }
    }
    
    /**
     * 화폐 조정
     */
    private void adjustCurrency(Player player, TradeSession session, long bdDelta, long bcDelta) {
        UUID playerId = player.getUniqueId();
        boolean isPlayer1 = session.isPlayer1(playerId);
        PlayerTycoonData data = playerDataManager.get(playerId);
        
        if (bdDelta != 0) {
            long currentBd = isPlayer1 ? session.getPlayer1Bd() : session.getPlayer2Bd();
            long newBd = Math.max(0, currentBd + bdDelta);
            newBd = Math.min(newBd, data.getMoney()); // 보유량 초과 방지
            
            if (isPlayer1) {
                session.setPlayer1Bd(newBd);
            } else {
                session.setPlayer2Bd(newBd);
            }
        }
        
        if (bcDelta != 0) {
            long currentBc = isPlayer1 ? session.getPlayer1Bc() : session.getPlayer2Bc();
            long newBc = Math.max(0, currentBc + bcDelta);
            newBc = Math.min(newBc, data.getBottCoin()); // 보유량 초과 방지
            
            if (isPlayer1) {
                session.setPlayer1Bc(newBc);
            } else {
                session.setPlayer2Bc(newBc);
            }
        }
        
        syncCurrency(player, session);
    }
    
    /**
     * 채팅으로 화폐 입력 처리
     */
    public boolean handleCurrencyInput(Player player, String message) {
        UUID playerId = player.getUniqueId();
        CurrencyInputType inputType = pendingCurrencyInput.remove(playerId);
        
        if (inputType == null) {
            return false;
        }
        
        if (message.equalsIgnoreCase("c") || message.equalsIgnoreCase("취소")) {
            player.sendMessage("§7[거래] 입력이 취소되었습니다.");
            // GUI 다시 열기
            reopenGui(player);
            return true;
        }
        
        TradeSession session = tradeService.getSession(playerId);
        if (session == null) {
            player.sendMessage("§c[거래] 진행 중인 거래가 없습니다.");
            return true;
        }
        
        try {
            long amount = Long.parseLong(message.replace(",", ""));
            if (amount < 0) {
                player.sendMessage("§c[거래] 0 이상의 값을 입력하세요.");
                reopenGui(player);
                return true;
            }
            
            PlayerTycoonData data = playerDataManager.get(playerId);
            boolean isPlayer1 = session.isPlayer1(playerId);
            
            if (inputType == CurrencyInputType.BD) {
                amount = Math.min(amount, data.getMoney());
                if (isPlayer1) {
                    session.setPlayer1Bd(amount);
                } else {
                    session.setPlayer2Bd(amount);
                }
            } else {
                long bcAmount = Math.min(amount, data.getBottCoin());
                if (isPlayer1) {
                    session.setPlayer1Bc(bcAmount);
                } else {
                    session.setPlayer2Bc(bcAmount);
                }
            }
            
            player.sendMessage("§a[거래] 설정되었습니다.");
            reopenGui(player);
            
        } catch (NumberFormatException e) {
            player.sendMessage("§c[거래] 올바른 숫자를 입력하세요.");
            reopenGui(player);
        }
        
        return true;
    }
    
    /**
     * GUI 다시 열기
     */
    private void reopenGui(Player player) {
        TradeSession session = tradeService.getSession(player.getUniqueId());
        if (session != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openTradeGui(player, session);
                syncOtherItems(session);
            }, 1L);
        }
    }
    
    /**
     * 내 아이템 수집
     */
    private void collectMyItems(Player player, TradeSession session) {
        UUID playerId = player.getUniqueId();
        boolean isPlayer1 = session.isPlayer1(playerId);
        Inventory gui = isPlayer1 ? session.getPlayer1Gui() : session.getPlayer2Gui();
        
        if (gui == null) return;
        
        List<ItemStack> items = isPlayer1 ? session.getPlayer1Items() : session.getPlayer2Items();
        items.clear();
        
        for (int slot : MY_ITEM_SLOTS) {
            ItemStack item = gui.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        
        // 아이템 변경 시 확정 해제
        if (isPlayer1) {
            session.onPlayer1ItemsChanged();
        } else {
            session.onPlayer2ItemsChanged();
        }
    }
    
    // ================================================================================
    // 유틸리티
    // ================================================================================
    
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private boolean isInArray(int value, int[] array) {
        for (int i : array) {
            if (i == value) return true;
        }
        return false;
    }
    
    /**
     * 채팅 입력 대기 중인지 확인
     */
    public boolean isPendingCurrencyInput(UUID playerId) {
        return pendingCurrencyInput.containsKey(playerId);
    }
    
    private enum CurrencyInputType {
        BD, BC
    }
}
