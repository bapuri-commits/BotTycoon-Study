package kr.bapuri.tycoon.enhance.lamp;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * LampConfirmGui - 램프 적용 확인 GUI
 * 
 * [LEGACY v3.0] 이 클래스는 더 이상 사용되지 않습니다.
 * - 슬롯 참조 버그로 인해 드래그&드롭 즉시 적용 방식으로 변경됨
 * - LampApplyListener에서 직접 LampService.applyLamp() 호출
 * - 혹시 모를 복원을 위해 코드 유지
 * 
 * Phase 6 LITE: 레거시 버전 이식 (호환성 유지)
 * 
 * @deprecated v3.0 - 드래그&드롭 즉시 적용 방식으로 대체됨
 */
@Deprecated
public class LampConfirmGui {

    private static final String GUI_TITLE = "§d§l램프 적용 확인";
    private static final int GUI_SIZE = 27;

    // 대기 중인 램프 적용 요청
    private final Map<UUID, PendingLampApply> pendingApplies = new HashMap<>();

    private final LampService lampService;

    public LampConfirmGui(LampService lampService) {
        this.lampService = lampService;
    }

    /**
     * 확인 GUI 열기
     * 
     * v2.1 FIX: 슬롯 인덱스를 저장하여 GUI 열기 후에도 아이템을 정확히 찾을 수 있게 함
     * 
     * @param player 플레이어
     * @param targetItem 대상 아이템 (표시용)
     * @param lampItem 램프 아이템 (표시용)
     * @param targetSlot 대상 아이템 슬롯 인덱스
     * @param lampSlot 램프 아이템 슬롯 인덱스 (-1 = 커서에 있었음, GUI 열면서 인벤토리로 이동됨)
     */
    public void openConfirmGui(Player player, ItemStack targetItem, ItemStack lampItem, int targetSlot, int lampSlot) {
        // 램프 타입 확인
        LampType lampType = LampItemFactory.getLampType(lampItem);
        if (lampType == null) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c유효하지 않은 램프입니다.");
            return;
        }

