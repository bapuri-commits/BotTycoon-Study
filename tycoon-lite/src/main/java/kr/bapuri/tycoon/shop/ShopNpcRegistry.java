package kr.bapuri.tycoon.shop;

import kr.bapuri.tycoon.integration.CitizensIntegration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.logging.Logger;

/**
 * 상점 NPC 등록 유틸리티
 * 
 * [Phase 3.B] Citizens NPC와 상점 연결
 * [Config 연동] config.yml의 shopNpc 섹션에서 매핑 로드
 * 
 * <h2>설정 예시 (config.yml)</h2>
 * <pre>
 * shopNpc:
 *   광부 상인: miner
 *   농부 상인: farmer
 *   miner shop: miner
 * </pre>
 */
public class ShopNpcRegistry {
    
    private static final Logger LOGGER = Logger.getLogger("Tycoon.ShopNpc");
    
    /**
     * 모든 상점 NPC 핸들러 등록 (config.yml에서 로드)
     * 
     * @param plugin 플러그인 인스턴스
     * @param citizens Citizens 연동 객체
     * @param shopService 상점 서비스
     */
    public static void registerAllShopNPCs(JavaPlugin plugin, CitizensIntegration citizens, ShopService shopService) {
        if (citizens == null || !citizens.isAvailable()) {
            LOGGER.warning("[ShopNpc] Citizens 플러그인이 없어 NPC 상점 비활성화");
            return;
        }
        
        // config.yml의 shopNpc 섹션에서 매핑 로드
        ConfigurationSection shopNpcSection = plugin.getConfig().getConfigurationSection("shopNpc");
        
        if (shopNpcSection == null) {
            LOGGER.warning("[ShopNpc] shopNpc 섹션이 없어 기본 매핑 사용");
            registerDefaultMappings(citizens, shopService);
            return;
        }
        
        // enabled 플래그 확인
        if (!shopNpcSection.getBoolean("enabled", true)) {
            LOGGER.info("[ShopNpc] shopNpc.enabled=false - 비활성화됨");
            return;
        }
        
        // debug 플래그 확인
        boolean debug = shopNpcSection.getBoolean("debug", false);
        
        // 실제 매핑은 shopNpc.mappings 섹션에 있음!
        ConfigurationSection mappingsSection = shopNpcSection.getConfigurationSection("mappings");
        if (mappingsSection == null) {
            LOGGER.warning("[ShopNpc] shopNpc.mappings 섹션이 없어 기본 매핑 사용");
            registerDefaultMappings(citizens, shopService);
            return;
        }
        
        int count = 0;
        for (String npcKeyword : mappingsSection.getKeys(false)) {
            String shopId = mappingsSection.getString(npcKeyword);
            if (shopId != null && !shopId.isEmpty()) {
                registerShopNPC(citizens, shopService, npcKeyword, shopId);
                if (debug) {
                    LOGGER.info("[ShopNpc][DEBUG] 매핑 등록: \"" + npcKeyword + "\" -> " + shopId);
                }
                count++;
            }
        }
        
        LOGGER.info("[ShopNpc] 상점 NPC 핸들러 등록 완료 (" + count + "개 매핑, config 로드" + (debug ? ", debug=ON" : "") + ")");
    }
    
    /**
     * 기본 매핑 등록 (config 없을 때)
     */
    private static void registerDefaultMappings(CitizensIntegration citizens, ShopService shopService) {
        // 기본 매핑 (하드코딩 fallback)
        registerShopNPC(citizens, shopService, "광부", "miner");
        registerShopNPC(citizens, shopService, "miner", "miner");
        registerShopNPC(citizens, shopService, "농부", "farmer");
        registerShopNPC(citizens, shopService, "farmer", "farmer");
        registerShopNPC(citizens, shopService, "어부", "fisher");
        registerShopNPC(citizens, shopService, "fisher", "fisher");
        
        LOGGER.info("[ShopNpc] 기본 상점 NPC 핸들러 등록 완료 (6개 키워드)");
    }
    
    /**
     * 모든 상점 NPC 핸들러 등록 (레거시 호환)
     */
    public static void registerAllShopNPCs(CitizensIntegration citizens, ShopService shopService) {
        if (citizens == null || !citizens.isAvailable()) {
            LOGGER.warning("[ShopNpc] Citizens 플러그인이 없어 NPC 상점 비활성화");
            return;
        }
        
        registerDefaultMappings(citizens, shopService);
    }
    
    /**
     * 단일 상점 NPC 등록
     */
    private static void registerShopNPC(CitizensIntegration citizens, ShopService shopService,
                                        String npcKeyword, String shopId) {
        citizens.registerShopNPC(npcKeyword, shopId, (player, sId) -> {
            shopService.openShopGui(player, sId);
        });
    }
    
    /**
     * 커스텀 상점 NPC 등록 (외부에서 사용)
     * 
     * @param citizens Citizens 연동
     * @param shopService 상점 서비스
     * @param npcKeyword NPC 이름에 포함된 키워드
     * @param shopId 열 상점 ID
     */
    public static void registerCustomShopNPC(CitizensIntegration citizens, ShopService shopService,
                                             String npcKeyword, String shopId) {
        if (citizens == null || !citizens.isAvailable()) {
            LOGGER.warning("[ShopNpc] Citizens 없음 - " + npcKeyword + " NPC 등록 실패");
            return;
        }
        
        registerShopNPC(citizens, shopService, npcKeyword, shopId);
        LOGGER.info("[ShopNpc] 커스텀 NPC 등록: " + npcKeyword + " -> " + shopId);
    }
}
