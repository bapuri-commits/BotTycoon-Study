package kr.bapuri.tycoon.enhance.lamp;

import java.util.Arrays;
import java.util.List;

/**
 * LampEffect - 램프 효과 정의
 * 
 * 램프 사용 시 랜덤하게 부여되는 효과
 * 
 * v2.0 - Stage 8 Enhance System Formalization
 * - 희귀도 가중치: COMMON 50%, UNCOMMON 30%, RARE 14%, EPIC 4%, LEGENDARY 1%
 * - 천장 시스템: 150회 사용 시 LEGENDARY 확정
 * 
 * Phase 6 LITE:
 * - Hunter 전용 효과 비활성화 (BLUEZONE_RESIST, RESPAWN_ACCEL, HCL_EXP_BOOST, AUGMENT_LUCK)
 * - 커스텀 시스템 연관 효과 비활성화 (FISH_TREASURE, RARE_FISH, ORE_GRADE)
 */
public enum LampEffect {

    // ===============================================================
    // COMMON 효과 (50%) - 모든 도구에 적용 가능
    // ===============================================================
    
    // --- 가축 전리품 (무기 전용) ---
    POULTRY_LOOT("poultry_loot", "§f가금류 수집",
                 "닭/토끼 처치 시 %s%% 확률로 전리품 %s개 추가",
                 LampType.WEAPON_LAMP, Rarity.COMMON, 10, 50, 1, 2),
    
    LIVESTOCK_LOOT("livestock_loot", "§f가축 수집",
                   "돼지/양 처치 시 %s%% 확률로 전리품 %s개 추가",
                   LampType.WEAPON_LAMP, Rarity.COMMON, 10, 50, 1, 2),
    
    COW_LOOT("cow_loot", "§f소 수집",
             "소 처치 시 %s%% 확률로 전리품 %s개 추가",
             LampType.WEAPON_LAMP, Rarity.COMMON, 10, 50, 1, 2),
    
    // --- 몬스터 (무기 전용) ---
    MONSTER_LOOT("monster_loot", "§f몬스터 수집",
                 "몬스터 처치 시 %s%% 확률로 전리품 %s개 추가",
                 LampType.WEAPON_LAMP, Rarity.COMMON, 10, 50, 1, 2),
    
    // --- 도구(괭이) 전용 ---
    BONE_MEAL_DROP("bone_meal_drop", "§f뼛가루 수확",
                   "농작물 수확 시 %s%% 확률로 뼛가루 %s개 드랍",
                   LampType.TOOL_LAMP, Rarity.COMMON, 20, 80, 2, 5),
    
    // --- 범용 (유지) ---
    EXP_BOOST("exp_boost", "§f바닐라 경험치 수집",
              "바닐라 경험치 획득 시 %s%% 확률로 획득량 +%s 증가 (직업 경험치 제외)",
              LampType.UNIVERSAL_LAMP, Rarity.COMMON, 40, 80, 5, 20),

    // ===============================================================
    // 도구 효과 - 곡괭이 특화 (TOOL_LAMP)
    // ===============================================================
    
    // --- LEGENDARY ---
    AUTO_SMELT("auto_smelt", "§6자동 제련",
               "광물 블록 채굴 시 %s%% 확률로 주괴(구워진) 상태로 드랍",
               LampType.TOOL_LAMP, Rarity.LEGENDARY, 10, 100, 0, 0),
    
    // --- EPIC ---
    MULTI_MINE_2("multi_mine_2", "§5광역 채굴 II",
                 "블록 채굴 시 3×3 영역 동시 채굴",
                 LampType.TOOL_LAMP, Rarity.EPIC, 9.0),
    
    ORE_GRADE("ore_grade", "§5광물 등급",
              "광물 블록 채굴 시 %s%% 확률로 추가 드랍 (섬세한 손길 불가)",
              LampType.TOOL_LAMP, Rarity.EPIC, 10, 30, 1, 1),
    
    // --- RARE ---
    MULTI_MINE("multi_mine", "§9광역 채굴",
               "블록 채굴 시 상하좌우 1칸 동시 채굴",
               LampType.TOOL_LAMP, Rarity.RARE, 5.0),

    // ===============================================================
    // 도구 효과 - 괭이 특화 (TOOL_LAMP)
    // ===============================================================
    
