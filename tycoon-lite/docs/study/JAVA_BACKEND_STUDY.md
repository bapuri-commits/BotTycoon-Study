# Java 백엔드 학습 설계서

> **최종 목표: Java 백엔드 활용능력 학부생 최상위.** 타이쿤 코드 스터디는 그 첫 번째 훈련장(Lv1.3 달성).
> 다른 대화창에서도 Java / 백엔드 관련 작업 시 이 문서를 기준으로 레벨·방향·원칙을 맞춘다.

---

## 1. 현재 내 Java 백엔드 레벨

> 공부가 진행되면서 감각이 달라지면, 새 버전(v2, v3…)으로 갱신한다.

### v1 (2026-02-09)

**Java**
- 학부 수업 수준으로 배움. 문법과 기본 OOP(클래스/상속/인터페이스) 개념은 아는 상태.
- 하지만 "참조가 왜 위험한지", "equals/hashCode가 왜 중요한지" 같은 **감각적 이해**는 아직 없음.
- 컬렉션(ArrayList, HashMap)을 사용해봤지만, **왜 이 자료구조를 선택하는지** 설명은 어려움.

**백엔드**
- Spring을 "왜 쓰는지" 모름.
- JVM, GC, 동시성 → 거의 모름.
- CRUD 따라 치면 되지만 설명은 못 함.
- Controller / Service / Repository 구분의 **이유**를 모름.

**구체적 관찰 (대화하며 발견한 것 — 계속 누적)**
- 타이쿤 코드의 `onEnable()` → `initServices()` → `initListeners()` 흐름을 따라가며 "서버 초기화 순서" 개념을 처음 접함.
- `MinerListener` → `MinerExpService` → `AbstractJobExpService` 흐름을 추적하며 "이벤트 → 서비스 → 데이터" 구조를 처음 체감.
- 아키텍처 다이어그램과 실제 코드의 초기화 순서가 다를 수 있다는 것을 이해함 (의존 방향 vs 생성 순서).

### 목표: Java 백엔드 학부생 최상위

**타이쿤 스터디의 마일스톤 (Lv1.3):**
- "서버가 켜질 때 무슨 순서로 올라오는지" 설명 가능
- "이벤트/요청이 들어오면 어떤 계층을 거쳐 처리되는지" 설명 가능
- "왜 Listener에 로직을 넣으면 안 되는지" 설명 가능
- "DI가 없으면 뭐가 불편한지" 코드로 보여줄 수 있음
- OOP가 "객체 놀이"가 아니라 "변경 격리 도구"라는 감각 확보

**최종 Lv2 (학부생 '개쩐다'):**

> **"자바로 서버를 만들 때, 이 코드가 언제, 왜, 어떻게 깨질 수 있는지를 미리 생각하면서 설계할 수 있는 상태"**

**자가 점검 질문 (이것들에 즉답 가능하면 Lv2 달성):**
- "왜 서버는 싱글 스레드가 아닌가?"
- "GC는 언제 서버를 멈출 수 있나?"
- "이 로직은 왜 트랜잭션이 필요한가?"
- "이 책임은 컨트롤러에 있어야 할까?"
- "장애 나면 어디부터 봐야 할까?"

---

## 2. 학습 철학 & 원칙

### 핵심 한 줄

**이 스터디의 목적은 "마크 플러그인 배우기"가 아니라, "자바 백엔드 시스템 사고를 몸에 박는 것"이다.**

### 원칙

1. **타이쿤 코드(프로젝트) 우선, 자료/책은 보조.**
   - 먼저 코드를 읽고 → 막히는 지점에서 자바의 정석·자료를 필요한 만큼만 가져온다.
   - 책을 다 읽고 시작하는 게 아니라, 타이쿤이 메인이고 책은 참고자료.

2. **Spring, Web, DB는 나중에 얹는 껍데기다.**
   - 지금은 **구조·흐름·역할 분리 감각**이 전부다.
   - 타이쿤에서 수동 DI를 이해하면, Spring의 자동 DI가 왜 필요한지 저절로 연결된다.

3. **"왜 이렇게 설계했는지"를 항상 질문하고 기록한다.**
   - 코드를 읽을 때 "어떻게 동작하는지"에서 멈추지 않고, "왜 이렇게 나눴는지"까지 간다.
   - 이 질문이 Controller/Service/Repository 감각을 만든다.

