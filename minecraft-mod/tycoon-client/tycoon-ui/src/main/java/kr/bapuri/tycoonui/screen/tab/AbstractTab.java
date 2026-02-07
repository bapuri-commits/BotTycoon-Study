package kr.bapuri.tycoonui.screen.tab;

import kr.bapuri.tycoonui.screen.TycoonScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * 탭 컨텐츠의 추상 기본 클래스입니다.
 * 
 * <p>모든 탭은 이 클래스를 상속받아 구현합니다.</p>
 */
public abstract class AbstractTab {
    
    /** 부모 화면 */
    protected final TycoonScreen parent;
    
    /** 컨텐츠 영역 좌표 및 크기 */
    protected final int x, y, width, height;
    
    /** Minecraft 클라이언트 */
    protected final MinecraftClient client;
    
    /** 텍스트 렌더러 */
    protected final TextRenderer textRenderer;
    
    /** 텍스트 색상 */
    protected static final int TEXT_COLOR = 0xFFFFFFFF;
    protected static final int TEXT_GRAY = 0xFFAAAAAA;
    protected static final int TEXT_GOLD = 0xFFFFD700;
    protected static final int TEXT_GREEN = 0xFF00FF00;
    protected static final int TEXT_RED = 0xFFFF0000;
    
    /**
     * 탭을 생성합니다.
     * 
     * @param parent 부모 화면
     * @param x 컨텐츠 영역 X
     * @param y 컨텐츠 영역 Y
     * @param width 컨텐츠 영역 너비
     * @param height 컨텐츠 영역 높이
     */
    public AbstractTab(TycoonScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        this.textRenderer = client.textRenderer;
    }
    
    /**
     * 탭 초기화 시 호출됩니다.
     */
    public abstract void init();
    
    /**
     * 탭 컨텐츠를 렌더링합니다.
     * 
     * @param context 렌더링 컨텍스트
     * @param mouseX 마우스 X
     * @param mouseY 마우스 Y
     * @param delta 델타 시간
     */
    public abstract void render(DrawContext context, int mouseX, int mouseY, float delta);
    
    /**
     * 탭이 활성화될 때 호출됩니다.
     * 
     * <p>서버에 데이터를 요청하는 등의 작업을 수행합니다.</p>
     */
    public void onActivate() {
        // 기본 구현: 아무것도 하지 않음
    }
    
    /**
     * 탭이 비활성화될 때 호출됩니다.
     */
    public void onDeactivate() {
        // 기본 구현: 아무것도 하지 않음
    }
    
    /**
     * 마우스 클릭을 처리합니다.
     * 
     * @return 이벤트를 소비했으면 true
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
    
    /**
     * 마우스 스크롤을 처리합니다.
     * 
     * @return 이벤트를 소비했으면 true
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }
    
    /**
     * 마우스가 컨텐츠 영역 내에 있는지 확인합니다.
     */
    protected boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    
    /**
     * 중앙 정렬된 텍스트를 그립니다.
     */
    protected void drawCenteredText(DrawContext context, String text, int y, int color) {
        int textWidth = textRenderer.getWidth(text);
        int textX = x + (width - textWidth) / 2;
        context.drawText(textRenderer, text, textX, y, color, true);
    }
    
    /**
     * "데이터 없음" 메시지를 표시합니다.
     */
    protected void renderNoData(DrawContext context, String message) {
        drawCenteredText(context, message, y + height / 2 - 4, TEXT_GRAY);
    }
}

