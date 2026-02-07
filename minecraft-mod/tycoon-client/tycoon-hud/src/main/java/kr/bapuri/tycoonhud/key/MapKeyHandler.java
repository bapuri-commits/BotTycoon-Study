package kr.bapuri.tycoonhud.key;

import kr.bapuri.tycoonhud.hud.HunterHudOverlay;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.model.VitalData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * MapKeyHandler - M키 토글로 큰 맵 표시 + 줌 조절 + 마우스 드래그
 * 
 * 헌터 월드에서:
 * - M 키: 큰 맵 토글
 * - ESC 키: 큰 맵 닫기
 * - [ 키: 미니맵 줌아웃 (넓은 범위)
 * - ] 키: 미니맵 줌인 (좁은 범위)
 * - \ 키: 줌 리셋 (1.0x)
 * - 마우스 드래그: 큰 맵 이동
 * - 마우스 스크롤: 큰 맵 줌
 */
public class MapKeyHandler {

    private static boolean wasMapPressed = false;
    // ESC 처리는 MinecraftClientMixin에서 담당 (openPauseMenu 가로채기)
    private static boolean wasZoomInPressed = false;
    private static boolean wasZoomOutPressed = false;
    private static boolean wasResetPressed = false;
    
    // 마우스 드래그 상태
    private static boolean wasMouseDown = false;
    private static double lastMouseX = 0;
    private static double lastMouseY = 0;
    private static double[] mouseX = new double[1];
    private static double[] mouseY = new double[1];
    
    // 스크롤 상태 (GLFW 콜백으로 처리해야 하므로 별도 변수)
    private static double scrollDelta = 0;
    private static boolean scrollCallbackRegistered = false;

    /**
     * 키 핸들러 등록
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MapKeyHandler::onTick);
    }
    
    /**
     * 스크롤 콜백 등록 (GLFW)
     */
    private static void registerScrollCallback(long windowHandle) {
        if (scrollCallbackRegistered) return;
        
        GLFW.glfwSetScrollCallback(windowHandle, (window, xOffset, yOffset) -> {
            // 큰 맵이 열려있을 때만 스크롤 처리
            if (HunterHudOverlay.isBigMapOpen()) {
                scrollDelta += yOffset;
            }
        });
        scrollCallbackRegistered = true;
    }

    /**
     * 매 틱마다 호출
     */
    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        // 헌터 월드 체크
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        VitalData vital = PlayerDataManager.getInstance().getVital();
        
        if (profile == null || !profile.isInHunter() || vital == null || !vital.isHunterMode()) {
            // 헌터 월드 아니면 큰 맵 닫기
            if (HunterHudOverlay.isBigMapOpen()) {
                HunterHudOverlay.toggleBigMap();
            }
            return;
        }

        long windowHandle = client.getWindow().getHandle();
        
        // 스크롤 콜백 등록 (한 번만)
        registerScrollCallback(windowHandle);
        
        // M 키: 큰 맵 토글
        boolean isMapPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_M);
        if (isMapPressed && !wasMapPressed && client.currentScreen == null) {
            HunterHudOverlay.toggleBigMap();
            // 맵 열릴 때 뷰 리셋
            if (HunterHudOverlay.isBigMapOpen()) {
                HunterHudOverlay.resetBigMapView();
            }
        }
        wasMapPressed = isMapPressed;
        
        // ESC 키 처리는 MinecraftClientMixin에서 담당 (openPauseMenu 가로채기)
        
        // ] 키: 줌인 (GLFW_KEY_RIGHT_BRACKET)
        boolean isZoomInPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_BRACKET);
        if (isZoomInPressed && !wasZoomInPressed && client.currentScreen == null) {
            HunterHudOverlay.zoomIn();
            showZoomMessage(client, "줌인", HunterHudOverlay.getZoomLevel());
        }
        wasZoomInPressed = isZoomInPressed;
        
        // [ 키: 줌아웃 (GLFW_KEY_LEFT_BRACKET)
        boolean isZoomOutPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_BRACKET);
        if (isZoomOutPressed && !wasZoomOutPressed && client.currentScreen == null) {
            HunterHudOverlay.zoomOut();
            showZoomMessage(client, "줌아웃", HunterHudOverlay.getZoomLevel());
        }
        wasZoomOutPressed = isZoomOutPressed;
        
        // \ 키: 줌 리셋 (GLFW_KEY_BACKSLASH)
        boolean isResetPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_BACKSLASH);
        if (isResetPressed && !wasResetPressed && client.currentScreen == null) {
            HunterHudOverlay.resetZoom();
            showZoomMessage(client, "줌 리셋", 1.0f);
        }
        wasResetPressed = isResetPressed;
        
        // ========== 큰 맵 마우스 드래그/스크롤 처리 ==========
        if (HunterHudOverlay.isBigMapOpen()) {
            handleBigMapMouseInput(client, windowHandle);
        } else {
            // 맵이 닫히면 드래그 상태 초기화
            wasMouseDown = false;
        }
    }
    
    /**
     * 큰 맵 마우스 입력 처리
     */
    private static void handleBigMapMouseInput(MinecraftClient client, long windowHandle) {
        // 마우스 위치 가져오기
        GLFW.glfwGetCursorPos(windowHandle, mouseX, mouseY);
        double currentMouseX = mouseX[0];
        double currentMouseY = mouseY[0];
        
        // 왼쪽 마우스 버튼 상태
        boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        
        if (isMouseDown) {
            if (wasMouseDown) {
                // 드래그 중: 이동량 계산
                double deltaX = currentMouseX - lastMouseX;
                double deltaY = currentMouseY - lastMouseY;
                
                if (deltaX != 0 || deltaY != 0) {
                    HunterHudOverlay.dragBigMap((float) deltaX, (float) deltaY);
                }
            }
            // 현재 위치 저장
            lastMouseX = currentMouseX;
            lastMouseY = currentMouseY;
        }
        wasMouseDown = isMouseDown;
        
        // 스크롤 처리
        if (scrollDelta != 0) {
            if (scrollDelta > 0) {
                HunterHudOverlay.zoomBigMapIn();
            } else {
                HunterHudOverlay.zoomBigMapOut();
            }
            scrollDelta = 0;
        }
    }
    
    /**
     * 줌 변경 메시지 표시
     */
    private static void showZoomMessage(MinecraftClient client, String action, float zoomLevel) {
        if (client.player != null) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal(String.format("§e[미니맵] %s: §f%.2fx", action, zoomLevel)),
                true  // actionBar
            );
        }
    }
}

