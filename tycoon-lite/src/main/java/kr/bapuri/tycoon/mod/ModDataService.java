package kr.bapuri.tycoon.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import kr.bapuri.tycoon.bootstrap.ServiceRegistry;
import kr.bapuri.tycoon.codex.CodexRegistry;
import kr.bapuri.tycoon.codex.CodexRule;
import kr.bapuri.tycoon.codex.CodexService;
import kr.bapuri.tycoon.integration.LandsIntegration;
import kr.bapuri.tycoon.integration.LandsListener;
import kr.bapuri.tycoon.job.JobService;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * ModDataService - 클라이언트 모드 연동 서비스
 * 
 * <h2>역할</h2>
 * 플러그인 메시지 채널을 통해 클라이언트 모드와 JSON 형식으로 데이터를 송수신합니다.
 * 
 * <h2>채널</h2>
 * <ul>
 *   <li>tycoon:ui_data - 서버 → 클라이언트 (데이터 전송)</li>
 *   <li>tycoon:ui_request - 클라이언트 → 서버 (요청 수신, ModRequestHandler에서 처리)</li>
 * </ul>
 * 
 * <h2>패킷 형식</h2>
 * <pre>
 * {
 *   "type": "PACKET_TYPE",
 *   "data": { ... }
 * }
 * </pre>
 * 
 * <h2>지원 패킷 타입</h2>
 * <ul>
 *   <li>PLAYER_PROFILE - 전체 프로필 데이터</li>
 *   <li>VITAL_UPDATE - 체력/배고픔 업데이트</li>
 *   <li>ECONOMY_UPDATE - BD/BottCoin 업데이트</li>
 *   <li>JOB_DATA - 직업 정보</li>
 *   <li>PLOT_UPDATE - 땅 정보 (Lands 연동)</li>
 *   <li>CODEX_DATA - 도감 요약</li>
 *   <li>CODEX_CATEGORY_DATA - 도감 카테고리 상세</li>
 * </ul>
 * 
 * @see ModRequestHandler 클라이언트 요청 처리
 */
public class ModDataService {
    
    /** 서버 → 클라이언트 채널 (ModPacketTypes에서 참조) */
    public static final String CHANNEL_UI_DATA = ModPacketTypes.CHANNEL_UI_DATA;
    
    /** 클라이언트 → 서버 채널 (ModPacketTypes에서 참조) */
    public static final String CHANNEL_UI_REQUEST = ModPacketTypes.CHANNEL_UI_REQUEST;
    
    /** 스키마 버전 (클라이언트 호환용) */
    private static final int SCHEMA_VERSION = ModPacketTypes.SCHEMA_VERSION;
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final PlayerDataManager dataManager;
    private final JobService jobService;
    private final Gson gson;
    
    // 서비스 참조 (순환 의존성 방지를 위한 setter 주입)
    private ServiceRegistry services;
    private CodexService codexService;
    private CodexRegistry codexRegistry;
    
    // 활성화 여부 (config에서 로드)
    private boolean enabled = false;
    
    public ModDataService(JavaPlugin plugin, PlayerDataManager dataManager, JobService jobService) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataManager = dataManager;
        this.jobService = jobService;
        this.gson = new GsonBuilder().serializeNulls().create();
        
