package kr.bapuri.tycoonhud.hud;

import kr.bapuri.tycoonhud.model.VitalData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.JumpingMount;
import net.minecraft.entity.LivingEntity;

import java.util.List;

/**
 * 세련된 Vital HUD 오버레이
 * 
 * <h3>레이아웃</h3>
 * <pre>
 *  [커스텀 효과 아이콘들]
 *  ❤ [████████░░] 20/20     🛡 [██████░░░░] 12/20
 *  🍖 [████████░░] 20/20     💧 [████░░░░░░] 15/15s
 *                  ═══════[15]═══════    ← 경험치바 (핫바 바로 위)
 *                       [핫 바]
 * </pre>
 */
public class VitalHudOverlay implements HudRenderCallback {
    
    // ================================================================================
    // 레이아웃 상수
    // ================================================================================
    
    /** 바 너비 (일반 모드) */
    private static final int BAR_WIDTH = 80;
    
    /** 바 너비 (헌터 모드 - 대폭 확장) */
    private static final int HUNTER_BAR_WIDTH = 150;
    
    /** 바 높이 */
    private static final int BAR_HEIGHT = 6;
    
    /** 행 간격 (컴팩트하게) */
    private static final int ROW_GAP = 4;
    
    /** 왼쪽/오른쪽 바 사이 간격 */
    private static final int COLUMN_GAP = 20;
    
    /** 아이콘 고정 너비 */
    private static final int ICON_WIDTH = 12;
    
    /** 값 텍스트 최대 너비 */
    private static final int VALUE_WIDTH = 36;
    
    /** 화면 하단으로부터의 오프셋 (체력/배고픔 바 - 액션바 위) */
    private static final int BOTTOM_OFFSET = 55;
    
    /** 헌터 모드 체력바 오프셋 (HCL 바와 간격 좁히기) */
    private static final int HUNTER_BOTTOM_OFFSET = 42;
    
    /** 채팅창 열림 시 추가 오프셋 (채팅 메시지 영역 위로 이동) */
    private static final int CHAT_OPEN_EXTRA_OFFSET = 50;
    
    /** 오른쪽으로 이동할 오프셋 */
    private static final int RIGHT_OFFSET = 15;
    
    /** 커스텀 효과 행 높이 */
    private static final int EFFECT_ROW_HEIGHT = 12;
    
    /** 경험치 바 너비 (핫바와 동일) */
    private static final int EXP_BAR_WIDTH = 182;
    
    /** 경험치 바 높이 */
    private static final int EXP_BAR_HEIGHT = 2;
    
    /** 경험치 바 Y 오프셋 (핫바 바로 위, 액션바 아래) */
    private static final int EXP_BAR_BOTTOM_OFFSET = 24;
    
    // ================================================================================
    // 아이콘
    // ================================================================================
    
    private static final String ICON_HEALTH = "❤";
    private static final String ICON_ABSORPTION = "💛";
    private static final String ICON_FOOD = "🍖";
    private static final String ICON_ARMOR = "🛡";
    private static final String ICON_AIR = "💧";
    
    // 커스텀 효과 아이콘
    private static final String ICON_BLEED = "🩸";
    private static final String ICON_FROST = "❄";
    private static final String ICON_VAMPIRE = "🧛";
    private static final String ICON_TRUE_DAMAGE = "⚔";
    private static final String ICON_MAX_HEALTH = "💚";
    private static final String ICON_BURN = "🔥";
    private static final String ICON_POISON_CUSTOM = "☠";
    private static final String ICON_SLOW = "🐌";
    
    // v2.2: 헌터 HCL 아이콘
    private static final String ICON_HCL = "⚔";
    
    // ================================================================================
    // 색상 팔레트 - 기본
    // ================================================================================
    
    // 배경
    private static final int BG_COLOR = 0xCC000000;
    private static final int BORDER_COLOR = 0xFF333333;
    
    // 체력 (빨강)
    private static final int HEALTH_COLOR = 0xFFE74C3C;
    private static final int HEALTH_LOW_COLOR = 0xFFFF6B6B;
    private static final int HEALTH_ICON_COLOR = 0xFFFF5555;
    
    // 배고픔 (주황)
    private static final int FOOD_COLOR = 0xFFF39C12;
    private static final int FOOD_LOW_COLOR = 0xFFFFB347;
    private static final int FOOD_ICON_COLOR = 0xFFFFAA00;
    
    // 갑옷 (파랑)
    private static final int ARMOR_COLOR = 0xFF3498DB;
    private static final int ARMOR_ICON_COLOR = 0xFF55AAFF;
    
    // 산소 (청록)
    private static final int AIR_COLOR = 0xFF1ABC9C;
    private static final int AIR_LOW_COLOR = 0xFF48DBBC;
    private static final int AIR_ICON_COLOR = 0xFF55FFFF;
    
