package kr.bapuri.koreanfix;

import kr.bapuri.koreanfix.config.ModConfig;
import kr.bapuri.koreanfix.ime.WindowsIme;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Korean Input Fix 모드의 메인 클래스
 * 
 * <p>마인크래프트에서 한글 IME 입력 문제를 해결합니다.</p>
 * 
 * <h2>주요 기능</h2>
 * <ul>
 *   <li>게임 플레이 중 IME 자동 비활성화</li>
 *   <li>텍스트 입력 화면에서 IME 자동 활성화</li>
 *   <li>F6 키로 모드 토글</li>
 * </ul>
 */
public class KoreanInputFixMod implements ClientModInitializer {

    public static final String MOD_ID = "koreanfix";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // 이전 화면 상태 저장 (변화 감지용)
    private Screen lastScreen = null;
    
    // 모드 토글 키 (F6)
    private static KeyBinding toggleKey;
    
    // IME 강제 비활성화 간격 (틱 단위) - 매 틱이 아닌 5틱마다 체크
    private static final int IME_CHECK_INTERVAL = 5;
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Korean Input Fix 모드가 로드되었습니다!");
        
        // 설정 로드 (최초 접근 시 파일에서 로드됨)
        ModConfig config = ModConfig.get();
        LOGGER.info("모드 활성화 상태: {}", config.enabled);
        
        // 에러 카운터 리셋 (게임 시작 시)
        WindowsIme.resetErrorCounter();
        
        // 토글 키 등록 (F6)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.koreanfix.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            "category.koreanfix"
        ));
        
        // 매 틱마다 처리
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 토글 키 처리
            handleToggleKey(client);
            
            // 모드가 비활성화되면 IME 제어 안 함
            if (!ModConfig.get().enabled) return;
            
            Screen currentScreen = client.currentScreen;
            
            // 화면 상태가 변경되었을 때 처리
            if (currentScreen != lastScreen) {
                handleScreenChange(currentScreen);
                lastScreen = currentScreen;
            }
            
            // 게임 플레이 중 IME 강제 비활성화 (안전망)
            // 최적화: 매 틱이 아닌 5틱마다 체크
            if (currentScreen == null) {
                tickCounter++;
                if (tickCounter >= IME_CHECK_INTERVAL) {
                    tickCounter = 0;
                    WindowsIme.disableImeSilent();
                }
            }
        });
    }
    
    /**
     * 토글 키 입력을 처리합니다.
     */
    private void handleToggleKey(net.minecraft.client.MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            ModConfig cfg = ModConfig.get();
            cfg.enabled = !cfg.enabled;
            cfg.save();
            
            // 상태 메시지 표시
            String status = cfg.enabled ? "§a활성화" : "§c비활성화";
            if (client.player != null) {
                client.player.sendMessage(
                    Text.literal("§6[Korean Fix]§r 모드가 " + status + "§r 되었습니다."),
                    true
                );
            }
            LOGGER.info("모드 토글: {}", cfg.enabled ? "활성화" : "비활성화");
            
            // 에러 카운터 리셋 (상태 변경 시)
            WindowsIme.resetErrorCounter();
        }
    }
    
    /**
     * 화면 변경 시 IME 상태를 조정합니다.
     */
    private void handleScreenChange(Screen screen) {
        // 캐시 무효화 (화면 전환 시 최신 상태 확인)
        WindowsIme.invalidateCache();
        
        if (screen == null) {
            LOGGER.debug("게임 플레이 모드 - IME 비활성화");
            WindowsIme.disableIme();
        } else if (isTextInputScreen(screen)) {
            LOGGER.debug("텍스트 입력 화면 ({}) - IME 활성화", screen.getClass().getSimpleName());
            WindowsIme.enableIme();
        } else {
            LOGGER.debug("일반 화면 ({}) - IME 비활성화", screen.getClass().getSimpleName());
            WindowsIme.disableIme();
        }
    }
    
    /**
     * 텍스트 입력이 필요한 화면인지 확인합니다.
     */
    private boolean isTextInputScreen(Screen screen) {
        return screen instanceof ChatScreen
            || screen instanceof SignEditScreen
            || screen instanceof BookEditScreen;
    }
}
