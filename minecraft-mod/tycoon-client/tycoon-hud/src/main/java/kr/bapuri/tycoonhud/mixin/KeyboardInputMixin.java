package kr.bapuri.tycoonhud.mixin;

import kr.bapuri.tycoonhud.hud.HunterHudOverlay;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeyboardInputMixin - 큰 맵 열림 시 이동 입력 차단
 * 
 * 큰 맵(M키)이 열려있을 때:
 * - WASD 이동 비활성화
 * - 점프/스니크 비활성화
 * - 마우스 드래그/스크롤은 맵 조작으로 사용
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    
    /**
     * tick() 메서드 끝에서 큰 맵이 열려있으면 모든 입력 초기화
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void tycoonhud$blockInputWhenBigMapOpen(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (HunterHudOverlay.isBigMapOpen()) {
            // 이동 입력 초기화
            KeyboardInput self = (KeyboardInput) (Object) this;
            self.movementForward = 0;
            self.movementSideways = 0;
            self.jumping = false;
            self.sneaking = false;
        }
    }
}
