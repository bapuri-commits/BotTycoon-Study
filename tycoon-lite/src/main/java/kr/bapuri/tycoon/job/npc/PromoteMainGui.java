package kr.bapuri.tycoon.job.npc;

import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.common.AbstractJobGradeService;
import kr.bapuri.tycoon.job.common.AbstractJobGradeService.PromotionCheckResult;
import kr.bapuri.tycoon.job.common.AbstractJobGradeService.PromotionResult;
import kr.bapuri.tycoon.job.common.GradeBonusConfig;
import kr.bapuri.tycoon.job.miner.MiningEfficiencyEnchant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PromoteMainGui - 승급 NPC 메인 GUI
 * 
 * [Phase 승급효과] 기능:
 * 1. 승급하기 - 조건 확인 및 승급 진행
 * 2. 등급 정보 - 현재 등급, 보너스, 다음 등급 조건 표시
 * 3. 도구 인챈트 - 채굴 효율 인챈트 적용 (광부 전용)
 */
public class PromoteMainGui implements Listener {
    
    private static final String GUI_TITLE_PREFIX = "§8[승급] ";
    private static final int GUI_SIZE = 27; // 3줄
    
    // 슬롯 위치
    private static final int SLOT_PROMOTE = 11;      // 승급하기
    private static final int SLOT_GRADE_INFO = 13;   // 등급 정보
    private static final int SLOT_TOOL_ENCHANT = 15; // 도구 인챈트
    
    private final JavaPlugin plugin;
    
    // 열려 있는 GUI 추적 (UUID → JobType)
    private final Map<UUID, JobType> openGuis = new HashMap<>();
    
    // 의존성
    private GradeBonusConfig gradeBonusConfig;
    private Map<JobType, AbstractJobGradeService> gradeServices = new HashMap<>();
    private MiningEfficiencyEnchant miningEfficiencyEnchant;
    
