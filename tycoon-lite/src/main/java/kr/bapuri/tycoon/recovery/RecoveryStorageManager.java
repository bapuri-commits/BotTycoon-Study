package kr.bapuri.tycoon.recovery;

import kr.bapuri.tycoon.common.PenaltyReason;
import kr.bapuri.tycoon.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * RecoveryStorageManager - Town Recovery Storage 관리
 * 
 * [Phase 8] 레거시에서 이식 및 리팩토링
 * 
 * 역할:
 * - AngelChest 만료 시 아이템을 영구 저장소로 이동 (외부 연동 필요)
 * - 보이드 사망 시 아이템 즉시 저장
 * - 플레이어별 최대 50개 엔트리 (FIFO)
 * - Town NPC GUI에서 회수 지원
 * 
 * 저장 형식: plugins/TycoonLite/recovery/<uuid>.yml
 * 
 * [LITE 리팩토링]
 * - 자체 DeathChest 시스템 제거 (AngelChest 사용)
 * - LITE의 EconomyService, PlayerDataManager 사용
 */
public class RecoveryStorageManager {

    private final Plugin plugin;
    private final Logger logger;
    private final File storageFolder;
    
    // 설정
    private int maxEntriesPerPlayer = 50;
    
    // 캐시 (플레이어 UUID -> 엔트리 리스트)
    private final Map<UUID, List<RecoveryEntry>> cache = new ConcurrentHashMap<>();
    
    // 처리된 이벤트 ID (중복 처리 방지) - 엔트리에도 저장되어 재시작 후에도 유지됨
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    
    // 의존성 (나중에 주입)
    private EconomyService economyService;

    public RecoveryStorageManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.storageFolder = new File(plugin.getDataFolder(), "recovery");
        
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
        