    // --- 작물 보너스 (9단계, EV = 확률 × 개수) ---
    // LEGENDARY (EV 1.5)
    CROP_BONUS_9("crop_bonus_9", "§6풍요의 축복 IX",
                 "농작물 수확 시 50%% 확률로 작물 3개 추가 (EV 1.5)",
                 LampType.TOOL_LAMP, Rarity.LEGENDARY, 50, 50, 3, 3),
    
    // EPIC (EV 1.0~1.2)
    CROP_BONUS_8("crop_bonus_8", "§5풍요의 축복 VIII",
                 "농작물 수확 시 40%% 확률로 작물 3개 추가 (EV 1.2)",
                 LampType.TOOL_LAMP, Rarity.EPIC, 40, 40, 3, 3),
    
    CROP_BONUS_7("crop_bonus_7", "§5풍요의 축복 VII",
                 "농작물 수확 시 50%% 확률로 작물 2개 추가 (EV 1.0)",
                 LampType.TOOL_LAMP, Rarity.EPIC, 50, 50, 2, 2),
    
    // RARE (EV 0.6~0.8)
    CROP_BONUS_6("crop_bonus_6", "§9풍요의 축복 VI",
                 "농작물 수확 시 40%% 확률로 작물 2개 추가 (EV 0.8)",
                 LampType.TOOL_LAMP, Rarity.RARE, 40, 40, 2, 2),
    
    CROP_BONUS_5("crop_bonus_5", "§9풍요의 축복 V",
                 "농작물 수확 시 30%% 확률로 작물 2개 추가 (EV 0.6)",
                 LampType.TOOL_LAMP, Rarity.RARE, 30, 30, 2, 2),
    
    // UNCOMMON (EV 0.35~0.5)
    CROP_BONUS_4("crop_bonus_4", "§a풍요의 축복 IV",
                 "농작물 수확 시 50%% 확률로 작물 1개 추가 (EV 0.5)",
                 LampType.TOOL_LAMP, Rarity.UNCOMMON, 50, 50, 1, 1),
    
    CROP_BONUS_3("crop_bonus_3", "§a풍요의 축복 III",
                 "농작물 수확 시 35%% 확률로 작물 1개 추가 (EV 0.35)",
                 LampType.TOOL_LAMP, Rarity.UNCOMMON, 35, 35, 1, 1),
    
    // COMMON (EV 0.15~0.25)
    CROP_BONUS_2("crop_bonus_2", "§f풍요의 축복 II",
                 "농작물 수확 시 25%% 확률로 작물 1개 추가 (EV 0.25)",
                 LampType.TOOL_LAMP, Rarity.COMMON, 25, 25, 1, 1),
    
    CROP_BONUS_1("crop_bonus_1", "§f풍요의 축복 I",
                 "농작물 수확 시 15%% 확률로 작물 1개 추가 (EV 0.15)",
                 LampType.TOOL_LAMP, Rarity.COMMON, 15, 15, 1, 1),
    
    // --- 씨앗 보너스 (3단계) ---
    SEED_BONUS_3("seed_bonus_3", "§9씨앗 축복 III",
                 "농작물 수확 시 50%% 확률로 씨앗 3개 추가",
                 LampType.TOOL_LAMP, Rarity.RARE, 50, 50, 3, 3),
    
    SEED_BONUS_2("seed_bonus_2", "§a씨앗 축복 II",
                 "농작물 수확 시 35%% 확률로 씨앗 2개 추가",
                 LampType.TOOL_LAMP, Rarity.UNCOMMON, 35, 35, 2, 2),
    
    SEED_BONUS_1("seed_bonus_1", "§f씨앗 축복 I",
                 "농작물 수확 시 20%% 확률로 씨앗 1개 추가",
                 LampType.TOOL_LAMP, Rarity.COMMON, 20, 20, 1, 1),

    // ===============================================================
    // 도구 효과 - 삽 특화 (TOOL_LAMP)
    // ===============================================================
    
    SAND_TO_GLASS("sand_to_glass", "§b유리 세공",
                  "모래 블록 채굴 시 %s%% 확률로 유리 블록으로 드랍",
                  LampType.TOOL_LAMP, Rarity.RARE, 10, 100, 0, 0),

    // ===============================================================
    // 도끼 효과 (AXE_LAMP)
    // ===============================================================
    
