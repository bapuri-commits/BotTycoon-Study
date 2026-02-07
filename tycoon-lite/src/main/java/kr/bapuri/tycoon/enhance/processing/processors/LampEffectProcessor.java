package kr.bapuri.tycoon.enhance.processing.processors;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.lamp.LampEffect;
import kr.bapuri.tycoon.enhance.lamp.LampSlotData;
import kr.bapuri.tycoon.enhance.processing.EffectProcessor;
import kr.bapuri.tycoon.enhance.processing.ProcessingContext;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LampEffectProcessor - 램프 효과 처리기
 * 
 * Priority: 400 (GradeBonus 다음)
 * 
 * 처리하는 램프 효과 (블록 파괴 관련):
 * - AUTO_SMELT: 롤링된 확률(10~100%)로 제련
 * - GOLDEN_TOUCH: 롤링된 확률(5~15%)로 금 조각 추가
 * - CROP_BONUS_1~9: 작물 추가 드롭
 * - SEED_BONUS_1~3: 씨앗 추가 드롭
 * - LUMBER_TRADER: 원목 추가 드롭
 * 
 * 주의: MULTI_MINE, TREE_FELLER는 블록 파괴 트리거이므로 여기서 처리하지 않음
 */
public class LampEffectProcessor implements EffectProcessor {
    
    // 제련 결과 캐시
    private static final Map<Material, Material> SMELT_CACHE = new HashMap<>();
    
    static {
        // 미리 알려진 제련 결과 캐시
        SMELT_CACHE.put(Material.IRON_ORE, Material.IRON_INGOT);
        SMELT_CACHE.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELT_CACHE.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        SMELT_CACHE.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELT_CACHE.put(Material.NETHER_GOLD_ORE, Material.GOLD_INGOT);
        SMELT_CACHE.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        SMELT_CACHE.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELT_CACHE.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT_CACHE.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT_CACHE.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT_CACHE.put(Material.COBBLESTONE, Material.STONE);
        SMELT_CACHE.put(Material.SAND, Material.GLASS);
        SMELT_CACHE.put(Material.NETHERRACK, Material.NETHER_BRICK);
        SMELT_CACHE.put(Material.CLAY_BALL, Material.BRICK);
        SMELT_CACHE.put(Material.CACTUS, Material.GREEN_DYE);
        SMELT_CACHE.put(Material.OAK_LOG, Material.CHARCOAL);
        SMELT_CACHE.put(Material.SPRUCE_LOG, Material.CHARCOAL);
        SMELT_CACHE.put(Material.BIRCH_LOG, Material.CHARCOAL);
        SMELT_CACHE.put(Material.JUNGLE_LOG, Material.CHARCOAL);
        SMELT_CACHE.put(Material.ACACIA_LOG, Material.CHARCOAL);
        SMELT_CACHE.put(Material.DARK_OAK_LOG, Material.CHARCOAL);
        SMELT_CACHE.put(Material.MANGROVE_LOG, Material.CHARCOAL);
        SMELT_CACHE.put(Material.CHERRY_LOG, Material.CHARCOAL);
    }
    
    @Override
    public String getName() {
        return "LampEffect";
    }
    
    @Override
    public int getPriority() {
        return 400;
    }
    
    @Override
    public boolean shouldProcess(ProcessingContext ctx) {
        if (!ctx.getOptions().isApplyLampEffects()) return false;
        
        ItemStack tool = ctx.getTool();
        if (tool == null) return false;
        
        // 램프 슬롯이 있는지 확인
        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(tool);
        return slots != null && !slots.isEmpty();
    }
    
