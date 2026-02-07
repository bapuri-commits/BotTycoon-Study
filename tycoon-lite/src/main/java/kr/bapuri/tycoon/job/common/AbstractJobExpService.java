package kr.bapuri.tycoon.job.common;

import kr.bapuri.tycoon.TycoonPlugin;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

import kr.bapuri.tycoon.job.JobGrade;

/**
 * AbstractJobExpService - 직업 경험치/레벨 서비스 추상 클래스
 * 
 * 레거시 리팩토링:
 * - 템플릿 메서드 패턴 사용
 * - 공통 로직은 구현, 직업별 로직은 추상 메서드로 위임
 * - null-safe 데이터 접근
 * - 비동기 저장 지원
 * 
 * 하위 클래스: MinerExpService, FarmerExpService, FisherExpService
 */
public abstract class AbstractJobExpService {
    
    protected final JavaPlugin plugin;
    protected final Logger logger;
    protected final PlayerDataManager dataManager;
    protected final JobType jobType;
    
    // [Phase 승급효과] 등급별 보너스 설정
    protected GradeBonusConfig gradeBonusConfig;
    
    // [Phase 승급효과] 등급 조회 함수 (순환 의존성 방지)
    protected Function<UUID, JobGrade> gradeProvider;
    
    // [Phase 8] 직업 변경 콜백 (모드 연동용) - static으로 모든 인스턴스가 공유
    private static java.util.function.Consumer<Player> jobChangeCallback;
    
