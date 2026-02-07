package kr.bapuri.tycoon.enhance.upgrade;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.common.EnhanceLoreBuilder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.logging.Logger;

/**
 * UpgradeService - 강화 비즈니스 로직
 * 
 * 주요 기능:
 * - 강화 실행 (확률 기반)
 * - 보호 주문서 적용
 * - 스탯 보너스 계산
 * 
 * Phase 6 LITE:
 * - CoreUpgradeProtectionHelper 의존성 제거 (setter 유지, 미사용)
 * - 향후 확장 가능성을 위해 인터페이스 유지
 */
public class UpgradeService {

    private final UpgradeConfig config;
    private final EconomyService economyService;
    private final Logger logger;
    private final Random random = new Random();

    public UpgradeService(UpgradeConfig config, EconomyService economyService, Logger logger) {
        this.config = config;
        this.economyService = economyService;
        this.logger = logger;
    }

    /**
     * 강화 실행 (레거시 - 하위 호환성)
     * 
     * @deprecated 새로운 2슬롯 분리 버전 사용: upgrade(player, item, hasDestroyProtection, hasDowngradeProtection)
     */
    @Deprecated
    public UpgradeResultInfo upgrade(Player player, ItemStack item, 
                                     boolean useProtection, String protectionType) {
        boolean hasDestroyProtection = useProtection && 
            (protectionType != null && (protectionType.equals("destroy") || protectionType.equals("all")));
        boolean hasDowngradeProtection = useProtection && 
            (protectionType != null && (protectionType.equals("downgrade") || protectionType.equals("all")));
        
        return upgrade(player, item, hasDestroyProtection, hasDowngradeProtection);
    }
    
    /**
     * 강화 실행
     * 
     * [Phase 8] 보호 주문서 2슬롯 분리 지원
     * 
     * @param player 플레이어
     * @param item 강화할 아이템
     * @param hasDestroyProtection 파괴 방지 주문서 사용 여부
     * @param hasDowngradeProtection 하락 방지 주문서 사용 여부
     * @return 강화 결과 정보
     */
    public UpgradeResultInfo upgrade(Player player, ItemStack item, 
                                     boolean hasDestroyProtection, boolean hasDowngradeProtection) {
        // 검증
        if (!config.isEnabled()) {
            return new UpgradeResultInfo(null, "§c강화 시스템이 비활성화되어 있습니다.", false);
        }

        if (item == null || !EnhanceItemUtil.isUpgradeable(item)) {
            return new UpgradeResultInfo(null, "§c강화할 수 없는 아이템입니다.", false);
        }

        int currentLevel = EnhanceItemUtil.getUpgradeLevel(item);
        
        if (currentLevel >= EnhanceConstants.MAX_UPGRADE_LEVEL) {
            return new UpgradeResultInfo(null, "§c이미 최대 강화 레벨입니다.", false);
        }

        // 비용 확인
        long cost = config.getUpgradeCost(currentLevel);
        if (!economyService.hasBalance(player, cost)) {
            return new UpgradeResultInfo(null, "§c돈이 부족합니다. (필요: " + cost + "원)", false);
        }

        // 비용 차감
        economyService.withdraw(player, cost);

        // 확률 롤링
        UpgradeResult originalResult = rollResult(currentLevel);
        UpgradeResult result = originalResult;
        
        // [Phase 8] 보호 주문서 적용 추적
        boolean destroyPrevented = false;
        boolean downgradePrevented = false;

        // [Phase 8] 보호 주문서 2슬롯 분리 적용
        if (result == UpgradeResult.DESTROY && hasDestroyProtection) {
            result = UpgradeResult.MAINTAIN;
            destroyPrevented = true;
        }
        if (result == UpgradeResult.DOWNGRADE && hasDowngradeProtection) {
            result = UpgradeResult.MAINTAIN;
            downgradePrevented = true;
        }

        // 결과 적용
        int newLevel = currentLevel;
        boolean itemDestroyed = false;

        switch (result) {
            case SUCCESS -> {
                newLevel = currentLevel + 1;
                EnhanceItemUtil.setUpgradeLevel(item, newLevel);
                EnhanceLoreBuilder.refreshLore(item);
                EnhanceLoreBuilder.updateItemName(item);
                playSuccessEffect(player);
            }
            case DOWNGRADE -> {
                newLevel = Math.max(0, currentLevel - 1);
                EnhanceItemUtil.setUpgradeLevel(item, newLevel);
                EnhanceLoreBuilder.refreshLore(item);
                EnhanceLoreBuilder.updateItemName(item);
                playDowngradeEffect(player);
            }
            case MAINTAIN -> {
                // [Phase 8] 보호 주문서로 방지된 경우 - 메시지는 UpgradeGui에서 통합 처리
                // (중복 메시지 방지: result.message에 정보 포함됨)
                playMaintainEffect(player);
            }
            case DESTROY -> {
                itemDestroyed = true;
                item.setAmount(0);
                playDestroyEffect(player);
            }
        }

        // 로그
        String protectionLog = "";
        if (destroyPrevented) protectionLog = " [파괴방지]";
        if (downgradePrevented) protectionLog = " [하락방지]";
        logger.info("[UpgradeService] " + player.getName() + " 강화: +" + currentLevel + 
                   " -> +" + newLevel + " (" + originalResult.name() + " → " + result.name() + ")" + protectionLog);

        return new UpgradeResultInfo(result, 
                                     result.getTitle() + " " + result.getDescription(),
                                     true,
                                     currentLevel,
                                     newLevel,
                                     itemDestroyed,
                                     destroyPrevented,
                                     downgradePrevented);
    }