    TREE_FELLER_3("tree_feller_3", "§5벌목꾼 III",
                  "나무 원목 채굴 시 위로 5칸까지 동시 채굴",
                  LampType.TOOL_LAMP, Rarity.EPIC, 5.0),
    
    TREE_FELLER_2("tree_feller_2", "§9벌목꾼 II",
                  "나무 원목 채굴 시 위로 4칸까지 동시 채굴",
                  LampType.TOOL_LAMP, Rarity.RARE, 4.0),
    
    TREE_FELLER_1("tree_feller_1", "§a벌목꾼 I",
                  "나무 원목 채굴 시 위로 3칸까지 동시 채굴",
                  LampType.TOOL_LAMP, Rarity.UNCOMMON, 3.0),

    // ===============================================================
    // 도구 효과 - 낚싯대 특화 (TOOL_LAMP)
    // ===============================================================
    
    FISH_SPEED("fish_speed", "§b빠른 입질",
               "낚시 시 물고기 무는 시간 %s%% 감소",
               LampType.TOOL_LAMP, Rarity.UNCOMMON, 20, 50, 0, 0),
    
    FISH_TREASURE("fish_treasure", "§e보물 탐색",
                  "낚시 보물 확률 %s%% 증가",
                  LampType.TOOL_LAMP, Rarity.RARE, 5, 20, 0, 0),
    
    RARE_FISH("rare_fish", "§9희귀 어종",
              "희귀 물고기 낚을 확률 %s%% 증가",
              LampType.TOOL_LAMP, Rarity.RARE, 5, 15, 0, 0),
    
    DOUBLE_FISH("double_fish", "§5쌍둥이 어획",
                "낚시 성공 시 %s%% 확률로 물고기 2배",
                LampType.TOOL_LAMP, Rarity.EPIC, 10, 50, 0, 0),
    
    JUNK_REDUCE("junk_reduce", "§a쓰레기 감소",
                "낚시 쓰레기 확률 %s%% 감소",
                LampType.TOOL_LAMP, Rarity.COMMON, 10, 30, 0, 0),
    
    FISH_EXP("fish_exp", "§a낚시 달인",
             "낚시 경험치 %s%% 증가",
             LampType.TOOL_LAMP, Rarity.UNCOMMON, 20, 50, 0, 0),
    
    AUTO_REEL("auto_reel", "§6자동 릴",
              "입질 시 자동으로 물고기를 끌어올림",
              LampType.TOOL_LAMP, Rarity.LEGENDARY, 100.0),
    
    RAIN_BOOST("rain_boost", "§b비의 축복",
               "비 올 때 모든 낚시 효과 %s%% 추가 증가",
               LampType.TOOL_LAMP, Rarity.RARE, 50, 100, 0, 0),

    // ===============================================================
    // 전투 효과 - COMMON (무기 전용)
    // ===============================================================
    
    LIFESTEAL("lifesteal", "§c생명력 흡수",
              "공격 시 고정 체력 %s 회복 (피해량 무관)",
              LampType.WEAPON_LAMP, Rarity.COMMON, 1, 10, 0, 0),
    
    CRIT_CHANCE("crit_chance", "§e치명타 확률",
                "치명타 확률 %s%% 증가",
                LampType.WEAPON_LAMP, Rarity.COMMON, 10.0, 40.0, 0, 0),
    
    CRIT_DAMAGE("crit_damage", "§e치명타 피해",
                "치명타 피해량 %s%% 증가",
                LampType.WEAPON_LAMP, Rarity.COMMON, 5.0, 35.0, 0, 0),
    
    // ===============================================================
    // 전투 효과 - COMMON (방어구 전용)
    // ===============================================================
    
    DODGE("dodge", "§7회피",
          "피격 회피 확률 %s%% 증가",
          LampType.ARMOR_LAMP, Rarity.COMMON, 10.0, 40.0, 0, 0),

    // ===============================================================
    // 전투 효과 - UNCOMMON/RARE (무기 전용)
    // ===============================================================
    
    BERSERKER("berserker", "§c광전사",
              "체력 %s%% 이하일 때 공격력 +%s 증가",
              LampType.WEAPON_LAMP, Rarity.UNCOMMON, 15, 45, 3, 7),
    
    CRIT_STACK("crit_stack", "§e치명타 중첩",
               "마지막 공격 후 7초간 치명타 피해 %s%% 증가 (최대 5중첩)",
               LampType.WEAPON_LAMP, Rarity.RARE, 5, 8, 0, 0),

