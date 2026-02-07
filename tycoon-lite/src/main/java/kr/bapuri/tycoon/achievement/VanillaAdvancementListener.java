package kr.bapuri.tycoon.achievement;

import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * VanillaAdvancementListener - 마인크래프트 바닐라 업적 연동
 * 
 * 마인크래프트 기본 업적(Advancements)을 달성하면 
 * 서버 업적 시스템에도 연동하여 보상을 지급합니다.
 * 
 * 연동 대상:
 * - story/* : 스토리 업적
 * - nether/* : 네더 업적
 * - end/* : 엔드 업적
 * - adventure/* : 모험 업적
 * - husbandry/* : 축산 업적
 * 
 * 제외:
 * - recipes/* : 레시피 해금 (업적 아님)
 */
public class VanillaAdvancementListener implements Listener {
    
    private final AchievementService achievementService;
    private final AchievementRegistry achievementRegistry;
    private final Logger logger;
    
    // 바닐라 업적 한국어 이름 매핑
    private static final Map<String, String> KOREAN_NAMES = new HashMap<>();
    private static final Map<String, String> KOREAN_DESCRIPTIONS = new HashMap<>();
    
    static {
        // ===== 스토리 =====
        KOREAN_NAMES.put("story/root", "마인크래프트");
        KOREAN_DESCRIPTIONS.put("story/root", "게임의 핵심, 작업대를 만들었습니다!");
        
        KOREAN_NAMES.put("story/mine_stone", "돌 캐기");
        KOREAN_DESCRIPTIONS.put("story/mine_stone", "첫 번째 돌을 캐냈습니다!");
        
        KOREAN_NAMES.put("story/upgrade_tools", "도구 업그레이드");
        KOREAN_DESCRIPTIONS.put("story/upgrade_tools", "더 좋은 곡괭이를 만들었습니다!");
        
        KOREAN_NAMES.put("story/smelt_iron", "철 제련");
        KOREAN_DESCRIPTIONS.put("story/smelt_iron", "첫 철 주괴를 제련했습니다!");
        
        KOREAN_NAMES.put("story/obtain_armor", "갑옷 장착");
        KOREAN_DESCRIPTIONS.put("story/obtain_armor", "철 갑옷으로 무장했습니다!");
        
        KOREAN_NAMES.put("story/lava_bucket", "뜨거운 것이 좋아");
        KOREAN_DESCRIPTIONS.put("story/lava_bucket", "용암 양동이를 획득했습니다!");
        
        KOREAN_NAMES.put("story/iron_tools", "철기 시대");
        KOREAN_DESCRIPTIONS.put("story/iron_tools", "철 도구를 제작했습니다!");
        
        KOREAN_NAMES.put("story/deflect_arrow", "못 들어온다");
        KOREAN_DESCRIPTIONS.put("story/deflect_arrow", "방패로 화살을 막았습니다!");
        
        KOREAN_NAMES.put("story/form_obsidian", "얼음아 꽁");
        KOREAN_DESCRIPTIONS.put("story/form_obsidian", "흑요석을 생성했습니다!");
        
        KOREAN_NAMES.put("story/mine_diamond", "다이아몬드!");
        KOREAN_DESCRIPTIONS.put("story/mine_diamond", "귀중한 다이아몬드를 발견했습니다!");
        
        KOREAN_NAMES.put("story/enter_the_nether", "네더로");
        KOREAN_DESCRIPTIONS.put("story/enter_the_nether", "네더 차원으로 진입했습니다!");
        
        KOREAN_NAMES.put("story/shiny_gear", "반짝반짝");
        KOREAN_DESCRIPTIONS.put("story/shiny_gear", "다이아몬드 장비를 제작했습니다!");
        
        KOREAN_NAMES.put("story/enchant_item", "인챈터");
        KOREAN_DESCRIPTIONS.put("story/enchant_item", "아이템에 마법을 부여했습니다!");
        
        KOREAN_NAMES.put("story/cure_zombie_villager", "좀비 의사");
        KOREAN_DESCRIPTIONS.put("story/cure_zombie_villager", "좀비 주민을 치료했습니다!");
        
        KOREAN_NAMES.put("story/follow_ender_eye", "눈을 따라");
        KOREAN_DESCRIPTIONS.put("story/follow_ender_eye", "엔더 눈으로 요새를 찾았습니다!");
        
        KOREAN_NAMES.put("story/enter_the_end", "끝?");
        KOREAN_DESCRIPTIONS.put("story/enter_the_end", "엔드 차원으로 진입했습니다!");
        
        // ===== 네더 =====
        KOREAN_NAMES.put("nether/root", "네더");
        KOREAN_DESCRIPTIONS.put("nether/root", "네더 차원에 도착했습니다!");
        
        KOREAN_NAMES.put("nether/return_to_sender", "반송");
        KOREAN_DESCRIPTIONS.put("nether/return_to_sender", "가스트의 화염구를 되돌려보냈습니다!");
        
        KOREAN_NAMES.put("nether/find_fortress", "무시무시한 요새");
        KOREAN_DESCRIPTIONS.put("nether/find_fortress", "네더 요새를 발견했습니다!");
        
        KOREAN_NAMES.put("nether/obtain_blaze_rod", "블레이즈 사냥꾼");
        KOREAN_DESCRIPTIONS.put("nether/obtain_blaze_rod", "블레이즈를 처치하고 막대를 획득했습니다!");
        
        KOREAN_NAMES.put("nether/create_beacon", "불을 밝혀라");
        KOREAN_DESCRIPTIONS.put("nether/create_beacon", "신호기를 설치했습니다!");
        
        KOREAN_NAMES.put("nether/summon_wither", "위더 소환");
        KOREAN_DESCRIPTIONS.put("nether/summon_wither", "위더를 소환했습니다!");
        
        KOREAN_NAMES.put("nether/netherite_armor", "잔해 속에서");
        KOREAN_DESCRIPTIONS.put("nether/netherite_armor", "최강의 네더라이트 갑옷을 완성했습니다!");
        
        KOREAN_NAMES.put("nether/obtain_ancient_debris", "고대 잔해");
        KOREAN_DESCRIPTIONS.put("nether/obtain_ancient_debris", "고대 잔해를 발견했습니다!");
        
        KOREAN_NAMES.put("nether/explore_nether", "네더 탐험가");
        KOREAN_DESCRIPTIONS.put("nether/explore_nether", "모든 네더 바이옴을 탐험했습니다!");
        
        // ===== 엔드 =====
        KOREAN_NAMES.put("end/root", "엔드");
        KOREAN_DESCRIPTIONS.put("end/root", "엔드 차원에 도착했습니다!");
        
        KOREAN_NAMES.put("end/kill_dragon", "끝!");
        KOREAN_DESCRIPTIONS.put("end/kill_dragon", "엔더 드래곤을 처치했습니다!");
        
        KOREAN_NAMES.put("end/find_end_city", "엔드 시티");
        KOREAN_DESCRIPTIONS.put("end/find_end_city", "엔드 시티를 발견했습니다!");
        
        KOREAN_NAMES.put("end/elytra", "날개를 펼쳐");
        KOREAN_DESCRIPTIONS.put("end/elytra", "겉날개를 획득했습니다!");
        
        KOREAN_NAMES.put("end/respawn_dragon", "드래곤 부활");
        KOREAN_DESCRIPTIONS.put("end/respawn_dragon", "엔더 드래곤을 다시 소환했습니다!");
        
        KOREAN_NAMES.put("end/dragon_egg", "드래곤 알");
        KOREAN_DESCRIPTIONS.put("end/dragon_egg", "드래곤 알을 획득했습니다!");
        
        KOREAN_NAMES.put("end/dragon_breath", "드래곤 브레스");
        KOREAN_DESCRIPTIONS.put("end/dragon_breath", "드래곤 브레스를 병에 담았습니다!");
        
        // ===== 모험 =====
        KOREAN_NAMES.put("adventure/root", "모험");
        KOREAN_DESCRIPTIONS.put("adventure/root", "모험, 탐험, 그리고 전투!");
        
        KOREAN_NAMES.put("adventure/kill_a_mob", "몬스터 사냥꾼");
        KOREAN_DESCRIPTIONS.put("adventure/kill_a_mob", "첫 번째 몬스터를 처치했습니다!");
        
        KOREAN_NAMES.put("adventure/trade", "상인과 거래");
        KOREAN_DESCRIPTIONS.put("adventure/trade", "주민과 거래를 완료했습니다!");
        
        KOREAN_NAMES.put("adventure/sleep_in_bed", "좋은 꿈");
        KOREAN_DESCRIPTIONS.put("adventure/sleep_in_bed", "침대에서 편안히 잠을 잤습니다!");
        
        KOREAN_NAMES.put("adventure/hero_of_the_village", "마을의 영웅");
        KOREAN_DESCRIPTIONS.put("adventure/hero_of_the_village", "마을을 습격에서 지켜냈습니다!");
        
        KOREAN_NAMES.put("adventure/totem_of_undying", "죽지 않는 자");
        KOREAN_DESCRIPTIONS.put("adventure/totem_of_undying", "불사의 토템으로 죽음을 피했습니다!");
        
        KOREAN_NAMES.put("adventure/kill_all_mobs", "몬스터 콜렉터");
        KOREAN_DESCRIPTIONS.put("adventure/kill_all_mobs", "모든 종류의 몬스터를 처치했습니다!");
        
        KOREAN_NAMES.put("adventure/voluntary_exile", "자발적 망명");
        KOREAN_DESCRIPTIONS.put("adventure/voluntary_exile", "약탈자 대장을 처치했습니다!");
        
        // ===== 축산/자연 =====
        KOREAN_NAMES.put("husbandry/root", "농축업의 시작");
        KOREAN_DESCRIPTIONS.put("husbandry/root", "무언가를 먹었습니다!");
        
        KOREAN_NAMES.put("husbandry/plant_seed", "씨앗 심기");
        KOREAN_DESCRIPTIONS.put("husbandry/plant_seed", "씨앗을 땅에 심었습니다!");
        
        KOREAN_NAMES.put("husbandry/breed_an_animal", "짝짓기");
        KOREAN_DESCRIPTIONS.put("husbandry/breed_an_animal", "동물 두 마리를 교배시켰습니다!");
        
        KOREAN_NAMES.put("husbandry/tame_an_animal", "길들이기");
        KOREAN_DESCRIPTIONS.put("husbandry/tame_an_animal", "동물을 길들였습니다!");
        
        KOREAN_NAMES.put("husbandry/fishy_business", "낚시왕");
        KOREAN_DESCRIPTIONS.put("husbandry/fishy_business", "물고기를 낚았습니다!");
    }
    
