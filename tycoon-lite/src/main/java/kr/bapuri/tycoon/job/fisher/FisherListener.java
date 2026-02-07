package kr.bapuri.tycoon.job.fisher;

import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.common.GradeBonusConfig;
import kr.bapuri.tycoon.world.WorldManager;
import kr.bapuri.tycoon.world.WorldType;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * FisherListener - 어부 낚시 이벤트 리스너
 * 
 * Phase 4.D:
 * - PlayerFishEvent 처리
 * - 희귀도 롤 (Town/Wild 구분)
 * - XP 부여 (희귀도 배율 적용)
 * - totalFished 기록
 */
public class FisherListener implements Listener {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final FisherExpService expService;
    private final WorldManager worldManager;
    private final FishRarityDistribution rarityDistribution;
    
    // [Phase 승급효과] 등급별 보너스
    private GradeBonusConfig gradeBonusConfig;
    private FisherGradeService gradeService;
    
    // Lure 인챈트 1레벨당 감소 틱 (바닐라: 5초 = 100틱)
    private static final int LURE_TICKS_PER_LEVEL = 100;
    
    public FisherListener(JavaPlugin plugin, FisherExpService expService, 
                          WorldManager worldManager, FishRarityDistribution rarityDistribution) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.expService = expService;
        this.worldManager = worldManager;
        this.rarityDistribution = rarityDistribution;
    }
    
    // ===== [Phase 승급효과] Setter =====
    
    public void setGradeBonusConfig(GradeBonusConfig config) {
        this.gradeBonusConfig = config;
    }
    
    public void setGradeService(FisherGradeService service) {
        this.gradeService = service;
    }
    
    /**
     * [Phase 승급효과] 낚시 시작 시 Lure 보너스 적용
     * 
     * Paper API를 사용하여 minWaitTime/maxWaitTime 감소
     * Lure 1레벨당 약 5초(100틱) 감소
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFishingStart(PlayerFishEvent event) {
        // FISHING 상태만 처리 (낚싯대 던짐)
        if (event.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        
        // 보너스 설정이 없으면 무시
        if (gradeBonusConfig == null || gradeService == null) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // 어부 직업인지 확인
        if (!expService.hasJob(player)) {
            return;
        }
        
        // 등급별 Lure 보너스 조회
        JobGrade grade = gradeService.getGrade(player);
        int lureBonus = gradeBonusConfig.getLureBonus(grade);
        
        if (lureBonus <= 0) {
            return;
        }
        
        // FishHook의 대기 시간 감소
        FishHook hook = event.getHook();
        int reduction = lureBonus * LURE_TICKS_PER_LEVEL;
        
        // minWaitTime과 maxWaitTime 감소 (최소 20틱 = 1초 보장)
        int currentMin = hook.getMinWaitTime();
        int currentMax = hook.getMaxWaitTime();
        
        int newMin = Math.max(20, currentMin - reduction);
        int newMax = Math.max(40, currentMax - reduction);
        
        // newMax는 newMin보다 커야 함
        if (newMax <= newMin) {
            newMax = newMin + 20;
        }
        
        hook.setMinWaitTime(newMin);
        hook.setMaxWaitTime(newMax);
        
        // 디버그 로그
        if (plugin.getConfig().getBoolean("debug.fisher", false)) {
            logger.info(String.format("[Fisher] %s Lure bonus applied: -%d ticks (min: %d→%d, max: %d→%d)",
                    player.getName(), reduction, currentMin, newMin, currentMax, newMax));
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        // CAUGHT_FISH 상태만 처리
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        
        Player player = event.getPlayer();
        Entity caught = event.getCaught();
        
        // 아이템이 아닌 경우 무시
        if (!(caught instanceof Item itemEntity)) {
            return;
        }
        
        ItemStack caughtItem = itemEntity.getItemStack();
        Material material = caughtItem.getType();
        
        // 물고기인지 확인
        if (!FishLootTable.isFish(material)) {
            // 물고기가 아니면 기본 XP만 부여 (쓰레기, 보물 등)
            if (FishLootTable.isFishable(material)) {
                expService.addExp(player, 5L);  // 기본 5 XP
            }
            return;
        }
        
        // 환경 확인 (Town / Wild)
        WorldType worldType = worldManager.getWorldType(player.getWorld());
        
        // 희귀도 롤
        FishRarity rarity = rarityDistribution.roll(worldType);
        
        // [Phase 승급효과] 등급별 희귀도 보너스 적용
        if (gradeBonusConfig != null && gradeService != null && expService.hasJob(player)) {
            JobGrade grade = gradeService.getGrade(player);
            double rareChanceBonus = gradeBonusConfig.getRareChanceBonus(grade);
            
            if (rareChanceBonus > 0 && rarity.ordinal() < FishRarity.LEGENDARY.ordinal()) {
                // 희귀도 업그레이드 확률 적용
                if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < rareChanceBonus) {
                    FishRarity upgraded = rarity.next();
                    if (upgraded != null) {
                        rarity = upgraded;
                    }
                }
            }
        }
        
        // [Phase 승급효과] 아이템에 희귀도 적용 (상점 가격 보너스용)
        if (rarity != FishRarity.COMMON && FishRarity.isFishMaterial(material)) {
            FishRarity.applyRarity(caughtItem, rarity);
        }
        
        // XP 부여 (희귀도 배율 적용)
        int count = caughtItem.getAmount();
        long xpGranted = expService.addFishingExp(player, material, rarity, count);
        
        // 희귀 등급 이상이면 메시지 표시
        if (rarity.ordinal() >= FishRarity.RARE.ordinal()) {
            player.sendMessage(String.format("§b[어부] %s 등급 물고기! §7(+%,d XP)",
                    rarity.getColoredName(), xpGranted));
        }
        
        // 디버그 로그 (옵션)
        if (plugin.getConfig().getBoolean("debug.fisher", false)) {
            logger.info(String.format("[Fisher] %s caught %s (%s) in %s, +%d XP",
                    player.getName(), material.name(), rarity.name(), worldType.name(), xpGranted));
        }
    }
}
