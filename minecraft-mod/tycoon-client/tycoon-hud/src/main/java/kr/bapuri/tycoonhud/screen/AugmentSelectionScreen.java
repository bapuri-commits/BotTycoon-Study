package kr.bapuri.tycoonhud.screen;

import kr.bapuri.tycoonhud.model.AugmentData;
import kr.bapuri.tycoonhud.model.AugmentData.AugmentChoice;
import kr.bapuri.tycoonhud.net.TycoonClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 증강 선택 화면
 * 
 * - Space 키로 열고 닫기
 * - 3개의 카드 형태로 증강 선택지 표시
 * - 리롤 버튼 (각 카드별 1회)
 * - 선택 완료 시 자동으로 닫힘
 * - 시간 제한 없음
 */
public class AugmentSelectionScreen extends Screen {

    // ================================================================================
    // 레이아웃 상수
    // ================================================================================

    private static final float CARD_WIDTH_RATIO = 0.14f;
    private static final float CARD_HEIGHT_RATIO = 0.55f;
    private static final float CARD_GAP_RATIO = 0.02f;
    private static final int CARD_PADDING = 12;
    private static final int REROLL_BTN_HEIGHT = 24;
    private static final int BTN_GAP = 8;

    private static final int MIN_CARD_WIDTH = 150;
    private static final int MAX_CARD_WIDTH = 220;
    private static final int MIN_CARD_HEIGHT = 220;
    private static final int MAX_CARD_HEIGHT = 320;

    // ================================================================================
    // 색상
    // ================================================================================

    // 배경 투명도 조정: 0xE8 → 0xC0 (약 75% 불투명)
    private static final int PANEL_BG_COLOR = 0xC0101825;
    private static final int CARD_BG_COLOR = 0xD0151C28;
    private static final int CARD_HOVER_COLOR = 0xD01A2535;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    private static final int REROLL_BTN_COLOR = 0xFF252535;
    private static final int REROLL_BTN_HOVER = 0xFF353550;
    private static final int REROLL_BTN_DISABLED = 0xFF1A1A22;

    // ================================================================================
    // 필드
    // ================================================================================

    private AugmentData augmentData;
    private final List<CardButton> cardButtons = new ArrayList<>();
    private final List<RerollButton> rerollButtons = new ArrayList<>();
    private final boolean[] rerollUsed = new boolean[4]; // 최대 4개 카드 지원
    
    private int cardWidth;
    private int cardHeight;
    private int cardGap;
    private int startX;
    private int startY;
    private int panelX, panelY, panelWidth, panelHeight;

    // ================================================================================
    // 생성자
    // ================================================================================

    public AugmentSelectionScreen(AugmentData data) {
        super(Text.literal("증강 선택"));
        this.augmentData = data;
    }

    public static AugmentSelectionScreen createDummy() {
        return new AugmentSelectionScreen(createDummyData());
    }

    private static AugmentData createDummyData() {
        String json = """
            {
                "showSelection": true,
                "choices": [
                    {
                        "id": "armor_1",
                        "name": "방어 강화 I",
                        "description": "방어력이 5% 증가합니다.",
                        "tier": "SILVER",
                        "icon": "iron_chestplate"
                    },
                    {
                        "id": "attack_1",
                        "name": "공격 강화 I",
                        "description": "공격력이 5% 증가합니다.",
                        "tier": "SILVER",
                        "icon": "iron_sword"
                    },
                    {
                        "id": "health_1",
                        "name": "체력 강화 I",
                        "description": "최대 체력이 10% 증가합니다.",
                        "tier": "SILVER",
                        "icon": "golden_apple"
                    }
                ],
                "currentHclLevel": 3
            }
            """;
        return new com.google.gson.Gson().fromJson(json, AugmentData.class);
    }

    // ================================================================================
    // 레이아웃 계산
    // ================================================================================

    private void calculateLayout() {
        cardWidth = (int) (width * CARD_WIDTH_RATIO);
        cardHeight = (int) (height * CARD_HEIGHT_RATIO);
        cardGap = (int) (width * CARD_GAP_RATIO);

        cardWidth = Math.max(MIN_CARD_WIDTH, Math.min(MAX_CARD_WIDTH, cardWidth));
        cardHeight = Math.max(MIN_CARD_HEIGHT, Math.min(MAX_CARD_HEIGHT, cardHeight));
        cardGap = Math.max(12, Math.min(35, cardGap));

        int cardCount = Math.min(augmentData.getChoices().size(), 4);
        int totalCardsWidth = cardCount * cardWidth + (cardCount - 1) * cardGap;
        int totalCardsHeight = cardHeight + BTN_GAP + REROLL_BTN_HEIGHT;

        startX = (width - totalCardsWidth) / 2;
        startY = (int) (height * 0.12);
        
        int panelPadding = 25;
        panelWidth = totalCardsWidth + panelPadding * 2;
        panelHeight = totalCardsHeight + 90 + panelPadding; // 타이틀 공간 확보
        panelX = (width - panelWidth) / 2;
        panelY = startY - 35; // 타이틀이 패널 내부에 위치하도록 조정
    }

