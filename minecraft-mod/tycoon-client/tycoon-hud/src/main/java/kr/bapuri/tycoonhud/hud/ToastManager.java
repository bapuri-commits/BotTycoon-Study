package kr.bapuri.tycoonhud.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * [Phase 5] 토스트 알림 시스템
 * 
 * <p>게임 내 이벤트(레벨업, 도감 등록, 승급 등)를 화면 상단에 일시적으로 표시합니다.</p>
 * 
 * <h3>사용 방법</h3>
 * <pre>
 * // 간단한 토스트
 * ToastManager.getInstance().show("레벨업!", ToastType.SUCCESS);
 * 
 * // 상세 토스트
 * ToastManager.getInstance().show("도감 등록", "다이아몬드 +10BC", ToastType.INFO);
 * </pre>
 */
public class ToastManager {
    
    private static ToastManager instance;
    
    public static ToastManager getInstance() {
        if (instance == null) {
            instance = new ToastManager();
        }
        return instance;
    }
    
    /** 토스트 타입 */
    public enum ToastType {
        SUCCESS(0xFF4AFF4A, 0xFF2A5A2A),   // 녹색 (레벨업, 도감 등록 등)
        INFO(0xFFAAAAFF, 0xFF2A2A5A),      // 파랑 (일반 정보)
        WARNING(0xFFFFD700, 0xFF5A5A2A),   // 금색 (경고)
        ERROR(0xFFFF4A4A, 0xFF5A2A2A);     // 빨강 (에러)
        
        public final int textColor;
        public final int bgColor;
        
        ToastType(int textColor, int bgColor) {
            this.textColor = textColor;
            this.bgColor = bgColor;
        }
    }
    
    /** 토스트 데이터 */
    private static class Toast {
        final String title;
        final String subtitle;
        final ToastType type;
        int ticksRemaining;
        float animProgress; // 0~1 (슬라이드 인/아웃)
        
        Toast(String title, String subtitle, ToastType type, int duration) {
            this.title = title;
            this.subtitle = subtitle;
            this.type = type;
            this.ticksRemaining = duration;
            this.animProgress = 0f;
        }
    }
    
    private final List<Toast> toasts = new ArrayList<>();
    
    /** 토스트 최대 표시 시간 (틱) */
    private static final int DEFAULT_DURATION = 80; // 4초
    
    /** 동시에 표시 가능한 최대 토스트 수 */
    private static final int MAX_VISIBLE = 3;
    
    /** 애니메이션 속도 */
    private static final float ANIM_SPEED = 0.15f;
    
    private ToastManager() {}
    
    /**
     * 토스트 표시 (제목만)
     */
    public void show(String title, ToastType type) {
        show(title, null, type);
    }
    
    /**
     * 토스트 표시 (제목 + 부제)
     */
    public void show(String title, String subtitle, ToastType type) {
        // 중복 방지 (동일 제목이 있으면 갱신)
        for (Toast t : toasts) {
            if (t.title.equals(title)) {
                t.ticksRemaining = DEFAULT_DURATION;
                return;
            }
        }
        
        // 최대 개수 초과 시 가장 오래된 것 제거
        while (toasts.size() >= MAX_VISIBLE) {
            toasts.remove(0);
        }
        
        toasts.add(new Toast(title, subtitle, type, DEFAULT_DURATION));
    }
    
    /**
     * 틱 업데이트 (매 프레임 호출)
     */
    public void tick() {
        Iterator<Toast> it = toasts.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            t.ticksRemaining--;
            
            // 애니메이션 업데이트
            if (t.ticksRemaining > 20) {
                // 슬라이드 인
                t.animProgress = Math.min(1f, t.animProgress + ANIM_SPEED);
            } else {
                // 페이드 아웃
                t.animProgress = Math.max(0f, t.animProgress - ANIM_SPEED);
            }
            
            // 시간 종료 및 애니메이션 완료 시 제거
            if (t.ticksRemaining <= 0 && t.animProgress <= 0f) {
                it.remove();
            }
        }
    }
    
    /**
     * 토스트 렌더링 (HUD 오버레이에서 호출)
     */
    public void render(DrawContext context, int screenWidth, int screenHeight) {
        if (toasts.isEmpty()) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        
        int toastY = 10;
        
        for (Toast toast : toasts) {
            if (toast.animProgress <= 0f) continue;
            
            int toastWidth = Math.max(
                textRenderer.getWidth(toast.title),
                toast.subtitle != null ? textRenderer.getWidth(toast.subtitle) : 0
            ) + 20;
            
            int toastHeight = toast.subtitle != null ? 28 : 18;
            
            // 슬라이드 애니메이션 (위에서 아래로)
            int slideOffset = (int) ((1f - toast.animProgress) * -30);
            int alpha = (int) (255 * toast.animProgress);
            
            int toastX = (screenWidth - toastWidth) / 2;
            int actualY = toastY + slideOffset;
            
            // 배경
            int bgColor = (alpha << 24) | (toast.type.bgColor & 0x00FFFFFF);
            context.fill(toastX, actualY, toastX + toastWidth, actualY + toastHeight, bgColor);
            
            // 테두리
            int borderColor = (alpha << 24) | (toast.type.textColor & 0x00FFFFFF);
            context.fill(toastX, actualY, toastX + toastWidth, actualY + 1, borderColor); // 상단
            context.fill(toastX, actualY + toastHeight - 1, toastX + toastWidth, actualY + toastHeight, borderColor); // 하단
            
            // 제목
            int titleX = toastX + (toastWidth - textRenderer.getWidth(toast.title)) / 2;
            int titleY = actualY + (toast.subtitle != null ? 4 : 5);
            context.drawText(textRenderer, toast.title, titleX, titleY, toast.type.textColor, true);
            
            // 부제
            if (toast.subtitle != null) {
                int subX = toastX + (toastWidth - textRenderer.getWidth(toast.subtitle)) / 2;
                context.drawText(textRenderer, toast.subtitle, subX, actualY + 16, 0xFFAAAAAA, false);
            }
            
            toastY += toastHeight + 5;
        }
    }
    
    /**
     * 모든 토스트 제거
     */
    public void clear() {
        toasts.clear();
    }
}
