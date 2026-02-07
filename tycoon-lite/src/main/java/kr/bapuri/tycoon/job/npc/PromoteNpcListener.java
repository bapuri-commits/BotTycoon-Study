package kr.bapuri.tycoon.job.npc;

import kr.bapuri.tycoon.integration.CitizensIntegration;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.common.AbstractJobExpService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.logging.Logger;

/**
 * PromoteNpcListener - 승급 NPC 이벤트 리스너
 * 
 * [Phase 승급효과] Citizens NPC 우클릭 시 승급 GUI 열기
 */
public class PromoteNpcListener implements Listener {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final PromoteNpcRegistry registry;
    private final PromoteMainGui mainGui;
    
    // ExpService 매핑 (직업 확인용)
    private Map<JobType, AbstractJobExpService> expServices;
    
    public PromoteNpcListener(JavaPlugin plugin, PromoteNpcRegistry registry, PromoteMainGui mainGui) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.registry = registry;
        this.mainGui = mainGui;
    }
    
    /**
     * ExpService 매핑 설정
     */
    public void setExpServices(Map<JobType, AbstractJobExpService> services) {
        this.expServices = services;
    }
    
    /**
     * Citizens 연동 설정
     */
    public void registerWithCitizens(CitizensIntegration citizens) {
        if (citizens == null || !citizens.isAvailable()) {
            logger.warning("[PromoteNpcListener] Citizens 플러그인을 찾을 수 없습니다.");
            return;
        }
        
        // 등록된 모든 NPC에 대해 핸들러 등록
        for (Map.Entry<Integer, JobType> entry : registry.getAllNpcs().entrySet()) {
            int npcId = entry.getKey();
            JobType jobType = entry.getValue();
            
            citizens.registerHandler(npcId, (player, npc) -> onNpcClick(player, npcId, jobType));
        }
        
        logger.info("[PromoteNpcListener] Citizens 연동 완료 - NPC 수: " + registry.size());
    }
    
    /**
     * NPC 클릭 처리
     */
    private void onNpcClick(Player player, int npcId, JobType jobType) {
        // 해당 직업을 가지고 있는지 확인
        if (expServices != null) {
            AbstractJobExpService expService = expServices.get(jobType);
            if (expService != null && !expService.hasJob(player)) {
                player.sendMessage("§c" + jobType.getDisplayName() + " 직업을 가지고 있지 않습니다.");
                return;
            }
        }
        
        // 메인 GUI 열기
        mainGui.open(player, jobType);
    }
    
    /**
     * 인벤토리 닫힘 처리
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            mainGui.handleClose(player);
        }
    }
    
    /**
     * 새 NPC 등록 시 Citizens 핸들러도 등록
     */
    public void registerNewNpc(int npcId, JobType jobType, CitizensIntegration citizens) {
        registry.registerNpc(npcId, jobType);
        
        if (citizens != null && citizens.isAvailable()) {
            citizens.registerHandler(npcId, (player, npc) -> onNpcClick(player, npcId, jobType));
        }
    }
}
