package kr.bapuri.koreanfix.mixin;

import kr.bapuri.koreanfix.ime.CompositionHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 표지판 편집 화면에서 한글 조합 글자를 표시하기 위한 Mixin
 * 
 * <p>SignEditScreen은 AbstractSignEditScreen을 상속받으며,
 * TextFieldWidget을 사용하지 않고 자체적으로 텍스트를 관리합니다.</p>
 */
@Mixin(AbstractSignEditScreen.class)
public class SignEditScreenMixin {
    
    @Shadow
    private String[] messages;
    
    @Shadow
    private int currentRow;
    
    @Unique
    private String koreanfix_originalMessage = null;

    /**
     * 렌더링 전: 조합 중인 문자를 현재 줄에 추가
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void koreanfix_beforeRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        String composing = CompositionHelper.getComposingIfEnabled();
        
        if (!composing.isEmpty() && isValidRow()) {
            this.koreanfix_originalMessage = this.messages[this.currentRow];
            this.messages[this.currentRow] = CompositionHelper.appendComposing(
                this.koreanfix_originalMessage, composing);
        }
    }

    /**
     * 렌더링 후: 원본 텍스트로 복원
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void koreanfix_afterRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.koreanfix_originalMessage != null && isValidRow()) {
            this.messages[this.currentRow] = this.koreanfix_originalMessage;
            this.koreanfix_originalMessage = null;
        }
    }
    
    /**
     * 현재 행이 유효한 범위인지 확인
     */
    @Unique
    private boolean isValidRow() {
        return this.messages != null 
            && this.currentRow >= 0 
            && this.currentRow < this.messages.length;
    }
}
