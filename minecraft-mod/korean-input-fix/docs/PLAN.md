# Korean Input Fix - 개발 플랜

> 이 문서는 프로젝트 진행 계획과 학습 방향을 정리합니다.

---

## 📋 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 프로젝트명 | Korean Input Fix |
| 목적 | 마인크래프트에서 한글 IME 입력 문제 해결 |
| 타입 | Fabric 클라이언트 모드 |
| Minecraft 버전 | 1.20.1 |
| Java 버전 | 17 |

---

## 🎯 해결할 문제

1. **게임 플레이 중 키 안먹힘** - IME가 키를 가로채서 WASD 등이 작동 안 함
2. **채팅창 한글 조합 깨짐** - 초성/중성/종성이 따로 나오거나 중복 입력
3. **조합 중 글자 안보임** - 글자가 완성될 때까지 화면에 표시되지 않음
4. **한/영 전환 키 충돌** - 게임 키 바인딩과 충돌

---

## 👤 진행 방식

### 사용자 수준
- 자바 중급자 (기본 문법 숙지, 간단한 프로젝트 경험)
- 마인크래프트 모딩은 처음

### AI 역할
- **가이드 모드**: 방향과 힌트 제공, 핵심 코드는 직접 작성
- **페어 프로그래밍**: 복잡한 부분은 함께 진행
- 반복 작업도 처음 1-2회는 직접 경험

### 문서화
- 각 Phase에서 배운 내용을 `docs/LEARNING.md`에 정리
- 파일 생성 시 해당 파일의 필요성과 개념 설명 포함

---

## 📚 학습 목표

- [x] 자바 기초 문법과 OOP 개념
- [x] Mixin 시스템과 바이트코드 조작 원리
- [x] JNI/JNA를 통한 네이티브 코드 연동
- [x] 마인크래프트 내부 구조와 이벤트 시스템
- [x] 프로젝트 구조화와 빌드 시스템 (Gradle)

---

## 🗓️ 개발 단계

### Phase 1: 프로젝트 셋업 ✅ 완료
**상태**: 완료 (2026-01-18)

**할 일:**
1. Fabric 모드 기본 구조 생성 (src/main/java, resources)
2. `fabric.mod.json` 작성 (모드 메타데이터)
3. 메인 모드 클래스 작성 (ModInitializer)
4. Mixin 설정 파일 작성
5. 빌드 테스트

**학습 포인트:**
- Fabric 모드 프로젝트 구조
- fabric.mod.json의 각 필드 의미
- ModInitializer 인터페이스
- Mixin 설정 구조

---

### Phase 2: IME 상태 감지 (JNI/JNA) ✅ 완료
**상태**: 완료 (2026-01-18)

**완료 항목:**
1. ✅ JNA 라이브러리 의존성 추가 (build.gradle)
2. ✅ Windows IMM32 API 래퍼 클래스 작성 (WindowsIme.java)
3. ✅ IME 상태 확인 메서드 구현 (isImeEnabled)
4. ✅ IME 활성화/비활성화 메서드 구현 (setImeEnabled, enableIme, disableIme)

**학습 포인트:**
- JNA vs JNI 차이점
- Windows API 호출 방법
- 네이티브 코드와 자바 연동
- 리소스 관리 패턴 (try-finally)

---

### Phase 3: 화면 전환 시 IME 제어 (Mixin) ✅ 완료
**상태**: 완료 (2026-01-18)

**완료 항목:**
1. ✅ ScreenMixin 작성 - 화면 열림/닫힘 이벤트 감지
2. ✅ ClientTickEvents로 화면 상태 변화 감지 (ChatScreenMixin 대신 통합 처리)
3. ✅ 텍스트 입력 화면 판별 로직 (ChatScreen, SignEditScreen, BookEditScreen)
4. ✅ IME 자동 on/off 연동 (한/영 키 시뮬레이션)

**주요 시행착오:**
- `extends Screen` 문제 → `(Object) this` 캐스팅으로 해결
- IME 중복 호출 문제 → ClientTickEvents에서만 처리하도록 단일화

**학습 포인트:**
- Mixin 원리와 동작 방식 (@Mixin, @Inject, @At)
- 바이트코드 조작 개념
- 마인크래프트 Screen 시스템 (생명주기)
- ClientTickEvents 이벤트 사용법

---

### Phase 4: 텍스트 필드 한글 조합 개선 ✅ 완료
**상태**: 완료 (2026-01-18 ~ 2026-01-19)
**목표**: 채팅창 등에서 한글이 정상적으로 입력되도록 (조합 중 글자 표시 + 중복 출력 방지)

**완료 항목:**
1. ✅ GLFW 문자 콜백 개념 학습 (Key Callback vs Char Callback)
2. ✅ IME 조합 과정 이해 (Pre-edit vs Commit)
3. ✅ ImmGetCompositionStringW API 래핑 (WindowsIme.java)
4. ✅ TextFieldWidgetMixin 작성 - renderButton 후킹으로 조합 중 글자 표시
5. ✅ 게임 중 IME 강제 비활성화 (한/영 키 문제 해결)
6. ✅ SignEditScreenMixin 작성 - 표지판에서 조합 글자 표시
7. ✅ BookEditScreenMixin 작성 - 책에서 조합 글자 표시

**주요 시행착오:**

