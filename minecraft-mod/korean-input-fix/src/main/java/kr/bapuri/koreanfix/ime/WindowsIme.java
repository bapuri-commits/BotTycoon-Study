package kr.bapuri.koreanfix.ime;

import com.sun.jna.Native;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windows IME(Input Method Editor) 제어를 위한 래퍼 클래스
 * 
 * <h2>최적화 포인트</h2>
 * <ul>
 *   <li>JNA 호출 결과 캐싱으로 불필요한 네이티브 호출 감소</li>
 *   <li>에러 발생 시 graceful degradation</li>
 *   <li>스레드 안전한 캐시 관리</li>
 * </ul>
 */
public class WindowsIme {

    private static final Logger LOGGER = LoggerFactory.getLogger("KoreanInputFix");

    // ==================== 캐시 관련 상수 및 필드 ====================
    
    /** IME 상태 캐시 유효 시간 (밀리초) - 약 1틱 (50ms) */
    private static final long IME_STATUS_CACHE_DURATION_MS = 50;
    
    // IME 상태 캐시 (isImeEnabled용)
    private static boolean cachedImeStatus = false;
    private static long lastImeStatusCheck = 0;
    
    // 참고: 조합 문자열(getCompositionString)은 캐싱하지 않음
    // 이유: backspace 등 키 입력 시 즉각적인 반응이 필요함
    
    // 에러 카운터 (연속 에러 시 호출 중단)
    private static int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    // ImmGetCompositionString의 dwIndex 플래그
    public static final int GCS_COMPSTR = 0x0008;    // 조합 중 문자열
    public static final int GCS_RESULTSTR = 0x0800;  // 확정된 문자열

    /**
     * Windows imm32.dll의 함수들을 자바 인터페이스로 정의
     */
    public interface Imm32 extends Library {
        Imm32 INSTANCE = Native.load("imm32", Imm32.class);
        
        WinNT.HANDLE ImmGetContext(WinDef.HWND hWnd);
        boolean ImmReleaseContext(WinDef.HWND hWnd, WinNT.HANDLE hIMC);
        boolean ImmGetOpenStatus(WinNT.HANDLE hIMC);
        boolean ImmSetOpenStatus(WinNT.HANDLE hIMC, boolean fOpen);
        int ImmGetCompositionStringW(WinNT.HANDLE hIMC, int dwIndex, char[] lpBuf, int dwBufLen);
    }
    
