package kr.bapuri.tycoon.job.npc;

import kr.bapuri.tycoon.integration.CitizensIntegration;
import kr.bapuri.tycoon.job.JobType;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * PromoteNpcCommand - 승급 NPC 관리 명령어
 * 
 * [Phase 승급효과] 관리자 전용 명령어
 * 
 * 명령어:
 * - /promotenpc set <job> - 현재 바라보는 NPC를 승급 NPC로 등록
 * - /promotenpc remove - 현재 바라보는 NPC의 승급 NPC 등록 해제
 * - /promotenpc list - 등록된 승급 NPC 목록 조회
 */
public class PromoteNpcCommand implements CommandExecutor, TabCompleter {
    
    private final JavaPlugin plugin;
    private final PromoteNpcRegistry registry;
    private final PromoteNpcListener listener;
    private final Optional<CitizensIntegration> citizensIntegration;
    
    public PromoteNpcCommand(JavaPlugin plugin, PromoteNpcRegistry registry, 
                              PromoteNpcListener listener, Optional<CitizensIntegration> citizens) {
        this.plugin = plugin;
        this.registry = registry;
        this.listener = listener;
        this.citizensIntegration = citizens;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("tycoon.admin.npc")) {
            sender.sendMessage("§c권한이 없습니다.");
            return true;
        }
        
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "set" -> handleSet(sender, args);
            case "remove" -> handleRemove(sender);
            case "list" -> handleList(sender);
            default -> showUsage(sender);
        }
        
        return true;
    }
    
    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /promotenpc set <miner|farmer|fisher>");
            return;
        }
        
        String jobName = args[1].toUpperCase();
        JobType parsedJobType;
        try {
            parsedJobType = JobType.valueOf(jobName);
        } catch (IllegalArgumentException e) {
            JobType byId = JobType.fromId(jobName.toLowerCase());
            if (byId == null) {
                sender.sendMessage("§c유효하지 않은 직업입니다: " + args[1]);
                sender.sendMessage("§7사용 가능: MINER, FARMER, FISHER");
                return;
            }
            parsedJobType = byId;
        }
        final JobType jobType = parsedJobType;
        
        // 바라보는 NPC 찾기
        NPC npc = getTargetNpc(player);
        if (npc == null) {
            sender.sendMessage("§cNPC를 바라보고 있지 않습니다. Citizens NPC를 바라보세요.");
            return;
        }
        
        int npcId = npc.getId();
        String npcName = npc.getName();
        
        // 등록
        citizensIntegration.ifPresent(ci -> 
            listener.registerNewNpc(npcId, jobType, ci)
        );
        
        if (citizensIntegration.isEmpty()) {
            registry.registerNpc(npcId, jobType);
        }
        
        sender.sendMessage(String.format("§a승급 NPC 등록 완료: §f%s §7(ID: %d) → §e%s",
                npcName, npcId, jobType.getDisplayName()));
    }
    
    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return;
        }
        
        NPC npc = getTargetNpc(player);
        if (npc == null) {
            sender.sendMessage("§cNPC를 바라보고 있지 않습니다.");
            return;
        }
        
        int npcId = npc.getId();
        
        if (!registry.isPromoteNpc(npcId)) {
            sender.sendMessage("§c이 NPC는 승급 NPC로 등록되어 있지 않습니다.");
            return;
        }
        
        registry.unregisterNpc(npcId);
        sender.sendMessage("§a승급 NPC 등록 해제 완료: " + npc.getName());
    }
    
    private void handleList(CommandSender sender) {
        var allNpcs = registry.getAllNpcs();
        
        if (allNpcs.isEmpty()) {
            sender.sendMessage("§7등록된 승급 NPC가 없습니다.");
            return;
        }
        
        sender.sendMessage("§6§l=== 등록된 승급 NPC ===");
        allNpcs.forEach((npcId, jobType) -> {
            String npcName = "알 수 없음";
            try {
                NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
                if (npc != null) {
                    npcName = npc.getName();
                }
            } catch (Exception e) {
                // Citizens 없으면 무시
            }
            sender.sendMessage(String.format("  §7- §f%s §7(ID: %d) → §e%s", 
                    npcName, npcId, jobType.getDisplayName()));
        });
    }
    
    private void showUsage(CommandSender sender) {
        sender.sendMessage("§6=== 승급 NPC 명령어 ===");
        sender.sendMessage("§e/promotenpc set <job> §7- NPC를 승급 NPC로 등록");
        sender.sendMessage("§e/promotenpc remove §7- 승급 NPC 등록 해제");
        sender.sendMessage("§e/promotenpc list §7- 등록된 NPC 목록");
    }
    
    /**
     * 플레이어가 바라보는 NPC 찾기
     */
    private NPC getTargetNpc(Player player) {
        try {
            // 플레이어가 바라보는 엔티티 찾기 (5블록 이내)
            Entity targetEntity = player.getTargetEntity(5);
            if (targetEntity == null) return null;
            
            // Citizens NPC인지 확인
            return CitizensAPI.getNPCRegistry().getNPC(targetEntity);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, 
                                                @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("set", "remove", "list"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            completions.addAll(Arrays.stream(JobType.values())
                    .filter(JobType::isTier1)
                    .map(j -> j.name().toLowerCase())
                    .collect(Collectors.toList()));
        }
        
        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
    }
}
