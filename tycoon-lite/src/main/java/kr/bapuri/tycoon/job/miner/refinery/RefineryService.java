package kr.bapuri.tycoon.job.miner.refinery;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * RefineryService - 정제소 서비스 (Stub)
 * 
 * [Phase 4.B] 정제소 기반 구조만 준비
 * 
 * <p>Lite에서는 비활성화. 향후 확장을 위한 인터페이스/구조만 정의.</p>
 * 
 * <h2>향후 구현 예정</h2>
 * <ul>
 *   <li>원석 → 주괴 정제</li>
 *   <li>정제소 업그레이드 (속도, 효율)</li>
 *   <li>자동 정제 시스템</li>
 * </ul>
 */
public class RefineryService {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private boolean enabled = false;
    
    public RefineryService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    /**
     * 서비스 초기화
     */
    public void initialize() {
        // [STUB] Lite에서는 비활성화
        logger.info("[RefineryService] 정제소 시스템 (Stub) - 비활성화됨");
    }
    
    /**
     * 서비스 종료
     */
    public void shutdown() {
        // [STUB] 정리 작업
    }
    
    /**
     * 활성화 상태 확인
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 활성화/비활성화 설정
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("[RefineryService] " + (enabled ? "활성화" : "비활성화"));
    }
    
    // ===== Stub 메서드 =====
    
    /**
     * [STUB] 플레이어의 정제소 데이터 조회
     */
    public RefineryData getRefineryData(UUID uuid) {
        // [STUB] 향후 구현
        return null;
    }
    
    /**
     * [STUB] 정제 시작
     */
    public boolean startRefining(Player player, String oreId, int amount) {
        if (!enabled) {
            player.sendMessage("§c정제소 시스템이 비활성화되어 있습니다.");
            return false;
        }
        
        // [STUB] 향후 구현
        player.sendMessage("§e정제소 시스템은 향후 업데이트에서 제공될 예정입니다.");
        return false;
    }
    
    /**
     * [STUB] 정제 완료 수집
     */
    public boolean collectRefined(Player player) {
        if (!enabled) {
            return false;
        }
        
        // [STUB] 향후 구현
        return false;
    }
    
    /**
     * [STUB] 정제소 업그레이드
     */
    public boolean upgradeRefinery(Player player, String upgradeType) {
        if (!enabled) {
            player.sendMessage("§c정제소 시스템이 비활성화되어 있습니다.");
            return false;
        }
        
        // [STUB] 향후 구현
        return false;
    }
    
    /**
     * [STUB] 정제소 GUI 열기
     */
    public void openRefineryGui(Player player) {
        if (!enabled) {
            player.sendMessage("§c정제소 시스템이 비활성화되어 있습니다.");
            return;
        }
        
        // [STUB] 향후 구현
        player.sendMessage("§e정제소 GUI는 향후 업데이트에서 제공될 예정입니다.");
    }
}
