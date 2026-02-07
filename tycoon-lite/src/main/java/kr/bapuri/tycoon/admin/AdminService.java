package kr.bapuri.tycoon.admin;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * AdminService - 관리자 시스템 서비스
 * 
 * 기능:
 * - 슈퍼관리자 관리 (config.yml의 admin.superAdmins)
 * - devMode 체크 (위험한 명령어 활성화 여부)
 * - 권한 계층: Player < Admin < SuperAdmin < Console
 * 
 * config.yml 구조:
 * admin:
 *   devMode: false
 *   superAdmins:
 *     CORNSFAB: "d86d7256-96ed-4805-abd8-771f6fc52886"
 */
public class AdminService {

    private final Plugin plugin;
    private final Set<UUID> superAdmins = new HashSet<>();
    private boolean devMode = false;

    public AdminService(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * 설정 리로드
     */
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        
        // devMode 로드
        this.devMode = config.getBoolean("admin.devMode", false);
        if (devMode) {
            plugin.getLogger().warning("[AdminService] ⚠️ DEV MODE 활성화됨! 프로덕션에서는 비활성화하세요.");
        }
        
        // 슈퍼관리자 로드
        loadSuperAdmins(config);
    }

    private void loadSuperAdmins(FileConfiguration config) {
        superAdmins.clear();

        // config.yml 구조: admin.superAdmins.이름: "UUID"
        ConfigurationSection adminSec = config.getConfigurationSection("admin.superAdmins");
        if (adminSec == null) {
            plugin.getLogger().warning("[AdminService] config.yml에 admin.superAdmins 섹션이 없습니다.");
            return;
        }

        for (String key : adminSec.getKeys(false)) {
            String uuidStr = adminSec.getString(key);
            if (uuidStr == null || uuidStr.isBlank()) continue;

            try {
                UUID uuid = UUID.fromString(uuidStr);
                superAdmins.add(uuid);
                plugin.getLogger().info("[AdminService] 슈퍼관리자 로드: " + key + " (" + uuid + ")");
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[AdminService] UUID 형식 오류: " + key + " = " + uuidStr);
            }
        }

        plugin.getLogger().info("[AdminService] superAdmins loaded = " + superAdmins.size());
    }

    // ========== devMode ==========
    
    /**
     * 개발 모드 활성화 여부
     * true일 때만 위험한 명령어(reset 등) 사용 가능
     */
    public boolean isDevMode() {
        return devMode;
    }

    // ========== 슈퍼관리자 ==========
    
    public boolean isSuperAdmin(UUID uuid) {
        return superAdmins.contains(uuid);
    }

    public boolean isSuperAdmin(Player player) {
        return isSuperAdmin(player.getUniqueId());
    }

    public boolean hasPrivilege(UUID uuid, AdminPrivilege priv) {
        // 지금은 슈퍼관리자면 전권
        return isSuperAdmin(uuid);
    }

    public boolean hasPrivilege(Player player, AdminPrivilege priv) {
        return hasPrivilege(player.getUniqueId(), priv);
    }
    
    // ========== 권한 계층 ==========
    
    /**
     * 콘솔인지 확인
     */
    public boolean isConsole(CommandSender sender) {
        return !(sender instanceof Player);
    }
    
    /**
     * 콘솔 전용 명령어 체크
     * @return true면 실행 가능, false면 거부됨
     */
    public boolean requireConsole(CommandSender sender, String commandName) {
        if (isConsole(sender)) {
            return true;
        }
        sender.sendMessage("§c[" + commandName + "] 이 명령어는 콘솔에서만 실행할 수 있습니다.");
        return false;
    }
    
    /**
     * devMode 전용 명령어 체크
     * @return true면 실행 가능, false면 거부됨
     */
    public boolean requireDevMode(CommandSender sender, String commandName) {
        if (devMode) {
            return true;
        }
        sender.sendMessage("§c[" + commandName + "] 이 명령어는 devMode=true에서만 사용할 수 있습니다.");
        return false;
    }
}
