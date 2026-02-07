package kr.bapuri.tycoon.achievement;

import kr.bapuri.tycoon.economy.CurrencyType;
import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * AchievementService - 업적 시스템 비즈니스 로직
 * 
 * 역할:
 * - 업적 해금/진행도 관리
 * - 보상 지급 (BottCoin, 칭호)
 * 
 * 의존성:
 * - PlayerDataManager: 플레이어 데이터
 * - AchievementRegistry: 업적 정의
 * - EconomyService: 보상 지급 (setter 주입)
 * - TitleService: 칭호 부여 (Phase 5.C에서 연결)
 */
public class AchievementService {
    
    private final PlayerDataManager dataManager;
    private final AchievementRegistry registry;
    private final Logger logger;
    
    // setter 주입
    private EconomyService economyService;
    
    // Phase 5.C에서 연결할 칭호 부여 콜백
    private TitleGrantCallback titleGrantCallback;
    
    public AchievementService(PlayerDataManager dataManager, AchievementRegistry registry, Logger logger) {
        this.dataManager = dataManager;
        this.registry = registry;
        this.logger = logger;
    }
    
    public void setEconomyService(EconomyService economyService) {
        this.economyService = economyService;
    }
    
    /**
     * 칭호 부여 콜백 설정 (Phase 5.C에서 LuckPerms 연동)
     */
    public void setTitleGrantCallback(TitleGrantCallback callback) {
        this.titleGrantCallback = callback;
    }
    
    // ========== 업적 해금 ==========
    
    /**
     * 업적 해금 시도 (즉시 해금형)
     * @return true if newly unlocked
     */
    public boolean tryUnlock(Player player, String achievementId) {
        Achievement ach = registry.get(achievementId);
        if (ach == null) return false;
        
        PlayerTycoonData data = dataManager.get(player);
        
        // 이미 해금됨
        if (data.hasAchievementUnlocked(achievementId)) {
            return false;
        }
        
        // 진행형 업적이면 진행도 체크
        if (ach.isProgressive()) {
            int progress = data.getAchievementProgress(achievementId);
            if (progress < ach.getTargetValue()) {
                return false;
            }
        }
        
        // 해금!
        return unlock(player, ach);
    }
    
    /**
     * 업적 진행도 증가 및 자동 해금 체크
     */
    public void addProgress(Player player, String achievementId, int amount) {
        Achievement ach = registry.get(achievementId);
        if (ach == null) return;
        
        PlayerTycoonData data = dataManager.get(player);
        
        // 이미 해금됨
        if (data.hasAchievementUnlocked(achievementId)) {
            return;
        }
        
        // 진행도 증가
        int newProgress = data.incrementAchievementProgress(achievementId, amount);
        
        // 목표 달성 시 자동 해금
        if (ach.isProgressive() && newProgress >= ach.getTargetValue()) {
            unlock(player, ach);
        }
    }
    
    /**
     * 업적 진행도 설정 (절대값)
     */
    public void setProgress(Player player, String achievementId, int progress) {
        Achievement ach = registry.get(achievementId);
        if (ach == null) return;
        
        PlayerTycoonData data = dataManager.get(player);
        
        if (data.hasAchievementUnlocked(achievementId)) {
            return;
        }
        
        data.setAchievementProgress(achievementId, progress);
        
        // 목표 달성 시 자동 해금
        if (ach.isProgressive() && progress >= ach.getTargetValue()) {
            unlock(player, ach);
        }
    }
    
    /**
     * 실제 업적 해금 처리
     */
    private boolean unlock(Player player, Achievement ach) {
        PlayerTycoonData data = dataManager.get(player);
        
        if (!data.unlockAchievement(ach.getId())) {
            return false;
        }
        
        // BottCoin 보상
        if (economyService != null && ach.getBottCoinReward() > 0) {
            economyService.depositBottCoin(player, ach.getBottCoinReward());
        }
        
        // 칭호 보상 (Phase 5.C에서 LuckPerms 연동)
        if (ach.hasTitleReward() && titleGrantCallback != null) {
            titleGrantCallback.grantTitle(player, ach.getTitleReward());
        }
        
        // 축하 메시지
        sendUnlockMessage(player, ach);
        
        // 사운드
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        
        return true;
    }
    
    /**
     * 업적 해금 메시지
     */
    private void sendUnlockMessage(Player player, Achievement ach) {
        player.sendMessage("");
        player.sendMessage("§6§l★ 업적 달성! ★");
        player.sendMessage(ach.getType().getColoredName() + " §7» " + ach.getColoredName());
        player.sendMessage("§7" + ach.getDescription());
        
        if (ach.getBottCoinReward() > 0) {
            player.sendMessage("§d보상: " + CurrencyType.BOTTCOIN.format(ach.getBottCoinReward()));
        }
        if (ach.hasTitleReward()) {
            player.sendMessage("§e칭호 해금!");
        }
        player.sendMessage("");
    }
    
    // ========== 조회 ==========
    
    /**
     * 업적 해금 여부 확인
     */
    public boolean isUnlocked(Player player, String achievementId) {
        return dataManager.get(player).hasAchievementUnlocked(achievementId);
    }
    
    public boolean isUnlocked(UUID uuid, String achievementId) {
        return dataManager.get(uuid).hasAchievementUnlocked(achievementId);
    }
    
    /**
     * 업적 진행도 조회
     */
    public int getProgress(Player player, String achievementId) {
        return dataManager.get(player).getAchievementProgress(achievementId);
    }
    
    /**
     * 해금된 업적 목록
     */
    public Set<String> getUnlockedAchievements(Player player) {
        return dataManager.get(player).getUnlockedAchievements();
    }
    
    /**
     * 해금된 업적 수
     */
    public int getUnlockedCount(Player player) {
        return dataManager.get(player).getUnlockedAchievements().size();
    }
    
    /**
     * 전체 업적 수
     */
    public int getTotalCount() {
        return registry.getCount();
    }
    
    public AchievementRegistry getRegistry() {
        return registry;
    }
    
    // ========== 칭호 콜백 인터페이스 ==========
    
    /**
     * 칭호 부여 콜백 (Phase 5.C에서 LuckPerms 연동)
     */
    @FunctionalInterface
    public interface TitleGrantCallback {
        void grantTitle(Player player, String titleId);
    }
}
