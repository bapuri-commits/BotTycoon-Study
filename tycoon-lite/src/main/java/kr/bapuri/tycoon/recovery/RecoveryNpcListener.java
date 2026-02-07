package kr.bapuri.tycoon.recovery;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
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
 * RecoveryNpcListener - Town 보관소 NPC 리스너
 * 
 * [Phase 8] 레거시에서 이식 및 리팩토링
 * 
 * "보관소", "Recovery" 등의 키워드가 포함된 NPC를 클릭하면
 * Recovery Storage GUI가 열립니다.
 * 
 * Citizens NPC 생성 예시:
 * <pre>
 * /npc create Town 보관소
 * /npc skin <스킨이름>
 * </pre>
 * 
 * 기본 매칭 키워드 (config에서 커스터마이징 가능):
 * - 보관소, 아이템 보관
 * - recovery, item storage
 * 
 * config.yml 설정:
 * <pre>
 * recovery:
 *   npc:
 *     enabled: true
 *     debug: false
 *     cooldownMs: 500
 *     keywords:
 *       - 보관소
 *       - 아이템 보관
 *       - recovery
 * </pre>
 */
public class RecoveryNpcListener implements Listener {

    private final JavaPlugin plugin;
    private final RecoveryGui recoveryGui;
    private final Logger logger;
    
    // 활성화 여부
    private boolean enabled = true;
    
    // 디버그 모드
    private boolean debug = false;
    
    // 스팸 클릭 방지 (플레이어 UUID → 마지막 클릭 시간)
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private long cooldownMs = 500; // 기본 500ms
    
    // Recovery NPC 매칭 키워드 (소문자로 저장)
    private List<String> recoveryNpcKeywords = new ArrayList<>();
    
    // 기본 키워드
    private static final List<String> DEFAULT_KEYWORDS = Arrays.asList(
            "보관소",
            "아이템 보관",
            "아이템보관",
            "데스체스트",
            "death chest",
            "recovery",
            "recovery storage",
            "item storage",
            "storage npc"
    );
    
    public RecoveryNpcListener(JavaPlugin plugin, RecoveryGui recoveryGui) {
        this.plugin = plugin;
        this.recoveryGui = recoveryGui;
        this.logger = plugin.getLogger();
        
        loadConfig();
    }
    
    /**
     * 설정 로드
     */
    public void loadConfig() {
        recoveryNpcKeywords.clear();
        
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("recovery.npc");
        
        if (section == null) {
            // 기본 설정 사용
            enabled = true;
            debug = plugin.getConfig().getBoolean("recovery.debug", false);
            cooldownMs = 500;
            recoveryNpcKeywords.addAll(DEFAULT_KEYWORDS);
            logger.info("[RecoveryNpc] config 없음, 기본 설정 사용 (키워드 " + recoveryNpcKeywords.size() + "개)");
            return;
        }
        
        enabled = section.getBoolean("enabled", true);
        debug = section.getBoolean("debug", false);
        cooldownMs = section.getLong("cooldownMs", 500);
        
        // 키워드 로드
        List<String> configKeywords = section.getStringList("keywords");
        if (configKeywords.isEmpty()) {
            recoveryNpcKeywords.addAll(DEFAULT_KEYWORDS);
        } else {
            for (String keyword : configKeywords) {
                if (keyword != null && !keyword.isEmpty()) {
                    recoveryNpcKeywords.add(keyword.toLowerCase());
                }
            }
        }
        
        if (enabled) {
            logger.info("[RecoveryNpc] 설정 로드 완료 - 키워드: " + recoveryNpcKeywords.size() + "개, 쿨다운: " + cooldownMs + "ms");
        } else {
            logger.info("[RecoveryNpc] 비활성화됨");
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
     * [Phase 8] Citizens NPC만 처리하도록 변경
     * - 일반 엔티티(몹, 플레이어 등)는 무시
     * - Citizens NPC 중 Recovery 키워드가 있는 것만 처리
     * 
     * HIGHEST 우선순위: ShopNpcHandler(HIGH)보다 먼저 처리
     * ignoreCancelled = true: 다른 핸들러가 취소했으면 건너뜀
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!enabled) return;
        
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        
        // [Phase 8] Citizens NPC인지 먼저 확인
        if (!isCitizensNpc(entity)) {
            return;
        }
        
        // NPC 이름 확인 (Citizens NPC는 NPC 객체에서 이름 가져오기)
        String entityName = getNpcName(entity);
        if (entityName == null || entityName.isEmpty()) {
            return;
        }
        
        // 색상 코드 제거하고 소문자로 변환
        String cleanName = stripColor(entityName).toLowerCase();
        
        // Recovery NPC인지 확인
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
                logger.info("[RecoveryNpc] 쿨다운 중: " + player.getName() + 
                           " (남은 시간: " + (cooldownMs - (now - lastClick)) + "ms)");
            }
            return;
        }
        
        // 쿨다운 기록
        lastClickTime.put(playerId, now);
        
        // 이벤트 취소 (기본 상호작용 및 다른 핸들러 방지)
        event.setCancelled(true);
        
        // [Phase 8] 권한 체크 (tycoon.recovery.use)
        if (!player.hasPermission("tycoon.recovery.use")) {
            player.sendMessage(org.bukkit.ChatColor.RED + "보관소를 사용할 권한이 없습니다.");
            return;
        }
        
        // Recovery Storage GUI 열기
        recoveryGui.open(player);
        
        // 로그
        if (debug) {
            logger.info("[RecoveryNpc] " + player.getName() + " → Recovery Storage GUI (키워드: '" + matchedKeyword + "')");
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
        
        for (String keyword : recoveryNpcKeywords) {
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
    
    /**
     * [Phase 8] Citizens NPC인지 확인
     * Citizens 플러그인이 없으면 false 반환
     */
    private boolean isCitizensNpc(Entity entity) {
        try {
            return CitizensAPI.getNPCRegistry().isNPC(entity);
        } catch (NoClassDefFoundError | Exception e) {
            // Citizens 플러그인이 없는 경우
            return false;
        }
    }
    
    /**
     * [Phase 8] Citizens NPC 이름 가져오기
     * NPC 객체에서 직접 이름을 가져옴 (CustomName보다 정확)
     */
    private String getNpcName(Entity entity) {
        try {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
            if (npc != null) {
                return npc.getName();
            }
        } catch (NoClassDefFoundError | Exception e) {
            // Citizens 플러그인이 없는 경우
        }
        // fallback: CustomName 사용
        return entity.getCustomName();
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
        return Collections.unmodifiableList(recoveryNpcKeywords);
    }
    
    /**
     * 키워드 추가
     */
    public void addKeyword(String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            recoveryNpcKeywords.add(keyword.toLowerCase());
        }
    }
    
    /**
     * 키워드 제거
     */
    public boolean removeKeyword(String keyword) {
        return recoveryNpcKeywords.remove(keyword.toLowerCase());
    }
    
    /**
     * 쿨다운 초기화 (테스트용)
     */
    public void clearCooldowns() {
        lastClickTime.clear();
    }
}
