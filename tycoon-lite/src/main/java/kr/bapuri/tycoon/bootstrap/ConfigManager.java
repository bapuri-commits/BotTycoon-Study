package kr.bapuri.tycoon.bootstrap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * ConfigManager - 다중 설정 파일 관리
 * 
 * 점진적 config 분리를 위한 매니저.
 * 기존 plugin.getConfig()는 그대로 유지하면서,
 * 새로운 시스템은 이 매니저를 통해 별도 파일 사용 가능.
 * 
 * 사용법:
 * - 기존 시스템: plugin.getConfig() 그대로 사용
 * - 새 시스템: configManager.getConfig("pvp") → config/pvp.yml
 * 
 * 파일 구조 (향후):
 * plugins/TycoonLite/
 * ├── config.yml (기존, 유지)
 * └── config/
 *     ├── economy.yml
 *     ├── worlds.yml
 *     └── ...
 */
public class ConfigManager {

    private final Plugin plugin;
    private final File configFolder;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.configFolder = new File(plugin.getDataFolder(), "config");
        
        // config 폴더 생성
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }
    }

    /**
     * 특정 설정 파일 로드
     * @param name 설정 이름 (예: "pvp" → config/pvp.yml)
     * @return FileConfiguration 객체
     */
    public FileConfiguration getConfig(String name) {
        if (configs.containsKey(name)) {
            return configs.get(name);
        }
        return loadConfig(name);
    }

    /**
     * 설정 파일 로드 (내부용)
     */
    private FileConfiguration loadConfig(String name) {
        File file = new File(configFolder, name + ".yml");
        configFiles.put(name, file);
        
        // 기본 리소스 복사 (있으면)
        if (!file.exists()) {
            String resourcePath = "config/" + name + ".yml";
            InputStream resource = plugin.getResource(resourcePath);
            if (resource != null) {
                try {
                    file.getParentFile().mkdirs();
                    java.nio.file.Files.copy(resource, file.toPath());
                    plugin.getLogger().info("[ConfigManager] 기본 설정 복사: " + name + ".yml");
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "[ConfigManager] 기본 설정 복사 실패: " + name, e);
                }
            }
        }
        
        // 설정 로드
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // 기본값 병합 (있으면)
        String resourcePath = "config/" + name + ".yml";
        InputStream defaultStream = plugin.getResource(resourcePath);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaultConfig);
        }
        
        configs.put(name, config);
        plugin.getLogger().info("[ConfigManager] 설정 로드: " + name + ".yml");
        return config;
    }

    /**
     * 특정 설정 저장
     */
    public void saveConfig(String name) {
        FileConfiguration config = configs.get(name);
        File file = configFiles.get(name);
        
        if (config != null && file != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[ConfigManager] 설정 저장 실패: " + name, e);
            }
        }
    }

    /**
     * 특정 설정 리로드
     */
    public void reloadConfig(String name) {
        configs.remove(name);
        loadConfig(name);
    }

    /**
     * 모든 분리된 설정 리로드
     */
    public void reloadAll() {
        for (String name : configs.keySet()) {
            reloadConfig(name);
        }
        plugin.getLogger().info("[ConfigManager] 모든 설정 리로드 완료: " + configs.size() + "개");
    }

    /**
     * 설정 파일 존재 여부 확인
     */
    public boolean hasConfig(String name) {
        File file = new File(configFolder, name + ".yml");
        return file.exists();
    }

    /**
     * 로드된 설정 목록 반환
     */
    public java.util.Set<String> getLoadedConfigs() {
        return configs.keySet();
    }
}
