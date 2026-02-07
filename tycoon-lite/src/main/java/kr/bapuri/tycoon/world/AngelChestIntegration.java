package kr.bapuri.tycoon.world;

import kr.bapuri.tycoon.common.PenaltyReason;
import kr.bapuri.tycoon.recovery.RecoveryStorageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AngelChest 플러그인 연동 (리플렉션 기반)
 * 
 * <h2>기능</h2>
 * <ul>
 *   <li>월드 리셋 시 해당 월드의 모든 AngelChest 강제 만료</li>
 *   <li>만료 임박 체스트 → Recovery 보관소로 아이템 자동 이관 [Phase 8]</li>
 * </ul>
 * 
 * <h2>API 분석 (AngelChest 11.1.2)</h2>
 * <ul>
 *   <li>AngelChestMain.getInstance() → 싱글톤 인스턴스</li>
 *   <li>getAllAngelChests() → Set&lt;AngelChest&gt; 반환</li>
 *   <li>AngelChest.getWorld() → World 반환</li>
 *   <li>AngelChest.getSecondsLeft() → 남은 시간</li>
 *   <li>AngelChest.setSecondsLeft(int) → 남은 시간 설정 (0 = 즉시 만료)</li>
 *   <li>AngelChest.getStorageInv() → ItemStack[] (인벤토리 아이템)</li>
 *   <li>AngelChest.getArmorInv() → ItemStack[] (방어구)</li>
 *   <li>AngelChest.getOffhandItem() → ItemStack (오프핸드)</li>
 *   <li>AngelChest.getPlayer() → OfflinePlayer (소유자)</li>
 *   <li>AngelChest.getBlock() → Block (체스트 위치)</li>
 * </ul>
 * 
 * @since Phase 3.5 (월드 리셋 연동)
 * @since Phase 8 (만료 시 Recovery 이관)
 */
public class AngelChestIntegration implements WorldResetCommand.DeathChestIntegration {

    private final Logger logger;
    private final Plugin tycoonPlugin;
    private Plugin angelChestPlugin;
    private Object angelChestInstance;
    private Method getAllChestsMethod;
    private boolean available = false;
    
    // [Phase 8] Recovery 연동
    private RecoveryStorageManager recoveryManager;
    private BukkitTask expiryWatcherTask;
    
    // 이미 이관된 체스트 추적 (중복 이관 방지)
    private final Set<String> transferredChests = ConcurrentHashMap.newKeySet();
    
    // 만료 감시 설정
    private static final int EXPIRY_THRESHOLD_SECONDS = 15; // 만료 15초 전에 이관
    private static final long WATCHER_INTERVAL_TICKS = 200L; // 10초마다 체크
    
    // [Phase 8] 리플렉션 메서드 캐시 (성능 최적화)
    private final Map<Class<?>, Map<String, Method>> methodCache = new ConcurrentHashMap<>();
    
    public AngelChestIntegration(Plugin tycoonPlugin) {
        this.tycoonPlugin = tycoonPlugin;
        this.logger = Logger.getLogger("TycoonLite.AngelChest");
        
        initialize();
    }
    
    private void initialize() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AngelChest");
        if (plugin == null) {
            logger.info("[AngelChest] 플러그인이 없습니다. DeathChest 연동 비활성화");
            return;
        }
        
        if (!plugin.isEnabled()) {
            logger.info("[AngelChest] 플러그인이 비활성화 상태입니다.");
            return;
        }
        
        this.angelChestPlugin = plugin;
        