    public VanillaAdvancementListener(AchievementService achievementService,
                                       AchievementRegistry achievementRegistry,
                                       Logger logger) {
        this.achievementService = achievementService;
        this.achievementRegistry = achievementRegistry;
        this.logger = logger;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Advancement advancement = event.getAdvancement();
        
        String key = advancement.getKey().getKey();
        String namespace = advancement.getKey().getNamespace();
        
        // 마인크래프트 네임스페이스만 처리
        if (!namespace.equals("minecraft")) return;
        
        // 레시피 해금 제외
        if (key.startsWith("recipes/")) return;
        
        // 우리 업적 시스템 ID로 변환 (예: "story/mine_stone" -> "vanilla_story_mine_stone")
        String achievementId = "vanilla_" + key.replace("/", "_");
        
        // 등록된 업적인 경우에만 처리
        Achievement achievement = achievementRegistry.get(achievementId);
        if (achievement == null) {
            // 등록되지 않은 바닐라 업적은 자동 등록 (기본 보상)
            autoRegisterVanillaAchievement(key, achievementId);
            achievement = achievementRegistry.get(achievementId);
        }
        
        if (achievement != null) {
            // 이미 해금된 경우 무시
            if (achievementService.isUnlocked(player, achievementId)) {
                return;
            }
            
            // 업적 해금
            boolean unlocked = achievementService.tryUnlock(player, achievementId);
            if (unlocked) {
                logger.fine("[VanillaAdvancement] 바닐라 업적 연동: " + player.getName() + " -> " + achievementId);
            }
        }
    }
    
