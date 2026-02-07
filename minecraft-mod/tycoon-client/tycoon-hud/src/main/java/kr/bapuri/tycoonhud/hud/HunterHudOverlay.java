package kr.bapuri.tycoonhud.hud;

import kr.bapuri.tycoonhud.model.AugmentData;
import kr.bapuri.tycoonhud.model.MinimapData;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.model.VitalData;
import kr.bapuri.tycoonhud.net.PlayerDataManager;
import kr.bapuri.tycoonhud.screen.AugmentSelectionScreen;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * 헌터 월드 전용 HUD 오버레이 - Premium Edition
 * 
 * <h3>디자인 특징</h3>
 * <ul>
 *     <li>글래스모피즘 미니맵</li>
 *     <li>네온 글로우 테두리</li>
 *     <li>애니메이션 자기장 펄스</li>
 *     <li>그라데이션 거리바</li>
 * </ul>
 */
public class HunterHudOverlay implements HudRenderCallback {
    
    // ================================================================================
    // M키 토글 (큰 맵) & 줌 조절
    // ================================================================================
    
    private static boolean bigMapToggled = false;
    private static final int BIG_MAP_SIZE = 200;  // 큰 맵 크기
    
    // 큰 맵 텍스처 캐싱 (성능 최적화 - 1회 계산, 텍스처로 렌더링)
    private static NativeImageBackedTexture bigMapTexture = null;
    private static Identifier bigMapTextureId = null;
    private static int bigMapCachePlayerX = 0;
    private static int bigMapCachePlayerZ = 0;
    private static long bigMapCacheTime = 0;
    private static final int BIG_MAP_CACHE_DISTANCE = 8;  // 8블록 이동 시 재계산
    private static final long BIG_MAP_CACHE_INTERVAL = 1000;  // 1초마다 재계산
    
    // 미니맵 줌 레벨 (1.0 = 기본, 0.5 = 줌아웃, 2.0 = 줌인)
    private static float minimapZoom = 1.0f;
    private static final float ZOOM_MIN = 0.5f;
    private static final float ZOOM_MAX = 3.0f;
    private static final float ZOOM_STEP = 0.25f;
    
    // ================================================================================
    // [v2.5] 서버 맵 시스템 - 게임 지역 맵
    // ================================================================================
    
    // 서버에서 받은 맵 텍스처
    private static NativeImageBackedTexture serverMapTexture = null;
    private static Identifier serverMapTextureId = null;
    private static boolean serverMapLoaded = false;
    private static int serverMapResolution = 600;
    private static int serverMapCenterX = 0;
    private static int serverMapCenterZ = 0;
    private static int serverMapRadius = 600;
    
    // 큰 맵 이동 (Pan)
    private static float bigMapViewX = 0;  // 뷰 오프셋 (맵 좌표)
    private static float bigMapViewZ = 0;
    private static float bigMapZoom = 1.0f;  // 큰 맵 줌 (별도)
    private static final float BIG_MAP_ZOOM_MIN = 0.5f;
    private static final float BIG_MAP_ZOOM_MAX = 4.0f;
    
    // 드래그 상태
    private static boolean isDragging = false;
    private static double lastDragX = 0;
    private static double lastDragY = 0;
    
    // ================================================================================
    // [v2.8] 자기장 원 텍스처 (Python 생성 이미지)
    // ================================================================================
    
    /** 현재 자기장 텍스처 (파란색 반투명 원) */
    private static final Identifier ZONE_CURRENT_TEXTURE = new Identifier("tycoonhud", "textures/hud/zone_current.png");
    /** 다음 자기장 텍스처 (흰색 실선 테두리) */
    private static final Identifier ZONE_NEXT_TEXTURE = new Identifier("tycoonhud", "textures/hud/zone_next.png");
    /** 원 이미지 해상도 (512x512) */
    private static final int ZONE_TEXTURE_SIZE = 512;
    
    // [v2.9] 텍스처 로드 디버그
    private static boolean textureDebugLogged = false;
    
    /**
     * 서버에서 맵 데이터 수신 시 호출
     */
    public static void onMapDataReceived(kr.bapuri.tycoonhud.model.HunterMapData mapData) {
        if (mapData == null || !mapData.isFullMap()) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        
        // 메인 스레드에서 텍스처 생성
        client.execute(() -> {
            try {
                loadServerMapTexture(mapData, client);
            } catch (Exception e) {
                kr.bapuri.tycoonhud.TycoonHudMod.LOGGER.error("[HunterHUD] Failed to load server map: {}", e.getMessage());
            }
        });
    }
    
    private static void loadServerMapTexture(kr.bapuri.tycoonhud.model.HunterMapData mapData, MinecraftClient client) {
        String base64 = mapData.getMapData();
        if (base64 == null || base64.isEmpty()) return;
        
        try {
            // Base64 디코딩
            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64);
            
            // PNG 이미지 로드
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes);
            NativeImage image = NativeImage.read(bais);
            
            // 기존 텍스처 정리
            if (serverMapTexture != null) {
                serverMapTexture.close();
            }
            
            // 새 텍스처 생성
            serverMapTexture = new NativeImageBackedTexture(image);
            serverMapTextureId = client.getTextureManager().registerDynamicTexture("tycoon_servermap", serverMapTexture);
            
            // 메타데이터 저장
            serverMapResolution = mapData.getResolution();
            serverMapCenterX = mapData.getCenterX();
            serverMapCenterZ = mapData.getCenterZ();
            serverMapRadius = mapData.getRadius();
            serverMapLoaded = true;
            
            // 뷰 초기화
            bigMapViewX = 0;
            bigMapViewZ = 0;
            bigMapZoom = 1.0f;
            
