package kr.bapuri.tycoon.shop;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 상점 인터페이스
 * 
 * [Phase 3.B] 모든 상점이 구현해야 하는 기본 인터페이스
 * 
 * <h2>상속 체인</h2>
 * <pre>
 * IShop (인터페이스)
 *   └── AbstractShop (추상 클래스, 공통 로직)
 *         └── JobShop (직업 상점 공통)
 *               ├── MinerShop
 *               ├── FarmerShop
 *               └── FisherShop
 * </pre>
 * 
 * <h2>새 상점 추가 시</h2>
 * <ul>
 *   <li>기존 유형 변형: 해당 패키지에 추가 (예: shop/job/에 새 직업 상점)</li>
 *   <li>새로운 유형: 새 패키지 생성 (예: shop/special/)</li>
 *   <li>IShop 구현 또는 AbstractShop 상속</li>
 * </ul>
 * 
 * @see AbstractShop
 * @see kr.bapuri.tycoon.shop.job.JobShop
 */
public interface IShop {
    
    /**
     * 상점 고유 ID
     * @return 소문자 영문 ID (예: "miner", "farmer")
     */
    String getShopId();
    
    /**
     * 상점 표시 이름
     * @return 플레이어에게 보여줄 이름 (예: "광부 상점")
     */
    String getDisplayName();
    
    /**
     * 상점 활성화 여부
     * @return true면 사용 가능
     */
    boolean isEnabled();
    
    /**
     * 아이템 구매가 조회 (플레이어가 지불하는 금액)
     * 
     * @param item 구매할 아이템
     * @return 가격 (BD), -1이면 구매 불가
     */
    long getBuyPrice(ItemStack item);
    
    /**
     * 아이템 판매가 조회 (플레이어가 받는 금액)
     * 
     * @param item 판매할 아이템
     * @return 가격 (BD), -1이면 판매 불가
     */
    long getSellPrice(ItemStack item);
    
    /**
     * 플레이어별 아이템 판매가 조회 (레벨 보너스 적용)
     * 
     * @param player 판매자 (레벨 보너스 계산용)
     * @param item 판매할 아이템
     * @return 가격 (BD), -1이면 판매 불가
     */
    default long getSellPrice(Player player, ItemStack item) {
        return getSellPrice(item);  // 기본 구현: 레벨 보너스 없음
    }
    
    /**
     * 플레이어별 아이템 구매가 조회 (레벨 보너스 적용)
     * 
     * <p>직업 레벨에 따라 구매가가 증가합니다 (판매가와 동일한 비율)</p>
     * 
     * @param player 구매자 (레벨 보너스 계산용)
     * @param item 구매할 아이템
     * @return 가격 (BD), -1이면 구매 불가
     */
    default long getBuyPrice(Player player, ItemStack item) {
        return getBuyPrice(item);  // 기본 구현: 레벨 보너스 없음
    }
    
    /**
     * 구매 가능 여부 확인
     * 
     * @param player 구매자
     * @param item 구매할 아이템
     * @return true면 구매 가능
     */
    boolean canBuy(Player player, ItemStack item);
    
    /**
     * 판매 가능 여부 확인
     * 
     * @param player 판매자
     * @param item 판매할 아이템
     * @return true면 판매 가능
     */
    boolean canSell(Player player, ItemStack item);
    
    /**
     * 상점에서 취급하는 아이템 목록
     * 
     * @return ShopItem 리스트 (GUI 표시용)
     */
    List<ShopItem> getItems();
    
    /**
     * 상점 데이터 저장
     */
    void save();
    
    /**
     * 상점 데이터 로드
     */
    void load();
}
