package kr.bapuri.tycoonhud.net;

import kr.bapuri.tycoonhud.TycoonHudMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BotTycoon 서버 연결 상태를 관리하는 클래스입니다.
 * 
 * <h3>기능</h3>
 * <ul>
 *     <li>서버 연결 상태 추적</li>
 *     <li>초기 데이터 요청 (연결 직후)</li>
 *     <li>연결 해제 시 데이터 초기화</li>
 * </ul>
 */
public class TycoonClientState {
    
    /** 요청 채널 ID */
    public static final Identifier REQUEST_CHANNEL = new Identifier("tycoon", "ui_request");
    
    /** 서버 연결 상태 */
    private static final AtomicBoolean connected = new AtomicBoolean(false);
    
    /** 채널 등록 여부 */
    private static final AtomicBoolean channelRegistered = new AtomicBoolean(false);
    
    private TycoonClientState() {}
    
    /**
     * 연결 이벤트 핸들러를 등록합니다.
     */
    public static void register() {
        // 서버 연결 시
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TycoonHudMod.LOGGER.info("[TycoonHUD] Connected to server, checking for BotTycoon support...");
            connected.set(false);
            channelRegistered.set(false);
            
            // 채널 등록 확인을 위해 약간의 지연 후 초기 데이터 요청
            client.execute(() -> {
                // 채널 등록 확인
                if (ClientPlayNetworking.canSend(REQUEST_CHANNEL)) {
                    channelRegistered.set(true);
                    connected.set(true);
                    TycoonHudMod.LOGGER.info("[TycoonHUD] BotTycoon server detected, requesting initial data...");
                    requestInitialData();
                } else {
                    TycoonHudMod.LOGGER.debug("[TycoonHUD] Not a BotTycoon server (channel not registered)");
                }
            });
        });
        
        // 서버 연결 해제 시
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TycoonHudMod.LOGGER.info("[TycoonHUD] Disconnected from server, clearing data...");
            connected.set(false);
            channelRegistered.set(false);
            PlayerDataManager.getInstance().clear();
        });
        
        TycoonHudMod.LOGGER.info("[TycoonHUD] Client state manager registered");
    }
    
    /**
     * 서버에 초기 데이터를 요청합니다.
     */
    public static void requestInitialData() {
        if (!canSend()) {
            TycoonHudMod.LOGGER.debug("[TycoonHUD] Cannot request initial data: channel not available");
            return;
        }
        
        // PROFILE 요청 (VITAL_UPDATE도 함께 전송됨)
        sendRequest("PROFILE");
        
        // VITAL 요청 (명시적)
        sendRequest("VITAL");
        
        TycoonHudMod.LOGGER.debug("[TycoonHUD] Initial data requested");
    }
    
    /**
     * 서버에 특정 타입의 데이터를 요청합니다.
     */
    public static void sendRequest(String requestType) {
        if (!canSend()) {
            return;
        }
        
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            String json = String.format("{\"type\":\"%s\"}", requestType);
            buf.writeString(json);
            ClientPlayNetworking.send(REQUEST_CHANNEL, buf);
            TycoonHudMod.LOGGER.debug("[TycoonHUD] Request sent: {}", requestType);
        } catch (Exception e) {
            TycoonHudMod.LOGGER.error("[TycoonHUD] Failed to send request: {}", e.getMessage());
        }
    }
    
    /**
     * 증강 선택 결과를 서버에 전송합니다.
     * 
     * @param choiceIndex 선택한 증강의 인덱스 (0, 1, 2)
     */
    public static void sendAugmentSelection(int choiceIndex) {
        if (!canSend()) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Cannot send augment selection: channel not available");
            return;
        }
        
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            // 서버 ModDataService 형식에 맞게 params 객체 사용
            String json = String.format("{\"type\":\"AUGMENT_SELECT\",\"params\":{\"index\":%d}}", choiceIndex);
            buf.writeString(json);
            ClientPlayNetworking.send(REQUEST_CHANNEL, buf);
            TycoonHudMod.LOGGER.info("[TycoonHUD] Augment selection sent: index={}", choiceIndex);
            
            // Clear augment data after selection
            PlayerDataManager.getInstance().clearAugmentData();
        } catch (Exception e) {
            TycoonHudMod.LOGGER.error("[TycoonHUD] Failed to send augment selection: {}", e.getMessage());
        }
    }
    
    /**
     * 증강 리롤 요청을 서버에 전송합니다.
     * 
     * @param choiceIndex 리롤할 증강의 인덱스 (0, 1, 2)
     */
    public static void sendAugmentReroll(int choiceIndex) {
        if (!canSend()) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Cannot send augment reroll: channel not available");
            return;
        }
        
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            // 서버 ModDataService 형식에 맞게 params 객체 사용
            String json = String.format("{\"type\":\"AUGMENT_REROLL\",\"params\":{\"index\":%d}}", choiceIndex);
            buf.writeString(json);
            ClientPlayNetworking.send(REQUEST_CHANNEL, buf);
            TycoonHudMod.LOGGER.info("[TycoonHUD] Augment reroll sent: index={}", choiceIndex);
        } catch (Exception e) {
            TycoonHudMod.LOGGER.error("[TycoonHUD] Failed to send augment reroll: {}", e.getMessage());
        }
    }
    
    /**
     * 증강 선택 UI 열기 요청을 서버에 전송합니다.
     * 관리자 권한이 필요합니다.
     */
    public static void sendAugmentOpenRequest() {
        if (!canSend()) {
            TycoonHudMod.LOGGER.warn("[TycoonHUD] Cannot send augment open request: channel not available");
            return;
        }
        
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            String json = "{\"type\":\"REQUEST_AUGMENT_OPEN\"}";
            buf.writeString(json);
            ClientPlayNetworking.send(REQUEST_CHANNEL, buf);
            TycoonHudMod.LOGGER.info("[TycoonHUD] Augment open request sent");
        } catch (Exception e) {
            TycoonHudMod.LOGGER.error("[TycoonHUD] Failed to send augment open request: {}", e.getMessage());
        }
    }
    
    /**
     * BotTycoon 서버에 연결되었는지 확인합니다.
     */
    public static boolean isConnected() {
        return connected.get() && channelRegistered.get();
    }
    
    /**
     * 채널로 패킷을 전송할 수 있는지 확인합니다.
     */
    public static boolean canSend() {
        return channelRegistered.get() && ClientPlayNetworking.canSend(REQUEST_CHANNEL);
    }
    
    /**
     * 연결 상태를 설정합니다.
     */
    public static void setConnected(boolean value) {
        connected.set(value);
        if (value) {
            channelRegistered.set(true);
        }
    }
}

