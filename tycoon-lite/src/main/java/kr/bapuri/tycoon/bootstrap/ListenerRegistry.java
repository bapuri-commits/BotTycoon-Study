package kr.bapuri.tycoon.bootstrap;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * ListenerRegistry - 리스너 등록 관리
 * 
 * 레거시의 ListenerRegistrar는 단일 static 메서드에 30개 이상의 리스너를 등록했습니다.
 * 이 클래스는 모듈화된 리스너 등록을 지원합니다.
 * 
 * 사용법:
 *   ListenerRegistry listeners = new ListenerRegistry(plugin);
 *   listeners.register(new PlayerSessionListener(...));
 *   listeners.register(new JobListener(...));
 *   listeners.registerAll();  // 한 번에 등록
 * 
 * Phase 2에서 확장:
 *   // 직업 시스템 리스너
 *   if (jobService != null) {
 *       listeners.register(jobService.getListener());
 *   }
 */
public class ListenerRegistry {

    private final Plugin plugin;
    private final Logger logger;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean registered = false;
    
    public ListenerRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.logger = Logger.getLogger("TycoonLite.Listeners");
    }
    
    /**
     * 리스너 등록 (아직 Bukkit에 등록하지 않음)
     */
    public void register(Listener listener) {
        if (registered) {
            // 이미 registerAll() 호출됨 - 직접 등록
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            logger.info("리스너 추가 등록: " + listener.getClass().getSimpleName());
        } else {
            listeners.add(listener);
        }
    }
    
    /**
     * 조건부 리스너 등록
     */
    public void registerIf(boolean condition, Listener listener) {
        if (condition) {
            register(listener);
        }
    }
    
    /**
     * 모든 리스너를 Bukkit에 등록
     */
    public void registerAll() {
        if (registered) {
            logger.warning("registerAll()이 이미 호출되었습니다.");
            return;
        }
        
        for (Listener listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
        
        registered = true;
        logger.info("리스너 " + listeners.size() + "개 등록 완료");
    }
    
    /**
     * 등록된 리스너 수
     */
    public int getListenerCount() {
        return listeners.size();
    }
    
    /**
     * 등록 완료 여부
     */
    public boolean isRegistered() {
        return registered;
    }
}
