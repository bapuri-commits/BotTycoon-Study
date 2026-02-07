package kr.bapuri.tycoon.common;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ChanneledActionManager - 채널링 액션 관리자
 * 
 * 특징:
 * - 플레이어당 하나의 채널링 액션만 허용
 * - 피격/이동 시 자동 취소
 * - BossBar로 진행률 표시
 * - 완료/취소 콜백 지원
 */
public class ChanneledActionManager {

    private final Plugin plugin;
    private final Map<UUID, ChanneledAction> activeActions = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    
    private static final double MOVE_TOLERANCE = 0.5; // 이동 허용 오차 (블록)

    public ChanneledActionManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // ========== 채널링 시작/취소 ==========

    /**
     * 채널링 액션 시작
     * 
     * @param player 플레이어
     * @param actionId 액션 식별자
     * @param displayName 표시명 (BossBar에 표시)
     * @param durationSeconds 채널링 시간 (초)
     * @param onComplete 완료 시 콜백
     * @param onCancel 취소 시 콜백 (nullable)
     * @return true if started, false if already channeling
     */
    public boolean startChanneling(Player player, String actionId, String displayName,
                                   int durationSeconds, Consumer<Player> onComplete, Consumer<Player> onCancel) {
        UUID uuid = player.getUniqueId();
        
        // 이미 채널링 중이면 실패
        if (activeActions.containsKey(uuid)) {
            player.sendMessage(ChatColor.RED + "이미 다른 액션을 진행 중입니다.");
            return false;
        }
        
        // 채널링 액션 생성
        ChanneledAction action = new ChanneledAction(player, actionId, displayName, 
                                                     durationSeconds, onComplete, onCancel);
        activeActions.put(uuid, action);
        
        // BossBar 생성
        BossBar bossBar = Bukkit.createBossBar(
            ChatColor.GOLD + displayName + " " + ChatColor.WHITE + "(" + durationSeconds + "초)",
            BarColor.YELLOW,
            BarStyle.SOLID
        );
        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);
        bossBars.put(uuid, bossBar);
        
        // 진행률 업데이트 스케줄러
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateChanneling(uuid);
        }, 20L, 20L); // 1초마다 업데이트
        
        action.setTaskId(task.getTaskId());
        
        player.sendMessage(ChatColor.GOLD + "[채널링] " + ChatColor.WHITE + displayName + 
            "을(를) 시작합니다. (" + durationSeconds + "초)");
        player.sendMessage(ChatColor.GRAY + "이동하거나 피격 시 취소됩니다.");
        
        return true;
    }

    /**
     * 채널링 취소
     * 
     * @param uuid 플레이어 UUID
     * @param reason 취소 사유
     */
    public void cancelChanneling(UUID uuid, String reason) {
        ChanneledAction action = activeActions.remove(uuid);
        if (action == null) return;
        
        action.setCancelled(true);
        
        // 스케줄러 취소
        if (action.getTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(action.getTaskId());
        }
        
        // BossBar 제거
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }
        
        // 취소 콜백
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.RED + "[취소] " + ChatColor.WHITE + 
                action.getDisplayName() + "이(가) 취소되었습니다. (" + reason + ")");
            
            if (action.getOnCancel() != null) {
                action.getOnCancel().accept(player);
            }
        }
    }

    /**
     * 특정 플레이어의 채널링 강제 취소 (외부 호출용)
     */
    public void cancelChanneling(Player player, String reason) {
        cancelChanneling(player.getUniqueId(), reason);
    }

    // ========== 업데이트 ==========

    /**
     * 채널링 진행률 업데이트 (매 초마다 호출)
     */
    private void updateChanneling(UUID uuid) {
        ChanneledAction action = activeActions.get(uuid);
        if (action == null || action.isCancelled()) return;
        
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            cancelChanneling(uuid, "접속 종료");
            return;
        }
        
        // 이동 체크
        if (action.hasMovedFrom(player.getLocation(), MOVE_TOLERANCE)) {
            cancelChanneling(uuid, "이동");
            return;
        }
        
        // 진행률 업데이트
        int remaining = action.getRemainingSeconds();
        BossBar bossBar = bossBars.get(uuid);
        if (bossBar != null) {
            double progress = (double) remaining / action.getDurationSeconds();
            bossBar.setProgress(Math.max(0, progress));
            bossBar.setTitle(ChatColor.GOLD + action.getDisplayName() + " " + 
                ChatColor.WHITE + "(" + remaining + "초)");
        }
        
        // 완료 체크
        if (remaining <= 0 || action.isExpired()) {
            completeChanneling(uuid);
        }
    }

    /**
     * 채널링 완료
     */
    private void completeChanneling(UUID uuid) {
        ChanneledAction action = activeActions.remove(uuid);
        if (action == null) return;
        
        // 스케줄러 취소
        if (action.getTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(action.getTaskId());
        }
        
        // BossBar 제거
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }
        
        // 완료 콜백
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.GREEN + "[완료] " + ChatColor.WHITE + 
                action.getDisplayName() + " 완료!");
            
            if (action.getOnComplete() != null) {
                action.getOnComplete().accept(player);
            }
        }
    }

    // ========== 조회 ==========

    /**
     * 플레이어가 채널링 중인지
     */
    public boolean isChanneling(UUID uuid) {
        return activeActions.containsKey(uuid);
    }

    /**
     * 플레이어가 채널링 중인지
     */
    public boolean isChanneling(Player player) {
        return isChanneling(player.getUniqueId());
    }

    /**
     * 특정 액션으로 채널링 중인지
     */
    public boolean isChanneling(UUID uuid, String actionId) {
        ChanneledAction action = activeActions.get(uuid);
        return action != null && action.getActionId().equals(actionId);
    }

    /**
     * 채널링 액션 가져오기
     */
    public ChanneledAction getAction(UUID uuid) {
        return activeActions.get(uuid);
    }

    // ========== 이벤트 핸들러용 ==========

    /**
     * 플레이어 피격 시 호출 (ChanneledActionListener에서 호출)
     */
    public void onPlayerDamaged(UUID uuid) {
        if (activeActions.containsKey(uuid)) {
            cancelChanneling(uuid, "피격");
        }
    }

    /**
     * 플레이어 퇴장 시 호출
     */
    public void onPlayerQuit(UUID uuid) {
        ChanneledAction action = activeActions.remove(uuid);
        if (action != null) {
            if (action.getTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(action.getTaskId());
            }
        }
        
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * 모든 채널링 정리 (서버 종료 시)
     */
    public void cleanup() {
        for (UUID uuid : activeActions.keySet()) {
            ChanneledAction action = activeActions.get(uuid);
            if (action != null && action.getTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(action.getTaskId());
            }
        }
        activeActions.clear();
        
        for (BossBar bossBar : bossBars.values()) {
            bossBar.removeAll();
        }
        bossBars.clear();
    }
}

