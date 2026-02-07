package kr.bapuri.tycoon.shop.price;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 시장 조작 방지 (간소화 버전)
 * 
 * [Phase 3.B] 레거시 MarketManipulationDetector를 간소화
 * 
 * <h2>탐지 패턴</h2>
 * <ul>
 *   <li>분할 거래: 1분 내 같은 아이템 5회 이상 분할 거래</li>
 *   <li>거래량 폭탄: 10분 내 전체 거래량의 30% 이상 점유</li>
 *   <li>대량 거래 영향력 감소: 10개 이상부터 영향력 감소, 100개 이상 최소 10%</li>
 * </ul>
 * 
 * <h2>레거시 대비 변경</h2>
 * <ul>
 *   <li>반복 패턴(Flipping) 탐지: 제거 (복잡도 대비 효과 미미)</li>
 *   <li>계정 협력 탐지: 제거 (오탐률 높음)</li>
 *   <li>코드 간소화: ~400줄 → ~200줄</li>
 * </ul>
 * 
 * @see DynamicPriceTracker
 */
public class ManipulationGuard {
    
    private static final Logger logger = Logger.getLogger("Tycoon.ManipulationGuard");
    
    // ========== 탐지 설정 (고정값) ==========
    
    // 분할 거래 탐지
    private static final int SPLIT_WINDOW_SECONDS = 60;      // 1분
    private static final int SPLIT_MAX_TRANSACTIONS = 5;     // 5회 이상 = 조작
    
    // 거래량 폭탄 탐지
    private static final double VOLUME_BOMB_THRESHOLD = 0.3; // 30% 점유
    private static final int VOLUME_BOMB_MIN_AMOUNT = 20;    // 최소 20개
    
    // 알림 쿨다운
    private static final int ALERT_COOLDOWN_SECONDS = 300;   // 5분
    
    // ========== 대량 거래 영향력 설정 (config에서 로드) ==========
    private int softLimitStart = 10;          // 영향력 감소 시작 (dynamicPrice.volumeInfluence.tier1Max)
    private int softLimitMax = 100;           // 최소 영향력 임계값 (dynamicPrice.volumeInfluence.tier3Max)
    private double minVolumeImpact = 0.10;    // 최소 영향력 (dynamicPrice.volumeInfluence.tier4Influence)
    
    private final Plugin plugin;
    private boolean enabled = true;
    
    // 플레이어별 거래 기록
    private final Map<UUID, List<TradeRecord>> playerHistory = new ConcurrentHashMap<>();
    
    // 아이템별 거래 기록
    private final Map<String, List<TradeRecord>> itemHistory = new ConcurrentHashMap<>();
    
    // 알림 쿨다운
    private final Map<String, Long> alertCooldowns = new ConcurrentHashMap<>();
    
    // 정리 태스크
    private BukkitTask cleanupTask;
    
    public ManipulationGuard(Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startCleanupTask();
        
        if (enabled) {
            logger.info("[ManipulationGuard] 초기화 완료");
        }
    }
    
    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("shop.manipulationGuard.enabled", true);
        
        // volumeInfluence 설정 (dynamicPrice 섹션에서 로드)
        softLimitStart = plugin.getConfig().getInt("dynamicPrice.volumeInfluence.tier1Max", 10);
        softLimitMax = plugin.getConfig().getInt("dynamicPrice.volumeInfluence.tier3Max", 100);
        minVolumeImpact = plugin.getConfig().getDouble("dynamicPrice.volumeInfluence.tier4Influence", 0.10);
        