    // ===============================================================
    // 전투 효과 - 무기 전용 (WEAPON_LAMP)
    // ===============================================================
    
    ATTACK_BOOST("attack_boost", "§c공격력 강화",
                 "공격력 +%s",
                 LampType.WEAPON_LAMP, Rarity.UNCOMMON, 1, 10, 0, 0),
    
    EXECUTE("execute", "§4처형",
            "%s%% 확률로 체력 8%% 이하 적 즉시 처치 (보스 불가)",
            LampType.WEAPON_LAMP, Rarity.EPIC, 10, 80, 0, 0),
    
    POISON_BLADE("poison_blade", "§2독날",
                 "공격 시 %s초간 독 효과 부여",
                 LampType.WEAPON_LAMP, Rarity.UNCOMMON, 2, 5, 0, 0),
    
    PHANTOM_STRIKE("phantom_strike", "§7유령 일격",
                   "%s%% 확률로 방어력 무시 피해",
                   LampType.WEAPON_LAMP, Rarity.EPIC, 3, 7, 0, 0),

    // ===============================================================
    // 전투 효과 - 방어구 전용 (ARMOR_LAMP)
    // ===============================================================
    
    HEALTH_BOOST("health_boost", "§c체력 강화",
                 "최대 체력 +%s칸",
                 LampType.ARMOR_LAMP, Rarity.UNCOMMON, 3, 10, 0, 0),
    
    REGEN_PROC("regen_proc", "§e재생 발동",
               "피격 시 %s%% 확률로 재생 I 2초 부여",
               LampType.ARMOR_LAMP, Rarity.COMMON, 5, 15, 0, 0),
    
    IRON_WILL("iron_will", "§7강철 의지",
              "받는 피해 %s%% 감소",
              LampType.ARMOR_LAMP, Rarity.RARE, 10, 30, 0, 0),
    
    THORNS_AURA("thorns_aura", "§a가시 오라",
                "피격 시 공격자에게 %s%% 피해 반사",
                LampType.ARMOR_LAMP, Rarity.EPIC, 10, 20, 0, 0),
    
    GUARDIAN_ANGEL("guardian_angel", "§f수호천사",
                   "5초간 피해 없을 시 초당 %s%% 체력 회복",
                   LampType.ARMOR_LAMP, Rarity.RARE, 3, 10, 0, 0),

    // ===============================================================
    // LEGENDARY 효과
    // ===============================================================
    
    GIANT_SLAYER("giant_slayer", "§4거인 학살자",
                 "본인과 적의 체력 차이 기반 %s~%s%% 추가 피해",
                 LampType.WEAPON_LAMP, Rarity.LEGENDARY, 0, 35, 0, 0),
    
    PHOENIX_BLESSING("phoenix_blessing", "§6불사조의 축복",
                     "치명상 시 %s초마다 한 번 부활 (체력 %s%%)",
                     LampType.ARMOR_LAMP, Rarity.LEGENDARY, 90, 120, 25, 35),
    
    MIDAS_TOUCH("midas_touch", "§6미다스의 손",
                "광물 채굴 시 %s%% 확률로 %s~%s BD 획득",
                LampType.TOOL_LAMP, Rarity.LEGENDARY, 20, 20, 300, 1000),

    // ===============================================================
    // 범용 효과 (UNIVERSAL_LAMP)
    // ===============================================================
    
    LUCK_AURA("luck_aura", "§a행운 오라",
              "모든 확률 기반 효과 발동률 %s%% 증가",
              LampType.UNIVERSAL_LAMP, Rarity.EPIC, 7, 20, 0, 0),
    
    // v2.3: 커스텀 인챈트로 이동 (CustomEnchant.DURABILITY)
    @Deprecated
    DURABILITY_MASTER("durability_master", "§7내구도 달인",
                      "내구도 소모 %s%% 감소",
                      LampType.UNIVERSAL_LAMP, Rarity.RARE, 30, 60, 0, 0),
    
    EXPLOIT_WEAKNESS("exploit_weakness", "§9약점 간파",
                     "이미 피해를 입은 적에게 %s%% 추가 데미지",
                     LampType.WEAPON_LAMP, Rarity.RARE, 5, 10, 0, 0),
    
