package kr.bapuri.tycoon.title;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * 구매형 칭호 레지스트리
 * 
 * purchasable_titles.yml에서 구매 가능한 칭호를 로드합니다.
 * 업적 칭호(TitleRegistry)와 분리되어 관리됩니다.
 */
public class PurchasableTitleRegistry {
    
    private final Plugin plugin;
    private final Logger logger;
    private final TitleRegistry titleRegistry;  // 기존 칭호 레지스트리 (업적 칭호 포함)
    private final Map<String, PurchasableTitle> titles = new LinkedHashMap<>();
    
    public PurchasableTitleRegistry(Plugin plugin, TitleRegistry titleRegistry) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.titleRegistry = titleRegistry;
        loadFromConfig();
    }
    
    /**
     * 설정 리로드
     */
    public void reload() {
        loadFromConfig();
    }
    
    private void loadFromConfig() {
        titles.clear();
        
        // purchasable_titles.yml 파일 로드
        File titlesFile = new File(plugin.getDataFolder(), "purchasable_titles.yml");
        if (!titlesFile.exists()) {
            plugin.saveResource("purchasable_titles.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(titlesFile);
        
        // 기본값 로드 (jar 내부)
        InputStream defaultStream = plugin.getResource("purchasable_titles.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
        
        ConfigurationSection section = config.getConfigurationSection("purchasable_titles");
        if (section == null) {
            logger.warning("[PurchasableTitleRegistry] purchasable_titles 섹션이 없습니다.");
            return;
        }
        
        for (String titleId : section.getKeys(false)) {
            ConfigurationSection titleSection = section.getConfigurationSection(titleId);
            if (titleSection == null) continue;
            
            String displayName = titleSection.getString("display", "[" + titleId + "]");
            String description = titleSection.getString("description", "");
            String color = titleSection.getString("color", "§f");
            String luckpermsGroup = titleSection.getString("luckperms_group", "title_" + titleId);
            long price = titleSection.getLong("price", 500);
            
            PurchasableTitle title = new PurchasableTitle(
                    titleId, displayName, description, color, luckpermsGroup, price);
            titles.put(titleId, title);
            
            // TitleRegistry에도 등록 (LuckPerms 연동 시 registry.get() 조회용)
            if (!titleRegistry.exists(titleId)) {
                titleRegistry.register(title);
            }
        }
        
        logger.info("[PurchasableTitleRegistry] 구매형 칭호 로드 완료: " + titles.size() + "개");
    }
    
    public PurchasableTitle get(String titleId) {
        return titles.get(titleId);
    }
    
    public Collection<PurchasableTitle> getAll() {
        return titles.values();
    }
    
    public boolean exists(String titleId) {
        return titles.containsKey(titleId);
    }
    
    public int getCount() {
        return titles.size();
    }
    
    /**
     * 구매형 칭호인지 확인
     */
    public boolean isPurchasable(String titleId) {
        return titles.containsKey(titleId);
    }
}
