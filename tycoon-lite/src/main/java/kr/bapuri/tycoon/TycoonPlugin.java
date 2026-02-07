package kr.bapuri.tycoon;

import kr.bapuri.tycoon.admin.AdminService;
import kr.bapuri.tycoon.antiexploit.CustomItemVanillaBlocker;
import kr.bapuri.tycoon.antiexploit.VillagerTradeBlocker;
import kr.bapuri.tycoon.antiexploit.VillagerTradeCommand;
import kr.bapuri.tycoon.antiexploit.XrayCommand;
import kr.bapuri.tycoon.antiexploit.XrayHeuristicAnalyzer;
import kr.bapuri.tycoon.bootstrap.ConfigManager;
import kr.bapuri.tycoon.bootstrap.ConfigUpdater;
import kr.bapuri.tycoon.bootstrap.ListenerRegistry;
import kr.bapuri.tycoon.bootstrap.ServiceRegistry;
import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.economy.vault.VaultIntegration;
import kr.bapuri.tycoon.integration.CitizensIntegration;
import kr.bapuri.tycoon.integration.CoreProtectIntegration;
import kr.bapuri.tycoon.integration.WorldGuardIntegration;
import kr.bapuri.tycoon.item.CoreItemAuthenticator;
import kr.bapuri.tycoon.item.CoreItemCommand;
import kr.bapuri.tycoon.item.CoreItemService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerSessionListener;
import kr.bapuri.tycoon.shop.ShopAdminCommand;
import kr.bapuri.tycoon.shop.ShopGuiManager;
import kr.bapuri.tycoon.shop.ShopNpcRegistry;
import kr.bapuri.tycoon.shop.ShopService;
import kr.bapuri.tycoon.shop.job.FarmerShop;
import kr.bapuri.tycoon.shop.job.FisherShop;
import kr.bapuri.tycoon.shop.job.MinerShop;
import kr.bapuri.tycoon.shop.price.DynamicPriceTracker;
import kr.bapuri.tycoon.world.AngelChestIntegration;
import kr.bapuri.tycoon.world.PortalIsolationListener;
import kr.bapuri.tycoon.world.WorldSpawnListener;
import kr.bapuri.tycoon.world.PvpDamageCommand;
import kr.bapuri.tycoon.world.PvpDamageListener;
import kr.bapuri.tycoon.world.WildSpawnManager;
import kr.bapuri.tycoon.world.WorldManager;
import kr.bapuri.tycoon.world.WorldResetCommand;
import kr.bapuri.tycoon.world.WorldResetScheduler;
import kr.bapuri.tycoon.world.WorldTpCommand;
import kr.bapuri.tycoon.job.JobRegistry;
import kr.bapuri.tycoon.job.JobService;
import kr.bapuri.tycoon.job.JobsConfigLoader;
import kr.bapuri.tycoon.job.Tier1JobCommand;
import kr.bapuri.tycoon.job.common.GradeBonusConfig;
import kr.bapuri.tycoon.job.common.JobExpCalculator;
import kr.bapuri.tycoon.job.common.SellService;
import kr.bapuri.tycoon.mod.ModDataService;
import kr.bapuri.tycoon.mod.ModEventBridge;
import kr.bapuri.tycoon.mod.ModPlayerListener;
import kr.bapuri.tycoon.mod.ModRequestHandler;
import kr.bapuri.tycoon.codex.CodexAdminCommand;
import kr.bapuri.tycoon.codex.CodexCommand;
import kr.bapuri.tycoon.codex.CodexRegistry;
import kr.bapuri.tycoon.codex.CodexService;
import kr.bapuri.tycoon.achievement.AchievementCommand;
import kr.bapuri.tycoon.achievement.AchievementListener;
import kr.bapuri.tycoon.achievement.AchievementRegistry;
import kr.bapuri.tycoon.achievement.AchievementService;
import kr.bapuri.tycoon.achievement.VanillaAdvancementListener;
import kr.bapuri.tycoon.title.LuckPermsTitleService;
import kr.bapuri.tycoon.title.TitleCommand;
import kr.bapuri.tycoon.title.TitleRegistry;
import kr.bapuri.tycoon.bcshop.BCShopCommand;
import kr.bapuri.tycoon.bcshop.BCShopService;
import kr.bapuri.tycoon.trade.TradeCommand;
import kr.bapuri.tycoon.trade.TradeCooldownManager;
import kr.bapuri.tycoon.trade.TradeHistoryManager;
import kr.bapuri.tycoon.trade.TradeListener;
import kr.bapuri.tycoon.trade.TradeService;
import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.integration.TycoonPlaceholders;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * TycoonLite 메인 플러그인 클래스
 * 
 * <h2>아키텍처 개요</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                        TycoonPlugin                             │
 * │  ┌───────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
 * │  │ ServiceRegistry│  │ ListenerRegistry│  │ ConfigManager   │   │
 * │  │ (서비스 컨테이너) │  │ (리스너 등록)    │  │ (설정 로드)     │   │
 * │  └───────────────┘  └─────────────────┘  └─────────────────┘   │
 * └─────────────────────────────────────────────────────────────────┘
 *                              ↓
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                      Core Services                              │
 * │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │
 * │  │PlayerDataMgr │ │EconomyService│ │ WorldManager │             │
 * │  │(플레이어 데이터)│ │(경제 시스템)   │ │(월드 관리)    │             │
 * │  └──────────────┘ └──────────────┘ └──────────────┘             │
 * └─────────────────────────────────────────────────────────────────┘
 *                              ↓
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    Feature Systems                              │
 * │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────────────┐│
 * │  │ Jobs   │ │ Shop   │ │ Codex  │ │ Trade  │ │ Enhance        ││
 * │  │(직업)   │ │(상점)   │ │(도감)   │ │(거래)   │ │(인챈트/램프/강화)││
 * │  └────────┘ └────────┘ └────────┘ └────────┘ └────────────────┘│
 * └─────────────────────────────────────────────────────────────────┘
 *                              ↓
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    External Integrations                        │
 * │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
 * │  │ Vault    │ │ Citizens │ │ Lands    │ │AngelChest│           │
 * │  │(경제API)  │ │(NPC)     │ │(땅 시스템)│ │(데스체스트)│           │
 * │  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 * 
 * <h2>설계 원칙</h2>
 * <ul>
 *   <li><b>Setter Injection</b>: 순환 의존성 방지를 위해 생성자 대신 setter 사용</li>
 *   <li><b>Optional Integration</b>: 외부 플러그인은 softdepend로 선언, 없어도 동작</li>
 *   <li><b>Config-Driven</b>: 모든 수치/설정은 YAML로 외부화 (밸런싱 용이)</li>
 *   <li><b>Phase 기반 개발</b>: 단계별 기능 추가, 이전 Phase는 LOCKED</li>
 * </ul>
 * 
 * <h2>Phase 이력</h2>
 * <ul>
 *   <li>Phase 0: 스코프 확정 (KEEP/DROP/HOLD)</li>
 *   <li>Phase 1-1.5: Minimal Boot + Core Refactoring</li>
 *   <li>Phase 2: 데이터 계층 (PlayerData, Backup)</li>
 *   <li>Phase 3: 경제/상점 + 기본 차단</li>
 *   <li>Phase 3.5: 월드 시스템 (리셋, 스폰, AngelChest)</li>
 *   <li>Phase 4: Tier 1 직업 (Miner, Farmer, Fisher)</li>
 *   <li>Phase 5: 주변부 시스템 (Codex, Achievement, Trade, Title)</li>
 *   <li>Phase 6: Enhance 이식 (Enchant, Lamp, Upgrade)</li>
 *   <li>Phase 7: 통합 및 연동 (GUI, Lands)</li>
 *   <li>Phase 8: 런타임 테스트 및 문서화</li>
 * </ul>
 * 
 * <h2>의도적 미구현 (v1.1 예정)</h2>
 * <ul>
 *   <li>Tier 2 직업 (Artisan, Chef, Herbalist)</li>
 *   <li>Pity 시스템 (어부 천장)</li>
 *   <li>커스텀 아이템 (CustomCrops, Oraxen 연동)</li>
 *   <li>던전/카지노/헌터 시스템</li>
 * </ul>
 * 
 * @author Bapuri
 * @version 1.0.0-SNAPSHOT
 * @see ServiceRegistry
 * @see <a href="docs/planning/LITE_MASTER_TRACKER.md">마스터 추적 문서</a>
 */
public class TycoonPlugin extends JavaPlugin {

    private static TycoonPlugin instance;
    
    // ===== Registries =====
    private ServiceRegistry services;
    private ListenerRegistry listeners;
    
    // ===== Achievement System =====
    private AchievementListener achievementListener;
    
    // ===== Phase 3.C Antiexploit =====
    private CustomItemVanillaBlocker customItemVanillaBlocker;
    private VillagerTradeBlocker villagerTradeBlocker;
    private FileConfiguration antiexploitConfig;
    
    // ===== Phase 3.5 World System =====
    private WorldResetScheduler worldResetScheduler;
    private AngelChestIntegration angelChestIntegration;
    
    // ===== 세션 기반 효과 메시지 토글 =====
    // 효과 메시지를 끈 플레이어 UUID (메모리에만 유지, 재접속 시 리셋)
    private final java.util.Set<java.util.UUID> effectMsgDisabledPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        instance = this;
        
        long startTime = System.currentTimeMillis();
        
        getLogger().info("========================================");
        getLogger().info("Tycoon Lite v" + getDescription().getVersion());
        getLogger().info("========================================");
        
