package kr.bapuri.tycoon.codex;

import org.bukkit.Material;
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
 * CodexRegistry - codex.yml에서 도감 규칙을 로드
 * 
 * codex.yml 구조:
 * categories:
 *   광물:
 *     required: 1
 *     consume: false
 *     reward: 5
 *     items:
 *       COAL: "석탄"
 *       IRON_INGOT: "철괴"
 * 
 * milestones:
 *   10:
 *     bottcoin: 5
 *   25:
 *     bottcoin: 10
 *     bd: 1000
 */
public class CodexRegistry {

    private final Plugin plugin;
    private final Logger logger;

    // material -> rule
    private final Map<Material, CodexRule> ruleMap = new HashMap<>();
    // category -> rules (출력용)
    private final Map<String, List<CodexRule>> byCategory = new LinkedHashMap<>();
    // 카테고리 순서 유지
    private final List<String> categoryOrder = new ArrayList<>();
    
    // 마일스톤 보상 (count -> MilestoneReward)
    private final Map<Integer, MilestoneReward> milestones = new LinkedHashMap<>();
    
    // 기본 보상 설정 (defaults 섹션)
    private long defaultConsumeReward = 1;  // 소비형 기본 보상
    private long defaultKeepReward = 3;     // 비소비형 기본 보상
    
    // [2026-02-01] 보상 버전 (소급적용용)
    private int configVersion = 1;

