package kr.bapuri.tycoon.job.common;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * AbstractJobGradeService - 직업 등급 관리 서비스 추상 클래스
 * 
 * 레거시 리팩토링:
 * - 승급 조건 확인 및 처리
 * - BD 차감 (idempotent)
 * - 등급별 추가 조건 지원
 * 
 * 등급 시스템:
 * - Tier 1: 1차~4차 (레벨 1, 20, 40, 80)
 * - Tier 2: 1차~3차 (레벨 1, 20, 40)
 */
public abstract class AbstractJobGradeService {
    
    protected final JavaPlugin plugin;
    protected final Logger logger;
    protected final PlayerDataManager dataManager;
    protected final EconomyService economyService;
    protected final JobType jobType;
    
    // [Phase 1 동기화] 승급 콜백 (모드 연동용) - static으로 모든 인스턴스가 공유
    private static GradeUpCallback gradeUpCallback;
    
    /**
     * 승급 콜백 인터페이스
     */
    @FunctionalInterface
    public interface GradeUpCallback {
        void onGradeUp(Player player, JobType jobType, int newGrade, String gradeTitle, java.util.List<String> bonuses);
    }
    
    /**
     * 승급 콜백 설정 (TycoonPlugin에서 호출)
     */
    public static void setGradeUpCallback(GradeUpCallback callback) {
        gradeUpCallback = callback;
    }
    
