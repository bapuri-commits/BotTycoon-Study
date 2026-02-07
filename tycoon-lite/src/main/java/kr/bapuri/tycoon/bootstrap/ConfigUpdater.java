package kr.bapuri.tycoon.bootstrap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ConfigUpdater - 설정 파일 자동 업데이트
 * 
 * 플러그인 업데이트 시 새로운 설정 옵션을 자동으로 추가하면서
 * 기존 사용자 설정 값은 보존합니다.
 * 
 * 동작 방식:
 * 1. 설정 파일에 config-version 키로 버전 관리
 * 2. 플러그인 내장 버전과 비교
 * 3. 버전이 낮으면 누락된 키 추가 + 기존 값 유지
 * 4. 업데이트 전 백업 생성
 * 
 * 안전 장치:
 * - 업데이트 전 자동 백업
 * - 예외 발생 시 원본 유지
 * - 상세 로깅
 */
public class ConfigUpdater {
    
    private final Plugin plugin;
    private final Logger logger;
    private final File backupFolder;
    
    // 버전 키 이름
    private static final String VERSION_KEY = "config-version";
    
    // 업데이트할 설정 파일 목록과 현재 버전
    // 새 버전 출시 시 이 버전 번호를 올리면 됨
    private static final Map<String, Integer> CONFIG_VERSIONS = new LinkedHashMap<>();
    
    static {
        // 설정 파일 버전 (리소스 파일의 config-version과 일치해야 함)
        CONFIG_VERSIONS.put("config.yml", 1);
        CONFIG_VERSIONS.put("jobs.yml", 2);
        CONFIG_VERSIONS.put("shops.yml", 2);
        CONFIG_VERSIONS.put("codex.yml", 1);
        CONFIG_VERSIONS.put("lamps.yml", 1);
        CONFIG_VERSIONS.put("enchants.yml", 1);
        CONFIG_VERSIONS.put("achievements.yml", 1);
        CONFIG_VERSIONS.put("titles.yml", 1);
        CONFIG_VERSIONS.put("purchasable_titles.yml", 1);
        CONFIG_VERSIONS.put("cosmetics.yml", 1);
        CONFIG_VERSIONS.put("antiexploit.yml", 1);
    }
    
    public ConfigUpdater(Plugin plugin) {
        this.plugin = plugin;
        this.logger = Logger.getLogger("TycoonLite.ConfigUpdater");
        this.backupFolder = new File(plugin.getDataFolder(), "config_backups");
    }
    
    /**
     * 모든 설정 파일 업데이트 실행
     * 플러그인 onEnable에서 호출
     * 
     * @return 업데이트된 파일 수
     */
    public int updateAllConfigs() {
        int updatedCount = 0;
        
        logger.info("[ConfigUpdater] 설정 파일 업데이트 검사 시작...");
        
        for (Map.Entry<String, Integer> entry : CONFIG_VERSIONS.entrySet()) {
            String fileName = entry.getKey();
            int latestVersion = entry.getValue();
            
            try {
                boolean updated = updateConfig(fileName, latestVersion);
                if (updated) {
                    updatedCount++;
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ConfigUpdater] " + fileName + " 업데이트 실패 (원본 유지)", e);
                // 실패해도 다른 파일 계속 처리
            }
        }
        
        if (updatedCount > 0) {
            logger.info("[ConfigUpdater] " + updatedCount + "개 설정 파일 업데이트 완료");
        } else {
            logger.info("[ConfigUpdater] 모든 설정 파일이 최신 상태입니다");
        }
        
        return updatedCount;
    }
    
