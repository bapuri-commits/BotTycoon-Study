package kr.bapuri.tycoonui.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Collections;

/**
 * 경제 거래 내역을 담는 클래스입니다.
 * 
 * <p>서버로부터 ECONOMY_HISTORY 패킷으로 수신됩니다.</p>
 */
public class EconomyHistory {
    
    @SerializedName("transactions")
    private List<Transaction> transactions;
    
    public List<Transaction> getTransactions() {
        return transactions != null ? transactions : Collections.emptyList();
    }
    
    /**
     * 개별 거래 내역
     */
    public static class Transaction {
        
        /** Unix timestamp (초) */
        @SerializedName("time")
        private long time;
        
        /** 금액 (양수=수입, 음수=지출) */
        @SerializedName("amount")
        private long amount;
        
        /** 거래 사유 */
        @SerializedName("reason")
        private String reason;
        
        public long getTime() {
            return time;
        }
        
        public long getAmount() {
            return amount;
        }
        
        public String getReason() {
            return reason != null ? reason : "";
        }
    }
}

