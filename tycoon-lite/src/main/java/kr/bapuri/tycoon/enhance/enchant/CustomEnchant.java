package kr.bapuri.tycoon.enhance.enchant;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * CustomEnchant - 커스텀 인챈트 정의
 * 
 * 바닐라에 없는 커스텀 인챈트 목록
 * 
 * Phase 6: 레거시 복사
 * 
 * 총 17개 인챈트:
 * - 무기: BLEED, THUNDER_STRIKE, FROST_ASPECT, VAMPIRE, TRUE_DAMAGE, TRIUMPH, LAST_STRIKE, GRIEVOUS_WOUNDS
 * - 방어구: SPEED_BOOST, DOUBLE_JUMP, REGENERATION, TOUGHNESS, WATER_WALKER
 * - 도구: VEIN_MINER, HARVEST, TELEKINESIS, WISDOM, DURABILITY
 * - 바닐라 확장: UNBREAKING_EXTENDED, FORTUNE_EXTENDED
 */
public enum CustomEnchant {

    // ===============================================================
    // 무기 인챈트
    // ===============================================================
    
    BLEED("bleed", "출혈", "적에게 출혈 효과를 부여 (지속 피해)",
          3, EnchantCategory.WEAPON,
          "SWORD"),
    
    THUNDER_STRIKE("thunder_strike", "번개 강타", "확률적으로 번개를 소환",
                   3, EnchantCategory.WEAPON,
                   "SWORD", "AXE"),
    
    FROST_ASPECT("frost_aspect", "서리", "적을 느리게 만들고 빙결 피해",
                 2, EnchantCategory.WEAPON,
                 "SWORD"),
    
    VAMPIRE("vampire", "흡혈", "공격 시 입힌 피해의 일정 비율을 체력으로 회복 (레벨당 7%)",
            3, EnchantCategory.WEAPON,
            "SWORD", "AXE"),
    
    TRUE_DAMAGE("true_damage", "트루 데미지", "입힌 피해의 일부가 방어구 무시 고정피해로 적용",
                3, EnchantCategory.WEAPON,
                "SWORD", "AXE"),
    
    TRIUMPH("triumph", "승전보", "킬 시 체력 회복 (PvP/PvE 차등 적용)",
            1, EnchantCategory.HYBRID,
            "SWORD", "AXE"),
    
    LAST_STRIKE("last_strike", "최후의 일격", "특수 조건에서 공격력 증가 (+5%/+10%/+15%)",
                3, EnchantCategory.WEAPON,
                "SWORD", "AXE"),
    
    GRIEVOUS_WOUNDS("grievous_wounds", "치유 감소", "공격한 적의 체력 회복량 감소 (20%/30%/40%, 2초)",
                    3, EnchantCategory.WEAPON,
                    "SWORD", "AXE"),
    
    HUNTER("hunter", "사냥꾼", "동물에게 추가 피해 (+10%/+20%/+30%)",
           3, EnchantCategory.WEAPON,
           "SWORD", "AXE"),
    
    PRECISION("precision", "정밀 타격", "치명타 피해량 증가 (+5%/+10%/+15%)",
              3, EnchantCategory.WEAPON,
              "SWORD", "AXE"),

    // ===============================================================
    // 방어구 인챈트
    // ===============================================================
    
    SPEED_BOOST("speed_boost", "신속", "이동 속도 증가",
                3, EnchantCategory.ARMOR,
                "BOOTS"),
    
    DOUBLE_JUMP("double_jump", "이단 점프", "공중에서 추가 점프 가능",
                1, EnchantCategory.ARMOR,
                "BOOTS"),
    
    REGENERATION("regeneration", "재생력", "추가 체력 재생 (+1/+2/+3 HP/3초)",
                 3, EnchantCategory.ARMOR,
                 "CHESTPLATE", "LEGGINGS"),
    
    TOUGHNESS("toughness", "강인함", "CC 지속시간 감소 (PvP/PvE 차등 적용)",
              2, EnchantCategory.HYBRID,
              "HELMET", "CHESTPLATE"),
    
    WATER_WALKER("water_walker", "수면 보행", "물과 용암 위를 걸을 수 있음 (용암: 화염 저항 자동 부여)",
                 1, EnchantCategory.ARMOR,
                 "BOOTS"),
    
    STEADFAST("steadfast", "부동", "넉백 저항 (+15%/+30%/+45%)",
              3, EnchantCategory.ARMOR,
              "CHESTPLATE", "LEGGINGS"),
    
