# Lands 플러그인 - 레거시 호환 설정 가이드

> **문서 버전**: 1.0  
> **최종 업데이트**: 2026-01-29  
> **목적**: 레거시 PlotManager 기능을 Lands로 대체

---

## 목차

1. [레거시 vs Lands 기능 매핑](#1-레거시-vs-lands-기능-매핑)
2. [Lands 최소 설정 (config.yml)](#2-lands-최소-설정-configyml)
3. [명령어 대응표](#3-명령어-대응표)
4. [보호 구역 설정](#4-보호-구역-설정)
5. [데이터 마이그레이션](#5-데이터-마이그레이션)
6. [운영 가이드](#6-운영-가이드)

---

## 1. 레거시 vs Lands 기능 매핑

| 레거시 기능 | Lands 대응 | 비고 |
|------------|-----------|------|
| 청크 구매 (`/plot buy`) | `/lands claim` | 첫 클레임 시 자동으로 Land 생성됨 |
| 청크 판매 (`/plot sell`) | `/lands unclaim` | 환불률 설정 가능 |
| 플롯 정보 (`/plot info`) | `/lands view` 또는 땅 위에서 자동 표시 | |
| 내 플롯 목록 (`/plot list`) | `/lands list` | |
| 멤버 추가 (`/plot member add`) | `/lands trust <player>` | |
| 멤버 제거 (`/plot member remove`) | `/lands untrust <player>` | |
| 멤버 목록 (`/plot member list`) | `/lands menu` → Members | GUI로 확인 |
| 관리자 설정 (`/plot admin set`) | `/lands admin claim` | |
| 관리자 해제 (`/plot admin clear`) | `/lands admin unclaim` | |
| 보호 구역 | 관리자 땅으로 미리 클레임 | 아래 섹션 참고 |
| 80% 환불 | `refund: 0.8` 설정 | |

---

## 2. Lands 최소 설정 (config.yml)

`plugins/Lands/config.yml` 파일을 아래와 같이 설정하세요:

```yaml
# ============================================================
# Lands 플러그인 - 레거시 PlotManager 호환 설정
# TycoonLite 서버용 최소 설정
# ============================================================

# ============ 기본 설정 ============
general:
  # 땅 이름 길이 제한
  land-name:
    min-length: 2
    max-length: 24
  
  # 플레이어당 최대 땅 개수 (레거시는 무제한이었음)
  max-lands: 10
  
  # 땅당 최대 청크 수 (레거시는 1청크=1플롯이었으나, Lands는 여러 청크 묶기 가능)
  max-chunks: 50
  
  # 시작 청크 수 (기본 클레임 가능 개수)
  chunks:
    start: 5
    
  # 초대 시스템 (레거시 member와 동일 기능)
  invite:
    enabled: false  # trust로 바로 추가 가능하게

# ============ 경제 설정 (TycoonLite BD 연동) ============
economy:
  enabled: true
  
  # 첫 땅 생성 비용 (레거시는 0이었음 - 땅 구매 시 자동 생성)
  land-creation-cost: 0
  
  # 청크 클레임 비용 (레거시 plotPrice: 100000)
  claim-cost:
    first-claim: 100000   # 첫 클레임 비용 (BD)
    claim: 100000         # 추가 클레임도 동일 가격
  
  # 환불률 (레거시 80%)
  refund: 0.8
  
  # 클레임 거리별 가격 증가 (비활성화 - 레거시처럼 균일 가격)
  distance-increase:
    enabled: false

# ============ 세금 시스템 (비활성화) ============
taxes:
  enabled: false

# ============ 전쟁 시스템 (비활성화) ============
wars:
  enabled: false

# ============ 국가 시스템 (비활성화) ============  
nations:
  enabled: false

# ============ 임대 시스템 (비활성화) ============
rental:
  enabled: false

# ============ 역할 시스템 (단순화) ============
roles:
  # 기본 역할만 사용 (Owner, Member, Visitor)
  # 복잡한 커스텀 역할 비활성화
  default:
    # 멤버(trust된 플레이어) 기본 권한
    member:
      - BLOCK_BREAK
      - BLOCK_PLACE
      - INTERACT_CONTAINER
      - INTERACT_DOOR
      - INTERACT_MECHANISM
      - ITEM_PICKUP
      - ATTACK_ANIMAL
      - VEHICLE_USE
      
    # 방문자 권한 (레거시와 동일 - 대부분 불가)
    visitor:
      - INTERACT_DOOR  # 문 열기만 허용

# ============ 월드 설정 ============
worlds:
  # 타운 월드만 Lands 활성화
  world_town:
    enabled: true
    
  # 야생 월드 비활성화 (리셋되므로)  
  world_wild:
    enabled: false
    
  # 던전 월드 비활성화
  world_dungeon:
    enabled: false
    
  # 네더/엔드 비활성화
  world_nether:
    enabled: false
  world_the_end:
    enabled: false

# ============ GUI 설정 (선택적 단순화) ============
gui:
  # 메인 메뉴 활성화 (/lands menu)
  main-menu:
    enabled: true

# ============ 플래그 설정 ============
flags:
  # 기본 플래그 (레거시와 유사하게)
  default:
    # PvP 비활성화 (타운 월드 기본)
    pvp: false
    # 몬스터 스폰
    monster_spawn: false
    # 폭발 피해 방지
    explosion_damage: false

# ============ 시각 효과 ============
visualization:
  # 땅 경계 표시 (청크 경계)
  type: CHUNK
  # 파티클 효과
  particle:
    enabled: true
    
# ============ 메시지 ============
# 한국어 메시지는 plugins/Lands/languages/ko_KR.yml 에서 설정
```

---

## 3. 명령어 대응표

### 플레이어 명령어

| 레거시 | Lands | 설명 |
|--------|-------|------|
| `/plot buy` | `/lands claim` | 현재 청크 구매 (첫 구매 시 땅 이름 입력 필요) |
| `/plot sell` | `/lands unclaim` | 현재 청크 판매 (환불) |
| `/plot info` | `/lands view` | 현재 위치 땅 정보 |
| `/plot list` | `/lands list` | 내 땅 목록 |
| `/plot member add <player>` | `/lands trust <player>` | 멤버 추가 |
| `/plot member remove <player>` | `/lands untrust <player>` | 멤버 제거 |
| `/plot member list` | `/lands menu` → Members 탭 | GUI에서 확인 |
| - | `/lands menu` | GUI 메뉴 (편리함) |
| - | `/lands map` | 주변 청크 지도 |
| - | `/lands spawn` | 내 땅 스폰으로 이동 |

### 관리자 명령어

| 레거시 | Lands | 설명 |
|--------|-------|------|
| `/plot admin set <player>` | `/lands admin claim` + `/lands setowner <player>` | 관리자 클레임 후 소유권 이전 |
| `/plot admin clear` | `/lands admin unclaim` | 관리자 클레임 해제 |
| - | `/lands admin bypass` | 보호 우회 토글 |
| - | `/lands admin delete <landname>` | 땅 강제 삭제 |
| - | `/lands admin reload` | 설정 리로드 |

### 팁: 첫 클레임 워크플로우

레거시 `/plot buy`는 바로 구매되었지만, Lands는 **첫 클레임 시 땅 이름**을 입력해야 합니다:

```
1. 플레이어가 원하는 청크에 서서 /lands claim 입력
2. (첫 클레임이면) 땅 이름 입력 창 표시
3. 이름 입력 후 클레임 완료
4. 이후 추가 청크는 /lands claim만으로 바로 추가됨
```

또는 `/lands create <이름>` 후 `/lands claim`으로 분리 가능.

---

## 4. 보호 구역 설정

레거시에서는 `config.yml`의 `restrictedZone` 좌표로 보호 구역을 설정했습니다:

```yaml
# 레거시 설정
worlds:
  town:
    restrictedZone:
      enabled: true
      minX: -127
      maxX: 127
      minZ: -111
      maxZ: 143
```

### Lands에서 보호 구역 설정 방법

**방법 1: 관리자 땅으로 미리 클레임 (권장)**

```bash
# 1. 관리자 모드 활성화
/lands admin bypass

# 2. 보호 구역 내 모든 청크를 관리자 땅으로 클레임
# (WorldEdit 등으로 청크 범위 확인 후)
/lands create 마을보호구역

# 3. 보호할 청크들 위에서 반복
/lands claim

# 4. 관리자 땅은 일반 플레이어가 unclaim 불가
```

**방법 2: WorldGuard 연동 (플러그인 추가 필요)**

WorldGuard가 설치되어 있다면:
```bash
# WorldGuard 영역 설정
//pos1
//pos2
//expand vert
/rg define town_protected

# Lands config.yml에서 WorldGuard 연동 활성화
worldguard:
  enabled: true
  regions:
    town_protected:
      claim: false  # 이 영역 내 클레임 불가
```

**방법 3: Lands 설정으로 좌표 기반 제한**

`plugins/Lands/config.yml`:
```yaml
# 특정 좌표 범위 클레임 금지
claim-limits:
  world_town:
    # 중앙 보호 구역 (청크 좌표 기준)
    - type: DENY
      min-chunk-x: -8   # 블록 -127 / 16 ≈ -8
      max-chunk-x: 8    # 블록 127 / 16 ≈ 8
      min-chunk-z: -7   # 블록 -111 / 16 ≈ -7
      max-chunk-z: 9    # 블록 143 / 16 ≈ 9
```

> **참고**: Lands 버전에 따라 `claim-limits` 설정 방식이 다를 수 있습니다. 최신 문서를 확인하세요.

---

## 5. 데이터 마이그레이션

### 5.1 레거시 데이터 형식

레거시 `plots.yml` 구조:
```yaml
plots:
  world_town_5_-3:    # plotKey: worldName_chunkX_chunkZ
    owner: "uuid-string"
    purchaseTime: 1706512345678
    purchasePrice: 100000
    members:
      - "uuid-string-1"
      - "uuid-string-2"
  world_town_5_-2:
    owner: "another-uuid"
    purchaseTime: 1706512345679
    purchasePrice: 100000
```

### 5.2 마이그레이션 방법

#### 방법 A: 수동 마이그레이션 (소량 데이터)

1. 레거시 `plots.yml`에서 소유자와 청크 좌표 확인
2. 게임 내에서 관리자가 해당 청크로 이동
3. Lands 명령어로 클레임 생성:

```bash
# 관리자 모드
/lands admin bypass

# 해당 청크에서
/lands create <플레이어이름>의땅

# 소유권 이전
/lands setowner <플레이어이름>

# 멤버가 있다면
/lands trust <멤버이름>
```

#### 방법 B: 마이그레이션 스크립트 (대량 데이터)

서버 시작 시 한 번 실행되는 마이그레이션 코드:

```java
// 예시 코드 (TycoonPlugin에서 1회성 실행)
public void migratePlotsToLands() {
    File plotsFile = new File(getDataFolder(), "plots.yml");
    if (!plotsFile.exists()) return;
    
    // 마이그레이션 완료 플래그 체크
    if (getConfig().getBoolean("migration.plots-to-lands.done", false)) {
        return;
    }
    
    YamlConfiguration plots = YamlConfiguration.loadConfiguration(plotsFile);
    ConfigurationSection plotsSection = plots.getConfigurationSection("plots");
    if (plotsSection == null) return;
    
    getLogger().info("[마이그레이션] plots.yml → Lands 마이그레이션 시작...");
    
    // Lands API 가져오기
    LandsIntegration landsApi = LandsIntegration.of(this);
    
    int migrated = 0;
    for (String plotKey : plotsSection.getKeys(false)) {
        ConfigurationSection plotSec = plotsSection.getConfigurationSection(plotKey);
        if (plotSec == null) continue;
        
        String ownerStr = plotSec.getString("owner");
        if (ownerStr == null || ownerStr.isEmpty()) continue;
        
        try {
            UUID ownerId = UUID.fromString(ownerStr);
            
            // plotKey 파싱: worldName_chunkX_chunkZ
            String[] parts = plotKey.split("_");
            int chunkZ = Integer.parseInt(parts[parts.length - 1]);
            int chunkX = Integer.parseInt(parts[parts.length - 2]);
            String worldName = plotKey.substring(0, plotKey.lastIndexOf("_" + chunkX));
            
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;
            
            // Lands API로 클레임 생성
            // (Lands API 문서 참고 - 버전별로 다름)
            Land land = landsApi.getLandPlayer(ownerId).createLand(
                world, chunkX, chunkZ, "Plot_" + chunkX + "_" + chunkZ
            );
            
            // 멤버 마이그레이션
            List<String> memberStrings = plotSec.getStringList("members");
            for (String memberStr : memberStrings) {
                try {
                    UUID memberId = UUID.fromString(memberStr);
                    land.trust(memberId, ROLE_MEMBER);
                } catch (Exception ignored) {}
            }
            
            migrated++;
        } catch (Exception e) {
            getLogger().warning("[마이그레이션] 플롯 마이그레이션 실패: " + plotKey + " - " + e.getMessage());
        }
    }
    
    getLogger().info("[마이그레이션] " + migrated + "개 플롯 마이그레이션 완료");
    
    // 완료 플래그 설정
    getConfig().set("migration.plots-to-lands.done", true);
    saveConfig();
    
    // 레거시 파일 백업
    plotsFile.renameTo(new File(getDataFolder(), "plots.yml.migrated"));
}
```

#### 방법 C: 콘솔 명령어 스크립트

`plots.yml`을 파싱하여 콘솔 명령어 목록 생성:

```bash
# plots_migration_commands.txt 생성
# 서버 콘솔에서 순차 실행

# 청크 (5, -3) 소유자: Steve
lands admin unclaim world_town 80 48 80 48
lands admin claim world_town 80 48 80 48
lands admin setowner Steve

# 청크 (5, -2) 소유자: Alex
lands admin unclaim world_town 80 -32 80 -32
lands admin claim world_town 80 -32 80 -32
lands admin setowner Alex
```

> **참고**: 청크 좌표 → 블록 좌표 변환: `blockX = chunkX * 16`, `blockZ = chunkZ * 16`

### 5.3 마이그레이션 체크리스트

- [ ] 레거시 `plots.yml` 백업
- [ ] Lands 플러그인 설치 및 config 설정
- [ ] 보호 구역 먼저 설정 (관리자 땅으로)
- [ ] 마이그레이션 실행 (위 방법 중 선택)
- [ ] 마이그레이션 완료 확인
  - [ ] 소유권 정상 이전
  - [ ] 멤버 권한 정상 이전
  - [ ] 빌드 권한 동작 확인
- [ ] 레거시 PlotManager 비활성화 (TycoonLite에서 제거)
- [ ] 플레이어에게 새 명령어 안내

---

## 6. 운영 가이드

### 6.1 플레이어 공지 예시

```
[공지] 땅 시스템이 업데이트되었습니다!

기존 /plot 명령어 대신 /lands 명령어를 사용해주세요.

주요 변경사항:
- /plot buy → /lands claim (첫 클레임 시 땅 이름 입력)
- /plot sell → /lands unclaim
- /plot member add → /lands trust <플레이어>
- /plot member remove → /lands untrust <플레이어>

새 기능:
- /lands menu : 편리한 GUI 메뉴
- /lands map : 주변 청크 지도
- /lands spawn : 내 땅으로 이동

기존에 소유하셨던 땅은 자동으로 이전되었습니다.
```

### 6.2 흔한 문제 해결

| 문제 | 해결 |
|------|------|
| "Economy not found" | TycoonLite가 Lands보다 먼저 로드되는지 확인 |
| 클레임 비용이 안 빠짐 | `economy.enabled: true` 확인 |
| 환불이 안 됨 | `refund: 0.8` 확인 (0이면 환불 없음) |
| 특정 월드에서 클레임 불가 | `worlds.월드이름.enabled: true` 확인 |
| 멤버가 빌드 못함 | `roles.default.member` 권한 확인 |

### 6.3 권한 노드 (LuckPerms)

```yaml
# 기본 플레이어
default:
  - lands.command.claim
  - lands.command.unclaim
  - lands.command.trust
  - lands.command.untrust
  - lands.command.menu
  - lands.command.list
  - lands.command.spawn
  - lands.chunks.10  # 최대 10청크 클레임 가능

# VIP (더 많은 청크)
vip:
  - lands.chunks.50

# 관리자
admin:
  - lands.admin.*
  - lands.bypass
```

---

## 부록: 실제 마이그레이션 예시

### 현재 plots.yml 데이터 (예시)

```yaml
plots:
  world_-9_0:
    owner: 2b6d91b3-2f5e-4fc5-998c-9b5080a8e3b4
    purchaseTime: 1769155059239
    purchasePrice: 50000
  world_-1_-8:
    owner: 411b7a3f-c691-41f5-b3f2-d26923c2ad1d
    purchaseTime: 1769144353260
    purchasePrice: 50000
  # ... 총 18개 플롯
```

### 마이그레이션 콘솔 명령어 생성

각 플롯을 Lands로 이전하는 명령어:

```bash
# plotKey: world_-9_0 → 청크 (-9, 0) → 블록 좌표 (-144, 0)
# 소유자 UUID: 2b6d91b3-2f5e-4fc5-998c-9b5080a8e3b4

# 1. 관리자가 해당 위치로 이동
/tp -144 64 0

# 2. 관리자 모드로 클레임
/lands admin bypass
/lands create Plot_-9_0

# 3. 소유권 이전 (플레이어 이름으로)
/lands admin setowner <플레이어닉네임>
```

### 소유자 UUID → 이름 조회

마이그레이션 전 UUID로 플레이어 이름 확인:

```bash
# 게임 내 명령어 (플러그인 필요)
/lp user 2b6d91b3-2f5e-4fc5-998c-9b5080a8e3b4 info

# 또는 https://mcuuid.net/ 에서 조회
```

### 동일 소유자 플롯 묶기 (Lands 장점 활용)

레거시에서는 1청크=1플롯이었지만, Lands에서는 **같은 소유자의 인접 청크를 하나의 Land로 묶을 수 있습니다**:

```bash
# 예: d86d7256-96ed-4805-abd8-771f6fc52886 소유자가 가진 인접 청크들
# world_8_0, world_8_1, world_9_0, world_9_1, world_10_0, world_10_1

# 하나의 Land로 통합
/lands create MyBase

# 각 청크에서 claim
/lands claim  # 8,0
/lands claim  # 8,1
/lands claim  # 9,0
# ... 반복

# 소유권 이전
/lands admin setowner <플레이어닉네임>
```

---

## 관련 문서

- [LANDS_INTEGRATION_GUIDE.md](./LANDS_INTEGRATION_GUIDE.md) - Lands API 연동 가이드
- [CONFIG_GUIDE.md](./CONFIG_GUIDE.md) - TycoonLite 설정 가이드
