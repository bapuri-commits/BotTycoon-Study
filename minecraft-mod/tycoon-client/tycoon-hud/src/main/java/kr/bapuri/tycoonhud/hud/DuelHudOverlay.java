package kr.bapuri.tycoonhud.hud;

import kr.bapuri.tycoonhud.model.DuelData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * 듀얼 세션 HUD 오버레이입니다.
 * 
 * <h3>표시 내용</h3>
 * <ul>
 *     <li>듀얼 타입 (랭크전/일반전)</li>
 *     <li>상대방 닉네임</li>
 *     <li>남은 시간 (카운트다운 타이머)</li>
 *     <li>시간 진행바</li>
 *     <li>베팅 금액</li>
 * </ul>
 * 
 * <h3>위치</h3>
 * <p>화면 좌상단, 프로필 HUD 아래 (y = 프로필 HUD 높이 + 여백)</p>
 * 
 * <h3>스타일</h3>
 * <ul>
 *     <li>배경: 반투명 빨강 (듀얼 강조)</li>
 *     <li>COUNTDOWN 상태일 때: 배경색 깜빡임 효과</li>
 * </ul>
 */
public class DuelHudOverlay implements HudRenderCallback {
    
    /** 화면 가장자리로부터의 여백 */
    private static final int PADDING = 5;
    
    /** 프로필 HUD 아래 추가 여백 */
    private static final int TOP_OFFSET = 80; // 프로필 HUD가 대략 70px 정도
    
    /** 배경 색상 (반투명 어두운 빨강) */
    private static final int BG_COLOR = 0xAA8B0000;
    
    /** 카운트다운 배경 색상 (더 밝은 빨강) */
    private static final int BG_COLOR_COUNTDOWN = 0xAACC0000;
    
    /** 텍스트 색상 (흰색) */
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    
    /** 시간 텍스트 색상 (노란색) */
    private static final int TIME_COLOR = 0xFFFFFF00;
    
    /** 베팅 텍스트 색상 (금색) */
    private static final int BET_COLOR = 0xFFFFD700;
    
    /** 줄 높이 */
    private static final int LINE_HEIGHT = 10;
    
    /** 내부 패딩 */
    private static final int INNER_PADDING = 4;
    
    /** 타이머 바 높이 */
    private static final int TIMER_BAR_HEIGHT = 4;
    
    /** 타이머 바 배경 색상 */
    private static final int TIMER_BG = 0xFF333333;
    
    /** 타이머 바 전경 색상 (밝은 빨강) */
    private static final int TIMER_FG = 0xFFFF4444;
    
    /** 깜빡임 주기 (밀리초) */
    private static final long BLINK_INTERVAL = 500;
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // F3 디버그 화면이 열려있으면 숨김
        if (client.options.debugEnabled) {
            return;
        }
        
        // 듀얼 데이터가 없거나 비활성화 상태면 숨김
        DuelData duel = PlayerDataManager.getInstance().getDuel();
        if (duel == null || !duel.isActive()) {
            return;
        }
        
        TextRenderer textRenderer = client.textRenderer;
        
        // 표시할 텍스트 라인 준비
        String[] lines = buildDisplayLines(duel);
        
        // 박스 크기 계산
        int maxWidth = 0;
        for (String line : lines) {
            int width = textRenderer.getWidth(line);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        
        // 최소 너비 확보 (타이머 바를 위해)
        maxWidth = Math.max(maxWidth, 100);
        
        int boxWidth = maxWidth + INNER_PADDING * 2;
        int boxHeight = lines.length * LINE_HEIGHT + INNER_PADDING * 2 + TIMER_BAR_HEIGHT + 4;
        
        int x = PADDING;
        int y = TOP_OFFSET;
        
        // 배경 그리기 (COUNTDOWN일 때 깜빡임)
        int bgColor = getBgColor(duel);
        context.fill(x, y, x + boxWidth, y + boxHeight, bgColor);
        
        // 텍스트 그리기
        int textY = y + INNER_PADDING;
        for (int i = 0; i < lines.length; i++) {
            int color = getLineColor(i, duel);
            context.drawText(textRenderer, lines[i], x + INNER_PADDING, textY, color, true);
            textY += LINE_HEIGHT;
        }
        
        // 타이머 바 그리기
        int timerY = textY + 2;
        drawTimerBar(context, x + INNER_PADDING, timerY, boxWidth - INNER_PADDING * 2, duel.getTimeRatio());
    }
    
    /**
     * 배경 색상을 반환합니다.
     * COUNTDOWN 상태일 때는 깜빡임 효과
     */
    private int getBgColor(DuelData duel) {
        if ("COUNTDOWN".equals(duel.getState())) {
            // 깜빡임 효과
            long time = System.currentTimeMillis();
            boolean blink = (time / BLINK_INTERVAL) % 2 == 0;
            return blink ? BG_COLOR_COUNTDOWN : BG_COLOR;
        }
        return BG_COLOR;
    }
    
    /**
     * 표시할 텍스트 라인들을 생성합니다.
     */
    private String[] buildDisplayLines(DuelData duel) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        
        // 1. 헤더 (타입 + 상태)
        String header = String.format("⚔ %s %s", duel.getLocalizedType(), duel.getLocalizedState());
        lines.add(header);
        
        // 2. 상대방
        lines.add(String.format("vs %s", duel.getOpponentName()));
        
        // 3. 남은 시간
        lines.add(String.format("⏱ %s", duel.getFormattedTime()));
        
        // 4. 베팅 금액 (있으면)
        if (duel.getTotalBet() > 0) {
            lines.add(String.format("💰 %,d BD", duel.getTotalBet()));
        }
        
        return lines.toArray(new String[0]);
    }
    
    /**
     * 라인별 색상을 반환합니다.
     */
    private int getLineColor(int lineIndex, DuelData duel) {
        return switch (lineIndex) {
            case 2 -> TIME_COLOR;  // 시간 라인
            case 3 -> BET_COLOR;   // 베팅 라인
            default -> TEXT_COLOR;
        };
    }
    
    /**
     * 타이머 바를 그립니다.
     */
    private void drawTimerBar(DrawContext context, int x, int y, int width, float ratio) {
        // 배경
        context.fill(x, y, x + width, y + TIMER_BAR_HEIGHT, TIMER_BG);
        
        // 채움 (비율에 따라)
        int fillWidth = (int) (width * Math.max(0, Math.min(1, ratio)));
        if (fillWidth > 0) {
            context.fill(x, y, x + fillWidth, y + TIMER_BAR_HEIGHT, TIMER_FG);
        }
    }
}

