package kr.bapuri.tycoon.enhance.enchant;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * CustomEnchantRegistry - 커스텀 인챈트 레지스트리
 * 
 * enchants.yml에서 인챈트 설정을 로드하고 관리
 * (기존 config.yml의 enhance.enchants 섹션도 호환성 지원)
 */
public class CustomEnchantRegistry {

    private final JavaPlugin plugin;
    private final Logger logger;
    
    // 인챈트 데이터 저장소
    private final Map<String, CustomEnchantData> enchantDataMap = new HashMap<>();
    
    // global 설정: 효과 발동 메시지 표시 여부
    private boolean showEffectMessages = true;

    public CustomEnchantRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 인챈트 설정 로드/리로드
     * 
     * 우선순위:
     * 1. enchants.yml 파일이 있으면 사용
     * 2. config.yml의 enhance.enchants 섹션이 있으면 경고 후 사용 (호환성)
     * 3. 둘 다 없으면 기본 enchants.yml 생성
     */
    public void reload() {
        enchantDataMap.clear();
        
        // 모든 CustomEnchant에 대해 기본 데이터 생성
        for (CustomEnchant enchant : CustomEnchant.values()) {
            enchantDataMap.put(enchant.getId(), new CustomEnchantData(enchant));
        }
        
        File enchantFile = new File(plugin.getDataFolder(), "enchants.yml");
        
        // Case 1: enchants.yml 존재
        if (enchantFile.exists()) {
            logger.info("[Enchant] enchants.yml에서 설정 로드");
            loadFromYamlFile(enchantFile);
            return;
        }
        
        // Case 2: config.yml의 기존 설정 확인 (호환성)
        ConfigurationSection oldSection = 
            plugin.getConfig().getConfigurationSection("enhance.enchants");
        
        if (oldSection != null && !oldSection.getKeys(false).isEmpty()) {
            logger.warning("================================================");
            logger.warning("[Enchant] config.yml의 enhance.enchants 설정 감지!");
            logger.warning("[Enchant] 호환성을 위해 기존 설정을 사용합니다.");
            logger.warning("[Enchant] enchants.yml로 마이그레이션을 권장합니다.");
            logger.warning("================================================");
            loadFromConfigSection(oldSection);
            // showEffectMessages는 config.yml에서 로드
            this.showEffectMessages = plugin.getConfig()
                .getBoolean("enhance.showEnchantMessages", true);
            logLoadResult();
            return;
        }
        
        // Case 3: 기본 파일 생성
        logger.info("[Enchant] 기본 enchants.yml 생성");
        plugin.saveResource("enchants.yml", false);
        loadFromYamlFile(enchantFile);
    }
    
    /**
     * 기존 메서드명 호환성 유지 (내부적으로 reload 호출)
     */
    public void loadFromConfig() {
        reload();
    }
    
    /**
     * enchants.yml 파일에서 로드
     */
    private void loadFromYamlFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // 기본값 병합 (jar 내부)
        InputStream defaultStream = plugin.getResource("enchants.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
        
        // global 설정
        this.showEffectMessages = config.getBoolean("global.showEffectMessages", true);
        
        // 인챈트 섹션 로드
        ConfigurationSection section = config.getConfigurationSection("enchants");
        if (section != null) {
            loadFromConfigSection(section);
        }
        
        logLoadResult();
    }
    
    /**
     * ConfigurationSection에서 인챈트 설정 로드
     */
    private void loadFromConfigSection(ConfigurationSection enchantsSection) {
        for (String enchantId : enchantsSection.getKeys(false)) {
            CustomEnchant enchant = CustomEnchant.fromId(enchantId);
            if (enchant == null) {
                logger.warning("[Enchant] 알 수 없는 인챈트 ID: " + enchantId);
                continue;
            }
            
            ConfigurationSection section = enchantsSection.getConfigurationSection(enchantId);
            if (section == null) continue;
            
            CustomEnchantData data = enchantDataMap.get(enchantId);
            loadEnchantData(data, section);
        }
    }
    
