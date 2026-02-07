package kr.bapuri.tycoonui.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import kr.bapuri.tycoonui.TycoonUiMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 서버에 UI 요청을 전송하는 유틸리티 클래스입니다.
 * 
 * <h3>요청 채널</h3>
 * <p>{@code tycoon:ui_request}</p>
 * 
 * <h3>요청 포맷</h3>
 * <pre>
 * {
 *     "action": "REQUEST_CODEX_DATA",
 *     "params": {}
 * }
 * </pre>
 */
public class UiRequestSender {
    
    /** 요청 채널 ID */
    public static final Identifier CHANNEL_ID = new Identifier("tycoon", "ui_request");
    
    private static final Gson GSON = new Gson();
    
    /**
     * 초기화
     */
    public static void init() {
        // 특별한 초기화 필요 없음
        TycoonUiMod.LOGGER.info("[TycoonUI] Request sender initialized");
    }
    
    /**
     * 도감 요약 데이터를 요청합니다 (카테고리 정보만, items 없음).
     */
    public static void requestCodexData() {
        sendRequest("REQUEST_CODEX_SUMMARY", null);
    }
    
    /**
     * 특정 카테고리의 아이템 목록을 요청합니다.
     * 
     * @param categoryName 카테고리 이름 (한글, 예: "광물")
     */
    public static void requestCodexCategory(String categoryName) {
        JsonObject params = new JsonObject();
        params.addProperty("category", categoryName);
        sendRequest("REQUEST_CODEX_CATEGORY", params);
        TycoonUiMod.LOGGER.debug("[TycoonUI] Codex category requested: {}", categoryName);
    }
    
    /**
     * 경제 거래 내역을 요청합니다.
     */
    public static void requestEconomyHistory() {
        sendRequest("REQUEST_ECONOMY_HISTORY", null);
    }
    
    /**
     * 직업 상세 정보를 요청합니다.
     */
    public static void requestJobDetail() {
        sendRequest("REQUEST_JOB_DETAIL", null);
    }
    
    /**
     * 직업 승급을 시도합니다.
     */
    public static void triggerJobPromotion() {
        sendRequest("TRIGGER_JOB_PROMOTION", null);
        TycoonUiMod.LOGGER.info("[TycoonUI] Promotion request sent");
    }
    
    /**
     * [2026-01-24] 도감 아이템 등록을 시도합니다.
     * 
     * @param materialName Bukkit Material 이름 (예: "DIAMOND", "IRON_INGOT")
     */
    public static void registerCodexItem(String materialName) {
        JsonObject params = new JsonObject();
        params.addProperty("material", materialName);
        sendRequest("REGISTER_CODEX_ITEM", params);
        TycoonUiMod.LOGGER.info("[TycoonUI] Codex registration request: {}", materialName);
    }
    
    /**
     * 요청을 전송합니다.
     * 
     * @param action 액션명
     * @param params 추가 파라미터 (nullable)
     */
    private static void sendRequest(String action, JsonObject params) {
        // 서버에 연결되어 있는지 확인
        if (!ClientPlayNetworking.canSend(CHANNEL_ID)) {
            TycoonUiMod.LOGGER.warn("[TycoonUI] Not connected to server or channel not registered: {}", CHANNEL_ID);
            return;
        }
        
        // JSON 생성
        JsonObject request = new JsonObject();
        request.addProperty("action", action);
        request.add("params", params != null ? params : new JsonObject());
        
        String json = GSON.toJson(request);
        
        // 패킷 전송
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(json);
        
        ClientPlayNetworking.send(CHANNEL_ID, buf);
        TycoonUiMod.LOGGER.debug("[TycoonUI] Request sent: {}", action);
    }
}

