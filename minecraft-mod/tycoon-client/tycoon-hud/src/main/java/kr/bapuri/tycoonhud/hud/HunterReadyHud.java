package kr.bapuri.tycoonhud.hud;

import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.model.ReadyStatusData;
import kr.bapuri.tycoonhud.model.VitalData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * HunterReadyHud - 레디 상태 HUD (좌상단)
 * 
 * 로비에서 HunterRankingHud 자리에 표시됩니다.
 * 
 * <h3>디자인</h3>
 * <ul>
 *     <li>미레디: 회색 연한 "READY"</li>
 *     <li>레디: 빨간색 역동적 "READY"</li>
 *     <li>하단: "X/Y 레디 완료"</li>
 * </ul>
 */
public class HunterReadyHud implements HudRenderCallback {

    // ================================================================================
    // 레이아웃
    // ================================================================================
    
    private static final int PADDING_TOP = 6;
    private static final int PADDING_LEFT = 6;
    private static final int BOX_WIDTH = 100;
    private static final int BOX_HEIGHT = 45;
    
    // ================================================================================
    // 색상
    // ================================================================================
    
    // 미레디 상태
    private static final int COLOR_UNREADY_TEXT = 0x60AAAAAA;     // 회색 연하게
    private static final int COLOR_UNREADY_BG = 0x30202020;       // 어두운 배경
    
    // 레디 상태
    private static final int COLOR_READY_TEXT = 0xFFFF3333;       // 빨간색
    private static final int COLOR_READY_GLOW = 0x40FF3333;       // 빨간 글로우
    private static final int COLOR_READY_BG = 0x40200808;         // 붉은 배경
    
    // 공통
    private static final int COLOR_COUNT_TEXT = 0xFFFFFFFF;       // 레디 현황 텍스트
    private static final int COLOR_BORDER = 0x60FFFFFF;           // 테두리
    
    // ================================================================================
    // 애니메이션
    // ================================================================================
    
    private long lastRenderTime = 0;
    private float pulsePhase = 0;
    private float glowIntensity = 0;
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.options.debugEnabled) return;
        
        // 헌터 월드 체크
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        if (profile == null || !profile.isInHunter()) return;
        
        VitalData vital = PlayerDataManager.getInstance().getVital();
        if (vital == null || !vital.isHunterMode()) return;
        
        // 레디 상태 체크 (로비 상태에서만 표시)
        ReadyStatusData readyStatus = PlayerDataManager.getInstance().getReadyStatus();
        if (readyStatus == null || !readyStatus.isLobbyState()) return;
        
        // TAB 또는 큰 맵 열렸을 때 숨김
        if (HunterTabOverlay.isTabPressed() || HunterHudOverlay.isBigMapOpen()) return;
        
        // 애니메이션 업데이트
        updateAnimation(readyStatus.isReady());
        
        TextRenderer tr = client.textRenderer;
        
        int boxX = PADDING_LEFT;
        int boxY = PADDING_TOP;
        
        boolean isReady = readyStatus.isReady();
        
        // 배경
        int bgColor = isReady ? COLOR_READY_BG : COLOR_UNREADY_BG;
        context.fill(boxX, boxY, boxX + BOX_WIDTH, boxY + BOX_HEIGHT, bgColor);
        
        // 테두리
        int borderColor = isReady ? COLOR_READY_GLOW : COLOR_BORDER;
        context.fill(boxX, boxY, boxX + BOX_WIDTH, boxY + 1, borderColor);
        context.fill(boxX, boxY + BOX_HEIGHT - 1, boxX + BOX_WIDTH, boxY + BOX_HEIGHT, borderColor);
        context.fill(boxX, boxY, boxX + 1, boxY + BOX_HEIGHT, borderColor);
        context.fill(boxX + BOX_WIDTH - 1, boxY, boxX + BOX_WIDTH, boxY + BOX_HEIGHT, borderColor);
        
        // READY 텍스트
        String readyText = "READY";
        int textWidth = tr.getWidth(readyText);
        int textX = boxX + (BOX_WIDTH - textWidth) / 2;
        int textY = boxY + 8;
        
        if (isReady) {
            // 레디 상태 - 역동적인 빨간색
            float pulse = (float) (0.8 + 0.2 * Math.sin(pulsePhase));
            int alpha = (int) (pulse * 255);
            int readyColor = (alpha << 24) | (COLOR_READY_TEXT & 0x00FFFFFF);
            
            // 글로우 효과
            int glowAlpha = (int) (glowIntensity * 60);
            int glowColor = (glowAlpha << 24) | 0xFF3333;
            
            for (int i = 0; i < 3; i++) {
                int offset = i + 1;
                context.drawText(tr, readyText, textX - offset, textY, glowColor, false);
                context.drawText(tr, readyText, textX + offset, textY, glowColor, false);
                context.drawText(tr, readyText, textX, textY - offset, glowColor, false);
                context.drawText(tr, readyText, textX, textY + offset, glowColor, false);
            }
            
            // 메인 텍스트 (스케일 효과 시뮬레이션 - 굵은 효과)
            context.drawText(tr, readyText, textX, textY, readyColor, true);
            context.drawText(tr, readyText, textX + 1, textY, readyColor, true);
        } else {
            // 미레디 상태 - 연한 회색
            context.drawText(tr, readyText, textX, textY, COLOR_UNREADY_TEXT, false);
        }
        
        // 구분선
        int lineY = boxY + 24;
        int lineColor = isReady ? COLOR_READY_GLOW : 0x30FFFFFF;
        context.fill(boxX + 10, lineY, boxX + BOX_WIDTH - 10, lineY + 1, lineColor);
        
        // 레디 현황
        String countText = readyStatus.getReadyCount() + "/" + readyStatus.getTotalInLobby() + " 레디";
        int countWidth = tr.getWidth(countText);
        int countX = boxX + (BOX_WIDTH - countWidth) / 2;
        int countY = boxY + 30;
        
        context.drawText(tr, countText, countX, countY, COLOR_COUNT_TEXT, true);
    }
    
    private void updateAnimation(boolean isReady) {
        long now = System.currentTimeMillis();
        float delta = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        
        // 펄스 애니메이션 (레디 시에만)
        if (isReady) {
            pulsePhase += delta * 5f; // 5Hz
            if (pulsePhase > Math.PI * 2) pulsePhase -= Math.PI * 2;
            
            // 글로우 강도 증가
            glowIntensity += delta * 3f;
            if (glowIntensity > 1.0f) glowIntensity = 1.0f;
        } else {
            // 글로우 강도 감소
            glowIntensity -= delta * 3f;
            if (glowIntensity < 0) glowIntensity = 0;
        }
    }
}

