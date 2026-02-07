package kr.bapuri.tycoonhud.hud;

import kr.bapuri.tycoonhud.model.CountdownData;
import kr.bapuri.tycoonhud.model.ReadyStatusData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/**
 * CountdownOverlay - 게임 시작 카운트다운 오버레이
 * 
 * 화면 중앙에 크고 역동적으로 카운트다운을 표시합니다.
 * 
 * <h3>디자인 특징</h3>
 * <ul>
 *     <li>큰 숫자가 중앙에 표시</li>
 *     <li>스케일 펄스 애니메이션</li>
 *     <li>마지막 5초는 빨간색 + 더 큰 효과</li>
 *     <li>글로우 이펙트</li>
 * </ul>
 */
public class CountdownOverlay implements HudRenderCallback {

    // ================================================================================
    // 색상 팔레트
    // ================================================================================
    
    private static final int COLOR_NORMAL = 0xFF00E5FF;       // 시안
    private static final int COLOR_FINAL = 0xFFFF3333;        // 빨간색 (마지막 5초)
    private static final int COLOR_GLOW_NORMAL = 0x4000E5FF;  // 시안 글로우
    private static final int COLOR_GLOW_FINAL = 0x40FF3333;   // 빨간 글로우
    private static final int COLOR_SHADOW = 0x80000000;       // 그림자
    
    private static final int COLOR_TEXT_SECONDARY = 0xFFAAAAAA; // 부제목 색상
    
    // ================================================================================
    // 애니메이션
    // ================================================================================
    
    private long lastRenderTime = 0;
    private float pulsePhase = 0;
    private int lastPlayedSecond = -1;
    
    // 숫자 변경 시 스케일 애니메이션
    private int lastSecond = -1;
    private float scaleAnimation = 1.0f;
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.options.debugEnabled) return;
        
        // 카운트다운 데이터 확인
        CountdownData countdown = PlayerDataManager.getInstance().getCountdown();
        if (countdown == null || !countdown.shouldDisplay()) return;
        
        // 레디 상태 확인 (로비 상태에서만 표시)
        ReadyStatusData readyStatus = PlayerDataManager.getInstance().getReadyStatus();
        if (readyStatus == null || !readyStatus.isCountdownState()) return;
        
        // 최신 데이터인지 확인
        if (!countdown.isRecent()) return;
        
        // 애니메이션 업데이트
        updateAnimation(countdown);
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        TextRenderer tr = client.textRenderer;
        
        int seconds = countdown.getSecondsRemaining();
        boolean isFinal = countdown.isFinalCountdown();
        
        // 사운드 재생
        if (seconds != lastPlayedSecond && seconds <= 10 && seconds > 0) {
            playCountdownSound(client, seconds);
            lastPlayedSecond = seconds;
        }
        
        // 중앙 위치
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2 - 40; // 약간 위쪽
        
        // 배경 글로우
        int glowColor = isFinal ? COLOR_GLOW_FINAL : COLOR_GLOW_NORMAL;
        int glowRadius = isFinal ? 80 : 60;
        float glowPulse = (float) (0.6 + 0.4 * Math.sin(pulsePhase * 2));
        int glowAlpha = (int) (glowPulse * 100);
        int dynamicGlow = (glowAlpha << 24) | (glowColor & 0x00FFFFFF);
        
        context.fill(
            centerX - glowRadius, centerY - glowRadius / 2,
            centerX + glowRadius, centerY + glowRadius / 2,
            dynamicGlow
        );
        
        // 메인 숫자
        String numberText = String.valueOf(seconds);
        int numberColor = isFinal ? COLOR_FINAL : COLOR_NORMAL;
        
        // 스케일 계산
        float baseScale = isFinal ? 6.0f : 4.0f;
        float currentScale = baseScale * scaleAnimation;
        
        // 숫자 그리기 (스케일 적용)
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(currentScale, currentScale, 1.0f);
        
        int textWidth = tr.getWidth(numberText);
        
        // 그림자
        context.drawText(tr, numberText, -textWidth / 2 + 2, -4 + 2, COLOR_SHADOW, false);
        
        // 글로우 레이어
        for (int i = 0; i < 3; i++) {
            int offset = i + 1;
            int glowLayerColor = (glowColor & 0x00FFFFFF) | ((40 - i * 10) << 24);
            context.drawText(tr, numberText, -textWidth / 2 - offset, -4, glowLayerColor, false);
            context.drawText(tr, numberText, -textWidth / 2 + offset, -4, glowLayerColor, false);
            context.drawText(tr, numberText, -textWidth / 2, -4 - offset, glowLayerColor, false);
            context.drawText(tr, numberText, -textWidth / 2, -4 + offset, glowLayerColor, false);
        }
        
        // 메인 텍스트
        context.drawText(tr, numberText, -textWidth / 2, -4, numberColor, false);
        
        context.getMatrices().pop();
        
        // 부제목
        String subtitle = isFinal ? "§c게임 시작!" : "§7게임이 곧 시작됩니다...";
        int subtitleWidth = tr.getWidth(subtitle);
        context.drawText(tr, subtitle, centerX - subtitleWidth / 2, centerY + 40, COLOR_TEXT_SECONDARY, true);
        
        // 레디 현황
        if (readyStatus.isValid()) {
            String readyText = "§a레디: §f" + readyStatus.getReadyCount() + "/" + readyStatus.getTotalInLobby();
            int readyWidth = tr.getWidth(readyText);
            context.drawText(tr, readyText, centerX - readyWidth / 2, centerY + 55, 0xFFFFFFFF, true);
        }
    }
    
    private void updateAnimation(CountdownData countdown) {
        long now = System.currentTimeMillis();
        float delta = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        
        // 펄스 애니메이션
        pulsePhase += delta * 4f; // 4Hz
        if (pulsePhase > Math.PI * 2) pulsePhase -= Math.PI * 2;
        
        // 숫자 변경 시 스케일 애니메이션
        int currentSecond = countdown.getSecondsRemaining();
        if (currentSecond != lastSecond) {
            scaleAnimation = 1.3f; // 초기 크게
            lastSecond = currentSecond;
        }
        
        // 스케일 복귀 (스프링 효과)
        if (scaleAnimation > 1.0f) {
            scaleAnimation -= delta * 2.0f;
            if (scaleAnimation < 1.0f) scaleAnimation = 1.0f;
        }
    }
    
    private void playCountdownSound(MinecraftClient client, int seconds) {
        if (seconds == 1) {
            // 마지막 1초 - 시작 사운드
            client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f)
            );
        } else if (seconds <= 5) {
            // 마지막 5초 - 틱 사운드
            float pitch = 1.0f + (5 - seconds) * 0.1f;
            client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), pitch)
            );
        } else if (seconds == 10 || seconds == 20 || seconds == 30) {
            // 10, 20, 30초 - 알림 사운드
            client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f)
            );
        }
    }
}

