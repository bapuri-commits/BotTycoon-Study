package kr.bapuri.tycoon.tax;

import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * IncomeTaxService - 소득세 계산 및 징수
 * 
 * 기능:
 * - [v1] BD 획득 시 즉시 소득세 차감 (deprecated)
 * - [v2] 3시간 간격 소득세 + 보유세 일괄 징수
 * - 관리자 지급분 면제
 * - VIP 할인 적용
 */
public class IncomeTaxService {

    private final Logger logger;
    private final TaxConfig config;
    private final PlayerDataManager playerDataManager;

    public IncomeTaxService(Logger logger, TaxConfig config, PlayerDataManager playerDataManager) {
        this.logger = logger;
        this.config = config;
        this.playerDataManager = playerDataManager;
    }

    // ===== [v2] 3시간 간격 세금 계산 =====

    /**
     * [v2] 3시간 간격 소득세 + 보유세 계산
     * 
     * TaxScheduler에서 3시간마다 호출
     * 
     * @param playerId 플레이어 UUID
     * @param vipService VIP 확인 서비스
     * @return 세금 계산 결과
     */
    public IntervalTaxResult calculateIntervalTax(UUID playerId, VipService vipService) {
        PlayerTycoonData data = playerDataManager.get(playerId);
        if (data == null) {
            return IntervalTaxResult.empty();
        }

        // 소득세 비활성화 시
        if (!config.isIncomeTaxEnabled()) {
            return IntervalTaxResult.disabled();
        }

        // 12시간 미접속 면제 체크
        if (isExemptByInactivity(data)) {
            return IntervalTaxResult.exemptInactive();
        }

        // 1. 소득세 계산 (3시간 간격 수입 기반)
        long intervalIncome = data.getIntervalIncome();
        double earnedRate = config.getEarnedTaxRate(intervalIncome);
        long earnedTax = Math.round(intervalIncome * earnedRate);

        // 2. 보유세 계산 (현재 잔액 기반)
        long currentBalance = data.getMoney();
        double wealthRate = config.getWealthTaxRate(currentBalance);
        long wealthTax = Math.round(currentBalance * wealthRate);

        long totalTax = earnedTax + wealthTax;
        long originalTotal = totalTax;

        // 3. VIP 할인 적용
        boolean isVip = vipService != null && vipService.isVip(playerId);
        if (isVip && totalTax > 0) {
            totalTax = config.applyVipDiscount(totalTax);
        }

        // 4. 간격 소득 리셋
        data.resetIntervalIncome();

        return new IntervalTaxResult(
            intervalIncome, 
            currentBalance,
            earnedTax, 
            wealthTax, 
            totalTax,
            earnedRate,
            wealthRate,
            isVip,
            isVip ? (originalTotal - totalTax) : 0
        );
    }

    /**
     * [v2] 12시간 미접속 면제 체크
     */
    private boolean isExemptByInactivity(PlayerTycoonData data) {
        long lastOnline = data.getLastOnlineTime();
        if (lastOnline <= 0) {
            return false; // 데이터 없음 - 면제 안함
        }

        long now = System.currentTimeMillis();
        long inactiveHours = config.getInactiveExemptHours();
        long inactiveMillis = inactiveHours * 60 * 60 * 1000L;

        return (now - lastOnline) >= inactiveMillis;
    }

    /**
     * [v2] 간격 세금 메시지 생성
     */
    public String formatIntervalTaxMessage(IntervalTaxResult result) {
        if (result.isExempt() || result.getTotalTax() <= 0) {
            return null;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.format("%,d", result.getTotalTax()));
        placeholders.put("earnedTax", String.format("%,d", result.getEarnedTax()));
        placeholders.put("wealthTax", String.format("%,d", result.getWealthTax()));

        if (result.isVip()) {
            placeholders.put("discount", String.valueOf(config.getVipDiscountPercent()));
        }

        return config.getMessage("incomeTaxDeducted", placeholders);
    }

    // ===== [v1] 기존 즉시 차감 방식 (하위 호환) =====

    /**
     * 소득세 계산 (즉시 차감 방식)
     * 
     * @deprecated [v2] 3시간 간격 방식으로 전환됨. calculateIntervalTax() 사용
     * 
     * @param player 플레이어
     * @param amount 획득 금액
     * @param isAdminGiven 관리자 지급 여부
     * @param giverUuid 지급자 UUID (null이면 시스템 지급)
     * @return 세금 차감 후 실제 지급액
     */
    @Deprecated
    public TaxResult calculateTax(Player player, long amount, boolean isAdminGiven, UUID giverUuid) {
        // 소득세 비활성화 시
        if (!config.isIncomeTaxEnabled()) {
            return new TaxResult(amount, 0, 0.0, false);
        }

        // 관리자 지급 면제
        if (isAdminGiven) {
            return new TaxResult(amount, 0, 0.0, true);
        }

        // 지급자가 관리자면 면제
        if (giverUuid != null && config.isAdminUuid(giverUuid)) {
            return new TaxResult(amount, 0, 0.0, true);
        }

        PlayerTycoonData data = playerDataManager.get(player);
        
        // 일일 리셋 체크
        checkDailyReset(data);
        
        // 현재 일일 소득 + 이번 획득량으로 세율 결정
        long currentDailyIncome = data.getDailyIncome();
        long newDailyIncome = currentDailyIncome + amount;
        
        // 세율 조회 (새로운 누적 소득 기준)
        double rate = config.getIncomeTaxRate(newDailyIncome);
        
        if (rate <= 0.0) {
            // 면세 구간
            data.addDailyIncome(amount);
            return new TaxResult(amount, 0, 0.0, false);
        }
        
        // 세금 계산
        long tax = Math.round(amount * rate);
        long afterTax = amount - tax;
        
        // 일일 소득 업데이트 (세금 차감 전 금액 기준)
        data.addDailyIncome(amount);
        
        return new TaxResult(afterTax, tax, rate, false);
    }