    /**
     * 로드 결과 로그 출력
     */
    private void logLoadResult() {
        long enabledCount = enchantDataMap.values().stream()
            .filter(CustomEnchantData::isEnabled).count();
        logger.info("[Enchant] 인챈트 로드 완료: " + enabledCount + "/" 
            + enchantDataMap.size() + "개 활성화");
    }

    /**
     * 개별 인챈트 데이터 로드
     */
    private void loadEnchantData(CustomEnchantData data, ConfigurationSection section) {
        if (section.contains("enabled")) {
            data.setEnabled(section.getBoolean("enabled", true));
        }
        
        if (section.contains("displayName")) {
            data.setDisplayName(section.getString("displayName"));
        }
        
        if (section.contains("description")) {
            data.setDescription(section.getString("description"));
        }
        
        if (section.contains("maxLevel")) {
            data.setMaxLevel(section.getInt("maxLevel"));
        }

        // 레벨별 설정
        ConfigurationSection levelsSection = section.getConfigurationSection("levels");
        if (levelsSection != null) {
            for (String levelKey : levelsSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(levelKey);
                    ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);
                    if (levelSection != null) {
                        if (levelSection.contains("price")) {
                            data.setPrice(level, levelSection.getLong("price"));
                        }
                        if (levelSection.contains("effect")) {
                            data.setEffectValue(level, levelSection.getDouble("effect"));
                        }
                        if (levelSection.contains("chance")) {
                            data.setChance(level, levelSection.getDouble("chance"));
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // ========== 조회 메서드 ==========

    /**
     * 인챈트 ID로 데이터 조회
     */
    public CustomEnchantData getData(String enchantId) {
        return enchantDataMap.get(enchantId);
    }

    /**
     * CustomEnchant으로 데이터 조회
     */
    public CustomEnchantData getData(CustomEnchant enchant) {
        if (enchant == null) return null;
        return enchantDataMap.get(enchant.getId());
    }

    /**
     * 모든 인챈트 데이터
     */
    public Collection<CustomEnchantData> getAllData() {
        return Collections.unmodifiableCollection(enchantDataMap.values());
    }

    /**
     * 활성화된 인챈트만
     */
    public List<CustomEnchantData> getEnabledEnchants() {
        List<CustomEnchantData> enabled = new ArrayList<>();
        for (CustomEnchantData data : enchantDataMap.values()) {
            if (data.isEnabled()) {
                enabled.add(data);
            }
        }
        return enabled;
    }

    /**
     * 특정 카테고리의 활성화된 인챈트
     */
    public List<CustomEnchantData> getEnabledByCategory(CustomEnchant.EnchantCategory category) {
        List<CustomEnchantData> result = new ArrayList<>();
        for (CustomEnchantData data : enchantDataMap.values()) {
            if (data.isEnabled() && data.getEnchant().getCategory() == category) {
                result.add(data);
            }
        }
        return result;
    }

    /**
     * 인챈트 존재 여부
     */
    public boolean exists(String enchantId) {
        return enchantDataMap.containsKey(enchantId);
    }

    /**
     * 인챈트 활성화 여부
     */
    public boolean isEnabled(String enchantId) {
        CustomEnchantData data = enchantDataMap.get(enchantId);
        return data != null && data.isEnabled();
    }

    /**
     * 모든 인챈트 ID 목록
     */
    public Set<String> getAllIds() {
        return Collections.unmodifiableSet(enchantDataMap.keySet());
    }

    /**
     * 활성화된 인챈트 ID 목록
     */
    public List<String> getEnabledIds() {
        List<String> ids = new ArrayList<>();
        for (CustomEnchantData data : enchantDataMap.values()) {
            if (data.isEnabled()) {
                ids.add(data.getId());
            }
        }
        return ids;
    }
    
    /**
     * 효과 발동 메시지 표시 여부
     */
    public boolean isShowEffectMessages() {
        return showEffectMessages;
    }
}
