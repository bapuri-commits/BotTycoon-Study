package kr.bapuri.tycoon.enhance.enchant;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.common.EnhanceLoreBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * EnchantApplyListener - 모루에서 커스텀 인챈트 적용
 * 
 * PrepareAnvilEvent를 사용하여 커스텀 인챈트 북 + 아이템 조합 처리
 * 
 * Phase 6: 레거시 복사
 * [버그수정] InventoryClickEvent 핸들러 추가 - 결과 아이템 가져갈 때 처리
 */
public class EnchantApplyListener implements Listener {

    private final CustomEnchantService enchantService;
    private final CustomEnchantRegistry registry;
    private final EnchantBookFactory bookFactory;
    private static final Logger LOGGER = Logger.getLogger("TycoonLite");
    
    // 디버그 모드 (true일 때 상세 로그 출력)
    private static final boolean DEBUG = true;
    
    // 커스텀 인챈트 결과 추적 (모루 결과 슬롯에서 가져갈 때 사용)
    private final Map<UUID, ItemStack> pendingResults = new HashMap<>();

    public EnchantApplyListener(CustomEnchantService enchantService, CustomEnchantRegistry registry,
                                EnchantBookFactory bookFactory) {
        this.enchantService = enchantService;
        this.registry = registry;
        this.bookFactory = bookFactory;
    }
    
    private void debug(String message) {
        if (DEBUG) {
            LOGGER.info("[EnchantApply DEBUG] " + message);
        }
    }

