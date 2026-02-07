package kr.bapuri.tycoon.enhance.lamp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * LampRegistry - 램프 설정 레지스트리
 * 
 * lamps.yml에서 램프 효과 설정 로드 (Phase 3)
 * 마이그레이션: config.yml의 enhance.lamps 호환
 * 
 * Phase 6: 레거시 복사
 * Phase 3: lamps.yml 분리
 */
public class LampRegistry {

    private final JavaPlugin plugin;
    private final Logger logger;

    // 램프 타입별 가격 (specialShops에서 관리, 여기서는 기본값만)
    private final Map<LampType, Long> lampPrices = new HashMap<>();
    
    // 효과별 가중치 (확률)
    private final Map<String, Double> effectWeights = new HashMap<>();
    
    // 효과 활성화 여부
    private final Map<String, Boolean> effectEnabled = new HashMap<>();
    
    // global 설정: 효과 발동 메시지 표시 여부
    private boolean showEffectMessages = true;

    public LampRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 설정 리로드 (lamps.yml 우선, config.yml 폴백)
     */
    public void reload() {
        lampPrices.clear();
        effectWeights.clear();
        effectEnabled.clear();
        
        // 기본값 초기화
        for (LampType type : LampType.values()) {
            lampPrices.put(type, 10000L);
        }
        for (LampEffect effect : LampEffect.values()) {
            effectWeights.put(effect.getId(), effect.getRarity().getWeight());
            // 기본적으로 LampEffect.isDisabled() 값 사용
            effectEnabled.put(effect.getId(), !effect.isDisabled());
        }
        
        File lampsFile = new File(plugin.getDataFolder(), "lamps.yml");
        
        // Case 1: lamps.yml 존재 → 새 파일 사용
        if (lampsFile.exists()) {
            logger.info("[Lamp] lamps.yml에서 설정 로드");
            loadFromYamlFile(lampsFile);
            logLoadResult();
            return;
        }
        
        // Case 2: lamps.yml 없음 → config.yml 기존 설정 확인
        ConfigurationSection oldSection = 
            plugin.getConfig().getConfigurationSection("enhance.lamps");
        
        if (oldSection != null && !oldSection.getKeys(false).isEmpty()) {
            logger.warning("================================================");
            logger.warning("[Lamp] config.yml의 enhance.lamps 설정 감지!");
            logger.warning("[Lamp] 호환성을 위해 기존 설정을 사용합니다.");
            logger.warning("[Lamp] lamps.yml로 마이그레이션을 권장합니다:");
            logger.warning("[Lamp]   1. lamps.yml 파일 생성");
            logger.warning("[Lamp]   2. config.yml의 enhance.lamps 내용 복사");
            logger.warning("[Lamp]   3. config.yml에서 enhance.lamps 섹션 삭제");
            logger.warning("================================================");
            loadFromConfigSection(oldSection);
            // showEffectMessages는 config.yml에서 로드
            this.showEffectMessages = plugin.getConfig()
                .getBoolean("enhance.showLampMessages", true);
            logLoadResult();
            return;
        }
        
        // Case 3: 둘 다 없음 → 기본 파일 생성
        logger.info("[Lamp] 기본 lamps.yml 생성");
        plugin.saveResource("lamps.yml", false);
        loadFromYamlFile(lampsFile);
        logLoadResult();
    }
    
    /**
     * 기존 loadFromConfig() 호환 - reload() 호출
     */
    public void loadFromConfig() {
        reload();
    }
    