    public AbstractJobGradeService(JavaPlugin plugin,
                                    PlayerDataManager dataManager,
                                    EconomyService economyService,
                                    JobType jobType) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataManager = dataManager;
        this.economyService = economyService;
        this.jobType = jobType;
    }
    
    // ===== 등급 조회 =====
    
    /**
     * 현재 등급 조회
     */
    public final JobGrade getGrade(UUID uuid) {
        PlayerTycoonData data = getDataSafe(uuid);
        if (data == null) return JobGrade.GRADE_1;
        
        int gradeValue = data.getJobGrade(jobType);
        return JobGrade.fromValue(gradeValue);
    }
    
    public final JobGrade getGrade(Player player) {
        if (player == null) return JobGrade.GRADE_1;
        return getGrade(player.getUniqueId());
    }
    
    /**
     * 특정 등급 이상인지 확인
     */
    public final boolean isGradeAtLeast(UUID uuid, JobGrade grade) {
        return getGrade(uuid).isAtLeast(grade);
    }
    
    public final boolean isGradeAtLeast(Player player, JobGrade grade) {
        if (player == null) return false;
        return isGradeAtLeast(player.getUniqueId(), grade);
    }
    
    // ===== 승급 조건 확인 =====
    
    /**
     * 승급 조건 확인 결과
     */
    public record PromotionCheckResult(
        boolean canPromote,
        JobGrade currentGrade,
        JobGrade targetGrade,
        String failureReason
    ) {
        public static PromotionCheckResult success(JobGrade current, JobGrade target) {
            return new PromotionCheckResult(true, current, target, null);
        }
        
        public static PromotionCheckResult failure(JobGrade current, JobGrade target, String reason) {
            return new PromotionCheckResult(false, current, target, reason);
        }
    }
    
    /**
     * 승급 가능 여부 확인
     */
    public final PromotionCheckResult canPromote(Player player) {
        if (player == null) {
            return PromotionCheckResult.failure(null, null, "§c플레이어가 유효하지 않습니다.");
        }
        
        PlayerTycoonData data = getDataSafe(player.getUniqueId());
        if (data == null) {
            return PromotionCheckResult.failure(null, null, "§c플레이어 데이터를 찾을 수 없습니다.");
        }
        
        if (!data.hasJob(jobType)) {
            return PromotionCheckResult.failure(null, null, 
                    String.format("§c%s 직업이 없습니다.", jobType.getDisplayName()));
        }
        
        JobGrade currentGrade = getGrade(player);
        JobGrade maxGrade = JobGrade.maxGradeFor(jobType);
        
        // 최대 등급 확인
        if (currentGrade.isAtLeast(maxGrade)) {
            return PromotionCheckResult.failure(currentGrade, null, "§c이미 최고 등급입니다.");
        }
        
        JobGrade targetGrade = currentGrade.next();
        if (targetGrade == null) {
            return PromotionCheckResult.failure(currentGrade, null, "§c다음 등급이 없습니다.");
        }
        
        // 레벨 조건 확인
        int currentLevel = data.getJobLevel(jobType);
        int requiredLevel = targetGrade.getRequiredLevel();
        
        if (currentLevel < requiredLevel) {
            return PromotionCheckResult.failure(currentGrade, targetGrade,
                    String.format("§c레벨이 부족합니다. §7(현재 Lv.%d / 필요 Lv.%d)", currentLevel, requiredLevel));
        }
        
        // BD 조건 확인
        long requiredBD = getRequiredBD(targetGrade);
        if (requiredBD > 0 && data.getMoney() < requiredBD) {
            return PromotionCheckResult.failure(currentGrade, targetGrade,
                    String.format("§cBD가 부족합니다. §7(현재 %,d / 필요 %,d BD)", data.getMoney(), requiredBD));
        }
        
        // 직업별 추가 조건 확인 (하위 클래스에서 오버라이드)
        String additionalCheck = checkAdditionalRequirements(player, data, targetGrade);
        if (additionalCheck != null) {
            return PromotionCheckResult.failure(currentGrade, targetGrade, additionalCheck);
        }
        
        return PromotionCheckResult.success(currentGrade, targetGrade);
    }
    
    // ===== 승급 실행 =====
    
    /**
     * 승급 실행 결과
     */
    public record PromotionResult(
        boolean success,
        JobGrade oldGrade,
        JobGrade newGrade,
        long paidBD,
        String message
    ) {
        public static PromotionResult success(JobGrade oldGrade, JobGrade newGrade, long paidBD) {
            return new PromotionResult(true, oldGrade, newGrade, paidBD, null);
        }
        
        public static PromotionResult failure(String reason) {
            return new PromotionResult(false, null, null, 0, reason);
        }
    }
    
    /**
     * 승급 실행
     */
    public final PromotionResult promote(Player player) {
        // 조건 확인
        PromotionCheckResult check = canPromote(player);
        if (!check.canPromote()) {
            return PromotionResult.failure(check.failureReason());
        }
        
        PlayerTycoonData data = getDataSafe(player.getUniqueId());
        JobGrade oldGrade = check.currentGrade();
        JobGrade newGrade = check.targetGrade();
        
        // BD 차감
        long requiredBD = getRequiredBD(newGrade);
        if (requiredBD > 0) {
            boolean withdrawn = economyService.withdraw(player.getUniqueId(), requiredBD);
            if (!withdrawn) {
                return PromotionResult.failure("§cBD 차감에 실패했습니다.");
            }
        }
        
        // 등급 변경
        setGrade(data, newGrade);
        data.markDirty();
        
        // 승급 이벤트 (하위 클래스에서 오버라이드 가능)
        onPromote(player, oldGrade, newGrade);
        
        return PromotionResult.success(oldGrade, newGrade, requiredBD);
    }
    
    // ===== 등급 관리 =====
    
    /**
     * 등급 설정 (관리자용)
     */
    public final boolean setGrade(UUID uuid, JobGrade grade) {
        PlayerTycoonData data = getDataSafe(uuid);
        if (data == null) return false;
        if (!data.hasJob(jobType)) return false;
        
        setGrade(data, grade);
        data.markDirty();
        return true;
    }
    
    /**
     * 내부 등급 설정
     */
    private void setGrade(PlayerTycoonData data, JobGrade grade) {
        int gradeValue = grade.getValue();
        
        // 직업별 등급 필드 설정
        switch (jobType) {
            case MINER -> data.setMinerGrade(gradeValue);
            case FARMER -> data.setFarmerGrade(gradeValue);
            case FISHER -> data.setFisherGrade(gradeValue);
            case ARTISAN -> data.setArtisanGrade(gradeValue);
            case CHEF -> data.setChefGrade(gradeValue);
            case HERBALIST -> data.setHerbalistGrade(gradeValue);
            case ENGINEER -> data.setEngineerGrade(gradeValue);
        }
    }
    
    // ===== 정보 표시 =====
    
    /**
     * 승급 조건 문자열
     */
    public final String getPromoteRequirementsString(Player player) {
        JobGrade currentGrade = getGrade(player);
        JobGrade nextGrade = currentGrade.next();
        
        if (nextGrade == null) {
            return "§a최고 등급입니다.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§6[%s → %s 승급 조건]§r\n", 
                currentGrade.getDisplayName(), nextGrade.getDisplayName()));
        
        // 레벨 조건
        int requiredLevel = nextGrade.getRequiredLevel();
        int currentLevel = getLevel(player);
        String levelStatus = currentLevel >= requiredLevel ? "§a✓" : "§c✗";
        sb.append(String.format("%s 레벨 %d 이상 (현재: %d)\n", levelStatus, requiredLevel, currentLevel));
        
        // BD 조건
        long requiredBD = getRequiredBD(nextGrade);
        if (requiredBD > 0) {
            PlayerTycoonData data = getDataSafe(player.getUniqueId());
            long currentBD = data != null ? data.getMoney() : 0;
            String bdStatus = currentBD >= requiredBD ? "§a✓" : "§c✗";
            sb.append(String.format("%s %,d BD (보유: %,d)\n", bdStatus, requiredBD, currentBD));
        }
        
        // 추가 조건 (하위 클래스에서 구현)
        String additionalRequirements = getAdditionalRequirementsString(player, nextGrade);
        if (additionalRequirements != null && !additionalRequirements.isEmpty()) {
            sb.append(additionalRequirements);
        }
        
        return sb.toString();
    }
    
    // ===== 유틸리티 =====
    
    protected final PlayerTycoonData getDataSafe(UUID uuid) {
        if (uuid == null) return null;
        return dataManager.get(uuid);
    }
    
    protected int getLevel(Player player) {
        if (player == null) return 1;
        PlayerTycoonData data = getDataSafe(player.getUniqueId());
        if (data == null) return 1;
        return data.getJobLevel(jobType);
    }
    
    public final JobType getJobType() {
        return jobType;
    }
    
    // ===== 하위 클래스 확장 포인트 =====
    
    /**
     * 등급별 필요 BD 반환 (하위 클래스에서 오버라이드)
     */
    protected long getRequiredBD(JobGrade grade) {
        // 기본값 (jobs.yml에서 로드하도록 오버라이드)
        return switch (grade) {
            case GRADE_1 -> 0;
            case GRADE_2 -> 10000;
            case GRADE_3 -> 50000;
            case GRADE_4 -> 200000;
        };
    }
    
    /**
     * 등급별 필요 레벨 반환 (하위 클래스에서 오버라이드)
     */
    protected int getRequiredLevel(JobGrade grade) {
        // 기본값: enum에 정의된 값 사용
        return grade.getRequiredLevel();
    }
    
    /**
     * [public API] 승급에 필요한 BD 반환
     * ModDataService 등 외부에서 접근 가능
     */
    public long getRequiredBDForPromotion(JobGrade targetGrade) {
        return getRequiredBD(targetGrade);
    }
    
    /**
     * [public API] 승급에 필요한 레벨 반환
     * ModDataService 등 외부에서 접근 가능
     */
    public int getRequiredLevelForPromotion(JobGrade targetGrade) {
        return getRequiredLevel(targetGrade);
    }
    
    /**
     * 추가 승급 조건 확인 (하위 클래스에서 오버라이드)
     * 
     * @return 실패 사유 (통과하면 null)
     */
    protected String checkAdditionalRequirements(Player player, PlayerTycoonData data, JobGrade targetGrade) {
        // 기본: 추가 조건 없음
        return null;
    }
    
    /**
     * 추가 승급 조건 문자열 (하위 클래스에서 오버라이드)
     */
    protected String getAdditionalRequirementsString(Player player, JobGrade targetGrade) {
        // 기본: 없음
        return null;
    }
    
    /**
     * 승급 시 호출 (하위 클래스에서 오버라이드)
     */
    protected void onPromote(Player player, JobGrade oldGrade, JobGrade newGrade) {
        // 기본 구현: 메시지 전송
        player.sendMessage(String.format("§a[%s] §6승급 완료! §7%s → %s",
                jobType.getDisplayName(), 
                oldGrade.getDisplayName(), 
                newGrade.getDisplayName()));
        
        // [Phase 1 동기화] 모드에 승급 알림 (콜백 호출)
        if (gradeUpCallback != null) {
            java.util.List<String> bonuses = getGradeBonuses(newGrade);
            gradeUpCallback.onGradeUp(player, jobType, newGrade.getValue(), 
                newGrade.getDisplayName(), bonuses);
        }
    }
    
    /**
     * 등급별 보너스 목록 반환 (하위 클래스에서 오버라이드)
     * <p>모드 UI에 표시될 보너스 문자열 목록을 반환합니다.</p>
     */
    protected java.util.List<String> getGradeBonuses(JobGrade grade) {
        // 기본 구현: 빈 목록
        return java.util.Collections.emptyList();
    }
    
    /**
     * 설정 리로드 (하위 클래스에서 구현)
     */
    public abstract void reloadConfig();
}
