package kr.bapuri.tycoon.cosmetic;

import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 치장 아이템 서비스
 * 
 * 치장 아이템의 구매, 활성화/비활성화를 처리합니다.
 * 효과 중첩을 지원합니다 (채팅색상 1개 + 파티클 1개 + 발광 1개).
 */
public class CosmeticService {
    
    private final Plugin plugin;
    private final PlayerDataManager dataManager;
    private final CosmeticRegistry registry;
    private final Logger logger;
    
    public CosmeticService(Plugin plugin, PlayerDataManager dataManager, CosmeticRegistry registry) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.registry = registry;
        this.logger = plugin.getLogger();
    }
    
    // ========== 보유 확인 ==========
    
    /**
     * 치장 아이템 보유 여부 확인
     */
    public boolean ownsCosmetic(Player player, String cosmeticId) {
        return dataManager.get(player).ownsCosmetic(cosmeticId);
    }
    
    /**
     * 치장 아이템 추가 (구매)
     */
    public void addCosmetic(Player player, String cosmeticId) {
        PlayerTycoonData data = dataManager.get(player);
        data.addCosmetic(cosmeticId);
        logger.fine("[CosmeticService] " + player.getName() + " 치장 추가: " + cosmeticId);
    }
    
    // ========== 활성화/비활성화 ==========
    
    /**
     * 치장 아이템 활성화 여부 확인
     */
    public boolean isActive(Player player, String cosmeticId) {
        PlayerTycoonData data = dataManager.get(player);
        CosmeticItem item = registry.get(cosmeticId);
        
        if (item == null) return false;
        
        return switch (item.getType()) {
            case CHAT_COLOR -> cosmeticId.equals(data.getActiveChatColor());
            case PARTICLE -> cosmeticId.equals(data.getActiveParticle());
            case GLOW -> cosmeticId.equals(data.getActiveGlow());
        };
    }
    
    /**
     * 치장 아이템 활성화
     */
    public boolean activate(Player player, String cosmeticId) {
        if (!ownsCosmetic(player, cosmeticId)) {
            player.sendMessage("§c보유하지 않은 치장 아이템입니다.");
            return false;
        }
        
        CosmeticItem item = registry.get(cosmeticId);
        if (item == null) {
            player.sendMessage("§c존재하지 않는 치장 아이템입니다.");
            return false;
        }
        
        PlayerTycoonData data = dataManager.get(player);
        
        switch (item.getType()) {
            case CHAT_COLOR -> {
                data.setActiveChatColor(cosmeticId);
                player.sendMessage("§a채팅 색상 " + item.getName() + " 활성화!");
            }
            case PARTICLE -> {
                data.setActiveParticle(cosmeticId);
                player.sendMessage("§a파티클 " + item.getName() + " 활성화!");
            }
            case GLOW -> {
                data.setActiveGlow(cosmeticId);
                player.sendMessage("§a발광 효과 " + item.getName() + " 활성화!");
            }
        }
        
        logger.fine("[CosmeticService] " + player.getName() + " 치장 활성화: " + cosmeticId);
        return true;
    }
    
    /**
     * 치장 아이템 비활성화
     */
    public void deactivate(Player player, String cosmeticId) {
        CosmeticItem item = registry.get(cosmeticId);
        if (item == null) return;
        
        PlayerTycoonData data = dataManager.get(player);
        
        switch (item.getType()) {
            case CHAT_COLOR -> {
                if (cosmeticId.equals(data.getActiveChatColor())) {
                    data.setActiveChatColor(null);
                    player.sendMessage("§7채팅 색상 해제됨");
                }
            }
            case PARTICLE -> {
                if (cosmeticId.equals(data.getActiveParticle())) {
                    data.setActiveParticle(null);
                    player.sendMessage("§7파티클 해제됨");
                }
            }
            case GLOW -> {
                if (cosmeticId.equals(data.getActiveGlow())) {
                    data.setActiveGlow(null);
                    player.sendMessage("§7발광 효과 해제됨");
                }
            }
        }
        
        logger.fine("[CosmeticService] " + player.getName() + " 치장 비활성화: " + cosmeticId);
    }
    
    /**
     * 치장 아이템 토글 (활성화 <-> 비활성화)
     */
    public void toggle(Player player, String cosmeticId) {
        if (isActive(player, cosmeticId)) {
            deactivate(player, cosmeticId);
        } else {
            activate(player, cosmeticId);
        }
    }
    
    // ========== 현재 활성화된 치장 조회 ==========
    
    public String getActiveChatColor(Player player) {
        return dataManager.get(player).getActiveChatColor();
    }
    
    public String getActiveParticle(Player player) {
        return dataManager.get(player).getActiveParticle();
    }
    
    public String getActiveGlow(Player player) {
        return dataManager.get(player).getActiveGlow();
    }
    
    public CosmeticRegistry getRegistry() {
        return registry;
    }
}
