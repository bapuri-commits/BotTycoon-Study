package kr.bapuri.tycoon.job.fisher;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.job.common.AbstractJobGradeService;
import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * FisherGradeService - 어부 등급 관리 서비스
 * 
 * Phase 4.D:
 * - 등급 승급 조건 검사: level + BD + totalFished + fisherSales
 * - 승급 시 보너스 표시 (실제 효과는 v1.1)
 */
public class FisherGradeService extends AbstractJobGradeService {
    
    private final FisherConfig config;
    
    public FisherGradeService(JavaPlugin plugin, PlayerDataManager dataManager, 
                              EconomyService economyService, FisherConfig config) {
        super(plugin, dataManager, economyService, JobType.FISHER);
        this.config = config;
    }
    
    @Override
    protected long getRequiredBD(JobGrade grade) {
        FisherConfig.GradeRequirement req = config.getGradeRequirement(grade);
        if (req == null) {
            // 기본값
            return switch (grade) {
                case GRADE_1 -> 0;
                case GRADE_2 -> 5000;
                case GRADE_3 -> 25000;
                case GRADE_4 -> 100000;
            };
        }
        return req.requiredBD;
    }
    
    @Override
    protected int getRequiredLevel(JobGrade grade) {
        FisherConfig.GradeRequirement req = config.getGradeRequirement(grade);
        if (req == null) {
            return grade.getRequiredLevel(); // 기본값: enum 값
        }
        return req.requiredLevel;
    }
    
    @Override
    protected String checkAdditionalRequirements(Player player, PlayerTycoonData data, JobGrade targetGrade) {
        FisherConfig.GradeRequirement req = config.getGradeRequirement(targetGrade);
        if (req == null) return null;
        
        // [Phase 4.D] 총 낚시량 조건
        if (req.requiredTotalFished > 0) {
            long totalFished = data.getTotalFished();
            if (totalFished < req.requiredTotalFished) {
                return String.format("§c총 낚시량이 부족합니다. §7(현재 %,d / 필요 %,d마리)",
                        totalFished, req.requiredTotalFished);
            }
        }
        
        // [Phase 4.D] 수산물 판매액 조건
        if (req.requiredTotalSoldAmount > 0) {
            long fisherSales = data.getTotalFisherSales();
            if (fisherSales < req.requiredTotalSoldAmount) {
                return String.format("§c수산물 판매액이 부족합니다. §7(현재 %,d / 필요 %,d BD)",
                        fisherSales, req.requiredTotalSoldAmount);
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
        FisherConfig.GradeRequirement req = config.getGradeRequirement(targetGrade);
        if (req == null) return null;
        
        StringBuilder sb = new StringBuilder();
        PlayerTycoonData data = getDataSafe(player.getUniqueId());
        
        // [Phase 4.D] 총 낚시량 조건
        if (req.requiredTotalFished > 0) {
            long totalFished = data != null ? data.getTotalFished() : 0;
            String status = totalFished >= req.requiredTotalFished ? "§a✓" : "§c✗";
            sb.append(String.format("%s 총 낚시량 %,d마리 이상 (현재: %,d)\n",
                    status, req.requiredTotalFished, totalFished));
        }
        
        // [Phase 4.D] 수산물 판매액 조건
        if (req.requiredTotalSoldAmount > 0) {
            long fisherSales = data != null ? data.getTotalFisherSales() : 0;
            String status = fisherSales >= req.requiredTotalSoldAmount ? "§a✓" : "§c✗";
            sb.append(String.format("%s 수산물 판매액 %,d BD 이상 (현재: %,d)\n",
                    status, req.requiredTotalSoldAmount, fisherSales));
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
        
        // 어부 승급 시 추가 메시지
        // TODO [v1.1]: 실제 보너스 적용
        String bonusInfo = switch (newGrade) {
            case GRADE_2 -> "§7보너스: 희귀 확률 +2%";
            case GRADE_3 -> "§7보너스: 희귀 확률 +5%, 에픽 확률 +1%";
            case GRADE_4 -> "§7보너스: 희귀 확률 +8%, 에픽 확률 +3%, 전설 확률 +0.5%";
            default -> null;
        };
        
        if (bonusInfo != null) {
            player.sendMessage(bonusInfo);
        }
    }
    
    @Override
    public void reloadConfig() {
        config.loadFromConfig();
        logger.info("[FisherGradeService] 설정 리로드 완료");
    }
    
    public FisherConfig getConfig() {
        return config;
    }
    
    /**
     * 플레이어의 등급 표시 이름 반환
     */
    public String getGradeDisplayName(Player player) {
        return getGrade(player).getDisplayName();
    }
    
    /**
     * 승급 진행률 문자열
     */
    public String getProgressString(Player player) {
        JobGrade current = getGrade(player);
        JobGrade next = current.next();
        
        if (next == null) {
            return "§a최고 등급 달성!";
        }
        
        FisherConfig.GradeRequirement req = config.getGradeRequirement(next);
        if (req == null) {
            return "§7다음 등급 조건이 설정되지 않았습니다.";
        }
        
        PlayerTycoonData data = getDataSafe(player.getUniqueId());
        if (data == null) {
            return "§c데이터 로드 실패";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§e다음 등급: %s\n", next.getDisplayName()));
        sb.append(String.format("§7레벨: %d / %d\n", getLevel(player), req.requiredLevel));
        
        if (req.requiredTotalFished > 0) {
            sb.append(String.format("§7낚시량: %,d / %,d\n", data.getTotalFished(), req.requiredTotalFished));
        }
        if (req.requiredTotalSoldAmount > 0) {
            sb.append(String.format("§7수산물 판매액: %,d / %,d BD\n", data.getTotalFisherSales(), req.requiredTotalSoldAmount));
        }
        
        return sb.toString();
    }
}
