package kr.bapuri.tycoonui.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import kr.bapuri.tycoonui.TycoonUiMod;
import kr.bapuri.tycoonui.model.CodexData;
import kr.bapuri.tycoonui.model.EconomyHistory;
import kr.bapuri.tycoonui.model.JobDetail;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 서버로부터 UI 관련 응답을 수신하는 핸들러입니다.
 * 
 * <p>tycoon:ui_data 채널에서 추가 패킷 타입을 처리합니다:</p>
 * <ul>
 *     <li>CODEX_DATA</li>
 *     <li>ECONOMY_HISTORY</li>
 *     <li>JOB_DETAIL</li>
 * </ul>
 * 
 * <p>참고: 기본 패킷(PLAYER_PROFILE, VITAL_UPDATE 등)은 
 * tycoon-hud의 UiDataReceiver에서 처리됩니다.</p>
 */
public class UiResponseHandler {
    
    /** 
     * 수신 채널 ID (tycoon-hud와 동일)
     * 같은 채널을 공유하며, 타입별로 분기 처리됩니다.
     */
    public static final Identifier CHANNEL_ID = new Identifier("tycoon", "ui_data");
    
    private static final Gson GSON = new Gson();
    
    /**
     * 응답 핸들러를 등록합니다.
     */
    public static void register() {
        TycoonUiMod.LOGGER.info("[TycoonUI] Response handler initialized");
    }
    
    /**
     * UI 전용 패킷을 처리합니다.
     * 
     * <p>tycoon-hud의 UiDataReceiver에서 호출됩니다.</p>
     * 
     * @param type 패킷 타입
     * @param data 데이터 JSON
     */
    public static void handlePacket(String type, JsonObject data) {
        MinecraftClient.getInstance().execute(() -> {
            try {
                switch (type) {
                    case "CODEX_DATA" -> handleCodexData(data);
                    case "CODEX_CATEGORY_DATA" -> handleCodexCategoryData(data);
                    case "ECONOMY_HISTORY" -> handleEconomyHistory(data);
                    case "JOB_DETAIL" -> handleJobDetail(data);
                    default -> TycoonUiMod.LOGGER.debug("[TycoonUI] Unknown packet type: {}", type);
                }
            } catch (Exception e) {
                TycoonUiMod.LOGGER.error("[TycoonUI] Error processing packet: {}", e.getMessage(), e);
            }
        });
    }
    
    private static void handleCodexData(JsonObject data) {
        CodexData codex = GSON.fromJson(data, CodexData.class);
        UiDataHolder.getInstance().setCodexData(codex);
        TycoonUiMod.LOGGER.debug("[TycoonUI] Codex summary received: {} categories", 
            codex.getCategories().size());
    }
    
    /**
     * 카테고리별 아이템 데이터 처리
     * 
     * 패킷 구조:
     * {
     *     "category": "광물",
     *     "items": [
     *         {"id": "DIAMOND", "name": "다이아몬드", "collected": true, ...},
     *         ...
     *     ]
     * }
     */
    private static void handleCodexCategoryData(JsonObject data) {
        String categoryName = data.get("category").getAsString();
        JsonArray itemsArray = data.getAsJsonArray("items");
        
        List<CodexData.Item> items = new ArrayList<>();
        for (int i = 0; i < itemsArray.size(); i++) {
            CodexData.Item item = GSON.fromJson(itemsArray.get(i), CodexData.Item.class);
            items.add(item);
        }
        
        UiDataHolder.getInstance().setCategoryItems(categoryName, items);
        TycoonUiMod.LOGGER.debug("[TycoonUI] Codex category items received: {} ({} items)", 
            categoryName, items.size());
    }
    
    private static void handleEconomyHistory(JsonObject data) {
        EconomyHistory history = GSON.fromJson(data, EconomyHistory.class);
        UiDataHolder.getInstance().setEconomyHistory(history);
        TycoonUiMod.LOGGER.debug("[TycoonUI] Economy history received");
    }
    
    private static void handleJobDetail(JsonObject data) {
        JobDetail detail = GSON.fromJson(data, JobDetail.class);
        UiDataHolder.getInstance().setJobDetail(detail);
        TycoonUiMod.LOGGER.debug("[TycoonUI] Job detail received");
    }
}
