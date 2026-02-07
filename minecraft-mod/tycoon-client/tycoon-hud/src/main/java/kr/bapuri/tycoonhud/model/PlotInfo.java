package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;

/**
 * 플롯(땅) 정보를 담는 데이터 클래스입니다.
 * 
 * <p>town 월드에서만 표시되는 플롯 정보입니다.</p>
 * <pre>
 * {
 *     "hasOwner": true,
 *     "ownerName": "홍길동",
 *     "purchasable": false,
 *     "price": 0,
 *     "restricted": false
 * }
 * </pre>
 */
public class PlotInfo {
    
    /** 소유자 존재 여부 */
    @SerializedName("hasOwner")
    private boolean hasOwner;
    
    /** 소유자 이름 (소유자가 있을 경우) */
    @SerializedName("ownerName")
    private String ownerName;
    
    /** 구매 가능 여부 */
    @SerializedName("purchasable")
    private boolean purchasable;
    
    /** 가격 (구매 가능할 경우) */
    @SerializedName("price")
    private long price;
    
    /** 보호 구역 여부 (외주 맵 건축물 보호) */
    @SerializedName("restricted")
    private boolean restricted;
    
    // Getters
    
    public boolean hasOwner() {
        return hasOwner;
    }
    
    public String getOwnerName() {
        return ownerName;
    }
    
    public boolean isPurchasable() {
        return purchasable;
    }
    
    public long getPrice() {
        return price;
    }
    
    public boolean isRestricted() {
        return restricted;
    }
    
    /**
     * HUD에 표시할 플롯 정보 문자열을 반환합니다.
     * 
     * @return 플롯 정보 문자열 (예: "홍길동의 땅" 또는 "무주지 (10,000 BD)" 또는 "보호 구역")
     */
    public String getDisplayText() {
        if (hasOwner) {
            return ownerName + "의 땅";
        } else if (restricted) {
            return "§c보호 구역";
        } else if (purchasable) {
            return String.format("무주지 (%,d BD)", price);
        }
        return null; // 표시하지 않음
    }
}

