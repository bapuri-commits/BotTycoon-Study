package kr.bapuri.tycoon.job.farmer.tool;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * FarmerToolListener - 농부 도구 리스너 (Stub)
 * 
 * Phase 4.C: 확장 대비 기본 구조만 정의
 * 외부 플러그인 (물뿌리개 등) 연동 시 구현 예정
 */
public class FarmerToolListener implements Listener {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private boolean enabled = false;
    
    public FarmerToolListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    /**
     * 리스너 초기화
     */
    public void initialize() {
        logger.info("[FarmerToolListener] Stub 초기화 (비활성 상태)");
    }
    
    /**
     * 리스너 종료
     */
    public void shutdown() {
        // stub
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("[FarmerToolListener] " + (enabled ? "활성화" : "비활성화"));
    }
}