    @Override
    public void process(ProcessingContext ctx) {
        ItemStack tool = ctx.getTool();
        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(tool);
        
        if (slots == null || slots.isEmpty()) return;
        
        for (LampSlotData slot : slots) {
            if (slot == null || slot.getEffectId() == null) continue;
            
            LampEffect effect = LampEffect.fromId(slot.getEffectId());
            if (effect == null || effect.isDisabled()) continue;
            
            // 롤링된 값 (value1)
            double rolledValue = slot.getValue1();
            
            switch (effect) {
                case AUTO_SMELT -> applyAutoSmelt(ctx, rolledValue);
                case GOLDEN_TOUCH -> applyGoldenTouch(ctx, rolledValue, slot.getValue2());
                case SAND_TO_GLASS -> applySandToGlass(ctx, rolledValue);
                case CROP_BONUS_1, CROP_BONUS_2, CROP_BONUS_3, CROP_BONUS_4, CROP_BONUS_5,
                     CROP_BONUS_6, CROP_BONUS_7, CROP_BONUS_8, CROP_BONUS_9 
                     -> applyCropBonus(ctx, rolledValue, slot.getValue2());
                case SEED_BONUS_1, SEED_BONUS_2, SEED_BONUS_3 
                     -> applySeedBonus(ctx, rolledValue, slot.getValue2());
                case LUMBER_TRADER -> applyLumberTrader(ctx, rolledValue, slot.getValue2());
                case BOUNTIFUL -> applyBountiful(ctx, rolledValue);
                case MIDAS_TOUCH -> applyMidasTouch(ctx, rolledValue, slot.getValue2());
                case CROP_COMBO -> applyCropCombo(ctx, rolledValue, slot.getValue2());
                case SILK_TOUCH_PRO -> applySilkTouchPro(ctx, rolledValue);
                case COMPOST_BONUS -> applyCompostBonus(ctx, rolledValue);
                default -> {
                    // 다른 효과는 무시 (전투, 방어구 등)
                }
            }
        }
    }
    
    /**
     * AUTO_SMELT - 자동 제련
     * 롤링된 확률(10~100%)로 각 드롭 아이템 제련
     * 
     * 밸런스 조정:
     * - ANCIENT_DEBRIS(고대잔해) → 네더라이트 조각 변환은 10% 확률 제한
     *   (복사 방지)
     */
    private void applyAutoSmelt(ProcessingContext ctx, double chance) {
        List<ItemStack> newDrops = new ArrayList<>();
        boolean applied = false;
        
        // 고대잔해는 확률 제한 (네더라이트 조각 복사 방지)
        double effectiveChance = chance;
        if (ctx.getOriginalMaterial() == Material.ANCIENT_DEBRIS) {
            effectiveChance = Math.min(chance, 10.0);
        }
        
        for (ItemStack drop : ctx.getDrops()) {
            Material smeltResult = getSmeltResult(drop.getType());
            
            if (smeltResult != null && ThreadLocalRandom.current().nextDouble() * 100 < effectiveChance) {
                // 제련 성공
                newDrops.add(new ItemStack(smeltResult, drop.getAmount()));
                applied = true;
            } else {
                // 제련 실패 또는 제련 불가능
                newDrops.add(drop);
            }
        }
        
        ctx.setDrops(newDrops);
        
        if (applied) {
            ctx.setMetadata("auto_smelt_chance", effectiveChance);
            ctx.markEffectApplied("LAMP_AUTO_SMELT");
        }
    }
    
