package kr.bapuri.tycoon.bootstrap;

import kr.bapuri.tycoon.admin.AdminService;
import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.economy.vault.VaultIntegration;
import kr.bapuri.tycoon.integration.CitizensIntegration;
import kr.bapuri.tycoon.integration.CoreProtectIntegration;
import kr.bapuri.tycoon.integration.LandsIntegration;
import kr.bapuri.tycoon.integration.LandsListener;
import kr.bapuri.tycoon.integration.WorldGuardIntegration;
import kr.bapuri.tycoon.item.CoreItemAuthenticator;
import kr.bapuri.tycoon.item.CoreItemService;
import kr.bapuri.tycoon.job.JobRegistry;
import kr.bapuri.tycoon.job.JobService;
import kr.bapuri.tycoon.job.JobsConfigLoader;
import kr.bapuri.tycoon.job.common.GradeBonusConfig;
import kr.bapuri.tycoon.job.common.SellService;
import kr.bapuri.tycoon.codex.CodexService;
import kr.bapuri.tycoon.achievement.AchievementService;
import kr.bapuri.tycoon.title.LuckPermsTitleService;
import kr.bapuri.tycoon.bcshop.BCShopService;
import kr.bapuri.tycoon.trade.TradeService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.shop.ShopService;
import kr.bapuri.tycoon.world.WorldManager;
import kr.bapuri.tycoon.enhance.enchant.CustomEnchantService;
import kr.bapuri.tycoon.enhance.enchant.CustomEnchantRegistry;
import kr.bapuri.tycoon.enhance.lamp.LampService;
import kr.bapuri.tycoon.enhance.lamp.LampRegistry;
import kr.bapuri.tycoon.enhance.upgrade.UpgradeService;
import kr.bapuri.tycoon.enhance.upgrade.UpgradeConfig;
import kr.bapuri.tycoon.enhance.upgrade.UpgradeGui;
import kr.bapuri.tycoon.enhance.processing.BlockProcessingService;
import kr.bapuri.tycoon.mod.ModDataService;
import kr.bapuri.tycoon.mod.ModEventBridge;
import kr.bapuri.tycoon.mod.ModRequestHandler;

import java.util.Optional;

/**
 * ServiceRegistry - 중앙 서비스 컨테이너 (Dependency Injection Container)
 * 
 * <h2>설계 의도</h2>
 * <p>
 * 레거시 BotTycoon은 TycoonPlugin 하나에 150개 이상의 필드가 있는 God Object였습니다.
 * 이로 인해 순환 의존성, 테스트 어려움, 유지보수 복잡성이 발생했습니다.
 * </p>
 * <p>
 * ServiceRegistry는 이를 해결하기 위한 경량 DI 컨테이너입니다:
 * </p>
 * <ul>
 *   <li><b>Core 서비스</b>: 생성자에서 주입, non-null 보장 (ConfigManager, PlayerDataManager 등)</li>
 *   <li><b>Optional 서비스</b>: getter에서 {@code Optional<T>} 반환 (외부 플러그인 연동)</li>
 *   <li><b>Setter Injection</b>: 순환 의존성 방지 (A→B, B→A 가능)</li>
 * </ul>
 * 
 * <h2>사용 패턴</h2>
 * <pre>{@code
 * // TycoonPlugin에서 초기화
 * ServiceRegistry services = new ServiceRegistry(config, playerData, admin, world);
 * services.setIntegrations(citizens, worldGuard, coreProtect);
 * services.setEconomyService(economyService);
 * 
 * // 다른 클래스에서 사용
 * // Core 서비스 (null 체크 불필요)
 * services.getPlayerDataManager().get(player);
 * 
 * // Optional 서비스 (null 안전)
 * services.getCitizensIntegration().ifPresent(c -> c.doSomething());
 * }</pre>
 * 
 * <h2>확장 방법</h2>
 * <p>
 * 새 서비스 추가 시:
 * </p>
 * <ol>
 *   <li>private 필드 추가</li>
 *   <li>setter 메서드 추가</li>
 *   <li>getter 메서드 추가 (Optional 여부 결정)</li>
 *   <li>TycoonPlugin에서 초기화 코드 추가</li>
 * </ol>
 * 
 * @see TycoonPlugin#initServices()
 */