    /**
     * [모루 비용 제한 해제] 모든 모루 작업에서 "Too Expensive!" 제한 해제
     * 
     * 바닐라 마인크래프트는 모루 비용이 40레벨 초과 시 작업 불가
     * 이 핸들러에서 최대 비용 제한을 크게 늘려서 해결
     * 
     * LOW 우선순위로 다른 처리보다 먼저 실행
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareAnvilRemoveCostLimit(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        
        // 최대 비용 제한을 1000레벨로 늘림 (사실상 무제한)
        inventory.setMaximumRepairCost(1000);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        
        // [추가] 비용 제한 해제 재확인 (다른 플러그인이 덮어쓸 수 있음)
        inventory.setMaximumRepairCost(1000);
        
        ItemStack firstItem = inventory.getItem(0);  // 왼쪽 슬롯 (대상 아이템)
        ItemStack secondItem = inventory.getItem(1); // 오른쪽 슬롯 (인챈트 북 또는 재료)

        debug("PrepareAnvil: first=" + (firstItem != null ? firstItem.getType() : "null") + 
              ", second=" + (secondItem != null ? secondItem.getType() : "null"));

        if (firstItem == null || secondItem == null) return;

        // 커스텀 인챈트 북인지 확인
        boolean isFirstBook = EnchantBookFactory.isEnchantBook(firstItem);
        boolean isSecondBook = EnchantBookFactory.isEnchantBook(secondItem);
        debug("isEnchantBook: first=" + isFirstBook + ", second=" + isSecondBook);
        
        // 인챈트 북 + 인챈트 북 합치기 (바닐라 스타일 레벨업)
        if (isFirstBook && isSecondBook) {
            handleBookMerge(event, firstItem, secondItem);
            return;
        }
        
        if (!isSecondBook) {
            // 커스텀 인챈트가 있는 아이템끼리 합치는 경우 처리
            handleItemMerge(event, firstItem, secondItem);
            return;
        }

        // 인챈트 북에서 정보 읽기
        String[] bookInfo = EnchantBookFactory.readBookInfo(secondItem);
        debug("bookInfo: " + (bookInfo != null ? bookInfo[0] + ":" + bookInfo[1] : "null"));
        if (bookInfo == null) return;

        String enchantId = bookInfo[0];
        int level;
        try {
            level = Integer.parseInt(bookInfo[1]);
        } catch (NumberFormatException e) {
            return;
        }

        // 적용 가능 여부 확인
        CustomEnchant enchant = CustomEnchant.fromId(enchantId);
        debug("enchant fromId: " + (enchant != null ? enchant.name() : "null"));
        if (enchant == null) {
            debug("canApplyTo failed: enchant is null for id=" + enchantId);
            return;
        }
        debug("enchant applicableTypes: " + enchant.getApplicableTypes());
        debug("target material: " + firstItem.getType().name());
        if (!enchant.canApplyTo(firstItem.getType())) {
            debug("canApplyTo failed: enchant=" + enchant.name() + 
                  ", targetType=" + firstItem.getType().name() +
                  ", applicableTypes=" + enchant.getApplicableTypes());
            return;
        }
        debug("canApplyTo passed!");

        CustomEnchantData data = registry.getData(enchantId);
        debug("registry data: " + (data != null ? "enabled=" + data.isEnabled() : "null"));
        if (data == null || !data.isEnabled()) return;

        // 결과 아이템 생성
        ItemStack result = firstItem.clone();

        // 기존 같은 인챈트가 있으면 레벨 합산
        int currentLevel = EnhanceItemUtil.getCustomEnchantLevel(result, enchantId);
        int newLevel = Math.min(currentLevel + level, data.getMaxLevel());

        // 인챈트 적용
        EnhanceItemUtil.addCustomEnchant(result, enchantId, newLevel);
        
        // Lore 업데이트
        EnhanceLoreBuilder.refreshLore(result);

        // 결과 아이템 설정
        event.setResult(result);
        debug("Result set: " + result.getType() + " with enchant " + enchantId + ":" + newLevel);

        // 레벨 비용 설정 (레벨에 따라)
        inventory.setRepairCost(level * 5);
        debug("RepairCost set: " + (level * 5));
        
        // [버그수정] 결과 추적 (InventoryClickEvent에서 사용)
        if (event.getViewers().size() > 0 && event.getViewers().get(0) instanceof Player player) {
            UUID playerId = player.getUniqueId();
            pendingResults.put(playerId, result.clone());
            debug("Pending result stored for: " + player.getName());
            
            // [버그수정] 1틱 후 결과 슬롯 강제 설정 + 인벤토리 업데이트
            // [복제방지] pendingResults에 여전히 존재하는 경우만 실행
            final ItemStack finalResult = result.clone();
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("TycoonLite"), () -> {
                    // 이미 결과를 가져갔거나 모루를 닫았으면 pendingResults에서 제거됨
                    if (!pendingResults.containsKey(playerId)) {
                        debug("Skipped forced update - player already took result: " + player.getName());
                        return;
                    }
                    if (player.getOpenInventory().getTopInventory() instanceof AnvilInventory anvil) {
                        anvil.setItem(2, finalResult);
                        player.updateInventory();
                        debug("Forced result update for: " + player.getName());
                    }
                }, 1L);
        }
    }

    /**
     * 인챈트 북 + 인챈트 북 합치기 (바닐라 스타일 레벨업)
     * 
     * 같은 인챈트의 같은 레벨 북 2개 → 다음 레벨 북 1개
     * 같은 인챈트의 다른 레벨 북 → 높은 레벨 북 유지
     * 다른 인챈트 → 합치기 불가
     */
    private void handleBookMerge(PrepareAnvilEvent event, ItemStack book1, ItemStack book2) {
        String[] info1 = EnchantBookFactory.readBookInfo(book1);
        String[] info2 = EnchantBookFactory.readBookInfo(book2);
        
        if (info1 == null || info2 == null) {
            debug("BookMerge: info null");
            return;
        }
        
        // 다른 인챈트면 합치기 불가
        if (!info1[0].equals(info2[0])) {
            debug("BookMerge: different enchants - " + info1[0] + " vs " + info2[0]);
            return;
        }
        
        String enchantId = info1[0];
        int level1, level2;
        try {
            level1 = Integer.parseInt(info1[1]);
            level2 = Integer.parseInt(info2[1]);
        } catch (NumberFormatException e) {
            return;
        }
        
        CustomEnchantData data = registry.getData(enchantId);
        if (data == null || !data.isEnabled()) {
            debug("BookMerge: enchant data null or disabled");
            return;
        }
        
        int newLevel;
        if (level1 == level2) {
            // 같은 레벨 → 다음 레벨
            newLevel = level1 + 1;
        } else {
            // 다른 레벨 → 높은 레벨 유지
            newLevel = Math.max(level1, level2);
        }
        
        // 최대 레벨 제한
        if (newLevel > data.getMaxLevel()) {
            // 이미 최대 레벨이면 합치기 불가
            if (level1 >= data.getMaxLevel() && level2 >= data.getMaxLevel()) {
                debug("BookMerge: already max level");
                return;
            }
            newLevel = data.getMaxLevel();
        }
        
        // 새 인챈트 북 생성
        ItemStack result = bookFactory.createBook(enchantId, newLevel);
        if (result == null) {
            debug("BookMerge: failed to create book");
            return;
        }
        
        event.setResult(result);
        
        // 레벨 비용 설정
        AnvilInventory inventory = event.getInventory();
        inventory.setRepairCost(newLevel * 3);
        
        debug("BookMerge success: " + enchantId + " " + level1 + "+" + level2 + " → " + newLevel);
        
        // 결과 추적
        if (event.getViewers().size() > 0 && event.getViewers().get(0) instanceof Player player) {
            UUID playerId = player.getUniqueId();
            pendingResults.put(playerId, result.clone());
            
            // [버그수정] 1틱 후 결과 슬롯 강제 설정 + 인벤토리 업데이트
            // [복제방지] pendingResults에 여전히 존재하는 경우만 실행
            final ItemStack finalResult = result.clone();
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("TycoonLite"), () -> {
                    if (!pendingResults.containsKey(playerId)) {
                        debug("BookMerge: Skipped - player already took result: " + player.getName());
                        return;
                    }
                    if (player.getOpenInventory().getTopInventory() instanceof AnvilInventory anvil) {
                        anvil.setItem(2, finalResult);
                        player.updateInventory();
                        debug("BookMerge: Forced result update for: " + player.getName());
                    }
                }, 1L);
        }
    }

    /**
     * 같은 아이템끼리 합치는 경우 커스텀 인챈트 병합
     */
    private void handleItemMerge(PrepareAnvilEvent event, ItemStack first, ItemStack second) {
        // 같은 타입의 아이템인지 확인
        if (first.getType() != second.getType()) return;

        // 둘 다 커스텀 인챈트가 있는지 확인
        Map<String, Integer> firstEnchants = EnhanceItemUtil.getCustomEnchants(first);
        Map<String, Integer> secondEnchants = EnhanceItemUtil.getCustomEnchants(second);

        if (firstEnchants.isEmpty() && secondEnchants.isEmpty()) return;

        // 기존 결과가 있으면 그것을 기반으로
        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) {
            result = first.clone();
        }

        // 인챈트 병합
        Map<String, Integer> resultEnchants = EnhanceItemUtil.getCustomEnchants(result);
        
        for (Map.Entry<String, Integer> entry : secondEnchants.entrySet()) {
            String enchantId = entry.getKey();
            int secondLevel = entry.getValue();
            int firstLevel = resultEnchants.getOrDefault(enchantId, 0);

            CustomEnchantData data = registry.getData(enchantId);
            if (data == null) continue;

            int newLevel;
            if (firstLevel == secondLevel) {
                // 같은 레벨이면 +1
                newLevel = Math.min(firstLevel + 1, data.getMaxLevel());
            } else {
                // 다른 레벨이면 높은 것 유지
                newLevel = Math.max(firstLevel, secondLevel);
            }

            resultEnchants.put(enchantId, newLevel);
        }

        // 결과에 적용
        EnhanceItemUtil.setCustomEnchants(result, resultEnchants);
        EnhanceLoreBuilder.refreshLore(result);

        event.setResult(result);
        
        // [버그수정] 결과 추적 + 강제 업데이트
        if (event.getViewers().size() > 0 && event.getViewers().get(0) instanceof Player player) {
            UUID playerId = player.getUniqueId();
            pendingResults.put(playerId, result.clone());
            
            // 1틱 후 결과 슬롯 강제 설정 + 인벤토리 업데이트
            // [복제방지] pendingResults에 여전히 존재하는 경우만 실행
            final ItemStack finalResult = result.clone();
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("TycoonLite"), () -> {
                    if (!pendingResults.containsKey(playerId)) {
                        debug("ItemMerge: Skipped - player already took result: " + player.getName());
                        return;
                    }
                    if (player.getOpenInventory().getTopInventory() instanceof AnvilInventory anvil) {
                        anvil.setItem(2, finalResult);
                        player.updateInventory();
                        debug("ItemMerge: Forced result update for: " + player.getName());
                    }
                }, 1L);
        }
    }
    
    /**
     * [버그수정] 모루 결과 슬롯 클릭 시 커스텀 인챈트 결과 적용
     * 
     * 일부 서버 환경에서 PrepareAnvilEvent의 setResult()가 제대로 작동하지 않을 수 있음
     * InventoryClickEvent로 결과 슬롯 클릭을 감지하여 직접 처리
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        
        // 결과 슬롯 (슬롯 2)
        if (event.getRawSlot() != 2) return;
        
        // 추적된 결과가 있는지 확인
        ItemStack pendingResult = pendingResults.get(player.getUniqueId());
        if (pendingResult == null) return;
        
        AnvilInventory anvil = (AnvilInventory) event.getInventory();
        ItemStack currentResult = anvil.getItem(2);
        
        // 결과가 비어있거나 커스텀 인챈트가 없으면 우리의 결과로 대체
        if (currentResult == null || currentResult.getType() == Material.AIR) {
            return;
        }
        
        // 커스텀 인챈트가 결과에 없으면 우리의 결과 적용
        Map<String, Integer> resultEnchants = EnhanceItemUtil.getCustomEnchants(currentResult);
        Map<String, Integer> pendingEnchants = EnhanceItemUtil.getCustomEnchants(pendingResult);
        
        if (resultEnchants.isEmpty() && !pendingEnchants.isEmpty()) {
            // 서버가 결과를 덮어썼으므로 우리의 결과로 복원
            anvil.setItem(2, pendingResult);
        }
        
        // 결과 추적 제거
        pendingResults.remove(player.getUniqueId());
    }
    
    /**
     * [버그수정] 모루 인벤토리 닫을 때 pendingResults 정리 (메모리 누수 방지)
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        
        pendingResults.remove(player.getUniqueId());
    }
}
