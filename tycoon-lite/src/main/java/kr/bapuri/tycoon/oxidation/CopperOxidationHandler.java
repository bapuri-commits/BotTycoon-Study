package kr.bapuri.tycoon.oxidation;

import kr.bapuri.tycoon.job.miner.MinerConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * CopperOxidationHandler - 구리 산화 규칙 핸들러
 * 
 * Phase 4.B 리팩토링:
 * - MinerConfig 기반 설정화
 * - 산화 확률/주기/반경 설정 가능
 * 
 * 규칙:
 * - 산화는 물/비에 의해 가속/감속되지 않음
 * - 각 구리 블록은 설정된 주기마다 확률로 '산화 대기' 상태 진입
 * - 반경 N블록 내 더 낮은 산화 단계 존재 시 산화 중단 (팜 억제)
 */
public class CopperOxidationHandler implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final MinerConfig config;
    
    // 구리 블록 산화 단계 (낮을수록 덜 산화됨)
    private static final Map<Material, Integer> COPPER_OXIDATION_STAGE = new EnumMap<>(Material.class);
    
    // 다음 산화 단계
    private static final Map<Material, Material> NEXT_OXIDATION_STAGE = new EnumMap<>(Material.class);

    static {
        // 산화 단계 정의 (0 = 기본, 1 = Exposed, 2 = Weathered, 3 = Oxidized)
        // 구리 블록
        COPPER_OXIDATION_STAGE.put(Material.COPPER_BLOCK, 0);
        COPPER_OXIDATION_STAGE.put(Material.EXPOSED_COPPER, 1);
        COPPER_OXIDATION_STAGE.put(Material.WEATHERED_COPPER, 2);
        COPPER_OXIDATION_STAGE.put(Material.OXIDIZED_COPPER, 3);
        
        // 깎인 구리
        COPPER_OXIDATION_STAGE.put(Material.CUT_COPPER, 0);
        COPPER_OXIDATION_STAGE.put(Material.EXPOSED_CUT_COPPER, 1);
        COPPER_OXIDATION_STAGE.put(Material.WEATHERED_CUT_COPPER, 2);
        COPPER_OXIDATION_STAGE.put(Material.OXIDIZED_CUT_COPPER, 3);
        
        // 깎인 구리 계단
        COPPER_OXIDATION_STAGE.put(Material.CUT_COPPER_STAIRS, 0);
        COPPER_OXIDATION_STAGE.put(Material.EXPOSED_CUT_COPPER_STAIRS, 1);
        COPPER_OXIDATION_STAGE.put(Material.WEATHERED_CUT_COPPER_STAIRS, 2);
        COPPER_OXIDATION_STAGE.put(Material.OXIDIZED_CUT_COPPER_STAIRS, 3);
        
        // 깎인 구리 반블록
        COPPER_OXIDATION_STAGE.put(Material.CUT_COPPER_SLAB, 0);
        COPPER_OXIDATION_STAGE.put(Material.EXPOSED_CUT_COPPER_SLAB, 1);
        COPPER_OXIDATION_STAGE.put(Material.WEATHERED_CUT_COPPER_SLAB, 2);
        COPPER_OXIDATION_STAGE.put(Material.OXIDIZED_CUT_COPPER_SLAB, 3);
        
        // 다음 산화 단계 매핑
        NEXT_OXIDATION_STAGE.put(Material.COPPER_BLOCK, Material.EXPOSED_COPPER);
        NEXT_OXIDATION_STAGE.put(Material.EXPOSED_COPPER, Material.WEATHERED_COPPER);
        NEXT_OXIDATION_STAGE.put(Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER);
        
        NEXT_OXIDATION_STAGE.put(Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER);
        NEXT_OXIDATION_STAGE.put(Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER);
        NEXT_OXIDATION_STAGE.put(Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER);
        
        NEXT_OXIDATION_STAGE.put(Material.CUT_COPPER_STAIRS, Material.EXPOSED_CUT_COPPER_STAIRS);
        NEXT_OXIDATION_STAGE.put(Material.EXPOSED_CUT_COPPER_STAIRS, Material.WEATHERED_CUT_COPPER_STAIRS);
        NEXT_OXIDATION_STAGE.put(Material.WEATHERED_CUT_COPPER_STAIRS, Material.OXIDIZED_CUT_COPPER_STAIRS);
        
        NEXT_OXIDATION_STAGE.put(Material.CUT_COPPER_SLAB, Material.EXPOSED_CUT_COPPER_SLAB);
        NEXT_OXIDATION_STAGE.put(Material.EXPOSED_CUT_COPPER_SLAB, Material.WEATHERED_CUT_COPPER_SLAB);
        NEXT_OXIDATION_STAGE.put(Material.WEATHERED_CUT_COPPER_SLAB, Material.OXIDIZED_CUT_COPPER_SLAB);
    }

    // 산화 대기 블록 추적
    private final Set<Long> oxidationPendingBlocks = Collections.synchronizedSet(new HashSet<>());
    
    private BukkitTask oxidationTask;

    public CopperOxidationHandler(JavaPlugin plugin, MinerConfig config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = config;
    }

    /**
     * 산화 체크 태스크 시작
     */
    public void startOxidationTask() {
        if (!config.isOxidationEnabled()) {
            logger.info("[CopperOxidation] 산화 시스템 비활성화됨");
            return;
        }
        
        if (oxidationTask != null) {
            oxidationTask.cancel();
        }
        
        // 설정에서 주기 로드 (분 → ticks)
        int intervalMinutes = config.getOxidationCheckIntervalMinutes();
        long intervalTicks = intervalMinutes * 60L * 20L;  // 분 → 초 → ticks
        
        oxidationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processOxidation, 
            intervalTicks, intervalTicks);
        
        logger.info(String.format("[CopperOxidation] 산화 체크 태스크 시작 (주기: %d분, 확률: %.2f%%, 반경: %d)", 
                intervalMinutes, 
                config.getOxidationChance() * 100, 
                config.getFarmPreventionRadius()));
    }

    /**
     * 산화 체크 태스크 중지
     */
    public void stopOxidationTask() {
        if (oxidationTask != null) {
            oxidationTask.cancel();
            oxidationTask = null;
        }
    }
    
    /**
     * 설정 리로드 후 태스크 재시작
     */
    public void reload() {
        stopOxidationTask();
        startOxidationTask();
    }

    /**
     * 바닐라 산화 이벤트 차단 (커스텀 규칙으로 대체)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (!config.isOxidationEnabled()) {
            return;
        }
        
        Block block = event.getBlock();
        Material currentType = block.getType();
        Material newType = event.getNewState().getType();
        
        // 구리 산화 이벤트인지 확인
        if (!isCopperBlock(currentType) || !isCopperBlock(newType)) {
            return;
        }
        
        // 바닐라 산화 차단 (커스텀 규칙 적용)
        event.setCancelled(true);
        
        // 커스텀 산화 체크
        if (canOxidize(block)) {
            // 산화 대기 상태로 등록
            long key = blockKey(block);
            oxidationPendingBlocks.add(key);
        }
    }

    /**
     * 주기적 산화 처리
     */
    private void processOxidation() {
        if (!config.isOxidationEnabled()) {
            return;
        }
        
        int processed = 0;
        int oxidized = 0;
        
        double oxidationChance = config.getOxidationChance();
        
        // 산화 대기 블록 처리
        Iterator<Long> iterator = oxidationPendingBlocks.iterator();
        List<Long> toRemove = new ArrayList<>();
        List<Block> toOxidize = new ArrayList<>();
        
        while (iterator.hasNext()) {
            Long key = iterator.next();
            Block block = blockFromKey(key);
            
            if (block == null || !block.getChunk().isLoaded()) {
                toRemove.add(key);
                continue;
            }
            
            if (!isCopperBlock(block.getType())) {
                toRemove.add(key);
                continue;
            }
            
            processed++;
            
            // 설정된 확률로 체크
            if (ThreadLocalRandom.current().nextDouble() > oxidationChance) {
                continue;
            }
            
            // 산화 가능 여부 체크
            if (canOxidize(block)) {
                toOxidize.add(block);
            }
            
            toRemove.add(key);
        }
        
        // 제거
        oxidationPendingBlocks.removeAll(toRemove);
        
        // 산화 실행 (메인 스레드에서)
        for (Block block : toOxidize) {
            oxidizeBlock(block);
            oxidized++;
        }
        
        if (oxidized > 0) {
            logger.info("[CopperOxidation] 산화 처리: " + oxidized + "/" + processed + " 블록");
        }
    }

    /**
     * 블록이 산화 가능한지 확인
     */
    public boolean canOxidize(Block block) {
        Material type = block.getType();
        
        // 구리 블록인지
        if (!isCopperBlock(type)) {
            return false;
        }
        
        // 이미 최대 산화 상태인지
        if (!NEXT_OXIDATION_STAGE.containsKey(type)) {
            return false;
        }
        
        // 왁스 처리된 블록인지 (Waxed)
        if (type.name().contains("WAXED")) {
            return false;
        }
        
        // ★ 핵심: 설정된 반경 내 낮은 산화 단계 존재 시 산화 중단
        if (hasLowerOxidationNearby(block)) {
            return false;
        }
        
        return true;
    }

    /**
     * 반경 내 더 낮은 산화 단계의 구리 블록 존재 여부
     * (팜 억제 핵심 로직)
     */
    private boolean hasLowerOxidationNearby(Block block) {
        int currentStage = getOxidationStage(block.getType());
        if (currentStage <= 0) {
            // 기본 상태는 항상 산화 가능
            return false;
        }
        
        int radius = config.getFarmPreventionRadius();
        World world = block.getWorld();
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    Block nearby = world.getBlockAt(bx + x, by + y, bz + z);
                    Material nearbyType = nearby.getType();
                    
                    if (!isCopperBlock(nearbyType)) continue;
                    if (nearbyType.name().contains("WAXED")) continue;
                    
                    int nearbyStage = getOxidationStage(nearbyType);
                    if (nearbyStage < currentStage) {
                        // 더 낮은 단계 발견 → 산화 중단
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * 블록 산화 실행
     */
    private void oxidizeBlock(Block block) {
        Material current = block.getType();
        Material next = NEXT_OXIDATION_STAGE.get(current);
        
        if (next != null) {
            block.setType(next);
        }
    }

    /**
     * 공기에 노출된 면 수 계산 (산화 속도 가속용 - 향후 확장)
     */
    @SuppressWarnings("unused")
    private int getExposedFaces(Block block) {
        int exposed = 0;
        BlockFace[] faces = {
            BlockFace.UP, BlockFace.DOWN, 
            BlockFace.NORTH, BlockFace.SOUTH, 
            BlockFace.EAST, BlockFace.WEST
        };
        
        for (BlockFace face : faces) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType() == Material.AIR || 
                adjacent.getType() == Material.CAVE_AIR) {
                exposed++;
            }
        }
        
        return exposed;
    }

    /**
     * 산화 단계 조회
     */
    public int getOxidationStage(Material material) {
        return COPPER_OXIDATION_STAGE.getOrDefault(material, -1);
    }

    /**
     * 구리 블록인지 확인
     */
    public boolean isCopperBlock(Material material) {
        return COPPER_OXIDATION_STAGE.containsKey(material);
    }

    // ========== 블록 키 유틸리티 ==========

    private long blockKey(Block block) {
        return ((long) block.getX() & 0x3FFFFFF) << 38 |
               ((long) block.getZ() & 0x3FFFFFF) << 12 |
               ((long) block.getY() & 0xFFF);
    }

    private Block blockFromKey(long key) {
        int x = (int) (key >> 38);
        int z = (int) ((key >> 12) & 0x3FFFFFF);
        int y = (int) (key & 0xFFF);
        
        // Sign extension
        if (x >= 0x2000000) x -= 0x4000000;
        if (z >= 0x2000000) z -= 0x4000000;
        
        // 기본 월드에서 찾기 (TODO: 월드 정보도 저장 필요)
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return null;
        
        return world.getBlockAt(x, y, z);
    }

    /**
     * 수동으로 블록 산화 대기 등록
     */
    public void registerForOxidation(Block block) {
        if (config.isOxidationEnabled() && isCopperBlock(block.getType())) {
            oxidationPendingBlocks.add(blockKey(block));
        }
    }
    
    /**
     * 현재 설정 정보 조회
     */
    public String getConfigInfo() {
        return String.format("산화 시스템: %s, 주기: %d분, 확률: %.2f%%, 반경: %d",
                config.isOxidationEnabled() ? "활성화" : "비활성화",
                config.getOxidationCheckIntervalMinutes(),
                config.getOxidationChance() * 100,
                config.getFarmPreventionRadius());
    }
}