public class ServiceRegistry {

    // ===== Core Services (Required, non-null) =====
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final AdminService adminService;
    private final WorldManager worldManager;
    
    // ===== Integration Services (Optional) =====
    private CitizensIntegration citizensIntegration;
    private WorldGuardIntegration worldGuardIntegration;
    private CoreProtectIntegration coreProtectIntegration;
    private LandsIntegration landsIntegration;
    private LandsListener landsListener;
    
    // ===== Phase 3 경제 서비스 =====
    private EconomyService economyService;
    private VaultIntegration vaultIntegration;
    
    // ===== Phase 3.B 상점 서비스 =====
    private ShopService shopService;
    private kr.bapuri.tycoon.shop.special.SpecialItemShop specialItemShop;
    
    // ===== Phase 3.C 아이템/안티익스플로잇 서비스 =====
    private CoreItemAuthenticator coreItemAuthenticator;
    private CoreItemService coreItemService;
    
    // ===== Phase 4.A 직업 시스템 =====
    private JobsConfigLoader jobsConfigLoader;
    private JobRegistry jobRegistry;
    private JobService jobService;
    private SellService sellService;
    private GradeBonusConfig gradeBonusConfig;
    
    // ===== Phase 5.A 도감 시스템 =====
    private CodexService codexService;
    
    // ===== Phase 5.B 업적 시스템 =====
    private AchievementService achievementService;
    
    // ===== Phase 5.C 칭호 시스템 =====
    private LuckPermsTitleService titleService;
    
    // ===== BC 치장 상점 시스템 =====
    private BCShopService bcShopService;
    
    // ===== Phase 5.D 거래 시스템 =====
    private TradeService tradeService;
    
    // ===== Phase 7 Enhance 시스템 =====
    private CustomEnchantService enchantService;
    private CustomEnchantRegistry enchantRegistry;
    private LampService lampService;
    private LampRegistry lampRegistry;
    private UpgradeService upgradeService;
    private UpgradeConfig upgradeConfig;
    private UpgradeGui upgradeGui;
    
    // ===== Block Processing 파이프라인 =====
    private BlockProcessingService blockProcessingService;
    
    // ===== Phase 8 Recovery 시스템 =====
    private kr.bapuri.tycoon.recovery.RecoveryStorageManager recoveryStorageManager;
    
    // ===== Phase 8 Mod 연동 =====
    private ModDataService modDataService;
    private ModEventBridge modEventBridge;
    private ModRequestHandler modRequestHandler;
    
    // ===== Phase 승급효과 =====
    private kr.bapuri.tycoon.job.miner.MiningEfficiencyEnchant miningEfficiencyEnchant;
    
    /**
     * Core 서비스로 초기화
     */
    public ServiceRegistry(ConfigManager configManager, 
                          PlayerDataManager playerDataManager, 
                          AdminService adminService,
                          WorldManager worldManager) {
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.adminService = adminService;
        this.worldManager = worldManager;
    }
    
    /**
     * Integration 서비스 설정
     */
    public void setIntegrations(CitizensIntegration citizens,
                               WorldGuardIntegration worldGuard,
                               CoreProtectIntegration coreProtect) {
        this.citizensIntegration = citizens;
        this.worldGuardIntegration = worldGuard;
        this.coreProtectIntegration = coreProtect;
    }
    
    // ===== Core Getters (non-null) =====
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    public AdminService getAdminService() {
        return adminService;
    }
    
    public WorldManager getWorldManager() {
        return worldManager;
    }
    
    // ===== Integration Getters (Optional) =====
    
