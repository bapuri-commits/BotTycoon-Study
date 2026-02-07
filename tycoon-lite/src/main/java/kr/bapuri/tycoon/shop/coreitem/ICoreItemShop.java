package kr.bapuri.tycoon.shop.coreitem;

import kr.bapuri.tycoon.shop.IShop;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 커스텀 아이템 상점 인터페이스
 * 
 * [Phase 3.B] 스켈레톤 - Phase 5+에서 전체 구현 예정
 * 
 * ==========================================
 * 이 인터페이스는 Phase 3.B에서 생성되었으며,
 * CoreItemService와의 연동이 필요한 Phase 5+에서
 * 전체 구현될 예정입니다.
 * ==========================================
 * 
 * <h2>일반 상점과의 차이점</h2>
 * <ul>
 *   <li>바닐라 Material 대신 커스텀 아이템 ID 사용</li>
 *   <li>CoreItemService로 아이템 생성/검증</li>
 *   <li>아이템 진위 여부 확인 (CoreItemAuthenticator)</li>
 *   <li>일부 아이템은 판매 불가 (sell: -1)</li>
 * </ul>
 * 
 * ==========================================
 * 필수 구조 (변경 불가)
 * ==========================================
 * 
 * <h3>1. IShop 상속</h3>
 * <pre>
 * public interface ICoreItemShop extends IShop {
 *     // IShop의 모든 메서드 구현 필요
 * }
 * </pre>
 * 
 * <h3>2. 커스텀 아이템 ID 기반 조회</h3>
 * <pre>
 * // Material 대신 String coreItemId 사용
 * long getBuyPrice(String coreItemId);
 * long getSellPrice(String coreItemId);
 * boolean canBuy(Player player, String coreItemId);
 * boolean canSell(Player player, String coreItemId);
 * </pre>
 * 
 * <h3>3. 아이템 진위 검증</h3>
 * <pre>
 * // 판매 시 CoreItemAuthenticator로 진위 확인 필수
 * boolean isAuthentic(ItemStack item);
 * </pre>
 * 
 * <h3>4. CoreItemService 의존성</h3>
 * <pre>
 * // 구현체는 반드시 CoreItemService를 주입받아야 함
 * void setCoreItemService(CoreItemService service);
 * </pre>
 * 
 * ==========================================
 * 가변 구조 (구현 시 선택 가능)
 * ==========================================
 * 
 * <h3>1. 상점 카테고리</h3>
 * <pre>
 * // 레거시에서는 magic, protection, scroll, ticket 등 여러 종류
 * // 구현 시 단일 통합 상점 또는 카테고리별 분리 선택 가능
 * enum CoreItemShopType {
 *     MAGIC,       // 마법 아이템 (인챈트 등)
 *     PROTECTION,  // 보호 아이템 (바인딩 등)
 *     SCROLL,      // 스크롤 (텔레포트, 귀환 등)
 *     TICKET,      // 티켓 (던전, 이벤트 입장 등)
 *     GENERAL      // 일반 커스텀 아이템
 * }
 * </pre>
 * 
 * <h3>2. 구매 제한</h3>
 * <pre>
 * // 일일 구매 제한, 레벨 제한 등 선택적 구현
 * int getDailyPurchaseLimit(String coreItemId);
 * int getRequiredLevel(String coreItemId);
 * </pre>
 * 
 * <h3>3. 가격 정책</h3>
 * <pre>
 * // 고정 가격 또는 동적 가격 선택
 * // 커스텀 아이템은 일반적으로 고정 가격 권장
 * boolean isDynamicPrice(String coreItemId);
 * </pre>
 * 
 * <h3>4. 특수 구매 조건</h3>
 * <pre>
 * // 퀘스트 완료, 업적 달성 등 선행 조건
 * boolean meetsRequirements(Player player, String coreItemId);
 * List<String> getRequirements(String coreItemId);
 * </pre>
 * 
 * ==========================================
 * 구현 예시 (Phase 5+)
 * ==========================================
 * 
 * <pre>
 * public class MagicItemShop extends AbstractShop implements ICoreItemShop {
 *     
 *     private CoreItemService coreItemService;
 *     private CoreItemAuthenticator authenticator;
 *     
 *     // 커스텀 아이템 목록 (coreItemId -> CoreShopItem)
 *     private final Map<String, CoreShopItem> items = new LinkedHashMap<>();
 *     
 *     {@literal @}Override
 *     public void setCoreItemService(CoreItemService service) {
 *         this.coreItemService = service;
 *         this.authenticator = service.getAuthenticator();
 *     }
 *     
 *     {@literal @}Override
 *     public long getBuyPrice(String coreItemId) {
 *         CoreShopItem item = items.get(coreItemId);
 *         return item != null ? item.getBuyPrice() : -1;
 *     }
 *     
 *     {@literal @}Override
 *     public boolean isAuthentic(ItemStack item) {
 *         if (authenticator == null) return false;
 *         return authenticator.isAuthentic(item);
 *     }
 *     
 *     // IShop 구현 (ItemStack 기반)
 *     {@literal @}Override
 *     public long getBuyPrice(ItemStack item) {
 *         String coreItemId = extractCoreItemId(item);
 *         return getBuyPrice(coreItemId);
 *     }
 *     
 *     private String extractCoreItemId(ItemStack item) {
 *         // PDC에서 custom_item_id 추출
 *         // ...
 *     }
 * }
 * </pre>
 * 
 * ==========================================
 * 레거시 참고 (G:\minecraft-botttycoon-server\tycoon\src\main\java\kr\bapuri\tycoon\shop\)
 * ==========================================
 * 
 * <ul>
 *   <li>MagicShop.java - 마법 아이템 상점</li>
 *   <li>ProtectionShop.java - 보호 아이템 상점</li>
 *   <li>ScrollShop.java - 스크롤 상점</li>
 *   <li>TicketShop.java - 티켓 상점</li>
 *   <li>CoreItemAuthenticator.java - 아이템 진위 검증</li>
 * </ul>
 * 
 * @see kr.bapuri.tycoon.shop.IShop
 * @see kr.bapuri.tycoon.shop.AbstractShop
 */
