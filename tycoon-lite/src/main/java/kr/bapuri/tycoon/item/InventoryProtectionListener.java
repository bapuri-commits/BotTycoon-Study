package kr.bapuri.tycoon.item;

import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * InventoryProtectionListener - 인벤토리 보호권 처리
 * 
 * <h2>기능</h2>
 * <ul>
 *   <li>UNIVERSAL_INVENTORY_SAVE: 모든 월드에서 사망 시 인벤토리 보호</li>
 *   <li>사망 시 인벤토리에 있으면 자동 소비</li>
 *   <li>아이템 드롭 자체를 막음 (keepInventory 효과)</li>
 *   <li>1회용 - 한 번 사망하면 소진</li>
 * </ul>
 * 
 * <h2>우선순위</h2>
 * <p>LOWEST 우선순위로 다른 플러그인(AngelChest, InventoryRollbackPlus 등)보다 먼저 처리</p>
 * 
 * <h2>v2.3 조건부 스냅샷 복원</h2>
 * <ul>
 *   <li>사망 시 인벤토리 스냅샷 저장</li>
 *   <li>keepInventory=true 설정</li>
 *   <li>AngelChest 차단 (AngelChestProtectionBlocker)</li>
 *   <li>리스폰 시 조건부 복원: 인벤토리가 비어있을 때만 스냅샷 복원</li>
 *   <li>keepInventory 작동 시: 복원 불필요 (두 배 방지)</li>
 * </ul>
 */
public class InventoryProtectionListener implements Listener {

    private static final String PREFIX = "§6[인벤토리 보호] §f";
    private static final boolean DEBUG = true;  // 디버그 모드
    
    private final JavaPlugin plugin;
    private final CoreItemAuthenticator authenticator;
    private final PlayerDataManager playerDataManager;
    private final Logger logger;
    
    // [v2.3] 스냅샷 저장 (조건부 복원용)
    private final Map<UUID, InventorySnapshot> pendingRestores = new ConcurrentHashMap<>();
    
    // [v2.2] AngelChest 충돌 방지를 위한 보호 대상 플레이어 추적
    // AngelChestProtectionBlocker에서 접근
    private static final java.util.Set<UUID> currentlyProtectedDeaths = 
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    /**
     * 인벤토리 스냅샷 - 사망 시점의 인벤토리 상태 저장
     */
    private static class InventorySnapshot {
        final ItemStack[] contents;
        final ItemStack[] armorContents;
        final ItemStack offhand;
        final int level;
        final float exp;
        final long timestamp;
        
