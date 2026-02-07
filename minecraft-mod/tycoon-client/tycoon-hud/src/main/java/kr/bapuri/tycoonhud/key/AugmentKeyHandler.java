package kr.bapuri.tycoonhud.key;

import kr.bapuri.tycoonhud.model.AugmentData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import kr.bapuri.tycoonhud.net.TycoonClientState;
import kr.bapuri.tycoonhud.screen.AugmentSelectionScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 증강 선택 키 핸들러
 * G 키로 증강 선택 창 열기
 */
public class AugmentKeyHandler {
    
    private static KeyBinding augmentKey;
    
    public static void register() {
        augmentKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tycoon.augment",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category.tycoon"
        ));
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (augmentKey.wasPressed()) {
                handleAugmentKey(client);
            }
        });
    }
    
    private static void handleAugmentKey(MinecraftClient client) {
        // 이미 GUI가 열려있으면 무시
        if (client.currentScreen != null) {
            return;
        }
        
        // 플레이어 없으면 무시
        if (client.player == null) {
            return;
        }
        
        AugmentData augmentData = PlayerDataManager.getInstance().getAugmentData();
        
        // Case 1: 서버에서 이미 선택지를 보냈으면 UI 열기
        if (augmentData != null && augmentData.isShowSelection() 
            && augmentData.getChoices() != null && !augmentData.getChoices().isEmpty()) {
            client.setScreen(new AugmentSelectionScreen(augmentData));
            return;
        }
        
        // Case 2: 증강 대기 중 (알림만 온 상태) → 서버에 요청
        if (augmentData != null && augmentData.isAugmentPending()) {
            TycoonClientState.sendAugmentOpenRequest();
            return;
        }
        
        // Case 3: 증강 선택 대기 중이 아니면 아무것도 안 함
    }
}
