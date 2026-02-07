package kr.bapuri.tycoon.enhance.lamp;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * LampApplyListener - 램프 적용 이벤트 리스너
 * 
 * [v3.4] 드래그&드롭 즉시 적용 방식
 * - 확인 GUI 제거
 * - 인벤토리에서 램프를 아이템에 드래그하면 바로 적용
 * 
 * [v4.0] GUI 기반 슬롯 선택 시스템
 * - 모든 슬롯이 차있을 때 GUI로 덮어쓸 슬롯 선택
 * - 채팅 기반에서 GUI 클릭 기반으로 변경 (인벤토리 닫힘 문제 해결)
 * 
 * [Phase 8 버그수정] 복사 버그 완전 해결
 * - event.setCancelled(true) 후 setCurrentItem/setCursor가 무시되는 문제 해결
 * - 인벤토리 직접 수정 + player.setItemOnCursor() + updateInventory() 사용
 * 
 * Phase 6 LITE: 레거시 버전 이식
 */
public class LampApplyListener implements Listener {

    private final LampService lampService;
    private final LampSlotSelectGui slotSelectGui;

    public LampApplyListener(LampService lampService, LampSlotSelectGui slotSelectGui) {
        this.lampService = lampService;
        this.slotSelectGui = slotSelectGui;
    }

    /**
     * 인벤토리 클릭 이벤트 처리
     * 
     * [v3.6] 슬롯이 모두 차있으면 슬롯 선택 모드로 진입
     * [Phase 8 버그수정] 명시적 인벤토리 조작으로 복사 버그 해결
     * [v4.0.2 FIX] GUI가 열려있을 때 하단 인벤토리 클릭 무시
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Shift 클릭은 무시
        if (event.isShiftClick()) return;

        // [v4.0.2 FIX] 슬롯 선택 GUI가 열려있으면 무시 (중복 적용 방지)
        if (slotSelectGui.hasActiveSession(player)) {
            return;
        }
        
        // [v4.0.3 FIX] 커스텀 GUI가 열려있으면 무시
        // CRAFTING = 플레이어 인벤토리 (E키), PLAYER = 생존 인벤토리
        // 이 두 경우에만 램프 적용 허용
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType != InventoryType.CRAFTING && topType != InventoryType.PLAYER) {
            return;
        }

        // 플레이어 인벤토리에서만 램프 적용 허용
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || clickedInventory != player.getInventory()) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        int clickedSlot = event.getSlot();

        // 커서에 램프가 있고, 클릭한 슬롯에 아이템이 있는 경우
        if (cursor != null && !cursor.getType().isAir() &&
            clicked != null && !clicked.getType().isAir() &&
            LampItemFactory.isLampItem(cursor) && 
            !LampItemFactory.isLampItem(clicked)) {
            
            LampType lampType = LampItemFactory.getLampType(cursor);
            if (lampType == null) {
                player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c유효하지 않은 램프입니다.");
                return;
            }
            
            // 적용 가능 여부 확인
            if (!lampType.canApplyTo(clicked)) {
                player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c이 램프는 해당 아이템에 적용할 수 없습니다.");
                player.sendMessage("§7적용 가능: " + lampType.getApplicableDescription());
                return;
            }
            
            // 이벤트 취소 (기본 아이템 교환 방지)
            event.setCancelled(true);
            
            // v3.6: 빈 슬롯이 있는지 확인
            EnhanceItemUtil.migrateLegacyLampEffect(clicked);
            int emptySlot = EnhanceItemUtil.findEmptySlot(clicked);
            
            if (emptySlot < 0) {
                // v4.0: 빈 슬롯 없음 → GUI 기반 슬롯 선택
                
                // [v4.0.4 FIX] GUI 열기 전 아이템 상태 명시적 고정 (복사 버그 방지)
                // 1. 램프를 인벤토리 빈 슬롯에 임시 저장
                ItemStack lampCopy = cursor.clone();
                int tempSlot = player.getInventory().firstEmpty();
                if (tempSlot >= 0) {
                    player.getInventory().setItem(tempSlot, lampCopy);
                } else {
                    // 빈 슬롯 없으면 기존 위치 유지
                    tempSlot = -1;
                }
                
                // 2. 커서 비우기 (Paper 서버 아이템 이동 방지)
                player.setItemOnCursor(null);
                
                // 3. 대상 아이템 슬롯 명시적 재설정
                player.getInventory().setItem(clickedSlot, clicked);
                
                // 4. 인벤토리 동기화
                player.updateInventory();
                
                slotSelectGui.openSlotSelectGui(player, clickedSlot, clicked, lampCopy);
                return;
            }
            
            // [Phase 8 버그수정] 램프 적용 전 수량 저장 (복사 방지)
            int cursorAmountBefore = cursor.getAmount();
            
            // LampService.applyLamp가 cursor와 clicked를 직접 수정함
            // lampItem.setAmount(lampItem.getAmount() - 1) 호출됨
            LampService.ApplyResult result = lampService.applyLamp(player, clicked, cursor);
            
            if (result.isSuccess()) {
                // [Phase 8 버그수정] 명시적 인벤토리 조작
                // 1. 클릭한 슬롯에 수정된 아이템 설정
                player.getInventory().setItem(clickedSlot, clicked);
                
                // 2. 커서 설정: 램프가 모두 소모되었으면 null
                if (cursor.getAmount() <= 0 || cursorAmountBefore == 1) {
                    player.setItemOnCursor(null);
                } else {
                    // 수량이 감소된 cursor 설정
                    player.setItemOnCursor(cursor);
                }
                
                // 3. 강제 인벤토리 동기화 (클라이언트-서버 불일치 방지)
                player.updateInventory();
            } else {
                player.sendMessage(result.getMessage());
            }
        }
    }
    
    // v4.0: 채팅 기반 슬롯 선택 제거됨
    // 슬롯 선택은 LampSlotSelectGui에서 GUI 클릭으로 처리
}
