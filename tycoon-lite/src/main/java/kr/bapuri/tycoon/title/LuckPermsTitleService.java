package kr.bapuri.tycoon.title;

import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * LuckPermsTitleService - LuckPerms 연동 칭호 시스템
 * 
 * 작동 방식:
 * 1. 업적 달성 시 칭호 해금 → PlayerTycoonData에 저장
 * 2. 칭호 장착 시 LuckPerms 그룹 부여 → TAB 플러그인이 자동 표시
 * 3. 칭호 해제 시 LuckPerms 그룹 제거
 * 
 * TAB 플러그인 설정:
 * - groups.yml에서 LuckPerms 그룹별 prefix 설정
 * - 예: title_collector 그룹에 "[수집가]" prefix
 */
public class LuckPermsTitleService {
    
    private final Plugin plugin;
    private final PlayerDataManager dataManager;
    private final TitleRegistry registry;
    private final Logger logger;
    private final boolean enabled;
    
    private LuckPerms luckPerms;
    private boolean luckPermsAvailable = false;
    
    public LuckPermsTitleService(Plugin plugin, PlayerDataManager dataManager, 
                                  TitleRegistry registry, Logger logger) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.registry = registry;
        this.logger = logger;
        this.enabled = plugin.getConfig().getBoolean("systems.titles.enabled", true);
        