    /**
     * 확률 기반 결과 롤링
     */
    private UpgradeResult rollResult(int currentLevel) {
        double roll = random.nextDouble();
        
        double successRate = config.getSuccessRate(currentLevel);
        double downgradeRate = config.getDowngradeRate(currentLevel);
        double destroyRate = config.getDestroyRate(currentLevel);

        if (roll < successRate) {
            return UpgradeResult.SUCCESS;
        }
        roll -= successRate;

        if (roll < downgradeRate) {
            return UpgradeResult.DOWNGRADE;
        }
        roll -= downgradeRate;

        if (roll < destroyRate) {
            return UpgradeResult.DESTROY;
        }

        return UpgradeResult.MAINTAIN;
    }

    /**
     * 강화 레벨 강제 설정 (관리자용)
     */
    public boolean setUpgradeLevel(ItemStack item, int level) {
        if (item == null || !EnhanceItemUtil.isUpgradeable(item)) {
            return false;
        }

        level = Math.max(0, Math.min(EnhanceConstants.MAX_UPGRADE_LEVEL, level));
        EnhanceItemUtil.setUpgradeLevel(item, level);
        EnhanceLoreBuilder.refreshLore(item);
        EnhanceLoreBuilder.updateItemName(item);
        
        return true;
    }

    /**
     * 강화 레벨 조회
     */
    public int getUpgradeLevel(ItemStack item) {
        return EnhanceItemUtil.getUpgradeLevel(item);
    }

    /**
     * 강화 레벨에 따른 공격력 보너스 (%)
     */
    public double getDamageBonus(ItemStack item) {
        int level = getUpgradeLevel(item);
        UpgradeLevel config = this.config.getLevel(level);
        return config != null ? config.getDamageBonus() : 0;
    }

    /**
     * 강화 레벨에 따른 방어력 보너스 (%)
     */
    public double getDefenseBonus(ItemStack item) {
        int level = getUpgradeLevel(item);
        UpgradeLevel config = this.config.getLevel(level);
        return config != null ? config.getDefenseBonus() : 0;
    }

    /**
     * 강화 비용 조회
     */
    public long getUpgradeCost(int currentLevel) {
        return config.getUpgradeCost(currentLevel);
    }

    /**
     * 강화 확률 정보 문자열
     */
    public String getUpgradeChanceInfo(int currentLevel) {
        if (currentLevel >= EnhanceConstants.MAX_UPGRADE_LEVEL) {
            return "§c최대 레벨입니다.";
        }

        UpgradeLevel next = config.getNextLevelConfig(currentLevel);
        if (next == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("§7성공: §a").append(next.getSuccessRateString()).append("\n");
        sb.append("§7유지: §f").append(next.getMaintainRateString()).append("\n");
        
        if (next.getDowngradeRate() > 0) {
            sb.append("§7하락: §e").append(next.getDowngradeRateString()).append("\n");
        }
        if (next.getDestroyRate() > 0) {
            sb.append("§7파괴: §c").append(next.getDestroyRateString()).append("\n");
        }

        return sb.toString();
    }

    // ========== 효과 재생 ==========

    private void playSuccessEffect(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.sendTitle("§a§l강화 성공!", "§7레벨이 상승했습니다!", 10, 40, 20);
    }

    private void playDowngradeEffect(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        player.sendTitle("§e§l강화 하락", "§7레벨이 감소했습니다.", 10, 40, 20);
    }

    private void playMaintainEffect(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
    }

    private void playDestroyEffect(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
        player.sendTitle("§c§l아이템 파괴!", "§4아이템이 사라졌습니다...", 10, 60, 20);
    }

    // ========== Getter ==========

    public UpgradeConfig getConfig() {
        return config;
    }

    // ========== 결과 정보 클래스 ==========

    public static class UpgradeResultInfo {
        public final UpgradeResult result;
        public final String message;
        public final boolean executed;
        public final int previousLevel;
        public final int newLevel;
        public final boolean itemDestroyed;
        
        // [Phase 8] 보호 주문서 2슬롯 분리 - 소모 여부 추적
        public final boolean destroyPrevented;
        public final boolean downgradePrevented;

        public UpgradeResultInfo(UpgradeResult result, String message, boolean executed) {
            this(result, message, executed, 0, 0, false, false, false);
        }

        public UpgradeResultInfo(UpgradeResult result, String message, boolean executed,
                                 int previousLevel, int newLevel, boolean itemDestroyed) {
            this(result, message, executed, previousLevel, newLevel, itemDestroyed, false, false);
        }
        
        /**
         * [Phase 8] 보호 주문서 2슬롯 분리 지원 생성자
         */
        public UpgradeResultInfo(UpgradeResult result, String message, boolean executed,
                                 int previousLevel, int newLevel, boolean itemDestroyed,
                                 boolean destroyPrevented, boolean downgradePrevented) {
            this.result = result;
            this.message = message;
            this.executed = executed;
            this.previousLevel = previousLevel;
            this.newLevel = newLevel;
            this.itemDestroyed = itemDestroyed;
            this.destroyPrevented = destroyPrevented;
            this.downgradePrevented = downgradePrevented;
        }
    }
}
