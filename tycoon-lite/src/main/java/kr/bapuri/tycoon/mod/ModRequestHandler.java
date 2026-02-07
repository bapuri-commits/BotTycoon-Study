package kr.bapuri.tycoon.mod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.bapuri.tycoon.codex.CodexRegisterResult;
import kr.bapuri.tycoon.codex.CodexService;
import kr.bapuri.tycoon.job.JobService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * ModRequestHandler - 클라이언트 모드 요청 수신 핸들러
 * 
 * <h2>역할</h2>
 * 클라이언트 모드에서 보내는 요청을 수신하고 처리합니다.
 * 
 * <h2>채널</h2>
 * tycoon:ui_request (클라이언트 → 서버)
 * 
 * <h2>지원 액션</h2>
 * <ul>
 *   <li>PROFILE - 프로필 요청</li>
 *   <li>VITAL - Vital 데이터 요청</li>
 *   <li>REQUEST_CODEX_SUMMARY - 도감 요약 요청</li>
 *   <li>REQUEST_CODEX_CATEGORY - 도감 카테고리 요청</li>
 *   <li>REQUEST_JOB_DETAIL - 직업 상세 요청</li>
 *   <li>TRIGGER_JOB_PROMOTION - 직업 승급 시도</li>
 *   <li>REGISTER_CODEX_ITEM - 도감 아이템 등록</li>
 * </ul>
 * 
 * @see ModDataService 데이터 전송 서비스
 */
public class ModRequestHandler implements PluginMessageListener {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final Gson gson;
    
    // 서비스 참조
    private ModDataService modDataService;
    private PlayerDataManager dataManager;
    private JobService jobService;
    private CodexService codexService;
    
    // 활성화 여부
    private boolean enabled = false;
    
