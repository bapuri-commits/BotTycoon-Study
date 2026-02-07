package kr.bapuri.tycoon.job.farmer;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * FarmlandLimitSystem - 청크당 농지 제한 시스템
 * 
 * Phase 4.C 구현:
 * - 청크당 최대 농지 블록 수 제한 (기본 196개)
 * - 토글 가능 (config 기반)
 * - 호미로 농지 생성 시 체크
 */
public class FarmlandLimitSystem implements Listener {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final FarmerConfig config;
    
    // 호미 종류
    private static final Material[] HOE_MATERIALS = {
        Material.WOODEN_HOE,
        Material.STONE_HOE,
        Material.IRON_HOE,
        Material.GOLDEN_HOE,
        Material.DIAMOND_HOE,
        Material.NETHERITE_HOE
    };
    
    public FarmlandLimitSystem(JavaPlugin plugin, FarmerConfig config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = config;
    }
    
    /**
     * 호미로 농지 생성 시 체크
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 시스템 비활성화 시 무시
        if (!config.isFarmlandLimitEnabled()) {
            return;
        }
        
        // 오른쪽 클릭만
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        
        // 흙 계열 블록만 (농지로 변환 가능)
        Material blockType = clickedBlock.getType();
        if (!isTillable(blockType)) {
            return;
        }
        
        // 호미를 들고 있는지 확인
        ItemStack heldItem = event.getItem();
        if (heldItem == null || !isHoe(heldItem.getType())) {
            return;
        }
        
        Player player = event.getPlayer();
        Chunk chunk = clickedBlock.getChunk();
        
        // 청크 내 농지 수 체크
        int currentFarmland = countFarmlandInChunk(chunk);
        int maxFarmland = config.getMaxFarmlandPerChunk();
        
        if (currentFarmland >= maxFarmland) {
            event.setCancelled(true);
            player.sendMessage(String.format(
                "§c이 청크에는 더 이상 농지를 만들 수 없습니다. §7(%d/%d)",
                currentFarmland, maxFarmland
            ));
        }
    }
    
    /**
     * 청크 내 농지 블록 수 카운트
     * 
     * 성능 최적화: Y 범위를 0~120으로 제한 (평지맵 기준)
     */
    public int countFarmlandInChunk(Chunk chunk) {
        int count = 0;
        
        // 성능 최적화: 평지맵 기준 Y 범위 (0~120)
        int minY = Math.max(chunk.getWorld().getMinHeight(), 0);
        int maxY = Math.min(chunk.getWorld().getMaxHeight(), 120);
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == Material.FARMLAND) {
                        count++;
                    }
                }
            }
        }
        
        return count;
    }
    
    /**
     * 청크에 농지를 더 만들 수 있는지 확인
     */
    public boolean canCreateFarmland(Chunk chunk) {
        if (!config.isFarmlandLimitEnabled()) {
            return true;
        }
        
        int current = countFarmlandInChunk(chunk);
        return current < config.getMaxFarmlandPerChunk();
    }
    
    /**
     * 현재 청크의 농지 정보 문자열
     */
    public String getFarmlandInfo(Chunk chunk) {
        int current = countFarmlandInChunk(chunk);
        int max = config.getMaxFarmlandPerChunk();
        return String.format("§7농지: %d/%d (%.1f%%)", current, max, (double) current / max * 100);
    }
    
    /**
     * 흙으로 변환 가능한 블록인지
     */
    private boolean isTillable(Material material) {
        return material == Material.DIRT ||
               material == Material.GRASS_BLOCK ||
               material == Material.DIRT_PATH ||
               material == Material.COARSE_DIRT ||
               material == Material.ROOTED_DIRT;
    }
    
    /**
     * 호미인지 확인
     */
    private boolean isHoe(Material material) {
        for (Material hoe : HOE_MATERIALS) {
            if (hoe == material) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 시스템 활성화/비활성화
     */
    public void setEnabled(boolean enabled) {
        config.setFarmlandLimitEnabled(enabled);
        logger.info("[FarmlandLimit] 시스템 " + (enabled ? "활성화" : "비활성화"));
    }
    
    /**
     * 시스템 활성화 상태
     */
    public boolean isEnabled() {
        return config.isFarmlandLimitEnabled();
    }
}
