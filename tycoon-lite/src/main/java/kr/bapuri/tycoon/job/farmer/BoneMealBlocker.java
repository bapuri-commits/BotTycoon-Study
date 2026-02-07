package kr.bapuri.tycoon.job.farmer;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * BoneMealBlocker - 뼛가루 차단 시스템 (Stub)
 * 
 * Phase 4.C: 확장 대비 기본 구조만 정의
 * 현재 비활성 상태, 비료 시스템 도입 시 활성화 예정
 */
public class BoneMealBlocker {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private boolean enabled = false;
    
    public BoneMealBlocker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    /**
     * 시스템 초기화
     */
    public void initialize() {
        logger.info("[BoneMealBlocker] Stub 초기화 (비활성 상태)");
    }
    
    /**
     * 시스템 종료
     */
    public void shutdown() {
        // stub
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("[BoneMealBlocker] " + (enabled ? "활성화" : "비활성화"));
    }
}