    EXP_MASTER("exp_master", "§a경험치 달인",
               "모든 경험치 획득량 항상 +%s%% 증가",
               LampType.UNIVERSAL_LAMP, Rarity.RARE, 15, 40, 0, 0),

    // ===============================================================
    // 헌터 PvP 전용 효과 (LITE에서 비활성화)
    // ===============================================================
    
    BLUEZONE_RESIST("bluezone_resist", "§b자기장 저항",
                    "자기장 데미지 %s%% 감소",
                    LampType.ARMOR_LAMP, Rarity.RARE, 10, 25, 0, 0),
    
    RESPAWN_ACCEL("respawn_accel", "§a리스폰 가속",
                  "리스폰 대기시간 %s%% 감소",
                  LampType.ARMOR_LAMP, Rarity.UNCOMMON, 20, 40, 0, 0),
    
    KILL_MOMENTUM("kill_momentum", "§c킬 모멘텀",
                  "킬(몹/플레이어) 후 %s초간 이동속도 +30%%",
                  LampType.WEAPON_LAMP, Rarity.EPIC, 3, 6, 0, 0),
    
    COMBAT_INSTINCT("combat_instinct", "§4전투 본능",
                    "전투 스택당 공격력 %s%% 증가 (근접2/원거리1, 최대10스택, 8초)",
                    LampType.WEAPON_LAMP, Rarity.RARE, 1, 2, 0, 0),

    // ===============================================================
    // HCL 부스트 효과 (LITE에서 비활성화)
    // ===============================================================
    
    HCL_EXP_BOOST("hcl_exp_boost", "§dHCL 경험치 부스트",
                  "HCL 경험치 획득량 %s%% 증가",
                  LampType.UNIVERSAL_LAMP, Rarity.UNCOMMON, 10, 25, 0, 0),
    
    AUGMENT_LUCK("augment_luck", "§5증강 행운",
                 "증강 선택 시 4개 중 선택 가능",
                 LampType.ARMOR_LAMP, Rarity.EPIC, 1.0),

    // ===============================================================
    // 기동성 효과
    // ===============================================================
    
    SPRINT_BURST("sprint_burst", "§b순간 가속",
                 "스프린트 시작 후 %s초간 이동속도 +50%% (기본 이속만, 중첩X)",
                 LampType.ARMOR_LAMP, Rarity.RARE, 2, 4, 0, 0),
    
    ENHANCED_JUMP("enhanced_jump", "§a강화 점프",
                  "점프 높이 증가 + 낙하 피해 %s%% 감소 + 착지 효과",
                  LampType.ARMOR_LAMP, Rarity.UNCOMMON, 20, 50, 0, 0),

    // ===============================================================
    // 신규 무기 효과 (WEAPON_LAMP)
    // ===============================================================
    
    CHAIN_LIGHTNING("chain_lightning", "§9연쇄 번개",
                    "공격 시 %s%% 확률로 주변 %s명에게 번개 연쇄",
                    LampType.WEAPON_LAMP, Rarity.EPIC, 10, 25, 2, 3),
    
    ABSORB("absorb", "§a흡수",
           "킬 시 적 최대 체력의 %s%% 일시 보호막 획득 (%s초)",
           LampType.WEAPON_LAMP, Rarity.RARE, 10, 25, 5, 8),
    
    DEATH_MARK("death_mark", "§4죽음의 표식",
               "3회 연속 공격 시 %s%% 추가 피해",
               LampType.WEAPON_LAMP, Rarity.RARE, 15, 30, 0, 0),
    
    AMBUSH("ambush", "§7기습",
           "은신/스프린트 중 첫 공격 시 %s%% 추가 피해",
           LampType.WEAPON_LAMP, Rarity.UNCOMMON, 20, 40, 0, 0),
    
    COMBO("combo", "§e연속 공격",
          "1.5초 내 연속 공격 시 %s%% 피해 증가 (최대 %s중첩)",
          LampType.WEAPON_LAMP, Rarity.UNCOMMON, 3, 6, 4, 5),

    // ===============================================================
    // 신규 방어구 효과 (ARMOR_LAMP)
    // ===============================================================
    
    SPEED_SACRIFICE("speed_sacrifice", "§c속도 희생",
                    "이동속도 %s%% 감소, 받는 피해 %s%% 감소",
                    LampType.ARMOR_LAMP, Rarity.RARE, 10, 20, 15, 25),
    
