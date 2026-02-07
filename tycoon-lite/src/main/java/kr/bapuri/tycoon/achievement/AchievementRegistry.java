package kr.bapuri.tycoon.achievement;

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
 * AchievementRegistry - achievements.yml에서 업적 로드
 * 
 * achievements.yml 구조:
 * achievements:
 *   codex_10:
 *     name: "초보 수집가"
 *     description: "도감에 10개의 아이템을 등록했습니다!"
 *     type: CODEX
 *     tier: NORMAL
 *     target: 10
 *     bottcoin: 1
 *     title: null
 */
public class AchievementRegistry {
    
    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, Achievement> achievements = new LinkedHashMap<>();
    private final Map<AchievementType, List<Achievement>> byType = new EnumMap<>(AchievementType.class);
    
    public AchievementRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadFromConfig();
    }
    
    /**
     * achievements.yml 리로드
     */
    public void reload() {
        loadFromConfig();
    }
    
    /**
     * achievements.yml에서 업적 로드
     */
    private void loadFromConfig() {
        achievements.clear();
        byType.clear();
        
        // 타입별 리스트 초기화
        for (AchievementType type : AchievementType.values()) {
            byType.put(type, new ArrayList<>());
        }
        
        // achievements.yml 파일 로드
        File achFile = new File(plugin.getDataFolder(), "achievements.yml");
        if (!achFile.exists()) {
            plugin.saveResource("achievements.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(achFile);
        
        // 기본값 로드 (jar 내부)
        InputStream defaultStream = plugin.getResource("achievements.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
        
        ConfigurationSection section = config.getConfigurationSection("achievements");
        if (section == null) {
            loadDefaultAchievements();
            logger.warning("[AchievementRegistry] achievements.yml에 achievements 섹션이 없습니다. 기본값 사용.");
            return;
        }
        
        for (String id : section.getKeys(false)) {
            ConfigurationSection achSection = section.getConfigurationSection(id);
            if (achSection == null) continue;
            
            String name = achSection.getString("name", id);
            String description = achSection.getString("description", "");
            String typeStr = achSection.getString("type", "CODEX");
            String tierStr = achSection.getString("tier", "NORMAL");
            int target = achSection.getInt("target", 0);
            long bottcoin = achSection.getLong("bottcoin", 0);
            String titleReward = achSection.getString("title", null);
            
            AchievementType type;
            AchievementTier tier;
            try {
                type = AchievementType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[AchievementRegistry] 알 수 없는 타입: " + typeStr + " (id=" + id + ")");
                type = AchievementType.CODEX;
            }
            try {
                tier = AchievementTier.valueOf(tierStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[AchievementRegistry] 알 수 없는 등급: " + tierStr + " (id=" + id + ")");
                tier = AchievementTier.NORMAL;
            }
            
            // 보상이 0이면 기본값 사용
            if (bottcoin <= 0) {
                bottcoin = tier.getDefaultBottCoinReward();
            }
            
            Achievement ach = new Achievement(id, name, description, type, tier, target, bottcoin, titleReward);
            registerAchievement(ach);
        }
        
        logger.info("[AchievementRegistry] 업적 로드 완료: " + achievements.size() + "개");
    }
    
    /**
     * 기본 업적 로드 (yml 파일 없을 때)
     */
    private void loadDefaultAchievements() {
        // CODEX 업적
        registerAchievement(new Achievement(
            "codex_10", "초보 수집가", "도감에 10개의 아이템을 등록했습니다!",
            AchievementType.CODEX, AchievementTier.NORMAL, 10, 1, null
        ));
        registerAchievement(new Achievement(
            "codex_50", "수집가", "도감에 50개의 아이템을 등록했습니다!",
            AchievementType.CODEX, AchievementTier.RARE, 50, 5, "title_collector"
        ));
        registerAchievement(new Achievement(
            "codex_100", "도감 마스터", "도감에 100개의 아이템을 등록했습니다!",
            AchievementType.CODEX, AchievementTier.LEGENDARY, 100, 10, "title_codex_master"
        ));
        
        // JOB 업적
        registerAchievement(new Achievement(
            "first_job", "첫 직업", "첫 번째 직업을 선택했습니다!",
            AchievementType.JOB, AchievementTier.NORMAL, 0, 1, null
        ));
        registerAchievement(new Achievement(
            "job_level_10", "견습생 탈출", "직업 레벨 10을 달성했습니다!",
            AchievementType.JOB, AchievementTier.NORMAL, 10, 1, null
        ));
        
        // PVP 업적
        registerAchievement(new Achievement(
            "pvp_first_kill", "첫 승리", "첫 번째 PvP 승리를 거뒀습니다!",
            AchievementType.PVP, AchievementTier.NORMAL, 0, 1, null
        ));
        
        logger.info("[AchievementRegistry] 기본 업적 로드: " + achievements.size() + "개");
    }
    
    private void registerAchievement(Achievement ach) {
        achievements.put(ach.getId(), ach);
        byType.get(ach.getType()).add(ach);
    }
    
    /**
     * 외부에서 업적 등록 (런타임 추가용)
     */
    public void register(Achievement ach) {
        if (ach == null) return;
        if (achievements.containsKey(ach.getId())) return; // 중복 방지
        registerAchievement(ach);
    }
    
    public Achievement get(String id) {
        return achievements.get(id);
    }
    
    public Collection<Achievement> getAll() {
        return achievements.values();
    }
    
    public List<Achievement> getByType(AchievementType type) {
        return byType.getOrDefault(type, Collections.emptyList());
    }
    
    public boolean exists(String id) {
        return achievements.containsKey(id);
    }
    
    public int getCount() {
        return achievements.size();
    }
}