    /**
     * 등록되지 않은 바닐라 업적을 자동으로 등록
     */
    private void autoRegisterVanillaAchievement(String vanillaKey, String achievementId) {
        // 카테고리 파싱
        String category = vanillaKey.contains("/") ? vanillaKey.split("/")[0] : "misc";
        
        // Tier 결정
        AchievementTier tier = determineTier(vanillaKey);
        
        // 한국어 이름/설명 매핑
        String name = KOREAN_NAMES.getOrDefault(vanillaKey, formatName(vanillaKey));
        String description = KOREAN_DESCRIPTIONS.getOrDefault(vanillaKey, generateDescription(category, tier));
        
        // 기본 보상
        long bottcoin = tier.getDefaultBottCoinReward();
        
        Achievement ach = new Achievement(
            achievementId, 
            name, 
            description,
            AchievementType.VANILLA, 
            tier, 
            0,  // target (즉시 달성형)
            bottcoin, 
            null  // 칭호 없음
        );
        
        achievementRegistry.register(ach);
        logger.info("[VanillaAdvancement] 자동 등록: " + achievementId + " -> " + name + " (" + tier + ")");
    }
    
    /**
     * 바닐라 업적 키에 따른 Tier 결정
     */
    private AchievementTier determineTier(String key) {
        // 엔더 드래곤/위더 처치 → LEGENDARY
        if (key.contains("kill_dragon") || key.contains("kill_wither")) {
            return AchievementTier.LEGENDARY;
        }
        
        // 겉날개, 네더라이트 갑옷 → LEGENDARY
        if (key.contains("elytra") || key.contains("netherite_armor")) {
            return AchievementTier.LEGENDARY;
        }
        
        // 엔드 관련 → EPIC
        if (key.startsWith("end/")) {
            return AchievementTier.EPIC;
        }
        
        // 네더 관련 → RARE
        if (key.startsWith("nether/")) {
            return AchievementTier.RARE;
        }
        
        // 특별한 모험 업적 → EPIC
        if (key.contains("hero_of_the_village") || key.contains("kill_all_mobs")) {
            return AchievementTier.EPIC;
        }
        
        // 다이아몬드, 엔드 진입 등 → RARE
        if (key.contains("diamond") || key.contains("enter_the_end") || key.contains("enter_the_nether")) {
            return AchievementTier.RARE;
        }
        
        // 기본 → NORMAL
        return AchievementTier.NORMAL;
    }
    
