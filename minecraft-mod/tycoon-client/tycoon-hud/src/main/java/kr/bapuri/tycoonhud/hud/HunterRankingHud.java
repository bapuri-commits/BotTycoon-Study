package kr.bapuri.tycoonhud.hud;

import kr.bapuri.tycoonhud.model.HunterRankingData;
import kr.bapuri.tycoonhud.model.HunterRankingData.RankingEntry;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.model.ReadyStatusData;
import kr.bapuri.tycoonhud.model.VitalData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * 우상단 순위 HUD (상시 표시) - Premium Edition
 * 
 * <h3>디자인 특징</h3>
 * <ul>
 *     <li>글래스모피즘 (반투명 유리 효과)</li>
 *     <li>네온 글로우 액센트</li>
 *     <li>순위별 아이콘 + 그라데이션</li>
 *     <li>본인 펄스 애니메이션</li>
 * </ul>
 */
public class HunterRankingHud implements HudRenderCallback {

    // ================================================================================
    // 레이아웃
    // ================================================================================
    
    private static final int PADDING_TOP = 6;
    private static final int PADDING_LEFT = 6;  // 좌상단으로 이동
    private static final int INNER_PADDING_X = 8;   // 축소
    private static final int INNER_PADDING_Y = 5;   // 축소
    private static final int ROW_HEIGHT = 12;       // 축소
    private static final int ITEM_GAP = 2;          // 축소
    private static final int HEADER_HEIGHT = 14;    // 축소
    
    // ================================================================================
    // 색상 팔레트 - Cyberpunk Neon
    // ================================================================================
    
    // 배경 (더 투명하게)
    private static final int BG_GRADIENT_TOP = 0x40102030;      // 0x60 → 0x40
    private static final int BG_GRADIENT_BOTTOM = 0x30081018;   // 0x40 → 0x30
    
    // 테두리 (연하게)
    private static final int BORDER_GLOW_OUTER = 0x1500FFFF;    // 0x20 → 0x15
    private static final int BORDER_GLOW_INNER = 0x4000D4FF;    // 0x60 → 0x40
    private static final int BORDER_ACCENT = 0xC000E5FF;        // 0xFF → 0xC0
    
    // 순위 아이콘 색상
    private static final int RANK_1_PRIMARY = 0xFFFFD700;    // 골드
    private static final int RANK_1_GLOW = 0x60FFD700;
    private static final int RANK_2_PRIMARY = 0xFFE0E8F0;    // 플래티넘
    private static final int RANK_2_GLOW = 0x40C0C8D0;
    private static final int RANK_3_PRIMARY = 0xFFE67E22;    // 브론즈
    private static final int RANK_3_GLOW = 0x40E67E22;
    
    // 텍스트 색상
    private static final int TEXT_HEADER = 0xFF00E5FF;
    private static final int TEXT_NAME = 0xFFF0F0F0;
    private static final int TEXT_NAME_ME = 0xFF00FFFF;
    private static final int TEXT_KDA = 0xFFB8C8D8;
    private static final int TEXT_BOUNTY = 0xFFFFD700;
    
    // 아이콘
    private static final String ICON_CROWN = "👑";
    private static final String ICON_MEDAL_SILVER = "🥈";
    private static final String ICON_MEDAL_BRONZE = "🥉";
    private static final String ICON_BOUNTY = "💰";
    private static final String ICON_SKULL = "💀";
    
    // ================================================================================
    // 애니메이션
    // ================================================================================
    