        // Step 1: 의존성 확인
        if (!checkDependencies()) {
            getLogger().severe("필수 플러그인이 없습니다. 플러그인을 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Step 2: 서비스 초기화
        initServices();
        
        // Step 3: 리스너 등록
        initListeners();
        
        // Step 4: 명령어 등록
        initCommands();
        
        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("플러그인이 활성화되었습니다. (" + loadTime + "ms)");
    }
    
    /**
     * 서비스 초기화 (ServiceRegistry 사용)
     *
     * 아키텍처 레이어와의 매핑:
     * - BOOTSTRAP / CORE SERVICES:
     *   - ConfigUpdater / ConfigManager
     *   - AdminService / PlayerDataManager / WorldManager
     *   - ServiceRegistry 생성 및 PlayerDataManager 스케줄러 시작
     * - INTEGRATION LAYER:
     *   - CitizensIntegration / WorldGuardIntegration / CoreProtectIntegration / LandsIntegration / VaultIntegration
     * - FEATURE SYSTEMS LAYER:
     *   - initShopSystem / initCoreItemSystem / initAntiexploitSystem / initWorldSystem / initJobSystem /
     *     initCodexSystem / initAchievementSystem / initTitleSystem / initTradeSystem / initRecoverySystem /
     *     initEnhanceSystem / initTaxSystem / initAutoFarmSystem 등
     * - MOD COMMUNICATION LAYER:
     *   - Mod 관련 서비스/리스너는 각 initXXXSystem 내부에서 Core/Feature 서비스를 기반으로 초기화됨
     */
    private void initServices() {
        // Step 0: 설정 파일 자동 업데이트 (새 옵션 추가, 기존 값 유지)
        ConfigUpdater configUpdater = new ConfigUpdater(this);
        configUpdater.updateAllConfigs();
        
        // Config
        saveDefaultConfig();
        ConfigManager configManager = new ConfigManager(this);
        getLogger().info("설정 로드 완료");
        
        // Core Services (CORE SERVICES LAYER)
        AdminService adminService = new AdminService(this);
        PlayerDataManager playerDataManager = new PlayerDataManager(this);
        playerDataManager.setAdminService(adminService);
        WorldManager worldManager = new WorldManager(this);
        
        // ServiceRegistry 생성
        services = new ServiceRegistry(configManager, playerDataManager, adminService, worldManager);
        
        // [Phase 2] 자동 저장 및 스냅샷 스케줄러 시작
        playerDataManager.startAutoSave();
        playerDataManager.startSnapshotScheduler();
        
        // Integration (INTEGRATION LAYER)
        CitizensIntegration citizens = new CitizensIntegration(this);
        WorldGuardIntegration worldGuard = new WorldGuardIntegration(this);
        CoreProtectIntegration coreProtect = new CoreProtectIntegration(this);
        
        services.setIntegrations(citizens, worldGuard, coreProtect);
        
        // [Phase 7] Lands Integration
        kr.bapuri.tycoon.integration.LandsIntegration landsIntegration = 
            new kr.bapuri.tycoon.integration.LandsIntegration(this);
        services.setLandsIntegration(landsIntegration);
        
        // [Phase 7] Lands Listener (권한 체크 + 모드 연동)
        kr.bapuri.tycoon.integration.LandsListener landsListener = 
            new kr.bapuri.tycoon.integration.LandsListener(this, services);
        if (landsIntegration.isAvailable()) {
            getServer().getPluginManager().registerEvents(landsListener, this);
            getLogger().info("✓ Lands 연동 활성화됨 (권한 체크 + 모드 연동)");
        }
        // landsListener는 ModDataService에서 콜백 등록 시 사용
        services.setLandsListener(landsListener);
        
        // Citizens 리스너는 별도 등록 (NPC 클릭 핸들링)
        if (citizens.isAvailable()) {
            citizens.registerListener();
        }
        
        // [Config 연동] NPC 텔레포트 서비스 (config.yml의 npcTeleport 섹션)
        if (citizens.isAvailable()) {
            kr.bapuri.tycoon.world.NpcTeleportService npcTeleportService = 
                new kr.bapuri.tycoon.world.NpcTeleportService(this, citizens, worldManager);
            getLogger().info("✓ NPC 텔레포트 서비스 초기화됨");
        }
        
        // [Phase 3] Economy 서비스
        EconomyService economyService = new EconomyService(playerDataManager, adminService);
        services.setEconomyService(economyService);
        getLogger().info("경제 서비스 초기화 완료");
        
        // [Phase 3] Vault 연동
        VaultIntegration vault = new VaultIntegration(this, economyService);
        if (vault.register()) {
            services.setVaultIntegration(vault);
            getLogger().info("✓ Vault Economy Provider 등록됨");
        } else {
            getLogger().warning("Vault 연동 실패 - 외부 플러그인에서 경제 사용 불가");
        }
        
        // [Phase 3.B] 상점 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initShopSystem(economyService, citizens);
        
        // [Phase 3.C] 아이템 및 안티익스플로잇 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initCoreItemSystem();
        initAntiexploitSystem();
        
        // [Phase 3.5] 월드 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initWorldSystem();
        
        // [Phase 4.A] 직업 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initJobSystem();
        
        // [Phase 5.A] 도감 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initCodexSystem();
        
        // [Phase 5.B] 업적 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initAchievementSystem();
        
        // [Phase 5.C] 칭호 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initTitleSystem();
        
        // [Phase 5.D] 거래 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initTradeSystem();
        
        // [Phase 8] 보관소 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initRecoverySystem();
        
        // [Phase 7] 강화 시스템 초기화 (인챈트, 램프, 강화) (FEATURE SYSTEMS LAYER)
        initEnhanceSystem();
        
        // [Phase 5.F] PlaceholderAPI 연동 (INTEGRATION LAYER)
        initPlaceholderAPI();
        
        // [Migration] 아이템 마이그레이션 서비스 (온라인 마이그레이션) (FEATURE SYSTEMS LAYER)
        initMigrationService();
        
        // [세금 시스템] 세금 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initTaxSystem();
        
        // [자동화 팜 제한] 자동화 팜 시스템 초기화 (FEATURE SYSTEMS LAYER)
        initAutoFarmSystem();
        
        getLogger().info("서비스 초기화 완료");
    }
    
    /**
     * [Phase 3.5] 월드 시스템 초기화
     */
    private void initWorldSystem() {
        WorldManager worldManager = services.getWorldManager();
        
        // 연결된 Wild 차원 로드 (네더/엔드)
        worldManager.loadLinkedDimensions();
        
        // 월드 설정 적용 (서버 시작 후 1틱 뒤에 실행)
        getServer().getScheduler().runTaskLater(this, () -> {
            worldManager.applyWorldSettings();
        }, 1L);
        
        // PortalIsolationListener 등록 (Town 포탈 차단)
        PortalIsolationListener portalListener = new PortalIsolationListener(worldManager, getLogger());
        getServer().getPluginManager().registerEvents(portalListener, this);
        
        // WorldSpawnListener 등록 (리스폰/재접속 위치 관리)
        WorldSpawnListener worldSpawnListener = new WorldSpawnListener(this, worldManager);
        getServer().getPluginManager().registerEvents(worldSpawnListener, this);
        getLogger().info("  ✓ WorldSpawnListener 등록 완료");
        
        // PvpDamageListener 등록 (Wild PvP 데미지 배율)
        PvpDamageListener pvpListener = new PvpDamageListener(worldManager, getLogger());
        getServer().getPluginManager().registerEvents(pvpListener, this);
        
        // PvpDamageCommand 등록
        PvpDamageCommand pvpCommand = new PvpDamageCommand(worldManager);
        registerCommand("pvpdamage", pvpCommand, pvpCommand);
        
        // WildSpawnManager 초기화 (기반암 플랫폼 + 귀환 NPC)
        WildSpawnManager wildSpawnManager = new WildSpawnManager(this);
        wildSpawnManager.setWorldManager(worldManager);
        getLogger().info("  ✓ WildSpawnManager 초기화 완료");
        
        // AngelChest 연동 초기화 (DeathChest)
        this.angelChestIntegration = new AngelChestIntegration(this);
        if (angelChestIntegration.isAvailable()) {
            getLogger().info("  ✓ AngelChest 연동 완료 (v" + angelChestIntegration.getVersion() + ")");
            
            // [Phase 8] 보호권 발동 시 AngelChest 생성 차단 리스너
            kr.bapuri.tycoon.integration.AngelChestProtectionBlocker blocker = 
                new kr.bapuri.tycoon.integration.AngelChestProtectionBlocker(this);
            if (blocker.tryRegister()) {
                getLogger().info("  ✓ AngelChest 보호권 충돌 방지 활성화");
            }
        } else {
            getLogger().info("  ○ AngelChest 없음 - DeathChest 이관 비활성화");
        }
        
        // WorldResetCommand 등록
        WorldResetCommand resetCommand = new WorldResetCommand(this, worldManager);
        resetCommand.setWildSpawnManager(wildSpawnManager);
        resetCommand.setDeathChestIntegration(angelChestIntegration);
        registerCommand("worldreset", resetCommand, resetCommand);
        
        // 크래시 복구 확인
        resetCommand.checkAndRecoverFromCrash();
        
        // WorldResetScheduler 초기화 (기본 비활성화)
        worldResetScheduler = new WorldResetScheduler(this, worldManager);
        worldResetScheduler.setWorldResetCommand(resetCommand);
        worldResetScheduler.loadConfig();
        worldResetScheduler.start();
        
        // WorldTpCommand 등록 (관리자 월드 텔레포트)
        WorldTpCommand worldTpCommand = new WorldTpCommand(worldManager);
        registerCommand("worldtp", worldTpCommand, worldTpCommand);
        
        // Citizens 귀환 NPC 핸들러 등록
        registerWildReturnNpcHandler(wildSpawnManager);
        
        getLogger().info("✓ 월드 시스템 초기화 완료");
    }
    
    /**
     * [Phase 4.A] 직업 시스템 초기화
     * [Phase 4.B] Miner 시스템 추가
     */
    private void initJobSystem() {
        PlayerDataManager playerDataManager = services.getPlayerDataManager();
        EconomyService economyService = services.getEconomyService();
        WorldManager worldManager = services.getWorldManager();
        
        // jobs.yml 로드
        saveResource("jobs.yml", false);
        JobsConfigLoader configLoader = new JobsConfigLoader(this, playerDataManager);
        configLoader.load();
        services.setJobsConfigLoader(configLoader);
        
        // [Phase 승급효과] GradeBonusConfig 로드
        GradeBonusConfig gradeBonusConfig = new GradeBonusConfig(this);
        services.setGradeBonusConfig(gradeBonusConfig);
        
        // 경험치 공식 설정 로드 (Phase 4.E)
        JobExpCalculator.loadFromConfig(configLoader.getExpFormulaSection(), getLogger());
        
        // JobRegistry 생성
        JobRegistry registry = new JobRegistry(this, configLoader);
        registry.loadUnlockConditions();
        services.setJobRegistry(registry);
        
        // JobService 생성
        JobService jobService = new JobService(this, playerDataManager, configLoader, registry);
        services.setJobService(jobService);
        
        // SellService 생성
        SellService sellService = new SellService(this, playerDataManager, economyService);
        sellService.loadConfig();
        services.setSellService(sellService);
        
        // [Phase 8] ModDataService 생성 및 연동 (JSON 프로토콜, 양방향 통신)
        ModDataService modDataService = new ModDataService(this, playerDataManager, jobService);
        modDataService.setServiceRegistry(services);
        modDataService.initialize();
        services.setModDataService(modDataService); // ServiceRegistry에 저장
        
        // [Phase 1 동기화] ModEventBridge 생성 및 연동 (실시간 이벤트 → 패킷 변환)
        ModEventBridge modEventBridge = new ModEventBridge(modDataService, getLogger());
        modEventBridge.setEnabled(modDataService.isEnabled());
        services.setModEventBridge(modEventBridge);
        
        // [Phase 8] ModRequestHandler 생성 및 연동 (클라이언트 요청 수신)
        // 주의: CodexService는 나중에 initCodexSystem()에서 주입됨
        ModRequestHandler modRequestHandler = new ModRequestHandler(this);
        modRequestHandler.setModDataService(modDataService);
        modRequestHandler.setPlayerDataManager(playerDataManager);
        modRequestHandler.setJobService(jobService);
        modRequestHandler.initialize();
        services.setModRequestHandler(modRequestHandler); // 나중에 CodexService 주입용
        
        // LandsListener와 ModDataService 연동 (모드에 땅 정보 전송)
        kr.bapuri.tycoon.integration.LandsListener landsListener = services.getLandsListener();
        if (landsListener != null) {
            modDataService.registerLandsCallback(landsListener);
        }
        
        // [Phase 8] ModPlayerListener 등록 (접속/월드이동/Vital 이벤트)
        ModPlayerListener modPlayerListener = new ModPlayerListener(this);
        modPlayerListener.setModDataService(modDataService);
        modPlayerListener.initialize();
        getServer().getPluginManager().registerEvents(modPlayerListener, this);
        
        // [Phase 8] EconomyService에 경제 변동 콜백 등록 (모드에 ECONOMY_UPDATE 자동 전송)
        economyService.setEconomyChangeCallback(uuid -> {
            org.bukkit.entity.Player player = getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                modDataService.sendEconomyUpdate(player);
            }
        });
        
        // [Phase 8] SellService에 직업 변경 콜백 등록 (모드에 JOB_DATA 자동 전송)
        sellService.setJobChangeCallback(player -> {
            if (player != null && player.isOnline()) {
                modDataService.sendJobData(player);
            }
        });
        
        // [Phase 8] AbstractJobExpService에 직업 변경 콜백 등록 (채굴/수확/낚시 등)
        kr.bapuri.tycoon.job.common.AbstractJobExpService.setJobChangeCallback(player -> {
            if (player != null && player.isOnline()) {
                modDataService.sendJobData(player);
            }
        });
        
        // [Phase 1 동기화] AbstractJobGradeService에 승급 콜백 등록 (NPC에서 승급 시)
        kr.bapuri.tycoon.job.common.AbstractJobGradeService.setGradeUpCallback(
            (player, jobType, newGrade, gradeTitle, bonuses) -> {
                if (modEventBridge != null && player != null && player.isOnline()) {
                    modEventBridge.onJobGradeUp(player, jobType, newGrade, gradeTitle, bonuses);
                }
            }
        );
        
        // [Phase 4.B] Miner 시스템 초기화
        initMinerSystem(playerDataManager, economyService, registry, worldManager);
        
        // [Phase 4.C] Farmer 시스템 초기화
        initFarmerSystem(playerDataManager, economyService, registry);
        
        // [Phase 4.D] Fisher 시스템 초기화
        initFisherSystem(playerDataManager, economyService, registry, worldManager);
        
        // Tier1JobCommand 등록
        Tier1JobCommand jobCommand = new Tier1JobCommand(this, jobService, playerDataManager);
        registerCommand("job", jobCommand, jobCommand);
        
        // [Phase 승급효과] NPC 승급 시스템 초기화
        initPromoteNpcSystem(registry);
        
        getLogger().info("✓ 직업 시스템 초기화 완료 (Tier 2: " + 
                (configLoader.isTier2JobsEnabled() ? "활성화" : "비활성화") + ")");
    }
    
