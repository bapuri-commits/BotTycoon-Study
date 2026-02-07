package kr.bapuri.tycoon.migration;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ItemMigrationService - 커스텀 아이템 온라인 마이그레이션
 * 
 * <h2>기능</h2>
 * <ul>
 *   <li>플레이어 접속 시 인벤토리 자동 마이그레이션</li>
 *   <li>상자 열 때 내용물 자동 마이그레이션</li>
 *   <li>CustomModelData 값 변환</li>
 *   <li>PDC NamespacedKey 변환 (tycoonlite → tycoon)</li>
 * </ul>
 * 
 * <h2>안전성</h2>
 * <ul>
 *   <li>서버가 NBT를 정상적으로 로드한 후 처리</li>
 *   <li>마인크래프트 API를 통한 안전한 수정</li>
 *   <li>청크 손상 위험 없음</li>
 * </ul>
 */
public class ItemMigrationService implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    
    // CMD 매핑: 구 CMD → 새 CMD
    private final Map<Integer, Integer> cmdMigrationMap = new HashMap<>();
    
    // PDC 키 마이그레이션 필요 여부
    private boolean migratePdcKeys = true;
    
    // 활성화 여부
    private boolean enabled = false;
    
    // 통계
    private int migratedItemCount = 0;
    private int migratedContainerCount = 0;

    public ItemMigrationService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 초기화 - CMD 매핑 설정 및 리스너 등록
     */
    public void initialize() {
        enabled = plugin.getConfig().getBoolean("migration.enabled", false);
        
        if (!enabled) {
            logger.info("[ItemMigration] 비활성화 상태 (migration.enabled=false)");
            return;
        }
        
        // CMD 매핑 로드 (config에서)
        loadCmdMappings();
        
        // PDC 키 마이그레이션 설정
        migratePdcKeys = plugin.getConfig().getBoolean("migration.migrate_pdc_keys", true);
        
        // 리스너 등록
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        logger.info("[ItemMigration] 초기화 완료 - CMD 매핑: " + cmdMigrationMap.size() + "개");
    }

    /**
     * CMD 매핑 로드
     */
    private void loadCmdMappings() {
        // config.yml에서 로드하거나 하드코딩
        // 예: migration.cmd_mappings:
        //       1500: 1600
        //       1501: 1601
        
        var section = plugin.getConfig().getConfigurationSection("migration.cmd_mappings");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int oldCmd = Integer.parseInt(key);
                    int newCmd = section.getInt(key);
                    cmdMigrationMap.put(oldCmd, newCmd);
                } catch (NumberFormatException e) {
                    logger.warning("[ItemMigration] 잘못된 CMD 매핑: " + key);
                }
            }
        }
        
        // 기본 매핑이 없으면 하드코딩된 값 사용
        if (cmdMigrationMap.isEmpty()) {
            setupDefaultMappings();
        }
    }

    /**
     * 기본 CMD 매핑 설정
     * CUSTOM_MODEL_DATA_MIGRATION.md 문서 기반
     */
    private void setupDefaultMappings() {
        // CMD 변경 매핑 (문서 기준)
        cmdMigrationMap.put(1620, 2070);  // LAMP_SLOT_TICKET
        cmdMigrationMap.put(1621, 2071);  // LAMP_REMOVE_TICKET
        
        logger.info("[ItemMigration] 기본 CMD 매핑 로드: 1620→2070, 1621→2071");
    }

    /**
     * 플레이어 접속 시 인벤토리 마이그레이션
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        
        // 비동기로 처리하지 않음 (인벤토리 수정은 메인 스레드에서)
        int migrated = migrateInventory(player.getInventory());
        
        if (migrated > 0) {
            logger.info("[ItemMigration] " + player.getName() + " 인벤토리 마이그레이션: " + migrated + "개 아이템");
            migratedItemCount += migrated;
        }
    }

    /**
     * 상자/컨테이너 열 때 마이그레이션
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!enabled) return;
        if (!(event.getPlayer() instanceof Player)) return;
        
        Inventory inv = event.getInventory();
        
        // 컨테이너인지 확인
        if (inv.getHolder() instanceof Container) {
            int migrated = migrateInventory(inv);
            
            if (migrated > 0) {
                logger.info("[ItemMigration] 컨테이너 마이그레이션: " + migrated + "개 아이템");
                migratedItemCount += migrated;
                migratedContainerCount++;
            }
        }
    }

    /**
     * 인벤토리 내 모든 아이템 마이그레이션
     * 
     * @return 마이그레이션된 아이템 수
     */
    public int migrateInventory(Inventory inventory) {
        int migrated = 0;
        
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            if (migrateItem(item)) {
                migrated++;
            }
        }
        
        return migrated;
    }

    /**
     * 단일 아이템 마이그레이션
     * 
     * @return 마이그레이션 되었으면 true
     */
    public boolean migrateItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        boolean changed = false;
        
        // 1. Material 변경 (구 Material → PAPER)
        // CUSTOM_MODEL_DATA_MIGRATION.md 문서 기준
        if (meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            Material oldMaterial = item.getType();
            Material newMaterial = getMaterialMigration(cmd, oldMaterial);
            
            if (newMaterial != null && newMaterial != oldMaterial) {
                item.setType(newMaterial);
                // setType 후 meta를 다시 가져와야 함
                meta = item.getItemMeta();
                changed = true;
                logger.info("[ItemMigration] Material 변환: " + oldMaterial + " → " + newMaterial + " (CMD: " + cmd + ")");
            }
        }
        
        // 2. CustomModelData 마이그레이션
        if (meta.hasCustomModelData()) {
            int currentCmd = meta.getCustomModelData();
            Integer newCmd = cmdMigrationMap.get(currentCmd);
            
            if (newCmd != null && newCmd != currentCmd) {
                meta.setCustomModelData(newCmd);
                changed = true;
                logger.info("[ItemMigration] CMD 변환: " + currentCmd + " → " + newCmd);
            }
        }
        
        // 3. PDC NamespacedKey 마이그레이션 (tycoonlite → tycoon)
        if (migratePdcKeys) {
            changed |= migratePdcKeys(meta);
        }
        
        if (changed) {
            item.setItemMeta(meta);
        }
        
        return changed;
    }
    
    /**
     * Material 마이그레이션 매핑
     * CUSTOM_MODEL_DATA_MIGRATION.md 문서 기준
     * 
     * @return 변경할 Material, 변경 불필요시 null
     */
    private Material getMaterialMigration(int cmd, Material oldMaterial) {
        return switch (cmd) {
            case 1602 -> oldMaterial == Material.NETHER_STAR ? Material.PAPER : null;      // UNIVERSAL_INVENTORY_SAVE
            case 1605 -> oldMaterial == Material.IRON_INGOT ? Material.PAPER : null;       // UPGRADE_DOWNGRADE_PROTECTION
            case 1607 -> oldMaterial == Material.MAP ? Material.PAPER : null;              // TELEPORT_SCROLL
            case 1608 -> oldMaterial == Material.BOOK ? Material.PAPER : null;             // REBIRTH_MEMORY_SCROLL
            case 1611 -> oldMaterial == Material.ENCHANTED_BOOK ? Material.PAPER : null;   // SPECIAL_ENCHANT_LOTTERY
            case 1620 -> oldMaterial == Material.FIREWORK_STAR ? Material.PAPER : null;    // LAMP_SLOT_TICKET (old)
            case 1621 -> oldMaterial == Material.GUNPOWDER ? Material.PAPER : null;        // LAMP_REMOVE_TICKET (old)
            case 2000 -> oldMaterial == Material.REDSTONE_LAMP ? Material.PAPER : null;    // WEAPON_LAMP
            case 2001 -> oldMaterial == Material.SEA_LANTERN ? Material.PAPER : null;      // ARMOR_LAMP
            case 2002 -> oldMaterial == Material.GLOWSTONE ? Material.PAPER : null;        // TOOL_LAMP
            case 2003 -> oldMaterial == Material.END_ROD ? Material.PAPER : null;          // UNIVERSAL_LAMP
            default -> null;
        };
    }

    /**
     * PDC 키 마이그레이션 (tycoonlite → tycoon)
     */
    @SuppressWarnings("deprecation")
    private boolean migratePdcKeys(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean changed = false;
        
        // 마이그레이션할 키 목록
        String[] keyNames = {
            "custom_enchants",
            "lamp_effect",
            "lamp_type",
            "upgrade_level",
            "enchant_book_id",
            "lamp_item",
            "protection_scroll",
            "lamp_slots",
            "lamp_slot_count",
            "core_item_type"
        };
        
        for (String keyName : keyNames) {
            // 구 키 (tycoonlite 네임스페이스)
            NamespacedKey oldKey = new NamespacedKey("tycoonlite", keyName);
            // 새 키 (tycoon 네임스페이스)
            NamespacedKey newKey = new NamespacedKey("tycoon", keyName);
            
            // String 타입 데이터 마이그레이션
            if (pdc.has(oldKey, PersistentDataType.STRING)) {
                String value = pdc.get(oldKey, PersistentDataType.STRING);
                pdc.remove(oldKey);
                pdc.set(newKey, PersistentDataType.STRING, value);
                changed = true;
                logger.fine("[ItemMigration] PDC 키 변환: " + oldKey + " → " + newKey);
            }
            
            // Integer 타입 데이터 마이그레이션
            if (pdc.has(oldKey, PersistentDataType.INTEGER)) {
                Integer value = pdc.get(oldKey, PersistentDataType.INTEGER);
                pdc.remove(oldKey);
                pdc.set(newKey, PersistentDataType.INTEGER, value);
                changed = true;
                logger.fine("[ItemMigration] PDC 키 변환 (INT): " + oldKey + " → " + newKey);
            }
        }
        
        return changed;
    }

    /**
     * CMD 매핑 추가
     */
    public void addCmdMapping(int oldCmd, int newCmd) {
        cmdMigrationMap.put(oldCmd, newCmd);
    }

    /**
     * 통계 반환
     */
    public String getStats() {
        return String.format("마이그레이션 통계: 아이템 %d개, 컨테이너 %d개", 
            migratedItemCount, migratedContainerCount);
    }

    /**
     * 활성화 여부
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 설정 리로드
     */
    public void reload() {
        cmdMigrationMap.clear();
        loadCmdMappings();
        migratePdcKeys = plugin.getConfig().getBoolean("migration.migrate_pdc_keys", true);
        enabled = plugin.getConfig().getBoolean("migration.enabled", false);
    }
}
