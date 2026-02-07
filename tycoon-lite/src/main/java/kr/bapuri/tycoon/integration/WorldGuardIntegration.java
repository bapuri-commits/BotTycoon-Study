package kr.bapuri.tycoon.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * WorldGuard 연동 유틸리티
 * 
 * WorldGuard 리전 정보를 조회하는 헬퍼 메서드 제공:
 * - PVP 가능 여부 확인
 * - 빌드 가능 여부 확인
 * - 특정 리전 내 위치 확인
 */
public class WorldGuardIntegration {
    
    private final Plugin plugin;
    private final Logger logger;
    private boolean available = false;
    
    public WorldGuardIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.logger = Logger.getLogger("TycoonLite.WorldGuard");
        
        checkAvailability();
    }
    
    private void checkAvailability() {
        try {
            Plugin wg = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
            if (wg != null && wg.isEnabled()) {
                // WorldGuard API 접근 테스트
                WorldGuard.getInstance();
                available = true;
                logger.info("[WorldGuard] 연동 초기화 완료");
            }
        } catch (Exception e) {
            logger.warning("[WorldGuard] 연동 실패: " + e.getMessage());
            available = false;
        }
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * 해당 위치에서 PVP가 가능한지 확인
     */
    public boolean canPVP(Location location) {
        if (!available || location == null || location.getWorld() == null) {
            return true;  // WorldGuard 없으면 기본 허용
        }
        
        try {
            ApplicableRegionSet regions = getRegions(location);
            if (regions == null) return true;
            
            // PVP 플래그 확인
            StateFlag.State pvpState = regions.queryState(null, Flags.PVP);
            return pvpState != StateFlag.State.DENY;
            
        } catch (Exception e) {
            logger.warning("[WorldGuard] PVP 체크 오류: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * 해당 위치에서 플레이어가 빌드할 수 있는지 확인
     */
    public boolean canBuild(Player player, Location location) {
        if (!available || player == null || location == null) {
            return true;
        }
        
        try {
            ApplicableRegionSet regions = getRegions(location);
            if (regions == null) return true;
            
            // BUILD 플래그 확인
            return regions.testState(
                WorldGuardPlugin.inst().wrapPlayer(player),
                Flags.BUILD
            );
            
        } catch (Exception e) {
            logger.warning("[WorldGuard] 빌드 체크 오류: " + e.getMessage());
            return true;
        }
    }
    
    /**
     * 해당 위치가 특정 리전 안에 있는지 확인
     */
    public boolean isInRegion(Location location, String regionId) {
        if (!available || location == null || regionId == null) {
            return false;
        }
        
        try {
            ApplicableRegionSet regions = getRegions(location);
            if (regions == null) return false;
            
            for (ProtectedRegion region : regions) {
                if (region.getId().equalsIgnoreCase(regionId)) {
                    return true;
                }
            }
            return false;
            
        } catch (Exception e) {
            logger.warning("[WorldGuard] 리전 체크 오류: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 해당 위치의 리전 세트 가져오기
     */
    private ApplicableRegionSet getRegions(Location location) {
        if (!available || location == null) return null;
        
        try {
            World world = location.getWorld();
            if (world == null) return null;
            
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
            
            if (regionManager == null) return null;
            
            BlockVector3 position = BlockVector3.at(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
            );
            
            return regionManager.getApplicableRegions(position);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 해당 위치의 가장 우선순위 높은 리전 ID 반환
     */
    public String getPrimaryRegionId(Location location) {
        if (!available || location == null) return null;
        
        try {
            ApplicableRegionSet regions = getRegions(location);
            if (regions == null || regions.size() == 0) return null;
            
            // 가장 우선순위 높은 리전 반환
            ProtectedRegion primary = null;
            for (ProtectedRegion region : regions) {
                if (primary == null || region.getPriority() > primary.getPriority()) {
                    primary = region;
                }
            }
            
            return primary != null ? primary.getId() : null;
            
        } catch (Exception e) {
            return null;
        }
    }
}
