package kr.bapuri.tycoon.tax;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * VipService - LuckPerms VIP 그룹 확인 서비스
 * 
 * [v2] 세금 할인 적용을 위한 VIP 그룹 확인
 * 
 * 기능:
 * - LuckPerms API를 통한 VIP 그룹 멤버십 확인
 * - 온라인/오프라인 플레이어 모두 지원
 * - LuckPerms 미설치 시 안전한 폴백 (항상 false)
 */
public class VipService {

    private final Logger logger;
    private final TaxConfig config;
    private LuckPerms luckPerms;
    private boolean luckPermsAvailable;

    public VipService(JavaPlugin plugin, TaxConfig config) {
        this.logger = plugin.getLogger();
        this.config = config;
        initLuckPerms();
    }

    /**
     * LuckPerms API 초기화
     */
    private void initLuckPerms() {
        try {
            luckPerms = LuckPermsProvider.get();
            luckPermsAvailable = true;
            logger.info("[VipService] LuckPerms 연동 성공");
        } catch (IllegalStateException e) {
            luckPerms = null;
            luckPermsAvailable = false;
            logger.warning("[VipService] LuckPerms 연동 실패 - VIP 할인이 비활성화됩니다.");
        }
    }

    /**
     * LuckPerms 사용 가능 여부
     */
    public boolean isLuckPermsAvailable() {
        return luckPermsAvailable;
    }

    /**
     * 플레이어가 VIP 그룹에 속해 있는지 확인
     * 
     * @param playerId 플레이어 UUID
     * @return VIP 여부 (LuckPerms 미설치 시 false)
     */
    public boolean isVip(UUID playerId) {
        if (!luckPermsAvailable || luckPerms == null) {
            return false;
        }

        if (playerId == null) {
            return false;
        }

        String vipGroup = config.getVipLuckpermsGroup();
        if (vipGroup == null || vipGroup.isEmpty()) {
            return false;
        }

        try {
            // 온라인 플레이어 확인 (캐시됨, 빠름)
            Player onlinePlayer = Bukkit.getPlayer(playerId);
            if (onlinePlayer != null) {
                return isPlayerInGroup(onlinePlayer, vipGroup);
            }

            // 오프라인 플레이어 확인 (느릴 수 있음)
            User user = luckPerms.getUserManager().getUser(playerId);
            if (user == null) {
                // 캐시에 없으면 로드 시도 (비동기 권장이지만, 세금 계산 시에는 동기 필요)
                user = luckPerms.getUserManager().loadUser(playerId).join();
            }

            if (user == null) {
                return false;
            }

            return isUserInGroup(user, vipGroup);
        } catch (Exception e) {
            logger.warning("[VipService] VIP 확인 중 오류 (player=" + playerId + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * 온라인 플레이어가 VIP인지 확인 (빠름)
     */
    public boolean isVip(Player player) {
        if (player == null) {
            return false;
        }
        return isVip(player.getUniqueId());
    }

    /**
     * 온라인 플레이어의 그룹 확인
     */
    private boolean isPlayerInGroup(Player player, String groupName) {
        if (!luckPermsAvailable || luckPerms == null) {
            return false;
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return false;
        }

        return isUserInGroup(user, groupName);
    }

    /**
     * User 객체의 그룹 확인
     * 상속된 그룹도 확인 (vip 그룹을 상속받은 경우도 VIP로 인정)
     */
    private boolean isUserInGroup(User user, String groupName) {
        if (user == null || groupName == null) {
            return false;
        }

        // 직접 그룹 확인
        String primaryGroup = user.getPrimaryGroup();
        if (groupName.equalsIgnoreCase(primaryGroup)) {
            return true;
        }

        // 상속된 그룹 확인
        return user.getInheritedGroups(user.getQueryOptions())
                   .stream()
                   .anyMatch(g -> g.getName().equalsIgnoreCase(groupName));
    }

    /**
     * VIP 할인이 적용 가능한지 확인
     * (LuckPerms 연동 + 할인율 > 0)
     */
    public boolean isVipDiscountEnabled() {
        return luckPermsAvailable && config.getVipDiscountPercent() > 0;
    }

    /**
     * 설정 리로드 (TaxConfig 리로드 후 호출)
     */
    public void reload() {
        // LuckPerms API는 다시 초기화할 필요 없음
        // config는 외부에서 리로드됨
        logger.info("[VipService] 설정 리로드 완료 (vipGroup=" + config.getVipLuckpermsGroup() + 
                   ", discount=" + config.getVipDiscountPercent() + "%)");
    }
}