        logger.info("[ModDataService] 인스턴스 생성됨");
    }
    
    // ========================================================================
    // Setter Injection (순환 의존성 방지)
    // ========================================================================
    
    public void setServiceRegistry(ServiceRegistry services) {
        this.services = services;
    }
    
    public void setCodexService(CodexService codexService) {
        this.codexService = codexService;
    }
    
    public void setCodexRegistry(CodexRegistry codexRegistry) {
        this.codexRegistry = codexRegistry;
    }
    
    // ========================================================================
    // 초기화 / 종료
    // ========================================================================
    
    /**
     * 초기화 (채널 등록)
     */
    public void initialize() {
        enabled = plugin.getConfig().getBoolean("mod_integration.enabled", false);
        
        if (!enabled) {
            logger.info("[ModDataService] 비활성화 상태 (mod_integration.enabled=false)");
            return;
        }
        
        // 송신 채널 등록
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_UI_DATA);
        
        logger.info("[ModDataService] 초기화 완료 (채널: " + CHANNEL_UI_DATA + ")");
    }
    
    /**
     * 종료 (채널 해제)
     */
    public void shutdown() {
        if (!enabled) return;
        
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL_UI_DATA);
        logger.info("[ModDataService] 종료됨");
    }
    
    /**
     * LandsListener와 연동하여 땅 변경 콜백 등록
     */
    public void registerLandsCallback(LandsListener landsListener) {
        if (!enabled) return;
        
        landsListener.setLandChangeCallback((player, plotInfo) -> {
            sendPlotUpdate(player, plotInfo);
        });
        
        logger.info("[ModDataService] Lands 변경 콜백 등록됨");
    }
    
    // ========================================================================
    // PLAYER_PROFILE - 전체 프로필
    // ========================================================================
    
    /**
     * 플레이어 프로필 전송 (접속 시, 요청 시)
     */
    public void sendPlayerProfile(Player player) {
        if (!enabled || player == null) return;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return;
        
        JsonObject profileData = new JsonObject();
        profileData.addProperty("schema", SCHEMA_VERSION);
        profileData.addProperty("uuid", player.getUniqueId().toString());
        profileData.addProperty("name", player.getName());
        profileData.addProperty("bd", data.getMoney());
        profileData.addProperty("bottcoin", data.getBottCoin());
        profileData.addProperty("title", data.getEquippedTitle());
        // 플레이타임: Bukkit 통계 API 사용 (PLAY_ONE_MINUTE는 틱 단위)
        long playtimeTicks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        profileData.addProperty("playtimeSeconds", playtimeTicks / 20L);
        
        // 직업 정보 - LITE에서는 getJobGrade(JobType)을 사용
        JobType tier1Job = data.getTier1Job();
        JobType tier2Job = data.getTier2Job();
        profileData.add("primaryJob", buildJobObject(tier1Job, 
            data.getTier1JobLevel(), data.getTier1JobExp(), 
            tier1Job != null ? data.getJobGrade(tier1Job) : 1));
        profileData.add("secondaryJob", buildJobObject(tier2Job,
            data.getTier2JobLevel(), data.getTier2JobExp(),
            tier2Job != null ? data.getJobGrade(tier2Job) : 1));
        
        // 월드/플롯 정보 (Bukkit 월드 이름 → 논리적 월드 ID 변환)
        String worldId = convertToWorldId(player.getWorld().getName());
        profileData.addProperty("currentWorld", worldId);
        
        // Lands 연동 (있으면)
        if (services != null) {
            services.getLandsIntegration().ifPresent(lands -> {
                lands.getPlotAt(player.getLocation()).ifPresent(plotInfo -> {
                    profileData.add("plotInfo", buildPlotObject(plotInfo));
                });
            });
            
            // 업적 연동
            kr.bapuri.tycoon.achievement.AchievementService achievementService = services.getAchievementService();
            if (achievementService != null) {
                JsonObject achievements = new JsonObject();
                achievements.addProperty("unlockedCount", achievementService.getUnlockedCount(player));
                achievements.addProperty("totalCount", achievementService.getTotalCount());
                profileData.add("achievements", achievements);
            }
        }
        
        sendPacket(player, ModPacketTypes.PLAYER_PROFILE, profileData);
        logger.fine("[ModDataService] PLAYER_PROFILE 전송: " + player.getName());
    }
    
    // ========================================================================
    // VITAL_UPDATE - 체력/배고픔
    // ========================================================================
    
    /**
     * Vital 업데이트 전송
     */
    public void sendVitalUpdate(Player player) {
        if (!enabled || player == null) return;
        
        JsonObject vitalData = new JsonObject();
        
        // 체력
        vitalData.addProperty("health", player.getHealth());
        vitalData.addProperty("maxHealth", player.getMaxHealth());
        
        // 배고픔 [Fix] 필드명을 클라이언트와 일치시킴
        vitalData.addProperty("foodLevel", player.getFoodLevel());
        vitalData.addProperty("maxFoodLevel", 20);
        vitalData.addProperty("saturation", player.getSaturation());
        
        // 갑옷
        int armor = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta) {
                armor += getArmorValue(item.getType());
            }
        }
        vitalData.addProperty("armor", armor);
        vitalData.addProperty("maxArmor", 20);
        
        // 산소
        vitalData.addProperty("air", player.getRemainingAir());
        vitalData.addProperty("maxAir", player.getMaximumAir());
        vitalData.addProperty("underwater", player.isInWater() || player.getRemainingAir() < player.getMaximumAir());
        
        // 경험치
        vitalData.addProperty("level", player.getLevel());
        vitalData.addProperty("expProgress", player.getExp());
        vitalData.addProperty("totalExp", player.getTotalExperience());
        
        sendPacket(player, ModPacketTypes.VITAL_UPDATE, vitalData);
    }
    
    /**
     * 갑옷 방어력 계산 헬퍼
     */
    private int getArmorValue(org.bukkit.Material material) {
        return switch (material) {
            case LEATHER_HELMET, LEATHER_BOOTS -> 1;
            case LEATHER_LEGGINGS, CHAINMAIL_HELMET, IRON_HELMET, GOLDEN_HELMET -> 2;
            case LEATHER_CHESTPLATE, CHAINMAIL_BOOTS, IRON_BOOTS, GOLDEN_BOOTS, DIAMOND_HELMET, NETHERITE_HELMET -> 3;
            case CHAINMAIL_LEGGINGS -> 4;
            case CHAINMAIL_CHESTPLATE, IRON_LEGGINGS, GOLDEN_LEGGINGS -> 5;
            case IRON_CHESTPLATE, GOLDEN_CHESTPLATE, DIAMOND_BOOTS, NETHERITE_BOOTS -> 6;
            case DIAMOND_LEGGINGS, NETHERITE_LEGGINGS -> 6;
            case DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE -> 8;
            default -> 0;
        };
    }
    
    // ========================================================================
    // ECONOMY_UPDATE - BD/BottCoin
    // ========================================================================
    
    /**
     * 경제 업데이트 전송 (BD/BottCoin 변동 시)
     */
    public void sendEconomyUpdate(Player player) {
        if (!enabled || player == null) return;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return;
        
        JsonObject economyData = new JsonObject();
        economyData.addProperty("bd", data.getMoney());
        economyData.addProperty("bottcoin", data.getBottCoin());
        
        sendPacket(player, ModPacketTypes.ECONOMY_UPDATE, economyData);
        logger.fine("[ModDataService] ECONOMY_UPDATE 전송: " + player.getName() 
            + " (BD=" + data.getMoney() + ", BC=" + data.getBottCoin() + ")");
    }
    
    // ========================================================================
    // JOB_DATA - 직업 정보
    // ========================================================================
    
    /**
     * 직업 데이터 전송 (선택/레벨업 시)
     */
    public void sendJobData(Player player) {
        if (!enabled || player == null) return;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return;
        
        // LITE에서는 getJobGrade(JobType)을 사용
        JobType tier1Job = data.getTier1Job();
        JobType tier2Job = data.getTier2Job();
        
        JsonObject jobData = new JsonObject();
        jobData.add("primaryJob", buildJobObject(tier1Job,
            data.getTier1JobLevel(), data.getTier1JobExp(),
            tier1Job != null ? data.getJobGrade(tier1Job) : 1));
        jobData.add("secondaryJob", buildJobObject(tier2Job,
            data.getTier2JobLevel(), data.getTier2JobExp(),
            tier2Job != null ? data.getJobGrade(tier2Job) : 1));
        
        sendPacket(player, ModPacketTypes.JOB_DATA, jobData);
        logger.fine("[ModDataService] JOB_DATA 전송: " + player.getName());
    }
    
    /**
     * 직업 상세 정보 전송 (요청 시) - 클라이언트 모드 JOB_DETAIL 스키마에 맞춤
     * 
     * <p>승급 조건 등 상세 정보를 포함합니다.</p>
     * 
     * <p>[Phase 3] 확장 정보:</p>
     * <ul>
     *   <li>승급 조건에 current/required 값 추가</li>
     *   <li>현재 등급 보너스 목록 (currentBonuses)</li>
     *   <li>다음 등급 보너스 목록 (nextGradeBonuses)</li>
     * </ul>
     */
    public void sendJobDetail(Player player) {
        if (!enabled || player == null) return;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return;
        
        JobType tier1Job = data.getTier1Job();
        if (tier1Job == null) {
            // 직업 미선택
            sendPacket(player, ModPacketTypes.JOB_DETAIL, new JsonObject());
            return;
        }
        
        int level = data.getTier1JobLevel();
        long exp = data.getTier1JobExp();
        int grade = data.getJobGrade(tier1Job);
        
        // 현재 레벨 시작 경험치와 다음 레벨 필요 경험치 계산
        long levelStartExp = kr.bapuri.tycoon.job.common.JobExpCalculator.getCumulativeExpForLevel(level);
        long nextLevelTotalExp = kr.bapuri.tycoon.job.common.JobExpCalculator.getCumulativeExpForLevel(level + 1);
        long currentXp = exp - levelStartExp;
        long nextLevelXp = nextLevelTotalExp - levelStartExp;
        
        JsonObject jobDetail = new JsonObject();
        jobDetail.addProperty("type", tier1Job.name());
        jobDetail.addProperty("level", level);
        jobDetail.addProperty("grade", grade);
        jobDetail.addProperty("gradeTitle", getGradeTitle(grade));
        jobDetail.addProperty("currentXp", Math.max(0, currentXp));
        jobDetail.addProperty("nextLevelXp", Math.max(1, nextLevelXp));
        
        // 승급 조건 (jobs.yml 설정값 사용)
        com.google.gson.JsonArray requirements = new com.google.gson.JsonArray();
        
        // 승급 가능 여부 확인
        boolean canPromote = false;
        kr.bapuri.tycoon.job.JobGrade currentGradeEnum = kr.bapuri.tycoon.job.JobGrade.fromValue(grade);
        kr.bapuri.tycoon.job.JobGrade targetGrade = null;
        
        if (jobService != null) {
            try {
                var gradeService = jobService.getRegistry().getGradeService(tier1Job);
                if (gradeService != null) {
                    var checkResult = gradeService.canPromote(player);
                    canPromote = checkResult.canPromote();
                    
                    // 다음 등급 정보 가져오기
                    targetGrade = currentGradeEnum.next();
                    
                    if (targetGrade != null) {
                        // [Phase 3] 조건: 레벨 요건 (current/required 포함)
                        int requiredLevel = gradeService.getRequiredLevelForPromotion(targetGrade);
                        JsonObject levelReq = new JsonObject();
                        levelReq.addProperty("description", String.format("레벨 %d 이상", requiredLevel));
                        levelReq.addProperty("completed", level >= requiredLevel);
                        levelReq.addProperty("current", level);
                        levelReq.addProperty("required", requiredLevel);
                        requirements.add(levelReq);
                        
                        // [Phase 3] 조건: BD 요건 (current/required 포함)
                        long requiredBD = gradeService.getRequiredBDForPromotion(targetGrade);
                        if (requiredBD > 0) {
                            JsonObject bdReq = new JsonObject();
                            bdReq.addProperty("description", String.format("%,d BD 필요", requiredBD));
                            bdReq.addProperty("completed", data.getMoney() >= requiredBD);
                            bdReq.addProperty("current", data.getMoney());
                            bdReq.addProperty("required", requiredBD);
                            requirements.add(bdReq);
                        }
                    }
                }
            } catch (Exception e) {
                logger.fine("[ModDataService] 승급 조건 확인 실패: " + e.getMessage());
            }
        }
        
        jobDetail.add("promotionRequirements", requirements);
        jobDetail.addProperty("canPromote", canPromote);
        
        // [Phase 3] 현재 등급 보너스 목록
        com.google.gson.JsonArray currentBonuses = buildGradeBonusList(tier1Job, currentGradeEnum);
        jobDetail.add("currentBonuses", currentBonuses);
        
        // [Phase 3] 다음 등급 보너스 목록
        com.google.gson.JsonArray nextGradeBonuses = new com.google.gson.JsonArray();
        if (targetGrade != null) {
            nextGradeBonuses = buildGradeBonusList(tier1Job, targetGrade);
        }
        jobDetail.add("nextGradeBonuses", nextGradeBonuses);
        
        sendPacket(player, ModPacketTypes.JOB_DETAIL, jobDetail);
        logger.fine("[ModDataService] JOB_DETAIL 전송: " + player.getName());
    }
    
    /**
     * [Phase 3] 등급별 보너스 목록 생성
     */
    private com.google.gson.JsonArray buildGradeBonusList(JobType jobType, kr.bapuri.tycoon.job.JobGrade grade) {
        com.google.gson.JsonArray bonuses = new com.google.gson.JsonArray();
        
        if (services == null) return bonuses;
        
        kr.bapuri.tycoon.job.common.GradeBonusConfig gradeBonusConfig = services.getGradeBonusConfig();
        if (gradeBonusConfig == null) return bonuses;
        
        kr.bapuri.tycoon.job.common.GradeBonusConfig.GradeBonus bonus = gradeBonusConfig.getBonus(jobType, grade);
        if (bonus == null) return bonuses;
        
        // 공통 보너스
        if (bonus.xpMulti > 1.0) {
            bonuses.add(String.format("경험치 +%.0f%%", (bonus.xpMulti - 1.0) * 100));
        }
        if (bonus.yieldMulti > 1.0) {
            bonuses.add(String.format("수확량 +%.0f%%", (bonus.yieldMulti - 1.0) * 100));
        }
        
        // 직업별 특수 보너스
        switch (jobType) {
            case MINER -> {
                if (bonus.miningEfficiency > 0) {
                    bonuses.add("채굴 효율 +" + bonus.miningEfficiency);
                }
            }
            case FARMER -> {
                if (bonus.seedMulti > 1.0) {
                    bonuses.add(String.format("씨앗 수확 +%.0f%%", (bonus.seedMulti - 1.0) * 100));
                }
                if (bonus.primeChance > 0) {
                    bonuses.add(String.format("프라임 확률 +%.1f%%", bonus.primeChance * 100));
                }
                if (bonus.trophyChance > 0) {
                    bonuses.add(String.format("트로피 확률 +%.1f%%", bonus.trophyChance * 100));
                }
            }
            case FISHER -> {
                if (bonus.lureBonus > 0) {
                    bonuses.add("찌 속도 +" + bonus.lureBonus);
                }
                if (bonus.rareChanceBonus > 0) {
                    bonuses.add(String.format("희귀 확률 +%.1f%%", bonus.rareChanceBonus * 100));
                }
            }
            default -> {}
        }
        
        return bonuses;
    }
    
    
    // ========================================================================
    // PLOT_UPDATE - 땅 정보
    // ========================================================================
    
    /**
     * 플롯 정보 전송 (땅 이동 시) - 클라이언트 모드 스키마에 맞춤
     */
    public void sendPlotUpdate(Player player, LandsIntegration.PlotInfo plotInfo) {
        if (!enabled || player == null) return;
        
        JsonObject plotData = new JsonObject();
        plotData.addProperty("currentWorld", convertToWorldId(player.getWorld().getName()));
        
        if (plotInfo != null) {
            boolean hasOwner = plotInfo.getOwnerId() != null;
            plotData.addProperty("hasOwner", hasOwner);
            plotData.addProperty("ownerName", plotInfo.getOwnerName());
            plotData.addProperty("purchasable", false);  // LITE: 땅 구매 시스템 미지원
            plotData.addProperty("price", 0L);
            plotData.addProperty("restricted", false);   // LITE: 보호 구역 구분 미지원
        } else {
            // 야생 (플롯 없음)
            plotData.addProperty("hasOwner", false);
            plotData.addProperty("ownerName", (String) null);
            plotData.addProperty("purchasable", false);
            plotData.addProperty("price", 0L);
            plotData.addProperty("restricted", false);
        }
        
        sendPacket(player, ModPacketTypes.PLOT_UPDATE, plotData);
    }
    
    /**
     * 현재 위치의 땅 정보 전송
     */
    public void sendCurrentPlotUpdate(Player player) {
        if (!enabled || player == null || services == null) return;
        
        services.getLandsIntegration().ifPresent(lands -> {
            LandsIntegration.PlotInfo plotInfo = lands.getPlotAt(player.getLocation()).orElse(null);
            sendPlotUpdate(player, plotInfo);
        });
    }
    
    // ========================================================================
    // CODEX_DATA - 도감 요약
    // ========================================================================
    
    /**
     * 도감 요약 전송 (요청 시) - 클라이언트 모드 스키마에 맞춤
     * 
     * <p>[Phase 4] 확장:</p>
     * <ul>
     *   <li>nextMilestone - 다음 마일스톤 정보</li>
     *   <li>카테고리별 reward - 완성 보상</li>
     * </ul>
     */
    public void sendCodexData(Player player) {
        if (!enabled || player == null || codexService == null || codexRegistry == null) return;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return;
        
        Set<String> registered = data.getUnlockedCodex();
        int totalItems = codexRegistry.getTotalCount();
        int collectedCount = registered.size();
        
        // 카테고리별 집계 (카테고리 순서 사용)
        Map<String, int[]> categoryStats = new LinkedHashMap<>(); // [collected, total]
        for (String category : codexRegistry.getCategoryOrder()) {
            List<CodexRule> rules = codexRegistry.getByCategory(category);
            if (rules == null) continue;
            
            int regCount = 0;
            for (CodexRule rule : rules) {
                if (registered.contains(rule.getMaterial().name())) {
                    regCount++;
                }
            }
            categoryStats.put(category, new int[]{regCount, rules.size()});
        }
        
        JsonObject codexData = new JsonObject();
        // 클라이언트 기대 필드명으로 변경
        codexData.addProperty("totalCount", totalItems);
        codexData.addProperty("collectedCount", collectedCount);
        codexData.addProperty("progressPercent", totalItems > 0 ? (collectedCount * 100.0 / totalItems) : 0.0);
        
        // 카테고리 배열 (클라이언트 스키마: name, total, collected, complete, reward)
        com.google.gson.JsonArray categories = new com.google.gson.JsonArray();
        for (Map.Entry<String, int[]> entry : categoryStats.entrySet()) {
            JsonObject cat = new JsonObject();
            int collected = entry.getValue()[0];
            int total = entry.getValue()[1];
            cat.addProperty("name", entry.getKey());
            cat.addProperty("total", total);
            cat.addProperty("collected", collected);
            cat.addProperty("complete", collected >= total);
            // [Phase 4] 카테고리 완성 보상
            cat.addProperty("reward", codexService.getCategoryCompleteReward(entry.getKey()));
            categories.add(cat);
        }
        codexData.add("categories", categories);
        
        // [Phase 4] 다음 마일스톤 정보
        JsonObject nextMilestone = buildNextMilestone(data, collectedCount);
        if (nextMilestone != null) {
            codexData.add("nextMilestone", nextMilestone);
        }
        
        sendPacket(player, ModPacketTypes.CODEX_DATA, codexData);
        logger.fine("[ModDataService] CODEX_DATA 전송: " + player.getName());
    }
    
    /**
     * [Phase 4] 다음 마일스톤 정보 생성
     */
    private JsonObject buildNextMilestone(PlayerTycoonData data, int collectedCount) {
        if (codexRegistry == null) return null;
        
        Map<Integer, kr.bapuri.tycoon.codex.CodexRegistry.MilestoneReward> milestones = codexRegistry.getMilestones();
        if (milestones == null || milestones.isEmpty()) return null;
        
        // 아직 달성하지 않은 가장 낮은 마일스톤 찾기
        for (Map.Entry<Integer, kr.bapuri.tycoon.codex.CodexRegistry.MilestoneReward> entry : milestones.entrySet()) {
            int target = entry.getKey();
            
            // 이미 수령했으면 스킵
            if (data.hasClaimedCodexMilestone(target)) continue;
            
            // 아직 달성하지 않은 마일스톤 발견
            kr.bapuri.tycoon.codex.CodexRegistry.MilestoneReward reward = entry.getValue();
            
            JsonObject milestone = new JsonObject();
            milestone.addProperty("target", target);
            milestone.addProperty("current", collectedCount);
            milestone.addProperty("bottcoinReward", reward.getBottcoin());
            milestone.addProperty("bdReward", reward.getBd());
            
            return milestone;
        }
        
        // 모든 마일스톤 달성
        return null;
    }
    
    /**
     * 도감 카테고리 상세 전송 (요청 시) - 클라이언트 모드 스키마에 맞춤
     */
    public void sendCodexCategoryData(Player player, String category) {
        if (!enabled || player == null || codexService == null || codexRegistry == null) return;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return;
        
        Set<String> registered = data.getUnlockedCodex();
        List<CodexRule> categoryRules = codexRegistry.getByCategory(category);
        
        JsonObject categoryData = new JsonObject();
        categoryData.addProperty("category", category);
        
        // 클라이언트 스키마: id, name, category, collected, iconMaterial, reward, requiredCount
        com.google.gson.JsonArray items = new com.google.gson.JsonArray();
        if (categoryRules != null) {
            for (CodexRule rule : categoryRules) {
                JsonObject item = new JsonObject();
                String materialName = rule.getMaterial().name();
                
                item.addProperty("id", materialName);
                item.addProperty("name", rule.getKoreanDisplayName());
                item.addProperty("category", category);
                item.addProperty("collected", registered.contains(materialName));
                item.addProperty("iconMaterial", materialName);  // 아이콘용
                
                // 보상 계산: override가 있으면 사용, 없으면 기본값
                Long rewardOverride = rule.getRewardOverride();
                long reward = rewardOverride != null ? rewardOverride : 
                    (rule.isConsumeOnRegister() ? codexRegistry.getDefaultConsumeReward() : codexRegistry.getDefaultKeepReward());
                item.addProperty("reward", reward);
                
                item.addProperty("requiredCount", Math.max(rule.getRequiredCount(), 1));
                items.add(item);
            }
        }
        categoryData.add("items", items);
        
        sendPacket(player, ModPacketTypes.CODEX_CATEGORY_DATA, categoryData);
    }
    
    // ========================================================================
    // 실시간 업데이트 패킷 (Phase 1에서 연동)
    // ========================================================================
    
    /**
     * 직업 경험치 업데이트 전송 (실시간)
     * 
     * @param player 플레이어
     * @param jobType 직업 타입
     * @param currentXp 현재 레벨 내 경험치
     * @param nextLevelXp 다음 레벨까지 필요한 총 경험치
     * @param level 현재 레벨
     */
    public void sendJobExpUpdate(Player player, JobType jobType, long currentXp, long nextLevelXp, int level) {
        if (!enabled || player == null || jobType == null) return;
        
        JsonObject data = new JsonObject();
        data.addProperty("jobType", jobType.name());
        data.addProperty("level", level);
        data.addProperty("currentXp", currentXp);
        data.addProperty("nextLevelXp", nextLevelXp);
        
        sendPacket(player, ModPacketTypes.JOB_EXP_UPDATE, data);
        logger.fine("[ModDataService] JOB_EXP_UPDATE 전송: " + player.getName() 
            + " " + jobType.name() + " Lv." + level);
    }
    
    /**
     * 직업 레벨업 알림 전송
     * 
     * @param player 플레이어
     * @param jobType 직업 타입
     * @param newLevel 새 레벨
     */
    public void sendJobLevelUp(Player player, JobType jobType, int newLevel) {
        if (!enabled || player == null || jobType == null) return;
        
        JsonObject data = new JsonObject();
        data.addProperty("jobType", jobType.name());
        data.addProperty("newLevel", newLevel);
        
        // 다음 레벨 경험치 정보도 함께 전송
        long nextLevelXp = kr.bapuri.tycoon.job.common.JobExpCalculator.getCumulativeExpForLevel(newLevel + 1)
            - kr.bapuri.tycoon.job.common.JobExpCalculator.getCumulativeExpForLevel(newLevel);
        data.addProperty("nextLevelXp", nextLevelXp);
        
        sendPacket(player, ModPacketTypes.JOB_LEVEL_UP, data);
        logger.info("[ModDataService] JOB_LEVEL_UP 전송: " + player.getName() 
            + " " + jobType.name() + " -> Lv." + newLevel);
    }
    
    /**
     * 직업 승급 알림 전송 (NPC에서 승급 완료 후)
     * 
     * @param player 플레이어
     * @param jobType 직업 타입
     * @param newGrade 새 등급
     * @param gradeTitle 등급 명칭
     * @param bonuses 보너스 목록
     */
    public void sendJobGradeUp(Player player, JobType jobType, int newGrade, String gradeTitle, List<String> bonuses) {
        if (!enabled || player == null || jobType == null) return;
        
        JsonObject data = new JsonObject();
        data.addProperty("jobType", jobType.name());
        data.addProperty("newGrade", newGrade);
        data.addProperty("gradeTitle", gradeTitle != null ? gradeTitle : getGradeTitle(newGrade));
        
        // 보너스 배열
        com.google.gson.JsonArray bonusArray = new com.google.gson.JsonArray();
        if (bonuses != null) {
            for (String bonus : bonuses) {
                bonusArray.add(bonus);
            }
        }
        data.add("bonuses", bonusArray);
        
        sendPacket(player, ModPacketTypes.JOB_GRADE_UP, data);
        logger.info("[ModDataService] JOB_GRADE_UP 전송: " + player.getName() 
            + " " + jobType.name() + " -> " + gradeTitle);
    }
    
    /**
     * 도감 아이템 등록 알림 전송 (서버에서 등록 완료 시)
     * 
     * @param player 플레이어
     * @param material 등록된 Material
     * @param displayName 한글 표시 이름
     * @param reward BottCoin 보상
     */
    public void sendCodexItemRegistered(Player player, Material material, String displayName, long reward) {
        if (!enabled || player == null || material == null) return;
        
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null) return;
        
        int newCollected = data.getCodexCount();
        int totalCount = codexRegistry != null ? codexRegistry.getTotalCount() : 0;
        
        JsonObject packetData = new JsonObject();
        packetData.addProperty("material", material.name());
        packetData.addProperty("displayName", displayName);
        packetData.addProperty("reward", reward);
        packetData.addProperty("newCollected", newCollected);
        packetData.addProperty("totalCount", totalCount);
        packetData.addProperty("progressPercent", totalCount > 0 ? (newCollected * 100.0 / totalCount) : 0.0);
        
        sendPacket(player, ModPacketTypes.CODEX_ITEM_REGISTERED, packetData);
        logger.info("[ModDataService] CODEX_ITEM_REGISTERED 전송: " + player.getName() 
            + " - " + displayName);
    }
    
    /**
     * 도감 등록 결과 전송 (모드 UI에서 등록 시도 시)
     * 
     * @param player 플레이어
     * @param success 성공 여부
     * @param material Material 이름
     * @param displayName 한글 표시 이름
     * @param reward BottCoin 보상
     * @param failReason 실패 사유
     * @param newCollected 새 수집 수
     * @param totalCount 전체 도감 수
     */
    public void sendCodexRegisterResult(Player player, boolean success, String material,
            String displayName, long reward, String failReason, int newCollected, int totalCount) {
        if (!enabled || player == null) return;
        
        JsonObject data = new JsonObject();
        data.addProperty("success", success);
        data.addProperty("material", material);
        
        if (success) {
            data.addProperty("displayName", displayName);
            data.addProperty("reward", reward);
            data.addProperty("newCollected", newCollected);
            data.addProperty("totalCount", totalCount);
            data.addProperty("progressPercent", totalCount > 0 ? (newCollected * 100.0 / totalCount) : 0.0);
        } else {
            data.addProperty("failReason", failReason);
        }
        
        sendPacket(player, ModPacketTypes.CODEX_REGISTER_RESULT, data);
        logger.fine("[ModDataService] CODEX_REGISTER_RESULT 전송: " + player.getName() 
            + " - " + (success ? "성공" : failReason));
    }
    
    // ========================================================================
    // 유틸리티
    // ========================================================================
    
    /**
     * 패킷 전송 (Fabric PacketByteBuf 호환 - VarInt + UTF-8 bytes)
     */
    private void sendPacket(Player player, String type, JsonObject data) {
        if (!enabled) return;
        
        // 모드 설치 여부 확인 (채널 리스닝 확인)
        if (!player.getListeningPluginChannels().contains(CHANNEL_UI_DATA)) {
            return;
        }
        
        JsonObject packet = new JsonObject();
        packet.addProperty("type", type);
        packet.add("data", data);
        
        String json = gson.toJson(packet);
        byte[] stringBytes = json.getBytes(StandardCharsets.UTF_8);
        
        try {
            // VarInt + String 형식으로 패킷 생성 (Fabric PacketByteBuf.writeString 호환)
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(baos);
            writeVarInt(out, stringBytes.length);
            out.write(stringBytes);
            byte[] bytes = baos.toByteArray();
            
            // 패킷 크기 확인 (32KB 제한)
            if (bytes.length > 32767) {
                logger.warning("[ModDataService] 패킷 크기 초과: " + bytes.length + " bytes (max 32767)");
                return;
            }
            
            player.sendPluginMessage(plugin, CHANNEL_UI_DATA, bytes);
            logger.fine("[ModDataService] 패킷 전송: player=" + player.getName() + " type=" + type + " size=" + bytes.length);
        } catch (Exception e) {
            // 채널 미등록 또는 모드 미설치 시 무시
            logger.fine("[ModDataService] 패킷 전송 실패: " + e.getMessage());
        }
    }
    
    /**
     * VarInt 쓰기 (Minecraft 표준)
     */
    private static void writeVarInt(java.io.DataOutputStream out, int value) throws java.io.IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
    
    /**
     * 직업 객체 생성 (클라이언트 모드 스키마에 맞춤)
     */
    private JsonObject buildJobObject(JobType jobType, int level, long exp, int grade) {
        if (jobType == null) return null;
        
        JsonObject job = new JsonObject();
        job.addProperty("type", jobType.name());
        job.addProperty("level", level);
        job.addProperty("grade", grade);
        job.addProperty("gradeTitle", getGradeTitle(grade));
        
        // 최대 레벨 체크
        int maxLevel = kr.bapuri.tycoon.job.common.JobExpCalculator.getMaxLevel(jobType);
        boolean isMaxLevel = level >= maxLevel;
        job.addProperty("isMaxLevel", isMaxLevel);
        
        if (isMaxLevel) {
            // 최대 레벨: 경험치 바를 가득 채운 상태로 표시
            job.addProperty("currentXp", 1L);
            job.addProperty("nextLevelXp", 1L);
        } else {
            // 현재 레벨 시작 경험치와 다음 레벨 필요 경험치 계산
            long levelStartExp = kr.bapuri.tycoon.job.common.JobExpCalculator.getCumulativeExpForLevel(level);
            long nextLevelTotalExp = kr.bapuri.tycoon.job.common.JobExpCalculator.getCumulativeExpForLevel(level + 1);
            long currentXp = exp - levelStartExp;  // 현재 레벨 내 경험치
            long nextLevelXp = nextLevelTotalExp - levelStartExp;  // 이번 레벨업에 필요한 총 경험치
            
            job.addProperty("currentXp", Math.max(0, currentXp));
            job.addProperty("nextLevelXp", Math.max(1, nextLevelXp));
        }
        return job;
    }
    
    /**
     * 등급에 따른 칭호 반환
     * 클라이언트 모드 기준: 0=초보, 1=견습, 2=숙련, 3=전문, 4=장인, 5=달인
     */
    private String getGradeTitle(int grade) {
        return switch (grade) {
            case 0 -> "초보";
            case 1 -> "견습";
            case 2 -> "숙련";
            case 3 -> "전문";
            case 4 -> "장인";
            case 5 -> "달인";
            default -> "Lv." + grade;
        };
    }
    
    /**
     * 플롯 객체 생성 (클라이언트 모드 스키마에 맞춤)
     */
    private JsonObject buildPlotObject(LandsIntegration.PlotInfo plotInfo) {
        if (plotInfo == null) return null;
        
        JsonObject plot = new JsonObject();
        boolean hasOwner = plotInfo.getOwnerId() != null;
        plot.addProperty("hasOwner", hasOwner);
        plot.addProperty("ownerName", plotInfo.getOwnerName());
        plot.addProperty("purchasable", false);  // LITE: 땅 구매 시스템 미지원
        plot.addProperty("price", 0L);
        plot.addProperty("restricted", false);   // LITE: 보호 구역 구분 미지원
        return plot;
    }
    
    // ========================================================================
    // 설정
    // ========================================================================
    
    /**
     * 설정 리로드
     */
    public void reload() {
        boolean wasEnabled = enabled;
        enabled = plugin.getConfig().getBoolean("mod_integration.enabled", false);
        
        if (enabled && !wasEnabled) {
            initialize();
        } else if (!enabled && wasEnabled) {
            shutdown();
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Bukkit 월드 이름을 논리적 월드 ID로 변환합니다.
     * 
     * <p>config.yml의 월드 매핑에 맞춰 변환:</p>
     * <ul>
     *   <li>world → town</li>
     *   <li>world_wild, world_wild_nether, world_wild_the_end → wild</li>
     *   <li>world_hunter → hunter</li>
     *   <li>기타 → 원본 이름</li>
     * </ul>
     * 
     * @param bukkitWorldName Bukkit 월드 이름
     * @return 논리적 월드 ID
     */
    private String convertToWorldId(String bukkitWorldName) {
        if (bukkitWorldName == null) return "unknown";
        
        String name = bukkitWorldName.toLowerCase();
        
        // config.yml 기준 매핑
        if (name.equals("world")) {
            return "town";
        } else if (name.startsWith("world_wild")) {
            return "wild";
        } else if (name.startsWith("world_hunter")) {
            return "hunter";
        } else if (name.startsWith("world_duel")) {
            return "duel";
        } else if (name.startsWith("world_dungeon")) {
            return "dungeon";
        }
        
        return name;  // 매핑 안 되면 원본 반환
    }
}