*TextFieldWidgetMixin:*
- `charTyped` 후킹 시도 → 확정된 문자만 전달되어 실패
- `getText` 후킹 시도 → TextFieldWidget이 필드 직접 참조하여 실패
- `renderWidget` 후킹 시도 → 메서드명 틀려서 빌드 실패
- `renderButton` 후킹 → ✅ 성공! text 필드 직접 조작

*BookEditScreenMixin:*
- `getCurrentPageContent` 반환값 수정 → 저장 시에도 적용되어 중복 발생
- 렌더링 플래그 사용 → 캐시 때문에 화면 반영 안 됨
- `pages` 직접 수정 → 캐시 때문에 화면 반영 안 됨
- `pages` 수정 + `invalidatePageContent()` → ✅ 성공! 캐시 무효화로 해결

**학습 포인트:**
- GLFW 이벤트 시스템 (Key Callback vs Char Callback)
- IME 조합 과정 (Pre-edit/Composing vs Commit)
- TextFieldWidget 렌더링 구조
- @Shadow 어노테이션으로 필드/메서드 접근
- 마인크래프트 화면별 캐싱 메커니즘 이해
- 캐시 무효화 패턴 (invalidatePageContent)

---

### Phase 5: 설정 및 키 바인딩 ✅ 완료
**상태**: 완료 (2026-01-19)
**목표**: 사용자 설정 가능한 기능 제공

**완료 항목:**
1. ✅ Fabric KeyBinding API로 토글 키(F6) 등록
2. ✅ ModConfig 클래스 작성 - 싱글톤 패턴으로 설정 관리
3. ✅ Gson으로 JSON 파일 저장/로드 (config/koreanfix.json)
4. ✅ 언어 파일 작성 (ko_kr.json, en_us.json)
5. ✅ KeyboardMixin에 enabled 체크 추가

**학습 포인트:**
- 싱글톤 패턴 (Singleton Pattern)
- Gson 라이브러리로 JSON 처리
- Fabric KeyBinding API
- 언어 파일 (assets/modid/lang/)

---

## 📁 목표 파일 구조

```
korean-input-fix/
├── src/main/
│   ├── java/kr/bapuri/koreanfix/
│   │   ├── KoreanInputFixMod.java      # 메인 모드 클래스
│   │   ├── ime/
│   │   │   ├── ImeController.java      # IME 제어 로직
│   │   │   └── WindowsIme.java         # Windows API 래퍼
│   │   ├── mixin/
│   │   │   ├── ChatScreenMixin.java
│   │   │   ├── ScreenMixin.java
│   │   │   └── TextFieldWidgetMixin.java
│   │   └── config/
│   │       └── ModConfig.java
│   └── resources/
│       ├── fabric.mod.json
│       └── korean-input-fix.mixins.json
├── docs/
│   ├── PLAN.md                         # 이 문서
│   └── LEARNING.md                     # 학습 내용 정리
├── build.gradle
├── gradle.properties
├── settings.gradle
└── DESIGN.md                           # 설계 문서
```

---

## 📝 진행 기록

| 날짜 | 작업 내용 |
|------|----------|
| 2026-01-18 | 개발 플랜 수립, .cursorrules 생성 |
| 2026-01-18 | Phase 1 완료: 프로젝트 구조, fabric.mod.json, 메인 클래스, Mixin 설정 |
| 2026-01-18 | Phase 2 시작: JNA/JNI 개념 학습, IME 동작 원리 이해, 문제 3 추가 |
| 2026-01-18 | Phase 2 완료: WindowsIme.java 작성 (JNA로 IMM32 API 래핑) |
| 2026-01-18 | Phase 3 완료: ScreenMixin, ClientTickEvents로 IME 자동 제어 구현 |
| 2026-01-18 | Phase 4 시작: GLFW/IME 개념 학습, ImmGetCompositionStringW 래핑 |
| 2026-01-19 | Phase 4 진행: TextFieldWidgetMixin 작성 (renderButton 후킹으로 조합 글자 표시) |
| 2026-01-19 | 추가 개선: 게임 중 IME 강제 비활성화 (한/영 키 문제 해결) |
| 2026-01-19 | SignEditScreenMixin 작성: 표지판에서 조합 글자 표시 |
| 2026-01-19 | BookEditScreenMixin 작성: 책에서 조합 글자 표시 (캐시 무효화로 해결) |
| 2026-01-19 | Phase 4 완료: 모든 텍스트 입력 상황에서 한글 조합 글자 표시 지원 |
| 2026-01-19 | Phase 5 시작: 설정 및 키 바인딩 구현 |
| 2026-01-19 | KeyboardMixin 작성: 키 입력 시 IME 제어 (효율성 개선) |
| 2026-01-19 | ModConfig 클래스 작성: 싱글톤 패턴 + Gson JSON 저장/로드 |
| 2026-01-19 | 토글 키(F6) 등록: Fabric KeyBinding API 사용 |
| 2026-01-19 | Phase 5 완료: 모드 설정 저장/로드 및 토글 키 구현 |
| 2026-01-19 | 성능 최적화: JNA 호출 캐싱, 틱 간격 조절, CompositionHelper 추출 |
| 2026-01-19 | 버그 수정: 책 편집 시 Backspace로 조합 취소 시 화면 갱신 문제 해결 |
| 2026-01-19 | **Phase 5 최종 완료!** 모든 기능 테스트 통과 ✅ |

---

*이 문서는 프로젝트 진행에 따라 업데이트됩니다.*
*마지막 업데이트: 2026-01-19*