    /**
     * [Phase 승급효과] NPC 승급 시스템 초기화
     */
    private void initPromoteNpcSystem(kr.bapuri.tycoon.job.JobRegistry registry) {
        // PromoteNpcRegistry 로드
        kr.bapuri.tycoon.job.npc.PromoteNpcRegistry promoteNpcRegistry = 
            new kr.bapuri.tycoon.job.npc.PromoteNpcRegistry(this);
        promoteNpcRegistry.load();
        
        // PromoteMainGui 생성
        kr.bapuri.tycoon.job.npc.PromoteMainGui promoteMainGui = 
            new kr.bapuri.tycoon.job.npc.PromoteMainGui(this);
        promoteMainGui.setGradeBonusConfig(services.getGradeBonusConfig());
        
        // GradeService 등록
        for (kr.bapuri.tycoon.job.JobType jobType : kr.bapuri.tycoon.job.JobType.values()) {
            kr.bapuri.tycoon.job.common.AbstractJobGradeService gradeService = 
                registry.getGradeService(jobType);
            if (gradeService != null) {
                promoteMainGui.registerGradeService(jobType, gradeService);
            }
        }
        
        // MiningEfficiencyEnchant 설정 (ServiceRegistry에서 가져옴)
        kr.bapuri.tycoon.job.miner.MiningEfficiencyEnchant miningEnchant = services.getMiningEfficiencyEnchant();
        if (miningEnchant != null) {
            promoteMainGui.setMiningEfficiencyEnchant(miningEnchant);
        }
        
        getServer().getPluginManager().registerEvents(promoteMainGui, this);
        
        // PromoteNpcListener 생성
        kr.bapuri.tycoon.job.npc.PromoteNpcListener promoteNpcListener = 
            new kr.bapuri.tycoon.job.npc.PromoteNpcListener(this, promoteNpcRegistry, promoteMainGui);
        
        // ExpService 매핑 설정
        java.util.Map<kr.bapuri.tycoon.job.JobType, kr.bapuri.tycoon.job.common.AbstractJobExpService> expServices = 
            new java.util.HashMap<>();
        for (kr.bapuri.tycoon.job.JobType jobType : kr.bapuri.tycoon.job.JobType.values()) {
            kr.bapuri.tycoon.job.common.AbstractJobExpService expService = 
                registry.getExpService(jobType);
            if (expService != null) {
                expServices.put(jobType, expService);
            }
        }
        promoteNpcListener.setExpServices(expServices);
        
        // Citizens 연동
        services.getCitizensIntegration().ifPresent(promoteNpcListener::registerWithCitizens);
        
        getServer().getPluginManager().registerEvents(promoteNpcListener, this);
        
        // PromoteNpcCommand 등록
        kr.bapuri.tycoon.job.npc.PromoteNpcCommand promoteNpcCommand = 
            new kr.bapuri.tycoon.job.npc.PromoteNpcCommand(
                this, promoteNpcRegistry, promoteNpcListener, services.getCitizensIntegration());
        registerCommand("promotenpc", promoteNpcCommand, promoteNpcCommand);
        
        getLogger().info("  ✓ 승급 NPC 시스템 초기화 완료 - NPC 수: " + promoteNpcRegistry.size());
    }
    
    /**
     * [Phase 4.B] Miner 시스템 초기화
     */
    private void initMinerSystem(PlayerDataManager playerDataManager, 
                                  EconomyService economyService,
                                  JobRegistry registry,
                                  WorldManager worldManager) {
        // MinerConfig 로드 ([Phase 4.E] 생성자에서 자동 로드)
        kr.bapuri.tycoon.job.miner.MinerConfig minerConfig = 
            new kr.bapuri.tycoon.job.miner.MinerConfig(this);
        
        // MinerExpService 생성 및 등록
        kr.bapuri.tycoon.job.miner.MinerExpService minerExpService = 
            new kr.bapuri.tycoon.job.miner.MinerExpService(this, playerDataManager, minerConfig);
        registry.registerExpService(kr.bapuri.tycoon.job.JobType.MINER, minerExpService);
        
        // MinerGradeService 생성 및 등록
        kr.bapuri.tycoon.job.miner.MinerGradeService minerGradeService = 
            new kr.bapuri.tycoon.job.miner.MinerGradeService(this, playerDataManager, economyService, minerConfig);
        registry.registerGradeService(kr.bapuri.tycoon.job.JobType.MINER, minerGradeService);
        
        // [Phase 승급효과] ExpService에 GradeBonusConfig 및 gradeProvider 연결
        GradeBonusConfig gradeBonusConfig = services.getGradeBonusConfig();
        if (gradeBonusConfig != null) {
            minerExpService.setGradeBonusConfig(gradeBonusConfig);
            minerExpService.setGradeProvider(minerGradeService::getGrade);
        }
        
        // MinerListener 등록
        kr.bapuri.tycoon.job.miner.MinerListener minerListener = 
            new kr.bapuri.tycoon.job.miner.MinerListener(this, minerExpService, worldManager);
        minerListener.setGradeBonusConfig(gradeBonusConfig);
        minerListener.setGradeService(minerGradeService);
        getServer().getPluginManager().registerEvents(minerListener, this);
        
        // [Phase 승급효과] MiningEfficiencyEnchant 초기화
        kr.bapuri.tycoon.job.miner.MiningEfficiencyEnchant miningEfficiencyEnchant =
            new kr.bapuri.tycoon.job.miner.MiningEfficiencyEnchant(this);
        miningEfficiencyEnchant.setGradeBonusConfig(gradeBonusConfig);
        miningEfficiencyEnchant.setGradeService(minerGradeService);
        miningEfficiencyEnchant.setExpService(minerExpService);
        getServer().getPluginManager().registerEvents(miningEfficiencyEnchant, this);
        
        // ServiceRegistry에 저장 (PromoteMainGui에서 사용)
        services.setMiningEfficiencyEnchant(miningEfficiencyEnchant);
        
        // CopperOxidationHandler 초기화
        kr.bapuri.tycoon.oxidation.CopperOxidationHandler oxidationHandler = 
            new kr.bapuri.tycoon.oxidation.CopperOxidationHandler(this, minerConfig);
        getServer().getPluginManager().registerEvents(oxidationHandler, this);
        oxidationHandler.startOxidationTask();
        
        // MinerShop ↔ MinerExpService 연동
        ShopService shopService = services.getShopService();
        if (shopService != null) {
            MinerShop minerShop = shopService.getMinerShop();
            if (minerShop != null) {
                minerShop.setExpService(minerExpService);
                // [Phase 8] 판매액 기록용
                minerShop.setPlayerDataManager(playerDataManager);
                minerShop.setJobType(kr.bapuri.tycoon.job.JobType.MINER);
                // [Fix] 레벨 보너스 설정 (jobs.yml에서 로드)
                minerShop.setLevelBonusPercent(minerConfig.getLevelBonusPercent());
            }
        }
        
        // RefineryService 초기화 (stub)
        kr.bapuri.tycoon.job.miner.refinery.RefineryService refineryService = 
            new kr.bapuri.tycoon.job.miner.refinery.RefineryService(this);
        refineryService.setEnabled(minerConfig.isRefineryEnabled());
        refineryService.initialize();
        
        getLogger().info("  ✓ Miner 시스템 초기화 완료");
        getLogger().info("    - 산화 구리: " + (minerConfig.isOxidationEnabled() ? "활성화" : "비활성화"));
        getLogger().info("    - 정제소: " + (minerConfig.isRefineryEnabled() ? "활성화" : "비활성화"));
    }
    
    /**
     * [Phase 4.C] Farmer 시스템 초기화
     */
    private void initFarmerSystem(PlayerDataManager playerDataManager, 
                                  EconomyService economyService,
                                  JobRegistry registry) {
        // [Phase 승급효과] CropGrade 시스템 초기화
        kr.bapuri.tycoon.job.farmer.CropGrade.init(this);
        
        // FarmerConfig 로드 ([Phase 4.E] 생성자에서 자동 로드)
        kr.bapuri.tycoon.job.farmer.FarmerConfig farmerConfig = 
            new kr.bapuri.tycoon.job.farmer.FarmerConfig(this);
        
        // FarmerExpService 생성 및 등록
        kr.bapuri.tycoon.job.farmer.FarmerExpService farmerExpService = 
            new kr.bapuri.tycoon.job.farmer.FarmerExpService(this, playerDataManager, farmerConfig);
        registry.registerExpService(kr.bapuri.tycoon.job.JobType.FARMER, farmerExpService);
        
        // FarmerGradeService 생성 및 등록
        kr.bapuri.tycoon.job.farmer.FarmerGradeService farmerGradeService = 
            new kr.bapuri.tycoon.job.farmer.FarmerGradeService(this, playerDataManager, economyService, farmerConfig);
        registry.registerGradeService(kr.bapuri.tycoon.job.JobType.FARMER, farmerGradeService);
        
        // [Phase 승급효과] ExpService에 GradeBonusConfig 및 gradeProvider 연결
        GradeBonusConfig gradeBonusConfigFarmer = services.getGradeBonusConfig();
        if (gradeBonusConfigFarmer != null) {
            farmerExpService.setGradeBonusConfig(gradeBonusConfigFarmer);
            farmerExpService.setGradeProvider(farmerGradeService::getGrade);
        }
        
        // FarmerListener 등록
        kr.bapuri.tycoon.job.farmer.FarmerListener farmerListener = 
            new kr.bapuri.tycoon.job.farmer.FarmerListener(this, farmerExpService);
        farmerListener.setGradeBonusConfig(gradeBonusConfigFarmer);
        farmerListener.setGradeService(farmerGradeService);
        getServer().getPluginManager().registerEvents(farmerListener, this);
        
        // FarmlandLimitSystem 등록
        kr.bapuri.tycoon.job.farmer.FarmlandLimitSystem farmlandLimitSystem = 
            new kr.bapuri.tycoon.job.farmer.FarmlandLimitSystem(this, farmerConfig);
        getServer().getPluginManager().registerEvents(farmlandLimitSystem, this);
        
        // FarmerShop ↔ FarmerExpService 연동
        ShopService shopServiceFarmer = services.getShopService();
        if (shopServiceFarmer != null) {
            kr.bapuri.tycoon.shop.job.FarmerShop farmerShop = shopServiceFarmer.getFarmerShop();
            if (farmerShop != null) {
                farmerShop.setExpService(farmerExpService);
                // [Phase 8] 판매액 기록용
                farmerShop.setPlayerDataManager(playerDataManager);
                farmerShop.setJobType(kr.bapuri.tycoon.job.JobType.FARMER);
                // [Fix] 레벨 보너스 설정 (jobs.yml에서 로드)
                farmerShop.setLevelBonusPercent(farmerConfig.getLevelBonusPercent());
            }
        }
        
        getLogger().info("  ✓ Farmer 시스템 초기화 완료");
        getLogger().info("    - 농지 제한: " + (farmerConfig.isFarmlandLimitEnabled() ? 
            "활성화 (" + farmerConfig.getMaxFarmlandPerChunk() + "/청크)" : "비활성화"));
    }
    
    /**
     * [Phase 4.D] Fisher 시스템 초기화
     */
    private void initFisherSystem(PlayerDataManager playerDataManager, 
                                  EconomyService economyService,
                                  JobRegistry registry,
                                  WorldManager worldManager) {
        // [Phase 승급효과] FishRarity 시스템 초기화
        kr.bapuri.tycoon.job.fisher.FishRarity.init(this);
        
        // FisherConfig 로드 ([Phase 4.E] 생성자에서 자동 로드)
        kr.bapuri.tycoon.job.fisher.FisherConfig fisherConfig = 
            new kr.bapuri.tycoon.job.fisher.FisherConfig(this);
        
        // FishRarityDistribution 생성
        kr.bapuri.tycoon.job.fisher.FishRarityDistribution rarityDistribution = 
            new kr.bapuri.tycoon.job.fisher.FishRarityDistribution(fisherConfig);
        
        // FisherExpService 생성 및 등록
        kr.bapuri.tycoon.job.fisher.FisherExpService fisherExpService = 
            new kr.bapuri.tycoon.job.fisher.FisherExpService(this, playerDataManager, fisherConfig);
        registry.registerExpService(kr.bapuri.tycoon.job.JobType.FISHER, fisherExpService);
        
        // FisherGradeService 생성 및 등록
        kr.bapuri.tycoon.job.fisher.FisherGradeService fisherGradeService = 
            new kr.bapuri.tycoon.job.fisher.FisherGradeService(this, playerDataManager, economyService, fisherConfig);
        registry.registerGradeService(kr.bapuri.tycoon.job.JobType.FISHER, fisherGradeService);
        
        // [Phase 승급효과] ExpService에 GradeBonusConfig 및 gradeProvider 연결
        GradeBonusConfig gradeBonusConfigFisher = services.getGradeBonusConfig();
        if (gradeBonusConfigFisher != null) {
            fisherExpService.setGradeBonusConfig(gradeBonusConfigFisher);
            fisherExpService.setGradeProvider(fisherGradeService::getGrade);
        }
        
        // FisherListener 등록
        kr.bapuri.tycoon.job.fisher.FisherListener fisherListener = 
            new kr.bapuri.tycoon.job.fisher.FisherListener(this, fisherExpService, worldManager, rarityDistribution);
        fisherListener.setGradeBonusConfig(gradeBonusConfigFisher);
        fisherListener.setGradeService(fisherGradeService);
        getServer().getPluginManager().registerEvents(fisherListener, this);
        
        // Stub 초기화 (나중에 활성화)
        kr.bapuri.tycoon.job.fisher.PitySystem pitySystem = 
            new kr.bapuri.tycoon.job.fisher.PitySystem(fisherConfig);
        kr.bapuri.tycoon.job.fisher.FishLootTable lootTable = 
            new kr.bapuri.tycoon.job.fisher.FishLootTable(fisherConfig);
        
        // FisherShop ↔ FisherExpService 연동
        ShopService shopServiceFisher = services.getShopService();
        if (shopServiceFisher != null) {
            kr.bapuri.tycoon.shop.job.FisherShop fisherShop = shopServiceFisher.getFisherShop();
            if (fisherShop != null) {
                fisherShop.setExpService(fisherExpService);
                // [Phase 8] 판매액 기록용
                fisherShop.setPlayerDataManager(playerDataManager);
                fisherShop.setJobType(kr.bapuri.tycoon.job.JobType.FISHER);
                // [Fix] 레벨 보너스 설정 (jobs.yml에서 로드)
                fisherShop.setLevelBonusPercent(fisherConfig.getLevelBonusPercent());
            }
        }
        
        getLogger().info("  ✓ Fisher 시스템 초기화 완료");
        getLogger().info("    - Pity 시스템: " + (fisherConfig.isPityEnabled() ? "활성화" : "비활성화 (stub)"));
    }
    
