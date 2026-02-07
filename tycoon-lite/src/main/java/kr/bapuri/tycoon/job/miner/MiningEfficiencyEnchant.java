package kr.bapuri.tycoon.job.miner;

import kr.bapuri.tycoon.job.JobGrade;
import kr.bapuri.tycoon.job.common.GradeBonusConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * MiningEfficiencyEnchant - 채굴 효율 특수 인챈트
 * 
 * [Phase 승급효과] 광부 전용 특수 인챈트
 * 
 * 특징:
 * - 곡괭이, 삽에만 적용 가능
 * - 승급한 광부만 효과를 받음 (미승급자: 로어 표시, 효과 없음)
 * - NPC GUI를 통해서만 적용 가능
 * - Haste 포션 효과로 채굴 속도 증가
 * 
 * 레벨별 효과:
 * - Lv.1: Haste I (10% 채굴 속도 증가)
 * - Lv.2: Haste II (20% 채굴 속도 증가)
 * - Lv.3: Haste III (30% 채굴 속도 증가)
 */
public class MiningEfficiencyEnchant implements Listener {
    
    private final JavaPlugin plugin;
    private final NamespacedKey enchantKey;
    
    // 의존성 (setter로 주입)
    private GradeBonusConfig gradeBonusConfig;
    private MinerGradeService gradeService;
    private MinerExpService expService;
    
    // 적용 가능한 도구
    private static final Set<Material> APPLICABLE_TOOLS = EnumSet.of(
        // 곡괭이
        Material.WOODEN_PICKAXE,
        Material.STONE_PICKAXE,
        Material.IRON_PICKAXE,
        Material.GOLDEN_PICKAXE,
        Material.DIAMOND_PICKAXE,
        Material.NETHERITE_PICKAXE,
        // 삽
        Material.WOODEN_SHOVEL,
        Material.STONE_SHOVEL,
        Material.IRON_SHOVEL,
        Material.GOLDEN_SHOVEL,
        Material.DIAMOND_SHOVEL,
        Material.NETHERITE_SHOVEL
    );
    
    // 로어 색상
    private static final NamedTextColor ENCHANT_COLOR = NamedTextColor.LIGHT_PURPLE;
    private static final NamedTextColor INACTIVE_COLOR = NamedTextColor.GRAY;
    
    public MiningEfficiencyEnchant(JavaPlugin plugin) {
        this.plugin = plugin;
        this.enchantKey = new NamespacedKey(plugin, "mining_efficiency");
    }
    
    // ===== Setter =====
    
    public void setGradeBonusConfig(GradeBonusConfig config) {
        this.gradeBonusConfig = config;
    }
    
    public void setGradeService(MinerGradeService service) {
        this.gradeService = service;
    }
    
    public void setExpService(MinerExpService service) {
        this.expService = service;
    }
    
    // ===== 적용/제거 =====
    
    /**
     * 아이템에 채굴 효율 인챈트 적용
     * 
     * @param item 대상 아이템
     * @param level 인챈트 레벨 (1~3)
     * @return 성공 여부
     */
    public boolean applyEnchant(ItemStack item, int level) {
        if (item == null || item.getType().isAir()) return false;
        if (!canApplyTo(item.getType())) return false;
        if (level < 1 || level > 3) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        // PDC에 레벨 저장
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(enchantKey, PersistentDataType.INTEGER, level);
        
        // 로어 업데이트
        updateLore(meta, level);
        
        item.setItemMeta(meta);
        return true;
    }
    
    /**
     * 아이템에서 채굴 효율 인챈트 제거
     */
    public boolean removeEnchant(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(enchantKey, PersistentDataType.INTEGER)) return false;
        
        pdc.remove(enchantKey);
        removeLore(meta);
        
        item.setItemMeta(meta);
        return true;
    }
    
    /**
     * 아이템의 채굴 효율 레벨 조회
     * 
     * @return 레벨 (없으면 0)
     */
    public int getEnchantLevel(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.getOrDefault(enchantKey, PersistentDataType.INTEGER, 0);
    }
    
    /**
     * 아이템에 적용 가능한지 확인
     */
    public static boolean canApplyTo(Material material) {
        return APPLICABLE_TOOLS.contains(material);
    }
    
    // ===== 로어 관리 =====
    
    private static final String LORE_PREFIX = "§d채굴 효율 ";
    
    private void updateLore(ItemMeta meta, int level) {
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);
        
        // 기존 채굴 효율 로어 제거
        lore.removeIf(c -> {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(c);
            return plain.contains("채굴 효율");
        });
        
        // 새 로어 추가 (첫 번째 위치)
        String levelStr = toRomanNumeral(level);
        Component loreComponent = Component.text("채굴 효율 " + levelStr)
                .color(ENCHANT_COLOR)
                .decoration(TextDecoration.ITALIC, false);
        lore.add(0, loreComponent);
        
        meta.lore(lore);
    }
    
    private void removeLore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        if (lore == null) return;
        
        lore = new ArrayList<>(lore);
        lore.removeIf(c -> {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(c);
            return plain.contains("채굴 효율");
        });
        
        meta.lore(lore.isEmpty() ? null : lore);
    }
    
    private String toRomanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(level);
        };
    }
    
    // ===== 이벤트 핸들러 =====
    
    /**
     * 블록 채굴 시작 시 Haste 효과 적용
     * 
     * 조건: 
     * - 채굴 효율 인챈트가 있는 도구 사용
     * - 광부 직업이며 해당 등급 이상일 때만 효과 적용
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (gradeService == null || expService == null) return;
        
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        int enchantLevel = getEnchantLevel(tool);
        if (enchantLevel <= 0) return;
        
        // 광부 직업 확인
        if (!expService.hasJob(player)) return;
        
        // 등급 확인: 인챈트 레벨에 해당하는 등급 이상이어야 효과 적용
        JobGrade playerGrade = gradeService.getGrade(player);
        int requiredGradeValue = enchantLevel + 1; // Lv.1 → GRADE_2 필요
        JobGrade requiredGrade = JobGrade.fromValue(requiredGradeValue);
        
        if (requiredGrade == null || playerGrade.getValue() < requiredGrade.getValue()) {
            // 등급 미달: 효과 미적용 (로어는 표시됨)
            return;
        }
        
        // Haste(FAST_DIGGING) 효과 적용 (짧은 시간, 채굴 중 갱신)
        // 인챈트 레벨 = Haste 레벨
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.FAST_DIGGING,
            40, // 2초 (채굴 중 갱신됨)
            enchantLevel - 1, // Haste는 0부터 시작 (0 = Haste I)
            true, // ambient
            false, // particles
            true // icon
        ));
    }
    
    // ===== 유틸리티 =====
    
    /**
     * 플레이어가 받을 수 있는 최대 인챈트 레벨 조회
     */
    public int getMaxAvailableLevel(UUID uuid) {
        if (gradeBonusConfig == null || gradeService == null) return 0;
        
        JobGrade grade = gradeService.getGrade(uuid);
        return gradeBonusConfig.getMiningEfficiency(grade);
    }
    
    /**
     * 플레이어가 받을 수 있는 최대 인챈트 레벨 조회
     */
    public int getMaxAvailableLevel(Player player) {
        return getMaxAvailableLevel(player.getUniqueId());
    }
}
