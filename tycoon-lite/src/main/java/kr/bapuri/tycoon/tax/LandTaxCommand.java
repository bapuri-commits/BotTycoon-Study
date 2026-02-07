package kr.bapuri.tycoon.tax;

import kr.bapuri.tycoon.integration.LandsIntegration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

/**
 * LandTaxCommand - 관리자용 세금 관리 명령어
 * 
 * /landtax villager add <land> <count> - 주민 등록
 * /landtax villager remove <land> <count> - 주민 제거
 * /landtax villager list <land> - 주민 수 조회
 * /landtax freeze <land> - 마을 정지
 * /landtax unfreeze <land> - 마을 정지 해제
 * /landtax collect - 즉시 세금 징수
 * /landtax reload - 설정 리로드
 */
public class LandTaxCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "tycoon.tax.admin";

    private final TaxConfig config;
    private final VillagerRegistry villagerRegistry;
    private final LandTaxService landTaxService;
    private final TaxScheduler taxScheduler;
    private final LandsIntegration landsIntegration;

    public LandTaxCommand(TaxConfig config, VillagerRegistry villagerRegistry,
                         LandTaxService landTaxService, TaxScheduler taxScheduler,
                         LandsIntegration landsIntegration) {
        this.config = config;
        this.villagerRegistry = villagerRegistry;
        this.landTaxService = landTaxService;
        this.taxScheduler = taxScheduler;
        this.landsIntegration = landsIntegration;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "villager" -> handleVillager(sender, args);
            case "freeze" -> handleFreeze(sender, args);
            case "unfreeze" -> handleUnfreeze(sender, args);
            case "collect" -> handleCollect(sender, args);
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender, args);
            default -> showHelp(sender);
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§e=== 토지세 관리 명령어 ===");
        sender.sendMessage("§e/landtax villager add <마을> <수량> §7- 주민 등록");
        sender.sendMessage("§e/landtax villager remove <마을> <수량> §7- 주민 제거");
        sender.sendMessage("§e/landtax villager list <마을> §7- 주민 수 조회");
        sender.sendMessage("§e/landtax freeze <마을> §7- 마을 정지");
        sender.sendMessage("§e/landtax unfreeze <마을> §7- 마을 정지 해제");
        sender.sendMessage("§e/landtax collect §7- 즉시 세금 징수");
        sender.sendMessage("§e/landtax info <마을> §7- 마을 세금 정보");
        sender.sendMessage("§e/landtax reload §7- 설정 리로드");
    }

    private void handleVillager(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /landtax villager <add|remove|list> <마을> [수량]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage("§c사용법: /landtax villager add <마을> <수량>");
                    return;
                }
                addVillager(sender, args[2], args[3]);
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage("§c사용법: /landtax villager remove <마을> <수량>");
                    return;
                }
                removeVillager(sender, args[2], args[3]);
            }
            case "list" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c사용법: /landtax villager list <마을>");
                    return;
                }
                listVillager(sender, args[2]);
            }
            default -> sender.sendMessage("§c알 수 없는 하위 명령어: " + action);
        }
    }

    private void addVillager(CommandSender sender, String landName, String countStr) {
        int count;
        try {
            count = Integer.parseInt(countStr);
            if (count <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 수량을 입력하세요.");
            return;
        }

        // 마을 존재 확인
        Optional<LandsIntegration.PlotInfo> landOpt = landsIntegration.getLandByName(landName);
        if (landOpt.isEmpty()) {
            sender.sendMessage("§c마을을 찾을 수 없습니다: " + landName);
            return;
        }

        UUID ownerId = landOpt.get().getOwnerId();
        int total = villagerRegistry.addVillagers(landName, count, ownerId);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("land", landName);
        placeholders.put("count", String.valueOf(count));
        placeholders.put("total", String.valueOf(total));
        
        sender.sendMessage(config.getMessage("villagerAdded", placeholders));
    }

    private void removeVillager(CommandSender sender, String landName, String countStr) {
        int count;
        try {
            count = Integer.parseInt(countStr);
            if (count <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§c올바른 수량을 입력하세요.");
            return;
        }

        int result = villagerRegistry.removeVillagers(landName, count);
        if (result == -1) {
            sender.sendMessage("§c마을 데이터가 없습니다: " + landName);
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("land", landName);
        placeholders.put("count", String.valueOf(count));
        placeholders.put("total", String.valueOf(result));
        
        sender.sendMessage(config.getMessage("villagerRemoved", placeholders));
    }

    private void listVillager(CommandSender sender, String landName) {
        int villagers = villagerRegistry.getVillagers(landName);
        sender.sendMessage("§e" + landName + " 마을 등록 주민: §f" + villagers + "마리");
    }

    private void handleFreeze(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /landtax freeze <마을>");
            return;
        }

        String landName = args[1];
        
        // VillagerRegistry 정지
        villagerRegistry.freeze(landName);
        
        // Lands API 네이티브 정지
        boolean landsSuccess = landsIntegration.freezeLand(landName);
        
        Map<String, String> placeholders = Map.of("land", landName);
        sender.sendMessage(config.getMessage("landFrozen", placeholders));
        
        if (landsSuccess) {
            sender.sendMessage("§7(Lands API로 플래그가 비활성화되었습니다)");
        } else {
            sender.sendMessage("§7(경고: Lands API 정지 실패 - 이벤트 차단 방식으로 동작)");
        }
    }

    private void handleUnfreeze(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /landtax unfreeze <마을>");
            return;
        }

        String landName = args[1];
        
        // VillagerRegistry 정지 해제
        villagerRegistry.unfreeze(landName);
        
        // Lands API 네이티브 정지 해제
        boolean landsSuccess = landsIntegration.unfreezeLand(landName);
        
        Map<String, String> placeholders = Map.of("land", landName);
        sender.sendMessage(config.getMessage("landUnfrozen", placeholders));
        
        if (landsSuccess) {
            sender.sendMessage("§7(Lands API로 플래그가 복원되었습니다)");
        } else {
            sender.sendMessage("§7(경고: Lands API 해제 실패 - 수동으로 /lands admin 확인 필요)");
        }
    }

    private void handleCollect(CommandSender sender, String[] args) {
        sender.sendMessage("§e세금 징수를 시작합니다...");
        taxScheduler.forceCollection();
        sender.sendMessage("§a세금 징수가 완료되었습니다.");
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /landtax info <마을>");
            return;
        }

        String landName = args[1];
        LandTaxService.LandTaxInfo taxInfo = landTaxService.calculateTax(landName);
        
        sender.sendMessage("§e=== " + landName + " 세금 정보 ===");
        sender.sendMessage("§7청크: " + taxInfo.getChunks() + ", 주민: " + taxInfo.getVillagers());
        sender.sendMessage("§7청크 세금: " + String.format("%,d", taxInfo.getChunkTax()) + " BD");
        sender.sendMessage("§7주민 세금: " + String.format("%,d", taxInfo.getVillagerTax()) + " BD");
        sender.sendMessage("§7총합: §e" + String.format("%,d", taxInfo.getTotalTax()) + " BD");
        
        // 정지 상태 (VillagerRegistry + Lands API)
        boolean registryFrozen = villagerRegistry.isFrozen(landName);
        boolean landsFrozen = landsIntegration.isLandFrozen(landName);
        
        String frozenStatus;
        if (registryFrozen && landsFrozen) {
            frozenStatus = "§c예 (Registry + Lands 동기화됨)";
        } else if (registryFrozen) {
            frozenStatus = "§c예 (Registry만 - Lands 동기화 필요)";
        } else if (landsFrozen) {
            frozenStatus = "§e경고 (Lands만 정지됨 - Registry 미동기)";
        } else {
            frozenStatus = "§a아니오";
        }
        sender.sendMessage("§7정지 상태: " + frozenStatus);
    }

    private void handleReload(CommandSender sender) {
        config.reload();
        sender.sendMessage("§a세금 설정이 리로드되었습니다.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("villager", "freeze", "unfreeze", "collect", "info", "reload"), args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("villager")) {
                return filterStartsWith(Arrays.asList("add", "remove", "list"), args[1]);
            }
            if (args[0].equalsIgnoreCase("freeze") || args[0].equalsIgnoreCase("unfreeze") || 
                args[0].equalsIgnoreCase("info")) {
                // 등록된 마을 목록
                List<String> landNames = new ArrayList<>();
                for (VillagerRegistry.LandTaxData data : villagerRegistry.getAllLandData()) {
                    landNames.add(data.getLandName());
                }
                return filterStartsWith(landNames, args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("villager")) {
            // 마을 이름
            List<String> landNames = new ArrayList<>();
            for (VillagerRegistry.LandTaxData data : villagerRegistry.getAllLandData()) {
                landNames.add(data.getLandName());
            }
            return filterStartsWith(landNames, args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("villager") && 
            (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return Arrays.asList("1", "5", "10");
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lowerPrefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
