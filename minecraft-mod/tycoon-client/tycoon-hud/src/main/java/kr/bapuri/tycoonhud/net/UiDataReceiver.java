package kr.bapuri.tycoonhud.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import kr.bapuri.tycoonhud.TycoonHudMod;
import kr.bapuri.tycoonhud.model.AugmentData;
import kr.bapuri.tycoonhud.model.CountdownData;
import kr.bapuri.tycoonhud.model.DuelData;
import kr.bapuri.tycoonhud.model.DungeonMapData;
import kr.bapuri.tycoonhud.model.JobData;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.model.PlotInfo;
import kr.bapuri.tycoonhud.model.ReadyStatusData;
import kr.bapuri.tycoonhud.model.VitalData;
import kr.bapuri.tycoonhud.screen.AugmentSelectionScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.io.StringReader;

/**
 * Handler for receiving UI data from the server.
 * 
 * <h3>Receive Channel</h3>
 * <ul>
 *     <li>{@code tycoon:ui_data} - All UI-related data</li>
 * </ul>
 * 
 * <h3>Packet Types</h3>
 * <ul>
 *     <li>PLAYER_PROFILE - Full profile data</li>
 *     <li>VITAL_UPDATE - Health/hunger updates</li>
 *     <li>ECONOMY_UPDATE - BD/BottCoin updates</li>
 * </ul>
 * 
 * <h3>Troubleshooting</h3>
 * <p>If packets are not received:</p>
 * <ol>
 *     <li>Check if channel is registered on the server plugin</li>
 *     <li>Verify channel ID matches exactly (tycoon:ui_data)</li>
 *     <li>Confirm player.sendPluginMessage() is called on server</li>
 * </ol>
 */
public class UiDataReceiver {
    
    /** 
     * Receive channel ID.
     * Must exactly match the server plugin's channel ID.
     */
    public static final Identifier CHANNEL_ID = new Identifier("tycoon", "ui_data");
    
    /** JSON parser (reused) */
    private static final Gson GSON = new Gson();
    
    /** Expected schema version (v3: 업적 상세 정보 추가) */
    private static final int EXPECTED_SCHEMA = 3;
    
    // ================================================================================
    // 분할 맵 수신 버퍼 (v2.7)
    // ================================================================================
    private static String pendingMapAreaId = null;
    private static int pendingMapCenterX = 0;
    private static int pendingMapCenterZ = 0;
    private static int pendingMapRadius = 0;
    private static int pendingMapResolution = 0;
    private static String pendingMapFormat = null;
    private static int pendingTotalChunks = 0;
    private static String[] pendingChunks = null;
    private static int receivedChunks = 0;
    
