package kr.bapuri.tycoon.enhance.upgrade;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * UpgradeGui - 강화 GUI 인터페이스
 * 
 * Phase 6 LITE: 레거시 버전 이식
 * - CoreItemService 의존성 제거 (vanilla-plus에서 사용하던 CoreItem 보호권 기능)
 * - 기본 보호 주문서만 지원
 * 
 * v2.0 - GUI 개선
 * - 더 직관적인 레이아웃
 * - 시각적 피드백 강화
 * - 강화 등급별 색상 표시
 * 
 * v2.1 - 보호권 검증 개선
 * - 잘못된 슬롯 배치 시 경고 메시지
 * - 디버그 로깅 추가
 * - 보호 상태 GUI 표시
 */
public class UpgradeGui {
    
    private static final Logger LOGGER = Logger.getLogger("TycoonLite");

    public static final String GUI_TITLE = "§6§l⚔ 장비 강화 §6§l⚔";
    private static final int GUI_SIZE = 54; // 6줄로 확장

    // 슬롯 위치 (새 레이아웃)
    // [Phase 8] 보호 주문서 2슬롯 분리
    private static final int ITEM_SLOT = 20;                  // 강화할 아이템 (왼쪽)
    private static final int DESTROY_PROTECTION_SLOT = 23;    // 파괴 방지 주문서 슬롯 (중앙)
    private static final int DOWNGRADE_PROTECTION_SLOT = 24;  // 하락 방지 주문서 슬롯 (오른쪽)
    private static final int UPGRADE_BUTTON_SLOT = 40;        // 강화 버튼 (하단 중앙)
    private static final int INFO_SLOT = 22;                  // 정보 표시 (중앙-왼쪽)
    private static final int CLOSE_SLOT = 49;                 // 닫기 버튼
    private static final int RESULT_PREVIEW_SLOT = 31;        // 결과 미리보기
    
    // [Phase 8] 하위 호환성 (기존 코드 참조용) - deprecated
    @Deprecated
    private static final int PROTECTION_SLOT = DESTROY_PROTECTION_SLOT;

    private final UpgradeService upgradeService;
    private final EconomyService economyService;

    // 강화 중인 플레이어 정보
    // [v2.1 Audit] 스레드 안전성을 위해 ConcurrentHashMap 사용
    private final Map<UUID, UpgradeSession> sessions = new ConcurrentHashMap<>();

    public UpgradeGui(UpgradeService upgradeService, EconomyService economyService) {
        this.upgradeService = upgradeService;
        this.economyService = economyService;
    }

    /**
     * 강화 GUI 열기
     */
    public void openGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // 배경 패턴 채우기
        fillBackground(gui);

        // 아이템 슬롯 프레임
        createItemSlotFrame(gui, ITEM_SLOT, Material.CYAN_STAINED_GLASS_PANE);
        gui.setItem(ITEM_SLOT, null);

        // [Phase 8] 보호 주문서 2슬롯 분리
        // 파괴 방지 주문서 슬롯 (빨강)
        gui.setItem(DESTROY_PROTECTION_SLOT, null);
        gui.setItem(DESTROY_PROTECTION_SLOT - 9, createSlotLabel("§c§l파괴 방지", Material.RED_STAINED_GLASS_PANE));
        
        // 하락 방지 주문서 슬롯 (노랑)
        gui.setItem(DOWNGRADE_PROTECTION_SLOT, null);
        gui.setItem(DOWNGRADE_PROTECTION_SLOT - 9, createSlotLabel("§e§l하락 방지", Material.YELLOW_STAINED_GLASS_PANE));

        // 정보 패널
        gui.setItem(INFO_SLOT, createInfoItem(null));

        // 결과 미리보기
        gui.setItem(RESULT_PREVIEW_SLOT, createResultPreview(null));

        // 화살표 장식
        // [Phase 8] 23번은 파괴방지 슬롯으로 변경됨
        gui.setItem(21, createArrow("§7▶"));

