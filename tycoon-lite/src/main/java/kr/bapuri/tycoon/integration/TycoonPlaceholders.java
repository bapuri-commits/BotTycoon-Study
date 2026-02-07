package kr.bapuri.tycoon.integration;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.job.JobService;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import kr.bapuri.tycoon.title.LuckPermsTitleService;
import kr.bapuri.tycoon.title.Title;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * PlaceholderAPI 연동
 * 
 * Tycoon 데이터를 플레이스홀더로 노출:
 * 
 * 경제:
 * - %tycoon_money% / %tycoon_bd% : BD 잔액
 * - %tycoon_money_formatted% : BD 잔액 (천 단위 구분)
 * - %tycoon_bottcoin% / %tycoon_bc% : BottCoin
 * - %tycoon_bc_formatted% : BottCoin (포맷)
 * 
 * 직업:
 * - %tycoon_job% : 현재 직업명 (한글)
 * - %tycoon_job_id% : 직업 ID (영문)
 * - %tycoon_job_level% : 직업 레벨
 * - %tycoon_job_exp% : 직업 경험치
 * - %tycoon_job_grade% : 직업 등급
 * 
 * 칭호:
 * - %tycoon_title% : 현재 장착 칭호 (색상 포함)
 * - %tycoon_title_id% : 칭호 ID
 * 
 * 도감:
 * - %tycoon_codex_count% : 등록된 도감 수
 * - %tycoon_codex_total% : 전체 도감 수
 * 
 * 업적:
 * - %tycoon_achievement_count% : 해금된 업적 수
 * 
 * 기타:
 * - %tycoon_playtime% : 플레이타임 (분)
 * - %tycoon_playtime_hours% : 플레이타임 (시간)
 */
public class TycoonPlaceholders extends PlaceholderExpansion {
    
    private final Plugin plugin;
    private final EconomyService economyService;
    private final JobService jobService;
    private final PlayerDataManager playerDataManager;
    private final LuckPermsTitleService titleService;
    private final Logger logger;
    
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.KOREA);
    
    public TycoonPlaceholders(Plugin plugin, 
                              EconomyService economyService,
                              JobService jobService,
                              PlayerDataManager playerDataManager,
                              LuckPermsTitleService titleService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.jobService = jobService;
        this.playerDataManager = playerDataManager;
        this.titleService = titleService;
        this.logger = plugin.getLogger();
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "tycoon";
    }
    
    @Override
    public @NotNull String getAuthor() {
        return "bapuri";
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }
        
        Player player = offlinePlayer.getPlayer();
        
        try {
            switch (params.toLowerCase()) {
                // ========== 경제 ==========
                case "money", "bd" -> {
                    return String.valueOf(economyService.getMoney(offlinePlayer.getUniqueId()));
                }
                case "money_formatted", "bd_formatted" -> {
                    return NUMBER_FORMAT.format(economyService.getMoney(offlinePlayer.getUniqueId()));
                }
                case "bottcoin", "bc" -> {
                    return String.valueOf(economyService.getBottCoin(offlinePlayer.getUniqueId()));
                }
                case "bottcoin_formatted", "bc_formatted" -> {
                    return NUMBER_FORMAT.format(economyService.getBottCoin(offlinePlayer.getUniqueId()));
                }
                
                // ========== 직업 ==========
                case "job", "job_name" -> {
                    if (player != null) {
                        JobType jobType = jobService.getTier1Job(player);
                        return jobType != null ? jobType.getDisplayName() : "없음";
                    }
                    return "없음";
                }
                case "job_id" -> {
                    if (player != null) {
                        JobType jobType = jobService.getTier1Job(player);
                        return jobType != null ? jobType.name() : "NONE";
                    }
                    return "NONE";
                }
                case "job_level" -> {
                    if (player != null) {
                        JobType jobType = jobService.getTier1Job(player);
                        if (jobType != null) {
                            return String.valueOf(jobService.getLevel(player, jobType));
                        }
                    }
                    return "0";
                }
                case "job_exp" -> {
                    if (player != null) {
                        JobType jobType = jobService.getTier1Job(player);
                        if (jobType != null) {
                            PlayerTycoonData data = playerDataManager.get(player);
                            return String.valueOf(data.getJobExp(jobType));
                        }
                    }
                    return "0";
                }
                case "job_grade" -> {
                    if (player != null) {
                        JobType jobType = jobService.getTier1Job(player);
                        if (jobType != null) {
                            PlayerTycoonData data = playerDataManager.get(player);
                            return String.valueOf(data.getJobGrade(jobType));
                        }
                    }
                    return "0";
                }
                
                // ========== 칭호 ==========
                case "title", "title_name" -> {
                    if (player != null && titleService != null) {
                        Title title = titleService.getEquippedTitle(player);
                        return title != null ? title.getPrefix() : "";
                    }
                    return "";
                }
                case "title_id" -> {
                    if (player != null) {
                        PlayerTycoonData data = playerDataManager.get(player);
                        String titleId = data.getEquippedTitle();
                        return titleId != null ? titleId : "";
                    }
                    return "";
                }
                
                // ========== 도감 ==========
                case "codex_count" -> {
                    if (player != null) {
                        PlayerTycoonData data = playerDataManager.get(player);
                        Set<String> unlocked = data.getUnlockedCodex();
                        return String.valueOf(unlocked != null ? unlocked.size() : 0);
                    }
                    return "0";
                }
                
                // ========== 업적 ==========
                case "achievement_count" -> {
                    if (player != null) {
                        PlayerTycoonData data = playerDataManager.get(player);
                        Set<String> unlocked = data.getUnlockedAchievements();
                        return String.valueOf(unlocked != null ? unlocked.size() : 0);
                    }
                    return "0";
                }
                
                // ========== 기타 ==========
                case "playtime" -> {
                    if (player != null) {
                        int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                        return String.valueOf(ticks / 20 / 60);
                    }
                    return "0";
                }
                case "playtime_hours" -> {
                    if (player != null) {
                        int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                        return String.valueOf(ticks / 20 / 3600);
                    }
                    return "0";
                }
                
                default -> {
                    return null;
                }
            }
        } catch (Exception e) {
            logger.warning("[Placeholder] 처리 오류 (" + params + "): " + e.getMessage());
            return "";
        }
    }
    
    /**
     * PlaceholderAPI에 등록
     */
    public boolean tryRegister() {
        try {
            if (this.register()) {
                logger.info("[PlaceholderAPI] Tycoon 플레이스홀더 등록 완료");
                return true;
            }
        } catch (Exception e) {
            logger.warning("[PlaceholderAPI] 등록 실패: " + e.getMessage());
        }
        return false;
    }
}
