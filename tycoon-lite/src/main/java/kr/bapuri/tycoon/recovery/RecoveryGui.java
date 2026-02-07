package kr.bapuri.tycoon.recovery;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RecoveryGui - Town Recovery Storage GUI
 * 
 * [Phase 8] 레거시에서 이식 및 리팩토링
 * 
 * 플레이어가 Town NPC를 클릭하면 열리는 GUI.
 * 저장된 엔트리 목록을 보여주고 회수할 수 있게 함.
 */
public class RecoveryGui implements Listener {

    private final Plugin plugin;
    private final RecoveryStorageManager storageManager;
    
    // 열린 GUI 추적 (인벤토리 -> 플레이어 UUID)
    // [Phase 8] ConcurrentHashMap으로 변경 - 멀티스레드 안전성
    private final Map<Inventory, UUID> openGuis = new ConcurrentHashMap<>();
    
    // 엔트리 매핑 (인벤토리 -> 슬롯 -> 엔트리ID)
    // [Phase 8] ConcurrentHashMap으로 변경 - 멀티스레드 안전성
    private final Map<Inventory, Map<Integer, String>> entryMapping = new ConcurrentHashMap<>();

    public RecoveryGui(Plugin plugin, RecoveryStorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    /**
     * Recovery Storage GUI 열기 (자신의 보관소)
     */
    public void open(Player player) {
        openFor(player, player.getUniqueId());
    }
    
    /**
     * 특정 플레이어의 Recovery Storage GUI 열기 (관리자용)
     * 
     * @param viewer GUI를 보는 플레이어
     * @param ownerUUID 보관소 주인의 UUID
     */
    public void openFor(Player viewer, UUID ownerUUID) {
        List<RecoveryEntry> entries = storageManager.getEntries(ownerUUID);
        
        if (entries.isEmpty()) {
            viewer.sendMessage(ChatColor.GRAY + "[Town 보관소] 보관된 아이템이 없습니다.");
            return;
        }
        
        // GUI 크기 계산 (9의 배수, 최대 54)
        int size = Math.min(54, ((entries.size() - 1) / 9 + 1) * 9);
        size = Math.max(9, size);
        
        // 다른 사람 보관소인 경우 제목에 표시
        String ownerName = "";
        if (!ownerUUID.equals(viewer.getUniqueId())) {
            org.bukkit.OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
            ownerName = owner.getName() != null ? owner.getName() + "의 " : "";
        }
        
        String title = ChatColor.DARK_GREEN + ownerName + "Town 보관소 (" + entries.size() + "개)";
        Inventory inv = Bukkit.createInventory(null, size, title);
        
        Map<Integer, String> slotToEntry = new HashMap<>();
        
        int slot = 0;
        for (RecoveryEntry entry : entries) {
            if (slot >= size) break;
            
            // 회수 비용 계산을 위해 실제 소유자 기준으로 표시
            ItemStack displayItem = createDisplayItem(viewer, entry);
            inv.setItem(slot, displayItem);
            slotToEntry.put(slot, entry.getEntryId());
            slot++;
        }
        
        // [Phase 8] 보관소 주인 UUID 저장 (회수 시 올바른 플레이어 처리)
        openGuis.put(inv, ownerUUID);
        entryMapping.put(inv, slotToEntry);
        
        viewer.openInventory(inv);
    }

    /**
     * 엔트리를 표시하는 아이템 생성
     */
    private ItemStack createDisplayItem(Player player, RecoveryEntry entry) {
        // 대표 아이템 선택 (첫 번째 아이템)
        Material displayMat = Material.CHEST;
        List<ItemStack> items = entry.getItems();
        if (!items.isEmpty() && items.get(0) != null) {
            displayMat = items.get(0).getType();
        }
        
        ItemStack item = new ItemStack(displayMat);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // 이름
            String reasonName = entry.getReason() != null ? entry.getReason().getDisplayName() : "알 수 없음";
            meta.setDisplayName(ChatColor.GOLD + reasonName);
            
            // 설명
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "아이템 수: " + ChatColor.WHITE + entry.getTotalItemCount() + "개");
            lore.add(ChatColor.GRAY + "저장 시간: " + ChatColor.WHITE + entry.getAgeString());
            lore.add(ChatColor.GRAY + "위치: " + ChatColor.WHITE + entry.getDeathLocationString());
            
            // 킬러 정보
            if (entry.isPvpDeath()) {
                lore.add(ChatColor.GRAY + "킬러: " + ChatColor.RED + entry.getKillerName());
            }
            
            lore.add("");
            
            // 비용 정보
            long cost = storageManager.calculateClaimCost(player, entry);
            if (cost > 0) {
                lore.add(ChatColor.YELLOW + "회수 비용: " + cost + "원");
            } else {
                lore.add(ChatColor.GREEN + "무료 회수");
            }
            
            lore.add("");
            lore.add(ChatColor.AQUA + "클릭하여 회수");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        
        if (!openGuis.containsKey(inv)) {
            return;
        }
        
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        // 클릭된 슬롯 확인
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inv.getSize()) {
            return;
        }
        
        Map<Integer, String> slotMap = entryMapping.get(inv);
        if (slotMap == null || !slotMap.containsKey(slot)) {
            return;
        }
        
        String entryId = slotMap.get(slot);
        
        // 회수 시도
        RecoveryStorageManager.ClaimResult result = storageManager.claim(player, entryId);
        
        if (result == RecoveryStorageManager.ClaimResult.SUCCESS) {
            // GUI 새로고침
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(player), 1L);
        } else {
            player.sendMessage(ChatColor.RED + result.getMessage());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        openGuis.remove(inv);
        entryMapping.remove(inv);
    }

    /**
     * 인벤토리 드래그 이벤트 - 아이템 복제 버그 방지
     * [FIX] Recovery GUI에 아이템 드래그로 삽입하는 것을 차단
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (!openGuis.containsKey(topInv)) return;

        // Recovery GUI 슬롯에 드래그하려는 경우 취소
        for (int slot : event.getRawSlots()) {
            if (slot < topInv.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