    public Optional<CitizensIntegration> getCitizensIntegration() {
        return Optional.ofNullable(citizensIntegration)
                .filter(CitizensIntegration::isAvailable);
    }
    
    public Optional<WorldGuardIntegration> getWorldGuardIntegration() {
        return Optional.ofNullable(worldGuardIntegration)
                .filter(WorldGuardIntegration::isAvailable);
    }
    
    public Optional<CoreProtectIntegration> getCoreProtectIntegration() {
        return Optional.ofNullable(coreProtectIntegration)
                .filter(CoreProtectIntegration::isAvailable);
    }
    
    public void setLandsIntegration(LandsIntegration lands) {
        this.landsIntegration = lands;
    }
    
    public Optional<LandsIntegration> getLandsIntegration() {
        return Optional.ofNullable(landsIntegration)
                .filter(LandsIntegration::isAvailable);
    }
    
    public void setLandsListener(LandsListener listener) {
        this.landsListener = listener;
    }
    
    public LandsListener getLandsListener() {
        return landsListener;
    }
    
    // ===== Phase 3 경제 서비스 =====
    
    public void setEconomyService(EconomyService economyService) {
        this.economyService = economyService;
    }
    
    public EconomyService getEconomyService() {
        return economyService;
    }
    
    public void setVaultIntegration(VaultIntegration vaultIntegration) {
        this.vaultIntegration = vaultIntegration;
    }
    
    public Optional<VaultIntegration> getVaultIntegration() {
        return Optional.ofNullable(vaultIntegration)
                .filter(VaultIntegration::isRegistered);
    }
    
    // ===== Phase 3.B 상점 서비스 =====
    
    public void setShopService(ShopService shopService) {
        this.shopService = shopService;
    }
    
    public ShopService getShopService() {
        return shopService;
    }
    
    public void setSpecialItemShop(kr.bapuri.tycoon.shop.special.SpecialItemShop specialItemShop) {
        this.specialItemShop = specialItemShop;
    }
    
    public kr.bapuri.tycoon.shop.special.SpecialItemShop getSpecialItemShop() {
        return specialItemShop;
    }
    
    // ===== Phase 3.C 아이템/안티익스플로잇 서비스 =====
    
    public void setCoreItemAuthenticator(CoreItemAuthenticator coreItemAuthenticator) {
        this.coreItemAuthenticator = coreItemAuthenticator;
    }
    
    public CoreItemAuthenticator getCoreItemAuthenticator() {
        return coreItemAuthenticator;
    }
    
    public void setCoreItemService(CoreItemService coreItemService) {
        this.coreItemService = coreItemService;
    }
    
    public CoreItemService getCoreItemService() {
        return coreItemService;
    }
    
    // ===== Phase 4.A 직업 시스템 =====
    
    public void setJobsConfigLoader(JobsConfigLoader loader) {
        this.jobsConfigLoader = loader;
    }
    
    public JobsConfigLoader getJobsConfigLoader() {
        return jobsConfigLoader;
    }
    
    public void setJobRegistry(JobRegistry registry) {
        this.jobRegistry = registry;
    }
    
    public JobRegistry getJobRegistry() {
        return jobRegistry;
    }
    
    public void setJobService(JobService service) {
        this.jobService = service;
    }
    
    public JobService getJobService() {
        return jobService;
    }
    
    public void setSellService(SellService service) {
        this.sellService = service;
    }
    
    public SellService getSellService() {
        return sellService;
    }
    
    public void setGradeBonusConfig(GradeBonusConfig config) {
        this.gradeBonusConfig = config;
    }
    
    public GradeBonusConfig getGradeBonusConfig() {
        return gradeBonusConfig;
    }
    
    // ===== Phase 5.A 도감 시스템 =====
    
    public void setCodexService(CodexService service) {
        this.codexService = service;
    }
    
    public CodexService getCodexService() {
        return codexService;
    }
    
    // ===== Phase 5.B 업적 시스템 =====
    
