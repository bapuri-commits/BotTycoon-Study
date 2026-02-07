package kr.bapuri.tycoon.enhance.upgrade;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * UpgradeNpcListener - 레벨 강화 NPC 리스너
 * 
 * "레벨 강화 상인" 등의 NPC를 클릭하면 레벨 강화 GUI (+0~+100)가 열립니다.
 * 
 * Phase 6 LITE: 레거시 버전 이식
 * 
 * Citizens NPC 생성 예시:
 * <pre>
 * /npc create 레벨 강화 상인
 * /npc skin &lt;스킨이름&gt;
 * </pre>
 * 
 * 기본 매칭 키워드 (config에서 커스터마이징 가능):
 * - 레벨 강화, 레벨강화
 * - 무기 강화, 무기강화
 * - 장비 강화, 장비강화
 * - upgrade npc, level upgrade
 * 
 * config.yml 설정:
 * <pre>
 * enhance:
 *   upgrade:
 *     npc:
 *       enabled: true
 *       debug: false
 *       cooldownMs: 500
 *       keywords:
 *         - 레벨 강화
 *         - 레벨강화
 *         - 무기 강화
 *         - (... 추가 키워드 ...)
 * </pre>
 */
public class UpgradeNpcListener implements Listener {

    private final JavaPlugin plugin;
    private final UpgradeGui upgradeGui;
    private final Logger logger;
    
    // 활성화 여부
    private boolean enabled = true;
    
    // 디버그 모드
    private boolean debug = false;
    
    // 스팸 클릭 방지 (플레이어 UUID → 마지막 클릭 시간)
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private long cooldownMs = 500; // 기본 500ms
    
    // 레벨 강화 NPC 매칭 키워드 (소문자로 저장)
    private List<String> upgradeNpcKeywords = new ArrayList<>();
    
    // 기본 키워드
    // [Phase 8 버그수정] NPC_INSTALLATION_GUIDE와 일치하도록 "강화", "대장장이" 추가
    private static final List<String> DEFAULT_KEYWORDS = Arrays.asList(
            "강화",           // 가장 짧은 키워드 (범용)
            "대장장이",        // NPC_INSTALLATION_GUIDE 예시
            "레벨 강화",
            "레벨강화",
            "무기 강화",
            "무기강화",
            "장비 강화",
            "장비강화",
            "upgrade npc",
            "level upgrade",
            "weapon upgrade",
            "armor upgrade",
            "blacksmith"      // 영문 대장장이
    );
    
    public UpgradeNpcListener(JavaPlugin plugin, UpgradeGui upgradeGui) {
        this.plugin = plugin;
        this.upgradeGui = upgradeGui;
        this.logger = plugin.getLogger();
        
        loadConfig();
    }
    
    /**
     * 설정 로드
     */
    public void loadConfig() {
        upgradeNpcKeywords.clear();
        
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("enhance.upgrade.npc");
        
        if (section == null) {
            // 기본 설정 사용
            enabled = true;
            debug = plugin.getConfig().getBoolean("enhance.upgrade.debug", false);
            cooldownMs = 500;
            upgradeNpcKeywords.addAll(DEFAULT_KEYWORDS);
            logger.info("[UpgradeNpc] config 없음, 기본 설정 사용 (키워드 " + upgradeNpcKeywords.size() + "개)");
            return;
        }
        
        enabled = section.getBoolean("enabled", true);
        debug = section.getBoolean("debug", false);
        cooldownMs = section.getLong("cooldownMs", 500);
        
        // 키워드 로드
        List<String> configKeywords = section.getStringList("keywords");
        if (configKeywords.isEmpty()) {
            upgradeNpcKeywords.addAll(DEFAULT_KEYWORDS);
        } else {
            for (String keyword : configKeywords) {
                if (keyword != null && !keyword.isEmpty()) {
                    upgradeNpcKeywords.add(keyword.toLowerCase());
                }
            }
        }
        
        if (enabled) {
            logger.info("[UpgradeNpc] 설정 로드 완료 - 키워드: " + upgradeNpcKeywords.size() + "개, 쿨다운: " + cooldownMs + "ms");
        } else {
            logger.info("[UpgradeNpc] 비활성화됨");
        }
    }
    
