# TycoonLite

> **VanillaPlus Minecraft Economy Plugin** for Paper 1.20.1+

바닐라 마인크래프트의 감성을 유지하면서 경제, 직업, 강화 시스템을 추가하는 종합 서버 플러그인입니다.

---

## 주요 기능

### 경제 시스템
- **BD (기본 화폐)**: Vault 연동 기본 화폐
- **BottCoin (특수 화폐)**: 업적/이벤트 보상용 프리미엄 화폐
- **동적 가격 시스템**: 수요/공급에 따른 가격 변동
- **시장 조작 탐지**: 비정상 거래 패턴 감지

### 직업 시스템
| Tier | 직업 | 설명 |
|------|------|------|
| 1 | 광부 (Miner) | 광석 채굴 및 판매 |
| 1 | 농부 (Farmer) | 작물 재배 및 판매 |
| 1 | 어부 (Fisher) | 낚시 및 수산물 판매 |
| 2 | 장인 (Artisan) | 열쇠/귀중품 제작 (비활성화) |
| 2 | 셰프 (Chef) | 버프 음식 제작 (비활성화) |
| 2 | 약초사 (Herbalist) | 포션 양조 (비활성화) |

**직업 특징:**
- 레벨 1~100, 4개 등급 (GRADE_1~4)
- 해금 조건: 도감 25종 + 20,000 BD
- 레벨당 판매가 보너스 (+5.5% ~ +7%)
- 승급 시 추가 혜택

### 강화 시스템 (Enhance)

#### 커스텀 인챈트
- **무기**: 출혈, 번개강타, 빙결, 흡혈, 고정피해
- **방어구**: 이동속도, 이단점프, 재생
- **도구**: 광맥채굴, 수확, 텔레키네시스, 지혜

#### 램프 시스템
- 아이템에 램프 부착 → 확률적 효과 부여
- 57개 램프 효과 (전투/채굴/범용)
- 200회 천장 시스템 (LEGENDARY 확정)

#### 강화 (+0 ~ +100)
- NPC GUI를 통한 강화
- 구간별 성공/하락/파괴 확률
- 보호 주문서 시스템

### 주변부 시스템

#### 도감 (Codex)
- 바닐라 아이템 수집 시스템
- 20+ 카테고리, 마일스톤 보상
- `/codex` - GUI 열기

#### 업적 (Achievement)
- 도감, 직업, PvP, 바닐라 진척도 기반 업적
- 달성 시 BottCoin/칭호 보상
- `/achievement list`

#### 거래 (Trade)
- P2P 아이템/화폐 거래
- GUI 기반 안전 거래
- 쿨다운 및 로그 시스템

#### 칭호 (Title)
- 업적 달성 시 칭호 해금
- LuckPerms 그룹 연동
- TAB 플러그인 prefix 자동 적용

### 월드 시스템
| 월드 | 설명 |
|------|------|
| Town | 마을 (안전 구역, PEACEFUL) |
| Wild | 야생 (PvP 10%, HARD) |
| Hunter | 헌터 PvP (100% 데미지) |
| Duel | 1vs1 결투 아레나 |
| Dungeon | RPG 던전 (비활성화) |
| Casino | 도박 시스템 (비활성화) |

**Wild 월드 특징:**
- 7일 주기 자동/수동 리셋
- ZIP 백업 및 복원
- 다중 스폰 포인트 (5개)
- Citizens NPC 귀환 포탈

### 악용 방지 시스템
- **CustomItemVanillaBlocker**: 커스텀 아이템 조합/모루/화로 차단
- **VillagerTradeBlocker**: 주민 거래 제한 (10회 무료, 이후 BD 비용)
- **X-ray Analyzer**: 휴리스틱 X-ray 탐지
- **AFK Dampen**: AFK 보상 감쇠
- **Market Manipulation**: 시장 조작 탐지

---

## 설치 방법

### 1. 요구사항
- **서버**: Paper 1.20.1+
- **Java**: 17+

### 2. 필수 플러그인
```yaml
depend:
  - Vault
  - LuckPerms
  - Citizens
  - WorldGuard
```

### 3. 선택 플러그인 (연동 시 추가 기능)
```yaml
softdepend:
  - WorldEdit
  - CoreProtect
  - PlaceholderAPI
  - Lands              # 땅 시스템 연동
  - AngelChest         # 데스체스트 연동
  - Multiverse-Core
  - OpenInv
  - spark
  - InventoryRollbackPlus
  - TAB
  - HolographicDisplays
  - Plan
  - DiscordSRV
```

### 4. 설치
1. `TycoonLite.jar`를 `plugins/` 폴더에 복사
2. 서버 시작 (설정 파일 자동 생성)
3. `plugins/TycoonLite/` 폴더에서 설정 수정
4. `/tycoon reload` 또는 서버 재시작

---

## 설정 파일

| 파일 | 설명 |
|------|------|
| `config.yml` | 메인 설정 (월드, 상점, 강화, NPC 등) |
| `jobs.yml` | 직업 설정 (경험치, 승급, 가격) |
| `codex.yml` | 도감 카테고리 및 아이템 |
| `achievements.yml` | 업적 정의 |
| `titles.yml` | 칭호 정의 |
| `shops.yml` | 상점 아이템 및 가격 |
| `antiexploit.yml` | 악용 방지 상세 설정 |

> **설정 가이드**: `docs/reference/CONFIG_GUIDE.md` 참조

---

## 명령어