    public ModRequestHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gson = new Gson();
    }
    
    // ========================================================================
    // Setter Injection
    // ========================================================================
    
    public void setModDataService(ModDataService modDataService) {
        this.modDataService = modDataService;
    }
    
    public void setPlayerDataManager(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }
    
    public void setJobService(JobService jobService) {
        this.jobService = jobService;
    }
    
    public void setCodexService(CodexService codexService) {
        this.codexService = codexService;
    }
    
    // ========================================================================
    // 초기화 / 종료
    // ========================================================================
    
    /**
     * 초기화 (수신 채널 등록)
     */
    public void initialize() {
        enabled = plugin.getConfig().getBoolean("mod_integration.enabled", false);
        
        if (!enabled) {
            logger.info("[ModRequestHandler] 비활성화 상태");
            return;
        }
        
        // 수신 채널 등록
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
            plugin, ModDataService.CHANNEL_UI_REQUEST, this);
        
        logger.info("[ModRequestHandler] 초기화 완료 (채널: " + ModDataService.CHANNEL_UI_REQUEST + ")");
    }
    
    /**
     * 종료 (채널 해제)
     */
    public void shutdown() {
        if (!enabled) return;
        
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
            plugin, ModDataService.CHANNEL_UI_REQUEST, this);
        
        logger.info("[ModRequestHandler] 종료됨");
    }
    
    // ========================================================================
    // 메시지 수신
    // ========================================================================
    
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!enabled) return;
        if (!channel.equals(ModDataService.CHANNEL_UI_REQUEST)) return;
        
        try {
            // VarInt + String 형식으로 파싱 (Fabric PacketByteBuf.readString 호환)
            ByteArrayInputStream bais = new ByteArrayInputStream(message);
            DataInputStream in = new DataInputStream(bais);
            String json = readVarIntString(in, 32767);
            
            // JSON 파싱
            JsonObject request = JsonParser.parseString(json).getAsJsonObject();
            
            // action 또는 type 필드 확인 (클라이언트 호환성)
            String action;
            if (request.has("action")) {
                action = request.get("action").getAsString();
            } else if (request.has("type")) {
                action = request.get("type").getAsString();
            } else {
                logger.warning("[ModRequestHandler] 요청에 action/type 필드 없음: " + json);
                return;
            }
            
            // 클라이언트는 "params" 또는 "data" 필드를 사용
            JsonObject params = null;
            if (request.has("params") && request.get("params").isJsonObject()) {
                params = request.getAsJsonObject("params");
            } else if (request.has("data") && request.get("data").isJsonObject()) {
                params = request.getAsJsonObject("data");
            }
            
            logger.fine("[ModRequestHandler] 요청 수신: " + action + " from " + player.getName());
            
            // 액션 처리
            handleAction(player, action, params);
            
        } catch (Exception e) {
            logger.warning("[ModRequestHandler] 요청 처리 실패: " + e.getMessage());
        }
    }
    
    /**
     * VarInt 읽기 (Minecraft 표준)
     */
    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;

        while (true) {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0) break;

            position += 7;
            if (position >= 32) {
                throw new IOException("VarInt is too big");
            }
        }

        return value;
    }
    
    /**
     * VarInt + String 읽기 (Fabric PacketByteBuf.readString 호환)
     */
    private static String readVarIntString(DataInputStream in, int maxLength) throws IOException {
        int length = readVarInt(in);
        if (length > maxLength * 4) {
            throw new IOException("String too long: " + length + " bytes (max " + (maxLength * 4) + ")");
        }
        if (length < 0) {
            throw new IOException("String length is negative: " + length);
        }

        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    /**
     * 액션 처리
     */
    private void handleAction(Player player, String action, JsonObject params) {
        // ModPacketTypes 상수 사용
        if (ModPacketTypes.REQUEST_PROFILE.equals(action)) {
            handleProfileRequest(player);
        } else if (ModPacketTypes.REQUEST_VITAL.equals(action)) {
            handleVitalRequest(player);
        } else if (ModPacketTypes.REQUEST_CODEX_SUMMARY.equals(action)) {
            handleCodexSummaryRequest(player);
        } else if (ModPacketTypes.REQUEST_CODEX_CATEGORY.equals(action)) {
            handleCodexCategoryRequest(player, params);
        } else if (ModPacketTypes.REGISTER_CODEX_ITEM.equals(action)) {
            handleCodexRegister(player, params);
        } else if (ModPacketTypes.REQUEST_JOB_DETAIL.equals(action)) {
            handleJobDetailRequest(player);
        } else if (ModPacketTypes.TRIGGER_JOB_PROMOTION.equals(action)) {
            handleJobPromotion(player);
        } else {
            logger.fine("[ModRequestHandler] 알 수 없는 액션: " + action);
        }
    }
    
    // ========================================================================
    // 기본 데이터 요청 핸들러
    // ========================================================================
    
    private void handleProfileRequest(Player player) {
        if (modDataService != null) {
            modDataService.sendPlayerProfile(player);
        }
    }
    
    private void handleVitalRequest(Player player) {
        if (modDataService != null) {
            modDataService.sendVitalUpdate(player);
        }
    }
    
    // ========================================================================
    // 도감 요청 핸들러
    // ========================================================================
    
    private void handleCodexSummaryRequest(Player player) {
        if (modDataService != null) {
            modDataService.sendCodexData(player);
        }
    }
    
    private void handleCodexCategoryRequest(Player player, JsonObject params) {
        if (modDataService == null || params == null) return;
        
        if (!params.has("category")) {
            logger.warning("[ModRequestHandler] REQUEST_CODEX_CATEGORY에 category 없음");
            return;
        }
        
        String category = params.get("category").getAsString();
        modDataService.sendCodexCategoryData(player, category);
    }
    
    /**
     * 도감 아이템 등록 요청 처리
     * 
     * <p>클라이언트 모드에서 도감 GUI를 통해 아이템을 등록할 때 호출됩니다.</p>
     * <p>[Phase 1 동기화] 등록 결과를 CODEX_REGISTER_RESULT 패킷으로 전송합니다.</p>
     */
    private void handleCodexRegister(Player player, JsonObject params) {
        if (codexService == null || params == null) return;
        
        if (!params.has("material")) {
            logger.warning("[ModRequestHandler] REGISTER_CODEX_ITEM에 material 없음");
            return;
        }
        
        String materialName = params.get("material").getAsString();
        
        try {
            Material material = Material.valueOf(materialName);
            
            // CodexService를 통해 등록 시도
            CodexRegisterResult result = codexService.tryRegister(player, material);
            
            // [Phase 1 동기화] 결과를 CODEX_REGISTER_RESULT 패킷으로 전송
            if (modDataService != null) {
                var rule = codexService.getRule(material);
                int totalCount = codexService.getTotalCount();
                int newCollected = codexService.getCollectedCount(player);
                
                if (result == CodexRegisterResult.SUCCESS) {
                    // 성공: 도감 데이터도 함께 갱신
                    String displayName = rule != null ? rule.getKoreanDisplayName() : materialName;
                    // [Phase 1 동기화] CodexService.calculateReward() 사용으로 보상 계산 일관성 확보
                    long reward = codexService.calculateReward(rule);
                    
                    modDataService.sendCodexRegisterResult(player, true, materialName, 
                        displayName, reward, null, newCollected, totalCount);
                    modDataService.sendCodexData(player);
                    
                    logger.info("[ModRequestHandler] 도감 등록 성공: " + player.getName() + " - " + materialName);
                } else {
                    // 실패: 사유 전송
                    String failReason = switch (result) {
                        case ALREADY_REGISTERED -> "already_registered";
                        case NOT_IN_CODEX -> "not_in_codex";
                        case NOT_ENOUGH_ITEMS -> "not_enough_count";
                        default -> "unknown_error";
                    };
                    
                    modDataService.sendCodexRegisterResult(player, false, materialName, 
                        null, 0, failReason, newCollected, totalCount);
                    
                    logger.fine("[ModRequestHandler] 도감 등록 실패: " + player.getName() + " - " + result);
                }
            }
            
        } catch (IllegalArgumentException e) {
            // 잘못된 Material
            if (modDataService != null) {
                modDataService.sendCodexRegisterResult(player, false, materialName, 
                    null, 0, "not_in_codex", 0, 0);
            }
            logger.warning("[ModRequestHandler] 잘못된 Material: " + materialName);
        }
    }
    
    // ========================================================================
    // 직업 요청 핸들러
    // ========================================================================
    
    private void handleJobDetailRequest(Player player) {
        if (modDataService != null) {
            modDataService.sendJobDetail(player);
        }
    }
    
    /**
     * 직업 승급 시도
     * 
     * <p>LITE 버전에서는 직업 승급이 NPC/GUI를 통해 이루어지므로
     * 클라이언트 모드에서는 데이터 갱신만 전송합니다.</p>
     */
    private void handleJobPromotion(Player player) {
        // LITE에서는 모드를 통한 직업 승급 미지원 - 데이터 갱신만 전송
        if (modDataService != null) {
            modDataService.sendJobData(player);
            modDataService.sendEconomyUpdate(player);
        }
        
        logger.fine("[ModRequestHandler] 승급 요청 (LITE: 미지원, 데이터 갱신만 전송): " + player.getName());
    }
    
    // ========================================================================
    // 설정
    // ========================================================================
    
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
}
