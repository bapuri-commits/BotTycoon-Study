package kr.bapuri.koreanfix.mixin;

import kr.bapuri.koreanfix.config.ModConfig;
import kr.bapuri.koreanfix.ime.WindowsIme;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 키보드 입력 이벤트를 후킹하여 게임 플레이 중 IME를 제어합니다.
 * 
 * <h2>목적</h2>
 * <p>게임 플레이 중 한/영 키가 눌려 한글 모드가 되면,
 * WASD 등의 키 입력이 IME에 가로채져 게임 조작이 안 됩니다.
 * 이를 방지하기 위해 키 입력 시 IME 상태를 확인하고 영문으로 전환합니다.</p>
 * 
 * <h2>최적화 포인트</h2>
 * <ul>
 *   <li>매 틱 폴링 대신 키 입력 시에만 처리 (효율성 ↑)</li>
 *   <li>WindowsIme의 캐싱 활용</li>
 * </ul>
 */
@Mixin(Keyboard.class)
public class KeyboardMixin {

    /**
     * Keyboard.onKey() 메서드의 시작 부분에 주입됩니다.
     */
    @Inject(method = "onKey", at = @At("HEAD"))
    private void koreanfix_onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // 모드가 비활성화되어 있으면 무시
        if (!ModConfig.get().enabled) return;
        
        // 키 누름(PRESS)이 아니면 무시
        if (action != GLFW.GLFW_PRESS) return;

        // 화면이 열려있으면 무시 (채팅, 인벤토리 등에서는 한글 허용)
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) return;

        // 게임 플레이 중 → IME가 한글 모드면 영문으로 전환
        WindowsIme.disableImeSilent();
    }
}