        loadConfig();
    }

    private void loadConfig() {
        maxEntriesPerPlayer = plugin.getConfig().getInt("recoveryStorage.maxEntriesPerPlayer", 50);
    }

    // ===== 의존성 주입 =====

    public void setEconomyService(EconomyService economyService) {
        this.economyService = economyService;
    }
    
    /**
     * 설정 리로드
     * [Phase 8] 런타임 설정 변경 지원
     */
    public void reloadConfig() {
        loadConfig();
        logger.info("[RecoveryStorage] 설정 리로드 완료: maxEntriesPerPlayer=" + maxEntriesPerPlayer);
    }

    // ===== 저장 =====

    /**
     * 아이템을 Recovery Storage에 저장
     * 
     * @param ownerUUID 소유자 UUID
     * @param reason 저장 이유
     * @param deathLocation 사망 위치 (옵션)
     * @param items 저장할 아이템
     * @param killerUUID PvP 킬러 UUID (옵션)
     * @param killerName PvP 킬러 이름 (옵션)
     * @param eventId 이벤트 ID (중복 처리 방지)
     * @return 저장된 RecoveryEntry, 실패 시 null
     */
    public RecoveryEntry store(UUID ownerUUID, PenaltyReason reason, Location deathLocation,
                                List<ItemStack> items, UUID killerUUID, String killerName,
                                String eventId) {
        // 중복 처리 방지 (in-memory 캐시)
        if (eventId != null && processedEventIds.contains(eventId)) {
            logger.info("[RecoveryStorage] 이미 처리된 이벤트 (캐시): " + eventId);
            return null;
        }
        
        // 중복 처리 방지 (저장된 엔트리에서 sourceEventId 확인 - 재시작 후에도 유효)
        if (eventId != null) {
            List<RecoveryEntry> existing = getOrLoadEntries(ownerUUID);
            for (RecoveryEntry e : existing) {
                if (eventId.equals(e.getSourceEventId())) {
                    logger.info("[RecoveryStorage] 이미 처리된 이벤트 (저장됨): " + eventId);
                    processedEventIds.add(eventId); // 캐시에도 추가
                    return null;
                }
            }
        }
        
        // 아이템이 없으면 저장하지 않음
        if (items == null || items.isEmpty()) {
            return null;
        }
        
        // 유효한 아이템만 필터링
        List<ItemStack> validItems = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                validItems.add(item.clone());
            }
        }
        
        if (validItems.isEmpty()) {
            return null;
        }
        
        // 엔트리 생성
        RecoveryEntry entry = new RecoveryEntry(ownerUUID, reason, deathLocation, validItems);
        if (killerUUID != null) {
            entry.withKiller(killerUUID, killerName);
        }
        if (eventId != null) {
            entry.withSourceEventId(eventId);
        }
        
        // 캐시에 추가
        List<RecoveryEntry> entries = getOrLoadEntries(ownerUUID);
        entries.add(entry);
        
        // 최대 개수 초과 시 가장 오래된 것 삭제 (FIFO)
        while (entries.size() > maxEntriesPerPlayer) {
            RecoveryEntry oldest = entries.remove(0);
            logger.warning("[RecoveryStorage] 용량 초과로 가장 오래된 엔트리 삭제: " + 
                    ownerUUID + " -> " + oldest.getEntryId() + " (" + oldest.getTotalItemCount() + "개 아이템)");
        }
        
        // 이벤트 ID 기록
        if (eventId != null) {
            processedEventIds.add(eventId);
        }
        
        // 저장
        saveAsync(ownerUUID);
        
        // 온라인 플레이어에게 알림
        Player player = Bukkit.getPlayer(ownerUUID);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.GOLD + "[Town 보관소] " + ChatColor.WHITE + 
                    "아이템이 Town 보관소로 이동되었습니다.");
            player.sendMessage(ChatColor.GRAY + "Town의 보관소 NPC에서 회수할 수 있습니다.");
        }
        
        logger.info("[RecoveryStorage] 저장됨: " + ownerUUID + " -> " + entry);
        
        return entry;
    }

    /**
     * 간편 저장 (이벤트 ID 자동 생성)
     */
    public RecoveryEntry store(UUID ownerUUID, PenaltyReason reason, Location deathLocation,
                                List<ItemStack> items) {
        return store(ownerUUID, reason, deathLocation, items, null, null, null);
    }

    // ===== 조회 =====

    /**
     * 플레이어의 모든 엔트리 조회 (미수거 항목만)
     */
    public List<RecoveryEntry> getEntries(UUID ownerUUID) {
        List<RecoveryEntry> all = getOrLoadEntries(ownerUUID);
        List<RecoveryEntry> unclaimed = new ArrayList<>();
        for (RecoveryEntry entry : all) {
            if (!entry.isClaimed()) {
                unclaimed.add(entry);
            }
        }
        return unclaimed;
    }

    /**
     * 특정 엔트리 조회
     */
    public RecoveryEntry getEntry(UUID ownerUUID, String entryId) {
        for (RecoveryEntry entry : getOrLoadEntries(ownerUUID)) {
            if (entry.getEntryId().equals(entryId) && !entry.isClaimed()) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 플레이어의 미수거 엔트리 개수
     */
    public int getUnclaimedCount(UUID ownerUUID) {
        return getEntries(ownerUUID).size();
    }

    // ===== 회수 =====

    /**
     * 엔트리 회수 시도
     * 
     * @param player 회수하는 플레이어
     * @param entryId 엔트리 ID
     * @return 결과 (성공/실패/이유)
     */
    public ClaimResult claim(Player player, String entryId) {
        UUID uuid = player.getUniqueId();
        RecoveryEntry entry = getEntry(uuid, entryId);
        
        if (entry == null) {
            return ClaimResult.NOT_FOUND;
        }
        
        if (entry.isClaimed()) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        
        // 비용 계산
        long claimCost = calculateClaimCost(player, entry);
        
        // 비용 확인 (무료가 아닌 경우)
        if (claimCost > 0) {
            if (economyService == null || !economyService.hasBalance(player, claimCost)) {
                return ClaimResult.INSUFFICIENT_FUNDS;
            }
        }
        
        // [SAFETY] 먼저 회수 완료 처리 + 동기 저장 (중복 회수/DUPE 방지)
        // 이후 크래시 발생 시: 아이템 손실은 있을 수 있지만, 중복 지급은 불가능
        entry.markClaimed();
        saveSync(uuid);
        
        // 비용 차감 (이미 회수 처리되었으므로 안전)
        if (claimCost > 0) {
            economyService.withdraw(player, claimCost);
        }
        
        // 아이템 지급
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack item : entry.getItems()) {
            if (item != null) {
                HashMap<Integer, ItemStack> notFit = player.getInventory().addItem(item.clone());
                overflow.addAll(notFit.values());
            }
        }
        
        // 넘치는 아이템 드랍
        for (ItemStack item : overflow) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        
        // 메시지
        player.sendMessage(ChatColor.GREEN + "[Town 보관소] 아이템을 회수했습니다!");
        if (claimCost > 0) {
            player.sendMessage(ChatColor.GOLD + "회수 비용: " + claimCost + "원");
        } else {
            player.sendMessage(ChatColor.GRAY + "(무료 회수)");
        }
        if (!overflow.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "인벤토리가 가득 차서 " + overflow.size() + "개 아이템이 바닥에 떨어졌습니다.");
        }
        
        logger.info("[RecoveryStorage] 회수됨: " + player.getName() + " -> " + entryId);
        
        return ClaimResult.SUCCESS;
    }

    /**
     * 회수 비용 계산
     * 
     * [LITE 간소화] 직업 등급 기반 비용 제거, 고정 비용만 사용
     * - PvP 사망: 무료
     * - 그 외: 기본 비용
     */
    public long calculateClaimCost(Player player, RecoveryEntry entry) {
        // PvP 사망은 무료
        if (entry.isFreeClaim() || entry.isPvpDeath()) {
            return 0;
        }
        
        // 기본 비용
        long baseCost = plugin.getConfig().getLong("recoveryStorage.claimBaseCost", 500);
        
        // [LITE 간소화] 직업 등급/재산 기반 추가 비용 제거
        // 필요시 config에서 설정으로 변경 가능
        
        return baseCost;
    }

    // ===== 저장/로드 =====

    private List<RecoveryEntry> getOrLoadEntries(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromFile);
    }

    private List<RecoveryEntry> loadFromFile(UUID uuid) {
        File file = new File(storageFolder, uuid.toString() + ".yml");
        List<RecoveryEntry> entries = new ArrayList<>();
        
        if (!file.exists()) {
            return entries;
        }
        
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<?> rawList = config.getList("entries");
            
            if (rawList != null) {
                for (Object obj : rawList) {
                    if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) obj;
                        try {
                            RecoveryEntry entry = RecoveryEntry.deserialize(map);
                            entries.add(entry);
                        } catch (Exception e) {
                            logger.warning("[RecoveryStorage] 엔트리 로드 실패: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[RecoveryStorage] 파일 로드 실패: " + uuid + " - " + e.getMessage());
        }
        
        return entries;
    }

    private void saveAsync(UUID uuid) {
        List<RecoveryEntry> entries = cache.get(uuid);
        if (entries == null) return;
        
        // 데이터 복사
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (RecoveryEntry entry : entries) {
            serialized.add(entry.serialize());
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            saveToFile(uuid, serialized);
        });
    }

    /**
     * 동기 저장 (원자성이 필요한 작업용)
     */
    private void saveSync(UUID uuid) {
        List<RecoveryEntry> entries = cache.get(uuid);
        if (entries == null) return;
        
        // 데이터 복사
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (RecoveryEntry entry : entries) {
            serialized.add(entry.serialize());
        }
        
        saveToFile(uuid, serialized);
    }

    private void saveToFile(UUID uuid, List<Map<String, Object>> entries) {
        File file = new File(storageFolder, uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("uuid", uuid.toString());
        config.set("entries", entries);
        
        try {
            config.save(file);
        } catch (IOException e) {
            logger.warning("[RecoveryStorage] 저장 실패: " + uuid + " - " + e.getMessage());
        }
    }

    /**
     * 모든 캐시 저장 (서버 종료 시)
     */
    public void saveAll() {
        for (Map.Entry<UUID, List<RecoveryEntry>> e : cache.entrySet()) {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (RecoveryEntry entry : e.getValue()) {
                serialized.add(entry.serialize());
            }
            saveToFile(e.getKey(), serialized);
        }
        logger.info("[RecoveryStorage] 모든 데이터 저장 완료: " + cache.size() + "명");
    }

    /**
     * 캐시에서 언로드 (플레이어 퇴장 시 - 옵션)
     */
    public void unload(UUID uuid) {
        if (cache.containsKey(uuid)) {
            saveAsync(uuid);
            cache.remove(uuid);
        }
    }

    // ===== 결과 Enum =====

    public enum ClaimResult {
        SUCCESS("회수 성공"),
        NOT_FOUND("엔트리를 찾을 수 없습니다"),
        ALREADY_CLAIMED("이미 회수된 엔트리입니다"),
        INSUFFICIENT_FUNDS("비용이 부족합니다"),
        INVENTORY_FULL("인벤토리가 가득 찼습니다");

        private final String message;
        ClaimResult(String message) { this.message = message; }
        public String getMessage() { return message; }
    }
}
