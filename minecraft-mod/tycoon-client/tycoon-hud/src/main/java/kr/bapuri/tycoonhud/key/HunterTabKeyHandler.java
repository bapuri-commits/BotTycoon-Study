package kr.bapuri.tycoonhud.key;

import kr.bapuri.tycoonhud.hud.HunterTabOverlay;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.model.VitalData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * HunterTabKeyHandler - TAB 키 감지
 * 
 * 헌터 월드에서 TAB 키를 홀드하면:
 * - 바닐라 플레이어 리스트 대신 커스텀 순위 오버레이 표시
 * - 키를 떼면 오버레이 숨김
 */
public class HunterTabKeyHandler {

    /**
     * 키 핸들러 등록
     */
    public static void register() {
        // 틱 이벤트 등록
        ClientTickEvents.END_CLIENT_TICK.register(HunterTabKeyHandler::onTick);
    }

    /**
     * 매 틱마다 호출
     */
    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            HunterTabOverlay.setTabPressed(false);
            return;
        }

        // 헌터 월드 체크
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        VitalData vital = PlayerDataManager.getInstance().getVital();
        
        if (profile == null || !profile.isInHunter() || vital == null || !vital.isHunterMode()) {
            HunterTabOverlay.setTabPressed(false);
            return;
        }

        // TAB 키 상태 확인 (바닐라 키바인딩 사용)
        KeyBinding tabKey = client.options.playerListKey;
        boolean isTabPressed = tabKey.isPressed();
        
        // 또는 직접 GLFW로 확인
        // boolean isTabPressed = InputUtil.isKeyPressed(
        //     client.getWindow().getHandle(),
        //     GLFW.GLFW_KEY_TAB
        // );

        HunterTabOverlay.setTabPressed(isTabPressed);
    }
}