    /**
     * 단일 설정 파일 업데이트
     * 
     * @param fileName 설정 파일명 (예: "config.yml")
     * @param latestVersion 최신 버전 번호
     * @return 업데이트 수행 여부
     */
    public boolean updateConfig(String fileName, int latestVersion) throws IOException {
        File configFile = new File(plugin.getDataFolder(), fileName);
        
        // 파일이 없으면 기본 리소스 복사 (saveResource가 처리)
        if (!configFile.exists()) {
            logger.fine("[ConfigUpdater] " + fileName + " 파일 없음 - 새로 생성됨");
            return false;
        }
        
        // 플러그인 내장 리소스 확인
        InputStream defaultResource = plugin.getResource(fileName);
        if (defaultResource == null) {
            logger.warning("[ConfigUpdater] " + fileName + " 기본 리소스 없음 - 건너뜀");
            return false;
        }
        
        // 현재 파일 로드
        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        int currentVersion = currentConfig.getInt(VERSION_KEY, 0);
        
        // 버전 체크
        if (currentVersion >= latestVersion) {
            logger.fine("[ConfigUpdater] " + fileName + " 최신 버전 (v" + currentVersion + ")");
            defaultResource.close();
            return false;
        }
        
        // 기본 리소스 로드
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
            new InputStreamReader(defaultResource, StandardCharsets.UTF_8)
        );
        
        logger.info("[ConfigUpdater] " + fileName + " 업데이트 필요 (v" + currentVersion + " → v" + latestVersion + ")");
        
        // 백업 생성
        createBackup(configFile, currentVersion);
        
        // 누락된 키 추가 (기존 값 유지)
        int addedKeys = mergeConfigs(currentConfig, defaultConfig);
        
        // 버전 업데이트
        currentConfig.set(VERSION_KEY, latestVersion);
        
        // 저장
        currentConfig.save(configFile);
        
        logger.info("[ConfigUpdater] " + fileName + " 업데이트 완료 (추가된 키: " + addedKeys + "개)");
        
        return true;
    }
    
    /**
     * 기본 설정에서 누락된 키를 현재 설정에 추가
     * 기존 값은 절대 덮어쓰지 않음
     * 
     * @return 추가된 키 수
     */
    private int mergeConfigs(YamlConfiguration current, YamlConfiguration defaults) {
        int addedCount = 0;
        
        // 모든 키 순회 (깊은 탐색)
        Set<String> allKeys = defaults.getKeys(true);
        
        for (String key : allKeys) {
            // config-version은 별도 처리
            if (key.equals(VERSION_KEY)) {
                continue;
            }
            
            // 현재 설정에 키가 없으면 추가
            if (!current.contains(key)) {
                Object value = defaults.get(key);
                
                // ConfigurationSection은 건너뜀 (하위 키가 개별 추가됨)
                if (value instanceof ConfigurationSection) {
                    continue;
                }
                
                current.set(key, value);
                addedCount++;
                logger.fine("[ConfigUpdater] 키 추가: " + key);
            }
        }
        
        return addedCount;
    }
    
    /**
     * 설정 파일 백업 생성
     */
    private void createBackup(File configFile, int version) throws IOException {
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String backupName = configFile.getName().replace(".yml", "") 
            + "_v" + version + "_" + timestamp + ".yml";
        
        File backupFile = new File(backupFolder, backupName);
        Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("[ConfigUpdater] 백업 생성: " + backupName);
    }
    
    /**
     * 특정 설정 파일의 현재 버전 조회
     */
    public int getCurrentVersion(String fileName) {
        File configFile = new File(plugin.getDataFolder(), fileName);
        if (!configFile.exists()) {
            return 0;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        return config.getInt(VERSION_KEY, 0);
    }
    
    /**
     * 특정 설정 파일의 최신 버전 조회
     */
    public int getLatestVersion(String fileName) {
        return CONFIG_VERSIONS.getOrDefault(fileName, 0);
    }
    
    /**
     * 업데이트 필요 여부 확인
     */
    public boolean needsUpdate(String fileName) {
        int current = getCurrentVersion(fileName);
        int latest = getLatestVersion(fileName);
        return current < latest;
    }
    
    /**
     * 관리되는 설정 파일 목록 반환
     */
    public Set<String> getManagedConfigs() {
        return Collections.unmodifiableSet(CONFIG_VERSIONS.keySet());
    }
    
    /**
     * 설정 버전 등록/업데이트 (향후 확장용)
     */
    public static void registerConfigVersion(String fileName, int version) {
        CONFIG_VERSIONS.put(fileName, version);
    }
}