    /**
     * lamps.yml 파일에서 로드
     */
    private void loadFromYamlFile(File file) {
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            
            // global 설정
            this.showEffectMessages = config.getBoolean("global.showEffectMessages", true);
            
            // 효과 섹션 로드
            ConfigurationSection effectsSection = config.getConfigurationSection("effects");
            if (effectsSection != null) {
                loadEffectsFromSection(effectsSection);
            }
        } catch (IOException e) {
            logger.warning("[Lamp] lamps.yml 로드 실패: " + e.getMessage());
        }
    }
    
    /**
     * config.yml의 enhance.lamps 섹션에서 로드 (마이그레이션 폴백)
     */
    private void loadFromConfigSection(ConfigurationSection lampsSection) {
        // 효과 설정 로드
        ConfigurationSection effectsSection = lampsSection.getConfigurationSection("effects");
        if (effectsSection != null) {
            loadEffectsFromSection(effectsSection);
        }
    }
    
    /**
     * 효과 섹션에서 enabled/weight 로드
     */
    private void loadEffectsFromSection(ConfigurationSection effectsSection) {
        for (String effectId : effectsSection.getKeys(false)) {
            LampEffect effect = LampEffect.fromId(effectId);
            if (effect != null) {
                ConfigurationSection section = effectsSection.getConfigurationSection(effectId);
                if (section != null) {
                    effectEnabled.put(effectId, section.getBoolean("enabled", !effect.isDisabled()));
                    effectWeights.put(effectId, section.getDouble("weight", effect.getRarity().getWeight()));
                }
            }
        }
    }
    
    /**
     * 로드 결과 로그
     */
    private void logLoadResult() {
        long enabledCount = effectEnabled.values().stream().filter(b -> b).count();
        logger.info("[Lamp] 램프 설정 로드 완료 - 효과: " + effectEnabled.size() + "개 (활성: " + enabledCount + "개)");
        logger.info("[Lamp] 효과 메시지 표시: " + (showEffectMessages ? "ON" : "OFF"));
    }

    // ========== 조회 메서드 ==========

    /**
     * 램프 가격
     */
    public long getLampPrice(LampType type) {
        return lampPrices.getOrDefault(type, 10000L);
    }

    /**
     * 효과 가중치
     */
    public double getEffectWeight(String effectId) {
        return effectWeights.getOrDefault(effectId, 10.0);
    }

    /**
     * 효과 활성화 여부
     */
    public boolean isEffectEnabled(String effectId) {
        return effectEnabled.getOrDefault(effectId, true);
    }

    /**
     * 특정 램프 타입에 사용 가능한 활성화된 효과 목록
     */
    public List<LampEffect> getAvailableEffects(LampType lampType) {
        List<LampEffect> available = new ArrayList<>();
        
        for (LampEffect effect : LampEffect.values()) {
            // 램프 타입 호환 체크
            if (effect.getRequiredLampType() != lampType && 
                effect.getRequiredLampType() != LampType.UNIVERSAL_LAMP) {
                continue;
            }
            
            // 활성화 여부 체크
            if (!isEffectEnabled(effect.getId())) {
                continue;
            }
            
            available.add(effect);
        }
        
        return available;
    }

    /**
     * 가중치 기반 랜덤 효과 선택
     */
    public LampEffect rollRandomEffect(LampType lampType) {
        List<LampEffect> available = getAvailableEffects(lampType);
        if (available.isEmpty()) return null;

        // 가중치 총합 계산
        double totalWeight = 0;
        for (LampEffect effect : available) {
            totalWeight += getEffectWeight(effect.getId());
        }

        // 가중치 기반 랜덤 선택
        double roll = Math.random() * totalWeight;
        double cumulative = 0;

        for (LampEffect effect : available) {
            cumulative += getEffectWeight(effect.getId());
            if (roll < cumulative) {
                return effect;
            }
        }

        // 기본값
        return available.get(available.size() - 1);
    }

    /**
     * 모든 램프 타입 목록
     */
    public List<LampType> getAllLampTypes() {
        return Arrays.asList(LampType.values());
    }

    /**
     * 모든 효과 목록
     */
    public List<LampEffect> getAllEffects() {
        return Arrays.asList(LampEffect.values());
    }
    
    /**
     * 효과 발동 메시지 표시 여부
     */
    public boolean isShowEffectMessages() {
        return showEffectMessages;
    }
}
