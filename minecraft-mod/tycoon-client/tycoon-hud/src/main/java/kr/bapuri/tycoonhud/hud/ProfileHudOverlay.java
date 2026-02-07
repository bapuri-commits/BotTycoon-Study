package kr.bapuri.tycoonhud.hud;

import kr.bapuri.tycoonhud.model.JobData;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * 좌상단 프로필 HUD 오버레이입니다.
 * 
 * <h3>표시 내용 (위→아래)</h3>
 * <ol>
 *     <li>[칭호] 이름 또는 이름만</li>
 *     <li>BD: 50,000 (천 단위 콤마)</li>
 *     <li>BC: 25</li>
 *     <li>광부 Lv.35 (주 직업)</li>
 *     <li>마을 (현재 월드)</li>
 *     <li>플롯 정보 (town에서만)</li>
 * </ol>
 * 
 * <h3>스타일</h3>
 * <ul>
 *     <li>위치: 화면 좌상단 padding 5px</li>
 *     <li>배경: 반투명 검정 (0x80000000)</li>
 * </ul>
 */
public class ProfileHudOverlay implements HudRenderCallback {
    
    /** 화면 가장자리로부터의 여백 */
    private static final int PADDING = 5;
    
    /** 배경 색상 (반투명 검정) */
    private static final int BG_COLOR = 0x80000000;
    
    /** 텍스트 색상 (흰색) */
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    
    /** BD 색상 (금색) */
    private static final int BD_COLOR = 0xFFFFD700;
    
    /** BC 색상 (청록색) */
    private static final int BC_COLOR = 0xFF00CED1;
    
    /** 회색 (비활성) */
    private static final int GRAY_COLOR = 0xFF888888;
    
    /** 빨간색 (보호 구역) */
    private static final int RESTRICTED_COLOR = 0xFFFF5555;
    
    /** 줄 높이 */
    private static final int LINE_HEIGHT = 10;
    
    /** 내부 패딩 */
    private static final int INNER_PADDING = 4;
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // F3 디버그 화면이 열려있으면 숨김
        if (client.options.debugEnabled) {
            return;
        }
        
        // 데이터가 없으면 숨김
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        if (profile == null) {
            return;
        }
        
        // v2.2: 헌터 월드에서는 프로필 HUD 비활성화
        if (profile.isInHunter()) {
            return;
        }
        
        TextRenderer textRenderer = client.textRenderer;
        
        // 표시할 텍스트 라인 준비
        String[] lines = buildDisplayLines(profile);
        
        // 박스 크기 계산
        int maxWidth = 0;
        for (String line : lines) {
            int width = textRenderer.getWidth(line);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        
        int boxWidth = maxWidth + INNER_PADDING * 2;
        int boxHeight = lines.length * LINE_HEIGHT + INNER_PADDING * 2;
        
        int x = PADDING;
        int y = PADDING;
        
        // 배경 그리기
        context.fill(x, y, x + boxWidth, y + boxHeight, BG_COLOR);
        
        // 텍스트 그리기
        int textY = y + INNER_PADDING;
        for (int i = 0; i < lines.length; i++) {
            int color = getLineColor(i, profile, lines);
            context.drawText(textRenderer, lines[i], x + INNER_PADDING, textY, color, true);
            textY += LINE_HEIGHT;
        }
    }
    
    /**
     * 표시할 텍스트 라인들을 생성합니다.
     * 
     * @param profile 프로필 데이터
     * @return 텍스트 라인 배열
     */
    private String[] buildDisplayLines(PlayerProfileData profile) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        
        // 1. 이름 (칭호 포함)
        lines.add(profile.getDisplayName());
        
        // 2. BD
        lines.add(String.format("BD: %,d", profile.getBd()));
        
        // 3. BC
        lines.add(String.format("BC: %d", profile.getBottcoin()));
        
        // 4. 주 직업 (Job 시스템 비활성화 시 라인 생략)
        JobData job = profile.getPrimaryJob();
        if (job != null) {
            // 직업명 + 레벨 + 등급 표시
            lines.add(String.format("%s Lv.%d (%s)", 
                job.getLocalizedType(), job.getLevel(), job.getGradeTitle()));
        }
        // Job 시스템 비활성화 시 (job == null) 직업 라인을 표시하지 않음
        
        // 5. 현재 월드
        lines.add(profile.getLocalizedWorld());
        
        // 6. 플롯 정보 (town에서만)
        if (profile.isInTown() && profile.getPlotInfo() != null) {
            String plotText = profile.getPlotInfo().getDisplayText();
            if (plotText != null) {
                lines.add(plotText);
            }
        }
        
        return lines.toArray(new String[0]);
    }
    
    /**
     * 라인별 색상을 반환합니다.
     * 
     * @param lineIndex 라인 인덱스
     * @param profile 프로필 데이터
     * @param lines 표시되는 라인 배열 (동적 라인 수 대응)
     * @return 색상 값
     */
    private int getLineColor(int lineIndex, PlayerProfileData profile, String[] lines) {
        // 0: 이름, 1: BD, 2: BC, (3: 직업 if exists), 이후: 월드/플롯
        if (lineIndex == 1) return BD_COLOR;  // BD 라인
        if (lineIndex == 2) return BC_COLOR;  // BC 라인
        
        // 직업 라인 체크 (Job 시스템 활성화 시 3번째)
        if (profile.getPrimaryJob() != null && lineIndex == 3) {
            return TEXT_COLOR;  // 직업 라인
        }
        
        // 마지막 라인이 플롯 정보이고 보호 구역이면 빨간색
        if (lineIndex == lines.length - 1 && profile.isInTown() && profile.getPlotInfo() != null) {
            if (profile.getPlotInfo().isRestricted()) {
                return RESTRICTED_COLOR;
            }
        }
        
        return TEXT_COLOR;
    }
}

