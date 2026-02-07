package kr.bapuri.tycoon.integration;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Logger;

/**
 * CoreProtect 연동 유틸리티
 * 
 * 블록 변경 이력을 조회하는 헬퍼 메서드 제공:
 * - 특정 블록의 변경 이력 조회
 * - 특정 플레이어의 활동 조회
 */
public class CoreProtectIntegration {
    
    private final Plugin plugin;
    private final Logger logger;
    private CoreProtectAPI api;
    private boolean available = false;
    
    public CoreProtectIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.logger = Logger.getLogger("TycoonLite.CoreProtect");
        
        initAPI();
    }
    
    private void initAPI() {
        try {
            Plugin cpPlugin = plugin.getServer().getPluginManager().getPlugin("CoreProtect");
            
            if (cpPlugin == null || !(cpPlugin instanceof CoreProtect)) {
                logger.info("[CoreProtect] 플러그인을 찾을 수 없음");
                return;
            }
            
            CoreProtectAPI cpApi = ((CoreProtect) cpPlugin).getAPI();
            
            if (!cpApi.isEnabled()) {
                logger.warning("[CoreProtect] API가 비활성화됨");
                return;
            }
            
            // API 버전 확인 (7 이상 권장)
            if (cpApi.APIVersion() < 9) {
                logger.warning("[CoreProtect] API 버전이 낮음: " + cpApi.APIVersion());
            }
            
            this.api = cpApi;
            this.available = true;
            logger.info("[CoreProtect] 연동 초기화 완료 (API v" + cpApi.APIVersion() + ")");
            
        } catch (Exception e) {
            logger.warning("[CoreProtect] 연동 실패: " + e.getMessage());
            available = false;
        }
    }
    
    public boolean isAvailable() {
        return available && api != null;
    }
    
    /**
     * 특정 블록의 변경 이력 조회
     * 
     * @param block 조회할 블록
     * @param seconds 조회할 시간 범위 (초)
     * @return 변경 이력 목록 (각 항목: [시간, 플레이어, x, y, z, 타입, 블록])
     */
    public List<String[]> lookupBlock(Block block, int seconds) {
        if (!available || block == null) {
            return null;
        }
        
        try {
            return api.blockLookup(block, seconds);
        } catch (Exception e) {
            logger.warning("[CoreProtect] 블록 조회 오류: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 특정 플레이어의 최근 활동 조회
     * 
     * @param playerName 플레이어 이름
     * @param seconds 조회할 시간 범위 (초)
     * @return 활동 이력 목록
     */
    public List<String[]> lookupPlayer(String playerName, int seconds) {
        if (!available || playerName == null) {
            return null;
        }
        
        try {
            // 전역 조회
            return api.performLookup(
                seconds,
                List.of(playerName),  // 플레이어 필터
                null,  // 제외 플레이어
                null,  // 블록 필터
                null,  // 제외 블록
                null,  // 액션 필터
                0,     // 반경 (0 = 무제한)
                null   // 위치 (null = 전역)
            );
        } catch (Exception e) {
            logger.warning("[CoreProtect] 플레이어 조회 오류: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 특정 위치 주변의 변경 이력 조회
     * 
     * @param location 중심 위치
     * @param radius 반경
     * @param seconds 조회할 시간 범위 (초)
     * @return 변경 이력 목록
     */
    public List<String[]> lookupRadius(Location location, int radius, int seconds) {
        if (!available || location == null) {
            return null;
        }
        
        try {
            return api.performLookup(
                seconds,
                null,  // 플레이어 필터
                null,  // 제외 플레이어
                null,  // 블록 필터
                null,  // 제외 블록
                null,  // 액션 필터
                radius,
                location
            );
        } catch (Exception e) {
            logger.warning("[CoreProtect] 반경 조회 오류: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 변경 이력 파싱 헬퍼
     * CoreProtect 결과: [시간, 사용자, x, y, z, 타입, 블록ID, 데이터, 액션, 롤백여부]
     */
    public static class LookupResult {
        public final long timestamp;
        public final String player;
        public final int x, y, z;
        public final int action;  // 0=break, 1=place
        public final String blockType;
        
        public LookupResult(String[] data) {
            this.timestamp = data.length > 0 ? Long.parseLong(data[0]) : 0;
            this.player = data.length > 1 ? data[1] : "";
            this.x = data.length > 2 ? Integer.parseInt(data[2]) : 0;
            this.y = data.length > 3 ? Integer.parseInt(data[3]) : 0;
            this.z = data.length > 4 ? Integer.parseInt(data[4]) : 0;
            this.action = data.length > 8 ? Integer.parseInt(data[8]) : 0;
            this.blockType = data.length > 6 ? data[6] : "";
        }
        
        public boolean isBreak() {
            return action == 0;
        }
        
        public boolean isPlace() {
            return action == 1;
        }
    }
}
