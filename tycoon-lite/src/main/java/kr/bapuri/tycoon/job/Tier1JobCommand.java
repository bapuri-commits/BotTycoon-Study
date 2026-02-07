package kr.bapuri.tycoon.job;

import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier1JobCommand - Tier 1 직업 관리 명령어
 * 
 * 명령어:
 * - /job                     : 직업 정보 표시
 * - /job info                : 현재 직업 상세 정보
 * - /job select <직업>       : 직업 선택
 * - /job promote             : 승급 시도
 * - /job requirements        : 승급 조건 확인
 * 
 * 관리자 명령어:
 * - /job admin give <player> <job>      : 직업 부여
 * - /job admin remove <player> <job>    : 직업 제거
 * - /job admin setlevel <player> <job> <level>  : 레벨 설정
 * - /job admin setexp <player> <job> <exp>      : 경험치 설정
 */
public class Tier1JobCommand implements CommandExecutor, TabCompleter {
    
    private final JobService jobService;
    private final PlayerDataManager dataManager;
    
    public Tier1JobCommand(JavaPlugin plugin, JobService jobService, PlayerDataManager dataManager) {
        this.jobService = jobService;
        this.dataManager = dataManager;
    }
    
    private static final String USE_PERMISSION = "tycoon.job.use";
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 일반 플레이어 권한 체크 (관리자 명령어는 별도 체크)
        if (!sender.hasPermission(USE_PERMISSION) && !sender.hasPermission("tycoon.admin.job")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length == 0) {
            return handleInfo(sender);
        }
        
        String subCommand = args[0].toLowerCase();
        
