package kr.bapuri.tycoon.economy.vault;

import kr.bapuri.tycoon.economy.EconomyService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.util.logging.Logger;

/**
 * Vault Economy 연동
 * 
 * Tycoon의 BD 경제 시스템을 Vault Economy Provider로 등록하여
 * 다른 플러그인이 Tycoon 경제를 사용할 수 있게 함
 * 
 * [Phase 3.A] TycoonLite용 간소화 버전
 */
public class VaultIntegration {
    
    private final Plugin plugin;
    private final EconomyService economyService;
    private final Logger logger;
    
    private TycoonEconomy tycoonEconomy;
    private boolean registered = false;
    
    public VaultIntegration(Plugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.logger = Logger.getLogger("Tycoon.VaultIntegration");
    }
    
    /**
     * Vault에 Tycoon Economy Provider 등록
     */
    public boolean register() {
        if (registered) {
            return true;
        }
        
        // Vault 플러그인 확인
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            logger.warning("[Vault] Vault 플러그인을 찾을 수 없습니다.");
            return false;
        }
        
        try {
            tycoonEconomy = new TycoonEconomy(plugin, economyService);
            
            plugin.getServer().getServicesManager().register(
                Economy.class,
                tycoonEconomy,
                plugin,
                ServicePriority.Highest  // 최우선 등록
            );
            
            registered = true;
            logger.info("[Vault] TycoonLite Economy Provider 등록 완료");
            return true;
            
        } catch (Exception e) {
            logger.warning("[Vault] Economy Provider 등록 실패: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Vault 연동 해제
     */
    public void unregister() {
        if (!registered || tycoonEconomy == null) {
            return;
        }
        
        try {
            plugin.getServer().getServicesManager().unregister(Economy.class, tycoonEconomy);
            registered = false;
            logger.info("[Vault] TycoonLite Economy Provider 해제 완료");
        } catch (Exception e) {
            logger.warning("[Vault] Economy Provider 해제 실패: " + e.getMessage());
        }
    }
    
    public boolean isRegistered() {
        return registered;
    }
    
    public TycoonEconomy getEconomy() {
        return tycoonEconomy;
    }
}
