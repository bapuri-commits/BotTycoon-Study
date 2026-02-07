package kr.bapuri.tycoon.job.miner;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.common.AbstractJobGradeService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MinerGradeService - 광부 등급 관리 서비스
 * 
 * Phase 4.B 구현:
 * - AbstractJobGradeService 상속
 * - 등급별 승급 조건 (레벨, BD, 총 판매액)
 * - MinerConfig 기반 조건 로드
 */
public class MinerGradeService extends AbstractJobGradeService {
    
    private final MinerConfig config;
    
    public MinerGradeService(JavaPlugin plugin,
                              PlayerDataManager dataManager,
                              EconomyService economyService,
                              MinerConfig config) {
        super(plugin, dataManager, economyService, JobType.MINER);
        this.config = config;
    }
    
    // ===== AbstractJobGradeService 구현 =====
    
    @Override
    protected long getRequiredBD(JobGrade grade) {
        MinerConfig.GradeRequirement req = config.getGradeRequirement(grade);
        if (req == null) {
            // 기본값
            return switch (grade) {
                case GRADE_1 -> 0;
                case GRADE_2 -> 10000;
                case GRADE_3 -> 50000;
                case GRADE_4 -> 200000;
            };
        }
        return req.requiredBD;
    }
    
    @Override
    protected int getRequiredLevel(JobGrade grade) {
        MinerConfig.GradeRequirement req = config.getGradeRequirement(grade);
        if (req == null) {
            return grade.getRequiredLevel(); // 기본값: enum 값 (20, 40, 80)
        }
        return req.requiredLevel;
    }
    
    @Override
    protected String checkAdditionalRequirements(Player player, PlayerTycoonData data, JobGrade targetGrade) {
        MinerConfig.GradeRequirement req = config.getGradeRequirement(targetGrade);
        if (req == null) return null;
        
        // [Phase 4.D] 총 채굴량 조건
        if (req.requiredTotalMined > 0) {
            long totalMined = data.getTotalMined();
            if (totalMined < req.requiredTotalMined) {
                return String.format("§c총 채굴량이 부족합니다. §7(현재 %,d / 필요 %,d개)",
                        totalMined, req.requiredTotalMined);
            }
        }
        
        // [Phase 4.D] 광물 판매액 조건
        if (req.requiredTotalSoldAmount > 0) {
            long minerSales = data.getTotalMinerSales();
            if (minerSales < req.requiredTotalSoldAmount) {
                return String.format("§c광물 판매액이 부족합니다. §7(현재 %,d / 필요 %,d BD)",
                        minerSales, req.requiredTotalSoldAmount);
            }
        }
        
        // 총 판매액 조건 (레거시 호환)
        if (req.requiredTotalSales > 0) {
            long totalSales = data.getTotalSales();
            if (totalSales < req.requiredTotalSales) {
                return String.format("§c총 판매액이 부족합니다. §7(현재 %,d / 필요 %,d BD)",
                        totalSales, req.requiredTotalSales);
            }
        }
        
        return null;  // 모든 조건 충족
    }
    
    @Override
    protected String getAdditionalRequirementsString(Player player, JobGrade targetGrade) {
        MinerConfig.GradeRequirement req = config.getGradeRequirement(targetGrade);
        if (req == null) return null;
        
        StringBuilder sb = new StringBuilder();
        PlayerTycoonData data = getDataSafe(player.getUniqueId());
        
        // [Phase 4.D] 총 채굴량 조건
        if (req.requiredTotalMined > 0) {
            long totalMined = data != null ? data.getTotalMined() : 0;
            String status = totalMined >= req.requiredTotalMined ? "§a✓" : "§c✗";
            sb.append(String.format("%s 총 채굴량 %,d개 이상 (현재: %,d)\n",
                    status, req.requiredTotalMined, totalMined));
        }
        
        // [Phase 4.D] 광물 판매액 조건
        if (req.requiredTotalSoldAmount > 0) {
            long minerSales = data != null ? data.getTotalMinerSales() : 0;
            String status = minerSales >= req.requiredTotalSoldAmount ? "§a✓" : "§c✗";
            sb.append(String.format("%s 광물 판매액 %,d BD 이상 (현재: %,d)\n",
                    status, req.requiredTotalSoldAmount, minerSales));
        }
        
        // 총 판매액 조건 (레거시 호환)
        if (req.requiredTotalSales > 0) {
            long totalSales = data != null ? data.getTotalSales() : 0;
            String status = totalSales >= req.requiredTotalSales ? "§a✓" : "§c✗";
            sb.append(String.format("%s 총 판매액 %,d BD 이상 (현재: %,d)\n",
                    status, req.requiredTotalSales, totalSales));
        }
        
        return sb.length() > 0 ? sb.toString() : null;
    }
    
    @Override
    protected void onPromote(Player player, JobGrade oldGrade, JobGrade newGrade) {
        // 기본 메시지 전송
        super.onPromote(player, oldGrade, newGrade);
        
        // 광부 승급 시 추가 메시지
        String bonusInfo = switch (newGrade) {
            case GRADE_2 -> "§7보너스: 채굴 속도 +5%";
            case GRADE_3 -> "§7보너스: 채굴 속도 +10%, 추가 드롭 확률 +3%";
            case GRADE_4 -> "§7보너스: 채굴 속도 +15%, 추가 드롭 확률 +5%, 산화 구리 가격 +20%";
            default -> null;
        };
        
        if (bonusInfo != null) {
            player.sendMessage(bonusInfo);
        }
    }
    
    @Override
    public void reloadConfig() {
        config.loadFromConfig();
        logger.info("[MinerGradeService] 설정 리로드 완료");
    }
    
    // ===== 유틸리티 =====
    
    /**
     * MinerConfig 접근
     */
    public MinerConfig getConfig() {
        return config;
    }
    
    /**
     * 현재 등급 이름 조회
     */
    public String getGradeDisplayName(Player player) {
        JobGrade grade = getGrade(player);
        return grade.getDisplayName();
    }
    
    /**
     * 다음 등급까지 진행률 표시
     */
    public String getProgressString(Player player) {
        JobGrade currentGrade = getGrade(player);
        JobGrade nextGrade = currentGrade.next();
        
        if (nextGrade == null) {
            return "§a최고 등급 달성!";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§6%s → %s 진행 상황\n",
                currentGrade.getDisplayName(), nextGrade.getDisplayName()));
        
        // 레벨 진행률
        int currentLevel = getLevel(player);
        int requiredLevel = nextGrade.getRequiredLevel();
        double levelProgress = Math.min(1.0, (double) currentLevel / requiredLevel);
        sb.append(String.format("§7레벨: %d/%d (%.0f%%)\n", currentLevel, requiredLevel, levelProgress * 100));
        
        // 판매액 진행률
        MinerConfig.GradeRequirement req = config.getGradeRequirement(nextGrade);
        if (req != null && req.requiredTotalSales > 0) {
            PlayerTycoonData data = getDataSafe(player.getUniqueId());
            long totalSales = data != null ? data.getTotalSales() : 0;
            double salesProgress = Math.min(1.0, (double) totalSales / req.requiredTotalSales);
            sb.append(String.format("§7판매액: %,d/%,d BD (%.0f%%)\n",
                    totalSales, req.requiredTotalSales, salesProgress * 100));
        }
        
        return sb.toString();
    }
}
