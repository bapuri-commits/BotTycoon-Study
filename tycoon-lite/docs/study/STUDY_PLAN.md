# 타이쿤 스터디 계획 (스킬 우선 → 플러그인 제작)

> 목표: 코드에 나오는 스킬을 능수능란하게 사용한 뒤, 이 수준의 플러그인을 직접 제작할 수 있는 수준까지 도달한다.

---

## 📌 전체 단계 요약

| 단계 | 내용 | 목표 상태 |
|------|------|-----------|
| **1. 준비** | 환경 + 가이드 위치 | 빌드 성공, 가이드 찾기 가능 |
| **2. 구조 감 잡기** | 진입점 + 아키텍처 + Use-case 한 줄기 | "광물 캐기" 흐름을 말로 설명 가능 |
| **3. OOP·패턴** | 상점/OOP → Registry·Listener·Factory·DI | 코드 스킬을 이름 붙여 설명 가능 |
| **4. 이벤트·데이터** | 리스너 1~2개 + PlayerData/Economy 흐름 | 이벤트→서비스, 데이터 관리 감 잡힘 |
| **5. 네트워크(선택)** | PROTOCOL + ModData/UiData 흐름 | 서버–클라 통신 구조 이해 |
| **6. 스킬 적용** | 기능 하나 설계·구현 (확장 or 새 플러그인) | "이 정도 플러그인 제작" 체감 |

---

## 1. 준비 (1~2일) ✅ 완료

- [x] 프로젝트 열기: tycoon-lite (서버), 필요 시 tycoon-hud/ui (클라이언트)
- [x] 빌드: `mvn clean package` 성공
- [x] (선택) 테스트 서버에서 플러그인 로드 확인
- [x] 가이드 위치: `docs/study/STUDY_GUIDE.md` — 목차에서 **아키텍처 이해하기**, **학습 로드맵**, **효과적인 학습 전략** 확인

---

## 2. 구조 감 잡기 (약 1주) ← **현재 단계**

**이 단계 끝나면:** "타이쿤이 어떻게 켜지는지", "광물 캐기 한 가지가 어떤 클래스들을 타는지"를 말로 설명할 수 있는 수준.

### 2-1. 진입점 파악

- [ ] **TycoonPlugin 열기**  
  경로: `src/main/java/kr/bapuri/tycoon/TycoonPlugin.java`
- [ ] **onEnable() 메서드만 읽기** (대략 177~205줄 근처)
  - `checkDependencies()` → 필수 플러그인 확인
  - **`initServices()`** → 서비스 초기화
  - **`initListeners()`** → 리스너 등록
  - **`initCommands()`** → 명령어 등록  
  → "이 세 단계가 있다"는 것만 이해하면 됨.
- [ ] **initServices() 안을 훑기** (211줄부터)
  - `initWorldSystem()`, `initJobSystem()`, `initMinerSystem()`, `initCodexSystem()` 등 **이름만** 보면서 "어떤 기능들이 올라오는지" 목록만 파악.  
  - 세부 구현은 건너뛰어도 됨.

**참고:** STUDY_GUIDE — **아키텍처 이해하기** > "서버 플러그인 상세 계층 구조" 다이어그램.

---

### 2-2. 아키텍처 다이어그램과 연결

- [ ] STUDY_GUIDE에서 **"서버 플러그인 상세 계층 구조"** 섹션 읽기
  - Bootstrap (TycoonPlugin, ServiceRegistry, ListenerRegistry, ConfigManager)
  - Core Services (PlayerDataManager, EconomyService, WorldManager …)
  - Feature Systems (Job, Shop, Codex, Enhance …)
  - Integration (Vault, Citizens, Lands …)
  - Mod Communication (ModDataService, ModRequestHandler)
- [ ] "계층이 나뉘어 있고, 진입점에서 서비스·리스너·명령을 등록한다"는 느낌만 갖기.

---

### 2-3. Use-case 한 줄기: "플레이어가 광물을 캤을 때"

아래 순서대로 **코드에서 실제로** 따라가 보기.

| 순서 | 할 일 | 파일 경로 (tycoon-lite 기준) |
|------|--------|------------------------------|
| 1 | 블록 파괴 이벤트를 누가 받는지 찾기 | `job/miner/MinerListener.java` |
| 2 | `@EventHandler` 메서드 확인 | `onBlockBreak(BlockBreakEvent event)` (92~125줄 근처) |
| 3 | 이 메서드 안에서 어떤 서비스를 부르는지 확인 | `expService.addMiningExp(player, material, 1)` (117줄) |
| 4 | MinerExpService가 뭘 하는지 열어보기 | `job/miner/MinerExpService.java` — `addMiningExp` → 내부에서 `addExp` 호출 |
| 5 | 경험치가 어디에 저장되는지 (선택) | `job/common/AbstractJobExpService.java` — `addExp` → `PlayerDataManager` 등 사용 |

