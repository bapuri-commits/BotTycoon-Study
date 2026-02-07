package kr.bapuri.tycoonhud.hud;

import kr.bapuri.tycoonhud.model.HunterRankingData;
import kr.bapuri.tycoonhud.model.HunterRankingData.AugmentEntry;
import kr.bapuri.tycoonhud.model.HunterRankingData.BountyEntry;
import kr.bapuri.tycoonhud.model.HunterRankingData.RankingEntry;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.model.VitalData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * TAB 키 상세 정보 오버레이 - Valorant/Apex 스타일
 * 
 * <h3>디자인 특징</h3>
 * <ul>
 *     <li>다크 글래스 패널</li>
 *     <li>네온 액센트 라인</li>
 *     <li>스탯 아이콘 + 프로그레스 바</li>
 *     <li>등급별 그라데이션</li>
 * </ul>
 */
public class HunterTabOverlay implements HudRenderCallback {

    // ================================================================================
    // TAB 상태
    // ================================================================================
    
    private static boolean tabPressed = false;
    private static float fadeAlpha = 0f;
    
    public static void setTabPressed(boolean pressed) {
        tabPressed = pressed;
    }
    
    public static boolean isTabPressed() {
        return tabPressed;
    }
    
    // ================================================================================
    // 레이아웃
    // ================================================================================
    
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_GAP = 24;
    private static final int PADDING = 12;
    private static final int SECTION_TITLE_HEIGHT = 18;
    private static final int ROW_HEIGHT = 13;
    private static final int SECTION_GAP = 10;
    private static final int COMPACT_ROW_HEIGHT = 12;
    
    // ================================================================================
    // 색상 팔레트 - Futuristic Dark
    // ================================================================================
    
    // 배경
    private static final int BG_PRIMARY = 0xE8080C14;
    private static final int BG_SECONDARY = 0xD0101824;
    private static final int BG_HEADER = 0xF0061018;
    
    // 테두리/액센트
    private static final int ACCENT_CYAN = 0xFF00E5FF;
    private static final int ACCENT_GLOW = 0x4000E5FF;
    private static final int BORDER_SUBTLE = 0x30FFFFFF;
    
    // 순위 색상
    private static final int RANK_1 = 0xFFFFD700;
    private static final int RANK_2 = 0xFFE8F0F8;
    private static final int RANK_3 = 0xFFE67E22;
    private static final int RANK_OTHER = 0xFFA8B8C8;
    
    // 하이라이트
    private static final int HIGHLIGHT_BG = 0x5000D4FF;
    private static final int HIGHLIGHT_TEXT = 0xFF00FFFF;
    
    // 텍스트
    private static final int TEXT_TITLE = 0xFF00E5FF;
    private static final int TEXT_PRIMARY = 0xFFF0F4F8;
    private static final int TEXT_SECONDARY = 0xFFA0B0C0;
    private static final int TEXT_MUTED = 0xFF708090;
    
    // 스탯 색상
    private static final int STAT_LABEL = 0xFF80C0E0;
    private static final int STAT_POSITIVE = 0xFF00FF88;
    private static final int STAT_NEUTRAL = 0xFFE0E8F0;
    private static final int STAT_NEGATIVE = 0xFFFF6B6B;
    
    // 현상금
    private static final int BOUNTY_COLOR = 0xFFFFD700;
    private static final int BOUNTY_GLOW = 0x40FFD700;
    
    // 증강 등급
    private static final int AUGMENT_SILVER = 0xFFC8D0D8;
    private static final int AUGMENT_GOLD = 0xFFFFD700;
    private static final int AUGMENT_PRISM = 0xFFE040FB;
    
    // 아이콘
    private static final String ICON_RANKING = "🏆";
    private static final String ICON_BOUNTY = "💰";
    private static final String ICON_STATS = "📊";
    private static final String ICON_AUGMENT = "✦";
    private static final String ICON_SWORD = "⚔";
    private static final String ICON_SHIELD = "🛡";
    private static final String ICON_HEART = "❤";
    private static final String ICON_SPEED = "⚡";
    private static final String ICON_CRIT = "💥";
    
    // ================================================================================
    // 애니메이션
    // ================================================================================
    
    private long lastRenderTime = 0;
    private float pulsePhase = 0;
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        // 페이드 애니메이션
        if (tabPressed) {
            fadeAlpha = Math.min(1f, fadeAlpha + 0.15f);
        } else {
            fadeAlpha = Math.max(0f, fadeAlpha - 0.2f);
        }
        
