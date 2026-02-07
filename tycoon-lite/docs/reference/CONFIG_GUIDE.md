# TycoonLite 설정 가이드

> 이 문서는 TycoonLite의 모든 설정 파일에 대한 상세 가이드입니다.

---

## 목차

1. [config.yml](#configyml) - 메인 설정
2. [jobs.yml](#jobsyml) - 직업 설정
3. [codex.yml](#codexyml) - 도감 설정
4. [achievements.yml](#achievementsyml) - 업적 설정
5. [titles.yml](#titlesyml) - 칭호 설정
6. [shops.yml](#shopsyml) - 상점 설정
7. [antiexploit.yml](#antiexploityml) - 악용 방지 설정

---

## config.yml

메인 설정 파일입니다. 대부분의 시스템 설정이 이 파일에 있습니다.

### 시스템 활성화

```yaml
systems:
  jobs:
    enabled: true      # 직업 시스템
  dungeon:
    enabled: false     # 던전 시스템 (비활성화)
  chest:
    enabled: false     # 보물상자 (비활성화)
  refinery:
    enabled: false     # 제련 시스템 (비활성화)
```

### 플레이어 데이터

```yaml
playerdata:
  auto-save:
    enabled: true
    interval-minutes: 5        # 저장 주기 (분)
  
  backup:
    enabled: true
    interval-minutes: 30       # 스냅샷 주기 (분)
    max-snapshots: 10          # 플레이어당 최대 스냅샷
    backup-on-quit: true       # 퇴장 시 백업

inventoryBackup:
  enabled: true
  intervalSeconds: 60          # 인벤토리 백업 주기 (초)
  maxBackupsPerPlayer: 30      # 플레이어당 최대 백업
```

### 화폐 시스템

```yaml
currency:
  bd:
    displayName: "BD"
    suffix: "원"
  
  bottcoin:
    displayName: "BottCoin"
    suffix: "BC"
    rewards:
      codex:
        common: 1              # 일반 아이템 등록 보상
        rare: 5                # 희귀 아이템 등록 보상
      dungeon:
        clear: 10              # 던전 클리어 보상
        bossKill: 5            # 보스 처치 보상
```

### 월드 설정

```yaml
worlds:
  # 타운 (안전 구역)
  town:
    name: "world"              # Bukkit 월드 이름
    pvp: false
    keepInventory: false
    difficulty: PEACEFUL
    spawnLocation: "1.5,1,15.5,0,0"   # x,y,z,yaw,pitch
    portalIsolation: true      # 네더/엔드 포탈 분리
    plotPrice: 100000          # 플롯 가격

  # 야생 (자원 파밍)
  wild:
    name: "world_wild"
    pvp: true
    keepInventory: false
    difficulty: HARD
    resetDays: 7               # 리셋 주기 (일)
    villagerTrading: false
    pvpDamageMultiplier: 0.1   # PvP 10% 데미지
    deathChestDurationSeconds: 600
    nether: "world_wild_nether"
    the_end: "world_wild_the_end"
```

### 야생 리셋 설정

```yaml
wildReset:
  enabled: false               # 자동 리셋 (기본 비활성화)
  intervalDays: 7
  announceMinutes: [10, 5, 1]  # 사전 알림
  countdownSeconds: 10
  backupEnabled: true
  backupFolder: "plugins/TycoonLite/backups/wild"
  keepBackups: 10

wildSpawn:
  npcEnabled: true
  returnNpcName: "§e[귀환] §f마을로 돌아가기"
  returnTarget: "town_spawn"
  multipleSpawns:
    enabled: true
    count: 5                   # 스폰 포인트 개수
    minDistance: 500           # 포인트 간 최소 거리
    spreadRadius: 2000
```

### NPC 텔레포트

```yaml
npcTeleport:
  enabled: true
  
  # NPC 이름 → 포인트 ID 매핑
  npcMappings:
    "야생 안내원": "town_to_wild"
    "귀환 안내원": "wild_to_town"
    "§e[귀환] §f마을로 돌아가기": "wild_to_town"
  
  # 텔레포트 포인트 정의
  points:
    town_to_wild:
      type: TELEPORT
      sourceWorld: "world"
      targetWorld: "world_wild"
      targetLocation: "WORLD_SPAWN"
      message: "§c야생으로 이동합니다."
      cooldownSeconds: 5
```

### 상점 NPC

```yaml
shopNpc:
  enabled: true
  debug: false
  
  # NPC 이름 키워드 → 상점 ID
  mappings:
    광부 상인: miner
    농부 상인: farmer
    어부 상인: fisher
    강화 상인: enhance
    잡화 상인: general
```

### 강화 시스템

```yaml
enhance:
  showEnchantMessages: true    # 효과 발동 메시지
  
  # 커스텀 인챈트
  enchants:
    bleed:
      enabled: true
    thunder_strike:
      enabled: true
    vampire:
      enabled: true
    # ... (기타 인챈트)
  
  # 램프 시스템
  lamps:
    types:
      weapon: { price: 10000 }
      armor: { price: 10000 }
      tool: { price: 8000 }
      universal: { price: 25000 }
    effects:
      soul_reaper: { enabled: true, weight: 2.0 }
      berserker: { enabled: true, weight: 8.0 }
      # ... (기타 효과)
  
  # 강화 (+0 ~ +100)
  upgrade:
    enabled: true
    baseCost: 230              # 기본 비용
    costMultiplier: 1.16       # 레벨당 비용 배율
    ranges:
      "0-10":
        successRate: 0.90
        downgradeRate: 0
        destroyRate: 0
      "11-30":
        successRate: 0.60
        downgradeRate: 0.05
        destroyRate: 0
      "31-50":
        successRate: 0.30
        downgradeRate: 0.15
        destroyRate: 0.02
      "51-80":
        successRate: 0.15
        downgradeRate: 0.20
        destroyRate: 0.05
      "81-100":
        successRate: 0.05
        downgradeRate: 0.25
        destroyRate: 0.10
```

### 동적 가격

```yaml
dynamicPrice:
  updateIntervalMinutes: 10
  maxPriceChangePercent: 70    # 기준가 ±70%
  maxChangePerUpdate: 10       # 한 번에 ±10%
  sellSpreadRatio: 0.5         # 매입가 = 판매가 × 0.5
  
  volumeInfluence:
    tier1Max: 10
    tier1Influence: 1.0        # 1~10개: 100%
    tier2Max: 50
    tier2Influence: 0.5        # 11~50개: 50%
    tier3Max: 100
    tier3Influence: 0.2        # 51~100개: 20%
    tier4Influence: 0.1        # 100+: 10%
```

### 거래 시스템

```yaml
trade:
  enabled: true
  requestTimeout: 60           # 요청 대기 (초)
  cooldown:
    global: 60                 # 전체 쿨다운
    perTarget: 120             # 대상별 쿨다운
  currency:
    bdPresets: [100, 1000, 10000, 100000]
    bcPresets: [1, 5, 10, 50]
  itemSlots: 12                # 거래 슬롯 수
```

### 악용 방지

```yaml
villagerTrade:
  enabled: true
  blockedWorlds: [town, wild, dungeon]
  freeTradeLimit: 10
  tradeCosts:
    tier1: 5000                # 11~15회
    tier2: 10000               # 16~20회
    tier3: 15000               # 21~30회
    tier4: 25000               # 31~50회
    tier5: 50000               # 51회+

xrayAnalyzer:
  enabled: true
  enabledWorlds: [wild]
  diamondFindRateThreshold: 0.03
  straightLineMineThreshold: 25
  rapidOreFindThreshold: 4

afkDampen:
  enabled: true
  afkAfterSeconds: 480         # 8분
  dampenWorlds: [wild]
  afkDropMultiplier: 0.0
  afkXpMultiplier: 0.0
```

---

## jobs.yml

직업 시스템 전용 설정 파일입니다.

### 글로벌 설정

```yaml
global:
  tier2_jobs_enabled: false    # Tier 2 비활성화 (Lite)
  exp_from_actions: true       # 행동으로 경험치
  exp_from_sales: true         # 판매로 경험치

pricing_policy:
  no_job_sell_multiplier: 0.7  # 무직업 판매 70%
  no_job_buy_multiplier: 1.3   # 무직업 구매 130%
  level_bonus_percent: 0.5     # 레벨당 +0.5%
```

### 경험치 공식

```yaml
# 공식: levelUpExp = base + (level² × multiplier)
exp_formula:
  segments:
    - min_level: 1
      max_level: 20
      base: 100
      multiplier: 10.0         # Lv1→2: 110 XP
    - min_level: 21
      max_level: 40
      base: 200
      multiplier: 25.0
    - min_level: 41
      max_level: 80
      base: 400
      multiplier: 50.0
    - min_level: 81
      max_level: 100
      base: 800
      multiplier: 80.0
```

### 공통 설정

```yaml
common:
  maxLevel:
    tier1: 100
    tier2: 70
  defaultLevelBonusPercent: 7.0
  defaultUnlock:
    codex_count: 25            # 도감 25종
    money: 20000               # 20,000 BD
```

### 광부 (Miner)

```yaml
miner:
  displayName: "광부"
  maxLevel: 100
  levelBonusPercent: 7.0
  
  unlock:
    codex_count: 25
    money: 20000
  
  grades:
    GRADE_1:
      requiredLevel: 1
      requiredBD: 0
    GRADE_2:
      requiredLevel: 20
      requiredBD: 10000
      requiredTotalSales: 50000
    GRADE_3:
      requiredLevel: 40
      requiredBD: 50000
      requiredTotalSales: 200000
    GRADE_4:
      requiredLevel: 80
      requiredBD: 200000
      requiredTotalSales: 1000000
  
  expRewards:
    COAL_ORE: 3
    IRON_ORE: 7
    GOLD_ORE: 12
    DIAMOND_ORE: 30
    ANCIENT_DEBRIS: 80
  
  oxidation:
    enabled: true
    check_interval_minutes: 20
    oxidation_chance: 0.0569   # 5.69%
    farm_prevention_radius: 4
  
  basePrices:
    COAL: 5
    IRON_INGOT: 20
    GOLD_INGOT: 50
    DIAMOND: 150
```

### 농부 (Farmer)

```yaml
farmer:
  displayName: "농부"
  maxLevel: 100
  levelBonusPercent: 5.5       # 광부보다 낮음
  
  maxFarmlandPerChunk: 196     # 청크당 농지 제한
  
  gradeDropChance:
    normal: 70.0
    prime: 25.0
    trophy: 5.0
  
  grades:
    GRADE_1: { requiredLevel: 1 }
    GRADE_2: { requiredLevel: 25, requiredMoney: 50000 }
    GRADE_3: { requiredLevel: 50, requiredMoney: 200000 }
    GRADE_4: { requiredLevel: 90, requiredMoney: 1000000 }
  
  expRewards:
    WHEAT: 5
    CARROT: 5
    POTATO: 5
    MELON_SLICE: 3
    NETHER_WART: 8
  
  basePrices:
    WHEAT: 3
    CARROT: 5
    MELON_SLICE: 2
    NETHER_WART: 12
```

### 어부 (Fisher)

```yaml
fisher:
  displayName: "어부"
  maxLevel: 100
  levelBonusPercent: 7.0
  
  townCastsPerHour: 150
  wildCastsPerHour: 140
  
  grades:
    GRADE_1: { requiredLevel: 1 }
    GRADE_2: { requiredLevel: 15, requiredMoney: 50000 }
    GRADE_3: { requiredLevel: 35, requiredMoney: 200000 }
    GRADE_4: { requiredLevel: 70, requiredMoney: 1000000 }
  
  # Town 희귀도 분포
  townDistribution:
    GRADE_1:
      common: 40
      uncommon: 35
      rare: 20
      epic: 4
      legendary: 1
  
  # Wild 희귀도 분포 (더 좋음)
  wildDistribution:
    GRADE_1:
      common: 35
      uncommon: 32
      rare: 25
      epic: 6
      legendary: 2
  
  # 천장 시스템
  pity:
    GRADE_1:
      rare: 40
      epic: 120
      legendary: 300
  
  expRewards:
    COD: 5
    SALMON: 6
    TROPICAL_FISH: 10
  
  basePrices:
    COD: 10
    SALMON: 15
    TROPICAL_FISH: 30
```

### 직업 업그레이드

```yaml
upgrades:
  maxLevel: 5
  
  prices:
    1: { bd: 50000, bottcoin: 0 }
    2: { bd: 120000, bottcoin: 0 }
    3: { bd: 250000, bottcoin: 10 }
    4: { bd: 400000, bottcoin: 15 }
    5: { bd: 600000, bottcoin: 25 }
  
  # 공통 업그레이드
  common:
    sell_cap_increase:
      displayName: "판매 한도 증가"
      effectPerLevel: 10
    exp_boost:
      displayName: "경험치 부스트"
      effectPerLevel: 0.05     # +5%/레벨
  
  # 광부 전용
  miner:
    refine_efficiency:
      displayName: "제련 효율"
      effectPerLevel: 0.05     # -5%/레벨
    ore_detection:
      displayName: "광맥 감지"
      effectPerLevel: 2        # +2블록/레벨
```

---

## codex.yml

도감 시스템 설정입니다. config.yml의 `codex:` 섹션에 정의됩니다.

### 카테고리 구조

```yaml
codex:
  # 카테고리명:
  #   required: N       # 등록에 필요한 아이템 수
  #   consume: true     # 소비 여부 (true=소비, false=소지만)
  #   items: [...]      # 아이템 목록 (Material 이름)
  
  자연블록:
    required: 10
    consume: true
    items:
      - GRASS_BLOCK
      - DIRT
      - STONE
      - DEEPSLATE
      - SAND
      - GRAVEL
  
  광석:
    required: 10
    consume: true
    items:
      - COAL_ORE
      - IRON_ORE
      - GOLD_ORE
      - DIAMOND_ORE
      - EMERALD_ORE
  
  # 희귀 아이템 (소비 안함)
  희귀물품:
    required: 1
    consume: false
    items:
      - DRAGON_HEAD
      - ENCHANTED_GOLDEN_APPLE
      - TOTEM_OF_UNDYING
      - TRIDENT
  
  # 전설 등급 (보상 증가)
  전설등급:
    required: 1
    consume: false
    reward: 15                 # 카테고리별 보상 오버라이드
    items:
      - DRAGON_EGG
      - NETHER_STAR
      - ELYTRA
      - BEACON
```

### 마일스톤

도감 등록 수에 따른 마일스톤 보상은 코드에서 정의됩니다:
- 10개: BottCoin 보상
- 25개: 직업 해금 조건 충족
- 50개: Tier 2 직업 해금 조건

---

## achievements.yml

업적 시스템 설정입니다.

### 업적 구조

```yaml
achievements:
  # 업적 ID:
  #   type: CODEX|JOB|PVP|VANILLA
  #   displayName: "표시 이름"
  #   description: "설명"
  #   condition: { ... }       # 조건
  #   reward:
  #     bottcoin: N            # BottCoin 보상
  #     title: "칭호ID"        # 칭호 해금 (선택)
  
  codex_10:
    type: CODEX
    displayName: "수집가 입문"
    description: "도감에 10종 등록"
    condition:
      codexCount: 10
    reward:
      bottcoin: 5
  
  codex_50:
    type: CODEX
    displayName: "열정 수집가"
    description: "도감에 50종 등록"
    condition:
      codexCount: 50
    reward:
      bottcoin: 20
      title: "collector"
  
  miner_level_50:
    type: JOB
    displayName: "베테랑 광부"
    description: "광부 레벨 50 달성"
    condition:
      job: miner
      level: 50
    reward:
      bottcoin: 30
  
  pvp_first_kill:
    type: PVP
    displayName: "첫 승리"
    description: "PvP에서 첫 킬"
    condition:
      kills: 1
    reward:
      bottcoin: 10
  
  vanilla_nether:
    type: VANILLA
    displayName: "네더 탐험가"
    description: "네더에 첫 발을 내딛다"
    condition:
      advancement: "minecraft:story/enter_the_nether"
    reward:
      bottcoin: 5
```

---

## titles.yml

칭호 시스템 설정입니다.

### 칭호 구조

```yaml
titles:
  # 칭호 ID:
  #   displayName: "표시 이름"
  #   prefix: "TAB/채팅 prefix"
  #   luckpermsGroup: "LP 그룹 이름"
  #   unlockCondition: { ... }   # 해금 조건 (업적 연동)
  
  collector:
    displayName: "수집가"
    prefix: "§6[수집가] "
    luckpermsGroup: "title_collector"
    unlockCondition:
      achievement: "codex_50"
  
  veteran_miner:
    displayName: "베테랑 광부"
    prefix: "§7[베테랑 광부] "
    luckpermsGroup: "title_veteran_miner"
    unlockCondition:
      achievement: "miner_level_50"
  
  pvp_champion:
    displayName: "PvP 챔피언"
    prefix: "§c[챔피언] "
    luckpermsGroup: "title_pvp_champion"
    unlockCondition:
      achievement: "pvp_100_kills"
```

### LuckPerms 연동

칭호 장착 시:
1. `luckpermsGroup`에 지정된 그룹에 자동 추가
2. TAB 플러그인이 LuckPerms 그룹 기반 prefix 표시
3. 해제 시 그룹에서 자동 제거

**TAB 설정 예시** (`plugins/TAB/config.yml`):
```yaml
groups:
  title_collector:
    tabprefix: "§6[수집가] "
    tagprefix: "§6[수집가] "
```

---

## shops.yml

상점 아이템 및 가격 설정입니다.

### 상점 구조

```yaml
shops:
  # 상점 ID:
  #   displayName: "상점 이름"
  #   type: JOB|FIXED           # JOB=동적가, FIXED=고정가
  #   job: miner|farmer|fisher  # type=JOB일 때만
  #   items: [...]
  
  miner:
    displayName: "광부 상점"
    type: JOB
    job: miner
    items:
      - id: "coal"
        material: COAL
        buyPrice: -1           # -1 = 구매 불가
        sellPrice: 5
      - id: "iron_ingot"
        material: IRON_INGOT
        buyPrice: -1
        sellPrice: 20
      - id: "diamond"
        material: DIAMOND
        buyPrice: -1
        sellPrice: 150
  
  general:
    displayName: "잡화 상점"
    type: FIXED
    items:
      - id: "torch"
        material: TORCH
        buyPrice: 5
        sellPrice: 1
      - id: "oak_planks"
        material: OAK_PLANKS
        buyPrice: 10
        sellPrice: 2
```

### 동적 가격

`type: JOB` 상점은 동적 가격이 적용됩니다:
- 기준가 × (1 + 레벨보너스%) × 동적가격배율
- 대량 판매 시 가격 하락
- 시간 경과 시 복귀

---

## antiexploit.yml

악용 방지 상세 설정입니다. (대부분 config.yml에 통합)

### 주요 설정

```yaml
# 주민 거래 제한
villagerTrade:
  enabled: true
  freeTradeLimit: 10
  tradeCosts:
    tier1: 5000    # 11~15회
    tier2: 10000   # 16~20회
    tier3: 15000   # 21~30회

# X-ray 탐지
xrayAnalyzer:
  enabled: true
  diamondFindRateThreshold: 0.03
  minBlocksForAnalysis: 50

# AFK 감쇠
afkDampen:
  enabled: true
  afkAfterSeconds: 480

# 시장 조작 탐지
marketManipulation:
  enabled: true
  splitTradeWindowSeconds: 60
  volumeBombThreshold: 0.3

# 자동화 농장 무효화
antiFarm:
  enabled: true
  requirePlayerContribution: true
  wildNonPlayerDropMultiplier: 0.0
```

---

## 설정 리로드

설정 변경 후 적용 방법:

```bash
# 전체 리로드
/tycoon reload

# 서버 재시작 (권장, 일부 설정)
```

**주의**: 일부 설정은 서버 재시작이 필요합니다:
- `plugin.yml` 변경 (명령어/권한)
- 월드 설정 변경
- 서비스 활성화/비활성화

---

## 설정 검증

설정 파일 오류 시 서버 로그에 경고가 표시됩니다:

```
[TycoonLite] [WARN] config.yml: 'worlds.wild.name' is missing
[TycoonLite] [WARN] jobs.yml: Invalid exp formula segment
```

---

## 자주 묻는 질문

### Q: 직업 해금 조건을 변경하려면?
`jobs.yml`의 `common.defaultUnlock` 또는 각 직업의 `unlock` 섹션 수정

### Q: 동적 가격 변동폭을 줄이려면?
`config.yml`의 `dynamicPrice.maxPriceChangePercent` 값 감소

### Q: 새 커스텀 인챈트를 추가하려면?
코드 수정 필요 (인챈트는 Java 클래스로 정의)

### Q: 도감 카테고리를 추가하려면?
`config.yml`의 `codex:` 섹션에 새 카테고리 추가

### Q: NPC 상점 연결이 안 되면?
`shopNpc.mappings`에 NPC 이름 키워드 추가 (컬러코드 제외)

---

*TycoonLite 설정 가이드 - v1.0.0*
