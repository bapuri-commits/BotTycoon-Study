package kr.bapuri.tycoon.mod;

import kr.bapuri.tycoon.job.JobType;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Logger;

/**
 * ModEventBridge - 서버 이벤트를 모드 패킷으로 변환하는 브릿지
 * 
 * <h2>역할</h2>
 * <p>서버의 각종 이벤트(경험치 획득, 레벨업, 승급, 도감 등록 등)를 
 * 클라이언트 모드로 전송하여 실시간 동기화를 제공합니다.</p>
 * 
 * <h2>사용 방법</h2>
 * <pre>
 * // 경험치 획득 시
 * modEventBridge.onJobExpGain(player, JobType.MINER, 1500, 3600, 35);
 * 
 * // 레벨업 시
 * modEventBridge.onJobLevelUp(player, JobType.MINER, 36);
 * 
 * // 승급 시 (NPC에서)
 * modEventBridge.onJobGradeUp(player, JobType.MINER, 2, "숙련", List.of("채굴 속도 +5%"));
 * 
 * // 도감 등록 시
 * modEventBridge.onCodexRegistered(player, Material.DIAMOND, "다이아몬드", 2);
 * </pre>
 * 
 * <h2>연동 위치</h2>
 * <ul>
 *   <li>MinerExpService, FarmerExpService, FisherExpService - 경험치/레벨업</li>
 *   <li>MinerGradeService, FarmerGradeService, FisherGradeService - 승급</li>
 *   <li>CodexService - 도감 등록</li>
 * </ul>
 * 
 * @see ModDataService 패킷 전송
 * @see ModPacketTypes 패킷 타입
 */
public class ModEventBridge {
    
    private final ModDataService modDataService;
    private final Logger logger;
    
    // 활성화 여부 (ModDataService 상태에 따름)
    private boolean enabled = false;
    
    public ModEventBridge(ModDataService modDataService, Logger logger) {
        this.modDataService = modDataService;
        this.logger = logger;
    }
    
    /**
     * 브릿지 활성화 상태 설정
     * <p>ModDataService 초기화 후 호출됩니다.</p>
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            logger.info("[ModEventBridge] 활성화됨");
        }
    }
    
    public boolean isEnabled() {
        return enabled && modDataService.isEnabled();
    }
    
    // ========================================================================
    // 직업 이벤트
    // ========================================================================
    
    /**
     * 직업 경험치 획득 시 호출
     * 
     * <p>채굴/농사/낚시 등으로 경험치가 변경될 때 클라이언트에 알립니다.</p>
     * 
     * @param player 플레이어
     * @param jobType 직업 타입
     * @param currentXp 현재 레벨 내 경험치
     * @param nextLevelXp 다음 레벨까지 필요한 총 경험치
     * @param level 현재 레벨
     */
    public void onJobExpGain(Player player, JobType jobType, long currentXp, long nextLevelXp, int level) {
        if (!isEnabled() || player == null || jobType == null) return;
        
        modDataService.sendJobExpUpdate(player, jobType, currentXp, nextLevelXp, level);
        logger.fine("[ModEventBridge] 경험치 업데이트: " + player.getName() 
            + " " + jobType.name() + " Lv." + level + " (" + currentXp + "/" + nextLevelXp + ")");
    }
    
    /**
     * 직업 레벨업 시 호출
     * 
     * @param player 플레이어
     * @param jobType 직업 타입
     * @param newLevel 새 레벨
     */
    public void onJobLevelUp(Player player, JobType jobType, int newLevel) {
        if (!isEnabled() || player == null || jobType == null) return;
        
        modDataService.sendJobLevelUp(player, jobType, newLevel);
        logger.info("[ModEventBridge] 레벨업: " + player.getName() 
            + " " + jobType.name() + " -> Lv." + newLevel);
    }
    
    /**
     * 직업 승급 시 호출 (NPC에서 승급 완료 후)
     * 
     * @param player 플레이어
     * @param jobType 직업 타입
     * @param newGrade 새 등급 (1=견습, 2=숙련, 3=전문, 4=장인)
     * @param gradeTitle 등급 명칭
     * @param bonuses 새 등급에서 얻는 보너스 목록
     */
    public void onJobGradeUp(Player player, JobType jobType, int newGrade, String gradeTitle, List<String> bonuses) {
        if (!isEnabled() || player == null || jobType == null) return;
        
        modDataService.sendJobGradeUp(player, jobType, newGrade, gradeTitle, bonuses);
        logger.info("[ModEventBridge] 승급: " + player.getName() 
            + " " + jobType.name() + " -> " + gradeTitle + " (" + newGrade + "차)");
    }
    
    // ========================================================================
    // 도감 이벤트
    // ========================================================================
    
    /**
     * 도감 아이템 등록 시 호출 (서버에서 등록 완료 후)
     * 
     * <p>모드 UI가 아닌 서버 명령어/GUI로 등록했을 때도 클라이언트에 알립니다.</p>
     * 
     * @param player 플레이어
     * @param material 등록된 Material
     * @param displayName 한글 표시 이름
     * @param reward BottCoin 보상
     */
    public void onCodexRegistered(Player player, Material material, String displayName, long reward) {
        if (!isEnabled() || player == null || material == null) return;
        
        modDataService.sendCodexItemRegistered(player, material, displayName, reward);
        logger.info("[ModEventBridge] 도감 등록: " + player.getName() 
            + " - " + displayName + " (보상: " + reward + " BC)");
    }
    
    /**
     * 도감 등록 결과 전송 (모드 UI에서 등록 시도 시)
     * 
     * @param player 플레이어
     * @param success 성공 여부
     * @param material Material 이름
     * @param displayName 한글 표시 이름 (성공 시)
     * @param reward BottCoin 보상 (성공 시)
     * @param failReason 실패 사유 (실패 시)
     * @param newCollected 새 수집 수 (성공 시)
     * @param totalCount 전체 도감 수
     */
    public void onCodexRegisterResult(Player player, boolean success, String material, 
            String displayName, long reward, String failReason, int newCollected, int totalCount) {
        if (!isEnabled() || player == null) return;
        
        modDataService.sendCodexRegisterResult(player, success, material, 
            displayName, reward, failReason, newCollected, totalCount);
        
        if (success) {
            logger.fine("[ModEventBridge] 도감 등록 결과: " + player.getName() 
                + " - " + displayName + " 성공");
        } else {
            logger.fine("[ModEventBridge] 도감 등록 결과: " + player.getName() 
                + " - " + material + " 실패 (" + failReason + ")");
        }
    }
    
    // ========================================================================
    // 경제 이벤트 (기존 sendEconomyUpdate 활용)
    // ========================================================================
    
    /**
     * 경제 변동 시 호출 (BD/BC 변경)
     * 
     * <p>기존 ModDataService.sendEconomyUpdate()를 래핑합니다.</p>
     * 
     * @param player 플레이어
     */
    public void onEconomyChange(Player player) {
        if (!isEnabled() || player == null) return;
        
        modDataService.sendEconomyUpdate(player);
    }
}