    RAGE("rage", "§c격노",
         "체력 %s%% 이하일 때 공격속도 %s%% 증가",
         LampType.ARMOR_LAMP, Rarity.UNCOMMON, 30, 50, 20, 40),
    
    STEALTH("stealth", "§7은신",
            "5초간 피격 없을 시 반투명 + 적 감지거리 %s%% 감소",
            LampType.ARMOR_LAMP, Rarity.RARE, 30, 50, 0, 0),
    
    LAST_STAND("last_stand", "§6최후의 저항",
               "치명상 시 %s초간 무적 + 이동속도 %s%% 증가 (쿨타임 120초)",
               LampType.ARMOR_LAMP, Rarity.LEGENDARY, 2, 3, 30, 50),
    
    SHATTER_RESIST("shatter_resist", "§7파쇄 저항",
                   "폭발 피해 %s%% 감소",
                   LampType.ARMOR_LAMP, Rarity.UNCOMMON, 15, 35, 0, 0),
    
    COUNTER("counter", "§e반격",
            "피격 시 %s%% 확률로 0.5초간 공격력 %s%% 증가",
            LampType.ARMOR_LAMP, Rarity.RARE, 15, 30, 25, 50),

    // ===============================================================
    // 신규 도구 효과 (TOOL_LAMP)
    // ===============================================================
    
    GOLDEN_TOUCH("golden_touch", "§6황금 손길",
                 "채굴/수확 시 %s%% 확률로 금 조각 %s개 획득",
                 LampType.TOOL_LAMP, Rarity.RARE, 5, 15, 1, 3),
    
    BOUNTIFUL("bountiful", "§a풍년",
              "수확 시 %s%% 확률로 씨앗 소모 없이 자동 재파종",
              LampType.TOOL_LAMP, Rarity.UNCOMMON, 20, 50, 0, 0),
    
    CURRENT_SENSE("current_sense", "§b조류 감지",
                  "낚시 시 물고기가 무는 시간 추가 %s%% 감소",
                  LampType.TOOL_LAMP, Rarity.UNCOMMON, 15, 30, 0, 0),
    
    LUMBER_TRADER("lumber_trader", "§6목재상",
                  "원목 채굴 시 %s%% 확률로 추가 %s개 드랍",
                  LampType.TOOL_LAMP, Rarity.UNCOMMON, 10, 30, 1, 2),
    
    // --- 신규 도구 램프 효과 ---
    CROP_COMBO("crop_combo", "§e수확 콤보",
               "연속 수확 시 스택당 +%s%% 추가 드롭 (최대 %s스택, 3초 유지)",
               LampType.TOOL_LAMP, Rarity.RARE, 2, 5, 8, 12),
    
    SILK_TOUCH_PRO("silk_touch_pro", "§5정밀 채굴",
                   "섬세한 손길 사용 시 %s%% 확률로 경험치도 획득",
                   LampType.TOOL_LAMP, Rarity.EPIC, 10, 50, 0, 0),
    
    COMPOST_BONUS("compost_bonus", "§a퇴비 생성",
                  "잡초/씨앗 획득 시 %s%% 확률로 뼛가루로 변환",
                  LampType.TOOL_LAMP, Rarity.UNCOMMON, 20, 50, 0, 0),

    // ===============================================================
    // 신규 활 효과 (WEAPON_LAMP - 활/석궁 전용)
    // ===============================================================
    
    EXPLOSIVE_ARROW("explosive_arrow", "§c폭발 화살",
                    "명중 시 %s%% 확률로 폭발 (반경 %s블록)",
                    LampType.WEAPON_LAMP, Rarity.EPIC, 10, 25, 2, 2),
    
    SPLIT_ARROW("split_arrow", "§9분열 화살",
                "발사 시 %s%% 확률로 화살 %s개로 분열",
                LampType.WEAPON_LAMP, Rarity.RARE, 15, 35, 2, 3),
    
    VAMPIRIC_ARROW("vampiric_arrow", "§c흡혈 화살",
                   "화살 명중 시 피해의 %s%% 체력 회복",
                   LampType.WEAPON_LAMP, Rarity.UNCOMMON, 5, 15, 0, 0),
    
    POISON_ARROW("poison_arrow", "§2독 화살",
                 "명중 시 %s초간 독 효과 부여",
                 LampType.WEAPON_LAMP, Rarity.COMMON, 2, 5, 0, 0),
    
