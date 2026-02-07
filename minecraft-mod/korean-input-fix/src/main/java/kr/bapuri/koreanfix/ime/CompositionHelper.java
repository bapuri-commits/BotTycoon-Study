package kr.bapuri.koreanfix.ime;

import kr.bapuri.koreanfix.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IME 조합 문자 처리를 위한 헬퍼 클래스
 * 
 * <p>여러 Mixin에서 공통으로 사용되는 조합 문자열 처리 로직을 제공합니다.</p>
 * 
 * <h2>사용 예시</h2>
 * <pre>
 * // 렌더링 전
 * String composing = CompositionHelper.getComposingIfEnabled();
 * if (!composing.isEmpty()) {
 *     // 텍스트에 조합 문자 추가
 * }
 * </pre>
 */
public class CompositionHelper {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("KoreanInputFix");
    
    /**
     * 모드가 활성화된 경우에만 조합 중인 문자열을 반환합니다.
     * 
     * @return 조합 중인 문자열, 모드 비활성화 또는 조합 중이 아니면 빈 문자열
     */
    public static String getComposingIfEnabled() {
        // 모드가 비활성화되어 있으면 빈 문자열
        if (!ModConfig.get().enabled) {
            return "";
        }
        return WindowsIme.getCompositionString();
    }
    
    /**
     * 커서 위치에 조합 문자열을 삽입한 결과를 반환합니다.
     * 
     * @param original 원본 문자열
     * @param composing 조합 중인 문자열
     * @param cursorPos 커서 위치
     * @return 조합 문자가 삽입된 문자열
     */
    public static String insertAtCursor(String original, String composing, int cursorPos) {
        if (composing.isEmpty()) {
            return original;
        }
        
        // 커서 위치 범위 검증
        int safePos = Math.max(0, Math.min(cursorPos, original.length()));
        
        String before = original.substring(0, safePos);
        String after = original.substring(safePos);
        
        return before + composing + after;
    }
    
    /**
     * 문자열 끝에 조합 문자열을 추가한 결과를 반환합니다.
     * 
     * @param original 원본 문자열
     * @param composing 조합 중인 문자열
     * @return 조합 문자가 추가된 문자열
     */
    public static String appendComposing(String original, String composing) {
        if (composing.isEmpty()) {
            return original;
        }
        return original + composing;
    }
    
    /**
     * 디버그 로깅을 위한 메서드
     */
    public static void logComposing(String context, String original, String composing, String result) {
        LOGGER.debug("[{}] '{}' + '{}' = '{}'", context, original, composing, result);
    }
}
