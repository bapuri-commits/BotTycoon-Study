package kr.bapuri.tycoon.tax;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.integration.LandsIntegration;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;

/**
 * LandTaxService - 토지세 계산 및 징수
 * 
 * 기능:
 * - 마을별 토지세 계산 (청크 수 + 주민 수 기반)
 * - [v2] 직업별 청크 세금 + 청크 누진세 적용
 * - [v2] VIP 할인 적용
 * - 세금 징수 및 미납 처리
 * - Frozen 마을 관리
 */
public class LandTaxService {

    private final Logger logger;
    private final TaxConfig config;
    private final VillagerRegistry villagerRegistry;
    private final LandsIntegration landsIntegration;
    private final EconomyService economyService;
    private final PlayerDataManager playerDataManager;

    public LandTaxService(Logger logger, TaxConfig config, VillagerRegistry villagerRegistry,
                          LandsIntegration landsIntegration, EconomyService economyService,
                          PlayerDataManager playerDataManager) {
        this.logger = logger;
        this.config = config;
        this.villagerRegistry = villagerRegistry;
        this.landsIntegration = landsIntegration;
        this.economyService = economyService;
        this.playerDataManager = playerDataManager;
    }

    /**
     * 특정 마을의 토지세 계산 (마을별 정보용, 누진 미적용)
     * 
     * @param landName 마을 이름
     * @return 토지세 정보
     * @deprecated [v2] 청크 누진은 calculatePlayerTax에서 전체 적용
     */
    @Deprecated
    public LandTaxInfo calculateTax(String landName) {
        return calculateTax(landName, null);
    }

    /**
     * [v2] 특정 마을의 토지세 계산 (직업 기반, 누진 미적용)
     * 
     * @param landName 마을 이름
     * @param job 플레이어 직업 (null이면 기본값)
     * @return 토지세 정보
     */
    public LandTaxInfo calculateTax(String landName, JobType job) {
        // Lands에서 마을 정보 조회
        Optional<LandsIntegration.PlotInfo> landOpt = landsIntegration.getLandByName(landName);
        if (landOpt.isEmpty()) {
            return new LandTaxInfo(landName, null, 0, 0, 0, 0, 0);
        }

        LandsIntegration.PlotInfo land = landOpt.get();
        int chunks = (int) land.getSize();
        int villagers = villagerRegistry.getVillagers(landName);

        // 청크 세금 계산 (직업별 기본 세금, 누진 미적용 - 참고용)
        long chunkTax = chunks * config.getPerChunkByJob(job);

        // 주민 세금 계산
        long villagerTax = 0;
        if (villagers > 0) {
            long perVillager = config.getVillagerTaxPerUnit(villagers);
            villagerTax = villagers * perVillager;
        }

        long totalTax = chunkTax + villagerTax;

        return new LandTaxInfo(landName, land.getOwnerId(), chunks, villagers, chunkTax, villagerTax, totalTax);
    }

    /**
     * [v2] 플레이어 소유 모든 마을의 총 세금 계산 (직업별 + 청크 누진 + VIP 할인)
     * 
     * @param playerId 플레이어 UUID
     * @return 세금 요약
     */
    public PlayerTaxSummary calculatePlayerTax(UUID playerId) {
        return calculatePlayerTax(playerId, null);
    }

    /**
     * [v2] 플레이어 소유 모든 마을의 총 세금 계산 (VIP 확인 포함)
     * 
     * @param playerId 플레이어 UUID
     * @param vipService VIP 확인 서비스 (null이면 할인 미적용)
     * @return 세금 요약
     */
    public PlayerTaxSummary calculatePlayerTax(UUID playerId, VipService vipService) {
        List<LandsIntegration.PlotInfo> lands = landsIntegration.getOwnedLands(playerId);
        
        // 플레이어 직업 조회
        JobType playerJob = getPlayerJob(playerId);
        
        // 1단계: 마을별 정보 수집
        int totalChunks = 0;
        int totalVillagers = 0;
        long totalVillagerTax = 0;
        List<LandTaxInfo> landTaxes = new ArrayList<>();

        for (LandsIntegration.PlotInfo land : lands) {
            LandTaxInfo taxInfo = calculateTax(land.getName(), playerJob);
            landTaxes.add(taxInfo);
            
            totalChunks += taxInfo.getChunks();
            totalVillagers += taxInfo.getVillagers();
            totalVillagerTax += taxInfo.getVillagerTax();
        }

        // 2단계: 전체 청크에 누진세 적용 (마을별이 아닌 전체 기준)
        long totalChunkTax = config.calculateProgressiveChunkTax(totalChunks, playerJob);

        // 3단계: VIP 할인 적용
        boolean isVip = vipService != null && vipService.isVip(playerId);
        long originalTotal = totalChunkTax + totalVillagerTax;
        long finalTotal = originalTotal;
        
        if (isVip && originalTotal > 0) {
            finalTotal = config.applyVipDiscount(originalTotal);
        }

        return new PlayerTaxSummary(playerId, landTaxes, totalChunks, totalVillagers, 
                                    totalChunkTax, totalVillagerTax, isVip, 
                                    isVip ? (originalTotal - finalTotal) : 0);
    }

