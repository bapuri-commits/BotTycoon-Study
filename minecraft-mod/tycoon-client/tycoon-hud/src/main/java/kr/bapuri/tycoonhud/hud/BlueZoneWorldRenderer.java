package kr.bapuri.tycoonhud.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import kr.bapuri.tycoonhud.model.VitalData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * BlueZoneWorldRenderer - 인게임 3D 자기장 벽 렌더링
 * 
 * 배틀그라운드 스타일의 반투명 자기장 벽을 표시합니다.
 * - 자기장 밖에서는 파란색/흰색 반투명 벽이 보임
 * - 자기장 안에서 밖을 보면 위험 영역이 보임
 */
public class BlueZoneWorldRenderer {
    
    // [v2.9] 자기장 벽 설정 (동적으로 플레이어 위치 기반으로 계산)
    // 세그먼트 수, 높이 등은 renderBlueZoneWall()에서 직접 설정
    
    // 색상 (RGBA)
    private static final float[] COLOR_SAFE = {0.0f, 0.6f, 1.0f, 0.15f};     // 파란색 (안전 영역 경계)
    private static final float[] COLOR_DANGER = {0.0f, 0.5f, 1.0f, 0.3f};    // 진한 파란색 (위험 영역)
    private static final float[] COLOR_SHRINK = {1.0f, 0.6f, 0.0f, 0.2f};    // 주황색 (축소 중)
    
    /**
     * 렌더러 등록
     */
    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BlueZoneWorldRenderer::render);
    }
    
    // [v2.9] 디버그 로깅 쿨다운 (1초에 1번만)
    private static long lastDebugLog = 0;
    
    /**
     * 월드 렌더링 후 호출
     */
    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        VitalData vital = PlayerDataManager.getInstance().getVital();
        if (vital == null || !vital.isHunterMode()) return;
        
        double radius = vital.getBlueZoneRadius();
        double centerX = vital.getBlueZoneCenterX();
        double centerZ = vital.getBlueZoneCenterZ();
        
        // [v2.9] 디버그 로깅 (1초에 1번)
        long now = System.currentTimeMillis();
        if (now - lastDebugLog > 1000) {
            lastDebugLog = now;
            kr.bapuri.tycoonhud.TycoonHudMod.LOGGER.info("[BlueZone3D] center=({}, {}), radius={}", 
                centerX, centerZ, radius);
        }
        
        if (radius <= 0) {
            return;
        }
        
        // 카메라 위치
        Vec3d cameraPos = context.camera().getPos();
        
        // 매트릭스 스택 (null 체크)
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) {
            // Fabric API 버전에 따라 null일 수 있음
            matrices = new MatrixStack();
        }
        matrices.push();
        
        // 카메라 기준으로 변환
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        // 자기장 상태에 따른 색상 결정
        String state = vital.getBlueZoneState();
        float[] color = switch (state) {
            case "SHRINKING" -> COLOR_SHRINK;
            case "FINAL" -> COLOR_DANGER;
            default -> COLOR_SAFE;
        };
        
        // 플레이어가 자기장 밖에 있는지 확인
        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        double distFromCenter = Math.sqrt(
            (playerX - centerX) * (playerX - centerX) +
            (playerZ - centerZ) * (playerZ - centerZ)
        );
        boolean outsideZone = distFromCenter > radius;
        
        // 자기장 밖이면 더 진하게
        if (outsideZone) {
            color = new float[]{color[0], color[1], color[2], color[3] * 2.0f};
        }
        
        // 렌더링
        renderBlueZoneWall(matrices, context.consumers(), centerX, centerZ, radius, color);
        
        matrices.pop();
    }
    
    /**
     * 원통형 자기장 벽 렌더링
     * [v2.9] Fabric 1.20.1 호환 렌더링 - 더 단순화된 방식
     */
    private static void renderBlueZoneWall(MatrixStack matrices, VertexConsumerProvider consumers,
                                           double centerX, double centerZ, double radius, float[] color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        
        // [v2.9] 렌더링 높이를 플레이어 주변으로 제한 (성능 및 가시성)
        MinecraftClient client = MinecraftClient.getInstance();
        float playerY = client.player != null ? (float) client.player.getY() : 64f;
        float yBottom = playerY - 20f;  // 플레이어 아래 20블록
        float yTop = playerY + 50f;     // 플레이어 위 50블록
        
        // Tessellator를 사용한 직접 렌더링
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        
        // [v2.9] OpenGL 상태 저장 후 설정
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // [v2.9] 깊이 테스트를 유지하여 지형에 가려지도록 함
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);  // 깊이 버퍼에 쓰지 않음 (투명 객체)
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        
        // 원통 벽 그리기 (세그먼트 수 줄임 - 성능)
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double angle1 = 2 * Math.PI * i / segments;
            double angle2 = 2 * Math.PI * (i + 1) / segments;
            
            float x1 = (float) (centerX + radius * Math.cos(angle1));
            float z1 = (float) (centerZ + radius * Math.sin(angle1));
            float x2 = (float) (centerX + radius * Math.cos(angle2));
            float z2 = (float) (centerZ + radius * Math.sin(angle2));
            
            // [v2.9] 그라데이션: 아래는 불투명, 위는 투명
            float alphaBottom = color[3] * 0.8f;
            float alphaTop = color[3] * 0.1f;
            
            // 바깥에서 안쪽을 볼 때
            buffer.vertex(matrix, x1, yBottom, z1).color(color[0], color[1], color[2], alphaBottom).next();
            buffer.vertex(matrix, x2, yBottom, z2).color(color[0], color[1], color[2], alphaBottom).next();
            buffer.vertex(matrix, x2, yTop, z2).color(color[0], color[1], color[2], alphaTop).next();
            buffer.vertex(matrix, x1, yTop, z1).color(color[0], color[1], color[2], alphaTop).next();
            
            // 안쪽에서 바깥쪽을 볼 때 (양면 렌더링)
            buffer.vertex(matrix, x2, yBottom, z2).color(color[0], color[1], color[2], alphaBottom).next();
            buffer.vertex(matrix, x1, yBottom, z1).color(color[0], color[1], color[2], alphaBottom).next();
            buffer.vertex(matrix, x1, yTop, z1).color(color[0], color[1], color[2], alphaTop).next();
            buffer.vertex(matrix, x2, yTop, z2).color(color[0], color[1], color[2], alphaTop).next();
        }
        
        tessellator.draw();
        
        // [v2.9] OpenGL 상태 복원
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
