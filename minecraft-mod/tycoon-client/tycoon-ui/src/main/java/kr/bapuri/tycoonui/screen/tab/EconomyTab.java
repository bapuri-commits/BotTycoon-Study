package kr.bapuri.tycoonui.screen.tab;

import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import kr.bapuri.tycoonui.model.EconomyHistory;
import kr.bapuri.tycoonui.net.UiDataHolder;
import kr.bapuri.tycoonui.net.UiRequestSender;
import kr.bapuri.tycoonui.screen.TycoonScreen;
import net.minecraft.client.gui.DrawContext;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 경제 탭입니다.
 * 
 * <h3>표시 내용</h3>
 * <ul>
 *     <li>BD / BottCoin 큰 글씨로 표시</li>
 *     <li>최근 거래 내역 리스트</li>
 *     <li>수입/지출 색상 구분 (+초록, -빨강)</li>
 * </ul>
 */
public class EconomyTab extends AbstractTab {
    
    /** 거래 내역 스크롤 오프셋 */
    private int scrollOffset = 0;
    
    /** 거래 내역 줄 높이 */
    private static final int LINE_HEIGHT = 14;
    
    /** 날짜 포맷 */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");
    
    public EconomyTab(TycoonScreen parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
    }
    
    @Override
    public void init() {
        // 초기화 로직
    }
    
    @Override
    public void onActivate() {
        // 서버에 거래 내역 요청
        UiRequestSender.requestEconomyHistory();
        scrollOffset = 0;
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        
        if (profile == null) {
            renderNoData(context, "서버 데이터를 기다리는 중...");
            return;
        }
        
        int currentY = y + 10;
        
        // BD 표시 (큰 글씨 효과 - 1.5배 스케일)
        String bdText = String.format("BD: %,d", profile.getBd());
        context.getMatrices().push();
        context.getMatrices().translate(x + 20, currentY, 0);
        context.getMatrices().scale(1.5f, 1.5f, 1f);
        context.drawText(textRenderer, bdText, 0, 0, TEXT_GOLD, true);
        context.getMatrices().pop();
        currentY += 25;
        
        // BC 표시
        String bcText = String.format("BottCoin: %d", profile.getBottcoin());
        context.getMatrices().push();
        context.getMatrices().translate(x + 20, currentY, 0);
        context.getMatrices().scale(1.5f, 1.5f, 1f);
        context.drawText(textRenderer, bcText, 0, 0, 0xFF00CED1, true);
        context.getMatrices().pop();
        currentY += 35;
        
        // 구분선
        context.fill(x + 10, currentY, x + width - 10, currentY + 1, 0xFF444444);
        currentY += 10;
        
        // 거래 내역 타이틀
        context.drawText(textRenderer, "최근 거래 내역", x + 10, currentY, TEXT_COLOR, true);
        currentY += 15;
        
        // 거래 내역
        EconomyHistory history = UiDataHolder.getInstance().getEconomyHistory();
        if (history == null || history.getTransactions().isEmpty()) {
            context.drawText(textRenderer, "거래 내역이 없습니다.", x + 20, currentY, TEXT_GRAY, true);
        } else {
            List<EconomyHistory.Transaction> transactions = history.getTransactions();
            int visibleCount = (height - (currentY - y) - 10) / LINE_HEIGHT;
            
            for (int i = scrollOffset; i < Math.min(scrollOffset + visibleCount, transactions.size()); i++) {
                EconomyHistory.Transaction tx = transactions.get(i);
                
                // 시간
                String timeStr = DATE_FORMAT.format(new Date(tx.getTime() * 1000));
                context.drawText(textRenderer, timeStr, x + 10, currentY, TEXT_GRAY, true);
                
                // 금액 (수입=초록, 지출=빨강)
                String amountStr = tx.getAmount() >= 0 
                    ? String.format("+%,d", tx.getAmount())
                    : String.format("%,d", tx.getAmount());
                int amountColor = tx.getAmount() >= 0 ? TEXT_GREEN : TEXT_RED;
                context.drawText(textRenderer, amountStr, x + 80, currentY, amountColor, true);
                
                // 사유
                context.drawText(textRenderer, tx.getReason(), x + 150, currentY, TEXT_COLOR, true);
                
                currentY += LINE_HEIGHT;
            }
        }
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        
        EconomyHistory history = UiDataHolder.getInstance().getEconomyHistory();
        if (history == null) return false;
        
        int maxScroll = Math.max(0, history.getTransactions().size() - 10);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) amount));
        return true;
    }
}
