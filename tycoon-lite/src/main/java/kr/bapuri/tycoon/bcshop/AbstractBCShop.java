package kr.bapuri.tycoon.bcshop;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * BC 상점 추상 클래스
 * 
 * BC(BottCoin)를 사용하는 모든 상점의 기반 클래스입니다.
 * 기존 AbstractShop 패턴을 참고하되, BC 화폐 전용으로 단순화했습니다.
 * 
 * <h2>상속 구조</h2>
 * <pre>
 * AbstractBCShop
 * ├── TitleShop (구매형 칭호)
 * └── CosmeticShop (채팅 색상, 파티클, 발광)
 * </pre>
 * 
 * <h2>특징</h2>
 * <ul>
 *   <li>고정 가격 (동적 가격 없음)</li>
 *   <li>BC 화폐만 사용</li>
 *   <li>Chest GUI 지원</li>
 * </ul>
 */
public abstract class AbstractBCShop {
    
    protected final Plugin plugin;
    protected final EconomyService economyService;
    protected final PlayerDataManager dataManager;
    protected final String shopId;
    protected final String displayName;
    protected final Logger logger;
    
    protected final List<BCShopItem> items = new ArrayList<>();
    protected boolean enabled = true;
    
    protected AbstractBCShop(Plugin plugin, EconomyService economyService, 
                             PlayerDataManager dataManager, String shopId, String displayName) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.dataManager = dataManager;
        this.shopId = shopId;
        this.displayName = displayName;
        this.logger = plugin.getLogger();
    }
    
    // ========== 기본 정보 ==========
    
    public String getShopId() {
        return shopId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<BCShopItem> getItems() {
        return items;
    }
    
    // ========== BC 구매 처리 ==========
    
    /**
     * BC로 구매 처리 (고정 가격)
     * 
     * @param player 구매자
     * @param price BC 가격
     * @param itemId 아이템 ID (로깅용)
     * @param reason 사유 (로깅용)
     * @return 구매 성공 여부
     */
    protected boolean purchaseWithBC(Player player, long price, String itemId, String reason) {
        if (!economyService.hasBottCoin(player, price)) {
            long currentBC = economyService.getBottCoin(player);
            player.sendMessage(String.format("§cBC가 부족합니다. (보유: %,d BC, 필요: %,d BC)", 
                    currentBC, price));
            return false;
        }
        
        // BC 차감
        economyService.withdrawBottCoin(player, price, reason, shopId);
        
        logger.info(String.format("[BCShop] %s: %s 구매 - %s (%,d BC)", 
                shopId, player.getName(), itemId, price));
        
        return true;
    }
    
    /**
     * 플레이어가 아이템을 이미 보유하고 있는지 확인
     * 
     * @param player 플레이어
     * @param item 확인할 아이템
     * @return 보유 여부
     */
    public abstract boolean hasItem(Player player, BCShopItem item);
    
    /**
     * 플레이어가 아이템을 활성화했는지 확인
     * 
     * @param player 플레이어
     * @param item 확인할 아이템
     * @return 활성화 여부
     */
    public abstract boolean isItemActive(Player player, BCShopItem item);
    
    /**
     * 아이템 구매 처리
     * 
     * @param player 구매자
     * @param item 구매할 아이템
     */
    public abstract void handlePurchase(Player player, BCShopItem item);
    
    /**
     * 아이템 활성화/비활성화 토글
     * 
     * @param player 플레이어
     * @param item 토글할 아이템
     */
    public abstract void handleToggle(Player player, BCShopItem item);
    
    /**
     * 설정 리로드
     */
    public abstract void reload();
}
