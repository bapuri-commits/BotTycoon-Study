package kr.bapuri.tycoon.job.farmer;

import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.common.GradeBonusConfig;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FarmerListener - 농부 수확 이벤트 리스너
 * 
 * Phase 4.C 구현:
 * - BlockBreakEvent 처리 (성숙한 작물)
 * - PlayerInteractEvent 처리 (오른쪽 클릭 수확)
 * - Town + Wild 월드 모두 적용 (농사는 Town 중심)
 */
public class FarmerListener implements Listener {
    
    private final FarmerExpService expService;
    
    // [Phase 승급효과] 등급별 보너스
    private GradeBonusConfig gradeBonusConfig;
    private FarmerGradeService gradeService;
    
    // ===== 씨앗 Material 목록 (낮은 배율 적용) =====
    private static final Set<Material> SEED_MATERIALS = EnumSet.of(
        Material.WHEAT_SEEDS,
        Material.BEETROOT_SEEDS,
        Material.MELON_SEEDS,
        Material.PUMPKIN_SEEDS,
        Material.TORCHFLOWER_SEEDS,
        Material.PITCHER_POD
    );
    
    // ===== 작물 Material → 드롭 아이템 매핑 =====
    private static final Map<Material, Material> CROP_TO_DROP = new EnumMap<>(Material.class);
    
    // ===== 성숙 시 파괴로 XP를 주는 작물 =====
    private static final Set<Material> AGEABLE_CROPS = EnumSet.of(
        Material.WHEAT,
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOTS,
        Material.NETHER_WART,
        Material.COCOA
    );
    
    // ===== 오른쪽 클릭 수확 작물 =====
    private static final Set<Material> RIGHT_CLICK_HARVEST_CROPS = EnumSet.of(
        Material.SWEET_BERRY_BUSH,
        Material.CAVE_VINES,           // Glow Berries
        Material.CAVE_VINES_PLANT      // Glow Berries (줄기)
    );
    
    // ===== 블록 파괴로 수확하는 작물 (성숙 체크 불필요) =====
    private static final Set<Material> BREAK_HARVEST_CROPS = EnumSet.of(
        Material.MELON,
        Material.PUMPKIN,
        Material.SUGAR_CANE,
        Material.BAMBOO,
        Material.CACTUS,
        Material.KELP,
        Material.KELP_PLANT,
        Material.BROWN_MUSHROOM,
        Material.RED_MUSHROOM,
        Material.CHORUS_FLOWER,
        Material.CHORUS_PLANT
    );
    
    static {
        // 작물 블록 → 드롭 아이템 매핑
        CROP_TO_DROP.put(Material.WHEAT, Material.WHEAT);
        CROP_TO_DROP.put(Material.CARROTS, Material.CARROT);
        CROP_TO_DROP.put(Material.POTATOES, Material.POTATO);
        CROP_TO_DROP.put(Material.BEETROOTS, Material.BEETROOT);
        CROP_TO_DROP.put(Material.NETHER_WART, Material.NETHER_WART);
        CROP_TO_DROP.put(Material.COCOA, Material.COCOA_BEANS);
        CROP_TO_DROP.put(Material.MELON, Material.MELON_SLICE);
        CROP_TO_DROP.put(Material.PUMPKIN, Material.PUMPKIN);
        CROP_TO_DROP.put(Material.SUGAR_CANE, Material.SUGAR_CANE);
        CROP_TO_DROP.put(Material.BAMBOO, Material.BAMBOO);
        CROP_TO_DROP.put(Material.CACTUS, Material.CACTUS);
        CROP_TO_DROP.put(Material.KELP, Material.KELP);
        CROP_TO_DROP.put(Material.KELP_PLANT, Material.KELP);
        CROP_TO_DROP.put(Material.BROWN_MUSHROOM, Material.BROWN_MUSHROOM);
        CROP_TO_DROP.put(Material.RED_MUSHROOM, Material.RED_MUSHROOM);
        CROP_TO_DROP.put(Material.CHORUS_FLOWER, Material.CHORUS_FRUIT);
        CROP_TO_DROP.put(Material.CHORUS_PLANT, Material.CHORUS_FRUIT);
        CROP_TO_DROP.put(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES);
        CROP_TO_DROP.put(Material.CAVE_VINES, Material.GLOW_BERRIES);
        CROP_TO_DROP.put(Material.CAVE_VINES_PLANT, Material.GLOW_BERRIES);
    }
    
