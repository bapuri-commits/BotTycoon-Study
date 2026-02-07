package kr.bapuri.tycoonhud.hud;

import kr.bapuri.tycoonhud.model.DungeonMapData;
import kr.bapuri.tycoonhud.model.DungeonMapData.MapNode;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.*;

/**
 * 던전 맵 오버레이 - 옛날 지도 스타일
 * 
 * <h3>디자인 특징</h3>
 * <ul>
 *     <li>양피지 배경 (노란/갈색 종이)</li>
 *     <li>잉크로 그린 듯한 노드와 경로</li>
 *     <li>Fog of War - 탐험 안 한 곳은 안개</li>
 *     <li>주변만 표시, 이동 시 빠르게 업데이트</li>
 * </ul>
 */
public class DungeonMapOverlay implements HudRenderCallback {

    // ================================================================================
    // 레이아웃 (헌터 맵보다 약간 작게)
    // ================================================================================
    
    private static final int MAP_SIZE = 70;      // 미니맵 크기
    private static final int PADDING = 12;       // 오른쪽 여백
    private static final int NODE_SIZE = 8;      // 노드 크기
    private static final int NODE_SPACING_X = 20; // 노드 가로 간격
    private static final int NODE_SPACING_Y = 14; // 노드 세로 간격
    
    // ================================================================================
    // 색상 팔레트 - 옛날 지도 스타일
    // ================================================================================
    
    // 양피지 배경색
    private static final int PARCHMENT_BASE = 0xF0D4B896;      // 메인 양피지
    private static final int PARCHMENT_DARK = 0xF0C4A070;      // 어두운 부분
    private static final int PARCHMENT_EDGE = 0xFF8B7355;      // 테두리
    private static final int PARCHMENT_SHADOW = 0x40000000;    // 그림자
    
    // 잉크 색상
    private static final int INK_BLACK = 0xFF2C1810;           // 검은 잉크
    private static final int INK_BROWN = 0xFF4A3728;           // 갈색 잉크
    private static final int INK_RED = 0xFF8B2500;             // 붉은 잉크 (위험)
    private static final int INK_GOLD = 0xFFB8860B;            // 금색 (보물)
    private static final int INK_BLUE = 0xFF2E4A6E;            // 파란 잉크 (휴식)
    private static final int INK_GREEN = 0xFF2E5A2E;           // 초록 잉크 (상점)
    
    // 안개/탐험 안 한 영역
    private static final int FOG_COLOR = 0xC0A09070;           // 안개 (반투명)
    private static final int FOG_EDGE = 0x60806040;            // 안개 가장자리
    
    // 현재 위치/선택 가능
    private static final int GLOW_CURRENT = 0x60FFD700;        // 현재 위치 글로우
    private static final int GLOW_AVAILABLE = 0x4000FF00;      // 선택 가능 글로우
    
    // ================================================================================
    // 상태
    // ================================================================================
    
    private static DungeonMapOverlay INSTANCE;
    private long lastRenderTime = 0;
    private float animPhase = 0;
    
    // 뷰포트 (주변만 표시)
    private int viewCenterDepth = 0;
    private int viewCenterIndex = 0;
    private static final int VIEW_RANGE_DEPTH = 2;  // 현재 깊이 ±2
    private static final int VIEW_RANGE_INDEX = 2;  // 현재 인덱스 ±2
    
    public DungeonMapOverlay() {
        INSTANCE = this;
    }
    
