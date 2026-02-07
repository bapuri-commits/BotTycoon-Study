package kr.bapuri.tycoon.bcshop;

import org.bukkit.Material;

/**
 * BC 상점 아이템 정의
 * 
 * BC(BottCoin)로 구매하는 아이템의 기본 정보를 담습니다.
 * TitleShop, CosmeticShop 등에서 공통으로 사용됩니다.
 */
public class BCShopItem {
    
    private final String id;           // 고유 ID
    private final String name;         // 표시 이름
    private final String description;  // 설명
    private final long price;          // BC 가격 (고정)
    private final Material icon;       // GUI 아이콘
    private final String category;     // 카테고리 (title, chat_color, particle, glow)
    
    public BCShopItem(String id, String name, String description, long price, Material icon, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.icon = icon;
        this.category = category;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public long getPrice() {
        return price;
    }
    
    public Material getIcon() {
        return icon;
    }
    
    public String getCategory() {
        return category;
    }
    
    /**
     * 가격 표시용 문자열
     */
    public String getPriceDisplay() {
        return String.format("%,d BC", price);
    }
}
