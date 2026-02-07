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
 * TitleRegistry - titles.yml에서 칭호 정의 로드
 * 
 * titles.yml 구조:
 * titles:
 *   title_collector:
 *     display: "[수집가]"
 *     description: "도감 50개 달성"
 *     color: "§a"
 *     luckperms_group: "title_collector"  # optional, defaults to id
 */
public class TitleRegistry {
    
    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, Title> titles = new LinkedHashMap<>();
    
    public TitleRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadFromConfig();
    }
    
    /**
     * titles.yml 리로드
     */
    public void reload() {
        loadFromConfig();
    }
    
    private void loadFromConfig() {
        titles.clear();
        
        // titles.yml 파일 로드
        File titlesFile = new File(plugin.getDataFolder(), "titles.yml");
        if (!titlesFile.exists()) {
            plugin.saveResource("titles.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(titlesFile);
        
        // 기본값 로드 (jar 내부)
        InputStream defaultStream = plugin.getResource("titles.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
        
        ConfigurationSection section = config.getConfigurationSection("titles");
        if (section == null) {
            loadDefaultTitles();
            logger.warning("[TitleRegistry] titles.yml에 titles 섹션이 없습니다. 기본값 사용.");
            return;
        }
        
        for (String titleId : section.getKeys(false)) {
            ConfigurationSection titleSection = section.getConfigurationSection(titleId);
            if (titleSection == null) continue;
            
            String displayName = titleSection.getString("display", "[" + titleId + "]");
            String description = titleSection.getString("description", "");
            String color = titleSection.getString("color", "§f");
            String luckpermsGroup = titleSection.getString("luckperms_group", titleId);
            
            Title title = new Title(titleId, displayName, description, color, luckpermsGroup);
            titles.put(titleId, title);
        }
        
        logger.info("[TitleRegistry] 칭호 로드 완료: " + titles.size() + "개");
    }
    
    /**
     * 기본 칭호 로드 (yml 없을 경우)
     */
    private void loadDefaultTitles() {
        // 도감 관련
        titles.put("title_collector", new Title("title_collector", "[수집가]", "도감 50개 달성", "§a"));
        titles.put("title_codex_master", new Title("title_codex_master", "[도감왕]", "도감 100개 달성", "§b"));
        
        // 직업 관련
        titles.put("title_artisan", new Title("title_artisan", "[장인]", "직업 만렙 달성", "§e"));
        
        // PvP 관련
        titles.put("title_warrior", new Title("title_warrior", "[전사]", "PvP 10킬 달성", "§c"));
        titles.put("title_champion", new Title("title_champion", "[챔피언]", "PvP 50킬 달성", "§d"));
        titles.put("title_legend", new Title("title_legend", "[전설]", "PvP 100킬 달성", "§6"));
        
        // 바닐라 관련
        titles.put("title_dragon_slayer", new Title("title_dragon_slayer", "[용사냥꾼]", "엔더 드래곤 처치", "§5"));
        titles.put("title_village_hero", new Title("title_village_hero", "[영웅]", "마을 영웅", "§2"));
        titles.put("title_sky_explorer", new Title("title_sky_explorer", "[하늘탐험가]", "겉날개 획득", "§f"));
        
        logger.info("[TitleRegistry] 기본 칭호 로드: " + titles.size() + "개");
    }
    
    public Title get(String titleId) {
        return titles.get(titleId);
    }
    
    public Collection<Title> getAll() {
        return titles.values();
    }
    
    public boolean exists(String titleId) {
        return titles.containsKey(titleId);
    }
    
    public int getCount() {
        return titles.size();
    }
    
    /**
     * 칭호 등록 (구매형 칭호 등 외부에서 추가 시 사용)
     */
    public void register(Title title) {
        titles.put(title.getId(), title);
    }
}