public interface ICoreItemShop extends IShop {
    
    // ==========================================
    // 필수 메서드 (구현 필요)
    // ==========================================
    
    /**
     * 커스텀 아이템 ID로 구매가 조회
     * 
     * @param coreItemId 커스텀 아이템 ID (예: "scroll_teleport", "protection_binding")
     * @return 구매가 (BD), -1이면 구매 불가
     */
    long getBuyPriceByCoreId(String coreItemId);
    
    /**
     * 커스텀 아이템 ID로 판매가 조회
     * 
     * @param coreItemId 커스텀 아이템 ID
     * @return 판매가 (BD), -1이면 판매 불가
     */
    long getSellPriceByCoreId(String coreItemId);
    
    /**
     * 아이템 진위 검증
     * 
     * CoreItemAuthenticator를 사용하여 아이템이 정품인지 확인
     * (복제, 위조 아이템 거래 방지)
     * 
     * @param item 검증할 아이템
     * @return true면 정품
     */
    boolean isAuthentic(ItemStack item);
    
    /**
     * 커스텀 아이템 ID 추출
     * 
     * ItemStack의 PDC에서 custom_item_id 태그 추출
     * 
     * @param item 아이템
     * @return 커스텀 아이템 ID, 없으면 null
     */
    String extractCoreItemId(ItemStack item);
    
    // ==========================================
    // 선택적 메서드 (기본 구현 제공)
    // ==========================================
    
    /**
     * 일일 구매 제한 (기본: 제한 없음)
     */
    default int getDailyPurchaseLimit(String coreItemId) {
        return -1; // 제한 없음
    }
    
    /**
     * 필요 레벨 (기본: 제한 없음)
     */
    default int getRequiredLevel(String coreItemId) {
        return 0; // 제한 없음
    }
    
    /**
     * 특수 요구 조건 충족 여부 (기본: 항상 충족)
     */
    default boolean meetsSpecialRequirements(Player player, String coreItemId) {
        return true;
    }
}