        // AngelChest API 접근 시도
        try {
            Class<?> mainClass = Class.forName("de.jeff_media.angelchest.AngelChestMain");
            
            // 1차 시도: getInstance() (표준)
            try {
                Method getInstanceMethod = mainClass.getMethod("getInstance");
                this.angelChestInstance = getInstanceMethod.invoke(null);
            } catch (NoSuchMethodException e) {
                // 2차 시도: $$goto() (난독화된 버전)
                Method obfuscatedMethod = mainClass.getMethod("$$goto");
                this.angelChestInstance = obfuscatedMethod.invoke(null);
            }
            
            // getAllAngelChests() 메서드 캐싱
            this.getAllChestsMethod = mainClass.getMethod("getAllAngelChests");
            
            this.available = true;
            logger.info("[AngelChest] 연동 초기화 완료 (버전: " + plugin.getDescription().getVersion() + ")");
            
        } catch (ClassNotFoundException e) {
            tryLegacyPackage();
        } catch (Exception e) {
            logger.log(Level.WARNING, "[AngelChest] API 접근 실패", e);
            logger.info("[AngelChest] DeathChest 연동이 비활성화됩니다.");
        }
    }
    
    private void tryLegacyPackage() {
        try {
            // 레거시 패키지 구조
            Class<?> mainClass = Class.forName("de.jeff_media.angelchest.Main");
            
            try {
                Method getInstanceMethod = mainClass.getMethod("getInstance");
                this.angelChestInstance = getInstanceMethod.invoke(null);
            } catch (NoSuchMethodException e) {
                Method obfuscatedMethod = mainClass.getMethod("$$goto");
                this.angelChestInstance = obfuscatedMethod.invoke(null);
            }
            
            this.getAllChestsMethod = mainClass.getMethod("getAllAngelChests");
            
            this.available = true;
            logger.info("[AngelChest] 레거시 API 연동 완료");
            
        } catch (Exception e) {
            logger.warning("[AngelChest] 레거시 API 접근도 실패. 수동 처리 필요.");
        }
    }
    
    @Override
    public int expireAllInWorlds(List<String> worldNames) {
        if (!available || angelChestInstance == null) {
            logger.info("[AngelChest] 연동 불가 - DeathChest 처리 생략");
            return 0;
        }
        
        int totalExpired = 0;
        
        for (String worldName : worldNames) {
            int expired = expireChestsInWorld(worldName);
            totalExpired += expired;
        }
        
        return totalExpired;
    }
    
    /**
     * 특정 월드의 모든 AngelChest 만료
     * getAllAngelChests() → Set<AngelChest> 순회 → setSecondsLeft(0)
     */
    private int expireChestsInWorld(String worldName) {
        try {
            if (getAllChestsMethod == null) {
                logger.fine("[AngelChest] getAllAngelChests 메서드가 없음");
                return 0;
            }
            
            Object chestsCollection = getAllChestsMethod.invoke(angelChestInstance);
            
            if (chestsCollection == null) {
                return 0;
            }
            
            int count = 0;
            
            // Set<AngelChest> 반환
            if (chestsCollection instanceof Set<?> chests) {
                // ConcurrentModificationException 방지
                Object[] chestArray = chests.toArray();
                
                for (Object chest : chestArray) {
                    if (isChestInWorld(chest, worldName)) {
                        expireChest(chest);
                        count++;
                    }
                }
            } else if (chestsCollection instanceof Iterable<?> chests) {
                for (Object chest : chests) {
                    if (isChestInWorld(chest, worldName)) {
                        expireChest(chest);
                        count++;
                    }
                }
            }
            
            if (count > 0) {
                logger.info("[AngelChest] " + worldName + "에서 " + count + "개 체스트 만료 처리");
            }
            
            return count;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "[AngelChest] " + worldName + " 처리 중 오류", e);
            return 0;
        }
    }
    
    /**
     * AngelChest.getWorld() → World 인스턴스 반환
     */
    private boolean isChestInWorld(Object chest, String worldName) {
        try {
            // AngelChest 인터페이스: getWorld() → World
            Method getWorldMethod = chest.getClass().getMethod("getWorld");
            Object world = getWorldMethod.invoke(chest);
            
            if (world instanceof World w) {
                return w.getName().equals(worldName);
            }
            
            // 폴백: getBlock().getWorld()
            try {
                Method getBlockMethod = chest.getClass().getMethod("getBlock");
                Object block = getBlockMethod.invoke(chest);
                if (block instanceof org.bukkit.block.Block b && b.getWorld() != null) {
                    return b.getWorld().getName().equals(worldName);
                }
            } catch (NoSuchMethodException ignored) {}
            
        } catch (Exception e) {
            logger.fine("[AngelChest] 월드 확인 실패: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 체스트 만료 처리
     * 방법 1: setSecondsLeft(0) → AngelChest 자체 만료 메커니즘 사용 (안전)
     * 방법 2: destroy()/remove() 직접 호출 (폴백)
     */
    private void expireChest(Object chest) {
        try {
            // 방법 1: setSecondsLeft(0) - 자연스러운 만료 (아이템 반환)
            try {
                Method setSecondsLeftMethod = chest.getClass().getMethod("setSecondsLeft", int.class);
                setSecondsLeftMethod.invoke(chest, 0);
                return; // 성공 시 종료
            } catch (NoSuchMethodException ignored) {}
            
            // 방법 2: setProtected(false) + setSecondsLeft(0)
            try {
                Method setProtectedMethod = chest.getClass().getMethod("setProtected", boolean.class);
                setProtectedMethod.invoke(chest, false);
                
                Method setSecondsLeftMethod = chest.getClass().getMethod("setSecondsLeft", int.class);
                setSecondsLeftMethod.invoke(chest, 0);
                return;
            } catch (NoSuchMethodException ignored) {}
            
            // 방법 3: destroy() 직접 호출 (폴백)
            for (String methodName : new String[]{"destroy", "remove"}) {
                try {
                    Method method = chest.getClass().getMethod(methodName);
                    method.invoke(chest);
                    return;
                } catch (NoSuchMethodException ignored) {}
            }
            
            logger.warning("[AngelChest] 체스트 만료 메서드를 찾을 수 없음");
            
        } catch (Exception e) {
            logger.fine("[AngelChest] 체스트 만료 실패: " + e.getMessage());
        }
    }
    
    /**
     * 연동 가능 여부
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * AngelChest 버전 반환
     */
    public String getVersion() {
        return angelChestPlugin != null ? angelChestPlugin.getDescription().getVersion() : "N/A";
    }
    
    // ========== [Phase 8] Recovery 연동 ==========
    
    /**
     * RecoveryStorageManager 연결
     * TycoonPlugin 초기화 시 호출
     */
    public void setRecoveryManager(RecoveryStorageManager recoveryManager) {
        this.recoveryManager = recoveryManager;
        
        if (available && recoveryManager != null) {
            startExpiryWatcher();
        }
    }
    
    /**
     * 만료 감시 스케줄러 시작
     * 만료 임박 체스트를 Recovery로 이관
     */
    private void startExpiryWatcher() {
        if (expiryWatcherTask != null) {
            expiryWatcherTask.cancel();
        }
        
        expiryWatcherTask = Bukkit.getScheduler().runTaskTimer(tycoonPlugin, () -> {
            if (!available || recoveryManager == null) return;
            
            try {
                checkAndTransferExpiringChests();
            } catch (Exception e) {
                logger.log(Level.WARNING, "[AngelChest] 만료 감시 중 오류", e);
            }
        }, WATCHER_INTERVAL_TICKS, WATCHER_INTERVAL_TICKS);
        
        logger.info("[AngelChest] 만료 감시 스케줄러 시작 (간격: " + (WATCHER_INTERVAL_TICKS / 20) + "초)");
    }
    
    /**
     * 만료 임박 체스트 확인 및 Recovery 이관
     */
    private void checkAndTransferExpiringChests() {
        if (getAllChestsMethod == null || angelChestInstance == null) return;
        
        try {
            Object chestsCollection = getAllChestsMethod.invoke(angelChestInstance);
            if (chestsCollection == null) return;
            
            // ConcurrentModificationException 방지를 위해 항상 배열로 복사
            Object[] chestArray;
            if (chestsCollection instanceof Collection<?> coll) {
                chestArray = coll.toArray();
            } else if (chestsCollection instanceof Iterable<?> iter) {
                List<Object> list = new ArrayList<>();
                iter.forEach(list::add);
                chestArray = list.toArray();
            } else {
                return;
            }
            
            // 현재 존재하는 체스트 ID 수집 (메모리 누수 방지)
            Set<String> currentChestIds = new HashSet<>();
            
            for (Object chest : chestArray) {
                try {
                    String chestId = getChestId(chest);
                    if (chestId != null) {
                        currentChestIds.add(chestId);
                    }
                    processChestForTransfer(chest);
                } catch (Exception e) {
                    logger.fine("[AngelChest] 체스트 처리 실패: " + e.getMessage());
                }
            }
            
            // 더 이상 존재하지 않는 체스트 ID 정리 (메모리 누수 방지)
            if (!transferredChests.isEmpty()) {
                transferredChests.retainAll(currentChestIds);
            }
            
        } catch (Exception e) {
            logger.fine("[AngelChest] 체스트 목록 조회 실패: " + e.getMessage());
        }
    }
    
    /**
     * 개별 체스트 이관 처리
     */
    private void processChestForTransfer(Object chest) throws Exception {
        // 체스트 ID 생성 (중복 방지용)
        String chestId = getChestId(chest);
        if (chestId == null || transferredChests.contains(chestId)) {
            return;
        }
        
        // 남은 시간 확인
        int secondsLeft = getSecondsLeft(chest);
        if (secondsLeft < 0) return; // 무한 체스트
        if (secondsLeft == 0) return; // 이미 만료 중 - AngelChest 자체 처리에 맡김
        if (secondsLeft > EXPIRY_THRESHOLD_SECONDS) return; // 아직 시간 여유 있음
        
        // 아이템 수집
        List<ItemStack> items = collectChestItems(chest);
        if (items.isEmpty()) return;
        
        // 소유자 정보
        OfflinePlayer owner = getChestOwner(chest);
        if (owner == null) return;
        
        // 체스트 위치
        Location location = getChestLocation(chest);
        
        // Recovery에 저장 (성공 여부 확인!)
        String eventId = "angelchest_expire_" + chestId;
        var entry = recoveryManager.store(
            owner.getUniqueId(),
            PenaltyReason.DEATHCHEST_EXPIRED,
            location,
            items,
            null,
            null,
            eventId
        );
        
        // 저장 실패 시 체스트 비우지 않음 (아이템 손실 방지)
        if (entry == null) {
            logger.warning("[AngelChest] Recovery 저장 실패 - 체스트 유지: " + chestId);
            return;
        }
        
        // 체스트 비우기 (바닥 드롭 방지)
        clearChestItems(chest);
        
        // 이관 완료 표시
        transferredChests.add(chestId);
        
        logger.info("[AngelChest] Recovery 이관 완료: " + owner.getName() + 
                " (" + items.size() + "개 아이템)");
        
        // 온라인이면 알림
        if (owner.isOnline() && owner.getPlayer() != null) {
            owner.getPlayer().sendMessage("§6[Town 보관소] §fAngelChest 아이템이 보관소로 이관되었습니다.");
        }
    }
    
    // ========== 리플렉션 헬퍼 메서드 ==========
    
    /**
     * 캐시된 메서드 조회 (성능 최적화)
     * 
     * @param clazz 대상 클래스
     * @param methodName 메서드 이름
     * @param paramTypes 파라미터 타입 (메서드 시그니처 구분용)
     * @return 캐시된 Method 또는 null
     */
    private Method getCachedMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        // 캐시 키: 메서드이름(파라미터타입1,파라미터타입2,...)
        StringBuilder keyBuilder = new StringBuilder(methodName);
        if (paramTypes.length > 0) {
            keyBuilder.append('(');
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) keyBuilder.append(',');
                keyBuilder.append(paramTypes[i].getSimpleName());
            }
            keyBuilder.append(')');
        }
        String cacheKey = keyBuilder.toString();
        
        return methodCache
            .computeIfAbsent(clazz, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(cacheKey, key -> {
                try {
                    return clazz.getMethod(methodName, paramTypes);
                } catch (NoSuchMethodException e) {
                    return null;
                }
            });
    }
    
    private String getChestId(Object chest) {
        try {
            Method getBlockMethod = getCachedMethod(chest.getClass(), "getBlock");
            if (getBlockMethod == null) return null;
            
            Object block = getBlockMethod.invoke(chest);
            if (block instanceof org.bukkit.block.Block b) {
                Location loc = b.getLocation();
                World world = loc.getWorld();
                if (world == null) return null; // 월드 언로드된 경우
                return world.getName() + "_" + loc.getBlockX() + "_" + 
                       loc.getBlockY() + "_" + loc.getBlockZ();
            }
        } catch (Exception ignored) {}
        return null;
    }
    
    private int getSecondsLeft(Object chest) {
        try {
            Method method = getCachedMethod(chest.getClass(), "getSecondsLeft");
            if (method == null) return -1;
            
            Object result = method.invoke(chest);
            return (int) result;
        } catch (Exception e) {
            return -1;
        }
    }
    
    private OfflinePlayer getChestOwner(Object chest) {
        try {
            Method method = getCachedMethod(chest.getClass(), "getPlayer");
            if (method == null) return null;
            
            Object result = method.invoke(chest);
            if (result instanceof OfflinePlayer) {
                return (OfflinePlayer) result;
            }
        } catch (Exception ignored) {}
        return null;
    }
    
    private Location getChestLocation(Object chest) {
        try {
            Method getBlockMethod = getCachedMethod(chest.getClass(), "getBlock");
            if (getBlockMethod == null) return null;
            
            Object block = getBlockMethod.invoke(chest);
            if (block instanceof org.bukkit.block.Block b) {
                return b.getLocation();
            }
        } catch (Exception ignored) {}
        return null;
    }
    
    private List<ItemStack> collectChestItems(Object chest) {
        List<ItemStack> items = new ArrayList<>();
        Class<?> chestClass = chest.getClass();
        
        try {
            // 인벤토리 아이템
            Method storageMethod = getCachedMethod(chestClass, "getStorageInv");
            if (storageMethod != null) {
                ItemStack[] storage = (ItemStack[]) storageMethod.invoke(chest);
                if (storage != null) {
                    for (ItemStack item : storage) {
                        if (item != null && !item.getType().isAir()) {
                            items.add(item.clone());
                        }
                    }
                }
            }
            
            // 방어구
            Method armorMethod = getCachedMethod(chestClass, "getArmorInv");
            if (armorMethod != null) {
                ItemStack[] armor = (ItemStack[]) armorMethod.invoke(chest);
                if (armor != null) {
                    for (ItemStack item : armor) {
                        if (item != null && !item.getType().isAir()) {
                            items.add(item.clone());
                        }
                    }
                }
            }
            
            // 오프핸드
            Method offhandMethod = getCachedMethod(chestClass, "getOffhandItem");
            if (offhandMethod != null) {
                ItemStack offhand = (ItemStack) offhandMethod.invoke(chest);
                if (offhand != null && !offhand.getType().isAir()) {
                    items.add(offhand.clone());
                }
            }
            
        } catch (Exception e) {
            logger.fine("[AngelChest] 아이템 수집 실패: " + e.getMessage());
        }
        
        return items;
    }
    
    private void clearChestItems(Object chest) {
        Class<?> chestClass = chest.getClass();
        
        try {
            // 인벤토리 비우기 (원본 크기 유지)
            Method getStorageMethod = getCachedMethod(chestClass, "getStorageInv");
            if (getStorageMethod != null) {
                ItemStack[] current = (ItemStack[]) getStorageMethod.invoke(chest);
                if (current != null) {
                    Method setStorageMethod = getCachedMethod(chestClass, "setStorageInv", ItemStack[].class);
                    if (setStorageMethod != null) {
                        setStorageMethod.invoke(chest, (Object) new ItemStack[current.length]);
                    }
                }
            }
            
            // 방어구 비우기 (원본 크기 유지)
            Method getArmorMethod = getCachedMethod(chestClass, "getArmorInv");
            if (getArmorMethod != null) {
                ItemStack[] current = (ItemStack[]) getArmorMethod.invoke(chest);
                if (current != null) {
                    Method setArmorMethod = getCachedMethod(chestClass, "setArmorInv", ItemStack[].class);
                    if (setArmorMethod != null) {
                        setArmorMethod.invoke(chest, (Object) new ItemStack[current.length]);
                    }
                }
            }
            
            // 오프핸드 비우기
            Method setOffhandMethod = getCachedMethod(chestClass, "setOffhandItem", ItemStack.class);
            if (setOffhandMethod != null) {
                setOffhandMethod.invoke(chest, (ItemStack) null);
            }
            
            // 경험치 비우기
            Method setExpMethod = getCachedMethod(chestClass, "setExperience", int.class);
            if (setExpMethod != null) {
                setExpMethod.invoke(chest, 0);
            }
            
        } catch (Exception e) {
            logger.fine("[AngelChest] 체스트 비우기 실패: " + e.getMessage());
        }
    }
    
    /**
     * 종료 처리
     */
    public void shutdown() {
        if (expiryWatcherTask != null) {
            expiryWatcherTask.cancel();
            expiryWatcherTask = null;
        }
        transferredChests.clear();
    }
}
