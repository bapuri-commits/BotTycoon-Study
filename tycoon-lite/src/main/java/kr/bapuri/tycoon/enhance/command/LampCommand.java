package kr.bapuri.tycoon.enhance.command;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.common.EnhanceLoreBuilder;
import kr.bapuri.tycoon.enhance.lamp.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.bukkit.Material;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LampCommand - 램프 명령어
 * 
 * Phase 6 LITE: 레거시 버전 이식
 * 
 * v2.5 다중 슬롯 시스템:
 * /lamp info - 손에 든 아이템의 램프 슬롯 상세 정보
 * /lamp effect <effectId> - 효과 정보 조회
 * /lamp list - 램프 타입 목록 (관리자)
 * /lamp effects - 램프 효과 목록 (관리자)
 * /lamp give <player> <lampType> [amount] - 램프 지급 (관리자)
 * /lamp apply <effectId> [value1] [value2] - 손에 든 아이템에 효과 적용 (관리자)
 * /lamp remove <slotIndex> - 손에 든 아이템에서 효과 제거 (관리자)
 * 
 * v2.6: 탭 완성 및 입력에 한글 displayName 지원
 */
public class LampCommand implements CommandExecutor, TabCompleter {

    private final LampService lampService;
    private final LampRegistry registry;

    private static final List<String> SUB_COMMANDS = Arrays.asList("info", "effect", "list", "effects", "give", "apply", "remove");
    
    // 한글 이름 -> ID 매핑 (탭 완성 및 입력 지원용)
    private static final Map<String, String> KOREAN_TO_ID_MAP = new HashMap<>();
    
    static {
        for (LampEffect effect : LampEffect.values()) {
            KOREAN_TO_ID_MAP.put(effect.getDisplayName(), effect.getId());
        }
    }

    public LampCommand(LampService lampService, LampRegistry registry) {
        this.lampService = lampService;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> handleItemInfo(sender);        // v2.5: 손에 든 아이템 정보
            case "effect" -> handleEffectInfo(sender, args); // 효과 ID 조회
            case "list" -> handleList(sender);
            case "effects" -> handleEffects(sender);
            case "give" -> handleGive(sender, args);
            case "apply" -> handleApply(sender, args);    // v2.6: 관리자 직접 적용
            case "remove" -> handleRemove(sender, args);  // v2.6: 관리자 직접 제거
            default -> showUsage(sender);
        }