    /**
     * Registers the network receiver.
     * 
     * <p>Should be called only once during mod initialization.</p>
     */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL_ID, UiDataReceiver::handlePacket);
        TycoonHudMod.LOGGER.info("[TycoonHUD] Network receiver registered: {}", CHANNEL_ID);
    }
    
    /**
     * Packet receive handler.
     * 
     * <p>Note: This method is called from the network thread.
     * UI updates must be performed on the main thread.</p>
     * 
     * @param client Minecraft client
     * @param handler Network handler
     * @param buf Packet data buffer
     * @param responseSender Response sender
     */
    private static void handlePacket(
            MinecraftClient client,
            ClientPlayNetworkHandler handler,
            PacketByteBuf buf,
            PacketSender responseSender
    ) {
        // Read data from network thread
        String jsonString;
        try {
            // Check remaining bytes in buffer
            int readableBytes = buf.readableBytes();
            if (readableBytes == 0) {
                TycoonHudMod.LOGGER.warn("[TycoonHUD] Empty packet received");
                return;
            }
            
            jsonString = buf.readString(32767); // Max 32KB
            
            // Empty string check
            if (jsonString == null || jsonString.trim().isEmpty()) {
                TycoonHudMod.LOGGER.warn("[TycoonHUD] Empty JSON string received");
                return;
            }
            
        } catch (Exception e) {
            TycoonHudMod.LOGGER.error("[TycoonHUD] Failed to read packet: {}", e.getMessage());
            return;
        }
        
        // Mark as connected (received first packet from BotTycoon server)
        TycoonClientState.setConnected(true);
        
        // Log raw data (for debugging - first 100 chars only)
        String preview = jsonString.length() > 100 ? jsonString.substring(0, 100) + "..." : jsonString;
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Packet received (length={}): {}", jsonString.length(), preview);
        
        // Process on main thread
        final String finalJson = jsonString;
        client.execute(() -> processJson(finalJson));
    }
    
    /**
     * Parses and processes JSON data.
     * 
     * <p>This method is called from the main thread.</p>
     * <p>Uses lenient mode to handle non-standard JSON.</p>
     * 
     * @param jsonString JSON string
     */
    private static void processJson(String jsonString) {
        try {
            // Preprocess JSON - remove BOM and trim whitespace
            jsonString = sanitizeJson(jsonString);
            
            // Quick check if JSON is valid
            if (!jsonString.startsWith("{")) {
                TycoonHudMod.LOGGER.error("[TycoonHUD] Invalid JSON format (not an object). First char: '{}'", 
                    jsonString.isEmpty() ? "(empty string)" : jsonString.charAt(0));
                TycoonHudMod.LOGGER.debug("[TycoonHUD] Raw data: {}", jsonString);
                return;
            }
            
            // Try parsing with lenient mode
            JsonObject root;
            try {
                root = JsonParser.parseString(jsonString).getAsJsonObject();
            } catch (Exception e) {
                // Retry with lenient mode
                TycoonHudMod.LOGGER.debug("[TycoonHUD] Standard parsing failed, retrying with lenient mode...");
                JsonReader reader = new JsonReader(new StringReader(jsonString));
                reader.setLenient(true);
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            
            // Check type field
            if (!root.has("type") || root.get("type").isJsonNull()) {
                TycoonHudMod.LOGGER.warn("[TycoonHUD] Packet missing 'type' field");
                TycoonHudMod.LOGGER.debug("[TycoonHUD] Received JSON: {}", jsonString);
                return;
            }
            
            String type = root.get("type").getAsString();
            
            // 분할 맵 패킷은 data 필드 없이 최상위에 직접 필드 포함
            if ("HUNTER_MAP_META".equals(type) || "HUNTER_MAP_CHUNK".equals(type)) {
                switch (type) {
                    case "HUNTER_MAP_META" -> handleHunterMapMeta(root);
                    case "HUNTER_MAP_CHUNK" -> handleHunterMapChunk(root);
                }
                return;
            }
            
            JsonObject data = root.has("data") ? root.getAsJsonObject("data") : null;
            
            if (data == null) {
                TycoonHudMod.LOGGER.warn("[TycoonHUD] Packet missing 'data' field (type={})", type);
                return;
            }
            
            // Schema version check (graceful degradation)
            if (data.has("schema")) {
                int schema = data.get("schema").getAsInt();
                if (schema > EXPECTED_SCHEMA) {
                    // 서버가 더 최신 - 경고만 출력하고 계속 처리 (하위 호환)
                    TycoonHudMod.LOGGER.warn(
                        "[TycoonHUD] Server schema is newer. Expected: {}, Received: {}. Some new features may not work.",
                        EXPECTED_SCHEMA, schema
                    );
                } else if (schema < EXPECTED_SCHEMA) {
                    // 서버가 더 구버전 - 기본값 사용
                    TycoonHudMod.LOGGER.info(
                        "[TycoonHUD] Server schema is older. Expected: {}, Received: {}. Using defaults for missing fields.",
                        EXPECTED_SCHEMA, schema
                    );
                }
            }
            
            // Handle by type
            switch (type) {
                // 기본 패킷
                case "PLAYER_PROFILE" -> handlePlayerProfile(data);
                case "VITAL_UPDATE" -> handleVitalUpdate(data);
                case "ECONOMY_UPDATE" -> handleEconomyUpdate(data);
                case "JOB_DATA" -> handleJobData(data);
                case "PLOT_UPDATE" -> handlePlotUpdate(data);
                
                // 실시간 직업 업데이트 패킷 (Phase 1)
                case "JOB_EXP_UPDATE" -> handleJobExpUpdate(data);
                case "JOB_LEVEL_UP" -> handleJobLevelUp(data);
                case "JOB_GRADE_UP" -> handleJobGradeUp(data);
                
                // 도감 패킷 (Phase 1)
                case "CODEX_REGISTER_RESULT" -> handleCodexRegisterResult(data);
                case "CODEX_ITEM_REGISTERED" -> handleCodexItemRegistered(data);
                
                // 헌터/듀얼/던전 패킷
                case "DUEL_SESSION" -> handleDuelSession(data);
                case "MINIMAP_DATA" -> handleMinimapData(data);
                case "HUNTER_RANKING" -> handleHunterRanking(data);
                case "READY_STATUS" -> handleReadyStatus(data);
                case "COUNTDOWN_UPDATE" -> handleCountdownUpdate(data);
                case "AUGMENT_SELECTION" -> handleAugmentSelection(data);
                case "DUNGEON_MAP" -> handleDungeonMap(data);
                case "HUNTER_MAP_FULL" -> handleHunterMapFull(data);
                case "HUNTER_MAP_META" -> handleHunterMapMeta(data);
                case "HUNTER_MAP_CHUNK" -> handleHunterMapChunk(data);
                case "HUNTER_MAP_UPDATE" -> handleHunterMapUpdate(data);
                
                default -> {
                    // Try forwarding to tycoon-ui (check if class exists at runtime)
                    try {
                        Class<?> handlerClass = Class.forName("kr.bapuri.tycoonui.net.UiResponseHandler");
                        java.lang.reflect.Method method = handlerClass.getMethod("handlePacket", String.class, JsonObject.class);
                        method.invoke(null, type, data);
                    } catch (ClassNotFoundException e) {
                        // tycoon-ui not installed - ignore
                        TycoonHudMod.LOGGER.debug("[TycoonHUD] Unknown packet type (tycoon-ui not installed): {}", type);
                    } catch (Exception e) {
                        TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to forward packet: {}", e.getMessage());
                    }
                }
            }
            
        } catch (JsonSyntaxException | IllegalStateException e) {
            // JsonSyntaxException wraps MalformedJsonException
            TycoonHudMod.LOGGER.error("[TycoonHUD] JSON parsing error: {}", e.getMessage());
            logMalformedJsonDetails(jsonString);
        } catch (Exception e) {
            TycoonHudMod.LOGGER.error("[TycoonHUD] Error processing packet: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Preprocesses the JSON string.
     * Removes BOM, control characters, etc.
     * 
     * @param json Raw JSON string
     * @return Sanitized JSON string
     */
    private static String sanitizeJson(String json) {
        if (json == null) return "";
        
        // Remove UTF-8 BOM
        if (json.startsWith("\uFEFF")) {
            json = json.substring(1);
        }
        
        // Trim whitespace
        json = json.trim();
        
        // Remove NULL characters
        json = json.replace("\u0000", "");
        
        return json;
    }
    
    /**
     * Logs detailed information about malformed JSON.
     * 
     * @param jsonString The malformed JSON string
     */
    private static void logMalformedJsonDetails(String jsonString) {
        TycoonHudMod.LOGGER.error("[TycoonHUD] === Malformed JSON Debug Info ===");
        TycoonHudMod.LOGGER.error("[TycoonHUD] Length: {}", jsonString.length());
        
        // Print first 50 and last 50 characters
        if (jsonString.length() <= 100) {
            TycoonHudMod.LOGGER.error("[TycoonHUD] Full content: '{}'", jsonString);
        } else {
            TycoonHudMod.LOGGER.error("[TycoonHUD] Start: '{}'", jsonString.substring(0, 50));
            TycoonHudMod.LOGGER.error("[TycoonHUD] End: '{}'", jsonString.substring(jsonString.length() - 50));
        }
        
        // Print hex values of first 10 bytes
        StringBuilder hexBuilder = new StringBuilder();
        for (int i = 0; i < Math.min(10, jsonString.length()); i++) {
            hexBuilder.append(String.format("%02X ", (int) jsonString.charAt(i)));
        }
        TycoonHudMod.LOGGER.error("[TycoonHUD] First 10 bytes (hex): {}", hexBuilder.toString().trim());
    }
    
    /**
     * Handles PLAYER_PROFILE packet.
     * 
     * @param data Profile data JSON object
     */
    private static void handlePlayerProfile(JsonObject data) {
        PlayerProfileData profile = GSON.fromJson(data, PlayerProfileData.class);
        
        if (profile == null || profile.getName() == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Incomplete profile data received");
            return;
        }
        
        PlayerDataManager.getInstance().setProfile(profile);
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Profile updated: {}", profile.getName());
    }
    
    /**
     * Handles VITAL_UPDATE packet.
     * 
     * <p>버그 방지:</p>
     * <ul>
     *     <li>null 데이터 거부</li>
     *     <li>유효하지 않은 데이터 경고 (하지만 안전한 기본값 사용)</li>
     *     <li>모든 필드 로깅</li>
     * </ul>
     * 
     * @param data Vital data JSON object
     */
    private static void handleVitalUpdate(JsonObject data) {
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Processing VITAL_UPDATE: {}", data.toString());
        
        VitalData vital;
        try {
            vital = GSON.fromJson(data, VitalData.class);
        } catch (Exception e) {
            TycoonHudMod.LOGGER.error("[TycoonHUD] Failed to parse VITAL_UPDATE: {}", e.getMessage());
            return;
        }
        
        if (vital == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] VITAL_UPDATE parsed to null");
            return;
        }
        
        // 유효성 검증 (경고만, VitalData가 안전한 기본값 사용)
        if (!vital.isValid()) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Invalid vital data received: {}", vital);
        }
        
        // 데이터 저장
        PlayerDataManager.getInstance().setVital(vital);
        
        // 상세 로깅
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Vital updated: {}", vital);
    }
    
    /**
     * Handles ECONOMY_UPDATE packet.
     * 
     * <p>Updates BD and BottCoin in real-time.</p>
     * 
     * @param data Economy data JSON object
     */
    private static void handleEconomyUpdate(JsonObject data) {
        long bd = data.has("bd") ? data.get("bd").getAsLong() : 0;
        int bottcoin = data.has("bottcoin") ? data.get("bottcoin").getAsInt() : 0;
        
        PlayerDataManager.getInstance().updateEconomy(bd, bottcoin);
        TycoonHudMod.LOGGER.info("[TycoonHUD] Economy updated: BD={}, BC={}", bd, bottcoin);
    }
    
    /**
     * Handles JOB_DATA packet.
     * 
     * <p>Updates job data when player changes, selects, or levels up a job.</p>
     * 
     * @param data Job data JSON object
     */
    private static void handleJobData(JsonObject data) {
        // JOB_DATA can contain primaryJob and/or secondaryJob
        JobData primaryJob = null;
        JobData secondaryJob = null;
        
        if (data.has("primaryJob") && !data.get("primaryJob").isJsonNull()) {
            primaryJob = GSON.fromJson(data.getAsJsonObject("primaryJob"), JobData.class);
        }
        
        if (data.has("secondaryJob") && !data.get("secondaryJob").isJsonNull()) {
            secondaryJob = GSON.fromJson(data.getAsJsonObject("secondaryJob"), JobData.class);
        }
        
        PlayerDataManager.getInstance().updateJobs(primaryJob, secondaryJob);
        
        String primaryName = primaryJob != null ? primaryJob.getLocalizedType() + " Lv." + primaryJob.getLevel() : "없음";
        String secondaryName = secondaryJob != null ? secondaryJob.getLocalizedType() + " Lv." + secondaryJob.getLevel() : "없음";
        TycoonHudMod.LOGGER.info("[TycoonHUD] Job updated: Primary={}, Secondary={}", primaryName, secondaryName);
    }
    
    /**
     * Handles DUEL_SESSION packet.
     * 
     * <p>Updates duel session data for HUD display.</p>
     * 
     * @param data Duel session data JSON object
     */
    private static void handleDuelSession(JsonObject data) {
        DuelData duel = GSON.fromJson(data, DuelData.class);
        
        if (duel == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse DUEL_SESSION data");
            return;
        }
        
        // If duel ended or not in duel, clear the data
        if (!duel.isInDuel() || "FINISHED".equals(duel.getState())) {
            PlayerDataManager.getInstance().clearDuel();
            TycoonHudMod.LOGGER.debug("[TycoonHUD] Duel session ended");
        } else {
            PlayerDataManager.getInstance().setDuel(duel);
            TycoonHudMod.LOGGER.debug("[TycoonHUD] Duel session updated: vs {} ({}s remaining)", 
                duel.getOpponentName(), duel.getRemainingSeconds());
        }
    }
    
    /**
     * Handles MINIMAP_DATA packet.
     * 
     * <p>Updates minimap data for hunter world HUD.</p>
     * 
     * @param data Minimap data JSON object
     */
    private static void handleMinimapData(JsonObject data) {
        kr.bapuri.tycoonhud.model.MinimapData minimap = GSON.fromJson(data, kr.bapuri.tycoonhud.model.MinimapData.class);
        
        if (minimap == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse MINIMAP_DATA");
            return;
        }
        
        PlayerDataManager.getInstance().setMinimap(minimap);
    }
    
    /**
     * Handles HUNTER_RANKING packet.
     * 
     * <p>Updates hunter world ranking and stats data for HUD display.</p>
     * 
     * @param data Hunter ranking data JSON object
     */
    private static void handleHunterRanking(JsonObject data) {
        kr.bapuri.tycoonhud.model.HunterRankingData ranking = GSON.fromJson(data, kr.bapuri.tycoonhud.model.HunterRankingData.class);
        
        if (ranking == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse HUNTER_RANKING");
            return;
        }
        
        PlayerDataManager.getInstance().setHunterRanking(ranking);
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Hunter ranking updated: rank={}/{}, score={}", 
            ranking.getMyRank(), ranking.getTotalPlayers(), ranking.getMyScore());
    }
    
    /**
     * Handles READY_STATUS packet.
     * 
     * <p>Updates ready status data for hunter lobby HUD.</p>
     * 
     * @param data Ready status data JSON object
     */
    private static void handleReadyStatus(JsonObject data) {
        ReadyStatusData readyStatus = GSON.fromJson(data, ReadyStatusData.class);
        
        if (readyStatus == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse READY_STATUS");
            return;
        }
        
        PlayerDataManager.getInstance().setReadyStatus(readyStatus);
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Ready status updated: ready={}, {}/{} in lobby, state={}", 
            readyStatus.isReady(), readyStatus.getReadyCount(), readyStatus.getTotalInLobby(), readyStatus.getState());
    }
    
    /**
     * Handles COUNTDOWN_UPDATE packet.
     * 
     * <p>Updates countdown data for game start countdown display.</p>
     * 
     * @param data Countdown data JSON object
     */
    private static void handleCountdownUpdate(JsonObject data) {
        CountdownData countdown = GSON.fromJson(data, CountdownData.class);
        
        if (countdown == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse COUNTDOWN_UPDATE");
            return;
        }
        
        PlayerDataManager.getInstance().setCountdown(countdown);
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Countdown updated: {}s remaining, active={}", 
            countdown.getSecondsRemaining(), countdown.isActive());
    }
    
    /**
     * Handles AUGMENT_SELECTION packet.
     * 
     * <p>Opens the augment selection screen when server sends augment choices.</p>
     * <p>This is triggered when player levels up in HCL (Hunter Combat Level).</p>
     * 
     * @param data Augment selection data JSON object
     */
    private static void handleAugmentSelection(JsonObject data) {
        TycoonHudMod.LOGGER.info("[TycoonHUD] Received AUGMENT_SELECTION packet");
        
        AugmentData augmentData = GSON.fromJson(data, AugmentData.class);
        
        if (augmentData == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse AUGMENT_SELECTION");
            return;
        }
        
        // Case 1: showSelection=false → 선택 완료 또는 알림만
        if (!augmentData.isShowSelection()) {
            if (augmentData.isAugmentPending()) {
                // Case 1a: 알림만 (증강 대기 중, UI 안 열림)
                // G키로 열 수 있도록 pending 상태만 저장
                PlayerDataManager.getInstance().setAugmentData(augmentData);
                TycoonHudMod.LOGGER.info("[TycoonHUD] Augment pending notification (press G to open)");
            } else {
                // Case 1b: 선택 완료 → augmentData 클리어
                PlayerDataManager.getInstance().clearAugmentData();
                TycoonHudMod.LOGGER.info("[TycoonHUD] Augment selection closed (showSelection=false)");
            }
            return;
        }
        
        // Case 2: showSelection=true → UI 열기
        if (augmentData.getChoices() == null || augmentData.getChoices().isEmpty()) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] AUGMENT_SELECTION has no choices");
            return;
        }
        
        // Store in PlayerDataManager for reference
        PlayerDataManager.getInstance().setAugmentData(augmentData);
        
        // Open the selection screen on the main thread
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                TycoonHudMod.LOGGER.info("[TycoonHUD] Opening AugmentSelectionScreen with {} choices (HCL Lv.{})",
                    augmentData.getChoices().size(), augmentData.getCurrentHclLevel());
                client.setScreen(new AugmentSelectionScreen(augmentData));
            });
        }
    }
    
    /**
     * Handles DUNGEON_MAP packet.
     * 
     * <p>Updates dungeon map data for roguelike dungeon HUD.</p>
     * <p>This includes node layout, explored areas, and current position.</p>
     * 
     * @param data Dungeon map data JSON object
     */
    private static void handleDungeonMap(JsonObject data) {
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Received DUNGEON_MAP packet");
        
        DungeonMapData dungeonMap = GSON.fromJson(data, DungeonMapData.class);
        
        if (dungeonMap == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse DUNGEON_MAP");
            return;
        }
        
        // If dungeon is not active, clear the data
        if (!dungeonMap.isActive()) {
            PlayerDataManager.getInstance().clearDungeonMap();
            TycoonHudMod.LOGGER.debug("[TycoonHUD] Dungeon map cleared (inactive)");
            return;
        }
        
        PlayerDataManager.getInstance().setDungeonMap(dungeonMap);
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Dungeon map updated: floor={}, currentNode={}, exploredNodes={}",
            dungeonMap.getCurrentFloor(), 
            dungeonMap.getCurrentNodeId(), 
            dungeonMap.getExploredNodes().size());
    }
    
    // ================================================================================
    // 헌터 맵 패킷 처리 (v2.7: 분할 전송 지원)
    // ================================================================================
    
    /**
     * Handles HUNTER_MAP_FULL packet (legacy single-packet method).
     * 
     * <p>Receives the full game area map image from the server.</p>
     * <p>This is sent once at game start.</p>
     * 
     * @param data Hunter map data JSON object
     */
    private static void handleHunterMapFull(JsonObject data) {
        TycoonHudMod.LOGGER.info("[TycoonHUD] Received HUNTER_MAP_FULL packet (legacy)");
        
        kr.bapuri.tycoonhud.model.HunterMapData mapData = GSON.fromJson(data, kr.bapuri.tycoonhud.model.HunterMapData.class);
        
        if (mapData == null || !mapData.isValid()) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse HUNTER_MAP_FULL or invalid data");
            return;
        }
        
        PlayerDataManager.getInstance().setHunterMap(mapData);
        
        // Notify HunterHudOverlay to load the texture
        kr.bapuri.tycoonhud.hud.HunterHudOverlay.onMapDataReceived(mapData);
        
        TycoonHudMod.LOGGER.info("[TycoonHUD] Hunter map loaded: area={}, center=({},{}), radius={}, resolution={}",
            mapData.getAreaId(), mapData.getCenterX(), mapData.getCenterZ(), 
            mapData.getRadius(), mapData.getResolution());
    }
    
    /**
     * Handles HUNTER_MAP_META packet (v2.7 chunked transfer).
     * 
     * <p>Receives metadata for the map and prepares for chunk reception.</p>
     * 
     * @param data Map metadata JSON object
     */
    private static void handleHunterMapMeta(JsonObject data) {
        pendingMapAreaId = data.has("areaId") ? data.get("areaId").getAsString() : null;
        pendingMapCenterX = data.has("centerX") ? data.get("centerX").getAsInt() : 0;
        pendingMapCenterZ = data.has("centerZ") ? data.get("centerZ").getAsInt() : 0;
        pendingMapRadius = data.has("radius") ? data.get("radius").getAsInt() : 0;
        pendingMapResolution = data.has("resolution") ? data.get("resolution").getAsInt() : 0;
        pendingMapFormat = data.has("format") ? data.get("format").getAsString() : "JPEG";
        pendingTotalChunks = data.has("totalChunks") ? data.get("totalChunks").getAsInt() : 0;
        int totalSize = data.has("totalSize") ? data.get("totalSize").getAsInt() : 0;
        
        // 청크 배열 초기화
        pendingChunks = new String[pendingTotalChunks];
        receivedChunks = 0;
        
        TycoonHudMod.LOGGER.info("[TycoonHUD] Map meta received: area={}, {}KB in {} chunks",
            pendingMapAreaId, totalSize / 1024, pendingTotalChunks);
    }
    
    /**
     * Handles HUNTER_MAP_CHUNK packet (v2.7 chunked transfer).
     * 
     * <p>Receives a chunk of map data and assembles when complete.</p>
     * 
     * @param data Map chunk JSON object
     */
    private static void handleHunterMapChunk(JsonObject data) {
        String areaId = data.has("areaId") ? data.get("areaId").getAsString() : null;
        int chunkIndex = data.has("chunkIndex") ? data.get("chunkIndex").getAsInt() : -1;
        int totalChunks = data.has("totalChunks") ? data.get("totalChunks").getAsInt() : 0;
        String chunkData = data.has("data") ? data.get("data").getAsString() : null;
        
        // 유효성 검사
        if (areaId == null || chunkData == null || chunkIndex < 0) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Invalid map chunk received");
            return;
        }
        
        // 현재 대기 중인 맵과 일치하는지 확인
        if (!areaId.equals(pendingMapAreaId) || pendingChunks == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Unexpected map chunk: expected={}, got={}", 
                pendingMapAreaId, areaId);
            return;
        }
        
        if (chunkIndex >= pendingChunks.length) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Chunk index out of bounds: {} >= {}", 
                chunkIndex, pendingChunks.length);
            return;
        }
        
        // 청크 저장
        pendingChunks[chunkIndex] = chunkData;
        receivedChunks++;
        
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Map chunk {}/{} received", receivedChunks, totalChunks);
        
        // 모든 청크 수신 완료 시 조합
        if (receivedChunks >= totalChunks) {
            assembleAndLoadMap();
        }
    }
    
    /**
     * Assembles all received chunks and loads the map.
     */
    private static void assembleAndLoadMap() {
        if (pendingChunks == null || pendingMapAreaId == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] No pending map data to assemble");
            return;
        }
        
        // 모든 청크 조합
        StringBuilder sb = new StringBuilder();
        for (String chunk : pendingChunks) {
            if (chunk == null) {
                TycoonHudMod.LOGGER.warn("[TycoonHUD] Missing chunk in map data");
                clearPendingMap();
                return;
            }
            sb.append(chunk);
        }
        
        String fullBase64 = sb.toString();
        TycoonHudMod.LOGGER.info("[TycoonHUD] Map assembled: {} chunks, {}KB total",
            pendingChunks.length, fullBase64.length() / 1024);
        
        // HunterMapData 생성
        kr.bapuri.tycoonhud.model.HunterMapData mapData = new kr.bapuri.tycoonhud.model.HunterMapData();
        mapData.setAreaId(pendingMapAreaId);
        mapData.setCenterX(pendingMapCenterX);
        mapData.setCenterZ(pendingMapCenterZ);
        mapData.setRadius(pendingMapRadius);
        mapData.setResolution(pendingMapResolution);
        mapData.setFormat(pendingMapFormat);
        mapData.setMapData(fullBase64);
        
        // 데이터 저장 및 텍스처 로드
        PlayerDataManager.getInstance().setHunterMap(mapData);
        kr.bapuri.tycoonhud.hud.HunterHudOverlay.onMapDataReceived(mapData);
        
        TycoonHudMod.LOGGER.info("[TycoonHUD] Hunter map loaded: area={}, center=({},{}), radius={}, resolution={}",
            pendingMapAreaId, pendingMapCenterX, pendingMapCenterZ, 
            pendingMapRadius, pendingMapResolution);
        
        // 버퍼 정리
        clearPendingMap();
    }
    
    /**
     * Clears pending map data.
     */
    private static void clearPendingMap() {
        pendingMapAreaId = null;
        pendingChunks = null;
        receivedChunks = 0;
    }
    
    /**
     * Handles HUNTER_MAP_UPDATE packet.
     * 
     * <p>Receives block change updates for the game area map.</p>
     * <p>This is sent every 0.5 seconds when blocks change.</p>
     * 
     * @param data Hunter map update JSON object
     */
    private static void handleHunterMapUpdate(JsonObject data) {
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Received HUNTER_MAP_UPDATE packet");
        
        kr.bapuri.tycoonhud.model.HunterMapData updateData = GSON.fromJson(data, kr.bapuri.tycoonhud.model.HunterMapData.class);
        
        if (updateData == null || updateData.getUpdates() == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse HUNTER_MAP_UPDATE");
            return;
        }
        
        // Apply updates to the texture
        kr.bapuri.tycoonhud.hud.HunterHudOverlay.applyMapUpdates(updateData.getUpdates());
        
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Hunter map updated: {} blocks", updateData.getUpdates().size());
    }
    
    /**
     * [2026-01-24] Handles PLOT_UPDATE packet.
     * 경량 패킷으로 플롯 정보만 빠르게 업데이트합니다.
     * 
     * @param data Plot update JSON object
     */
    private static void handlePlotUpdate(JsonObject data) {
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Received PLOT_UPDATE packet");
        
        // 현재 프로필 가져오기
        PlayerProfileData currentProfile = PlayerDataManager.getInstance().getProfile();
        if (currentProfile == null) {
            TycoonHudMod.LOGGER.debug("[TycoonHUD] No profile to update plot info");
            return;
        }
        
        // PlotInfo 파싱
        PlotInfo plotInfo = GSON.fromJson(data, PlotInfo.class);
        if (plotInfo == null) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to parse PLOT_UPDATE");
            return;
        }
        
        // 월드 정보 업데이트
        if (data.has("currentWorld")) {
            String world = data.get("currentWorld").getAsString();
            currentProfile.setCurrentWorld(world);
        }
        
        // 플롯 정보 업데이트
        currentProfile.setPlotInfo(plotInfo);
        
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Plot info updated: {}", plotInfo.getDisplayText());
    }
    
    // ========================================================================
    // 실시간 직업 업데이트 핸들러 (Phase 1)
    // ========================================================================
    
    /**
     * JOB_EXP_UPDATE 패킷 처리 (경험치 변경)
     * 
     * <p>서버에서 채굴/농사/낚시 시 실시간으로 전송됩니다.</p>
     */
    private static void handleJobExpUpdate(JsonObject data) {
        try {
            String jobType = data.has("jobType") ? data.get("jobType").getAsString() : null;
            int level = data.has("level") ? data.get("level").getAsInt() : 0;
            long currentXp = data.has("currentXp") ? data.get("currentXp").getAsLong() : 0;
            long nextLevelXp = data.has("nextLevelXp") ? data.get("nextLevelXp").getAsLong() : 100;
            
            if (jobType == null) {
                TycoonHudMod.LOGGER.debug("[TycoonHUD] JOB_EXP_UPDATE: missing jobType");
                return;
            }
            
            PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
            if (profile != null && profile.getPrimaryJob() != null 
                && jobType.equals(profile.getPrimaryJob().getType())) {
                // 현재 경험치 업데이트
                profile.getPrimaryJob().setCurrentXp(currentXp);
                profile.getPrimaryJob().setNextLevelXp(nextLevelXp);
                profile.getPrimaryJob().setLevel(level);
            }
            
            TycoonHudMod.LOGGER.debug("[TycoonHUD] JOB_EXP_UPDATE: {} Lv.{} ({}/{})", 
                jobType, level, currentXp, nextLevelXp);
        } catch (Exception e) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to handle JOB_EXP_UPDATE: {}", e.getMessage());
        }
    }
    
    /**
     * JOB_LEVEL_UP 패킷 처리 (레벨업 알림)
     */
    private static void handleJobLevelUp(JsonObject data) {
        try {
            String jobType = data.has("jobType") ? data.get("jobType").getAsString() : null;
            int newLevel = data.has("newLevel") ? data.get("newLevel").getAsInt() : 1;
            long nextLevelXp = data.has("nextLevelXp") ? data.get("nextLevelXp").getAsLong() : 100;
            
            if (jobType == null) return;
            
            PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
            if (profile != null && profile.getPrimaryJob() != null 
                && jobType.equals(profile.getPrimaryJob().getType())) {
                profile.getPrimaryJob().setLevel(newLevel);
                profile.getPrimaryJob().setCurrentXp(0);
                profile.getPrimaryJob().setNextLevelXp(nextLevelXp);
            }
            
            // [Phase 5] 토스트 메시지 표시
            kr.bapuri.tycoonhud.hud.ToastManager.getInstance().show(
                "🎉 레벨 업!", 
                "Lv." + newLevel + " 달성",
                kr.bapuri.tycoonhud.hud.ToastManager.ToastType.SUCCESS
            );
            TycoonHudMod.LOGGER.info("[TycoonHUD] Level up! {} -> Lv.{}", jobType, newLevel);
        } catch (Exception e) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to handle JOB_LEVEL_UP: {}", e.getMessage());
        }
    }
    
    /**
     * JOB_GRADE_UP 패킷 처리 (승급 알림)
     */
    private static void handleJobGradeUp(JsonObject data) {
        try {
            String jobType = data.has("jobType") ? data.get("jobType").getAsString() : null;
            int newGrade = data.has("newGrade") ? data.get("newGrade").getAsInt() : 1;
            String gradeTitle = data.has("gradeTitle") ? data.get("gradeTitle").getAsString() : "숙련";
            
            if (jobType == null) return;
            
            PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
            if (profile != null && profile.getPrimaryJob() != null 
                && jobType.equals(profile.getPrimaryJob().getType())) {
                profile.getPrimaryJob().setGrade(newGrade);
                profile.getPrimaryJob().setGradeTitle(gradeTitle);
            }
            
            // [Phase 5] 토스트 메시지 표시
            kr.bapuri.tycoonhud.hud.ToastManager.getInstance().show(
                "🏆 " + gradeTitle + " 승급!",
                newGrade + "차 등급 달성",
                kr.bapuri.tycoonhud.hud.ToastManager.ToastType.WARNING
            );
            TycoonHudMod.LOGGER.info("[TycoonHUD] Grade up! {} -> {} ({}차)", jobType, gradeTitle, newGrade);
        } catch (Exception e) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to handle JOB_GRADE_UP: {}", e.getMessage());
        }
    }
    
    // ========================================================================
    // 도감 피드백 핸들러 (Phase 1)
    // ========================================================================
    
    /**
     * CODEX_REGISTER_RESULT 패킷 처리 (등록 결과)
     * 
     * <p>모드 UI에서 도감 등록 시도 시 결과를 반환합니다.</p>
     */
    private static void handleCodexRegisterResult(JsonObject data) {
        try {
            boolean success = data.has("success") && data.get("success").getAsBoolean();
            String material = data.has("material") ? data.get("material").getAsString() : "unknown";
            
            if (success) {
                String displayName = data.has("displayName") ? data.get("displayName").getAsString() : material;
                long reward = data.has("reward") ? data.get("reward").getAsLong() : 0;
                int newCollected = data.has("newCollected") ? data.get("newCollected").getAsInt() : 0;
                int totalCount = data.has("totalCount") ? data.get("totalCount").getAsInt() : 0;
                
                // [Phase 5] 토스트 메시지 표시
                kr.bapuri.tycoonhud.hud.ToastManager.getInstance().show(
                    "📖 도감 등록!",
                    displayName + " +" + reward + "BC",
                    kr.bapuri.tycoonhud.hud.ToastManager.ToastType.INFO
                );
                TycoonHudMod.LOGGER.info("[TycoonHUD] Codex registered: {} (+{} BC) [{}/{}]", 
                    displayName, reward, newCollected, totalCount);
            } else {
                String failReason = data.has("failReason") ? data.get("failReason").getAsString() : "알 수 없는 오류";
                
                // [Phase 5] 오류 토스트 표시
                String failMsg = switch (failReason) {
                    case "already_registered" -> "이미 등록됨";
                    case "not_in_codex" -> "도감 대상 아님";
                    case "not_enough_count" -> "아이템 부족";
                    default -> failReason;
                };
                kr.bapuri.tycoonhud.hud.ToastManager.getInstance().show(
                    "도감 등록 실패",
                    failMsg,
                    kr.bapuri.tycoonhud.hud.ToastManager.ToastType.ERROR
                );
                TycoonHudMod.LOGGER.info("[TycoonHUD] Codex registration failed: {} - {}", material, failReason);
            }
            
            // tycoon-ui로 포워딩하여 UI 업데이트
            try {
                Class<?> handlerClass = Class.forName("kr.bapuri.tycoonui.net.UiResponseHandler");
                java.lang.reflect.Method method = handlerClass.getMethod("handleCodexRegisterResult", JsonObject.class);
                method.invoke(null, data);
            } catch (ClassNotFoundException e) {
                // tycoon-ui not installed - ignore
            } catch (Exception e) {
                TycoonHudMod.LOGGER.debug("[TycoonHUD] Failed to forward CODEX_REGISTER_RESULT: {}", e.getMessage());
            }
        } catch (Exception e) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to handle CODEX_REGISTER_RESULT: {}", e.getMessage());
        }
    }
    
    /**
     * CODEX_ITEM_REGISTERED 패킷 처리 (서버에서 등록 시)
     * 
     * <p>서버 명령어/GUI로 도감 등록 시 클라이언트에 알립니다.</p>
     */
    private static void handleCodexItemRegistered(JsonObject data) {
        try {
            String material = data.has("material") ? data.get("material").getAsString() : "unknown";
            String displayName = data.has("displayName") ? data.get("displayName").getAsString() : material;
            long reward = data.has("reward") ? data.get("reward").getAsLong() : 0;
            int newCollected = data.has("newCollected") ? data.get("newCollected").getAsInt() : 0;
            int totalCount = data.has("totalCount") ? data.get("totalCount").getAsInt() : 0;
            
            // TODO: Phase 5에서 토스트 메시지 표시
            TycoonHudMod.LOGGER.info("[TycoonHUD] Codex item registered (from server): {} (+{} BC) [{}/{}]", 
                displayName, reward, newCollected, totalCount);
            
            // tycoon-ui로 포워딩하여 캐시 무효화
            try {
                Class<?> handlerClass = Class.forName("kr.bapuri.tycoonui.net.UiResponseHandler");
                java.lang.reflect.Method method = handlerClass.getMethod("handleCodexItemRegistered", JsonObject.class);
                method.invoke(null, data);
            } catch (ClassNotFoundException e) {
                // tycoon-ui not installed - ignore
            } catch (Exception e) {
                TycoonHudMod.LOGGER.debug("[TycoonHUD] Failed to forward CODEX_ITEM_REGISTERED: {}", e.getMessage());
            }
        } catch (Exception e) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Failed to handle CODEX_ITEM_REGISTERED: {}", e.getMessage());
        }
    }
}
