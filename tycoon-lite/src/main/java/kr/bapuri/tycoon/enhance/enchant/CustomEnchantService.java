package kr.bapuri.tycoon.enhance.enchant;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.common.EnhanceLoreBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.logging.Logger;

/**
 * CustomEnchantService - 커스텀 인챈트 비즈니스 로직
 * 
 * 주요 기능:
 * - 아이템에 커스텀 인챈트 적용/제거
 * - 인챈트 조회
 * - 적용 가능 여부 검증
 * 
 * Phase 6: 레거시 복사
 */
public class CustomEnchantService {

    private final CustomEnchantRegistry registry;
    private final Logger logger;

    public CustomEnchantService(CustomEnchantRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    // ========== 인챈트 적용 ==========

    /**
     * 아이템에 커스텀 인챈트 적용
     * 
     * @return ApplyResult
     */
    public ApplyResult applyEnchant(ItemStack item, String enchantId, int level) {
        if (item == null || item.getType() == Material.AIR) {
            return ApplyResult.INVALID_ITEM;
        }

        CustomEnchant enchant = CustomEnchant.fromId(enchantId);
        if (enchant == null) {
            return ApplyResult.UNKNOWN_ENCHANT;
        }

        CustomEnchantData data = registry.getData(enchantId);
        if (data == null || !data.isEnabled()) {
            return ApplyResult.ENCHANT_DISABLED;
        }

        // 레벨 검증
        if (level < 1 || level > data.getMaxLevel()) {
            return ApplyResult.INVALID_LEVEL;
        }

        // 적용 가능 여부 검증
        if (!enchant.canApplyTo(item.getType())) {
            return ApplyResult.INCOMPATIBLE_ITEM;
        }

        // 인챈트 적용
        EnhanceItemUtil.addCustomEnchant(item, enchantId, level);
        
        // Lore 업데이트
        EnhanceLoreBuilder.refreshLore(item);

        logger.info("[CustomEnchantService] 인챈트 적용: " + enchantId + " Lv." + level);
        return ApplyResult.SUCCESS;
    }

    /**
     * 인챈트 북에서 인챈트 적용 (모루 사용)
     */
    public ApplyResult applyFromBook(ItemStack item, ItemStack book) {
        if (item == null || book == null) {
            return ApplyResult.INVALID_ITEM;
        }

        // 인챈트 북인지 확인
        String bookData = EnhanceItemUtil.getEnchantBookId(book);
        if (bookData == null) {
            return ApplyResult.NOT_ENCHANT_BOOK;
        }

        // 인챈트 ID와 레벨 파싱
        String[] parts = bookData.split(":");
        if (parts.length != 2) {
            return ApplyResult.INVALID_BOOK_DATA;
        }

        String enchantId = parts[0];
        int level;
        try {
            level = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return ApplyResult.INVALID_BOOK_DATA;
        }

        // 기존 인챈트 레벨 확인
        int currentLevel = EnhanceItemUtil.getCustomEnchantLevel(item, enchantId);
        
        // 같은 인챈트가 있으면 레벨 합산 (최대 레벨까지)
        CustomEnchantData data = registry.getData(enchantId);
        if (data != null) {
            int newLevel = Math.min(currentLevel + level, data.getMaxLevel());
            if (newLevel > currentLevel) {
                level = newLevel;
            } else {
                // 이미 최대 레벨
                level = currentLevel;
            }
        }

        return applyEnchant(item, enchantId, level);
    }

    // ========== 인챈트 제거 ==========

    /**
     * 특정 인챈트 제거
     */
    public boolean removeEnchant(ItemStack item, String enchantId) {
        if (item == null || enchantId == null) return false;

        if (!EnhanceItemUtil.hasCustomEnchant(item, enchantId)) {
            return false;
        }

        EnhanceItemUtil.removeCustomEnchant(item, enchantId);
        EnhanceLoreBuilder.refreshLore(item);
        
        return true;
    }

    /**
     * 모든 커스텀 인챈트 제거
     */
    public void clearAllEnchants(ItemStack item) {
        if (item == null) return;

        EnhanceItemUtil.setCustomEnchants(item, null);
        EnhanceLoreBuilder.refreshLore(item);
    }

    // ========== 인챈트 조회 ==========

    /**
     * 아이템의 모든 커스텀 인챈트
     */
    public Map<String, Integer> getEnchants(ItemStack item) {
        return EnhanceItemUtil.getCustomEnchants(item);
    }

    /**
     * 특정 인챈트 레벨
     */
    public int getEnchantLevel(ItemStack item, String enchantId) {
        return EnhanceItemUtil.getCustomEnchantLevel(item, enchantId);
    }

    /**
     * 특정 인챈트 보유 여부
     */
    public boolean hasEnchant(ItemStack item, String enchantId) {
        return EnhanceItemUtil.hasCustomEnchant(item, enchantId);
    }

    /**
     * 커스텀 인챈트가 있는지
     */
    public boolean hasAnyEnchant(ItemStack item) {
        return !EnhanceItemUtil.getCustomEnchants(item).isEmpty();
    }

    // ========== 검증 ==========

    /**
     * 아이템에 특정 인챈트 적용 가능 여부
     */
    public boolean canApply(ItemStack item, String enchantId) {
        if (item == null || enchantId == null) return false;

        CustomEnchant enchant = CustomEnchant.fromId(enchantId);
        if (enchant == null) return false;

        CustomEnchantData data = registry.getData(enchantId);
        if (data == null || !data.isEnabled()) return false;

        return enchant.canApplyTo(item.getType());
    }

    /**
     * 레벨 유효성 검증
     */
    public boolean isValidLevel(String enchantId, int level) {
        if (level < 1) return false;

        CustomEnchantData data = registry.getData(enchantId);
        if (data == null) return false;

        return level <= data.getMaxLevel();
    }

    // ========== 효과값 조회 ==========

    /**
     * 인챈트 효과 수치 (레벨 기반)
     */
    public double getEffectValue(String enchantId, int level) {
        CustomEnchantData data = registry.getData(enchantId);
        if (data == null) return 0;
        return data.getEffectValue(level);
    }

    /**
     * 인챈트 발동 확률 (레벨 기반)
     */
    public double getChance(String enchantId, int level) {
        CustomEnchantData data = registry.getData(enchantId);
        if (data == null) return 0;
        return data.getChance(level);
    }

    /**
     * 인챈트 가격 (레벨 기반)
     */
    public long getPrice(String enchantId, int level) {
        CustomEnchantData data = registry.getData(enchantId);
        if (data == null) return 0;
        return data.getPrice(level);
    }

    // ========== Registry 접근 ==========

    public CustomEnchantRegistry getRegistry() {
        return registry;
    }

    // ========== 결과 enum ==========

    public enum ApplyResult {
        SUCCESS("§a인챈트가 적용되었습니다!"),
        INVALID_ITEM("§c유효하지 않은 아이템입니다."),
        UNKNOWN_ENCHANT("§c존재하지 않는 인챈트입니다."),
        ENCHANT_DISABLED("§c해당 인챈트는 비활성화되어 있습니다."),
        INVALID_LEVEL("§c유효하지 않은 레벨입니다."),
        INCOMPATIBLE_ITEM("§c이 아이템에는 적용할 수 없는 인챈트입니다."),
        NOT_ENCHANT_BOOK("§c커스텀 인챈트 북이 아닙니다."),
        INVALID_BOOK_DATA("§c인챈트 북 데이터가 손상되었습니다.");

        private final String message;

        ApplyResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return this == SUCCESS;
        }
    }
}