            kr.bapuri.tycoonhud.TycoonHudMod.LOGGER.info("[HunterHUD] Server map texture loaded: {}x{}", image.getWidth(), image.getHeight());
        } catch (Exception e) {
            kr.bapuri.tycoonhud.TycoonHudMod.LOGGER.error("[HunterHUD] Failed to decode map image: {}", e.getMessage());
        }
    }
    
    /**
     * 블록 업데이트 적용
     */
    public static void applyMapUpdates(java.util.List<kr.bapuri.tycoonhud.model.HunterMapData.MapUpdate> updates) {
        if (!serverMapLoaded || serverMapTexture == null || updates == null) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        
        client.execute(() -> {
            try {
                NativeImage image = serverMapTexture.getImage();
                if (image == null) return;
                
                for (kr.bapuri.tycoonhud.model.HunterMapData.MapUpdate update : updates) {
                    int x = update.getX();
                    int z = update.getZ();
                    
                    if (x >= 0 && x < image.getWidth() && z >= 0 && z < image.getHeight()) {
                        // ARGB → ABGR 변환 (NativeImage 형식)
                        int color = update.getColor();
                        int a = (color >> 24) & 0xFF;
                        int r = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int b = color & 0xFF;
                        int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                        
                        image.setColor(x, z, abgr);
                    }
                }
                
                // GPU에 업로드
                serverMapTexture.upload();
            } catch (Exception e) {
                kr.bapuri.tycoonhud.TycoonHudMod.LOGGER.warn("[HunterHUD] Failed to apply map updates: {}", e.getMessage());
            }
        });
    }
    
    /**
     * 큰 맵 드래그 시작
     */
    public static void startDrag(double mouseX, double mouseY) {
        if (!bigMapToggled) return;
        isDragging = true;
        lastDragX = mouseX;
        lastDragY = mouseY;
    }
    
    /**
     * 큰 맵 드래그 중
     */
    public static void onDrag(double mouseX, double mouseY) {
        if (!bigMapToggled || !isDragging) return;
        
        double dx = mouseX - lastDragX;
        double dy = mouseY - lastDragY;
        
        // 뷰 오프셋 업데이트 (줌 레벨 적용)
        float sensitivity = 1.0f / bigMapZoom;
        bigMapViewX -= (float) (dx * sensitivity);
        bigMapViewZ -= (float) (dy * sensitivity);
        
        // 범위 제한
        float maxOffset = serverMapResolution / 2.0f;
        bigMapViewX = Math.max(-maxOffset, Math.min(maxOffset, bigMapViewX));
        bigMapViewZ = Math.max(-maxOffset, Math.min(maxOffset, bigMapViewZ));
        
        lastDragX = mouseX;
        lastDragY = mouseY;
    }
    
    /**
     * 큰 맵 드래그 종료
     */
    public static void endDrag() {
        isDragging = false;
    }
    
    /**
     * 큰 맵 스크롤 줌
     */
    public static void onBigMapScroll(double delta) {
        if (!bigMapToggled) return;
        
        float zoomDelta = (float) (delta * 0.2);
        bigMapZoom = Math.max(BIG_MAP_ZOOM_MIN, Math.min(BIG_MAP_ZOOM_MAX, bigMapZoom + zoomDelta));
    }
    
    /**
     * 큰 맵 드래그 (MapKeyHandler에서 호출)
     */
    public static void dragBigMap(float deltaX, float deltaY) {
        if (!bigMapToggled) return;
        
        // 뷰 오프셋 업데이트 (드래그 방향 반대로)
        float sensitivity = 1.0f / bigMapZoom;
        bigMapViewX -= deltaX * sensitivity;
        bigMapViewZ -= deltaY * sensitivity;
        
        // 범위 제한
        float maxOffset = serverMapResolution / 2.0f;
        bigMapViewX = Math.max(-maxOffset, Math.min(maxOffset, bigMapViewX));
        bigMapViewZ = Math.max(-maxOffset, Math.min(maxOffset, bigMapViewZ));
    }
    
    /**
     * 큰 맵 줌인
     */
    public static void zoomBigMapIn() {
        if (!bigMapToggled) return;
        bigMapZoom = Math.min(BIG_MAP_ZOOM_MAX, bigMapZoom + 0.25f);
    }
    
    /**
     * 큰 맵 줌아웃
     */
    public static void zoomBigMapOut() {
        if (!bigMapToggled) return;
        bigMapZoom = Math.max(BIG_MAP_ZOOM_MIN, bigMapZoom - 0.25f);
    }
    
    /**
     * 큰 맵 뷰 리셋
     */
    public static void resetBigMapView() {
        bigMapViewX = 0;
        bigMapViewZ = 0;
        bigMapZoom = 1.0f;
    }
    
    /**
     * 서버 맵 로드 여부
     */
    public static boolean isServerMapLoaded() {
        return serverMapLoaded;
    }
    
    /**
     * 서버 맵 정리 (게임 종료 시)
     */
    public static void clearServerMap() {
        if (serverMapTexture != null) {
            serverMapTexture.close();
            serverMapTexture = null;
        }
        serverMapTextureId = null;
        serverMapLoaded = false;
        bigMapViewX = 0;
        bigMapViewZ = 0;
        bigMapZoom = 1.0f;
    }
    
    public static void toggleBigMap() {
        bigMapToggled = !bigMapToggled;
        if (bigMapToggled) {
            // 큰 맵 열 때 뷰 리셋
            resetBigMapView();
        }
    }
    
    public static boolean isBigMapOpen() {
        return bigMapToggled;
    }
    
    /**
     * 미니맵 줌인 (+키)
     */
    public static void zoomIn() {
        minimapZoom = Math.min(ZOOM_MAX, minimapZoom + ZOOM_STEP);
    }
    
    /**
     * 미니맵 줌아웃 (-키)
     */
    public static void zoomOut() {
        minimapZoom = Math.max(ZOOM_MIN, minimapZoom - ZOOM_STEP);
    }
    
    /**
     * 현재 줌 레벨
     */
    public static float getZoomLevel() {
        return minimapZoom;
    }
    
    /**
     * 줌 레벨 리셋 (1.0x)
     */
    public static void resetZoom() {
        minimapZoom = 1.0f;
    }
    
    // ================================================================================
    // 증강 선택 알림
    // ================================================================================
    
    /**
     * Space 키로 증강 선택 창 열기
     */
    public static void openAugmentSelection() {
        MinecraftClient client = MinecraftClient.getInstance();
        AugmentData augmentData = PlayerDataManager.getInstance().getAugmentData();
        
        if (augmentData != null && augmentData.isShowSelection() 
            && augmentData.getChoices() != null && !augmentData.getChoices().isEmpty()) {
            client.execute(() -> {
                client.setScreen(new AugmentSelectionScreen(augmentData));
            });
        }
    }
    
    /**
     * 증강 선택 대기 중인지 확인
     * - showSelection=true AND choices 있음 → UI 열 수 있음
     * - augmentPending=true → 알림 표시 (G키로 요청)
     */
    public static boolean isAugmentPending() {
        AugmentData augmentData = PlayerDataManager.getInstance().getAugmentData();
        if (augmentData == null) return false;
        
        // UI 열 수 있는 상태
        if (augmentData.isShowSelection() 
            && augmentData.getChoices() != null && !augmentData.getChoices().isEmpty()) {
            return true;
        }
        
        // 알림만 온 상태 (G키로 서버에 요청)
        return augmentData.isAugmentPending();
    }
    
    // ================================================================================
    // 레이아웃
    // ================================================================================
    
    private static final int MINIMAP_SIZE = 85;   // 적당한 크기
    private static final int MINIMAP_RANGE = 150; // [v2.8] 미니맵 표시 범위 (블록)
    private static final int PADDING = 12;        // 오른쪽 여백 (잘림 방지)
    private static final int DISTANCE_BAR_HEIGHT = 8;
    private static final int GAP = 4;
    
    // ================================================================================
    // 색상 팔레트 - Futuristic Scanner
    // ================================================================================
    
    // 미니맵 배경 (더 밝게)
    private static final int BG_DARK = 0xB0203040;            // 밝은 청색 계열
    private static final int BG_GRID = 0x25FFFFFF;
    
    // 테두리 (네온 글로우)
    private static final int BORDER_GLOW_OUTER = 0x2000E5FF;
    private static final int BORDER_GLOW_INNER = 0x3000E5FF;  // 연하게
    private static final int BORDER_ACCENT = 0xFF00E5FF;
    
    // 자기장 (연하게)
    private static final int ZONE_SAFE = 0x4000C8FF;          // 안전 구역 (연한 청색)
    private static final int ZONE_BORDER = 0xC0FFFFFF;        // 현재 경계 (연하게)
    private static final int ZONE_NEXT = 0xB000AAFF;          // 다음 경계 (연하게)
    private static final int ZONE_SHRINK = 0xC0F39C12;        // 축소 중 (연하게)
    private static final int ZONE_DANGER = 0xD0E74C3C;        // 위험
    
    // 마커
    private static final int MARKER_SELF = 0xFFFFFF00;        // 내 위치
    private static final int MARKER_SELF_GLOW = 0x60FFFF00;
    private static final int MARKER_TEAM = 0xFF2ECC71;        // 팀원
    private static final int MARKER_ENEMY = 0xFFE74C3C;       // 적
    private static final int MARKER_AIRDROP = 0xFFFFD700;     // 에어드랍
    private static final int MARKER_AIRDROP_GLOW = 0x40FFD700;
    
    // 거리바
    private static final int DIST_SAFE = 0xFF00FF88;
    private static final int DIST_WARN = 0xFFFFAA00;
    private static final int DIST_DANGER = 0xFFFF4444;
    
    // 페이즈 정보
    private static final int PHASE_WAIT = 0xFF00FF88;
    private static final int PHASE_SHRINK = 0xFFFF6600;
    private static final int PHASE_FINAL = 0xFFFF0000;
    
    // ================================================================================
    // 싱글톤 (Mixin에서 직접 호출용)
    // ================================================================================
    
    private static HunterHudOverlay INSTANCE;
    
    public HunterHudOverlay() {
        INSTANCE = this;
    }
    
    public static HunterHudOverlay getInstance() {
        return INSTANCE;
    }
    
    // ================================================================================
    // 애니메이션
    // ================================================================================
    
    private long lastRenderTime = 0;
    private float pulsePhase = 0;
    private float scanLineY = 0;
    
    /**
     * Mixin에서 직접 호출하는 렌더링 메서드
     * 채팅 렌더링 후에 호출되어 채팅 위에 HUD가 표시됨
     */
    public void renderDirect(DrawContext context, float tickDelta) {
        doRender(context, tickDelta);
    }
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        // Mixin에서 직접 렌더링하므로 여기서는 아무것도 하지 않음
        // 채팅 후에 렌더링되어야 채팅에 가려지지 않음
    }
    
    /**
     * 실제 렌더링 로직
     */
    private void doRender(DrawContext context, float tickDelta) {
        // TAB 오버레이와 겹침 방지
        if (HunterTabOverlay.isTabPressed()) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.options.debugEnabled) return;
        
        PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
        if (profile == null || !profile.isInHunter()) return;
        
        VitalData vital = PlayerDataManager.getInstance().getVital();
        if (vital == null || !vital.isHunterMode()) return;
        
        updateAnimation();
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        TextRenderer tr = client.textRenderer;
        
        // 큰 맵 토글 시 화면 중앙에 큰 맵 표시
        if (bigMapToggled) {
            renderBigMap(context, tr, screenWidth, screenHeight, vital);
            return;  // 큰 맵 열리면 미니맵 숨김
        }
        
        // 우하단 위치 (패딩으로 잘림 방지)
        int minimapX = screenWidth - MINIMAP_SIZE - PADDING;
        int minimapY = screenHeight - MINIMAP_SIZE - DISTANCE_BAR_HEIGHT - GAP - PADDING;
        
        // 렌더링
        renderMinimap(context, minimapX, minimapY, vital);
        
        // 거리바와 페이즈 패널 (미니맵 아래 좌우 정렬)
        int barY = minimapY + MINIMAP_SIZE + GAP;
        int distBarWidth = (MINIMAP_SIZE - GAP) / 2;  // 반반 나누기
        renderDistanceBar(context, tr, minimapX, barY, vital, distBarWidth);
        renderPhasePanel(context, tr, minimapX + distBarWidth + GAP, barY, vital, distBarWidth);
        
        // 증강 선택 알림 (화면 중앙 상단)
        renderAugmentNotification(context, tr, screenWidth, screenHeight);
    }
    
    /**
     * 증강 선택 알림 렌더링 (체력바 위)
     */
    private void renderAugmentNotification(DrawContext ctx, TextRenderer tr, int screenWidth, int screenHeight) {
        if (!isAugmentPending()) return;
        
        // 화면 중앙 상단 (체력바 위쪽)
        String text = "⚔ 증강 선택 가능! [G]";
        int textWidth = tr.getWidth(text);
        
        int boxWidth = textWidth + 20;
        int boxHeight = 18;
        int x = (screenWidth - boxWidth) / 2;
        int y = screenHeight - 85; // 체력바 위
        
        // 애니메이션 펄스
        float pulse = (float) (0.7 + 0.3 * Math.sin(pulsePhase * 2));
        int alpha = (int) (pulse * 255);
        int bgColor = (0xD0 << 24) | (0x20 << 16) | (0x30 << 8) | 0x50;
        int borderColor = (alpha << 24) | (0x00 << 16) | (0xE5 << 8) | 0xFF;
        
        // 배경
        ctx.fill(x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, borderColor);
        ctx.fill(x, y, x + boxWidth, y + boxHeight, bgColor);
        
        // 상단 액센트
        ctx.fill(x, y, x + boxWidth, y + 2, 0xFF00E5FF);
        
        // 텍스트
        ctx.drawText(tr, text, x + 10, y + 5, 0xFFFFFF00, true);
    }
    
    /**
     * M키 토글 시 화면 중앙에 큰 맵 표시
     * - 지형 렌더링 포함
     * - 자기장, 마커 오버레이
     */
    private void renderBigMap(DrawContext ctx, TextRenderer tr, int screenWidth, int screenHeight, VitalData vital) {
        MinecraftClient client = MinecraftClient.getInstance();
        int mapSize = BIG_MAP_SIZE;
        int x = (screenWidth - mapSize) / 2;
        int y = (screenHeight - mapSize) / 2;
        
        // 딤 배경
        ctx.fill(0, 0, screenWidth, screenHeight, 0x80000000);
        
        // 외부 글로우
        ctx.fill(x - 6, y - 6, x + mapSize + 6, y + mapSize + 6, BORDER_GLOW_OUTER);
        ctx.fill(x - 3, y - 3, x + mapSize + 3, y + mapSize + 3, BORDER_GLOW_INNER);
        
        // 메인 배경
        ctx.fill(x, y, x + mapSize, y + mapSize, BG_DARK);
        
        // [v2.5] 서버 맵 또는 레거시 지형 렌더링
        if (serverMapLoaded && serverMapTextureId != null) {
            // 서버 맵 렌더링 (이동/줌 지원)
            renderServerMap(ctx, x, y, mapSize, vital);
        } else if (client.world != null && client.player != null) {
            // 레거시: 클라이언트 지형 렌더링
            renderBigMapTerrainLegacy(ctx, x, y, mapSize, client);
        }
        
        int centerX = x + mapSize / 2;
        int centerY = y + mapSize / 2;
        
        // [v2.8] 플레이어 실시간 위치 사용 (VitalData 대신 클라이언트 직접 참조)
        double playerX = client.player != null ? client.player.getX() : vital.getPlayerX();
        double playerZ = client.player != null ? client.player.getZ() : vital.getPlayerZ();
        
        // 자기장 계산
        double blueZoneRadius = vital.getBlueZoneRadius();
        if (blueZoneRadius <= 0) blueZoneRadius = 500;
        
        // 자기장 중심 계산 (월드 좌표 기반)
        double bzCenterXWorld = vital.getBlueZoneCenterX();
        double bzCenterZWorld = vital.getBlueZoneCenterZ();
        
        // [v2.9] 디버그 로깅
        // kr.bapuri.tycoonhud.TycoonHudMod.LOGGER.info("[BigMap] Zone: center=({}, {}), radius={}, player=({}, {})",
        //     bzCenterXWorld, bzCenterZWorld, blueZoneRadius, playerX, playerZ);
        
        // [v2.9] 스케일: 자기장이 맵의 80%를 차지하도록 계산
        // 자기장 중심을 맵 중앙에 배치하고, 플레이어는 상대 위치로 표시
        double scale = (mapSize / 2.0 - 10) / (blueZoneRadius * 1.2);
        
        // [v2.9] 큰 맵에서는 자기장 중심을 맵 중앙에 고정
        // 플레이어 위치는 자기장 중심 기준 상대 위치로 표시
        int bzDisplayX = centerX;  // 자기장 중심 = 맵 중앙
        int bzDisplayY = centerY;
        
        // 플레이어 오프셋 (자기장 중심 기준)
        int playerOffsetX = (int) ((playerX - bzCenterXWorld) * scale);
        int playerOffsetY = (int) ((playerZ - bzCenterZWorld) * scale);
        int playerDisplayX = centerX + playerOffsetX;
        int playerDisplayY = centerY + playerOffsetY;
        
        int zoneRadius = (int) (blueZoneRadius * scale);
        
        // [v2.8] 현재 자기장 - 텍스처로 렌더링
        int zoneDiameter = zoneRadius * 2;
        if (zoneDiameter > 0) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            ctx.drawTexture(ZONE_CURRENT_TEXTURE, 
                bzDisplayX - zoneRadius, bzDisplayY - zoneRadius, 
                zoneDiameter, zoneDiameter,
                0, 0, ZONE_TEXTURE_SIZE, ZONE_TEXTURE_SIZE, 
                ZONE_TEXTURE_SIZE, ZONE_TEXTURE_SIZE);
            RenderSystem.disableBlend();
        }
        
        // 자기장 중심 마커 (작은 십자가)
        if (bzDisplayX >= x && bzDisplayX < x + mapSize && bzDisplayY >= y && bzDisplayY < y + mapSize) {
            ctx.fill(bzDisplayX - 3, bzDisplayY, bzDisplayX + 4, bzDisplayY + 1, 0xFF00AAFF);
            ctx.fill(bzDisplayX, bzDisplayY - 3, bzDisplayX + 1, bzDisplayY + 4, 0xFF00AAFF);
        }
        
        // [v2.9] 다음 자기장 - 텍스처로 렌더링 (자기장 중심 기준)
        if (vital.hasNextZone()) {
            double nextRadius = vital.getBlueZoneNextRadius();
            double nextCenterX = vital.getBlueZoneNextCenterX();
            double nextCenterZ = vital.getBlueZoneNextCenterZ();
            
            int nextZoneRadius = (int) (nextRadius * scale);
            // [v2.9] 현재 자기장 중심 기준 상대 좌표
            int nextOffsetX = (int) ((nextCenterX - bzCenterXWorld) * scale);
            int nextOffsetY = (int) ((nextCenterZ - bzCenterZWorld) * scale);
            int nextDisplayX = centerX + nextOffsetX;  // centerX = 현재 자기장 중심 = 맵 중앙
            int nextDisplayY = centerY + nextOffsetY;
            int nextDiameter = nextZoneRadius * 2;
            
            if (nextDiameter > 0) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                ctx.drawTexture(ZONE_NEXT_TEXTURE, 
                    nextDisplayX - nextZoneRadius, nextDisplayY - nextZoneRadius, 
                    nextDiameter, nextDiameter,
                    0, 0, ZONE_TEXTURE_SIZE, ZONE_TEXTURE_SIZE, 
                    ZONE_TEXTURE_SIZE, ZONE_TEXTURE_SIZE);
                RenderSystem.disableBlend();
            }
            
            // 다음 자기장 중심 (작은 십자가)
            if (nextDisplayX >= x && nextDisplayX < x + mapSize && 
                nextDisplayY >= y && nextDisplayY < y + mapSize) {
                ctx.fill(nextDisplayX - 2, nextDisplayY, nextDisplayX + 3, nextDisplayY + 1, 0xDDFFFFFF);
                ctx.fill(nextDisplayX, nextDisplayY - 2, nextDisplayX + 1, nextDisplayY + 3, 0xDDFFFFFF);
            }
        }
        
        // 마커들 (자기장 중심 기준 좌표 사용)
        MinimapData minimap = PlayerDataManager.getInstance().getMinimap();
        if (minimap != null) {
            for (MinimapData.PlayerMarker marker : minimap.getPlayers()) {
                // [v2.9] 자기장 중심 기준 상대 좌표
                int offX = (int) ((marker.getX() - bzCenterXWorld) * scale);
                int offY = (int) ((marker.getZ() - bzCenterZWorld) * scale);
                int dx = centerX + offX;
                int dy = centerY + offY;
                
                if (dx >= x && dx < x + mapSize && dy >= y && dy < y + mapSize) {
                    int color = marker.isTeammate() ? MARKER_TEAM : MARKER_ENEMY;
                    ctx.fill(dx - 3, dy - 3, dx + 4, dy + 4, color);
                }
            }
            
            for (MinimapData.AirdropMarker airdrop : minimap.getAirdrops()) {
                // [v2.9] 자기장 중심 기준 상대 좌표
                int offX = (int) ((airdrop.getX() - bzCenterXWorld) * scale);
                int offY = (int) ((airdrop.getZ() - bzCenterZWorld) * scale);
                int dx = centerX + offX;
                int dy = centerY + offY;
                
                if (dx >= x && dx < x + mapSize && dy >= y && dy < y + mapSize) {
                    int color = airdrop.isLooted() ? 0xFF888888 : MARKER_AIRDROP;
                    if (!airdrop.isLooted()) {
                        ctx.fill(dx - 4, dy - 4, dx + 5, dy + 5, MARKER_AIRDROP_GLOW);
                    }
                    ctx.fill(dx, dy - 3, dx + 1, dy - 2, color);
                    ctx.fill(dx - 1, dy - 2, dx + 2, dy - 1, color);
                    ctx.fill(dx - 2, dy - 1, dx + 3, dy, color);
                    ctx.fill(dx - 3, dy, dx + 4, dy + 1, color);
                    ctx.fill(dx - 2, dy + 1, dx + 3, dy + 2, color);
                    ctx.fill(dx - 1, dy + 2, dx + 2, dy + 3, color);
                    ctx.fill(dx, dy + 3, dx + 1, dy + 4, color);
                }
            }
        }
        
        // [v2.9] 내 위치 (자기장 중심 기준 상대 위치)
        if (playerDisplayX >= x && playerDisplayX < x + mapSize && 
            playerDisplayY >= y && playerDisplayY < y + mapSize) {
            ctx.fill(playerDisplayX - 5, playerDisplayY - 5, playerDisplayX + 6, playerDisplayY + 6, MARKER_SELF_GLOW);
            ctx.fill(playerDisplayX - 3, playerDisplayY - 3, playerDisplayX + 4, playerDisplayY + 4, MARKER_SELF);
            ctx.fill(playerDisplayX, playerDisplayY - 4, playerDisplayX + 1, playerDisplayY, 0xFFFFFFFF);
        }
        
        // 방향 표시
        renderBigMapDirections(ctx, tr, x, y, mapSize);
        
        // 상단 액센트
        ctx.fill(x, y, x + mapSize, y + 2, BORDER_ACCENT);
        
        // 큰 맵 거리바 (맵 상단)
        renderBigMapDistanceBar(ctx, tr, x, y - 20, mapSize, vital);
        
        // 안내 텍스트
        String hint = "M 또는 ESC로 닫기 | 마우스 드래그: 이동 | 스크롤: 확대/축소";
        int hintWidth = tr.getWidth(hint);
        ctx.drawText(tr, hint, (screenWidth - hintWidth) / 2, y + mapSize + 10, 0xFFAAAAAA, true);
    }
    
    /**
     * 큰 맵 거리바 렌더링 (배틀그라운드 스타일)
     */
    private void renderBigMapDistanceBar(DrawContext ctx, TextRenderer tr, int x, int y, int mapSize, VitalData vital) {
        int barWidth = mapSize;
        int barHeight = 14;
        
        // 배경
        ctx.fill(x, y, x + barWidth, y + barHeight, 0xCC1A1A2E);
        
        // 자기장까지 거리 계산
        double playerX = vital.getPlayerX();
        double playerZ = vital.getPlayerZ();
        double centerX = vital.getBlueZoneCenterX();
        double centerZ = vital.getBlueZoneCenterZ();
        double radius = vital.getBlueZoneRadius();
        
        double distFromCenter = Math.sqrt(
            (playerX - centerX) * (playerX - centerX) +
            (playerZ - centerZ) * (playerZ - centerZ)
        );
        double distToEdge = distFromCenter - radius;  // 음수면 안전 영역 내부
        
        // 페이즈 정보
        String phaseText = vital.getBlueZoneState();
        if ("WAITING".equals(phaseText)) {
            phaseText = "Phase " + (vital.getBlueZonePhase() + 1) + " 대기중";
        } else if ("SHRINKING".equals(phaseText)) {
            phaseText = "Phase " + (vital.getBlueZonePhase() + 1) + " 축소중";
        } else if ("FINAL".equals(phaseText)) {
            phaseText = "최종 Phase";
        }
        
        // 거리 텍스트
        String distText;
        int distColor;
        if (distToEdge <= 0) {
            distText = "안전 영역";
            distColor = DIST_SAFE;
        } else if (distToEdge < 100) {
            distText = String.format("%.0fm", distToEdge);
            distColor = DIST_WARN;
        } else {
            distText = String.format("%.0fm", distToEdge);
            distColor = DIST_DANGER;
        }
        
        // 좌측: 페이즈
        ctx.drawText(tr, phaseText, x + 4, y + 3, 0xFF00E5FF, false);
        
        // 중앙: 거리
        int distWidth = tr.getWidth(distText);
        ctx.drawText(tr, distText, x + (barWidth - distWidth) / 2, y + 3, distColor, false);
        
        // 우측: 줌 레벨
        String zoomText = String.format("%.1fx", bigMapZoom);
        int zoomWidth = tr.getWidth(zoomText);
        ctx.drawText(tr, zoomText, x + barWidth - zoomWidth - 4, y + 3, 0xAAFFFFFF, false);
        
        // 하단 액센트
        ctx.fill(x, y + barHeight - 1, x + barWidth, y + barHeight, BORDER_ACCENT);
    }
    
    /**
     * [v2.5] 서버 맵 렌더링 (이동/줌 지원)
     */
    private void renderServerMap(DrawContext ctx, int mapX, int mapY, int displaySize, VitalData vital) {
        if (serverMapTexture == null || serverMapTextureId == null) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        
        // 플레이어 위치를 맵 좌표로 변환
        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        
        // 월드 좌표 → 맵 픽셀 좌표
        double scale = (double) serverMapResolution / (serverMapRadius * 2);
        float playerMapX = (float) ((playerX - serverMapCenterX + serverMapRadius) * scale);
        float playerMapZ = (float) ((playerZ - serverMapCenterZ + serverMapRadius) * scale);
        
        // 뷰 중심 (기본: 플레이어 위치, 드래그로 이동 가능)
        float viewCenterX = playerMapX + bigMapViewX;
        float viewCenterZ = playerMapZ + bigMapViewZ;
        
        // 표시할 영역 계산 (줌 적용)
        float viewRadius = (serverMapResolution / 2.0f) / bigMapZoom;
        float srcX = viewCenterX - viewRadius;
        float srcZ = viewCenterZ - viewRadius;
        float srcSize = viewRadius * 2;
        
        // 범위 클램핑
        srcX = Math.max(0, Math.min(serverMapResolution - srcSize, srcX));
        srcZ = Math.max(0, Math.min(serverMapResolution - srcSize, srcZ));
        
        // 텍스처 렌더링
        RenderSystem.setShaderTexture(0, serverMapTextureId);
        ctx.drawTexture(serverMapTextureId, 
            mapX, mapY, displaySize, displaySize,
            (int) srcX, (int) srcZ, (int) srcSize, (int) srcSize,
            serverMapResolution, serverMapResolution);
        
        // 플레이어 위치 마커 (화면 좌표로 변환)
        float playerScreenX = mapX + ((playerMapX - srcX) / srcSize) * displaySize;
        float playerScreenZ = mapY + ((playerMapZ - srcZ) / srcSize) * displaySize;
        
        // 마커가 맵 안에 있을 때만 표시
        if (playerScreenX >= mapX && playerScreenX < mapX + displaySize &&
            playerScreenZ >= mapY && playerScreenZ < mapY + displaySize) {
            int px = (int) playerScreenX;
            int pz = (int) playerScreenZ;
            
            // 플레이어 화살표 마커
            renderPlayerArrowMarker(ctx, px, pz, client.player.getYaw());
        }
        
        // 줌 레벨 표시
        if (Math.abs(bigMapZoom - 1.0f) > 0.01f) {
            TextRenderer tr = client.textRenderer;
            String zoomText = String.format("%.1fx", bigMapZoom);
            ctx.drawText(tr, zoomText, mapX + displaySize - tr.getWidth(zoomText) - 4, mapY + 4, 0xFFFFFFFF, true);
        }
        
        // 드래그 힌트
        TextRenderer tr = client.textRenderer;
        String dragHint = "드래그: 이동 | 스크롤: 줌";
        ctx.drawText(tr, dragHint, mapX + 4, mapY + displaySize - 12, 0xAAFFFFFF, false);
    }
    
    /**
     * [Legacy] 큰 맵 지형 렌더링 (텍스처 캐싱)
     * 서버 맵이 없을 때 사용하는 레거시 방식
     * - 1회 계산 → NativeImage 텍스처로 저장
     * - 매 프레임 1회의 텍스처 드로우만 수행 (40,000배 개선)
     */
    private void renderBigMapTerrainLegacy(DrawContext ctx, int mapX, int mapY, int mapSize, MinecraftClient client) {
        World world = client.world;
        if (world == null || client.player == null) return;
        
        int playerX = (int) client.player.getX();
        int playerZ = (int) client.player.getZ();
        int playerY = (int) client.player.getY();
        
        long now = System.currentTimeMillis();
        int dx = Math.abs(playerX - bigMapCachePlayerX);
        int dz = Math.abs(playerZ - bigMapCachePlayerZ);
        boolean needsUpdate = bigMapTexture == null 
            || dx >= BIG_MAP_CACHE_DISTANCE 
            || dz >= BIG_MAP_CACHE_DISTANCE
            || (now - bigMapCacheTime) >= BIG_MAP_CACHE_INTERVAL;
        
        if (needsUpdate) {
            // 텍스처 재계산
            updateBigMapTexture(world, playerX, playerZ, playerY, mapSize, client);
            bigMapCachePlayerX = playerX;
            bigMapCachePlayerZ = playerZ;
            bigMapCacheTime = now;
        }
        
        // [v2.8] 텍스처 렌더링 (드래그/줌 적용)
        if (bigMapTextureId != null) {
            // 뷰 영역 계산 (줌 적용)
            float viewRadius = (mapSize / 2.0f) / bigMapZoom;
            float viewCenterX = mapSize / 2.0f + bigMapViewX;
            float viewCenterZ = mapSize / 2.0f + bigMapViewZ;
            
            float srcX = viewCenterX - viewRadius;
            float srcZ = viewCenterZ - viewRadius;
            float srcSize = viewRadius * 2;
            
            // 범위 클램핑
            srcX = Math.max(0, Math.min(mapSize - srcSize, srcX));
            srcZ = Math.max(0, Math.min(mapSize - srcSize, srcZ));
            
            ctx.drawTexture(bigMapTextureId, 
                mapX, mapY, mapSize, mapSize,
                (int) srcX, (int) srcZ, (int) srcSize, (int) srcSize,
                mapSize, mapSize);
            
            // 줌 레벨 표시
            if (Math.abs(bigMapZoom - 1.0f) > 0.01f) {
                TextRenderer tr = client.textRenderer;
                String zoomText = String.format("%.1fx", bigMapZoom);
                ctx.drawText(tr, zoomText, mapX + mapSize - tr.getWidth(zoomText) - 4, mapY + 4, 0xFFFFFFFF, true);
            }
            
            // 드래그 힌트
            TextRenderer tr = client.textRenderer;
            String dragHint = "드래그: 이동 | 스크롤: 줌";
            ctx.drawText(tr, dragHint, mapX + 4, mapY + mapSize - 12, 0xAAFFFFFF, false);
        }
    }
    
    /**
     * 큰 맵 텍스처 생성/업데이트
     */
    private void updateBigMapTexture(World world, int playerX, int playerZ, int playerY, int mapSize, MinecraftClient client) {
        // 텍스처 초기화
        if (bigMapTexture == null) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, mapSize, mapSize, false);
            bigMapTexture = new NativeImageBackedTexture(image);
            bigMapTextureId = client.getTextureManager().registerDynamicTexture("tycoon_bigmap", bigMapTexture);
        }
        
        NativeImage image = bigMapTexture.getImage();
        if (image == null) return;
        
        int range = 128;
        int centerMapX = mapSize / 2;
        int centerMapY = mapSize / 2;
        float radius = mapSize / 2.0f;
        
        for (int px = 0; px < mapSize; px++) {
            for (int pz = 0; pz < mapSize; pz++) {
                // 원형 클리핑
                float ddx = px - centerMapX;
                float ddz = pz - centerMapY;
                float distFromCenter = (float) Math.sqrt(ddx * ddx + ddz * ddz);
                
                if (distFromCenter > radius) {
                    image.setColor(px, pz, 0x00000000);  // 투명
                    continue;
                }
                
                double relX = (px - centerMapX) / (double) mapSize * range * 2;
                double relZ = (pz - centerMapY) / (double) mapSize * range * 2;
                
                int worldX = playerX + (int) relX;
                int worldZ = playerZ + (int) relZ;
                
                TerrainInfo terrain = getTerrainInfo(world, worldX, worldZ);
                int color = applyHeightShading(terrain.color, terrain.height, playerY);
                
                // 가장자리 페이드
                float fadeStart = radius * 0.85f;
                if (distFromCenter > fadeStart) {
                    float fadeRatio = (distFromCenter - fadeStart) / (radius - fadeStart);
                    color = applyFade(color, fadeRatio);
                }
                
                // NativeImage는 ABGR 형식 사용
                int a = (color >> 24) & 0xFF;
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                
                image.setColor(px, pz, abgr);
            }
        }
        
        // GPU에 업로드
        bigMapTexture.upload();
    }
    
    /**
     * 큰 맵 방향 표시
     */
    private void renderBigMapDirections(DrawContext ctx, TextRenderer tr, int mapX, int mapY, int mapSize) {
        int cx = mapX + mapSize / 2;
        int cy = mapY + mapSize / 2;
        int color = 0xDDFFFFFF;
        
        // N (북쪽 - 위) - 빨간색 강조
        ctx.drawText(tr, "N", cx - 3, mapY + 4, 0xFFFF5555, true);
        // S (남쪽 - 아래)
        ctx.drawText(tr, "S", cx - 3, mapY + mapSize - 12, color, true);
        // E (동쪽 - 오른쪽)
        ctx.drawText(tr, "E", mapX + mapSize - 10, cy - 4, color, true);
        // W (서쪽 - 왼쪽)
        ctx.drawText(tr, "W", mapX + 4, cy - 4, color, true);
    }
    
    private void updateAnimation() {
        long now = System.currentTimeMillis();
        float delta = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        
        pulsePhase += delta * 2f;
        if (pulsePhase > Math.PI * 2) pulsePhase -= Math.PI * 2;
        
        scanLineY += delta * 40f;
        if (scanLineY > MINIMAP_SIZE) scanLineY = 0;
    }
    
    // ================================================================================
    // 미니맵
    // ================================================================================
    
    private void renderMinimap(DrawContext ctx, int x, int y, VitalData vital) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // 외부 글로우
        ctx.fill(x - 4, y - 4, x + MINIMAP_SIZE + 4, y + MINIMAP_SIZE + 4, BORDER_GLOW_OUTER);
        ctx.fill(x - 2, y - 2, x + MINIMAP_SIZE + 2, y + MINIMAP_SIZE + 2, BORDER_GLOW_INNER);
        
        // 메인 배경
        ctx.fill(x, y, x + MINIMAP_SIZE, y + MINIMAP_SIZE, BG_DARK);
        
        // 지형 렌더링 (줌 레벨 적용)
        if (client.world != null && client.player != null) {
            renderTerrain(ctx, x, y, client);
        } else {
            // 월드 없으면 그리드만
            renderGrid(ctx, x, y);
        }
        
        // 스캔 라인 (애니메이션)
        int scanY = y + (int) scanLineY;
        if (scanY >= y && scanY < y + MINIMAP_SIZE) {
            ctx.fill(x, scanY, x + MINIMAP_SIZE, scanY + 1, 0x3000E5FF);
            ctx.fill(x, scanY + 1, x + MINIMAP_SIZE, scanY + 2, 0x1800E5FF);
        }
        
        int cx = x + MINIMAP_SIZE / 2;
        int cy = y + MINIMAP_SIZE / 2;
        
        // [v2.8] 플레이어 실시간 위치 사용
        double playerX = client.player != null ? client.player.getX() : vital.getPlayerX();
        double playerZ = client.player != null ? client.player.getZ() : vital.getPlayerZ();
        
        // 자기장 데이터
        double blueZoneRadius = vital.getBlueZoneRadius();
        if (blueZoneRadius <= 0) blueZoneRadius = 500;
        double bzCenterX = vital.getBlueZoneCenterX();
        double bzCenterZ = vital.getBlueZoneCenterZ();
        
        // [v2.9] 디버그: 자기장 좌표 로깅 (처음 몇 번만)
        // kr.bapuri.tycoonhud.TycoonHudMod.LOGGER.info("[Minimap] Zone: center=({}, {}), radius={}, player=({}, {})",
        //     bzCenterX, bzCenterZ, blueZoneRadius, playerX, playerZ);
        
        // [v2.9] 미니맵 스케일 동적 계산:
        // 자기장이 미니맵에 적절히 표시되도록 스케일 조정
        // 자기장 반경이 미니맵의 40%를 차지하도록 계산 (여유 공간 확보)
        double effectiveRange = Math.max(MINIMAP_RANGE, blueZoneRadius * 2.5);
        double scale = (MINIMAP_SIZE / 2.0) / effectiveRange * minimapZoom;
        
        // [v2.8] 자기장 경계까지의 거리 계산
        double distToZoneCenter = Math.sqrt(
            Math.pow(bzCenterX - playerX, 2) + Math.pow(bzCenterZ - playerZ, 2));
        double distToZoneEdge = distToZoneCenter - blueZoneRadius;
        
        // [v2.9] 자기장은 항상 표시 (effectiveRange가 자기장 포함하므로)
        boolean zoneInRange = true;
        
        if (zoneInRange) {
            int bzOffsetX = (int) ((bzCenterX - playerX) * scale);
            int bzOffsetY = (int) ((bzCenterZ - playerZ) * scale);
            int bzDisplayX = cx + bzOffsetX;
            int bzDisplayY = cy + bzOffsetY;
            int zoneRadius = (int) (blueZoneRadius * scale);
            
            // [v2.9] 디버그 로깅 (처음 1번만)
            if (!textureDebugLogged) {
                textureDebugLogged = true;
                kr.bapuri.tycoonhud.TycoonHudMod.LOGGER.info(
                    "[Minimap] scale={}, zoneRadius={}, effectiveRange={}, center=({}, {}), display=({}, {})",
                    String.format("%.4f", scale), zoneRadius, String.format("%.0f", effectiveRange),
                    bzCenterX, bzCenterZ, bzDisplayX, bzDisplayY);
            }
            
            // [v2.8] 현재 자기장 - 텍스처로 렌더링
            int zoneDiameter = zoneRadius * 2;
            if (zoneDiameter > 0) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                ctx.drawTexture(ZONE_CURRENT_TEXTURE, 
                    bzDisplayX - zoneRadius, bzDisplayY - zoneRadius, 
                    zoneDiameter, zoneDiameter,
                    0, 0, ZONE_TEXTURE_SIZE, ZONE_TEXTURE_SIZE, 
                    ZONE_TEXTURE_SIZE, ZONE_TEXTURE_SIZE);
                RenderSystem.disableBlend();
            }
            
            // 자기장 중심 마커 (미니맵 범위 내에만)
            if (bzDisplayX >= x && bzDisplayX < x + MINIMAP_SIZE &&
                bzDisplayY >= y && bzDisplayY < y + MINIMAP_SIZE) {
                ctx.fill(bzDisplayX - 2, bzDisplayY, bzDisplayX + 3, bzDisplayY + 1, 0xFF00AAFF);
                ctx.fill(bzDisplayX, bzDisplayY - 2, bzDisplayX + 1, bzDisplayY + 3, 0xFF00AAFF);
            }
        }
        
        // [v2.8] 다음 자기장 원 (텍스처 기반)
        if (vital.hasNextZone()) {
            double nextRadius = vital.getBlueZoneNextRadius();
            double nextCenterX = vital.getBlueZoneNextCenterX();
            double nextCenterZ = vital.getBlueZoneNextCenterZ();
            
            double distToNextCenter = Math.sqrt(
                Math.pow(nextCenterX - playerX, 2) + Math.pow(nextCenterZ - playerZ, 2));
            double nextDisplayRange = MINIMAP_RANGE / minimapZoom + nextRadius;
            
            if (distToNextCenter < nextDisplayRange) {
                int nextZoneRadius = (int) (nextRadius * scale);
                int nextOffsetX = (int) ((nextCenterX - playerX) * scale);
                int nextOffsetY = (int) ((nextCenterZ - playerZ) * scale);
                int nextDisplayX = cx + nextOffsetX;
                int nextDisplayY = cy + nextOffsetY;
                int nextDiameter = nextZoneRadius * 2;
                
                if (nextDiameter > 0) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    ctx.drawTexture(ZONE_NEXT_TEXTURE, 
                        nextDisplayX - nextZoneRadius, nextDisplayY - nextZoneRadius, 
                        nextDiameter, nextDiameter,
                        0, 0, ZONE_TEXTURE_SIZE, ZONE_TEXTURE_SIZE, 
                        ZONE_TEXTURE_SIZE, ZONE_TEXTURE_SIZE);
                    RenderSystem.disableBlend();
                }
                
                // 다음 자기장 중심 (미니맵 범위 내에만)
                if (nextDisplayX >= x && nextDisplayX < x + MINIMAP_SIZE && 
                    nextDisplayY >= y && nextDisplayY < y + MINIMAP_SIZE) {
                    ctx.fill(nextDisplayX - 2, nextDisplayY, nextDisplayX + 3, nextDisplayY + 1, 0xDDFFFFFF);
                    ctx.fill(nextDisplayX, nextDisplayY - 2, nextDisplayX + 1, nextDisplayY + 3, 0xDDFFFFFF);
                }
            }
        }
        
        // 마커들
        MinimapData minimap = PlayerDataManager.getInstance().getMinimap();
        if (minimap != null) {
            // 팀원/적
            for (MinimapData.PlayerMarker marker : minimap.getPlayers()) {
                int offX = (int) ((marker.getX() - playerX) * scale);
                int offY = (int) ((marker.getZ() - playerZ) * scale);
                int dx = cx + offX;
                int dy = cy + offY;
                
                if (isInBounds(dx, dy, x, y)) {
                    int color = marker.isTeammate() ? MARKER_TEAM : MARKER_ENEMY;
                    ctx.fill(dx - 2, dy - 2, dx + 3, dy + 3, color);
                }
            }
            
            // 에어드랍
            for (MinimapData.AirdropMarker airdrop : minimap.getAirdrops()) {
                int offX = (int) ((airdrop.getX() - playerX) * scale);
                int offY = (int) ((airdrop.getZ() - playerZ) * scale);
                int dx = cx + offX;
                int dy = cy + offY;
                
                if (isInBounds(dx, dy, x, y)) {
                    int color = airdrop.isLooted() ? 0xFF888888 : MARKER_AIRDROP;
                    if (!airdrop.isLooted()) {
                        ctx.fill(dx - 3, dy - 3, dx + 4, dy + 4, MARKER_AIRDROP_GLOW);
                    }
                    // 다이아몬드 모양
                    ctx.fill(dx, dy - 2, dx + 1, dy - 1, color);
                    ctx.fill(dx - 1, dy - 1, dx + 2, dy, color);
                    ctx.fill(dx - 2, dy, dx + 3, dy + 1, color);
                    ctx.fill(dx - 1, dy + 1, dx + 2, dy + 2, color);
                    ctx.fill(dx, dy + 2, dx + 1, dy + 3, color);
                }
            }
        }
        
        // 플레이어 → 다음 자기장 중심 점선 연결
        if (vital.hasNextZone()) {
            double nextCenterX = vital.getBlueZoneNextCenterX();
            double nextCenterZ = vital.getBlueZoneNextCenterZ();
            int nextOffX = (int) ((nextCenterX - playerX) * scale);
            int nextOffY = (int) ((nextCenterZ - playerZ) * scale);
            
            // 점선 그리기 (플레이어 → 다음 자기장 중심)
            drawDashedLine(ctx, cx, cy, cx + nextOffX, cy + nextOffY, 0xAAFFFFFF, x, y, MINIMAP_SIZE);
        }
        
        // 내 위치 (화살표 모양 - 배그 스타일)
        renderPlayerArrowMarker(ctx, cx, cy, client.player != null ? client.player.getYaw() : 0);
        
        // 자기장 밖 경고 테두리
        if (vital.isBlueZoneDanger()) {
            float dangerPulse = (float) (0.6 + 0.4 * Math.sin(pulsePhase * 2));
            int dangerAlpha = (int) (dangerPulse * 255);
            int dangerColor = (dangerAlpha << 24) | (ZONE_DANGER & 0x00FFFFFF);
            
            // 테두리 전체
            ctx.fill(x, y, x + MINIMAP_SIZE, y + 2, dangerColor);
            ctx.fill(x, y + MINIMAP_SIZE - 2, x + MINIMAP_SIZE, y + MINIMAP_SIZE, dangerColor);
            ctx.fill(x, y, x + 2, y + MINIMAP_SIZE, dangerColor);
            ctx.fill(x + MINIMAP_SIZE - 2, y, x + MINIMAP_SIZE, y + MINIMAP_SIZE, dangerColor);
        }
        
        // 상단 액센트 라인
        ctx.fill(x, y, x + MINIMAP_SIZE, y + 1, BORDER_ACCENT);
        
        // 줌 레벨 표시 (1.0x가 아닐 때만)
        if (Math.abs(minimapZoom - 1.0f) > 0.01f) {
            TextRenderer tr = client.textRenderer;
            String zoomText = String.format("%.1fx", minimapZoom);
            int textWidth = tr.getWidth(zoomText);
            ctx.drawText(tr, zoomText, x + MINIMAP_SIZE - textWidth - 2, y + 2, 0xAAFFFFFF, false);
        }
    }
    
    private boolean isInBounds(int dx, int dy, int x, int y) {
        return dx >= x && dx < x + MINIMAP_SIZE && dy >= y && dy < y + MINIMAP_SIZE;
    }
    
    private void renderGrid(DrawContext ctx, int x, int y) {
        int gridSize = 21;  // 85px 미니맵에 맞춤
        for (int i = gridSize; i < MINIMAP_SIZE; i += gridSize) {
            ctx.fill(x + i, y, x + i + 1, y + MINIMAP_SIZE, BG_GRID);
            ctx.fill(x, y + i, x + MINIMAP_SIZE, y + i + 1, BG_GRID);
        }
        // 중앙선 (더 눈에 띄게)
        int centerX = x + MINIMAP_SIZE / 2;
        int centerY = y + MINIMAP_SIZE / 2;
        ctx.fill(centerX, y, centerX + 1, y + MINIMAP_SIZE, 0x35FFFFFF);
        ctx.fill(x, centerY, x + MINIMAP_SIZE, centerY + 1, 0x35FFFFFF);
    }
    
    // ================================================================================
    // 지형 렌더링 (고급 효과 적용)
    // ================================================================================
    
    // 높이 음영용 기준 높이 (플레이어 Y 저장)
    private int lastPlayerY = 64;
    
    /**
     * 플레이어 주변 지형을 미니맵에 렌더링
     * - 원형 클리핑
     * - 높이 음영
     * - 가장자리 페이드
     * - 물 투명도
     */
    private void renderTerrain(DrawContext ctx, int mapX, int mapY, MinecraftClient client) {
        World world = client.world;
        if (world == null || client.player == null) return;
        
        int playerX = (int) client.player.getX();
        int playerZ = (int) client.player.getZ();
        lastPlayerY = (int) client.player.getY();
        
        // 줌에 따른 블록 범위 (기본 150블록)
        int baseRange = 150;
        int range = (int) (baseRange / minimapZoom);
        
        int centerX = MINIMAP_SIZE / 2;
        int centerY = MINIMAP_SIZE / 2;
        float radius = MINIMAP_SIZE / 2.0f;
        
        // 미니맵 각 픽셀에 대해 블록 색상 결정
        for (int px = 0; px < MINIMAP_SIZE; px++) {
            for (int pz = 0; pz < MINIMAP_SIZE; pz++) {
                // 원형 클리핑 - 중심에서 거리 계산
                float dx = px - centerX;
                float dz = pz - centerY;
                float distFromCenter = (float) Math.sqrt(dx * dx + dz * dz);
                
                // 원 밖이면 스킵 (원형 미니맵)
                if (distFromCenter > radius) continue;
                
                // 미니맵 픽셀 → 월드 좌표
                double relX = (px - centerX) / (double) MINIMAP_SIZE * range * 2;
                double relZ = (pz - centerY) / (double) MINIMAP_SIZE * range * 2;
                
                int worldX = playerX + (int) relX;
                int worldZ = playerZ + (int) relZ;
                
                // 해당 위치의 표면 블록 색상 + 높이 가져오기
                TerrainInfo terrain = getTerrainInfo(world, worldX, worldZ);
                int color = terrain.color;
                
                // 높이 음영 적용
                color = applyHeightShading(color, terrain.height, lastPlayerY);
                
                // 가장자리 페이드 효과
                float fadeStart = radius * 0.75f;  // 75% 지점부터 페이드 시작
                if (distFromCenter > fadeStart) {
                    float fadeRatio = (distFromCenter - fadeStart) / (radius - fadeStart);
                    color = applyFade(color, fadeRatio);
                }
                
                // 픽셀 그리기
                ctx.fill(mapX + px, mapY + pz, mapX + px + 1, mapY + pz + 1, color);
            }
        }
        
        // 방향 표시 (N/E/S/W)
        renderDirections(ctx, client.textRenderer, mapX, mapY);
    }
    
    /**
     * 방향 표시 렌더링 (N/E/S/W)
     */
    private void renderDirections(DrawContext ctx, TextRenderer tr, int mapX, int mapY) {
        int cx = mapX + MINIMAP_SIZE / 2;
        int cy = mapY + MINIMAP_SIZE / 2;
        
        // 방향 글자 색상 (반투명 흰색)
        int color = 0xCCFFFFFF;
        
        // N (북쪽 - 위)
        ctx.drawText(tr, "N", cx - 2, mapY + 2, 0xFFFF5555, true);  // 빨간색 강조
        
        // S (남쪽 - 아래)
        ctx.drawText(tr, "S", cx - 2, mapY + MINIMAP_SIZE - 10, color, false);
        
        // E (동쪽 - 오른쪽)
        ctx.drawText(tr, "E", mapX + MINIMAP_SIZE - 8, cy - 4, color, false);
        
        // W (서쪽 - 왼쪽)
        ctx.drawText(tr, "W", mapX + 2, cy - 4, color, false);
    }
    
    /**
     * 지형 정보 (색상 + 높이)
     */
    private static class TerrainInfo {
        int color;
        int height;
        boolean isWater;
        
        TerrainInfo(int color, int height, boolean isWater) {
            this.color = color;
            this.height = height;
            this.isWater = isWater;
        }
    }
    
    /**
     * 특정 XZ 좌표의 표면 블록 정보 반환
     * - Heightmap 사용으로 Y 스캔 제거 (95% 성능 개선)
     * - 바닐라 MapColor 사용
     * - 물 감지 및 처리
     */
    private TerrainInfo getTerrainInfo(World world, int x, int z) {
        // Heightmap으로 표면 Y 좌표 즉시 획득 (Y 스캔 불필요!)
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        
        if (surfaceY < world.getBottomY()) {
            return new TerrainInfo(0xFF000000, world.getBottomY(), false);
        }
        
        BlockPos pos = new BlockPos(x, surfaceY, z);
        BlockState state = world.getBlockState(pos);
        MapColor mapColor = state.getMapColor(world, pos);
        
        // 물인 경우 - 물 색상 + 약간의 깊이 체크
        if (mapColor == MapColor.WATER_BLUE) {
            int waterY = surfaceY;
            int waterDepth = 0;
            
            // 물 깊이 간단히 체크 (최대 4번만)
            BlockPos.Mutable mutablePos = new BlockPos.Mutable(x, surfaceY, z);
            for (int i = 0; i < 4; i++) {
                mutablePos.setY(waterY - i);
                BlockState belowState = world.getBlockState(mutablePos);
                MapColor belowColor = belowState.getMapColor(world, mutablePos);
                
                if (belowColor != MapColor.WATER_BLUE) {
                    // 물 아래 지형 발견
                    int groundColor = 0xFF000000 | belowColor.color;
                    int waterColor = 0xFF000000 | mapColor.color;
                    float waterRatio = Math.min(0.6f, waterDepth * 0.2f);
                    int blendedColor = blendColors(groundColor, waterColor, waterRatio);
                    return new TerrainInfo(blendedColor, waterY - i, true);
                }
                waterDepth++;
            }
            
            // 깊은 물
            return new TerrainInfo(0xFF000000 | mapColor.color, surfaceY, true);
        }
        
        // 일반 블록
        if (mapColor == MapColor.CLEAR) {
            return new TerrainInfo(0xFF707060, surfaceY, false);  // 기본 색상
        }
        
        return new TerrainInfo(0xFF000000 | mapColor.color, surfaceY, false);
    }
    
    /**
     * 높이 음영 적용
     * 플레이어보다 높으면 밝게, 낮으면 어둡게
     */
    private int applyHeightShading(int color, int blockY, int playerY) {
        int diff = blockY - playerY;
        
        // -20 ~ +20 범위를 -0.3 ~ +0.3 밝기로 매핑
        float brightness = Math.max(-0.3f, Math.min(0.3f, diff / 60.0f));
        
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        if (brightness > 0) {
            // 밝게
            r = Math.min(255, (int) (r + (255 - r) * brightness));
            g = Math.min(255, (int) (g + (255 - g) * brightness));
            b = Math.min(255, (int) (b + (255 - b) * brightness));
        } else {
            // 어둡게
            float factor = 1.0f + brightness;  // 0.7 ~ 1.0
            r = (int) (r * factor);
            g = (int) (g * factor);
            b = (int) (b * factor);
        }
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * 가장자리 페이드 효과
     */
    private int applyFade(int color, float fadeRatio) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // 배경색(BG_DARK)과 블렌딩
        int bgR = (BG_DARK >> 16) & 0xFF;
        int bgG = (BG_DARK >> 8) & 0xFF;
        int bgB = BG_DARK & 0xFF;
        
        r = (int) (r * (1 - fadeRatio) + bgR * fadeRatio);
        g = (int) (g * (1 - fadeRatio) + bgG * fadeRatio);
        b = (int) (b * (1 - fadeRatio) + bgB * fadeRatio);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * 두 색상 블렌딩
     */
    private int blendColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 * (1 - ratio) + r2 * ratio);
        int g = (int) (g1 * (1 - ratio) + g2 * ratio);
        int b = (int) (b1 * (1 - ratio) + b2 * ratio);
        
        return (a1 << 24) | (r << 16) | (g << 8) | b;
    }
    
    // ================================================================================
    // 거리바
    // ================================================================================
    
    private void renderDistanceBar(DrawContext ctx, TextRenderer tr, int x, int y, VitalData vital, int width) {
        // 글로우
        ctx.fill(x - 2, y - 2, x + width + 2, y + DISTANCE_BAR_HEIGHT + 2, 0x2000E5FF);
        
        // 배경
        ctx.fill(x, y, x + width, y + DISTANCE_BAR_HEIGHT, 0xE0081018);
        
        // 거리 계산
        float distance = vital.getBlueZoneDistance();
        float radius = (float) vital.getBlueZoneRadius();
        if (radius <= 0) radius = 500f;
        
        float ratio = Math.min(1f, distance / radius);
        
        // 색상
        int barColor;
        if (vital.isBlueZoneDanger()) {
            barColor = DIST_DANGER;
            ratio = 1f;
        } else if (ratio < 0.4f) {
            barColor = DIST_SAFE;
        } else if (ratio < 0.7f) {
            barColor = DIST_WARN;
        } else {
            barColor = DIST_DANGER;
        }
        
        // 채움 (반전)
        int fillWidth = (int) (width * (1f - ratio));
        if (fillWidth > 0) {
            int color = (0xFF << 24) | (barColor & 0x00FFFFFF);
            ctx.fill(x, y, x + fillWidth, y + DISTANCE_BAR_HEIGHT, color);
        }
        
        // 거리 텍스트
        String distText = String.format("%.0fm", distance);
        int textWidth = tr.getWidth(distText);
        int textX = x + (width - textWidth) / 2;
        ctx.drawText(tr, distText, textX, y, 0xFFFFFFFF, true);
        
        // 상단 하이라이트
        ctx.fill(x, y, x + width, y + 1, 0x40FFFFFF);
    }
    
    // ================================================================================
    // 페이즈 패널
    // ================================================================================
    
    private void renderPhasePanel(DrawContext ctx, TextRenderer tr, int x, int y, VitalData vital, int width) {
        // 글로우
        ctx.fill(x - 2, y - 2, x + width + 2, y + DISTANCE_BAR_HEIGHT + 2, 0x2000E5FF);
        
        // 배경
        ctx.fill(x, y, x + width, y + DISTANCE_BAR_HEIGHT, 0xE0081018);
        
        // 상태
        String state = vital.getBlueZoneState();
        int color;
        String text;
        
        switch (state) {
            case "SHRINKING" -> {
                color = PHASE_SHRINK;
                text = "GO!";
            }
            case "FINAL" -> {
                color = PHASE_FINAL;
                text = "END";
            }
            default -> {
                color = PHASE_WAIT;
                int phase = vital.getBlueZonePhase();
                text = "P" + phase;
            }
        }
        
        // 상태 배경 글로우
        int glowAlpha = (int)(0x30 + 0x20 * Math.sin(pulsePhase));
        ctx.fill(x, y, x + width, y + DISTANCE_BAR_HEIGHT, (glowAlpha << 24) | (color & 0x00FFFFFF));
        
        // 텍스트
        int textWidth = tr.getWidth(text);
        int textX = x + (width - textWidth) / 2;
        ctx.drawText(tr, text, textX, y, color, true);
        
        // 상단 액센트
        ctx.fill(x, y, x + width, y + 1, color);
    }
    
    // ================================================================================
    // 도형 그리기
    // ================================================================================
    
    private void drawCircleOutlineFancy(DrawContext ctx, int cx, int cy, int radius, int color) {
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double angle1 = 2 * Math.PI * i / segments;
            double angle2 = 2 * Math.PI * (i + 1) / segments;
            
            int x1 = cx + (int) (radius * Math.cos(angle1));
            int y1 = cy + (int) (radius * Math.sin(angle1));
            int x2 = cx + (int) (radius * Math.cos(angle2));
            int y2 = cy + (int) (radius * Math.sin(angle2));
            
            // 2픽셀 두께
            ctx.fill(x1 - 1, y1 - 1, x1 + 2, y1 + 2, color);
        }
    }
    
    /**
     * [개선] 부드러운 점선 원 그리기 (다음 자기장용)
     * 
     * [개선 사항]
     * - segments=72 (매우 부드러운 원)
     * - 3:2 비율 점선 패턴
     * - Bresenham 알고리즘으로 부드러운 선
     */
    private void drawDashedCircle(DrawContext ctx, int cx, int cy, int radius, int color) {
        int segments = 72;  // 부드러운 원
        int dashOn = 3;     // 3 세그먼트 그리기
        int dashOff = 2;    // 2 세그먼트 건너뛰기
        int cycle = dashOn + dashOff;
        
        for (int i = 0; i < segments; i++) {
            // 점선 패턴 (3:2)
            if (i % cycle < dashOn) {
                double angle1 = 2 * Math.PI * i / segments;
                double angle2 = 2 * Math.PI * (i + 1) / segments;
                
                int x1 = cx + (int) (radius * Math.cos(angle1));
                int y1 = cy + (int) (radius * Math.sin(angle1));
                int x2 = cx + (int) (radius * Math.cos(angle2));
                int y2 = cy + (int) (radius * Math.sin(angle2));
                
                // 부드러운 선 (Bresenham)
                drawSmoothLine(ctx, x1, y1, x2, y2, color);
            }
        }
    }
    
    /**
     * 실선 원 그리기 (현재 자기장용) - 배틀그라운드 스타일
     */
    private void drawSolidCircle(DrawContext ctx, int cx, int cy, int radius, int color) {
        int segments = 72;
        for (int i = 0; i < segments; i++) {
            double angle1 = 2 * Math.PI * i / segments;
            double angle2 = 2 * Math.PI * (i + 1) / segments;
            
            int x1 = cx + (int) (radius * Math.cos(angle1));
            int y1 = cy + (int) (radius * Math.sin(angle1));
            int x2 = cx + (int) (radius * Math.cos(angle2));
            int y2 = cy + (int) (radius * Math.sin(angle2));
            
            drawSmoothLine(ctx, x1, y1, x2, y2, color);
        }
    }
    
    /**
     * 부드러운 선 그리기 (Bresenham 알고리즘)
     */
    private void drawSmoothLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        
        while (true) {
            ctx.fill(x1, y1, x1 + 1, y1 + 1, color);
            
            if (x1 == x2 && y1 == y2) break;
            
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }
    
    /**
     * 선 세그먼트 그리기 (두꺼운 선용)
     */
    private void drawLineSegment(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            ctx.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }
    
    /**
     * 점선 그리기 (플레이어 → 목표)
     */
    private void drawDashedLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color, 
                                 int mapX, int mapY, int mapSize) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 5) return;  // 너무 짧으면 스킵
        
        int dashLength = 4;
        int gapLength = 4;
        int totalLength = dashLength + gapLength;
        int segments = (int) (length / totalLength);
        
        for (int i = 0; i < segments; i++) {
            double startRatio = (i * totalLength) / length;
            double endRatio = (i * totalLength + dashLength) / length;
            
            int sx = x1 + (int) (dx * startRatio);
            int sy = y1 + (int) (dy * startRatio);
            int ex = x1 + (int) (dx * endRatio);
            int ey = y1 + (int) (dy * endRatio);
            
            // 맵 범위 내에서만 그리기
            if (sx >= mapX && sx < mapX + mapSize && sy >= mapY && sy < mapY + mapSize) {
                ctx.fill(sx, sy, ex + 1, ey + 1, color);
            }
        }
    }
    
    /**
     * 플레이어 화살표 마커 (배그 스타일)
     * 플레이어가 바라보는 방향을 표시
     */
    private void renderPlayerArrowMarker(DrawContext ctx, int cx, int cy, float yaw) {
        // 줌 레벨에 따른 마커 크기 조정 (축소됨)
        int baseSize = 3;
        int size = Math.max(2, (int) (baseSize * Math.min(1.0f, minimapZoom)));
        
        // 글로우
        ctx.fill(cx - size, cy - size, cx + size + 1, cy + size + 1, MARKER_SELF_GLOW);
        
        // 중심 사각형
        int innerSize = size - 2;
        ctx.fill(cx - innerSize, cy - innerSize, cx + innerSize + 1, cy + innerSize + 1, MARKER_SELF);
        
        // 방향 화살표 (yaw 기반)
        // yaw: 0 = 남쪽, 90 = 서쪽, 180/-180 = 북쪽, -90 = 동쪽
        // 마인크래프트 좌표계: +Z = 남쪽, -Z = 북쪽, +X = 동쪽, -X = 서쪽
        double rad = Math.toRadians(yaw + 180);  // 북쪽 기준으로 조정
        int arrowLength = size + 3;
        int arrowX = cx + (int) (Math.sin(rad) * arrowLength);
        int arrowY = cy - (int) (Math.cos(rad) * arrowLength);
        
        // 방향 점
        ctx.fill(arrowX - 1, arrowY - 1, arrowX + 2, arrowY + 2, 0xFFFFFFFF);
    }
    
    private void drawCircleFilled(DrawContext ctx, int cx, int cy, int radius, int color) {
        // 간단한 채우기 (가로선)
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
        }
    }
}
