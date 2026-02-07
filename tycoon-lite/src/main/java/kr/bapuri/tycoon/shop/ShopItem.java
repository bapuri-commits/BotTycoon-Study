package kr.bapuri.tycoon.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 상점 아이템 정보를 담는 데이터 클래스
 * 
 * [Phase 3.B] 설정 파일 기반 품목/가격 관리
 * - shops.yml에서 로드된 아이템 정보
 * - 동적 가격 적용 여부
 * 
 * <h2>가격 규칙</h2>
 * <ul>
 *   <li>buyPrice = -1: 구매 불가 (판매 전용)</li>
 *   <li>sellPrice = -1: 판매 불가 (구매 전용)</li>
 *   <li>둘 다 양수: 차익거래 방지로 sell < buy 보장</li>
 * </ul>
 */
public class ShopItem {
    
    /** 거래 불가를 나타내는 특수 가격 */
    public static final long PRICE_DISABLED = -1L;
    
    private final Material material;
    private final long baseBuyPrice;   // 기본 구매가 (플레이어가 지불), -1이면 구매 불가
    private final long baseSellPrice;  // 기본 판매가 (플레이어가 받음), -1이면 판매 불가
    private final boolean dynamicPrice; // 동적 가격 적용 여부
    
    public ShopItem(Material material, long baseBuyPrice, long baseSellPrice, boolean dynamicPrice) {
        this.material = material;
        this.baseBuyPrice = baseBuyPrice;
        this.baseSellPrice = baseSellPrice;
        this.dynamicPrice = dynamicPrice;
        
        // 차익거래 방지: 둘 다 양수일 때만 sell < buy 검증
        if (baseBuyPrice > 0 && baseSellPrice > 0 && baseSellPrice >= baseBuyPrice) {
            throw new IllegalArgumentException(
                "ShopItem 차익거래 방지 위반: sell(" + baseSellPrice + ") >= buy(" + baseBuyPrice + ") for " + material);
        }
    }
    
    /**
     * 차익거래 방지를 적용한 ShopItem 생성
     * 
     * <ul>
     *   <li>buyPrice = -1: 판매 전용 아이템 (sellPrice 그대로 사용)</li>
     *   <li>sellPrice = -1: 구매 전용 아이템 (buyPrice 그대로 사용)</li>
     *   <li>둘 다 양수이고 sell >= buy: sell = buy * 0.5로 자동 조정</li>
     * </ul>
     */
    public static ShopItem createSafe(Material material, long buyPrice, long sellPrice, boolean dynamicPrice) {
        // 판매 전용 (구매 불가)
        if (buyPrice <= 0 && sellPrice > 0) {
            return new ShopItem(material, PRICE_DISABLED, sellPrice, dynamicPrice);
        }
        
        // 구매 전용 (판매 불가)
        if (sellPrice <= 0 && buyPrice > 0) {
            return new ShopItem(material, buyPrice, PRICE_DISABLED, dynamicPrice);
        }
        
        // 둘 다 양수: 차익거래 방지
        if (buyPrice > 0 && sellPrice > 0) {
            if (sellPrice >= buyPrice) {
                sellPrice = (long)(buyPrice * 0.5);
            }
            // 최소값 보장
            if (sellPrice < 1) sellPrice = 1;
        }
        
        // 둘 다 음수/0이면 최소값으로
        if (buyPrice <= 0) buyPrice = 1;
        if (sellPrice <= 0) sellPrice = 1;
        
        return new ShopItem(material, buyPrice, sellPrice, dynamicPrice);
    }
    
    public boolean canBuy() {
        return baseBuyPrice > 0;
    }
    
    public boolean canSell() {
        return baseSellPrice > 0;
    }
    
    public Material getMaterial() {
        return material;
    }
    
    public long getBaseBuyPrice() {
        return baseBuyPrice;
    }
    
    public long getBaseSellPrice() {
        return baseSellPrice;
    }
    
    public boolean isDynamicPrice() {
        return dynamicPrice;
    }
    
    /**
     * ItemStack과 매칭되는지 확인
     */
    public boolean matches(ItemStack item) {
        if (item == null) return false;
        return item.getType() == material;
    }
    
    @Override
    public String toString() {
        return "ShopItem{" + material + ", buy=" + baseBuyPrice + ", sell=" + baseSellPrice + 
               ", dynamic=" + dynamicPrice + "}";
    }
}