        return switch (subCommand) {
            case "info" -> handleInfo(sender);
            case "select" -> handleSelect(sender, args);
            case "promote" -> handlePromote(sender, args);
            case "requirements", "req" -> handleRequirements(sender, args);
            case "admin" -> handleAdmin(sender, args);
            // Phase 4.B: 직업별 서브커맨드
            case "miner" -> handleJobSpecific(sender, args, JobType.MINER);
            case "farmer" -> handleJobSpecific(sender, args, JobType.FARMER);
            case "fisher" -> handleJobSpecific(sender, args, JobType.FISHER);
            default -> {
                sender.sendMessage("§c알 수 없는 명령어입니다. /job 으로 도움말을 확인하세요.");
                yield true;
            }
        };
    }
    
    /**
     * 직업 정보 표시
     */
    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return true;
        }
        
        String summary = jobService.getJobSummary(player);
        player.sendMessage(summary);
        return true;
    }
    
    /**
     * 직업 선택
     */
    private boolean handleSelect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return true;
        }
        
        if (args.length < 2) {
            player.sendMessage("§c사용법: /job select <직업>");
            player.sendMessage("§7사용 가능한 직업: miner, farmer, fisher");
            return true;
        }
        
        String jobId = args[1].toLowerCase();
        JobType jobType = JobType.fromId(jobId);
        
        if (jobType == null) {
            player.sendMessage("§c존재하지 않는 직업입니다: " + jobId);
            return true;
        }
        
        if (!jobType.isTier1()) {
            player.sendMessage("§c해당 직업은 Tier 1이 아닙니다.");
            return true;
        }
        
        // 해금 조건 확인 및 선택
        JobService.JobSelectResult result = jobService.selectTier1Job(player, jobType);
        
        if (result.success()) {
            player.sendMessage(String.format("§a[직업] §6%s§a 직업을 선택했습니다!", jobType.getDisplayName()));
            player.sendMessage("§7" + jobType.getDescription());
        } else {
            player.sendMessage(result.message());
        }
        
        return true;
    }
    
    /**
     * 승급 시도 (NPC로 이관됨)
     */
    private boolean handlePromote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return true;
        }
        
        // [Phase 승급효과] NPC를 통해서만 승급 가능하도록 변경
        player.sendMessage("§e[직업] §f승급은 마을의 §6승급 NPC§f를 통해서만 가능합니다.");
        player.sendMessage("§7각 직업의 승급 NPC를 찾아가세요.");
        player.sendMessage("§7- 광부 승급 NPC");
        player.sendMessage("§7- 농부 승급 NPC");
        player.sendMessage("§7- 어부 승급 NPC");
        return true;
    }
    
    /**
     * 승급 조건 확인
     */
    private boolean handleRequirements(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return true;
        }
        
        JobType tier1Job = jobService.getTier1Job(player);
        if (tier1Job == null) {
            // 직업이 없으면 해금 조건 표시
            player.sendMessage("§6=== Tier 1 직업 해금 조건 ===");
            for (JobType type : JobType.getByTier(JobTier.TIER_1)) {
                String requirements = jobService.getRegistry().getUnlockRequirementsString(player, type);
                player.sendMessage(requirements);
            }
            return true;
        }
        
        // 직업이 있으면 승급 조건 표시
        var gradeService = jobService.getRegistry().getGradeService(tier1Job);
        if (gradeService != null) {
            String requirements = gradeService.getPromoteRequirementsString(player);
            player.sendMessage(requirements);
        } else {
            player.sendMessage("§c승급 정보를 조회할 수 없습니다.");
        }
        
        return true;
    }
    
    /**
     * 관리자 명령어 처리
     */
    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tycoon.admin.job")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length < 2) {
            showAdminHelp(sender);
            return true;
        }
        
        String adminAction = args[1].toLowerCase();
        
        return switch (adminAction) {
            case "give" -> handleAdminGive(sender, args);
            case "remove" -> handleAdminRemove(sender, args);
            case "setlevel" -> handleAdminSetLevel(sender, args);
            case "setexp" -> handleAdminSetExp(sender, args);
            case "promote" -> handleAdminPromote(sender, args);
            default -> {
                showAdminHelp(sender);
                yield true;
            }
        };
    }
    
    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage("§6=== Job Admin Commands ===");
        sender.sendMessage("§e/job admin give <player> <job> §7- 직업 부여");
        sender.sendMessage("§e/job admin remove <player> <job> §7- 직업 제거");
        sender.sendMessage("§e/job admin setlevel <player> <job> <level> §7- 레벨 설정");
        sender.sendMessage("§e/job admin setexp <player> <job> <exp> §7- 경험치 설정");
        sender.sendMessage("§e/job admin promote <player> [job] §7- 강제 승급");
    }
    
    private boolean handleAdminGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c사용법: /job admin give <player> <job>");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return true;
        }
        
        JobType jobType = JobType.fromId(args[3]);
        if (jobType == null) {
            sender.sendMessage("§c존재하지 않는 직업입니다: " + args[3]);
            return true;
        }
        
        boolean success = jobService.grantJobAdmin(target.getUniqueId(), jobType);
        if (success) {
            sender.sendMessage(String.format("§a[Admin] %s에게 %s 직업을 부여했습니다.",
                    target.getName(), jobType.getDisplayName()));
            target.sendMessage(String.format("§a[직업] 관리자에 의해 %s 직업이 부여되었습니다.",
                    jobType.getDisplayName()));
        } else {
            sender.sendMessage("§c직업 부여에 실패했습니다.");
        }
        
        return true;
    }
    
    private boolean handleAdminRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c사용법: /job admin remove <player> <job>");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return true;
        }
        
        JobType jobType = JobType.fromId(args[3]);
        if (jobType == null) {
            sender.sendMessage("§c존재하지 않는 직업입니다: " + args[3]);
            return true;
        }
        
        boolean success = jobService.removeJobAdmin(target.getUniqueId(), jobType);
        if (success) {
            sender.sendMessage(String.format("§a[Admin] %s의 %s 직업을 제거했습니다.",
                    target.getName(), jobType.getDisplayName()));
            target.sendMessage(String.format("§c[직업] 관리자에 의해 %s 직업이 제거되었습니다.",
                    jobType.getDisplayName()));
        } else {
            sender.sendMessage("§c직업 제거에 실패했습니다.");
        }
        
        return true;
    }
    
    private boolean handleAdminSetLevel(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c사용법: /job admin setlevel <player> <job> <level>");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return true;
        }
        
        JobType jobType = JobType.fromId(args[3]);
        if (jobType == null) {
            sender.sendMessage("§c존재하지 않는 직업입니다: " + args[3]);
            return true;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 숫자를 입력하세요.");
            return true;
        }
        
        boolean success = jobService.setLevelAdmin(target.getUniqueId(), jobType, level);
        if (success) {
            sender.sendMessage(String.format("§a[Admin] %s의 %s 레벨을 %d로 설정했습니다.",
                    target.getName(), jobType.getDisplayName(), level));
            // 대상 플레이어에게 알림
            target.sendMessage(String.format("§e[직업] 관리자에 의해 %s 레벨이 %d로 설정되었습니다.",
                    jobType.getDisplayName(), level));
        } else {
            sender.sendMessage("§c레벨 설정에 실패했습니다. 해당 직업이 있는지 확인하세요.");
        }
        
        return true;
    }
    
    /**
     * 직업별 상세 명령어 처리 (Phase 4.B)
     * 
     * /job miner [stats|promote|requirements]
     * /job farmer [stats|promote|requirements]
     * /job fisher [stats|promote|requirements]
     */
    private boolean handleJobSpecific(CommandSender sender, String[] args, JobType jobType) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return true;
        }
        
        // 해당 직업을 가지고 있는지 확인
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data == null || !data.hasJob(jobType)) {
            player.sendMessage(String.format("§c%s 직업이 없습니다.", jobType.getDisplayName()));
            return true;
        }
        
        // 서브커맨드 처리
        String subAction = args.length >= 2 ? args[1].toLowerCase() : "stats";
        
        return switch (subAction) {
            case "stats", "info" -> handleJobStats(player, jobType);
            case "promote" -> {
                // [Phase 승급효과] NPC를 통해서만 승급 가능
                player.sendMessage("§e[직업] §f승급은 마을의 §6승급 NPC§f를 통해서만 가능합니다.");
                yield true;
            }
            case "requirements", "req" -> handleJobRequirements(player, jobType);
            default -> {
                player.sendMessage(String.format("§6=== %s 명령어 ===", jobType.getDisplayName()));
                player.sendMessage(String.format("§e/job %s §7- 통계 보기", jobType.getId()));
                player.sendMessage(String.format("§e/job %s stats §7- 통계 보기", jobType.getId()));
                player.sendMessage(String.format("§e/job %s requirements §7- 승급 조건", jobType.getId()));
                player.sendMessage("§7승급은 마을의 승급 NPC를 통해 가능합니다.");
                yield true;
            }
        };
    }
    
    private boolean handleJobStats(Player player, JobType jobType) {
        var expService = jobService.getRegistry().getExpService(jobType);
        var gradeService = jobService.getRegistry().getGradeService(jobType);
        
        if (expService == null || gradeService == null) {
            player.sendMessage("§c직업 정보를 조회할 수 없습니다.");
            return true;
        }
        
        player.sendMessage(String.format("§6=== %s 통계 ===", jobType.getDisplayName()));
        player.sendMessage(expService.getInfoString(player));
        player.sendMessage(String.format("§7등급: %s", gradeService.getGrade(player).getDisplayName()));
        
        // 추가 통계 (직업별로 다른 통계 표시)
        PlayerTycoonData data = dataManager.get(player.getUniqueId());
        if (data != null) {
            switch (jobType) {
                case MINER -> {
                    player.sendMessage(String.format("§7총 채굴량: %,d개", data.getTotalMined()));
                    player.sendMessage(String.format("§7광물 판매액: %,d BD", data.getTotalMinerSales()));
                }
                case FARMER -> {
                    player.sendMessage(String.format("§7총 수확량: %,d개", data.getTotalHarvested()));
                    player.sendMessage(String.format("§7작물 판매액: %,d BD", data.getTotalFarmerSales()));
                }
                case FISHER -> {
                    player.sendMessage(String.format("§7총 낚시량: %,d마리", data.getTotalFished()));
                    player.sendMessage(String.format("§7수산물 판매액: %,d BD", data.getTotalFisherSales()));
                }
                default -> {}
            }
            player.sendMessage(String.format("§7총 판매액: %,d BD", data.getTotalSales()));
        }
        
        return true;
    }
    
    private boolean handleJobRequirements(Player player, JobType jobType) {
        var gradeService = jobService.getRegistry().getGradeService(jobType);
        if (gradeService != null) {
            String requirements = gradeService.getPromoteRequirementsString(player);
            player.sendMessage(requirements);
        } else {
            player.sendMessage("§c승급 정보를 조회할 수 없습니다.");
        }
        
        return true;
    }
    
    private boolean handleAdminSetExp(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§c사용법: /job admin setexp <player> <job> <exp>");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return true;
        }
        
        JobType jobType = JobType.fromId(args[3]);
        if (jobType == null) {
            sender.sendMessage("§c존재하지 않는 직업입니다: " + args[3]);
            return true;
        }
        
        long exp;
        try {
            exp = Long.parseLong(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 숫자를 입력하세요.");
            return true;
        }
        
        boolean success = jobService.setExpAdmin(target.getUniqueId(), jobType, exp);
        if (success) {
            sender.sendMessage(String.format("§a[Admin] %s의 %s 경험치를 %,d로 설정했습니다.",
                    target.getName(), jobType.getDisplayName(), exp));
            // 대상 플레이어에게 알림
            target.sendMessage(String.format("§e[직업] 관리자에 의해 %s 경험치가 %,d로 설정되었습니다.",
                    jobType.getDisplayName(), exp));
        } else {
            sender.sendMessage("§c경험치 설정에 실패했습니다. 해당 직업이 있는지 확인하세요.");
        }
        
        return true;
    }
    
    /**
     * [Phase 승급효과] 관리자 강제 승급
     */
    private boolean handleAdminPromote(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c사용법: /job admin promote <player> [job]");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[2]);
            return true;
        }
        
        // 직업 지정 여부
        JobType jobType;
        if (args.length >= 4) {
            jobType = JobType.fromId(args[3]);
            if (jobType == null) {
                sender.sendMessage("§c존재하지 않는 직업입니다: " + args[3]);
                return true;
            }
        } else {
            // 직업 미지정 시 Tier 1 직업 자동 감지
            jobType = jobService.getTier1Job(target);
            if (jobType == null) {
                sender.sendMessage("§c" + target.getName() + "님은 Tier 1 직업이 없습니다. 직업을 지정해주세요.");
                return true;
            }
        }
        
        // 직업 보유 확인
        PlayerTycoonData data = dataManager.get(target.getUniqueId());
        if (data == null || !data.hasJob(jobType)) {
            sender.sendMessage("§c" + target.getName() + "님은 " + jobType.getDisplayName() + " 직업이 없습니다.");
            return true;
        }
        
        // 승급 실행 (BD 차감 없이 강제 승급)
        var gradeService = jobService.getRegistry().getGradeService(jobType);
        if (gradeService == null) {
            sender.sendMessage("§c승급 서비스를 찾을 수 없습니다.");
            return true;
        }
        
        JobGrade currentGrade = gradeService.getGrade(target);
        if (currentGrade.isMaxGrade()) {
            sender.sendMessage("§c" + target.getName() + "님은 이미 최고 등급입니다.");
            return true;
        }
        
        JobGrade nextGrade = currentGrade.next();
        boolean success = gradeService.setGrade(target.getUniqueId(), nextGrade);
        
        if (success) {
            sender.sendMessage(String.format("§a[Admin] %s의 %s 등급을 %s → %s로 승급했습니다.",
                    target.getName(), jobType.getDisplayName(), 
                    currentGrade.getDisplayName(), nextGrade.getDisplayName()));
            target.sendMessage(String.format("§a[직업] 관리자에 의해 %s %s로 승급되었습니다!",
                    jobType.getDisplayName(), nextGrade.getDisplayName()));
        } else {
            sender.sendMessage("§c승급에 실패했습니다.");
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("info");
            completions.add("select");
            completions.add("promote");
            completions.add("requirements");
            // Phase 4.B: 직업별 서브커맨드
            completions.add("miner");
            completions.add("farmer");
            completions.add("fisher");
            if (sender.hasPermission("tycoon.admin.job")) {
                completions.add("admin");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("select")) {
                for (JobType type : JobType.getByTier(JobTier.TIER_1)) {
                    completions.add(type.getId());
                }
            } else if (args[0].equalsIgnoreCase("miner") || 
                       args[0].equalsIgnoreCase("farmer") || 
                       args[0].equalsIgnoreCase("fisher")) {
                // 직업별 서브커맨드
                completions.add("stats");
                completions.add("requirements");
            } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("tycoon.admin.job")) {
                completions.add("give");
                completions.add("remove");
                completions.add("setlevel");
                completions.add("setexp");
                completions.add("promote");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            // 플레이어 이름 자동완성
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            // 직업 이름 자동완성
            for (JobType type : JobType.values()) {
                completions.add(type.getId());
            }
        }
        
        // 필터링
        String lastArg = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(lastArg));
        
        return completions;
    }
}
