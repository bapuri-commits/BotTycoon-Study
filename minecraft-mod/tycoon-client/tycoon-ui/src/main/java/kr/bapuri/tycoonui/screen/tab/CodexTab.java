package kr.bapuri.tycoonui.screen.tab;

import kr.bapuri.tycoonui.model.CodexData;
import kr.bapuri.tycoonui.net.UiDataHolder;
import kr.bapuri.tycoonui.net.UiRequestSender;
import kr.bapuri.tycoonui.screen.TycoonScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 도감 탭입니다.
 * 
 * <h3>표시 내용</h3>
 * <ul>
 *     <li>왼쪽 사이드바: 카테고리 목록 (스크롤 지원)</li>
 *     <li>오른쪽: 아이템 그리드 또는 카테고리별 진행도</li>
 * </ul>
 */
public class CodexTab extends AbstractTab {
    
    /** 사이드바 너비 */
    private static final int SIDEBAR_WIDTH = 130;
    
    /** 아이템 셀 크기 */
    private static final int CELL_SIZE = 24;
    
    /** 셀 간격 */
    private static final int CELL_GAP = 4;
    
    /** 카테고리 한 줄 높이 */
    private static final int CATEGORY_HEIGHT = 16;
    
    /** 선택된 카테고리 인덱스 */
    private int selectedCategory = 0;
    
    /** 아이템 스크롤 오프셋 */
    private int scrollOffset = 0;
    
    /** 카테고리 스크롤 오프셋 */
    private int categoryScrollOffset = 0;
    
    public CodexTab(TycoonScreen parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
    }
    
    @Override
    public void init() {
    }
    
    @Override
    public void onActivate() {
        // 도감 요약 데이터 요청 (카테고리 정보만)
        UiRequestSender.requestCodexData();
        selectedCategory = 0;
        scrollOffset = 0;
        categoryScrollOffset = 0;
        
        // 카테고리 아이템 캐시 초기화 (새로고침)
        UiDataHolder.getInstance().clearCategoryCache();
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CodexData codex = UiDataHolder.getInstance().getCodexData();
        
        if (codex == null) {
            renderNoData(context, "도감 데이터를 불러오는 중...");
            return;
        }
        
        List<CodexData.Category> categories = codex.getCategories();
        if (categories.isEmpty()) {
            renderNoData(context, "등록된 도감 항목이 없습니다.");
            return;
        }
        
        // 전체 진행도
        String progressText = String.format("전체 진행도: %d / %d (%.1f%%)", 
            codex.getCollectedCount(), codex.getTotalCount(), codex.getProgressPercent());
        context.drawText(textRenderer, progressText, x + 10, y + 5, TEXT_GOLD, true);
        
        // [Phase 4] 다음 마일스톤 표시
        int headerHeight = 20;
        var nextMilestone = codex.getNextMilestone();
        if (nextMilestone != null) {
            headerHeight = 35;
            String milestoneText = String.format("§7다음 마일스톤: §e%d§7개 (보상: §d%dBC§7)", 
                nextMilestone.getTarget(), nextMilestone.getBottcoinReward());
            context.drawText(textRenderer, milestoneText, x + 10, y + 17, TEXT_GRAY, true);
            
            // 미니 진행도 바
            int barX = x + width - 110;
            int barY = y + 17;
            int barW = 100;
            int barH = 8;
            context.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
            int fillW = (int) (barW * nextMilestone.getProgress());
            if (fillW > 0) {
                context.fill(barX, barY, barX + fillW, barY + barH, 0xFFAA00AA);
            }
        }
        
        int contentY = y + headerHeight;
        int contentHeight = height - headerHeight - 5;
        
        // ===== 왼쪽 사이드바 (카테고리 목록 - 스크롤 지원) =====
        renderCategorySidebar(context, codex, categories, contentY, contentHeight, mouseX, mouseY);
        
        // ===== 오른쪽 영역 =====
        int gridX = x + SIDEBAR_WIDTH + 10;
        int gridY = contentY + 5;
        int gridWidth = width - SIDEBAR_WIDTH - 20;
        int gridHeight = contentHeight - 10;
        
        // 배경
        context.fill(gridX - 5, gridY - 5, gridX + gridWidth + 5, gridY + gridHeight + 5, 0xFF252525);
        
        if (selectedCategory < categories.size()) {
            CodexData.Category currentCat = categories.get(selectedCategory);
            String categoryName = currentCat.getName();
            
            // 1. 캐시에서 아이템 확인
            List<CodexData.Item> items = UiDataHolder.getInstance().getCategoryItems(categoryName);
            
            // 2. 캐시에 없으면 서버에 요청
            if (items == null) {
                // 이미 요청 중인지 확인
                String loadingCat = UiDataHolder.getInstance().getLoadingCategory();
                if (!categoryName.equals(loadingCat)) {
                    UiDataHolder.getInstance().setLoadingCategory(categoryName);
                    UiRequestSender.requestCodexCategory(categoryName);
                }
                
                // 로딩 중 표시
                String loadingText = "'" + categoryName + "' 아이템 로딩 중...";
                int textX = gridX + (gridWidth - textRenderer.getWidth(loadingText)) / 2;
                int textY = gridY + gridHeight / 2;
                context.drawText(textRenderer, loadingText, textX, textY, TEXT_GRAY, false);
                
                // 로딩 중에도 진행도 바 표시
                renderCategoryProgress(context, codex, currentCat, gridX, gridY + 20, gridWidth, gridHeight - 20);
                return;
            }
            
            // 3. items가 있으면 아이템 그리드, 비어있으면 진행도 바
            if (!items.isEmpty()) {
                renderItemGrid(context, items, currentCat, gridX, gridY, gridWidth, gridHeight, mouseX, mouseY);
            } else {
                renderCategoryProgress(context, codex, currentCat, gridX, gridY, gridWidth, gridHeight);
            }
        }
    }
    