    VITALITY("vitality", "활력", "최대 체력 증가 (+1/+2/+3 하트)",
             3, EnchantCategory.ARMOR,
             "CHESTPLATE"),
    
    NIGHT_VISION("night_vision", "야시", "어둠 속에서도 시야 확보 (야간 투시)",
                 1, EnchantCategory.ARMOR,
                 "HELMET"),

    // ===============================================================
    // 도구 인챈트
    // ===============================================================
    
    VEIN_MINER("vein_miner", "광맥 채굴", "연결된 광석을 한번에 채굴 (최대 8/16/32개)",
               3, EnchantCategory.TOOL,
               "PICKAXE"),
    
    HARVEST("harvest", "수확", "작물 자동 재파종",
            1, EnchantCategory.TOOL,
            "HOE"),
    
    TELEKINESIS("telekinesis", "염동력", "드랍 아이템이 인벤토리로 직접 이동",
                1, EnchantCategory.UNIVERSAL,
                "PICKAXE", "SHOVEL", "AXE", "HOE", "SWORD"),
    
    WISDOM("wisdom", "지혜", "직업 경험치 획득량 증가 (바닐라 경험치 제외)",
           5, EnchantCategory.UNIVERSAL,
           "PICKAXE", "SHOVEL", "AXE", "HOE", "SWORD", "FISHING_ROD"),
    
