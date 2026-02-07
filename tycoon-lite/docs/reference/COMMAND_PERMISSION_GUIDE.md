# TycoonLite 명령어 및 권한 설정 가이드

> **최종 업데이트**: 2026-01-28 (Phase 8 기준)

---

## 1. 개요

TycoonLite에서 명령어 권한은 **3단계 레이어**로 제어됩니다:

```
┌─────────────────────────────────────────────────────┐
│ Layer 1: CommandWhitelist                           │
│   → 명령어 입력 자체를 허용/차단                      │
│   → "이 명령어를 타이핑할 수 있는가?"                 │
├─────────────────────────────────────────────────────┤
│ Layer 2: plugin.yml 권한                            │
│   → Bukkit 레벨에서 명령어 실행 권한 검사             │
│   → "이 명령어를 실행할 권한이 있는가?"               │
├─────────────────────────────────────────────────────┤
│ Layer 3: LuckPerms 그룹                             │
│   → 플레이어별 세부 권한 관리                         │
│   → "어떤 그룹에 속해 있는가?"                        │
└─────────────────────────────────────────────────────┘
```

---

## 2. 현재 등록된 명령어

### 2.1 일반 플레이어용 명령어

| 명령어 | 별칭 | 설명 | 필요 권한 |
|--------|------|------|-----------|
| `/tycoon` | `/ty`, `/tl` | 메인 명령어 | 없음 (기본 허용) |
| `/tycoon version` | - | 버전 확인 | 없음 |
| `/money` | - | 잔액 확인 | `tycoon.player` |
| `/shop` | - | 상점 이용 | `tycoon.player` |
| `/codex` | `/도감` | 도감 GUI 열기 | `tycoon.codex.use` |
| `/trade` | `/거래` | 거래 기능 | `tycoon.trade.use` |
| NPC 클릭 | - | Town 보관소 GUI | `tycoon.recovery.use` |

### 2.2 관리자용 명령어

| 명령어 | 별칭 | 설명 | 필요 권한 |
|--------|------|------|-----------|
| `/tycoon backup` | - | 백업 관리 | `tycoon.admin.backup` |
| `/tycoon reload` | - | 설정 리로드 | `tycoon.admin` |
| `/eco` | - | 경제 관리 | `tycoon.admin.eco` |
| `/shopadmin` | `/sa` | 상점 관리 | `tycoon.admin.shop` |
| `/lamp` | - | 램프 관리 | `tycoon.admin.lamp` |
| `/enchantadmin` | `/ea` | 인챈트 관리 | `tycoon.admin.enchant` |
| `/upgrade` | `/강화` | 강화 관리 | `tycoon.admin.upgrade` |
| `/coreitem` | `/ci` | 핵심 아이템 관리 | `tycoon.admin.coreitem` |
| `/recovery` | `/보관소` | 보관소 관리 (다른 플레이어 포함) | `tycoon.admin.recovery` |
| `/codex <하위명령>` | - | 도감 관리 | `tycoon.admin.codex` |

---

## 3. 권한 계층 구조

```yaml
# plugin.yml에 정의된 권한 트리
tycoon.admin:                    # 최상위 관리자 권한
  children:
    tycoon.admin.eco: true       # /eco 명령어
    tycoon.admin.backup: true    # /tycoon backup
    tycoon.admin.shop: true      # /shopadmin
    tycoon.admin.market: true    # 시장 조작 알림 수신
    tycoon.admin.lamp: true      # /lamp 명령어
    tycoon.admin.enchant: true   # /enchantadmin 명령어
    tycoon.admin.upgrade: true   # /upgrade 명령어
    tycoon.admin.coreitem: true  # /coreitem 명령어
    tycoon.admin.recovery: true  # /recovery 명령어
    tycoon.admin.codex: true     # /codex 관리 명령어

tycoon.player:                   # 일반 플레이어 권한
  default: true                  # 기본 허용

# 일반 플레이어 기능별 권한 (기본 허용)
tycoon.recovery.use: true        # NPC를 통한 보관소 접근
tycoon.codex.use: true           # /codex GUI 열기
tycoon.trade.use: true           # /trade 거래 기능
```

