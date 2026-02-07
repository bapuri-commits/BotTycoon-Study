# BotTycoon 코드 스터디 & 유지보수 가이드

> 컴퓨터공학 학부생을 위한 학습 가이드이자, 향후 개발/유지보수를 위한 참고 문서

---

## 📖 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [아키텍처 이해하기](#아키텍처-이해하기)
3. [모듈별 완전 분석](#모듈별-완전-분석)
4. [학습 로드맵](#학습-로드맵)
5. [핵심 디자인 패턴 (심화)](#핵심-디자인-패턴-심화)
6. [코드에서 배우는 CS 개념](#코드에서-배우는-cs-개념)
7. [효과적인 학습 전략](#효과적인-학습-전략)
8. [개발/관리 필수 도구](#개발관리-필수-도구)
9. [유지보수 가이드](#유지보수-가이드)
10. [확장/개발 가이드](#확장개발-가이드)
11. [추천 학습 자료](#추천-학습-자료)
12. [포트폴리오로 만들기](#포트폴리오로-만들기)
13. [타이쿤 1.21.X 개발 vs 토이 프로젝트](#타이쿤-121x-개발-vs-토이-프로젝트)
14. [2026년 연간 학습 계획](#2026년-연간-학습-계획)
15. [프로젝트 상세 설계](#프로젝트-상세-설계)

---

## 프로젝트 개요

### 구성 요소

| 프로젝트 | 위치 | 설명 | 빌드 |
|---------|------|------|------|
| **tycoon-lite** | `G:\tycoon-lite` | 서버 플러그인 (Paper API) | Maven |
| **tycoon-hud** | `G:\Minecraft Mod\tycoon-client-template-1.20.1\tycoon-hud` | 클라이언트 HUD 모드 | Gradle |
| **tycoon-ui** | `G:\Minecraft Mod\tycoon-client-template-1.20.1\tycoon-ui` | 클라이언트 통합 UI 모드 | Gradle |

### 기술 스택

```
┌─────────────────────────────────────────────────────────────┐
│                        SERVER SIDE                          │
├─────────────────────────────────────────────────────────────┤
│  Java 21 + Paper API 1.20.1 + Maven                        │
│  외부 연동: Vault, Citizens, WorldGuard, Lands, LuckPerms  │
└─────────────────────────────────────────────────────────────┘
                              ↕ JSON over Plugin Messaging
┌─────────────────────────────────────────────────────────────┐
│                        CLIENT SIDE                          │
├─────────────────────────────────────────────────────────────┤
│  Java 17 + Fabric 1.20.1 + Gradle                          │
│  기술: Mixin, HudRenderCallback, ClientPlayNetworking      │
└─────────────────────────────────────────────────────────────┘
```

---

## 아키텍처 이해하기

### 전체 시스템 아키텍처

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                              BotTycoon System                                   │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────┐        JSON/PluginMsg        ┌─────────────────┐ │
│  │   tycoon-lite (서버)     │◄────────────────────────────►│ tycoon-hud/ui   │ │
│  │   Paper Plugin (Java 21) │                              │ Fabric Mod      │ │
│  └─────────────────────────┘                              └─────────────────┘ │
│              │                                                     │           │
│              ▼                                                     ▼           │
│  ┌─────────────────────────┐                              ┌─────────────────┐ │
│  │ External Plugins        │                              │ Minecraft       │ │
│  │ - Vault (경제 API)       │                              │ Client 1.20.1   │ │
│  │ - Citizens (NPC)        │                              │ - HUD Overlay   │ │
│  │ - Lands (땅 관리)        │                              │ - Custom GUI    │ │
│  │ - LuckPerms (권한)      │                              │ - Mixin Hooks   │ │
│  │ - WorldGuard (리전)     │                              └─────────────────┘ │
│  └─────────────────────────┘                                                   │
│                                                                                 │
└────────────────────────────────────────────────────────────────────────────────┘
```

### 서버 플러그인 상세 계층 구조 (tycoon-lite)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           BOOTSTRAP LAYER (초기화 계층)                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  TycoonPlugin.java (진입점, 2284줄)                                              │
│  ├── onEnable()                                                                  │
│  │   ├── checkDependencies()     → 필수 플러그인 확인                             │
│  │   ├── initServices()          → 서비스 초기화 (순서 중요!)                      │
│  │   ├── initListeners()         → 이벤트 리스너 등록                             │
│  │   └── initCommands()          → 명령어 등록                                   │
│  └── onDisable()                                                                │
│      ├── saveAll()               → 데이터 저장                                   │
│      └── shutdown()              → 리소스 정리                                   │
│                                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                 │
│  │ ServiceRegistry │  │ ListenerRegistry│  │ ConfigManager   │                 │
│  │ (DI 컨테이너)    │  │ (리스너 관리)    │  │ (설정 로드)      │                 │
│  │ - 40+ 서비스     │  │ - 48+ 리스너    │  │ - 12+ YAML     │                 │
│  │ - Setter DI     │  │ - 일괄 등록     │  │ - 자동 업데이트  │                 │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                 │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           CORE SERVICES LAYER (핵심 서비스)                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                 │
│  │ PlayerDataMgr  │  │ EconomyService  │  │ WorldManager    │                 │
│  │                 │  │                 │  │                 │                 │
│  │ • UUID → Data   │  │ • BD 잔액 관리   │  │ • Town/Wild 관리│                 │
│  │ • JSON 저장     │  │ • 입출금 트랜잭션 │  │ • 월드 리셋     │                 │
│  │ • 캐시 관리     │  │ • Vault 연동    │  │ • 스폰 관리     │                 │
│  │ • 백업/복원     │  │ • 소득세 계산    │  │ • 포탈 격리     │                 │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                 │
│                                                                                  │
│  ┌─────────────────┐                                                            │
│  │ AdminService    │  권한 체크, 관리자 기능, 로깅                                │
│  └─────────────────┘                                                            │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         FEATURE SYSTEMS LAYER (기능 시스템)                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐           │
│  │   JOB        │ │    SHOP      │ │   ENHANCE    │ │   CODEX      │           │
│  │   직업 시스템  │ │    상점 시스템  │ │   강화 시스템   │ │   도감 시스템   │           │
│  ├──────────────┤ ├──────────────┤ ├──────────────┤ ├──────────────┤           │
│  │ • JobRegistry│ │ • ShopService│ │ • Enchant    │ │ • CodexReg   │           │
│  │ • JobService │ │ • MinerShop  │ │ • Lamp       │ │ • CodexSvc   │           │
│  │ • ExpService │ │ • FarmerShop │ │ • Upgrade    │ │ • GUI        │           │
│  │ • GradeService│ │ • MarketShop│ │ • Processing │ │              │           │
│  │ • Listeners  │ │ • GUI        │ │ • GUI        │ │              │           │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘           │
│                                                                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐           │
│  │  ACHIEVEMENT │ │    TITLE     │ │    TRADE     │ │     TAX      │           │
│  │   업적 시스템  │ │   칭호 시스템   │ │   거래 시스템   │ │   세금 시스템   │           │
│  ├──────────────┤ ├──────────────┤ ├──────────────┤ ├──────────────┤           │
│  │ • Registry   │ │ • TitleReg   │ │ • TradeGui   │ │ • IncomeTax  │           │
│  │ • Service    │ │ • LuckPerms  │ │ • Session    │ │ • LandTax    │           │
│  │ • Listener   │ │ • Command    │ │ • Cooldown   │ │ • Scheduler  │           │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘           │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        INTEGRATION LAYER (외부 연동 계층)                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐   │
│  │   Vault    │ │  Citizens  │ │   Lands    │ │ WorldGuard │ │ AngelChest │   │
│  │  (경제 API) │ │   (NPC)    │ │ (땅 시스템) │ │  (리전)    │ │(데스체스트)│   │
│  ├────────────┤ ├────────────┤ ├────────────┤ ├────────────┤ ├────────────┤   │
│  │ VaultInteg │ │ Citizens   │ │ LandsInteg │ │ WorldGuard │ │ AngelChest │   │
│  │ ration     │ │ Integration│ │ ration     │ │ Integration│ │ Integration│   │
│  │            │ │ • 핸들러    │ │ • 권한체크  │ │ • 리전체크  │ │ • 아이템   │   │
│  │ Economy    │ │ • NPC상점   │ │ • 세금연동  │ │ • 플래그   │ │   이관     │   │
│  │ Provider   │ │            │ │            │ │            │ │            │   │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘ └────────────┘   │
│                                                                                  │
│  특징: Optional<T> 패턴으로 softdepend 처리 - 없어도 서버 동작                     │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           MOD COMMUNICATION LAYER                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      mod/ 패키지                                         │   │
│  │                                                                          │   │
│  │  서버 → 클라이언트                      클라이언트 → 서버                  │   │
│  │  ┌─────────────────┐                   ┌─────────────────┐               │   │
│  │  │ ModDataService  │                   │ ModRequestHandler│              │   │
│  │  │                 │    tycoon:ui_data │                 │              │   │
│  │  │ • sendProfile() │ ─────────────────►│ • handleRequest()│              │   │
│  │  │ • sendJobData() │ ◄─────────────────│ • validateType() │              │   │
│  │  │ • sendCodex()   │   tycoon:ui_request                  │              │   │
│  │  └─────────────────┘                   └─────────────────┘               │   │
│  │                                                                          │   │
│  │  ┌─────────────────┐                                                     │   │
│  │  │ ModEventBridge  │  실시간 이벤트 → 패킷 변환                            │   │
│  │  │ • 도감 등록 알림 │  (승급, 도감, 경제 변동 등)                           │   │
│  │  └─────────────────┘                                                     │   │
│  │                                                                          │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 클라이언트 모드 상세 계층 구조

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        tycoon-hud (기반 모드) - Fabric 1.20.1                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  TycoonHudMod.java (진입점)                                                      │
│  ├── onInitializeClient()                                                       │
│  │   ├── UiDataReceiver.register()      → 네트워크 수신 등록                      │
│  │   ├── TycoonClientState.register()   → 연결 상태 관리                         │
│  │   ├── HudRenderCallback 등록          → HUD 오버레이들                        │
│  │   ├── KeyBindingHelper 등록           → 키바인딩                              │
│  │   └── BlueZoneWorldRenderer.register()→ 3D 렌더링                            │
│  │                                                                              │
│  │  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  │                      NETWORK LAYER (net/)                          │     │
│  │  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │     │
│  │  │  │ UiDataReceiver  │  │PlayerDataManager│  │TycoonClientState│   │     │
│  │  │  │ (패킷 수신/파싱) │  │ (데이터 캐시)    │  │ (연결 상태)      │   │     │
│  │  │  │                 │  │ • 싱글톤        │  │ • 초기 요청      │   │     │
│  │  │  │ • handlePacket()│  │ • 스레드 안전   │  │ • 재연결 감지    │   │     │
│  │  │  │ • 타입별 분기   │  │ • Optional     │  │                 │   │     │
│  │  │  └─────────────────┘  └─────────────────┘  └─────────────────┘   │     │
│  │  └────────────────────────────────────────────────────────────────────┘     │
│  │                                                                              │
│  │  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  │                        HUD LAYER (hud/)                            │     │
│  │  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ │     │
│  │  │  │ProfileHud   │ │VitalHud     │ │HunterHud    │ │ToastOverlay │ │     │
│  │  │  │(좌상단 프로필)│ │(하단 체력)   │ │(미니맵)      │ │(토스트 알림) │ │     │
│  │  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ │     │
│  │  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐                 │     │
│  │  │  │DuelHud      │ │CountdownOvl │ │DungeonMapOvl│                 │     │
│  │  │  │(듀얼 정보)   │ │(카운트다운)  │ │(던전 맵)    │                 │     │
│  │  │  └─────────────┘ └─────────────┘ └─────────────┘                 │     │
│  │  └────────────────────────────────────────────────────────────────────┘     │
│  │                                                                              │
│  │  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  │                       MIXIN LAYER (mixin/)                         │     │
│  │  │  ┌─────────────────────┐  ┌─────────────────────┐                 │     │
│  │  │  │ InGameHudMixin      │  │ MinecraftClientMixin │                │     │
│  │  │  │ • 바닐라 체력바 숨김 │  │ • ESC 키 커스터마이징 │                │     │
│  │  │  │ • @Inject + cancel  │  │ • 큰 맵 열림 시 처리  │                │     │
│  │  │  └─────────────────────┘  └─────────────────────┘                 │     │
│  │  └────────────────────────────────────────────────────────────────────┘     │
│  │                                                                              │
│  │  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  │                       MODEL LAYER (model/)                         │     │
│  │  │  PlayerProfileData, VitalData, JobData, HunterMapData, ...         │     │
│  │  │  (서버에서 받은 JSON을 Java 객체로 변환 - DTO 패턴)                    │     │
│  │  └────────────────────────────────────────────────────────────────────┘     │
│  │                                                                              │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │ depends
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                             tycoon-ui (UI 모드)                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  TycoonUiMod.java (진입점)                                                       │
│  │                                                                              │
│  │  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  │                      screen/ (화면)                              │        │
│  │  │  ┌─────────────────────────────────────────────────────────┐   │        │
│  │  │  │ TycoonScreen.java (메인 화면)                            │   │        │
│  │  │  │ • 4개 탭 관리                                            │   │        │
│  │  │  │ • 탭 전환 애니메이션                                       │   │        │
│  │  │  │ • 키바인딩 처리                                           │   │        │
│  │  │  └─────────────────────────────────────────────────────────┘   │        │
│  │  │                                                                  │        │
│  │  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────┐│        │
│  │  │  │ProfileTab   │ │JobTab       │ │CodexTab     │ │EconomyTab ││        │
│  │  │  │(프로필)      │ │(직업 정보)   │ │(도감)        │ │(경제 내역) ││        │
│  │  │  │             │ │             │ │             │ │           ││        │
│  │  │  │ AbstractTab │ │ AbstractTab │ │ AbstractTab │ │AbstractTab││        │
│  │  │  │ 상속        │ │ 상속        │ │ 상속        │ │상속       ││        │
│  │  │  └─────────────┘ └─────────────┘ └─────────────┘ └───────────┘│        │
│  │  └─────────────────────────────────────────────────────────────────┘        │
│  │                                                                              │
│  │  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  │                      net/ (네트워크)                             │        │
│  │  │  ┌─────────────────┐  ┌─────────────────┐                      │        │
│  │  │  │ UiRequestSender │  │UiResponseHandler│                      │        │
│  │  │  │ (요청 전송)      │  │ (응답 처리)      │                      │        │
│  │  │  │ • 도감 조회 요청 │  │ • 데이터 저장    │                      │        │
│  │  │  │ • 거래 내역 요청 │  │ • 탭 갱신       │                      │        │
│  │  │  └─────────────────┘  └─────────────────┘                      │        │
│  │  └─────────────────────────────────────────────────────────────────┘        │
│  │                                                                              │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 데이터 흐름 다이어그램

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DATA FLOW: 플레이어 접속                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  1. 플레이어 접속                                                                │
│     │                                                                           │
│     ▼                                                                           │
│  ┌──────────────────┐                                                           │
│  │ PlayerJoinEvent  │  (Bukkit 이벤트)                                          │
│  └────────┬─────────┘                                                           │
│           │                                                                     │
│           ▼                                                                     │
│  ┌──────────────────┐    JSON 로드    ┌──────────────────┐                     │
│  │PlayerDataManager │◄───────────────│ players/{uuid}.json│                    │
│  │  • get(player)   │                └──────────────────┘                     │
│  │  • cache에 저장   │                                                          │
│  └────────┬─────────┘                                                           │
│           │                                                                     │
│           ▼                                                                     │
│  ┌──────────────────┐                                                           │
│  │ ModDataService   │                                                           │
│  │ • sendProfile()  │                                                           │
│  └────────┬─────────┘                                                           │
│           │  JSON 패킷                                                          │
│           ▼                                                                     │
│  ───────────────────  네트워크 ─────────────────────                            │
│           │                                                                     │
│           ▼                                                                     │
│  ┌──────────────────┐                                                           │
│  │ UiDataReceiver   │  (클라이언트)                                             │
│  │ • handlePacket() │                                                           │
│  └────────┬─────────┘                                                           │
│           │                                                                     │
│           ▼                                                                     │
│  ┌──────────────────┐                                                           │
│  │PlayerDataManager │  (클라이언트 캐시)                                        │
│  │  • 데이터 저장    │                                                          │
│  └────────┬─────────┘                                                           │
│           │                                                                     │
│           ▼                                                                     │
│  ┌──────────────────┐                                                           │
│  │ ProfileHudOverlay│  HUD에 표시                                               │
│  │ • render()       │                                                           │
│  └──────────────────┘                                                           │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        DATA FLOW: 블록 파괴 (채굴)                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  1. 플레이어가 광물 파괴                                                         │
│     │                                                                           │
│     ▼                                                                           │
│  ┌──────────────────┐                                                           │
│  │ BlockBreakEvent  │                                                           │
│  └────────┬─────────┘                                                           │
│           │                                                                     │
│           ├───────────────────┐                                                 │
│           ▼                   ▼                                                 │
│  ┌──────────────────┐  ┌──────────────────┐                                    │
│  │ MinerListener    │  │EnchantEffectList │  (동시 처리)                        │
│  │ • 경험치 계산     │  │ • VEIN_MINER?    │                                    │
│  │ • 등급 보너스     │  │ • TELEKINESIS?   │                                    │
│  └────────┬─────────┘  └────────┬─────────┘                                    │
│           │                     │                                               │
│           │       ┌─────────────┘                                               │
│           │       ▼                                                             │
│           │  ┌──────────────────────────────────────┐                          │
│           │  │ BlockProcessingService (파이프라인)  │                          │
│           │  │                                      │                          │
│           │  │  ┌─────────────┐  Priority 100      │                          │
│           │  │  │FortuneProc  │───────────────►    │                          │
│           │  │  └─────────────┘                    │                          │
│           │  │  ┌─────────────┐  Priority 200      │                          │
│           │  │  │GradeBonusP  │───────────────►    │                          │
│           │  │  └─────────────┘                    │                          │
│           │  │  ┌─────────────┐  Priority 400      │                          │
│           │  │  │LampEffectP  │───────────────►    │                          │
│           │  │  └─────────────┘                    │                          │
│           │  │  ┌─────────────┐  Priority 500      │                          │
│           │  │  │JobExpProc   │───────────────►    │                          │
│           │  │  └─────────────┘                    │                          │
│           │  │  ┌─────────────┐  Priority 900      │                          │
│           │  │  │DeliveryProc │───────────────►    │  최종 아이템 지급         │
│           │  │  └─────────────┘                    │                          │
│           │  │                                      │                          │
│           │  └──────────────────────────────────────┘                          │
│           │                                                                     │
│           ▼                                                                     │
│  ┌──────────────────┐                                                           │
│  │ PlayerDataManager│  경험치/레벨 저장                                         │
│  │ • addJobExp()    │                                                           │
│  └────────┬─────────┘                                                           │
│           │                                                                     │
│           ▼                                                                     │
│  ┌──────────────────┐                                                           │
│  │ ModEventBridge   │  클라이언트에 실시간 알림                                  │
│  │ • onJobExpGain() │                                                           │
│  └──────────────────┘                                                           │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 모듈별 완전 분석

### 서버 (tycoon-lite) 전체 패키지 구조

```
src/main/java/kr/bapuri/tycoon/
│
├── TycoonPlugin.java              # 메인 진입점 (2284줄)
│
├── bootstrap/                      # 🟢 플러그인 부트스트랩
│   ├── ServiceRegistry.java       # DI 컨테이너 (40+ 서비스 관리)
│   ├── ListenerRegistry.java      # 리스너 일괄 등록
│   ├── ConfigManager.java         # YAML 설정 로드
│   └── ConfigUpdater.java         # 설정 버전 자동 업그레이드
│
├── player/                         # 🟢 플레이어 데이터 관리
│   ├── PlayerDataManager.java     # 핵심! UUID→데이터 매핑, 캐시, 저장
│   ├── PlayerTycoonData.java      # 플레이어 데이터 모델 (POJO)
│   ├── PlayerSessionListener.java # 접속/퇴장 이벤트 처리
│   └── BackupManager.java         # 데이터 백업/복원
│
├── economy/                        # 🟡 경제 시스템
│   ├── EconomyService.java        # 잔액 조회/입출금 (핵심 로직)
│   ├── vault/
│   │   └── VaultIntegration.java  # Vault API 구현 (외부 플러그인 연동)
│   ├── EcoCommand.java            # /eco 관리자 명령어
│   └── MoneyCommand.java          # /money 플레이어 명령어
│
├── job/                            # 🟡 직업 시스템
│   ├── JobType.java               # Enum: MINER, FARMER, FISHER
│   ├── JobRegistry.java           # 직업 설정 저장소
│   ├── JobService.java            # 직업 관련 비즈니스 로직
│   ├── JobsConfigLoader.java      # jobs.yml 파서
│   ├── Tier1JobCommand.java       # /job 명령어
│   │
│   ├── common/                     # 직업 공통 추상화
│   │   ├── AbstractJobExpService.java    # 경험치 처리 추상 클래스
│   │   ├── AbstractJobGradeService.java  # 등급 승급 추상 클래스
│   │   ├── JobExpCalculator.java         # 경험치 공식 계산
│   │   ├── SellService.java              # 아이템 판매 처리
│   │   ├── GradeBonusConfig.java         # 등급별 보너스 설정
│   │   └── UnlockCondition.java          # 해금 조건
│   │
│   ├── miner/                      # 채굴부 구현
│   │   ├── MinerConfig.java       # 채굴 설정
│   │   ├── MinerExpService.java   # 채굴 경험치 (extends Abstract)
│   │   ├── MinerGradeService.java # 채굴 등급 (extends Abstract)
│   │   └── MinerListener.java     # BlockBreakEvent 처리
│   │
│   ├── farmer/                     # 농부 구현 (유사 구조)
│   │   ├── FarmerConfig.java
│   │   ├── FarmerExpService.java
│   │   ├── FarmerListener.java
│   │   └── sprinkler/              # 스프링클러 서브시스템
│   │
│   ├── fisher/                     # 어부 구현 (유사 구조)
│   │   ├── FisherConfig.java
│   │   ├── FisherExpService.java
│   │   ├── FishRarityDistribution.java  # 희귀도 분포
│   │   └── PitySystem.java              # 천장 시스템
│   │
│   └── npc/                        # 승급 NPC 시스템
│       ├── PromoteNpcRegistry.java
│       ├── PromoteNpcListener.java
│       └── PromoteMainGui.java
│
├── shop/                           # 🟡 상점 시스템
│   ├── IShop.java                 # 상점 인터페이스
│   ├── AbstractShop.java          # 상점 추상 클래스 (Template Method)
│   ├── ShopService.java           # 상점 관리 서비스
│   ├── ShopItem.java              # 상점 아이템 모델
│   ├── ShopGuiManager.java        # GUI 관리
│   ├── ShopNpcRegistry.java       # NPC 연동
│   │
│   ├── job/                        # 직업 상점
│   │   ├── JobShop.java           # 직업 상점 베이스
│   │   ├── MinerShop.java         # 채굴부 상점
│   │   ├── FarmerShop.java        # 농부 상점
│   │   └── FisherShop.java        # 어부 상점
│   │
│   ├── market/                     # 마켓 상점 (동적 가격)
│   │   ├── FoodShop.java
│   │   └── LootShop.java
│   │
│   ├── price/                      # 가격 시스템
│   │   ├── DynamicPriceTracker.java  # 동적 가격 추적
│   │   └── ManipulationGuard.java    # 가격 조작 방지
│   │
│   └── special/
│       └── SpecialItemShop.java   # 특수 아이템 상점
│
├── enhance/                        # 🔴 강화 시스템 (가장 복잡)
│   ├── common/
│   │   └── EnhanceConstants.java  # 공통 상수
│   │
│   ├── enchant/                    # 인챈트 시스템
│   │   ├── CustomEnchant.java     # Enum: 인챈트 종류
│   │   ├── CustomEnchantRegistry.java
│   │   ├── CustomEnchantService.java
│   │   ├── EnchantBookFactory.java     # 인챈트북 생성
│   │   ├── EnchantApplyListener.java   # 적용 처리
│   │   └── EnchantEffectListener.java  # 효과 발동
│   │
│   ├── lamp/                       # 램프 시스템
│   │   ├── LampEffect.java        # Enum: 램프 효과
│   │   ├── LampRegistry.java
│   │   ├── LampService.java
│   │   ├── LampItemFactory.java   # 램프 아이템 생성
│   │   ├── LampApplyListener.java
│   │   └── LampEffectListener.java
│   │
│   ├── upgrade/                    # 강화(+1~+10) 시스템
│   │   ├── UpgradeConfig.java
│   │   ├── UpgradeService.java    # 강화 로직
│   │   ├── UpgradeGui.java        # 강화 UI
│   │   ├── UpgradeStatCalculator.java  # 스탯 계산
│   │   └── ProtectionScrollFactory.java
│   │
│   ├── processing/                 # 🔴 블록 처리 파이프라인
│   │   ├── BlockProcessingService.java  # 파이프라인 메인
│   │   └── processors/
│   │       ├── FortuneProcessor.java      # Priority 100
│   │       ├── EnchantDropBonusProcessor.java  # Priority 150
│   │       ├── GradeBonusProcessor.java   # Priority 200
│   │       ├── LampEffectProcessor.java   # Priority 400
│   │       ├── JobExpProcessor.java       # Priority 500
│   │       └── DeliveryProcessor.java     # Priority 900
│   │
│   └── command/
│       ├── EnchantCommand.java
│       ├── LampCommand.java
│       └── UpgradeCommand.java
│
├── codex/                          # 도감 시스템
│   ├── CodexRegistry.java         # 도감 아이템 등록부
│   ├── CodexService.java          # 등록 로직
│   ├── CodexGuiManager.java       # GUI
│   └── CodexCommand.java
│
├── achievement/                    # 업적 시스템
│   ├── AchievementRegistry.java
│   ├── AchievementService.java
│   ├── AchievementListener.java   # 다양한 이벤트에서 업적 체크
│   └── VanillaAdvancementListener.java  # 바닐라 진행도 연동
│
├── title/                          # 칭호 시스템
│   ├── TitleRegistry.java
│   ├── LuckPermsTitleService.java # LuckPerms prefix/suffix 연동
│   └── TitleCommand.java
│
├── trade/                          # 거래 시스템
│   ├── TradeService.java          # 거래 로직
│   ├── TradeSession.java          # 거래 세션 상태
│   ├── TradeGui.java              # 거래 GUI
│   ├── TradeCooldownManager.java  # 쿨다운 관리
│   └── TradeHistoryManager.java   # 거래 기록
│
├── tax/                            # 세금 시스템
│   ├── TaxConfig.java
│   ├── IncomeTaxService.java      # 소득세
│   ├── LandTaxService.java        # 토지세
│   ├── TaxScheduler.java          # 주기적 징수
│   └── VillagerRegistry.java      # 마을 등록
│
├── integration/                    # 외부 플러그인 연동
│   ├── CitizensIntegration.java   # NPC
│   ├── VaultIntegration.java      # 경제 API
│   ├── WorldGuardIntegration.java # 리전
│   ├── LandsIntegration.java      # 땅 시스템
│   ├── CoreProtectIntegration.java # 블록 로깅
│   └── TycoonPlaceholders.java    # PlaceholderAPI
│
├── mod/                            # 클라이언트 모드 연동
│   ├── ModDataService.java        # 서버→클라이언트 패킷 전송
│   ├── ModRequestHandler.java     # 클라이언트→서버 요청 처리
│   ├── ModEventBridge.java        # 이벤트→패킷 변환
│   ├── ModPacketTypes.java        # 패킷 타입 상수
│   └── ModPlayerListener.java     # 접속/이동 이벤트
│
├── world/                          # 월드 관리
│   ├── WorldManager.java          # 월드 타입 관리
│   ├── WorldType.java             # Enum: TOWN, WILD
│   ├── WorldResetScheduler.java   # 월드 리셋 스케줄링
│   ├── WildSpawnManager.java      # 야생 스폰 관리
│   └── AngelChestIntegration.java # 데스체스트 연동
│
├── antiexploit/                    # 악용 방지
│   ├── CustomItemVanillaBlocker.java
│   ├── VillagerTradeBlocker.java
│   ├── AutoFarmTracker.java       # 자동화 팜 추적
│   └── AutoFarmLimiter.java       # 자동화 팜 제한
│
├── recovery/                       # 아이템 복구
│   ├── RecoveryStorageManager.java
│   ├── RecoveryGui.java
│   └── RecoveryNpcListener.java
│
└── item/                           # 커스텀 아이템
    ├── CoreItemAuthenticator.java # 아이템 진품 확인
    ├── CoreItemService.java       # 아이템 생성
    └── CoreItemUseListener.java   # 아이템 사용 처리
```

### 파일별 역할과 핵심 개념

| 파일 | 줄 수 | 역할 | 핵심 CS 개념 |
|------|-------|------|--------------|
| `TycoonPlugin.java` | 2284 | 플러그인 진입점, 초기화 순서 관리 | 의존성 그래프, 초기화 순서 |
| `ServiceRegistry.java` | 493 | 40+ 서비스 관리 DI 컨테이너 | DI, Setter Injection |
| `PlayerDataManager.java` | ~600 | 플레이어 데이터 CRUD, 캐싱 | 캐시, JSON 직렬화, 동시성 |
| `EconomyService.java` | ~300 | 경제 로직, 트랜잭션 | ACID, 롤백 |
| `AbstractShop.java` | ~400 | 상점 공통 로직 | Template Method 패턴 |
| `BlockProcessingService.java` | ~200 | 효과 파이프라인 | Chain of Responsibility |
| `ModDataService.java` | ~500 | 서버→클라 통신 | 네트워크, JSON 프로토콜 |

---

## 학습 로드맵

### 🟢 1단계: Java OOP 기초 (1~2주)

**목표**: 객체지향 프로그래밍 개념을 실제 코드에서 이해

#### 학습 파일
| 파일 | 핵심 개념 |
|------|----------|
| `shop/IShop.java` | 인터페이스 정의 |
| `shop/AbstractShop.java` | 추상 클래스, 템플릿 메서드 |
| `shop/job/MinerShop.java` | 상속, 메서드 오버라이딩 |

#### 실습 과제
1. `AbstractShop`의 `executeBuy()` 메서드 흐름 추적
2. 클래스 다이어그램 그리기 (IShop → AbstractShop → MinerShop)
3. 새로운 `TestShop` 클래스 만들어보기 (코드만, 동작 X)

#### 핵심 코드 분석

```java
// AbstractShop.java - 템플릿 메서드 패턴
public abstract class AbstractShop implements IShop {
    
    // 공통 로직 (변하지 않는 부분)
    public final TransactionResult executeBuy(Player player, ShopItem item, int amount) {
        // 1. 재고 확인
        // 2. 잔액 확인
        // 3. 차감
        double price = calculatePrice(item, amount);  // 추상 메서드 호출!
        // 4. 아이템 지급
        // 5. 로깅
    }
    
    // 서브클래스에서 구현해야 하는 부분
    protected abstract double calculatePrice(ShopItem item, int amount);
}
```

---

### 🟡 2단계: 디자인 패턴 (2~3주)

**목표**: 실무에서 자주 쓰이는 패턴을 코드에서 발견하고 이해

#### 패턴별 학습 가이드

##### 1. Factory 패턴
- **파일**: `enhance/lamp/LampItemFactory.java`
- **용도**: 복잡한 객체(아이템) 생성 로직 캡슐화
- **학습 포인트**: 
  - 왜 `new LampItem(...)`을 직접 안 쓰고 Factory를 쓸까?
  - ItemMeta 설정, Lore 생성 등 복잡한 초기화 로직 분리

```java
// LampItemFactory.java
public class LampItemFactory {
    public ItemStack createLamp(LampType type, int level) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        
        // 복잡한 초기화 로직...
        meta.setDisplayName(formatName(type, level));
        meta.setLore(buildLore(type, level));
        setCustomModelData(meta, type);
        setPersistentData(meta, type, level);
        
        item.setItemMeta(meta);
        return item;
    }
}
```

##### 2. Registry 패턴
- **파일**: `codex/CodexRegistry.java`, `job/JobRegistry.java`
- **용도**: 설정 파일에서 로드한 데이터를 Map으로 관리
- **학습 포인트**:
  - 왜 List가 아닌 Map을 쓸까? (O(1) 조회)
  - Key 설계의 중요성 (Material, String ID 등)

```java
// CodexRegistry.java
public class CodexRegistry {
    private final Map<Material, CodexRule> rules = new HashMap<>();
    
    public void load() {
        // YAML에서 로드 → Map에 저장
        for (String key : config.getKeys(false)) {
            Material mat = Material.valueOf(key);
            CodexRule rule = parseRule(config.getSection(key));
            rules.put(mat, rule);
        }
    }
    
    public Optional<CodexRule> getRule(Material material) {
        return Optional.ofNullable(rules.get(material));
    }
}
```

##### 3. Observer (Listener) 패턴
- **파일**: `job/miner/MinerListener.java`
- **용도**: 이벤트 발생 시 자동으로 반응
- **학습 포인트**:
  - Bukkit 이벤트 시스템의 동작 원리
  - `@EventHandler` 어노테이션의 역할

```java
// MinerListener.java
public class MinerListener implements Listener {
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        // 광물인가? → 경험치 지급
        if (isOre(block.getType())) {
            minerExpService.addExp(player, block.getType());
        }
    }
}
```

##### 4. Dependency Injection (DI)
- **파일**: `bootstrap/ServiceRegistry.java`
- **용도**: 서비스 간 의존성을 외부에서 주입
- **학습 포인트**:
  - 왜 직접 `new`하지 않고 주입받을까?
  - 순환 의존성 문제와 Setter Injection

```java
// ServiceRegistry.java - 수동 DI 컨테이너
public class ServiceRegistry {
    private EconomyService economyService;
    private JobService jobService;
    
    // Setter Injection (순환 의존성 방지)
    public void setEconomyService(EconomyService service) {
        this.economyService = service;
    }
    
    public Optional<EconomyService> getEconomyService() {
        return Optional.ofNullable(economyService);
    }
}
```

##### 5. Strategy 패턴
- **파일**: `enhance/processing/EffectProcessor.java` + 구현체들
- **용도**: 알고리즘을 인터페이스로 분리하여 교체 가능하게
- **학습 포인트**:
  - 우선순위 기반 파이프라인 처리
  - if-else 대신 전략 객체로 분기

```java
// EffectProcessor.java
public interface EffectProcessor {
    int getPriority();  // 실행 순서
    void process(BlockContext context);  // 실제 처리
}

// FortuneProcessor.java
public class FortuneProcessor implements EffectProcessor {
    @Override
    public int getPriority() { return 100; }  // 먼저 실행
    
    @Override
    public void process(BlockContext context) {
        // Fortune 효과 적용
    }
}
```

#### 실습 과제
1. 각 패턴의 UML 클래스 다이어그램 그리기
2. `AchievementRegistry`가 Registry 패턴을 어떻게 구현하는지 분석
3. `EnchantEffectListener`에서 Observer 패턴 흐름 추적

---

### 🟡 3단계: 네트워크 프로그래밍 (1~2주)

**목표**: 클라이언트-서버 통신 원리 이해

#### 학습 파일
| 위치 | 파일 | 역할 |
|------|------|------|
| 서버 | `mod/ModDataService.java` | 패킷 전송 |
| 서버 | `mod/ModRequestHandler.java` | 요청 수신 |
| 클라이언트 | `net/UiDataReceiver.java` | 패킷 수신 |
| 클라이언트 | `net/UiRequestSender.java` | 요청 전송 |
| 문서 | `docs/PROTOCOL.md` | 프로토콜 명세 |

#### 통신 흐름

```
서버 (Paper)                                클라이언트 (Fabric)
     │                                              │
     │  ← ClientPlayConnectionEvents.JOIN ─────────│  연결 감지
     │                                              │
     │  ───── tycoon:ui_data (PLAYER_PROFILE) ───→ │  초기 데이터
     │                                              │
     │  ← tycoon:ui_request (REQUEST_CODEX) ────── │  도감 요청
     │                                              │
     │  ───── tycoon:ui_data (CODEX_DATA) ───────→ │  도감 응답
     │                                              │
```

#### 핵심 코드 분석

```java
// 서버: ModDataService.java
public void sendPlayerProfile(Player player) {
    JsonObject json = new JsonObject();
    json.addProperty("type", "PLAYER_PROFILE");
    json.addProperty("schema_version", 2);
    
    JsonObject data = new JsonObject();
    data.addProperty("name", player.getName());
    data.addProperty("bd", economyService.getBalance(player));
    // ... 더 많은 데이터
    
    json.add("data", data);
    
    // 바이트 배열로 변환 후 전송
    byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
    player.sendPluginMessage(plugin, "tycoon:ui_data", bytes);
}
```

```java
// 클라이언트: UiDataReceiver.java
public static void register() {
    ClientPlayNetworking.registerGlobalReceiver(
        CHANNEL_ID,
        (client, handler, buf, responseSender) -> {
            // 네트워크 스레드에서 실행됨!
            String jsonStr = buf.readString();
            
            // 메인 스레드로 전환 (중요!)
            client.execute(() -> {
                handlePacket(jsonStr);
            });
        }
    );
}
```

#### 학습 포인트
- **스레드 안전성**: 네트워크 스레드 vs 메인 스레드
- **직렬화**: Java 객체 → JSON → 바이트 배열
- **프로토콜 설계**: 타입 필드, 스키마 버전 관리

---

### 🔴 4단계: 고급 주제 (2~3주)

#### A. Mixin (바이트코드 조작)

**파일**: `tycoon-hud/src/main/java/kr/bapuri/tycoonhud/mixin/InGameHudMixin.java`

```java
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    
    @Shadow @Final private MinecraftClient client;
    
    // 바닐라 메서드 실행 전에 가로채기
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void tycoonhud$hideStatusBars(DrawContext context, CallbackInfo ci) {
        if (hasCustomVital()) {
            ci.cancel();  // 바닐라 체력바 숨김!
        }
    }
}
```

**학습 포인트**:
- `@Inject`: 특정 메서드에 코드 삽입
- `@At("HEAD")`, `@At("RETURN")`: 삽입 시점
- `@Shadow`: private 필드 접근
- `cancellable = true`: 원본 메서드 취소 가능

#### B. 멀티스레드 안전성

**파일**: `player/PlayerDataManager.java`

```java
public class PlayerDataManager {
    // 스레드 안전한 컬렉션
    private final ConcurrentHashMap<UUID, PlayerTycoonData> cache = 
        new ConcurrentHashMap<>();
    
    // 원자적 연산
    private final AtomicLong transactionIdCounter = new AtomicLong(0);
    
    public long nextTransactionId() {
        return transactionIdCounter.incrementAndGet();
    }
}
```

#### C. 트랜잭션 처리

**파일**: `shop/AbstractShop.java`

```java
public TransactionResult executeBuy(Player player, ShopItem item, int amount) {
    double price = calculateTotalPrice(item, amount);
    
    // 1. 잔액 확인
    if (!economyService.has(player, price)) {
        return TransactionResult.INSUFFICIENT_FUNDS;
    }
    
    // 2. 차감 (실패 가능)
    boolean withdrawn = economyService.withdraw(player, price);
    if (!withdrawn) {
        return TransactionResult.ECONOMY_ERROR;
    }
    
    // 3. 아이템 지급 (실패 시 롤백!)
    boolean given = giveItems(player, item, amount);
    if (!given) {
        // 롤백: 돈 돌려주기
        economyService.deposit(player, price);
        return TransactionResult.INVENTORY_FULL;
    }
    
    return TransactionResult.SUCCESS;
}
```

---

## 코드에서 배우는 CS 개념

### 1. 자료구조 활용

#### HashMap vs ConcurrentHashMap
```java
// PlayerDataManager.java - 왜 ConcurrentHashMap을 쓸까?
private final ConcurrentHashMap<UUID, PlayerTycoonData> cache = 
    new ConcurrentHashMap<>();

// 일반 HashMap을 쓰면?
// → 네트워크 스레드와 메인 스레드가 동시 접근 시 ConcurrentModificationException!

// ConcurrentHashMap의 특징:
// 1. 세그먼트 단위 잠금 (전체 잠금 X)
// 2. 읽기는 잠금 없이 가능
// 3. putIfAbsent() 같은 원자적 연산 제공
```

**코드 예시 위치**: `player/PlayerDataManager.java`

#### Optional 패턴
```java
// ServiceRegistry.java - null 안전성
public Optional<CitizensIntegration> getCitizensIntegration() {
    return Optional.ofNullable(citizensIntegration)
            .filter(CitizensIntegration::isAvailable);
}

// 사용측
services.getCitizensIntegration()
    .ifPresent(citizens -> citizens.registerHandler(...));

// 왜 Optional을 쓸까?
// 1. NullPointerException 방지
// 2. "없을 수 있음"을 타입으로 명시
// 3. 함수형 체이닝 가능 (.map, .filter, .orElse)
```

**코드 예시 위치**: `bootstrap/ServiceRegistry.java`

---

### 2. 동시성 (Concurrency)

#### AtomicLong - 원자적 연산
```java
// PlayerDataManager.java
private final AtomicLong transactionIdCounter = new AtomicLong(0);

public long nextTransactionId() {
    return transactionIdCounter.incrementAndGet();
    // incrementAndGet()은 원자적 연산
    // 여러 스레드가 동시에 호출해도 각각 고유한 값 반환
}

// 만약 그냥 long을 쓰면?
// private long counter = 0;
// public long next() { return ++counter; }  // RACE CONDITION!
// 두 스레드가 같은 값을 읽고, 같은 값을 쓸 수 있음
```

#### 스레드 전환 (Thread Switching)
```java
// 클라이언트: UiDataReceiver.java
ClientPlayNetworking.registerGlobalReceiver(CHANNEL_ID,
    (client, handler, buf, responseSender) -> {
        // ⚠️ 여기는 네트워크 스레드!
        String jsonStr = buf.readString();
        
        // 렌더링/UI 조작은 메인 스레드에서만 가능
        client.execute(() -> {
            // ✅ 여기는 메인 스레드!
            handlePacket(jsonStr);
        });
    }
);

// 서버도 마찬가지
// Bukkit.getScheduler().runTask(plugin, () -> { ... });
```

**학습 포인트**: 
- 왜 스레드 분리가 필요한가? (UI 프레임 드롭 방지)
- `execute()` vs `submit()`의 차이

---

### 3. 트랜잭션과 롤백

```java
// shop/AbstractShop.java - 상점 구매 트랜잭션
public TransactionResult executeBuy(Player player, ShopItem item, int amount) {
    double price = calculateTotalPrice(item, amount);
    
    // 1. Validation (검증)
    if (!economyService.has(player, price)) {
        return TransactionResult.INSUFFICIENT_FUNDS;
    }
    
    // 2. Debit (차감) - 실패 가능
    boolean withdrawn = economyService.withdraw(player, price);
    if (!withdrawn) {
        return TransactionResult.ECONOMY_ERROR;
    }
    
    // 3. Credit (지급) - 실패 시 롤백!
    boolean given = giveItems(player, item, amount);
    if (!given) {
        // ⚠️ ROLLBACK: 돈 돌려주기
        economyService.deposit(player, price);
        return TransactionResult.INVENTORY_FULL;
    }
    
    // 4. Commit (확정)
    logTransaction(player, item, amount, price);
    return TransactionResult.SUCCESS;
}
```

**학습 포인트**:
- ACID 원칙 (Atomicity, Consistency, Isolation, Durability)
- 보상 트랜잭션 (Compensating Transaction)
- 왜 단순 try-catch가 아닌 명시적 롤백인가?

---

### 4. 이벤트 기반 아키텍처

```java
// 전통적인 방식 (폴링)
while (true) {
    if (player.brokeBlock()) {
        handleBlockBreak();
    }
    Thread.sleep(50);  // CPU 낭비!
}

// 이벤트 기반 방식 (Bukkit)
@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    // 블록이 파괴될 때만 호출됨
    // CPU 효율적, 실시간 반응
}
```

**코드에서 Observer 패턴 찾기**:
1. `MinerListener` - BlockBreakEvent 구독
2. `FarmerListener` - BlockBreakEvent 구독  
3. `FisherListener` - PlayerFishEvent 구독
4. `TradeListener` - InventoryClickEvent 구독

**학습 포인트**:
- 이벤트 우선순위 (`EventPriority.LOWEST` ~ `MONITOR`)
- 이벤트 취소 (`event.setCancelled(true)`)
- 왜 `ignoreCancelled = true`를 쓸까?

---

### 5. 직렬화 (Serialization)

#### JSON 직렬화
```java
// 서버: ModDataService.java
public void sendPlayerProfile(Player player) {
    JsonObject json = new JsonObject();  // Gson 라이브러리
    json.addProperty("type", "PLAYER_PROFILE");
    json.addProperty("schema_version", 2);  // 버전 관리!
    
    JsonObject data = new JsonObject();
    data.addProperty("name", player.getName());
    data.addProperty("bd", economyService.getBalance(player));
    json.add("data", data);
    
    byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
    player.sendPluginMessage(plugin, "tycoon:ui_data", bytes);
}

// 클라이언트: UiDataReceiver.java
private static void handlePacket(String jsonStr) {
    JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
    String type = json.get("type").getAsString();
    int version = json.get("schema_version").getAsInt();
    
    // 버전 체크 - 호환성 관리
    if (version < MIN_SUPPORTED_VERSION) {
        LOGGER.warn("Unsupported schema version: " + version);
        return;
    }
    
    switch (type) {
        case "PLAYER_PROFILE":
            handlePlayerProfile(json.getAsJsonObject("data"));
            break;
        // ...
    }
}
```

**학습 포인트**:
- 왜 JSON을 쓸까? (사람이 읽기 쉬움, 언어 독립적)
- 스키마 버전 관리의 중요성
- 대안: Protocol Buffers, MessagePack (더 빠름, 더 작음)

---

### 6. 파이프라인 패턴 (Chain of Responsibility)

```java
// enhance/processing/BlockProcessingService.java
public class BlockProcessingService {
    private final List<EffectProcessor> processors = new ArrayList<>();
    
    public void registerProcessor(EffectProcessor processor) {
        processors.add(processor);
        // 우선순위로 정렬!
        processors.sort(Comparator.comparingInt(EffectProcessor::getPriority));
    }
    
    public void process(BlockContext context) {
        // 파이프라인 실행
        for (EffectProcessor processor : processors) {
            processor.process(context);
            
            // 중간에 멈출 수도 있음
            if (context.isCancelled()) break;
        }
    }
}

// 프로세서 우선순위
// 100: FortuneProcessor      - 행운 계산
// 150: EnchantDropBonus      - 인챈트 드롭 보너스
// 200: GradeBonusProcessor   - 직업 등급 보너스
// 400: LampEffectProcessor   - 램프 효과 (AUTO_SMELT 등)
// 500: JobExpProcessor       - 경험치 계산 (최종 드롭 기준)
// 900: DeliveryProcessor     - 아이템 지급 (텔레키네시스 or 드롭)
```

**학습 포인트**:
- 왜 if-else 체인 대신 파이프라인을 쓸까?
- 새 프로세서 추가 시 기존 코드 수정 불필요 (OCP)
- Unix 파이프라인과의 유사성 (`cat file | grep pattern | sort`)

---

### 7. Enum 활용 고급 기법

```java
// job/JobType.java - 단순 Enum
public enum JobType {
    MINER, FARMER, FISHER
}

// enhance/lamp/LampEffect.java - Enum with 메서드
public enum LampEffect {
    AUTO_SMELT("자동 제련", "auto_smelt") {
        @Override
        public void apply(BlockContext context) {
            // 광석 → 주괴로 변환
            ItemStack result = SmeltingRecipe.getResult(context.getDrops());
            context.setDrops(result);
        }
    },
    TELEKINESIS("텔레키네시스", "telekinesis") {
        @Override
        public void apply(BlockContext context) {
            // 드롭 아이템을 인벤토리로 직접 이동
            context.setDeliveryMode(DeliveryMode.INVENTORY);
        }
    };
    
    private final String displayName;
    private final String id;
    
    // 각 Enum 상수가 다른 동작을 가짐!
    public abstract void apply(BlockContext context);
}
```

**학습 포인트**:
- Enum은 클래스다 (필드, 메서드, 생성자 가능)
- Strategy 패턴의 Enum 구현
- switch 문 대신 다형성 활용

---

### 8. 설정 기반 개발 (Configuration-Driven)

```yaml
# jobs.yml - 코드 수정 없이 밸런싱 조정
miner:
  enabled: true
  tier: 1
  
  expTable:
    COAL_ORE: 5
    IRON_ORE: 10
    GOLD_ORE: 20
    DIAMOND_ORE: 50
  
  levelFormula:
    base: 100
    multiplier: 1.5
    # Level N 필요 경험치 = base * (multiplier ^ (N-1))
```

```java
// job/miner/MinerConfig.java
public class MinerConfig {
    private Map<Material, Integer> expTable = new HashMap<>();
    
    public void load() {
        ConfigurationSection section = config.getConfigurationSection("miner.expTable");
        for (String key : section.getKeys(false)) {
            Material mat = Material.valueOf(key);
            int exp = section.getInt(key);
            expTable.put(mat, exp);
        }
    }
    
    public int getExpFor(Material ore) {
        return expTable.getOrDefault(ore, 0);
    }
}
```

**학습 포인트**:
- 코드와 데이터의 분리
- Hot Reload 가능 (`/tycoon reload`)
- 비개발자도 밸런싱 수정 가능

---

## 핵심 디자인 패턴 (심화)

### 패턴 요약 표

| 패턴 | 위치 | 용도 | 학습 난이도 |
|------|------|------|------------|
| Factory | `LampItemFactory`, `EnchantBookFactory` | 복잡한 객체 생성 | ⭐⭐ |
| Registry | `CodexRegistry`, `JobRegistry`, `LampRegistry` | 데이터 관리 | ⭐⭐ |
| Template Method | `AbstractShop` | 공통 알고리즘 + 특화 | ⭐⭐⭐ |
| Observer | 모든 `*Listener.java` | 이벤트 처리 | ⭐⭐ |
| Strategy | `EffectProcessor` + 구현체 | 알고리즘 교체 | ⭐⭐⭐ |
| Singleton | `TycoonPlugin.getInstance()` | 전역 접근점 | ⭐ |
| DI Container | `ServiceRegistry` | 의존성 관리 | ⭐⭐⭐⭐ |

---

## 모듈별 상세 분석

### 서버 (tycoon-lite)

#### 1. bootstrap/ - 플러그인 초기화
| 파일 | 역할 |
|------|------|
| `ServiceRegistry.java` | 모든 서비스 인스턴스 관리 (DI 컨테이너) |
| `ListenerRegistry.java` | 이벤트 리스너 일괄 등록 |
| `ConfigManager.java` | YAML 설정 파일 로드 |
| `ConfigUpdater.java` | 설정 파일 버전 업그레이드 |

#### 2. economy/ - 경제 시스템
| 파일 | 역할 |
|------|------|
| `EconomyService.java` | 잔액 조회/입출금 (핵심 비즈니스 로직) |
| `vault/VaultIntegration.java` | Vault API 구현체 (외부 플러그인 연동) |
| `EcoCommand.java` | /eco 관리자 명령어 |
| `MoneyCommand.java` | /money 플레이어 명령어 |

#### 3. job/ - 직업 시스템
```
job/
├── JobType.java           # Enum: MINER, FARMER, FISHER
├── JobRegistry.java       # 직업 설정 저장소
├── JobService.java        # 직업 관련 비즈니스 로직
├── common/
│   ├── AbstractJobExpService.java    # 경험치 처리 추상 클래스
│   ├── AbstractJobGradeService.java  # 등급 승급 추상 클래스
│   └── SellService.java              # 아이템 판매 처리
├── miner/
│   ├── MinerConfig.java      # 채굴부 설정
│   ├── MinerExpService.java  # 채굴 경험치
│   └── MinerListener.java    # 블록 파괴 이벤트
├── farmer/
│   └── ...
└── fisher/
    └── ...
```

#### 4. enhance/ - 강화 시스템
```
enhance/
├── enchant/
│   ├── CustomEnchant.java       # Enum: 커스텀 인챈트 종류
│   ├── CustomEnchantRegistry.java
│   ├── EnchantApplyListener.java  # 인챈트북 적용
│   └── EnchantEffectListener.java # 인챈트 효과 발동
├── lamp/
│   ├── LampEffect.java          # Enum: 램프 효과 종류
│   ├── LampRegistry.java
│   └── LampEffectListener.java
├── upgrade/
│   ├── UpgradeService.java      # 강화 로직
│   └── UpgradeGui.java          # 강화 UI
└── processing/
    ├── BlockProcessingService.java  # 블록 처리 파이프라인
    └── processors/
        ├── FortuneProcessor.java    # Priority 100
        ├── GradeBonusProcessor.java # Priority 200
        ├── LampEffectProcessor.java # Priority 400
        └── DeliveryProcessor.java   # Priority 900
```

### 클라이언트 (tycoon-hud, tycoon-ui)

#### tycoon-hud 구조
```
tycoon-hud/
├── TycoonHudMod.java         # 모드 진입점
├── hud/
│   ├── ProfileHudOverlay.java  # 좌상단 프로필
│   ├── VitalHudOverlay.java    # 하단 체력/배고픔
│   ├── HunterHudOverlay.java   # 미니맵
│   └── ToastOverlay.java       # 토스트 알림
├── net/
│   ├── UiDataReceiver.java     # 서버 → 클라이언트 패킷
│   ├── PlayerDataManager.java  # 데이터 캐시 (싱글톤)
│   └── TycoonClientState.java  # 연결 상태 관리
├── model/
│   ├── PlayerProfileData.java  # 프로필 데이터 DTO
│   ├── VitalData.java          # 체력/배고픔 DTO
│   └── JobData.java            # 직업 데이터 DTO
└── mixin/
    ├── InGameHudMixin.java     # 바닐라 HUD 숨김
    └── MinecraftClientMixin.java
```

---

## 유지보수 가이드

### 새로운 기능 추가 시 체크리스트

#### 서버 (tycoon-lite)

- [ ] 설정이 필요하면 `src/main/resources/`에 YAML 파일 추가
- [ ] Service 클래스 생성 → `ServiceRegistry`에 등록
- [ ] Listener 클래스 생성 → `TycoonPlugin.initListeners()`에 등록
- [ ] Command 클래스 생성 → `plugin.yml`에 명령어 추가 + `registerCommand()` 호출
- [ ] 클라이언트 연동 필요 시 → `ModDataService`에 전송 메서드 추가
- [ ] 테스트: `/tycoon reload`로 설정 리로드 확인

#### 클라이언트 (tycoon-hud/tycoon-ui)

- [ ] HUD 추가: `HudRenderCallback.EVENT.register(new MyOverlay())`
- [ ] 새 패킷 타입: `UiDataReceiver.handlePacket()`에 case 추가
- [ ] 새 Model 클래스: `model/` 패키지에 DTO 생성
- [ ] Mixin 필요 시: `tycoon-hud.mixins.json`에 등록

### 설정 파일 가이드

| 파일 | 용도 | 리로드 방법 |
|------|------|------------|
| `config.yml` | 기본 설정 | `/tycoon reload` |
| `jobs.yml` | 직업 설정 (경험치, 등급) | 서버 재시작 |
| `shops.yml` | 상점 아이템/가격 | `/tycoon reload` |
| `enchants.yml` | 커스텀 인챈트 정의 | `/tycoon reload` |
| `lamps.yml` | 램프 효과 정의 | `/tycoon reload` |
| `codex.yml` | 도감 아이템 정의 | `/tycoon reload` |
| `achievements.yml` | 업적 정의 | 서버 재시작 |
| `tax.yml` | 세금 설정 | 서버 재시작 |

### 자주 발생하는 문제

#### 1. NullPointerException in ServiceRegistry
**원인**: 서비스 초기화 순서 문제
**해결**: `TycoonPlugin.initServices()` 순서 확인, Setter Injection 사용

#### 2. 클라이언트에서 데이터가 안 보임
**체크리스트**:
1. 서버에서 패킷 전송 로그 확인
2. `PROTOCOL.md`의 스키마 버전 일치 확인
3. 클라이언트 `UiDataReceiver`에서 해당 타입 처리 여부

#### 3. 이벤트가 발동 안 함
**체크리스트**:
1. Listener가 `registerEvents()`로 등록되었는지
2. `@EventHandler` 어노테이션 있는지
3. `ignoreCancelled = true` 설정 시 이전 리스너가 취소했는지

### 알려진 기술 부채 / 리팩토링 포인트

#### 1. TycoonPlugin.java - Fat Plugin Class 문제

**현재 상태**: `TycoonPlugin.java`가 약 **2,284줄**로 비대함

```
TycoonPlugin.java (2,284줄) 구성
├── import 문 (~80줄)
├── JavaDoc 주석 (~70줄)
├── 필드 선언 (~20줄)
├── onEnable/onDisable (~50줄)
└── init* 메서드들 (~2,000줄) ← 문제 영역
    ├── initServices()
    ├── initWorldSystem()
    ├── initJobSystem()
    ├── initMinerSystem()
    ├── initFarmerSystem()
    ├── initFisherSystem()
    ├── initCodexSystem()
    ├── initAchievementSystem()
    ├── initTitleSystem()
    ├── initTradeSystem()
    ├── initEnhanceSystem()
    ├── initTaxSystem()
    └── ... 등등
```

**God Object vs Fat Plugin Class 분석**:

| 구분 | God Object (안티패턴) | 현재 TycoonPlugin |
|------|----------------------|-------------------|
| 비즈니스 로직 | 한 클래스에 모두 있음 | Service로 분리됨 ✅ |
| 데이터 관리 | 필드 150개+ | ServiceRegistry가 관리 ✅ |
| 초기화 코드 | 한 클래스에 모두 있음 | 한 클래스에 모두 있음 ❌ |
| 결론 | - | **God Object는 아님**, 하지만 **Fat Plugin Class** |

**개선 방향 (향후 리팩토링 시)**:

```java
// 현재: TycoonPlugin.java에 모든 init이 있음
private void initJobSystem() { ... }      // 150줄
private void initMinerSystem() { ... }    // 80줄
private void initShopSystem() { ... }     // 100줄

// 개선: 각 시스템별 Initializer로 분리
public class JobSystemInitializer implements SystemInitializer {
    @Override
    public void initialize(ServiceRegistry services, Plugin plugin) {
        JobRegistry jobRegistry = new JobRegistry();
        JobService jobService = new JobService(services.get(PlayerDataManager.class));
        services.setJobService(jobService);
        // ...
    }
}

public class ShopSystemInitializer implements SystemInitializer {
    @Override
    public void initialize(ServiceRegistry services, Plugin plugin) {
        ShopService shopService = new ShopService(...);
        // ...
    }
}

// TycoonPlugin은 조립만 담당 (300줄 이하로 축소 가능)
@Override
public void onEnable() {
    List<SystemInitializer> initializers = List.of(
        new CoreSystemInitializer(),
        new JobSystemInitializer(),
        new ShopSystemInitializer(),
        new EnhanceSystemInitializer()
    );
    
    for (SystemInitializer init : initializers) {
        init.initialize(services, this);
    }
}
```

**추천 패키지 구조**:
```
bootstrap/
├── ServiceRegistry.java        (기존)
├── ListenerRegistry.java       (기존)
├── ConfigManager.java          (기존)
├── SystemInitializer.java      (NEW - 인터페이스)
├── CoreSystemInitializer.java  (NEW)
├── JobSystemInitializer.java   (NEW)
├── ShopSystemInitializer.java  (NEW)
├── EnhanceSystemInitializer.java (NEW)
└── ...
```

**발생 원인**: 새 기능 추가 시 `initXxxSystem()` 메서드를 TycoonPlugin에 바로 추가하는 것이 가장 빠르고 편했기 때문

**우선순위**: 낮음 (동작에는 문제 없음, 코드 가독성/유지보수성 개선 목적)

---

## 확장/개발 가이드

### 새 직업 추가하기 (예: HUNTER)

#### 1. Enum 추가
```java
// JobType.java
public enum JobType {
    MINER, FARMER, FISHER,
    HUNTER  // 추가
}
```

#### 2. 설정 추가 (jobs.yml)
```yaml
hunter:
  enabled: true
  tier: 1
  expPerKill: 10
  levelBonusPercent: 0.5
```

#### 3. Config 클래스 생성
```java
// job/hunter/HunterConfig.java
public class HunterConfig {
    private final Plugin plugin;
    private int expPerKill;
    
    public void load() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(
            new File(plugin.getDataFolder(), "jobs.yml"));
        expPerKill = config.getInt("hunter.expPerKill", 10);
    }
}
```

#### 4. Service 클래스 생성
```java
// job/hunter/HunterExpService.java
public class HunterExpService extends AbstractJobExpService {
    @Override
    protected JobType getJobType() {
        return JobType.HUNTER;
    }
}
```

#### 5. Listener 클래스 생성
```java
// job/hunter/HunterListener.java
public class HunterListener implements Listener {
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 몹 처치 시 경험치 지급
    }
}
```

#### 6. TycoonPlugin에서 초기화
```java
private void initHunterSystem(...) {
    HunterConfig config = new HunterConfig(this);
    HunterExpService expService = new HunterExpService(...);
    registry.registerExpService(JobType.HUNTER, expService);
    
    HunterListener listener = new HunterListener(expService);
    getServer().getPluginManager().registerEvents(listener, this);
}
```

### 새 인챈트 추가하기

#### 1. enchants.yml에 정의
```yaml
LIGHTNING_STRIKE:
  displayName: "§e벼락"
  maxLevel: 3
  applicableTo: [SWORD]
  description:
    - "§7공격 시 %chance%% 확률로 벼락"
  levelData:
    1: { chance: 5 }
    2: { chance: 10 }
    3: { chance: 15 }
```

#### 2. CustomEnchant Enum에 추가
```java
public enum CustomEnchant {
    // 기존...
    LIGHTNING_STRIKE("벼락", "lightning_strike");
}
```

#### 3. EnchantEffectListener에 효과 구현
```java
@EventHandler
public void onEntityDamage(EntityDamageByEntityEvent event) {
    // LIGHTNING_STRIKE 처리
    if (hasEnchant(weapon, CustomEnchant.LIGHTNING_STRIKE)) {
        int level = getLevel(weapon, CustomEnchant.LIGHTNING_STRIKE);
        double chance = registry.getLevelData("LIGHTNING_STRIKE", level, "chance");
        if (random.nextDouble() * 100 < chance) {
            target.getWorld().strikeLightning(target.getLocation());
        }
    }
}
```

---

## 효과적인 학습 전략

### 📌 코드 리딩 전략

#### 1. Top-Down 접근법 (권장)
```
TycoonPlugin.java (진입점)
    ↓
onEnable() 메서드 읽기
    ↓
initServices() → 어떤 서비스들이 있는지 파악
    ↓
관심 있는 서비스 하나 선택 (예: EconomyService)
    ↓
해당 서비스의 public 메서드들 파악
    ↓
메서드 하나씩 깊게 파고들기
```

#### 2. Use-Case 기반 접근법
```
"플레이어가 광물을 캤을 때 무슨 일이 일어나는가?"

1. BlockBreakEvent 발생
2. MinerListener.onBlockBreak() 호출
3. → MinerExpService.addExp() 호출
4.   → PlayerDataManager.get() 호출
5.   → PlayerTycoonData 수정
6.   → ModDataService.sendJobData() 호출
7. → EnchantEffectListener 체크
8.   → BlockProcessingService.process() 호출
9.   → 각 Processor 순차 실행
```

#### 3. 디버깅으로 배우기
```java
// 코드에 System.out.println() 추가하며 흐름 파악
@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    System.out.println("[DEBUG] MinerListener: " + event.getBlock().getType());
    // ...
}

// 또는 IDE의 디버거 사용 (중단점 설정)
```

---

### 📌 단계별 학습 계획

#### Week 1-2: 환경 구축 + 기초
- [ ] IntelliJ IDEA 설치 및 프로젝트 임포트
- [ ] Maven/Gradle 빌드 이해
- [ ] `TycoonPlugin.java` 전체 읽기 (주석만 읽어도 OK)
- [ ] `ServiceRegistry.java` 분석

#### Week 3-4: OOP + 디자인 패턴
- [ ] `AbstractShop` → `MinerShop` 상속 구조 분석
- [ ] Factory 패턴: `LampItemFactory` 분석
- [ ] Registry 패턴: `CodexRegistry` 분석
- [ ] UML 클래스 다이어그램 직접 그리기

#### Week 5-6: 이벤트 시스템
- [ ] `MinerListener` 코드 분석
- [ ] 자신만의 Listener 클래스 만들어보기
- [ ] 이벤트 우선순위 실험

#### Week 7-8: 네트워크 + 고급
- [ ] `PROTOCOL.md` 문서 읽기
- [ ] `ModDataService` ↔ `UiDataReceiver` 흐름 추적
- [ ] Wireshark로 실제 패킷 캡처해보기

#### Week 9-10: 종합 프로젝트
- [ ] 새로운 기능 직접 추가해보기 (예: 새 직업)
- [ ] 코드 리뷰 경험: 자신의 코드를 다시 보기
- [ ] 문서화: 배운 내용 정리

---

### 📌 효과적인 학습 습관

#### 1. Active Reading (능동적 읽기)
```
❌ 코드를 위에서 아래로 그냥 읽기
✅ 질문하며 읽기:
   - "이 클래스의 책임은 무엇인가?"
   - "이 메서드는 왜 public인가?"
   - "이 필드는 왜 final인가?"
   - "이 패턴은 왜 여기 적용됐을까?"
```

#### 2. 코드 따라치기 (Typing Practice)
```
이해가 안 되는 코드는 직접 타이핑해보기
복사-붙여넣기 X, 직접 타이핑 O
→ 세부사항이 눈에 들어옴
```

#### 3. Rubber Duck Debugging
```
코드를 누군가에게 설명하듯 말해보기
"이 메서드는... 음... 여기서 뭘 하는 거지?"
→ 설명이 막히는 부분이 이해 안 된 부분
```

#### 4. 학습 일지 작성
```markdown
## 2026-02-03

### 오늘 본 파일
- EconomyService.java

### 새로 배운 것
- Vault API가 EconomyProvider 인터페이스를 구현한다
- withdraw()는 실패 시 false를 반환한다

### 질문
- 왜 Vault를 직접 안 쓰고 EconomyService로 감쌌을까?
  → 추측: 테스트 용이성? 추상화?

### 내일 볼 것
- VaultIntegration.java
```

---

## 개발/관리 필수 도구

### 🔧 버전 관리: Git

#### 이 프로젝트에서 사용했으면 좋았을 Git 워크플로우

```
main (production)
  │
  ├── develop (개발 통합)
  │     │
  │     ├── feature/job-hunter      # 새 기능
  │     ├── feature/enchant-lightning
  │     ├── bugfix/shop-npe         # 버그 수정
  │     └── refactor/shop-cleanup   # 리팩토링
  │
  └── hotfix/critical-exploit       # 긴급 수정
```

#### 필수 Git 명령어
```bash
# 브랜치 생성 및 이동
git checkout -b feature/new-job

# 변경사항 확인
git status
git diff

# 커밋 (의미 있는 단위로)
git add src/main/java/kr/bapuri/tycoon/job/hunter/
git commit -m "feat(job): Add Hunter job base classes"

# 커밋 메시지 컨벤션
# feat: 새 기능
# fix: 버그 수정
# refactor: 리팩토링
# docs: 문서
# test: 테스트

# 푸시
git push origin feature/new-job

# Pull Request 생성 → 코드 리뷰 → 머지
```

#### Git으로 배우는 것
- 변경 이력 추적의 중요성
- 실험적 변경을 안전하게 시도 (브랜치)
- 협업의 기초

---

### 🔧 빌드 도구

#### Maven (tycoon-lite)
```xml
<!-- pom.xml 핵심 구조 -->
<project>
    <groupId>kr.bapuri</groupId>
    <artifactId>tycoon-lite</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    
    <properties>
        <java.version>21</java.version>
    </properties>
    
    <dependencies>
        <!-- 외부 라이브러리 의존성 -->
        <dependency>
            <groupId>io.papermc.paper</groupId>
            <artifactId>paper-api</artifactId>
            <version>1.20.1-R0.1-SNAPSHOT</version>
            <scope>provided</scope>  <!-- 서버가 제공 -->
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <!-- 빌드 플러그인 -->
        </plugins>
    </build>
</project>
```

```bash
# 빌드 명령
mvn clean package

# 의존성 다운로드만
mvn dependency:resolve

# 테스트 실행
mvn test
```

#### Gradle (tycoon-hud/ui)
```groovy
// build.gradle
plugins {
    id 'fabric-loom' version '1.4-SNAPSHOT'
}

dependencies {
    minecraft "com.mojang:minecraft:1.20.1"
    mappings "net.fabricmc:yarn:1.20.1+build.10:v2"
    modImplementation "net.fabricmc:fabric-loader:0.14.24"
}
```

```bash
# 빌드
./gradlew build

# 실행 (개발 클라이언트)
./gradlew runClient
```

---

### 🔧 IDE: IntelliJ IDEA

#### 필수 단축키
| 기능 | Windows | Mac |
|------|---------|-----|
| 클래스 찾기 | Ctrl+N | Cmd+O |
| 파일 찾기 | Ctrl+Shift+N | Cmd+Shift+O |
| 전체 검색 | Ctrl+Shift+F | Cmd+Shift+F |
| 선언으로 이동 | Ctrl+B | Cmd+B |
| 구현체 찾기 | Ctrl+Alt+B | Cmd+Alt+B |
| 사용처 찾기 | Alt+F7 | Alt+F7 |
| 리팩토링 | Ctrl+Shift+Alt+T | Ctrl+T |
| 디버그 | Shift+F9 | Ctrl+D |

#### 필수 플러그인
- **Minecraft Development** - Paper/Fabric 개발 지원
- **Rainbow Brackets** - 괄호 색상 구분
- **GitToolBox** - Git 정보 표시
- **SonarLint** - 코드 품질 분석

---

### 🔧 테스트 도구

#### JUnit (단위 테스트)
```java
// 이 프로젝트에 있었으면 좋았을 테스트
public class EconomyServiceTest {
    
    @Test
    void withdraw_shouldReturnFalse_whenInsufficientFunds() {
        // Given
        PlayerDataManager mockManager = mock(PlayerDataManager.class);
        EconomyService service = new EconomyService(mockManager);
        
        when(mockManager.get(any())).thenReturn(new PlayerTycoonData(100));
        
        // When
        boolean result = service.withdraw(player, 200);
        
        // Then
        assertFalse(result);
    }
}
```

#### 통합 테스트 서버
```bash
# 테스트 서버 폴더 구조
test-server/
├── plugins/
│   └── tycoon-lite-1.0.0.jar
├── server.properties
└── start.bat
```

---

### 🔧 문서화 도구

#### JavaDoc
```java
/**
 * 경제 서비스 - 플레이어 자산 관리
 * 
 * <p>모든 금전 거래는 이 서비스를 통해야 합니다.</p>
 * 
 * @author Bapuri
 * @since 1.0.0
 * @see PlayerDataManager
 */
public class EconomyService {
    
    /**
     * 플레이어 잔액 조회
     * 
     * @param player 대상 플레이어
     * @return 현재 잔액 (BD)
     * @throws IllegalArgumentException player가 null인 경우
     */
    public double getBalance(Player player) { ... }
}
```

```bash
# JavaDoc 생성
mvn javadoc:javadoc
```

#### Markdown 문서
```
docs/
├── PROTOCOL.md         # 통신 프로토콜
├── STUDY_GUIDE.md      # 이 문서!
├── TROUBLESHOOTING.md  # 문제 해결
└── planning/
    └── LITE_MASTER_TRACKER.md
```

---

### 🔧 모니터링/디버깅 도구

#### 서버 로그 분석
```bash
# 에러 찾기
grep -i "error\|exception" logs/latest.log

# 특정 클래스 로그만
grep "MinerListener" logs/latest.log
```

#### spark (Minecraft 프로파일러)
```
/spark profiler start
/spark profiler stop
# → 성능 병목 지점 찾기
```

#### Wireshark (네트워크 패킷 분석)
```
1. 필터: tcp.port == 25565
2. Plugin Message 패킷 찾기
3. JSON 페이로드 확인
```

---

### 🔧 추천 도구 요약표

| 분류 | 도구 | 용도 | 학습 가치 |
|------|------|------|----------|
| 버전관리 | **Git** | 코드 변경 추적 | ⭐⭐⭐⭐⭐ |
| IDE | **IntelliJ IDEA** | 코드 작성/디버깅 | ⭐⭐⭐⭐⭐ |
| 빌드 | Maven/Gradle | 의존성 관리 | ⭐⭐⭐⭐ |
| 테스트 | JUnit + Mockito | 단위 테스트 | ⭐⭐⭐⭐ |
| 문서 | Markdown + JavaDoc | 문서화 | ⭐⭐⭐ |
| 협업 | GitHub | PR, 이슈 관리 | ⭐⭐⭐⭐ |
| 모니터링 | spark | 성능 분석 | ⭐⭐⭐ |
| 네트워크 | Wireshark | 패킷 분석 | ⭐⭐⭐ |

---

### 🔧 이 프로젝트에 적용했으면 좋았을 것들

#### 1. CI/CD (지속적 통합/배포)
```yaml
# .github/workflows/build.yml
name: Build
on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: mvn clean package
      - uses: actions/upload-artifact@v3
        with:
          name: plugin
          path: target/*.jar
```

#### 2. 코드 품질 도구
```xml
<!-- pom.xml에 추가 -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <!-- 코딩 컨벤션 체크 -->
</plugin>

<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <!-- 버그 패턴 탐지 -->
</plugin>
```

#### 3. 이슈 트래커
```
GitHub Issues 또는 Jira

[BUG] 상점에서 인벤토리 가득 찼을 때 롤백 안 됨
[FEATURE] 새로운 직업: 사냥꾼
[REFACTOR] AbstractShop 코드 정리
```

---

## 추천 학습 자료

### Java 기초
- [Effective Java 3판](https://www.yes24.com/Product/Goods/65551284) - 조슈아 블로크
- [Java 언어로 배우는 디자인 패턴](https://www.yes24.com/Product/Goods/2867578) - 유키 히로시
- [모던 자바 인 액션](https://www.yes24.com/Product/Goods/77125987) - 람다, 스트림

### Minecraft 개발
- [Paper API JavaDoc](https://jd.papermc.io/paper/1.20/)
- [Fabric Wiki](https://fabricmc.net/wiki/)
- [Mixin Wiki](https://github.com/SpongePowered/Mixin/wiki)
- [SpigotMC 포럼](https://www.spigotmc.org/) - 커뮤니티

### 디자인 패턴
- [Refactoring.Guru - 디자인 패턴](https://refactoring.guru/ko/design-patterns)
- [Head First Design Patterns](https://www.yes24.com/Product/Goods/108192370)

### Git
- [Pro Git Book (무료)](https://git-scm.com/book/ko/v2)
- [Learn Git Branching (인터랙티브)](https://learngitbranching.js.org/)

### 네트워크 프로그래밍
- [Netty in Action](https://www.yes24.com/Product/Goods/25662949) (고급)
- [JSON 표준](https://www.json.org/json-ko.html)

### 소프트웨어 공학
- [클린 코드](https://www.yes24.com/Product/Goods/11681152) - 로버트 마틴
- [클린 아키텍처](https://www.yes24.com/Product/Goods/77283734) - 로버트 마틴

---

## 학습 일지 템플릿

```markdown
## 날짜: 2026-XX-XX

### 오늘 학습한 내용
- 파일: 
- 개념: 

### 이해한 것
- 

### 이해 안 되는 것
- 

### 질문
- 

### 다음에 볼 것
- 
```

---

## 포트폴리오로 만들기

### 🎯 왜 이 프로젝트가 포트폴리오로 가치가 있는가?

#### 일반적인 학부생 포트폴리오
```
❌ "ToDoList 앱 만들었습니다"
❌ "게시판 CRUD 구현했습니다"
❌ "부트캠프 클론 코딩했습니다"
```

#### 이 프로젝트의 차별점
```
✅ 실제 사용자가 있는 서비스 (라이브 서버 운영 경험)
✅ 복잡한 도메인 로직 (경제 시스템, 직업 시스템, 거래 시스템)
✅ 클라이언트-서버 아키텍처 (실시간 통신)
✅ 외부 API 연동 (Vault, Citizens, Lands 등)
✅ 대규모 코드베이스 관리 경험 (248+ Java 파일, 20,000+ LOC)
```

---

### 📁 GitHub 저장소 정리

#### 1. README.md 작성 (가장 중요!)

```markdown
# 🎮 BotTycoon - Minecraft Economy Server Plugin

> 경제 시뮬레이션 마인크래프트 서버를 위한 통합 플러그인 시스템

![Java](https://img.shields.io/badge/Java-21-orange)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)
![License](https://img.shields.io/badge/License-Private-red)

## 📌 프로젝트 소개

BotTycoon은 마인크래프트 서버에서 **경제 시뮬레이션 게임**을 구현한 플러그인입니다.
- 🏗️ **서버 플러그인** (Paper API) + **클라이언트 모드** (Fabric)
- 👥 **실제 운영**: XX명 동시접속 서버에서 운영
- 📅 **개발 기간**: 2025.XX ~ 2026.XX (약 X개월)

## 🛠️ 기술 스택

### Backend (Server Plugin)
- **Language**: Java 21
- **Platform**: Paper API 1.20.1
- **Build**: Maven
- **External APIs**: Vault, Citizens, WorldGuard, Lands, LuckPerms

### Frontend (Client Mod)  
- **Language**: Java 17
- **Platform**: Fabric 1.20.1
- **Build**: Gradle
- **Rendering**: Mixin, HudRenderCallback

### Communication
- **Protocol**: JSON over Plugin Messaging
- **Pattern**: Request-Response + Server Push

## 🏗️ 아키텍처

[아키텍처 다이어그램 이미지]

### 서버 계층 구조
```
Bootstrap Layer → Core Services → Feature Systems → External Integrations
```

### 주요 디자인 패턴
- **Factory Pattern**: 복잡한 아이템 생성 (LampItemFactory)
- **Registry Pattern**: 설정 기반 데이터 관리 (CodexRegistry)
- **Template Method**: 상점 공통 로직 (AbstractShop)
- **Observer Pattern**: 이벤트 기반 처리 (48+ Listeners)
- **Strategy Pattern**: 블록 처리 파이프라인 (EffectProcessor)
- **DI Container**: 수동 의존성 주입 (ServiceRegistry)

## ✨ 주요 기능

### 1. 경제 시스템
- 자체 화폐(BD) 시스템 + Vault API 연동
- 트랜잭션 롤백 지원
- 실시간 경제 데이터 클라이언트 동기화

### 2. 직업 시스템
- 3개 Tier 1 직업: 채굴부, 농부, 어부
- 경험치 기반 레벨업
- 등급 시스템 (견습 → 초보 → 숙련 → 전문 → 대가)

### 3. 강화 시스템
- 커스텀 인챈트 (20+ 종류)
- 램프 효과 (AUTO_SMELT, TELEKINESIS 등)
- +1~+10 강화 시스템

### 4. 클라이언트 HUD
- 실시간 프로필 표시
- 커스텀 체력/배고픔 게이지
- 미니맵, 토스트 알림

## 📊 프로젝트 규모

| 항목 | 수치 |
|------|------|
| 총 코드 라인 | 20,000+ LOC |
| Java 파일 | 248+ 개 |
| 설정 파일 | 12+ YAML |
| 이벤트 리스너 | 48+ 개 |
| 서비스 클래스 | 40+ 개 |

## 🎓 배운 점

### 기술적 성장
- 대규모 코드베이스 구조화
- 의존성 관리와 순환 참조 해결
- 네트워크 프로그래밍 (직렬화, 프로토콜 설계)
- 동시성 처리 (스레드 안전성)

### 소프트웨어 공학
- 설정 기반 개발의 중요성
- 확장 가능한 아키텍처 설계
- 실제 사용자 피드백 기반 개발

## 🔗 관련 링크
- [기술 문서](./docs/)
- [통신 프로토콜](./docs/PROTOCOL.md)
- [학습 가이드](./docs/STUDY_GUIDE.md)
```

#### 2. 폴더 구조 정리
```
repository/
├── README.md                 # 메인 소개 (가장 중요!)
├── docs/
│   ├── ARCHITECTURE.md       # 아키텍처 설명
│   ├── PROTOCOL.md           # 통신 프로토콜
│   ├── STUDY_GUIDE.md        # 학습 가이드
│   └── images/
│       ├── architecture.png  # 아키텍처 다이어그램
│       ├── demo.gif          # 동작 데모
│       └── screenshot.png    # 스크린샷
├── tycoon-lite/              # 서버 플러그인
│   ├── pom.xml
│   └── src/
└── tycoon-client/            # 클라이언트 모드
    ├── tycoon-hud/
    └── tycoon-ui/
```

#### 3. 커밋 히스토리 정리
```bash
# 지저분한 커밋 히스토리가 있다면
# (선택) 새 브랜치에서 깔끔하게 시작

# 커밋 메시지 컨벤션 적용
feat(job): Add Hunter job system
fix(shop): Fix rollback on inventory full
refactor(economy): Extract transaction logic
docs: Add architecture diagram
```

---

### 📄 이력서/자소서 작성법

#### 프로젝트 설명 (200자)
```
Minecraft 경제 시뮬레이션 서버 플러그인 개발 (Java, 20,000+ LOC)
- Paper API 서버 플러그인 + Fabric 클라이언트 모드 풀스택 개발
- 실시간 JSON 기반 통신 프로토콜 설계 및 구현
- Factory, Registry, Observer 등 GoF 디자인 패턴 적용
- XX명 동시접속 환경에서 운영 및 성능 최적화
```

#### 기술 면접 대비 질문 & 답변

**Q1. 이 프로젝트에서 가장 어려웠던 점은?**
```
순환 의존성 문제였습니다. 
EconomyService가 ShopService를 참조하고, ShopService가 EconomyService를 
참조하는 상황이 발생했습니다.

해결 방법으로 ServiceRegistry라는 수동 DI 컨테이너를 만들고,
생성자 주입 대신 Setter Injection을 사용해 순환 참조를 끊었습니다.
이 과정에서 Spring의 DI가 왜 필요한지 체감했습니다.
```

**Q2. 왜 이런 아키텍처를 선택했나요?**
```
초기에는 TycoonPlugin 하나에 모든 로직이 있는 "God Object" 구조였습니다.
150개 이상의 필드가 한 클래스에 있어서 유지보수가 어려웠습니다.

이를 해결하기 위해:
1. 기능별로 Service 클래스 분리 (Single Responsibility)
2. Registry 패턴으로 데이터 관리 통합
3. ServiceRegistry로 의존성 중앙 관리

결과적으로 새 기능 추가 시 기존 코드 수정 없이 
확장할 수 있는 구조가 되었습니다. (Open-Closed Principle)
```

**Q3. 네트워크 통신은 어떻게 구현했나요?**
```
Minecraft Plugin Messaging 채널을 사용해 JSON 기반 프로토콜을 설계했습니다.

서버 → 클라이언트: PLAYER_PROFILE, JOB_DATA, ECONOMY_UPDATE 등
클라이언트 → 서버: REQUEST_CODEX, REQUEST_ECONOMY_HISTORY 등

스키마 버전 관리를 통해 하위 호환성을 유지하고,
네트워크 스레드에서 메인 스레드로의 전환을 통해 
스레드 안전성을 확보했습니다.
```

**Q4. 동시성 이슈는 어떻게 처리했나요?**
```
두 가지 상황에서 동시성 이슈가 있었습니다:

1. 플레이어 데이터 캐시 (여러 이벤트가 동시 접근)
   → ConcurrentHashMap 사용

2. 트랜잭션 ID 생성 (고유성 보장 필요)
   → AtomicLong의 incrementAndGet() 사용

클라이언트에서는 네트워크 스레드에서 받은 데이터를
client.execute()로 메인 스레드로 전달해 UI 업데이트 시
race condition을 방지했습니다.
```

**Q5. 테스트는 어떻게 했나요?**
```
(정직하게) 이 프로젝트에서는 자동화된 단위 테스트를 작성하지 못했습니다.
대신 로컬 테스트 서버에서 수동 테스트를 진행했습니다.

이 경험을 통해 테스트 자동화의 필요성을 절감했고,
다음 프로젝트에서는 JUnit과 Mockito를 활용한 
테스트 코드 작성을 계획하고 있습니다.

(보완점으로 언급)
```

---

### 🎥 데모 준비

#### 1. 동작 영상/GIF 녹화
```
필수 장면:
1. 서버 접속 → 클라이언트 HUD 표시
2. 광물 채굴 → 경험치 증가 → 실시간 반영
3. 상점 이용 → 구매/판매 → 잔액 변동
4. 강화 시스템 → 인챈트 적용

도구:
- OBS Studio (영상)
- ScreenToGif (GIF)
- 영상 편집: DaVinci Resolve (무료)
```

#### 2. 스크린샷 준비
```
1. 코드 구조 (IntelliJ 프로젝트 뷰)
2. 실제 게임 화면 (HUD 표시)
3. 아키텍처 다이어그램
4. 클래스 다이어그램 (일부)
```

#### 3. 라이브 데모 준비
```
면접에서 라이브 데모 요청 시:

1. 로컬 테스트 서버 준비
   - 미리 세팅된 플레이어 데이터
   - 시연용 아이템/상황 구성

2. 코드 설명 준비
   - 핵심 클래스 3-4개 선정
   - 디자인 패턴 적용 부분 북마크

3. 네트워크 끊김 대비
   - 오프라인에서도 보여줄 자료 준비
   - 녹화된 영상 백업
```

---

### 📊 프로젝트 수치화

#### 정량적 지표 (면접에서 언급)
```
• 총 코드 라인: 20,000+ LOC
• Java 파일 수: 248개
• 설정 파일: 12개 YAML
• 이벤트 리스너: 48개
• 서비스 클래스: 40+개
• 디자인 패턴: 6개 이상 적용
• 개발 기간: X개월
• (있다면) 동시접속자: XX명
```

#### 코드 통계 뽑기
```bash
# 전체 Java 파일 수
find . -name "*.java" | wc -l

# 전체 코드 라인 수 (주석/공백 제외)
find . -name "*.java" -exec cat {} \; | grep -v "^$" | grep -v "^\s*//" | wc -l

# 클래스당 평균 라인 수
# → 100-200줄이면 적절한 응집도

# 또는 IntelliJ: Analyze → Calculate Metrics
```

---

### 🎓 기술 블로그 작성

#### 추천 주제 (포트폴리오 보강)

**1. 아키텍처 관련**
```
제목: "Minecraft 플러그인에 DI 컨테이너를 직접 구현한 이유"

내용:
- 기존 God Object 문제점
- Spring 없이 수동 DI 구현
- 순환 의존성 해결 과정
- 코드 예시와 before/after 비교
```

**2. 디자인 패턴 관련**
```
제목: "게임 서버에서 Factory 패턴 활용하기"

내용:
- 왜 new 대신 Factory를 쓰는가?
- LampItemFactory 구현 과정
- 복잡한 객체 생성 로직 캡슐화
- 테스트 용이성 향상
```

**3. 네트워크 관련**
```
제목: "Minecraft 클라이언트-서버 실시간 통신 구현기"

내용:
- Plugin Messaging 채널 소개
- JSON 프로토콜 설계
- 스키마 버전 관리
- 스레드 안전성 처리
```

**4. 트러블슈팅**
```
제목: "멀티스레드 환경에서 발생한 Race Condition 디버깅"

내용:
- 문제 상황 설명
- 원인 분석 과정
- ConcurrentHashMap으로 해결
- 배운 점
```

#### 블로그 플랫폼 추천
- **velog** - 개발자 특화, 마크다운 지원
- **tistory** - 커스터마이징 자유도
- **GitHub Pages** - 직접 관리, 포트폴리오와 연동

---

### ✅ 포트폴리오 체크리스트

#### GitHub 정리
- [ ] README.md 작성 (프로젝트 소개, 기술 스택, 아키텍처)
- [ ] 폴더 구조 정리
- [ ] 불필요한 파일 제거 (.gitignore)
- [ ] 라이선스 명시 (또는 Private 명시)
- [ ] 스크린샷/GIF 추가

#### 문서화
- [ ] 아키텍처 다이어그램 이미지화
- [ ] 주요 기능 설명 문서
- [ ] API 문서 (JavaDoc 또는 별도 문서)

#### 시연 준비
- [ ] 동작 영상 녹화 (2-3분)
- [ ] 스크린샷 5-10장
- [ ] 라이브 데모 환경 구축

#### 면접 대비
- [ ] 기술 면접 Q&A 준비
- [ ] 코드 리뷰 포인트 선정
- [ ] 프로젝트 소개 1분 스피치

#### 보강 활동
- [ ] 기술 블로그 1-2개 포스팅
- [ ] (선택) 단위 테스트 추가
- [ ] (선택) CI/CD 구축

---

### 💡 포트폴리오 차별화 팁

#### DO ✅
```
• "직접 설계하고 구현했다"를 강조
• 문제 → 해결 과정 → 배운 점 스토리텔링
• 수치로 표현 (코드 라인, 파일 수, 사용자 수)
• 아키텍처 다이어그램으로 시각화
• 실제 동작하는 데모 준비
```

#### DON'T ❌
```
• "마인크래프트 게임 만들었어요" (게임 자체가 아닌 시스템 개발 강조)
• 기술 나열만 하기 (왜 그 기술을 선택했는지 설명)
• 너무 긴 설명 (README는 스크롤 3번 이내)
• 실행 안 되는 코드 올리기 (빌드 확인 필수)
```

---

## 타이쿤 1.21.X 개발 vs 토이 프로젝트

> 기존에 계획한 Java/C++ 토이 프로젝트와 타이쿤 1.21.X 신규 개발을 비교 분석

### 배경

기존에 Java/C++ 실력 향상을 위한 토이 프로젝트 계획이 있었음:
- **Java 실용**: GitHub 대시보드, 다운로드 정리 도우미, 마크 로그 분석기
- **Java 비실용**: 주문 처리 시스템, 플러그인 구조, 로그 분석, 파일 시스템, 채팅 서버
- **C++ 실용**: xrun (커맨드 러너)
- **C++ 비실용**: 미니 쉘, 멀티스레드 스케줄러, KV 스토어, 메모리 풀

**질문**: LITE 코드를 학습하고 1.21.X 버전 타이쿤을 새로 만드는 것이 이 토이 프로젝트들을 대체할 수 있는가?

---

### 1. 학습 내용 비교

#### 타이쿤 1.21.X 개발이 커버하는 영역

| 토이 프로젝트 계획 | 타이쿤이 커버? | 설명 |
|------------------|--------------|------|
| **주문 처리 시스템 (상태 머신)** | ✅✅ | enhance 시스템, job grade 시스템에 상태 전이 패턴 있음 |
| **플러그인 구조 프로그램** | ✅✅✅ | 이건 그 자체가 플러그인 개발 |
| **로그 수집 & 분석** | ⚠️ 부분적 | 서버 운영은 하지만 "분석 도구"는 별도 |
| **미니 파일 시스템** | ❌ | 관련 없음 |
| **멀티룸 채팅 서버** | ⚠️ 약간 | Plugin Messaging은 추상화됨 (소켓 직접 안 다룸) |
| **GitHub 대시보드** | ❌ | REST API, JavaFX 없음 |
| **다운로드 정리 도우미** | ❌ | 로컬 파일/JavaFX 없음 |

#### 타이쿤에서 배우는 것 (이 문서 기준)

```
✅ 타이쿤이 강한 영역:
├── 디자인 패턴 (Factory, Registry, Template, Observer, Strategy, DI)
├── 도메인 모델링 (직업, 경제, 강화, 상점 등 복잡한 도메인)
├── 클라이언트-서버 통신 (JSON 프로토콜 설계)
├── 이벤트 기반 아키텍처
├── 동시성 (ConcurrentHashMap, AtomicLong, Thread Switching)
├── 트랜잭션/롤백 패턴
├── 설정 기반 개발 (YAML, 핫 리로드)
├── 외부 API 연동 (Vault, Citizens, WorldGuard)
└── 대규모 코드베이스 관리 (260+ 파일)

❌ 타이쿤이 약한 영역:
├── REST API 호출 (HTTP Client)
├── JavaFX / GUI 개발
├── 소켓 프로그래밍 직접 경험
├── 데이터 분석 / ML 파이프라인
└── 순수 Java (프레임워크 없이) 설계
```

---

### 2. 경험적 관점 비교

| 관점 | 토이 프로젝트 | 타이쿤 1.21.X 개발 |
|------|-------------|-------------------|
| **실제 사용자** | 나만 씀 | ✅ 실제 플레이어 있음 |
| **코드베이스 규모** | 수백~천 줄 | ✅ 수만 줄 (검증됨) |
| **외부 연동** | 1~2개 API | ✅ 5개+ 플러그인 연동 |
| **운영 경험** | 없음 | ✅ 실서버 배포/운영 |
| **레거시 리팩토링** | 없음 | ✅ LITE가 리팩토링 결과물 |
| **협업 구조** | 혼자 | 혼자 (동일) |
| **포트폴리오 스토리** | "만들어봤음" | ✅ "실제 운영 + 개선" |

#### 포트폴리오 관점에서

```
타이쿤의 강점:
├── "실제 사용자 있는 서비스 개발/운영 경험" → 면접에서 높은 평가
├── "레거시 코드를 리팩토링한 경험" → 실무 감각 증명
├── "복잡한 도메인 모델링" → 설계력 증명
├── "외부 시스템 연동" → 실무적
└── 규모가 큼 → "진짜 만들어봤구나" 느낌

토이 프로젝트 계획의 강점:
├── "다양한 기술 스택 경험" (REST API, JavaFX 등)
├── "진로별 맞춤 프로젝트" (Applied AI → 로그 분석)
└── "짧고 명확한 설명 가능" (1~2주 프로젝트)
```

---

### 3. 결론: 대체 가능한가?

#### 짧은 답변

**Java 비실용 프로젝트의 60~70%는 대체 가능**하지만, **실용 프로젝트는 대체 불가**.

#### 상세 분석

| 계획된 프로젝트 | 대체 가능? | 이유 |
|---------------|----------|------|
| 주문 처리 시스템 | ✅ 대체 | 타이쿤에 더 복잡한 상태 관리 있음 |
| 플러그인 구조 | ✅✅ 완전 대체 | 그 자체가 플러그인 |
| 로그 분석 | ⚠️ 부분 대체 | 분석 "도구"는 별도로 만들어야 |
| 파일 시스템 | ❌ 대체 불가 | 자료구조 직접 구현 경험 없음 |
| 채팅 서버 | ❌ 대체 불가 | 소켓 직접 다루지 않음 |
| **GitHub 대시보드** | ❌ 대체 불가 | REST API 경험 없음 |
| **다운로드 정리** | ❌ 대체 불가 | JavaFX, 로컬 파일 처리 없음 |

---

### 4. 이상적인 조합 추천

#### Option A: 타이쿤 중심 (Java 깊이 우선) ⭐ 추천

```
┌─────────────────────────────────────────────────────────┐
│  메인: 타이쿤 1.21.X 개발 (Java 핵심 역량 80% 커버)      │
├─────────────────────────────────────────────────────────┤
│  보완 1: GitHub 대시보드 (REST API 경험 보완)            │
│    → 타이쿤에 없는 "HTTP API 호출" 역량                  │
│    → 포트폴리오 다양성                                   │
├─────────────────────────────────────────────────────────┤
│  보완 2: 마크 서버 로그 분석기 (Applied AI 진로 연결)    │
│    → 타이쿤 서버 로그를 분석하는 도구                    │
│    → 데이터 분석 → ML 연습 → 진로 연결                  │
│    → 시너지: "내 서버 데이터로 분석"                     │
└─────────────────────────────────────────────────────────┘

C++: 계획대로 별도 진행 (xrun + 미니 쉘)
```

#### Option B: 밸런스 (다양성 우선)

```
┌─────────────────────────────────────────────────────────┐
│  메인: 타이쿤 1.21.X 개발 (규모 축소 - 핵심 시스템만)    │
│    → 직업 시스템 + 경제 시스템 + 강화 시스템             │
│    → 나머지는 기존 LITE 참고용으로만                     │
├─────────────────────────────────────────────────────────┤
│  토이 1: GitHub 대시보드 (Round 1 그대로)               │
│  토이 2: 로그 분석 (비실용, Round 1 그대로)             │
└─────────────────────────────────────────────────────────┘

C++: 계획대로 별도 진행
```

---

### 5. 최종 추천: Option A (타이쿤 중심)

#### 추천 이유

1. **포트폴리오 임팩트**: "실제 사용자 있는 서비스 개발/운영/리팩토링" 경험은 토이 5개보다 강력함
2. **학습 효율**: 이미 LITE 코드베이스를 이해했으니, 1.21.X로 새로 만들면 "왜 이렇게 설계했는지" 깊이 이해 가능
3. **시너지**: 마크 서버 로그 분석기를 만들면 타이쿤 서버 데이터로 바로 적용 가능
4. **현실적**: 토이 프로젝트 여러 개 병렬은 분산되기 쉬움

#### 구체적 계획

| 순서 | 프로젝트 | 목적 |
|------|---------|------|
| 1 | **타이쿤 1.21.X** | Java 핵심 역량 + 실서비스 경험 |
| 2 | **GitHub 대시보드** | REST API 보완 + 포트폴리오 다양성 |
| 3 | **마크 로그 분석기** | Applied AI 진로 연결 |
| 병렬 | **xrun (C++)** | C++ 실용 경험 |
| 병렬 | **미니 쉘 (C++)** | OS/시스템 감각 |

---

### 6. 주의사항

```
⚠️ 타이쿤만 하면 안 되는 이유:
├── REST API 경험 없음 → 백엔드 취업 시 약점
├── JavaFX 경험 없음 → GUI 포기하거나 별도 학습
└── "마크 플러그인만 했네" 편향 위험 → 다양성 필요

⚠️ 토이 프로젝트만 하면 안 되는 이유:
├── "실제 사용자" 경험 없음 → 면접에서 약함
├── 운영/배포 경험 없음
└── 규모가 작음 → "진짜 만들어봤나?" 의문
```

---

### 7. 핵심 요약

| 관점 | 결론 |
|------|------|
| **Java 학습** | 타이쿤 1.21.X가 비실용 토이의 60~70% 대체 가능 |
| **포트폴리오** | 타이쿤이 토이 5개보다 임팩트 큼 |
| **진로 연결** | 로그 분석기는 별도로 해야 Applied AI 연결 가능 |
| **현실적 선택** | 타이쿤 + GitHub 대시보드 + 로그 분석기 조합 |
| **C++** | 타이쿤과 무관하므로 계획대로 별도 진행 |

> **한 줄 결론**: 타이쿤 1.21.X 개발은 Java 토이 프로젝트의 상당 부분을 대체할 수 있고, 오히려 포트폴리오로서 더 강력하다. 다만 REST API나 데이터 분석 경험을 위해 GitHub 대시보드 + 로그 분석기 정도는 추가로 진행하는 것이 이상적이다.

---

## 2026년 연간 학습 계획

> "올해는 나의 자기 계발의 해" - 백엔드/프로그래밍 실무 수준 + AI 입문

### 목표 설정

```
2026년 목표:
├── 상반기: C/C++ + Java로 백엔드/프로그래밍 기초를 실무 수준으로
├── 하반기: Python/TensorFlow로 AI 딥하게
├── 연중: 백준 알고리즘 꾸준히
└── 연말: 둘 다 꽤 높은 수준 도달
```

### 학교 커리큘럼과의 시너지

#### 학교가 가르쳐주는 것 vs 직접 해야 하는 것

| 구분 | 학교가 커버 | 직접 해야 함 |
|------|-----------|-------------|
| **AI** | 기초~중급 이론/실습 | 실전 프로젝트 적용 |
| **소프트웨어 공학** | 고급 이론 | 대규모 코드베이스 경험 |
| **백엔드 실무** | 부족 | 직접 프로젝트 |
| **REST API** | 부족 | GitHub 대시보드 |
| **시스템 레벨** | 부족 | xrun, 미니쉘 |

#### 전략: 학교와 중복하지 않기

```
핵심 원칙:
├── 학교가 AI 가르쳐줄 때 혼자 AI 공부 = 비효율
├── 학교가 안 가르쳐주는 "실무 경험"에 집중
└── 하반기에 학교 AI + 내 프로젝트 연결

시너지 효과:
├── 학교 AI 수업 → "이거 내 서버에 적용하면?"
├── 소프트웨어 공학 → "타이쿤 코드에 적용하면?"
└── "학교에서 배운 AI를 내 프로젝트에 적용했습니다" = 차별화
```

### 구체적 타임라인

| 시기 | 학교 | 학교 밖 | 목표 |
|------|------|--------|------|
| **2월** | 방학 | **집중 스프린트**: 기초+GitMini+xrun+타이쿬 설계 | 기반 구축 |
| **3~4월** | 학기 시작, AI 기초 | 프로젝트 유지+타이쿬 개발 | Java 설계력 |
| **5~6월** | AI 중급, 소공 | 타이쿬 개발, GitMini 완성 | REST API 경험 |
| **7~8월** | 방학 | **집중 개발**: 타이쿬 완성, 미니쉘, Python 시작 | C++ 시스템 레벨 |
| **9~10월** | AI 심화 | 마크 로그 분석기 (Python) | 데이터 분석 |
| **11~12월** | 기말, 프로젝트 | AI 적용 실험, 포트폴리오 정리 | 통합/정리 |

---

### 2월 방학 집중 계획 (스프린트)

> **기간**: 2026년 2월 3일 ~ 2월 28일 (약 4주)  
> **목표**: 학기 전에 기반 구축, 습관 형성, 프로젝트 시작

#### 전략

```
방학 = 스프린트 (빡세게 당김)
학기 = 마라톤 (습관으로 유지)

2월에 해야 할 것:
├── Java/C++ 기초 개념 복습
├── GitMini MVP 진행 (70~100%)
├── xrun 기본 동작 (50~70%)
├── 타이쿬 1.21.X 설계 + 조금씩 시작 (매일 30분)
├── 알고리즘 습관 시작 (매일 1문제)
└── 수학 선행/복습
```

#### 주차별 계획

##### Week 1 (2/3~2/9): 기초 + 환경 설정

| 영역 | 내용 |
|------|------|
| **Java** | OOP 복습, JavaFX 환경 설정, FXML 튜토리얼, GitMini 프로젝트 생성 |
| **C++** | 포인터/메모리 복습 (learncpp), 스마트 포인터, CMake 기본, xrun 프로젝트 생성 |
| **타이쿬** | 1.21.X API 변경점 조사, 설계 문서 작성 (매일 30분) |
| **알고리즘** | 매일 1문제 시작 |
| **수학** | 학교 선행/복습 |

##### Week 2 (2/10~2/16): GitMini 집중

| 영역 | 내용 |
|------|------|
| **GitMini (메인)** | 메인 화면 UI, 레포 목록 표시, ProcessBuilder git 연동, git status 파싱 |
| **C++ (서브)** | 이론 30분/일, xrun 설계 다듬기 |
| **타이쿬** | 부트스트랩 구조 설계, 핵심 클래스 스케치 (매일 30분) |
| **알고리즘** | 매일 1문제 |
| **수학** | 진행 |

##### Week 3 (2/17~2/23): GitMini + xrun 본격

| 영역 | 내용 |
|------|------|
| **GitMini** | Stage, Commit, Push 구현, 명령어 히스토리, 레포 상세 화면 |
| **xrun** | 기본 구조, 프로세스 실행 + 출력 캡처, add/run 명령어 |
| **타이쿬** | ServiceRegistry 구현 시작, 기본 구조 코딩 (매일 30분) |
| **알고리즘** | 매일 1문제 |
| **수학** | 진행 |

##### Week 4 (2/24~2/28): 마무리 + 학기 준비

| 영역 | 내용 |
|------|------|
| **GitMini** | Pull, Fetch, 브랜치 기본, 버그 수정 → **MVP 목표** |
| **xrun** | list, history, 인코딩 처리 시작, 로그 저장 → **기본 동작 목표** |
| **타이쿬** | 진행된 만큼 정리, 학기 중 TODO 정리 |
| **정리** | 코드 정리, 문서화, 학기 중 계획 수립 |

#### 하루 루틴 (방학)

```
오전 (3~4시간):
├── 알고리즘 1문제 (1시간)
├── 수학 (1~2시간)
└── 이론 학습 (1시간)

오후 (4~5시간):
├── 메인 프로젝트 (GitMini 또는 xrun) (3~4시간)
└── 타이쿬 설계/코딩 (30분~1시간)

저녁:
├── 밥, 운동, 휴식
└── 가볍게 복습 또는 자유 시간
```

#### 2월 말 예상 결과

```
최선의 경우:
├── GitMini: MVP 완성 (기본 기능 동작)
├── xrun: 기본 동작 (add, run, list)
├── 타이쿬: 기본 구조 + ServiceRegistry 완성
├── 알고리즘: 25~30문제
├── 수학: 선행/복습 완료
└── 기초 개념: Java/C++ 감 확실히 잡음

현실적인 경우:
├── GitMini: 70~80%
├── xrun: 50~60%
├── 타이쿬: 설계 완료 + 기본 골격
├── 알고리즘: 20문제+
├── 수학: 진행 중
└── 기초 개념: 대략 잡음

핵심:
├── 완성 못 해도 OK → 학기 중에 이어서
├── 습관이 잡히는 게 더 중요
├── 번아웃 없이 학기 시작 = 승리
└── 타이쿬은 매일 30분이라도 → 학기 중 이어가기 쉬움
```

#### 학기 중 유지 모드

```
방학에 만들어둔 것:
├── GitMini MVP (또는 70~80%)
├── xrun 기본 동작 (또는 50~60%)
├── 타이쿬 기본 구조
├── 알고리즘 습관
└── 수학 기초

학기 중 (주당 ~10~15시간):
├── 알고리즘: 매일 1문제 (주 5~7시간)
├── 프로젝트: 주말 + 틈틈이 (주 5~8시간)
│   ├── GitMini 완성도 높이기
│   ├── xrun 기능 추가
│   └── 타이쿬 꾸준히 진행 (매일 30분~1시간)
└── 학교 수업 집중
```

---

### 알고리즘 학습 계획 (백준)

```
목표:
├── 매일 1문제 (30분~1시간)
├── 코딩 테스트 통과 수준 (실버~골드)
└── 알고리즘적 사고력 향상

전략:
├── 못 풀면 풀이 보고 이해 → 다음날 다시 풀기
├── "맞았습니다"보다 "왜 이게 맞는지" 이해
├── 주 5일 (평일), 주말은 프로젝트 집중
└── solved.ac 클래스 시스템 활용

알고리즘 재능과 개발 재능은 다름:
├── 알고리즘 잘하는 사람 ≠ 좋은 개발자
├── 좋은 개발자 ≠ 알고리즘 고수
└── 둘 다 잘하면 금상첨화
```

### 예상 결과 (연말 기준)

```
백엔드/프로그래밍:
├── Java: 중급~실무 (타이쿬 경험)
├── C++: 초급~중급 (xrun, 미니쉘)
├── REST API: OK (GitHub 대시보드)
└── 레벨: 주니어 실무자 충분 ✅

AI:
├── 이론: 학교 커리큘럼 (기초~중급)
├── 실습: 마크 로그 분석 + ML 적용
├── Python: 중급 (실전 프로젝트로 숙련)
└── 레벨: 학부생 상위권 (실무자는 2027년)

알고리즘:
├── 백준 실버~골드 안정권
├── 코딩 테스트 통과 가능
└── 알고리즘적 사고력 향상

차별점:
├── "실제 사용자 있는 서비스 개발/운영"
├── "학교 AI를 내 프로젝트에 적용"
└── "다양한 언어/기술 스택 경험"
```

---

### 마인드셋: 재능 vs 꾸준함

#### "재능"에 대한 현실적 관점

```
재능이 결정하는 것:
└── 속도 (얼마나 빨리 배우는가)

재능이 결정 못하는 것:
├── 끝까지 가는가 (꾸준함)
├── 얼마나 깊이 파는가 (집요함)
├── 협업 잘하는가 (소통)
├── 문제를 정의하는가 (사고력)
└── 포기 안 하는가 (끈기)
```

#### 개발자 레벨과 재능 필요도

| 레벨 | 설명 | 재능 필요도 |
|------|------|-----------|
| **취업 가능** | 코드 짜고, 리뷰 받고, 성장 | 꾸준함 > 재능 |
| **실무 중급** | 독립적으로 기능 개발, 설계 가능 | 경험 > 재능 |
| **시니어** | 팀 리딩, 아키텍처 설계 | 경험 + 소통 > 재능 |
| **업계 상위 10%** | 기술 블로그, 오픈소스 기여 | 꾸준함 + 깊이 |
| **업계 상위 1%** | 언어/프레임워크 만드는 사람 | 재능 + 환경 + 운 |

**현실**: 상위 1%가 아니어도 "대단한" 개발자 많음. 상위 10%면 충분히 인정받음.

#### 핵심 메시지

> **재능은 속도를 결정하고, 꾸준함은 도착을 결정한다.**

```
"재능이 없다"고 느끼는 이유:

가능성 1: 비교 대상이 잘못됨
   → 유튜브/커뮤니티의 "천재 개발자"들은 상위 0.1%
   → 비교 자체가 불공평

가능성 2: 아직 경험이 부족한 것뿐
   → "모르는 게 많다" ≠ "재능이 없다"
   → 3~5년 하면 지금 고민이 웃겨질 수 있음

가능성 3: 성장 속도에 대한 불안
   → "남들은 빨리 배우는데 나는..."
   → 속도보다 방향이 중요함
```

---

## 프로젝트 상세 설계

> 각 토이 프로젝트의 의도, 범위, 기술 스택, 학습 목표를 명확히 정의

### 프로젝트 목록 및 우선순위

| 순서 | 프로젝트 | 언어 | 목적 | 시기 |
|------|---------|------|------|------|
| 1 | **타이쿬 1.21.X** | Java | 핵심 역량 + 실서비스 | 상반기 |
| 2 | **GitHub 대시보드** | Java | REST API 보완 | 상반기 |
| 3 | **xrun** | C++ | 시스템 레벨 경험 | 상반기~방학 |
| 4 | **미니 쉘** | C++ | OS/프로세스 감각 | 방학 |
| 5 | **마크 로그 분석기** | Python | AI 진로 연결 | 하반기 |

---

### 1. 타이쿬 1.21.X (Java)

#### 프로젝트 정보

| 항목 | 내용 |
|------|------|
| **한 줄 설명** | Minecraft 1.21.X용 경제/직업 시스템 플러그인 신규 개발 |
| **목적** | Java 핵심 역량 + 대규모 프로젝트 경험 + 실서비스 운영 |
| **기간** | 3~8월 (약 6개월) |
| **난이도** | ★★★★☆ |

#### 학습 목표

```
핵심 역량:
├── 디자인 패턴 실전 적용 (Factory, Registry, Template, Observer, Strategy)
├── 도메인 모델링 (직업, 경제, 강화 시스템)
├── 클라이언트-서버 통신 프로토콜 설계
├── 이벤트 기반 아키텍처
├── 동시성 처리 (ConcurrentHashMap, Thread Safety)
├── 설정 기반 개발 (YAML, Hot Reload)
└── 외부 플러그인 연동 (Vault, Citizens, WorldGuard)

부가 역량:
├── Git 브랜치 전략
├── 문서화 습관
└── 디버깅/프로파일링
```

#### 기술 스택

```
언어: Java 21
프레임워크: Paper API 1.21.X
빌드: Maven (또는 Gradle로 전환 고려)
외부 연동: Vault, Citizens, WorldGuard, Lands, LuckPerms
통신: Plugin Messaging + JSON Protocol
```

#### MVP 범위

```
Phase 1 (필수):
├── 부트스트랩 구조 (ServiceRegistry, ConfigManager)
├── 플레이어 데이터 관리
├── 경제 시스템 (Vault 연동)
└── 기본 직업 1개 (Miner)

Phase 2 (핵심):
├── 추가 직업 (Farmer, Fisher)
├── 등급 시스템
├── 상점 시스템
└── 클라이언트 HUD 연동

Phase 3 (확장):
├── 강화 시스템
├── 업적 시스템
├── 도감 시스템
└── 세금 시스템
```

#### LITE와의 차이점 (개선 방향)

```
개선할 점:
├── TycoonPlugin.java 분리 (Fat Plugin Class 해소)
│   └── SystemInitializer 패턴 적용
├── 테스트 코드 추가 (JUnit)
├── 문서화 강화 (JavaDoc)
└── CI/CD 구축 (GitHub Actions)

유지할 점:
├── ServiceRegistry 패턴
├── 설정 기반 개발
├── 이벤트 리스너 구조
└── 클라이언트 통신 프로토콜
```

---

### 2. GitHub 대시보드 (Java)

#### 프로젝트 정보

| 항목 | 내용 |
|------|------|
| **한 줄 설명** | 내 GitHub 정보를 한눈에 보는 데스크톱 대시보드 |
| **목적** | REST API 경험 + JavaFX GUI + 포트폴리오 다양성 |
| **기간** | 5~6월 (약 1~2개월) |
| **난이도** | ★★★☆☆ |

#### 학습 목표

```
핵심 역량:
├── REST API 호출 (HttpClient)
├── JSON 파싱 (Gson/Jackson)
├── 비동기 처리 (UI 블로킹 방지)
├── JavaFX 기본 (FXML, Scene)
└── 인증 처리 (Personal Access Token)

부가 역량:
├── 사용자 경험 (UX) 설계
├── 에러 핸들링
└── 캐싱 전략
```

#### 기술 스택

```
언어: Java 17+
GUI: JavaFX
HTTP: java.net.http.HttpClient
JSON: Gson 또는 Jackson
API: GitHub REST API v3
인증: Personal Access Token (PAT)
```

#### MVP 범위

```
MVP (Must Have):
├── 내 레포지토리 목록 조회
├── 내가 할당된 Issues 조회
├── 리뷰 요청된 PR 조회
├── Quick Action (브라우저에서 열기, 링크 복사)
└── 토큰 설정/저장

MVP 제외 (Won't Have):
├── git clone / push / pull
├── 브랜치 관리 / 머지
├── Actions 관리
└── 코드 에디터
```

#### 확장 아이디어 (후순위)

```
├── 알림 기능 (새 Issue/PR)
├── 여러 조직 지원
├── 다크 모드
├── 통계 대시보드 (커밋 히트맵 등)
└── 템플릿 Issue 생성
```

---

### 3. xrun (C++)

#### 프로젝트 정보

| 항목 | 내용 |
|------|------|
| **한 줄 설명** | 커맨드 러너 + 인코딩 엔진 (cmd/PowerShell 한글 깨짐 해결) |
| **목적** | C++ 시스템 레벨 경험 + 실용 도구 |
| **기간** | 6~8월 (약 2~3개월) |
| **난이도** | ★★★☆☆ |

#### 학습 목표

```
핵심 역량:
├── 프로세스 생성/관리 (CreateProcess 등)
├── stdout/stderr 캡처
├── 인코딩 변환 (CP949 ↔ UTF-8)
├── 파일 I/O
├── 명령어 파싱
└── 로그 저장/관리

부가 역량:
├── Windows API 사용
├── 에러 핸들링
└── CLI 인터페이스 설계
```

#### 기술 스택

```
언어: C++17 이상
빌드: CMake
플랫폼: Windows (우선), Linux (선택)
외부 라이브러리: 최소화 (가능하면 표준 라이브러리만)
```

#### MVP 범위

```
기본 기능:
├── xrun add <name> "<command>" - 명령어 등록
├── xrun run <name> - 명령어 실행
├── xrun list - 등록된 명령어 목록
├── xrun history --last N - 실행 이력
└── xrun run <name> --retry N - 재시도

인코딩 기능:
├── xrun run <name> --decode auto|cp949|utf8
├── xrun enc file <path> --to utf8
└── xrun doctor - 환경 진단

로그 기능:
├── 실행 요약 (exit code, duration)
└── 로그 파일 저장 (logs/YYYY-MM-DD_<name>.log)
```

#### 설계 구조

```
src/
├── core/
│   ├── Runner.cpp      // 프로세스 실행 + stdout/stderr 캡처
│   ├── Encoding.cpp    // 바이트 → 문자열 디코딩
│   └── LogStore.cpp    // 로그 파일 저장 (UTF-8 통일)
├── commands/
│   ├── AddCommand.cpp
│   ├── RunCommand.cpp
│   ├── ListCommand.cpp
│   ├── HistoryCommand.cpp
│   ├── EncCommand.cpp
│   └── DoctorCommand.cpp
├── config/
│   └── ConfigManager.cpp  // JSON 설정 파일 관리
└── main.cpp
```

---

### 4. 미니 쉘 (C++)

#### 프로젝트 정보

| 항목 | 내용 |
|------|------|
| **한 줄 설명** | 기본 명령어와 파이프를 지원하는 미니 쉘 |
| **목적** | OS/시스템 감각 + xrun과 시너지 |
| **기간** | 7~8월 방학 (약 1~2개월) |
| **난이도** | ★★★☆☆ |

#### 학습 목표

```
핵심 역량:
├── 프로세스 생성/종료 (fork/exec 또는 CreateProcess)
├── 파이프 구현
├── 리다이렉션 (>, <, >>)
├── 환경 변수 관리
├── 시그널 처리 (Ctrl+C 등)
└── 명령어 파싱 (토큰화)

부가 역량:
├── 시스템 콜 이해
├── 쉘이 어떻게 동작하는지 체득
└── xrun 설계에 피드백
```

#### 기술 스택

```
언어: C++17 이상
빌드: CMake
플랫폼: Linux 우선 (fork/exec), Windows 선택
```

#### MVP 범위

```
내부 명령어:
├── cd <path> - 디렉토리 이동
├── pwd - 현재 경로 출력
├── exit - 쉘 종료
├── export VAR=value - 환경 변수 설정
└── echo $VAR - 환경 변수 출력

외부 명령어:
├── 일반 명령어 실행 (ls, cat 등)
├── 파이프 지원 (cmd1 | cmd2)
└── 리다이렉션 (cmd > file, cmd < file)

예외 처리:
├── 없는 명령어
├── 권한 오류
└── Ctrl+C 처리
```

---

### 5. 마크 로그 분석기 (Python)

#### 프로젝트 정보

| 항목 | 내용 |
|------|------|
| **한 줄 설명** | 마인크래프트 서버 로그를 분석하는 데이터 분석 도구 |
| **목적** | Python 숙련 + 데이터 분석 + AI 진로 연결 |
| **기간** | 9~12월 (약 3~4개월) |
| **난이도** | ★★★☆☆ (기본) ~ ★★★★☆ (ML 적용 시) |

#### 학습 목표

```
핵심 역량:
├── Python 실전 숙련
├── 파일 I/O + 문자열 파싱
├── 데이터 분석 (pandas)
├── 시각화 (matplotlib/seaborn)
└── CLI 인터페이스 (argparse/click)

AI 연결 (심화):
├── 기초 ML (scikit-learn)
├── 이상 탐지 (플레이어 행동 패턴)
├── 예측 모델 (접속자 수 예측)
└── 학교 AI 수업 내용 적용
```

#### 기술 스택

```
언어: Python 3.10+
데이터: pandas, numpy
시각화: matplotlib, seaborn
ML (선택): scikit-learn, (TensorFlow/PyTorch 맛보기)
CLI: argparse 또는 click
출력: CSV, HTML 리포트
```

#### MVP 범위

```
Phase 1 (기본 분석):
├── 로그 파싱 (접속자, 시간, 이벤트)
├── 통계 (일별 접속자, 활동 시간대, 자주 쓰는 명령어)
├── 시각화 (차트)
└── CLI 인터페이스

Phase 2 (심화 분석):
├── 플레이어별 행동 패턴
├── 경제 데이터 분석 (타이쿬 연동)
├── HTML 리포트 자동 생성
└── 이상 탐지 (급증/급감 알림)

Phase 3 (ML 적용):
├── 접속자 수 예측
├── 이탈 예측
├── 추천 시스템 (아이템/직업)
└── 학교 AI 프로젝트로 연결
```

#### 타이쿬과의 시너지

```
연결점:
├── 타이쿬 서버에서 실제 로그 데이터 획득
├── 경제 시스템 데이터 → 물가 분석
├── 직업 시스템 데이터 → 인기 직업 분석
└── "내 서버 데이터로 AI 적용" = 포트폴리오 차별화

데이터 소스:
├── server.log (Minecraft 기본 로그)
├── 플러그인 로그 (타이쿬 자체 로깅)
├── 플레이어 데이터 (JSON/YAML)
└── 경제 트랜잭션 로그 (별도 구현 필요)
```

---

### 프로젝트 간 연결 관계

```
┌─────────────────────────────────────────────────────────────────┐
│                        프로젝트 연결도                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐         개념 연결         ┌──────────┐           │
│  │ 미니 쉘  │ ─────────────────────────▶│  xrun    │           │
│  │  (C++)   │  프로세스/명령 실행 이해   │  (C++)   │           │
│  └──────────┘                          └──────────┘           │
│       │                                                        │
│       │ 시스템 레벨 사고                                        │
│       ▼                                                        │
│  ┌──────────────┐                      ┌──────────────┐        │
│  │ 타이쿬 1.21.X │   Java 설계 역량      │ GitHub 대시보드│        │
│  │   (Java)     │ ───────────────────▶ │   (Java)      │        │
│  └──────────────┘                      └──────────────┘        │
│       │                                       │                 │
│       │ 서버 로그 데이터                       │ API 경험        │
│       ▼                                       ▼                 │
│  ┌──────────────────────────────────────────────┐              │
│  │           마크 로그 분석기 (Python)           │              │
│  │   ┌─────────────────────────────────────┐   │              │
│  │   │ 타이쿬 데이터 + AI 적용 = 포트폴리오  │   │              │
│  │   └─────────────────────────────────────┘   │              │
│  └──────────────────────────────────────────────┘              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 성공 판정 기준

| 프로젝트 | 성공 기준 |
|---------|----------|
| **타이쿬 1.21.X** | 실서버 배포 + 플레이어 10명+ 동시 접속 안정 |
| **GitHub 대시보드** | 본인이 1주일 20회+ 실제 사용 |
| **xrun** | 자주 쓰는 명령 5개+ 등록, 1달간 실사용 |
| **미니 쉘** | 기본 명령어 + 파이프 동작 |
| **마크 로그 분석기** | 분석 리포트 생성 + ML 1개 이상 적용 |

---

## 이 문서 활용법

### 3가지 학습 소스 조합

```
┌─────────────────────────────────────────────────────────────┐
│                    효과적인 학습 방식                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  📚 서적 (이론)       📄 STUDY_GUIDE (실전)    🤖 AI (질문)  │
│  ┌─────────────┐     ┌─────────────┐       ┌───────────┐   │
│  │ Java 기본서  │     │ 디자인 패턴  │       │ "이게 왜  │   │
│  │ 디자인 패턴책│ ──▶ │ 아키텍처    │ ──▶   │ 이렇게    │   │
│  │ Clean Code │     │ 실제 코드   │       │ 되는거야?"│   │
│  └─────────────┘     └─────────────┘       └───────────┘   │
│        │                   │                     │          │
│        └───────────────────┼─────────────────────┘          │
│                            ▼                                │
│                  💡 깊은 이해 + 실전 적용                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘

서적이 "왜?"를 알려주면,
이 문서는 "어떻게 적용했는지"를 보여줌
```

### 상황별 활용법

| 상황 | 방법 |
|------|------|
| **개념 처음 볼 때** | 서적으로 이론 학습 |
| **실제 적용 보고 싶을 때** | 이 문서 + LITE 코드 참고 |
| **이해 안 될 때** | AI에게 질문 ("이 패턴이 여기서 왜 쓰인 거야?") |
| **새 기능 만들 때** | 이 문서의 패턴 참고 + AI와 설계 논의 |
| **코드 리뷰 원할 때** | AI에게 코드 보여주고 피드백 요청 |

### 마인크래프트 버전과 무관한 핵심 배움

```
이 문서에서 배우는 것 (버전 불문):
├── 디자인 패턴 (Factory, Registry, Template, Observer, Strategy)
├── 아키텍처 설계 (레이어 분리, 모듈화)
├── DI 컨테이너 (ServiceRegistry)
├── 이벤트 기반 아키텍처
├── 동시성 (ConcurrentHashMap, Thread Safety)
├── 트랜잭션/롤백 패턴
├── 설정 기반 개발 (YAML)
├── 클라이언트-서버 통신 프로토콜
└── 대규모 코드베이스 관리

→ 전부 Java 백엔드의 핵심 개념 = 불변
→ 1.20이든 1.21이든 1.30이든 그대로 적용 가능

버전에 따라 변하는 것 (사소함):
├── API 메서드 이름/시그니처
├── 이벤트 이름
└── 일부 기능 deprecated
→ 공식 문서 참고하면 금방 적응
```

### 이 문서의 역할

```
STUDY_GUIDE.md의 포지션:
├── "이론 ↔ 실전" 연결 다리
├── LITE 코드베이스의 해설서
├── 학습 체크리스트
├── 새 프로젝트 설계 시 참고서
├── 포트폴리오 준비 가이드
└── 살아있는 문서 (계속 업데이트)
```

---

## 마치며

### 이 코드베이스의 특징

| 특징 | 설명 | 학습 가치 |
|------|------|----------|
| **실무 수준의 구조** | 대규모 프로젝트에서 사용하는 패턴들이 적용됨 | 취업 준비에 도움 |
| **설정 기반 설계** | 코드 수정 없이 YAML로 밸런싱 조정 가능 | 유지보수성 이해 |
| **모듈화** | 기능별로 패키지가 분리되어 있어 유지보수 용이 | 코드 구조화 학습 |
| **확장성** | 새로운 직업, 상점, 인챈트 등을 쉽게 추가할 수 있는 구조 | OCP 원칙 체득 |
| **클라이언트-서버 통신** | 실시간 JSON 프로토콜 구현 | 네트워크 프로그래밍 |

### 학습 팁

1. **"왜"를 질문하라**
   - 왜 이 패턴을 썼을까?
   - 왜 이 자료구조를 선택했을까?
   - 왜 이 순서로 초기화할까?

2. **코드를 수정해보라**
   - 일부러 잘못된 순서로 초기화해보고 에러 확인
   - Optional을 null로 바꿔보고 NPE 확인
   - 새 기능 추가해보며 구조 이해

3. **문서화하며 배워라**
   - 이해한 내용을 이 문서에 추가
   - 나만의 학습 일지 작성
   - 다른 사람에게 설명해보기

4. **실제 서버에서 테스트하라**
   - 로컬 테스트 서버 구축
   - 실제 플레이하며 코드 동작 확인
   - 로그 분석으로 흐름 추적

### 다음 단계

이 프로젝트를 충분히 이해했다면:

1. **Spring Boot 학습** - 이 프로젝트의 DI 패턴이 Spring의 기초
2. **데이터베이스** - JSON 파일 대신 MySQL/Redis 연동
3. **테스트 코드** - JUnit으로 단위 테스트 작성
4. **CI/CD** - GitHub Actions로 자동 빌드/배포

---

## 문서 업데이트 이력

| 날짜 | 내용 |
|------|------|
| 2026-02-03 | 초판 작성 |
| 2026-02-03 | 아키텍처 다이어그램 상세화, CS 개념 추가, 학습 전략/외부 도구 추가 |
| 2026-02-03 | 포트폴리오 섹션 추가 |
| 2026-02-03 | 기술 부채/리팩토링 포인트 추가 (TycoonPlugin Fat Class) |
| 2026-02-03 | 타이쿬 1.21.X vs 토이 프로젝트 비교 분석 추가 |
| 2026-02-03 | 2026년 연간 학습 계획, 프로젝트 상세 설계 추가 |
| 2026-02-03 | **2월 방학 집중 계획 추가** (타이쿬 매일 30분 포함) |
| 2026-02-03 | 이 문서 활용법 섹션 추가 |

---

> 이 문서는 살아있는 문서입니다. 학습하며 새로 알게 된 내용을 계속 추가하세요!
