package kr.bapuri.tycoon.world;

import org.bukkit.Difficulty;

/**
 * WorldType - 서버의 논리적 월드 타입 (기본 설정 내장)
 * 
 * TycoonLite에서 사용하는 월드:
 * - TOWN: 메인 허브, 안전 구역, 상점/시설 위치
 * - WILD: 자원 파밍, 하드 난이도, 주기적 리셋
 * 
 * 기본 설정값:
 * - config.yml에서 오버라이드 가능
 * - WorldManager.applyWorldSettings()에서 적용
 */
public enum WorldType {
    
    /**
     * 타운 월드 - 메인 허브
     * - 모든 플레이어의 기본 리스폰 위치
     * - PvP OFF (안전 구역)
     * - 상점, 인챈트, 포탈 등 모든 인프라 위치
     * - PEACEFUL 고정, 몹/동물 스폰 OFF
     */
    TOWN("town", "마을", 
         Difficulty.PEACEFUL,   // difficulty
         false,                 // pvp
         false,                 // keepInventory
         true,                  // portalIsolation (Town에서 네더/엔드 포탈 분리)
         false,                 // mobSpawning
         false),                // animalSpawning
    
    /**
     * 야생 월드 - 자원 파밍
     * - 하드 난이도
     * - N일마다 리셋 (config.yml로 설정)
     * - 주요 자원 획득처
     * - PvP ON (선택형, 기본 10% 데미지)
     */
    WILD("wild", "야생", 
         Difficulty.HARD,       // difficulty
         true,                  // pvp
         false,                 // keepInventory
         false,                 // portalIsolation (Wild는 네더/엔드 연결)
         true,                  // mobSpawning
         true);                 // animalSpawning
    
    // [DROP] HUNTER, DUEL, DUNGEON, LOBBY, CASINO 제거됨 (Phase 1.5)

    private final String configKey;
    private final String displayName;
    private final Difficulty defaultDifficulty;
    private final boolean defaultPvp;
    private final boolean defaultKeepInventory;
    private final boolean defaultPortalIsolation;
    private final boolean defaultMobSpawning;
    private final boolean defaultAnimalSpawning;

    WorldType(String configKey, String displayName, 
              Difficulty defaultDifficulty, boolean defaultPvp, boolean defaultKeepInventory,
              boolean defaultPortalIsolation, boolean defaultMobSpawning, boolean defaultAnimalSpawning) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.defaultDifficulty = defaultDifficulty;
        this.defaultPvp = defaultPvp;
        this.defaultKeepInventory = defaultKeepInventory;
        this.defaultPortalIsolation = defaultPortalIsolation;
        this.defaultMobSpawning = defaultMobSpawning;
        this.defaultAnimalSpawning = defaultAnimalSpawning;
    }

    /**
     * config.yml에서 사용하는 키 (예: worlds.town.name)
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * 표시용 이름 (한글)
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 기본 난이도
     */
    public Difficulty getDefaultDifficulty() {
        return defaultDifficulty;
    }

    /**
     * 기본 PvP 설정
     */
    public boolean isDefaultPvp() {
        return defaultPvp;
    }

    /**
     * 기본 keepInventory 설정
     */
    public boolean isDefaultKeepInventory() {
        return defaultKeepInventory;
    }

    /**
     * 기본 포탈 격리 설정 (Town에서 네더/엔드 분리)
     */
    public boolean isDefaultPortalIsolation() {
        return defaultPortalIsolation;
    }

    /**
     * 기본 몬스터 스폰 설정
     */
    public boolean isDefaultMobSpawning() {
        return defaultMobSpawning;
    }

    /**
     * 기본 동물 스폰 설정
     */
    public boolean isDefaultAnimalSpawning() {
        return defaultAnimalSpawning;
    }

    /**
     * config key로 WorldType 찾기
     */
    public static WorldType fromConfigKey(String key) {
        for (WorldType type : values()) {
            if (type.configKey.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}

