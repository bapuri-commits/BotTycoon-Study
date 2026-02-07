package kr.bapuri.tycoon.enhance.upgrade;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * UpgradeListener - 강화 GUI 이벤트 리스너
 * 
 * Phase 6 LITE: 레거시 버전 이식
 * 
 * v2.0 - 쉬프트 클릭 수정
 */
public class UpgradeListener implements Listener {

    private final UpgradeGui upgradeGui;
    private final JavaPlugin plugin;

    public UpgradeListener(UpgradeGui upgradeGui, JavaPlugin plugin) {
        this.upgradeGui = upgradeGui;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        // [Phase 8 버그수정] Paper 서버에서 타이틀 색상 코드가 다르게 처리될 수 있음
        // contains 비교로 변경하여 더 유연하게 처리
        if (!upgradeGui.isUpgradeGui(title)) {
            // 폴백: "장비 강화" 키워드로도 확인
            if (!title.contains("장비 강화")) return;
        }

        Inventory guiInventory = event.getInventory();
        int rawSlot = event.getRawSlot();

        // 상단 인벤토리 (강화 GUI)
        if (rawSlot < guiInventory.getSize()) {
            // 수정 가능한 슬롯인지 확인
            if (!upgradeGui.isEditableSlot(rawSlot)) {
                // 버튼 등 다른 슬롯 클릭
                event.setCancelled(true);
                upgradeGui.handleClick(player, rawSlot, event.getCursor(), guiInventory);
            } else {
                // 아이템/보호 주문서 슬롯 클릭 - 허용
                // 다음 틱에 GUI 업데이트
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.2f);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        upgradeGui.updateGui(player, guiInventory);
                    }
                }.runTaskLater(plugin, 1L);
            }
        } else {
            // 하단 인벤토리 (플레이어 인벤토리)
            if (event.isShiftClick()) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType().isAir()) {
                    return;
                }
                
                // 쉬프트 클릭 시 아이템을 적절한 슬롯으로 이동
                event.setCancelled(true);
                
                // 강화 가능 아이템인지 확인
                if (EnhanceItemUtil.isUpgradeable(clickedItem)) {
                    // 아이템 슬롯이 비어있으면 이동
                    if (guiInventory.getItem(upgradeGui.getItemSlot()) == null) {
                        guiInventory.setItem(upgradeGui.getItemSlot(), clickedItem.clone());
                        event.setCurrentItem(null);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    }
                } else if (EnhanceItemUtil.isProtectionScroll(clickedItem)) {
                    // [Phase 8] 보호 주문서 2슬롯 분리 - 타입에 따라 적절한 슬롯으로 이동
                    String scrollType = EnhanceItemUtil.getProtectionScrollType(clickedItem);
                    int targetSlot = -1;
                    
                    if ("destroy".equals(scrollType)) {
                        // 파괴 방지 → 파괴 방지 슬롯
                        if (guiInventory.getItem(upgradeGui.getDestroyProtectionSlot()) == null) {
                            targetSlot = upgradeGui.getDestroyProtectionSlot();
                        }
                    } else if ("downgrade".equals(scrollType)) {
                        // 하락 방지 → 하락 방지 슬롯
                        if (guiInventory.getItem(upgradeGui.getDowngradeProtectionSlot()) == null) {
                            targetSlot = upgradeGui.getDowngradeProtectionSlot();
                        }
                    }
                    
                    if (targetSlot != -1) {
                        guiInventory.setItem(targetSlot, clickedItem.clone());
                        event.setCurrentItem(null);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    } else {
                        // 슬롯이 가득 찬 경우 알림
                        player.sendMessage("§c해당 보호 주문서 슬롯이 이미 사용 중입니다.");
                    }
                }
                
                // GUI 업데이트
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        upgradeGui.updateGui(player, guiInventory);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        // [Phase 8 버그수정] Paper 서버 타이틀 호환성
        if (!upgradeGui.isUpgradeGui(title) && !title.contains("장비 강화")) return;

        // 상단 인벤토리에 드래그 시 수정 가능한 슬롯만 허용
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize() && !upgradeGui.isEditableSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }

        // GUI 업데이트
        new BukkitRunnable() {
            @Override
            public void run() {
                upgradeGui.updateGui(player, event.getInventory());
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();
        // [Phase 8 버그수정] Paper 서버 타이틀 호환성
        if (!upgradeGui.isUpgradeGui(title) && !title.contains("장비 강화")) return;

        upgradeGui.handleClose(player, event.getInventory());
    }
}