    /**
     * 카테고리 사이드바 렌더링 (스크롤 지원)
     */
    private void renderCategorySidebar(DrawContext context, CodexData codex, 
            List<CodexData.Category> categories, int contentY, int contentHeight, int mouseX, int mouseY) {
        
        // 사이드바 배경
        context.fill(x, contentY, x + SIDEBAR_WIDTH, contentY + contentHeight, 0xFF1A1A1A);
        
        // 표시 가능한 카테고리 수
        int visibleCount = (contentHeight - 10) / CATEGORY_HEIGHT;
        int maxScrollOffset = Math.max(0, categories.size() - visibleCount);
        categoryScrollOffset = Math.max(0, Math.min(maxScrollOffset, categoryScrollOffset));
        
        int catY = contentY + 5;
        for (int i = categoryScrollOffset; i < categories.size(); i++) {
            // 영역 초과 시 중단
            if (catY + CATEGORY_HEIGHT > contentY + contentHeight - 5) break;
            
            CodexData.Category cat = categories.get(i);
            boolean isSelected = (i == selectedCategory);
            
            // 선택된 카테고리 하이라이트
            if (isSelected) {
                context.fill(x, catY - 2, x + SIDEBAR_WIDTH, catY + 12, 0xFF3A3A3A);
            }
            
            // 카테고리명 + 진행도
            String catText = String.format("%s (%d/%d)", cat.getName(), cat.getCollected(), cat.getTotal());
            String completeMarker = cat.isComplete() ? " ✓" : "";
            
            // 텍스트가 너무 길면 자르기
            String displayText = catText + completeMarker;
            if (textRenderer.getWidth(displayText) > SIDEBAR_WIDTH - 20) {
                String shortName = cat.getName();
                if (shortName.length() > 6) {
                    shortName = shortName.substring(0, 6) + "..";
                }
                displayText = String.format("%s %d/%d%s", shortName, cat.getCollected(), cat.getTotal(), completeMarker);
            }
            
            int color = cat.isComplete() ? 0xFF4AFF4A : (isSelected ? TEXT_COLOR : TEXT_GRAY);
            context.drawText(textRenderer, displayText, x + 5, catY, color, false);
            
            catY += CATEGORY_HEIGHT;
        }
        
        // 스크롤 인디케이터 (위)
        if (categoryScrollOffset > 0) {
            context.fill(x + SIDEBAR_WIDTH - 15, contentY + 2, x + SIDEBAR_WIDTH - 3, contentY + 12, 0xFF2A2A2A);
            context.drawText(textRenderer, "▲", x + SIDEBAR_WIDTH - 13, contentY + 2, TEXT_GRAY, false);
        }
        // 스크롤 인디케이터 (아래)
        if (categoryScrollOffset < maxScrollOffset) {
            context.fill(x + SIDEBAR_WIDTH - 15, contentY + contentHeight - 14, x + SIDEBAR_WIDTH - 3, contentY + contentHeight - 4, 0xFF2A2A2A);
            context.drawText(textRenderer, "▼", x + SIDEBAR_WIDTH - 13, contentY + contentHeight - 12, TEXT_GRAY, false);
        }
    }
    
