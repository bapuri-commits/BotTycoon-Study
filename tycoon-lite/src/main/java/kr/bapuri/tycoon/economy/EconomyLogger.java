package kr.bapuri.tycoon.economy;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * EconomyLogger - 경제 트랜잭션 로깅 전용 클래스
 * 
 * 책임:
 * - 모든 경제 트랜잭션 로깅
 * - 관리자 작업 별도 로깅 (WARNING 레벨)
 * - 중복 트랜잭션 로깅
 * 
 * 로그 형식:
 * - [ECONOMY] player=<name>(<uuid>) currency=<BD|BOTTCOIN> action=<DEPOSIT|WITHDRAW|SET> 
 *   before=<이전> after=<이후> delta=<변화량> reason=<사유> source=<호출원>
 */
public class EconomyLogger {

    private final Logger logger;
    
    public EconomyLogger() {
        this.logger = Logger.getLogger("Tycoon.Economy");
    }
    
    /**
     * 일반 트랜잭션 로깅
     */
    public void logTransaction(UUID uuid, String playerName, CurrencyType currency, 
            String action, long before, long after, String reason, String source) {
        long delta = after - before;
        logger.info(String.format("[ECONOMY] player=%s(%s) currency=%s action=%s before=%d after=%d delta=%+d reason=%s source=%s",
                playerName, uuid.toString().substring(0, 8), currency.name(), action,
                before, after, delta, 
                reason != null ? reason : "N/A", 
                source != null ? source : "UNKNOWN"));
    }
    
    /**
     * 관리자 트랜잭션 로깅 (WARNING 레벨)
     */
    public void logAdminAction(UUID uuid, String playerName, CurrencyType currency,
            String action, long before, long after, String reason) {
        long delta = after - before;
        logger.warning(String.format("[ECONOMY][ADMIN] %s player=%s(%s) currency=%s before=%d after=%d delta=%+d reason=%s",
                action, playerName, uuid.toString().substring(0, 8), currency.name(),
                before, after, delta,
                reason != null ? reason : "N/A"));
    }
    
    /**
     * 중복 트랜잭션 스킵 로깅
     */
    public void logDuplicate(String txnId, UUID uuid, long amount, CurrencyType currency, String reason) {
        logger.info(String.format("[ECONOMY] SKIPPED_DUPLICATE txnId=%s player=%s currency=%s amount=%d reason=%s",
                txnId, uuid.toString().substring(0, 8), currency.name(), amount, 
                reason != null ? reason : "N/A"));
    }
    
    /**
     * 잔액 부족 실패 로깅
     */
    public void logInsufficientFunds(UUID uuid, String playerName, CurrencyType currency,
            long requested, long balance, String reason, String source) {
        logger.info(String.format("[ECONOMY] WITHDRAW_FAILED player=%s(%s) currency=%s requested=%d balance=%d reason=%s source=%s",
                playerName, uuid.toString().substring(0, 8), currency.name(), 
                requested, balance,
                reason != null ? reason : "N/A", 
                source != null ? source : "UNKNOWN"));
    }
    
    /**
     * 정보 로깅
     */
    public void info(String message) {
        logger.info(message);
    }
    
    /**
     * 경고 로깅
     */
    public void warning(String message) {
        logger.warning(message);
    }
}