    /**
     * [Phase 5.A] 도감 시스템 초기화
     */
    private void initCodexSystem() {
        PlayerDataManager playerDataManager = services.getPlayerDataManager();
        AdminService adminService = services.getAdminService();
        EconomyService economyService = services.getEconomyService();
        
        // codex.yml 저장 (없으면 기본값 생성)
        saveResource("codex.yml", false);
        
        // CodexRegistry 생성 (codex.yml 로드)
        CodexRegistry codexRegistry = new CodexRegistry(this);
        
        // CodexService 생성
        CodexService codexService = new CodexService(
            playerDataManager, codexRegistry, adminService, getLogger());
        codexService.setEconomyService(economyService);
        
        // [Phase 1 동기화] CodexService에 등록 콜백 설정 (모드에 실시간 알림)
        kr.bapuri.tycoon.mod.ModEventBridge modEventBridge = services.getModEventBridge();
        if (modEventBridge != null) {
            codexService.setRegisterCallback((player, material, displayName, reward) -> {
                modEventBridge.onCodexRegistered(player, material, displayName, reward);
            });
        }
        
        // ServiceRegistry에 등록
        services.setCodexService(codexService);
        
        // [Phase 8] ModDataService 및 ModRequestHandler에 도감 서비스 연결 (양방향 통신용)
        ModDataService modDataService = services.getModDataService();
        if (modDataService != null) {
            modDataService.setCodexService(codexService);
            modDataService.setCodexRegistry(codexRegistry);
        }
        ModRequestHandler modRequestHandler = services.getModRequestHandler();
        if (modRequestHandler != null) {
            modRequestHandler.setCodexService(codexService);
        }
        
        // [Phase 7] CodexGuiManager 생성 및 리스너 등록
        kr.bapuri.tycoon.codex.CodexGuiManager codexGuiManager = 
            new kr.bapuri.tycoon.codex.CodexGuiManager(this, codexService, playerDataManager);
        getServer().getPluginManager().registerEvents(codexGuiManager, this);
        
        // CodexCommand 등록 (GUI 매니저 연결)
        CodexCommand codexCommand = new CodexCommand(codexService);
        codexCommand.setGuiManager(codexGuiManager);
        registerCommand("codex", codexCommand, codexCommand);
        
        // CodexAdminCommand 등록
        CodexAdminCommand codexAdminCommand = new CodexAdminCommand(codexService);
        registerCommand("codexadmin", codexAdminCommand, codexAdminCommand);
        
        getLogger().info("✓ 도감 시스템 초기화 완료 (" + codexRegistry.getTotalCount() + "개 아이템, " 
            + codexRegistry.getCategoryOrder().size() + "개 카테고리, GUI 활성화)");
    }
    
    /**
     * [Phase 5.B] 업적 시스템 초기화
     */
    private void initAchievementSystem() {
        PlayerDataManager playerDataManager = services.getPlayerDataManager();
        EconomyService economyService = services.getEconomyService();
        CodexService codexService = services.getCodexService();
        JobService jobService = services.getJobService();
        
        // achievements.yml 저장 (없으면 기본값 생성)
        saveResource("achievements.yml", false);
        
        // AchievementRegistry 생성
        AchievementRegistry achievementRegistry = new AchievementRegistry(this);
        
        // AchievementService 생성
        AchievementService achievementService = new AchievementService(
            playerDataManager, achievementRegistry, getLogger());
        achievementService.setEconomyService(economyService);
        
        // ServiceRegistry에 등록
        services.setAchievementService(achievementService);
        
        // AchievementListener 생성 및 등록 (레거시 방식: TycoonPlugin 참조)
        this.achievementListener = new AchievementListener(this, achievementService);
        achievementListener.setCodexService(codexService);
        achievementListener.setJobService(jobService);
        getServer().getPluginManager().registerEvents(achievementListener, this);
        
        // AchievementCommand 등록
        AchievementCommand achievementCommand = new AchievementCommand(achievementService);
        registerCommand("achievement", achievementCommand, achievementCommand);
        
        // VanillaAdvancementListener 등록 (바닐라 진행도 연동)
        VanillaAdvancementListener vanillaListener = new VanillaAdvancementListener(
            achievementService, achievementRegistry, getLogger());
        getServer().getPluginManager().registerEvents(vanillaListener, this);
        
        getLogger().info("✓ 업적 시스템 초기화 완료 (" + achievementRegistry.getCount() + "개 업적, 바닐라 연동 활성화)");
    }
    
    /**
     * [Phase 5.C] 칭호 시스템 초기화
     */
    private void initTitleSystem() {
        PlayerDataManager playerDataManager = services.getPlayerDataManager();
        AchievementService achievementService = services.getAchievementService();
        
        // titles.yml 저장 (없으면 기본값 생성)
        saveResource("titles.yml", false);
        
        // TitleRegistry 생성
        TitleRegistry titleRegistry = new TitleRegistry(this);
        
        // LuckPermsTitleService 생성
        LuckPermsTitleService titleService = new LuckPermsTitleService(
            this, playerDataManager, titleRegistry, getLogger());
        
        // ServiceRegistry에 등록
        services.setTitleService(titleService);
        
        // AchievementService에 TitleGrantCallback 연결
        if (achievementService != null) {
            achievementService.setTitleGrantCallback((player, titleId) -> {
                titleService.unlockTitle(player, titleId);
            });
        }
        
        // TitleCommand 등록
        TitleCommand titleCommand = new TitleCommand(titleService);
        registerCommand("title", titleCommand, titleCommand);
        
        getLogger().info("✓ 칭호 시스템 초기화 완료 (" + titleRegistry.getCount() + "개 칭호, LuckPerms: " 
            + (titleService.isLuckPermsAvailable() ? "연동됨" : "미연동") + ")");
        
        // [BC Shop] BC 치장 상점 시스템 초기화
        EconomyService economyService = services.getEconomyService();
        initBCShopSystem(economyService, playerDataManager, titleRegistry, titleService);
    }
    
    /**
     * [BC Shop] BC 치장 상점 시스템 초기화
     * 
     * BC(BottCoin)로 구매하는 칭호/치장 상점 시스템입니다.
     */
    private void initBCShopSystem(EconomyService economyService, PlayerDataManager playerDataManager,
                                  TitleRegistry titleRegistry, LuckPermsTitleService titleService) {
        // purchasable_titles.yml, cosmetics.yml 저장
        saveResource("purchasable_titles.yml", false);
        saveResource("cosmetics.yml", false);
        
        // BCShopService 생성 및 초기화
        BCShopService bcShopService = new BCShopService(
            this, economyService, playerDataManager, titleRegistry, titleService);
        bcShopService.initialize();
        
        // ServiceRegistry에 등록 (필요시)
        services.setBCShopService(bcShopService);
        
        // NPC 연동 (Citizens가 있으면)
        services.getCitizensIntegration().ifPresent(bcShopService::registerNPCs);
        
        // 관리자 명령어 등록
        BCShopCommand bcShopCommand = new BCShopCommand(bcShopService);
        registerCommand("bcshop", bcShopCommand, bcShopCommand);
        
        getLogger().info("✓ BC 상점 시스템 초기화 완료");
    }
    
    /**
     * [Phase 5.D] 거래 시스템 초기화
     */
    private void initTradeSystem() {
        PlayerDataManager playerDataManager = services.getPlayerDataManager();
        EconomyService economyService = services.getEconomyService();
        
        // config.yml에서 trade 설정 로드
        boolean tradeEnabled = getConfig().getBoolean("trade.enabled", true);
        long requestTimeout = getConfig().getLong("trade.requestTimeout", 60);
        long globalCooldown = getConfig().getLong("trade.cooldown.global", 60);
        long perTargetCooldown = getConfig().getLong("trade.cooldown.perTarget", 120);
        
        // TradeCooldownManager 생성 및 설정 적용
        TradeCooldownManager cooldownManager = new TradeCooldownManager();
        cooldownManager.setGlobalCooldown(globalCooldown);
        cooldownManager.setPerTargetCooldown(perTargetCooldown);
        
        // TradeHistoryManager 생성
        TradeHistoryManager historyManager = new TradeHistoryManager(this);
        
        // TradeService 생성
        TradeService tradeService = new TradeService(
            this, economyService, playerDataManager, cooldownManager, historyManager);
        tradeService.setEnabled(tradeEnabled);
        tradeService.setRequestTimeout(requestTimeout);
        
        // ServiceRegistry에 등록
        services.setTradeService(tradeService);
        
        // [Phase 7] TradeGui 생성 및 리스너 등록
        kr.bapuri.tycoon.trade.TradeGui tradeGui = 
            new kr.bapuri.tycoon.trade.TradeGui(this, tradeService, playerDataManager);
        getServer().getPluginManager().registerEvents(tradeGui, this);
        
        // TradeListener 등록 (TradeGui 연결)
        TradeListener tradeListener = new TradeListener(this, tradeService, tradeGui);
        getServer().getPluginManager().registerEvents(tradeListener, this);
        
        // TradeCommand 등록
        TradeCommand tradeCommand = new TradeCommand(tradeService);
        // [Phase 8 버그수정] TradeGui 주입 - 수락 시 GUI 열기
        tradeCommand.setTradeGui(tradeGui);
        registerCommand("trade", tradeCommand, tradeCommand);
        
        getLogger().info("✓ 거래 시스템 초기화 완료 (enabled=" + tradeEnabled 
            + ", timeout=" + requestTimeout + "s"
            + ", globalCD=" + globalCooldown + "s"
            + ", targetCD=" + perTargetCooldown + "s, GUI 활성화)");
    }
    
    /**
     * [Phase 8] Recovery 시스템 초기화 (Town 보관소)
     * 
     * 사망 시 아이템을 Town NPC에서 회수할 수 있는 시스템.
     * AngelChest 만료 시 자동 이관은 별도 연동 필요.
     */
    private void initRecoverySystem() {
        PlayerDataManager playerDataManager = services.getPlayerDataManager();
        EconomyService economyService = services.getEconomyService();
        
        // RecoveryStorageManager 생성
        kr.bapuri.tycoon.recovery.RecoveryStorageManager storageManager = 
            new kr.bapuri.tycoon.recovery.RecoveryStorageManager(this);
        storageManager.setEconomyService(economyService);
        
        // RecoveryGui 생성 및 리스너 등록
        kr.bapuri.tycoon.recovery.RecoveryGui recoveryGui = 
            new kr.bapuri.tycoon.recovery.RecoveryGui(this, storageManager);
        getServer().getPluginManager().registerEvents(recoveryGui, this);
        
        // RecoveryNpcListener 생성 및 리스너 등록
        kr.bapuri.tycoon.recovery.RecoveryNpcListener npcListener = 
            new kr.bapuri.tycoon.recovery.RecoveryNpcListener(this, recoveryGui);
        getServer().getPluginManager().registerEvents(npcListener, this);
        
        // RecoveryCommand 등록
        kr.bapuri.tycoon.recovery.RecoveryCommand recoveryCommand = 
            new kr.bapuri.tycoon.recovery.RecoveryCommand(recoveryGui, storageManager);
        registerCommand("recovery", recoveryCommand, recoveryCommand);
        
        // ServiceRegistry에 등록 (onDisable에서 saveAll 호출용)
        services.setRecoveryStorageManager(storageManager);
        
        // [Phase 8] AngelChest 만료 시 Recovery 이관 연동
        if (angelChestIntegration != null && angelChestIntegration.isAvailable()) {
            angelChestIntegration.setRecoveryManager(storageManager);
            getLogger().info("  ✓ AngelChest → Recovery 이관 연동 완료");
        }
        
        getLogger().info("✓ 보관소 시스템 초기화 완료");
    }
    
