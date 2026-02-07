package kr.bapuri.tycoon.title;

/**
 * 구매형 칭호 정의
 * 
 * BC(BottCoin)로 구매할 수 있는 칭호입니다.
 * 업적 칭호(Title)와 분리되어 관리됩니다.
 * 
 * <h2>업적 칭호 vs 구매 칭호</h2>
 * <ul>
 *   <li>업적 칭호: 업적 달성 시 자동 해금, 위엄 있는 느낌</li>
 *   <li>구매 칭호: BC로 구매, 화려하고 예쁜 느낌</li>
 * </ul>
 */
public class PurchasableTitle extends Title {
    
    private final long price;  // BC 가격
    
    public PurchasableTitle(String id, String displayName, String description, 
                            String color, String luckpermsGroup, long price) {
        super(id, displayName, description, color, luckpermsGroup);
        this.price = price;
    }
    
    public long getPrice() {
        return price;
    }
    
    /**
     * 가격 표시용 문자열
     */
    public String getPriceDisplay() {
        return String.format("%,d BC", price);
    }
}