    /**
     * 아이템 그리드 렌더링 (items 데이터가 있을 때)
     */
    private void renderItemGrid(DrawContext context, List<CodexData.Item> items, 
            CodexData.Category category, int gridX, int gridY, int gridWidth, int gridHeight, int mouseX, int mouseY) {
        
        // 카테고리 타이틀
        String title = category.getName() + " (" + category.getCollected() + "/" + category.getTotal() + ")";
        context.drawText(textRenderer, title, gridX, gridY, TEXT_GOLD, true);
        
        int itemGridY = gridY + 15;
        int itemGridHeight = gridHeight - 20;
        
        // 그리드 레이아웃 계산
        int cols = gridWidth / (CELL_SIZE + CELL_GAP);
        if (cols < 1) cols = 1;
        int rows = itemGridHeight / (CELL_SIZE + CELL_GAP);
        int visibleItems = cols * rows;
        
        int maxScroll = Math.max(0, items.size() - visibleItems);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        
        int itemX = gridX;
        int itemY = itemGridY;
        int col = 0;
        
        for (int i = scrollOffset; i < items.size() && itemY + CELL_SIZE <= gridY + gridHeight; i++) {
            CodexData.Item item = items.get(i);
            
            // [Phase 5] 호버 체크
            boolean isHovered = mouseX >= itemX && mouseX < itemX + CELL_SIZE 
                             && mouseY >= itemY && mouseY < itemY + CELL_SIZE;
            
            // [Phase 5] 아이템 셀 색상 (호버 시 밝게)
            int cellColor;
            if (item.isCollected()) {
                cellColor = isHovered ? 0xFF3A7A3A : 0xFF2A5A2A; // 수집 완료 - 녹색
            } else {
                cellColor = isHovered ? 0xFF5A4A4A : 0xFF3A2A2A; // 미수집 - 빨간색/회색
            }
            context.fill(itemX, itemY, itemX + CELL_SIZE, itemY + CELL_SIZE, cellColor);
            
            // [Phase 5] 테두리 (호버 시 강조)
            int borderColor;
            if (isHovered) {
                borderColor = item.isCollected() ? 0xFFFFD700 : 0xFFAAAAFF; // 골드/하늘색 테두리
            } else {
                borderColor = item.isCollected() ? 0xFF4A8A4A : 0xFF5A3A3A;
            }
            drawBorder(context, itemX, itemY, CELL_SIZE, CELL_SIZE, borderColor);
            
            // 아이템 아이콘 렌더링
            ItemStack itemStack = getItemStackFromMaterial(item.getIconMaterial());
            if (!itemStack.isEmpty()) {
                // 아이템 스택 렌더링 (16x16 기본 크기)
                int iconX = itemX + (CELL_SIZE - 16) / 2;
                int iconY = itemY + (CELL_SIZE - 16) / 2;
                context.drawItem(itemStack, iconX, iconY);
                
                // 미수집 아이템은 어둡게 오버레이 (호버 시 제외)
                if (!item.isCollected() && !isHovered) {
                    context.fill(itemX + 1, itemY + 1, itemX + CELL_SIZE - 1, itemY + CELL_SIZE - 1, 0x80000000);
                }
            } else {
                // 아이콘을 못 찾으면 첫 글자 표시 (폴백)
                String initial = item.getName().length() > 0 ? item.getName().substring(0, 1) : "?";
                int textX = itemX + (CELL_SIZE - textRenderer.getWidth(initial)) / 2;
                int textY = itemY + (CELL_SIZE - 8) / 2;
                context.drawText(textRenderer, initial, textX, textY, 
                    item.isCollected() ? TEXT_COLOR : TEXT_GRAY, false);
            }
            
            // [Phase 5] 수집 완료 체크마크
            if (item.isCollected()) {
                context.drawText(textRenderer, "✓", itemX + CELL_SIZE - 8, itemY + 1, 0xFF4AFF4A, true);
            }
            
            // 다음 위치
            col++;
            if (col >= cols) {
                col = 0;
                itemX = gridX;
                itemY += CELL_SIZE + CELL_GAP;
            } else {
                itemX += CELL_SIZE + CELL_GAP;
            }
        }
        
        // 툴팁 렌더링
        renderItemTooltip(context, items, gridX, itemGridY, cols, mouseX, mouseY, gridWidth);
        
        // 스크롤 인디케이터
        if (items.size() > visibleItems) {
            String scrollInfo = String.format("스크롤: %d-%d / %d", 
                scrollOffset + 1, Math.min(scrollOffset + visibleItems, items.size()), items.size());
            context.drawText(textRenderer, scrollInfo, gridX, gridY + gridHeight - 10, TEXT_GRAY, false);
        }
    }
    