        // 적용 가능 여부 확인
        if (!lampType.canApplyTo(targetItem)) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c이 램프는 해당 아이템에 적용할 수 없습니다.");
            player.sendMessage("§7적용 가능: " + lampType.getApplicableDescription());
            return;
        }

        // GUI 생성
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // 배경 채우기
        ItemStack background = createBackground();
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, background);
        }

        // 대상 아이템 표시 (슬롯 11)
        gui.setItem(11, targetItem.clone());

        // 램프 아이템 표시 (슬롯 13)
        gui.setItem(13, lampItem.clone());

        // 정보 표시 (슬롯 15)
        gui.setItem(15, createInfoItem(targetItem, lampType));

        // 확인 버튼 (슬롯 21)
        gui.setItem(21, createConfirmButton());

        // 취소 버튼 (슬롯 23)
        gui.setItem(23, createCancelButton());

        // 대기 중인 적용 정보 저장 (슬롯 인덱스 + 표시용 복사본)
        pendingApplies.put(player.getUniqueId(), new PendingLampApply(
            targetSlot, lampSlot, targetItem.clone(), lampItem.clone()
        ));

        player.openInventory(gui);
    }

    /**
     * GUI 클릭 처리
     * 
     * v2.1 FIX: 슬롯 인덱스에서 아이템을 다시 가져와서 검증
     * 
     * @return true if handled
     */
    public boolean handleClick(Player player, int slot, String inventoryTitle) {
        if (!GUI_TITLE.equals(inventoryTitle)) {
            return false;
        }

        PendingLampApply pending = pendingApplies.get(player.getUniqueId());
        if (pending == null) {
            player.closeInventory();
            return true;
        }

        if (slot == 21) {
            // 확인 버튼
            
            // [v2.1 FIX] 슬롯에서 아이템 다시 가져오기
            ItemStack targetItem = player.getInventory().getItem(pending.targetSlot);
            ItemStack lampItem = findLampItem(player, pending);
            
            // 아이템 유효성 검증
            if (targetItem == null || targetItem.getType().isAir()) {
                player.closeInventory();
                player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c대상 아이템을 찾을 수 없습니다. 인벤토리를 확인해주세요.");
                pendingApplies.remove(player.getUniqueId());
                return true;
            }
            
            // 아이템이 변경되었는지 확인 (isSimilar로 내용 비교)
            if (!targetItem.isSimilar(pending.targetClone)) {
                player.closeInventory();
                player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c대상 아이템이 변경되었습니다. 다시 시도해주세요.");
                pendingApplies.remove(player.getUniqueId());
                return true;
            }
            
            if (lampItem == null || lampItem.getType().isAir()) {
                player.closeInventory();
                player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c램프 아이템을 찾을 수 없습니다. 인벤토리를 확인해주세요.");
                pendingApplies.remove(player.getUniqueId());
                return true;
            }
            
            if (!LampItemFactory.isLampItem(lampItem)) {
                player.closeInventory();
                player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c램프 아이템이 변경되었습니다. 다시 시도해주세요.");
                pendingApplies.remove(player.getUniqueId());
                return true;
            }
            
            player.closeInventory();
            
            // 램프 적용
            LampService.ApplyResult result = lampService.applyLamp(
                player, 
                targetItem, 
                lampItem
            );

            if (!result.isSuccess()) {
                player.sendMessage(result.getMessage());
            }

            pendingApplies.remove(player.getUniqueId());
            return true;
        }

        if (slot == 23) {
            // 취소 버튼
            player.closeInventory();
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§7램프 적용이 취소되었습니다.");
            pendingApplies.remove(player.getUniqueId());
            return true;
        }

        return true; // 다른 슬롯 클릭도 무시
    }
    
    /**
     * 램프 아이템 찾기
     * 
     * GUI가 열리면서 커서 아이템이 인벤토리로 자동 이동되므로,
     * lampSlot이 -1이면 인벤토리에서 같은 램프를 찾아야 함
     */
    private ItemStack findLampItem(Player player, PendingLampApply pending) {
        if (pending.lampSlot >= 0) {
            // 슬롯 인덱스가 있으면 해당 슬롯에서 가져옴
            return player.getInventory().getItem(pending.lampSlot);
        }
        
        // 커서에 있었던 경우: 인벤토리에서 같은 램프 찾기
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(pending.lampClone)) {
                return item;
            }
        }
        
        return null;
    }

    /**
     * GUI 닫힘 처리
     */
    public void handleClose(Player player) {
        pendingApplies.remove(player.getUniqueId());
    }

    /**
     * 확인 GUI인지 확인
     */
    public boolean isConfirmGui(String title) {
        return GUI_TITLE.equals(title);
    }

    /**
     * 대기 중인 적용 정보 가져오기
     */
    public PendingLampApply getPending(UUID playerId) {
        return pendingApplies.get(playerId);
    }

    // ========== 아이템 생성 ==========

    private ItemStack createBackground() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(ItemStack targetItem, LampType lampType) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l램프 적용 정보");

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7램프 타입: " + lampType.getDisplayName());
            lore.add("");
            lore.add("§c⚠ 경고:");
            lore.add("§7- 랜덤한 효과가 부여됩니다.");
            lore.add("§7- 기존 램프 효과는 덮어씌워집니다.");
            lore.add("§7- 이 작업은 되돌릴 수 없습니다.");

            // 기존 효과가 있으면 표시
            String existingEffect = EnhanceItemUtil.getLampEffect(targetItem);
            if (existingEffect != null) {
                LampEffect effect = LampEffect.fromId(existingEffect);
                if (effect != null) {
                    lore.add("");
                    lore.add("§c현재 효과: " + effect.getDisplayName());
                    lore.add("§c이 효과는 사라집니다!");
                }
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createConfirmButton() {
        ItemStack item = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§l확인");
            meta.setLore(Arrays.asList(
                "",
                "§7클릭하여 램프를 적용합니다.",
                "§c(되돌릴 수 없습니다)"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l취소");
            meta.setLore(Arrays.asList(
                "",
                "§7클릭하여 취소합니다."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ========== 내부 클래스 ==========

    /**
     * 대기 중인 램프 적용 정보
     * 
     * v2.1 FIX: 아이템 참조 대신 슬롯 인덱스 저장
     * - GUI 열 때 커서 아이템이 인벤토리로 돌아가면서 참조가 변경되는 문제 해결
     */
    public static class PendingLampApply {
        public final int targetSlot;      // 대상 아이템 슬롯 인덱스
        public final int lampSlot;        // 램프 아이템 슬롯 인덱스 (-1 = 원래 커서에 있었음)
        public final ItemStack targetClone;  // 표시용 복사본
        public final ItemStack lampClone;    // 표시용 복사본

        public PendingLampApply(int targetSlot, int lampSlot, ItemStack targetClone, ItemStack lampClone) {
            this.targetSlot = targetSlot;
            this.lampSlot = lampSlot;
            this.targetClone = targetClone;
            this.lampClone = lampClone;
        }
    }
}