    public AbstractJobExpService(JavaPlugin plugin, 
                                  PlayerDataManager dataManager, 
                                  JobType jobType) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataManager = dataManager;
        this.jobType = jobType;
    }
    
    // ================================================================================
    // [Phase 8] 직업 변경 콜백 (모드 연동)
    // ================================================================================
    
    /**
     * 직업 변경 콜백 설정 (ModDataService에서 호출)
     * static으로 모든 ExpService 인스턴스가 공유
     * 
     * @param callback Player를 받아 해당 플레이어에게 JOB_DATA 전송
     */
    public static void setJobChangeCallback(java.util.function.Consumer<Player> callback) {
        jobChangeCallback = callback;
    }
    
    /**
     * 직업 변경 알림 (콜백 호출)
     */
    protected void notifyJobChange(UUID uuid) {
        if (jobChangeCallback != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                jobChangeCallback.accept(player);
            }
        }
    }
    
    // ================================================================================
    // [Phase 승급효과] 등급별 보너스 설정
    // ================================================================================
    
    /**
     * GradeBonusConfig 설정 (TycoonPlugin에서 호출)
     */
    public void setGradeBonusConfig(GradeBonusConfig config) {
        this.gradeBonusConfig = config;
    }
    
    /**
     * 등급 조회 함수 설정 (GradeService 연동)
     * 순환 의존성 방지를 위해 Function으로 주입
     */
    public void setGradeProvider(Function<UUID, JobGrade> provider) {
        this.gradeProvider = provider;
    }
    
    /**
     * 현재 등급 조회 (gradeProvider 사용)
     */
    protected JobGrade getGrade(UUID uuid) {
        if (gradeProvider == null) return JobGrade.GRADE_1;
        return gradeProvider.apply(uuid);
    }
    
    /**
     * XP 배율 조회 (등급 기반)
     */
    protected double getXpMultiplier(UUID uuid) {
        if (gradeBonusConfig == null) return 1.0;
        JobGrade grade = getGrade(uuid);
        return gradeBonusConfig.getXpMultiplier(jobType, grade);
    }
    
    // ===== 핵심 조회 메서드 =====
    
    /**
     * 플레이어가 해당 직업을 가지고 있는지
     */
    public final boolean hasJob(UUID uuid) {
        PlayerTycoonData data = getDataSafe(uuid);
        if (data == null) return false;
        return data.hasJob(jobType);
    }
    
    public final boolean hasJob(Player player) {
        if (player == null) return false;
        return hasJob(player.getUniqueId());
    }
    
    /**
     * 현재 레벨 조회
     */
    public final int getLevel(UUID uuid) {
        PlayerTycoonData data = getDataSafe(uuid);
        if (data == null) return 1;
        return data.getJobLevel(jobType);
    }
    
    public final int getLevel(Player player) {
        if (player == null) return 1;
        return getLevel(player.getUniqueId());
    }
    
    /**
     * 현재 경험치 조회
     */
    public final long getExp(UUID uuid) {
        PlayerTycoonData data = getDataSafe(uuid);
        if (data == null) return 0;
        return data.getJobExp(jobType);
    }
    
    public final long getExp(Player player) {
        if (player == null) return 0;
        return getExp(player.getUniqueId());
    }
    
    /**
     * 다음 레벨까지 필요한 경험치
     */
    public final long getExpToNextLevel(UUID uuid) {
        int level = getLevel(uuid);
        long exp = getExp(uuid);
        return JobExpCalculator.getExpToNextLevel(level, exp);
    }
    
    /**
     * 현재 레벨 진행률 (0.0 ~ 1.0)
     */
    public final double getLevelProgress(UUID uuid) {
        int level = getLevel(uuid);
        long exp = getExp(uuid);
        return JobExpCalculator.getLevelProgress(level, exp);
    }
    
    /**
     * 최대 레벨인지 확인
     */
    public final boolean isMaxLevel(UUID uuid) {
        int level = getLevel(uuid);
        return JobExpCalculator.isMaxLevel(level, jobType);
    }
    
    // ===== 경험치 관리 =====
    
    /**
     * 경험치 추가 (레벨업 처리 포함)
     * 
     * [Phase 승급효과] 등급별 XP 배율 적용
     * 
     * @param uuid 플레이어 UUID
     * @param amount 추가할 경험치 (배율 적용 전)
     * @return 레벨업 발생 시 새 레벨, 아니면 0
     */
    public final int addExp(UUID uuid, long amount) {
        if (amount <= 0) return 0;
        
        PlayerTycoonData data = getDataSafe(uuid);
        if (data == null) return 0;
        if (!data.hasJob(jobType)) return 0;
        
        // [Phase 승급효과] 등급별 XP 배율 적용
        double xpMultiplier = getXpMultiplier(uuid);
        long actualAmount = (long) (amount * xpMultiplier);
        if (actualAmount <= 0) actualAmount = amount; // 최소 원래 값 보장
        
        int oldLevel = data.getJobLevel(jobType);
        long oldExp = data.getJobExp(jobType);
        
        // 경험치 추가 (배율 적용된 값)
        long newExp = JobExpCalculator.addExp(oldExp, actualAmount, jobType);
        data.setJobExp(jobType, newExp);
        
        // 레벨 계산
        int maxLevel = JobExpCalculator.getMaxLevel(jobType);
        int newLevel = JobExpCalculator.calculateLevel(newExp, maxLevel);
        
        if (newLevel > oldLevel) {
            data.setJobLevel(jobType, newLevel);
            data.markDirty();
            
            // 레벨업 이벤트 처리 (하위 클래스에서 오버라이드 가능)
            onLevelUp(uuid, oldLevel, newLevel);
            
            // 업적 트리거 (버그수정: 직업 레벨업 업적 연동)
            Player player = Bukkit.getPlayer(uuid);
            TycoonPlugin tycoonPlugin = TycoonPlugin.getInstance();
            if (player != null && tycoonPlugin != null && tycoonPlugin.getAchievementListener() != null) {
                tycoonPlugin.getAchievementListener().onJobLevelUp(player, jobType, newLevel);
            }
            
            // [Phase 8] 모드에 직업 변경 알림 (레벨업 시)
            notifyJobChange(uuid);
            
            return newLevel;
        }
        
        data.markDirty();
        
        // [Phase 8] 모드에 직업 변경 알림 (경험치만 변경)
        notifyJobChange(uuid);
        
        return 0;
    }
    
    public final int addExp(Player player, long amount) {
        if (player == null) return 0;
        return addExp(player.getUniqueId(), amount);
    }
    
    /**
     * 경험치 설정 (관리자용)
     */
    public final boolean setExp(UUID uuid, long exp) {
        PlayerTycoonData data = getDataSafe(uuid);
        if (data == null) return false;
        if (!data.hasJob(jobType)) return false;
        
        long clampedExp = JobExpCalculator.clampExp(exp);
        data.setJobExp(jobType, clampedExp);
        
        // 레벨도 갱신
        int maxLevel = JobExpCalculator.getMaxLevel(jobType);
        int newLevel = JobExpCalculator.calculateLevel(clampedExp, maxLevel);
        data.setJobLevel(jobType, newLevel);
        
        data.markDirty();
        return true;
    }
    
    /**
     * 레벨 설정 (관리자용)
     */
    public final boolean setLevel(UUID uuid, int level) {
        PlayerTycoonData data = getDataSafe(uuid);
        if (data == null) return false;
        if (!data.hasJob(jobType)) return false;
        
        int clampedLevel = JobExpCalculator.clampLevel(level, jobType);
        data.setJobLevel(jobType, clampedLevel);
        
        // 경험치도 해당 레벨 시작 경험치로 설정
        long levelExp = JobExpCalculator.getCumulativeExpForLevel(clampedLevel);
        data.setJobExp(jobType, levelExp);
        
        data.markDirty();
        return true;
    }
    
    // ===== 정보 표시 =====
    
    /**
     * 직업 정보 문자열 (채팅 표시용)
     */
    public final String getInfoString(Player player) {
        if (player == null) return "";
        
        if (!hasJob(player)) {
            return String.format("§7%s 직업이 없습니다.", jobType.getDisplayName());
        }
        
        int level = getLevel(player);
        long exp = getExp(player);
        long expToNext = getExpToNextLevel(player.getUniqueId());
        double progress = getLevelProgress(player.getUniqueId());
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§6%s §7Lv.%d", jobType.getDisplayName(), level));
        
        if (!isMaxLevel(player.getUniqueId())) {
            sb.append(String.format(" §7(다음 레벨까지 %,d EXP, %.1f%%)", expToNext, progress * 100));
        } else {
            sb.append(" §a(MAX)");
        }
        
        return sb.toString();
    }
    
    // ===== 유틸리티 =====
    
    /**
     * Null-safe 데이터 접근
     */
    protected final PlayerTycoonData getDataSafe(UUID uuid) {
        if (uuid == null) return null;
        return dataManager.get(uuid);
    }
    
    /**
     * 직업 타입 반환
     */
    public final JobType getJobType() {
        return jobType;
    }
    
    // ===== 하위 클래스 확장 포인트 =====
    
    /**
     * 레벨업 시 호출 (하위 클래스에서 오버라이드)
     * 
     * @param uuid 플레이어 UUID
     * @param oldLevel 이전 레벨
     * @param newLevel 새 레벨
     */
    protected void onLevelUp(UUID uuid, int oldLevel, int newLevel) {
        // 기본 구현: 로그만 출력
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(String.format("§a[%s] §6레벨 업! §7Lv.%d → Lv.%d",
                    jobType.getDisplayName(), oldLevel, newLevel));
        }
    }
    
    /**
     * 행동별 경험치 획득 (하위 클래스에서 구현)
     * 
     * @param player 플레이어
     * @param actionId 행동 ID (예: "COAL_ORE", "WHEAT")
     * @param count 수량
     * @return 획득한 경험치
     */
    public abstract long grantExpForAction(Player player, String actionId, int count);
    
    /**
     * 직업별 설정 리로드 (하위 클래스에서 구현)
     */
    public abstract void reloadConfig();
    
    // ===== [Phase 4.E] 상점 판매 XP 통합 인터페이스 =====
    
    /**
     * 상점 판매 시 경험치 부여 (통합 인터페이스)
     * 
     * <p>JobShop에서 판매 완료 후 호출하여 XP를 부여합니다.
     * 각 직업별 ExpService에서 구현하여 직업에 맞는 XP 계산을 수행합니다.</p>
     * 
     * @param player 플레이어
     * @param material 판매한 아이템 종류
     * @param count 판매 수량
     * @param saleAmount 판매 총액 (BD)
     * @return 부여된 경험치 (직업이 없거나 처리 불가 시 0)
     */
    public abstract long addSaleExpFromShop(Player player, Material material, int count, long saleAmount);
}
