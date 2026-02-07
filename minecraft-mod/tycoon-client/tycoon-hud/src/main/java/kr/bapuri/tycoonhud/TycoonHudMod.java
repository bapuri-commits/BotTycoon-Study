package kr.bapuri.tycoonhud;

import kr.bapuri.tycoonhud.hud.BlueZoneWorldRenderer;
import kr.bapuri.tycoonhud.hud.CountdownOverlay;
import kr.bapuri.tycoonhud.hud.DuelHudOverlay;
import kr.bapuri.tycoonhud.hud.DungeonMapOverlay;
import kr.bapuri.tycoonhud.hud.HunterHudOverlay;
import kr.bapuri.tycoonhud.hud.HunterRankingHud;
import kr.bapuri.tycoonhud.hud.HunterReadyHud;
import kr.bapuri.tycoonhud.hud.HunterTabOverlay;
import kr.bapuri.tycoonhud.hud.ProfileHudOverlay;
import kr.bapuri.tycoonhud.hud.VitalHudOverlay;
import kr.bapuri.tycoonhud.key.AugmentKeyHandler;
import kr.bapuri.tycoonhud.key.HunterTabKeyHandler;
import kr.bapuri.tycoonhud.key.MapKeyHandler;
import kr.bapuri.tycoonhud.key.TeamKeyHandler;
import kr.bapuri.tycoonhud.net.TycoonClientState;
import kr.bapuri.tycoonhud.net.UiDataReceiver;
import kr.bapuri.tycoonhud.screen.AugmentSelectionScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BotTycoon HUD 모드의 메인 진입점입니다.
 * 
 * <p>이 모드는 다음 기능을 제공합니다:</p>
 * <ul>
 *     <li>서버로부터 플레이어 데이터 수신 (tycoon:ui_data 채널)</li>
 *     <li>좌상단 프로필 HUD (이름, BD, 직업, 월드, 플롯)</li>
 *     <li>하단 Vital 게이지 (체력/배고픔)</li>
 *     <li>바닐라 체력/배고픔 HUD 숨김</li>
 * </ul>
 * 
 * @author Bapuri
 * @version 1.0.0
 */
public class TycoonHudMod implements ClientModInitializer {
    
    /** 모드 ID */
    public static final String MOD_ID = "tycoon-hud";
    
    /** 로거 인스턴스 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    /** 증강 선택 테스트 키바인딩 (F8) */
    private static KeyBinding augmentTestKey;
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("[TycoonHUD] Initializing mod...");
        
        // Initialize networking - receive data from server
        UiDataReceiver.register();
        
        // Initialize client state manager (connection tracking + initial data request)
        TycoonClientState.register();
        
        // Register HUD overlays
        HudRenderCallback.EVENT.register(new ProfileHudOverlay());
        HudRenderCallback.EVENT.register(new VitalHudOverlay());
        HudRenderCallback.EVENT.register(new DuelHudOverlay());
        HudRenderCallback.EVENT.register(new HunterHudOverlay());      // v2.2: 미니맵, 거리바
        HudRenderCallback.EVENT.register(new HunterRankingHud());      // v2.3: 좌상단 Top 3 순위 (게임 중)
        HudRenderCallback.EVENT.register(new HunterReadyHud());        // v2.4: 좌상단 레디 상태 (로비)
        HudRenderCallback.EVENT.register(new HunterTabOverlay());      // v2.3: TAB 오버레이
        HudRenderCallback.EVENT.register(new CountdownOverlay());      // v2.4: 카운트다운 오버레이
        HudRenderCallback.EVENT.register(new DungeonMapOverlay());     // v2.5: 던전 맵 (양피지 스타일)
        HudRenderCallback.EVENT.register(new kr.bapuri.tycoonhud.hud.ToastOverlay());  // [Phase 5] 토스트 알림
        
        // 키바인딩 등록
        TeamKeyHandler.register();        // R키: 팀 초대/수락
        HunterTabKeyHandler.register();   // TAB키: 상세 정보 오버레이
        MapKeyHandler.register();         // M키: 큰 맵 토글
        AugmentKeyHandler.register();     // Space키: 증강 선택 (대기 중일 때)
        
        // 3D 월드 렌더링 (자기장 벽)
        BlueZoneWorldRenderer.register();
        
        // 테스트용 키바인딩 등록 (F8 = 증강 선택 UI 테스트)
        augmentTestKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tycoon-hud.augment_test",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "category.tycoon-hud.debug"
        ));
        
        // 키바인딩 이벤트 처리 (F8 = 서버에 증강 선택 UI 열기 요청)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (augmentTestKey.wasPressed()) {
                if (client.player != null) {
                    // 서버에 증강 선택 UI 열기 요청 (관리자 권한 필요)
                    TycoonClientState.sendAugmentOpenRequest();
                    LOGGER.info("[TycoonHUD] F8: 증강 선택 UI 열기 요청 전송");
                }
            }
        });
        
        LOGGER.info("[TycoonHUD] Mod initialized successfully!");
    }
}

