package kr.bapuri.tycoon.shop;

import kr.bapuri.tycoon.economy.EconomyService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 상점 추상 클래스 - 공통 로직 구현
 * 
 * [Phase 3.B] 모든 상점의 기본 구현
 * 
 * <h2>제공 기능</h2>
 * <ul>
 *   <li>구매/판매 트랜잭션 처리</li>
 *   <li>초기화 검증</li>
 *   <li>롤백 로직</li>
 *   <li>트랜잭션 ID 기반 로깅</li>
 * </ul>
 * 
 * <h2>상속 시 구현 필요</h2>
 * <ul>
 *   <li>{@link #getBuyPrice(ItemStack)}</li>
 *   <li>{@link #getSellPrice(ItemStack)}</li>
 *   <li>{@link #canBuy(Player, ItemStack)}</li>
 *   <li>{@link #canSell(Player, ItemStack)}</li>
 *   <li>{@link #getItems()}</li>
 * </ul>
 * 
 * @see IShop
 * @see kr.bapuri.tycoon.shop.job.JobShop
 */
public abstract class AbstractShop implements IShop {
    
    /** 트랜잭션 ID 생성용 카운터 */
    private static final AtomicLong TXN_COUNTER = new AtomicLong(System.currentTimeMillis() % 100000);
    
    /** 통합 로거 (일관된 형식) */
    private static final Logger SHOP_LOGGER = Logger.getLogger("Tycoon.Shop");
    
    protected final Plugin plugin;
    protected final EconomyService economyService;
    protected final String shopId;
    protected String displayName;  // YAML에서 변경 가능
    protected final Logger logger;
    
    protected boolean enabled = true;
    
    /**
     * 트랜잭션 ID 생성
     * 형식: SHP-{counter}
     */
    protected static String generateTxnId() {
        return "SHP-" + TXN_COUNTER.incrementAndGet();
    }
    
    /**
     * 거래 결과
     */
    public enum TransactionResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_ITEMS,
        ITEM_NOT_TRADEABLE,
        INVENTORY_FULL,
        SHOP_DISABLED,
        VALIDATION_FAILED,
        UNKNOWN_ERROR
    }
    
    protected AbstractShop(Plugin plugin, EconomyService economyService, String shopId, String displayName) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.shopId = shopId.toLowerCase();
        this.displayName = displayName;
        this.logger = plugin.getLogger();
    }
    
    @Override
    public String getShopId() {
        return shopId;
    }
    
    @Override
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    // ========== 초기화 검증 (버그 방지) ==========
    
    /**
     * [버그 방지] 거래 전 필수 검증
     */
    protected boolean validateTransaction(Player player) {
        if (player == null) {
            logger.warning("[Shop] validateTransaction 실패: player is null");
            return false;
        }
        if (!enabled) {
            logger.fine("[Shop] validateTransaction 실패: shop disabled - " + shopId);
            return false;
        }
        if (economyService == null) {
            logger.warning("[Shop] validateTransaction 실패: economyService is null");
            return false;
        }
        return true;
    }
    
    // ========== 구매 처리 (롤백 로직 포함) ==========
    
    /**
     * 구매 실행
     * 
     * [롤백 로직]
     * 1. 돈 차감
     * 2. 아이템 지급
     * 3. 실패 시 돈 복구
     * 
     * @param player 구매자
     * @param item 구매할 아이템 (amount 포함)
     * @return 거래 결과
     */
    public TransactionResult executeBuy(Player player, ItemStack item) {
        String txnId = generateTxnId();
        
        // 검증
        if (!validateTransaction(player)) {
            logTransaction(txnId, "BUY", player, item, 0, TransactionResult.VALIDATION_FAILED);
            return TransactionResult.VALIDATION_FAILED;
        }
        if (!canBuy(player, item)) {
            logTransaction(txnId, "BUY", player, item, 0, TransactionResult.ITEM_NOT_TRADEABLE);
            return TransactionResult.ITEM_NOT_TRADEABLE;
        }
        
        // [Fix] 플레이어별 레벨 보너스 적용 가격 사용
        long price = getBuyPrice(player, item);
        if (price < 0) {
            logTransaction(txnId, "BUY", player, item, 0, TransactionResult.ITEM_NOT_TRADEABLE);
            return TransactionResult.ITEM_NOT_TRADEABLE;
        }
        
        // 총 가격 계산 (수량 반영)
        long totalPrice = price * item.getAmount();
        
        // 잔액 확인
        if (!economyService.hasBalance(player.getUniqueId(), totalPrice)) {
            logTransaction(txnId, "BUY", player, item, totalPrice, TransactionResult.INSUFFICIENT_FUNDS);
            return TransactionResult.INSUFFICIENT_FUNDS;
        }
        
        // 인벤토리 공간 확인 (사전 체크, 실패해도 addItem에서 재확인)
        if (!hasInventorySpace(player, item)) {
            logTransaction(txnId, "BUY", player, item, totalPrice, TransactionResult.INVENTORY_FULL);
            return TransactionResult.INVENTORY_FULL;
        }
        
        // 1. 돈 차감 (EconomyService.withdraw는 void, hasBalance로 사전 확인)
        economyService.withdraw(player.getUniqueId(), totalPrice);
        
        // 2. 아이템 지급
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        if (!overflow.isEmpty()) {
            // 롤백: 돈 복구
            economyService.deposit(player.getUniqueId(), totalPrice);
            SHOP_LOGGER.warning(String.format("[%s] ROLLBACK player=%s reason=inventory_full",
                    txnId, player.getName()));
            logTransaction(txnId, "BUY", player, item, totalPrice, TransactionResult.INVENTORY_FULL);
            return TransactionResult.INVENTORY_FULL;
        }
        
        // 성공 로깅
        logTransaction(txnId, "BUY", player, item, totalPrice, TransactionResult.SUCCESS);
        
        return TransactionResult.SUCCESS;
    }
    
    // ========== 판매 처리 (롤백 로직 포함) ==========
    
    /**
     * 판매 실행
     * 
     * [롤백 로직]
     * 1. 아이템 차감
     * 2. 돈 지급
     * 3. 실패 시 아이템 복구
     * 
     * @param player 판매자
     * @param item 판매할 아이템 (amount 포함)
     * @return 거래 결과
     */
    public TransactionResult executeSell(Player player, ItemStack item) {
        // [Fix] 플레이어별 레벨 보너스 적용 가격 사용
        long price = getSellPrice(player, item);
        if (price < 0) {
            String txnId = generateTxnId();
            logTransaction(txnId, "SELL", player, item, 0, TransactionResult.ITEM_NOT_TRADEABLE);
            return TransactionResult.ITEM_NOT_TRADEABLE;
        }
        
        // 총 가격 계산 (수량 반영)
        long totalPrice = price * item.getAmount();
        
        // 가격 오버라이드 버전 호출
        return executeSellWithPrice(player, item, totalPrice);
    }
    
    /**
     * 판매 실행 (가격 직접 지정)
     * 
     * 서브클래스에서 레벨 보너스 등 커스텀 가격을 적용할 때 사용합니다.
     * 
     * @param player 판매자
     * @param item 판매할 아이템 (amount 포함)
     * @param totalPrice 총 판매가 (이미 수량 반영됨)
     * @return 거래 결과
     */
    protected TransactionResult executeSellWithPrice(Player player, ItemStack item, long totalPrice) {
        String txnId = generateTxnId();
        
        // 검증
        if (!validateTransaction(player)) {
            logTransaction(txnId, "SELL", player, item, 0, TransactionResult.VALIDATION_FAILED);
            return TransactionResult.VALIDATION_FAILED;
        }
        if (!canSell(player, item)) {
            logTransaction(txnId, "SELL", player, item, 0, TransactionResult.ITEM_NOT_TRADEABLE);
            return TransactionResult.ITEM_NOT_TRADEABLE;
        }
        
        // 아이템 보유 확인
        if (!hasItems(player, item)) {
            logTransaction(txnId, "SELL", player, item, totalPrice, TransactionResult.INSUFFICIENT_ITEMS);
            return TransactionResult.INSUFFICIENT_ITEMS;
        }
        
        // 1. 아이템 차감
        ItemStack toRemove = item.clone();
        if (!removeItems(player, toRemove)) {
            logTransaction(txnId, "SELL", player, item, totalPrice, TransactionResult.INSUFFICIENT_ITEMS);
            return TransactionResult.INSUFFICIENT_ITEMS;
        }
        
        // 2. 돈 지급 (EconomyService.deposit은 void, 항상 성공으로 간주)
        economyService.deposit(player.getUniqueId(), totalPrice);
        
        // 성공 로깅
        logTransaction(txnId, "SELL", player, item, totalPrice, TransactionResult.SUCCESS);
        
        return TransactionResult.SUCCESS;
    }
    
    // ========== 로깅 ==========
    
    /**
     * 트랜잭션 로깅 (일관된 형식)
     * 
     * 형식: [TXN-ID] ACTION shop=ID player=NAME item=TYPE x AMOUNT = PRICE BD result=RESULT
     */
    private void logTransaction(String txnId, String action, Player player, ItemStack item, 
                                long totalPrice, TransactionResult result) {
        String logMsg = String.format("[%s] %s shop=%s player=%s item=%s x%d = %d BD result=%s",
                txnId, action, shopId, player.getName(), 
                item.getType().name(), item.getAmount(), totalPrice, result.name());
        
        if (result == TransactionResult.SUCCESS) {
            SHOP_LOGGER.info(logMsg);
        } else {
            SHOP_LOGGER.warning(logMsg);
        }
    }
    
    // ========== 유틸리티 ==========
    
    /**
     * 인벤토리 공간 확인
     * 
     * 정확한 스택 가능 여부 체크:
     * - 빈 슬롯이 있으면 OK
     * - 같은 아이템이 있고 스택에 여유가 있으면 OK
     */
    protected boolean hasInventorySpace(Player player, ItemStack item) {
        // 빈 슬롯이 있으면 OK
        if (player.getInventory().firstEmpty() != -1) {
            return true;
        }
        
        // 같은 타입의 아이템 슬롯에서 스택 여유 확인
        int maxStack = item.getMaxStackSize();
        int needed = item.getAmount();
        
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot != null && slot.isSimilar(item)) {
                int space = maxStack - slot.getAmount();
                needed -= space;
                if (needed <= 0) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 아이템 보유 확인
     */
    protected boolean hasItems(Player player, ItemStack item) {
        return player.getInventory().containsAtLeast(item, item.getAmount());
    }
    
    /**
     * 아이템 제거
     */
    protected boolean removeItems(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> notRemoved = player.getInventory().removeItem(item);
        return notRemoved.isEmpty();
    }
    
    @Override
    public void save() {
        // 기본 구현: 아무것도 안함 (서브클래스에서 오버라이드)
    }
    
    @Override
    public void load() {
        // 기본 구현: 아무것도 안함 (서브클래스에서 오버라이드)
    }
}
