package kr.bapuri.tycoon.economy;

import kr.bapuri.tycoon.admin.AdminPrivilege;
import kr.bapuri.tycoon.admin.AdminService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * EconomyService - 경제 시스템 핵심 서비스
 * 
 * 두 가지 화폐 지원:
 * - BD (기본 화폐): 상점, 직업 보상, 일반 게임플레이
 * - BottCoin (특수 화폐): 도감 완성, 업적 보상
 * 
 * [Phase 3.A] 리팩토링된 구조:
 * - EconomyLogger로 로깅 분리
 * - 채무(garnish) 시스템 제거
 * - 모드 UI 알림 제거 (Phase 5+)
 * - 메트릭스 연동 준비 (Phase 3.B)
 * 
 * Idempotency 지원:
 * - txnId 기반 중복 트랜잭션 방지
 * - 던전 보상, 퀘스트 완료 등 중요 작업에 사용
 */
public class EconomyService {

    private final PlayerDataManager dataManager;
    private final AdminService adminService;
    private final EconomyLogger logger;
    
    // [Phase 8] 경제 변동 콜백 (모드 연동용)
    private Consumer<UUID> economyChangeCallback;
    
    // [세금 시스템] 소득세 서비스 (지연 초기화)
    private kr.bapuri.tycoon.tax.IncomeTaxService incomeTaxService;
    
    /**
     * [슈퍼관리자 무한 돈] 표시 금액
     * - 슈퍼관리자의 잔액 조회 시 항상 이 값 반환
     * - 출금 시 잔액 변동 없음 (취소된 것처럼 동작)
     * - int 최대값(2,147,483,647)보다 약간 작게 설정 (오버플로우 방지)
     */
    private static final long INFINITE_MONEY_DISPLAY = 2_100_000_000L;

    public EconomyService(PlayerDataManager dataManager, AdminService adminService) {
        this.dataManager = dataManager;
        this.adminService = adminService;
        this.logger = new EconomyLogger();
        
        logger.info("[EconomyService] 초기화 완료");
    }
    
    // ================================================================================
    // [Phase 8] 경제 변동 콜백 (모드 연동)
    // ================================================================================
    
    /**
     * 경제 변동 콜백 설정 (ModDataService에서 호출)
     * 
     * @param callback UUID를 받아 해당 플레이어에게 ECONOMY_UPDATE 전송
     */
    public void setEconomyChangeCallback(Consumer<UUID> callback) {
        this.economyChangeCallback = callback;
        logger.info("[EconomyService] 경제 변동 콜백 등록됨 (모드 연동)");
    }
    