---

## 4. CommandWhitelist 설정

### 4.1 파일 위치
```
plugins/CommandWhitelist/config.yml
```

### 4.2 현재 설정

```yaml
groups:
  default:
    commands:
    # ... 기존 명령어들 ...
    # [TycoonLite] 일반 플레이어 명령어
    - tycoon
    - ty
    - tl
    # - money    # Phase 3 완료 후 활성화
    # - shop     # Phase 3 완료 후 활성화
    subcommands:
    - help about

  # [TycoonLite] 관리자 명령어
  admin:
    commands:
    - eco
    - shopadmin
    - sa
    subcommands: []
```

### 4.3 동작 원리

1. **default 그룹**: 모든 플레이어에게 적용
2. **admin 그룹**: LuckPerms에서 `commandwhitelist.group.admin` 권한을 가진 플레이어에게만 적용

**중요**: CommandWhitelist의 `admin` 그룹은 LuckPerms의 권한과 연결됩니다:
```bash
# LuckPerms에서 admin 그룹에 CommandWhitelist 권한 부여
/lp group admin permission set commandwhitelist.group.admin true
```

---

## 5. LuckPerms 설정

### 5.1 Tycoon 권한 부여

```bash
# ===== 관리자 그룹 설정 =====
# 모든 Tycoon 관리 권한 부여
/lp group admin permission set tycoon.admin true

# CommandWhitelist admin 그룹 접근 권한
/lp group admin permission set commandwhitelist.group.admin true

# ===== 개별 권한 부여 (필요시) =====
/lp user <플레이어> permission set tycoon.admin.eco true
/lp user <플레이어> permission set tycoon.admin.recovery true
```

### 5.2 Recovery (보관소) 권한

```bash
# ===== 일반 플레이어 =====
# NPC를 통한 보관소 접근 (기본값 true, 보통 불필요)
/lp group default permission set tycoon.recovery.use true

# 특정 플레이어 보관소 사용 금지 (처벌용)
/lp user <플레이어> permission set tycoon.recovery.use false

# ===== 관리자 =====
# /recovery 명령어 사용 (다른 플레이어 보관소 열기 포함)
/lp group admin permission set tycoon.admin.recovery true
```

| 권한 | 대상 | 용도 |
|------|------|------|
| `tycoon.admin.recovery` | 관리자 (OP) | `/recovery` 명령어, 다른 플레이어 보관소 열기 |
| `tycoon.recovery.use` | 모든 플레이어 | NPC를 통한 보관소 접근 (기본 허용) |

### 5.3 AngelChest (데스체스트) 권한

```bash
# ===== 일반 플레이어 (자기 상자만 열기) =====
/lp group default permission set angelchest.protect true

# ===== 관리자 (모든 상자 열기) =====
/lp group admin permission set angelchest.protect.ignore true

# ===== 문제 해결: 일반 플레이어가 남의 상자를 열 수 있는 경우 =====
/lp group default permission set angelchest.protect.ignore false
```

| 권한 | 대상 | 용도 |
|------|------|------|
| `angelchest.protect` | 모든 플레이어 | 자신의 데스체스트를 보호 |
| `angelchest.protect.ignore` | 관리자 | 다른 플레이어 데스체스트 열기 |

### 5.4 추천 그룹 구조

```
default (일반 플레이어)
  └── tycoon.player (기본값)
  └── tycoon.recovery.use (기본값)
  └── tycoon.codex.use (기본값)
  └── tycoon.trade.use (기본값)
  └── angelchest.protect

vip (VIP 플레이어)
  └── 상위: default
  └── (추가 혜택)

admin (관리자)
  └── tycoon.admin (모든 관리 권한 포함)
  └── angelchest.protect.ignore
  └── commandwhitelist.group.admin

superadmin (슈퍼관리자)
  └── 상위: admin
  └── tycoon.superadmin (특수 기능)
```

### 5.5 권한 확인 명령어