    DURABILITY("durability", "내구도 달인", "내구도 소모 50% 감소 (Unbreaking과 중복 가능)",
               1, EnchantCategory.UNIVERSAL,
               "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "FISHING_ROD",
               "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "ELYTRA"),
    
    EXPERTISE("expertise", "전문가", "직업 활동 시 Fortune 효과 추가 적용 (+1/+2/+3)",
              3, EnchantCategory.TOOL,
              "PICKAXE", "SHOVEL", "AXE", "HOE"),
    
    LUCKY_HAND("lucky_hand", "행운의 손", "희귀 드랍 확률 증가 (+5%/+10%/+15%)",
               3, EnchantCategory.TOOL,
               "PICKAXE", "SHOVEL", "AXE", "HOE", "FISHING_ROD"),
    
    REPAIR_EFFICIENCY("repair_efficiency", "수선 효율", "수선 인챈트 경험치 비용 감소 (10%/20%/30%)",
                      3, EnchantCategory.UNIVERSAL,
                      "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "FISHING_ROD", "BOW", "CROSSBOW",
                      "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "ELYTRA"),
    
    DEEP_MINER("deep_miner", "심층 채굴", "Y < 0 광석 채굴 시 추가 드롭 (+10%/+20%/+30%)",
               3, EnchantCategory.TOOL,
               "PICKAXE"),
    
    TREE_SPIRIT("tree_spirit", "숲의 정령", "원목 채굴 시 묘목 드롭 확률 증가 (+15%/+25%/+35%)",
                3, EnchantCategory.TOOL,
                "AXE"),
    
    ORE_SENSE("ore_sense", "광맥 감지", "주변 광석 위치 파티클로 표시 (5/7/10블록)",
              3, EnchantCategory.TOOL,
              "PICKAXE"),

    // ===============================================================
    // 활 인챈트
    // ===============================================================
    
    QUICK_DRAW("quick_draw", "속사", "활 당기기 속도 증가 (+10%/+20%/+30%)",
               3, EnchantCategory.BOW,
               "BOW", "CROSSBOW"),
    
    SNIPER("sniper", "저격", "풀차지 시 추가 피해 (+10%/+20%/+30%)",
           3, EnchantCategory.BOW,
           "BOW", "CROSSBOW"),
    
    HUNTERS_EYE("hunters_eye", "사냥꾼의 눈", "동물에게 추가 피해 (+15%/+25%/+35%) - 원거리",
                3, EnchantCategory.BOW,
                "BOW", "CROSSBOW"),
    
    FROST_ARROW("frost_arrow", "서리 화살", "명중 시 대상 둔화 (1/2/3초)",
                3, EnchantCategory.BOW,
                "BOW", "CROSSBOW"),
    
    // ===============================================================
    // 바닐라 인챈트 확장
    // ===============================================================
    
    UNBREAKING_EXTENDED("unbreaking_extended", "내구도 강화", "내구도 소모 감소 IV=15%, V=10% (바닐라 Unbreaking 확장)",
                        5, EnchantCategory.UNIVERSAL,
                        "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "FISHING_ROD", "BOW", "CROSSBOW", "TRIDENT", "SHEARS",
                        "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "ELYTRA", "SHIELD"),
    
    FORTUNE_EXTENDED("fortune_extended", "행운 강화", "드랍량 증가 IV=3배, V=4배 (바닐라 Fortune 확장)",
                     5, EnchantCategory.TOOL,
                     "PICKAXE", "SHOVEL", "AXE", "HOE");

    // ===============================================================
    // 필드 정의
    // ===============================================================

    private final String id;
    private final String displayName;
    private final String description;
    private final int maxLevel;
    private final EnchantCategory category;
    private final Set<String> applicableTypes;

    CustomEnchant(String id, String displayName, String description, 
                  int maxLevel, EnchantCategory category, String... applicableTypes) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.maxLevel = maxLevel;
        this.category = category;
        this.applicableTypes = new HashSet<>(Arrays.asList(applicableTypes));
    }

    // ===============================================================
    // Getters
    // ===============================================================

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public EnchantCategory getCategory() {
        return category;
    }

    public Set<String> getApplicableTypes() {
        return applicableTypes;
    }

    // ===============================================================
    // 유틸리티
    // ===============================================================

    /**
     * ID로 인챈트 찾기
     */
    public static CustomEnchant fromId(String id) {
        if (id == null) return null;
        for (CustomEnchant enchant : values()) {
            if (enchant.id.equalsIgnoreCase(id)) {
                return enchant;
            }
        }
        return null;
    }

    /**
     * 아이템에 적용 가능한지 확인
     */
    public boolean canApplyTo(Material material) {
        if (material == null) return false;
        
        String materialName = material.name();
        
        for (String type : applicableTypes) {
            if (materialName.endsWith("_" + type) || materialName.equals(type)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 특정 카테고리의 인챈트 목록
     */
    public static CustomEnchant[] getByCategory(EnchantCategory category) {
        return Arrays.stream(values())
                .filter(e -> e.category == category)
                .toArray(CustomEnchant[]::new);
    }

    /**
     * 모든 인챈트 ID 목록
     */
    public static String[] getAllIds() {
        return Arrays.stream(values())
                .map(CustomEnchant::getId)
                .toArray(String[]::new);
    }

    /**
     * 비활성화된 인챈트인지 확인
     * 
     * LITE에서는 LAST_STRIKE, DURABILITY 비활성화
     */
    public boolean isDisabled() {
        return this == LAST_STRIKE || this == DURABILITY;
    }

    /**
     * 활성화된 인챈트 목록
     */
    public static CustomEnchant[] getEnabledEnchants() {
        return Arrays.stream(values())
                .filter(e -> !e.isDisabled())
                .toArray(CustomEnchant[]::new);
    }

    /**
     * 레벨에 따른 효과 값 (인챈트별 다름)
     * 
     * LITE: PvE 기준값 반환
     */
    public double getEffectValue(int level) {
        return switch (this) {
            // === 무기 인챈트 ===
            case BLEED -> level * 1.5; // 레벨당 1.5초 출혈
            case THUNDER_STRIKE -> level * 5.0 + 2.0; // 7%/12%/17% 번개 확률
            case FROST_ASPECT -> level * 1.0; // 레벨당 1초 슬로우
            case VAMPIRE -> level * 7.0; // PvE 레벨당 7% 흡혈
            case TRUE_DAMAGE -> level * 15.0; // PvE 15%/30%/45%
            case LAST_STRIKE -> level * 5.0; // 비활성화됨
            
            // === HYBRID 인챈트 ===
            case TRIUMPH -> 15.0; // PvE 최대HP 10~20% 회복 (평균 15%)
            case TOUGHNESS -> switch (level) { // PvE CC 감소
                case 1 -> 20.0; // -20%
                case 2 -> 35.0; // -35%
                default -> 20.0;
            };
            
            // === 새 인챈트 ===
            case GRIEVOUS_WOUNDS -> switch (level) { // 치유 감소 20/30/40%
                case 1 -> 20.0;
                case 2 -> 30.0;
                case 3 -> 40.0;
                default -> 20.0;
            };
            case HUNTER -> level * 10.0; // 동물 추가 피해 10%/20%/30%
            case PRECISION -> level * 5.0; // 치명타 피해 5%/10%/15%
            case WATER_WALKER -> 1.0; // 수면 보행 (레벨 무관)
            
            // === 방어구 인챈트 ===
            case SPEED_BOOST -> switch (level) { // 8%/15%/25%
                case 1 -> 0.08;
                case 2 -> 0.15;
                case 3 -> 0.25;
                default -> 0.08;
            };
            case DOUBLE_JUMP -> 1.0; // 1회 추가 점프
            case REGENERATION -> switch (level) { // 1/2/3 HP/3초
                case 1 -> 1.0;
                case 2 -> 2.0;
                case 3 -> 3.0;
                default -> 1.0;
            };
            case STEADFAST -> level * 15.0; // 넉백 저항 15%/30%/45%
            case VITALITY -> level * 2.0; // 최대 체력 +2/+4/+6 HP (1/2/3 하트)
            case NIGHT_VISION -> 1.0; // 야간 투시 (레벨 무관)
            
            // === 도구 인챈트 ===
            case VEIN_MINER -> switch (level) {
                case 1 -> 8;
                case 2 -> 16;
                case 3 -> 32;
                default -> 8;
            };
            case HARVEST -> 1.0; // 자동 재파종
            case TELEKINESIS -> 1.0; // 자동 수집
            case WISDOM -> switch (level) { // 직업 경험치: 5%/10%/15%/20%/25%
                case 1 -> 5.0;
                case 2 -> 10.0;
                case 3 -> 15.0;
                case 4 -> 20.0;
                case 5 -> 25.0;
                default -> 5.0;
            };
            case DURABILITY -> 50.0; // 비활성화됨
            case EXPERTISE -> level; // Fortune +1/+2/+3
            case LUCKY_HAND -> level * 5.0; // 희귀 드랍 5%/10%/15%
            case REPAIR_EFFICIENCY -> level * 10.0; // 수선 비용 감소 10%/20%/30%
            case DEEP_MINER -> level * 10.0; // 심층 채굴 10%/20%/30%
            case TREE_SPIRIT -> switch (level) { // 묘목 드롭 15%/25%/35%
                case 1 -> 15.0;
                case 2 -> 25.0;
                case 3 -> 35.0;
                default -> 15.0;
            };
            case ORE_SENSE -> switch (level) { // 탐지 범위 5/7/10블록
                case 1 -> 5.0;
                case 2 -> 7.0;
                case 3 -> 10.0;
                default -> 5.0;
            };
            
            // === 활 인챈트 ===
            case QUICK_DRAW -> level * 10.0; // 당기기 속도 10%/20%/30%
            case SNIPER -> level * 10.0; // 풀차지 피해 10%/20%/30%
            case HUNTERS_EYE -> switch (level) { // 동물 추가 피해 15%/25%/35%
                case 1 -> 15.0;
                case 2 -> 25.0;
                case 3 -> 35.0;
                default -> 15.0;
            };
            case FROST_ARROW -> level; // 둔화 1/2/3초
            
            // 바닐라 확장
            case UNBREAKING_EXTENDED -> switch (level) {
                case 4 -> 15.0; // IV = 15% 소모 확률
                case 5 -> 10.0; // V = 10% 소모 확률
                default -> 100.0 / (level + 1); // 바닐라 공식
            };
            case FORTUNE_EXTENDED -> switch (level) {
                case 4 -> 3.0; // IV = 평균 3배
                case 5 -> 4.0; // V = 평균 4배
                default -> 1.0 + (level * 0.4);
            };
        };
    }
    
    /**
     * 레벨별 상세 효과 설명 (Lore용)
     */
    public String getEffectDescription(int level) {
        return switch (this) {
            // === 무기 인챈트 ===
            case BLEED -> "§7출혈: §c" + (level + 1) + "초간 초당 " + String.format("%.1f", level * 0.5 + 0.5) + " 피해";
            case THUNDER_STRIKE -> "§7번개 확률: §e" + (int) getEffectValue(level) + "%";
            case FROST_ASPECT -> "§7둔화: §b" + level + "초간 Slowness " + (level == 1 ? "I" : "II");
            case VAMPIRE -> "§7흡혈: §c피해량의 " + (int) getEffectValue(level) + "% 회복";
            case TRUE_DAMAGE -> "§7방어 무시: §6" + (int) getEffectValue(level) + "% 고정 피해";
            case TRIUMPH -> "§7처치 시 회복: §a4 HP (PvP: 2 HP)";
            case LAST_STRIKE -> "§7특수 조건: §e+" + (int) getEffectValue(level) + "% 피해";
            case GRIEVOUS_WOUNDS -> "§7치유 감소: §c" + (int) getEffectValue(level) + "% (2초간)";
            case HUNTER -> "§7동물 추가 피해: §a+" + (int) getEffectValue(level) + "%";
            case PRECISION -> "§7치명타 피해: §e+" + (int) getEffectValue(level) + "%";
            
            // === 방어구 인챈트 ===
            case SPEED_BOOST -> "§7이동 속도: §b+" + (int) (getEffectValue(level) * 100) + "%";
            case DOUBLE_JUMP -> "§7공중 점프: §a1회 추가 점프 가능";
            case REGENERATION -> "§7재생: §a3초마다 +" + (int) getEffectValue(level) + " HP";
            case TOUGHNESS -> "§7CC 감소: §e" + (int) getEffectValue(level) + "% (PvP: " + (int) getPvPEffectValue(level) + "%)";
            case WATER_WALKER -> "§7수면 보행: §b물/용암 위 이동 가능";
            case STEADFAST -> "§7넉백 저항: §7+" + (int) getEffectValue(level) + "%";
            case VITALITY -> "§7최대 체력: §c+" + level + " 하트 (+" + (int) getEffectValue(level) + " HP)";
            case NIGHT_VISION -> "§7야간 투시: §e항상 활성화";
            
            // === 도구 인챈트 ===
            case VEIN_MINER -> "§7광맥 채굴: §6최대 " + (int) getEffectValue(level) + "개 동시 채굴";
            case HARVEST -> "§7자동 재파종: §a수확 시 자동으로 다시 심기";
            case TELEKINESIS -> "§7자동 수집: §e드랍 아이템 인벤토리로 이동";
            case WISDOM -> "§7직업 경험치: §a+" + (int) getEffectValue(level) + "%";
            case DURABILITY -> "§7내구도 보호: §750% 소모 감소";
            case EXPERTISE -> "§7Fortune 추가: §6+" + level;
            case LUCKY_HAND -> "§7희귀 드랍: §a+" + (int) getEffectValue(level) + "%";
            case DEEP_MINER -> "§7심층 채굴: §6Y<0 광석 +" + (int) getEffectValue(level) + "% 드롭";
            case TREE_SPIRIT -> "§7묘목 드롭: §a+" + (int) getEffectValue(level) + "% 확률";
            case ORE_SENSE -> "§7광맥 감지: §e" + (int) getEffectValue(level) + "블록 범위";
            case REPAIR_EFFICIENCY -> "§7수선 비용: §e-" + (int) getEffectValue(level) + "%";
            
            // === 활 인챈트 ===
            case QUICK_DRAW -> "§7당기기 속도: §b+" + (int) getEffectValue(level) + "%";
            case SNIPER -> "§7풀차지 피해: §c+" + (int) getEffectValue(level) + "%";
            case HUNTERS_EYE -> "§7동물 피해 (원거리): §a+" + (int) getEffectValue(level) + "%";
            case FROST_ARROW -> "§7명중 둔화: §b" + level + "초";
            
            // === 바닐라 확장 ===
            case UNBREAKING_EXTENDED -> "§7내구도 소모: §7" + (int) getEffectValue(level) + "% 확률";
            case FORTUNE_EXTENDED -> "§7드랍 배율: §6평균 " + String.format("%.1f", getEffectValue(level)) + "배";
        };
    }

    /**
     * PvP용 효과 값 (하이브리드화된 인챈트 전용)
     */
    public double getPvPEffectValue(int level) {
        return switch (this) {
            case VAMPIRE -> level * 5.0; // PvP: 레벨당 5% 흡혈
            case TRUE_DAMAGE -> switch (level) { // PvP: 7%/15%/25%
                case 1 -> 7.0;
                case 2 -> 15.0;
                case 3 -> 25.0;
                default -> 7.0;
            };
            case TRIUMPH -> 12.0; // PvP: 12 + 기여율% HP 회복
            case TOUGHNESS -> level * 10.0; // PvP: 레벨당 10% CC 감소
            default -> getEffectValue(level); // 그 외는 동일
        };
    }

    // ===============================================================
    // 인챈트 카테고리
    // ===============================================================

    public enum EnchantCategory {
        WEAPON("무기"),
        ARMOR("방어구"),
        TOOL("도구"),
        BOW("활"),
        UNIVERSAL("범용"),
        HYBRID("하이브리드");

        private final String displayName;

        EnchantCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