    /**
     * 설정 리로드
     */
    public void reload() {
        loadConfig();
        lastClickTime.clear();
    }
    
    /**
     * NPC 클릭 이벤트 처리
     * 
     * HIGHEST 우선순위: ShopNpcHandler(HIGH)보다 먼저 처리
     * ignoreCancelled = true: 다른 핸들러가 취소했으면 건너뜀
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!enabled) return;
        
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        
        // CustomName 확인
        String entityName = entity.getCustomName();
        if (entityName == null || entityName.isEmpty()) {
            return;
        }
        
        // 색상 코드 제거하고 소문자로 변환
        String cleanName = stripColor(entityName).toLowerCase();
        
        // 레벨 강화 NPC인지 확인
        String matchedKeyword = findMatchingKeyword(cleanName);
        if (matchedKeyword == null) {
            return;
        }
        
        // 스팸 클릭 방지
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(playerId);
        
        if (lastClick != null && (now - lastClick) < cooldownMs) {
            // 쿨다운 중 - 이벤트만 취소하고 GUI는 열지 않음
            event.setCancelled(true);
            if (debug) {
                logger.info("[UpgradeNpc] 쿨다운 중: " + player.getName() + 
                           " (남은 시간: " + (cooldownMs - (now - lastClick)) + "ms)");
            }
            return;
        }
        
        // 쿨다운 기록
        lastClickTime.put(playerId, now);
        
        // 이벤트 취소 (기본 상호작용 및 다른 핸들러 방지)
        event.setCancelled(true);
        
        // 레벨 강화 GUI 열기
        upgradeGui.openGui(player);
        
        // 로그
        if (debug) {
            logger.info("[UpgradeNpc] " + player.getName() + " → 레벨 강화 GUI (키워드: '" + matchedKeyword + "')");
        }
    }
    
    /**
     * NPC 이름에서 매칭되는 키워드 찾기
     * 더 긴 키워드(더 구체적인)가 우선 매칭됨
     * 
     * @return 매칭된 키워드, 없으면 null
     */
    private String findMatchingKeyword(String entityName) {
        String bestMatch = null;
        int bestLength = 0;
        
        for (String keyword : upgradeNpcKeywords) {
            if (entityName.contains(keyword) && keyword.length() > bestLength) {
                bestMatch = keyword;
                bestLength = keyword.length();
            }
        }
        
        return bestMatch;
    }
    
    /**
     * 색상 코드 제거 (§a, &b 등)
     */
    private String stripColor(String input) {
        if (input == null) return "";
        // § 코드 제거
        String result = input.replaceAll("§[0-9a-fk-or]", "");
        // & 코드 제거
        result = result.replaceAll("&[0-9a-fk-or]", "");
        return result.trim();
    }
    
    // ========== Getter/Setter ==========
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isDebug() {
        return debug;
    }
    
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    public long getCooldownMs() {
        return cooldownMs;
    }
    
    public void setCooldownMs(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }
    
    /**
     * 키워드 목록 조회 (읽기 전용)
     */
    public List<String> getKeywords() {
        return Collections.unmodifiableList(upgradeNpcKeywords);
    }
    
    /**
     * 키워드 추가
     */
    public void addKeyword(String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            upgradeNpcKeywords.add(keyword.toLowerCase());
        }
    }
    
    /**
     * 키워드 제거
     */
    public boolean removeKeyword(String keyword) {
        return upgradeNpcKeywords.remove(keyword.toLowerCase());
    }
    
    /**
     * 쿨다운 초기화 (테스트용)
     */
    public void clearCooldowns() {
        lastClickTime.clear();
    }
}
