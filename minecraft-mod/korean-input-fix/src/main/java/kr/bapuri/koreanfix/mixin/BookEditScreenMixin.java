package kr.bapuri.koreanfix.mixin;

import kr.bapuri.koreanfix.ime.CompositionHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.util.SelectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 책 편집 화면에서 한글 조합 글자를 표시하기 위한 Mixin
 * 
 * <p>BookEditScreen은 TextFieldWidget을 사용하지 않고 자체적으로 텍스트를 관리합니다.
 * 또한 PageContent라는 캐시 객체를 사용하므로, pages 수정 후 캐시 무효화가 필요합니다.</p>
 * 
 * <h2>핵심 발견</h2>
 * <ul>
 *   <li>pages 리스트를 직접 수정해도 캐시가 갱신되지 않음</li>
 *   <li>invalidatePageContent() 호출로 캐시 무효화 필요</li>
 *   <li>조합이 끝날 때도 캐시 무효화 필요 (Backspace 시)</li>
 *   <li>SelectionManager.getSelectionEnd()로 커서 위치 확인</li>
 * </ul>
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {

    @Shadow
    private List<String> pages;
    
    @Shadow
    private int currentPage;
    
    @Shadow
    private SelectionManager currentPageSelectionManager;

    @Shadow
    protected abstract void invalidatePageContent();

    @Unique
    private String koreanfix_originalContent = null;
    
    // 이전 프레임에 조합 중이었는지 추적 (조합 종료 감지용)
    @Unique
    private boolean koreanfix_wasComposing = false;

    /**
     * 렌더링 전: 조합 중인 문자를 커서 위치에 삽입
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void koreanfix_beforeRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String composing = CompositionHelper.getComposingIfEnabled();
        
        if (!composing.isEmpty() && isValidPage()) {
            // 조합 중
            koreanfix_wasComposing = true;
            
            String original = pages.get(currentPage);
            this.koreanfix_originalContent = original;
            
            // 커서 위치에 조합 문자 삽입
            int cursorPos = currentPageSelectionManager.getSelectionEnd();
            String modified = CompositionHelper.insertAtCursor(original, composing, cursorPos);
            pages.set(currentPage, modified);
            
            // 캐시 무효화하여 변경사항 반영
            invalidatePageContent();
        } else if (koreanfix_wasComposing) {
            // 조합이 끝남 (있었다가 없어짐) - Backspace 등으로 조합 취소
            koreanfix_wasComposing = false;
            
            // 캐시 무효화하여 조합 문자가 사라진 것을 반영
            invalidatePageContent();
        }
    }

    /**
     * 렌더링 후: 원본 페이지 내용으로 복원
     * 
     * <p>주의: 여기서는 invalidatePageContent()를 호출하지 않음!
     * 호출하면 무한 렌더링 루프가 발생함</p>
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void koreanfix_afterRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.koreanfix_originalContent != null && isValidPage()) {
            pages.set(currentPage, this.koreanfix_originalContent);
            this.koreanfix_originalContent = null;
            // invalidatePageContent() 호출 안 함 - 무한 루프 방지
        }
    }
    
    /**
     * 현재 페이지가 유효한 범위인지 확인
     */
    @Unique
    private boolean isValidPage() {
        return pages != null 
            && currentPage >= 0 
            && currentPage < pages.size();
    }
}