        InventorySnapshot(Player player) {
            // 인벤토리 깊은 복사 (보호권 소비 전 상태)
            ItemStack[] original = player.getInventory().getContents();
            this.contents = new ItemStack[original.length];
            for (int i = 0; i < original.length; i++) {
                if (original[i] != null) {
                    this.contents[i] = original[i].clone();
                }
            }
            
            // 아머 깊은 복사
            ItemStack[] originalArmor = player.getInventory().getArmorContents();
            this.armorContents = new ItemStack[originalArmor.length];
            for (int i = 0; i < originalArmor.length; i++) {
                if (originalArmor[i] != null) {
                    this.armorContents[i] = originalArmor[i].clone();
                }
            }
            
            // 오프핸드 복사
            ItemStack originalOffhand = player.getInventory().getItemInOffHand();
            this.offhand = originalOffhand != null ? originalOffhand.clone() : null;
            
            // 경험치
            this.level = player.getLevel();
            this.exp = player.getExp();
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 현재 사망 이벤트에서 보호권이 발동된 플레이어인지 확인
     * AngelChestProtectionBlocker에서 사용
     */
    public static boolean isCurrentlyProtected(UUID playerId) {
        return currentlyProtectedDeaths.contains(playerId);
    }
    
    /**
     * 보호 상태 제거 (리스폰 후 호출)
     */
    public static void clearProtection(UUID playerId) {
        currentlyProtectedDeaths.remove(playerId);
    }

    public InventoryProtectionListener(JavaPlugin plugin, 
                                       CoreItemAuthenticator authenticator,
                                       PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.authenticator = authenticator;
        this.playerDataManager = playerDataManager;
        this.logger = plugin.getLogger();
        
        logger.info("[InventoryProtectionListener] 초기화 완료");
    }

    /**
     * 사망 시 인벤토리 보호 처리
     * 
     * LOWEST 우선순위로 다른 플러그인보다 먼저 처리하여
     * 인벤토리 스냅샷을 저장하고, 드롭 방지
     * 
     * [v2.0] Paper 호환성 개선:
     * - drops.clear() 후 keepInventory 설정
     * - 스냅샷 기반 복원 방식 유지
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDeathProtection(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        if (DEBUG) {
            logger.info("[INVENTORY_PROTECTION] 사망 이벤트 감지: " + player.getName());
        }
        
        // 인벤토리에서 범용 인벤토리 보호권 찾기
        ItemStack protectionItem = findProtectionItem(player);
        
        if (DEBUG) {
            logger.info("[INVENTORY_PROTECTION] 보호권 검색 결과: " + 
                (protectionItem != null ? "발견됨" : "없음"));
        }
        
        if (protectionItem != null) {
            // [v2.2] AngelChest 충돌 방지를 위해 먼저 보호 상태 등록
            currentlyProtectedDeaths.add(player.getUniqueId());
            
            // [v2.3] 스냅샷 먼저 저장 (보호권 소비 전 상태)
            InventorySnapshot snapshot = new InventorySnapshot(player);
            pendingRestores.put(player.getUniqueId(), snapshot);
            
            if (DEBUG) {
                int itemCount = 0;
                for (ItemStack item : snapshot.contents) {
                    if (item != null) itemCount++;
                }
                logger.info("[INVENTORY_PROTECTION] 스냅샷 저장: " + itemCount + "개 아이템, Lv." + snapshot.level);
            }
            
            // 아이템 소비
            consumeProtectionItem(player, protectionItem);
            
            // drops 클리어
            int dropsCleared = event.getDrops().size();
            event.getDrops().clear();
            
            // 인벤토리 보호 적용 - keepInventory 설정
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            
            // Paper 1.19+: setShouldDropExperience 사용 가능하면 호출
            try {
                event.setShouldDropExperience(false);
            } catch (NoSuchMethodError ignored) {
                // 구버전 Paper/Spigot에서는 무시
            }
            
            // 플레이어 데이터 업데이트
            PlayerTycoonData data = playerDataManager.get(player.getUniqueId());
            if (data != null) {
                data.markDirty();
            }
            
            // 메시지
            player.sendMessage(PREFIX + "§a범용 인벤토리 보호권이 발동했습니다!");
            player.sendMessage(PREFIX + "§7인벤토리와 경험치가 보호됩니다.");
            
            logger.info("[INVENTORY_PROTECTION] " + player.getName() + 
                " - 보호권 발동! drops=" + dropsCleared + "개 클리어, keepInventory=true" +
                " (위치: " + player.getLocation().getWorld().getName() + ")");
        } else {
            if (DEBUG) {
                // 인벤토리 내용 덤프 (디버깅용)
                int coreItemCount = 0;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && authenticator.isCoreItem(item)) {
                        CoreItemType type = authenticator.getItemType(item);
                        logger.info("[INVENTORY_PROTECTION] CoreItem 발견: " + 
                            (type != null ? type.name() : "UNKNOWN") + 
                            " (Material: " + item.getType() + ")");
                        coreItemCount++;
                    }
                }
                logger.info("[INVENTORY_PROTECTION] " + player.getName() + 
                    " 인벤토리에 CoreItem " + coreItemCount + "개 (보호권 아님)");
            }
        }
    }
    
    /**
     * 리스폰 시 조건부 인벤토리 복원
     * 
     * [v2.3] 조건부 스냅샷 복원:
     * - 인벤토리가 비어있으면: keepInventory 실패 → 스냅샷 복원
     * - 인벤토리가 있으면: keepInventory 성공 → 복원 불필요 (두 배 방지)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // AngelChest 보호 상태 정리
        currentlyProtectedDeaths.remove(uuid);
        
        // 스냅샷 가져오기
        InventorySnapshot snapshot = pendingRestores.remove(uuid);
        if (snapshot == null) {
            return;
        }
        
        // 1틱 후 조건부 복원 (리스폰 처리 완료 후)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            
            // 인벤토리가 비어있는지 확인
            boolean inventoryEmpty = isInventoryEmpty(player);
            
            if (DEBUG) {
                logger.info("[INVENTORY_PROTECTION] " + player.getName() + 
                    " - 리스폰 후 인벤토리 상태: " + (inventoryEmpty ? "비어있음" : "아이템 있음"));
            }
            
            if (inventoryEmpty) {
                // keepInventory가 작동하지 않았거나 다른 플러그인이 클리어함
                // 스냅샷에서 복원
                restoreFromSnapshot(player, snapshot);
                
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.2f);
                player.sendMessage(PREFIX + "§a인벤토리와 경험치가 복원되었습니다!");
                
                logger.info("[INVENTORY_PROTECTION] " + player.getName() + 
                    " - 스냅샷에서 복원됨 (keepInventory 실패 감지)");
            } else {
                // keepInventory가 정상 작동함 → 복원 불필요
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.2f);
                player.sendMessage(PREFIX + "§a인벤토리와 경험치가 보호되었습니다!");
                
                logger.info("[INVENTORY_PROTECTION] " + player.getName() + 
                    " - keepInventory 정상 작동 (복원 불필요)");
            }
        }, 1L);
    }
    
    /**
     * 인벤토리가 비어있는지 확인
     */
    private boolean isInventoryEmpty(Player player) {
        // 메인 인벤토리 확인
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        
        // 아머 슬롯 확인
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        
        // 오프핸드 확인
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 스냅샷에서 인벤토리 복원
     * 
     * 주의: 보호권은 이미 소비되었으므로 스냅샷에서 1개 제거 후 복원
     */
    private void restoreFromSnapshot(Player player, InventorySnapshot snapshot) {
        boolean protectionRemoved = false;
        
        // 1. 메인 인벤토리 복원 (깊은 복사 + 보호권 1개 제거)
        ItemStack[] contentsToRestore = new ItemStack[snapshot.contents.length];
        for (int i = 0; i < snapshot.contents.length; i++) {
            ItemStack original = snapshot.contents[i];
            if (original == null) {
                contentsToRestore[i] = null;
                continue;
            }
            
            // 깊은 복사
            ItemStack cloned = original.clone();
            
            // 보호권 1개 제거 (첫 번째만)
            if (!protectionRemoved) {
                CoreItemType type = authenticator.getItemType(cloned);
                if (type == CoreItemType.UNIVERSAL_INVENTORY_SAVE) {
                    if (cloned.getAmount() > 1) {
                        cloned.setAmount(cloned.getAmount() - 1);
                    } else {
                        cloned = null;
                    }
                    protectionRemoved = true;
                }
            }
            
            contentsToRestore[i] = cloned;
        }
        player.getInventory().setContents(contentsToRestore);
        
        // 2. 아머 복원 (깊은 복사)
        ItemStack[] armorToRestore = new ItemStack[snapshot.armorContents.length];
        for (int i = 0; i < snapshot.armorContents.length; i++) {
            ItemStack original = snapshot.armorContents[i];
            armorToRestore[i] = (original != null) ? original.clone() : null;
        }
        player.getInventory().setArmorContents(armorToRestore);
        
        // 3. 오프핸드 복원 (보호권 처리 포함)
        if (snapshot.offhand != null) {
            ItemStack offhandToRestore = snapshot.offhand.clone();
            
            // 오프핸드에서 보호권 제거 (메인에서 못 제거했으면)
            if (!protectionRemoved) {
                CoreItemType offhandType = authenticator.getItemType(offhandToRestore);
                if (offhandType == CoreItemType.UNIVERSAL_INVENTORY_SAVE) {
                    if (offhandToRestore.getAmount() > 1) {
                        offhandToRestore.setAmount(offhandToRestore.getAmount() - 1);
                    } else {
                        offhandToRestore = null;
                    }
                    protectionRemoved = true;
                }
            }
            
            if (offhandToRestore != null) {
                player.getInventory().setItemInOffHand(offhandToRestore);
            }
        }
        
        // 4. 경험치 복원
        player.setLevel(snapshot.level);
        player.setExp(snapshot.exp);
        
        if (DEBUG) {
            int itemCount = 0;
            for (ItemStack item : contentsToRestore) {
                if (item != null) itemCount++;
            }
            logger.info("[INVENTORY_PROTECTION] 복원 완료: " + itemCount + "개 아이템, Lv." + snapshot.level + 
                " (보호권 제거: " + protectionRemoved + ")");
        }
    }
    
    /**
     * 플레이어 퇴장 시 메모리 정리
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingRestores.remove(uuid);
        currentlyProtectedDeaths.remove(uuid);
    }

    /**
     * 플레이어 인벤토리에서 범용 인벤토리 보호권 찾기
     * 
     * 검색 범위: 메인 인벤토리 + 오프핸드 + 아머 슬롯
     * 
     * [v2.0] 디버그 로깅 추가
     * 
     * @return 찾은 아이템 (없으면 null)
     */
    private ItemStack findProtectionItem(Player player) {
        if (DEBUG) {
            logger.info("[INVENTORY_PROTECTION] " + player.getName() + " 인벤토리 검색 시작");
        }
        
        // 1. 메인 인벤토리 (핫바 + 인벤토리 슬롯)
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            
            CoreItemType type = authenticator.getItemType(item);
            if (DEBUG && type != null) {
                logger.info("[INVENTORY_PROTECTION]   - CoreItem: " + type.name() + 
                    " (Material: " + item.getType() + ")");
            }
            if (type == CoreItemType.UNIVERSAL_INVENTORY_SAVE) {
                if (DEBUG) {
                    logger.info("[INVENTORY_PROTECTION]   → 보호권 발견! (메인 인벤토리)");
                }
                return item;
            }
        }
        
        // 2. 오프핸드
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            CoreItemType type = authenticator.getItemType(offhand);
            if (DEBUG && type != null) {
                logger.info("[INVENTORY_PROTECTION]   - 오프핸드 CoreItem: " + type.name());
            }
            if (type == CoreItemType.UNIVERSAL_INVENTORY_SAVE) {
                if (DEBUG) {
                    logger.info("[INVENTORY_PROTECTION]   → 보호권 발견! (오프핸드)");
                }
                return offhand;
            }
        }
        
        // 3. 아머 슬롯 (혹시 모를 경우 대비)
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            
            CoreItemType type = authenticator.getItemType(armor);
            if (type == CoreItemType.UNIVERSAL_INVENTORY_SAVE) {
                if (DEBUG) {
                    logger.info("[INVENTORY_PROTECTION]   → 보호권 발견! (아머)");
                }
                return armor;
            }
        }
        
        if (DEBUG) {
            logger.info("[INVENTORY_PROTECTION] 검색 완료: 보호권 없음");
        }
        return null;
    }

    /**
     * 보호권 아이템 1개 소비
     */
    private void consumeProtectionItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }
        
        logger.info(String.format("[CORE_ITEM] CONSUME player=%s type=%s reason=사망 시 자동 발동",
                player.getName(), CoreItemType.UNIVERSAL_INVENTORY_SAVE.getConfigKey()));
    }
}