    /**
     * 일일 리셋 체크
     */
    private void checkDailyReset(PlayerTycoonData data) {
        long now = System.currentTimeMillis();
        long lastReset = data.getLastDailyReset();
        
        // 24시간(밀리초) 경과 시 리셋
        long dayInMillis = 24 * 60 * 60 * 1000L;
        if (now - lastReset >= dayInMillis) {
            data.resetDailyIncome();
            data.setLastDailyReset(now);
        }
    }

    /**
     * 세금 메시지 생성
     */
    public String formatTaxMessage(TaxResult result) {
        if (result.isExempt()) {
            return null; // 면제 시 메시지 없음
        }
        
        if (result.getTax() <= 0) {
            return null; // 세금 없으면 메시지 없음
        }
        
        Map<String, String> placeholders = Map.of(
            "amount", String.format("%,d", result.getTax()),
            "rate", String.format("%.1f", result.getRate() * 100)
        );
        
        return config.getMessage("incomeTaxDeducted", placeholders);
    }

    /**
     * 플레이어 세금 정보 조회
     */
    public PlayerTaxInfo getPlayerTaxInfo(Player player) {
        PlayerTycoonData data = playerDataManager.get(player);
        checkDailyReset(data);
        
        long dailyIncome = data.getDailyIncome();
        double currentRate = config.getIncomeTaxRate(dailyIncome);
        
        return new PlayerTaxInfo(dailyIncome, currentRate);
    }

    // ===== 결과 클래스 =====

    /**
     * 세금 계산 결과
     */
    public static class TaxResult {
        private final long afterTax;
        private final long tax;
        private final double rate;
        private final boolean exempt;

        public TaxResult(long afterTax, long tax, double rate, boolean exempt) {
            this.afterTax = afterTax;
            this.tax = tax;
            this.rate = rate;
            this.exempt = exempt;
        }

        public long getAfterTax() { return afterTax; }
        public long getTax() { return tax; }
        public double getRate() { return rate; }
        public boolean isExempt() { return exempt; }
    }

    /**
     * 플레이어 세금 정보
     */
    public static class PlayerTaxInfo {
        private final long dailyIncome;
        private final double currentRate;

        public PlayerTaxInfo(long dailyIncome, double currentRate) {
            this.dailyIncome = dailyIncome;
            this.currentRate = currentRate;
        }

        public long getDailyIncome() { return dailyIncome; }
        public double getCurrentRate() { return currentRate; }
    }

    /**
     * [v2] 3시간 간격 세금 계산 결과
     */
    public static class IntervalTaxResult {
        private final long intervalIncome;
        private final long currentBalance;
        private final long earnedTax;
        private final long wealthTax;
        private final long totalTax;
        private final double earnedRate;
        private final double wealthRate;
        private final boolean isVip;
        private final long vipDiscount;
        private final boolean exempt;
        private final ExemptReason exemptReason;

        public IntervalTaxResult(long intervalIncome, long currentBalance, 
                                 long earnedTax, long wealthTax, long totalTax,
                                 double earnedRate, double wealthRate,
                                 boolean isVip, long vipDiscount) {
            this.intervalIncome = intervalIncome;
            this.currentBalance = currentBalance;
            this.earnedTax = earnedTax;
            this.wealthTax = wealthTax;
            this.totalTax = totalTax;
            this.earnedRate = earnedRate;
            this.wealthRate = wealthRate;
            this.isVip = isVip;
            this.vipDiscount = vipDiscount;
            this.exempt = false;
            this.exemptReason = null;
        }

        private IntervalTaxResult(ExemptReason reason) {
            this.intervalIncome = 0;
            this.currentBalance = 0;
            this.earnedTax = 0;
            this.wealthTax = 0;
            this.totalTax = 0;
            this.earnedRate = 0;
            this.wealthRate = 0;
            this.isVip = false;
            this.vipDiscount = 0;
            this.exempt = true;
            this.exemptReason = reason;
        }

        public static IntervalTaxResult empty() {
            return new IntervalTaxResult(ExemptReason.NO_DATA);
        }

        public static IntervalTaxResult disabled() {
            return new IntervalTaxResult(ExemptReason.DISABLED);
        }

        public static IntervalTaxResult exemptInactive() {
            return new IntervalTaxResult(ExemptReason.INACTIVE);
        }

        // Getters
        public long getIntervalIncome() { return intervalIncome; }
        public long getCurrentBalance() { return currentBalance; }
        public long getEarnedTax() { return earnedTax; }
        public long getWealthTax() { return wealthTax; }
        public long getTotalTax() { return totalTax; }
        public double getEarnedRate() { return earnedRate; }
        public double getWealthRate() { return wealthRate; }
        public boolean isVip() { return isVip; }
        public long getVipDiscount() { return vipDiscount; }
        public boolean isExempt() { return exempt; }
        public ExemptReason getExemptReason() { return exemptReason; }

        public enum ExemptReason {
            NO_DATA,
            DISABLED,
            INACTIVE
        }
    }
}