    /**
     * [Phase 7] Enhance 시스템 초기화 (인챈트, 램프, 강화)
     */
    private void initEnhanceSystem() {
        EconomyService economyService = services.getEconomyService();
        
        // ===== 상수 초기화 =====
        kr.bapuri.tycoon.enhance.common.EnhanceConstants.init(this);
        
        // ===== 인챈트 시스템 =====
        kr.bapuri.tycoon.enhance.enchant.CustomEnchantRegistry enchantRegistry = 
            new kr.bapuri.tycoon.enhance.enchant.CustomEnchantRegistry(this);
        enchantRegistry.loadFromConfig();
        
        kr.bapuri.tycoon.enhance.enchant.CustomEnchantService enchantService = 
            new kr.bapuri.tycoon.enhance.enchant.CustomEnchantService(enchantRegistry, getLogger());
        
        kr.bapuri.tycoon.enhance.enchant.EnchantBookFactory enchantBookFactory = 
            new kr.bapuri.tycoon.enhance.enchant.EnchantBookFactory(enchantRegistry);
        
        services.setEnchantRegistry(enchantRegistry);
        services.setEnchantService(enchantService);
        
        // 인챈트 리스너 등록
        getServer().getPluginManager().registerEvents(
            new kr.bapuri.tycoon.enhance.enchant.EnchantApplyListener(enchantService, enchantRegistry, enchantBookFactory), this);
        
        // EnchantEffectListener 생성 및 등록 (BlockProcessingService 주입용 참조 유지)
        kr.bapuri.tycoon.enhance.enchant.EnchantEffectListener enchantEffectListener = 
            new kr.bapuri.tycoon.enhance.enchant.EnchantEffectListener(this, enchantRegistry, services.getJobRegistry());
        getServer().getPluginManager().registerEvents(enchantEffectListener, this);
        
        getLogger().info("  ✓ 인챈트 시스템 초기화 완료");
        
        // ===== 램프 시스템 =====
        kr.bapuri.tycoon.enhance.lamp.LampRegistry lampRegistry = 
            new kr.bapuri.tycoon.enhance.lamp.LampRegistry(this);
        lampRegistry.loadFromConfig();
        
        kr.bapuri.tycoon.enhance.lamp.LampItemFactory lampItemFactory = 
            new kr.bapuri.tycoon.enhance.lamp.LampItemFactory(lampRegistry);
        
        kr.bapuri.tycoon.enhance.lamp.LampService lampService = 
            new kr.bapuri.tycoon.enhance.lamp.LampService(lampRegistry, lampItemFactory, getLogger(), this);
        
        services.setLampRegistry(lampRegistry);
        services.setLampService(lampService);
        
        // 램프 리스너 등록
        // v4.0: GUI 기반 슬롯 선택 시스템
        kr.bapuri.tycoon.enhance.lamp.LampSlotSelectGui lampSlotSelectGui = 
            new kr.bapuri.tycoon.enhance.lamp.LampSlotSelectGui(lampService, getLogger());
        getServer().getPluginManager().registerEvents(lampSlotSelectGui, this);
        getServer().getPluginManager().registerEvents(
            new kr.bapuri.tycoon.enhance.lamp.LampApplyListener(lampService, lampSlotSelectGui), this);
        
        // LampEffectListener 생성 및 등록 (BlockProcessingService 주입용 참조 유지)
        kr.bapuri.tycoon.enhance.lamp.LampEffectListener lampEffectListener = 
            new kr.bapuri.tycoon.enhance.lamp.LampEffectListener(this, lampService, lampRegistry, economyService, services.getJobRegistry());
        getServer().getPluginManager().registerEvents(lampEffectListener, this);
        
        // 램프 티켓 리스너 (슬롯 확장권만 - 제거권은 덮어쓰기 방식으로 불필요)
        kr.bapuri.tycoon.enhance.lamp.LampSlotExpandTicket lampSlotExpandTicket = 
            new kr.bapuri.tycoon.enhance.lamp.LampSlotExpandTicket(this, lampService, economyService);
        getServer().getPluginManager().registerEvents(lampSlotExpandTicket, this);
        
        // [2026-01-29] 특수 아이템 상점 초기화
        initSpecialItemShop(economyService, lampItemFactory, lampSlotExpandTicket);
        
        getLogger().info("  ✓ 램프 시스템 초기화 완료");
        
        // ===== 강화 시스템 =====
        kr.bapuri.tycoon.enhance.upgrade.UpgradeConfig upgradeConfig = 
            new kr.bapuri.tycoon.enhance.upgrade.UpgradeConfig(this);
        upgradeConfig.loadFromConfig();
        
        kr.bapuri.tycoon.enhance.upgrade.UpgradeService upgradeService = 
            new kr.bapuri.tycoon.enhance.upgrade.UpgradeService(upgradeConfig, economyService, getLogger());
        
        kr.bapuri.tycoon.enhance.upgrade.UpgradeGui upgradeGui = 
            new kr.bapuri.tycoon.enhance.upgrade.UpgradeGui(upgradeService, economyService);
        
        services.setUpgradeConfig(upgradeConfig);
        services.setUpgradeService(upgradeService);
        services.setUpgradeGui(upgradeGui);
        
        // UpgradeStatCalculator 생성
        kr.bapuri.tycoon.enhance.upgrade.UpgradeStatCalculator statCalculator = 
            new kr.bapuri.tycoon.enhance.upgrade.UpgradeStatCalculator(upgradeConfig);
        
        // 강화 리스너 등록
        getServer().getPluginManager().registerEvents(
            new kr.bapuri.tycoon.enhance.upgrade.UpgradeListener(upgradeGui, this), this);
        getServer().getPluginManager().registerEvents(
            new kr.bapuri.tycoon.enhance.upgrade.UpgradeDamageListener(statCalculator), this);
        getServer().getPluginManager().registerEvents(
            new kr.bapuri.tycoon.enhance.upgrade.UpgradeNpcListener(this, upgradeGui), this);
        
        // 내구도 리스너 등록
        getServer().getPluginManager().registerEvents(
            new kr.bapuri.tycoon.enhance.durability.DurabilityListener(this), this);
        
        getLogger().info("  ✓ 강화 시스템 초기화 완료");
        
        // ===== Block Processing 파이프라인 =====
        kr.bapuri.tycoon.enhance.processing.BlockProcessingService blockProcessingService = 
            initBlockProcessingSystem(economyService);
        
        // 리스너에 BlockProcessingService 주입
        if (blockProcessingService != null) {
            enchantEffectListener.setBlockProcessingService(blockProcessingService);
            lampEffectListener.setBlockProcessingService(blockProcessingService);
            getLogger().info("  ✓ BlockProcessingService → Listeners 주입 완료");
        }
        
        // ===== 명령어 등록 =====
        kr.bapuri.tycoon.enhance.command.EnchantCommand enchantCommand = 
            new kr.bapuri.tycoon.enhance.command.EnchantCommand(enchantService, enchantRegistry, enchantBookFactory);
        registerCommand("enchant", enchantCommand, enchantCommand);
        
        kr.bapuri.tycoon.enhance.command.LampCommand lampCommand = 
            new kr.bapuri.tycoon.enhance.command.LampCommand(lampService, lampRegistry);
        registerCommand("lamp", lampCommand, lampCommand);
        
        kr.bapuri.tycoon.enhance.command.UpgradeCommand upgradeCommand = 
            new kr.bapuri.tycoon.enhance.command.UpgradeCommand(upgradeService, upgradeGui, statCalculator);
        registerCommand("upgrade", upgradeCommand, upgradeCommand);
        
        getLogger().info("✓ Enhance 시스템 초기화 완료");
    }
    
    /**
     * [Block Processing] 블록 처리 파이프라인 초기화
     * 
     * VEIN_MINER, MULTI_MINE, TREE_FELLER 등 블록 파괴 인챈트/램프 효과를
     * 통합 파이프라인으로 처리하여 Fortune, 등급 보너스, 직업 XP가 누락되지 않도록 함
     * 
     * @return 생성된 BlockProcessingService (비활성화 시 null)
     */
    private kr.bapuri.tycoon.enhance.processing.BlockProcessingService initBlockProcessingSystem(EconomyService economyService) {
        // config.yml에서 활성화 여부 확인
        boolean useNewProcessing = getConfig().getBoolean("processing.use-new-processing", true);
        
        if (!useNewProcessing) {
            getLogger().info("○ Block Processing 파이프라인 비활성화됨 (config.yml)");
            return null;
        }
        
        JobRegistry jobRegistry = services.getJobRegistry();
        WorldManager worldManager = services.getWorldManager();
        GradeBonusConfig gradeBonusConfig = services.getGradeBonusConfig();
        
        // BlockProcessingService 생성
        kr.bapuri.tycoon.enhance.processing.BlockProcessingService blockProcessingService = 
            new kr.bapuri.tycoon.enhance.processing.BlockProcessingService(this);
        
        // 프로세서 등록 (우선순위 순서대로 실행됨)
        // Priority 100: Fortune (바닐라 + 커스텀 IV/V)
        blockProcessingService.registerProcessor(
            new kr.bapuri.tycoon.enhance.processing.processors.FortuneProcessor());
        
        // Priority 150: EnchantDropBonus (EXPERTISE, LUCKY_HAND)
        blockProcessingService.registerProcessor(
            new kr.bapuri.tycoon.enhance.processing.processors.EnchantDropBonusProcessor());
        
        // Priority 200: GradeBonus (직업 등급별 yieldMulti)
        blockProcessingService.registerProcessor(
            new kr.bapuri.tycoon.enhance.processing.processors.GradeBonusProcessor(
                jobRegistry, worldManager, gradeBonusConfig));
        
        // Priority 400: LampEffect (AUTO_SMELT, GOLDEN_TOUCH 등)
        blockProcessingService.registerProcessor(
            new kr.bapuri.tycoon.enhance.processing.processors.LampEffectProcessor());
        
        // Priority 500: JobExp (최종 드롭 기준 XP 계산)
        blockProcessingService.registerProcessor(
            new kr.bapuri.tycoon.enhance.processing.processors.JobExpProcessor(jobRegistry, worldManager));
        
        // Priority 900: Delivery (TELEKINESIS + 드롭 + 블록 제거)
        blockProcessingService.registerProcessor(
            new kr.bapuri.tycoon.enhance.processing.processors.DeliveryProcessor(economyService));
        
        // ServiceRegistry에 등록
        services.setBlockProcessingService(blockProcessingService);
        
        getLogger().info("✓ Block Processing 파이프라인 초기화 완료 (프로세서: " + 
            blockProcessingService.getProcessorCount() + "개)");
        
        return blockProcessingService;
    }
    
