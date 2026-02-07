package kr.bapuri.tycoon.tax;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.integration.LandsIntegration;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Logger;

/**
 * TaxScheduler - 주기적 세금 징수 스케줄러
 * 
 * 기능:
 * - 설정된 주기(기본 3시간)마다 세금 징수
 * - [v2] 소득세 + 보유세 동시 징수 (모든 플레이어)
 * - [v2] 토지세 징수 (마을 소유자)
 * - [v2] VIP 할인 적용
 * - 징수 결과 로깅 및 알림
 */
public class TaxScheduler {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final TaxConfig config;
    private final LandTaxService landTaxService;
    private final IncomeTaxService incomeTaxService;  // [v2]
    private final VipService vipService;              // [v2]
    private final EconomyService economyService;      // [v2]
    private final VillagerRegistry villagerRegistry;
    private final LandsIntegration landsIntegration;
    private final PlayerDataManager playerDataManager;

    private BukkitTask schedulerTask;
    private long lastCollectionTime = 0;

    // [v2] 새 생성자 (IncomeTaxService, VipService, EconomyService 추가)
    public TaxScheduler(JavaPlugin plugin, TaxConfig config, LandTaxService landTaxService,
                        IncomeTaxService incomeTaxService, VipService vipService, 
                        EconomyService economyService,
                        VillagerRegistry villagerRegistry, LandsIntegration landsIntegration, 
                        PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = config;
        this.landTaxService = landTaxService;
        this.incomeTaxService = incomeTaxService;
        this.vipService = vipService;
        this.economyService = economyService;
        this.villagerRegistry = villagerRegistry;
        this.landsIntegration = landsIntegration;
        this.playerDataManager = playerDataManager;
    }

    // 하위 호환용 생성자
    @Deprecated
    public TaxScheduler(JavaPlugin plugin, TaxConfig config, LandTaxService landTaxService,
                        VillagerRegistry villagerRegistry, LandsIntegration landsIntegration, 
                        PlayerDataManager playerDataManager) {
        this(plugin, config, landTaxService, null, null, null, 
             villagerRegistry, landsIntegration, playerDataManager);
    }

    /**
     * 스케줄러 시작
     */
    public void start() {
        if (!config.isLandTaxEnabled()) {
            logger.info("[TaxScheduler] 토지세가 비활성화되어 스케줄러를 시작하지 않습니다.");
            return;
        }

        stop(); // 기존 작업 중지

        long periodMillis = config.getPeriodMillis();
        long periodTicks = periodMillis / 50; // 밀리초 → 틱 변환

        // 서버 시작 시 즉시 실행하지 않고, 1분 후부터 시작
        long delayTicks = 20 * 60; // 1분

        schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runCollection, delayTicks, periodTicks);

