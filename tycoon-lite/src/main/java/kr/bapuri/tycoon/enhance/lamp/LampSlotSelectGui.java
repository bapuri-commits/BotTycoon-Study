package kr.bapuri.tycoon.enhance.lamp;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * LampSlotSelectGui - 램프 슬롯 선택 GUI
 * 
 * v4.0: 채팅 기반에서 GUI 클릭 기반으로 변경
 * - 인벤토리를 닫지 않고 슬롯 선택 가능
 * - 직관적인 UX
 */
public class LampSlotSelectGui implements Listener {
    
    private static final String GUI_TITLE = "§6§l램프 슬롯 선택";
    private static final int GUI_SIZE = 9; // 1줄
    
    // 슬롯 위치 (GUI 내)
    private static final int[] SLOT_POSITIONS = {1, 2, 3, 4}; // 최대 4슬롯
    private static final int CANCEL_SLOT = 7;
    
    private final LampService lampService;
    private final Logger logger;
    
    // 열린 GUI 세션 관리
    private final Map<UUID, SlotSelectSession> activeSessions = new ConcurrentHashMap<>();
    
    public LampSlotSelectGui(LampService lampService, Logger logger) {
        this.lampService = lampService;
        this.logger = logger;
    }
    
    /**
     * 슬롯 선택 GUI 세션 데이터
     */
    public static class SlotSelectSession {
        public final int inventorySlot;          // 대상 아이템 인벤토리 슬롯
        public final ItemStack targetItemCopy;   // 검증용 복사본
        public final ItemStack lampItemCopy;     // 검증용 복사본
        public final LampType lampType;
        public final int lampSlotCount;
        public final List<LampSlotData> existingSlots;
        public final long timestamp;
        
        public SlotSelectSession(int inventorySlot, ItemStack targetItem, ItemStack lampItem,
                                  LampType lampType, int lampSlotCount, List<LampSlotData> existingSlots) {
            this.inventorySlot = inventorySlot;
            this.targetItemCopy = targetItem.clone();
            this.lampItemCopy = lampItem.clone();
            this.lampType = lampType;
            this.lampSlotCount = lampSlotCount;
            this.existingSlots = existingSlots;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 슬롯 선택 GUI 열기
     */
    public void openSlotSelectGui(Player player, int inventorySlot, ItemStack targetItem, ItemStack lampItem) {
        // 기존 세션 제거
        activeSessions.remove(player.getUniqueId());
        
        LampType lampType = LampItemFactory.getLampType(lampItem);
        if (lampType == null) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c유효하지 않은 램프입니다.");
            return;
        }
        
        // 레거시 마이그레이션
        EnhanceItemUtil.migrateLegacyLampEffect(targetItem);
        
        int slotCount = EnhanceItemUtil.getLampSlotCount(targetItem);
        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(targetItem);
        
        // 세션 생성
        SlotSelectSession session = new SlotSelectSession(
            inventorySlot, targetItem, lampItem, lampType, slotCount, slots);
        activeSessions.put(player.getUniqueId(), session);
        
        // GUI 생성
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);
        