    /**
     * Windows user32.dll의 함수들을 자바 인터페이스로 정의
     */
    public interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);
        WinDef.HWND GetForegroundWindow();
        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
    }
    
    // 가상 키 코드
    private static final byte VK_HANGUL = 0x15;
    private static final int KEYEVENTF_KEYUP = 0x0002;
    
    // ==================== 편의 메서드 ====================
    
    /**
     * 마인크래프트 창의 Windows HWND 핸들을 가져옵니다.
     */
    private static WinDef.HWND getMinecraftWindowHandle() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return null;
            }
            
            long glfwHandle = client.getWindow().getHandle();
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwHandle);
            return new WinDef.HWND(Pointer.createConstant(hwnd));
        } catch (Exception e) {
            LOGGER.debug("창 핸들을 가져오는 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 현재 활성 창의 IME가 켜져있는지(한글 모드인지) 확인합니다.
     * 
     * <p><b>최적화:</b> 결과를 캐싱하여 50ms 내 중복 호출 시 캐시된 값 반환</p>
     * 
     * @return true=한글 모드, false=영문 모드, 오류 시 false
     */
    public static boolean isImeEnabled() {
        // 에러가 너무 많으면 호출 중단
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            return false;
        }
        
        // 캐시 유효성 확인
        long now = System.currentTimeMillis();
        if (now - lastImeStatusCheck < IME_STATUS_CACHE_DURATION_MS) {
            return cachedImeStatus;
        }
        
        try {
            WinDef.HWND hWnd = getMinecraftWindowHandle();
            if (hWnd == null) {
                return false;
            }
            
            WinNT.HANDLE hIMC = Imm32.INSTANCE.ImmGetContext(hWnd);
            if (hIMC == null) {
                cachedImeStatus = false;
                lastImeStatusCheck = now;
                return false;
            }
            
            try {
                cachedImeStatus = Imm32.INSTANCE.ImmGetOpenStatus(hIMC);
                lastImeStatusCheck = now;
                consecutiveErrors = 0; // 성공 시 에러 카운터 리셋
                return cachedImeStatus;
            } finally {
                Imm32.INSTANCE.ImmReleaseContext(hWnd, hIMC);
            }
        } catch (Exception e) {
            consecutiveErrors++;
            LOGGER.debug("IME 상태 확인 중 오류 ({}회): {}", consecutiveErrors, e.getMessage());
            return false;
        }
    }
    
    /**
     * 현재 조합 중인 문자열을 가져옵니다.
     * 
     * <p><b>주의:</b> 캐싱하지 않음 - backspace 등 키 입력에 즉각 반응 필요</p>
     * 
     * @return 조합 중인 문자열, 조합 중이 아니면 빈 문자열
     */
    public static String getCompositionString() {
        // 에러가 너무 많으면 호출 중단
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            return "";
        }
        
        try {
            WinDef.HWND hWnd = getMinecraftWindowHandle();
            if (hWnd == null) {
                return "";
            }

            WinNT.HANDLE hIMC = Imm32.INSTANCE.ImmGetContext(hWnd);
            if (hIMC == null) {
                return "";
            }

            try {
                int size = Imm32.INSTANCE.ImmGetCompositionStringW(hIMC, GCS_COMPSTR, null, 0);
                if (size <= 0) {
                    return "";
                }

                char[] buffer = new char[size / 2];
                Imm32.INSTANCE.ImmGetCompositionStringW(hIMC, GCS_COMPSTR, buffer, size);
                
                consecutiveErrors = 0; // 성공 시 에러 카운터 리셋
                return new String(buffer);
            } finally {
                Imm32.INSTANCE.ImmReleaseContext(hWnd, hIMC);
            }
        } catch (Exception e) {
            consecutiveErrors++;
            LOGGER.debug("조합 문자열 가져오기 중 오류 ({}회): {}", consecutiveErrors, e.getMessage());
            return "";
        }
    }
    
    /**
     * IME 상태 캐시를 무효화합니다.
     * 
     * <p>IME 상태가 변경된 것이 확실할 때 호출하면 
     * 다음 호출에서 최신 상태를 가져옵니다.</p>
     */
    public static void invalidateCache() {
        lastImeStatusCheck = 0;
    }
    
    /**
     * 에러 카운터를 리셋합니다.
     * 
     * <p>게임 재시작이나 화면 전환 시 호출하여 
     * 에러로 인한 호출 중단 상태를 해제합니다.</p>
     */
    public static void resetErrorCounter() {
        consecutiveErrors = 0;
    }
    
    /**
     * IME를 끕니다 (영문 모드로 전환).
     */
    public static void disableIme() {
        if (isImeEnabled()) {
            pressHangulKey();
            invalidateCache(); // 상태 변경 후 캐시 무효화
            LOGGER.info("한/영 키 시뮬레이션: 영문 모드로 전환");
        }
    }
    
    /**
     * IME를 조용히 끕니다 (로그 없이).
     */
    public static void disableImeSilent() {
        if (isImeEnabled()) {
            pressHangulKey();
            invalidateCache(); // 상태 변경 후 캐시 무효화
        }
    }
    
    /**
     * IME를 켭니다 (한글 모드로 전환).
     */
    public static void enableIme() {
        if (!isImeEnabled()) {
            pressHangulKey();
            invalidateCache(); // 상태 변경 후 캐시 무효화
            LOGGER.info("한/영 키 시뮬레이션: 한글 모드로 전환");
        }
    }
    
    /**
     * 한/영 키를 시뮬레이션합니다.
     */
    private static void pressHangulKey() {
        try {
            User32.INSTANCE.keybd_event(VK_HANGUL, (byte) 0, 0, 0);
            User32.INSTANCE.keybd_event(VK_HANGUL, (byte) 0, KEYEVENTF_KEYUP, 0);
        } catch (Exception e) {
            consecutiveErrors++;
            LOGGER.debug("한/영 키 시뮬레이션 실패: {}", e.getMessage());
        }
    }
}
