package kr.bapuri.tycoonui.screen;

import kr.bapuri.tycoonui.screen.tab.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * BotTycoon 통합 GUI 화면입니다.
 * 
 * <h3>레이아웃</h3>
 * <pre>
 * ┌──────────────────────────────────────────────┐
 * │ [경제] [프로필] [직업] [도감] ← 탭 버튼      │
 * ├──────────────────────────────────────────────┤
 * │                                              │
 * │              탭별 컨텐츠 영역                 │
 * │                                              │
 * └──────────────────────────────────────────────┘
 * </pre>
 * 
 * <h3>크기</h3>
 * <p>화면의 약 80%, 중앙 배치</p>
 * 
 * <h3>닫기</h3>
 * <p>ESC 키 또는 백틱(`) 키</p>
 */
public class TycoonScreen extends Screen {
    
    /** GUI 너비 (화면 비율) */
    private static final float WIDTH_RATIO = 0.8f;
    
    /** GUI 높이 (화면 비율) */
    private static final float HEIGHT_RATIO = 0.75f;
    
    /** 탭 버튼 높이 */
    private static final int TAB_HEIGHT = 25;
    
    /** 탭 버튼 너비 */
    private static final int TAB_WIDTH = 60;
    
    /** 배경 색상 */
    private static final int BG_COLOR = 0xE0101010;
    
    /** 탭 영역 배경 색상 */
    private static final int TAB_BG_COLOR = 0xFF1A1A1A;
    
    /** 컨텐츠 영역 배경 색상 */
    private static final int CONTENT_BG_COLOR = 0xFF202020;
    
    /** 현재 선택된 탭 */
    private TabType currentTab;
    
    /** 탭 컨텐츠 렌더러들 */
    private final AbstractTab[] tabs = new AbstractTab[TabType.values().length];
    
    /** GUI 영역 */
    private int guiLeft, guiTop, guiWidth, guiHeight;
    
    /** 컨텐츠 영역 */
    private int contentLeft, contentTop, contentWidth, contentHeight;
    
    /**
     * 지정된 초기 탭으로 화면을 생성합니다.
     * 
     * @param initialTab 초기 탭
     */
    public TycoonScreen(TabType initialTab) {
        super(Text.literal("Tycoon"));
        this.currentTab = initialTab;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // GUI 크기 계산
        guiWidth = (int) (width * WIDTH_RATIO);
        guiHeight = (int) (height * HEIGHT_RATIO);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        
        // 컨텐츠 영역 계산
        contentLeft = guiLeft + 5;
        contentTop = guiTop + TAB_HEIGHT + 10;
        contentWidth = guiWidth - 10;
        contentHeight = guiHeight - TAB_HEIGHT - 15;
        
        // 탭 버튼 생성
        int tabX = guiLeft + 5;
        for (TabType tab : TabType.values()) {
            final TabType tabType = tab;
            ButtonWidget button = ButtonWidget.builder(
                Text.literal(tab.getDisplayName()),
                btn -> selectTab(tabType)
            )
            .dimensions(tabX, guiTop + 5, TAB_WIDTH, TAB_HEIGHT - 10)
            .build();
            
            addDrawableChild(button);
            tabX += TAB_WIDTH + 5;
        }
        
        // 탭 컨텐츠 초기화
        tabs[TabType.ECONOMY.getIndex()] = new EconomyTab(this, contentLeft, contentTop, contentWidth, contentHeight);
        tabs[TabType.PROFILE.getIndex()] = new ProfileTab(this, contentLeft, contentTop, contentWidth, contentHeight);
        tabs[TabType.JOB.getIndex()] = new JobTab(this, contentLeft, contentTop, contentWidth, contentHeight);
        tabs[TabType.CODEX.getIndex()] = new CodexTab(this, contentLeft, contentTop, contentWidth, contentHeight);
        
        // 각 탭 초기화
        for (AbstractTab tab : tabs) {
            if (tab != null) {
                tab.init();
            }
        }
        
        // 현재 탭 활성화
        getCurrentTabContent().onActivate();
    }
    
    /**
     * 탭을 선택합니다.
     * 
     * @param tab 선택할 탭
     */
    public void selectTab(TabType tab) {
        if (currentTab != tab) {
            getCurrentTabContent().onDeactivate();
            currentTab = tab;
            getCurrentTabContent().onActivate();
        }
    }
    
    /**
     * 현재 탭 컨텐츠를 반환합니다.
     */
    private AbstractTab getCurrentTabContent() {
        return tabs[currentTab.getIndex()];
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 배경 어둡게
        renderBackground(context);
        
        // GUI 배경
        context.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, BG_COLOR);
        
        // 탭 영역 배경
        context.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + TAB_HEIGHT, TAB_BG_COLOR);
        
        // 컨텐츠 영역 배경
        context.fill(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight, CONTENT_BG_COLOR);
        
        // 현재 탭 컨텐츠 렌더링
        getCurrentTabContent().render(context, mouseX, mouseY, delta);
        
        // 위젯 렌더링 (탭 버튼 등)
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 현재 탭에 먼저 전달
        if (getCurrentTabContent().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (getCurrentTabContent().mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
    
    @Override
    public boolean shouldPause() {
        return false;  // 게임 일시정지 안 함
    }
    
    /**
     * 현재 선택된 탭을 반환합니다.
     */
    public TabType getCurrentTab() {
        return currentTab;
    }
}
