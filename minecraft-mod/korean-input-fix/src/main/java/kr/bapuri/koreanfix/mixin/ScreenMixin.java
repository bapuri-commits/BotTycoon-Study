package kr.bapuri.koreanfix.mixin;

import kr.bapuri.koreanfix.KoreanInputFixMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screen.Screen;

@Mixin(Screen.class)
public class ScreenMixin {
    // Mixin은 화면 이벤트 감지만 담당
    // 실제 IME 제어는 KoreanInputFixMod의 ClientTickEvents에서 담당
    // (중복 호출 방지를 위해 여기서는 로그만 출력)

    @Inject(method = "init", at = @At("TAIL"))
    private void onScreenInit(CallbackInfo ci){
        // 화면 초기화 이벤트 감지 (로그 전용)
        KoreanInputFixMod.LOGGER.debug("화면 init: {}", this.getClass().getSimpleName());
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onScreenRemoved(CallbackInfo ci){
        // 화면 닫힘 이벤트 감지 (로그 전용)
        KoreanInputFixMod.LOGGER.debug("화면 removed: {}", this.getClass().getSimpleName());
    }

}