    THUNDER_ARROW("thunder_arrow", "§e번개 화살",
                  "명중 시 %s%% 확률로 번개 소환",
                  LampType.WEAPON_LAMP, Rarity.LEGENDARY, 15, 30, 0, 0),
    
    PIERCING_SHOT("piercing_shot", "§7관통 사격",
                  "%s%% 확률로 방어력 무시 피해",
                  LampType.WEAPON_LAMP, Rarity.RARE, 10, 25, 0, 0),

    // ===============================================================
    // 신규 효과 - 방어구 전용
    // ===============================================================
    
    FIRST_AID("first_aid", "§c응급 처치",
              "체력 %s%% 이하일 때 재생 I 효과 자동 부여",
              LampType.ARMOR_LAMP, Rarity.UNCOMMON, 20, 35, 0, 0),
    
    // ===============================================================
    // 신규 효과 - 범용 (유지)
    // ===============================================================
    
    TREASURE_HUNTER("treasure_hunter", "§6보물 사냥꾼",
                    "희귀 드랍 확률 %s%% 증가",
                    LampType.UNIVERSAL_LAMP, Rarity.RARE, 5, 15, 0, 0),
    
    // ===============================================================
    // 신규 효과 - 무기 전용
    // ===============================================================
    
    FOCUS("focus", "§d집중",
          "5초간 공격/피격 없을 시 다음 공격 치명타 확률 %s%% 증가",
          LampType.WEAPON_LAMP, Rarity.UNCOMMON, 20, 40, 0, 0),

    // ===============================================================
    // 비활성 효과 (나중에 사용 예정)
    // ===============================================================
    
    @Deprecated
    MAGNETIC_FIELD("magnetic_field", "§d자기장",
                   "드랍 아이템이 %s블록 내에서 자동 수집",
                   LampType.TOOL_LAMP, Rarity.UNCOMMON, 3, 5, 0, 0);

    // ===============================================================
    // 필드 정의
    // ===============================================================

    private final String id;
    private final String displayName;
    private final String descriptionFormat;
    private final LampType requiredLampType;
    private final Rarity rarity;
    
    // 범위 값 (적용 시 고정 롤)
    private final double minValue1;
    private final double maxValue1;
    private final int minValue2;
    private final int maxValue2;

    // 단일 값 생성자
    LampEffect(String id, String displayName, String description,
               LampType requiredLampType, Rarity rarity, double fixedValue) {
        this(id, displayName, description, requiredLampType, rarity, 
             fixedValue, fixedValue, 0, 0);
    }

    // 범위 값 생성자
    LampEffect(String id, String displayName, String descriptionFormat,
               LampType requiredLampType, Rarity rarity,
               double minValue1, double maxValue1, int minValue2, int maxValue2) {
        this.id = id;
        this.displayName = displayName;
        this.descriptionFormat = descriptionFormat;
        this.requiredLampType = requiredLampType;
        this.rarity = rarity;
        this.minValue1 = minValue1;
        this.maxValue1 = maxValue1;
        this.minValue2 = minValue2;
        this.maxValue2 = maxValue2;
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

    public String getDescriptionFormat() {
        return descriptionFormat;
    }

    /**
     * 기본 설명 (범위 표시)
     */
    public String getDescription() {
        try {
            if (minValue2 > 0 || maxValue2 > 0) {
                String v1 = formatRange(minValue1, maxValue1);
                String v2 = formatRange(minValue2, maxValue2);
                return String.format(descriptionFormat, v1, v2);
            } else if (minValue1 != maxValue1) {
                String v1 = formatRange(minValue1, maxValue1);
                return String.format(descriptionFormat, v1);
            }
            return descriptionFormat;
        } catch (Exception e) {
            return displayName + " 효과";
        }
    }
    
    private String formatRange(double min, double max) {
        if (min == max) {
            return formatDouble(min);
        }
        return formatDouble(min) + "~" + formatDouble(max);
    }
    
    private String formatRange(int min, int max) {
        if (min == max) {
            return String.valueOf(min);
        }
        return min + "~" + max;
    }
    
    private static String formatDouble(double value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        }
        return String.format("%.1f", value);
    }
    