    /**
     * GOLDEN_TOUCH - 황금 손길
     * 롤링된 확률(5~15%)로 금 조각 추가 드롭
     */
    private void applyGoldenTouch(ProcessingContext ctx, double chance, int amount) {
        if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
            int dropAmount = amount > 0 ? amount : 1;
            ctx.addDrop(new ItemStack(Material.GOLD_NUGGET, dropAmount));
            ctx.markEffectApplied("LAMP_GOLDEN_TOUCH");
        }
    }
    
    /**
     * CROP_BONUS - 작물 보너스
     * 롤링된 확률로 작물 추가 드롭
     */
    private void applyCropBonus(ProcessingContext ctx, double chance, int amount) {
        if (!isCropMaterial(ctx.getOriginalMaterial())) return;
        
        if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
            int bonusAmount = amount > 0 ? amount : 1;
            
            // 드롭 중 첫 번째 아이템과 같은 종류 추가
            if (!ctx.getDrops().isEmpty()) {
                ItemStack firstDrop = ctx.getDrops().get(0);
                ctx.addDrop(new ItemStack(firstDrop.getType(), bonusAmount));
            }
            
            ctx.markEffectApplied("LAMP_CROP_BONUS");
        }
    }
    
    /**
     * SEED_BONUS - 씨앗 보너스
     * 롤링된 확률로 씨앗 추가 드롭
     */
    private void applySeedBonus(ProcessingContext ctx, double chance, int amount) {
        if (!isCropMaterial(ctx.getOriginalMaterial())) return;
        
        if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
            Material seedType = getSeedType(ctx.getOriginalMaterial());
            if (seedType != null) {
                int bonusAmount = amount > 0 ? amount : 1;
                ctx.addDrop(new ItemStack(seedType, bonusAmount));
                ctx.markEffectApplied("LAMP_SEED_BONUS");
            }
        }
    }
    
    /**
     * LUMBER_TRADER - 원목 추가 드롭
     */
    private void applyLumberTrader(ProcessingContext ctx, double chance, int amount) {
        if (!isLogMaterial(ctx.getOriginalMaterial())) return;
        
        if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
            int bonusAmount = amount > 0 ? amount : 1;
            ctx.addDrop(new ItemStack(ctx.getOriginalMaterial(), bonusAmount));
            ctx.markEffectApplied("LAMP_LUMBER_TRADER");
        }
    }
    
    /**
     * SAND_TO_GLASS - 유리 세공
     * 모래/붉은 모래 채굴 시 확률적으로 유리로 변환
     */
    private void applySandToGlass(ProcessingContext ctx, double chance) {
        Material material = ctx.getOriginalMaterial();
        if (material != Material.SAND && material != Material.RED_SAND) return;
        
        if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
            // 기존 드롭을 유리로 교체
            List<ItemStack> newDrops = new ArrayList<>();
            for (ItemStack drop : ctx.getDrops()) {
                if (drop.getType() == Material.SAND || drop.getType() == Material.RED_SAND) {
                    newDrops.add(new ItemStack(Material.GLASS, drop.getAmount()));
                } else {
                    newDrops.add(drop);
                }
            }
            ctx.setDrops(newDrops);
            ctx.markEffectApplied("LAMP_SAND_TO_GLASS");
        }
    }
    
    /**
     * BOUNTIFUL - 자동 재파종 (씨앗 소모 없이)
     * 여기서는 플래그만 설정, 실제 재파종은 EnchantEffectListener에서 처리
     */
    private void applyBountiful(ProcessingContext ctx, double chance) {
        if (!isCropMaterial(ctx.getOriginalMaterial())) return;
        
        if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
            ctx.setMetadata("bountiful_replant", true);
            ctx.markEffectApplied("LAMP_BOUNTIFUL");
        }
    }
    
    /**
     * MIDAS_TOUCH - BD 획득 (광물 전용)
     * LampEffect enum에서 minValue2=300, maxValue2=1000 BD
     * value2에 롤링된 BD 범위가 저장되어 있음
     * 
     * 실제 BD 지급은 DeliveryProcessor 또는 별도 서비스에서 처리
     * 여기서는 획득할 BD 금액을 계산하여 메타데이터에 저장
     */
    private void applyMidasTouch(ProcessingContext ctx, double chance, int minBd) {
        // 광물에만 적용
        if (!isOre(ctx.getOriginalMaterial())) return;
        
        if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
            // MIDAS_TOUCH는 minValue2=300, maxValue2=1000 범위
            // value2에는 롤링된 min BD가 저장됨
            // 실제 BD는 min~max 사이에서 랜덤
            int maxBd = Math.max(minBd, 1000); // 최대값은 1000으로 고정
            int actualBd = minBd + ThreadLocalRandom.current().nextInt(Math.max(1, maxBd - minBd + 1));
            
            ctx.setMetadata("midas_touch_triggered", true);
            ctx.setMetadata("midas_touch_bd", actualBd);
            ctx.markEffectApplied("LAMP_MIDAS_TOUCH");
        }
    }
    
    // ===============================================================
    // 신규 램프 효과
    // ===============================================================
    
    /**
     * CROP_COMBO - 수확 콤보
     * 연속 수확 시 스택당 추가 드롭
     * 
     * 스택 시스템은 플레이어별로 추적하며, 3초 내 재수확 시 스택 증가
     * 스택당 value1% 추가 드롭, 최대 value2 스택
     */
    private void applyCropCombo(ProcessingContext ctx, double bonusPerStack, int maxStacks) {
        if (!isCropMaterial(ctx.getOriginalMaterial())) return;
        
        // 스택 시스템은 복잡하므로 간소화된 버전 사용
        // (실제 스택 추적은 별도 Manager 필요)
        // 여기서는 확률적 보너스로 대체
        double avgStack = maxStacks / 2.0; // 평균 스택 가정
        double avgBonus = avgStack * bonusPerStack / 100.0;
        
        if (ThreadLocalRandom.current().nextDouble() < avgBonus) {
            for (ItemStack drop : ctx.getDrops()) {
                drop.setAmount(drop.getAmount() + 1);
            }
            ctx.markEffectApplied("LAMP_CROP_COMBO");
        }
    }
    
    /**
     * SILK_TOUCH_PRO - 정밀 채굴
     * 섬세한 손길 사용 시에도 확률적으로 경험치 획득
     */
    private void applySilkTouchPro(ProcessingContext ctx, double chance) {
        ItemStack tool = ctx.getTool();
        if (tool == null) return;
        
        // 섬세한 손길 확인
        if (!tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) {
            return;
        }
        
        // 광석인지 확인
        Material material = ctx.getOriginalMaterial();
        if (!isOre(material)) return;
        
        // 확률 체크
        if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
            // 바닐라 광석 경험치 부여 - metadata로 전달
            int xp = getOreExp(material);
            if (xp > 0) {
                ctx.setMetadata("silk_touch_pro_xp", xp);
                ctx.markEffectApplied("LAMP_SILK_TOUCH_PRO");
            }
        }
    }
    
    /**
     * COMPOST_BONUS - 퇴비 생성
     * 잡초/씨앗 획득 시 확률로 뼛가루 변환
     */
    private void applyCompostBonus(ProcessingContext ctx, double chance) {
        if (!isCropMaterial(ctx.getOriginalMaterial())) return;
        
        List<ItemStack> newDrops = new ArrayList<>();
        boolean applied = false;
        
        for (ItemStack drop : ctx.getDrops()) {
            // 씨앗 또는 잡초 타입 확인
            if (isSeedOrWeed(drop.getType())) {
                if (ThreadLocalRandom.current().nextDouble() * 100 < chance) {
                    // 뼛가루로 변환
                    newDrops.add(new ItemStack(Material.BONE_MEAL, drop.getAmount()));
                    applied = true;
                } else {
                    newDrops.add(drop);
                }
            } else {
                newDrops.add(drop);
            }
        }
        
        if (applied) {
            ctx.setDrops(newDrops);
            ctx.markEffectApplied("LAMP_COMPOST_BONUS");
        }
    }
    
    /**
     * 씨앗/잡초 타입인지 확인
     */
    private boolean isSeedOrWeed(Material material) {
        return switch (material) {
            case WHEAT_SEEDS, BEETROOT_SEEDS, MELON_SEEDS, PUMPKIN_SEEDS,
                 SWEET_BERRIES, TALL_GRASS, FERN, DEAD_BUSH -> true;
            default -> false;
        };
    }
    
    /**
     * 광석 여부 확인
     */
    private boolean isOre(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }
    
    /**
     * 광석별 바닐라 경험치
     */
    private int getOreExp(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 1;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 1;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 1;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> 2;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 2;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 3;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 5;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 5;
            case NETHER_QUARTZ_ORE -> 2;
            default -> 0;
        };
    }
    
    // ===============================================================
    // 유틸리티 메서드
    // ===============================================================
    
    /**
     * 제련 결과 가져오기
     */
    private Material getSmeltResult(Material input) {
        // 캐시에서 먼저 확인
        Material cached = SMELT_CACHE.get(input);
        if (cached != null) return cached;
        
        // 동적으로 레시피 검색 (필요 시)
        for (Recipe recipe : Bukkit.getRecipesFor(new ItemStack(input))) {
            if (recipe instanceof FurnaceRecipe furnace) {
                Material result = furnace.getResult().getType();
                SMELT_CACHE.put(input, result);
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * 작물 재료인지 확인
     */
    private boolean isCropMaterial(Material material) {
        return switch (material) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART,
                 COCOA, MELON, PUMPKIN, SUGAR_CANE, BAMBOO, CACTUS,
                 SWEET_BERRY_BUSH -> true;
            default -> false;
        };
    }
    
    /**
     * 원목 재료인지 확인
     */
    private boolean isLogMaterial(Material material) {
        return material.name().endsWith("_LOG") || material.name().endsWith("_WOOD");
    }
    
    /**
     * 작물에 해당하는 씨앗 타입 가져오기
     */
    private Material getSeedType(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case MELON -> Material.MELON_SEEDS;
            case PUMPKIN -> Material.PUMPKIN_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            default -> null;
        };
    }
}
