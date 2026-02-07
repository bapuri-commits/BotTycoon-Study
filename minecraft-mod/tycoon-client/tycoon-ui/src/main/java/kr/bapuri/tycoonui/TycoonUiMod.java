package kr.bapuri.tycoonui;

import kr.bapuri.tycoonui.key.TycoonKeybinds;
import kr.bapuri.tycoonui.net.UiRequestSender;
import kr.bapuri.tycoonui.net.UiResponseHandler;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BotTycoon UI 모드의 메인 진입점입니다.
 * 
 * <p>이 모드는 다음 기능을 제공합니다:</p>
 * <ul>
 *     <li>통합 GUI (TycoonScreen) - 4개 탭</li>
 *     <li>키바인딩 (백틱, C, J)</li>
 *     <li>서버 요청/응답 처리</li>
 * </ul>
 * 
 * <p>tycoon-hud 모듈에 의존합니다.</p>
 * 
 * @author Bapuri
 * @version 1.0.0
 */
public class TycoonUiMod implements ClientModInitializer {
    
    /** 모드 ID */
    public static final String MOD_ID = "tycoon-ui";
    
    /** 로거 인스턴스 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("[TycoonUI] Initializing mod...");
        
        // Register keybindings
        TycoonKeybinds.register();
        
        // Initialize networking
        UiRequestSender.init();
        UiResponseHandler.register();
        
        LOGGER.info("[TycoonUI] Mod initialized successfully!");
    }
}