    /**
     * [v2] 플레이어 직업 조회
     */
    private JobType getPlayerJob(UUID playerId) {
        PlayerTycoonData data = playerDataManager.get(playerId);
        if (data == null) {
            return null; // 기본값 (2000 BD) 사용
        }
        return data.getTier1Job();
    }

    /**
     * 세금 징수 실행
     * 
     * @param playerId 플레이어 UUID
     * @return 징수 결과
     */
    public TaxCollectionResult collectTax(UUID playerId) {
        return collectTax(playerId, null);
    }

    /**
     * [v2] 세금 징수 실행 (VIP 할인 적용)
     * 
     * @param playerId 플레이어 UUID
     * @param vipService VIP 확인 서비스 (null이면 할인 미적용)
     * @return 징수 결과
     */
    public TaxCollectionResult collectTax(UUID playerId, VipService vipService) {
        if (!config.isLandTaxEnabled()) {
            return new TaxCollectionResult(playerId, 0, 0, TaxCollectionStatus.DISABLED, Collections.emptyList());
        }

        // 면제 조건 체크
        ExemptionResult exemption = checkExemption(playerId);
        if (exemption.isExempt()) {
            return new TaxCollectionResult(playerId, 0, 0, exemption.getStatus(), Collections.emptyList());
        }

        // [v2] VipService 전달
        PlayerTaxSummary summary = calculatePlayerTax(playerId, vipService);
        long totalTax = summary.getTotalTax();
        
        // [v2] VIP 정보 추출
        boolean isVip = summary.isVip();
        long vipDiscount = summary.getVipDiscount();
        long taxBeforeDiscount = summary.getTotalTaxBeforeDiscount();

        if (totalTax <= 0) {
            return new TaxCollectionResult(playerId, 0, 0, TaxCollectionStatus.NO_LAND, Collections.emptyList());
        }

        // 잔액 확인
        long balance = economyService.getBalance(playerId);
        
        if (balance >= totalTax) {
            // 징수 성공
            boolean success = economyService.withdraw(playerId, totalTax, 
                    "LAND_TAX", "토지세 징수 (" + summary.getLandTaxes().size() + "개 마을)" +
                               (isVip ? " [VIP 할인: " + vipDiscount + " BD]" : ""));
            
            if (success) {
                // 모든 마을 납부 기록 업데이트
                for (LandTaxInfo landTax : summary.getLandTaxes()) {
                    villagerRegistry.recordPayment(landTax.getLandName());
                    // Frozen 해제 (VillagerRegistry + Lands API)
                    if (villagerRegistry.isFrozen(landTax.getLandName())) {
                        villagerRegistry.unfreeze(landTax.getLandName());
                        // Lands API로 네이티브 정지 해제
                        landsIntegration.unfreezeLand(landTax.getLandName());
                    }
                }

                // 스냅샷 업데이트
                updateLifetimeSnapshot(playerId);

                // [v2] VIP 정보 포함하여 반환
                return new TaxCollectionResult(playerId, totalTax, balance - totalTax, 
                        TaxCollectionStatus.SUCCESS, summary.getLandTaxes(),
                        isVip, vipDiscount, taxBeforeDiscount);
            }
        }

        // 징수 실패 - 마을 정지
        if (config.isFreezeOnUnpaid()) {
            List<String> frozenLands = new ArrayList<>();
            for (LandTaxInfo landTax : summary.getLandTaxes()) {
                villagerRegistry.freeze(landTax.getLandName());
                // Lands API로 네이티브 정지
                landsIntegration.freezeLand(landTax.getLandName());
                frozenLands.add(landTax.getLandName());
            }
            
            // [v2] VIP 정보 포함하여 반환
            return new TaxCollectionResult(playerId, totalTax, balance, 
                    TaxCollectionStatus.FAILED_FROZEN, summary.getLandTaxes(),
                    isVip, vipDiscount, taxBeforeDiscount);
        }

        // [v2] VIP 정보 포함하여 반환
        return new TaxCollectionResult(playerId, totalTax, balance, 
                TaxCollectionStatus.FAILED, summary.getLandTaxes(),
                isVip, vipDiscount, taxBeforeDiscount);
    }

