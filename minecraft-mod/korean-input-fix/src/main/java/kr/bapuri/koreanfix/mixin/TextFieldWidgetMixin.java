package kr.bapuri.koreanfix.mixin;

import kr.bapuri.koreanfix.ime.CompositionHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TextFieldWidget에 한글 조합 표시 기능을 추가하는 Mixin
 * 
 * <p>채팅창, 검색창, 모루 등 TextFieldWidget을 사용하는 모든 곳에서 
 * 한글 조합 중인 글자가 화면에 표시됩니다.</p>
 * 
 * <h2>동작 방식</h2>
 * <ol>
 *   <li>렌더링 전: 커서 위치에 조합 문자열 삽입</li>
 *   <li>렌더링: 조합 문자가 포함된 텍스트로 화면 그리기</li>
 *   <li>렌더링 후: text 필드를 원본으로 복원</li>
 * </ol>
 */
@Mixin(TextFieldWidget.class)
public class TextFieldWidgetMixin {

    @Shadow
    private String text;
    
    // 커서 위치 (selectionEnd가 현재 커서 위치)
    @Shadow
    private int selectionEnd;

    @Unique
    private String koreanfix_originalText = null;

    /**
     * renderButton() 호출 전: 커서 위치에 조합 문자열 삽입
     */
    @Inject(method = "renderButton", at = @At("HEAD"))
    private void koreanfix_beforeRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String composing = CompositionHelper.getComposingIfEnabled();

        if (!composing.isEmpty()) {
            this.koreanfix_originalText = this.text;
            // 커서 위치에 조합 문자 삽입 (끝이 아닌 정확한 위치에)
            this.text = CompositionHelper.insertAtCursor(this.text, composing, this.selectionEnd);
        }
    }

    /**
     * renderButton() 호출 후: text를 원본으로 복구
     */
    @Inject(method = "renderButton", at = @At("RETURN"))
    private void koreanfix_afterRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.koreanfix_originalText != null) {
            this.text = this.koreanfix_originalText;
            this.koreanfix_originalText = null;
        }
    }
}
