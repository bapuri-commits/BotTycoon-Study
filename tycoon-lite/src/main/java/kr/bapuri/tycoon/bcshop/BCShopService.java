package kr.bapuri.tycoon.bcshop;

import kr.bapuri.tycoon.cosmetic.CosmeticRegistry;
import kr.bapuri.tycoon.cosmetic.CosmeticService;
import kr.bapuri.tycoon.cosmetic.CosmeticShop;
import kr.bapuri.tycoon.cosmetic.listener.ChatColorListener;
import kr.bapuri.tycoon.cosmetic.listener.ParticleTask;
import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.integration.CitizensIntegration;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.title.LuckPermsTitleService;
import kr.bapuri.tycoon.title.PurchasableTitleRegistry;
import kr.bapuri.tycoon.title.TitleRegistry;
import kr.bapuri.tycoon.title.TitleShop;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Logger;

/**
 * BC 상점 서비스
 * 
 * BC 상점 시스템의 통합 관리자입니다.
 * TitleShop, CosmeticShop을 관리하고 NPC 연동을 처리합니다.
 */
public class BCShopService {
    
    private static final Logger LOGGER = Logger.getLogger("Tycoon.BCShopService");
    
    private final Plugin plugin;
    private final EconomyService economyService;
    private final PlayerDataManager dataManager;
    
    // 칭호 관련
    private final TitleRegistry titleRegistry;
    private final LuckPermsTitleService titleService;
    private PurchasableTitleRegistry purchasableTitleRegistry;
    private TitleShop titleShop;
    
    // 치장 관련
    private CosmeticRegistry cosmeticRegistry;
    private CosmeticService cosmeticService;
    private CosmeticShop cosmeticShop;
    
    // GUI 관리자
    private BCShopGuiManager guiManager;
    
    // 리스너/태스크
    private ChatColorListener chatColorListener;
    private ParticleTask particleTask;
    
    private boolean enabled = true;
    
    public BCShopService(Plugin plugin, EconomyService economyService, PlayerDataManager dataManager,
                         TitleRegistry titleRegistry, LuckPermsTitleService titleService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.dataManager = dataManager;
        this.titleRegistry = titleRegistry;
        this.titleService = titleService;
    }
    
    /**
     * 서비스 초기화
     */
    public void initialize() {
        LOGGER.info("[BCShopService] 초기화 시작...");
        
        // 활성화 여부 확인
        enabled = plugin.getConfig().getBoolean("bcShop.enabled", true);
        if (!enabled) {
            LOGGER.info("[BCShopService] bcShop.enabled=false - 비활성화됨");
            return;
        }
        
        // GUI 관리자
        guiManager = new BCShopGuiManager(plugin);
        
        // 칭호 상점 초기화
        purchasableTitleRegistry = new PurchasableTitleRegistry(plugin, titleRegistry);
        titleShop = new TitleShop(plugin, economyService, dataManager, 
                purchasableTitleRegistry, titleService);
        
        // 치장 상점 초기화
        cosmeticRegistry = new CosmeticRegistry(plugin);
        cosmeticService = new CosmeticService(plugin, dataManager, cosmeticRegistry);
        cosmeticShop = new CosmeticShop(plugin, economyService, dataManager, 
                cosmeticRegistry, cosmeticService);
        
        // 채팅 색상 리스너
        chatColorListener = new ChatColorListener(plugin, dataManager, cosmeticRegistry);
        Bukkit.getPluginManager().registerEvents(chatColorListener, plugin);
        
        // 파티클 태스크
        particleTask = new ParticleTask(plugin, dataManager, cosmeticRegistry);
        particleTask.start();
        
        LOGGER.info("[BCShopService] 초기화 완료 - " +
                "구매형 칭호 " + purchasableTitleRegistry.getCount() + "개, " +
                "치장 아이템 " + cosmeticShop.getItems().size() + "개");
    }
    
    /**
     * NPC 연동 등록
     */
    public void registerNPCs(CitizensIntegration citizens) {
        if (!enabled || citizens == null || !citizens.isAvailable()) {
            return;
        }
        
        ConfigurationSection bcShopNpc = plugin.getConfig().getConfigurationSection("bcShopNpc");
        if (bcShopNpc == null || !bcShopNpc.getBoolean("enabled", true)) {
            LOGGER.info("[BCShopService] bcShopNpc 비활성화");
            return;
        }
        
        ConfigurationSection mappings = bcShopNpc.getConfigurationSection("mappings");
        if (mappings == null) {
            // 기본 매핑
            registerDefaultNPCs(citizens);
            return;
        }
        
        int count = 0;
        for (String npcKeyword : mappings.getKeys(false)) {
            String shopId = mappings.getString(npcKeyword);
            if (shopId != null && !shopId.isEmpty()) {
                registerNPC(citizens, npcKeyword, shopId);
                count++;
            }
        }
        
        LOGGER.info("[BCShopService] NPC 매핑 등록: " + count + "개");
    }
    
    private void registerDefaultNPCs(CitizensIntegration citizens) {
        registerNPC(citizens, "칭호상인", "title");
        registerNPC(citizens, "TitleShop", "title");
        registerNPC(citizens, "치장상인", "cosmetic");
        registerNPC(citizens, "CosmeticShop", "cosmetic");
        LOGGER.info("[BCShopService] 기본 NPC 매핑 등록 (4개)");
    }
    
    private void registerNPC(CitizensIntegration citizens, String npcKeyword, String shopId) {
        citizens.registerShopNPC(npcKeyword, "bc_" + shopId, (player, sId) -> {
            openShop(player, shopId);
        });
    }
    
    /**
     * 상점 열기
     */
    public void openShop(Player player, String shopId) {
        if (!enabled) {
            player.sendMessage("§cBC 상점이 비활성화되어 있습니다.");
            return;
        }
        
        AbstractBCShop shop = getShop(shopId);
        if (shop == null) {
            player.sendMessage("§c존재하지 않는 상점입니다: " + shopId);
            return;
        }
        
        guiManager.openShop(player, shop);
    }
    
    /**
     * 상점 조회
     */
    public AbstractBCShop getShop(String shopId) {
        return switch (shopId.toLowerCase()) {
            case "title", "칭호" -> titleShop;
            case "cosmetic", "치장" -> cosmeticShop;
            default -> null;
        };
    }
    
    /**
     * 설정 리로드
     */
    public void reload() {
        if (purchasableTitleRegistry != null) purchasableTitleRegistry.reload();
        if (titleShop != null) titleShop.reload();
        if (cosmeticRegistry != null) cosmeticRegistry.reload();
        if (cosmeticShop != null) cosmeticShop.reload();
        LOGGER.info("[BCShopService] 리로드 완료");
    }
    
    /**
     * 서비스 종료
     */
    public void shutdown() {
        if (guiManager != null) guiManager.shutdown();
        if (particleTask != null) particleTask.stop();
        LOGGER.info("[BCShopService] 종료됨");
    }
    
    // ========== Getter ==========
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public TitleShop getTitleShop() {
        return titleShop;
    }
    
    public CosmeticShop getCosmeticShop() {
        return cosmeticShop;
    }
    
    public CosmeticService getCosmeticService() {
        return cosmeticService;
    }
    
    public BCShopGuiManager getGuiManager() {
        return guiManager;
    }
}