        if (fadeAlpha <= 0) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.debugEnabled) return;
        
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        if (profile == null || !profile.isInHunter()) return;
        
        VitalData vital = PlayerDataManager.getInstance().getVital();
        if (vital == null || !vital.isHunterMode()) return;
        
        HunterRankingData ranking = PlayerDataManager.getInstance().getHunterRanking();
        if (ranking == null) return;
        
        updateAnimation();
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        TextRenderer tr = client.textRenderer;
        
        // 전체 레이아웃
        int totalWidth = PANEL_WIDTH * 2 + PANEL_GAP;
        int startX = (screenWidth - totalWidth) / 2;
        int startY = 40;
        
        // 딤 배경 (화면 전체)
        int dimAlpha = (int) (fadeAlpha * 0x60);
        context.fill(0, 0, screenWidth, screenHeight, dimAlpha << 24);
        
        // 패널 렌더링
        renderLeftPanel(context, tr, startX, startY, ranking);
        renderRightPanel(context, tr, startX + PANEL_WIDTH + PANEL_GAP, startY, ranking);
    }
    
    private void updateAnimation() {
        long now = System.currentTimeMillis();
        lastRenderTime = now;
        
        pulsePhase += 0.05f;
        if (pulsePhase > Math.PI * 2) pulsePhase -= Math.PI * 2;
    }
    
    // ================================================================================
    // 좌측 패널 (순위 + 현상금)
    // ================================================================================
    
    private void renderLeftPanel(DrawContext ctx, TextRenderer tr, int x, int y, HunterRankingData data) {
        List<RankingEntry> rankings = data.getRankings();
        List<BountyEntry> bounties = data.getBounties();
        
        int rankingRows = Math.min(rankings.size(), 10);
        int bountyRows = Math.min(bounties.size(), 5);
        
        int height = SECTION_TITLE_HEIGHT + (rankingRows * ROW_HEIGHT) + PADDING
                   + (bountyRows > 0 ? SECTION_GAP + SECTION_TITLE_HEIGHT + (bountyRows * ROW_HEIGHT) : 0)
                   + PADDING * 2;
        
        renderPanel(ctx, x, y, PANEL_WIDTH, height);
        
        int currentY = y + PADDING;
        
        // 순위 섹션
        renderSectionHeader(ctx, tr, x, currentY, PANEL_WIDTH, ICON_RANKING + " LEADERBOARD", rankings.size() + " players");
        currentY += SECTION_TITLE_HEIGHT;
        
        int rank = 1;
        for (RankingEntry entry : rankings) {
            if (rank > 10) break;
            boolean isMe = data.getMyRank() == rank;
            renderRankingRow(ctx, tr, x + PADDING, currentY, PANEL_WIDTH - PADDING * 2, entry, isMe);
            currentY += ROW_HEIGHT;
            rank++;
        }
        
        // 현상금 섹션
        if (!bounties.isEmpty()) {
            currentY += SECTION_GAP;
            renderSectionHeader(ctx, tr, x, currentY, PANEL_WIDTH, ICON_BOUNTY + " WANTED", "");
            currentY += SECTION_TITLE_HEIGHT;
            
            int bountyRank = 1;
            for (BountyEntry entry : bounties) {
                if (bountyRank > 5) break;
                renderBountyRow(ctx, tr, x + PADDING, currentY, PANEL_WIDTH - PADDING * 2, entry);
                currentY += ROW_HEIGHT;
                bountyRank++;
            }
        }
    }
    
    // ================================================================================
    // 우측 패널 (스탯 + 증강)
    // ================================================================================
    
    private void renderRightPanel(DrawContext ctx, TextRenderer tr, int x, int y, HunterRankingData data) {
        List<AugmentEntry> augments = data.getMyAugments();
        
        int basicRows = 4;
        int combatRows = 5;
        int augmentRows = Math.min(augments.size(), 4);
        
        int height = SECTION_TITLE_HEIGHT + (basicRows * ROW_HEIGHT) + PADDING
                   + SECTION_GAP + SECTION_TITLE_HEIGHT + (combatRows * COMPACT_ROW_HEIGHT) + PADDING
                   + (augmentRows > 0 ? SECTION_GAP + SECTION_TITLE_HEIGHT + (augmentRows * ROW_HEIGHT) : 0)
                   + PADDING * 2;
        
        renderPanel(ctx, x, y, PANEL_WIDTH, height);
        
        int currentY = y + PADDING;
        int contentWidth = PANEL_WIDTH - PADDING * 2;
        
        // 기본 스탯
        renderSectionHeader(ctx, tr, x, currentY, PANEL_WIDTH, ICON_STATS + " MY STATS", "#" + data.getMyRank());
        currentY += SECTION_TITLE_HEIGHT;
        
        renderStatRowWithIcon(ctx, tr, x + PADDING, currentY, contentWidth, "⚔", "K/D/A", data.getMyKdaText(), STAT_NEUTRAL);
        currentY += ROW_HEIGHT;
        renderStatRowWithIcon(ctx, tr, x + PADDING, currentY, contentWidth, "🎯", "Score", String.valueOf(data.getMyScore()), STAT_NEUTRAL);
        currentY += ROW_HEIGHT;
        renderStatRowWithIcon(ctx, tr, x + PADDING, currentY, contentWidth, "💢", "Damage", data.getMyDamageText(), STAT_NEUTRAL);
        currentY += ROW_HEIGHT;
        String bountyText = data.getMyBounty() > 0 ? data.getMyBounty() + " BD" : "-";
        int bountyColor = data.getMyBounty() > 0 ? BOUNTY_COLOR : TEXT_MUTED;
        renderStatRowWithIcon(ctx, tr, x + PADDING, currentY, contentWidth, "💰", "Bounty", bountyText, bountyColor);
        currentY += ROW_HEIGHT;
        
        // 전투 스탯 (2열)
        currentY += SECTION_GAP;
        renderSectionHeader(ctx, tr, x, currentY, PANEL_WIDTH, ICON_SWORD + " COMBAT", "");
        currentY += SECTION_TITLE_HEIGHT;
        
        int col1X = x + PADDING;
        int col2X = x + PADDING + contentWidth / 2 + 4;
        int halfWidth = contentWidth / 2 - 4;
        
        // 행 1
        renderCompactStatWithIcon(ctx, tr, col1X, currentY, halfWidth, "⚔", "ATK", data.getMyAttackBonusText(), data.getMyAttackBonus() > 0);
        renderCompactStatWithIcon(ctx, tr, col2X, currentY, halfWidth, "🛡", "DEF", data.getMyDefenseBonusText(), data.getMyDefenseBonus() > 0);
        currentY += COMPACT_ROW_HEIGHT;
        
        // 행 2
        renderCompactStatWithIcon(ctx, tr, col1X, currentY, halfWidth, "❤", "HP", data.getMyMaxHealthBonusText(), data.getMyMaxHealthBonus() > 0);
        renderCompactStatWithIcon(ctx, tr, col2X, currentY, halfWidth, "🛡", "DR", data.getMyDamageReductionText(), data.getMyDamageReduction() > 0);
        currentY += COMPACT_ROW_HEIGHT;
        
        // 행 3
        renderCompactStatWithIcon(ctx, tr, col1X, currentY, halfWidth, "💥", "CRIT", data.getMyCritChanceText(), data.getMyCritChance() > 0);
        renderCompactStatWithIcon(ctx, tr, col2X, currentY, halfWidth, "💥", "CDMG", data.getMyCritDamageText(), data.getMyCritDamage() > 0);
        currentY += COMPACT_ROW_HEIGHT;
        
        // 행 4
        renderCompactStatWithIcon(ctx, tr, col1X, currentY, halfWidth, "🎯", "PEN", data.getMyArmorPenetrationText(), data.getMyArmorPenetration() > 0);
        renderCompactStatWithIcon(ctx, tr, col2X, currentY, halfWidth, "🩸", "VAMP", data.getMyLifestealText(), data.getMyLifesteal() > 0);
        currentY += COMPACT_ROW_HEIGHT;
        
        // 행 5
        renderCompactStatWithIcon(ctx, tr, col1X, currentY, halfWidth, "⚡", "SPD", data.getMySpeedBonusText(), data.getMySpeedBonus() > 0);
        renderCompactStatWithIcon(ctx, tr, col2X, currentY, halfWidth, "⚓", "KB-R", data.getMyKnockbackResistText(), data.getMyKnockbackResist() > 0);
        currentY += COMPACT_ROW_HEIGHT;
        
        // 증강
        if (!augments.isEmpty()) {
            currentY += SECTION_GAP;
            renderSectionHeader(ctx, tr, x, currentY, PANEL_WIDTH, ICON_AUGMENT + " AUGMENTS", augments.size() + " active");
            currentY += SECTION_TITLE_HEIGHT;
            
            int count = 0;
            for (AugmentEntry augment : augments) {
                if (count >= 4) break;
                renderAugmentRow(ctx, tr, x + PADDING, currentY, contentWidth, augment);
                currentY += ROW_HEIGHT;
                count++;
            }
        }
    }
    
    // ================================================================================
    // 공통 렌더링 컴포넌트
    // ================================================================================
    
    private void renderPanel(DrawContext ctx, int x, int y, int width, int height) {
        int alpha = (int) (fadeAlpha * 255);
        
        // 외부 글로우
        int glowColor = (int)(fadeAlpha * 0x40) << 24 | 0x00E5FF;
        ctx.fill(x - 3, y - 3, x + width + 3, y + height + 3, glowColor);
        
        // 메인 배경
        int bgColor = (alpha << 24) | (BG_PRIMARY & 0x00FFFFFF);
        ctx.fill(x, y, x + width, y + height, bgColor);
        
        // 상단 액센트 라인
        int accentColor = (alpha << 24) | (ACCENT_CYAN & 0x00FFFFFF);
        ctx.fill(x, y, x + width, y + 2, accentColor);
        
        // 미세 테두리
        int borderColor = (int)(fadeAlpha * 0x30) << 24 | 0xFFFFFF;
        ctx.fill(x, y + 2, x + 1, y + height, borderColor);
        ctx.fill(x + width - 1, y + 2, x + width, y + height, borderColor);
        ctx.fill(x, y + height - 1, x + width, y + height, borderColor);
    }
    
    private void renderSectionHeader(DrawContext ctx, TextRenderer tr, int x, int y, int width, String title, String badge) {
        int alpha = (int) (fadeAlpha * 255);
        
        // 헤더 배경
        int bgColor = (int)(fadeAlpha * 0xF0) << 24 | (BG_HEADER & 0x00FFFFFF);
        ctx.fill(x, y, x + width, y + SECTION_TITLE_HEIGHT, bgColor);
        
        // 하단 라인
        int lineColor = (int)(fadeAlpha * 0x60) << 24 | 0xFFFFFF;
        ctx.fill(x + PADDING, y + SECTION_TITLE_HEIGHT - 1, x + width - PADDING, y + SECTION_TITLE_HEIGHT, lineColor);
        
        // 타이틀
        int titleColor = (alpha << 24) | (TEXT_TITLE & 0x00FFFFFF);
        ctx.drawText(tr, title, x + PADDING, y + 5, titleColor, false);
        
        // 배지 (우측)
        if (!badge.isEmpty()) {
            int badgeWidth = tr.getWidth(badge);
            int badgeColor = (alpha << 24) | (TEXT_SECONDARY & 0x00FFFFFF);
            ctx.drawText(tr, badge, x + width - PADDING - badgeWidth, y + 5, badgeColor, false);
        }
    }
    
    private void renderRankingRow(DrawContext ctx, TextRenderer tr, int x, int y, int width, RankingEntry entry, boolean isMe) {
        int alpha = (int) (fadeAlpha * 255);
        
        // 본인 하이라이트
        if (isMe) {
            float pulse = (float) (0.4 + 0.1 * Math.sin(pulsePhase));
            int highlightAlpha = (int) (fadeAlpha * pulse * 255);
            ctx.fill(x - 4, y - 1, x + width + 4, y + ROW_HEIGHT - 2, (highlightAlpha << 24) | 0x00D4FF);
        }
        
        // 순위
        int rankColor = switch (entry.rank) {
            case 1 -> RANK_1;
            case 2 -> RANK_2;
            case 3 -> RANK_3;
            default -> RANK_OTHER;
        };
        String rankText = String.format("%2d.", entry.rank);
        ctx.drawText(tr, rankText, x, y, (alpha << 24) | (rankColor & 0x00FFFFFF), false);
        
        // 이름
        String name = truncateName(entry.playerName, 12);
        int nameColor = isMe ? HIGHLIGHT_TEXT : TEXT_PRIMARY;
        ctx.drawText(tr, name, x + 20, y, (alpha << 24) | (nameColor & 0x00FFFFFF), false);
        
        // 점수 (우측)
        String scoreText = entry.score + "pts";
        int scoreWidth = tr.getWidth(scoreText);
        ctx.drawText(tr, scoreText, x + width - scoreWidth, y, (alpha << 24) | (TEXT_SECONDARY & 0x00FFFFFF), false);
    }
    
    private void renderBountyRow(DrawContext ctx, TextRenderer tr, int x, int y, int width, BountyEntry entry) {
        int alpha = (int) (fadeAlpha * 255);
        
        // 글로우 배경
        ctx.fill(x - 2, y - 1, x + width + 2, y + ROW_HEIGHT - 2, (int)(fadeAlpha * 0x20) << 24 | 0xFFD700);
        
        // 아이콘
        ctx.drawText(tr, "💰", x, y, (alpha << 24) | 0xFFFFFF, false);
        
        // 이름
        String name = truncateName(entry.playerName, 10);
        ctx.drawText(tr, name, x + 14, y, (alpha << 24) | (TEXT_PRIMARY & 0x00FFFFFF), false);
        
        // 금액 (우측)
        String amountText = entry.amount + " BD";
        int amountWidth = tr.getWidth(amountText);
        ctx.drawText(tr, amountText, x + width - amountWidth, y, (alpha << 24) | (BOUNTY_COLOR & 0x00FFFFFF), false);
        
        // 연속킬 표시
        if (entry.killStreak >= 3) {
            String streakText = "🔥" + entry.killStreak;
            int streakWidth = tr.getWidth(streakText);
            ctx.drawText(tr, streakText, x + width - amountWidth - streakWidth - 6, y, (alpha << 24) | 0xFF6B00, false);
        }
    }
    
    private void renderStatRowWithIcon(DrawContext ctx, TextRenderer tr, int x, int y, int width, String icon, String label, String value, int valueColor) {
        int alpha = (int) (fadeAlpha * 255);
        
        // 아이콘
        ctx.drawText(tr, icon, x, y, (alpha << 24) | 0xFFFFFF, false);
        
        // 레이블
        ctx.drawText(tr, label, x + 14, y, (alpha << 24) | (STAT_LABEL & 0x00FFFFFF), false);
        
        // 값 (우측 정렬)
        int valueWidth = tr.getWidth(value);
        ctx.drawText(tr, value, x + width - valueWidth, y, (alpha << 24) | (valueColor & 0x00FFFFFF), false);
    }
    
    private void renderCompactStatWithIcon(DrawContext ctx, TextRenderer tr, int x, int y, int width, String icon, String label, String value, boolean hasValue) {
        int alpha = (int) (fadeAlpha * 255);
        
        // 아이콘 (작게)
        ctx.drawText(tr, icon, x, y, (alpha << 24) | 0xFFFFFF, false);
        
        // 레이블
        ctx.drawText(tr, label, x + 10, y, (alpha << 24) | (TEXT_MUTED & 0x00FFFFFF), false);
        
        // 값
        int valueColor = hasValue ? STAT_POSITIVE : TEXT_MUTED;
        int valueWidth = tr.getWidth(value);
        ctx.drawText(tr, value, x + width - valueWidth, y, (alpha << 24) | (valueColor & 0x00FFFFFF), false);
    }
    
    private void renderAugmentRow(DrawContext ctx, TextRenderer tr, int x, int y, int width, AugmentEntry augment) {
        int alpha = (int) (fadeAlpha * 255);
        
        // 등급 배경 글로우
        int tierColor = switch (augment.tier) {
            case "GOLD" -> AUGMENT_GOLD;
            case "PRISM" -> AUGMENT_PRISM;
            default -> AUGMENT_SILVER;
        };
        ctx.fill(x - 2, y - 1, x + width + 2, y + ROW_HEIGHT - 2, (int)(fadeAlpha * 0x20) << 24 | (tierColor & 0x00FFFFFF));
        
        // 등급 태그
        String tierTag = switch (augment.tier) {
            case "GOLD" -> "◆";
            case "PRISM" -> "◇";
            default -> "○";
        };
        ctx.drawText(tr, tierTag, x, y, (alpha << 24) | (tierColor & 0x00FFFFFF), false);
        
        // 이름
        ctx.drawText(tr, augment.name, x + 12, y, (alpha << 24) | (TEXT_PRIMARY & 0x00FFFFFF), false);
    }
    
    private String truncateName(String name, int maxLen) {
        if (name == null) return "???";
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 1) + "…";
    }
}
