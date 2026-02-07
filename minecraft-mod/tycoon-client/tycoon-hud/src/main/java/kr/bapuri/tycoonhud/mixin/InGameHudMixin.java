package kr.bapuri.tycoonhud.mixin;

import kr.bapuri.tycoonhud.hud.DungeonMapOverlay;
import kr.bapuri.tycoonhud.hud.HunterHudOverlay;
import kr.bapuri.tycoonhud.hud.VitalHudOverlay;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.JumpingMount;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 바닐라 상태 HUD를 커스텀으로 대체하는 Mixin입니다.
 * 
 * <h3>숨기는 요소 (커스텀 HUD로 대체)</h3>
 * <ul>
 *     <li>체력바 (하트 아이콘)</li>
 *     <li>배고픔바 (고기 아이콘)</li>
 *     <li>갑옷바 (갑옷 아이콘)</li>
 *     <li>공기 방울 바 (산소 아이콘)</li>
 *     <li>경험치 바 (초록색 바) - 탑승 중이 아닐 때만</li>
 * </ul>
 * 
 * <h3>유지되는 요소 (바닐라 그대로)</h3>
 * <ul>
 *     <li>탑승물 체력 (renderMountHealth) - 말 등 탑승 시</li>
 *     <li>점프 게이지 (renderMountJumpBar) - 말 탑승 시</li>
 *     <li>상태 효과 아이콘 - 우측 상단</li>
 *     <li>핫바 - 화면 하단</li>
 *     <li>보스바 - 화면 상단</li>
 *     <li>액션바 메시지 (overlay message) - 핫바 위</li>
 * </ul>
 * 
 * <h3>동작</h3>
 * <p>서버로부터 Vital 데이터를 수신한 경우에만 바닐라 HUD를 숨깁니다.
 * 데이터가 없으면 바닐라 HUD가 정상적으로 표시됩니다 (폴백).</p>
 * <p>탑승물에 탑승 중일 때는 바닐라 점프 게이지/탑승물 체력을 유지합니다.</p>
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    
    @Shadow
    private int scaledWidth;
    
    @Shadow
    private int scaledHeight;
    
    /**
     * renderStatusBars 전체 취소
     * 
     * <p>이 메서드는 다음을 모두 렌더링합니다:</p>
     * <ul>
     *     <li>갑옷 아이콘</li>
     *     <li>체력 하트 (renderHealthBar 호출)</li>
     *     <li>배고픔 아이콘</li>
     *     <li>공기 방울 (물속에서)</li>
     * </ul>
     * 
     * <p>서버 데이터가 있으면 전부 커스텀 HUD로 대체합니다.</p>
     * <p>탑승 중일 때도 숨김 (탑승물 체력은 별도 메서드로 표시)</p>
     * 
     * @param context 렌더링 컨텍스트
     * @param ci 콜백 정보
     */
    @Inject(
        method = "renderStatusBars",
        at = @At("HEAD"),
        cancellable = true
    )
    private void tycoonhud$hideAllStatusBars(DrawContext context, CallbackInfo ci) {
        // 서버 데이터가 있을 때만 바닐라 HUD 전체 숨김
        // 데이터가 없으면 바닐라 HUD 표시 (폴백)
        if (tycoonhud$hasCustomVital()) {
            ci.cancel();
        }
    }
    
    /**
     * renderExperienceBar 취소 (탑승 중이 아닐 때만)
     * 
     * <p>바닐라 경험치 바를 숨기고 커스텀 HUD로 대체합니다.</p>
     * <p>말 등 탑승 중일 때는 점프 게이지가 표시되므로 이 메서드가 호출되지 않습니다.</p>
     * <p>1.20.1에서 메서드 시그니처: renderExperienceBar(DrawContext context, int x)</p>
     * 
     * @param context 렌더링 컨텍스트
     * @param x X 좌표
     * @param ci 콜백 정보
     */
    @Inject(
        method = "renderExperienceBar",
        at = @At("HEAD"),
        cancellable = true
    )
    private void tycoonhud$hideExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        // 탑승 중이면 바닐라 경험치 바 유지 (실제로는 표시 안 됨 - 점프 게이지가 대신 표시)
        // 서버 데이터가 있고 탑승 중이 아닐 때만 숨김
        if (tycoonhud$hasCustomVital() && !tycoonhud$isRidingJumpingMount()) {
            ci.cancel();
        }
    }
    
    /**
     * 커스텀 Vital 데이터 사용 여부 확인
     * 
     * @return 서버에서 Vital 데이터를 받았으면 true
     */
    @Unique
    private boolean tycoonhud$hasCustomVital() {
        return PlayerDataManager.getInstance().getVital() != null;
    }
    
    /**
     * 점프 가능한 탑승물(말 등)에 타고 있는지 확인
     * 
     * @return 점프 가능한 탑승물에 타고 있으면 true
     */
    @Unique
    private boolean tycoonhud$isRidingJumpingMount() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        
        // 플레이어가 탑승 중인 엔티티 확인
        if (client.player.getVehicle() instanceof LivingEntity mount) {
            // 점프 가능한 탑승물인지 확인 (말, 당나귀, 노새 등)
            return mount instanceof JumpingMount;
        }
        return false;
    }
    
    /**
     * 채팅 렌더링 직후에 커스텀 HUD 렌더링
     * 
     * <p>ChatHud.render() 호출 직후에 Inject하여, 커스텀 HUD가 채팅 위에 표시됩니다.</p>
     * <p>바닐라 마인크래프트처럼 채팅 배경은 투과되고 HUD는 잘 보입니다.</p>
     * <p>HudRenderCallback보다 먼저 렌더링되어 순서가 보장됩니다.</p>
     * 
     * <h3>렌더링 순서</h3>
     * <ol>
     *     <li>바닐라 HUD (핫바, 경험치 등)</li>
     *     <li>채팅</li>
     *     <li>우리 커스텀 HUD (여기서 렌더링)</li>
     *     <li>HudRenderCallback (다른 모드들)</li>
     * </ol>
     * 
     * @param context 렌더링 컨텍스트
     * @param tickDelta 틱 델타
     * @param ci 콜백 정보
     */
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/ChatHud;render(Lnet/minecraft/client/gui/DrawContext;III)V",
            shift = At.Shift.AFTER
        )
    )
    private void tycoonhud$renderAfterChat(DrawContext context, float tickDelta, CallbackInfo ci) {
        // 1. 체력/배고픔 등 상태바
        VitalHudOverlay vitalOverlay = VitalHudOverlay.getInstance();
        if (vitalOverlay != null) {
            vitalOverlay.renderDirect(context, tickDelta);
        }
        
        // 2. 던전 맵 HUD (로그라이크 던전 진행 중일 때)
        // 던전 맵이 활성화되어 있으면 헌터 HUD 대신 던전 맵 표시
        if (tycoonhud$isDungeonMapActive()) {
            DungeonMapOverlay dungeonMapOverlay = DungeonMapOverlay.getInstance();
            if (dungeonMapOverlay != null) {
                dungeonMapOverlay.renderDirect(context, tickDelta);
            }
        } else {
            // 3. 헌터 월드 HUD (미니맵 등) - 던전이 아닐 때만
            HunterHudOverlay hunterOverlay = HunterHudOverlay.getInstance();
            if (hunterOverlay != null) {
                hunterOverlay.renderDirect(context, tickDelta);
            }
        }
    }
    
    /**
     * 던전 맵이 활성화되어 있는지 확인
     * 
     * @return 던전 맵 데이터가 있고 활성 상태면 true
     */
    @Unique
    private boolean tycoonhud$isDungeonMapActive() {
        var dungeonMap = PlayerDataManager.getInstance().getDungeonMap();
        return dungeonMap != null && dungeonMap.isActive();
    }
}