        // LuckPerms API 초기화
        initLuckPerms();
    }
    
    private void initLuckPerms() {
        try {
            luckPerms = LuckPermsProvider.get();
            luckPermsAvailable = true;
            logger.info("[TitleService] LuckPerms 연동 성공");
        } catch (Exception e) {
            luckPermsAvailable = false;
            logger.warning("[TitleService] LuckPerms 연동 실패 - 칭호 시스템이 제한됩니다.");
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isLuckPermsAvailable() {
        return luckPermsAvailable;
    }
    
    // ========== 칭호 해금 ==========
    
    /**
     * 칭호 해금 (업적 달성 시 호출)
     * @return true if newly unlocked
     */
    public boolean unlockTitle(Player player, String titleId) {
        if (!enabled) return false;
        if (!registry.exists(titleId)) {
            logger.warning("[TitleService] 존재하지 않는 칭호: " + titleId);
            return false;
        }
        
        PlayerTycoonData data = dataManager.get(player);
        boolean newlyUnlocked = data.unlockTitle(titleId);
        
        if (newlyUnlocked) {
            Title title = registry.get(titleId);
            player.sendMessage("");
            player.sendMessage("§6§l★ 새 칭호 해금! ★");
            player.sendMessage("§e" + title.getColoredDisplayName());
            player.sendMessage("§7" + title.getDescription());
            player.sendMessage("§8/title equip " + titleId + " 로 장착하세요!");
            player.sendMessage("");
        }
        
        return newlyUnlocked;
    }
    
    /**
     * 칭호 해금 (UUID로, 오프라인 가능)
     */
    public boolean unlockTitle(UUID uuid, String titleId) {
        if (!enabled) return false;
        if (!registry.exists(titleId)) return false;
        
        PlayerTycoonData data = dataManager.get(uuid);
        return data.unlockTitle(titleId);
    }
    
    // ========== 칭호 장착/해제 ==========
    
    /**
     * 칭호 장착
     */
    public boolean equipTitle(Player player, String titleId) {
        if (!enabled) {
            player.sendMessage("§c칭호 시스템이 비활성화되어 있습니다.");
            return false;
        }
        
        PlayerTycoonData data = dataManager.get(player);
        
        // 해금되지 않은 칭호
        if (!data.hasTitleUnlocked(titleId)) {
            player.sendMessage("§c해금되지 않은 칭호입니다.");
            return false;
        }
        
        // 존재하지 않는 칭호
        Title title = registry.get(titleId);
        if (title == null) {
            player.sendMessage("§c존재하지 않는 칭호입니다.");
            return false;
        }
        
        // 기존 칭호 제거
        String previousTitle = data.getEquippedTitle();
        if (previousTitle != null && !previousTitle.isEmpty()) {
            removeFromLuckPermsGroup(player, previousTitle);
        }
        
        // 새 칭호 장착
        data.setEquippedTitle(titleId);
        addToLuckPermsGroup(player, titleId);
        
        player.sendMessage("§a칭호 " + title.getColoredDisplayName() + " §a장착!");
        return true;
    }
    
    /**
     * 칭호 해제
     */
    public void unequipTitle(Player player) {
        if (!enabled) {
            player.sendMessage("§c칭호 시스템이 비활성화되어 있습니다.");
            return;
        }
        
        PlayerTycoonData data = dataManager.get(player);
        String currentTitle = data.getEquippedTitle();
        
        if (currentTitle != null && !currentTitle.isEmpty()) {
            removeFromLuckPermsGroup(player, currentTitle);
        }
        
        data.setEquippedTitle(null);
        player.sendMessage("§7칭호를 해제했습니다.");
    }
    
    // ========== LuckPerms 연동 ==========
    
    /**
     * LuckPerms 그룹에 추가
     */
    private void addToLuckPermsGroup(Player player, String titleId) {
        if (!luckPermsAvailable) return;
        
        Title title = registry.get(titleId);
        if (title == null) return;
        
        String groupName = title.getLuckpermsGroup();
        
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                logger.warning("[TitleService] LuckPerms 사용자 로드 실패: " + player.getName());
                return;
            }
            
            InheritanceNode node = InheritanceNode.builder(groupName).build();
            user.data().add(node);
            
            luckPerms.getUserManager().saveUser(user);
            logger.fine("[TitleService] 그룹 추가: " + player.getName() + " -> " + groupName);
        } catch (Exception e) {
            logger.warning("[TitleService] LuckPerms 그룹 추가 실패: " + e.getMessage());
        }
    }
    
    /**
     * LuckPerms 그룹에서 제거
     */
    private void removeFromLuckPermsGroup(Player player, String titleId) {
        if (!luckPermsAvailable) return;
        
        Title title = registry.get(titleId);
        if (title == null) return;
        
        String groupName = title.getLuckpermsGroup();
        
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return;
            
            InheritanceNode node = InheritanceNode.builder(groupName).build();
            user.data().remove(node);
            
            luckPerms.getUserManager().saveUser(user);
            logger.fine("[TitleService] 그룹 제거: " + player.getName() + " <- " + groupName);
        } catch (Exception e) {
            logger.warning("[TitleService] LuckPerms 그룹 제거 실패: " + e.getMessage());
        }
    }
    
    /**
     * 플레이어 로그인 시 칭호 동기화
     */
    public void syncOnLogin(Player player) {
        if (!enabled || !luckPermsAvailable) return;
        
        PlayerTycoonData data = dataManager.get(player);
        String equippedTitle = data.getEquippedTitle();
        
        if (equippedTitle != null && !equippedTitle.isEmpty()) {
            addToLuckPermsGroup(player, equippedTitle);
        }
    }
    
    /**
     * 플레이어 로그아웃 시 칭호 그룹 제거 (선택적)
     * 참고: TAB이 LuckPerms prefix를 사용하므로 그룹 유지해도 무방
     */
    public void syncOnLogout(Player player) {
        // 로그아웃 시에는 그룹 유지 (TAB이 알아서 처리)
    }
    
    // ========== 조회 ==========
    
    public Set<String> getUnlockedTitles(Player player) {
        return dataManager.get(player).getUnlockedTitles();
    }
    
    public Title getEquippedTitle(Player player) {
        String titleId = dataManager.get(player).getEquippedTitle();
        if (titleId == null) return null;
        return registry.get(titleId);
    }
    
    public boolean hasTitleUnlocked(Player player, String titleId) {
        return dataManager.get(player).hasTitleUnlocked(titleId);
    }
    
    public TitleRegistry getRegistry() {
        return registry;
    }
}
