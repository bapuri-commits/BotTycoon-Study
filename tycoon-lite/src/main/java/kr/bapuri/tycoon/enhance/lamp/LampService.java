package kr.bapuri.tycoon.enhance.lamp;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.common.EnhanceLoreBuilder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * LampService - 램프 비즈니스 로직
 * 
 * v2.5 다중 슬롯 시스템:
 * - 최대 4개 램프 슬롯 (기본 1, 확장 구매)
 * - 수치 영속화 (롤링된 값 저장)
 * - 빈 슬롯에 자동 적용, 슬롯 풀이면 경고
 * - 같은 효과 중복 허용 (수치 합산)
 * 
 * 천장(Pity) 시스템: 150회 사용 시 LEGENDARY 확정
 * 
 * v3.6 슬롯 선택 시스템:
 * - 모든 슬롯이 차있을 때 채팅으로 덮어쓸 슬롯 선택
 * - 15초 타임아웃
 * 
 * Phase 6 LITE: 레거시 버전 이식
 */
public class LampService {

    private final LampRegistry registry;
    private final LampItemFactory itemFactory;
    private final Logger logger;
    private final JavaPlugin plugin;
    
    // 천장 시스템 - 플레이어별 사용 횟수 (메모리, 재시작 시 리셋)
    // TODO: PlayerTycoonData로 이동하여 영속화
    // ConcurrentHashMap for thread safety (v3.6.1)
    private final Map<UUID, Integer> pityCounters = new ConcurrentHashMap<>();
    private static final int PITY_THRESHOLD = 150;
    
    // v3.6: 슬롯 선택 대기 상태 (ConcurrentHashMap for thread safety)
    private final Map<UUID, PendingLampApplication> pendingApplications = new ConcurrentHashMap<>();
    private static final long PENDING_TIMEOUT_MS = 15000; // 15초
    private static final long PENDING_TIMEOUT_TICKS = 300; // 15초 = 300틱

    public LampService(LampRegistry registry, LampItemFactory itemFactory, Logger logger, JavaPlugin plugin) {
        this.registry = registry;
        this.itemFactory = itemFactory;
        this.logger = logger;
        this.plugin = plugin;
    }
    
    // ========== v3.6: 슬롯 선택 시스템 ==========
    
    /**
     * 대기 중인 램프 적용 데이터
     */
    public static class PendingLampApplication {
        public final int inventorySlot;          // 대상 아이템 슬롯
        public final ItemStack targetItemCopy;   // 검증용 복사본 (isSimilar 비교용)
        public final ItemStack lampItemCopy;     // 검증용 복사본
        public final LampType lampType;          // 램프 타입
        public final int lampSlotCount;          // 선택 가능한 슬롯 수
        public final List<String> slotDescriptions; // 기존 슬롯 효과 설명
        public final long timestamp;
        private BukkitTask timeoutTask;          // 타임아웃 스케줄러
        
        public PendingLampApplication(int inventorySlot, ItemStack targetItem, ItemStack lampItem,
                                       LampType lampType, int lampSlotCount, List<String> slotDescriptions) {
            this.inventorySlot = inventorySlot;
            this.targetItemCopy = targetItem.clone();
            this.lampItemCopy = lampItem.clone();
            this.lampType = lampType;
            this.lampSlotCount = lampSlotCount;
            this.slotDescriptions = slotDescriptions;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > PENDING_TIMEOUT_MS;
        }
        
        public void setTimeoutTask(BukkitTask task) {
            this.timeoutTask = task;
        }
        
        public void cancelTimeoutTask() {
            if (timeoutTask != null && !timeoutTask.isCancelled()) {
                timeoutTask.cancel();
            }
        }
    }
    
    /**
     * 슬롯 선택 대기 상태 등록 (슬롯이 모두 차있을 때 호출)
     * @return 성공 시 슬롯 정보 리스트, 실패 시 null
     */
    public PendingLampApplication requestSlotSelection(Player player, int inventorySlot, 
                                                        ItemStack targetItem, ItemStack lampItem) {
        // 기존 대기 상태 취소
        cancelPendingApplication(player);
        
        LampType lampType = LampItemFactory.getLampType(lampItem);
        if (lampType == null) return null;
        
        // 레거시 마이그레이션
        EnhanceItemUtil.migrateLegacyLampEffect(targetItem);
        
        int slotCount = EnhanceItemUtil.getLampSlotCount(targetItem);
        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(targetItem);
        
        // 슬롯 설명 생성
        List<String> slotDescriptions = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            if (i < slots.size() && !slots.get(i).isEmpty()) {
                slotDescriptions.add(slots.get(i).getCompactDisplay());
            } else {
                slotDescriptions.add("§7(빈 슬롯)");
            }
        }
        