        return true;
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("tycoon.admin.lamp")) {
            sender.sendMessage("§c관리자만 사용 가능한 명령어입니다.");
            sender.sendMessage("§7램프 정보는 서버 공지/문서를 참고해주세요.");
            return;
        }
        
        sender.sendMessage("§d§l===== 램프 타입 목록 (관리자 전용) =====");
        sender.sendMessage("");

        for (LampType type : LampType.values()) {
            long price = registry.getLampPrice(type);
            sender.sendMessage(type.getDisplayName());
            sender.sendMessage("  §7ID: §f" + type.getId());
            sender.sendMessage("  §7적용: §f" + type.getApplicableDescription());
            sender.sendMessage("  §7가격: §e" + price + "원");
            sender.sendMessage("");
        }
    }

    private void handleEffects(CommandSender sender) {
        if (!sender.hasPermission("tycoon.admin.lamp")) {
            sender.sendMessage("§c관리자만 사용 가능한 명령어입니다.");
            sender.sendMessage("§7램프 효과 정보는 서버 공지/문서를 참고해주세요.");
            return;
        }
        
        sender.sendMessage("§d§l===== 램프 효과 목록 (관리자 전용) =====");

        for (LampType type : LampType.values()) {
            sender.sendMessage("");
            sender.sendMessage("§e§l[" + type.getDisplayName() + "§e§l 효과]");
            
            List<LampEffect> effects = registry.getAvailableEffects(type);
            for (LampEffect effect : effects) {
                if (effect.getRequiredLampType() == type) {
                    try {
                        sender.sendMessage("  " + effect.getRarity().getDisplayName() + " " + effect.getDisplayName());
                        sender.sendMessage("    §7" + effect.getDescription());
                    } catch (Exception e) {
                        sender.sendMessage("  §c" + effect.getId() + " - 설명 로드 실패");
                    }
                }
            }
        }

        sender.sendMessage("");
        sender.sendMessage("§e§l[만능 효과] §7(모든 램프에 등장)");
        for (LampEffect effect : LampEffect.values()) {
            if (effect.getRequiredLampType() == LampType.UNIVERSAL_LAMP && !effect.isDisabled()) {
                try {
                    sender.sendMessage("  " + effect.getRarity().getDisplayName() + " " + effect.getDisplayName());
                } catch (Exception e) {
                    sender.sendMessage("  §c" + effect.getId() + " - 로드 실패");
                }
            }
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tycoon.admin.lamp")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c사용법: /lamp give <플레이어> <램프타입> [수량]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
            return;
        }

        LampType lampType = LampType.fromId(args[2]);
        if (lampType == null) {
            sender.sendMessage("§c존재하지 않는 램프 타입입니다: " + args[2]);
            sender.sendMessage("§7사용 가능: " + Arrays.stream(LampType.values())
                    .map(LampType::getId).collect(Collectors.joining(", ")));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c수량은 숫자여야 합니다.");
                return;
            }
        }

        ItemStack lamp = lampService.getItemFactory().createLamps(lampType, amount);
        if (lamp == null) {
            sender.sendMessage("§c램프 생성에 실패했습니다.");
            return;
        }

        target.getInventory().addItem(lamp);
        sender.sendMessage("§a" + target.getName() + "님에게 " + lampType.getDisplayName() + 
                         " §a" + amount + "개를 지급했습니다.");
        target.sendMessage(EnhanceConstants.PREFIX_LAMP + "램프를 받았습니다!");
    }

    /**
     * v2.5: 손에 든 아이템의 램프 슬롯 상세 정보
     */
    private void handleItemInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능한 명령어입니다.");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            sender.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c손에 아이템을 들고 명령어를 사용하세요.");
            return;
        }

        // 램프 적용 가능한 아이템인지 확인
        if (!EnhanceItemUtil.isWeapon(item) && !EnhanceItemUtil.isArmor(item) && !EnhanceItemUtil.isTool(item)) {
            sender.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c이 아이템에는 램프를 적용할 수 없습니다.");
            return;
        }

        // 레거시 마이그레이션
        EnhanceItemUtil.migrateLegacyLampEffect(item);

        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(item);
        int slotCount = EnhanceItemUtil.getLampSlotCount(item);
        int activeCount = EnhanceItemUtil.getActiveLampCount(item);

        sender.sendMessage("");
        sender.sendMessage("§d§l═══════ 램프 슬롯 정보 ═══════");
        sender.sendMessage("");
        sender.sendMessage("§7슬롯 상태: §e" + activeCount + "/" + slotCount + 
                          (slotCount < EnhanceConstants.MAX_LAMP_SLOTS ? " §8(확장 가능)" : " §6(최대)"));
        sender.sendMessage("");

        // 슬롯별 상세 정보
        for (int i = 0; i < slotCount; i++) {
            LampSlotData slot = i < slots.size() ? slots.get(i) : LampSlotData.empty();
            
            if (slot.isEmpty()) {
                sender.sendMessage("§8[슬롯 " + (i + 1) + "] §7빈 슬롯");
            } else {
                LampEffect effect = slot.getEffect();
                if (effect != null) {
                    sender.sendMessage("§e[슬롯 " + (i + 1) + "] " + effect.getRarity().getColorCode() + 
                                      "[" + effect.getRarity().getDisplayName() + "] §r" + effect.getDisplayName());
                    sender.sendMessage("   §7" + effect.getDescription(slot.getValue1(), (int) slot.getValue2()));
                    sender.sendMessage("   §8수치: v1=" + String.format("%.2f", slot.getValue1()) + 
                                      (slot.getValue2() > 0 ? ", v2=" + slot.getValue2() : ""));
                } else {
                    sender.sendMessage("§c[슬롯 " + (i + 1) + "] 알 수 없는 효과: " + slot.getEffectId());
                }
            }
            sender.sendMessage("");
        }

        // 잠긴 슬롯 표시
        for (int i = slotCount; i < EnhanceConstants.MAX_LAMP_SLOTS; i++) {
            long cost = switch (i + 1) {
                case 2 -> 25000L;
                case 3 -> 75000L;
                case 4 -> 250000L;
                default -> -1L;
            };
            sender.sendMessage("§8[슬롯 " + (i + 1) + "] 잠김 §7(확장 비용: " + String.format("%,d", cost) + " BD)");
        }

        sender.sendMessage("");
        sender.sendMessage("§7§o램프 제거: 제거 티켓 사용");
        sender.sendMessage("§7§o슬롯 확장: 확장권 + BD 필요");
        sender.sendMessage("§d§l═══════════════════════════");
    }

    /**
     * 한글 이름 또는 ID를 실제 ID로 변환
     */
    private String resolveEffectId(String input) {
        // 한글 이름인 경우 ID로 변환
        String resolved = KOREAN_TO_ID_MAP.get(input);
        if (resolved != null) {
            return resolved;
        }
        // 이미 ID인 경우 그대로 반환
        return input.toLowerCase();
    }

    /**
     * 효과 ID로 정보 조회
     */
    private void handleEffectInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c사용법: /lamp effect <효과ID>");
            return;
        }

        String effectId = resolveEffectId(args[1]);
        LampEffect effect = LampEffect.fromId(effectId);

        if (effect == null) {
            sender.sendMessage("§c존재하지 않는 효과입니다: " + args[1]);
            return;
        }

        sender.sendMessage("§d§l===== " + effect.getDisplayName() + " =====");
        sender.sendMessage("§7ID: §f" + effect.getId());
        sender.sendMessage("§7희귀도: " + effect.getRarity().getDisplayName());
        sender.sendMessage("§7설명: §f" + effect.getDescription());
        sender.sendMessage("§7필요 램프: §f" + effect.getRequiredLampType().getDisplayName());
        sender.sendMessage("§7수치 범위: §f" + effect.getMinValue1() + " ~ " + effect.getMaxValue1());
        if (effect.getMinValue2() > 0 || effect.getMaxValue2() > 0) {
            sender.sendMessage("§7보조 수치: §f" + effect.getMinValue2() + " ~ " + effect.getMaxValue2());
        }
        sender.sendMessage("§7활성화: " + (!effect.isDisabled() ? "§a예" : "§c아니오"));
    }
    
    /**
     * v2.6: 관리자가 손에 든 아이템에 램프 효과 직접 적용
     * /lamp apply <effectId> [value1] [value2]
     */
    private void handleApply(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tycoon.admin.lamp")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c사용법: /lamp apply <효과ID> [value1] [value2]");
            sender.sendMessage("§7value 미지정 시 랜덤 롤링");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            sender.sendMessage("§c손에 아이템을 들고 있어야 합니다.");
            return;
        }

        // 램프 적용 가능한 아이템인지 확인
        if (!EnhanceItemUtil.isWeapon(item) && !EnhanceItemUtil.isArmor(item) && !EnhanceItemUtil.isTool(item)) {
            sender.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c이 아이템에는 램프를 적용할 수 없습니다.");
            return;
        }

        String effectId = resolveEffectId(args[1]);
        LampEffect effect = LampEffect.fromId(effectId);

        if (effect == null) {
            sender.sendMessage("§c존재하지 않는 효과입니다: " + args[1]);
            return;
        }

        if (effect.isDisabled()) {
            sender.sendMessage("§c비활성화된 효과입니다: " + effect.getDisplayName());
            return;
        }

        // 수치 파싱 (미지정 시 랜덤)
        double value1;
        double value2;

        if (args.length >= 3) {
            try {
                value1 = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cvalue1은 숫자여야 합니다.");
                return;
            }
        } else {
            // 랜덤 롤링
            value1 = effect.getMinValue1() + Math.random() * (effect.getMaxValue1() - effect.getMinValue1());
        }

        if (args.length >= 4) {
            try {
                value2 = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cvalue2는 숫자여야 합니다.");
                return;
            }
        } else {
            // 랜덤 롤링
            value2 = effect.getMinValue2() + Math.random() * (effect.getMaxValue2() - effect.getMinValue2());
        }

        // 슬롯에 추가
        LampSlotData newSlot = new LampSlotData(effect.getId(), value1, (int) value2);
        int slotIndex = EnhanceItemUtil.addLampSlot(item, newSlot);

        if (slotIndex < 0) {
            sender.sendMessage("§c램프 슬롯이 가득 찼습니다! /lamp info로 확인하세요.");
            return;
        }

        // Lore 업데이트
        EnhanceLoreBuilder.refreshLore(item);

        sender.sendMessage("§a램프 효과 '" + effect.getDisplayName() + "'를 슬롯 " + (slotIndex + 1) + "에 적용했습니다.");
        sender.sendMessage("§7수치: value1=" + String.format("%.2f", value1) + 
                          (value2 > 0 ? ", value2=" + String.format("%.0f", value2) : ""));
    }

    /**
     * v2.6: 관리자가 손에 든 아이템에서 램프 효과 제거
     * /lamp remove <slotIndex>
     */
    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tycoon.admin.lamp")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용 가능합니다.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c사용법: /lamp remove <슬롯번호>");
            sender.sendMessage("§7슬롯 번호는 1부터 시작합니다.");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            sender.sendMessage("§c손에 아이템을 들고 있어야 합니다.");
            return;
        }

        int slotIndex;
        try {
            slotIndex = Integer.parseInt(args[1]) - 1; // 사용자 입력은 1부터, 내부는 0부터
        } catch (NumberFormatException e) {
            sender.sendMessage("§c슬롯 번호는 숫자여야 합니다.");
            return;
        }

        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(item);
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            sender.sendMessage("§c유효하지 않은 슬롯 번호입니다. (1~" + slots.size() + ")");
            return;
        }

        LampSlotData slot = slots.get(slotIndex);
        if (slot.isEmpty()) {
            sender.sendMessage("§c해당 슬롯은 이미 비어있습니다.");
            return;
        }

        String removedEffectName = slot.getEffect() != null ? slot.getEffect().getDisplayName() : slot.getEffectId();
        
        // 슬롯 비우기
        boolean removed = EnhanceItemUtil.removeLampSlot(item, slotIndex);
        if (removed) {
            EnhanceLoreBuilder.refreshLore(item);
            sender.sendMessage("§a슬롯 " + (slotIndex + 1) + "의 '" + removedEffectName + "' 효과를 제거했습니다.");
        } else {
            sender.sendMessage("§c효과 제거에 실패했습니다.");
        }
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage("§d§l[램프 명령어]");
        sender.sendMessage("§e/lamp info §7- 손에 든 아이템의 램프 상세 정보");
        sender.sendMessage("§e/lamp effect <효과ID> §7- 효과 상세 정보");
        if (sender.hasPermission("tycoon.admin.lamp")) {
            sender.sendMessage("§e/lamp list §7- 램프 타입 목록");
            sender.sendMessage("§e/lamp effects §7- 램프 효과 목록");
            sender.sendMessage("§e/lamp give <플레이어> <램프타입> [수량] §7- 램프 지급");
            sender.sendMessage("§e/lamp apply <효과ID> [v1] [v2] §7- 손에 든 아이템에 적용");
            sender.sendMessage("§e/lamp remove <슬롯번호> §7- 손에 든 아이템에서 제거");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUB_COMMANDS.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("effect") || sub.equals("apply")) {
                return getEffectSuggestions(args[1]);
            }
            if (sub.equals("remove")) {
                // 슬롯 번호 제안 (1~4)
                return Arrays.asList("1", "2", "3", "4").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Arrays.stream(LampType.values())
                    .map(LampType::getId)
                    .filter(id -> id.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
    
    /**
     * 램프 효과 ID와 한글 이름 모두 포함한 탭 완성 목록
     */
    private List<String> getEffectSuggestions(String input) {
        String lowerInput = input.toLowerCase();
        List<String> suggestions = new ArrayList<>();
        
        for (LampEffect effect : LampEffect.values()) {
            if (effect.isDisabled()) continue;
            
            String id = effect.getId();
            String displayName = effect.getDisplayName();
            
            // ID로 검색
            if (id.startsWith(lowerInput)) {
                suggestions.add(id);
            }
            // 한글 이름으로 검색
            if (displayName.contains(input)) {
                suggestions.add(displayName);
            }
        }
        
        return suggestions;
    }
}
