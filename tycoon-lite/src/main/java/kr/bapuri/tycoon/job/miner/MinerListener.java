package kr.bapuri.tycoon.job.miner;

import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.common.GradeBonusConfig;
import kr.bapuri.tycoon.world.WorldManager;
import kr.bapuri.tycoon.world.WorldType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MinerListener - 광부 채굴 이벤트 리스너
 * 
 * Phase 4.B 구현:
 * - BlockBreakEvent 처리
 * - 광석 채굴 시 XP 부여
 * - Wild 월드에서만 광부 활동 인정
 */
public class MinerListener implements Listener {
    
    private final MinerExpService expService;
    private final WorldManager worldManager;
    
    // [Phase 승급효과] 등급별 보너스
    private GradeBonusConfig gradeBonusConfig;
    private MinerGradeService gradeService;
    
    // 광석 Material 목록
    private static final Set<Material> ORE_MATERIALS = EnumSet.of(
        // 석탄
        Material.COAL_ORE,
        Material.DEEPSLATE_COAL_ORE,
        // 구리
        Material.COPPER_ORE,
        Material.DEEPSLATE_COPPER_ORE,
        // 철
        Material.IRON_ORE,
        Material.DEEPSLATE_IRON_ORE,
        // 금
        Material.GOLD_ORE,
        Material.DEEPSLATE_GOLD_ORE,
        Material.NETHER_GOLD_ORE,
        // 레드스톤
        Material.REDSTONE_ORE,
        Material.DEEPSLATE_REDSTONE_ORE,
        // 청금석
        Material.LAPIS_ORE,
        Material.DEEPSLATE_LAPIS_ORE,
        // 다이아몬드
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        // 에메랄드
        Material.EMERALD_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        // 네더
        Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS
    );
    
    public MinerListener(JavaPlugin plugin, MinerExpService expService, WorldManager worldManager) {
        this.expService = expService;
        this.worldManager = worldManager;
    }
    
    // ===== [Phase 승급효과] Setter =====
    
    public void setGradeBonusConfig(GradeBonusConfig config) {
        this.gradeBonusConfig = config;
    }
    
    public void setGradeService(MinerGradeService service) {
        this.gradeService = service;
    }
    
    /**
     * 블록 파괴 이벤트 처리
     * 
     * MONITOR 우선순위: 다른 플러그인이 취소했는지 확인
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();
        
        // 광석인지 확인
        if (!isOre(material)) {
            return;
        }
        
        // 광부 직업인지 확인
        if (!expService.hasJob(player)) {
            return;
        }
        
        // Wild 월드인지 확인 (Town에서는 채굴 XP 미지급)
        if (worldManager != null) {
            WorldType worldType = worldManager.getWorldType(block.getWorld());
            if (worldType != WorldType.WILD) {
                return;
            }
        }
        
        // 경험치 부여
        long xp = expService.addMiningExp(player, material, 1);
        
        // 디버그 로그 (필요 시 제거)
        if (xp > 0) {
            // 액션바로 XP 표시 (선택적)
            // player.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
            //     new TextComponent("§a+" + xp + " XP"));
        }
    }
    
    /**
     * [Phase 승급효과] 블록 드롭 아이템 이벤트 처리
     * 
     * 등급별 yieldMulti에 따라 추가 드롭 확률 적용
     * 예: yieldMulti=1.05 → 5% 확률로 드롭 아이템 +1
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
        
        // 광석인지 확인
        if (!isOre(originalMaterial)) {
            return;
        }
        
        // 광부 직업인지 확인
        if (!expService.hasJob(player)) {
            return;
        }
        
        // Wild 월드인지 확인
        if (worldManager != null) {
            WorldType worldType = worldManager.getWorldType(block.getWorld());
            if (worldType != WorldType.WILD) {
                return;
            }
        }
        
        // 등급별 추가 드롭 확률 계산
        JobGrade grade = gradeService.getGrade(player);
        double yieldMulti = gradeBonusConfig.getYieldMultiplier(JobType.MINER, grade);
        double bonusChance = yieldMulti - 1.0; // 1.05 → 0.05 (5%)
        
        if (bonusChance <= 0) {
            return;
        }
        
        // 각 드롭 아이템에 대해 추가 드롭 확률 적용
        for (Item item : event.getItems()) {
            if (ThreadLocalRandom.current().nextDouble() < bonusChance) {
                ItemStack original = item.getItemStack();
                // 동일 아이템 1개 추가 드롭
                ItemStack bonus = original.clone();
                bonus.setAmount(1);
                block.getWorld().dropItemNaturally(block.getLocation(), bonus);
            }
        }
    }
    
    /**
     * 광석 Material인지 확인
     */
    public static boolean isOre(Material material) {
        return ORE_MATERIALS.contains(material);
    }
    
    /**
     * 등록된 광석 Material 목록
     */
    public static Set<Material> getOreMaterials() {
        return EnumSet.copyOf(ORE_MATERIALS);
    }
}