    /**
     * 경제 변동 알림 (콜백 호출)
     */
    private void notifyEconomyChange(UUID uuid) {
        if (economyChangeCallback != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                economyChangeCallback.accept(uuid);
            }
        }
    }

    // ================================================================================
    // BD (기본 화폐) 메서드
    // ================================================================================

    // ========== BD 조회 ==========

    public long getMoney(Player player) {
        return getMoney(player.getUniqueId());
    }

    public long getMoney(UUID uuid) {
        // 슈퍼관리자 무한 돈: 항상 고정 금액 표시
        if (adminService.hasPrivilege(uuid, AdminPrivilege.INFINITE_MONEY)) {
            return INFINITE_MONEY_DISPLAY;
        }
        return dataManager.get(uuid).getMoney();
    }
    
    public long getBalance(Player player) {
        return getMoney(player);
    }

    public long getBalance(UUID uuid) {
        return getMoney(uuid);
    }
    
    /**
     * 실제 잔액 조회 (관리자 특권 무시)
     * 관리자 명령어나 디버깅 용도
     */
    public long getActualBalance(UUID uuid) {
        return dataManager.get(uuid).getMoney();
    }
    
    public long getActualBalance(Player player) {
        return getActualBalance(player.getUniqueId());
    }

    // ========== BD 입금 ==========

    public void deposit(UUID uuid, long amount) {
        deposit(uuid, amount, null, null);
    }
    
    public void deposit(UUID uuid, long amount, String reason, String source) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getMoney();
        data.addMoney(amount);
        long after = data.getMoney();
        
        // [v2] 3시간 간격 소득 누적 (소득세 계산용)
        data.addIntervalIncome(amount);
        
        logger.logTransaction(uuid, data.getPlayerName(), CurrencyType.BD, 
                "DEPOSIT", before, after, reason, source);
        
        // [Phase 8] 모드에 경제 변동 알림
        notifyEconomyChange(uuid);
    }

    public void deposit(Player player, long amount) {
        deposit(player.getUniqueId(), amount, null, null);
    }
    
    public void deposit(Player player, long amount, String reason, String source) {
        deposit(player.getUniqueId(), amount, reason, source);
    }

    // ========== [세금 시스템] 소득세 적용 입금 ==========

    /**
     * 소득세 서비스 설정 (지연 초기화)
     */
    public void setIncomeTaxService(kr.bapuri.tycoon.tax.IncomeTaxService service) {
        this.incomeTaxService = service;
    }

    /**
     * 소득세가 적용되는 BD 입금
     * 
     * @param player 플레이어
     * @param amount 원래 금액
     * @param reason 사유
     * @param source 호출원
     * @param isAdminGiven 관리자 지급 여부 (true면 세금 면제)
     * @param giverUuid 지급자 UUID (관리자 지급 판정용, null이면 시스템 지급)
     * @return 실제 지급된 금액 (세금 차감 후)
     */
    public long depositWithTax(Player player, long amount, String reason, String source, 
                               boolean isAdminGiven, UUID giverUuid) {
        if (incomeTaxService == null || isAdminGiven) {
            // 세금 서비스 없거나 관리자 지급이면 그냥 입금
            deposit(player.getUniqueId(), amount, reason, source);
            return amount;
        }

        // 소득세 계산
        kr.bapuri.tycoon.tax.IncomeTaxService.TaxResult result = 
            incomeTaxService.calculateTax(player, amount, false, giverUuid);

        if (result.isExempt() || result.getTax() <= 0) {
            // 면제 또는 세금 없음
            deposit(player.getUniqueId(), amount, reason, source);
            return amount;
        }

        // 세금 차감 후 입금
        long afterTax = result.getAfterTax();
        deposit(player.getUniqueId(), afterTax, reason + " (세금 " + result.getTax() + " 차감)", source);

        // 세금 메시지 전송
        String taxMessage = incomeTaxService.formatTaxMessage(result);
        if (taxMessage != null) {
            player.sendMessage(taxMessage);
        }

        return afterTax;
    }

    /**
     * 소득세가 적용되는 BD 입금 (간편 버전)
     */
    public long depositWithTax(Player player, long amount, String reason, String source) {
        return depositWithTax(player, amount, reason, source, false, null);
    }

    // ========== BD 출금 ==========

    public boolean withdraw(UUID uuid, long amount) {
        return withdraw(uuid, amount, null, null);
    }
    
    public boolean withdraw(UUID uuid, long amount, String reason, String source) {
        // 슈퍼관리자 무한 돈
        if (adminService.hasPrivilege(uuid, AdminPrivilege.INFINITE_MONEY)) {
            logger.logTransaction(uuid, getPlayerName(uuid), CurrencyType.BD, 
                    "WITHDRAW_ADMIN_BYPASS", 0, 0, reason, source);
            return true;
        }

        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getMoney();
        boolean success = data.removeMoney(amount);
        
        if (success) {
            long after = data.getMoney();
            logger.logTransaction(uuid, data.getPlayerName(), CurrencyType.BD, 
                    "WITHDRAW", before, after, reason, source);
            
            // [Phase 8] 모드에 경제 변동 알림
            notifyEconomyChange(uuid);
        } else {
            logger.logInsufficientFunds(uuid, data.getPlayerName(), CurrencyType.BD, 
                    amount, before, reason, source);
        }
        return success;
    }

    public boolean withdraw(Player player, long amount) {
        return withdraw(player.getUniqueId(), amount, null, null);
    }
    
    public boolean withdraw(Player player, long amount, String reason, String source) {
        return withdraw(player.getUniqueId(), amount, reason, source);
    }

    // ========== BD 설정 ==========

    public void setMoney(UUID uuid, long money) {
        setMoney(uuid, money, null, null);
    }
    
    public void setMoney(UUID uuid, long money, String reason, String source) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getMoney();
        data.setMoney(money);
        
        logger.logAdminAction(uuid, data.getPlayerName(), CurrencyType.BD, 
                "SET_BALANCE", before, money, reason);
        
        // [Phase 8] 모드에 경제 변동 알림
        notifyEconomyChange(uuid);
    }

    public void setMoney(Player player, long money) {
        setMoney(player.getUniqueId(), money, null, null);
    }

    // ========== BD 잔액 확인 ==========

    public boolean hasBalance(Player player, long amount) {
        return hasBalance(player.getUniqueId(), amount);
    }

    public boolean hasBalance(UUID uuid, long amount) {
        // 슈퍼관리자 무한 돈: 항상 잔액 있음
        if (adminService.hasPrivilege(uuid, AdminPrivilege.INFINITE_MONEY)) {
            return true;
        }
        return dataManager.get(uuid).hasMoney(amount);
    }

    // ================================================================================
    // BottCoin (특수 화폐) 메서드
    // ================================================================================

    // ========== BottCoin 조회 ==========

    public long getBottCoin(Player player) {
        return getBottCoin(player.getUniqueId());
    }

    public long getBottCoin(UUID uuid) {
        // 슈퍼관리자 무한 돈: 항상 고정 금액 표시
        if (adminService.hasPrivilege(uuid, AdminPrivilege.INFINITE_MONEY)) {
            return INFINITE_MONEY_DISPLAY;
        }
        return dataManager.get(uuid).getBottCoin();
    }
    
    /**
     * 실제 BottCoin 조회 (관리자 특권 무시)
     */
    public long getActualBottCoin(UUID uuid) {
        return dataManager.get(uuid).getBottCoin();
    }

    // ========== BottCoin 입금 ==========

    public void depositBottCoin(UUID uuid, long amount) {
        depositBottCoin(uuid, amount, null, null);
    }
    
    public void depositBottCoin(UUID uuid, long amount, String reason, String source) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getBottCoin();
        data.addBottCoin(amount);
        long after = data.getBottCoin();
        
        logger.logTransaction(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                "DEPOSIT", before, after, reason, source);
        
        // [Phase 8] 모드에 경제 변동 알림
        notifyEconomyChange(uuid);
    }

    public void depositBottCoin(Player player, long amount) {
        depositBottCoin(player.getUniqueId(), amount, null, null);
    }
    
    public void depositBottCoin(Player player, long amount, String reason, String source) {
        depositBottCoin(player.getUniqueId(), amount, reason, source);
    }

    // ========== BottCoin 출금 ==========

    public boolean withdrawBottCoin(UUID uuid, long amount) {
        return withdrawBottCoin(uuid, amount, null, null);
    }
    
    public boolean withdrawBottCoin(UUID uuid, long amount, String reason, String source) {
        // 슈퍼관리자 무한 돈
        if (adminService.hasPrivilege(uuid, AdminPrivilege.INFINITE_MONEY)) {
            logger.logTransaction(uuid, getPlayerName(uuid), CurrencyType.BOTTCOIN, 
                    "WITHDRAW_ADMIN_BYPASS", 0, 0, reason, source);
            return true;
        }

        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getBottCoin();
        boolean success = data.removeBottCoin(amount);
        
        if (success) {
            long after = data.getBottCoin();
            logger.logTransaction(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                    "WITHDRAW", before, after, reason, source);
            
            // [Phase 8] 모드에 경제 변동 알림
            notifyEconomyChange(uuid);
        } else {
            logger.logInsufficientFunds(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                    amount, before, reason, source);
        }
        return success;
    }

    public boolean withdrawBottCoin(Player player, long amount) {
        return withdrawBottCoin(player.getUniqueId(), amount, null, null);
    }
    
    public boolean withdrawBottCoin(Player player, long amount, String reason, String source) {
        return withdrawBottCoin(player.getUniqueId(), amount, reason, source);
    }

    // ========== BottCoin 설정 ==========

    public void setBottCoin(UUID uuid, long bottCoin) {
        setBottCoin(uuid, bottCoin, null, null);
    }
    
    public void setBottCoin(UUID uuid, long bottCoin, String reason, String source) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getBottCoin();
        data.setBottCoin(bottCoin);
        
        logger.logAdminAction(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                "SET_BALANCE", before, bottCoin, reason);
        
        // [Phase 8] 모드에 경제 변동 알림
        notifyEconomyChange(uuid);
    }

    public void setBottCoin(Player player, long bottCoin) {
        setBottCoin(player.getUniqueId(), bottCoin, null, null);
    }

    // ========== BottCoin 잔액 확인 ==========

    public boolean hasBottCoin(Player player, long amount) {
        return hasBottCoin(player.getUniqueId(), amount);
    }

    public boolean hasBottCoin(UUID uuid, long amount) {
        // 슈퍼관리자 무한 돈: 항상 잔액 있음
        if (adminService.hasPrivilege(uuid, AdminPrivilege.INFINITE_MONEY)) {
            return true;
        }
        return dataManager.get(uuid).hasBottCoin(amount);
    }

    // ================================================================================
    // Idempotent 메서드 (txnId 기반 중복 방지)
    // ================================================================================

    /**
     * txnId 기반 idempotent BD 입금
     * 
     * @param uuid 플레이어 UUID
     * @param amount 입금 금액
     * @param txnId 트랜잭션 ID (null이면 idempotency 체크 안함)
     * @param reason 사유
     * @param source 호출원
     * @return true if applied, false if skipped (duplicate txnId)
     */
    public boolean depositIdempotent(UUID uuid, long amount, String txnId, String reason, String source) {
        PlayerTycoonData data = dataManager.get(uuid);
        
        // 중복 트랜잭션 체크
        if (txnId != null && !txnId.isEmpty() && data.isTxnProcessed(txnId)) {
            logger.logDuplicate(txnId, uuid, amount, CurrencyType.BD, reason);
            return false;
        }
        
        long before = data.getMoney();
        data.addMoney(amount);
        
        // txnId 기록
        if (txnId != null && !txnId.isEmpty()) {
            data.addTxnId(txnId);
        }
        
        long after = data.getMoney();
        logger.logTransaction(uuid, data.getPlayerName(), CurrencyType.BD, 
                "DEPOSIT", before, after, reason, source);
        
        // 중요 트랜잭션은 즉시 저장
        if (txnId != null && !txnId.isEmpty()) {
            dataManager.save(uuid);
        }
        
        return true;
    }
    
    /**
     * txnId 기반 idempotent BD 출금
     */
    public boolean withdrawIdempotent(UUID uuid, long amount, String txnId, String reason, String source) {
        // 슈퍼관리자 무한 돈
        if (adminService.hasPrivilege(uuid, AdminPrivilege.INFINITE_MONEY)) {
            logger.logTransaction(uuid, getPlayerName(uuid), CurrencyType.BD, 
                    "WITHDRAW_ADMIN_BYPASS", 0, 0, reason, source);
            return true;
        }
        
        PlayerTycoonData data = dataManager.get(uuid);
        
        // 중복 트랜잭션 체크
        if (txnId != null && !txnId.isEmpty() && data.isTxnProcessed(txnId)) {
            logger.logDuplicate(txnId, uuid, amount, CurrencyType.BD, reason);
            return false;
        }
        
        long before = data.getMoney();
        boolean success = data.removeMoney(amount);
        
        if (success) {
            // txnId 기록
            if (txnId != null && !txnId.isEmpty()) {
                data.addTxnId(txnId);
            }
            
            long after = data.getMoney();
            logger.logTransaction(uuid, data.getPlayerName(), CurrencyType.BD, 
                    "WITHDRAW", before, after, reason, source);
            
            // 중요 트랜잭션은 즉시 저장
            if (txnId != null && !txnId.isEmpty()) {
                dataManager.save(uuid);
            }
        } else {
            logger.logInsufficientFunds(uuid, data.getPlayerName(), CurrencyType.BD, 
                    amount, before, reason, source);
        }
        return success;
    }
    
    /**
     * txnId 기반 idempotent BottCoin 입금
     */
    public boolean depositBottCoinIdempotent(UUID uuid, long amount, String txnId, String reason, String source) {
        PlayerTycoonData data = dataManager.get(uuid);
        
        // 중복 트랜잭션 체크
        if (txnId != null && !txnId.isEmpty() && data.isTxnProcessed(txnId)) {
            logger.logDuplicate(txnId, uuid, amount, CurrencyType.BOTTCOIN, reason);
            return false;
        }
        
        long before = data.getBottCoin();
        data.addBottCoin(amount);
        
        // txnId 기록
        if (txnId != null && !txnId.isEmpty()) {
            data.addTxnId(txnId);
        }
        
        long after = data.getBottCoin();
        logger.logTransaction(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                "DEPOSIT", before, after, reason, source);
        
        // 중요 트랜잭션은 즉시 저장
        if (txnId != null && !txnId.isEmpty()) {
            dataManager.save(uuid);
        }
        
        return true;
    }
    
    /**
     * txnId 기반 idempotent BottCoin 출금
     */
    public boolean withdrawBottCoinIdempotent(UUID uuid, long amount, String txnId, String reason, String source) {
        // 슈퍼관리자 무한 돈
        if (adminService.hasPrivilege(uuid, AdminPrivilege.INFINITE_MONEY)) {
            logger.logTransaction(uuid, getPlayerName(uuid), CurrencyType.BOTTCOIN, 
                    "WITHDRAW_ADMIN_BYPASS", 0, 0, reason, source);
            return true;
        }
        
        PlayerTycoonData data = dataManager.get(uuid);
        
        // 중복 트랜잭션 체크
        if (txnId != null && !txnId.isEmpty() && data.isTxnProcessed(txnId)) {
            logger.logDuplicate(txnId, uuid, amount, CurrencyType.BOTTCOIN, reason);
            return false;
        }
        
        long before = data.getBottCoin();
        boolean success = data.removeBottCoin(amount);
        
        if (success) {
            // txnId 기록
            if (txnId != null && !txnId.isEmpty()) {
                data.addTxnId(txnId);
            }
            
            long after = data.getBottCoin();
            logger.logTransaction(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                    "WITHDRAW", before, after, reason, source);
            
            // 중요 트랜잭션은 즉시 저장
            if (txnId != null && !txnId.isEmpty()) {
                dataManager.save(uuid);
            }
        } else {
            logger.logInsufficientFunds(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                    amount, before, reason, source);
        }
        return success;
    }

    // ================================================================================
    // 제네릭 화폐 메서드 (CurrencyType 기반)
    // ================================================================================

    public long getBalance(UUID uuid, CurrencyType currency) {
        return switch (currency) {
            case BD -> getMoney(uuid);
            case BOTTCOIN -> getBottCoin(uuid);
        };
    }

    public long getBalance(Player player, CurrencyType currency) {
        return getBalance(player.getUniqueId(), currency);
    }

    public void deposit(UUID uuid, CurrencyType currency, long amount, String reason, String source) {
        switch (currency) {
            case BD -> deposit(uuid, amount, reason, source);
            case BOTTCOIN -> depositBottCoin(uuid, amount, reason, source);
        }
    }

    public boolean withdraw(UUID uuid, CurrencyType currency, long amount, String reason, String source) {
        return switch (currency) {
            case BD -> withdraw(uuid, amount, reason, source);
            case BOTTCOIN -> withdrawBottCoin(uuid, amount, reason, source);
        };
    }

    public boolean hasBalance(UUID uuid, CurrencyType currency, long amount) {
        return switch (currency) {
            case BD -> hasBalance(uuid, amount);
            case BOTTCOIN -> hasBottCoin(uuid, amount);
        };
    }

    // ================================================================================
    // 관리자 전용 메서드 (메트릭스 제외)
    // ================================================================================

    /**
     * 관리자 전용 BD 입금 (메트릭스 제외)
     */
    public void depositAdmin(UUID uuid, long amount, String reason) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getMoney();
        data.addMoney(amount);
        long after = data.getMoney();
        
        logger.logAdminAction(uuid, data.getPlayerName(), CurrencyType.BD, 
                "DEPOSIT", before, after, reason);
    }
    
    /**
     * 관리자 전용 BD 출금 (메트릭스 제외, 잔액 체크 선택)
     */
    public boolean withdrawAdmin(UUID uuid, long amount, String reason, boolean checkBalance) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getMoney();
        
        if (checkBalance && before < amount) {
            logger.logInsufficientFunds(uuid, data.getPlayerName(), CurrencyType.BD, 
                    amount, before, reason, "ADMIN");
            return false;
        }
        
        long newBalance = Math.max(0, before - amount);
        data.setMoney(newBalance);
        long after = data.getMoney();
        
        logger.logAdminAction(uuid, data.getPlayerName(), CurrencyType.BD, 
                "WITHDRAW", before, after, reason);
        
        return true;
    }
    
    /**
     * 관리자 전용 BD 설정 (메트릭스 제외)
     */
    public void setMoneyAdmin(UUID uuid, long money, String reason) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getMoney();
        data.setMoney(money);
        
        logger.logAdminAction(uuid, data.getPlayerName(), CurrencyType.BD, 
                "SET", before, money, reason);
    }
    
    /**
     * 관리자 전용 BottCoin 입금
     */
    public void depositBottCoinAdmin(UUID uuid, long amount, String reason) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getBottCoin();
        data.addBottCoin(amount);
        long after = data.getBottCoin();
        
        logger.logAdminAction(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                "DEPOSIT", before, after, reason);
    }
    
    /**
     * 관리자 전용 BottCoin 출금 (메트릭스 제외, 잔액 체크 선택)
     */
    public boolean withdrawBottCoinAdmin(UUID uuid, long amount, String reason, boolean checkBalance) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getBottCoin();
        
        if (checkBalance && before < amount) {
            logger.logInsufficientFunds(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                    amount, before, reason, "ADMIN");
            return false;
        }
        
        long newBalance = Math.max(0, before - amount);
        data.setBottCoin(newBalance);
        long after = data.getBottCoin();
        
        logger.logAdminAction(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                "WITHDRAW", before, after, reason);
        
        return true;
    }
    
    /**
     * 관리자 전용 BottCoin 설정
     */
    public void setBottCoinAdmin(UUID uuid, long bottCoin, String reason) {
        PlayerTycoonData data = dataManager.get(uuid);
        long before = data.getBottCoin();
        data.setBottCoin(bottCoin);
        
        logger.logAdminAction(uuid, data.getPlayerName(), CurrencyType.BOTTCOIN, 
                "SET", before, bottCoin, reason);
    }

    // ================================================================================
    // 유틸리티
    // ================================================================================
    
    /**
     * 플레이어 데이터 즉시 저장
     * 오프라인 플레이어 수정 시 호출 필요
     */
    public void savePlayer(UUID uuid) {
        dataManager.save(uuid);
    }
    
    private String getPlayerName(UUID uuid) {
        PlayerTycoonData data = dataManager.get(uuid);
        return data != null ? data.getPlayerName() : "Unknown";
    }
    
    /**
     * 표시용 잔액 문자열 반환
     */
    public String getBalanceDisplay(UUID uuid) {
        long actual = getActualBalance(uuid);
        if (adminService.hasPrivilege(uuid, AdminPrivilege.INFINITE_MONEY)) {
            return String.format("%,d BD (무한)", actual);
        }
        return String.format("%,d BD", actual);
    }
    
    public String getBalanceDisplay(Player player) {
        return getBalanceDisplay(player.getUniqueId());
    }
    
    // ================================================================================
    // [Phase 3.B] 메트릭스 연동 준비
    // ================================================================================
    
    // TODO: Phase 3.B에서 구현
    // private EconomyMetrics metrics;
    // 
    // public void setMetrics(EconomyMetrics metrics) {
    //     this.metrics = metrics;
    // }
    // 
    // public double getSellMultiplier() {
    //     return metrics != null ? metrics.getSellMultiplier() : 1.0;
    // }
    // 
    // public double getSinkMultiplier() {
    //     return metrics != null ? metrics.getSinkMultiplier() : 1.0;
    // }
}