        PendingLampApplication pending = new PendingLampApplication(
            inventorySlot, targetItem, lampItem, lampType, slotCount, slotDescriptions);
        
        pendingApplications.put(player.getUniqueId(), pending);
        
        // 타임아웃 스케줄러 등록 (15초 후 자동 취소 + 알림)
        UUID playerId = player.getUniqueId();
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingLampApplication expired = pendingApplications.remove(playerId);
            if (expired != null) {
                Player p = Bukkit.getPlayer(playerId);
                if (p != null && p.isOnline()) {
                    p.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c시간이 초과되어 램프 슬롯 선택이 취소되었습니다.");
                }
                logger.info("[LampService] " + (p != null ? p.getName() : playerId) + " 슬롯 선택 타임아웃");
            }
        }, PENDING_TIMEOUT_TICKS);
        
        pending.setTimeoutTask(timeoutTask);
        
        logger.info("[LampService] " + player.getName() + " 슬롯 선택 대기 상태 진입");
        
        return pending;
    }
    
    /**
     * 선택된 슬롯에 램프 적용
     * @param slotChoice 1-based 슬롯 번호 (플레이어 입력)
     */
    public ApplyResult applyWithSlot(Player player, int slotChoice) {
        UUID playerId = player.getUniqueId();
        PendingLampApplication pending = pendingApplications.get(playerId);
        
        if (pending == null) {
            return ApplyResult.NO_PENDING_APPLICATION;
        }
        
        // 타임아웃 체크
        if (pending.isExpired()) {
            pendingApplications.remove(playerId);
            return ApplyResult.TIMEOUT;
        }
        
        // 슬롯 범위 체크 (1-based to 0-based)
        int targetSlot = slotChoice - 1;
        if (targetSlot < 0 || targetSlot >= pending.lampSlotCount) {
            return ApplyResult.INVALID_SLOT;
        }
        
        // 인벤토리에서 아이템 검증
        ItemStack currentTarget = player.getInventory().getItem(pending.inventorySlot);
        ItemStack currentCursor = player.getItemOnCursor();
        
        // 대상 아이템 검증
        if (currentTarget == null || currentTarget.getType().isAir()) {
            pending.cancelTimeoutTask();
            pendingApplications.remove(playerId);
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c대상 아이템이 사라졌습니다.");
            return ApplyResult.INVALID_TARGET;
        }
        
        // 아이템이 변경되었는지 확인 (isSimilar로 상세 비교 - 타입, 메타, 인챈트 등)
        if (!currentTarget.isSimilar(pending.targetItemCopy)) {
            pending.cancelTimeoutTask();
            pendingApplications.remove(playerId);
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c대상 아이템이 변경되었습니다.");
            return ApplyResult.INVALID_TARGET;
        }
        
        // 커서에 램프가 있는지 확인
        if (currentCursor == null || currentCursor.getType().isAir() || 
            !LampItemFactory.isLampItem(currentCursor)) {
            pending.cancelTimeoutTask();
            pendingApplications.remove(playerId);
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c램프가 사라졌습니다.");
            return ApplyResult.NOT_LAMP_ITEM;
        }
        
        // 램프 타입 확인
        LampType currentLampType = LampItemFactory.getLampType(currentCursor);
        if (currentLampType != pending.lampType) {
            pending.cancelTimeoutTask();
            pendingApplications.remove(playerId);
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c램프 종류가 변경되었습니다.");
            return ApplyResult.INVALID_LAMP;
        }
        
        // 대기 상태 제거 (타임아웃 태스크도 취소)
        pending.cancelTimeoutTask();
        pendingApplications.remove(playerId);
        
        // 덮어쓰기할 기존 효과 저장
        List<LampSlotData> existingSlots = EnhanceItemUtil.getLampSlots(currentTarget);
        String overwrittenEffect = null;
        if (targetSlot < existingSlots.size() && !existingSlots.get(targetSlot).isEmpty()) {
            overwrittenEffect = existingSlots.get(targetSlot).getCompactDisplay();
        }
        
        // 천장 시스템 체크
        int currentPity = pityCounters.getOrDefault(playerId, 0) + 1;
        LampEffect effect;
        
        if (currentPity >= PITY_THRESHOLD) {
            effect = rollLegendaryEffect(pending.lampType);
            pityCounters.put(playerId, 0);
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§6§l천장 도달! 전설 등급 확정!");
            logger.info("[LampService] " + player.getName() + " 천장 도달 (" + PITY_THRESHOLD + "회)");
        } else {
            effect = registry.rollRandomEffect(pending.lampType);
            if (effect != null && effect.getRarity() == LampEffect.Rarity.LEGENDARY) {
                pityCounters.put(playerId, 0);
            } else {
                pityCounters.put(playerId, currentPity);
            }
        }
        
        if (effect == null) {
            return ApplyResult.NO_AVAILABLE_EFFECTS;
        }
        
        // 수치 롤링
        double rolledValue1 = rollEffectValue(effect.getMinValue1(), effect.getMaxValue1());
        int rolledValue2 = rollEffectValue2(effect.getMinValue2(), effect.getMaxValue2());
        LampSlotData slotData = new LampSlotData(effect.getId(), rolledValue1, rolledValue2);
        
        // 램프 소모
        currentCursor.setAmount(currentCursor.getAmount() - 1);
        if (currentCursor.getAmount() <= 0) {
            player.setItemOnCursor(null);
        } else {
            player.setItemOnCursor(currentCursor);
        }
        
        // 슬롯에 효과 적용
        EnhanceItemUtil.setLampSlot(currentTarget, targetSlot, slotData);
        EnhanceLoreBuilder.refreshLore(currentTarget);
        
        // 인벤토리 업데이트
        player.getInventory().setItem(pending.inventorySlot, currentTarget);
        player.updateInventory();
        
        // 효과 재생
        playLampEffect(player, effect);
        
        // 결과 메시지
        player.sendMessage(EnhanceConstants.PREFIX_LAMP + effect.getRarity().getDisplayName() + " " + 
                          effect.getDisplayName() + " §f효과를 획득했습니다!");
        player.sendMessage("§7" + slotData.getCompactDisplay());
        player.sendMessage("§c기존 효과 덮어쓰기: §7" + (overwrittenEffect != null ? overwrittenEffect : "알 수 없음"));
        player.sendMessage("§8슬롯 [" + slotChoice + "]에 덮어씀");
        
        // 슬롯 상태 표시
        int activeCount = EnhanceItemUtil.getActiveLampCount(currentTarget);
        int slotCount = EnhanceItemUtil.getLampSlotCount(currentTarget);
        player.sendMessage("§7램프 슬롯: §e" + activeCount + "/" + slotCount + 
                          (slotCount < EnhanceConstants.MAX_LAMP_SLOTS ? " §8(확장 가능)" : ""));
        
        logger.info("[LampService] " + player.getName() + " 램프 슬롯 선택 적용: " + effect.getId() + 
                   " [v1=" + rolledValue1 + ", v2=" + rolledValue2 + "] slot=" + targetSlot + " (선택 덮어쓰기)");
        
        return ApplyResult.SUCCESS;
    }
    
    /**
     * 대기 상태 취소
     */
    public void cancelPendingApplication(Player player) {
        cancelPendingApplication(player.getUniqueId(), player.getName());
    }
    
    /**
     * 대기 상태 취소 (UUID 기반 - 로그아웃 시에도 사용)
     */
    public void cancelPendingApplication(UUID playerId, String playerName) {
        PendingLampApplication removed = pendingApplications.remove(playerId);
        if (removed != null) {
            removed.cancelTimeoutTask();
            logger.info("[LampService] " + playerName + " 슬롯 선택 취소됨");
        }
    }
    
    /**
     * 대기 상태가 있는지 확인
     */
    public boolean hasPendingApplication(Player player) {
        return hasPendingApplication(player.getUniqueId());
    }
    
    /**
     * 대기 상태가 있는지 확인 (UUID 기반)
     */
    public boolean hasPendingApplication(UUID playerId) {
        PendingLampApplication pending = pendingApplications.get(playerId);
        if (pending == null) return false;
        
        // 만료된 경우는 타임아웃 스케줄러가 처리하므로 여기서는 체크만
        return !pending.isExpired();
    }
    
    /**
     * 대기 상태 조회 (null-safe, 원자적 연산)
     * @return 유효한 대기 상태 또는 null (만료된 경우도 null)
     */
    public PendingLampApplication getPendingApplication(Player player) {
        PendingLampApplication pending = pendingApplications.get(player.getUniqueId());
        if (pending == null || pending.isExpired()) {
            return null;
        }
        return pending;
    }
    
    /**
     * 대기 상태 조회 및 검증 (원자적 연산)
     * hasPendingApplication + getPendingApplication 통합
     * @return 유효한 대기 상태 또는 null
     */
    public PendingLampApplication getValidPendingApplication(Player player) {
        PendingLampApplication pending = pendingApplications.get(player.getUniqueId());
        if (pending == null) return null;
        
        // 만료된 경우 null 반환 (타임아웃 스케줄러가 정리함)
        if (pending.isExpired()) {
            return null;
        }
        
        return pending;
    }
    
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * v4.0: 특정 슬롯에 램프 적용 (GUI에서 호출)
     * 
     * @param player 플레이어
     * @param targetItem 대상 아이템 (인벤토리에서 직접 참조)
     * @param lampItem 램프 아이템 (커서에서 직접 참조)
     * @param targetSlot 적용할 슬롯 (0-based)
     * @param overwrittenEffect 덮어쓸 기존 효과 설명 (null 가능)
     * @return ApplyResult
     */
    public ApplyResult applyLampToSpecificSlot(Player player, ItemStack targetItem, ItemStack lampItem,
                                                int targetSlot, String overwrittenEffect) {
        LampType lampType = LampItemFactory.getLampType(lampItem);
        if (lampType == null) {
            return ApplyResult.INVALID_LAMP;
        }
        
        // 적용 가능 여부 확인
        if (!lampType.canApplyTo(targetItem)) {
            return ApplyResult.INCOMPATIBLE_ITEM;
        }
        
        // 천장 시스템 체크
        UUID playerId = player.getUniqueId();
        int currentPity = pityCounters.getOrDefault(playerId, 0) + 1;
        
        LampEffect effect;
        
        if (currentPity >= PITY_THRESHOLD) {
            effect = rollLegendaryEffect(lampType);
            pityCounters.put(playerId, 0);
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§6§l천장 도달! 전설 등급 확정!");
            logger.info("[LampService] " + player.getName() + " 천장 도달 (" + PITY_THRESHOLD + "회)");
        } else {
            effect = registry.rollRandomEffect(lampType);
            if (effect != null && effect.getRarity() == LampEffect.Rarity.LEGENDARY) {
                pityCounters.put(playerId, 0);
            } else {
                pityCounters.put(playerId, currentPity);
            }
        }
        
        if (effect == null) {
            return ApplyResult.NO_AVAILABLE_EFFECTS;
        }
        
        // 수치 롤링
        double rolledValue1 = rollEffectValue(effect.getMinValue1(), effect.getMaxValue1());
        int rolledValue2 = rollEffectValue2(effect.getMinValue2(), effect.getMaxValue2());
        LampSlotData slotData = new LampSlotData(effect.getId(), rolledValue1, rolledValue2);
        
        // 램프 소모 (호출자에서 처리)
        // lampItem.setAmount(lampItem.getAmount() - 1);
        
        // 슬롯에 효과 적용
        EnhanceItemUtil.setLampSlot(targetItem, targetSlot, slotData);
        EnhanceLoreBuilder.refreshLore(targetItem);
        
        // 효과 재생
        playLampEffect(player, effect);
        
        // 결과 메시지
        player.sendMessage(EnhanceConstants.PREFIX_LAMP + effect.getRarity().getDisplayName() + " " + 
                          effect.getDisplayName() + " §f효과를 획득했습니다!");
        player.sendMessage("§7" + slotData.getCompactDisplay());
        
        if (overwrittenEffect != null) {
            player.sendMessage("§c기존 효과 덮어쓰기: §7" + overwrittenEffect);
        }
        player.sendMessage("§8슬롯 [" + (targetSlot + 1) + "]에 적용됨");
        
        // 슬롯 상태 표시
        int activeCount = EnhanceItemUtil.getActiveLampCount(targetItem);
        int slotCount = EnhanceItemUtil.getLampSlotCount(targetItem);
        player.sendMessage("§7램프 슬롯: §e" + activeCount + "/" + slotCount + 
                          (slotCount < EnhanceConstants.MAX_LAMP_SLOTS ? " §8(확장 가능)" : ""));
        
        logger.info("[LampService] " + player.getName() + " 램프 슬롯 적용 (GUI): " + effect.getId() + 
                   " [v1=" + rolledValue1 + ", v2=" + rolledValue2 + "] slot=" + targetSlot);
        
        return ApplyResult.SUCCESS;
    }
    
    /**
     * 램프 적용 (가챠 롤링) - v2.5 다중 슬롯 시스템
     * 
     * @param player 플레이어
     * @param targetItem 대상 아이템
     * @param lampItem 램프 아이템
     * @return ApplyResult
     */
    public ApplyResult applyLamp(Player player, ItemStack targetItem, ItemStack lampItem) {
        // 램프 아이템 검증
        if (!LampItemFactory.isLampItem(lampItem)) {
            return ApplyResult.NOT_LAMP_ITEM;
        }

        LampType lampType = LampItemFactory.getLampType(lampItem);
        if (lampType == null) {
            return ApplyResult.INVALID_LAMP;
        }

        // 대상 아이템 검증
        if (targetItem == null || targetItem.getType().isAir()) {
            return ApplyResult.INVALID_TARGET;
        }

        // 적용 가능 여부 확인
        if (!lampType.canApplyTo(targetItem)) {
            return ApplyResult.INCOMPATIBLE_ITEM;
        }

        // 레거시 마이그레이션 체크
        EnhanceItemUtil.migrateLegacyLampEffect(targetItem);

        // v3.5: 덮어쓰기 방식 - 빈 슬롯이 없으면 첫 번째 슬롯 덮어쓰기
        int targetSlot = EnhanceItemUtil.findEmptySlot(targetItem);
        boolean isOverwrite = false;
        String overwrittenEffect = null;
        
        if (targetSlot < 0) {
            // 빈 슬롯 없음 → 첫 번째 슬롯(0번) 덮어쓰기
            targetSlot = 0;
            isOverwrite = true;
            
            List<LampSlotData> existingSlots = EnhanceItemUtil.getLampSlots(targetItem);
            if (!existingSlots.isEmpty() && !existingSlots.get(0).isEmpty()) {
                overwrittenEffect = existingSlots.get(0).getCompactDisplay();
            }
        }

        // 천장 시스템 체크
        UUID playerId = player.getUniqueId();
        int currentPity = pityCounters.getOrDefault(playerId, 0) + 1;
        
        LampEffect effect;
        
        if (currentPity >= PITY_THRESHOLD) {
            // 천장 도달! LEGENDARY 확정
            effect = rollLegendaryEffect(lampType);
            pityCounters.put(playerId, 0); // 리셋
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§6§l천장 도달! 전설 등급 확정!");
            logger.info("[LampService] " + player.getName() + " 천장 도달 (" + PITY_THRESHOLD + "회)");
        } else {
            // 일반 롤링
            effect = registry.rollRandomEffect(lampType);
            
            // LEGENDARY 획득 시 천장 리셋
            if (effect != null && effect.getRarity() == LampEffect.Rarity.LEGENDARY) {
                pityCounters.put(playerId, 0);
            } else {
                pityCounters.put(playerId, currentPity);
            }
        }
        
        if (effect == null) {
            return ApplyResult.NO_AVAILABLE_EFFECTS;
        }

        // v2.5: 수치 롤링 (영속화)
        double rolledValue1 = rollEffectValue(effect.getMinValue1(), effect.getMaxValue1());
        int rolledValue2 = rollEffectValue2(effect.getMinValue2(), effect.getMaxValue2());
        
        LampSlotData slotData = new LampSlotData(effect.getId(), rolledValue1, rolledValue2);

        // [E-1 FIX] 램프 아이템 소모를 먼저 수행 (크래시 시 무료 효과 방지)
        lampItem.setAmount(lampItem.getAmount() - 1);

        // v3.5: 슬롯에 효과 적용 (덮어쓰기 또는 추가)
        if (isOverwrite) {
            EnhanceItemUtil.setLampSlot(targetItem, targetSlot, slotData);
        } else {
            EnhanceItemUtil.addLampSlot(targetItem, slotData);
        }
        
        // Lore 업데이트
        EnhanceLoreBuilder.refreshLore(targetItem);

        // 효과 및 소리
        playLampEffect(player, effect);

        // 결과 메시지
        player.sendMessage(EnhanceConstants.PREFIX_LAMP + effect.getRarity().getDisplayName() + " " + 
                          effect.getDisplayName() + " §f효과를 획득했습니다!");
        player.sendMessage("§7" + slotData.getCompactDisplay());
        
        if (isOverwrite) {
            player.sendMessage("§c기존 효과 덮어쓰기: §7" + (overwrittenEffect != null ? overwrittenEffect : "알 수 없음"));
            player.sendMessage("§8슬롯 [" + (targetSlot + 1) + "]에 덮어씀");
        } else {
            player.sendMessage("§8슬롯 [" + (targetSlot + 1) + "]에 적용됨");
        }

        // 슬롯 상태 표시
        int activeCount = EnhanceItemUtil.getActiveLampCount(targetItem);
        int slotCount = EnhanceItemUtil.getLampSlotCount(targetItem);
        player.sendMessage("§7램프 슬롯: §e" + activeCount + "/" + slotCount + 
                          (slotCount < EnhanceConstants.MAX_LAMP_SLOTS ? " §8(확장 가능)" : ""));

        logger.info("[LampService] " + player.getName() + " 램프 적용: " + effect.getId() + 
                   " [v1=" + rolledValue1 + ", v2=" + rolledValue2 + "] slot=" + targetSlot + 
                   (isOverwrite ? " (덮어쓰기)" : ""));
        return ApplyResult.SUCCESS;
    }
    
    /**
     * 효과 수치 롤링 (double)
     */
    private double rollEffectValue(double min, double max) {
        if (min == max) return min;
        return min + Math.random() * (max - min);
    }
    
    /**
     * 효과 보조 수치 롤링 (int)
     */
    private int rollEffectValue2(int min, int max) {
        if (min == max) return min;
        if (min > max) return min;
        return min + (int) (Math.random() * (max - min + 1));
    }

    /**
     * 램프 효과 제거 (v2.5: 특정 슬롯 제거)
     */
    public boolean removeLampEffect(ItemStack item, int slotIndex) {
        if (item == null) return false;

        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(item);
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return false;
        }
        
        if (slots.get(slotIndex).isEmpty()) {
            return false; // 이미 빈 슬롯
        }

        EnhanceItemUtil.removeLampSlot(item, slotIndex);
        EnhanceLoreBuilder.refreshLore(item);
        
        return true;
    }
    
    /**
     * 모든 램프 효과 제거
     */
    public boolean removeAllLampEffects(ItemStack item) {
        if (item == null) return false;

        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(item);
        if (slots.isEmpty()) return false;
        
        boolean removed = false;
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).isEmpty()) {
                EnhanceItemUtil.removeLampSlot(item, i);
                removed = true;
            }
        }
        
        if (removed) {
            EnhanceLoreBuilder.refreshLore(item);
        }
        
        return removed;
    }

    /**
     * 아이템의 램프 슬롯 데이터 조회 (v2.5)
     */
    public List<LampSlotData> getLampSlots(ItemStack item) {
        // 레거시 마이그레이션
        EnhanceItemUtil.migrateLegacyLampEffect(item);
        return EnhanceItemUtil.getLampSlots(item);
    }

    /**
     * 램프 효과가 있는지 확인 (v2.5: 하나라도 있으면 true)
     */
    public boolean hasLampEffect(ItemStack item) {
        return EnhanceItemUtil.getActiveLampCount(item) > 0;
    }

    /**
     * 특정 효과의 총 합산 값 조회 (v2.5: 같은 효과 중복 시 합산)
     */
    public double getTotalEffectValue(ItemStack item, String effectId) {
        return EnhanceItemUtil.getTotalEffectValue(item, effectId);
    }
    
    /**
     * 특정 효과의 총 보조값 합산 조회
     */
    public int getTotalEffectValue2(ItemStack item, String effectId) {
        return EnhanceItemUtil.getTotalEffectValue2(item, effectId);
    }

    /**
     * 특정 효과가 있는지 확인 (v2.5)
     */
    public boolean hasEffect(ItemStack item, String effectId) {
        return EnhanceItemUtil.hasLampSlotEffect(item, effectId);
    }
    
    /**
     * 슬롯 수 확장
     */
    public boolean expandSlot(ItemStack item) {
        if (item == null) return false;
        
        int currentSlots = EnhanceItemUtil.getLampSlotCount(item);
        if (currentSlots >= EnhanceConstants.MAX_LAMP_SLOTS) {
            return false; // 이미 최대
        }
        
        EnhanceItemUtil.setLampSlotCount(item, currentSlots + 1);
        EnhanceLoreBuilder.refreshLore(item);
        
        return true;
    }
    
    /**
     * 슬롯 확장 비용 조회
     */
    public long getSlotExpandCost(ItemStack item) {
        int currentSlots = EnhanceItemUtil.getLampSlotCount(item);
        int nextSlot = currentSlots + 1;
        
        // 급증 비용: 25K -> 75K -> 250K
        return switch (nextSlot) {
            case 2 -> 25000L;
            case 3 -> 75000L;
            case 4 -> 250000L;
            default -> -1L; // 확장 불가
        };
    }

    /**
     * 램프 적용 시 효과 재생
     */
    private void playLampEffect(Player player, LampEffect effect) {
        switch (effect.getRarity()) {
            case LEGENDARY -> {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.sendTitle("§6§l전설!", effect.getDisplayName(), 10, 40, 20);
            }
            case EPIC -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                player.sendTitle("§5§l영웅!", effect.getDisplayName(), 10, 30, 20);
            }
            case RARE -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            case UNCOMMON -> {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            }
            default -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
        }
    }

    /**
     * 천장용 LEGENDARY 효과 롤링
     */
    private LampEffect rollLegendaryEffect(LampType lampType) {
        // getAvailableEffects()에서 이미 활성화 체크 완료
        List<LampEffect> available = registry.getAvailableEffects(lampType);
        List<LampEffect> legendaries = available.stream()
                .filter(e -> e.getRarity() == LampEffect.Rarity.LEGENDARY)
                .toList();
        
        if (legendaries.isEmpty()) {
            // LEGENDARY 없으면 EPIC 중에서 선택
            List<LampEffect> epics = available.stream()
                    .filter(e -> e.getRarity() == LampEffect.Rarity.EPIC)
                    .toList();
            if (!epics.isEmpty()) {
                return epics.get((int) (Math.random() * epics.size()));
            }
            // 그것도 없으면 아무거나
            return available.isEmpty() ? null : available.get(0);
        }
        
        return legendaries.get((int) (Math.random() * legendaries.size()));
    }
    
    /**
     * 플레이어의 현재 천장 카운터 조회
     */
    public int getPityCount(Player player) {
        return pityCounters.getOrDefault(player.getUniqueId(), 0);
    }
    
    /**
     * 천장까지 남은 횟수
     */
    public int getPityRemaining(Player player) {
        return PITY_THRESHOLD - getPityCount(player);
    }

    // ========== Getter ==========

    public LampRegistry getRegistry() {
        return registry;
    }

    public LampItemFactory getItemFactory() {
        return itemFactory;
    }

    // ========== 결과 enum ==========

    public enum ApplyResult {
        SUCCESS("§a램프 효과가 적용되었습니다!"),
        NOT_LAMP_ITEM("§c램프 아이템이 아닙니다."),
        INVALID_LAMP("§c유효하지 않은 램프입니다."),
        INVALID_TARGET("§c유효하지 않은 대상 아이템입니다."),
        INCOMPATIBLE_ITEM("§c이 램프는 해당 아이템에 적용할 수 없습니다."),
        NO_AVAILABLE_EFFECTS("§c사용 가능한 효과가 없습니다."),
        SLOTS_FULL("§c모든 램프 슬롯이 사용 중입니다!"),
        // v3.6: 슬롯 선택 관련
        PENDING_SLOT_SELECTION("§e덮어쓸 슬롯을 선택해주세요."),
        NO_PENDING_APPLICATION("§c대기 중인 램프 적용이 없습니다."),
        TIMEOUT("§c시간이 초과되었습니다. 다시 시도해주세요."),
        INVALID_SLOT("§c유효하지 않은 슬롯 번호입니다.");

        private final String message;

        ApplyResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return this == SUCCESS;
        }
        
        public boolean isPendingSelection() {
            return this == PENDING_SLOT_SELECTION;
        }
    }
}