    public void setAchievementService(AchievementService service) {
        this.achievementService = service;
    }
    
    public AchievementService getAchievementService() {
        return achievementService;
    }
    
    // ===== Phase 5.C 칭호 시스템 =====
    
    public void setTitleService(LuckPermsTitleService service) {
        this.titleService = service;
    }
    
    public LuckPermsTitleService getTitleService() {
        return titleService;
    }
    
    // ===== BC 치장 상점 시스템 =====
    
    public void setBCShopService(BCShopService service) {
        this.bcShopService = service;
    }
    
    public BCShopService getBCShopService() {
        return bcShopService;
    }
    
    // ===== Phase 5.D 거래 시스템 =====
    
    public void setTradeService(TradeService service) {
        this.tradeService = service;
    }
    
    public TradeService getTradeService() {
        return tradeService;
    }
    
    // ===== Phase 7 Enhance 시스템 =====
    
    public void setEnchantService(CustomEnchantService service) {
        this.enchantService = service;
    }
    
    public CustomEnchantService getEnchantService() {
        return enchantService;
    }
    
    public void setEnchantRegistry(CustomEnchantRegistry registry) {
        this.enchantRegistry = registry;
    }
    
    public CustomEnchantRegistry getEnchantRegistry() {
        return enchantRegistry;
    }
    
    public void setLampService(LampService service) {
        this.lampService = service;
    }
    
    public LampService getLampService() {
        return lampService;
    }
    
    public void setLampRegistry(LampRegistry registry) {
        this.lampRegistry = registry;
    }
    
    public LampRegistry getLampRegistry() {
        return lampRegistry;
    }
    
    public void setUpgradeService(UpgradeService service) {
        this.upgradeService = service;
    }
    
    public UpgradeService getUpgradeService() {
        return upgradeService;
    }
    
    public void setUpgradeConfig(UpgradeConfig config) {
        this.upgradeConfig = config;
    }
    
    public UpgradeConfig getUpgradeConfig() {
        return upgradeConfig;
    }
    
    public void setUpgradeGui(UpgradeGui gui) {
        this.upgradeGui = gui;
    }
    
    public UpgradeGui getUpgradeGui() {
        return upgradeGui;
    }
    
    // ===== Block Processing 파이프라인 =====
    
    public void setBlockProcessingService(BlockProcessingService service) {
        this.blockProcessingService = service;
    }
    
    public BlockProcessingService getBlockProcessingService() {
        return blockProcessingService;
    }
    
    // ===== Phase 8 Recovery 시스템 =====
    
    public void setRecoveryStorageManager(kr.bapuri.tycoon.recovery.RecoveryStorageManager manager) {
        this.recoveryStorageManager = manager;
    }
    
    public kr.bapuri.tycoon.recovery.RecoveryStorageManager getRecoveryStorageManager() {
        return recoveryStorageManager;
    }
    
    // ===== Phase 8 Mod 연동 =====
    
    public void setModDataService(ModDataService service) {
        this.modDataService = service;
    }
    
    public ModDataService getModDataService() {
        return modDataService;
    }
    
    public void setModRequestHandler(ModRequestHandler handler) {
        this.modRequestHandler = handler;
    }
    
    public ModRequestHandler getModRequestHandler() {
        return modRequestHandler;
    }
    
    public void setModEventBridge(ModEventBridge bridge) {
        this.modEventBridge = bridge;
    }
    
    public ModEventBridge getModEventBridge() {
        return modEventBridge;
    }
    
    // ===== Phase 승급효과 =====
    
    public void setMiningEfficiencyEnchant(kr.bapuri.tycoon.job.miner.MiningEfficiencyEnchant enchant) {
        this.miningEfficiencyEnchant = enchant;
    }
    
    public kr.bapuri.tycoon.job.miner.MiningEfficiencyEnchant getMiningEfficiencyEnchant() {
        return miningEfficiencyEnchant;
    }
}