```bash
# 그룹 권한 확인
/lp group default permission info tycoon.recovery.use
/lp group admin permission info tycoon.admin

# 특정 플레이어 권한 확인
/lp user <플레이어> permission check tycoon.recovery.use
/lp user <플레이어> permission check angelchest.protect.ignore

# 플레이어 전체 권한 목록
/lp user <플레이어> permission info
```

---

## 6. 새 명령어 추가 시 체크리스트

### 6.1 일반 플레이어용 명령어

```
□ 1. Java 코드에서 명령어 구현
□ 2. plugin.yml의 commands 섹션에 등록
□ 3. plugin.yml의 permissions 섹션에 권한 정의 (필요시)
□ 4. CommandWhitelist config.yml의 default 그룹에 추가
□ 5. TabCompleter 구현 (권장)
□ 6. 문서 업데이트
```

### 6.2 관리자용 명령어

```
□ 1. Java 코드에서 명령어 구현
□ 2. plugin.yml의 commands 섹션에 등록 (permission 필드 포함)
□ 3. plugin.yml의 permissions 섹션에 권한 정의
     - tycoon.admin의 children에 추가
□ 4. CommandWhitelist config.yml의 admin 그룹에 추가
□ 5. TabCompleter 구현 (권장)
□ 6. 문서 업데이트
```

---

## 7. 예시: 새 명령어 `/job` 추가

### Step 1: plugin.yml 수정

```yaml
commands:
  job:
    description: 직업 관련 명령어
    usage: /job <list|info|join|leave>
    permission: tycoon.player.job

permissions:
  tycoon.player:
    children:
      tycoon.player.job: true
  tycoon.player.job:
    description: 직업 명령어 사용 권한
    default: true
```

### Step 2: CommandWhitelist 수정

```yaml
groups:
  default:
    commands:
    # ... 기존 명령어들 ...
    - job    # 새로 추가
```

### Step 3: LuckPerms (필요시)

```bash
# 특정 플레이어의 직업 권한 제거
/lp user <player> permission set tycoon.player.job false
```

---

## 8. 하위 명령어 권한 분리

하나의 명령어에 일반/관리자 하위 명령어가 혼합된 경우:

### 예시: `/tycoon` 명령어

```
/tycoon
  ├── version       → 권한 불필요 (누구나)
  ├── backup list   → tycoon.admin.backup 필요
  ├── backup create → tycoon.admin.backup 필요
  └── reload        → tycoon.admin 필요
```

### 구현 방법

```java
// TycoonPlugin.java onCommand()
if (args[0].equalsIgnoreCase("backup")) {
    if (!sender.hasPermission("tycoon.admin.backup")) {
        sender.sendMessage("권한이 없습니다.");
        return true;
    }
    // 백업 로직...
}
```

**핵심**: 
- CommandWhitelist에서는 `/tycoon` 자체를 default에 허용
- 관리자 하위 명령어는 **코드 내 권한 체크**로 제어

---

## 9. 문제 해결

### Q: 명령어가 "No such command" 오류 발생
**A**: CommandWhitelist에 명령어가 등록되지 않음
```yaml
# CommandWhitelist config.yml 확인
groups:
  default:
    commands:
    - <명령어 이름>
```

### Q: "You don't have permission" 오류 발생
**A**: LuckPerms에서 권한 확인
```bash
/lp user <player> permission info
/lp user <player> permission check <권한노드>
```

### Q: 관리자인데 명령어가 안 됨
**A**: CommandWhitelist admin 그룹 권한 확인
```bash
/lp user <player> permission check commandwhitelist.group.admin
```

---

## 10. 관련 파일

| 파일 | 위치 | 설명 |
|------|------|------|
| `plugin.yml` | `tycoon-lite/src/main/resources/` | 명령어/권한 정의 |
| `config.yml` | `plugins/CommandWhitelist/` | 명령어 화이트리스트 |
| `groups/admin.yml` | `plugins/LuckPerms/yaml-storage/` | LuckPerms admin 그룹 |

---

## 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-01-25 | 초안 작성 (Phase 2 완료) |
| 2026-01-28 | Phase 8 업데이트: Recovery, AngelChest 권한 추가, 명령어 목록 확장 |
