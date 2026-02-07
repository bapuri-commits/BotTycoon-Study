# Lands 플러그인 연동 가이드

> **문서 버전**: 1.0  
> **최종 업데이트**: 2026-01-29  
> **대상**: TycoonLite 서버 운영자

---

## 목차

1. [개요](#1-개요)
2. [Lands 플러그인 설치](#2-lands-플러그인-설치)
3. [플레이어 명령어](#3-플레이어-명령어)
4. [관리자 명령어](#4-관리자-명령어)
5. [경제 시스템 연동](#5-경제-시스템-연동)
6. [TycoonLite 연동 코드](#6-tycoonlite-연동-코드)
7. [설정 가이드](#7-설정-가이드)
8. [기존 땅 시스템과의 비교](#8-기존-땅-시스템과의-비교)
9. [문제 해결](#9-문제-해결)

---

## 1. 개요

### 1.1 Lands란?

Lands는 Minecraft 서버용 프리미엄 땅 보호 플러그인으로, 청크 기반의 클레임 시스템을 제공합니다.

### 1.2 TycoonLite 연동 구조

```
┌─────────────────────────────────────────────────────────────┐
│                    TycoonLite 서버                          │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐                 │
│  │ EconomyService  │◄───│ VaultIntegration│                 │
│  │ (BD 화폐 관리)  │    │ (Vault Provider)│                 │
│  └────────┬────────┘    └────────┬────────┘                 │
│           │                      │                          │
│           ▼                      ▼                          │
│  ┌─────────────────────────────────────────┐                │
│  │              Vault API                  │                │
│  └─────────────────────────────────────────┘                │
│                      ▲                                      │
│                      │                                      │
│  ┌─────────────────────────────────────────┐                │
│  │           Lands 플러그인                │                │
│  │  - 청크 클레임                          │                │
│  │  - 권한 관리                            │                │
│  │  - 멤버 시스템                          │                │
│  └─────────────────────────────────────────┘                │
│                      ▲                                      │
│                      │                                      │
│  ┌─────────────────────────────────────────┐                │
│  │    LandsIntegration + LandsListener     │                │
│  │    (TycoonLite 커스텀 연동)             │                │
│  └─────────────────────────────────────────┘                │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Lands 플러그인 설치

### 2.1 다운로드

- **공식 사이트**: https://www.spigotmc.org/resources/lands.53313/
- **버전 요구사항**: Lands 7.x 이상

### 2.2 설치

1. `Lands-x.x.x.jar` 파일을 `plugins/` 폴더에 복사
2. 서버 재시작
3. `plugins/Lands/config.yml` 생성 확인

### 2.3 softdepend 확인

TycoonLite의 `plugin.yml`에서 Lands가 softdepend로 등록되어 있습니다:

```yaml
softdepend:
  - Lands
```

---

## 3. 플레이어 명령어

### 3.1 땅 생성/클레임

| 명령어 | 설명 | 비용 |
|--------|------|------|
| `/lands create <이름>` | 새 땅 생성 (현재 위치에 첫 청크 클레임) | config 설정 |
| `/lands claim` | 현재 청크를 기존 땅에 추가 | config 설정 |
| `/lands unclaim` | 현재 청크 클레임 해제 | 환불 비율 적용 |
| `/lands delete` | 땅 전체 삭제 | - |

### 3.2 땅 공유/멤버 관리

| 명령어 | 설명 |
|--------|------|
| `/lands trust <플레이어>` | 플레이어에게 빌드 권한 부여 |
| `/lands untrust <플레이어>` | 플레이어 권한 해제 |
| `/lands setowner <플레이어>` | 땅 소유권 이전 |
| `/lands setrole <플레이어> <역할>` | 특정 역할 부여 |

### 3.3 땅 정보/관리

| 명령어 | 설명 |
|--------|------|
| `/lands menu` | GUI 메뉴 열기 (권장) |
| `/lands info` | 현재 위치 땅 정보 |
| `/lands list` | 내 땅 목록 |
| `/lands map` | 주변 청크 지도 |
| `/lands spawn` | 내 땅 스폰으로 이동 |
| `/lands setspawn` | 땅 스폰 위치 설정 |

### 3.4 플래그/설정

| 명령어 | 설명 |
|--------|------|
| `/lands flags` | 땅 플래그 설정 GUI |
| `/lands rename <새이름>` | 땅 이름 변경 |
| `/lands ban <플레이어>` | 플레이어 출입 금지 |
| `/lands unban <플레이어>` | 출입 금지 해제 |

---

## 4. 관리자 명령어

| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/lands admin claim` | 관리자 땅 클레임 (비용 무시) | `lands.admin.claim` |
| `/lands admin unclaim` | 관리자 땅 해제 | `lands.admin.unclaim` |
| `/lands admin delete <땅이름>` | 땅 강제 삭제 | `lands.admin.delete` |
| `/lands admin setowner <플레이어>` | 소유자 강제 변경 | `lands.admin.setowner` |
| `/lands admin bypass` | 보호 우회 토글 | `lands.admin.bypass` |
| `/lands admin reload` | 설정 리로드 | `lands.admin.reload` |

---

## 5. 경제 시스템 연동

### 5.1 Vault 연동 구조

TycoonLite는 Vault Economy Provider를 통해 BD 화폐를 외부 플러그인에 제공합니다.

```
TycoonLite EconomyService
        ↓
VaultIntegration (ServicePriority.Highest)
        ↓
    Vault API
        ↓
  Lands 플러그인 (경제 사용)
```

### 5.2 TycoonLite Vault 등록 코드

```java
// TycoonPlugin.java (Line 248-254)
VaultIntegration vault = new VaultIntegration(this, economyService);
if (vault.register()) {
    services.setVaultIntegration(vault);
    getLogger().info("✓ Vault Economy Provider 등록됨");
} else {
    getLogger().warning("Vault 연동 실패 - 외부 플러그인에서 경제 사용 불가");
}
```

### 5.3 Lands 경제 설정

`plugins/Lands/config.yml`:

```yaml
economy:
  # Vault 연동 활성화
  enabled: true
  
  # 첫 땅 생성 비용
  land-creation-cost: 0
  
  # 청크당 클레임 비용
  claim-cost:
    first-claim: 100000    # 첫 클레임 (BD)
    claim: 50000           # 추가 클레임 (BD)
    
  # 클레임 해제 시 환불 비율 (0.0 ~ 1.0)
  refund: 0.8              # 80% 환불
```

### 5.4 연동 확인 방법

서버 시작 시 로그 확인:

```
[TycoonLite] ✓ Vault Economy Provider 등록됨
[Lands] Hooked into economy: Vault (TycoonLite)
```

---

## 6. TycoonLite 연동 코드

### 6.1 파일 위치

```
src/main/java/kr/bapuri/tycoon/integration/
├── LandsIntegration.java    # Lands API 래퍼
└── LandsListener.java       # 이벤트 리스너
```

### 6.2 LandsIntegration 주요 기능

| 메서드 | 설명 |
|--------|------|
| `isAvailable()` | Lands 사용 가능 여부 |
| `getPlotAt(Location)` | 특정 위치의 땅 정보 조회 |
| `canBuild(Player, Location)` | 빌드 권한 체크 |
| `getOwnedLandCount(UUID)` | 플레이어 소유 땅 개수 |
| `getCurrentLandName(Player)` | 현재 위치 땅 이름 |

### 6.3 PlotInfo 데이터 클래스

```java
public static class PlotInfo {
    private final String name;       // 땅 이름
    private final UUID ownerId;      // 소유자 UUID
    private final String ownerName;  // 소유자 이름
    private final long size;         // 청크 수
    
    // 클라이언트 모드 패킷용 직렬화
    public String serialize() {
        return name + ";" + ownerId + ";" + ownerName + ";" + size;
    }
}
```

### 6.4 LandsListener 이벤트

| 이벤트 | 기능 |
|--------|------|
| `BlockBreakEvent` | 빌드 권한 추가 검증 |
| `BlockPlaceEvent` | 빌드 권한 추가 검증 |
| `PlayerMoveEvent` | 땅 변경 감지 → 모드 연동 |
| `PlayerQuitEvent` | 캐시 정리 |

### 6.5 클라이언트 모드 연동

땅 변경 시 `PLOT_UPDATE` 패킷 자동 전송:

```java
// LandsListener.java (Line 144-148)
if (landChangeCallback != null) {
    Optional<LandsIntegration.PlotInfo> plotInfo = lands.getPlotAt(to);
    landChangeCallback.onLandChange(player, plotInfo.orElse(null));
}
```

---

## 7. 설정 가이드

### 7.1 Lands 권장 설정

`plugins/Lands/config.yml`:

```yaml
# 일반 설정
general:
  # 땅 이름 최소/최대 길이
  land-name-min-length: 3
  land-name-max-length: 24
  
  # 최대 땅 개수 (per player)
  max-lands: 5
  
  # 최대 청크 수 (per land)
  max-chunks: 100

# 경제 설정
economy:
  enabled: true
  land-creation-cost: 0
  claim-cost:
    first-claim: 100000
    claim: 50000
  refund: 0.8
  
# 월드 설정
worlds:
  # TycoonLite Town 월드만 Lands 활성화
  world_town:
    enabled: true
  # Wild 월드는 비활성화 (리셋되므로)
  world_wild:
    enabled: false
```

### 7.2 권한 설정 (LuckPerms)

```yaml
# 기본 플레이어 권한
default:
  - lands.claim
  - lands.trust
  - lands.menu

# VIP 권한 (더 많은 청크)
vip:
  - lands.chunks.50

# 관리자 권한
admin:
  - lands.admin.*
  - lands.bypass
```

---

## 8. 기존 땅 시스템과의 비교

### 8.1 기능 매핑

| 기능 | 기존 PlotManager | Lands |
|------|------------------|-------|
| 땅 단위 | 1청크 (PlotData) | 1청크 (Area) |
| 땅 묶음 | 불가 | 가능 (Land = 여러 Area) |
| 구매 | `/plot buy` | `/lands create` + `/lands claim` |
| 판매 | `/plot sell` | `/lands unclaim` + `/lands delete` |
| 공유 | `/plot share` | `/lands trust` |
| 환불 | 80% 고정 | config.refund 설정 |
| 보호 구역 | `isInRestrictedZone()` | 관리자 unclaim |
| 경제 | BD 직접 연동 | Vault 통해 BD 사용 |

### 8.2 Lands가 더 나은 점

- **멀티 청크 땅**: 여러 청크를 하나의 땅으로 관리
- **역할 시스템**: 멤버별 세부 권한 설정
- **GUI 지원**: `/lands menu`로 편리한 관리
- **플래그 시스템**: PvP, 몬스터 스폰 등 세부 설정

### 8.3 커스텀 구현 필요 항목

| 기능 | 구현 방법 |
|------|----------|
| 땅문서 아이템 | `LandClaimEvent` 훅 + 무료 클레임 부여 |
| 채무 상환 환불 | `LandUnclaimEvent` 훅 + 커스텀 환불 로직 |
| 보호 구역 차단 | `LandClaimEvent` 취소 처리 |

---

## 9. 문제 해결

### 9.1 "Economy not found" 오류

**원인**: Vault가 TycoonLite Economy를 인식 못함

**해결**:
1. TycoonLite가 Lands보다 먼저 로드되는지 확인
2. 서버 로그에서 `Vault Economy Provider 등록됨` 확인
3. `/vault-info` 명령어로 연동 상태 확인

### 9.2 클레임 시 돈이 차감되지 않음

**확인사항**:
1. `plugins/Lands/config.yml`의 `economy.enabled: true` 확인
2. `claim-cost` 값이 0보다 큰지 확인
3. 서버 로그에서 Lands → Vault 연동 메시지 확인

### 9.3 Lands API 호환성 오류

**증상**: `[LandsIntegration] Lands API 버전이 호환되지 않습니다`

**원인**: Lands 버전과 API 호출 불일치

**해결**: Lands 7.x 이상 사용, 그래도 안 되면 TycoonLite 업데이트

### 9.4 모드에서 땅 정보 안 보임

**확인사항**:
1. `LandsIntegration.isAvailable()` 반환값 확인
2. `LandsListener`가 정상 등록되었는지 확인
3. 클라이언트 모드 PLOT_UPDATE 패킷 수신 확인

---

## 부록: 돈 관련 명령어 정리

### 플레이어용 명령어

| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/money` | 본인 잔액 확인 (BD + BottCoin) | 없음 (모든 플레이어) |
| `/money <player>` | 다른 플레이어 잔액 확인 | `tycoon.admin.eco` |

**별칭**: `/돈`, `/잔액`, `/bal`, `/balance`

### 관리자용 명령어 (`/eco`)

| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/eco give <player> <amount> [bd\|bottcoin]` | 돈 지급 | `tycoon.admin.eco` |
| `/eco take <player> <amount> [bd\|bottcoin]` | 돈 차감 | `tycoon.admin.eco` |
| `/eco set <player> <amount> [bd\|bottcoin]` | 잔액 설정 | `tycoon.admin.eco` |
| `/eco check <player>` | 잔액 확인 | `tycoon.admin.eco` |

### 화폐 종류

| 화폐 | 설명 | 용도 |
|------|------|------|
| BD | 기본 화폐 | 땅 구매, 상점, 강화 등 |
| BottCoin | 프리미엄 화폐 | 특수 아이템, 스킨 등 |

### 예시

```bash
# 플레이어에게 100,000 BD 지급
/eco give Steve 100000 bd

# 플레이어의 BottCoin 1000으로 설정
/eco set Steve 1000 bottcoin

# 잔액 확인
/eco check Steve
```

---

## 관련 문서

- [COMMAND_PERMISSION_GUIDE.md](./COMMAND_PERMISSION_GUIDE.md) - 전체 명령어/권한 가이드
- [CONFIG_GUIDE.md](./CONFIG_GUIDE.md) - TycoonLite 설정 가이드
- [EXTERNAL_PLUGIN_ANALYSIS.md](./EXTERNAL_PLUGIN_ANALYSIS.md) - 외부 플러그인 분석
