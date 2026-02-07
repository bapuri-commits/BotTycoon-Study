package kr.bapuri.tycoonui.screen.tab;

import kr.bapuri.tycoonhud.model.JobData;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import kr.bapuri.tycoonui.model.JobDetail;
import kr.bapuri.tycoonui.net.UiDataHolder;
import kr.bapuri.tycoonui.net.UiRequestSender;
import kr.bapuri.tycoonui.screen.TycoonScreen;
import net.minecraft.client.gui.DrawContext;

/**
 * 직업 탭입니다.
 * 
 * <h3>표시 내용</h3>
 * <ul>
 *     <li>주 직업 카드 (직업명, 레벨, 등급, XP 바)</li>
 *     <li>승급 조건 체크리스트</li>
 *     <li>[승급] 버튼</li>
 *     <li>부 직업 (있으면)</li>
 *     <li>직업 미선택 시 선택 가능한 직업 목록</li>
 * </ul>
 */
public class JobTab extends AbstractTab {
    
    /** XP 바 색상 */
    private static final int XP_BAR_BG = 0xFF333333;
    private static final int XP_BAR_FG = 0xFF00AA00;
    
    /** 직업 카드 색상 */
    private static final int CARD_BG = 0xFF2A2A2A;
    private static final int CARD_HOVER = 0xFF3A3A3A;
    private static final int CARD_ACCENT_MINER = 0xFF8B4513;      // 갈색 (광부)
    private static final int CARD_ACCENT_FARMER = 0xFF228B22;     // 녹색 (농부)
    private static final int CARD_ACCENT_FISHER = 0xFF1E90FF;     // 파랑 (어부)
    
    /** 선택 가능한 Tier 1 직업 정보 */
    private static final JobInfo[] AVAILABLE_JOBS = {
        new JobInfo("MINER", "⛏ 광부", "광물을 채굴하여 판매합니다.", CARD_ACCENT_MINER),
        new JobInfo("FARMER", "🌾 농부", "농작물을 재배하여 판매합니다.", CARD_ACCENT_FARMER),
        new JobInfo("FISHER", "🎣 어부", "낚시로 물고기를 잡아 판매합니다.", CARD_ACCENT_FISHER)
    };
    
    /** 직업 정보 내부 클래스 */
    private record JobInfo(String id, String displayName, String description, int accentColor) {}
    
    // ========================================================================
    // [Phase 5] 애니메이션 상태
    // ========================================================================
    
    /** 부드러운 XP 바 진행도 (애니메이션용) */
    private float displayedProgress = 0f;
    
    /** 이전 레벨 (레벨업 감지용) */
    private int lastLevel = -1;
    
    /** 레벨업 애니메이션 틱 (0이면 비활성) */
    private int levelUpAnimTick = 0;
    
    /** 레벨업 애니메이션 지속 시간 (틱) */
    private static final int LEVEL_UP_ANIM_DURATION = 40;
    
    /** XP 바 애니메이션 속도 (높을수록 빠름) */
    private static final float XP_BAR_ANIM_SPEED = 0.08f;
    
    public JobTab(TycoonScreen parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
    }
    
    @Override
    public void init() {
        // 승급 버튼은 조건 충족 시에만 활성화
    }
    