    // 경험치 (연두색 그라데이션)
    private static final int EXP_COLOR_START = 0xFF4ADE80;    // 연한 초록
    private static final int EXP_COLOR_END = 0xFF22C55E;      // 진한 초록
    private static final int EXP_GLOW_COLOR = 0xFF86EFAC;     // 글로우
    private static final int EXP_BG_COLOR = 0xFF1A1A1A;       // 어두운 배경
    private static final int EXP_BORDER_COLOR = 0xFF2D2D2D;   // 테두리
    
    // 텍스트
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_SHADOW = 0xFF000000;
    
    // ================================================================================
    // 색상 팔레트 - 상태 효과
    // ================================================================================
    
    private static final int POISON_COLOR = 0xFF7CFC00;
    private static final int POISON_ICON_COLOR = 0xFF32CD32;
    private static final int WITHER_COLOR = 0xFF3D3D3D;
    private static final int WITHER_ICON_COLOR = 0xFF1A1A1A;
    private static final int REGEN_COLOR = 0xFFFF69B4;
    private static final int REGEN_ICON_COLOR = 0xFFFF1493;
    private static final int ABSORPTION_COLOR = 0xFFFFD700;
    private static final int HUNGER_EFFECT_COLOR = 0xFF9ACD32;
    private static final int HUNGER_EFFECT_ICON_COLOR = 0xFF6B8E23;
    private static final int WATER_BREATHING_COLOR = 0xFF00BFFF;
    
    // 커스텀 효과 색상
    private static final int BLEED_COLOR = 0xFFFF4444;
    private static final int MAX_HEALTH_COLOR = 0xFFFFD700;
    
    // v2.2: HCL 그라데이션 색상
    private static final int HCL_COLOR_START = 0xFFFF6B6B;    // 빨강
    private static final int HCL_COLOR_END = 0xFFFFD93D;       // 노랑
    private static final int HCL_GLOW_COLOR = 0xFFFFE066;      // 글로우
    private static final int HCL_ICON_COLOR = 0xFFFF7777;
    
    // v2.3: 스태미너 색상
    private static final int STAMINA_COLOR = 0xFFFFD700;       // 금색
    private static final int STAMINA_LOW_COLOR = 0xFFFF8C00;   // 주황색 (경고)
    private static final int STAMINA_CRITICAL_COLOR = 0xFFFF4444; // 빨강 (위험)
    private static final int STAMINA_ICON_COLOR = 0xFFFFE066;
    private static final String ICON_STAMINA = "⚡";
    
    // 애니메이션
    private long lastTime = 0;
    private float pulse = 0f;
    
    // 싱글톤 인스턴스 (Mixin에서 직접 호출용)
    private static VitalHudOverlay INSTANCE;
    
    public VitalHudOverlay() {
        INSTANCE = this;
    }
    
    /**
     * 싱글톤 인스턴스 반환
     */
    public static VitalHudOverlay getInstance() {
        return INSTANCE;
    }
    
    // ================================================================================
    // 렌더링
    // ================================================================================
    
