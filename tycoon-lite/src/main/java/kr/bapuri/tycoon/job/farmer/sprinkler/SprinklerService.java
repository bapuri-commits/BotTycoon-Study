package kr.bapuri.tycoon.job.farmer.sprinkler;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * SprinklerService - 스프링클러 시스템 (Stub)
 * 
 * Phase 4.C: 확장 대비 기본 구조만 정의
 * 외부 플러그인 연동 시 구현 예정
 */
public class SprinklerService {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private boolean enabled = false;
    
    public SprinklerService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    /**
     * 시스템 초기화
     */
    public void initialize() {
        logger.info("[SprinklerService] Stub 초기화 (비활성 상태)");
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
        logger.info("[SprinklerService] " + (enabled ? "활성화" : "비활성화"));
    }
}
