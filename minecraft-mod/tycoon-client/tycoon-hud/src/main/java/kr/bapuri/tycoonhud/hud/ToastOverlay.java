package kr.bapuri.tycoonhud.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * [Phase 5] 토스트 HUD 오버레이
 * 
 * <p>ToastManager의 토스트를 화면에 렌더링합니다.</p>
 */
public class ToastOverlay implements HudRenderCallback {
    
    @Override
    public void onHudRender(DrawContext drawContext, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        // 디버그 화면이나 HUD 숨김 시 토스트도 숨김
        if (client.options.hudHidden) return;
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        // 토스트 틱 업데이트
        ToastManager.getInstance().tick();
        
        // 토스트 렌더링
        ToastManager.getInstance().render(drawContext, screenWidth, screenHeight);
    }
}
