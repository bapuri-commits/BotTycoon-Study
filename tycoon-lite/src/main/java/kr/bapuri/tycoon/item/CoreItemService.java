package kr.bapuri.tycoon.item;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * CoreItemService - 핵심 아이템 관리 서비스 (LITE 버전)
 * 
 * <h2>기능</h2>
 * <ul>
 *   <li>createItem(): 인증된 핵심 아이템 생성</li>
 *   <li>giveItem(): 플레이어에게 아이템 지급</li>
 *   <li>hasItem(): 플레이어가 특정 아이템을 보유하는지 확인</li>
 * </ul>
 * 
 * <h2>레거시 대비 간소화</h2>
 * <ul>
 *   <li>ConsumedItemRegistry 제거 (단순화)</li>
 *   <li>HMAC 서명 제거 → PDC 기반 인증</li>
 *   <li>설정 파일 의존성 제거 → 하드코딩 기본값</li>
 * </ul>
 * 
 * <h2>확장 지점</h2>
 * <p>v1.1에서 필요 시 ConsumedItemRegistry, 쿨다운, 월드 제한 추가</p>
 */
public class CoreItemService {
    
    private final Plugin plugin;
    private final Logger logger;
    private final CoreItemAuthenticator authenticator;
    
    // 아이템별 설정 캐시
    private final Map<CoreItemType, ItemSettings> settingsCache = new EnumMap<>(CoreItemType.class);
    
    public CoreItemService(Plugin plugin, CoreItemAuthenticator authenticator) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.authenticator = authenticator;
        
        // 설정 로드 (config + 기본값)
        loadSettings();
        