        logger.info("[ManipulationGuard] 설정 로드: enabled=" + enabled + 
                   ", softLimit=" + softLimitStart + "~" + softLimitMax + 
                   ", minImpact=" + minVolumeImpact);
    }
    
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long cutoff = System.currentTimeMillis() - 30 * 60 * 1000; // 30분 이전 삭제
            
            // [FIX] 동기화된 정리 - ConcurrentModificationException 방지
            for (List<TradeRecord> list : playerHistory.values()) {
                synchronized (list) {
                    list.removeIf(r -> r.timestamp < cutoff);
                }
            }
            playerHistory.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty();
                }
            });
            
            for (List<TradeRecord> list : itemHistory.values()) {
                synchronized (list) {
                    list.removeIf(r -> r.timestamp < cutoff);
                }
            }
            itemHistory.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty();
                }
            });
            
            // 오래된 쿨다운 정리
            long cooldownCutoff = System.currentTimeMillis() - ALERT_COOLDOWN_SECONDS * 2 * 1000L;
            alertCooldowns.entrySet().removeIf(e -> e.getValue() < cooldownCutoff);
            
        }, 20 * 60, 20 * 60); // 1분마다
    }
    
    /**
     * 거래 기록 및 조작 탐지
     * 
     * @param playerUuid 플레이어 UUID
     * @param playerName 플레이어 이름
     * @param itemId 아이템 ID
     * @param amount 거래량
     * @param isBuy true=구매, false=판매
     * @return 조작 탐지 시 true (가격 영향력 무효화)
     */
    public boolean recordAndCheck(UUID playerUuid, String playerName, String itemId, 
                                  int amount, boolean isBuy) {
        if (!enabled) return false;
        
        long now = System.currentTimeMillis();
        TradeRecord record = new TradeRecord(playerUuid, playerName, itemId, amount, isBuy, now);
        
        // 기록 추가
        playerHistory.computeIfAbsent(playerUuid, k -> 
                Collections.synchronizedList(new ArrayList<>())).add(record);
        itemHistory.computeIfAbsent(itemId.toLowerCase(), k -> 
                Collections.synchronizedList(new ArrayList<>())).add(record);
        
        // 탐지
        boolean splitDetected = detectSplitTrading(playerUuid, itemId, isBuy, now);
        boolean volumeDetected = detectVolumeBombing(playerUuid, itemId, now);
        
        if (splitDetected || volumeDetected) {
            handleDetection(playerUuid, playerName, itemId, amount, isBuy, 
                    splitDetected, volumeDetected);
            return true;
        }
        
        return false;
    }
    
    /**
     * 분할 거래 탐지
     */
    private boolean detectSplitTrading(UUID playerUuid, String itemId, boolean isBuy, long now) {
        List<TradeRecord> history = playerHistory.get(playerUuid);
        if (history == null) return false;
        
        long windowStart = now - SPLIT_WINDOW_SECONDS * 1000L;
        
        synchronized (history) {
            long count = history.stream()
                    .filter(r -> r.timestamp >= windowStart)
                    .filter(r -> r.itemId.equalsIgnoreCase(itemId))
                    .filter(r -> r.isBuy == isBuy)
                    .count();
            
            return count >= SPLIT_MAX_TRANSACTIONS;
        }
    }
    
    /**
     * 거래량 폭탄 탐지
     */
    private boolean detectVolumeBombing(UUID playerUuid, String itemId, long now) {
        List<TradeRecord> history = itemHistory.get(itemId.toLowerCase());
        if (history == null || history.size() < 3) return false;
        
        long windowStart = now - 10 * 60 * 1000; // 10분
        
        int totalVolume = 0;
        int playerVolume = 0;
        
        synchronized (history) {
            for (TradeRecord r : history) {
                if (r.timestamp >= windowStart) {
                    totalVolume += r.amount;
                    if (r.playerUuid.equals(playerUuid)) {
                        playerVolume += r.amount;
                    }
                }
            }
        }
        
        if (totalVolume == 0) return false;
        
        double ratio = (double) playerVolume / totalVolume;
        return ratio >= VOLUME_BOMB_THRESHOLD && playerVolume > VOLUME_BOMB_MIN_AMOUNT;
    }
    
    /**
     * 대량 거래 시 가격 영향력 감소 계산
     * 
     * @param amount 거래량
     * @return 영향력 (minVolumeImpact ~ 1.0)
     */
    public double calculateVolumeImpact(int amount) {
        if (amount <= softLimitStart) {
            return 1.0;
        }
        if (amount >= softLimitMax) {
            return minVolumeImpact;
        }
        
        // 선형 보간
        double ratio = (double)(amount - softLimitStart) / (softLimitMax - softLimitStart);
        return 1.0 - ratio * (1.0 - minVolumeImpact);
    }
    
    private void handleDetection(UUID playerUuid, String playerName, String itemId,
                                 int amount, boolean isBuy, boolean split, boolean volume) {
        // 쿨다운 체크
        String cooldownKey = playerUuid + ":" + itemId;
        Long lastAlert = alertCooldowns.get(cooldownKey);
        if (lastAlert != null && System.currentTimeMillis() - lastAlert < ALERT_COOLDOWN_SECONDS * 1000L) {
            return;
        }
        alertCooldowns.put(cooldownKey, System.currentTimeMillis());
        
        // 로그
        String types = (split ? "분할거래" : "") + (split && volume ? ", " : "") + (volume ? "거래량폭탄" : "");
        logger.warning(String.format("[ManipulationGuard] DETECTED player=%s item=%s amount=%d action=%s types=[%s]",
                playerName, itemId, amount, isBuy ? "BUY" : "SELL", types));
        
        // 운영자 알림
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("tycoon.admin.market")) {
                admin.sendMessage("§c[시장 조작 의심] §f" + playerName);
                admin.sendMessage("§7  아이템: " + itemId + ", " + (isBuy ? "구매" : "판매") + " x" + amount);
                admin.sendMessage("§7  탐지: " + types);
            }
        }
    }
    
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        playerHistory.clear();
        itemHistory.clear();
        alertCooldowns.clear();
        logger.info("[ManipulationGuard] 종료 완료");
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    // ========== 내부 클래스 ==========
    
    private static class TradeRecord {
        final UUID playerUuid;
        final String playerName;
        final String itemId;
        final int amount;
        final boolean isBuy;
        final long timestamp;
        
        TradeRecord(UUID playerUuid, String playerName, String itemId,
                   int amount, boolean isBuy, long timestamp) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.itemId = itemId;
            this.amount = amount;
            this.isBuy = isBuy;
            this.timestamp = timestamp;
        }
    }
}