    /**
     * 면제 조건 체크
     */
    private ExemptionResult checkExemption(UUID playerId) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        
        // 미접속 면제 (inactiveHours 동안 미접속 시 면제)
        long lastPlayed = offlinePlayer.getLastPlayed();
        long inactiveMillis = (long) config.getInactiveExemptHours() * 60 * 60 * 1000;
        
        if (System.currentTimeMillis() - lastPlayed > inactiveMillis) {
            return new ExemptionResult(true, TaxCollectionStatus.EXEMPT_INACTIVE);
        }

        // 소득 변화 없음 면제 (온라인/오프라인 모두 체크)
        if (config.isNoIncomeExempt()) {
            // UUID로 데이터 조회 (오프라인도 파일에서 로드)
            PlayerTycoonData data = playerDataManager.get(playerId);
            if (data != null && !data.hasIncomeChange()) {
                return new ExemptionResult(true, TaxCollectionStatus.EXEMPT_NO_INCOME);
            }
        }

        return new ExemptionResult(false, null);
    }

    /**
     * 소득 스냅샷 업데이트
     */
    public void updateLifetimeSnapshot(UUID playerId) {
        // UUID로 데이터 조회 (오프라인도 가능)
        PlayerTycoonData data = playerDataManager.get(playerId);
        if (data != null) {
            data.setLifetimeEarnedSnapshot(data.getLifetimeEarned());
        }
    }

    /**
     * 마을이 정지 상태인지 확인
     */
    public boolean isLandFrozen(String landName) {
        return villagerRegistry.isFrozen(landName);
    }

    // ===== 결과 클래스 =====

    /**
     * 마을 세금 정보
     */
    public static class LandTaxInfo {
        private final String landName;
        private final UUID ownerId;
        private final int chunks;
        private final int villagers;
        private final long chunkTax;
        private final long villagerTax;
        private final long totalTax;

        public LandTaxInfo(String landName, UUID ownerId, int chunks, int villagers,
                          long chunkTax, long villagerTax, long totalTax) {
            this.landName = landName;
            this.ownerId = ownerId;
            this.chunks = chunks;
            this.villagers = villagers;
            this.chunkTax = chunkTax;
            this.villagerTax = villagerTax;
            this.totalTax = totalTax;
        }

        public String getLandName() { return landName; }
        public UUID getOwnerId() { return ownerId; }
        public int getChunks() { return chunks; }
        public int getVillagers() { return villagers; }
        public long getChunkTax() { return chunkTax; }
        public long getVillagerTax() { return villagerTax; }
        public long getTotalTax() { return totalTax; }
    }

    /**
     * 플레이어 세금 요약
     */
    public static class PlayerTaxSummary {
        private final UUID playerId;
        private final List<LandTaxInfo> landTaxes;
        private final int totalChunks;
        private final int totalVillagers;
        private final long totalChunkTax;
        private final long totalVillagerTax;
        private final boolean isVip;           // [v2]
        private final long vipDiscount;        // [v2]

        // [v2] 새 생성자 (VIP 정보 포함)
        public PlayerTaxSummary(UUID playerId, List<LandTaxInfo> landTaxes,
                               int totalChunks, int totalVillagers,
                               long totalChunkTax, long totalVillagerTax,
                               boolean isVip, long vipDiscount) {
            this.playerId = playerId;
            this.landTaxes = landTaxes;
            this.totalChunks = totalChunks;
            this.totalVillagers = totalVillagers;
            this.totalChunkTax = totalChunkTax;
            this.totalVillagerTax = totalVillagerTax;
            this.isVip = isVip;
            this.vipDiscount = vipDiscount;
        }

        // 하위 호환용 생성자
        public PlayerTaxSummary(UUID playerId, List<LandTaxInfo> landTaxes,
                               int totalChunks, int totalVillagers,
                               long totalChunkTax, long totalVillagerTax) {
            this(playerId, landTaxes, totalChunks, totalVillagers, 
                 totalChunkTax, totalVillagerTax, false, 0);
        }

        public UUID getPlayerId() { return playerId; }
        public List<LandTaxInfo> getLandTaxes() { return landTaxes; }
        public int getTotalChunks() { return totalChunks; }
        public int getTotalVillagers() { return totalVillagers; }
        public long getTotalChunkTax() { return totalChunkTax; }
        public long getTotalVillagerTax() { return totalVillagerTax; }
        public boolean isVip() { return isVip; }
        public long getVipDiscount() { return vipDiscount; }
        
        /** 
         * [v2] 총 세금 (VIP 할인 적용 후)
         */
        public long getTotalTax() { 
            return totalChunkTax + totalVillagerTax - vipDiscount; 
        }
        
        /**
         * [v2] 총 세금 (VIP 할인 적용 전)
         */
        public long getTotalTaxBeforeDiscount() {
            return totalChunkTax + totalVillagerTax;
        }
    }

    /**
     * 징수 결과
     */
    public static class TaxCollectionResult {
        private final UUID playerId;
        private final long taxAmount;
        private final long remainingBalance;
        private final TaxCollectionStatus status;
        private final List<LandTaxInfo> landTaxes;
        private final boolean isVip;           // [v2]
        private final long vipDiscount;        // [v2]
        private final long taxBeforeDiscount;  // [v2]

        // [v2] 새 생성자 (VIP 정보 포함)
        public TaxCollectionResult(UUID playerId, long taxAmount, long remainingBalance,
                                   TaxCollectionStatus status, List<LandTaxInfo> landTaxes,
                                   boolean isVip, long vipDiscount, long taxBeforeDiscount) {
            this.playerId = playerId;
            this.taxAmount = taxAmount;
            this.remainingBalance = remainingBalance;
            this.status = status;
            this.landTaxes = landTaxes;
            this.isVip = isVip;
            this.vipDiscount = vipDiscount;
            this.taxBeforeDiscount = taxBeforeDiscount;
        }

        // 하위 호환용 생성자
        public TaxCollectionResult(UUID playerId, long taxAmount, long remainingBalance,
                                   TaxCollectionStatus status, List<LandTaxInfo> landTaxes) {
            this(playerId, taxAmount, remainingBalance, status, landTaxes, false, 0, taxAmount);
        }

        public UUID getPlayerId() { return playerId; }
        public long getTaxAmount() { return taxAmount; }
        public long getRemainingBalance() { return remainingBalance; }
        public TaxCollectionStatus getStatus() { return status; }
        public List<LandTaxInfo> getLandTaxes() { return landTaxes; }
        public boolean isSuccess() { return status == TaxCollectionStatus.SUCCESS; }
        
        // [v2] VIP 정보
        public boolean isVip() { return isVip; }
        public long getVipDiscount() { return vipDiscount; }
        public long getTaxBeforeDiscount() { return taxBeforeDiscount; }
    }

    /**
     * 면제 결과
     */
    private static class ExemptionResult {
        private final boolean exempt;
        private final TaxCollectionStatus status;

        public ExemptionResult(boolean exempt, TaxCollectionStatus status) {
            this.exempt = exempt;
            this.status = status;
        }

        public boolean isExempt() { return exempt; }
        public TaxCollectionStatus getStatus() { return status; }
    }

    /**
     * 징수 상태
     */
    public enum TaxCollectionStatus {
        SUCCESS,          // 성공
        FAILED,           // 실패 (BD 부족)
        FAILED_FROZEN,    // 실패 + 마을 정지
        NO_LAND,          // 소유 마을 없음
        DISABLED,         // 세금 비활성화
        EXEMPT_INACTIVE,  // 면제 (미접속)
        EXEMPT_NO_INCOME  // 면제 (소득 변화 없음)
    }
}
