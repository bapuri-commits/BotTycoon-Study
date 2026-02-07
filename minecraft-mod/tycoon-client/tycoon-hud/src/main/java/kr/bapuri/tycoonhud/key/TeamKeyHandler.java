package kr.bapuri.tycoonhud.key;

import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * TeamKeyHandler - 팀 관련 키 바인딩
 * 
 * R키: 조준한 플레이어에게 팀 초대 / 받은 초대 수락
 * Shift+R: 팀 탈퇴
 * 
 * R키 로직:
 * 1. 레이캐스팅으로 조준 중인 플레이어 감지
 * 2. 대상이 있으면 → /team invite <플레이어이름>
 * 3. 대상이 없으면 → /team accept (받은 초대 수락 시도)
 */
public class TeamKeyHandler {

    private static KeyBinding teamKey;
    private static boolean wasPressed = false;
    
    // 팀 초대 범위 (블록)
    private static final double INVITE_RANGE = 5.0;

    /**
     * 키 바인딩 등록
     */
    public static void register() {
        teamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tycoon.team",      // 번역 키
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,        // R키
            "category.tycoon.keys"  // 카테고리
        ));

        // 틱 이벤트 등록
        ClientTickEvents.END_CLIENT_TICK.register(TeamKeyHandler::onTick);
    }

    /**
     * 매 틱마다 호출
     */
    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // 헌터 월드 체크
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        if (profile == null || !profile.isInHunter()) return;

        // R키 상태 확인
        boolean isPressed = teamKey.isPressed();

        // 처음 눌렸을 때만 처리 (한 번만)
        if (isPressed && !wasPressed) {
            // Shift 체크
            boolean isShiftHeld = InputUtil.isKeyPressed(
                client.getWindow().getHandle(),
                GLFW.GLFW_KEY_LEFT_SHIFT
            ) || InputUtil.isKeyPressed(
                client.getWindow().getHandle(),
                GLFW.GLFW_KEY_RIGHT_SHIFT
            );

            if (isShiftHeld) {
                // Shift+R: 팀 탈퇴
                sendTeamLeave();
            } else {
                // R: 팀 초대 (조준 대상) / 수락 (대상 없을 때)
                sendTeamAction(client);
            }
        }

        wasPressed = isPressed;
    }

    /**
     * 팀 초대/수락 요청 전송
     * 
     * 1. 레이캐스팅으로 조준 중인 플레이어 감지
     * 2. 대상이 있으면 → /team invite <플레이어이름>
     * 3. 대상이 없으면 → /team accept (받은 초대 수락 시도)
     */
    private static void sendTeamAction(MinecraftClient client) {
        if (client.player == null) return;

        // 레이캐스팅으로 조준 중인 플레이어 감지
        PlayerEntity target = getTargetPlayer(client);
        
        if (target != null) {
            // 대상이 있으면 초대
            client.player.networkHandler.sendChatCommand("team invite " + target.getName().getString());
        } else {
            // 대상이 없으면 받은 초대 수락 시도
            client.player.networkHandler.sendChatCommand("team accept");
        }
    }
    
    /**
     * 레이캐스팅으로 조준 중인 플레이어 감지
     */
    private static PlayerEntity getTargetPlayer(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        
        Vec3d eyePos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVec(1.0f);
        Vec3d endPos = eyePos.add(lookVec.multiply(INVITE_RANGE));
        
        // 바운딩 박스 확장 (레이와 플레이어 히트박스 교차 검사용)
        Box searchBox = client.player.getBoundingBox()
            .stretch(lookVec.multiply(INVITE_RANGE))
            .expand(1.0);
        
        // 플레이어만 대상으로 필터링
        Predicate<Entity> predicate = entity -> 
            entity instanceof PlayerEntity && 
            entity != client.player && 
            !entity.isSpectator();
        
        // 가장 가까운 엔티티 찾기
        Entity closestEntity = null;
        double closestDistance = INVITE_RANGE;
        
        for (Entity entity : client.world.getOtherEntities(client.player, searchBox, predicate)) {
            Box entityBox = entity.getBoundingBox().expand(entity.getTargetingMargin());
            Optional<Vec3d> hitResult = entityBox.raycast(eyePos, endPos);
            
            if (hitResult.isPresent()) {
                double distance = eyePos.distanceTo(hitResult.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestEntity = entity;
                }
            }
        }
        
        return closestEntity instanceof PlayerEntity ? (PlayerEntity) closestEntity : null;
    }

    /**
     * 팀 탈퇴 요청 전송
     */
    private static void sendTeamLeave() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        client.player.networkHandler.sendChatCommand("team leave");
    }
}