4. **파일 하나 볼 때마다 "코드 스터디 템플릿"으로 정리한다.**
   - 단순 코드 읽기 → 시스템 사고로 전환하는 도구.
   - 7절의 템플릿 참고.

5. **레벨을 뛰어넘지 않는다.**
   - Phase 순서를 지킨다. 어기면 "Spring만 아는 사람"이 된다.
   - 타이쿤 스터디(Phase 0~1 + 3 일부) → Spring 프로젝트(Phase 3~4) → 동시성 심화(Phase 5).

---

## 3. Java 기초 감각 로드맵

### 3.1 Phase 0 — Java 언어 기초 (Lv0 → Lv0.5)

**타이쿤 스터디에서 커버:** STUDY_PLAN 1~2단계 (준비 + 구조 감 잡기)

반드시 확보해야 하는 감각:

**문법 / 실행 흐름**
- [ ] main부터 시작해서 코드가 어떻게 흘러가는지 말로 설명 가능
- [ ] 클래스/객체가 뭔지 설명 가능
- [ ] 메서드 호출 → 리턴 흐름 이해

**타이쿤에서 확인:**
- `TycoonPlugin.onEnable()` → `initServices()` → `initMinerSystem()` 흐름 추적
- `MinerListener.onBlockBreak()` → `MinerExpService.addMiningExp()` → `addExp()` 호출 체인

**종료 기준:** "플러그인이 켜질 때 무슨 순서로 뭐가 올라오는지" 말로 설명 가능

---

### 3.2 Phase 1 — 객체·컬렉션 감각 (Lv0.5 → Lv1)

**타이쿬 스터디에서 커버:** STUDY_PLAN 3~4단계 (OOP·패턴 + 이벤트·데이터)
**자바의 정석 병행:** ch6~7(OOP), ch11(컬렉션)

반드시 확보해야 하는 감각:

**참조 개념 (제일 중요)**
- [ ] 객체는 전부 참조라는 것 이해
- [ ] 메서드 인자로 객체를 넘기면 무슨 일이 생기는지 설명 가능
- [ ] 같은 `PlayerTycoonData` 객체를 여러 서비스가 공유할 때 한 쪽에서 바꾸면 다른 쪽에도 영향 → 이해

**컬렉션 기본**
- [ ] ArrayList — 왜 대부분 이걸 쓰는가
- [ ] HashMap — Key로 O(1) 조회가 왜 중요한가
- [ ] EnumSet — 왜 광석 목록에 이걸 쓰는가

**OOP 실전 의미**
- [ ] 인터페이스/추상 클래스가 "변경 격리 도구"라는 감각
- [ ] `IShop → AbstractShop → MinerShop` 구조가 왜 좋은지 설명 가능
- [ ] `AbstractJobExpService → MinerExpService` 템플릿 메서드 패턴 이해

**타이쿤에서 확인:**

| 개념 | 코드 위치 |
|------|----------|
| 참조 공유 | `PlayerDataManager.get(uuid)` → 같은 객체를 여러 서비스가 사용 |
| ArrayList | `BlockProcessingService` 프로세서 목록 |
| HashMap | `CodexRegistry`의 `Map<Material, CodexRule>` |
| EnumSet | `MinerListener.ORE_MATERIALS` |
| 인터페이스 → 추상 → 구현 | `IShop → AbstractShop → MinerShop` |
| 템플릿 메서드 | `AbstractJobExpService.addExp()` (공통) ← `MinerExpService.addMiningExp()` (특화) |
| equals/hashCode | `Material` enum 비교, `EnumSet.contains()` |

**종료 기준 — 아래 질문에 대답 못 하면 다음 단계 금지:**
- "왜 Java는 포인터가 없는데도 위험한가?"
- "HashMap에서 equals/hashCode가 왜 중요한가?"
- "왜 대부분 ArrayList를 쓰는가?"

---

## 4. 백엔드 시스템 사고 로드맵

### 4.1 Phase 2 — JVM & 메모리 감각 (Lv1 → Lv1.3)

**타이쿤에서 힌트만, 별도 자료 필요**

반드시 알 것 (깊이 아닌 개념):

- [ ] Stack / Heap 차이
- [ ] 객체는 언제 GC 대상이 되는가
- [ ] 객체를 많이 만들면 왜 느려지는가