        logger.info("[CoreItemService] 초기화 완료 (" + settingsCache.size() + "개 아이템 타입)");
    }
    
    // ========== 설정 로드 ==========
    
    /**
     * 설정 로드 (config.yml의 specialItems 섹션 + 기본값)
     */
    private void loadSettings() {
        settingsCache.clear();
        
        org.bukkit.configuration.ConfigurationSection specialItems = 
            ((org.bukkit.plugin.java.JavaPlugin) plugin).getConfig().getConfigurationSection("specialItems");
        
        int configLoaded = 0;
        
        for (CoreItemType type : CoreItemType.values()) {
            ItemSettings settings = null;
            
            // 1. config에서 먼저 시도 (configKey로 검색)
            if (specialItems != null) {
                settings = loadFromConfig(type, specialItems);
                if (settings != null) {
                    configLoaded++;
                }
            }
            
            // 2. config에 없으면 기본값 사용
            if (settings == null) {
                settings = createDefaultSettings(type);
            }
            
            settingsCache.put(type, settings);
        }
        
        logger.info("[CoreItemService] 설정 로드: config=" + configLoaded + ", 기본값=" + (settingsCache.size() - configLoaded));
    }
    
    /**
     * 대체 config 키 반환 (키 이름 불일치 해결)
     */
    private String getAlternativeConfigKey(String configKey) {
        return switch (configKey) {
            case "return_scroll_v2" -> "return_scroll";
            case "dungeon_inventory_save" -> "inventory_protection_ticket"; // 던전용도 범용으로 대체
            case "universal_inventory_save" -> "inventory_protection_ticket";
            default -> null;
        };
    }
    
    /**
     * config.yml에서 아이템 설정 로드
     */
    private ItemSettings loadFromConfig(CoreItemType type, org.bukkit.configuration.ConfigurationSection specialItems) {
        String configKey = type.getConfigKey();
        
        // configKey와 일치하는 섹션 찾기
        org.bukkit.configuration.ConfigurationSection section = specialItems.getConfigurationSection(configKey);
        
        // 대체 키 시도
        if (section == null) {
            String altKey = getAlternativeConfigKey(configKey);
            if (altKey != null) {
                section = specialItems.getConfigurationSection(altKey);
            }
        }
        
        if (section == null) {
            return null;
        }
        
        ItemSettings settings = new ItemSettings();
        
        // Material
        String materialName = section.getString("material");
        if (materialName != null) {
            try {
                settings.material = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[CoreItemService] 잘못된 material: " + materialName + " (" + configKey + ")");
                return null;
            }
        } else {
            return null; // material 필수
        }
        
        // Display Name (색상 코드 변환)
        String displayName = section.getString("displayName");
        if (displayName != null) {
            settings.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        } else {
            settings.displayName = type.getDisplayName();
        }
        
        // Lore (색상 코드 변환)
        java.util.List<String> loreList = section.getStringList("lore");
        if (!loreList.isEmpty()) {
            settings.lore = loreList.stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(java.util.stream.Collectors.toList());
        } else {
            settings.lore = List.of(ChatColor.DARK_GRAY + "[인증된 아이템]");
        }
        
        // consumeOnUse
        settings.consumeOnUse = section.getBoolean("consumeOnUse", true);
        
        // cooldownSeconds
        settings.cooldownSeconds = section.getInt("cooldownSeconds", 0);
        
        // validWorlds
        settings.validWorlds = section.getStringList("validWorlds");
        
        // customModelData (기본값 유지)
        settings.customModelData = createDefaultSettings(type).customModelData;
        
        return settings;
    }
    
    /**
     * 설정 리로드
     */
    public void reload() {
        loadSettings();
        logger.info("[CoreItemService] 설정 리로드 완료");
    }
    
    private ItemSettings createDefaultSettings(CoreItemType type) {
        ItemSettings settings = new ItemSettings();
        
        switch (type) {
            // ========== PROTECTION ==========
            // [Phase 8 정리] DUNGEON_INVENTORY_SAVE, UPGRADE_*_PROTECTION 제거
            
            case UNIVERSAL_INVENTORY_SAVE:
                settings.material = Material.PAPER;  // 리소스팩 연동을 위해 PAPER로 통일
                settings.displayName = ChatColor.AQUA + "범용 인벤토리 보호권";
                settings.lore = List.of(
                    ChatColor.GRAY + "어디서든 사망 시 인벤토리 보호",
                    ChatColor.DARK_GRAY + "[인증된 아이템]"
                );
                settings.customModelData = 1602;
                break;
                
            // ========== SCROLL ==========
            case RETURN_SCROLL:
                settings.material = Material.PAPER;
                settings.displayName = ChatColor.GREEN + "귀환 주문서";
                settings.lore = List.of(
                    ChatColor.GRAY + "우클릭으로 마을로 귀환",
                    ChatColor.DARK_GRAY + "[인증된 아이템]"
                );
                settings.customModelData = 1606;
                break;
                
            case TELEPORT_SCROLL:
                settings.material = Material.PAPER;  // 리소스팩 연동을 위해 PAPER로 통일
                settings.displayName = ChatColor.LIGHT_PURPLE + "텔레포트 주문서";
                settings.lore = List.of(
                    ChatColor.GRAY + "우클릭 후 현재 월드에서 좌표 입력",
                    ChatColor.GRAY + "형식: x y z (예: 100 64 -200)",
                    ChatColor.DARK_GRAY + "[인증된 아이템]"
                );
                settings.customModelData = 1607;
                break;
                
            case REBIRTH_MEMORY_SCROLL:
                settings.material = Material.PAPER;  // 리소스팩 연동을 위해 PAPER로 통일
                settings.displayName = ChatColor.DARK_PURPLE + "전생의 기억 주문서";
                settings.lore = List.of(
                    ChatColor.GRAY + "마지막 사망 위치로 텔레포트",
                    ChatColor.DARK_GRAY + "[인증된 아이템]"
                );
                settings.customModelData = 1608;
                break;
                
            // ========== VOUCHER ==========
            case BD_CHECK:
                settings.material = Material.PAPER;
                settings.displayName = ChatColor.GOLD + "수표";
                settings.lore = List.of(
                    ChatColor.GRAY + "우클릭으로 BD 수령",
                    ChatColor.DARK_GRAY + "[인증된 아이템]"
                );
                settings.customModelData = 1600;
                break;
                
            case BOTTCOIN_VOUCHER:
                settings.material = Material.PAPER;
                settings.displayName = ChatColor.AQUA + "BottCoin 바우처";
                settings.lore = List.of(
                    ChatColor.GRAY + "우클릭으로 BottCoin 수령",
                    ChatColor.DARK_GRAY + "[인증된 아이템]"
                );
                settings.customModelData = 1601;
                break;
                
            // [Phase 8 정리] DOCUMENT, TICKET 카테고리 제거
                
            // ========== LOTTERY ==========
            case BASIC_ENCHANT_LOTTERY:
                settings.material = Material.PAPER;
                settings.displayName = ChatColor.GREEN + "기본 인챈트 뽑기권";
                settings.lore = List.of(
                    ChatColor.GRAY + "바닐라 인챈트북 랜덤 획득",
                    ChatColor.YELLOW + "Shift + 우클릭으로 사용",
                    ChatColor.DARK_GRAY + "[인증된 아이템]"
                );
                settings.customModelData = 1610;
                break;
                
            case SPECIAL_ENCHANT_LOTTERY:
                settings.material = Material.PAPER;  // 리소스팩 연동을 위해 PAPER로 통일
                settings.displayName = ChatColor.LIGHT_PURPLE + "특수 인챈트 뽑기권";
                settings.lore = List.of(
                    ChatColor.GRAY + "커스텀 인챈트북 랜덤 획득",
                    ChatColor.YELLOW + "Shift + 우클릭으로 사용",
                    ChatColor.DARK_GRAY + "[인증된 아이템]"
                );
                settings.customModelData = 1611;
                break;
                
            // ========== LAMP_TICKET ==========
            case LAMP_SLOT_TICKET:
                settings.material = Material.PAPER;
                settings.displayName = ChatColor.LIGHT_PURPLE + "램프 슬롯 확장권";
                settings.lore = List.of(
                    ChatColor.GRAY + "아이템에 사용하여 램프 슬롯 1개 추가",
                    ChatColor.GRAY + "(최대 4슬롯)",
                    "",
                    ChatColor.YELLOW + "장비를 오프핸드에 들고 우클릭",
                    ChatColor.DARK_GRAY + "[인증된 아이템]"
                );
                settings.customModelData = 2070;
                break;
            // [Phase 8 정리] LAMP_REMOVE_TICKET 제거
        }
        
        return settings;
    }
    
    // ========== 아이템 생성 ==========
    
    /**
     * 인증된 핵심 아이템 생성
     * 
     * @param type 아이템 타입
     * @param amount 수량
     * @return 생성된 아이템 (실패 시 null)
     */
    public ItemStack createItem(CoreItemType type, int amount) {
        ItemSettings settings = settingsCache.get(type);
        if (settings == null || settings.material == null) {
            logger.warning("[CoreItemService] createItem 실패 - 설정 없음: " + type);
            return null;
        }
        
        ItemStack item = new ItemStack(settings.material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            logger.warning("[CoreItemService] createItem 실패 - ItemMeta null: " + type);
            return null;
        }
        
        // 표시명 설정
        if (settings.displayName != null) {
            meta.setDisplayName(settings.displayName);
        }
        
        // Lore 설정
        if (settings.lore != null && !settings.lore.isEmpty()) {
            meta.setLore(settings.lore);
        }
        
        // CustomModelData 설정 (리소스팩 연동)
        if (settings.customModelData > 0) {
            meta.setCustomModelData(settings.customModelData);
        }
        
        item.setItemMeta(meta);
        
        // PDC 서명 추가
        if (!authenticator.signItem(item, type)) {
            logger.warning("[CoreItemService] 아이템 서명 실패: " + type);
            return null;
        }
        
        return item;
    }
    
    /**
     * 인증된 핵심 아이템 생성 (수량 1개)
     */
    public ItemStack createItem(CoreItemType type) {
        return createItem(type, 1);
    }
    
    // ========== 아이템 지급 ==========
    
    /**
     * 플레이어에게 아이템 지급
     * 
     * <p>스택 가능 아이템: 한 번에 생성 (64개 초과 시 분할)</p>
     * <p>스택 불가 아이템: 개별 생성 (각각 고유 ID)</p>
     * 
     * @param player 대상 플레이어
     * @param type 아이템 타입
     * @param amount 수량
     * @return 성공 여부
     */
    public boolean giveItem(Player player, CoreItemType type, int amount) {
        if (amount <= 0) {
            return false;
        }
        
        // VOUCHER는 별도 명령어 사용 (금액 정보 필요)
        if (type.getCategory() == CoreItemType.Category.VOUCHER) {
            logger.warning("[CoreItemService] VOUCHER 타입은 giveItem으로 지급 불가: " + type);
            return false;
        }
        // [Phase 8 정리] TICKET, DUNGEON_INVENTORY_SAVE, TOWN_LAND_DEED 차단 로직 제거 (타입 자체가 삭제됨)
        
        int totalGiven = 0;
        
        if (type.isStackable()) {
            // 스택 가능: 64개 단위로 분할
            int remaining = amount;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, 64);
                ItemStack item = createItem(type, stackSize);
                if (item == null) break;
                
                giveItemToPlayer(player, item);
                totalGiven += stackSize;
                remaining -= stackSize;
            }
        } else {
            // 스택 불가: 개별 생성
            for (int i = 0; i < amount; i++) {
                ItemStack item = createItem(type, 1);
                if (item == null) continue;
                
                giveItemToPlayer(player, item);
                totalGiven++;
            }
        }
        
        if (totalGiven > 0) {
            logger.info(String.format("[CORE_ITEM] GIVE player=%s type=%s amount=%d",
                    player.getName(), type.getConfigKey(), totalGiven));
        }
        
        return totalGiven == amount;
    }
    
    private void giveItemToPlayer(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack dropped : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), dropped);
            }
            player.sendMessage("§e인벤토리가 가득 차 일부 아이템이 바닥에 드롭되었습니다.");
        }
    }
    
    // ========== 유틸리티 ==========
    
    /**
     * 플레이어가 특정 타입의 아이템을 보유하는지 확인
     */
    public boolean hasItem(Player player, CoreItemType type) {
        return findItemOfType(player, type) != null;
    }
    
    /**
     * 플레이어 인벤토리에서 특정 타입의 첫 번째 아이템 찾기
     */
    public ItemStack findItemOfType(Player player, CoreItemType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (authenticator.getItemType(item) == type) {
                return item;
            }
        }
        return null;
    }
    
    /**
     * 인벤토리에서 특정 타입 아이템 1개 소비
     * 
     * @param player 플레이어
     * @param type 아이템 타입
     * @param reason 소비 사유 (로깅용)
     * @return 소비 성공 여부
     */
    public boolean consumeItem(Player player, CoreItemType type, String reason) {
        ItemStack item = findItemOfType(player, type);
        if (item == null) {
            return false;
        }
        
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }
        
        logger.info(String.format("[CORE_ITEM] CONSUME player=%s type=%s reason=%s",
                player.getName(), type.getConfigKey(), reason));
        
        return true;
    }
    
    // ========== 내부 클래스 ==========
    
    /**
     * 아이템 설정
     */
    private static class ItemSettings {
        Material material;
        String displayName;
        List<String> lore = new ArrayList<>();
        int customModelData = 0;
        
        // [Config 연동] 추가 필드
        boolean consumeOnUse = true;
        int cooldownSeconds = 0;
        List<String> validWorlds = new ArrayList<>();
    }
    
    // ========== Getter ==========
    
    public CoreItemAuthenticator getAuthenticator() {
        return authenticator;
    }
}
