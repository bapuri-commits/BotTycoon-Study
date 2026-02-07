package kr.bapuri.tycoon.tax;

import kr.bapuri.tycoon.integration.LandsIntegration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * TaxCommand - 플레이어용 세금 조회 명령어
 * 
 * /tax info - 내 세금 정보 조회
 * /tax land <landName> - 특정 마을 세금 조회
 */
public class TaxCommand implements CommandExecutor, TabCompleter {

    private final TaxConfig config;
    private final IncomeTaxService incomeTaxService;
    private final LandTaxService landTaxService;
    private final LandsIntegration landsIntegration;

    public TaxCommand(TaxConfig config, IncomeTaxService incomeTaxService,
                     LandTaxService landTaxService, LandsIntegration landsIntegration) {
        this.config = config;
        this.incomeTaxService = incomeTaxService;
        this.landTaxService = landTaxService;
        this.landsIntegration = landsIntegration;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> showTaxInfo(player);
            case "land" -> {
                if (args.length < 2) {
                    player.sendMessage("§c사용법: /tax land <마을이름>");
                    return true;
                }
                showLandTaxInfo(player, args[1]);
            }
            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§e=== 세금 명령어 ===");
        player.sendMessage("§e/tax info §7- 내 세금 정보 조회");
        player.sendMessage("§e/tax land <마을이름> §7- 특정 마을 세금 조회");
    }

    private void showTaxInfo(Player player) {
        // 소득세 정보
        IncomeTaxService.PlayerTaxInfo taxInfo = incomeTaxService.getPlayerTaxInfo(player);
        
        player.sendMessage("§e=== 세금 정보 ===");
        player.sendMessage("§7오늘 소득: §f" + String.format("%,d", taxInfo.getDailyIncome()) + " BD");
        player.sendMessage("§7현재 소득세율: §f" + String.format("%.1f", taxInfo.getCurrentRate() * 100) + "%");
        
        // 토지세 정보
        LandTaxService.PlayerTaxSummary summary = landTaxService.calculatePlayerTax(player.getUniqueId());
        
        if (summary.getLandTaxes().isEmpty()) {
            player.sendMessage("§7소유한 마을이 없습니다.");
        } else {
            player.sendMessage("§7소유 마을: §f" + summary.getLandTaxes().size() + "개");
            player.sendMessage("§7총 청크: §f" + summary.getTotalChunks() + "개");
            player.sendMessage("§7총 주민: §f" + summary.getTotalVillagers() + "마리");
            player.sendMessage("§7예상 토지세: §f" + String.format("%,d", summary.getTotalTax()) + " BD/" + 
                             config.getPeriodHours() + "시간");
        }
    }

    private void showLandTaxInfo(Player player, String landName) {
        // 마을 정보 조회
        Optional<LandsIntegration.PlotInfo> landOpt = landsIntegration.getLandByName(landName);
        
        if (landOpt.isEmpty()) {
            player.sendMessage("§c마을을 찾을 수 없습니다: " + landName);
            return;
        }

        LandsIntegration.PlotInfo land = landOpt.get();
        
        // 소유자 확인
        if (!land.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("tycoon.tax.admin")) {
            player.sendMessage("§c자신이 소유한 마을만 조회할 수 있습니다.");
            return;
        }

        LandTaxService.LandTaxInfo taxInfo = landTaxService.calculateTax(landName);
        
        player.sendMessage("§e=== " + landName + " 마을 세금 정보 ===");
        player.sendMessage("§7소유자: §f" + land.getOwnerName());
        player.sendMessage("§7청크 수: §f" + taxInfo.getChunks() + "개");
        player.sendMessage("§7등록된 주민: §f" + taxInfo.getVillagers() + "마리");
        player.sendMessage("§7---");
        player.sendMessage("§7청크 세금: §f" + String.format("%,d", taxInfo.getChunkTax()) + " BD");
        player.sendMessage("§7주민 세금: §f" + String.format("%,d", taxInfo.getVillagerTax()) + " BD");
        player.sendMessage("§7총 세금: §e" + String.format("%,d", taxInfo.getTotalTax()) + " BD/" + 
                         config.getPeriodHours() + "시간");
        
        if (landTaxService.isLandFrozen(landName)) {
            player.sendMessage("§c⚠ 이 마을은 세금 미납으로 정지되어 있습니다!");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("info", "land"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("land")) {
            // 자신이 소유한 마을 목록
            List<LandsIntegration.PlotInfo> lands = landsIntegration.getOwnedLands(player.getUniqueId());
            List<String> landNames = new ArrayList<>();
            for (LandsIntegration.PlotInfo land : lands) {
                landNames.add(land.getName());
            }
            return filterStartsWith(landNames, args[1]);
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