    /**
     * [Phase 5.F] PlaceholderAPI 연동 초기화
     */
    private void initPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("○ PlaceholderAPI 없음 - 플레이스홀더 연동 생략");
            return;
        }
        
        TycoonPlaceholders placeholders = new TycoonPlaceholders(
            this,
            services.getEconomyService(),
            services.getJobService(),
            services.getPlayerDataManager(),
            services.getTitleService()
        );
        
        if (placeholders.tryRegister()) {
            getLogger().info("✓ PlaceholderAPI 연동 완료 (%tycoon_...)");
        }
    }
    
    /**
     * [Migration] 아이템 마이그레이션 서비스 초기화
     * config.yml의 migration.enabled=true일 때만 활성화
     */
    private void initMigrationService() {
        kr.bapuri.tycoon.migration.ItemMigrationService migrationService = 
            new kr.bapuri.tycoon.migration.ItemMigrationService(this);
        migrationService.initialize();
        
        if (migrationService.isEnabled()) {
            getLogger().info("✓ 아이템 마이그레이션 서비스 활성화");
        }
    }
    
    // ===== 세금 시스템 필드 =====
    private kr.bapuri.tycoon.tax.TaxScheduler taxScheduler;
    private kr.bapuri.tycoon.tax.VillagerRegistry villagerRegistry;
    
    // ===== 자동화 팜 제한 시스템 필드 =====
    private kr.bapuri.tycoon.antiexploit.AutoFarmTracker autoFarmTracker;
    
    /**
     * [세금 시스템] 세금 시스템 초기화
     * 
     * - TaxConfig: 설정 로드
     * - VillagerRegistry: 마을별 주민 등록
     * - IncomeTaxService: 소득세 계산
     * - LandTaxService: 토지세 계산
     * - TaxScheduler: 주기적 징수
     */
    private void initTaxSystem() {
        PlayerDataManager playerDataManager = services.getPlayerDataManager();
        EconomyService economyService = services.getEconomyService();
        
        // Lands 연동이 없으면 세금 시스템 비활성화
        java.util.Optional<kr.bapuri.tycoon.integration.LandsIntegration> optLands = services.getLandsIntegration();
        if (optLands.isEmpty()) {
            getLogger().info("○ Lands 없음 - 세금 시스템 비활성화");
            return;
        }
        kr.bapuri.tycoon.integration.LandsIntegration landsIntegration = optLands.get();
        
        // tax.yml 저장 (없으면 기본값 생성)
        saveResource("tax.yml", false);
        
        // TaxConfig 로드
        kr.bapuri.tycoon.tax.TaxConfig taxConfig = new kr.bapuri.tycoon.tax.TaxConfig(this);
        taxConfig.load();
        
        // 세금 시스템 비활성화 시 스킵
        if (!taxConfig.isEnabled()) {
            getLogger().info("○ 세금 시스템 비활성화됨 (tax.yml)");
            return;
        }
        
        // VillagerRegistry 생성 및 로드
        villagerRegistry = new kr.bapuri.tycoon.tax.VillagerRegistry(this);
        villagerRegistry.load();
        
        // IncomeTaxService 생성
        kr.bapuri.tycoon.tax.IncomeTaxService incomeTaxService = 
            new kr.bapuri.tycoon.tax.IncomeTaxService(getLogger(), taxConfig, playerDataManager);
        
        // EconomyService에 소득세 서비스 연결
        economyService.setIncomeTaxService(incomeTaxService);
        
        // LandTaxService 생성
        kr.bapuri.tycoon.tax.LandTaxService landTaxService = 
            new kr.bapuri.tycoon.tax.LandTaxService(
                getLogger(), taxConfig, villagerRegistry, 
                landsIntegration, economyService, playerDataManager);
        
        // TaxScheduler 생성 및 시작
        taxScheduler = new kr.bapuri.tycoon.tax.TaxScheduler(
            this, taxConfig, landTaxService, villagerRegistry, 
            landsIntegration, playerDataManager);
        taxScheduler.start();
        
        // 명령어 등록
        kr.bapuri.tycoon.tax.TaxCommand taxCommand = 
            new kr.bapuri.tycoon.tax.TaxCommand(taxConfig, incomeTaxService, landTaxService, landsIntegration);
        registerCommand("tax", taxCommand, taxCommand);
        
        kr.bapuri.tycoon.tax.LandTaxCommand landTaxCommand = 
            new kr.bapuri.tycoon.tax.LandTaxCommand(
                taxConfig, villagerRegistry, landTaxService, taxScheduler, landsIntegration);
        registerCommand("landtax", landTaxCommand, landTaxCommand);
        
        // LandsListener에 VillagerRegistry 주입 (Frozen 마을 차단용)
        kr.bapuri.tycoon.integration.LandsListener landsListener = services.getLandsListener();
        if (landsListener != null) {
            landsListener.setVillagerRegistry(villagerRegistry);
            getLogger().info("  ✓ LandsListener에 세금 시스템 연동됨");
        }
        
        getLogger().info("✓ 세금 시스템 초기화 완료 (주기: " + taxConfig.getPeriodHours() + "시간, " +
                        "청크당: " + taxConfig.getPerChunk() + " BD)");
    }
    
    /**
     * [자동화 팜 제한] 자동화 팜 시스템 초기화
     * 
     * 마을 내 자동화 팜 생산량 제한:
     * - 청크당 시간당 상한 (기본 1000개)
     * - 상한 도달 시 점진적 드롭 감소
     */
    private void initAutoFarmSystem() {
        // Lands 연동이 없으면 비활성화
        java.util.Optional<kr.bapuri.tycoon.integration.LandsIntegration> optLands = services.getLandsIntegration();
        if (optLands.isEmpty()) {
            getLogger().info("○ Lands 없음 - 자동화 팜 제한 비활성화");
            return;
        }
        kr.bapuri.tycoon.integration.LandsIntegration landsIntegration = optLands.get();
        
        // autofarm.yml 저장 (없으면 기본값 생성)
        saveResource("autofarm.yml", false);
        
        // AutoFarmConfig 로드
        kr.bapuri.tycoon.antiexploit.AutoFarmConfig autoFarmConfig = 
            new kr.bapuri.tycoon.antiexploit.AutoFarmConfig(this);
        autoFarmConfig.load();
        
        // 비활성화 시 스킵
        if (!autoFarmConfig.isEnabled()) {
            getLogger().info("○ 자동화 팜 제한 비활성화됨 (autofarm.yml)");
            return;
        }
        
        // AutoFarmTracker 생성 및 시작
        autoFarmTracker = new kr.bapuri.tycoon.antiexploit.AutoFarmTracker(
            this, autoFarmConfig, landsIntegration);
        autoFarmTracker.start();
        
        // AutoFarmLimiter 리스너 등록
        kr.bapuri.tycoon.antiexploit.AutoFarmLimiter autoFarmLimiter = 
            new kr.bapuri.tycoon.antiexploit.AutoFarmLimiter(
                this, autoFarmConfig, autoFarmTracker, landsIntegration);
        getServer().getPluginManager().registerEvents(autoFarmLimiter, this);
        
        getLogger().info("✓ 자동화 팜 제한 초기화 완료 (청크당: " + autoFarmConfig.getPerChunkPerHour() + 
                        "개/시간, 감소: " + (autoFarmConfig.isReductionEnabled() ? "활성화" : "비활성화") + ")");
    }
    
    /**
     * [Phase 3.5] Wild 귀환 NPC 클릭 핸들러 등록
     */
    private void registerWildReturnNpcHandler(WildSpawnManager wildSpawnManager) {
        java.util.Optional<CitizensIntegration> optCitizens = services.getCitizensIntegration();
        if (optCitizens.isEmpty()) {
            getLogger().info("  ○ Citizens 없음 - 귀환 NPC 핸들러 생략");
            return;
        }
        
        CitizensIntegration citizens = optCitizens.get();
        
        // 귀환 NPC 이름으로 핸들러 등록
        String npcName = wildSpawnManager.getReturnNpcName();
        citizens.registerHandler(npcName, (player, npc) -> {
            org.bukkit.Location returnLoc = wildSpawnManager.getReturnLocation();
            if (returnLoc != null) {
                player.teleport(returnLoc);
                player.sendMessage("§a마을로 돌아갑니다...");
            } else {
                player.sendMessage("§c귀환 위치를 찾을 수 없습니다.");
            }
        });
        
        // 컬러코드 없는 버전도 등록 (호환성)
        String plainName = org.bukkit.ChatColor.stripColor(npcName);
        if (!plainName.equals(npcName)) {
            citizens.registerHandler(plainName, (player, npc) -> {
                org.bukkit.Location returnLoc = wildSpawnManager.getReturnLocation();
                if (returnLoc != null) {
                    player.teleport(returnLoc);
                    player.sendMessage("§a마을로 돌아갑니다...");
                } else {
                    player.sendMessage("§c귀환 위치를 찾을 수 없습니다.");
                }
            });
        }
        
        getLogger().info("  ✓ 귀환 NPC 핸들러 등록 완료");
        
        // [Phase 8 버그수정] Town→Wild NPC 핸들러 등록
        registerTownToWildNpcHandler(wildSpawnManager);
    }
    
    /**
     * [Phase 8] Town→Wild NPC 클릭 핸들러 등록
     * 
     * 마을에 수동 설치된 NPC 클릭 시 야생 스폰 포인트 중 랜덤으로 텔레포트
     * 키워드: "야생", "wild", "Wild"
     */
    private void registerTownToWildNpcHandler(WildSpawnManager wildSpawnManager) {
        java.util.Optional<CitizensIntegration> optCitizens = services.getCitizensIntegration();
        if (optCitizens.isEmpty()) {
            return; // 이미 귀환 NPC에서 로그 출력됨
        }
        
        CitizensIntegration citizens = optCitizens.get();
        WorldManager worldManager = services.getWorldManager();
        
        // Wild 월드 이름 가져오기
        String wildWorldName = worldManager.getWorld(kr.bapuri.tycoon.world.WorldType.WILD)
            .map(org.bukkit.World::getName)
            .orElse("world_wild");
        
        // "야생" 키워드로 핸들러 등록
        citizens.registerHandler("야생", (player, npc) -> {
            org.bukkit.Location randomSpawn = wildSpawnManager.getRandomSpawnPoint(wildWorldName);
            if (randomSpawn != null) {
                player.teleport(randomSpawn);
                player.sendMessage("§a야생으로 이동합니다...");
            } else {
                player.sendMessage("§c야생 스폰 위치를 찾을 수 없습니다.");
            }
        });
        
        // "wild" 키워드도 등록 (영문)
        citizens.registerHandler("wild", (player, npc) -> {
            org.bukkit.Location randomSpawn = wildSpawnManager.getRandomSpawnPoint(wildWorldName);
            if (randomSpawn != null) {
                player.teleport(randomSpawn);
                player.sendMessage("§a야생으로 이동합니다...");
            } else {
                player.sendMessage("§c야생 스폰 위치를 찾을 수 없습니다.");
            }
        });
        
        getLogger().info("  ✓ Town→Wild NPC 핸들러 등록 완료 (키워드: 야생, wild)");
    }
    
    /**
     * [Phase 3.C] CoreItem 시스템 초기화
     * [Phase 8] CoreItemService, CoreItemCommand 추가
     */
    private void initCoreItemSystem() {
        // CoreItemAuthenticator 생성
        CoreItemAuthenticator coreItemAuthenticator = new CoreItemAuthenticator(this);
        services.setCoreItemAuthenticator(coreItemAuthenticator);
        
        // CoreItemService 생성
        CoreItemService coreItemService = new CoreItemService(this, coreItemAuthenticator);
        services.setCoreItemService(coreItemService);
        
        // CoreItemCommand 등록
        CoreItemCommand coreItemCommand = new CoreItemCommand(coreItemService);
        registerCommand("coreitem", coreItemCommand, coreItemCommand);
        
        // [Phase 8] CoreItemUseListener 등록 (귀환/텔레포트/뽑기권 사용)
        kr.bapuri.tycoon.item.CoreItemUseListener coreItemUseListener = 
            new kr.bapuri.tycoon.item.CoreItemUseListener(
                this, coreItemAuthenticator, coreItemService, services.getPlayerDataManager());
        getServer().getPluginManager().registerEvents(coreItemUseListener, this);
        
        // [Phase 8.5] InventoryProtectionListener 등록 (범용 인벤토리 보호권)
        kr.bapuri.tycoon.item.InventoryProtectionListener inventoryProtectionListener =
            new kr.bapuri.tycoon.item.InventoryProtectionListener(
                this, coreItemAuthenticator, services.getPlayerDataManager());
        getServer().getPluginManager().registerEvents(inventoryProtectionListener, this);
        
        getLogger().info("✓ CoreItem 시스템 초기화 완료 (인증, 생성, 명령어, 인벤토리 보호)");
    }
    
    /**
     * [Phase 3.C] Antiexploit 시스템 초기화
     */
    private void initAntiexploitSystem() {
        // antiexploit.yml 로드
        saveResource("antiexploit.yml", false);
        File configFile = new File(getDataFolder(), "antiexploit.yml");
        antiexploitConfig = YamlConfiguration.loadConfiguration(configFile);
        
        // CustomItemVanillaBlocker 초기화
        CoreItemAuthenticator coreItemAuth = services.getCoreItemAuthenticator();
        customItemVanillaBlocker = new CustomItemVanillaBlocker(this, coreItemAuth);
        customItemVanillaBlocker.loadConfig(antiexploitConfig);
        getServer().getPluginManager().registerEvents(customItemVanillaBlocker, this);
        
        // VillagerTradeBlocker 초기화
        WorldManager worldManager = services.getWorldManager();
        PlayerDataManager playerDataManager = services.getPlayerDataManager();
        EconomyService economyService = services.getEconomyService();
        villagerTradeBlocker = new VillagerTradeBlocker(worldManager, playerDataManager, economyService, getLogger());
        villagerTradeBlocker.loadConfig(antiexploitConfig);
        getServer().getPluginManager().registerEvents(villagerTradeBlocker, this);
        
        // ItemTransformListener 초기화 (가공 시 속성 전달)
        kr.bapuri.tycoon.antiexploit.ItemTransformListener itemTransformListener = 
            new kr.bapuri.tycoon.antiexploit.ItemTransformListener(this);
        getServer().getPluginManager().registerEvents(itemTransformListener, this);
        getLogger().info("  ✓ ItemTransformListener 등록 (굽기 시 희귀도/등급 전달)");
        
        // VillagerTradeCommand 등록
        VillagerTradeCommand vtCommand = new VillagerTradeCommand(villagerTradeBlocker, playerDataManager);
        registerCommand("villagertrade", vtCommand, vtCommand);
        
        // [SCAFFOLD] Phase 3.C.3 기반 - 나중에 구현 예정
        getLogger().info("  [SCAFFOLD] BlameTracker 기반 등록 (미구현)");
        getLogger().info("  [SCAFFOLD] AfkDampenSystem 기반 등록 (미구현)");
        getLogger().info("  [SCAFFOLD] AntiFarmSystem 기반 등록 (미구현)");
        
        // [SCAFFOLD] Phase 3.C.4 X-ray 휴리스틱 - 나중에 구현 예정
        XrayHeuristicAnalyzer xrayAnalyzer = new XrayHeuristicAnalyzer();
        XrayCommand xrayCommand = new XrayCommand(xrayAnalyzer);
        registerCommand("xray", xrayCommand, xrayCommand);
        getLogger().info("  [SCAFFOLD] XrayHeuristicAnalyzer 기반 등록 (미구현)");
        
        getLogger().info("✓ Antiexploit 시스템 초기화 완료");
    }
    
    /**
     * [Phase 3.B] 상점 시스템 초기화
     */
    private void initShopSystem(EconomyService economyService, CitizensIntegration citizens) {
        // ShopService 생성 (shops.yml 로드)
        ShopService shopService = new ShopService(this, economyService);
        
        // 동적 가격 추적기
        DynamicPriceTracker priceTracker = new DynamicPriceTracker(this);
        shopService.setPriceTracker(priceTracker);
        
        // 직업 상점 생성
        MinerShop minerShop = new MinerShop(this, economyService);
        FarmerShop farmerShop = new FarmerShop(this, economyService);
        FisherShop fisherShop = new FisherShop(this, economyService);
        
        // [핵심] shops.yml에서 아이템 로드 (단일 진실 공급원)
        minerShop.loadFromYaml(shopService.getShopConfig("miner"));
        farmerShop.loadFromYaml(shopService.getShopConfig("farmer"));
        fisherShop.loadFromYaml(shopService.getShopConfig("fisher"));
        
        // 동적 가격 추적기 연결 (loadFromYaml 후에 해야 아이템이 등록됨)
        minerShop.setPriceTracker(priceTracker);
        farmerShop.setPriceTracker(priceTracker);
        fisherShop.setPriceTracker(priceTracker);
        
        // 이전 가격 데이터 로드
        priceTracker.load();
        
        // 상점 등록
        shopService.registerShop(minerShop);
        shopService.registerShop(farmerShop);
        shopService.registerShop(fisherShop);
        
        // ========== 마켓 상점 (동적 가격 + 비대칭 레벨 보너스) ==========
        kr.bapuri.tycoon.shop.market.FoodShop foodShop = 
            new kr.bapuri.tycoon.shop.market.FoodShop(this, economyService);
        kr.bapuri.tycoon.shop.market.LootShop lootShop = 
            new kr.bapuri.tycoon.shop.market.LootShop(this, economyService);
        
        // PlayerDataManager 주입 (레벨 조회용)
        foodShop.setPlayerDataManager(services.getPlayerDataManager());
        lootShop.setPlayerDataManager(services.getPlayerDataManager());
        
        // shops.yml에서 아이템 로드
        foodShop.loadFromYaml(shopService.getMarketShopConfig("food"));
        lootShop.loadFromYaml(shopService.getMarketShopConfig("loot"));
        
        // 동적 가격 추적기 연결
        foodShop.setPriceTracker(priceTracker);
        lootShop.setPriceTracker(priceTracker);
        
        // 상점 등록
        shopService.registerShop(foodShop);
        shopService.registerShop(lootShop);
        
        // ========== 일반 상점 (고정 가격) ==========
        kr.bapuri.tycoon.shop.general.SpawnEggShop spawnEggShop = 
            new kr.bapuri.tycoon.shop.general.SpawnEggShop(this, economyService);
        
        // shops.yml에서 아이템 로드
        spawnEggShop.loadFromYaml(shopService.getGeneralShopConfig("spawnegg"));
        
        // 상점 등록
        shopService.registerShop(spawnEggShop);
        
        // GUI 관리자
        ShopGuiManager guiManager = new ShopGuiManager(this, shopService);
        shopService.setGuiManager(guiManager);
        
        // 가격 갱신 태스크 시작
        priceTracker.startUpdateTask();
        
        // ServiceRegistry에 등록
        services.setShopService(shopService);
        
        // NPC 상점 핸들러 등록 (Citizens 연동 + config.yml shopNpc 섹션)
        ShopNpcRegistry.registerAllShopNPCs(this, citizens, shopService);
        
        getLogger().info("✓ 상점 시스템 초기화 완료 (" + shopService.getAllShops().size() + "개 상점)");
    }
    
    /**
     * [2026-01-29] 특수 아이템 상점 초기화
     */
    private void initSpecialItemShop(EconomyService economyService,
                                     kr.bapuri.tycoon.enhance.lamp.LampItemFactory lampItemFactory,
                                     kr.bapuri.tycoon.enhance.lamp.LampSlotExpandTicket slotExpandTicket) {
        CoreItemService coreItemService = services.getCoreItemService();
        if (coreItemService == null) {
            getLogger().warning("[SpecialItemShop] CoreItemService가 null - 특수 상점 비활성화");
            return;
        }
        
        // 특수 아이템 상점 생성
        kr.bapuri.tycoon.shop.special.SpecialItemShop specialShop = 
            new kr.bapuri.tycoon.shop.special.SpecialItemShop(
                this, economyService, coreItemService,
                lampItemFactory, slotExpandTicket);
        
        // config.yml에서 설정 로드
        org.bukkit.configuration.ConfigurationSection section = 
            getConfig().getConfigurationSection("specialShops.special");
        specialShop.loadFromConfig(section);
        
        // ServiceRegistry에 등록
        services.setSpecialItemShop(specialShop);
        
        // NPC 핸들러 등록 (Citizens 연동)
        services.getCitizensIntegration().ifPresent(citizens -> {
            citizens.registerHandler("특수", (player, npc) -> {
                specialShop.openShop(player);
            });
            citizens.registerHandler("special", (player, npc) -> {
                specialShop.openShop(player);
            });
            citizens.registerHandler("마법", (player, npc) -> {
                specialShop.openShop(player);
            });
            getLogger().info("  ✓ 특수 아이템 상점 NPC 등록 완료");
        });
        
        getLogger().info("  ✓ 특수 아이템 상점 초기화 완료 (" + 13 + "개 아이템)");
    }
    
    /**
     * 리스너 초기화 (ListenerRegistry 사용)
     */
    private void initListeners() {
        listeners = new ListenerRegistry(this);
        
        // Core Listeners
        listeners.register(new PlayerSessionListener(this, services.getPlayerDataManager()));
        
        // Phase 2에서 추가될 리스너들:
        // listeners.registerIf(jobService != null, jobService.getListener());
        // listeners.register(new ShopListener(services));
        
        // 모든 리스너 등록
        listeners.registerAll();
    }
    
    /**
     * 명령어 초기화
     */
    private void initCommands() {
        // [Phase 3] /eco 명령어 등록
        kr.bapuri.tycoon.economy.EcoCommand ecoCommand = 
            new kr.bapuri.tycoon.economy.EcoCommand(services.getEconomyService());
        registerCommand("eco", ecoCommand, ecoCommand);
        
        // /money 명령어 등록 (플레이어용 잔액 확인)
        kr.bapuri.tycoon.economy.MoneyCommand moneyCommand = 
            new kr.bapuri.tycoon.economy.MoneyCommand(services.getEconomyService());
        registerCommand("money", moneyCommand, moneyCommand);
        
        // [Phase 3.B] /shopadmin 명령어 등록
        ShopService shopService = services.getShopService();
        if (shopService != null) {
            ShopAdminCommand shopAdminCommand = new ShopAdminCommand(shopService);
            registerCommand("shopadmin", shopAdminCommand, shopAdminCommand);
        }
        
        getLogger().info("명령어 등록 완료");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tycoon")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("version")) {
                sender.sendMessage("§6========================================");
                sender.sendMessage("§eTycoon Lite §7v" + getDescription().getVersion());
                sender.sendMessage("§7Phase 2: Data Layer & Backup");
                sender.sendMessage("§6========================================");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("tycoon.admin")) {
                reloadConfig();
                services.getAdminService().reload();
                
                // [Phase 3.C] antiexploit.yml 리로드
                File antiexploitFile = new File(getDataFolder(), "antiexploit.yml");
                antiexploitConfig = YamlConfiguration.loadConfiguration(antiexploitFile);
                if (customItemVanillaBlocker != null) {
                    customItemVanillaBlocker.loadConfig(antiexploitConfig);
                }
                if (villagerTradeBlocker != null) {
                    villagerTradeBlocker.loadConfig(antiexploitConfig);
                }
                
                // [Phase 5.A] codex.yml 리로드
                if (services.getCodexService() != null) {
                    services.getCodexService().getRegistry().reload();
                    getLogger().info("[Reload] codex.yml 리로드 완료");
                }
                
                // TODO: titles.yml 리로드 추가
                // services.getTitleService().getRegistry().reload();
                
                // [Phase 4] enchants.yml 리로드
                boolean enchantsReloaded = false;
                boolean lampsReloaded = false;
                
                if (services.getEnchantRegistry() != null) {
                    try {
                        services.getEnchantRegistry().reload();
                        getLogger().info("[Reload] enchants.yml 리로드 완료");
                        enchantsReloaded = true;
                    } catch (Exception e) {
                        getLogger().severe("[Reload] enchants.yml 리로드 실패: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // [Phase 4] lamps.yml 리로드
                if (services.getLampRegistry() != null) {
                    try {
                        services.getLampRegistry().reload();
                        getLogger().info("[Reload] lamps.yml 리로드 완료");
                        lampsReloaded = true;
                    } catch (Exception e) {
                        getLogger().severe("[Reload] lamps.yml 리로드 실패: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // [Phase 3.B] shops.yml 리로드
                boolean shopsReloaded = false;
                ShopService reloadShopService = services.getShopService();
                if (reloadShopService != null) {
                    try {
                        int[] result = reloadShopService.reload();
                        getLogger().info("[Reload] shops.yml 리로드 완료 (성공: " + result[0] + ", 실패: " + result[1] + ")");
                        shopsReloaded = result[1] == 0;
                    } catch (Exception e) {
                        getLogger().severe("[Reload] shops.yml 리로드 실패: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // [2026-01-31] specialShops (config.yml) 리로드
                boolean specialShopsReloaded = false;
                if (services.getSpecialItemShop() != null) {
                    try {
                        org.bukkit.configuration.ConfigurationSection specialSection = 
                            getConfig().getConfigurationSection("specialShops.special");
                        services.getSpecialItemShop().loadFromConfig(specialSection);
                        getLogger().info("[Reload] config.yml (specialShops) 리로드 완료");
                        specialShopsReloaded = true;
                    } catch (Exception e) {
                        getLogger().severe("[Reload] config.yml (specialShops) 리로드 실패: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // 실행자에게 결과 표시
                sender.sendMessage("§a설정이 리로드되었습니다.");
                sender.sendMessage("§7- enchants.yml: " + (enchantsReloaded ? "§a✓" : "§c✗ (콘솔 확인)"));
                sender.sendMessage("§7- lamps.yml: " + (lampsReloaded ? "§a✓" : "§c✗ (콘솔 확인)"));
                sender.sendMessage("§7- shops.yml: " + (shopsReloaded ? "§a✓" : "§c✗ (콘솔 확인)"));
                sender.sendMessage("§7- specialShops: " + (specialShopsReloaded ? "§a✓" : "§c✗ (콘솔 확인)"));
                return true;
            }
            
            // [Phase 2] 백업 명령어
            if (args[0].equalsIgnoreCase("backup")) {
                return handleBackupCommand(sender, args);
            }
            
            // 효과 메시지 토글 (플레이어 전용)
            if (args[0].equalsIgnoreCase("effectmsg")) {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
                    return true;
                }
                
                boolean nowEnabled = toggleEffectMsg(player.getUniqueId());
                if (nowEnabled) {
                    player.sendMessage("§a효과 메시지가 §e활성화§a되었습니다.");
                } else {
                    player.sendMessage("§a효과 메시지가 §7비활성화§a되었습니다.");
                }
                return true;
            }
            
            sender.sendMessage("§c사용법: /tycoon [version|reload|backup|effectmsg]");
            return true;
        }
        return false;
    }
    
    /**
     * [Phase 2] 백업 명령어 처리
     */
    private boolean handleBackupCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tycoon.admin.backup")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§e=== 백업 명령어 ===");
            sender.sendMessage("§7/tycoon backup list <player> §8- 백업 목록");
            sender.sendMessage("§7/tycoon backup restore <player> <timestamp> §8- 복원");
            sender.sendMessage("§7/tycoon backup create <player> §8- 수동 백업");
            sender.sendMessage("§7/tycoon backup info [player] §8- 백업 통계");
            return true;
        }
        
        String subCmd = args[1].toLowerCase();
        kr.bapuri.tycoon.player.BackupManager backupManager = 
            services.getPlayerDataManager().getBackupManager();
        
        switch (subCmd) {
            case "list": {
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /tycoon backup list <player>");
                    return true;
                }
                
                java.util.UUID uuid = getPlayerUUID(args[2]);
                if (uuid == null) {
                    sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
                    return true;
                }
                
                java.util.List<String> snapshots = backupManager.listSnapshots(uuid);
                if (snapshots.isEmpty()) {
                    sender.sendMessage("§7" + args[2] + "의 백업이 없습니다.");
                } else {
                    sender.sendMessage("§e=== " + args[2] + "의 백업 목록 (" + snapshots.size() + "개) ===");
                    for (int i = 0; i < Math.min(10, snapshots.size()); i++) {
                        sender.sendMessage("§7 " + (i+1) + ". §f" + snapshots.get(i));
                    }
                    if (snapshots.size() > 10) {
                        sender.sendMessage("§7 ... 외 " + (snapshots.size() - 10) + "개");
                    }
                }
                return true;
            }
            
            case "restore": {
                if (args.length < 4) {
                    sender.sendMessage("§c사용법: /tycoon backup restore <player> <timestamp>");
                    sender.sendMessage("§7예: /tycoon backup restore Steve 2026-01-26_15-30-00");
                    return true;
                }
                
                java.util.UUID uuid = getPlayerUUID(args[2]);
                if (uuid == null) {
                    sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
                    return true;
                }
                
                String timestamp = args[3];
                boolean success = backupManager.restoreFromSnapshot(uuid, timestamp);
                
                if (success) {
                    sender.sendMessage("§a복원 완료: " + args[2] + " <- " + timestamp);
                    
                    // 온라인이면 데이터 리로드
                    org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        // 캐시에서 제거 후 다시 로드
                        services.getPlayerDataManager().unload(uuid);
                        services.getPlayerDataManager().get(player);
                        sender.sendMessage("§7온라인 플레이어 데이터가 리로드되었습니다.");
                    }
                } else {
                    sender.sendMessage("§c복원 실패. 타임스탬프를 확인하세요.");
                }
                return true;
            }
            
            case "create": {
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /tycoon backup create <player>");
                    return true;
                }
                
                java.util.UUID uuid = getPlayerUUID(args[2]);
                if (uuid == null) {
                    sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
                    return true;
                }
                
                boolean success = backupManager.createSnapshot(uuid);
                if (success) {
                    sender.sendMessage("§a스냅샷 생성 완료: " + args[2]);
                } else {
                    sender.sendMessage("§c스냅샷 생성 실패. 플레이어 데이터가 없을 수 있습니다.");
                }
                return true;
            }
            
            case "info": {
                if (args.length >= 3) {
                    // 특정 플레이어 정보
                    java.util.UUID uuid = getPlayerUUID(args[2]);
                    if (uuid == null) {
                        sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
                        return true;
                    }
                    
                    int count = backupManager.getSnapshotCount(uuid);
                    String latest = backupManager.getLatestSnapshot(uuid);
                    
                    sender.sendMessage("§e=== " + args[2] + " 백업 정보 ===");
                    sender.sendMessage("§7스냅샷 수: §f" + count);
                    sender.sendMessage("§7최신 백업: §f" + (latest != null ? latest : "없음"));
                } else {
                    // 전체 통계
                    int totalCount = backupManager.getTotalSnapshotCount();
                    long totalSize = backupManager.getTotalBackupSize();
                    String sizeStr = formatFileSize(totalSize);
                    
                    sender.sendMessage("§e=== 백업 시스템 통계 ===");
                    sender.sendMessage("§7총 스냅샷 수: §f" + totalCount);
                    sender.sendMessage("§7총 사용량: §f" + sizeStr);
                    sender.sendMessage("§7최대 스냅샷/플레이어: §f" + backupManager.getMaxSnapshots());
                    sender.sendMessage("§7스냅샷 간격: §f" + backupManager.getSnapshotIntervalMinutes() + "분");
                }
                return true;
            }
            
            default:
                sender.sendMessage("§c알 수 없는 명령어: " + subCmd);
                return true;
        }
    }
    
    /**
     * 플레이어 이름으로 UUID 조회
     */
    private java.util.UUID getPlayerUUID(String name) {
        // 온라인 플레이어 먼저 확인
        org.bukkit.entity.Player online = getServer().getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        
        // 오프라인 플레이어 확인
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer offline = getServer().getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        
        return null;
    }
    
    /**
     * 파일 크기 포맷팅
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    /**
     * [Phase 2] 탭 자동완성
     */
    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("tycoon")) {
            return null;
        }
        
        java.util.List<String> completions = new java.util.ArrayList<>();
        
        if (args.length == 1) {
            // /tycoon <...>
            java.util.List<String> options = java.util.Arrays.asList("version", "reload", "backup", "effectmsg");
            String partial = args[0].toLowerCase();
            for (String opt : options) {
                if (opt.startsWith(partial)) {
                    completions.add(opt);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("backup")) {
            // /tycoon backup <...>
            if (sender.hasPermission("tycoon.admin.backup")) {
                java.util.List<String> subOptions = java.util.Arrays.asList("list", "restore", "create", "info");
                String partial = args[1].toLowerCase();
                for (String opt : subOptions) {
                    if (opt.startsWith(partial)) {
                        completions.add(opt);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("backup")) {
            // /tycoon backup <sub> <player>
            String sub = args[1].toLowerCase();
            if (sub.equals("list") || sub.equals("restore") || sub.equals("create") || sub.equals("info")) {
                String partial = args[2].toLowerCase();
                for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("backup") && args[1].equalsIgnoreCase("restore")) {
            // /tycoon backup restore <player> <timestamp>
            java.util.UUID uuid = getPlayerUUID(args[2]);
            if (uuid != null && sender.hasPermission("tycoon.admin.backup")) {
                java.util.List<String> snapshots = services.getPlayerDataManager()
                    .getBackupManager().listSnapshots(uuid);
                String partial = args[3].toLowerCase();
                for (String ts : snapshots) {
                    if (ts.toLowerCase().startsWith(partial)) {
                        completions.add(ts);
                    }
                }
            }
        }
        
        return completions;
    }

    @Override
    public void onDisable() {
        getLogger().info("Tycoon Lite 비활성화 중...");
        
        // [세금 시스템] 세금 스케줄러 정지 및 데이터 저장
        if (taxScheduler != null) {
            taxScheduler.stop();
        }
        if (villagerRegistry != null) {
            villagerRegistry.shutdown();
        }
        
        // [자동화 팜 제한] 트래커 정지
        if (autoFarmTracker != null) {
            autoFarmTracker.stop();
        }
        
        // [Phase 3.5] 월드 리셋 스케줄러 정지
        if (worldResetScheduler != null) {
            worldResetScheduler.stop();
        }
        
        // [Phase 8] AngelChest 연동 종료
        if (angelChestIntegration != null) {
            angelChestIntegration.shutdown();
        }
        
        if (services != null) {
            // [Phase 3.B] 상점 시스템 종료
            ShopService shopService = services.getShopService();
            if (shopService != null) {
                shopService.shutdown();
            }
            
            // [BC Shop] BC 상점 시스템 종료
            BCShopService bcShopService = services.getBCShopService();
            if (bcShopService != null) {
                bcShopService.shutdown();
            }
            
            // [Phase 3] Vault 연동 해제
            services.getVaultIntegration().ifPresent(VaultIntegration::unregister);
            
            // [Phase 8] Recovery 시스템 저장
            var recoveryManager = services.getRecoveryStorageManager();
            if (recoveryManager != null) {
                recoveryManager.saveAll();
            }
            
            // [Phase 2] 스케줄러 중지
            services.getPlayerDataManager().stopAutoSave();
            services.getPlayerDataManager().stopSnapshotScheduler();
            services.getPlayerDataManager().saveAll();
        }
        
        getLogger().info("Tycoon Lite 비활성화 완료");
        instance = null;
    }

    /**
     * 필수 플러그인 존재 여부 확인
     */
    private boolean checkDependencies() {
        String[] required = {"Vault", "LuckPerms", "Citizens", "WorldGuard"};
        boolean allPresent = true;
        
        for (String plugin : required) {
            if (getServer().getPluginManager().getPlugin(plugin) == null) {
                getLogger().severe("필수 플러그인 누락: " + plugin);
                allPresent = false;
            } else {
                getLogger().info("✓ " + plugin + " 감지됨");
            }
        }
        
        // softdepend 확인 (정보 출력용)
        String[] optional = {"WorldEdit", "CoreProtect", "PlaceholderAPI", 
                            "Multiverse-Core", "OpenInv", "spark"};
        for (String plugin : optional) {
            if (getServer().getPluginManager().getPlugin(plugin) != null) {
                getLogger().info("○ " + plugin + " 감지됨 (선택)");
            }
        }
        
        return allPresent;
    }

    // ===== Getters =====
    
    /**
     * ServiceRegistry 반환
     */
    public ServiceRegistry getServices() {
        return services;
    }
    
    /**
     * 플러그인 인스턴스 반환
     */
    public static TycoonPlugin getInstance() {
        return instance;
    }
    
    // ===== 효과 메시지 토글 (세션 기반) =====
    
    /**
     * 플레이어의 효과 메시지 활성화 여부 확인
     * @param uuid 플레이어 UUID
     * @return true면 메시지 표시, false면 숨김
     */
    public boolean isEffectMsgEnabled(java.util.UUID uuid) {
        return !effectMsgDisabledPlayers.contains(uuid);
    }
    
    /**
     * 효과 메시지 토글
     * @param uuid 플레이어 UUID
     * @return 토글 후 활성화 상태 (true = 활성화됨)
     */
    public boolean toggleEffectMsg(java.util.UUID uuid) {
        if (effectMsgDisabledPlayers.contains(uuid)) {
            effectMsgDisabledPlayers.remove(uuid);
            return true; // 이제 활성화됨
        } else {
            effectMsgDisabledPlayers.add(uuid);
            return false; // 이제 비활성화됨
        }
    }
    
    /**
     * 플레이어 퇴장 시 효과 메시지 설정 정리
     * (세션 기반이므로 재접속 시 기본값으로 리셋)
     */
    public void clearEffectMsgSetting(java.util.UUID uuid) {
        effectMsgDisabledPlayers.remove(uuid);
    }
    
    // ===== 하위 호환 Getters (Phase 2에서 제거 가능) =====
    
    public ConfigManager getConfigManager() {
        return services.getConfigManager();
    }
    
    public AdminService getAdminService() {
        return services.getAdminService();
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return services.getPlayerDataManager();
    }
    
    // ===== 명령어 등록 헬퍼 (NPE 방지) =====
    
    /**
     * 명령어 등록 헬퍼 - null 체크 포함
     * plugin.yml에 명령어가 없으면 NPE 대신 경고 로그 출력
     */
    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        } else {
            getLogger().severe("[명령어 등록 실패] '" + name + "'이 plugin.yml에 정의되지 않음!");
        }
    }
    
    /**
     * 명령어 + 탭 완성 등록 헬퍼 - null 체크 포함
     */
    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor, 
                                  org.bukkit.command.TabCompleter tabCompleter) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(tabCompleter);
        } else {
            getLogger().severe("[명령어 등록 실패] '" + name + "'이 plugin.yml에 정의되지 않음!");
        }
    }
    
    // ===== Getter for Achievement System =====
    
    /**
     * AchievementListener 반환 (다른 서비스에서 업적 트리거 호출용)
     */
    public AchievementListener getAchievementListener() {
        return achievementListener;
    }
}