    /**
     * Mixin에서 직접 호출하는 렌더링 메서드
     * 채팅 렌더링 후에 호출되어 채팅 위에 HUD가 표시됨
     */
    public void renderDirect(DrawContext context, float tickDelta) {
        doRender(context, tickDelta);
    }
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        // Mixin에서 직접 렌더링하므로 여기서는 아무것도 하지 않음
        // 채팅 후에 렌더링되어야 채팅에 가려지지 않음
    }
    
    /**
     * 실제 렌더링 로직
     */
    private void doRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.options.debugEnabled) {
            return;
        }
        
        VitalData vital = PlayerDataManager.getInstance().getVital();
        if (vital == null || !vital.isValid()) {
            return;
        }
        
        // [FIX] 로컬 플레이어 데이터로 즉시 동기화 (서버 패킷 지연 해결)
        // 체력, 배고픔, 갑옷, 산소, 경험치는 로컬 값 사용
        // 상태 효과, 커스텀 효과, 헌터 모드 데이터는 서버 값 유지
        if (client.player != null) {
            vital.updateFromLocal(
                client.player.getHealth(),
                client.player.getMaxHealth(),
                client.player.getHungerManager().getFoodLevel(),
                20, // maxFoodLevel
                client.player.getHungerManager().getSaturationLevel(),
                client.player.getArmor(),
                client.player.getAir(),
                client.player.getMaxAir(),
                client.player.isSubmergedInWater() || client.player.getAir() < client.player.getMaxAir(),
                client.player.experienceLevel,
                client.player.experienceProgress,
                client.player.getAbsorptionAmount()
            );
        }
        
        // 헌터 모드에서 TAB 또는 큰 맵 열렸을 때 숨김 (겹침 방지)
        if (vital.isHunterMode() && (HunterTabOverlay.isTabPressed() || HunterHudOverlay.isBigMapOpen())) {
            return;
        }
        
        updatePulse();
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        TextRenderer tr = client.textRenderer;
        
        // 채팅창 열림 여부 확인 - 채팅 UI가 열리면 HUD를 위로 이동
        boolean isChatOpen = client.currentScreen instanceof ChatScreen;
        int chatOffset = isChatOpen ? CHAT_OPEN_EXTRA_OFFSET : 0;
        
        // 탑승물(말 등) 탑승 여부 확인
        boolean isRidingMount = isRidingJumpingMount(client);
        
        // === 경험치 바 (탑승 중이 아닐 때만 - 탑승 시 바닐라 점프 게이지 표시) ===
        if (!isRidingMount) {
            if (vital.isHunterMode()) {
                // 헌터 모드: HCL 바를 바닐라 경험치바 자리에 표시
                renderHclExpBar(context, tr, vital, screenWidth, screenHeight, chatOffset);
            } else {
                // 일반 모드: 바닐라 경험치바
                renderExpBar(context, tr, vital, screenWidth, screenHeight, chatOffset);
            }
        }
        
        // 전체 HUD 너비 계산
        int barBlockWidth = ICON_WIDTH + BAR_WIDTH + 4 + VALUE_WIDTH;
        int totalWidth = barBlockWidth * 2 + COLUMN_GAP;
        
        // 시작 위치 (화면 중앙 기준 + 오른쪽 오프셋)
        int startX = (screenWidth - totalWidth) / 2 + RIGHT_OFFSET;
        
        // 커스텀 효과가 있으면 추가 공간 확보
        List<VitalData.CustomEffect> customEffects = vital.getCustomEffects();
        int effectRowOffset = customEffects.isEmpty() ? 0 : EFFECT_ROW_HEIGHT;
        
        int row1Y = screenHeight - BOTTOM_OFFSET - effectRowOffset - chatOffset;
        int row2Y = row1Y + BAR_HEIGHT + ROW_GAP;
        
        // === 커스텀 효과 행 (있을 때만) ===
        if (!customEffects.isEmpty()) {
            int effectY = row1Y - EFFECT_ROW_HEIGHT;
            renderCustomEffects(context, tr, vital, startX, effectY, totalWidth);
        }
        
        // v2.2: 헌터 월드 별도 레이아웃
        if (vital.isHunterMode()) {
            renderHunterLayout(context, tr, vital, startX, row1Y, barBlockWidth);
        } else {
            renderNormalLayout(context, tr, vital, startX, row1Y, row2Y, barBlockWidth);
        }
    }
    
    /**
     * v2.2: 일반 월드 레이아웃 (기존)
     */
    private void renderNormalLayout(DrawContext ctx, TextRenderer tr, VitalData vital, 
                                     int startX, int row1Y, int row2Y, int barBlockWidth) {
        int rightX = startX + barBlockWidth + COLUMN_GAP;
        
        // 첫 번째 행: 체력 + 갑옷
        renderHealthBar(ctx, tr, vital, startX, row1Y);
        drawVitalBar(ctx, tr, rightX, row1Y,
                ICON_ARMOR, ARMOR_ICON_COLOR,
                vital.getArmorRatio(), vital.getArmorText(),
                ARMOR_COLOR, false);
        
        // 두 번째 행: 배고픔 + 산소
        renderFoodBar(ctx, tr, vital, startX, row2Y);
        renderAirBar(ctx, tr, vital, rightX, row2Y);
    }
    
    /**
     * v3.0: 헌터 월드 레이아웃 (깔끔한 2줄 구성)
     * 
     * 레이아웃:
     * - 호흡바: 물 안에서만 체력바 바로 위에 표시
     * - 1줄: 체력바 (150px 확장)
     * - 2줄: 스태미너바 (150px 확장)
     * - HCL 경험치바: 핫바 바로 위 (바닐라 경험치바 자리)
     */
    private void renderHunterLayout(DrawContext ctx, TextRenderer tr, VitalData vital, 
                                     int startX, int row1Y, int barBlockWidth) {
        // 헌터 모드용 중앙 정렬 및 위치 재계산
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        // 확장된 바 너비로 중앙 계산
        int hunterBarBlockWidth = ICON_WIDTH + HUNTER_BAR_WIDTH + 4 + VALUE_WIDTH;
        int hunterStartX = (screenWidth - hunterBarBlockWidth) / 2;
        
        // 채팅창 열림 여부
        boolean isChatOpen = client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
        int chatOffset = isChatOpen ? CHAT_OPEN_EXTRA_OFFSET : 0;
        
        // 헌터 전용 오프셋 사용 (HCL 바와 간격 좁히기)
        int hunterRow1Y = screenHeight - HUNTER_BOTTOM_OFFSET - chatOffset;
        int hunterRow2Y = hunterRow1Y + BAR_HEIGHT + ROW_GAP;
        
        // 호흡바: 물 안에서만 체력바 바로 위에 표시
        if (vital.isUnderwater() || vital.getAirRatio() < 1.0f) {
            int airY = hunterRow1Y - BAR_HEIGHT - ROW_GAP;
            renderHunterAirBar(ctx, tr, vital, hunterStartX, airY);
        }
        
        // 1줄: 체력바 (확장)
        renderHunterHealthBar(ctx, tr, vital, hunterStartX, hunterRow1Y);
        
        // 2줄: 스태미너바 (확장)
        renderHunterStaminaBar(ctx, tr, vital, hunterStartX, hunterRow2Y);
    }
    
    /**
     * v3.0: 헌터 전용 체력바 (확장 너비)
     */
    private void renderHunterHealthBar(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y) {
        int barColor;
        int iconColor;
        boolean pulsing;
        String icon = ICON_HEALTH;
        
        if (vital.isBleeding()) {
            barColor = getPulseColor(BLEED_COLOR);
            iconColor = BLEED_COLOR;
            pulsing = true;
        } else if (vital.isWithered()) {
            barColor = getPulseColor(WITHER_COLOR);
            iconColor = WITHER_ICON_COLOR;
            pulsing = true;
        } else if (vital.isPoisoned()) {
            barColor = getPulseColor(POISON_COLOR);
            iconColor = POISON_ICON_COLOR;
            pulsing = true;
        } else if (vital.isRegenerating() || vital.hasVampireHeal()) {
            barColor = getPulseColor(REGEN_COLOR);
            iconColor = REGEN_ICON_COLOR;
            pulsing = false;
        } else if (vital.hasMaxHealthBoost()) {
            barColor = blendColors(HEALTH_COLOR, MAX_HEALTH_COLOR, 0.3f);
            iconColor = MAX_HEALTH_COLOR;
            pulsing = false;
        } else if (vital.isHealthLow()) {
            barColor = getPulseColor(HEALTH_LOW_COLOR);
            iconColor = HEALTH_ICON_COLOR;
            pulsing = vital.isHealthCritical();
        } else {
            barColor = HEALTH_COLOR;
            iconColor = HEALTH_ICON_COLOR;
            pulsing = false;
        }
        
        String healthText = vital.getHealthText();
        if (vital.hasAbsorption()) {
            healthText = String.format("%.0f+%.0f", vital.getHealth(), vital.getAbsorptionAmount());
            icon = ICON_ABSORPTION;
            if (!vital.isPoisoned() && !vital.isWithered() && !vital.isBleeding()) {
                barColor = blendColors(barColor, ABSORPTION_COLOR, 0.3f);
            }
        }
        
        drawHunterVitalBar(ctx, tr, x, y, icon, iconColor, vital.getHealthRatio(), healthText, barColor, pulsing);
        
        // 흡수 오버레이
        if (vital.hasAbsorption()) {
            float absorptionRatio = Math.min(1f, vital.getAbsorptionAmount() / vital.getMaxHealth());
            int barX = x + ICON_WIDTH;
            int healthFillWidth = (int) (HUNTER_BAR_WIDTH * vital.getHealthRatio());
            int absorptionWidth = (int) (HUNTER_BAR_WIDTH * absorptionRatio);
            
            int absorptionStartX = barX + healthFillWidth;
            int absorptionEndX = Math.min(barX + HUNTER_BAR_WIDTH, absorptionStartX + absorptionWidth);
            
            if (absorptionEndX > absorptionStartX) {
                ctx.fill(absorptionStartX, y, absorptionEndX, y + BAR_HEIGHT, ABSORPTION_COLOR);
                int highlight = brighten(ABSORPTION_COLOR, 0.3f);
                ctx.fill(absorptionStartX, y, absorptionEndX, y + 1, highlight);
            }
        }
    }
    
    /**
     * v3.0: 헌터 전용 스태미너바 (확장 너비)
     */
    private void renderHunterStaminaBar(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y) {
        int barColor;
        int iconColor;
        boolean pulsing;
        
        if (vital.isStaminaCritical()) {
            barColor = getPulseColor(STAMINA_CRITICAL_COLOR);
            iconColor = STAMINA_CRITICAL_COLOR;
            pulsing = true;
        } else if (vital.isStaminaLow()) {
            barColor = getPulseColor(STAMINA_LOW_COLOR);
            iconColor = STAMINA_LOW_COLOR;
            pulsing = true;
        } else {
            barColor = STAMINA_COLOR;
            iconColor = STAMINA_ICON_COLOR;
            pulsing = false;
        }
        
        drawHunterVitalBar(ctx, tr, x, y, ICON_STAMINA, iconColor, 
                vital.getStaminaRatio(), vital.getStaminaText(), barColor, pulsing);
    }
    
    /**
     * v3.0: 헌터 전용 호흡바 (물에서만, 체력바 위)
     */
    private void renderHunterAirBar(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y) {
        int barColor;
        int iconColor;
        boolean pulsing;
        
        if (vital.hasWaterBreathing()) {
            barColor = WATER_BREATHING_COLOR;
            iconColor = AIR_ICON_COLOR;
            pulsing = false;
        } else if (vital.isAirLow()) {
            barColor = getPulseColor(AIR_LOW_COLOR);
            iconColor = AIR_ICON_COLOR;
            pulsing = true;
        } else {
            barColor = AIR_COLOR;
            iconColor = AIR_ICON_COLOR;
            pulsing = false;
        }
        
        drawHunterVitalBar(ctx, tr, x, y, ICON_AIR, iconColor, vital.getAirRatio(), vital.getAirText(), barColor, pulsing);
    }
    
    /**
     * v3.0: 헌터 전용 바 렌더링 (확장 너비 150px)
     */
    private void drawHunterVitalBar(DrawContext ctx, TextRenderer tr,
            int x, int y,
                                     String icon, int iconColor,
                                     float ratio, String value,
                                     int barColor, boolean pulsing) {
        
        int iconDisplayColor = pulsing ? getPulseColor(iconColor) : iconColor;
        ctx.drawText(tr, icon, x, y - 1, iconDisplayColor, true);
        
        int barX = x + ICON_WIDTH;
        
        // 배경
        ctx.fill(barX - 1, y - 1, barX + HUNTER_BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BORDER_COLOR);
        ctx.fill(barX, y, barX + HUNTER_BAR_WIDTH, y + BAR_HEIGHT, BG_COLOR);
        
        // 채움
        int fillWidth = (int) (HUNTER_BAR_WIDTH * Math.max(0, Math.min(1, ratio)));
        if (fillWidth > 0) {
            ctx.fill(barX, y, barX + fillWidth, y + BAR_HEIGHT, barColor);
            
            // 상단 하이라이트
            int highlight = brighten(barColor, 0.3f);
            ctx.fill(barX, y, barX + fillWidth, y + 1, highlight);
        }
        
        // 값 텍스트
        int valueX = barX + HUNTER_BAR_WIDTH + 4;
        ctx.drawText(tr, value, valueX, y - 1, TEXT_WHITE, true);
    }
    
    /**
     * v2.3: 스태미너 바 렌더링
     * - 30% 이하: 주황색 + 깜빡임
     * - 6% 이하: 빨간색 + 빠른 깜빡임
     */
    private void renderStaminaBar(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y) {
        int barColor;
        int iconColor;
        boolean pulsing;
        
        if (vital.isStaminaCritical()) {
            // 위험 (6% 이하) - 빨간색 + 빠른 깜빡임
            barColor = getPulseColor(STAMINA_CRITICAL_COLOR);
            iconColor = STAMINA_CRITICAL_COLOR;
            pulsing = true;
        } else if (vital.isStaminaLow()) {
            // 경고 (30% 이하) - 주황색 + 깜빡임
            barColor = getPulseColor(STAMINA_LOW_COLOR);
            iconColor = STAMINA_LOW_COLOR;
            pulsing = true;
        } else {
            // 정상 - 금색
            barColor = STAMINA_COLOR;
            iconColor = STAMINA_ICON_COLOR;
            pulsing = false;
        }
        
        drawVitalBar(ctx, tr, x, y, ICON_STAMINA, iconColor, 
                vital.getStaminaRatio(), vital.getStaminaText(), barColor, pulsing);
    }
    
    /**
     * v2.2: HCL 바 렌더링 (그라데이션)
     */
    private void renderHclBar(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y) {
        float ratio = vital.getHclProgress();
        String text = vital.getHclText();
        
        // 레벨에 따른 그라데이션 색상
        float levelRatio = vital.getHclRatio();
        int barColor = blendColors(HCL_COLOR_START, HCL_COLOR_END, levelRatio);
        
        // 아이콘
        ctx.drawText(tr, ICON_HCL, x, y - 1, HCL_ICON_COLOR, true);
        
        int barX = x + ICON_WIDTH;
        
        // 배경
        ctx.fill(barX - 1, y - 1, barX + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BORDER_COLOR);
        ctx.fill(barX, y, barX + BAR_WIDTH, y + BAR_HEIGHT, BG_COLOR);
        
        // 채움 (그라데이션)
        int fillWidth = (int) (BAR_WIDTH * Math.max(0, Math.min(1, ratio)));
        if (fillWidth > 0) {
            ctx.fill(barX, y, barX + fillWidth, y + BAR_HEIGHT, barColor);
            
            // 상단 하이라이트
            int highlight = brighten(barColor, 0.4f);
            ctx.fill(barX, y, barX + fillWidth, y + 1, highlight);
        }
        
        // 값 텍스트
        int valueX = barX + BAR_WIDTH + 4;
        ctx.drawText(tr, text, valueX, y - 1, TEXT_WHITE, true);
    }
    
    /**
     * v2.2: 항상 표시되는 호흡바 (헌터 월드용)
     */
    private void renderAirBarAlways(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y) {
        int barColor;
        int iconColor;
        boolean pulsing;
        
        if (vital.hasWaterBreathing()) {
            barColor = WATER_BREATHING_COLOR;
            iconColor = AIR_ICON_COLOR;
            pulsing = false;
        } else if (vital.isAirLow()) {
            barColor = getPulseColor(AIR_LOW_COLOR);
            iconColor = AIR_ICON_COLOR;
            pulsing = true;
        } else {
            barColor = AIR_COLOR;
            iconColor = AIR_ICON_COLOR;
            pulsing = false;
        }
        
        drawVitalBar(ctx, tr, x, y, ICON_AIR, iconColor, vital.getAirRatio(), vital.getAirText(), barColor, pulsing);
    }
    
    /**
     * 점프 가능한 탑승물(말, 당나귀 등)에 타고 있는지 확인
     * 탑승 중일 때는 바닐라 점프 게이지가 경험치 바 위치에 표시됨
     */
    private boolean isRidingJumpingMount(MinecraftClient client) {
        if (client.player == null) return false;
        
        if (client.player.getVehicle() instanceof LivingEntity mount) {
            return mount instanceof JumpingMount;
        }
        return false;
    }
    
    // ================================================================================
    // 경험치 바 렌더링 (세련된 디자인)
    // ================================================================================
    
    /**
     * 세련된 경험치 바 렌더링
     * - 슬림한 바
     * - 레벨 숫자 중앙 표시 (글로우 효과)
     * - 그라데이션 채움
     */
    private void renderExpBar(DrawContext ctx, TextRenderer tr, VitalData vital, int screenWidth, int screenHeight, int chatOffset) {
        int level = vital.getLevel();
        
        // 레벨 텍스트 (경험치 바 오른쪽에 작게)
        String levelText = "Lv" + level;
        int levelWidth = tr.getWidth(levelText);
        
        // 경험치 바 너비 + 레벨 텍스트 공간
        int totalWidth = EXP_BAR_WIDTH + 4 + levelWidth;
        int barX = (screenWidth - totalWidth) / 2;
        int barY = screenHeight - EXP_BAR_BOTTOM_OFFSET - chatOffset;
        
        // 외부 테두리 (약간의 글로우)
        ctx.fill(barX - 1, barY - 1, barX + EXP_BAR_WIDTH + 1, barY + EXP_BAR_HEIGHT + 1, EXP_BORDER_COLOR);
        
        // 내부 배경
        ctx.fill(barX, barY, barX + EXP_BAR_WIDTH, barY + EXP_BAR_HEIGHT, EXP_BG_COLOR);
        
        // 경험치 채움 (그라데이션 효과)
        float expRatio = vital.getExpProgress();
        int fillWidth = (int) (EXP_BAR_WIDTH * expRatio);
        
        if (fillWidth > 0) {
            // 메인 바
            ctx.fill(barX, barY, barX + fillWidth, barY + EXP_BAR_HEIGHT, EXP_COLOR_END);
            
            // 상단 하이라이트 (1px)
            ctx.fill(barX, barY, barX + fillWidth, barY + 1, EXP_COLOR_START);
        }
        
        // 레벨 숫자 (경험치 바 오른쪽 옆에 작게)
        int textX = barX + EXP_BAR_WIDTH + 4;
        int textY = barY - 2; // 경험치 바와 수직 정렬
        
        // 레벨 표시 (항상)
        ctx.drawText(tr, levelText, textX, textY, EXP_COLOR_START, true);
    }
    
    /**
     * v3.0: HCL 경험치바 (바닐라 경험치바 자리, 헌터 전용)
     * - 바닐라 스타일과 유사
     * - 레벨 숫자 왼쪽에 표시 (Lv. XX)
     * - 빨강→노랑 그라데이션
     */
    private void renderHclExpBar(DrawContext ctx, TextRenderer tr, VitalData vital, 
                                  int screenWidth, int screenHeight, int chatOffset) {
        int hclLevel = vital.getHclLevel();
        float hclProgress = vital.getHclProgress();
        
        // 레벨 텍스트 (왼쪽)
        String levelText = "Lv. " + hclLevel;
        int levelWidth = tr.getWidth(levelText);
        
        // 바 위치 (핫바와 동일하게 화면 중앙 정렬, 레벨은 바 왼쪽에)
        int barX = (screenWidth - EXP_BAR_WIDTH) / 2;
        int barY = screenHeight - EXP_BAR_BOTTOM_OFFSET - chatOffset;
        int textX = barX - levelWidth - 4;  // 바 왼쪽에 레벨 표시
        
        // 레벨에 따른 색상
        float levelRatio = vital.getHclRatio();
        int levelColor = blendColors(HCL_COLOR_START, HCL_COLOR_END, levelRatio);
        
        // 레벨 텍스트 그리기
        ctx.drawText(tr, levelText, textX, barY - 2, levelColor, true);
        
        // 외부 테두리 (HCL 글로우)
        int glowColor = 0x30000000 | (levelColor & 0x00FFFFFF);
        ctx.fill(barX - 2, barY - 2, barX + EXP_BAR_WIDTH + 2, barY + EXP_BAR_HEIGHT + 2, glowColor);
        ctx.fill(barX - 1, barY - 1, barX + EXP_BAR_WIDTH + 1, barY + EXP_BAR_HEIGHT + 1, EXP_BORDER_COLOR);
        
        // 내부 배경
        ctx.fill(barX, barY, barX + EXP_BAR_WIDTH, barY + EXP_BAR_HEIGHT, EXP_BG_COLOR);
        
        // HCL 채움 (그라데이션)
        int fillWidth = (int) (EXP_BAR_WIDTH * hclProgress);
        if (fillWidth > 0) {
            // 그라데이션 효과 (빨강→노랑)
            int barColor = blendColors(HCL_COLOR_START, HCL_COLOR_END, hclProgress);
            ctx.fill(barX, barY, barX + fillWidth, barY + EXP_BAR_HEIGHT, barColor);
            
            // 상단 하이라이트
            int highlight = brighten(barColor, 0.4f);
            ctx.fill(barX, barY, barX + fillWidth, barY + 1, highlight);
        }
    }
    
    // ================================================================================
    // 커스텀 효과 렌더링
    // ================================================================================
    
    private void renderCustomEffects(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y, int maxWidth) {
        List<VitalData.CustomEffect> effects = vital.getCustomEffects();
        if (effects.isEmpty()) return;
        
        int currentX = x;
        int spacing = 4;
        
        for (VitalData.CustomEffect effect : effects) {
            String icon = getEffectIcon(effect.effectId);
            int color = parseColor(effect.color);
            
            String timeText = "";
            if (effect.duration > 0) {
                int seconds = effect.duration / 20;
                timeText = seconds + "s";
            }
            
            String displayText = icon + (timeText.isEmpty() ? "" : timeText);
            int textWidth = tr.getWidth(displayText) + ICON_WIDTH;
            
            if (currentX + textWidth > x + maxWidth) break;
            
            ctx.fill(currentX - 1, y - 1, currentX + textWidth + 1, y + 10, 0x88000000);
            
            int iconColor = getPulseColor(color);
            ctx.drawText(tr, icon, currentX, y, iconColor, true);
            
            if (!timeText.isEmpty()) {
                ctx.drawText(tr, timeText, currentX + ICON_WIDTH, y, TEXT_WHITE, true);
            }
            
            currentX += textWidth + spacing;
        }
    }
    
    private String getEffectIcon(String effectId) {
        if (effectId == null) return "?";
        return switch (effectId.toLowerCase()) {
            case "bleed" -> ICON_BLEED;
            case "frost" -> ICON_FROST;
            case "vampire" -> ICON_VAMPIRE;
            case "true_damage" -> ICON_TRUE_DAMAGE;
            case "max_health_boost" -> ICON_MAX_HEALTH;
            case "burn" -> ICON_BURN;
            case "poison_custom" -> ICON_POISON_CUSTOM;
            case "slow_custom" -> ICON_SLOW;
            default -> "✦";
        };
    }
    
    private int parseColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return TEXT_WHITE;
        }
        try {
            String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
            int rgb = Integer.parseInt(hex, 16);
            return 0xFF000000 | rgb;
        } catch (NumberFormatException e) {
            return TEXT_WHITE;
        }
    }
    
    // ================================================================================
    // 상태 효과 적용 렌더링
    // ================================================================================
    
    private void renderHealthBar(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y) {
        int barColor;
        int iconColor;
        boolean pulsing;
        String icon = ICON_HEALTH;
        
        if (vital.isBleeding()) {
            barColor = getPulseColor(BLEED_COLOR);
            iconColor = BLEED_COLOR;
            pulsing = true;
        } else if (vital.isWithered()) {
            barColor = getPulseColor(WITHER_COLOR);
            iconColor = WITHER_ICON_COLOR;
            pulsing = true;
        } else if (vital.isPoisoned()) {
            barColor = getPulseColor(POISON_COLOR);
            iconColor = POISON_ICON_COLOR;
            pulsing = true;
        } else if (vital.isRegenerating() || vital.hasVampireHeal()) {
            barColor = getPulseColor(REGEN_COLOR);
            iconColor = REGEN_ICON_COLOR;
            pulsing = false;
        } else if (vital.hasMaxHealthBoost()) {
            barColor = blendColors(HEALTH_COLOR, MAX_HEALTH_COLOR, 0.3f);
            iconColor = MAX_HEALTH_COLOR;
            pulsing = false;
        } else if (vital.isHealthLow()) {
            barColor = getPulseColor(HEALTH_LOW_COLOR);
            iconColor = HEALTH_ICON_COLOR;
            pulsing = vital.isHealthCritical();
        } else {
            barColor = HEALTH_COLOR;
            iconColor = HEALTH_ICON_COLOR;
            pulsing = false;
        }
        
        String healthText = vital.getHealthText();
        if (vital.hasAbsorption()) {
            healthText = String.format("%.0f+%.0f", vital.getHealth(), vital.getAbsorptionAmount());
            icon = ICON_ABSORPTION;
            if (!vital.isPoisoned() && !vital.isWithered() && !vital.isBleeding()) {
                barColor = blendColors(barColor, ABSORPTION_COLOR, 0.3f);
            }
        }
        
        drawVitalBar(ctx, tr, x, y, icon, iconColor, vital.getHealthRatio(), healthText, barColor, pulsing);
        
        if (vital.hasAbsorption()) {
            float absorptionRatio = Math.min(1f, vital.getAbsorptionAmount() / vital.getMaxHealth());
            int barX = x + ICON_WIDTH;
            int healthFillWidth = (int) (BAR_WIDTH * vital.getHealthRatio());
            int absorptionWidth = (int) (BAR_WIDTH * absorptionRatio);
            
            int absorptionStartX = barX + healthFillWidth;
            int absorptionEndX = Math.min(barX + BAR_WIDTH, absorptionStartX + absorptionWidth);
            
            if (absorptionEndX > absorptionStartX) {
                ctx.fill(absorptionStartX, y, absorptionEndX, y + BAR_HEIGHT, ABSORPTION_COLOR);
                int highlight = brighten(ABSORPTION_COLOR, 0.3f);
                ctx.fill(absorptionStartX, y, absorptionEndX, y + 1, highlight);
            }
        }
    }
    
    private void renderFoodBar(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y) {
        int barColor;
        int iconColor;
        boolean pulsing;
        
        if (vital.hasHungerEffect()) {
            barColor = getPulseColor(HUNGER_EFFECT_COLOR);
            iconColor = HUNGER_EFFECT_ICON_COLOR;
            pulsing = true;
        } else if (vital.isFoodLow()) {
            barColor = getPulseColor(FOOD_LOW_COLOR);
            iconColor = FOOD_ICON_COLOR;
            pulsing = true;
        } else {
            barColor = FOOD_COLOR;
            iconColor = FOOD_ICON_COLOR;
            pulsing = false;
        }
        
        drawVitalBar(ctx, tr, x, y, ICON_FOOD, iconColor, vital.getFoodRatio(), vital.getFoodText(), barColor, pulsing);
    }
    
    private void renderAirBar(DrawContext ctx, TextRenderer tr, VitalData vital, int x, int y) {
        int barColor;
        int iconColor;
        boolean pulsing;
        
        if (vital.hasWaterBreathing()) {
            barColor = WATER_BREATHING_COLOR;
            iconColor = AIR_ICON_COLOR;
            pulsing = false;
        } else if (vital.isAirLow()) {
            barColor = getPulseColor(AIR_LOW_COLOR);
            iconColor = AIR_ICON_COLOR;
            pulsing = true;
        } else {
            barColor = AIR_COLOR;
            iconColor = AIR_ICON_COLOR;
            pulsing = false;
        }
        
        drawVitalBar(ctx, tr, x, y, ICON_AIR, iconColor, vital.getAirRatio(), vital.getAirText(), barColor, pulsing);
    }
    
    // ================================================================================
    // 바 렌더링
    // ================================================================================
    
    private void drawVitalBar(DrawContext ctx, TextRenderer tr,
                               int x, int y,
                               String icon, int iconColor,
                               float ratio, String value,
                               int barColor, boolean pulsing) {
        
        int iconDisplayColor = pulsing ? getPulseColor(iconColor) : iconColor;
        ctx.drawText(tr, icon, x, y - 1, iconDisplayColor, true);
        
        int barX = x + ICON_WIDTH;
        
        ctx.fill(barX - 1, y - 1, barX + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BORDER_COLOR);
        ctx.fill(barX, y, barX + BAR_WIDTH, y + BAR_HEIGHT, BG_COLOR);
        
        int fillWidth = (int) (BAR_WIDTH * Math.max(0, Math.min(1, ratio)));
        if (fillWidth > 0) {
            ctx.fill(barX, y, barX + fillWidth, y + BAR_HEIGHT, barColor);
            
            int highlight = brighten(barColor, 0.3f);
            ctx.fill(barX, y, barX + fillWidth, y + 1, highlight);
        }
        
        int valueX = barX + BAR_WIDTH + 4;
        ctx.drawText(tr, value, valueX, y - 1, TEXT_WHITE, true);
    }
    
    // ================================================================================
    // 색상 유틸
    // ================================================================================
    
    private int brighten(int color, float amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int)(((color >> 16) & 0xFF) * (1 + amount)));
        int g = Math.min(255, (int)(((color >> 8) & 0xFF) * (1 + amount)));
        int b = Math.min(255, (int)((color & 0xFF) * (1 + amount)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    private int blendColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int)(r1 * (1 - ratio) + r2 * ratio);
        int g = (int)(g1 * (1 - ratio) + g2 * ratio);
        int b = (int)(b1 * (1 - ratio) + b2 * ratio);
        
        return (a1 << 24) | (r << 16) | (g << 8) | b;
    }
    
    private void updatePulse() {
        long now = System.currentTimeMillis();
        float dt = (now - lastTime) / 1000f;
        lastTime = now;
        pulse += dt * 5f;
        if (pulse > Math.PI * 2) pulse -= Math.PI * 2;
    }
    
    private int getPulseColor(int color) {
        float factor = 0.7f + 0.3f * (float)((Math.sin(pulse) + 1) / 2);
        int a = (color >> 24) & 0xFF;
        int r = (int)(((color >> 16) & 0xFF) * factor);
        int g = (int)(((color >> 8) & 0xFF) * factor);
        int b = (int)((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
