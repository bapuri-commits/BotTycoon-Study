package kr.bapuri.tycoon.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * CoreItemAuthenticator - 핵심 아이템 인증 시스템
 * 
 * <h2>기능</h2>
 * <ul>
 *   <li>isCoreItem(): PDC에 core_item_type이 있는지 확인</li>
 *   <li>getItemType(): 아이템 타입 조회</li>
 *   <li>signItem(): 아이템에 인증 정보 추가 (PDC 기반)</li>
 * </ul>
 * 
 * <h2>PDC 구조</h2>
 * <pre>
 * tycoon:core_item_type    - 아이템 타입 (String)
 * tycoon:core_item_version - 버전 (Integer)
 * tycoon:core_item_id      - 고유 ID (String, UUID)
 * tycoon:core_item_issued_at - 발급 시간 (Long)
 * tycoon:core_item_stackable - 스택 가능 여부 (Byte)
 * </pre>
 * 
 * <h2>레거시 호환</h2>
 * PDC 키가 레거시(vanilla-plus)와 동일하여 기존 아이템 인식 가능
 */
public class CoreItemAuthenticator {
    
    private final Plugin plugin;
    private final Logger logger;
    
    // PDC 키들 (레거시와 동일)
    private final NamespacedKey keyType;
    private final NamespacedKey keyVersion;
    private final NamespacedKey keyId;
    private final NamespacedKey keyIssuedAt;
    private final NamespacedKey keySig;
    private final NamespacedKey keyStackable;
    
    // 레거시 호환을 위한 고정 namespace (레거시 plugin.yml name: "Tycoon")
    private static final String LEGACY_NAMESPACE = "tycoon";
    
    // 현재 아이템 버전
    public static final int CURRENT_VERSION = 1;
    
    public CoreItemAuthenticator(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        // NamespacedKey 초기화 (레거시와 동일한 namespace 사용)
        // 주의: plugin 이름이 아닌 고정 namespace 사용하여 호환성 유지
        this.keyType = new NamespacedKey(LEGACY_NAMESPACE, "core_item_type");
        this.keyVersion = new NamespacedKey(LEGACY_NAMESPACE, "core_item_version");
        this.keyId = new NamespacedKey(LEGACY_NAMESPACE, "core_item_id");
        this.keyIssuedAt = new NamespacedKey(LEGACY_NAMESPACE, "core_item_issued_at");
        this.keySig = new NamespacedKey(LEGACY_NAMESPACE, "core_item_sig");
        this.keyStackable = new NamespacedKey(LEGACY_NAMESPACE, "core_item_stackable");
        
        logger.info("[CoreItemAuthenticator] 초기화 완료 (namespace=" + LEGACY_NAMESPACE + ")");
    }
    
    /**
     * 핵심 아이템인지 확인 (검증 없이 PDC 존재 여부만)
     * 
     * @param item 확인할 아이템
     * @return 핵심 아이템이면 true
     */
    public boolean isCoreItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(keyType, PersistentDataType.STRING);
    }
    
    /**
     * 아이템의 타입 조회 (검증 없이)
     * 
     * @param item 확인할 아이템
     * @return 타입 (없으면 null)
     */
    public CoreItemType getItemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        String typeKey = meta.getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
        return CoreItemType.fromConfigKey(typeKey);
    }
    
    /**
     * 아이템의 타입 조회 (Optional 버전)
     * 
     * @param item 확인할 아이템
     * @return Optional<CoreItemType>
     */
    public Optional<CoreItemType> getItemTypeOptional(ItemStack item) {
        return Optional.ofNullable(getItemType(item));
    }
    
    /**
     * 스택 가능 아이템인지 확인 (검증 없이)
     * 
     * @param item 확인할 아이템
     * @return 스택 가능이면 true
     */
    public boolean isStackable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        return meta.getPersistentDataContainer().has(keyStackable, PersistentDataType.BYTE);
    }
    
    /**
     * 아이템의 고유 ID 추출 (검증 없이)
     * 
     * @param item 확인할 아이템
     * @return 고유 ID (없으면 null)
     */
    public String getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        return meta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
    }
    
    // ========== 아이템 서명 ==========
    
    /**
     * 아이템에 인증 정보(PDC) 추가
     * 
     * <p>스택 가능 아이템은 타입 기반 고정 ID를 공유하여 스택 가능</p>
     * <p>스택 불가 아이템은 고유 UUID와 발급 시간을 가짐</p>
     * 
     * @param item 서명할 아이템
     * @param type 아이템 타입
     * @return 성공 여부
     */
    public boolean signItem(ItemStack item, CoreItemType type) {
        if (item == null || type == null) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        // 타입 기록
        pdc.set(keyType, PersistentDataType.STRING, type.getConfigKey());
        
        // 버전 기록
        pdc.set(keyVersion, PersistentDataType.INTEGER, CURRENT_VERSION);
        
        if (type.isStackable()) {
            // 스택 가능: 타입 기반 고정 ID, 타임스탬프 없음 → 동일 타입끼리 스택 가능
            String itemId = "STACK_" + type.getConfigKey();
            pdc.set(keyId, PersistentDataType.STRING, itemId);
            // keyIssuedAt 저장하지 않음
            pdc.set(keyStackable, PersistentDataType.BYTE, (byte) 1);
        } else {
            // 스택 불가: 고유 UUID + 타임스탬프 → 각 아이템이 고유함
            String itemId = UUID.randomUUID().toString();
            pdc.set(keyId, PersistentDataType.STRING, itemId);
            pdc.set(keyIssuedAt, PersistentDataType.LONG, System.currentTimeMillis());
        }
        
        item.setItemMeta(meta);
        return true;
    }
    
    /**
     * 아이템의 버전 조회
     * 
     * @param item 확인할 아이템
     * @return 버전 (없으면 0)
     */
    public int getItemVersion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        
        Integer version = meta.getPersistentDataContainer().get(keyVersion, PersistentDataType.INTEGER);
        return version != null ? version : 0;
    }
    
    /**
     * 아이템의 발급 시간 조회
     * 
     * @param item 확인할 아이템
     * @return 발급 시간 (없으면 0)
     */
    public long getIssuedAt(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        
        Long issuedAt = meta.getPersistentDataContainer().get(keyIssuedAt, PersistentDataType.LONG);
        return issuedAt != null ? issuedAt : 0;
    }
    
    // ========== Getters ==========
    
    public NamespacedKey getKeyType() { return keyType; }
    public NamespacedKey getKeyVersion() { return keyVersion; }
    public NamespacedKey getKeyId() { return keyId; }
    public NamespacedKey getKeyIssuedAt() { return keyIssuedAt; }
    public NamespacedKey getKeySig() { return keySig; }
    public NamespacedKey getKeyStackable() { return keyStackable; }
}
