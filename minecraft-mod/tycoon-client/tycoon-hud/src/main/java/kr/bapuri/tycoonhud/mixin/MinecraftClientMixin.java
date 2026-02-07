package kr.bapuri.tycoonhud.mixin;

import kr.bapuri.tycoonhud.hud.HunterHudOverlay;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MinecraftClient Mixin - ESC 키 처리
 * 
 * 큰 맵(M키)이 열려있을 때 ESC를 누르면:
 * - 설정 창을 열지 않고
 * - 큰 맵만 닫음
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    
    /**
     * openPauseMenu 호출 시 큰 맵이 열려있으면 취소
     */
    @Inject(method = "openPauseMenu", at = @At("HEAD"), cancellable = true)
    private void tycoonhud$handleBigMapEsc(boolean pause, CallbackInfo ci) {
        if (HunterHudOverlay.isBigMapOpen()) {
            // 큰 맵 닫기
            HunterHudOverlay.toggleBigMap();
            // 설정 창 열기 취소
            ci.cancel();
        }
    }
}