**타이쿤에서 보이는 힌트:**
- `ConcurrentHashMap` 사용 → 성능/스레드 관련 설계 결정
- `markDirty()` 패턴 → 매번 저장하면 I/O 비용이 크다는 감각
- `AtomicLong` → 원자적 연산이 필요한 이유

**종료 기준:**
- "객체를 많이 만들면 왜 서버가 느려질 수 있나?"
- "GC가 언제 문제를 일으키나?"

---

### 4.2 Phase 3 — Spring으로 서버 만들기 (Lv1.3 → Lv1.6)

**타이쿤 스터디 완료 후, 별도 프로젝트(Web Dashboard 등)에서 진행**

반드시 이해할 것:

- [ ] Controller = HTTP 어댑터, Service = 비즈니스 로직, Repository = 저장소
- [ ] new 안 쓰는 이유 (DI)
- [ ] Bean 생명주기 (개념)

**타이쿤에서 이미 준비되는 감각:**

| Spring 개념 | 타이쿬 대응 | 왜 연결되는가 |
|------------|-----------|-------------|
| Controller | `MinerListener` (이벤트 어댑터) | 외부 자극 → 내부 로직 구조 동일 |
| Service | `MinerExpService`, `EconomyService` | 비즈니스 로직 분리 동일 |
| Repository | `PlayerDataManager` | 데이터 접근 계층 분리 동일 |
| DI Container | `ServiceRegistry` (수동 DI) | 수동으로 해봤으니 자동 DI의 가치를 체감 |
| Bean 등록 | `initMinerSystem()`에서 서비스 생성 후 등록 | `@Component` 스캔의 원시 버전 |

**종료 기준:**
- "왜 컨트롤러에 로직을 넣으면 안 되는가?"
- "DI가 없으면 뭐가 불편한가?"

---

### 4.3 Phase 4 — DB & 트랜잭션 사고 (Lv1.6 → Lv1.8)

**타이쿤으로는 부족, 별도 프로젝트 필요**

반드시 알 것:

- [ ] 트랜잭션이 왜 필요한가
- [ ] JPA 영속성 컨텍스트
- [ ] read-modify-write 위험

**타이쿬에서 보이는 힌트:**
- `AbstractShop.executeBuy()`의 롤백 패턴 → 트랜잭션의 원시적 구현
- `markDirty()` → JPA dirty checking과 유사한 발상
- `EconomyService.withdraw()` → 잔액 확인 → 차감 → 실패 시 복구

**종료 기준:**
- "왜 이 로직에 트랜잭션이 필요한가?"
- "트랜잭션 없으면 어떤 버그가 생길까?"

---

### 4.4 Phase 5 — 동시성 & 서버 안정성 (Lv1.8 → Lv2)

**타이쿤에서 맛보기, 심화는 별도**

반드시 알 것:

- [ ] 서버는 기본적으로 멀티스레드
- [ ] ThreadPool 개념
- [ ] race condition 개념

**타이쿬에서 보이는 힌트:**
- `ConcurrentHashMap` vs `HashMap` 선택 이유
- `AtomicLong.incrementAndGet()` — 원자적 연산
- 네트워크 스레드 → 메인 스레드 전환 (`client.execute()`)

**종료 기준:**
- "이 코드는 동시에 호출되면 안전한가?"
- "synchronized는 왜 위험할 수 있는가?"

---

## 5. 타이쿬 STUDY_PLAN Phase별 백엔드 학습 포인트

> "뭘 공부할지"는 STUDY_PLAN.md가 진실. 여기서는 각 단계에서 **"백엔드 감각 무엇을 얻는지"**만 정리한다.

### 5.1 STUDY_PLAN 1단계: 준비 ✅ 완료

**백엔드 감각:** 빌드 도구(Maven) 사용, 프로젝트 구조 파악

### 5.2 STUDY_PLAN 2단계: 구조 감 잡기 ← 현재 진행 중

**백엔드 감각: 진입점(Entry Point) + 요청/이벤트 흐름**

