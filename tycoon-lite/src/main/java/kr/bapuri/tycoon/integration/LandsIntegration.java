package kr.bapuri.tycoon.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * LandsIntegration - Lands 플러그인 연동 (LITE)
 * 
 * 기능:
 * - 플레이어 땅 정보 조회 (Area 기반)
 * - 클라이언트 모드 PLOT_UPDATE 패킷용 PlotInfo 제공
 * - 땅 소유권/권한 체크
 * 
 * Note: Lands API 7.x 기준으로 리플렉션 호출
 * - getArea(Location) 사용 (getLand가 아님)
 * - Area에서 Land 정보 획득: area.getLand()
 */
public class LandsIntegration {

    private final JavaPlugin plugin;
    private final Logger logger;
    private Object landsApi; // 리플렉션으로 접근
    private volatile boolean available = false;
    
    // 에러 로깅 제어 - 동일 에러 반복 방지
    private final AtomicBoolean errorLogged = new AtomicBoolean(false);

    public LandsIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        initialize();
    }

    /**
     * Lands API 초기화 (리플렉션)
     */
    private void initialize() {
        Plugin landsPlugin = Bukkit.getPluginManager().getPlugin("Lands");
        if (landsPlugin == null) {
            logger.info("[LandsIntegration] Lands 플러그인이 없습니다. 땅 기능 비활성화.");
            return;
        }

        try {
            // 리플렉션으로 LandsIntegration.of(plugin) 호출
            Class<?> landsIntegrationClass = Class.forName("me.angeschossen.lands.api.LandsIntegration");
            java.lang.reflect.Method ofMethod = landsIntegrationClass.getMethod("of", Plugin.class);
            landsApi = ofMethod.invoke(null, plugin);
            
            // API 메서드 존재 여부 검증 (getArea)
            landsApi.getClass().getMethod("getArea", Location.class);
            
            available = true;
            logger.info("[LandsIntegration] Lands 연동 활성화됨 (v" + landsPlugin.getDescription().getVersion() + ")");
        } catch (NoSuchMethodException e) {
            logger.warning("[LandsIntegration] Lands API 버전이 호환되지 않습니다. getArea(Location) 메서드 없음.");
        } catch (Exception e) {
            logger.warning("[LandsIntegration] Lands API 초기화 실패: " + e.getMessage());
        }
    }

    /**
     * Lands 사용 가능 여부
     */
    public boolean isAvailable() {
        return available && landsApi != null;
    }
    
    /**
     * API 오류 발생 시 비활성화 및 로깅 (한 번만)
     */
    private void disableOnError(String context, Exception e) {
        if (errorLogged.compareAndSet(false, true)) {
            logger.warning("[LandsIntegration] " + context + " 실패로 Lands 연동 비활성화: " + e.getMessage());
            logger.warning("[LandsIntegration] Lands 플러그인 버전을 확인하거나 업데이트하세요.");
            available = false;
        }
    }

    /**
     * 특정 위치의 땅 정보 조회
     * Lands API 7.x: getArea(Location) → Area → Area.getLand() → Land
     */
    public Optional<PlotInfo> getPlotAt(Location location) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        try {
            // landsApi.getArea(location) 리플렉션 호출
            java.lang.reflect.Method getAreaMethod = landsApi.getClass().getMethod("getArea", Location.class);
            Object area = getAreaMethod.invoke(landsApi, location);
            
            if (area == null) {
                // 클레임되지 않은 땅
                return Optional.empty();
            }

            // area.getLand() → Land 객체 획득
            Object land = area.getClass().getMethod("getLand").invoke(area);
            if (land == null) {
                return Optional.empty();
            }

            // Land에서 정보 추출
            String name = (String) land.getClass().getMethod("getName").invoke(land);
            UUID ownerId = (UUID) land.getClass().getMethod("getOwnerUID").invoke(land);
            
            // 소유자 이름 조회 (Bukkit OfflinePlayer 사용)
            String ownerName = getPlayerName(ownerId);
            
            // 땅 크기 (청크 수) - MemberHolder.getChunksAmount()
            long size = 0;
            try {
                Object sizeObj = land.getClass().getMethod("getChunksAmount").invoke(land);
                size = sizeObj instanceof Long ? (Long) sizeObj : ((Number) sizeObj).longValue();
            } catch (Exception ignored) {
                // getSize() fallback (deprecated)
                try {
                    Object sizeObj = land.getClass().getMethod("getSize").invoke(land);
                    size = sizeObj instanceof Long ? (Long) sizeObj : ((Number) sizeObj).longValue();
                } catch (Exception ignored2) {}
            }

            return Optional.of(new PlotInfo(name, ownerId, ownerName, size));
        } catch (Exception e) {
            disableOnError("땅 조회", e);
            return Optional.empty();
        }
    }

    /**
     * 플레이어가 해당 위치에서 빌드 권한이 있는지 확인
     */
    public boolean canBuild(Player player, Location location) {
        if (!isAvailable()) {
            return true; // Lands 없으면 기본 허용
        }

        try {
            // getArea(location) → Area
            java.lang.reflect.Method getAreaMethod = landsApi.getClass().getMethod("getArea", Location.class);
            Object area = getAreaMethod.invoke(landsApi, location);
            
            if (area == null) {
                return true; // 클레임되지 않은 땅 → 허용
            }

            // area.isTrusted(UUID) 확인
            java.lang.reflect.Method isTrustedMethod = area.getClass().getMethod("isTrusted", UUID.class);
            return (Boolean) isTrustedMethod.invoke(area, player.getUniqueId());
        } catch (Exception e) {
            disableOnError("권한 체크", e);
            return true; // 에러 시 기본 허용
        }
    }

    /**
     * 플레이어가 소유한 땅 개수
     */
    public int getOwnedLandCount(UUID playerId) {
        if (!isAvailable()) {
            return 0;
        }

        try {
            java.lang.reflect.Method getLandPlayerMethod = landsApi.getClass().getMethod("getLandPlayer", UUID.class);
            Object landPlayer = getLandPlayerMethod.invoke(landsApi, playerId);
            
            if (landPlayer == null) {
                return 0;
            }
            
            java.lang.reflect.Method getLandsMethod = landPlayer.getClass().getMethod("getLands");
            Object lands = getLandsMethod.invoke(landPlayer);
            
            if (lands instanceof java.util.Collection) {
                return ((java.util.Collection<?>) lands).size();
            }
            return 0;
        } catch (Exception e) {
            disableOnError("땅 개수 조회", e);
            return 0;
        }
    }

    /**
     * 플레이어가 현재 위치한 땅의 이름
     */
    public String getCurrentLandName(Player player) {
        Optional<PlotInfo> plot = getPlotAt(player.getLocation());
        return plot.map(PlotInfo::getName).orElse("야생");
    }

    /**
     * 플레이어가 소유한 모든 Land 목록 조회
     * (세금 시스템용)
     */
    public List<PlotInfo> getOwnedLands(UUID playerId) {
        if (!isAvailable()) {
            return Collections.emptyList();
        }

        try {
            java.lang.reflect.Method getLandPlayerMethod = landsApi.getClass().getMethod("getLandPlayer", UUID.class);
            Object landPlayer = getLandPlayerMethod.invoke(landsApi, playerId);
            
            if (landPlayer == null) {
                return Collections.emptyList();
            }
            
            java.lang.reflect.Method getLandsMethod = landPlayer.getClass().getMethod("getLands");
            Object lands = getLandsMethod.invoke(landPlayer);
            
            if (!(lands instanceof java.util.Collection)) {
                return Collections.emptyList();
            }

            List<PlotInfo> result = new ArrayList<>();
            for (Object land : (java.util.Collection<?>) lands) {
                try {
                    String name = (String) land.getClass().getMethod("getName").invoke(land);
                    UUID ownerId = (UUID) land.getClass().getMethod("getOwnerUID").invoke(land);
                    String ownerName = getPlayerName(ownerId);
                    
                    long size = 0;
                    try {
                        Object sizeObj = land.getClass().getMethod("getChunksAmount").invoke(land);
                        size = sizeObj instanceof Long ? (Long) sizeObj : ((Number) sizeObj).longValue();
                    } catch (Exception ignored) {
                        try {
                            Object sizeObj = land.getClass().getMethod("getSize").invoke(land);
                            size = sizeObj instanceof Long ? (Long) sizeObj : ((Number) sizeObj).longValue();
                        } catch (Exception ignored2) {}
                    }
                    
                    result.add(new PlotInfo(name, ownerId, ownerName, size));
                } catch (Exception ignored) {
                    // 개별 Land 처리 실패 시 스킵
                }
            }
            return result;
        } catch (Exception e) {
            disableOnError("소유 Land 조회", e);
            return Collections.emptyList();
        }
    }

    /**
     * Land 이름으로 정보 조회
     * (세금 시스템용)
     */
    public Optional<PlotInfo> getLandByName(String landName) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        try {
            // landsApi.getLand(landName) 호출
            java.lang.reflect.Method getLandMethod = landsApi.getClass().getMethod("getLand", String.class);
            Object land = getLandMethod.invoke(landsApi, landName);
            
            if (land == null) {
                return Optional.empty();
            }

            String name = (String) land.getClass().getMethod("getName").invoke(land);
            UUID ownerId = (UUID) land.getClass().getMethod("getOwnerUID").invoke(land);
            String ownerName = getPlayerName(ownerId);
            
            long size = 0;
            try {
                Object sizeObj = land.getClass().getMethod("getChunksAmount").invoke(land);
                size = sizeObj instanceof Long ? (Long) sizeObj : ((Number) sizeObj).longValue();
            } catch (Exception ignored) {
                try {
                    Object sizeObj = land.getClass().getMethod("getSize").invoke(land);
                    size = sizeObj instanceof Long ? (Long) sizeObj : ((Number) sizeObj).longValue();
                } catch (Exception ignored2) {}
            }

            return Optional.of(new PlotInfo(name, ownerId, ownerName, size));
        } catch (Exception e) {
            disableOnError("Land 이름 조회", e);
            return Optional.empty();
        }
    }

    /**
     * 플레이어의 총 청크 수 (모든 Land 합산)
     * (세금 시스템용)
     */
    public int getTotalChunks(UUID playerId) {
        List<PlotInfo> lands = getOwnedLands(playerId);
        return lands.stream()
                .mapToInt(p -> (int) p.getSize())
                .sum();
    }

    // ===== [세금 시스템] 마을 정지/해제 (Lands 네이티브 API) =====

    /**
     * 마을 정지 (Lands 네이티브 플래그 설정)
     * - BLOCK_PLACE, BLOCK_BREAK, INTERACT_GENERAL 플래그를 비활성화
     * 
     * @param landName 마을 이름
     * @return 성공 여부
     */
    public boolean freezeLand(String landName) {
        if (!isAvailable()) {
            return false;
        }

        try {
            Object land = getLandObject(landName);
            if (land == null) {
                return false;
            }

            // Lands API의 Flag 설정
            // 방법 1: land.setFlagsEnabled(false) - 모든 플래그 비활성화
            // 방법 2: 개별 플래그 설정
            
            // 먼저 setFlagsEnabled 시도 (전체 비활성화)
            try {
                java.lang.reflect.Method setFlagsEnabledMethod = 
                    land.getClass().getMethod("setFlagsEnabled", boolean.class);
                setFlagsEnabledMethod.invoke(land, false);
                logger.info("[LandsIntegration] 마을 '" + landName + "' 정지됨 (setFlagsEnabled)");
                return true;
            } catch (NoSuchMethodException ignored) {
                // setFlagsEnabled가 없으면 개별 플래그 설정 시도
            }

            // 개별 플래그 비활성화 시도 (BLOCK_PLACE, BLOCK_BREAK)
            boolean success = setLandFlag(land, "BLOCK_PLACE", false);
            success &= setLandFlag(land, "BLOCK_BREAK", false);
            success &= setLandFlag(land, "INTERACT_GENERAL", false);
            
            if (success) {
                logger.info("[LandsIntegration] 마을 '" + landName + "' 정지됨 (개별 플래그)");
            }
            return success;
        } catch (Exception e) {
            logger.warning("[LandsIntegration] 마을 정지 실패 (" + landName + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * 마을 정지 해제 (Lands 네이티브 플래그 복원)
     * 
     * @param landName 마을 이름
     * @return 성공 여부
     */
    public boolean unfreezeLand(String landName) {
        if (!isAvailable()) {
            return false;
        }

        try {
            Object land = getLandObject(landName);
            if (land == null) {
                return false;
            }

            // setFlagsEnabled(true) 시도
            try {
                java.lang.reflect.Method setFlagsEnabledMethod = 
                    land.getClass().getMethod("setFlagsEnabled", boolean.class);
                setFlagsEnabledMethod.invoke(land, true);
                logger.info("[LandsIntegration] 마을 '" + landName + "' 정지 해제됨 (setFlagsEnabled)");
                return true;
            } catch (NoSuchMethodException ignored) {
                // 개별 플래그 복원 시도
            }

            // 개별 플래그 활성화
            boolean success = setLandFlag(land, "BLOCK_PLACE", true);
            success &= setLandFlag(land, "BLOCK_BREAK", true);
            success &= setLandFlag(land, "INTERACT_GENERAL", true);
            
            if (success) {
                logger.info("[LandsIntegration] 마을 '" + landName + "' 정지 해제됨 (개별 플래그)");
            }
            return success;
        } catch (Exception e) {
            logger.warning("[LandsIntegration] 마을 정지 해제 실패 (" + landName + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * 마을이 정지 상태인지 확인 (Lands API)
     */
    public boolean isLandFrozen(String landName) {
        if (!isAvailable()) {
            return false;
        }

        try {
            Object land = getLandObject(landName);
            if (land == null) {
                return false;
            }

            // isFlagsEnabled() 시도
            try {
                java.lang.reflect.Method isFlagsEnabledMethod = 
                    land.getClass().getMethod("isFlagsEnabled");
                Boolean enabled = (Boolean) isFlagsEnabledMethod.invoke(land);
                return !enabled;
            } catch (NoSuchMethodException ignored) {
                // 개별 플래그 체크 - BLOCK_BREAK가 false면 정지 상태로 판단
            }

            return !getLandFlag(land, "BLOCK_BREAK");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Land 객체 조회 (리플렉션)
     */
    private Object getLandObject(String landName) {
        try {
            java.lang.reflect.Method getLandMethod = landsApi.getClass().getMethod("getLand", String.class);
            return getLandMethod.invoke(landsApi, landName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Land 플래그 설정 (리플렉션)
     */
    private boolean setLandFlag(Object land, String flagName, boolean value) {
        try {
            // Flags enum 또는 Flag 클래스 찾기
            Class<?> flagsClass = Class.forName("me.angeschossen.lands.api.flags.Flags");
            
            // 정적 필드에서 Flag 객체 가져오기
            java.lang.reflect.Field flagField = flagsClass.getField(flagName);
            Object flag = flagField.get(null);
            
            if (flag == null) {
                return false;
            }

            // land.setFlag(Flag, Boolean) 호출
            java.lang.reflect.Method setFlagMethod = land.getClass().getMethod("setFlag", 
                flag.getClass().getInterfaces().length > 0 ? flag.getClass().getInterfaces()[0] : flag.getClass(), 
                Boolean.class);
            setFlagMethod.invoke(land, flag, value);
            return true;
        } catch (ClassNotFoundException e) {
            // Flags 클래스가 다른 패키지에 있을 수 있음
            logger.fine("[LandsIntegration] Flags 클래스 찾기 실패: " + e.getMessage());
        } catch (NoSuchFieldException e) {
            logger.fine("[LandsIntegration] 플래그 '" + flagName + "' 없음");
        } catch (NoSuchMethodException e) {
            // setFlag 메서드 시그니처가 다를 수 있음
            return tryAlternativeSetFlag(land, flagName, value);
        } catch (Exception e) {
            logger.fine("[LandsIntegration] 플래그 설정 실패: " + e.getMessage());
        }
        return false;
    }

    /**
     * 대안 플래그 설정 방식 (Role 기반)
     */
    private boolean tryAlternativeSetFlag(Object land, String flagName, boolean value) {
        try {
            // land.getDefaultRole() → role.setFlag(flag, value)
            java.lang.reflect.Method getDefaultRoleMethod = land.getClass().getMethod("getDefaultArea");
            Object defaultArea = getDefaultRoleMethod.invoke(land);
            
            if (defaultArea != null) {
                // defaultArea에서 플래그 설정 시도
                Class<?> flagsClass = Class.forName("me.angeschossen.lands.api.flags.Flags");
                java.lang.reflect.Field flagField = flagsClass.getField(flagName);
                Object flag = flagField.get(null);
                
                // 여러 메서드 시그니처 시도
                for (java.lang.reflect.Method method : defaultArea.getClass().getMethods()) {
                    if (method.getName().equals("setFlag") && method.getParameterCount() == 2) {
                        method.invoke(defaultArea, flag, value);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("[LandsIntegration] 대안 플래그 설정 실패: " + e.getMessage());
        }
        return false;
    }

    /**
     * Land 플래그 조회 (리플렉션)
     */
    private boolean getLandFlag(Object land, String flagName) {
        try {
            Class<?> flagsClass = Class.forName("me.angeschossen.lands.api.flags.Flags");
            java.lang.reflect.Field flagField = flagsClass.getField(flagName);
            Object flag = flagField.get(null);
            
            if (flag == null) {
                return true; // 기본값
            }

            // land.hasFlag(Flag) 또는 land.getFlag(Flag)
            for (java.lang.reflect.Method method : land.getClass().getMethods()) {
                if ((method.getName().equals("hasFlag") || method.getName().equals("getFlag")) 
                    && method.getParameterCount() == 1) {
                    Object result = method.invoke(land, flag);
                    if (result instanceof Boolean) {
                        return (Boolean) result;
                    }
                }
            }
        } catch (Exception e) {
            // 무시
        }
        return true; // 기본값
    }
    
    /**
     * UUID로 플레이어 이름 조회
     */
    private String getPlayerName(UUID uuid) {
        if (uuid == null) return "Unknown";
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        return name != null ? name : "Unknown";
    }

    /**
     * 클라이언트 모드용 PlotInfo 데이터 클래스
     */
    public static class PlotInfo {
        private final String name;
        private final UUID ownerId;
        private final String ownerName;
        private final long size;

        public PlotInfo(String name, UUID ownerId, String ownerName, long size) {
            this.name = name;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.size = size;
        }

        public String getName() { return name; }
        public UUID getOwnerId() { return ownerId; }
        public String getOwnerName() { return ownerName; }
        public long getSize() { return size; }

        /**
         * 클라이언트 모드 PLOT_UPDATE 패킷용 직렬화
         */
        public String serialize() {
            return name + ";" + ownerId + ";" + ownerName + ";" + size;
        }
    }
}