        logger.info("[TaxScheduler] 스케줄러 시작됨 (주기: " + config.getPeriodHours() + "시간, " + 
                   periodTicks + " ticks)");
    }

    /**
     * 스케줄러 중지
     */
    public void stop() {
        if (schedulerTask != null && !schedulerTask.isCancelled()) {
            schedulerTask.cancel();
            schedulerTask = null;
            logger.info("[TaxScheduler] 스케줄러 중지됨");
        }
    }

    /**
     * [v2] 세금 징수 실행 (소득세 + 보유세 + 토지세)
     */
    private void runCollection() {
        logger.info("[TaxScheduler] 세금 징수 시작...");
        lastCollectionTime = System.currentTimeMillis();

        // 통계
        int incomeTaxSuccess = 0;
        int incomeTaxExempt = 0;
        long incomeTaxTotal = 0;
        
        int landTaxSuccess = 0;
        int landTaxFail = 0;
        int landTaxExempt = 0;
        long landTaxTotal = 0;

        // ===== 1. 소득세 + 보유세 징수 (모든 활동 플레이어) =====
        if (config.isIncomeTaxEnabled() && incomeTaxService != null) {
            Set<UUID> allPlayers = collectAllActivePlayers();
            
            for (UUID playerId : allPlayers) {
                IncomeTaxService.IntervalTaxResult result = 
                    incomeTaxService.calculateIntervalTax(playerId, vipService);
                
                if (result.isExempt()) {
                    incomeTaxExempt++;
                    notifyIncomeTaxExempt(playerId, result);
                    continue;
                }
                
                long taxAmount = result.getTotalTax();
                if (taxAmount <= 0) {
                    continue;
                }
                
                // 징수 실행
                boolean success = economyService.withdraw(playerId, taxAmount,
                        "INCOME_TAX", "소득세+보유세 (소득:" + result.getEarnedTax() + 
                                     ", 보유:" + result.getWealthTax() + ")");
                
                if (success) {
                    incomeTaxSuccess++;
                    incomeTaxTotal += taxAmount;
                    notifyIncomeTax(playerId, result);
                }
            }
            
            logger.info("[TaxScheduler] 소득세 징수 - 성공: " + incomeTaxSuccess + 
                       ", 면제: " + incomeTaxExempt + 
                       ", 총액: " + String.format("%,d", incomeTaxTotal) + " BD");
        }

        // ===== 2. 토지세 징수 (마을 소유자) =====
        if (config.isLandTaxEnabled()) {
            Set<UUID> landOwners = collectLandOwners();
            
            for (UUID playerId : landOwners) {
                LandTaxService.TaxCollectionResult result = collectLandTaxWithVip(playerId);
                
                switch (result.getStatus()) {
                    case SUCCESS:
                        landTaxSuccess++;
                        landTaxTotal += result.getTaxAmount();
                        notifyLandTax(playerId, result);
                        break;
                    case FAILED:
                    case FAILED_FROZEN:
                        landTaxFail++;
                        notifyLandTaxFailed(playerId, result);
                        break;
                    case EXEMPT_INACTIVE:
                    case EXEMPT_NO_INCOME:
                        landTaxExempt++;
                        break;
                    default:
                        break;
                }
            }
            
            logger.info("[TaxScheduler] 토지세 징수 - 성공: " + landTaxSuccess + 
                       ", 실패: " + landTaxFail + 
                       ", 면제: " + landTaxExempt + 
                       ", 총액: " + String.format("%,d", landTaxTotal) + " BD");
        }

        logger.info("[TaxScheduler] 세금 징수 완료 - 총합: " + 
                   String.format("%,d", incomeTaxTotal + landTaxTotal) + " BD");
    }

    /**
     * [v2] VIP 적용 토지세 징수
     */
    private LandTaxService.TaxCollectionResult collectLandTaxWithVip(UUID playerId) {
        // [v2] VipService 전달하여 VIP 할인 적용
        return landTaxService.collectTax(playerId, vipService);
    }

    /**
     * [v2] 모든 활동 플레이어 수집 (소득세 대상)
     * - 온라인 플레이어
     * - 오프라인이지만 intervalIncome > 0인 플레이어
     */
    private Set<UUID> collectAllActivePlayers() {
        Set<UUID> players = new HashSet<>();
        
        // 온라인 플레이어
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(player.getUniqueId());
        }
        
        // 오프라인 플레이어 중 intervalIncome이 있는 플레이어
        // (PlayerDataManager에서 모든 저장된 데이터 조회)
        for (UUID playerId : playerDataManager.getAllPlayerIds()) {
            PlayerTycoonData data = playerDataManager.get(playerId);
            if (data != null && data.getIntervalIncome() > 0) {
                players.add(playerId);
            }
        }
        
        return players;
    }

    /**
     * 마을 소유자 UUID 목록 수집
     */
    private Set<UUID> collectLandOwners() {
        Set<UUID> owners = new HashSet<>();

        // 온라인 플레이어의 마을 소유자
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<LandsIntegration.PlotInfo> lands = landsIntegration.getOwnedLands(player.getUniqueId());
            if (!lands.isEmpty()) {
                owners.add(player.getUniqueId());
            }
        }

        // VillagerRegistry에 등록된 마을 소유자도 포함
        for (VillagerRegistry.LandTaxData data : villagerRegistry.getAllLandData()) {
            if (data.getOwnerUuid() != null) {
                owners.add(data.getOwnerUuid());
            }
        }

        return owners;
    }

    // ===== 알림 메서드 =====

    /**
     * [v2] 소득세 징수 알림
     */
    private void notifyIncomeTax(UUID playerId, IncomeTaxService.IntervalTaxResult result) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.format("%,d", result.getTotalTax()));
            placeholders.put("earnedTax", String.format("%,d", result.getEarnedTax()));
            placeholders.put("wealthTax", String.format("%,d", result.getWealthTax()));
            placeholders.put("discount", String.valueOf(result.isVip() ? config.getVipDiscountPercent() : 0));
            placeholders.put("discountAmount", String.format("%,d", result.getVipDiscount()));
            placeholders.put("originalAmount", String.format("%,d", result.getTotalTax() + result.getVipDiscount()));
            
            player.sendMessage(config.getMessage("incomeTaxDeducted", placeholders));
            
            // VIP 할인 알림 (할인액 표시)
            if (result.isVip() && result.getVipDiscount() > 0) {
                Map<String, String> vipPlaceholders = new HashMap<>();
                vipPlaceholders.put("discount", String.valueOf(config.getVipDiscountPercent()));
                vipPlaceholders.put("discountAmount", String.format("%,d", result.getVipDiscount()));
                player.sendMessage(config.getMessage("vipDiscount", vipPlaceholders));
            }
        }
    }

    /**
     * [v2] 소득세 면제 알림
     */
    private void notifyIncomeTaxExempt(UUID playerId, IncomeTaxService.IntervalTaxResult result) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(config.getMessage("exemptInactive", Map.of()));
        }
    }

    /**
     * 토지세 징수 성공 알림
     */
    private void notifyLandTax(UUID playerId, LandTaxService.TaxCollectionResult result) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.format("%,d", result.getTaxAmount()));
            placeholders.put("land", result.getLandTaxes().size() + "개 마을");
            placeholders.put("discount", String.valueOf(result.isVip() ? config.getVipDiscountPercent() : 0));
            placeholders.put("discountAmount", String.format("%,d", result.getVipDiscount()));
            placeholders.put("originalAmount", String.format("%,d", result.getTaxBeforeDiscount()));
            
            player.sendMessage(config.getMessage("landTaxCollected", placeholders));
            
            // [v2] VIP 할인 알림 (할인액 표시)
            if (result.isVip() && result.getVipDiscount() > 0) {
                Map<String, String> vipPlaceholders = new HashMap<>();
                vipPlaceholders.put("discount", String.valueOf(config.getVipDiscountPercent()));
                vipPlaceholders.put("discountAmount", String.format("%,d", result.getVipDiscount()));
                player.sendMessage(config.getMessage("vipDiscount", vipPlaceholders));
            }
        }
    }

    /**
     * 토지세 징수 실패 알림
     */
    private void notifyLandTaxFailed(UUID playerId, LandTaxService.TaxCollectionResult result) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("required", String.format("%,d", result.getTaxAmount()));
            placeholders.put("balance", String.format("%,d", result.getRemainingBalance()));
            placeholders.put("discount", String.valueOf(result.isVip() ? config.getVipDiscountPercent() : 0));
            placeholders.put("discountAmount", String.format("%,d", result.getVipDiscount()));
            placeholders.put("originalAmount", String.format("%,d", result.getTaxBeforeDiscount()));
            
            player.sendMessage(config.getMessage("landTaxFailed", placeholders));
            
            // 정지된 마을 알림
            if (result.getStatus() == LandTaxService.TaxCollectionStatus.FAILED_FROZEN) {
                for (LandTaxService.LandTaxInfo landTax : result.getLandTaxes()) {
                    Map<String, String> landPlaceholders = Map.of("land", landTax.getLandName());
                    player.sendMessage(config.getMessage("landFrozen", landPlaceholders));
                }
            }
        }
    }

    // 하위 호환용 (deprecated)
    @Deprecated
    private void notifyPlayer(UUID playerId, LandTaxService.TaxCollectionResult result) {
        notifyLandTax(playerId, result);
    }

    @Deprecated
    private void notifyPlayerFailed(UUID playerId, LandTaxService.TaxCollectionResult result) {
        notifyLandTaxFailed(playerId, result);
    }

    /**
     * 수동 징수 (관리자용)
     */
    public void forceCollection() {
        runCollection();
    }

    /**
     * 특정 플레이어 세금 즉시 징수 (관리자용)
     */
    public LandTaxService.TaxCollectionResult forceCollectPlayer(UUID playerId) {
        return landTaxService.collectTax(playerId);
    }

    /**
     * 마지막 징수 시간 조회
     */
    public long getLastCollectionTime() {
        return lastCollectionTime;
    }

    /**
     * 다음 징수까지 남은 시간 (밀리초)
     */
    public long getTimeUntilNextCollection() {
        if (lastCollectionTime == 0) {
            return config.getPeriodMillis();
        }
        long elapsed = System.currentTimeMillis() - lastCollectionTime;
        return Math.max(0, config.getPeriodMillis() - elapsed);
    }
}