    public static DungeonMapOverlay getInstance() {
        return INSTANCE;
    }
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        // Mixin에서 호출하므로 여기서는 처리 안 함
    }
    
    /**
     * Mixin에서 직접 호출하는 렌더링 메서드
     */
    public void renderDirect(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // 디버그 모드면 스킵
        if (client.options.debugEnabled) return;
        
        // 던전 맵 데이터 확인
        DungeonMapData mapData = PlayerDataManager.getInstance().getDungeonMap();
        if (mapData == null || !mapData.isActive()) return;
        
        // 애니메이션 업데이트
        updateAnimation();
        
        // 뷰포트 업데이트
        updateViewport(mapData);
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        TextRenderer tr = client.textRenderer;
        
        // 우하단 위치 (헌터 맵과 동일)
        int mapX = screenWidth - MAP_SIZE - PADDING;
        int mapY = screenHeight - MAP_SIZE - PADDING;
        
        // 렌더링
        renderMap(context, tr, mapX, mapY, mapData);
    }
    
    private void updateAnimation() {
        long now = System.currentTimeMillis();
        float delta = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        
        animPhase += delta * 2f;
        if (animPhase > Math.PI * 2) animPhase -= Math.PI * 2;
    }
    
    private void updateViewport(DungeonMapData mapData) {
        MapNode current = mapData.getCurrentNode();
        if (current != null) {
            viewCenterDepth = current.getDepth();
            viewCenterIndex = current.getIndex();
        }
    }
    
    // ================================================================================
    // 메인 렌더링
    // ================================================================================
    
    private void renderMap(DrawContext ctx, TextRenderer tr, int x, int y, DungeonMapData mapData) {
        // 1. 양피지 배경
        renderParchmentBackground(ctx, x, y);
        
        // 2. 경로 (노드 연결선)
        renderPaths(ctx, x, y, mapData);
        
        // 3. 노드들
        renderNodes(ctx, x, y, mapData);
        
        // 4. 안개 (Fog of War)
        renderFog(ctx, x, y, mapData);
        
        // 5. 층 표시
        renderFloorIndicator(ctx, tr, x, y, mapData);
    }
    
    /**
     * 양피지 배경 렌더링
     */
    private void renderParchmentBackground(DrawContext ctx, int x, int y) {
        // 그림자
        ctx.fill(x + 2, y + 2, x + MAP_SIZE + 2, y + MAP_SIZE + 2, PARCHMENT_SHADOW);
        
        // 테두리 (찢어진 느낌)
        ctx.fill(x - 2, y - 2, x + MAP_SIZE + 2, y + MAP_SIZE + 2, PARCHMENT_EDGE);
        
        // 메인 양피지
        ctx.fill(x, y, x + MAP_SIZE, y + MAP_SIZE, PARCHMENT_BASE);
        
        // 질감 효과 (세로선)
        for (int i = 5; i < MAP_SIZE; i += 8) {
            int alpha = 0x08 + (i % 16 == 0 ? 0x05 : 0);
            ctx.fill(x + i, y, x + i + 1, y + MAP_SIZE, (alpha << 24) | 0x000000);
        }
        
        // 구겨진 느낌 (대각선)
        for (int i = 0; i < MAP_SIZE; i += 15) {
            ctx.fill(x + i, y + i / 2, x + i + 2, y + i / 2 + 1, 0x0C000000);
        }
        
        // 가장자리 어둡게
        for (int i = 0; i < 4; i++) {
            int alpha = 0x10 - i * 0x04;
            ctx.fill(x, y + i, x + MAP_SIZE, y + i + 1, (alpha << 24) | 0x000000);
            ctx.fill(x, y + MAP_SIZE - i - 1, x + MAP_SIZE, y + MAP_SIZE - i, (alpha << 24) | 0x000000);
            ctx.fill(x + i, y, x + i + 1, y + MAP_SIZE, (alpha << 24) | 0x000000);
            ctx.fill(x + MAP_SIZE - i - 1, y, x + MAP_SIZE - i, y + MAP_SIZE, (alpha << 24) | 0x000000);
        }
    }
    
    /**
     * 경로 렌더링 (노드 연결)
     */
    private void renderPaths(DrawContext ctx, int mapX, int mapY, DungeonMapData mapData) {
        List<MapNode> nodes = mapData.getExploredNodes();
        
        for (MapNode node : nodes) {
            // 뷰포트 범위 체크
            if (!isInViewport(node)) continue;
            
            int[] nodePos = getNodePosition(mapX, mapY, node);
            int nx = nodePos[0];
            int ny = nodePos[1];
            
            // 연결된 다음 노드들로 선 그리기
            for (String connId : node.getConnections()) {
                MapNode connNode = mapData.getNode(connId);
                if (connNode == null) continue;
                
                // 연결 노드도 뷰포트 범위 체크
                if (!isInViewport(connNode)) continue;
                
                int[] connPos = getNodePosition(mapX, mapY, connNode);
                int cx = connPos[0];
                int cy = connPos[1];
                
                // 잉크 선 그리기 (손으로 그린 느낌)
                int lineColor = node.isCleared() ? INK_BROWN : 0x80000000 | (INK_BROWN & 0x00FFFFFF);
                drawInkLine(ctx, nx, ny, cx, cy, lineColor);
            }
        }
    }
    
    /**
     * 손으로 그린 듯한 선 그리기
     */
    private void drawInkLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        // 간단한 브레젠햄 라인 (약간의 흔들림 효과는 추후 추가)
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        
        int x = x1, y = y1;
        while (true) {
            // 2픽셀 두께 + 약간의 불규칙함
            ctx.fill(x, y, x + 2, y + 2, color);
            
            if (x == x2 && y == y2) break;
            
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
    
    /**
     * 노드 렌더링
     */
    private void renderNodes(DrawContext ctx, int mapX, int mapY, DungeonMapData mapData) {
        List<MapNode> nodes = mapData.getExploredNodes();
        String currentId = mapData.getCurrentNodeId();
        
        for (MapNode node : nodes) {
            // 뷰포트 범위 체크
            if (!isInViewport(node)) continue;
            
            int[] pos = getNodePosition(mapX, mapY, node);
            int nx = pos[0];
            int ny = pos[1];
            
            // 현재 위치면 글로우
            if (node.getNodeId().equals(currentId)) {
                float pulse = (float) (1 + 0.2 * Math.sin(animPhase * 2));
                int glowSize = (int) (NODE_SIZE * pulse);
                ctx.fill(nx - glowSize / 2, ny - glowSize / 2, 
                         nx + glowSize / 2 + NODE_SIZE, ny + glowSize / 2 + NODE_SIZE, GLOW_CURRENT);
            }
            // 선택 가능하면 글로우
            else if (mapData.isNodeAvailable(node.getNodeId())) {
                ctx.fill(nx - 2, ny - 2, nx + NODE_SIZE + 2, ny + NODE_SIZE + 2, GLOW_AVAILABLE);
            }
            
            // 노드 본체
            int nodeColor = getNodeColor(node);
            ctx.fill(nx, ny, nx + NODE_SIZE, ny + NODE_SIZE, nodeColor);
            
            // 노드 내부 아이콘/심볼
            renderNodeSymbol(ctx, nx, ny, node);
            
            // 클리어된 노드는 체크 표시
            if (node.isCleared()) {
                ctx.fill(nx + 1, ny + 3, nx + 3, ny + 5, INK_GREEN);
                ctx.fill(nx + 3, ny + 1, nx + 5, ny + 3, INK_GREEN);
            }
        }
    }
    
    /**
     * 노드 색상 결정
     */
    private int getNodeColor(MapNode node) {
        return switch (node.getType()) {
            case "START" -> INK_BROWN;
            case "COMBAT" -> INK_BLACK;
            case "ELITE" -> INK_RED;
            case "SHOP" -> INK_GREEN;
            case "REST" -> INK_BLUE;
            case "BOSS" -> INK_RED;
            case "TREASURE" -> INK_GOLD;
            case "EVENT" -> INK_BROWN;
            default -> INK_BLACK;
        };
    }
    
    /**
     * 노드 내부 심볼 렌더링
     */
    private void renderNodeSymbol(DrawContext ctx, int x, int y, MapNode node) {
        int innerColor = 0xFFD4B896;  // 양피지 색
        
        switch (node.getType()) {
            case "START" -> {
                // S 모양
                ctx.fill(x + 2, y + 2, x + 6, y + 3, innerColor);
                ctx.fill(x + 2, y + 3, x + 3, y + 5, innerColor);
                ctx.fill(x + 2, y + 4, x + 6, y + 5, innerColor);
            }
            case "COMBAT" -> {
                // 검 모양 (X)
                ctx.fill(x + 2, y + 2, x + 3, y + 3, innerColor);
                ctx.fill(x + 5, y + 2, x + 6, y + 3, innerColor);
                ctx.fill(x + 3, y + 3, x + 5, y + 5, innerColor);
                ctx.fill(x + 2, y + 5, x + 3, y + 6, innerColor);
                ctx.fill(x + 5, y + 5, x + 6, y + 6, innerColor);
            }
            case "ELITE" -> {
                // 해골 (E)
                ctx.fill(x + 2, y + 1, x + 6, y + 4, innerColor);
                ctx.fill(x + 3, y + 4, x + 5, y + 7, innerColor);
            }
            case "SHOP" -> {
                // 동전 ($)
                ctx.fill(x + 3, y + 1, x + 5, y + 7, innerColor);
                ctx.fill(x + 2, y + 2, x + 6, y + 3, innerColor);
                ctx.fill(x + 2, y + 5, x + 6, y + 6, innerColor);
            }
            case "REST" -> {
                // 불꽃
                ctx.fill(x + 3, y + 1, x + 5, y + 3, innerColor);
                ctx.fill(x + 2, y + 3, x + 6, y + 5, innerColor);
                ctx.fill(x + 3, y + 5, x + 5, y + 7, innerColor);
            }
            case "BOSS" -> {
                // 왕관
                ctx.fill(x + 1, y + 5, x + 7, y + 7, innerColor);
                ctx.fill(x + 1, y + 3, x + 2, y + 5, innerColor);
                ctx.fill(x + 3, y + 2, x + 5, y + 5, innerColor);
                ctx.fill(x + 6, y + 3, x + 7, y + 5, innerColor);
            }
            case "TREASURE" -> {
                // 상자
                ctx.fill(x + 1, y + 3, x + 7, y + 6, innerColor);
                ctx.fill(x + 2, y + 2, x + 6, y + 3, innerColor);
            }
            default -> {
                // ? 모양
                ctx.fill(x + 2, y + 2, x + 6, y + 3, innerColor);
                ctx.fill(x + 4, y + 3, x + 6, y + 5, innerColor);
                ctx.fill(x + 3, y + 6, x + 5, y + 7, innerColor);
            }
        }
    }
    
    /**
     * 안개 렌더링 (Fog of War)
     */
    private void renderFog(DrawContext ctx, int mapX, int mapY, DungeonMapData mapData) {
        // 맵 가장자리에 안개 효과
        int fogLayers = 6;
        for (int i = 0; i < fogLayers; i++) {
            int alpha = (fogLayers - i) * 0x18;
            int offset = i * 2;
            
            // 상단
            ctx.fill(mapX, mapY + offset, mapX + MAP_SIZE, mapY + offset + 2, (alpha << 24) | (FOG_COLOR & 0x00FFFFFF));
            // 하단
            ctx.fill(mapX, mapY + MAP_SIZE - offset - 2, mapX + MAP_SIZE, mapY + MAP_SIZE - offset, (alpha << 24) | (FOG_COLOR & 0x00FFFFFF));
            // 좌측
            ctx.fill(mapX + offset, mapY, mapX + offset + 2, mapY + MAP_SIZE, (alpha << 24) | (FOG_COLOR & 0x00FFFFFF));
            // 우측
            ctx.fill(mapX + MAP_SIZE - offset - 2, mapY, mapX + MAP_SIZE - offset, mapY + MAP_SIZE, (alpha << 24) | (FOG_COLOR & 0x00FFFFFF));
        }
    }
    
    /**
     * 층 표시 렌더링
     */
    private void renderFloorIndicator(DrawContext ctx, TextRenderer tr, int mapX, int mapY, DungeonMapData mapData) {
        String floorText = mapData.getCurrentFloor() + "F";
        
        // 배경
        int textWidth = tr.getWidth(floorText);
        int textX = mapX + MAP_SIZE - textWidth - 4;
        int textY = mapY + 3;
        
        ctx.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + 9, 0x80000000);
        
        // 텍스트 (잉크 색상)
        ctx.drawText(tr, floorText, textX, textY, INK_BROWN, false);
    }
    
    // ================================================================================
    // 유틸리티
    // ================================================================================
    
    /**
     * 노드 위치 계산 (맵 좌표 기준)
     */
    private int[] getNodePosition(int mapX, int mapY, MapNode node) {
        int centerX = mapX + MAP_SIZE / 2;
        int centerY = mapY + MAP_SIZE / 2;
        
        // 현재 뷰포트 중심 기준 상대 위치
        int relDepth = node.getDepth() - viewCenterDepth;
        int relIndex = node.getIndex() - viewCenterIndex;
        
        // 깊이는 X축 (오른쪽이 진행 방향)
        // 인덱스는 Y축
        int x = centerX + relDepth * NODE_SPACING_X;
        int y = centerY + relIndex * NODE_SPACING_Y;
        
        return new int[]{x, y};
    }
    
    /**
     * 노드가 뷰포트 범위 내에 있는지 확인
     */
    private boolean isInViewport(MapNode node) {
        int depthDiff = Math.abs(node.getDepth() - viewCenterDepth);
        int indexDiff = Math.abs(node.getIndex() - viewCenterIndex);
        
        return depthDiff <= VIEW_RANGE_DEPTH && indexDiff <= VIEW_RANGE_INDEX;
    }
}