| 할 일 | 백엔드 학습 포인트 |
|-------|-------------------|
| 2-1. `onEnable()` 읽기 | 서버 초기화 순서. Spring의 `@SpringBootApplication`이 왜 필요한지의 원형. |
| 2-2. 아키텍처 다이어그램 | 계층 분리(Bootstrap → Core → Feature → Integration). Spring의 레이어드 아키텍처와 동일 발상. |
| 2-3. Use-case 한 줄기 | **이벤트 → Listener → Service → Data** 흐름. Spring의 **Request → Controller → Service → Repository** 흐름과 1:1 대응. |
| 2-4. 완료 체크 | 위 흐름을 말로 설명 가능 = 백엔드 아키텍처의 기본 감각 확보. |

### 5.3 STUDY_PLAN 3단계: OOP·패턴

**백엔드 감각: 역할 분리 + OOP의 실전 의미 + 확장 가능한 구조**

| 할 일 | 백엔드 학습 포인트 |
|-------|-------------------|
| IShop → AbstractShop → MinerShop | **Template Method 패턴**. Spring에서 `AbstractController`, `JdbcTemplate` 등과 동일 발상. |
| Registry 패턴 (CodexRegistry) | **Map 기반 설정 관리**. Spring의 `BeanFactory`와 유사. |
| Factory 패턴 (LampItemFactory) | **객체 생성 캡슐화**. Spring의 `FactoryBean`과 동일. |
| DI (ServiceRegistry) | **의존성 주입의 원형**. Spring `@Autowired`가 자동화한 것을 수동으로 체험. |

**자바의 정석 병행:** ch6(OOP 기초), ch7(상속/인터페이스)

### 5.4 STUDY_PLAN 4단계: 이벤트·데이터 흐름

**백엔드 감각: 상태 관리 + 트랜잭션 사고의 시작**

| 할 일 | 백엔드 학습 포인트 |
|-------|-------------------|
| PlayerDataManager | **상태 관리**. 캐시/로드/저장. DB 없이도 "영속성"이 필요한 이유 체감. |
| EconomyService 롤백 | **트랜잭션의 원시 버전**. 잔액 확인 → 차감 → 실패 시 복구. |
| 다른 리스너 추적 | **여러 진입점이 같은 서비스를 공유**. 멀티 엔드포인트 감각. |

**자바의 정석 병행:** ch11(컬렉션), ch9(예외 처리)

### 5.5 STUDY_PLAN 5단계: 네트워크 (선택)

**백엔드 감각: 직렬화 + 프로토콜 설계 + 스레드 전환**

| 할 일 | 백엔드 학습 포인트 |
|-------|-------------------|
| ModDataService | **JSON 직렬화**. REST API의 원형 (객체 → JSON → 전송). |
| 스레드 전환 | **네트워크 스레드 vs 메인 스레드**. 서버 동시성의 시작점. |

### 5.6 STUDY_PLAN 6단계: 스킬 적용

**백엔드 감각: 설계력 검증**

| 할 일 | 백엔드 학습 포인트 |
|-------|-------------------|
| 새 기능 설계·구현 | 기존 구조에 맞춰 Listener·Service·Registry 위치 설계 = **아키텍처 사고의 실전 적용**. |

---

## 6. 타이쿬 모듈별 백엔드 매핑

> 타이쿬의 각 모듈이 백엔드 세계에서 무엇에 해당하는지.

| 타이쿬 모듈 | 위치 | 백엔드 대응 | 배우는 감각 |
|------------|------|-----------|-----------|
| TycoonPlugin | `TycoonPlugin.java` | Application Bootstrap | 서버 초기화, 의존성 그래프, 생명주기 |
| ServiceRegistry | `bootstrap/` | DI Container | 의존성 주입, 순환 참조 해결, Setter Injection |
| MinerListener 등 | `job/miner/` 등 | Controller (입력 어댑터) | 외부 이벤트 수신, 서비스로 위임 |
| MinerExpService 등 | `job/miner/` 등 | Service (비즈니스 로직) | 핵심 로직 분리, 재사용 |
| AbstractJobExpService | `job/common/` | Base Service (Template Method) | 공통 로직 추출, 확장 포인트 |
| PlayerDataManager | `player/` | Repository + Cache | 데이터 접근 계층, 캐싱, 영속성 |
| EconomyService | `economy/` | Transaction Service | 트랜잭션, 롤백, ACID 감각 |
| CodexRegistry 등 | `codex/` 등 | Configuration Store | Map 기반 설정 관리, O(1) 조회 |
| AbstractShop | `shop/` | Template Method Service | 알고리즘 공통화, 특화 포인트 분리 |
| BlockProcessingService | `enhance/processing/` | Pipeline / Chain of Responsibility | 파이프라인 패턴, 프로세서 우선순위 |
| ModDataService | `mod/` | API Layer (직렬화/통신) | JSON 프로토콜, 직렬화/역직렬화 |