    public CodexRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadFromConfig();
    }

    // ========== Getters ==========
    
    public CodexRule get(Material mat) {
        return ruleMap.get(mat);
    }

    public int getTotalCount() {
        return ruleMap.size();
    }

    public Set<Material> getAllMaterials() {
        return ruleMap.keySet();
    }

    public List<String> getCategoryOrder() {
        return Collections.unmodifiableList(categoryOrder);
    }

    public List<CodexRule> getByCategory(String category) {
        return byCategory.getOrDefault(category, Collections.emptyList());
    }
    
    public Map<Integer, MilestoneReward> getMilestones() {
        return Collections.unmodifiableMap(milestones);
    }
    
    /**
     * 소비형 아이템 기본 보상 (codex.yml defaults.consume_reward)
     */
    public long getDefaultConsumeReward() {
        return defaultConsumeReward;
    }
    
    /**
     * 비소비형 아이템 기본 보상 (codex.yml defaults.keep_reward)
     */
    public long getDefaultKeepReward() {
        return defaultKeepReward;
    }
    
    /**
     * [2026-02-01] 설정 버전 (소급적용용)
     * 버전이 변경되면 기존 마일스톤 수령 기록 초기화
     */
    public int getConfigVersion() {
        return configVersion;
    }

    // ========== Config Loading ==========
    
    /**
     * codex.yml 리로드
     */
    public void reload() {
        loadFromConfig();
    }
    
    private void loadFromConfig() {
        ruleMap.clear();
        byCategory.clear();
        categoryOrder.clear();
        milestones.clear();
        
        // codex.yml 파일 로드
        File codexFile = new File(plugin.getDataFolder(), "codex.yml");
        if (!codexFile.exists()) {
            plugin.saveResource("codex.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(codexFile);
        
        // 기본값 로드 (jar 내부)
        InputStream defaultStream = plugin.getResource("codex.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
        
        // 기본 보상 로드 (defaults 섹션)
        loadDefaults(config);
        
        // 카테고리 로드
        loadCategories(config);
        
        // 마일스톤 로드
        loadMilestones(config);
        
        logger.info("[CodexRegistry] Loaded rules: " + ruleMap.size() 
            + ", categories: " + categoryOrder.size()
            + ", milestones: " + milestones.size()
            + ", defaults: consume=" + defaultConsumeReward + "/keep=" + defaultKeepReward);
    }
    
    /**
     * defaults 섹션 로드
     * 
     * defaults:
     *   consume_reward: 1   # 소비형 아이템 기본 보상
     *   keep_reward: 3      # 비소비형 아이템 기본 보상
     * 
     * [2026-02-01] config-version도 함께 로드 (소급적용용)
     */
    private void loadDefaults(YamlConfiguration config) {
        // [2026-02-01] 설정 버전 로드
        configVersion = config.getInt("config-version", 1);
        defaultConsumeReward = config.getLong("defaults.consume_reward", 1);
        defaultKeepReward = config.getLong("defaults.keep_reward", 3);
    }
    
    /**
     * 카테고리 섹션 로드
     * 
     * [Phase 8] 레거시 규칙 적용:
     * - required 값은 1 또는 10만 허용 (다른 값은 자동 변환)
     * - consume 값은 required에서 자동 결정 (YAML 설정 무시)
     *   - required = 1 → consume = false (희귀, 소멸 안 함)
     *   - required = 10 → consume = true (일반, 소멸)
     */
    private void loadCategories(YamlConfiguration config) {
        ConfigurationSection categoriesSec = config.getConfigurationSection("categories");
        if (categoriesSec == null) {
            logger.warning("[CodexRegistry] codex.yml에 categories 섹션이 없습니다.");
            return;
        }
        
        for (String categoryKey : categoriesSec.getKeys(false)) {
            ConfigurationSection catSec = categoriesSec.getConfigurationSection(categoryKey);
            if (catSec == null) continue;
            
            // 카테고리 등록
            categoryOrder.add(categoryKey);
            byCategory.put(categoryKey, new ArrayList<>());
            
            // [Phase 8] 레거시 규칙: required 값 정규화 (1 또는 10만 허용)
            int rawRequired = catSec.getInt("required", 1);
            int required = normalizeRequired(rawRequired);
            
            // [Phase 8] 레거시 규칙: consume은 required에서 자동 결정
            // required = 1 (희귀) → consume = false (소멸 안 함)
            // required = 10 (일반) → consume = true (소멸)
            boolean consume = (required >= 10);
            
            // 로그 (설정 검증용)
            if (rawRequired != required) {
                logger.info("[CodexRegistry] '" + categoryKey + "' required 자동 변환: " 
                    + rawRequired + " → " + required);
            }
            
            Long rewardOverride = catSec.contains("reward") 
                ? catSec.getLong("reward") 
                : null;
            
            // 아이템 로드
            ConfigurationSection itemsSec = catSec.getConfigurationSection("items");
            if (itemsSec != null) {
                loadItemsFromSection(categoryKey, itemsSec, required, consume, rewardOverride);
            }
        }
    }
    
    /**
     * [Phase 8] required 값을 1 또는 10으로 정규화
     * 
     * @param value 원본 값
     * @return 1 또는 10 (중간값은 가까운 쪽으로)
     */
    private int normalizeRequired(int value) {
        if (value <= 0) return 1;  // 0 이하는 1로
        if (value <= 5) return 1;  // 1~5는 1로 (희귀)
        return 10;                  // 6 이상은 10으로 (일반)
    }
    
    /**
     * 아이템 섹션 로드
     * items:
     *   COAL: "석탄"
     *   IRON_INGOT: "철괴"
     */
    private void loadItemsFromSection(String category, ConfigurationSection itemsSec,
                                       int required, boolean consume, Long rewardOverride) {
        for (String matName : itemsSec.getKeys(false)) {
            Material mat = parseMaterial(matName, category);
            if (mat == null) continue;
            
            // 한글 이름 (값으로 직접 지정하거나 기본값 사용)
            String koreanName = itemsSec.getString(matName);
            if (koreanName == null || koreanName.isBlank()) {
                koreanName = getDefaultKoreanName(mat);
            }
            
            CodexRule rule = new CodexRule(
                mat, category, required, consume, 
                mat.name(), koreanName, rewardOverride
            );
            
            ruleMap.put(mat, rule);
            byCategory.get(category).add(rule);
        }
    }
    
    /**
     * 마일스톤 섹션 로드
     * 
     * [2026-01-31] items 목록 파싱 추가
     */
    private void loadMilestones(YamlConfiguration config) {
        ConfigurationSection milestonesSec = config.getConfigurationSection("milestones");
        if (milestonesSec == null) {
            logger.info("[CodexRegistry] 마일스톤 설정이 없습니다. 기본값 사용.");
            setDefaultMilestones();
            return;
        }
        
        for (String countStr : milestonesSec.getKeys(false)) {
            try {
                int count = Integer.parseInt(countStr);
                ConfigurationSection rewardSec = milestonesSec.getConfigurationSection(countStr);
                if (rewardSec == null) continue;
                
                long bottcoin = rewardSec.getLong("bottcoin", 0);
                long bd = rewardSec.getLong("bd", 0);
                
                // [2026-01-31] 아이템 보상 파싱
                List<String> items = rewardSec.getStringList("items");
                
                milestones.put(count, new MilestoneReward(bottcoin, bd, items));
                
                // 아이템 보상이 있으면 로그
                if (!items.isEmpty()) {
                    logger.info("[CodexRegistry] 마일스톤 " + count + " 아이템 보상: " + items);
                }
            } catch (NumberFormatException e) {
                logger.warning("[CodexRegistry] 잘못된 마일스톤 수: " + countStr);
            }
        }
        
        // 마일스톤이 비어있으면 기본값
        if (milestones.isEmpty()) {
            setDefaultMilestones();
        }
    }
    
    /**
     * 기본 마일스톤 설정
     */
    private void setDefaultMilestones() {
        milestones.put(10, new MilestoneReward(5, 0));
        milestones.put(25, new MilestoneReward(10, 500));
        milestones.put(50, new MilestoneReward(20, 1000));
        milestones.put(100, new MilestoneReward(50, 2500));
        milestones.put(200, new MilestoneReward(100, 5000));
    }
    
    private Material parseMaterial(String matName, String category) {
        try {
            return Material.valueOf(matName.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            logger.warning("[CodexRegistry] 알 수 없는 Material: " 
                + matName + " (category=" + category + ")");
            return null;
        }
    }
    
    /**
     * 기본 한글 이름 반환
     */
    private String getDefaultKoreanName(Material mat) {
        return switch (mat) {
            // 광물/원석
            case COAL -> "석탄";
            case RAW_IRON -> "철 원석";
            case RAW_COPPER -> "구리 원석";
            case RAW_GOLD -> "금 원석";
            case IRON_INGOT -> "철괴";
            case COPPER_INGOT -> "구리괴";
            case GOLD_INGOT -> "금괴";
            case DIAMOND -> "다이아몬드";
            case EMERALD -> "에메랄드";
            case LAPIS_LAZULI -> "청금석";
            case REDSTONE -> "레드스톤";
            case QUARTZ -> "석영";
            case NETHERITE_INGOT -> "네더라이트 주괴";
            case NETHERITE_SCRAP -> "네더라이트 파편";
            case AMETHYST_SHARD -> "자수정 조각";
            
            // 작물
            case WHEAT -> "밀";
            case CARROT -> "당근";
            case POTATO -> "감자";
            case BEETROOT -> "비트";
            case MELON_SLICE -> "수박 조각";
            case PUMPKIN -> "호박";
            case SUGAR_CANE -> "사탕수수";
            case BAMBOO -> "대나무";
            case CACTUS -> "선인장";
            case COCOA_BEANS -> "코코아 콩";
            case NETHER_WART -> "네더 사마귀";
            case SWEET_BERRIES -> "달콤한 열매";
            case GLOW_BERRIES -> "발광 열매";
            
            // 물고기
            case COD -> "대구";
            case SALMON -> "연어";
            case TROPICAL_FISH -> "열대어";
            case PUFFERFISH -> "복어";
            
            // 낚시 잡템
            case LILY_PAD -> "수련잎";
            case BOWL -> "그릇";
            case LEATHER -> "가죽";
            case LEATHER_BOOTS -> "가죽 부츠";
            case ROTTEN_FLESH -> "썩은 살점";
            case BONE -> "뼈";
            case INK_SAC -> "먹물 주머니";
            case STRING -> "실";
            case TRIPWIRE_HOOK -> "철사 덫 갈고리";
            case STICK -> "막대기";
            case NAUTILUS_SHELL -> "앵무조개 껍데기";
            case SADDLE -> "안장";
            case NAME_TAG -> "이름표";
            case BOW -> "활";
            case FISHING_ROD -> "낚싯대";
            case ENCHANTED_BOOK -> "마법이 부여된 책";
            
            // 기본값
            default -> mat.name();
        };
    }
    
    // ========== 마일스톤 보상 데이터 클래스 ==========
    
    /**
     * 마일스톤 보상 정의
     * 
     * [2026-01-31] 아이템 보상 추가
     * items 형식: "type:param:amount"
     *   - lamp:TOOL_LAMP:1 → 도구 램프 1개
     *   - enchant_random:S:1 → 랜덤 S등급 인챈트북 1개
     *   - scroll:destroy:1 → 파괴방지 주문서 1개
     */
    public static class MilestoneReward {
        private final long bottcoin;
        private final long bd;
        private final List<String> items;  // [2026-01-31] 아이템 보상 추가
        
        public MilestoneReward(long bottcoin, long bd) {
            this(bottcoin, bd, Collections.emptyList());
        }
        
        public MilestoneReward(long bottcoin, long bd, List<String> items) {
            this.bottcoin = bottcoin;
            this.bd = bd;
            this.items = items != null ? items : Collections.emptyList();
        }
        
        public long getBottcoin() { return bottcoin; }
        public long getBd() { return bd; }
        public List<String> getItems() { return items; }
        public boolean hasItems() { return !items.isEmpty(); }
    }
}