    public FarmerListener(JavaPlugin plugin, FarmerExpService expService) {
        this.expService = expService;
    }
    
    // ===== [Phase 승급효과] Setter =====
    
    public void setGradeBonusConfig(GradeBonusConfig config) {
        this.gradeBonusConfig = config;
    }
    
    public void setGradeService(FarmerGradeService service) {
        this.gradeService = service;
    }
    
    /**
     * 블록 파괴 이벤트 처리
     * MONITOR 우선순위: 다른 플러그인이 취소했는지 확인
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();
        
        // 농부 직업인지 확인
        if (!expService.hasJob(player)) {
            return;
        }
        
        // 성숙한 Ageable 작물 체크
        if (AGEABLE_CROPS.contains(blockType)) {
            if (isMature(block)) {
                Material dropMaterial = CROP_TO_DROP.getOrDefault(blockType, blockType);
                expService.addHarvestExp(player, dropMaterial, 1);
            }
            return;
        }
        
        // 블록 파괴로 수확하는 작물 (성숙 체크 불필요)
        if (BREAK_HARVEST_CROPS.contains(blockType)) {
            Material dropMaterial = CROP_TO_DROP.getOrDefault(blockType, blockType);
            expService.addHarvestExp(player, dropMaterial, 1);
        }
    }
    
    /**
     * 오른쪽 클릭 수확 처리 (Sweet Berries, Glow Berries)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 오른쪽 클릭만
        if (!event.getAction().name().contains("RIGHT")) {
            return;
        }
        
        // 메인 핸드만 (중복 이벤트 방지)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        
        Material blockType = block.getType();
        
        // 오른쪽 클릭 수확 가능한 작물인지
        if (!RIGHT_CLICK_HARVEST_CROPS.contains(blockType)) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // 농부 직업인지 확인
        if (!expService.hasJob(player)) {
            return;
        }
        
        // Sweet Berry Bush: age >= 2 (최소 수확 가능 상태)
        // Cave Vines: berries가 있는 경우
        if (canRightClickHarvest(block)) {
            Material dropMaterial = CROP_TO_DROP.getOrDefault(blockType, blockType);
            expService.addHarvestExp(player, dropMaterial, 1);
        }
    }
    
    /**
     * 작물이 성숙했는지 확인
     */
    private boolean isMature(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return false;
    }
    
    /**
     * 오른쪽 클릭 수확 가능한지 확인
     */
    private boolean canRightClickHarvest(Block block) {
        BlockData data = block.getBlockData();
        
        // Sweet Berry Bush: age >= 2
        if (block.getType() == Material.SWEET_BERRY_BUSH) {
            if (data instanceof Ageable ageable) {
                return ageable.getAge() >= 2;
            }
        }
        
        // Cave Vines: berries 속성 확인
        if (block.getType() == Material.CAVE_VINES || 
            block.getType() == Material.CAVE_VINES_PLANT) {
            // CaveVinesPlant는 berries 메서드가 있음
            if (data instanceof org.bukkit.block.data.type.CaveVinesPlant caveVines) {
                return caveVines.isBerries();
            }
            if (data instanceof org.bukkit.block.data.type.CaveVines caveVines) {
                return caveVines.isBerries();
            }
        }
        
        return false;
    }
    