    @Override
    public void onActivate() {
        // 서버에 직업 상세 정보 요청
        UiRequestSender.requestJobDetail();
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        
        // 프로필 데이터 없음 (서버 연결 전)
        if (profile == null) {
            renderNoData(context, "프로필 로딩 중...");
            return;
        }
        
        // 직업 미선택 → 선택 안내 표시
        if (profile.getPrimaryJob() == null) {
            renderJobSelection(context, mouseX, mouseY);
            return;
        }
        
        JobData job = profile.getPrimaryJob();
        JobDetail detail = UiDataHolder.getInstance().getJobDetail();
        
        int currentY = y + 15;
        int cardX = x + 20;
        int cardWidth = width - 40;
        
        // ===== 주 직업 카드 =====
        // 카드 배경
        context.fill(cardX, currentY, cardX + cardWidth, currentY + 120, 0xFF2A2A2A);
        context.fill(cardX, currentY, cardX + cardWidth, currentY + 2, 0xFF4A9FD4); // 상단 액센트
        currentY += 10;
        
        // 직업명 + 등급
        String titleText = String.format("%s - %s", job.getLocalizedType(), job.getGradeTitle());
        context.getMatrices().push();
        context.getMatrices().translate(cardX + 15, currentY, 0);
        context.getMatrices().scale(1.3f, 1.3f, 1f);
        context.drawText(textRenderer, titleText, 0, 0, TEXT_COLOR, true);
        context.getMatrices().pop();
        currentY += 25;
        
        // 레벨
        context.drawText(textRenderer, "Lv. " + job.getLevel(), cardX + 15, currentY, TEXT_GOLD, true);
        currentY += 15;
        
        // XP 바
        int barX = cardX + 15;
        int barWidth = cardWidth - 30;
        int barHeight = 12;
        
        // [Phase 5] 레벨업 감지
        int currentLevel = job.getLevel();
        if (lastLevel >= 0 && currentLevel > lastLevel) {
            levelUpAnimTick = LEVEL_UP_ANIM_DURATION;
            displayedProgress = 0f; // 레벨업 시 프로그레스 리셋
        }
        lastLevel = currentLevel;
        
        // [Phase 5] 부드러운 XP 바 애니메이션
        float targetProgress = job.getXpProgress();
        if (displayedProgress < targetProgress) {
            displayedProgress += XP_BAR_ANIM_SPEED;
            if (displayedProgress > targetProgress) displayedProgress = targetProgress;
        } else if (displayedProgress > targetProgress) {
            displayedProgress = targetProgress; // 레벨업 후 즉시 타겟으로
        }
        
        // 배경
        context.fill(barX, currentY, barX + barWidth, currentY + barHeight, XP_BAR_BG);
        
        // [Phase 5] 레벨업 효과 - 배경 글로우
        if (levelUpAnimTick > 0) {
            float glowIntensity = (float) levelUpAnimTick / LEVEL_UP_ANIM_DURATION;
            int glowAlpha = (int) (100 * glowIntensity);
            int glowColor = (glowAlpha << 24) | 0xFFD700; // 골드 글로우
            context.fill(barX - 2, currentY - 2, barX + barWidth + 2, currentY + barHeight + 2, glowColor);
            levelUpAnimTick--;
        }
        
        // 채움 (애니메이션된 프로그레스 사용)
        int fillWidth = (int) (barWidth * displayedProgress);
        if (fillWidth > 0) {
            // [Phase 5] 레벨업 중이면 밝은 색상
            int barColor = levelUpAnimTick > 0 ? 0xFF44FF44 : XP_BAR_FG;
            context.fill(barX, currentY, barX + fillWidth, currentY + barHeight, barColor);
        }
        
        // XP 텍스트
        String xpText;
        if (job.isMaxLevel()) {
            xpText = "MAX LEVEL";
        } else {
            xpText = String.format("%,d / %,d XP (%.1f%%)", 
                job.getCurrentXp(), job.getNextLevelXp(), targetProgress * 100);
        }
        int xpTextWidth = textRenderer.getWidth(xpText);
        int xpTextColor = job.isMaxLevel() ? TEXT_GOLD : TEXT_COLOR;
        context.drawText(textRenderer, xpText, barX + (barWidth - xpTextWidth) / 2, currentY + 2, xpTextColor, true);
        currentY += barHeight + 15;
        
        // ===== 승급 조건 =====
        // 최고 등급(4차)이면 승급 정보 대신 달성 메시지 표시
        boolean isMaxGrade = job.getGrade() >= 4;
        
        if (isMaxGrade) {
            context.drawText(textRenderer, "§6✦ 최고 등급 달성!", cardX + 15, currentY, TEXT_GOLD, true);
            currentY += 12;
            context.drawText(textRenderer, "§7모든 등급 보너스가 적용되었습니다.", cardX + 15, currentY, TEXT_GRAY, true);
            currentY += 11;
        } else if (detail != null && detail.getPromotionRequirements() != null) {
            context.drawText(textRenderer, "승급 조건:", cardX + 15, currentY, TEXT_COLOR, true);
            currentY += 12;
            
            for (JobDetail.Requirement req : detail.getPromotionRequirements()) {
                String checkmark = req.isCompleted() ? "✓" : "✗";
                int checkColor = req.isCompleted() ? TEXT_GREEN : TEXT_RED;
                
                context.drawText(textRenderer, checkmark, cardX + 20, currentY, checkColor, true);
                
                // [Phase 3] 진행률 표시 (current/required가 있으면)
                String reqText = req.getDescription();
                if (req.getRequired() > 0 && !req.isCompleted()) {
                    reqText = String.format("%s (%,d/%,d)", req.getDescription(), req.getCurrent(), req.getRequired());
                }
                context.drawText(textRenderer, reqText, cardX + 35, currentY, 
                    req.isCompleted() ? TEXT_GRAY : TEXT_COLOR, true);
                currentY += 11;
            }
            
            // 승급 버튼 (조건 충족 시, 최고 등급 아닐 때만)
            if (detail.canPromote()) {
                int btnX = cardX + cardWidth - 80;
                int btnY = y + 85;
                context.fill(btnX, btnY, btnX + 60, btnY + 20, 0xFF00AA00);
                context.drawText(textRenderer, "승급", btnX + 18, btnY + 6, TEXT_COLOR, true);
            }
        }
        
        currentY = y + 145;
        
        // ===== [Phase 3] 등급 보너스 =====
        if (detail != null) {
            // 현재 등급 보너스
            var currentBonuses = detail.getCurrentBonuses();
            if (!currentBonuses.isEmpty()) {
                context.drawText(textRenderer, "§6현재 등급 보너스:", cardX, currentY, TEXT_GOLD, true);
                currentY += 12;
                
                for (String bonus : currentBonuses) {
                    context.drawText(textRenderer, "  §a✦ " + bonus, cardX + 5, currentY, TEXT_GREEN, true);
                    currentY += 10;
                }
                currentY += 5;
            }
            
            // 다음 등급 보너스 미리보기
            var nextBonuses = detail.getNextGradeBonuses();
            if (!nextBonuses.isEmpty()) {
                context.drawText(textRenderer, "§7다음 등급 추가 보너스:", cardX, currentY, TEXT_GRAY, true);
                currentY += 12;
                
                for (String bonus : nextBonuses) {
                    context.drawText(textRenderer, "  §7▸ " + bonus, cardX + 5, currentY, TEXT_GRAY, true);
                    currentY += 10;
                }
                currentY += 5;
            }
        }
        
        // ===== 부 직업 (있으면) =====
        if (profile.getSecondaryJob() != null) {
            JobData secondJob = profile.getSecondaryJob();
            
            context.drawText(textRenderer, "부 직업", cardX, currentY, TEXT_GRAY, true);
            currentY += 15;
            
            // 작은 카드
            context.fill(cardX, currentY, cardX + cardWidth, currentY + 50, 0xFF252525);
            currentY += 10;
            
            String secondTitle = String.format("%s Lv.%d", secondJob.getLocalizedType(), secondJob.getLevel());
            context.drawText(textRenderer, secondTitle, cardX + 15, currentY, TEXT_COLOR, true);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        
        // 직업 미선택 상태에서 카드 클릭 처리
        // [Phase 2] renderJobSelection()과 동일한 레이아웃 값 사용
        if (profile != null && profile.getPrimaryJob() == null) {
            int cardX = x + 20;
            int cardWidth = width - 40;
            int cardHeight = 55;
            int cardSpacing = 10;
            int currentY = y + 15 + 18 + 20;  // 시작 + 제목 + 안내
            
            for (JobInfo jobInfo : AVAILABLE_JOBS) {
                if (mouseX >= cardX && mouseX < cardX + cardWidth &&
                    mouseY >= currentY && mouseY < currentY + cardHeight) {
                    // 채팅으로 직업 선택 명령어 실행
                    if (client.player != null) {
                        client.player.networkHandler.sendChatCommand("job select " + jobInfo.id.toLowerCase());
                    }
                    return true;
                }
                currentY += cardHeight + cardSpacing;
            }
            return false;
        }
        
        // 승급 버튼 클릭 처리
        JobDetail detail = UiDataHolder.getInstance().getJobDetail();
        if (detail != null && detail.canPromote()) {
            int btnX = x + 20 + width - 40 - 80;
            int btnY = y + 85;
            
            if (mouseX >= btnX && mouseX < btnX + 60 && mouseY >= btnY && mouseY < btnY + 20) {
                UiRequestSender.triggerJobPromotion();
                return true;
            }
        }
        return false;
    }
    
    // ================================================================================
    // 직업 선택 화면 렌더링
    // ================================================================================
    
    /**
     * 직업 미선택 시 선택 가능한 직업 목록 렌더링
     * 
     * <p>[Phase 2 버그수정] 카드 크기/간격 개선:</p>
     * <ul>
     *   <li>카드 높이: 45 → 55 (여유 공간 확보)</li>
     *   <li>카드 간격: 6 → 10 (시각적 분리 강화)</li>
     *   <li>카드 마진: 15 → 20 (양쪽 여백 증가)</li>
     * </ul>
     */
    private void renderJobSelection(DrawContext context, int mouseX, int mouseY) {
        int currentY = y + 15;
        int cardX = x + 20;
        int cardWidth = width - 40;
        
        // 제목
        drawCenteredText(context, "§e직업을 선택하세요", currentY, TEXT_GOLD);
        currentY += 18;
        
        // 안내 메시지
        drawCenteredText(context, "카드를 클릭하여 직업을 선택할 수 있습니다.", currentY, TEXT_GRAY);
        currentY += 20;
        
        // [Phase 2] 개선된 카드 레이아웃
        int cardHeight = 55;
        int cardSpacing = 10;
        
        for (JobInfo jobInfo : AVAILABLE_JOBS) {
            // 마우스 호버 체크
            boolean hovered = mouseX >= cardX && mouseX < cardX + cardWidth &&
                              mouseY >= currentY && mouseY < currentY + cardHeight;
            
            // 카드 배경 (호버 시 밝게)
            int bgColor = hovered ? CARD_HOVER : CARD_BG;
            context.fill(cardX, currentY, cardX + cardWidth, currentY + cardHeight, bgColor);
            
            // 좌측 액센트 바 (굵게)
            context.fill(cardX, currentY, cardX + 5, currentY + cardHeight, jobInfo.accentColor);
            
            // 직업명 (약간 아래로)
            context.drawText(textRenderer, jobInfo.displayName, cardX + 14, currentY + 12, TEXT_COLOR, true);
            
            // 설명 (여유 공간)
            context.drawText(textRenderer, jobInfo.description, cardX + 14, currentY + 28, TEXT_GRAY, true);
            
            // 호버 시 클릭 안내 (중앙 정렬)
            if (hovered) {
                String clickHint = "§a▶ 클릭하여 선택";
                int hintWidth = textRenderer.getWidth(clickHint);
                context.drawText(textRenderer, clickHint, cardX + cardWidth - hintWidth - 12, currentY + 20, TEXT_GREEN, true);
            }
            
            currentY += cardHeight + cardSpacing;
        }
        
        // 하단 팁
        currentY += 8;
        drawCenteredText(context, "§7또는 채팅에 §f/job select <직업명> §7입력", currentY, TEXT_GRAY);
    }
}