### 플레이어 명령어
| 명령어 | 설명 | 별칭 |
|--------|------|------|
| `/job` | 직업 정보/선택/승급 | `/jobs`, `/직업` |
| `/job select <직업>` | 직업 선택 (miner/farmer/fisher) | |
| `/job promote` | 승급 시도 | |
| `/codex` | 도감 GUI | `/도감` |
| `/achievement` | 업적 목록 | `/ach`, `/업적` |
| `/title` | 칭호 관리 | `/칭호` |
| `/trade <플레이어>` | 거래 요청 | `/거래` |

### 관리자 명령어
| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/tycoon reload` | 설정 리로드 | `tycoon.admin` |
| `/eco give/take/set` | 경제 관리 | `tycoon.admin.eco` |
| `/job admin` | 직업 관리 | `tycoon.admin.job` |
| `/codexadmin` | 도감 관리 | `tycoon.admin.codex` |
| `/shopadmin` | 상점 관리 | `tycoon.admin.shop` |
| `/worldreset` | 월드 리셋 | `tycoon.admin.worldreset` |
| `/worldtp` | 월드 텔레포트 | `tycoon.admin.worldtp` |
| `/pvpdamage` | PvP 데미지 배율 | `tycoon.admin.pvpdamage` |
| `/enchant` | 인챈트 관리 | `tycoon.admin.enchant` |
| `/lamp` | 램프 관리 | `tycoon.admin.lamp` |
| `/upgrade` | 강화 관리 | `tycoon.admin.upgrade` |

---

## 권한

### 기본 권한
| 권한 | 기본값 | 설명 |
|------|--------|------|
| `tycoon.player` | true | 기본 플레이어 권한 |
| `tycoon.job.use` | true | 직업 명령어 |
| `tycoon.codex.use` | true | 도감 명령어 |
| `tycoon.trade.use` | true | 거래 명령어 |

### 관리자 권한
| 권한 | 기본값 | 설명 |
|------|--------|------|
| `tycoon.admin` | op | 모든 관리 권한 |
| `tycoon.admin.eco` | op | 경제 관리 |
| `tycoon.admin.job` | op | 직업 관리 |
| `tycoon.admin.shop` | op | 상점 관리 |
| `tycoon.xray.bypass` | op | X-ray 탐지 우회 |

---

## 외부 플러그인 연동

### Lands
- 땅 시스템 연동 (레거시 PlotManager 대체)
- 블록 파괴/배치 권한 체크
- 클라이언트 모드 연동 (PLOT_UPDATE 패킷)

### AngelChest
- Wild 리셋 시 AngelChest 만료 처리
- 리플렉션 기반 (softdepend)

### TAB + LuckPerms
- 칭호 장착 시 LuckPerms 그룹 자동 부여
- TAB prefix로 자동 표시

---

## 빌드

```bash
# Maven 빌드
cd tycoon-lite
mvn clean compile
mvn package
```

**출력**: `target/TycoonLite-1.0.0-SNAPSHOT.jar`

---

## 프로젝트 구조

```
tycoon-lite/
├── src/main/java/kr/bapuri/tycoon/
│   ├── TycoonPlugin.java          # 메인 플러그인 클래스
│   ├── bootstrap/                 # 초기화, ServiceRegistry
│   ├── player/                    # 플레이어 데이터
│   ├── economy/                   # 경제 시스템
│   ├── shop/                      # 상점 시스템
│   ├── job/                       # 직업 시스템
│   │   ├── common/                # 공통 인프라
│   │   ├── miner/                 # 광부
│   │   ├── farmer/                # 농부
│   │   └── fisher/                # 어부
│   ├── codex/                     # 도감 시스템
│   ├── achievement/               # 업적 시스템
│   ├── trade/                     # 거래 시스템
│   ├── title/                     # 칭호 시스템
│   ├── enhance/                   # 강화 시스템
│   │   ├── enchant/               # 커스텀 인챈트
│   │   ├── lamp/                  # 램프 시스템
│   │   └── upgrade/               # 강화 시스템
│   ├── world/                     # 월드 관리
│   ├── antiexploit/               # 악용 방지
│   ├── integration/               # 외부 플러그인 연동
│   └── mod/                       # 클라이언트 모드 연동
├── src/main/resources/
│   ├── plugin.yml
│   ├── config.yml
│   ├── jobs.yml
│   └── ...
├── docs/                          # 문서
│   ├── planning/                  # 기획 문서
│   ├── reports/                   # Phase 완료 보고서
│   └── reference/                 # 참조 가이드
└── pom.xml
```

---

## 문서

| 문서 | 설명 |
|------|------|
| `docs/planning/LITE_MASTER_TRACKER.md` | 전체 개발 진행 상황 |
| `docs/reports/PHASE*_COMPLETION_REPORT.md` | Phase별 완료 보고서 |
| `docs/reports/PHASE8_TEST_CHECKLIST.md` | 테스트 체크리스트 |
| `docs/reference/COMMAND_PERMISSION_GUIDE.md` | 명령어/권한 상세 가이드 |
| `docs/reference/EXTERNAL_PLUGIN_ANALYSIS.md` | 외부 플러그인 분석 |

---

## 버전 이력

### v1.0.0 (개발 중)
- Phase 0~7 완료
- Tier 1 직업 (Miner, Farmer, Fisher)
- 강화 시스템 (인챈트, 램프, 강화)
- 도감, 업적, 거래, 칭호
- Lands/AngelChest 연동
- 악용 방지 시스템

### 향후 계획 (v1.1+)
- Tier 2 직업 활성화
- CustomCrops 연동
- Oraxen 커스텀 광물
- 던전 시스템

---

## 라이선스

Private - All rights reserved

---

## 제작자

- **Bapuri**

---

*TycoonLite - VanillaPlus 마인크래프트 경제 플러그인*
