package kr.bapuri.tycoonui.screen.tab;

import kr.bapuri.tycoonhud.model.AchievementData;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import kr.bapuri.tycoonui.screen.TycoonScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;

/**
 * 프로필 탭입니다.
 * 
 * <h3>표시 내용</h3>
 * <ul>
 *     <li>플레이어 스킨 렌더링</li>
 *     <li>이름, 칭호</li>
 *     <li>플레이타임</li>
 * </ul>
 */
public class ProfileTab extends AbstractTab {
    
    public ProfileTab(TycoonScreen parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
    }
    
    @Override
    public void init() {
        // 초기화 로직
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        
        if (profile == null) {
            renderNoData(context, "서버 데이터를 기다리는 중...");
            return;
        }
        
        // 왼쪽: 플레이어 스킨 얼굴
        int faceX = x + 30;
        int faceY = y + 30;
        int faceSize = 64;
        
        // 스킨 배경
        context.fill(faceX - 4, faceY - 4, faceX + faceSize + 4, faceY + faceSize + 4, 0xFF2A2A2A);
        context.fill(faceX - 2, faceY - 2, faceX + faceSize + 2, faceY + faceSize + 2, 0xFF3A3A3A);
        
        // 스킨 얼굴 렌더링
        renderPlayerFace(context, faceX, faceY, faceSize);
        
        // 오른쪽: 정보
        int infoX = x + 120;
        int infoY = y + 30;
        
        // 이름 (큰 글씨)
        context.getMatrices().push();
        context.getMatrices().translate(infoX, infoY, 0);
        context.getMatrices().scale(1.5f, 1.5f, 1f);
        context.drawText(textRenderer, profile.getDisplayName(), 0, 0, TEXT_COLOR, true);
        context.getMatrices().pop();
        infoY += 25;
        
        // 구분선
        context.fill(infoX, infoY, infoX + 200, infoY + 1, 0xFF444444);
        infoY += 10;
        
        // 플레이타임
        context.drawText(textRenderer, "플레이타임: " + profile.getFormattedPlaytime(), infoX, infoY, TEXT_COLOR, true);
        infoY += 15;
        
        // 현재 위치
        context.drawText(textRenderer, "현재 위치: " + profile.getLocalizedWorld(), infoX, infoY, TEXT_COLOR, true);
        infoY += 15;
        
        // 주 직업
        if (profile.getPrimaryJob() != null) {
            String jobText = String.format("주 직업: %s Lv.%d (%s)",
                profile.getPrimaryJob().getLocalizedType(),
                profile.getPrimaryJob().getLevel(),
                profile.getPrimaryJob().getGradeTitle()
            );
            context.drawText(textRenderer, jobText, infoX, infoY, TEXT_COLOR, true);
            infoY += 15;
        } else {
            // 직업이 없는 경우
            context.drawText(textRenderer, "주 직업: 없음", infoX, infoY, TEXT_GRAY, true);
            infoY += 12;
            context.drawText(textRenderer, "  💡 /job 으로 직업 선택", infoX, infoY, 0xFFAAAAFF, false);
            infoY += 15;
        }
        
        // 부 직업
        if (profile.getSecondaryJob() != null) {
            String jobText = String.format("부 직업: %s Lv.%d",
                profile.getSecondaryJob().getLocalizedType(),
                profile.getSecondaryJob().getLevel()
            );
            context.drawText(textRenderer, jobText, infoX, infoY, TEXT_COLOR, true);
            infoY += 15;
        }
        
        // 구분선
        infoY += 5;
        context.fill(infoX, infoY, infoX + 200, infoY + 1, 0xFF444444);
        infoY += 10;
        
        // 업적 섹션
        AchievementData achievements = profile.getAchievements();
        if (achievements != null) {
            // 업적 타이틀
            context.drawText(textRenderer, "🏆 " + achievements.getDisplayText(), infoX, infoY, 0xFFFFD700, true);
            infoY += 15;
            
            // 진행바
            int barWidth = 180;
            int barHeight = 8;
            float progress = achievements.getUnlockedCount() / (float) Math.max(1, achievements.getTotalCount());
            
            // 배경
            context.fill(infoX, infoY, infoX + barWidth, infoY + barHeight, 0xFF333333);
            // 채움
            int fillWidth = (int) (barWidth * Math.min(1.0f, progress));
            if (fillWidth > 0) {
                context.fill(infoX, infoY, infoX + fillWidth, infoY + barHeight, 0xFFFFD700);
            }
            infoY += barHeight + 10;
            
            // 최근 해금된 업적 표시 (v3)
            var recentUnlocks = achievements.getRecentUnlocks();
            if (!recentUnlocks.isEmpty()) {
                context.drawText(textRenderer, "최근 해금:", infoX, infoY, TEXT_GRAY, false);
                infoY += 12;
                
                int maxDisplay = Math.min(3, recentUnlocks.size());
                for (int i = 0; i < maxDisplay; i++) {
                    var detail = recentUnlocks.get(i);
                    String displayText = String.format("  • %s (+%dBC)", detail.getName(), detail.getBottCoinReward());
                    int color = detail.getColorARGB();
                    context.drawText(textRenderer, displayText, infoX, infoY, color, false);
                    infoY += 11;
                }
            }
        } else {
            context.drawText(textRenderer, "🏆 업적: 데이터 없음", infoX, infoY, TEXT_GRAY, true);
        }
    }
    
    /**
     * 플레이어 스킨 얼굴을 렌더링합니다.
     */
    private void renderPlayerFace(DrawContext context, int x, int y, int size) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // 현재 플레이어의 스킨 가져오기
        if (client.player != null && client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (entry != null) {
                Identifier skinTexture = entry.getSkinTexture();
                if (skinTexture != null) {
                    // 스킨 얼굴 렌더링 (8x8 영역을 size로 확대)
                    // 스킨 텍스처에서 얼굴 위치: u=8, v=8, width=8, height=8
                    // drawTexture(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight)
                    context.drawTexture(skinTexture, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
                    // 헬멧 레이어 (u=40, v=8)
                    context.drawTexture(skinTexture, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
                    return;
                }
            }
        }
        
        // 폴백: 기본 스킨 아이콘
        context.fill(x, y, x + size, y + size, 0xFF666666);
        String text = "?";
        int textX = x + (size - textRenderer.getWidth(text)) / 2;
        int textY = y + (size - 8) / 2;
        context.drawText(textRenderer, text, textX, textY, TEXT_GRAY, false);
    }
}
