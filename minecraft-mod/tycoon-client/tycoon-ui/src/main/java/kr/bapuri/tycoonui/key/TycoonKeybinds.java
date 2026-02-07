package kr.bapuri.tycoonui.key;

import kr.bapuri.tycoonui.TycoonUiMod;
import kr.bapuri.tycoonui.screen.TycoonScreen;
import kr.bapuri.tycoonui.screen.tab.TabType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 모드 키바인딩을 관리하는 클래스입니다.
 * 
 * <h3>키바인딩 목록</h3>
 * <table>
 *     <tr><th>키</th><th>동작</th></tr>
 *     <tr><td>` (백틱)</td><td>통합 GUI 열기 (경제 탭)</td></tr>
 *     <tr><td>C</td><td>통합 GUI 열기 (도감 탭)</td></tr>
 *     <tr><td>J</td><td>통합 GUI 열기 (직업 탭)</td></tr>
 * </table>
 * 
 * <h3>핵심 동작</h3>
 * <p>모든 키는 같은 통합 GUI를 열지만, 초기 탭만 다릅니다.</p>
 */
public class TycoonKeybinds {
    
    /** 키바인딩 카테고리 */
    private static final String CATEGORY = "key.category.tycoon";
    
    /** 통합 GUI 열기 (백틱) */
    private static KeyBinding openGuiKey;
    
    /** 도감 탭으로 열기 (C) */
    private static KeyBinding openCodexKey;
    
    /** 직업 탭으로 열기 (J) */
    private static KeyBinding openJobKey;
    
    /**
     * 모든 키바인딩을 등록합니다.
     * 
     * <p>모드 초기화 시 한 번만 호출되어야 합니다.</p>
     */
    public static void register() {
        // 백틱 키 (GRAVE_ACCENT = `)
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tycoon.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            CATEGORY
        ));
        
        // C 키 - 도감
        openCodexKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tycoon.open_codex",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
        ));
        
        // J 키 - 직업
        openJobKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tycoon.open_job",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
        ));
        
        // 틱 이벤트에서 키 입력 처리
        ClientTickEvents.END_CLIENT_TICK.register(TycoonKeybinds::onClientTick);
        
        TycoonUiMod.LOGGER.info("[TycoonUI] Keybindings registered");
    }
    
    /**
     * 클라이언트 틱마다 키 입력을 확인합니다.
     * 
     * @param client Minecraft 클라이언트
     */
    private static void onClientTick(MinecraftClient client) {
        // 월드에 있지 않으면 무시
        if (client.player == null) {
            return;
        }
        
        // 이미 GUI가 열려있으면 무시 (백틱으로 닫기 가능하도록 예외 처리)
        if (client.currentScreen != null) {
            // 현재 TycoonScreen이 열려있고 백틱을 누르면 닫기
            if (client.currentScreen instanceof TycoonScreen && openGuiKey.wasPressed()) {
                client.currentScreen.close();
            }
            return;
        }
        
        // 키 입력 처리
        if (openGuiKey.wasPressed()) {
            openScreen(client, TabType.ECONOMY);
        } else if (openCodexKey.wasPressed()) {
            openScreen(client, TabType.CODEX);
        } else if (openJobKey.wasPressed()) {
            openScreen(client, TabType.JOB);
        }
    }
    
    /**
     * 지정된 탭으로 TycoonScreen을 엽니다.
     * 
     * @param client Minecraft 클라이언트
     * @param initialTab 초기 탭
     */
    private static void openScreen(MinecraftClient client, TabType initialTab) {
        client.setScreen(new TycoonScreen(initialTab));
    }
}