    // ================================================================================
    // 초기화
    // ================================================================================

    @Override
    protected void init() {
        super.init();
        cardButtons.clear();
        rerollButtons.clear();

        List<AugmentChoice> choices = augmentData.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }

        calculateLayout();

        int cardCount = Math.min(choices.size(), 4);

        for (int i = 0; i < cardCount; i++) {
            AugmentChoice choice = choices.get(i);
            int x = startX + i * (cardWidth + cardGap);

            CardButton cardButton = new CardButton(x, startY, cardWidth, cardHeight, choice, i);
            cardButtons.add(cardButton);
            addDrawableChild(cardButton);

            int rerollY = startY + cardHeight + BTN_GAP;
            final int index = i;
            RerollButton rerollButton = new RerollButton(
                    x, rerollY, cardWidth, REROLL_BTN_HEIGHT,
                    i, () -> onReroll(index)
            );
            rerollButtons.add(rerollButton);
            addDrawableChild(rerollButton);
        }
    }

    // ================================================================================
    // 렌더링
    // ================================================================================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 패널 배경 (약간의 딤 효과)
        context.fill(0, 0, width, height, 0x60000000);
        
        // 패널 테두리
        context.fill(panelX - 2, panelY - 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0xFF1A1A25);
        // 패널 배경
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BG_COLOR);
        // 상단 액센트
        int accentColor = getAccentColorForTier();
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 3, accentColor);

        // 타이틀 (패널 내부, 상단 여백 확보)
        String tierName = getTierDisplayName();
        String title = "§b⚔ 증강 선택 §7(HCL Lv." + augmentData.getCurrentHclLevel() + ") §8- §f" + tierName;
        int titleWidth = textRenderer.getWidth(title);
        int titleY = panelY + 12; // 패널 내부, 상단 액센트 아래
        context.drawText(textRenderer, title, (width - titleWidth) / 2, titleY, TEXT_WHITE, true);

        // 카드 및 버튼 렌더링
        super.render(context, mouseX, mouseY, delta);

        // 하단 안내 문구
        String hint1 = "§7카드를 클릭하여 증강을 선택하세요";
        String hint2 = "§8[1][2][3] 리롤  |  [G/ESC] 닫기";
        int hint1Width = textRenderer.getWidth(hint1);
        int hint2Width = textRenderer.getWidth(hint2);
        int hintY = startY + cardHeight + BTN_GAP + REROLL_BTN_HEIGHT + 18;
        context.drawText(textRenderer, hint1, (width - hint1Width) / 2, hintY, TEXT_GRAY, true);
        context.drawText(textRenderer, hint2, (width - hint2Width) / 2, hintY + 13, 0xFF666666, true);
    }
    
    private int getAccentColorForTier() {
        if (augmentData.getChoices() == null || augmentData.getChoices().isEmpty()) {
            return 0xFFC0C0C0;
        }
        return augmentData.getChoices().get(0).getTierColor();
    }
    
    private String getTierDisplayName() {
        if (augmentData.getChoices() == null || augmentData.getChoices().isEmpty()) {
            return "실버";
        }
        return augmentData.getChoices().get(0).getTierDisplayName();
    }

    // ================================================================================
    // 이벤트 처리
    // ================================================================================

    private void onCardSelected(int index) {
        // 서버로 선택 결과 전송
        TycoonClientState.sendAugmentSelection(index);
        // 선택 완료 - 화면 닫기
        close();
    }

    private void onReroll(int index) {
        if (rerollUsed[index]) {
            return;
        }
        rerollUsed[index] = true;
        TycoonClientState.sendAugmentReroll(index);
        clearAndInit();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // G 또는 ESC: 화면 닫기
        if (keyCode == GLFW.GLFW_KEY_G || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        // 1,2,3,4 키로 리롤
        if (keyCode == GLFW.GLFW_KEY_1 && !rerollUsed[0] && cardButtons.size() > 0) {
            onReroll(0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_2 && !rerollUsed[1] && cardButtons.size() > 1) {
            onReroll(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_3 && !rerollUsed[2] && cardButtons.size() > 2) {
            onReroll(2);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_4 && !rerollUsed[3] && cardButtons.size() > 3) {
            onReroll(3);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true; // ESC로 닫기 허용
    }

    // ================================================================================
    // 카드 버튼 위젯
    // ================================================================================

    private class CardButton extends ButtonWidget {
        private final AugmentChoice choice;
        private final int cardIndex;

        public CardButton(int x, int y, int width, int height, AugmentChoice choice, int index) {
            super(x, y, width, height, Text.literal(""), button -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.choice = choice;
            this.cardIndex = index;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onCardSelected(cardIndex);
        }

        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = isMouseOver(mouseX, mouseY);
            
            int tierColor = choice.getTierColor();
            int tierBorderColor = choice.getTierBorderColor();
            int tierBgColor = choice.getTierBgColor();

            // 테두리 (등급 색상)
            context.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() + height + 2, tierBorderColor);
            
            // 배경
            int bgColor = hovered ? CARD_HOVER_COLOR : CARD_BG_COLOR;
            context.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            
            // 등급 배경 그라데이션
            context.fill(getX(), getY(), getX() + width, getY() + 60, tierBgColor);

            // 아이콘 영역
            int iconSize = 48;
            int iconX = getX() + (width - iconSize) / 2;
            int iconY = getY() + 55;
            context.fill(iconX - 2, iconY - 2, iconX + iconSize + 2, iconY + iconSize + 2, tierBorderColor);
            context.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF000000);

            // 등급 표시
            String tierText = "[" + choice.getTierDisplayName() + "]";
            int tierTextWidth = textRenderer.getWidth(tierText);
            context.drawText(textRenderer, tierText, getX() + (width - tierTextWidth) / 2, iconY + iconSize + 15, tierColor, true);

            // 이름
            int nameY = iconY + iconSize + 30;
            int nameWidth = textRenderer.getWidth(choice.getName());
            context.drawText(textRenderer, choice.getName(), getX() + (width - nameWidth) / 2, nameY, TEXT_WHITE, true);

            // 설명 (줄바꿈)
            int descY = nameY + 18;
            int maxDescWidth = width - 16;
            String desc = choice.getDescription();
            List<String> lines = wrapText(desc, maxDescWidth);
            for (int i = 0; i < Math.min(lines.size(), 4); i++) {
                String line = lines.get(i);
                if (i == 3 && lines.size() > 4) {
                    line = line.substring(0, Math.max(0, line.length() - 3)) + "...";
                }
                context.drawText(textRenderer, line, getX() + 8, descY + i * 11, TEXT_GRAY, false);
            }

            // 호버 시 선택 힌트
            if (hovered) {
                String hint = "▶ 클릭하여 선택";
                int hintWidth = textRenderer.getWidth(hint);
                context.drawText(textRenderer, hint, getX() + (width - hintWidth) / 2, getY() + height - 20, 0xFF00FF88, true);
            }
        }

        private List<String> wrapText(String text, int maxWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();
            
            for (char c : text.toCharArray()) {
                currentLine.append(c);
                if (textRenderer.getWidth(currentLine.toString()) > maxWidth) {
                    String line = currentLine.toString();
                    int lastSpace = line.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        lines.add(line.substring(0, lastSpace));
                        currentLine = new StringBuilder(line.substring(lastSpace + 1));
                    } else {
                        lines.add(line.substring(0, line.length() - 1));
                        currentLine = new StringBuilder(String.valueOf(c));
                    }
                }
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
            }
            return lines;
        }
    }

    // ================================================================================
    // 리롤 버튼 위젯
    // ================================================================================

    private class RerollButton extends ButtonWidget {
        private final int cardIndex;
        private final Runnable onClick;

        public RerollButton(int x, int y, int width, int height, int index, Runnable onClick) {
            super(x, y, width, height, Text.literal("[" + (index + 1) + "] 리롤"), button -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.cardIndex = index;
            this.onClick = onClick;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (!rerollUsed[cardIndex]) {
                onClick.run();
            }
        }

        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean used = rerollUsed[cardIndex];
            boolean hovered = isMouseOver(mouseX, mouseY) && !used;

            int bgColor = used ? REROLL_BTN_DISABLED : (hovered ? REROLL_BTN_HOVER : REROLL_BTN_COLOR);
            int textColor = used ? 0xFF555555 : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);

            // 배경
            context.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            
            // 상단 하이라이트
            if (!used) {
                context.fill(getX(), getY(), getX() + width, getY() + 1, 0x30FFFFFF);
            }

            // 텍스트
            String text = used ? "사용됨" : "[" + (cardIndex + 1) + "] 리롤";
            int textWidth = textRenderer.getWidth(text);
            int textX = getX() + (width - textWidth) / 2;
            int textY = getY() + (height - 8) / 2;
            context.drawText(textRenderer, text, textX, textY, textColor, false);
        }
    }
}
