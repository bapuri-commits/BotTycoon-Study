# Changelog

이 문서는 TycoonLite의 모든 주요 변경 사항을 기록합니다.

형식: [Keep a Changelog](https://keepachangelog.com/ko/1.0.0/)  
버전: [Semantic Versioning](https://semver.org/lang/ko/)

---

## [Unreleased]

### Phase 8 진행중 - 테스트 및 버그 수정

#### 수정됨 (Fixed)
- **Codex 레거시 규칙 적용**
  - `required` 값 1 또는 10으로 정규화
  - `consume` 자동 결정 (required=1: 소멸안함, required=10: 소멸)
  
- **WildSpawn NPC 고정**
  - `setAI(false)`, `setCollidable(false)` 추가
  - 물/밀림에 의한 NPC 이동 방지

- **방어구 강화 밸런스**
  - 낙하/익사/마법 피해 강화 효과 제외

- **Recovery 시스템 관리자 전용**
  - `/recovery` 명령어 `tycoon.admin.recovery` 권한 필수
  - 일반 플레이어는 NPC 통해서만 접근

- **직업 에러 추적 강화**
  - `JobService`에 try-catch + 상세 로깅 추가

- **거래 GUI 수정**
  - `TradeCommand`에 `TradeGui` 주입 완료

#### 추가됨 (Added)
- `NPC_INSTALLATION_GUIDE.md`: Citizens NPC 설치 가이드
- `COMMAND_PERMISSION_GUIDE.md` 업데이트: LuckPerms 설정 추가
- `PHASE8_TEST_CHECKLIST.md`: 코드 감사 결과 반영

#### 확인됨 (Verified - 코드 감사)
- 버그 10건 코드 확인 완료
- 대부분 이미 수정됨 또는 런타임 테스트 필요

### 예정
- 런타임 테스트 계속 진행
- v1.0.0 정식 릴리즈

---

## [1.0.0-SNAPSHOT] - 2026-01-28

### Phase 7 완료 - 통합 및 연동

#### 추가됨 (Added)
- **Lands 플러그인 연동**
  - `LandsIntegration.java`: 리플렉션 기반 API 연동
  - `LandsListener.java`: 블록 파괴/배치 권한 체크
  - 클라이언트 모드 PLOT_UPDATE 패킷 지원

- **GUI 시스템 이식**
  - `CodexGuiManager.java`: 도감 GUI (카테고리별 자연스러운 그리드 배치)
  - `TradeGui.java`: P2P 거래 GUI (ConcurrentHashMap 사용)

- **Enhance 시스템 완전 등록**
  - 서비스 3개, 리스너 8개, 명령어 3개 초기화 코드 추가
  - `/enchant`, `/lamp`, `/upgrade` 명령어 활성화

- **서버 설정 최적화**
  - Paper timings 비활성화 (제거 예정 경고 해결)
  - AngelChest 업데이트 체크 비활성화

#### 수정됨 (Fixed)
- `CodexCommand.countVanillaItems()` 일관성 수정
- `LandsListener.onPlayerQuit` 메모리 누수 수정
- `plugin.yml`에 Lands, AngelChest softdepend 추가

---

## [0.7.0] - 2026-01-28

### Phase 6 완료 - Enhance 시스템 이식

#### 추가됨 (Added)
- **커스텀 인챈트 시스템** (11개 인챈트)
  - 무기: bleed, thunder_strike, frost_aspect, vampire, true_damage
  - 방어구: speed_boost, double_jump, regeneration
  - 도구: vein_miner, harvest, telekinesis, wisdom

- **램프 시스템** (57개 효과)
  - 9개 램프 타입 (weapon, armor, tool, universal 등)
  - 200회 천장 시스템 (LEGENDARY 확정)
  - 희귀도 기반 확률 테이블

- **강화 시스템** (+0 ~ +100)
  - 구간별 성공/하락/파괴 확률
  - 보호 주문서 시스템
  - GUI 기반 강화 인터페이스

- **리팩토링**
  - 레거시 43개 → LITE 36개 파일 (핵심 기능 유지)
  - vanilla-plus 전용 의존성 완전 제거

---

## [0.6.0] - 2026-01-27

### Phase 5 완료 - 주변부 시스템

#### 추가됨 (Added)
- **도감 시스템** (Codex)
  - 6개 파일: CodexRegistry, CodexService, CodexCommand 등
  - codex.yml 외부화 (카테고리/마일스톤 설정)
  - 바닐라 아이템만 등록 (커스텀 아이템 필터링)

- **업적 시스템** (Achievement)
  - 8개 파일: AchievementRegistry, AchievementService 등
  - achievements.yml 외부화
  - 4종 업적: CODEX, JOB, PVP, VANILLA

- **거래 시스템** (Trade)
  - 9개 파일: TradeSession, TradeService, TradeCommand 등
  - config.yml 연동 (쿨타임/타임아웃)
  - 트랜잭션 로그 (수동 롤백 지원)

- **칭호 시스템** (Title)
  - 4개 파일: TitleManager, TitleCommand 등
  - titles.yml 외부화
  - LuckPerms 그룹 연동, TAB prefix 자동 적용

---

## [0.5.0] - 2026-01-27

### Phase 4 완료 - Tier 1 직업 시스템

#### 추가됨 (Added)
- **직업 공통 인프라** (Phase 4.A)
  - JobExpCalculator: 경험치/레벨 공식 (설정 가능)
  - UnlockCondition 인터페이스 (도감/BD 조건)
  - SellService: 통합 판매 서비스
  - PricingPolicy: 가격 정책 (레벨 보너스)
  - JobRegistry: 직업 서비스 레지스트리

- **광부 (Miner)** (Phase 4.B)
  - MinerExpService, MinerGradeService
  - MinerListener: 채굴 이벤트 처리
  - CopperOxidationHandler: 산화 구리 시스템 (설정 기반)
  - RefineryService: 정제소 stub

- **농부 (Farmer)** (Phase 4.C)
  - FarmerExpService, FarmerGradeService
  - FarmerListener: 수확 이벤트 처리
  - FarmlandLimitSystem: 청크당 196 농지 제한

- **어부 (Fisher)** (Phase 4.D)
  - FisherExpService, FisherGradeService
  - FisherListener: 낚시 이벤트 처리
  - FishRarityDistribution: Town/Wild 희귀도 분포
  - PitySystem: 천장 시스템 stub

- **직업 리워크** (Phase 4.E)
  - 해금 조건 상향: 10종/500 BD → 25종/20,000 BD
  - 경험치 초반 2배 느리게: Lv1→2: 55→110 XP
  - 다중 Tier1 stub (GRADE_4 달성 시 2번째 슬롯)

---

## [0.4.0] - 2026-01-27

### Phase 3.5 완료 - 월드 시스템

#### 추가됨 (Added)
- **WorldType enum 개선**
  - 기본 설정 내장 (difficulty, pvp, mobSpawning 등)
  - TOWN: PEACEFUL, PvP OFF
  - WILD: HARD, PvP ON (10% 데미지)

- **WorldManager 리팩토링**
  - enum 기반 설정 적용
  - 헬퍼 메서드 추가

- **월드 리셋 시스템**
  - WorldResetCommand: 수동 리셋 + ZIP 백업
  - WorldResetScheduler: 자동 리셋 (기본 OFF)
  - 크래시 복구 메커니즘 (state file 기반)

- **WildSpawnManager**
  - 다중 스폰 포인트 (5개, 500블록 간격)
  - Citizens NPC 귀환 포탈 자동 생성
  - 기반암 5×5 플랫폼

- **AngelChestIntegration**
  - 리플렉션 기반 DeathChest 연동
  - Wild 리셋 시 AngelChest 만료 처리

- **PvP 데미지 명령어**
  - `/pvpdamage 10|50|100|off`

---

## [0.3.0] - 2026-01-26

### Phase 3 완료 - 경제/상점 + 기본 차단

#### 추가됨 (Added)
- **경제 시스템**
  - EconomyService: Vault 연동
  - BD (기본 화폐), BottCoin (특수 화폐)
  - 동적 가격 시스템 (DynamicPriceTracker)

- **상점 시스템**
  - ShopService: 통합 상점 관리
  - MinerShop, FarmerShop, FisherShop
  - NPC 클릭 → 상점 GUI 자동 오픈

- **악용 방지 시스템**
  - CustomItemVanillaBlocker: 조합/모루/화로 차단
  - VillagerTradeBlocker: 주민 거래 제한
  - MarketManipulationDetector: 시장 조작 탐지

---

## [0.2.0] - 2026-01-25

### Phase 2 완료 - 데이터 계층

#### 추가됨 (Added)
- **PlayerDataManager**
  - 자동 저장 (5분 주기)
  - 스냅샷 백업 (30분 주기, 최대 10개)
  - dirty 플래그 최적화

- **PlayerTycoonData**
  - 직업, 경제, 도감, 업적, 칭호 데이터
  - 직업 통계 필드 (totalMined, totalHarvested 등)

- **InventoryBackupService**
  - 인벤토리 주기적 백업 (1분)
  - 플레이어당 최대 30개 백업

---

## [0.1.5] - 2026-01-24

### Phase 1.5 완료 - Core Refactoring

#### 추가됨 (Added)
- **ServiceRegistry**: 서비스 컨테이너 (Setter Injection)
- **ListenerRegistry**: 리스너 일괄 등록
- **ConfigManager**: 설정 로더

#### 변경됨 (Changed)
- 모듈화 및 의존성 정리
- DROP 대상 시스템 제거

---

## [0.1.0] - 2026-01-24

### Phase 1 완료 - Minimal Boot

#### 추가됨 (Added)
- **TycoonPlugin.java**: 메인 플러그인 클래스
- **plugin.yml**: 플러그인 메타데이터
- **config.yml**: 기본 설정 파일

#### 확인됨 (Verified)
- 서버 부팅 성공
- 콘솔 에러 0

---

## [0.0.0] - 2026-01-23

### Phase 0 완료 - 스코프 확정

#### 결정됨 (Decided)
- KEEP/DROP/HOLD 분류 완료
- 외부 플러그인 버전 확정
- 정책 결정 (상점, 커스텀 아이템, ProtocolLib)

#### 문서화 (Documented)
- `LITE_MASTER_TRACKER.md`
- `LITE_EXTRACTION_PLAN.md`
- `PHASE0_SCOPE_CHECKLIST.md`

---

## 버전 비교 링크

- [Unreleased]: 개발 중
- [1.0.0-SNAPSHOT]: Phase 7 완료
- [0.7.0]: Phase 6 완료
- [0.6.0]: Phase 5 완료
- [0.5.0]: Phase 4 완료
- [0.4.0]: Phase 3.5 완료
- [0.3.0]: Phase 3 완료
- [0.2.0]: Phase 2 완료
- [0.1.5]: Phase 1.5 완료
- [0.1.0]: Phase 1 완료
- [0.0.0]: Phase 0 완료