        // 강화 버튼
        gui.setItem(UPGRADE_BUTTON_SLOT, createUpgradeButton(null, player));

        // 닫기 버튼
        gui.setItem(CLOSE_SLOT, createCloseButton());

        // 안내 표시
        gui.setItem(11, createGuideItem("§b§l아이템", Arrays.asList(
            "§7강화할 무기 또는 방어구를",
            "§7아래 슬롯에 놓으세요.",
            "",
            "§e✦ Shift + 클릭으로 빠른 등록"
        )));
        // [Phase 8] 보호 주문서 2슬롯 분리 안내
        gui.setItem(15, createGuideItem("§d§l보호 주문서", Arrays.asList(
            "§7(선택) 강화 실패 시",
            "§7아이템을 보호합니다.",
            "",
            "§c✦ 파괴 방지: §7아이템 파괴 방지",
            "§e✦ 하락 방지: §7레벨 하락 방지",
            "",
            "§8둘 다 장착 시 완전 보호!"
        )));

        // 타이틀 장식
        gui.setItem(4, createTitleDecor());

        // 세션 생성
        sessions.put(player.getUniqueId(), new UpgradeSession());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.2f);
    }
    
    /**
     * 배경 패턴 채우기
     */
    private void fillBackground(Inventory gui) {
        // 기본 어두운 배경
        ItemStack darkBg = createBackground(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, darkBg);
        }
        
        // 상단 테두리 (골드)
        ItemStack goldBorder = createBackground(Material.ORANGE_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, goldBorder);
        }
        
        // 하단 테두리 (골드)
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, goldBorder);
        }
        
        // 중앙 강조 영역
        ItemStack grayAccent = createBackground(Material.GRAY_STAINED_GLASS_PANE);
        int[] accentSlots = {19, 25, 28, 29, 30, 32, 33, 34, 37, 38, 39, 41, 42, 43};
        for (int slot : accentSlots) {
            gui.setItem(slot, grayAccent);
        }
    }
    
    /**
     * 아이템 슬롯 주변 프레임 생성 (3x3 - 9칸 인벤토리 기준)
     */
    private void createItemSlotFrame(Inventory gui, int centerSlot, Material frameMaterial) {
        ItemStack frame = createBackground(frameMaterial);
        // 9칸 인벤토리에서 3x3 프레임: 위아래 = ±9, 좌우 = ±1, 대각선 = ±8, ±10
        int[] offsets = {-10, -9, -8, -1, 1, 8, 9, 10};
        for (int offset : offsets) {
            int slot = centerSlot + offset;
            // 경계 체크 + 같은 줄 체크 (좌우 이동 시 줄 바뀜 방지)
            if (slot >= 0 && slot < GUI_SIZE) {
                // 좌우(±1) 이동 시 줄이 바뀌면 무시
                if ((offset == -1 || offset == 1) && (centerSlot / 9 != slot / 9)) {
                    continue;
                }
                gui.setItem(slot, frame);
            }
        }
    }

    /**
     * GUI 클릭 처리
     * 
     * @return true if handled
     */
    public boolean handleClick(Player player, int slot, ItemStack cursor, Inventory inventory) {
        UpgradeSession session = sessions.get(player.getUniqueId());
        if (session == null) return false;

        // 아이템 슬롯 클릭
        if (slot == ITEM_SLOT) {
            // 아이템 넣기/빼기 허용
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return false; // 기본 동작 허용
        }

        // [Phase 8] 보호 주문서 2슬롯 분리
        // 파괴 방지 주문서 슬롯 클릭
        if (slot == DESTROY_PROTECTION_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return false; // 기본 동작 허용
        }
        
        // 하락 방지 주문서 슬롯 클릭
        if (slot == DOWNGRADE_PROTECTION_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return false; // 기본 동작 허용
        }

        // 강화 버튼 클릭
        if (slot == UPGRADE_BUTTON_SLOT) {
            handleUpgrade(player, inventory);
            return true;
        }

        // 닫기 버튼
        if (slot == CLOSE_SLOT) {
            returnItems(player, inventory);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
            player.closeInventory();
            return true;
        }

        return true; // 다른 슬롯은 클릭 방지
    }

    /**
     * 슬롯 변경 시 GUI 업데이트
     * 
     * [v2.1] 보호권 상태 표시 추가
     */
    public void updateGui(Player player, Inventory inventory) {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        ItemStack destroyProtection = inventory.getItem(DESTROY_PROTECTION_SLOT);
        ItemStack downgradeProtection = inventory.getItem(DOWNGRADE_PROTECTION_SLOT);

        // 정보 패널 업데이트
        inventory.setItem(INFO_SLOT, createInfoItem(item));

        // 강화 버튼 업데이트
        inventory.setItem(UPGRADE_BUTTON_SLOT, createUpgradeButton(item, player));
        
        // 결과 미리보기 업데이트
        inventory.setItem(RESULT_PREVIEW_SLOT, createResultPreview(item));
        
        // [v2.1] 보호권 슬롯 라벨 업데이트 - 인식 상태 표시
        updateProtectionSlotLabels(inventory, destroyProtection, downgradeProtection);
    }
    
    /**
     * [v2.1] 보호권 슬롯 라벨 업데이트 - 현재 인식 상태 표시
     * [v2.1 Audit] 헬퍼 메서드 사용으로 리팩토링
     */
    private void updateProtectionSlotLabels(Inventory inventory, 
                                            ItemStack destroySlotItem, 
                                            ItemStack downgradeSlotItem) {
        // 파괴 방지 슬롯 라벨
        if (isValidDestroyProtection(destroySlotItem)) {
            inventory.setItem(DESTROY_PROTECTION_SLOT - 9, 
                createProtectionLabel("§a§l✓ 파괴 방지", Material.LIME_STAINED_GLASS_PANE, 
                    "§a보호 활성화됨", "§7파괴 시 아이템 보호"));
        } else if ("downgrade".equals(getWrongScrollTypeInSlot(destroySlotItem, "destroy"))) {
            inventory.setItem(DESTROY_PROTECTION_SLOT - 9, 
                createProtectionLabel("§c§l✗ 슬롯 오류", Material.RED_STAINED_GLASS_PANE, 
                    "§c하락 방지 주문서입니다!", "§7오른쪽 슬롯에 넣어주세요"));
        } else {
            inventory.setItem(DESTROY_PROTECTION_SLOT - 9, 
                createSlotLabel("§c§l파괴 방지", Material.RED_STAINED_GLASS_PANE));
        }
        
        // 하락 방지 슬롯 라벨
        if (isValidDowngradeProtection(downgradeSlotItem)) {
            inventory.setItem(DOWNGRADE_PROTECTION_SLOT - 9, 
                createProtectionLabel("§a§l✓ 하락 방지", Material.LIME_STAINED_GLASS_PANE, 
                    "§a보호 활성화됨", "§7하락 시 레벨 유지"));
        } else if ("destroy".equals(getWrongScrollTypeInSlot(downgradeSlotItem, "downgrade"))) {
            inventory.setItem(DOWNGRADE_PROTECTION_SLOT - 9, 
                createProtectionLabel("§e§l✗ 슬롯 오류", Material.ORANGE_STAINED_GLASS_PANE, 
                    "§c파괴 방지 주문서입니다!", "§7왼쪽 슬롯에 넣어주세요"));
        } else {
            inventory.setItem(DOWNGRADE_PROTECTION_SLOT - 9, 
                createSlotLabel("§e§l하락 방지", Material.YELLOW_STAINED_GLASS_PANE));
        }
    }
    
    /**
     * [v2.1] 보호 상태 라벨 생성
     */
    private ItemStack createProtectionLabel(String name, Material material, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add("§8↓ 해당 주문서를 아래에");
            lore.add("");
            for (String line : loreLines) {
                lore.add(line);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 강화 실행
     * 
     * [Phase 8] 보호 주문서 2슬롯 분리 지원
     * [v2.1] 잘못된 슬롯 배치 경고, 디버그 로깅 추가
     */
    private void handleUpgrade(Player player, Inventory inventory) {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        ItemStack destroyProtection = inventory.getItem(DESTROY_PROTECTION_SLOT);
        ItemStack downgradeProtection = inventory.getItem(DOWNGRADE_PROTECTION_SLOT);

        if (item == null || !EnhanceItemUtil.isUpgradeable(item)) {
            player.sendMessage(EnhanceConstants.PREFIX_UPGRADE + "§c강화할 아이템을 넣어주세요.");
            return;
        }

        int currentLevel = EnhanceItemUtil.getUpgradeLevel(item);
        long cost = upgradeService.getUpgradeCost(currentLevel);

        if (!economyService.hasBalance(player, cost)) {
            player.sendMessage(EnhanceConstants.PREFIX_UPGRADE + "§c돈이 부족합니다. (필요: " + cost + "원)");
            return;
        }

        // [v2.1] 슬롯 검증 - 잘못된 슬롯에 보호권 배치 감지 및 경고
        validateProtectionSlots(player, destroyProtection, downgradeProtection);

        // [Phase 8] 보호 주문서 2슬롯 분리 - 개별 확인
        // [v2.1 Audit] 헬퍼 메서드 사용
        boolean hasDestroyProtection = isValidDestroyProtection(destroyProtection);
        boolean hasDowngradeProtection = isValidDowngradeProtection(downgradeProtection);

        // [v2.1] 디버그 로깅 - 보호권 검증 과정
        LOGGER.info("[UpgradeGui] " + player.getName() + " 강화 시도: +" + currentLevel);
        if (destroyProtection != null) {
            String type = EnhanceItemUtil.getProtectionScrollType(destroyProtection);
            LOGGER.info("[UpgradeGui]   파괴방지 슬롯: " + (type != null ? type : "타입없음") + 
                       " → 인식: " + hasDestroyProtection);
        }
        if (downgradeProtection != null) {
            String type = EnhanceItemUtil.getProtectionScrollType(downgradeProtection);
            LOGGER.info("[UpgradeGui]   하락방지 슬롯: " + (type != null ? type : "타입없음") + 
                       " → 인식: " + hasDowngradeProtection);
        }

        // 강화 실행 (새 시그니처: 두 보호 타입 전달)
        UpgradeService.UpgradeResultInfo result = upgradeService.upgrade(
            player, item, hasDestroyProtection, hasDowngradeProtection
        );

        if (result.executed) {
            // [Phase 8] 보호 주문서 발동 시 특별 메시지, 아니면 기본 메시지
            if (result.destroyPrevented) {
                player.sendMessage(EnhanceConstants.PREFIX_UPGRADE + "§c§l파괴 방지! §7파괴 방지 주문서가 아이템을 보호했습니다!");
                LOGGER.info("[UpgradeGui] " + player.getName() + " 파괴 방지 발동!");
            } else if (result.downgradePrevented) {
                player.sendMessage(EnhanceConstants.PREFIX_UPGRADE + "§e§l하락 방지! §7하락 방지 주문서가 레벨을 유지했습니다!");
                LOGGER.info("[UpgradeGui] " + player.getName() + " 하락 방지 발동!");
            } else {
                player.sendMessage(EnhanceConstants.PREFIX_UPGRADE + result.message);
            }

            // [Phase 8] 보호 주문서 소모 - 각각 독립적으로 처리
            // 파괴 방지 소모: 파괴 결과였으나 방지된 경우
            if (hasDestroyProtection && result.destroyPrevented) {
                destroyProtection.setAmount(destroyProtection.getAmount() - 1);
                LOGGER.info("[UpgradeGui] " + player.getName() + " 파괴 방지 주문서 소모");
            }
            
            // 하락 방지 소모: 하락 결과였으나 방지된 경우
            if (hasDowngradeProtection && result.downgradePrevented) {
                downgradeProtection.setAmount(downgradeProtection.getAmount() - 1);
                LOGGER.info("[UpgradeGui] " + player.getName() + " 하락 방지 주문서 소모");
            }

            // 아이템 파괴 시 슬롯 비우기
            if (result.itemDestroyed) {
                inventory.setItem(ITEM_SLOT, null);
            }

            // GUI 업데이트
            updateGui(player, inventory);
        } else {
            player.sendMessage(EnhanceConstants.PREFIX_UPGRADE + result.message);
        }
    }
    
    /**
     * [v2.1] 보호권 슬롯 검증 - 잘못된 슬롯에 배치된 경우 경고
     * [v2.1 Audit] 헬퍼 메서드 사용으로 리팩토링
     */
    private void validateProtectionSlots(Player player, ItemStack destroySlotItem, ItemStack downgradeSlotItem) {
        // 파괴 방지 슬롯에 하락 방지 보호권이 있는 경우
        if ("downgrade".equals(getWrongScrollTypeInSlot(destroySlotItem, "destroy"))) {
            player.sendMessage(EnhanceConstants.PREFIX_UPGRADE + 
                "§e⚠ 하락 방지 주문서가 파괴 방지 슬롯에 있습니다!");
            player.sendMessage("§7  → 오른쪽(노랑) 슬롯에 넣어주세요.");
        }
        
        // 하락 방지 슬롯에 파괴 방지 보호권이 있는 경우
        if ("destroy".equals(getWrongScrollTypeInSlot(downgradeSlotItem, "downgrade"))) {
            player.sendMessage(EnhanceConstants.PREFIX_UPGRADE + 
                "§c⚠ 파괴 방지 주문서가 하락 방지 슬롯에 있습니다!");
            player.sendMessage("§7  → 왼쪽(빨강) 슬롯에 넣어주세요.");
        }
    }

    // ========== [v2.1 Audit] 보호권 검증 헬퍼 메서드 ==========
    
    /**
     * [v2.1 Audit] 파괴 방지 보호권 올바른 배치 여부
     */
    private boolean isValidDestroyProtection(ItemStack item) {
        return item != null && 
               EnhanceItemUtil.isProtectionScroll(item) &&
               "destroy".equals(EnhanceItemUtil.getProtectionScrollType(item));
    }
    
    /**
     * [v2.1 Audit] 하락 방지 보호권 올바른 배치 여부
     */
    private boolean isValidDowngradeProtection(ItemStack item) {
        return item != null && 
               EnhanceItemUtil.isProtectionScroll(item) &&
               "downgrade".equals(EnhanceItemUtil.getProtectionScrollType(item));
    }
    
    /**
     * [v2.1 Audit] 슬롯에 잘못된 보호권이 있는지 확인
     * @return "destroy", "downgrade", 또는 null (정상/빈 슬롯)
     */
    private String getWrongScrollTypeInSlot(ItemStack item, String expectedType) {
        if (item == null || !EnhanceItemUtil.isProtectionScroll(item)) {
            return null;
        }
        String actualType = EnhanceItemUtil.getProtectionScrollType(item);
        if (actualType != null && !actualType.equals(expectedType)) {
            return actualType;
        }
        return null;
    }

    /**
     * GUI 닫힘 처리 - 아이템 반환
     */
    public void handleClose(Player player, Inventory inventory) {
        returnItems(player, inventory);
        sessions.remove(player.getUniqueId());
    }

    /**
     * 아이템 반환
     * 
     * [Phase 8] 보호 주문서 2슬롯 분리
     */
    private void returnItems(Player player, Inventory inventory) {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        ItemStack destroyProtection = inventory.getItem(DESTROY_PROTECTION_SLOT);
        ItemStack downgradeProtection = inventory.getItem(DOWNGRADE_PROTECTION_SLOT);

        if (item != null) {
            player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
            inventory.setItem(ITEM_SLOT, null);
        }

        // [Phase 8] 파괴 방지 주문서 반환
        if (destroyProtection != null) {
            player.getInventory().addItem(destroyProtection).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
            inventory.setItem(DESTROY_PROTECTION_SLOT, null);
        }
        
        // [Phase 8] 하락 방지 주문서 반환
        if (downgradeProtection != null) {
            player.getInventory().addItem(downgradeProtection).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
            inventory.setItem(DOWNGRADE_PROTECTION_SLOT, null);
        }
    }

    /**
     * 강화 GUI인지 확인
     */
    public boolean isUpgradeGui(String title) {
        return GUI_TITLE.equals(title);
    }

    /**
     * 수정 가능한 슬롯인지 확인
     * 
     * [Phase 8] 보호 주문서 2슬롯 분리
     */
    public boolean isEditableSlot(int slot) {
        return slot == ITEM_SLOT || 
               slot == DESTROY_PROTECTION_SLOT || 
               slot == DOWNGRADE_PROTECTION_SLOT;
    }
    
    /**
     * 아이템 슬롯 번호 반환
     */
    public int getItemSlot() {
        return ITEM_SLOT;
    }
    
    /**
     * 파괴 방지 주문서 슬롯 번호 반환
     */
    public int getDestroyProtectionSlot() {
        return DESTROY_PROTECTION_SLOT;
    }
    
    /**
     * 하락 방지 주문서 슬롯 번호 반환
     */
    public int getDowngradeProtectionSlot() {
        return DOWNGRADE_PROTECTION_SLOT;
    }
    
    /**
     * 보호 주문서 슬롯 번호 반환 (하위 호환성)
     * @deprecated 2슬롯 분리됨. getDestroyProtectionSlot() 또는 getDowngradeProtectionSlot() 사용
     */
    @Deprecated
    public int getProtectionSlot() {
        return DESTROY_PROTECTION_SLOT;
    }

    // ========== 아이템 생성 ==========

    private ItemStack createBackground(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(ItemStack targetItem) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l✧ 강화 정보 ✧");

            List<String> lore = new ArrayList<>();
            lore.add("§8━━━━━━━━━━━━━━━━━━━━");

            if (targetItem == null || !EnhanceItemUtil.isUpgradeable(targetItem)) {
                lore.add("");
                lore.add("§7아이템을 왼쪽 슬롯에");
                lore.add("§7넣어주세요.");
                lore.add("");
            } else {
                int level = EnhanceItemUtil.getUpgradeLevel(targetItem);
                String levelColor = getUpgradeLevelColor(level);
                
                lore.add("");
                lore.add("§7현재 강화: " + levelColor + "+" + level);
                lore.add("");
                lore.add("§e▸ 성공 확률");
                lore.add("  " + upgradeService.getUpgradeChanceInfo(level));
                lore.add("");
                lore.add("§e▸ 실패 시");
                lore.add("  " + getFailureInfo(level));
                lore.add("");
                lore.add("§e▸ 비용");
                lore.add("  §f" + String.format("%,d", upgradeService.getUpgradeCost(level)) + " BD");
                lore.add("");
            }
            lore.add("§8━━━━━━━━━━━━━━━━━━━━");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * 강화 등급별 색상
     */
    private String getUpgradeLevelColor(int level) {
        if (level >= 10) return "§c§l"; // 빨강 (최고)
        if (level >= 8) return "§6§l";  // 주황
        if (level >= 6) return "§e";    // 노랑
        if (level >= 4) return "§a";    // 초록
        if (level >= 2) return "§b";    // 하늘
        return "§f";                     // 흰색
    }
    
    /**
     * 실패 시 정보
     */
    private String getFailureInfo(int level) {
        if (level >= 8) return "§c파괴 위험";
        if (level >= 5) return "§e레벨 하락 가능";
        return "§a유지";
    }

    private ItemStack createUpgradeButton(ItemStack targetItem, Player player) {
        boolean canUpgrade = targetItem != null && 
                            EnhanceItemUtil.isUpgradeable(targetItem) &&
                            EnhanceItemUtil.getUpgradeLevel(targetItem) < EnhanceConstants.MAX_UPGRADE_LEVEL;

        Material buttonMaterial;
        String buttonName;
        
        if (canUpgrade) {
            int level = EnhanceItemUtil.getUpgradeLevel(targetItem);
            long cost = upgradeService.getUpgradeCost(level);
            long balance = economyService.getBalance(player);
            
            if (balance >= cost) {
                buttonMaterial = Material.LIME_CONCRETE;
                buttonName = "§a§l✔ 강화 시도 ✔";
            } else {
                buttonMaterial = Material.YELLOW_CONCRETE;
                buttonName = "§e§l⚠ 돈 부족 ⚠";
            }
        } else {
            buttonMaterial = Material.RED_CONCRETE;
            buttonName = "§c§l✘ 강화 불가 ✘";
        }

        ItemStack item = new ItemStack(buttonMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(buttonName);

            List<String> lore = new ArrayList<>();
            lore.add("§8━━━━━━━━━━━━━━━━");
            
            if (canUpgrade) {
                int level = EnhanceItemUtil.getUpgradeLevel(targetItem);
                long cost = upgradeService.getUpgradeCost(level);
                long balance = economyService.getBalance(player);
                String levelColor = getUpgradeLevelColor(level);
                String nextColor = getUpgradeLevelColor(level + 1);
                
                lore.add("");
                lore.add("§7" + levelColor + "+" + level + " §7→ " + nextColor + "+" + (level + 1));
                lore.add("");
                lore.add("§7비용: " + (balance >= cost ? "§a" : "§c") + String.format("%,d", cost) + " BD");
                lore.add("§7소지금: §f" + String.format("%,d", balance) + " BD");
                lore.add("");
                
                if (balance >= cost) {
                    lore.add("§a§l▶ 클릭하여 강화!");
                } else {
                    lore.add("§c돈이 부족합니다!");
                }
            } else {
                lore.add("");
                lore.add("§7강화할 아이템을");
                lore.add("§7왼쪽 슬롯에 넣어주세요.");
                lore.add("");
            }
            lore.add("§8━━━━━━━━━━━━━━━━");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createResultPreview(ItemStack targetItem) {
        if (targetItem == null || !EnhanceItemUtil.isUpgradeable(targetItem)) {
            ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7§l결과 미리보기");
                meta.setLore(Arrays.asList("", "§8아이템 등록 시 표시"));
                item.setItemMeta(meta);
            }
            return item;
        }
        
        int level = EnhanceItemUtil.getUpgradeLevel(targetItem);
        int nextLevel = level + 1;
        String nextColor = getUpgradeLevelColor(nextLevel);
        
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l성공 시 결과");
            
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7강화 등급: " + nextColor + "+" + nextLevel);
            lore.add("");
            lore.add("§7예상 스탯 증가:");
            lore.add("§a  공격력/방어력 +" + (nextLevel * 2) + "%");
            lore.add("");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l✘ 닫기");
            meta.setLore(Arrays.asList("", "§7클릭하여 닫기", "§8(아이템은 자동 반환)"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuideItem(String name, List<String> description) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(description);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createArrow(String symbol) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(symbol);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createTitleDecor() {
        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l⚔ 장비 강화소 ⚔");
            meta.setLore(Arrays.asList(
                "",
                "§7무기와 방어구를 강화하여",
                "§7더 강력한 장비를 만드세요!",
                "",
                "§e강화 등급: §f+0 ~ +100",
                "§c고등급 실패 시 파괴 주의!"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * [Phase 8] 슬롯 라벨 생성
     */
    private ItemStack createSlotLabel(String name, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList("§8↓ 해당 주문서를 아래에"));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ========== 내부 클래스 ==========

    private static class UpgradeSession {
        // 향후 확장용
    }
}