---

## 7. 코드 스터디 템플릿 (고정)

파일 하나 볼 때마다 이 틀로 정리한다.

```md
## [파일명 / 클래스명]

### 1. 이 코드는 언제 실행되는가?
- (서버 시작 / 이벤트 발생 / 명령 입력 등)

### 2. 이 클래스의 책임은 무엇인가?
- 입력 처리? 비즈니스 로직? 데이터 관리?

### 3. 이 코드가 직접 하지 않는 일은 무엇인가?
- (왜 안 하는지?)

### 4. 연결된 다른 클래스는?
- 위에서 누가 부르고, 아래에서 누구를 부르는가

### 5. 이 구조가 백엔드에서 의미하는 것
- Controller / Service / Repository 중 어디?
- Spring으로 치면 뭐가 될까?

### 6. 만약 기능이 2배로 늘어나면?
- 이 구조는 버틸까? 어디를 수정해야 할까?
```

---

## 8. 자료 목록

> 타이쿬 코드가 메인. 아래 자료는 스터디 진행하면서 필요할 때 꺼내 본다.

### 우선순위 높음 (타이쿬 스터디 병행)

| 자료 | 언제 보는가 | 핵심 부분 |
|------|------------|----------|
| **자바의 정석** (남궁성) | STUDY_PLAN 3~4단계 병행 | ch6~7(OOP), ch11(컬렉션), ch9(예외) |
| **Effective Java** (Joshua Bloch) | STUDY_PLAN 3단계 이후 | Item 1~9(객체 생성/참조), Item 10~14(equals/hashCode) |

### 우선순위 중간 (타이쿬 스터디 완료 후)

| 자료 | 핵심 부분 |
|------|----------|
| **Spring 공식 가이드** (Getting Started) | Controller/Service/Repository 구조 |
| **김영한 JPA 강의** (개념 위주) | 영속성 컨텍스트, 트랜잭션 |
| **Java Performance** (Scott Oaks) 앞부분 | Stack/Heap, GC 개념 |

### 우선순위 낮음 (Lv2 도전 시)

| 자료 | 핵심 부분 |
|------|----------|
| **Java Concurrency in Practice** (Goetz) | 동시성, ThreadPool, race condition |
| **데이터 중심 애플리케이션 설계** (Kleppmann) | 트랜잭션, 분산 시스템 |
| **Spring in Action** | Spring 심화 |

### 안 해도 되는 것

- 자바의 정석 전체 통독 (필요한 챕터만 참조)
- 디자인 패턴 책 암기 (타이쿬 코드에서 실전으로 충분)
- Spring Security, MSA 등 (Lv2 이후의 영역)

---

## 9. 포트폴리오 체크리스트

> 타이쿬 코드 스터디를 "포트폴리오"로 직접 쓰지는 않지만, 여기서 배운 감각이 이후 프로젝트(Web Dashboard, 타이쿬 1.21.X 등)에서 보여야 한다.

- [ ] **계층 분리가 되어 있다**: Controller/Service/Repository 역할 분리
- [ ] **DI가 적용되어 있다**: 수동이든 Spring이든, new 직접 호출 최소화
- [ ] **상태 관리가 명확하다**: 캐시 vs 영속성 구분, dirty checking 또는 명시적 저장
- [ ] **트랜잭션 사고가 보인다**: 실패 시 롤백, 원자적 연산 사용
- [ ] **확장 가능한 구조다**: 기능 추가 시 기존 코드 수정 최소화 (OCP)
- [ ] **"왜 이렇게 설계했는지" 설명 가능**: README나 면접에서 설계 이유 서술

---

## 변경 이력

| 날짜 | 버전 | 내용 |
|------|------|------|
| 2026-02-09 | v1 | ChatGPT Lv0→Lv2 로드맵 + Claude 타이쿬 코드 매핑 기반으로 학습 설계서 초안 작성. |
