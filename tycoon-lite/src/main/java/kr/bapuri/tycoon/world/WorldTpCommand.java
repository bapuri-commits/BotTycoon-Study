package kr.bapuri.tycoon.world;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * WorldTpCommand - 관리자 월드 텔레포트 명령어
 * 
 * 명령어:
 * - /worldtp <town|wild|nether|end> [player|@a] - 월드 스폰으로 텔레포트
 * - /worldtp list - 월드 목록 표시
 */
public class WorldTpCommand implements CommandExecutor, TabCompleter {

    private static final Logger logger = Logger.getLogger("TycoonLite.WorldTpCommand");
    
    private final WorldManager worldManager;

    public WorldTpCommand(WorldManager worldManager) {
        this.worldManager = worldManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tycoon.admin.worldtp")) {
            sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            handleList(sender);
            return true;
        }

        // 월드 텔레포트
        handleTeleport(sender, args);
        return true;
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== 서버 월드 목록 =====");
        
        for (WorldType type : WorldType.values()) {
            World world = worldManager.getWorld(type).orElse(null);
            String status = (world != null) ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
            String worldName = world != null ? world.getName() : "미로드";
            
            sender.sendMessage(status + " " + ChatColor.YELLOW + type.getDisplayName() + 
                ChatColor.GRAY + " (" + worldName + ")");
        }
        
        // Wild 연결 차원
        String wildNetherName = worldManager.getWildNetherName();
        String wildEndName = worldManager.getWildEndName();
        
        World wildNether = Bukkit.getWorld(wildNetherName);
        World wildEnd = Bukkit.getWorld(wildEndName);
        
        sender.sendMessage((wildNether != null ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗") + 
            " " + ChatColor.YELLOW + "야생 네더" + ChatColor.GRAY + " (" + wildNetherName + ")");
        sender.sendMessage((wildEnd != null ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗") + 
            " " + ChatColor.YELLOW + "야생 엔드" + ChatColor.GRAY + " (" + wildEndName + ")");
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        String worldArg = args[0].toLowerCase();
        
        // 월드 해결
        WorldResolveResult result = resolveWorld(worldArg);
        if (result.world == null) {
            sender.sendMessage(ChatColor.RED + "알 수 없거나 로드되지 않은 월드: " + worldArg);
            sender.sendMessage(ChatColor.GRAY + "사용 가능: town, wild, nether, end");
            sender.sendMessage(ChatColor.GRAY + "목록 확인: /worldtp list");
            return;
        }
        
        // 타겟 플레이어 결정
        if (args.length >= 2) {
            String targetArg = args[1];
            
            // @a 지원 - 전체 플레이어 이동
            if (targetArg.equalsIgnoreCase("@a")) {
                teleportAllPlayers(sender, result);
                return;
            }
            
            // 특정 플레이어
            Player target = Bukkit.getPlayer(targetArg);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "플레이어를 찾을 수 없습니다: " + targetArg);
                return;
            }
            teleportPlayer(sender, target, result);
        } else {
            // 자기 자신 이동
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "콘솔에서는 플레이어를 지정해야 합니다: /worldtp <world> <player|@a>");
                return;
            }
            teleportPlayer(sender, player, result);
        }
    }
    
    /**
     * 월드 이름/별칭을 실제 World 객체로 해결
     */
    private WorldResolveResult resolveWorld(String worldArg) {
        return switch (worldArg.toLowerCase()) {
            case "town", "마을" -> new WorldResolveResult(
                worldManager.getWorld(WorldType.TOWN).orElse(null), "마을");
            case "wild", "야생" -> new WorldResolveResult(
                worldManager.getWorld(WorldType.WILD).orElse(null), "야생");
            case "nether", "네더" -> new WorldResolveResult(
                Bukkit.getWorld(worldManager.getWildNetherName()), "야생 네더");
            case "end", "엔드" -> new WorldResolveResult(
                Bukkit.getWorld(worldManager.getWildEndName()), "야생 엔드");
            default -> new WorldResolveResult(
                Bukkit.getWorld(worldArg), worldArg);
        };
    }
    
    /**
     * 단일 플레이어 텔레포트
     */
    private void teleportPlayer(CommandSender sender, Player target, WorldResolveResult result) {
        Location spawnLoc = result.world.getSpawnLocation();
        target.teleport(spawnLoc);
        
        // 로깅
        logger.info("[WorldTp] " + sender.getName() + " → " + target.getName() + 
                   " 이동: " + result.displayName + " (" + result.world.getName() + ")");
        
        // 메시지
        if (sender.equals(target)) {
            sender.sendMessage(ChatColor.GREEN + result.displayName + " 월드로 이동했습니다.");
        } else {
            sender.sendMessage(ChatColor.GREEN + target.getName() + "님을 " + 
                             result.displayName + " 월드로 이동시켰습니다.");
            target.sendMessage(ChatColor.GREEN + "관리자에 의해 " + 
                             result.displayName + " 월드로 이동되었습니다.");
        }
    }
    
    /**
     * 전체 플레이어 텔레포트 (@a)
     */
    private void teleportAllPlayers(CommandSender sender, WorldResolveResult result) {
        Location spawnLoc = result.world.getSpawnLocation();
        int count = 0;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawnLoc);
            player.sendMessage(ChatColor.GREEN + "관리자에 의해 " + 
                             result.displayName + " 월드로 이동되었습니다.");
            count++;
        }
        
        // 로깅
        logger.info("[WorldTp] " + sender.getName() + " → @a(" + count + "명) 이동: " + 
                   result.displayName + " (" + result.world.getName() + ")");
        
        sender.sendMessage(ChatColor.GREEN + "전체 플레이어(" + count + "명)를 " + 
                         result.displayName + " 월드로 이동시켰습니다.");
    }
    
    /**
     * 월드 해결 결과
     */
    private record WorldResolveResult(World world, String displayName) {}

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== /worldtp 명령어 =====");
        sender.sendMessage(ChatColor.YELLOW + "/worldtp <world> [player|@a] " + ChatColor.GRAY + "- 월드 스폰으로 이동");
        sender.sendMessage(ChatColor.YELLOW + "/worldtp list " + ChatColor.GRAY + "- 월드 목록 표시");
        sender.sendMessage(ChatColor.GRAY + "사용 가능 월드: town, wild, nether, end");
        sender.sendMessage(ChatColor.GRAY + "@a: 전체 플레이어 이동");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("tycoon.admin.worldtp")) {
            return completions;
        }

        if (args.length == 1) {
            List<String> options = Arrays.asList("town", "wild", "nether", "end", "list");
            for (String opt : options) {
                if (opt.startsWith(args[0].toLowerCase())) {
                    completions.add(opt);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            String input = args[1].toLowerCase();
            
            // @a 자동완성
            if ("@a".startsWith(input)) {
                completions.add("@a");
            }
            
            // 플레이어 이름 자동완성
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) {
                    completions.add(p.getName());
                }
            }
        }

        return completions;
    }
}
