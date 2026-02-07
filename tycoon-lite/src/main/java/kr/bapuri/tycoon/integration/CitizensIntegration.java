package kr.bapuri.tycoon.integration;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Citizens NPC 연동
 * 
 * NPC 클릭 시 Tycoon 기능 실행:
 * - NPC 이름/메타데이터 기반 핸들러 등록
 * - 상점, 텔레포트, 직업 NPC 등 지원
 */
public class CitizensIntegration implements Listener {
    
    private final Plugin plugin;
    private final Logger logger;
    private boolean available = false;
    
    // NPC 이름 → 핸들러 매핑
    private final Map<String, BiConsumer<Player, NPC>> npcHandlers = new HashMap<>();
    
    // NPC ID → 핸들러 매핑 (더 정확한 매핑)
    private final Map<Integer, BiConsumer<Player, NPC>> npcIdHandlers = new HashMap<>();
    
    public CitizensIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.logger = Logger.getLogger("TycoonLite.Citizens");
        
        checkAvailability();
    }
    
    private void checkAvailability() {
        try {
            Plugin citizens = plugin.getServer().getPluginManager().getPlugin("Citizens");
            if (citizens != null && citizens.isEnabled()) {
                // Citizens API 접근 테스트
                CitizensAPI.getNPCRegistry();
                available = true;
                logger.info("[Citizens] 연동 초기화 완료");
            }
        } catch (Exception e) {
            logger.warning("[Citizens] 연동 실패: " + e.getMessage());
            available = false;
        }
    }
    
    /**
     * 이벤트 리스너 등록
     */
    public void registerListener() {
        if (!available) return;
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("[Citizens] 이벤트 리스너 등록 완료");
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * NPC 이름 기반 핸들러 등록
     * 
     * @param npcName NPC 이름 (대소문자 무시)
     * @param handler 클릭 시 실행할 핸들러
     */
    public void registerHandler(String npcName, BiConsumer<Player, NPC> handler) {
        npcHandlers.put(npcName.toLowerCase(), handler);
        logger.info("[Citizens] NPC 핸들러 등록: " + npcName);
    }
    
    /**
     * NPC ID 기반 핸들러 등록
     * 
     * @param npcId NPC ID
     * @param handler 클릭 시 실행할 핸들러
     */
    public void registerHandler(int npcId, BiConsumer<Player, NPC> handler) {
        npcIdHandlers.put(npcId, handler);
        logger.info("[Citizens] NPC 핸들러 등록: ID " + npcId);
    }
    
    /**
     * NPC 우클릭 이벤트 처리
     */
    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        Player player = event.getClicker();
        
        // 1. NPC ID 기반 핸들러 확인
        BiConsumer<Player, NPC> handler = npcIdHandlers.get(npc.getId());
        
        // 2. NPC 이름 기반 핸들러 확인 (정확한 이름)
        if (handler == null && npc.getName() != null) {
            String npcName = stripColor(npc.getName()).toLowerCase();
            handler = npcHandlers.get(npcName);
            
            // 3. 키워드 포함 매칭 (부분 일치)
            if (handler == null) {
                handler = findHandlerByKeyword(npcName);
            }
        }
        
        // 4. 핸들러 실행
        if (handler != null) {
            try {
                handler.accept(player, npc);
            } catch (Exception e) {
                logger.warning("[Citizens] NPC 핸들러 오류 (" + npc.getName() + "): " + e.getMessage());
                player.sendMessage("§c오류가 발생했습니다. 관리자에게 문의하세요.");
            }
        }
    }
    
    /**
     * 키워드 포함 매칭으로 핸들러 찾기
     * 더 긴 키워드(구체적인)가 우선 매칭됨
     */
    private BiConsumer<Player, NPC> findHandlerByKeyword(String npcName) {
        BiConsumer<Player, NPC> bestMatch = null;
        int bestLength = 0;
        
        for (Map.Entry<String, BiConsumer<Player, NPC>> entry : npcHandlers.entrySet()) {
            String keyword = entry.getKey();
            if (npcName.contains(keyword) && keyword.length() > bestLength) {
                bestMatch = entry.getValue();
                bestLength = keyword.length();
            }
        }
        return bestMatch;
    }
    
    /**
     * 색상 코드 제거
     */
    private String stripColor(String input) {
        if (input == null) return "";
        String result = input.replaceAll("§[0-9a-fk-or]", "");
        result = result.replaceAll("&[0-9a-fk-or]", "");
        return result.trim();
    }
    
    /**
     * NPC 가져오기
     */
    public NPC getNPC(int id) {
        if (!available) return null;
        
        try {
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            return registry.getById(id);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 모든 NPC 순회
     */
    public Iterable<NPC> getAllNPCs() {
        if (!available) return null;
        
        try {
            return CitizensAPI.getNPCRegistry();
        } catch (Exception e) {
            return null;
        }
    }
    
    // ========== 헬퍼 메서드: 일반적인 NPC 타입 등록 ==========
    
    /**
     * 상점 NPC 등록 헬퍼
     */
    public void registerShopNPC(String npcName, String shopId, ShopOpenHandler shopHandler) {
        registerHandler(npcName, (player, npc) -> {
            shopHandler.openShop(player, shopId);
        });
    }
    
    /**
     * 텔레포트 NPC 등록 헬퍼
     */
    public void registerTeleportNPC(String npcName, TeleportHandler teleportHandler) {
        registerHandler(npcName, (player, npc) -> {
            teleportHandler.teleport(player);
        });
    }
    
    /**
     * 상점 열기 인터페이스
     */
    @FunctionalInterface
    public interface ShopOpenHandler {
        void openShop(Player player, String shopId);
    }
    
    /**
     * 텔레포트 인터페이스
     */
    @FunctionalInterface
    public interface TeleportHandler {
        void teleport(Player player);
    }
}