    /**
     * 롤된 값으로 설명 생성 (특정 값 사용)
     */
    public String getDescription(double value1, int value2) {
        try {
            if (minValue2 > 0 || maxValue2 > 0) {
                return String.format(descriptionFormat, formatDouble(value1), value2);
            } else if (minValue1 != maxValue1) {
                return String.format(descriptionFormat, formatDouble(value1));
            }
            return descriptionFormat;
        } catch (Exception e) {
            return displayName + " 효과";
        }
    }

    public LampType getRequiredLampType() {
        return requiredLampType;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public double getMinValue1() {
        return minValue1;
    }

    public double getMaxValue1() {
        return maxValue1;
    }

    public int getMinValue2() {
        return minValue2;
    }

    public int getMaxValue2() {
        return maxValue2;
    }

    /**
     * 대표 효과값 (범위의 평균)
     */
    public double getEffectValue() {
        return (minValue1 + maxValue1) / 2.0;
    }

    /**
     * 값이 범위인지 확인
     */
    public boolean hasRange() {
        return minValue1 != maxValue1 || minValue2 != maxValue2;
    }

    /**
     * 비활성 효과인지 확인
     * 
     * LITE Version:
     * - BLUEZONE_RESIST, RESPAWN_ACCEL, HCL_EXP_BOOST, AUGMENT_LUCK: 헌터 시스템 종속
     * - FISH_TREASURE, RARE_FISH: 커스텀 물고기/보물 연관 (일단 비활성화)
     * - ORE_GRADE: 바닐라 Fortune 확장으로 이동 예정
     * - COMBAT_INSTINCT: CombatTagManager 필요 (비활성화)
     */
    public boolean isDisabled() {
        return this == MAGNETIC_FIELD || 
               this == DURABILITY_MASTER ||
               // LITE: 헌터/듀얼 전용 효과 비활성화
               this == BLUEZONE_RESIST ||
               this == RESPAWN_ACCEL ||
               this == HCL_EXP_BOOST ||
               this == AUGMENT_LUCK ||
               this == COMBAT_INSTINCT ||
               // LITE: 커스텀 시스템 연관 비활성화
               this == FISH_TREASURE ||
               this == RARE_FISH ||
               this == ORE_GRADE;
    }
    
    /**
     * 도구 램프인지 확인
     */
    public boolean isToolLamp() {
        return requiredLampType == LampType.TOOL_LAMP;
    }

    // ===============================================================
    // 유틸리티
    // ===============================================================

    /**
     * ID로 효과 찾기
     */
    public static LampEffect fromId(String id) {
        if (id == null) return null;
        for (LampEffect effect : values()) {
            if (effect.id.equalsIgnoreCase(id)) {
                return effect;
            }
        }
        return null;
    }

    /**
     * 특정 램프 타입에 사용 가능한 효과 목록 (비활성 제외)
     */
    public static List<LampEffect> getEffectsForLampType(LampType lampType) {
        return Arrays.stream(values())
                .filter(e -> !e.isDisabled())
                .filter(e -> e.requiredLampType == lampType || e.requiredLampType == LampType.UNIVERSAL_LAMP)
                .toList();
    }

    /**
     * 특정 희귀도의 효과 목록 (비활성 제외)
     */
    public static List<LampEffect> getEffectsByRarity(Rarity rarity) {
        return Arrays.stream(values())
                .filter(e -> !e.isDisabled())
                .filter(e -> e.rarity == rarity)
                .toList();
    }

    /**
     * 활성화된 모든 효과
     */
    public static List<LampEffect> getActiveEffects() {
        return Arrays.stream(values())
                .filter(e -> !e.isDisabled())
                .toList();
    }

    // ===============================================================
    // 희귀도 enum
    // ===============================================================

    public enum Rarity {
        COMMON("§f일반", 50.0, "§f"),
        UNCOMMON("§a고급", 30.0, "§a"),
        RARE("§9희귀", 14.0, "§9"),
        EPIC("§5영웅", 4.0, "§5"),
        LEGENDARY("§6전설", 1.0, "§6§l");

        private final String displayName;
        private final double weight;
        private final String colorCode;

        Rarity(String displayName, double weight, String colorCode) {
            this.displayName = displayName;
            this.weight = weight;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getWeight() {
            return weight;
        }

        public String getColorCode() {
            return colorCode;
        }

        /**
         * 가중치 기반 총합 (확률 계산용)
         */
        public static double getTotalWeight() {
            double total = 0;
            for (Rarity r : values()) {
                total += r.weight;
            }
            return total;
        }
    }
}