    private long lastRenderTime = 0;
    private float pulsePhase = 0;
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.options.debugEnabled) return;
        
        // 헌터 월드 체크
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        if (profile == null || !profile.isInHunter()) return;
        
        VitalData vital = PlayerDataManager.getInstance().getVital();
        if (vital == null || !vital.isHunterMode()) return;
        
        // TAB 또는 큰 맵 열렸을 때 숨김 (겹침 방지)
        if (HunterTabOverlay.isTabPressed() || HunterHudOverlay.isBigMapOpen()) return;
        
        // 로비 상태에서는 HunterReadyHud가 표시되므로 숨김
        ReadyStatusData readyStatus = PlayerDataManager.getInstance().getReadyStatus();
        if (readyStatus != null && readyStatus.isLobbyState()) return;
        
        HunterRankingData ranking = PlayerDataManager.getInstance().getHunterRanking();
        if (ranking == null || !ranking.isValid()) return;
        
        List<RankingEntry> top3 = ranking.getTop3Rankings();
        if (top3.isEmpty()) return;
        
        // 애니메이션 업데이트
        updateAnimation();
        
        int screenWidth = client.getWindow().getScaledWidth();
        TextRenderer tr = client.textRenderer;
        
        // 크기 계산
        int contentWidth = calculateMaxWidth(tr, top3);
        int boxWidth = contentWidth + INNER_PADDING_X * 2;
        int boxHeight = HEADER_HEIGHT + INNER_PADDING_Y + (top3.size() * ROW_HEIGHT) + ((top3.size() - 1) * ITEM_GAP) + INNER_PADDING_Y;
        
        int boxX = PADDING_LEFT;  // 좌상단
        int boxY = PADDING_TOP;
        
        // 렌더링
        renderGlassBackground(context, boxX, boxY, boxWidth, boxHeight);
        renderHeader(context, tr, boxX, boxY, boxWidth);
        
        int y = boxY + HEADER_HEIGHT + INNER_PADDING_Y;
        for (RankingEntry entry : top3) {
            renderRankingEntry(context, tr, boxX + INNER_PADDING_X, y, contentWidth, entry);
            y += ROW_HEIGHT + ITEM_GAP;
        }
    }
    
    private void updateAnimation() {
        long now = System.currentTimeMillis();
        float delta = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        
        pulsePhase += delta * 3f; // 3Hz 펄스
        if (pulsePhase > Math.PI * 2) pulsePhase -= Math.PI * 2;
    }
    
    private int calculateMaxWidth(TextRenderer tr, List<RankingEntry> entries) {
        int maxWidth = 100; // 최소 너비
        for (RankingEntry entry : entries) {
            String name = truncateName(entry.playerName, 10);
            String kda = entry.getKdaText();
            int width = 20 + tr.getWidth(name) + 8 + tr.getWidth(kda);
            if (entry.bounty > 0) {
                width += 6 + tr.getWidth(formatBounty(entry.bounty));
            }
            maxWidth = Math.max(maxWidth, width);
        }
        return maxWidth;
    }
    
    /**
     * 글래스모피즘 배경
     */
    private void renderGlassBackground(DrawContext ctx, int x, int y, int width, int height) {
        // 외부 글로우 (큰 범위)
        ctx.fill(x - 4, y - 4, x + width + 4, y + height + 4, BORDER_GLOW_OUTER);
        
        // 내부 글로우 
        ctx.fill(x - 2, y - 2, x + width + 2, y + height + 2, BORDER_GLOW_INNER);
        
        // 메인 배경 (그라데이션 시뮬레이션 - 상단/하단 분할)
        int midY = y + height / 2;
        ctx.fill(x, y, x + width, midY, BG_GRADIENT_TOP);
        ctx.fill(x, midY, x + width, y + height, BG_GRADIENT_BOTTOM);
        
        // 상단 하이라이트 라인
        ctx.fill(x, y, x + width, y + 1, BORDER_ACCENT);
        
        // 좌우 미세 테두리
        int sideBorder = 0x3000D4FF;
        ctx.fill(x, y + 1, x + 1, y + height, sideBorder);
        ctx.fill(x + width - 1, y + 1, x + width, y + height, sideBorder);
    }
    
    /**
     * 헤더 렌더링
     */
    private void renderHeader(DrawContext ctx, TextRenderer tr, int x, int y, int width) {
        // 헤더 배경
        ctx.fill(x, y, x + width, y + HEADER_HEIGHT, 0x40000000);
        
        // 헤더 텍스트
        String headerText = "⚔ TOP HUNTERS";
        int textWidth = tr.getWidth(headerText);
        int textX = x + (width - textWidth) / 2;
        ctx.drawText(tr, headerText, textX, y + 4, TEXT_HEADER, false);
        
        // 하단 구분선
        ctx.fill(x + 10, y + HEADER_HEIGHT - 1, x + width - 10, y + HEADER_HEIGHT, 0x40FFFFFF);
    }
    
    /**
     * 순위 항목 렌더링 (프리미엄)
     */
    private void renderRankingEntry(DrawContext ctx, TextRenderer tr, int x, int y, int width, RankingEntry entry) {
        int currentX = x;
        
        // 본인 하이라이트 배경 (펄스 애니메이션)
        if (entry.isMe) {
            float pulse = (float) (0.3 + 0.15 * Math.sin(pulsePhase));
            int alpha = (int) (pulse * 255);
            int bgColor = (alpha << 24) | 0x00D4FF;
            ctx.fill(x - 4, y - 2, x + width + 4, y + ROW_HEIGHT - 2, bgColor);
        }
        
        // 순위 아이콘
        String rankIcon;
        int glowColor;
        switch (entry.rank) {
            case 1 -> { rankIcon = ICON_CROWN; glowColor = RANK_1_GLOW; }
            case 2 -> { rankIcon = ICON_MEDAL_SILVER; glowColor = RANK_2_GLOW; }
            case 3 -> { rankIcon = ICON_MEDAL_BRONZE; glowColor = RANK_3_GLOW; }
            default -> { rankIcon = ICON_SKULL; glowColor = 0; }
        }
        
        // 아이콘 글로우
        if (glowColor != 0) {
            ctx.fill(currentX - 1, y - 1, currentX + 10, y + 10, glowColor);
        }
        ctx.drawText(tr, rankIcon, currentX, y, 0xFFFFFFFF, false);
        currentX += 14;
        
        // 이름
        String nameText = truncateName(entry.playerName, 10);
        int nameColor = entry.isMe ? TEXT_NAME_ME : TEXT_NAME;
        ctx.drawText(tr, nameText, currentX, y, nameColor, false);
        currentX += tr.getWidth(nameText) + 8;
        
        // K/D/A (작은 폰트 효과 - 회색 톤)
        String kdaText = entry.getKdaText();
        ctx.drawText(tr, kdaText, currentX, y, TEXT_KDA, false);
        currentX += tr.getWidth(kdaText) + 6;
        
        // 현상금
        if (entry.bounty > 0) {
            ctx.drawText(tr, ICON_BOUNTY, currentX, y, TEXT_BOUNTY, false);
            currentX += 10;
            String bountyText = formatBounty(entry.bounty);
            ctx.drawText(tr, bountyText, currentX, y, TEXT_BOUNTY, false);
        }
    }
    
    private String truncateName(String name, int maxLen) {
        if (name == null) return "???";
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 1) + "…";
    }
    
    private String formatBounty(long bounty) {
        if (bounty >= 1000) {
            return String.format("%.1fK", bounty / 1000.0);
        }
        return String.valueOf(bounty);
    }
}