    /**
     * [Phase 승급효과] 블록 드롭 아이템 이벤트 처리
     * 
     * 등급별 yieldMulti/seedMulti에 따라 추가 드롭 확률 적용
     * - 작물: yieldMulti (예: 1.15 → 15% 확률로 +1)
     * - 씨앗: seedMulti (예: 1.05 → 5% 확률로 +1)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        // 보너스 설정이 없으면 무시
        if (gradeBonusConfig == null || gradeService == null) {
            return;
        }
        
        Player player = event.getPlayer();
        Block block = event.getBlockState().getBlock();
        Material originalMaterial = event.getBlockState().getType();
        
        // 작물인지 확인
        if (!isCrop(originalMaterial)) {
            return;
        }
        
        // 농부 직업인지 확인
        if (!expService.hasJob(player)) {
            return;
        }
        
        // 등급 조회
        JobGrade grade = gradeService.getGrade(player);
        double yieldMulti = gradeBonusConfig.getYieldMultiplier(JobType.FARMER, grade);
        double seedMulti = gradeBonusConfig.getSeedMultiplier(grade);
        
        // [Phase 승급효과] 프라임/트로피 확률 (추가 드롭에도 적용하기 위해 미리 계산)
        double primeChance = gradeBonusConfig.getPrimeChanceBonus(grade);
        double trophyChance = gradeBonusConfig.getTrophyChanceBonus(grade);
        
        // 각 드롭 아이템에 대해 추가 드롭 확률 적용
        for (Item item : event.getItems()) {
            ItemStack original = item.getItemStack();
            Material dropMaterial = original.getType();
            
            // 씨앗 여부에 따라 배율 선택
            double bonusChance;
            if (SEED_MATERIALS.contains(dropMaterial)) {
                bonusChance = seedMulti - 1.0; // 씨앗은 낮은 배율
            } else {
                bonusChance = yieldMulti - 1.0; // 작물은 높은 배율
            }
            
            if (bonusChance <= 0) {
                continue;
            }
            
            // 확률에 따라 추가 드롭
            if (ThreadLocalRandom.current().nextDouble() < bonusChance) {
                ItemStack bonus = original.clone();
                bonus.setAmount(1);
                
                // [Phase 승급효과] 추가 드롭에도 별도 확률 굴림으로 등급 적용
                applyRandomGrade(bonus, primeChance, trophyChance);
                
                block.getWorld().dropItemNaturally(block.getLocation(), bonus);
            }
        }
        
        // [Phase 승급효과] 원본 드롭에 등급 적용 (씨앗 제외)
        if (primeChance > 0 || trophyChance > 0) {
            for (Item item : event.getItems()) {
                ItemStack dropItem = item.getItemStack();
                Material dropMaterial = dropItem.getType();
                
                // 씨앗은 등급 적용 제외
                if (SEED_MATERIALS.contains(dropMaterial)) {
                    continue;
                }
                
                // 등급 적용 가능한 작물인지 확인
                if (!CropGrade.isGradeableCrop(dropMaterial)) {
                    continue;
                }
                
                // 등급 결정 및 적용
                CropGrade cropGrade = rollCropGrade(primeChance, trophyChance);
                if (cropGrade != CropGrade.NORMAL) {
                    CropGrade.applyGrade(dropItem, cropGrade);
                    item.setItemStack(dropItem);
                }
            }
        }
    }
    
    /**
     * [Phase 승급효과] 확률 굴림으로 등급 결정
     */
    private CropGrade rollCropGrade(double primeChance, double trophyChance) {
        if (primeChance <= 0 && trophyChance <= 0) {
            return CropGrade.NORMAL;
        }
        
        double roll = ThreadLocalRandom.current().nextDouble();
        
        if (trophyChance > 0 && roll < trophyChance) {
            return CropGrade.TROPHY;
        } else if (primeChance > 0 && roll < primeChance + trophyChance) {
            return CropGrade.PRIME;
        }
        
        return CropGrade.NORMAL;
    }
    
    /**
     * [Phase 승급효과] 아이템에 랜덤 등급 적용 (씨앗 제외)
     */
    private void applyRandomGrade(ItemStack item, double primeChance, double trophyChance) {
        if (item == null || item.getType().isAir()) return;
        
        // 씨앗은 등급 적용 제외
        if (SEED_MATERIALS.contains(item.getType())) return;
        
        // 등급 적용 가능한 작물인지 확인
        if (!CropGrade.isGradeableCrop(item.getType())) return;
        
        // 등급 결정 및 적용
        CropGrade cropGrade = rollCropGrade(primeChance, trophyChance);
        if (cropGrade != CropGrade.NORMAL) {
            CropGrade.applyGrade(item, cropGrade);
        }
    }
    
    /**
     * 작물 Material인지 확인
     */
    private boolean isCrop(Material material) {
        return CROP_TO_DROP.containsKey(material);
    }
    
    /**
     * XP 보상이 있는 작물 Material 목록
     */
    public static Set<Material> getCropMaterials() {
        return EnumSet.copyOf(CROP_TO_DROP.keySet());
    }
}