        // 배경 채우기
        ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, background);
        }
        
        // 슬롯 아이템 배치
        for (int i = 0; i < slotCount && i < SLOT_POSITIONS.length; i++) {
            ItemStack slotItem = createSlotItem(i, slots);
            gui.setItem(SLOT_POSITIONS[i], slotItem);
        }
        
        // 취소 버튼
        ItemStack cancelItem = createGuiItem(Material.BARRIER, "§c취소", 
            "§7클릭하면 슬롯 선택을 취소합니다.");
        gui.setItem(CANCEL_SLOT, cancelItem);
        
        // GUI 열기
        player.openInventory(gui);
        
        logger.info("[LampSlotSelectGui] " + player.getName() + " 슬롯 선택 GUI 열림");
    }
    
    /**
     * 슬롯 아이템 생성
     */
    private ItemStack createSlotItem(int slotIndex, List<LampSlotData> existingSlots) {
        LampSlotData slotData = (slotIndex < existingSlots.size()) ? existingSlots.get(slotIndex) : null;
        
        Material material;
        String name;
        List<String> lore = new ArrayList<>();
        
        if (slotData == null || slotData.isEmpty()) {
            material = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            name = "§7슬롯 [" + (slotIndex + 1) + "] §8(빈 슬롯)";
            lore.add("§7이 슬롯은 비어있습니다.");
        } else {
            material = Material.ORANGE_STAINED_GLASS_PANE;
            name = "§e슬롯 [" + (slotIndex + 1) + "]";
            lore.add("§7현재 효과:");
            lore.add("§f" + slotData.getCompactDisplay());
            lore.add("");
            lore.add("§c클릭하면 이 효과가 덮어씌워집니다!");
        }
        
        lore.add("");
        lore.add("§e클릭하여 이 슬롯에 램프 적용");
        
        return createGuiItem(material, name, lore.toArray(new String[0]));
    }
    
    /**
     * GUI 아이템 생성 헬퍼
     */
    private ItemStack createGuiItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * GUI 클릭 이벤트
     * 
     * [v4.0.2 FIX] 모든 클릭을 확실히 취소하여 중복 처리 방지
     */
    @EventHandler(priority = EventPriority.LOWEST) // 가장 먼저 처리
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        // [v4.0.2 FIX] 모든 클릭 취소 (하단 인벤토리 포함)
        event.setCancelled(true);
        
        SlotSelectSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        
        int clickedSlot = event.getRawSlot();
        
        // [v4.0.2 FIX] 하단 인벤토리(플레이어 인벤토리) 클릭은 무시
        // GUI 크기(9) 이상이면 하단 인벤토리 영역
        if (clickedSlot >= GUI_SIZE) {
            return; // 이벤트는 이미 취소됨, 추가 처리 없음
        }
        
        // 취소 버튼
        if (clickedSlot == CANCEL_SLOT) {
            activeSessions.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§7램프 슬롯 선택이 취소되었습니다.");
            return;
        }
        
        // 슬롯 클릭 확인
        int selectedSlotIndex = -1;
        for (int i = 0; i < session.lampSlotCount && i < SLOT_POSITIONS.length; i++) {
            if (clickedSlot == SLOT_POSITIONS[i]) {
                selectedSlotIndex = i;
                break;
            }
        }
        
        if (selectedSlotIndex < 0) {
            return; // 유효하지 않은 클릭
        }
        
        // [v4.0.4 FIX] 커서 확인 불필요 - 램프는 이미 인벤토리에 있음
        // LampApplyListener에서 GUI 열기 전에 커서를 비우고 램프를 인벤토리에 저장함
        
        // 세션 제거
        activeSessions.remove(player.getUniqueId());
        
        // GUI 닫기
        player.closeInventory();
        
        // 램프 적용 수행 (세션의 lampItemCopy로 인벤토리에서 램프 검색)
        applyLampToSlot(player, session, selectedSlotIndex);
    }
    
    /**
     * 선택된 슬롯에 램프 적용
     * 
     * [v4.0.4 FIX] 커서 파라미터 제거 - 램프는 항상 인벤토리에서 검색
     */
    private void applyLampToSlot(Player player, SlotSelectSession session, int targetSlot) {
        // 인벤토리에서 아이템 검증
        ItemStack currentTarget = player.getInventory().getItem(session.inventorySlot);
        
        // [v4.0.4 FIX] 세션에 저장된 램프 정보로 인벤토리에서 검색
        ItemStack lampToUse = session.lampItemCopy;
        
        // 대상 아이템 검증
        if (currentTarget == null || currentTarget.getType().isAir()) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c대상 아이템이 사라졌습니다.");
            return;
        }
        
        // 아이템이 변경되었는지 확인
        if (!currentTarget.isSimilar(session.targetItemCopy)) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c대상 아이템이 변경되었습니다.");
            return;
        }
        
        // [v4.0.1 FIX] 램프 검증 - GUI 닫기 전에 저장한 커서 아이템 사용
        if (lampToUse == null || lampToUse.getType().isAir() || 
            !LampItemFactory.isLampItem(lampToUse)) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c램프가 사라졌습니다.");
            return;
        }
        
        // 램프 타입 확인
        LampType currentLampType = LampItemFactory.getLampType(lampToUse);
        if (currentLampType != session.lampType) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c램프 종류가 변경되었습니다.");
            return;
        }
        
        // [v4.0.1 FIX] 인벤토리에서 실제 램프 위치 찾기 (closeInventory 시 커서→인벤토리 이동됨)
        int lampSlotInInventory = findLampInInventory(player, lampToUse);
        if (lampSlotInInventory < 0) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c램프를 인벤토리에서 찾을 수 없습니다.");
            return;
        }
        ItemStack actualLampItem = player.getInventory().getItem(lampSlotInInventory);
        
        // [v4.0.1 FIX] 추가 null 체크 (극히 드문 경쟁 조건 대비)
        if (actualLampItem == null || actualLampItem.getType().isAir()) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c램프가 사라졌습니다.");
            return;
        }
        
        // 덮어쓰기할 기존 효과 저장
        List<LampSlotData> existingSlots = EnhanceItemUtil.getLampSlots(currentTarget);
        String overwrittenEffect = null;
        if (targetSlot < existingSlots.size() && !existingSlots.get(targetSlot).isEmpty()) {
            overwrittenEffect = existingSlots.get(targetSlot).getCompactDisplay();
        }
        
        // 램프 적용 (LampService 통해)
        LampService.ApplyResult result = lampService.applyLampToSpecificSlot(
            player, currentTarget, actualLampItem, targetSlot, overwrittenEffect);
        
        if (result.isSuccess()) {
            // 인벤토리 업데이트 - 대상 아이템
            player.getInventory().setItem(session.inventorySlot, currentTarget);
            
            // [v4.0.1 FIX] 램프 소모 - 인벤토리에서 직접 처리
            if (actualLampItem.getAmount() <= 1) {
                player.getInventory().setItem(lampSlotInInventory, null);
            } else {
                actualLampItem.setAmount(actualLampItem.getAmount() - 1);
                player.getInventory().setItem(lampSlotInInventory, actualLampItem);
            }
            
            player.updateInventory();
            logger.info("[LampSlotSelectGui] " + player.getName() + " 램프 적용 완료, 슬롯=" + targetSlot);
        } else {
            player.sendMessage(result.getMessage());
        }
    }
    
    /**
     * [v4.0.1] 인벤토리에서 특정 램프 아이템 찾기
     * GUI 닫기 시 커서 아이템이 인벤토리로 이동하므로 위치를 다시 찾아야 함
     * 
     * @return 램프 슬롯 인덱스, 없으면 -1
     */
    private int findLampInInventory(Player player, ItemStack lampToFind) {
        // 먼저 isSimilar로 정확히 일치하는 아이템 찾기
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.isSimilar(lampToFind)) {
                return i;
            }
        }
        
        // 같은 타입의 램프라도 찾기 (isSimilar 실패 시 폴백)
        LampType targetType = LampItemFactory.getLampType(lampToFind);
        if (targetType != null) {
            for (int i = 0; i < 36; i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && LampItemFactory.isLampItem(item)) {
                    LampType itemType = LampItemFactory.getLampType(item);
                    if (itemType == targetType) {
                        return i;
                    }
                }
            }
        }
        
        return -1;
    }
    
    /**
     * [v4.0.2 FIX] 드래그 이벤트 처리 - GUI에서 드래그 방지
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        // GUI 영역(상단)에 드래그하려는 경우 취소
        for (int slot : event.getRawSlots()) {
            if (slot < GUI_SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }
    
    /**
     * GUI 닫기 이벤트
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        SlotSelectSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            logger.info("[LampSlotSelectGui] " + player.getName() + " 슬롯 선택 GUI 닫힘");
        }
    }
    
    /**
     * 플레이어 로그아웃 - 세션 정리
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * 세션이 활성화되어 있는지 확인
     */
    public boolean hasActiveSession(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }
}