**추적 요약 (직접 채우기):**

```
BlockBreakEvent 발생
  → MinerListener.onBlockBreak()
    → MinerExpService.addMiningExp(player, material, 1)
      → (내부) addExp() → 플레이어 데이터에 경험치 반영
```

- [ ] 위 흐름을 코드에서 한 번씩 **Ctrl+B(선언으로 이동)** 로 따라가 보기.
- [ ] (선택) `MinerListener`가 **어디서 생성·등록되는지** 찾기  
  → `TycoonPlugin.java`에서 `initMinerSystem` 검색 후, 리스너 등록 부분만 확인.

**참고:** STUDY_GUIDE — **효과적인 학습 전략** > "Use-Case 기반 접근법" (플레이어가 광물을 캤을 때).

---

### 2-4. 이 단계 완료 체크

- [ ] "플러그인 켜질 때 initServices → initListeners → initCommands 순서로 올라온다"를 말로 설명할 수 있다.
- [ ] "광물 캐면 BlockBreakEvent → MinerListener → MinerExpService → 경험치 반영" 흐름을 말로 설명할 수 있다.
- [ ] (선택) STUDY_LOG에 **오늘 본 파일 / 새로 안 것 / 질문** 한 줄씩 적어 두기.

---

## 3. OOP·패턴 (약 2~3주)

- [ ] **OOP (가이드 1단계)**  
  `shop` 패키지: IShop → AbstractShop → MinerShop (또는 FarmerShop) — 인터페이스·추상 클래스·템플릿 메서드가 어떻게 쓰이는지.
- [ ] **디자인 패턴 (가이드 2단계)**  
  Registry(CodexRegistry / JobRegistry), Listener(MinerListener), Factory(LampItemFactory), DI(ServiceRegistry) — 각각 "무엇 / 왜 / 코드 위치" 정리.
- [ ] **정리**  
  STUDY_NOTES 또는 STUDY_LOG에 패턴별 한 줄 요약. (선택) 클래스 다이어그램 한 장.

**참고:** STUDY_GUIDE — **학습 로드맵** > 1단계 Java OOP 기초, 2단계 디자인 패턴.

---

## 4. 이벤트·데이터 흐름 (약 1주)

- [ ] 다른 리스너 1~2개 골라서 "어떤 이벤트 → 어떤 서비스"인지 추적.
- [ ] PlayerDataManager — 플레이어 데이터 로드/저장/캐시 큰 그림.
- [ ] (가능하면) EconomyService — 거래 한 번 시 잔액 확인·차감·롤백 흐름 훑기.

**참고:** STUDY_GUIDE — **모듈별 완전 분석**, **코드에서 배우는 CS 개념**.

---

## 5. 네트워크 (선택, 약 1주)

- [ ] `docs/PROTOCOL.md` 훑기 (같은 repo 또는 상위 경로).
- [ ] ModDataService / ModRequestHandler (서버), UiDataReceiver / UiRequestSender (클라이언트) — 요청 한 종류만 끝까지 따라가기.

---

## 6. 스킬 적용 (플러그인 제작)

- [ ] 추가할 기능 하나 정하기 (새 직업, 새 상점, 새 이벤트 반응 등).
- [ ] 타이쿤 구조에 맞춰 "리스너·서비스·레지스트리" 위치 설계.
- [ ] 기존 패턴 따라 구현하거나, 새 Maven 프로젝트로 작은 플러그인을 만들어 같은 구조로 적용해 보기.
- [ ] "어떤 패턴을 어디에 썼는지" 한 줄씩 정리.

---

## 📁 관련 문서

| 문서 | 용도 |
|------|------|
| `STUDY_GUIDE.md` | 아키텍처, 로드맵, 패턴 설명, 학습 전략 |
| `STUDY_PLAN.md` | 이 문서 — 단계별 할 일·체크리스트 |
| (선택) STUDY_LOG.md | 일별 "오늘 본 파일 / 배운 것 / 질문" |
| (선택) STUDY_NOTES.md | 개념별 "무엇 / 왜 / 코드 위치 / 요약" |

---

*준비 단계 완료 후, 현재 단계는 **2. 구조 감 잡기**입니다. 위 2-1 ~ 2-4를 순서대로 진행하면 됩니다.*
