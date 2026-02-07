package kr.bapuri.tycoon.shop.job;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.common.AbstractJobExpService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import kr.bapuri.tycoon.shop.AbstractShop;
import kr.bapuri.tycoon.shop.ShopItem;
import kr.bapuri.tycoon.shop.price.DynamicPriceTracker;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 직업 상점 공통 추상 클래스
 * 
 * [Phase 3.B] 바닐라 아이템 거래용 상점
 * 
 * <h2>특징</h2>
 * <ul>
 *   <li>동적 가격 시스템 (DynamicPriceTracker 연동)</li>
 *   <li>설정 파일(shops.yml)에서 품목/가격 로드</li>
 *   <li>판매(플레이어→NPC) + 구매(NPC→플레이어) 지원</li>
 * </ul>
 * 
 * <h2>상속 구조</h2>
 * <pre>
 * IShop → AbstractShop → JobShop → MinerShop/FarmerShop/FisherShop
 * </pre>
 * 
 * <h2>새 직업 상점 추가 방법</h2>
 * <ol>
 *   <li>JobShop 상속하여 새 클래스 생성</li>
 *   <li>{@link #initItems()} 오버라이드하여 기본 품목 정의</li>
 *   <li>shops.yml에 설정 섹션 추가</li>
 *   <li>ShopService에서 등록</li>
 * </ol>
 * 
 * @see kr.bapuri.tycoon.shop.ShopService
 */
public abstract class JobShop extends AbstractShop {
    
    // 동적 가격 추적기
    protected DynamicPriceTracker priceTracker;
    
    // [Phase 4.E] 직업 경험치 서비스 (판매 XP 부여용)
    protected AbstractJobExpService expService;
    
    // [Phase 8] 판매액 기록용
    protected PlayerDataManager playerDataManager;
    protected JobType jobType;
    
    // [Fix] 레벨 보너스 퍼센트 (jobs.yml에서 로드)
    protected double levelBonusPercent = 7.0;
    
    // 아이템 목록 (Material → ShopItemEntry)
    protected final Map<Material, ShopItemEntry> items = new LinkedHashMap<>();
    
    protected JobShop(Plugin plugin, EconomyService economyService, String shopId, String displayName) {
        super(plugin, economyService, shopId, displayName);
    }
    
    /**
     * [Phase 4.E] ExpService 설정
     * 
     * 판매 시 경험치 부여를 위해 해당 직업의 ExpService를 설정합니다.
     * TycoonPlugin에서 각 상점 초기화 시 호출됩니다.
     */
    public void setExpService(AbstractJobExpService expService) {
        this.expService = expService;
    }
    
    /**
     * [Phase 8] PlayerDataManager 설정
     * 
     * 판매 시 판매액 기록을 위해 설정합니다.
     */
    public void setPlayerDataManager(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }
    
    /**
     * [Phase 8] JobType 설정
     * 
     * 직업별 판매 통계 기록을 위해 설정합니다.
     */
    public void setJobType(JobType jobType) {
        this.jobType = jobType;
    }
    
    /**
     * [Fix] 레벨 보너스 퍼센트 설정
     * 
     * jobs.yml의 levelBonusPercent 값을 설정합니다.
     * TycoonPlugin에서 각 상점 초기화 시 호출됩니다.
     */
    public void setLevelBonusPercent(double percent) {
        this.levelBonusPercent = percent;
    }
    
    /**
     * 동적 가격 추적기 설정
     * 
     * 이미 등록된 아이템들도 추적기에 등록
     */
    public void setPriceTracker(DynamicPriceTracker priceTracker) {
        this.priceTracker = priceTracker;
        
        // 이미 등록된 아이템들을 추적기에 등록
        if (priceTracker != null && !items.isEmpty()) {
            for (ShopItemEntry entry : items.values()) {
                if (entry.canBuy || entry.canSell) {
                    long basePrice = entry.canBuy ? entry.buyPrice : entry.sellPrice;
                    if (basePrice > 0) {
                        long minPrice = (long)(basePrice * 0.3);
                        long maxPrice = (long)(basePrice * 1.7);
                        priceTracker.registerItem(entry.material.name(), basePrice, minPrice, maxPrice);
                    }
                }
            }
            logger.info("[JobShop] " + shopId + ": " + items.size() + "개 아이템을 DynamicPriceTracker에 등록");
        }
    }
    
    // ========== 아이템 초기화 ==========
    
    /**
     * 기본 아이템 초기화 (서브클래스에서 구현)
     * 
     * 예시:
     * <pre>
     * {@literal @}Override
     * protected void initItems() {
     *     registerItem(Material.COAL, 50, 25, true, true);
     *     registerItem(Material.IRON_INGOT, 200, 100, true, true);
     * }
     * </pre>
     */
    protected abstract void initItems();
    
    /**
     * 아이템 등록
     * 
     * @param material 아이템 타입
     * @param buyPrice 구매가 (플레이어가 지불, -1이면 구매 불가)
     * @param sellPrice 판매가 (플레이어가 받음, -1이면 판매 불가)
     * @param canBuy 구매 가능 여부
     * @param canSell 판매 가능 여부
     */
    protected void registerItem(Material material, long buyPrice, long sellPrice, 
                                boolean canBuy, boolean canSell) {
        // 차익거래 방지
        if (canBuy && canSell && sellPrice >= buyPrice) {
            sellPrice = (long)(buyPrice * 0.5);
            logger.warning("[JobShop] 차익거래 방지 적용: " + material + " sell=" + sellPrice);
        }
        
        ShopItemEntry entry = new ShopItemEntry(material, buyPrice, sellPrice, canBuy, canSell);
        items.put(material, entry);
        
        // 동적 가격 추적기에 등록
        if (priceTracker != null && (canBuy || canSell)) {
            long basePrice = canBuy ? buyPrice : sellPrice;
            long minPrice = (long)(basePrice * 0.3);  // 최소 30%
            long maxPrice = (long)(basePrice * 1.7);  // 최대 170%
            priceTracker.registerItem(material.name(), basePrice, minPrice, maxPrice);
        }
    }
    
    /**
     * 간편 등록 (구매+판매 모두 가능, 판매가 = 구매가 × 0.5)
     */
    protected void registerItem(Material material, long basePrice) {
        registerItem(material, basePrice, (long)(basePrice * 0.5), true, true);
    }
    
    // ========== 설정 파일 로드 ==========
    
    /**
     * shops.yml에서 품목/가격 로드 (단일 진실 공급원)
     * 
     * <p>YAML에 items 섹션이 있으면 YAML만 사용.
     * items 섹션이 없으면 initItems() 폴백 (개발용 안전망).</p>
     * 
     * @param section job_shops.{shopId} 섹션
     * @return 로드 성공 여부 (차익거래 오류 시 false)
     */
    public boolean loadFromYaml(ConfigurationSection section) {
        // 임시 저장 (롤백용)
        Map<Material, ShopItemEntry> backup = new LinkedHashMap<>(items);
        
        // 기존 아이템 초기화
        items.clear();
        
        if (section == null) {
            logger.warning("[JobShop] " + shopId + ": 설정 섹션이 없음 - initItems() 폴백 사용");
            initItems();
            return true;
        }
        
        // enabled 체크
        enabled = section.getBoolean("enabled", true);
        
        // display_name (옵션)
        String configDisplayName = section.getString("display_name");
        if (configDisplayName != null && !configDisplayName.isEmpty()) {
            this.displayName = configDisplayName;
        }
        
        // items 섹션 로드
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection == null) {
            logger.warning("[JobShop] " + shopId + ": items 섹션이 없음 - initItems() 폴백 사용");
            initItems();
            return true;
        }
        
        int loadedCount = 0;
        int errorCount = 0;
        List<String> arbitrageErrors = new ArrayList<>();
        
        for (String key : itemsSection.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());
                ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                
                long buy, sell;
                
                if (itemSec != null) {
                    // 블록 형식: IRON_INGOT: { buy: 150, sell: 75 }
                    buy = itemSec.getLong("buy", -1);
                    sell = itemSec.getLong("sell", -1);
                } else {
                    // 단순 형식 지원 (확장성)
                    buy = -1;
                    sell = -1;
                }
                
                boolean canBuy = buy > 0;
                boolean canSell = sell > 0;
                
                if (!canBuy && !canSell) {
                    logger.warning("[JobShop] " + shopId + ": " + key + " - buy/sell 둘 다 없음, 무시됨");
                    errorCount++;
                    continue;
                }
                
                // [신규] 차익거래 설정 오류 감지 (sell >= buy)
                if (canBuy && canSell && sell >= buy) {
                    arbitrageErrors.add(key + " (buy=" + buy + ", sell=" + sell + ")");
                    errorCount++;
                    continue;
                }
                
                registerItem(material, buy, sell, canBuy, canSell);
                loadedCount++;
                
            } catch (IllegalArgumentException e) {
                logger.warning("[JobShop] " + shopId + ": 알 수 없는 Material - " + key);
                errorCount++;
            }
        }
        
        // [신규] 차익거래 오류가 있으면 리로드 실패 + 롤백
        if (!arbitrageErrors.isEmpty()) {
            logger.severe("[JobShop] " + shopId + ": 차익거래 설정 오류 발견! 리로드 취소");
            for (String err : arbitrageErrors) {
                logger.severe("[JobShop]   - " + err + " (sell >= buy 불가!)");
            }
            // 롤백
            items.clear();
            items.putAll(backup);
            return false;
        }
        
        logger.info("[JobShop] " + shopId + ": " + loadedCount + "개 아이템 로드 완료" + 
                   (errorCount > 0 ? " (" + errorCount + "개 오류)" : ""));
        return true;
    }
    
    // ========== IShop 구현 ==========
    
    @Override
    public long getBuyPrice(ItemStack item) {
        if (item == null) return -1;
        ShopItemEntry entry = items.get(item.getType());
        if (entry == null || !entry.canBuy) return -1;
        
        // 동적 가격 적용
        if (priceTracker != null && priceTracker.isRegistered(item.getType().name())) {
            long dynamicPrice = priceTracker.getBuyPrice(item.getType().name());
            if (dynamicPrice > 0) return dynamicPrice;
        }
        
        return entry.buyPrice;
    }
    
    @Override
    public long getSellPrice(ItemStack item) {
        if (item == null) return -1;
        ShopItemEntry entry = items.get(item.getType());
        if (entry == null || !entry.canSell) return -1;
        
        // 동적 가격 적용
        if (priceTracker != null && priceTracker.isRegistered(item.getType().name())) {
            long dynamicPrice = priceTracker.getSellPrice(item.getType().name());
            if (dynamicPrice > 0) return dynamicPrice;
        }
        
        return entry.sellPrice;
    }
    
    /**
     * [Fix] 플레이어별 판매가 조회 (레벨 보너스 적용)
     * 
     * 공식: basePrice × (1 + levelBonusPercent/100 × level) × cropGradeMultiplier
     * 
     * [Phase 승급효과] CropGrade 보너스 적용 (농부 상점 전용)
     */
    @Override
    public long getSellPrice(Player player, ItemStack item) {
        long basePrice = getSellPrice(item);
        if (basePrice <= 0) return basePrice;
        
        // 플레이어가 null이거나 직업이 없으면 기본 가격
        if (player == null || expService == null) {
            return basePrice;
        }
        
        // 해당 직업이 없으면 기본 가격
        if (!expService.hasJob(player)) {
            return basePrice;
        }
        
        // 레벨 보너스 적용 (복리 계산)
        int level = expService.getLevel(player);
        double multiplier = Math.pow(1.0 + levelBonusPercent / 100.0, level);
        
        // [2026-02-02] CropGrade 보너스 적용 (모든 JobShop에서 체크)
        // DRIED_KELP 등 가공품도 등급 보너스 받을 수 있도록 확장
        if (item != null) {
            kr.bapuri.tycoon.job.farmer.CropGrade cropGrade = 
                kr.bapuri.tycoon.job.farmer.CropGrade.getGrade(item);
            if (cropGrade != null && cropGrade.getPriceMultiplier() > 1.0) {
                multiplier *= cropGrade.getPriceMultiplier();
            }
        }
        
        // [2026-02-02] FishRarity 보너스 적용 (모든 JobShop에서 체크)
        // 구운 물고기도 희귀도 보너스 받을 수 있도록 확장
        if (item != null) {
            kr.bapuri.tycoon.job.fisher.FishRarity fishRarity = 
                kr.bapuri.tycoon.job.fisher.FishRarity.getRarity(item);
            if (fishRarity != null && fishRarity.getPriceMultiplier() > 1.0) {
                multiplier *= fishRarity.getPriceMultiplier();
            }
        }
        
        return Math.round(basePrice * multiplier);
    }
    
    /**
     * [Fix] 플레이어별 구매가 조회 (레벨 보너스 적용)
     * 
     * <p>판매가와 동일한 비율로 구매가도 증가하여 가격 역전 방지</p>
     * 
     * 공식: basePrice × (1 + levelBonusPercent/100)^level (복리)
     */
    @Override
    public long getBuyPrice(Player player, ItemStack item) {
        long basePrice = getBuyPrice(item);
        if (basePrice <= 0) return basePrice;
        
        // 플레이어가 null이거나 직업이 없으면 기본 가격
        if (player == null || expService == null) {
            return basePrice;
        }
        
        // 해당 직업이 없으면 기본 가격
        if (!expService.hasJob(player)) {
            return basePrice;
        }
        
        // 레벨 보너스 적용 (복리 계산)
        int level = expService.getLevel(player);
        double multiplier = Math.pow(1.0 + levelBonusPercent / 100.0, level);
        
        return Math.round(basePrice * multiplier);
    }
    
    @Override
    public boolean canBuy(Player player, ItemStack item) {
        if (item == null) return false;
        ShopItemEntry entry = items.get(item.getType());
        return entry != null && entry.canBuy && entry.buyPrice > 0;
    }
    
    @Override
    public boolean canSell(Player player, ItemStack item) {
        if (item == null) return false;
        ShopItemEntry entry = items.get(item.getType());
        return entry != null && entry.canSell && entry.sellPrice > 0;
    }
    
    @Override
    public List<ShopItem> getItems() {
        List<ShopItem> result = new ArrayList<>();
        for (ShopItemEntry entry : items.values()) {
            if (entry.canBuy || entry.canSell) {
                result.add(ShopItem.createSafe(entry.material, entry.buyPrice, entry.sellPrice, true));
            }
        }
        return result;
    }
    
    @Override
    public void load() {
        // YAML 설정이 없으면 initItems() 폴백 사용
        // 실제 YAML 로드는 ShopService에서 loadFromYaml() 호출
        if (items.isEmpty()) {
            logger.info("[JobShop] " + shopId + ": items 비어있음 - initItems() 폴백");
            initItems();
        }
        if (priceTracker != null) {
            priceTracker.load();
        }
    }
    
    @Override
    public void save() {
        if (priceTracker != null) {
            priceTracker.save();
        }
    }
    
    // ========== [Phase 4.E] 판매 XP 연동 ==========
    
    /**
     * 판매 실행 (AbstractShop 오버라이드)
     * 
     * [Fix] 레벨 보너스가 적용된 가격으로 판매 처리
     * 판매 성공 시 ExpService를 통해 XP 부여 및 판매액 기록
     */
    @Override
    public TransactionResult executeSell(Player player, ItemStack item) {
        // [Fix] 레벨 보너스 적용된 가격 계산 (실제 지급액)
        long unitPrice = getSellPrice(player, item);
        long totalSaleAmount = unitPrice * item.getAmount();
        
        // 부모의 가격 오버라이드 메서드 호출 (로깅 포함)
        TransactionResult result = super.executeSellWithPrice(player, item, totalSaleAmount);
        
        // 판매 성공 시 처리
        if (result == TransactionResult.SUCCESS) {
            // [Phase 8] 총 판매액 기록 (모든 직업 공통, 승급 조건용)
            if (playerDataManager != null) {
                PlayerTycoonData data = playerDataManager.get(player.getUniqueId());
                if (data != null) {
                    data.addTotalSales(totalSaleAmount);
                    
                    // 아이템 판매 수량만 기록 (판매액은 ExpService에서 기록)
                    if (jobType != null) {
                        recordJobSpecificItemSold(data, item);
                    }
                    
                    logger.fine(String.format("[JobShop] %s: %s 판매액 %,d BD (아이템: %s x%d, 레벨보너스 적용)",
                            shopId, player.getName(), totalSaleAmount, item.getType().name(), item.getAmount()));
                }
            }
            
            // XP 부여 + 직업별 판매액 기록 (ExpService에서 처리)
            if (expService != null) {
                long xpGranted = expService.addSaleExpFromShop(
                        player, 
                        item.getType(), 
                        item.getAmount(), 
                        totalSaleAmount
                );
                
                if (xpGranted > 0) {
                    logger.fine(String.format("[JobShop] %s: %s에게 판매 XP %d 부여 (아이템: %s x%d)",
                            shopId, player.getName(), xpGranted, item.getType().name(), item.getAmount()));
                }
            }
        }
        
        return result;
    }
    
    /**
     * [Fix] 직업별 아이템 판매 수량 기록 (판매액은 ExpService에서 기록)
     */
    private void recordJobSpecificItemSold(PlayerTycoonData data, ItemStack item) {
        switch (jobType) {
            case MINER -> data.recordSold(item.getType().name(), item.getAmount());
            case FARMER -> data.recordSoldCrop(item.getType().name(), item.getAmount());
            case FISHER -> data.recordSoldSeafood(item.getType().name(), item.getAmount());
            default -> { /* Tier 2 직업은 별도 처리 */ }
        }
    }
    
    // ========== 내부 클래스 ==========
    
    /**
     * 상점 아이템 정보
     */
    protected static class ShopItemEntry {
        final Material material;
        final long buyPrice;   // 플레이어가 지불 (-1이면 구매 불가)
        final long sellPrice;  // 플레이어가 받음 (-1이면 판매 불가)
        final boolean canBuy;
        final boolean canSell;
        
        ShopItemEntry(Material material, long buyPrice, long sellPrice, boolean canBuy, boolean canSell) {
            this.material = material;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.canBuy = canBuy;
            this.canSell = canSell;
        }
    }
}