    public PromoteMainGui(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    // ===== Setter =====
    
    public void setGradeBonusConfig(GradeBonusConfig config) {
        this.gradeBonusConfig = config;
    }
    
    public void registerGradeService(JobType jobType, AbstractJobGradeService service) {
        gradeServices.put(jobType, service);
    }
    
    public void setMiningEfficiencyEnchant(MiningEfficiencyEnchant enchant) {
        this.miningEfficiencyEnchant = enchant;
    }
    
    // ===== GUI 열기 =====
    
    /**
     * 메인 GUI 열기
     */
    public void open(Player player, JobType jobType) {
        AbstractJobGradeService gradeService = gradeServices.get(jobType);
        if (gradeService == null) {
            player.sendMessage("§c승급 서비스를 사용할 수 없습니다.");
            return;
        }
        
        String title = GUI_TITLE_PREFIX + jobType.getDisplayName();
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, Component.text(title));
        
        JobGrade currentGrade = gradeService.getGrade(player);
        
        // 배경 채우기
        ItemStack background = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, background);
        }
        
        // 1. 승급하기 버튼
        gui.setItem(SLOT_PROMOTE, createPromoteButton(player, jobType, currentGrade, gradeService));
        
        // 2. 등급 정보 버튼
        gui.setItem(SLOT_GRADE_INFO, createGradeInfoButton(player, jobType, currentGrade));
        
        // 3. 도구 인챈트 버튼 (광부 전용)
        if (jobType == JobType.MINER) {
            gui.setItem(SLOT_TOOL_ENCHANT, createEnchantButton(player, currentGrade));
        } else {
            gui.setItem(SLOT_TOOL_ENCHANT, createItem(Material.BARRIER, "§7도구 인챈트", "§8이 직업에서는 사용 불가"));
        }
        
        openGuis.put(player.getUniqueId(), jobType);
        player.openInventory(gui);
    }
    
    // ===== 버튼 생성 =====
    
    private ItemStack createPromoteButton(Player player, JobType jobType, JobGrade currentGrade, 
                                          AbstractJobGradeService gradeService) {
        JobGrade nextGrade = currentGrade.next();
        
        if (nextGrade == null || currentGrade.isMaxGrade()) {
            return createItem(Material.NETHER_STAR, "§6승급하기", "§a이미 최고 등급입니다!", "§7현재: " + currentGrade.getDisplayName());
        }
        
        PromotionCheckResult checkResult = gradeService.canPromote(player);
        boolean canPromote = checkResult.canPromote();
        String requirementsStr = checkResult.failureReason();
        
        List<String> lore = new ArrayList<>();
        lore.add("§7현재 등급: §f" + currentGrade.getDisplayName());
        lore.add("§7다음 등급: §e" + nextGrade.getDisplayName());
        lore.add("");
        lore.add("§7필요 조건:");
        if (requirementsStr != null && !requirementsStr.isEmpty()) {
            for (String line : requirementsStr.split("\n")) {
                lore.add("§7- " + line);
            }
        }
        lore.add("");
        if (canPromote) {
            lore.add("§a▶ 클릭하여 승급!");
        } else {
            lore.add("§c✗ 조건을 충족하지 않습니다");
        }
        
        Material mat = canPromote ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
        return createItem(mat, "§6승급하기", lore.toArray(new String[0]));
    }
    
    private ItemStack createGradeInfoButton(Player player, JobType jobType, JobGrade currentGrade) {
        List<String> lore = new ArrayList<>();
        lore.add("§7현재 등급: §f" + currentGrade.getDisplayName());
        lore.add("");
        lore.add("§e현재 보너스:");
        
        if (gradeBonusConfig != null) {
            GradeBonusConfig.GradeBonus bonus = gradeBonusConfig.getBonus(jobType, currentGrade);
            
            if (bonus.yieldMulti > 1.0) {
                lore.add("§7- 채집량: §a+" + String.format("%.0f%%", (bonus.yieldMulti - 1) * 100));
            }
            if (bonus.xpMulti > 1.0) {
                lore.add("§7- 경험치: §a+" + String.format("%.0f%%", (bonus.xpMulti - 1) * 100));
            }
            
            switch (jobType) {
                case MINER -> {
                    if (bonus.miningEfficiency > 0) {
                        lore.add("§7- 채굴 효율: §b" + bonus.miningEfficiency + "레벨");
                    }
                }
                case FARMER -> {
                    if (bonus.primeChance > 0) {
                        lore.add("§7- 프라임 확률: §a+" + String.format("%.0f%%", bonus.primeChance * 100));
                    }
                    if (bonus.trophyChance > 0) {
                        lore.add("§7- 트로피 확률: §6+" + String.format("%.0f%%", bonus.trophyChance * 100));
                    }
                }
                case FISHER -> {
                    if (bonus.lureBonus > 0) {
                        lore.add("§7- Lure 보너스: §b+" + bonus.lureBonus);
                    }
                    if (bonus.rareChanceBonus > 0) {
                        lore.add("§7- 희귀도 보너스: §9+" + String.format("%.0f%%", bonus.rareChanceBonus * 100));
                    }
                }
            }
        }
        
        lore.add("");
        lore.add("§7클릭하여 상세 정보 확인");
        
        return createItem(Material.BOOK, "§b등급 정보", lore.toArray(new String[0]));
    }
    
    private ItemStack createEnchantButton(Player player, JobGrade currentGrade) {
        int maxLevel = 0;
        if (gradeBonusConfig != null) {
            maxLevel = gradeBonusConfig.getMiningEfficiency(currentGrade);
        }
        
        List<String> lore = new ArrayList<>();
        lore.add("§7채굴 효율 인챈트를 도구에 적용합니다.");
        lore.add("");
        
        if (maxLevel > 0) {
            lore.add("§a사용 가능: 레벨 " + maxLevel + "까지");
            lore.add("");
            lore.add("§e클릭하여 인챈트 적용!");
        } else {
            lore.add("§c2차 승급 이상 필요");
        }
        
        Material mat = maxLevel > 0 ? Material.ENCHANTED_BOOK : Material.BOOK;
        return createItem(mat, "§d도구 인챈트", lore.toArray(new String[0]));
    }
    
    // ===== 클릭 핸들러 =====
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        JobType jobType = openGuis.get(player.getUniqueId());
        if (jobType == null) return;
        
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE_PREFIX)) return;
        
        // 모든 클릭 취소 (아이템 복사 방지)
        event.setCancelled(true);
        
        // 커서에 아이템이 있으면 반환 (드래그 복사 방지)
        if (event.getCursor() != null && !event.getCursor().getType().isAir()) {
            return;
        }
        
        // 플레이어 인벤토리 클릭 시 무시 (추가 복사 방지)
        if (event.getClickedInventory() == player.getInventory()) {
            return;
        }
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;
        
        AbstractJobGradeService gradeService = gradeServices.get(jobType);
        if (gradeService == null) return;
        
        switch (slot) {
            case SLOT_PROMOTE -> handlePromote(player, jobType, gradeService);
            case SLOT_GRADE_INFO -> handleGradeInfo(player, jobType);
            case SLOT_TOOL_ENCHANT -> handleEnchant(player, jobType);
        }
    }
    
    private void handlePromote(Player player, JobType jobType, AbstractJobGradeService gradeService) {
        JobGrade currentGrade = gradeService.getGrade(player);
        
        if (currentGrade.isMaxGrade()) {
            player.sendMessage("§a이미 최고 등급입니다!");
            return;
        }
        
        PromotionCheckResult checkResult = gradeService.canPromote(player);
        if (!checkResult.canPromote()) {
            player.sendMessage("§c승급 조건을 충족하지 않습니다.");
            if (checkResult.failureReason() != null) {
                player.sendMessage(checkResult.failureReason());
            }
            return;
        }
        
        // 승급 진행
        PromotionResult result = gradeService.promote(player);
        
        if (result.success()) {
            JobGrade newGrade = gradeService.getGrade(player);
            player.sendMessage("§a§l축하합니다! §f" + jobType.getDisplayName() + " §a" + 
                    newGrade.getDisplayName() + "§a로 승급했습니다!");
            player.closeInventory();
        } else {
            player.sendMessage("§c승급에 실패했습니다.");
            if (result.message() != null) {
                player.sendMessage(result.message());
            }
        }
    }
    
    private void handleGradeInfo(Player player, JobType jobType) {
        // TODO: 상세 정보 GUI 열기 (현재는 메시지로 대체)
        player.closeInventory();
        
        AbstractJobGradeService gradeService = gradeServices.get(jobType);
        if (gradeService == null) return;
        
        JobGrade currentGrade = gradeService.getGrade(player);
        
        player.sendMessage("§6§l=== " + jobType.getDisplayName() + " 등급 정보 ===");
        player.sendMessage("§7현재 등급: §f" + currentGrade.getDisplayName());
        
        if (gradeBonusConfig != null) {
            GradeBonusConfig.GradeBonus bonus = gradeBonusConfig.getBonus(jobType, currentGrade);
            player.sendMessage("");
            player.sendMessage("§e현재 보너스:");
            player.sendMessage(bonus.toString());
        }
    }
    
    private void handleEnchant(Player player, JobType jobType) {
        if (jobType != JobType.MINER) {
            player.sendMessage("§c이 직업에서는 도구 인챈트를 사용할 수 없습니다.");
            return;
        }
        
        if (miningEfficiencyEnchant == null) {
            player.sendMessage("§c인챈트 시스템이 비활성화되어 있습니다.");
            return;
        }
        
        int maxLevel = miningEfficiencyEnchant.getMaxAvailableLevel(player);
        if (maxLevel <= 0) {
            player.sendMessage("§c2차 승급 이상이 필요합니다.");
            return;
        }
        
        // 손에 든 도구에 인챈트 적용
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        if (tool.getType().isAir() || !MiningEfficiencyEnchant.canApplyTo(tool.getType())) {
            player.sendMessage("§c곡괭이 또는 삽을 손에 들고 다시 시도하세요.");
            return;
        }
        
        int currentLevel = miningEfficiencyEnchant.getEnchantLevel(tool);
        if (currentLevel >= maxLevel) {
            player.sendMessage("§c이미 최대 레벨의 채굴 효율이 적용되어 있습니다. (현재: " + currentLevel + "레벨)");
            return;
        }
        
        // 인챈트 적용
        boolean success = miningEfficiencyEnchant.applyEnchant(tool, maxLevel);
        
        if (success) {
            player.sendMessage("§a채굴 효율 " + maxLevel + "레벨이 적용되었습니다!");
            player.closeInventory();
        } else {
            player.sendMessage("§c인챈트 적용에 실패했습니다.");
        }
    }
    
    // ===== 유틸리티 =====
    
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        
        if (lore.length > 0) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreComponents);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * GUI 닫힘 이벤트 처리
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        UUID uuid = player.getUniqueId();
        if (!openGuis.containsKey(uuid)) return;
        
        String title = event.getView().getTitle();
        if (title.startsWith(GUI_TITLE_PREFIX)) {
            openGuis.remove(uuid);
        }
    }
    
    /**
     * GUI 닫힘 처리 (외부에서 호출)
     */
    public void handleClose(Player player) {
        openGuis.remove(player.getUniqueId());
    }
}