    /**
     * 아이템 툴팁 렌더링
     * 
     * <p>[Phase 2 버그수정] 툴팁 위치 개선:</p>
     * <ul>
     *   <li>마우스 오른쪽에 표시 (아이템 가림 방지)</li>
     *   <li>화면 밖으로 나가면 왼쪽/아래로 조정</li>
     * </ul>
     */
    private void renderItemTooltip(DrawContext context, List<CodexData.Item> items, 
            int gridX, int gridY, int cols, int mouseX, int mouseY, int gridWidth) {
        
        for (int i = scrollOffset; i < items.size(); i++) {
            int relIndex = i - scrollOffset;
            int row = relIndex / cols;
            int col = relIndex % cols;
            int ix = gridX + col * (CELL_SIZE + CELL_GAP);
            int iy = gridY + row * (CELL_SIZE + CELL_GAP);
            
            if (mouseX >= ix && mouseX < ix + CELL_SIZE && mouseY >= iy && mouseY < iy + CELL_SIZE) {
                CodexData.Item item = items.get(i);
                
                String line1 = item.getName() + (item.isCollected() ? " ✓" : "");
                String line2 = item.isCollected() ? "" : 
                    String.format("필요: %d개 | 보상: %dBC", item.getRequiredCount(), item.getReward());
                String line3 = item.isCollected() ? "" : "§7클릭하여 등록";
                
                int tooltipWidth = Math.max(textRenderer.getWidth(line1), 
                    Math.max(line2.isEmpty() ? 0 : textRenderer.getWidth(line2),
                             line3.isEmpty() ? 0 : textRenderer.getWidth(line3))) + 14;
                int tooltipHeight = line2.isEmpty() ? 18 : 40;
                
                // [Phase 2] 툴팁 위치 개선 - 마우스 오른쪽에 표시
                int tooltipX = mouseX + 15;
                int tooltipY = mouseY - 8;
                
                // 화면 오른쪽 경계 체크 → 왼쪽에 표시
                if (tooltipX + tooltipWidth > x + width - 5) {
                    tooltipX = mouseX - tooltipWidth - 10;
                }
                
                // 화면 상단 경계 체크 → 아래에 표시
                if (tooltipY < y + 5) {
                    tooltipY = mouseY + 20;
                }
                
                // 화면 하단 경계 체크 → 위로 조정
                if (tooltipY + tooltipHeight > y + height - 5) {
                    tooltipY = y + height - tooltipHeight - 5;
                }
                
                context.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0100010);
                drawBorder(context, tooltipX, tooltipY, tooltipWidth, tooltipHeight, 0xFF666688);
                
                context.drawText(textRenderer, line1, tooltipX + 6, tooltipY + 4, 
                    item.isCollected() ? 0xFF4AFF4A : TEXT_COLOR, true);
                if (!line2.isEmpty()) {
                    context.drawText(textRenderer, line2, tooltipX + 6, tooltipY + 16, TEXT_GRAY, false);
                    context.drawText(textRenderer, line3, tooltipX + 6, tooltipY + 28, 0xFF88AAFF, false);
                }
                break;
            }
        }
    }
    
    /**
     * 카테고리별 진행도 바를 렌더링합니다 (items가 빈 배열일 때)
     */
    private void renderCategoryProgress(DrawContext context, CodexData codex, 
            CodexData.Category selectedCat, int areaX, int areaY, int areaWidth, int areaHeight) {
        
        List<CodexData.Category> categories = codex.getCategories();
        
        // 선택된 카테고리 정보 (큰 진행도 바)
        String title = "📖 " + selectedCat.getName();
        context.drawText(textRenderer, title, areaX + 10, areaY + 5, TEXT_GOLD, true);
        
        int bigBarY = areaY + 25;
        int bigBarWidth = areaWidth - 40;
        int bigBarHeight = 16;
        
        context.fill(areaX + 10, bigBarY, areaX + 10 + bigBarWidth, bigBarY + bigBarHeight, 0xFF333333);
        float progress = selectedCat.getProgress();
        int fillWidth = (int) (bigBarWidth * progress);
        if (fillWidth > 0) {
            int fillColor = selectedCat.isComplete() ? 0xFF4AFF4A : 0xFF5588FF;
            context.fill(areaX + 10, bigBarY, areaX + 10 + fillWidth, bigBarY + bigBarHeight, fillColor);
        }
        
        String percentText = String.format("%d / %d (%.1f%%)", 
            selectedCat.getCollected(), selectedCat.getTotal(), progress * 100);
        int textX = areaX + 10 + (bigBarWidth - textRenderer.getWidth(percentText)) / 2;
        context.drawText(textRenderer, percentText, textX, bigBarY + 4, TEXT_COLOR, true);
        
        // 구분선
        int dividerY = areaY + 55;
        context.fill(areaX + 10, dividerY, areaX + areaWidth - 10, dividerY + 1, 0xFF444444);
        
        // 전체 카테고리 미니 진행도
        int miniY = dividerY + 10;
        context.drawText(textRenderer, "전체 카테고리", areaX + 10, miniY, TEXT_GRAY, false);
        miniY += 15;
        
        int barWidth = areaWidth - 40;
        int barHeight = 10;
        int displayedCount = 0;
        
        for (CodexData.Category cat : categories) {
            if (miniY + barHeight + 8 > areaY + areaHeight - 30) break;
            displayedCount++;
            
            // 카테고리명
            String catName = cat.getName();
            if (textRenderer.getWidth(catName) > 55) {
                catName = catName.substring(0, Math.min(5, catName.length())) + "..";
            }
            int nameColor = cat.isComplete() ? 0xFF4AFF4A : TEXT_GRAY;
            context.drawText(textRenderer, catName, areaX + 10, miniY, nameColor, false);
            
            // 미니 진행도 바
            int miniBarX = areaX + 65;
            int miniBarWidth = barWidth - 90;
            context.fill(miniBarX, miniY, miniBarX + miniBarWidth, miniY + barHeight, 0xFF333333);
            float catProgress = cat.getProgress();
            int fillW = (int) (miniBarWidth * catProgress);
            if (fillW > 0) {
                int fillColor = cat.isComplete() ? 0xFF4AFF4A : 0xFF5588FF;
                context.fill(miniBarX, miniY, miniBarX + fillW, miniY + barHeight, fillColor);
            }
            
            // 퍼센트
            String pct = String.format("%d/%d", cat.getCollected(), cat.getTotal());
            context.drawText(textRenderer, pct, miniBarX + miniBarWidth + 5, miniY + 1, TEXT_GRAY, false);
            
            miniY += barHeight + 6;
        }
        
        // 더 있으면 표시
        if (displayedCount < categories.size()) {
            context.drawText(textRenderer, 
                String.format("... 외 %d개 카테고리", categories.size() - displayedCount), 
                areaX + 10, miniY, TEXT_GRAY, false);
        }
        
        // 상세 보기 안내
        int hintY = areaY + areaHeight - 22;
        context.fill(areaX + 10, hintY - 3, areaX + areaWidth - 10, hintY + 13, 0xFF3A3A5A);
        String hint = "💡 상세 보기: /codex";
        int hintX = areaX + (areaWidth - textRenderer.getWidth(hint)) / 2;
        context.drawText(textRenderer, hint, hintX, hintY, 0xFFAAAAFF, false);
    }
    
    /**
     * 테두리를 그립니다.
     */
    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        CodexData codex = UiDataHolder.getInstance().getCodexData();
        if (codex == null) return false;
        
        // [Phase 4] render()와 동일한 헤더 높이 계산
        int headerHeight = codex.getNextMilestone() != null ? 35 : 20;
        int contentY = y + headerHeight;
        int contentHeight = height - headerHeight - 5;
        
        // 카테고리 클릭
        int catY = contentY + 5;
        for (int i = categoryScrollOffset; i < codex.getCategories().size(); i++) {
            if (catY + CATEGORY_HEIGHT > contentY + contentHeight - 5) break;
            
            if (mouseX >= x && mouseX < x + SIDEBAR_WIDTH &&
                mouseY >= catY - 2 && mouseY < catY + 12) {
                selectedCategory = i;
                scrollOffset = 0;
                return true;
            }
            catY += CATEGORY_HEIGHT;
        }
        
        // [2026-01-24] 아이템 그리드 클릭 - 도감 등록 시도
        if (selectedCategory < codex.getCategories().size()) {
            CodexData.Category currentCat = codex.getCategories().get(selectedCategory);
            List<CodexData.Item> items = UiDataHolder.getInstance().getCategoryItems(currentCat.getName());
            
            if (items != null && !items.isEmpty()) {
                int gridX = x + SIDEBAR_WIDTH + 10;
                int gridY = contentY + 5 + 15; // 타이틀 아래
                int gridWidth = width - SIDEBAR_WIDTH - 20;
                int gridHeight = contentHeight - 25;
                int cols = gridWidth / (CELL_SIZE + CELL_GAP);
                if (cols < 1) cols = 1;
                
                // 클릭된 아이템 찾기
                for (int i = scrollOffset; i < items.size(); i++) {
                    int relIndex = i - scrollOffset;
                    int row = relIndex / cols;
                    int col = relIndex % cols;
                    int ix = gridX + col * (CELL_SIZE + CELL_GAP);
                    int iy = gridY + row * (CELL_SIZE + CELL_GAP);
                    
                    // 화면 초과 시 중단
                    if (iy + CELL_SIZE > gridY + gridHeight) break;
                    
                    if (mouseX >= ix && mouseX < ix + CELL_SIZE &&
                        mouseY >= iy && mouseY < iy + CELL_SIZE) {
                        
                        CodexData.Item clickedItem = items.get(i);
                        
                        // 미수집 아이템만 등록 시도
                        if (!clickedItem.isCollected()) {
                            // 서버에 등록 요청
                            UiRequestSender.registerCodexItem(clickedItem.getIconMaterial());
                            return true;
                        } else {
                            // 이미 등록됨 메시지 (클라이언트만)
                            // TODO: 플레이어에게 메시지 표시
                        }
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        CodexData codex = UiDataHolder.getInstance().getCodexData();
        if (codex == null) return false;
        
        // [Phase 4] render()와 동일한 헤더 높이 계산
        int headerHeight = codex.getNextMilestone() != null ? 35 : 20;
        int contentY = y + headerHeight;
        int contentHeight = height - headerHeight - 5;
        
        // 사이드바 영역에서 스크롤 시 카테고리 스크롤
        if (mouseX >= x && mouseX < x + SIDEBAR_WIDTH) {
            int visibleCount = (contentHeight - 10) / CATEGORY_HEIGHT;
            int maxScroll = Math.max(0, codex.getCategories().size() - visibleCount);
            categoryScrollOffset = Math.max(0, Math.min(maxScroll, categoryScrollOffset - (int) amount));
            return true;
        }
        
        // 오른쪽 영역에서 스크롤 시 아이템 스크롤
        if (mouseX >= x + SIDEBAR_WIDTH && selectedCategory < codex.getCategories().size()) {
            CodexData.Category cat = codex.getCategories().get(selectedCategory);
            
            // 캐시에서 아이템 가져오기
            List<CodexData.Item> items = UiDataHolder.getInstance().getCategoryItems(cat.getName());
            
            if (items != null && !items.isEmpty()) {
                int gridWidth = width - SIDEBAR_WIDTH - 20;
                int gridHeight = contentHeight - 25;
                int cols = gridWidth / (CELL_SIZE + CELL_GAP);
                int rows = gridHeight / (CELL_SIZE + CELL_GAP);
                int visibleItems = cols * rows;
                int maxScroll = Math.max(0, items.size() - visibleItems);
                
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) amount * cols));
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Material 이름을 ItemStack으로 변환합니다.
     * 
     * @param materialName Bukkit Material 이름 (예: "DIAMOND", "RAW_IRON")
     * @return ItemStack 또는 EMPTY
     */
    private ItemStack getItemStackFromMaterial(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return ItemStack.EMPTY;
        }
        
        try {
            // Bukkit Material 이름을 Minecraft ID로 변환 (대문자 → 소문자)
            String itemId = materialName.toLowerCase();
            
            Identifier id = new Identifier("minecraft", itemId);
            Item item = Registries.ITEM.get(id);
            
            // AIR가 아닌지 확인 (없는 아이템은 AIR로 반환됨)
            if (item != null && !item.equals(Registries.ITEM.get(new Identifier("minecraft", "air")))) {
                return new ItemStack(item);
            }
        } catch (Exception e) {
            // 변환 실패 시 무시
        }
        
        return ItemStack.EMPTY;
    }
}