    /**
     * 카테고리와 등급에 따른 설명 생성
     */
    private String generateDescription(String category, AchievementTier tier) {
        return switch (category) {
            case "story" -> tier == AchievementTier.LEGENDARY ? "생존의 궁극적인 목표를 달성했습니다!" :
                           tier == AchievementTier.EPIC ? "생존 모험이 깊어지고 있습니다." :
                           tier == AchievementTier.RARE ? "생존의 중요한 이정표를 세웠습니다." :
                           "생존 여정의 한 걸음을 내딛었습니다.";
            case "nether" -> tier == AchievementTier.LEGENDARY ? "네더의 진정한 지배자가 되었습니다!" :
                            tier == AchievementTier.EPIC ? "네더에서 놀라운 업적을 달성했습니다." :
                            "네더 탐험에서 성과를 거뒀습니다.";
            case "end" -> tier == AchievementTier.LEGENDARY ? "엔드의 정복자로 우뚝 섰습니다!" :
                         tier == AchievementTier.EPIC ? "엔드에서 대단한 업적을 이뤘습니다." :
                         "엔드 세계를 탐험했습니다.";
            case "adventure" -> tier == AchievementTier.LEGENDARY ? "최고의 모험가로 인정받았습니다!" :
                               tier == AchievementTier.EPIC ? "위대한 모험을 완수했습니다." :
                               "새로운 모험을 경험했습니다.";
            case "husbandry" -> tier == AchievementTier.LEGENDARY ? "자연과 하나가 되었습니다!" :
                              tier == AchievementTier.EPIC ? "뛰어난 농부/목축가가 되었습니다." :
                              "자연과 함께하는 법을 배웠습니다.";
            default -> "새로운 업적을 달성했습니다.";
        };
    }
    
    /**
     * 바닐라 업적 키를 보기 좋은 이름으로 변환
     */
    private String formatName(String key) {
        String name = key.contains("/") ? key.substring(key.lastIndexOf("/") + 1) : key;
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
