# Korean Input Fix - 학습 내용 정리

> 프로젝트를 진행하며 배운 내용을 정리하는 문서입니다.

---

## Phase 1: 프로젝트 셋업 ✅

**완료일**: 2026-01-18

### 📁 Fabric 모드 프로젝트 구조

```
src/main/
├── java/           ← 자바 소스 코드
│   └── kr/bapuri/koreanfix/   ← 패키지 경로 (도메인 역순)
│       ├── KoreanInputFixMod.java  ← 메인 클래스
│       ├── ime/      ← IME 제어 (Phase 2에서)
│       ├── mixin/    ← Mixin 클래스 (Phase 3에서)
│       └── config/   ← 설정 관련 (Phase 5에서)
└── resources/      ← 설정 파일, 에셋
    ├── fabric.mod.json           ← 모드 메타데이터
    └── korean-input-fix.mixins.json  ← Mixin 설정
```

### 📄 fabric.mod.json

**역할**: 모드의 신분증. Fabric Loader가 이 파일로 모드를 인식함.

**주요 필드**:
| 필드 | 설명 |
|------|------|
| `id` | 모드 고유 식별자 (영문 소문자, 숫자, _, - 만 허용) |
| `entrypoints.client` | 클라이언트 시작 시 실행될 클래스 |
| `mixins` | Mixin 설정 파일 경로 |
| `depends` | 필수 의존성 (Fabric, Minecraft 버전 등) |
| `environment` | `"client"`, `"server"`, `"*"` 중 택1 |

### 🔌 ModInitializer 인터페이스

Fabric 모드의 진입점(Entry Point) 정의 방식:

| 인터페이스 | 용도 | 메서드 |
|-----------|------|--------|
| `ModInitializer` | 공통 | `onInitialize()` |
| `ClientModInitializer` | 클라이언트 전용 | `onInitializeClient()` |
| `DedicatedServerModInitializer` | 서버 전용 | `onInitializeServer()` |

**우리 모드는 클라이언트 전용이므로 `ClientModInitializer` 사용**

```java
public class KoreanInputFixMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 모드 초기화 코드
    }
}
```

### 🧬 Mixin 시스템 (개념)

**Mixin이란?**
- 마인크래프트 원본 코드를 수정하지 않고 기능을 추가/변경하는 기술
- 런타임에 바이트코드 조작으로 코드 주입
- 모드끼리 호환 가능

**korean-input-fix.mixins.json 주요 필드**:
| 필드 | 설명 |
|------|------|
| `package` | Mixin 클래스들이 있는 패키지 경로 |
| `client` | 클라이언트용 Mixin 클래스 목록 |
| `compatibilityLevel` | Java 버전 호환성 |

### 📝 Logger 사용

`System.out.println()` 대신 SLF4J Logger 사용이 권장됨:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

LOGGER.info("메시지");   // 정보
LOGGER.warn("경고");     // 경고
LOGGER.error("에러");    // 에러
```

### 🛠️ Gradle 빌드

```powershell
.\gradlew build          # 빌드
.\gradlew runClient      # 마인크래프트 실행 (테스트)
```

빌드 결과물: `build/libs/korean-input-fix-1.0.0.jar`

---

## Phase 2: IME 상태 감지

**시작일**: 2026-01-18

### 📚 JNA vs JNI

자바에서 네이티브(C/C++) 코드를 호출하는 두 가지 방법:

| 항목 | JNI | JNA |
|------|-----|-----|
| 정식 명칭 | Java Native Interface | Java Native Access |
| C 코드 작성 | 필요 | **불필요** |
| DLL 컴파일 | 필요 | **불필요** |
| 복잡도 | 높음 | **낮음** |
| 적합한 경우 | 성능 중요, 복잡한 연동 | 간단한 API 호출 |

**우리가 JNA를 선택한 이유**: IME 상태 확인/제어는 간단한 Windows API 호출이므로 JNA로 충분

### ⌨️ Windows 키보드 입력 처리 흐름

```
[키보드 하드웨어] ─ 스캔코드 생성
       ↓
[Windows 커널] ─ 가상 키코드로 변환 (VK_W 등)
       ↓
[Windows 메시지] ─ WM_KEYDOWN, WM_CHAR 등 생성
       ↓
[IME] ─ ⚠️ 한글 모드면 키를 가로채서 조합 처리
       ↓
[GLFW] ─ 마인크래프트의 창/입력 관리 라이브러리
       ↓
[마인크래프트] ─ 게임 로직 처리
```

### 🔤 IME (Input Method Editor) 이해

**IME란?**
키보드로 직접 입력할 수 없는 문자(한글, 중국어 등)를 조합해서 입력하게 해주는 시스템

**한글 조합 과정**:
```
"한" 입력: ㅎ → 하 → 한 → (확정)
           ↑    ↑    ↑
        조합중 조합중 조합중 → Commit!
```

**핵심 용어**:
| 용어 | 영문 | 설명 |
|------|------|------|
| 조합 중 | Pre-edit / Composing | 아직 완성 안 된 글자 (변할 수 있음) |
| 확정 | Commit | 완성된 글자 (더 이상 안 변함) |

**IME 관련 Windows 메시지**:
| 메시지 | 발생 시점 |
|--------|----------|
| `WM_IME_STARTCOMPOSITION` | 조합 시작 |
| `WM_IME_COMPOSITION` | 조합 중 글자 변경 |
| `WM_IME_ENDCOMPOSITION` | 조합 완료 |
| `WM_CHAR` | 확정된 문자 전달 |

### 🎮 GLFW 콜백

마인크래프트는 GLFW로 입력을 받으며, 두 종류의 콜백 사용:

| 콜백 | 용도 | 사용 상황 |
|------|------|----------|
| Key Callback | 물리적 키 입력 (GLFW_KEY_W 등) | 게임 조작 (이동, 점프) |
| Char Callback | 입력된 문자 (유니코드) | 텍스트 입력 (채팅) |

### 🔴 문제 발생 원인 정리

| 문제 | 원인 | 해결 Phase |
|------|------|-----------|
| 게임 중 키 안먹힘 | IME가 키를 가로챔 | Phase 3 |
| 조합 중 글자 안보임 | Pre-edit 이벤트 무시 | Phase 4 |
| 조합 과정 중복 출력 | Pre-edit 잘못 처리 | Phase 4 |

### 🔌 JNA로 Windows DLL 함수 호출하기

**JNA(Java Native Access)**를 사용하면 C 코드 없이 순수 자바만으로 Windows DLL 함수를 호출할 수 있습니다.

#### 기본 패턴

```java
// 1. Library를 상속받는 인터페이스 정의
public interface DllName extends Library {
    // 2. DLL 로드 (싱글톤)
    DllName INSTANCE = Native.load("dll이름", DllName.class);
    
    // 3. DLL 함수를 자바 메서드로 선언
    리턴타입 함수이름(파라미터들);
}

// 4. 호출
DllName.INSTANCE.함수이름(인자들);
```

#### ⭐ 핵심 개념: 인터페이스에 구현이 없는 이유

JNA 인터페이스를 보면 메서드에 **구현(body)이 없습니다**:

```java
// 중괄호 {} 안에 코드가 없음!
WinNT.HANDLE ImmGetContext(WinDef.HWND hWnd);
boolean ImmGetOpenStatus(WinNT.HANDLE hIMC);
```

**이게 정상입니다!** JNA의 핵심 기능이에요.

```
[일반적인 자바 인터페이스]
인터페이스 선언 → 클래스에서 직접 구현해야 함

[JNA 인터페이스]
인터페이스 선언 → JNA가 자동으로 DLL 함수와 연결!
                 (우리가 구현할 필요 없음)
```

**Native.load()가 하는 일:**
1. Windows의 DLL 파일을 메모리에 로드
2. 인터페이스 메서드 이름과 DLL 함수 이름을 **자동 매칭**
3. 메서드 호출 시 → DLL 함수가 실행됨

```
Imm32.INSTANCE.ImmGetContext(hWnd)
       ↓
JNA가 중간에서 자동 변환
       ↓
imm32.dll의 ImmGetContext() 함수 실행
       ↓
결과를 자바 타입으로 변환해서 반환
```

**비유:**
- 우리가 한 것: 전화번호부 작성 ("ImmGetContext는 imm32.dll에 있어")
- JNA가 하는 것: 실제 전화 걸기 (DLL 함수 호출)

#### C 타입 → JNA 자바 타입 변환

| C 타입 (Windows) | 의미 | JNA 자바 타입 |
|------------------|------|---------------|
| `HWND` | 창(Window) 핸들 | `WinDef.HWND` |
| `HIMC` | IME 컨텍스트 핸들 | `WinNT.HANDLE` |
| `BOOL` | 참/거짓 | `boolean` |
| `DWORD` | 32비트 정수 | `int` |
| `LPCSTR` | 문자열 포인터 | `String` |

#### 우리가 사용하는 IMM32 API 함수들

| 함수 | 역할 | 비유 |
|------|------|------|
| `ImmGetContext(hWnd)` | IME 핸들 얻기 | 창의 IME 리모컨 가져오기 |
| `ImmReleaseContext(hWnd, hIMC)` | IME 핸들 해제 | 리모컨 반납 |
| `ImmGetOpenStatus(hIMC)` | IME 상태 확인 | 한글 모드인지 확인 |
| `ImmSetOpenStatus(hIMC, open)` | IME 켜기/끄기 | 한/영 전환 |

#### 리소스 관리 패턴

```java
// ImmGetContext로 얻은 핸들은 반드시 해제해야 함
HANDLE hIMC = Imm32.INSTANCE.ImmGetContext(hWnd);
try {
    // IME 조작 코드
    boolean isKorean = Imm32.INSTANCE.ImmGetOpenStatus(hIMC);
} finally {
    // 반드시 해제!
    Imm32.INSTANCE.ImmReleaseContext(hWnd, hIMC);
}
```

### 🛠️ WindowsIme 클래스 구조

최종 구현된 `WindowsIme.java`의 구조:

```
WindowsIme.java
├── Imm32 인터페이스 (imm32.dll)
│   ├── ImmGetContext()      - IME 핸들 얻기
│   ├── ImmReleaseContext()  - IME 핸들 해제
│   ├── ImmGetOpenStatus()   - 상태 확인
│   └── ImmSetOpenStatus()   - 상태 변경
│
├── User32 인터페이스 (user32.dll)
│   └── GetForegroundWindow() - 활성 창 핸들 얻기
│
└── 편의 메서드 (static)
    ├── isImeEnabled()   - 한글 모드인지 확인
    ├── setImeEnabled()  - IME 켜기/끄기
    ├── enableIme()      - IME 켜기 (단축)
    └── disableIme()     - IME 끄기 (단축)
```

#### 편의 메서드 사용법

```java
// IME 상태 확인
if (WindowsIme.isImeEnabled()) {
    System.out.println("현재 한글 모드");
}

// IME 끄기 (게임 플레이 시)
WindowsIme.disableIme();

// IME 켜기 (채팅창 열릴 때)
WindowsIme.enableIme();
```

---

## Phase 3: 화면 전환 시 IME 제어 (Mixin)

**완료일**: 2026-01-18

### 🧬 Mixin 시스템 상세 설명

#### Mixin이란?

**Mixin**은 마인크래프트 원본 코드(`.class` 파일)를 **직접 수정하지 않고** 기능을 추가하거나 변경하는 기술입니다.

**비유:**
```
원본 코드 = 요리책의 레시피
Mixin = "3단계에서 소금 대신 간장을 넣으세요"라는 메모지

→ 레시피 원본은 그대로, 메모지로 수정사항 적용
```

#### 왜 Mixin을 사용하는가?

| 방법 | 문제점 |
|------|--------|
| 원본 수정 | 마인크래프트 업데이트 시 다시 수정해야 함 |
| 상속 | 마인크래프트가 우리 클래스를 모름 |
| Mixin | ✅ 원본 유지, 업데이트 호환, 다른 모드와 공존 |

#### 바이트코드 조작 원리

자바 프로그램의 실행 흐름:
```
[.java 소스] → 컴파일 → [.class 바이트코드] → JVM 로드 → [메모리] → 실행
                              ↑
                         Mixin이 여기서 개입!
```

**Mixin의 동작:**
1. 마인크래프트 `.class` 파일이 JVM에 로드될 때
2. Mixin이 중간에서 가로채서
3. 우리 코드를 원본 바이트코드에 **삽입(Inject)**
4. 수정된 바이트코드가 JVM에 로드됨

### 📝 Mixin 핵심 어노테이션

#### @Mixin - 대상 클래스 지정

```java
@Mixin(Screen.class)  // Screen 클래스를 수정하겠다!
public class ScreenMixin {
    // ...
}
```

| 파라미터 | 설명 |
|----------|------|
| `value` | 수정할 대상 클래스 |
| `targets` | 문자열로 클래스 지정 (난독화된 이름 사용 시) |

#### @Inject - 코드 주입

```java
@Inject(method = "init", at = @At("TAIL"))
private void onScreenInit(CallbackInfo ci) {
    // Screen.init() 메서드 끝에 이 코드가 삽입됨
}
```

| 파라미터 | 설명 |
|----------|------|
| `method` | 수정할 메서드 이름 |
| `at` | 어디에 삽입할지 (@At으로 지정) |

#### @At - 삽입 위치 지정

```java
@At("HEAD")   // 메서드 시작 부분
@At("TAIL")   // 메서드 끝 (return 직전)
@At("RETURN") // return 문 위치
```

**시각화:**
```java
// 원본 Screen.init() 메서드
public void init() {
    // ← @At("HEAD") 여기
    this.clearChildren();
    this.addDrawableChild(...);
    // ← @At("TAIL") 여기
}

// Mixin 적용 후
public void init() {
    onScreenInit_HEAD();  // @At("HEAD")로 주입된 코드
    this.clearChildren();
    this.addDrawableChild(...);
    onScreenInit_TAIL();  // @At("TAIL")로 주입된 코드
}
```

#### CallbackInfo - 콜백 정보

```java
private void onScreenInit(CallbackInfo ci) {
    // CallbackInfo는 Mixin이 자동으로 전달
    // ci.cancel(); // 원본 메서드 실행 취소 가능
}
```

### 🎮 마인크래프트 Screen 시스템

#### Screen 클래스 생명주기

```
[Screen 열림]
     ↓
init() ← 화면 초기화 (위젯 추가, 레이아웃 설정)
     ↓
tick() ← 매 게임 틱마다 호출
     ↓
render() ← 매 프레임 화면 그리기
     ↓
removed() ← 화면 닫힐 때
     ↓
[Screen 닫힘]
```

#### 주요 Screen 종류

| 클래스 | 용도 | 텍스트 입력 |
|--------|------|------------|
| `ChatScreen` | 채팅창 | ✅ |
| `SignEditScreen` | 표지판 편집 | ✅ |
| `BookEditScreen` | 책 편집 | ✅ |
| `GameMenuScreen` | ESC 메뉴 | ❌ |
| `InventoryScreen` | 인벤토리 | ❌ |
| `TitleScreen` | 메인 화면 | ❌ |

### 📡 ClientTickEvents - 매 틱 이벤트

Fabric API가 제공하는 이벤트로, 매 게임 틱(1초에 20번)마다 실행됩니다.

```java
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    Screen currentScreen = client.currentScreen;
    // 매 틱마다 현재 화면 확인 가능
});
```

#### Mixin vs ClientTickEvents 비교

| 방식 | 장점 | 단점 |
|------|------|------|
| Mixin | 정확한 타이밍 (init/removed) | 복잡함, 일부 화면에서 removed 안불림 |
| ClientTickEvents | 간단, 안정적 | 약간의 지연 (1틱 = 50ms) |

**우리의 선택:** ClientTickEvents 사용
- ChatScreen이 `removed()`를 안정적으로 호출하지 않는 경우가 있어서
- 매 틱마다 `currentScreen`을 확인하는 방식이 더 안정적

### ⚠️ 시행착오 1: extends Screen 문제

#### 발생한 에러

```
// IDE 에러 (빨간 줄)
Incompatible conditional operand types ScreenMixin and ChatScreen

// 런타임 에러
Super class 'net.minecraft.client.gui.screen.Screen' of ScreenMixin 
was not found in the hierarchy of target class
```

#### 잘못된 코드

```java
@Mixin(Screen.class)
public abstract class ScreenMixin extends Screen {
    // ❌ 대상 클래스를 extends 하면 안됨!
    
    protected ScreenMixin(Text title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onScreenInit(CallbackInfo ci) {
        if (this instanceof ChatScreen) {  // ❌ 에러 발생!
            WindowsIme.enableIme();
        }
    }
}
```

#### 원인 분석

**1. Mixin 클래스는 일반 클래스가 아님**

```
[일반 상속]
ScreenMixin extends Screen
→ ScreenMixin IS-A Screen

[Mixin]
@Mixin(Screen.class) ScreenMixin
→ ScreenMixin의 코드가 Screen에 주입됨
→ ScreenMixin 자체는 Screen이 아님!
```

**2. 컴파일 시점 vs 런타임 시점**

```
컴파일 시 (IDE가 보는 것):
    ScreenMixin ≠ Screen
    ScreenMixin ≠ ChatScreen
    → instanceof 비교 불가!

런타임 시 (Mixin 적용 후):
    Screen.init() 내부에서 실행
    → this는 실제로 ChatScreen 인스턴스일 수 있음
```

**3. extends 시 문제**

```
Mixin이 기대하는 것:
    Screen 클래스에 코드를 주입

extends Screen 시:
    ScreenMixin이 Screen을 상속받으려고 시도
    → 충돌 발생!
```

#### 올바른 해결법

```java
@Mixin(Screen.class)
public class ScreenMixin {
    // ✅ extends 제거!
    // ✅ 생성자도 제거!
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onScreenInit(CallbackInfo ci) {
        // ✅ (Object) this로 캐스팅하면 런타임 타입 체크 가능
        if ((Object) this instanceof ChatScreen) {
            WindowsIme.enableIme();
        }
    }
}
```

**왜 `(Object) this`가 필요한가?**

```java
// 컴파일러 입장:
this                    // ScreenMixin 타입
this instanceof Screen  // ❌ 컴파일 에러 (다른 타입끼리 비교)

// (Object)로 캐스팅하면:
(Object) this                      // Object 타입
(Object) this instanceof Screen    // ✅ OK (Object는 모든 타입과 비교 가능)
```

**런타임에 실제로 일어나는 일:**

```
init() 메서드가 호출될 때:
1. 실제 객체는 ChatScreen 인스턴스
2. Mixin으로 주입된 코드가 실행됨
3. (Object) this는 실제로 ChatScreen
4. instanceof ChatScreen → true!
```

### ⚠️ 시행착오 2: IME 중복 호출 문제

#### 발생한 현상

```
채팅창 열기 → 한글 입력 안됨 (영문만 됨)
로그를 보니:
  "한/영 키 시뮬레이션: 한글 모드로 전환"
  "한/영 키 시뮬레이션: 한글 모드로 전환"  ← 두 번!
```

#### 원인

IME 제어를 **두 곳**에서 동시에 했습니다:

```java
// 1. ScreenMixin.java
@Inject(method = "init", at = @At("TAIL"))
private void onScreenInit(CallbackInfo ci) {
    if ((Object) this instanceof ChatScreen) {
        WindowsIme.enableIme();  // 한/영 키 1번
    }
}

// 2. KoreanInputFixMod.java
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    if (currentScreen != lastScreen) {
        if (isTextInputScreen(currentScreen)) {
            WindowsIme.enableIme();  // 한/영 키 2번
        }
    }
});
```

**결과:**
```
영문 → 한/영 키 → 한글 → 한/영 키 → 영문!
                        ↑
                    다시 영문으로 돌아감
```

#### 해결: 한 곳에서만 처리

```java
// ScreenMixin.java - 로그 전용으로 변경
@Inject(method = "init", at = @At("TAIL"))
private void onScreenInit(CallbackInfo ci) {
    // ✅ IME 제어 코드 제거, 로그만 출력
    KoreanInputFixMod.LOGGER.debug("화면 init: {}", this.getClass().getSimpleName());
}

// KoreanInputFixMod.java - 여기서만 IME 제어
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    if (currentScreen != lastScreen) {
        handleScreenChange(currentScreen);  // ✅ 여기서만 처리
    }
});
```

#### 교훈

> **하나의 기능은 한 곳에서만 제어하자!**
> 
> 여러 곳에서 같은 기능을 제어하면:
> - 타이밍 문제 발생
> - 중복 호출로 의도치 않은 결과
> - 디버깅이 어려워짐

### 🏗️ 최종 구현 구조

```
[Mixin 레이어] - 이벤트 감지만 담당
ScreenMixin.java
├── onScreenInit()   - 화면 열림 로그
└── onScreenRemoved() - 화면 닫힘 로그

[메인 모듈] - 실제 IME 제어 담당
KoreanInputFixMod.java
├── ClientTickEvents 등록
├── handleScreenChange() - 화면 변경 감지
├── isTextInputScreen() - 텍스트 입력 화면 판별
└── WindowsIme 호출

[IME 제어]
WindowsIme.java
├── enableIme()   - 한/영 키로 한글 전환
├── disableIme()  - 한/영 키로 영문 전환
└── isImeEnabled() - 현재 상태 확인
```

### ⌨️ 한/영 키 시뮬레이션

#### ImmSetOpenStatus만으로는 부족한 이유

```java
// 이렇게만 하면:
Imm32.INSTANCE.ImmSetOpenStatus(hIMC, true);

// 문제:
// - IME가 "켜지기"만 하고, 이미 영문 모드면 영문 유지
// - Windows IME는 "Open Status"와 "Conversion Mode"가 별개
```

#### 해결: 가상 키 입력

```java
// user32.dll의 keybd_event 사용
private static final byte VK_HANGUL = 0x15;  // 한/영 키 코드

private static void pressHangulKey() {
    // 키 누르기
    User32.INSTANCE.keybd_event(VK_HANGUL, (byte) 0, 0, 0);
    // 키 떼기
    User32.INSTANCE.keybd_event(VK_HANGUL, (byte) 0, KEYEVENTF_KEYUP, 0);
}
```

**왜 이 방법이 확실한가:**
- 실제 한/영 키를 누르는 것과 동일한 효과
- Windows IME가 자체적으로 상태 전환 처리
- 모든 IME에서 동작 (MS, 구글 등)

---

## Phase 4: 텍스트 필드 한글 조합 개선

**시작일**: 2026-01-18
**완료일**: 2026-01-19

### 🎯 Phase 4 목표

- 채팅창에서 한글 조합 중 글자가 화면에 표시되도록 (문제 3 해결)
- 게임 플레이 중 한/영 키 잘못 눌러도 움직임 정상 유지 (추가 개선)

### 📚 GLFW 이벤트 시스템

마인크래프트는 **GLFW**를 사용해서 창 관리와 입력을 처리합니다.

#### Key Callback vs Char Callback

```
물리 키 → [Key Callback] → 게임 조작 (WASD, 점프, ESC)
         ↓ (변환)
문자 생성 → [Char Callback] → 텍스트 입력 (채팅)
```

| 콜백 | 전달 값 | 용도 |
|------|---------|------|
| Key Callback | 물리 키 코드 (GLFW_KEY_A = 65) | 게임 조작 |
| Char Callback | 유니코드 문자 ('가' = 0xAC00) | 텍스트 입력 |

**예시:**

```
[키보드: W 키 누름]
  │
  ├→ Key Callback: GLFW_KEY_W (87)
  │     → 게임에서 앞으로 이동
  │
  └→ Char Callback: 'w' (119)
        → 채팅창에 'w' 입력
```

**한글의 경우:**

```
[키보드: ㅎ → ㅏ → ㄴ 입력]
  │
  ├→ Key Callback: H, A, N 키 (IME가 가로챔)
  │     → 게임 조작 불가!
  │
  └→ Char Callback: '한' (완성 후 한 번만)
        → 채팅창에 '한' 입력
```

### 🔤 IME 조합 과정 (Pre-edit vs Commit)

#### 상태 구분

| 상태 | 영문 용어 | 설명 |
|------|-----------|------|
| **조합 중** | Pre-edit / Composing | 아직 완성 안 된 글자 (변할 수 있음) |
| **확정** | Commit | 완성된 글자 (더 이상 안 변함) |

#### "한글" 입력 시 전체 과정

```
키 입력: ㅎ → ㅏ → ㄴ → (스페이스) → ㄱ → ㅡ → ㄹ → (엔터)

IME 상태:
  ㅎ     (조합 중)
  하     (조합 중) ← ㅏ 추가로 조합 변경
  한     (조합 중) ← ㄴ 추가로 조합 변경
  한     (확정!) ← 스페이스로 조합 종료, Char Callback에 '한' 전달
  ㄱ     (조합 중)
  그     (조합 중)
  글     (조합 중)
  글     (확정!) ← 엔터로 조합 종료
```

#### ImmGetCompositionStringW API

조합 중인 문자열을 가져오는 Windows IME API입니다.

```c
// C 원형
LONG ImmGetCompositionStringW(
    HIMC hIMC,      // IME 컨텍스트 핸들
    DWORD dwIndex,  // 가져올 정보 종류
    LPVOID lpBuf,   // 결과 저장 버퍼
    DWORD dwBufLen  // 버퍼 크기
);
```

**dwIndex 플래그:**

| 플래그 | 값 | 의미 |
|--------|------|------|
| `GCS_COMPSTR` | 0x0008 | 조합 중인 문자열 |
| `GCS_RESULTSTR` | 0x0800 | 확정된 문자열 |

**사용 패턴:**

```java
// 1. 필요한 버퍼 크기 먼저 확인 (버퍼를 null로)
int size = ImmGetCompositionStringW(hIMC, GCS_COMPSTR, null, 0);
if (size <= 0) return "";  // 조합 중 아님

// 2. 버퍼 할당 (size는 바이트, char는 2바이트)
char[] buffer = new char[size / 2];

// 3. 실제 문자열 가져오기
ImmGetCompositionStringW(hIMC, GCS_COMPSTR, buffer, size);

return new String(buffer);
```

### 🔍 TextFieldWidgetMixin 구현 과정

#### 시도 1: charTyped 메서드 후킹

**가설**: `charTyped()`가 조합 중에도 호출될 것이다

```java
@Inject(method = "charTyped", at = @At("HEAD"))
private void onCharTyped(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
    String composing = WindowsIme.getCompositionString();
    LOGGER.info("charTyped: '{}', 조합중: '{}'", chr, composing);
}
```

**결과**: ❌ 실패

```
로그 분석:
- charTyped는 문자가 "확정"될 때만 호출됨
- 조합 중에는 한 번도 호출되지 않음!
- GLFW Char Callback이 확정된 문자만 전달하기 때문
```

#### 시도 2: getText 메서드 후킹

**가설**: `getText()`가 렌더링 시 호출되니, 반환값에 조합 문자를 붙이면 될 것이다

```java
@Inject(method = "getText", at = @At("RETURN"), cancellable = true)
private void appendCompositionString(CallbackInfoReturnable<String> cir) {
    String composing = WindowsIme.getCompositionString();
    if (!composing.isEmpty()) {
        cir.setReturnValue(cir.getReturnValue() + composing);
    }
}
```

**결과**: ❌ 실패

```
로그 분석:
- getText()가 호출되고 조합 문자열도 정상 반환됨
- 그러나 화면에 표시 안됨!

원인:
- TextFieldWidget이 렌더링 시 getText() 대신 내부 text 필드를 직접 참조
- getText() 반환값을 수정해도 실제 렌더링에는 영향 없음
```

#### 시도 3: renderWidget 메서드 후킹

**가설**: 렌더링 메서드를 후킹해서 text 필드를 직접 조작

```java
@Inject(method = "renderWidget", at = @At("HEAD"))  // ← 메서드 이름 틀림!
```

**결과**: ❌ 빌드 실패

```
경고 메시지:
Cannot remap renderWidget because it does not exists in any of the targets

원인:
- TextFieldWidget에는 renderWidget이 없음
- 실제 메서드 이름은 renderButton
```

#### 시도 4: renderButton 메서드 후킹 ✅ 성공

**정확한 메서드 찾기**:
```java
// TextFieldWidget은 ClickableWidget을 상속
// 렌더링 메서드: renderButton(DrawContext, int, int, float)
```

**최종 구현**:

```java
@Mixin(TextFieldWidget.class)
public class TextFieldWidgetMixin {
    
    @Shadow
    private String text;              // 실제 텍스트 필드
    
    private String originalText = null;  // 복원용 백업

    // 렌더링 전: 조합 문자 추가
    @Inject(method = "renderButton", at = @At("HEAD"))
    private void beforeRender(DrawContext context, int mouseX, int mouseY, 
                              float delta, CallbackInfo ci) {
        String composing = WindowsIme.getCompositionString();
        if (!composing.isEmpty()) {
            this.originalText = this.text;       // 원본 백업
            this.text = this.text + composing;   // 조합 문자 추가
        }
    }

    // 렌더링 후: 원본 복원
    @Inject(method = "renderButton", at = @At("RETURN"))
    private void afterRender(DrawContext context, int mouseX, int mouseY, 
                             float delta, CallbackInfo ci) {
        if (this.originalText != null) {
            this.text = this.originalText;   // 원본 복원
            this.originalText = null;
        }
    }
}
```

**동작 원리**:

```
[렌더링 사이클 - 매 프레임]

1. beforeRender 호출
   ├─ 조합 중 문자열 가져오기 ("ㅎ")
   ├─ 원본 text 백업 ("안녕")
   └─ text 수정 ("안녕ㅎ")

2. renderButton 실제 실행
   └─ text 필드 기준으로 화면에 그림 → "안녕ㅎ" 표시!

3. afterRender 호출
   └─ text 복원 ("안녕") → 실제 데이터는 원래대로
```

#### @Shadow 어노테이션

Mixin 대상 클래스의 필드/메서드에 접근하기 위한 어노테이션입니다.

```java
@Shadow
private String text;
// → TextFieldWidget의 private String text 필드에 접근 가능
```

**규칙:**
- 필드/메서드 이름이 정확히 일치해야 함
- 타입도 일치해야 함
- private여도 접근 가능 (Mixin 마법!)

### 🎮 게임 중 한/영 키 문제 해결

#### 문제 상황

```
게임 플레이 중 (채팅창 닫힌 상태):
1. 플레이어가 실수로 한/영 키 누름
2. Windows IME가 한글 모드로 전환됨
3. WASD 키가 IME에 가로채져서 이동 불가!
```

#### 기존 코드의 한계

```java
// 화면이 바뀔 때만 실행됨
if (currentScreen != lastScreen) {
    handleScreenChange(currentScreen);  // 여기서만 disableIme()
}

// 문제: 화면 변경 없이 한/영 키만 누르면?
// → 우리 코드가 개입할 기회가 없음!
```

#### 해결: 매 틱 IME 상태 강제 유지

```java
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    Screen currentScreen = client.currentScreen;
    
    // 화면 변경 시 처리 (기존)
    if (currentScreen != lastScreen) {
        handleScreenChange(currentScreen);
        lastScreen = currentScreen;
    }
    
    // [추가] 게임 플레이 중 IME 강제 비활성화
    // 사용자가 한/영 키를 눌러도 즉시(0.05초 내) 영문으로 복구
    if (currentScreen == null && WindowsIme.isImeEnabled()) {
        WindowsIme.disableImeSilent();  // 로그 없이 조용히
    }
});
```

#### disableImeSilent 메서드

```java
// 매 틱마다 호출되므로 로그 없이 조용히 처리
public static void disableImeSilent() {
    if (isImeEnabled()) {
        pressHangulKey();  // 로그 출력 안 함
    }
}
```

**결과:**
- 게임 플레이 중 한/영 키 눌러도 0.05초(1틱) 내에 자동 복구
- WASD 이동이 항상 정상 작동

### 🔴 책/표지판 지원 (추가 작업)

#### 문제 상황

```
[TextFieldWidget 사용 여부]

ChatScreen (채팅창)           → ✅ TextFieldWidget 사용
CreativeInventoryScreen (검색) → ✅ TextFieldWidget 사용

BookEditScreen (책)           → ❌ 자체 텍스트 렌더링
SignEditScreen (표지판)        → ❌ 자체 텍스트 렌더링
```

**원인**: `TextFieldWidgetMixin`은 `TextFieldWidget`만 대상으로 함

---

### 🪧 SignEditScreenMixin 구현

**구조 분석:**
- `AbstractSignEditScreen`을 상속하는 화면
- `messages[]` 배열에 각 줄 텍스트 저장
- `currentRow`가 현재 편집 중인 줄 인덱스
- `render()` 메서드에서 직접 텍스트 렌더링

**구현 (TextFieldWidget과 동일 패턴):**

```java
@Mixin(AbstractSignEditScreen.class)
public class SignEditScreenMixin {
    
    @Shadow private String[] messages;
    @Shadow private int currentRow;
    private String originalMessage = null;

    @Inject(method = "render", at = @At("HEAD"))
    private void beforeRender(...) {
        String composing = WindowsIme.getCompositionString();
        if (!composing.isEmpty()) {
            originalMessage = messages[currentRow];
            messages[currentRow] = messages[currentRow] + composing;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void afterRender(...) {
        if (originalMessage != null) {
            messages[currentRow] = originalMessage;
            originalMessage = null;
        }
    }
}
```

**결과:** ✅ 표지판에서 조합 글자 정상 표시

---

### 📖 BookEditScreenMixin 구현 (시행착오 多)

**구조 분석:**
- `pages` 리스트에 각 페이지 텍스트 저장
- `currentPage`가 현재 페이지 인덱스
- `render()` 메서드에서 텍스트 렌더링
- **특이점:** 내부적으로 `PageContent`라는 캐시 객체 사용

#### 시도 1: getCurrentPageContent() 반환값 수정

```java
@Inject(method = "getCurrentPageContent", at = @At("RETURN"), cancellable = true)
private void appendCompositionString(CallbackInfoReturnable<String> cir) {
    String composing = WindowsIme.getCompositionString();
    if (!composing.isEmpty()) {
        cir.setReturnValue(cir.getReturnValue() + composing);
    }
}
```

**결과:** ❌ 중복 문제 발생!

**원인:** `getCurrentPageContent()`가 렌더링뿐 아니라 텍스트 저장/처리에서도 호출됨
→ 조합 문자가 실제 데이터에도 추가되어 중복

#### 시도 2: 렌더링 플래그 사용

```java
private boolean isRendering = false;

@Inject(method = "render", at = @At("HEAD"))
private void startRendering(...) { isRendering = true; }

@Inject(method = "render", at = @At("RETURN"))
private void endRendering(...) { isRendering = false; }

@Inject(method = "getCurrentPageContent", at = @At("RETURN"), cancellable = true)
private void appendCompositionString(CallbackInfoReturnable<String> cir) {
    if (!isRendering) return;  // 렌더링 중에만!
    // ... 조합 문자 추가
}
```

**결과:** ❌ 중복 해결, but 화면에 안 보임

**원인:** 더 복잡한 캐싱 메커니즘 존재

#### 시도 3: pages 직접 수정 (표지판 패턴)

```java
@Inject(method = "render", at = @At("HEAD"))
private void beforeRender(...) {
    originalContent = pages.get(currentPage);
    pages.set(currentPage, originalContent + composing);
}

@Inject(method = "render", at = @At("RETURN"))
private void afterRender(...) {
    pages.set(currentPage, originalContent);
}
```

**결과:** ❌ 로그에는 수정됨, but 화면에 안 보임

**원인 발견:**
```
BookEditScreen 렌더링 흐름:
1. pages 리스트에서 데이터 읽음
2. PageContent 객체에 캐싱 (줄바꿈 처리, 레이아웃 계산)
3. 캐시된 PageContent로 화면 그림

→ pages를 수정해도 이미 캐시된 PageContent가 렌더링됨!
```

#### 시도 4: 캐시 무효화 (최종 성공!) ✅

```java
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {

    @Shadow private List<String> pages;
    @Shadow private int currentPage;
    
    // 캐시 무효화 메서드
    @Shadow protected abstract void invalidatePageContent();

    private String originalContent = null;

    @Inject(method = "render", at = @At("HEAD"))
    private void beforeRender(...) {
        String composing = WindowsIme.getCompositionString();
        if (!composing.isEmpty()) {
            originalContent = pages.get(currentPage);
            pages.set(currentPage, originalContent + composing);
            invalidatePageContent();  // ★ 캐시 무효화!
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void afterRender(...) {
        if (originalContent != null) {
            pages.set(currentPage, originalContent);
            invalidatePageContent();  // ★ 복원 후에도 캐시 무효화
            originalContent = null;
        }
    }
}
```

**핵심 발견:**
- `invalidatePageContent()` 메서드가 `PageContent` 캐시를 무효화
- 무효화 후 다음 렌더링에서 `pages`를 다시 읽어 캐시 재생성
- 렌더링 전후에 모두 무효화해야 원본 유지됨

**결과:** ✅ 책에서 조합 글자 정상 표시

---

### 📝 Phase 4에서 배운 점

1. **메서드 이름 정확히 확인하기**
   - Yarn 매핑 기준으로 메서드 이름 확인 필요
   - 빌드 경고 메시지 주의 깊게 읽기

2. **마인크래프트 렌더링 구조**
   - 렌더링은 매 프레임(60fps) 실행
   - 위젯은 `renderButton()` 같은 메서드로 그려짐
   - 내부 필드를 직접 수정해야 렌더링에 반영

3. **GLFW Char Callback의 특성**
   - **확정된 문자만** 전달됨
   - 조합 중 상태는 전달 안 됨
   - Windows IME API로 직접 조합 상태 가져와야 함

4. **마인크래프트 화면별 특성 파악**
   - `TextFieldWidget` 사용 화면: 필드 직접 수정으로 해결
   - `SignEditScreen`: 배열 직접 수정으로 해결
   - `BookEditScreen`: **캐시 시스템** 존재 → 캐시 무효화 필요

5. **캐시 시스템 이해**
   - 일부 화면은 렌더링 성능을 위해 데이터를 캐싱
   - 데이터 수정 시 캐시 무효화가 필요한 경우가 있음
   - `invalidateXxx()` 같은 메서드를 찾아 활용

6. **시행착오의 가치**
   - TextFieldWidget: charTyped → getText → renderWidget → **renderButton**
   - BookEditScreen: getCurrentPageContent → 플래그 → pages 직접 → **캐시 무효화**
   - 실패할 때마다 시스템 이해도가 깊어짐

---

### 🏗️ Phase 4 최종 구현 구조

```
[조합 문자 표시 Mixin 구조]

TextFieldWidgetMixin (채팅, 검색 등)
├── @Shadow String text
├── beforeRender: text += composing
└── afterRender: text = original

SignEditScreenMixin (표지판)
├── @Shadow String[] messages, int currentRow
├── beforeRender: messages[currentRow] += composing
└── afterRender: messages[currentRow] = original

BookEditScreenMixin (책)
├── @Shadow List<String> pages, int currentPage
├── @Shadow abstract invalidatePageContent()
├── beforeRender: pages.set() + invalidatePageContent()
└── afterRender: pages.set() + invalidatePageContent()
```

---

## 📋 마인크래프트 텍스트 입력 상황 정리

### 현재 지원됨 ✅

| 상황 | 화면 클래스 | 처리 방식 |
|------|-------------|-----------|
| 채팅 | ChatScreen | TextFieldWidgetMixin |
| 명령어 | ChatScreen | TextFieldWidgetMixin |
| 크리에이티브 검색 | CreativeInventoryScreen | TextFieldWidgetMixin |
| 모루 이름 변경 | AnvilScreen | TextFieldWidgetMixin |
| 월드 이름 입력 | CreateWorldScreen | TextFieldWidgetMixin |
| 서버 주소 입력 | DirectConnectScreen | TextFieldWidgetMixin |
| 표지판 편집 | SignEditScreen | SignEditScreenMixin |
| 책 편집 | BookEditScreen | BookEditScreenMixin |

### 추가 검토 필요 🔍

| 상황 | 화면 클래스 | 비고 |
|------|-------------|------|
| 명령 블록 | CommandBlockScreen | TextFieldWidget 사용 가능성 높음 |
| 구조물 블록 | StructureBlockScreen | TextFieldWidget 사용 가능성 높음 |
| 지도 이름(카트로그래퍼) | MerchantScreen | 해당 없음 (선택만) |
| 멀티플레이 채팅 | 동일 | ChatScreen 사용 |

### TextFieldWidget 사용 여부로 분류

```
[TextFieldWidget 사용] → TextFieldWidgetMixin으로 자동 지원
├── 채팅창
├── 크리에이티브 검색
├── 모루 (이름 변경)
├── 월드 생성 (이름, 시드)
├── 서버 직접 연결 (주소 입력)
├── 명령 블록 (추정)
└── 구조물 블록 (추정)

[자체 텍스트 렌더링] → 별도 Mixin 필요
├── 표지판 → SignEditScreenMixin ✅
└── 책 → BookEditScreenMixin ✅
```

---

## Phase 5: 설정 및 키 바인딩

**시작일**: 2026-01-19

### 🎯 Phase 5 목표

1. **효율성 개선**: 매틱 IME 폴링 → 키 입력 이벤트 기반으로 변경
2. **설정 시스템**: JSON 파일로 설정 저장/로드
3. **모드 토글 키**: 모드 기능을 켜고 끄는 단축키

---

### ⚡ Step 1: KeyboardMixin - 효율성 개선

#### 기존 방식의 문제점

```java
// KoreanInputFixMod.java - 기존 코드
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    // 매 틱(초당 20번) 실행됨!
    if (currentScreen == null && WindowsIme.isImeEnabled()) {
        WindowsIme.disableImeSilent();  // API 호출
    }
});
```

**문제:**
- 초당 20번 Windows API 호출 (비효율적)
- 대부분의 경우 영문 모드인데도 매번 확인

#### 개선된 방식

```
[기존] 매 틱마다: "한글 모드야?" → 확인 → 영문으로
[개선] 키 입력 시에만: "게임 중이네?" → 확인 → 영문으로
```

#### 마인크래프트 키 입력 흐름

```
[키보드 하드웨어]
     ↓ 키 누름
[운영체제 (Windows)]
     ↓ 키 이벤트 생성
[GLFW 라이브러리]
     ↓ GLFW 콜백 호출
[마인크래프트 Keyboard 클래스]
     ↓ onKey() 메서드  ← 여기를 후킹!
[게임 로직]
```

#### Keyboard.onKey() 메서드

```java
// 마인크래프트의 Keyboard 클래스
private void onKey(long window, int key, int scancode, int action, int modifiers) {
    // window: GLFW 창 핸들
    // key: GLFW 키 코드 (예: GLFW_KEY_W = 87)
    // scancode: 하드웨어 스캔 코드
    // action: 0=뗌, 1=누름, 2=반복
    // modifiers: 조합키 상태 (Shift, Ctrl, Alt)
}
```

#### action 값의 의미

```java
GLFW.GLFW_RELEASE = 0  // 키 뗌
GLFW.GLFW_PRESS = 1    // 키 누름 (최초 1회)
GLFW.GLFW_REPEAT = 2   // 키 반복 (길게 누르면)
```

```
[키를 누르고 있는 동안]

action=1    action=2 action=2 action=2    action=0
(PRESS)     (REPEAT) (REPEAT) (REPEAT)    (RELEASE)
  ↓           ↓        ↓        ↓           ↓
키 누름     계속 누름  계속 누름  계속 누름    키 뗌
```

#### KeyboardMixin 구현

```java
@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // 키 누름(PRESS)이 아니면 무시
        if (action != GLFW.GLFW_PRESS) return;

        // 화면이 열려있으면 무시 (채팅, 인벤토리 등에서는 한글 허용)
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) return;

        // 게임 플레이 중 → IME가 한글 모드면 영문으로 전환
        WindowsIme.disableImeSilent();
    }
}
```

**동작 흐름:**
1. 어떤 키든 눌림 (onKey 호출)
2. PRESS인지 확인 (RELEASE, REPEAT 제외)
3. 게임 플레이 중인지 확인 (currentScreen == null)
4. 한글 모드면 영문으로 전환

#### 왜 한/영 키를 직접 감지하지 않는가?

```
[문제점]
- 한글 키보드의 한/영 키 = 전용 키 (GLFW_KEY_UNKNOWN일 수 있음)
- 일반 키보드의 한/영 = Right Alt (GLFW_KEY_RIGHT_ALT)
- 키보드마다 키 코드가 다를 수 있음

[해결책]
- 한/영 키를 직접 감지하지 않음
- 대신 "어떤 키든 눌리면" IME 상태 확인
- 게임 플레이 중 + 한글 모드 = 영문으로 전환
```

#### 효율성 비교

| 항목 | 기존 (매틱) | 개선 (키 이벤트) |
|------|------------|-----------------|
| **확인 빈도** | 초당 20회 | 키 입력 시에만 |
| **API 호출** | 항상 | 필요할 때만 |
| **처리 위치** | ClientTickEvents | KeyboardMixin |
| **반응성** | 최대 50ms 지연 | 즉시 |

#### KoreanInputFixMod 변경 사항

```java
// 제거된 코드 (비효율적)
if (currentScreen == null && WindowsIme.isImeEnabled()) {
    WindowsIme.disableImeSilent();
}

// ClientTickEvents는 화면 전환 감지용으로만 사용
// 게임 플레이 중 IME 제어는 KeyboardMixin에서 담당
```

---

### 📝 Phase 5에서 배운 점 (Step 1)

1. **이벤트 기반 vs 폴링**
   - 폴링: 주기적으로 확인 (비효율적)
   - 이벤트 기반: 발생 시에만 처리 (효율적)

2. **Keyboard 클래스 구조**
   - onKey(): 모든 키 입력의 진입점
   - action 파라미터로 누름/뗌/반복 구분

3. **GLFW 키 코드**
   - 표준 키: GLFW_KEY_W, GLFW_KEY_SPACE 등
   - 특수 키: GLFW_KEY_UNKNOWN일 수 있음

4. **disableImeSilent() 활용**
   - 내부에서 isImeEnabled() 확인
   - 외부에서 중복 확인 불필요

---

### 🔧 Step 1.5: BookEditScreenMixin 커서 위치 수정

#### 문제 상황

책에서 텍스트 중간에 커서를 두고 한글 입력 시, 조합 글자가 맨 끝에 표시됨.

```
[기존 코드]
String modified = original + composing;  // 항상 끝에 추가!

[문제]
커서가 중간에 있어도 조합 글자는 맨 끝에 표시
```

#### 해결: SelectionManager 활용

BookEditScreen은 `SelectionManager`로 커서 위치를 관리합니다.

**마인크래프트 소스 분석:**
```java
// BookEditScreen.java
private final SelectionManager currentPageSelectionManager = new SelectionManager(...);

// SelectionManager.java
private int selectionStart;
private int selectionEnd;

public int getSelectionEnd() {
    return this.selectionEnd;  // 커서 위치!
}
```

#### 수정된 코드

```java
@Shadow
private SelectionManager currentPageSelectionManager;

@Inject(method = "render", at = @At("HEAD"))
private void beforeRender(...) {
    String composing = WindowsIme.getCompositionString();
    
    if (!composing.isEmpty()) {
        String original = pages.get(currentPage);
        this.koreanfix_originalContent = original;
        
        // 커서 위치에 조합 문자 삽입
        int cursorPos = currentPageSelectionManager.getSelectionEnd();
        cursorPos = Math.min(cursorPos, original.length());
        
        String before = original.substring(0, cursorPos);
        String after = original.substring(cursorPos);
        String modified = before + composing + after;
        pages.set(currentPage, modified);
        
        invalidatePageContent();
    }
}
```

#### 학습 포인트

1. **마인크래프트 소스 확인 방법**
   - `.\gradlew genSources` 실행
   - `.gradle/loom-cache/` 에서 소스 jar 확인
   - 필드/메서드 이름이 Yarn 매핑과 일치해야 함

2. **SelectionManager 구조**
   - `selectionStart`: 선택 시작 위치
   - `selectionEnd`: 선택 끝 위치 = 커서 위치
   - `getSelectionEnd()`: getter 메서드

---

## 🎮 Phase 5: 설정 및 키 바인딩

### 5.1 목표
- 모드 설정을 저장/로드하는 시스템 구축
- 토글 키(F6)로 모드 활성화/비활성화 기능 추가

### 5.2 새로운 개념

#### 싱글톤 패턴 (Singleton Pattern)
프로그램 전체에서 **단 하나의 인스턴스**만 존재하도록 보장하는 디자인 패턴.

```java
public class ModConfig {
    // 1. static으로 유일한 인스턴스 저장
    private static ModConfig INSTANCE;
    
    // 2. private 생성자로 외부에서 new 금지
    private ModConfig() { }
    
    // 3. 접근자 메서드 - 항상 같은 인스턴스 반환
    public static ModConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();  // 최초 1회만 생성
        }
        return INSTANCE;
    }
}
```

**사용하는 이유**:
- 설정은 게임 전체에서 공유되어야 함
- 어디서든 `ModConfig.get().enabled`로 접근 가능
- 파일에서 로드한 설정을 일관되게 유지

#### Gson 라이브러리
Java 객체를 JSON으로 변환하고, JSON을 Java 객체로 변환하는 Google 라이브러리.

```java
// 객체 → JSON (직렬화)
Gson gson = new GsonBuilder().setPrettyPrinting().create();
String json = gson.toJson(config);
// 결과: { "enabled": true }

// JSON → 객체 (역직렬화)
ModConfig config = gson.fromJson(json, ModConfig.class);
```

**장점**: 필드 이름과 JSON 키가 자동 매핑됨

#### Fabric KeyBinding API
마인크래프트 키 설정 화면에 커스텀 키를 등록하는 API.

```java
// 키 바인딩 생성 및 등록
KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
    "key.koreanfix.toggle",    // 번역 키
    InputUtil.Type.KEYSYM,     // 키보드 키
    GLFW.GLFW_KEY_F6,          // 기본 키
    "category.koreanfix"       // 카테고리
));

// 매 틱마다 키 입력 확인
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    while (toggleKey.wasPressed()) {
        // 키가 눌렸을 때 동작
    }
});
```

**주의**: `wasPressed()`는 while문으로 처리해야 함 (버퍼에 쌓인 입력 모두 소비)

#### 언어 파일 (Language Files)
키 바인딩 이름을 번역하기 위한 JSON 파일.

위치: `src/main/resources/assets/<modid>/lang/`

```json
// ko_kr.json (한국어)
{
  "category.koreanfix": "한글 입력 수정",
  "key.koreanfix.toggle": "모드 On/Off 토글"
}

// en_us.json (영어)
{
  "category.koreanfix": "Korean Input Fix",
  "key.koreanfix.toggle": "Toggle Mod On/Off"
}
```

### 5.3 구현된 파일들

#### ModConfig.java
```java
package kr.bapuri.koreanfix.config;

public class ModConfig {
    private static ModConfig INSTANCE;
    
    // 설정 필드 - Gson이 자동 매핑
    public boolean enabled = true;
    
    public static ModConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }
    
    private static Path getConfigPath() {
        return FabricLoader.getInstance()
            .getConfigDir()
            .resolve("koreanfix.json");
    }
    
    private static ModConfig load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            String json = Files.readString(path);
            return new Gson().fromJson(json, ModConfig.class);
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }
    
    public void save() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(getConfigPath(), gson.toJson(this));
    }
}
```

#### KoreanInputFixMod.java (토글 키 추가)
```java
// 토글 키 등록
toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
    "key.koreanfix.toggle",
    InputUtil.Type.KEYSYM,
    GLFW.GLFW_KEY_F6,
    "category.koreanfix"
));

// 매 틱마다 처리
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    // 토글 키 처리
    while (toggleKey.wasPressed()) {
        ModConfig cfg = ModConfig.get();
        cfg.enabled = !cfg.enabled;
        cfg.save();
        
        // 상태 메시지 표시
        client.player.sendMessage(
            Text.literal("§6[Korean Fix]§r 모드가 " + status + "§r 되었습니다."),
            true  // actionBar
        );
    }
    
    // 모드 비활성화 시 아무것도 안 함
    if (!ModConfig.get().enabled) return;
    
    // ... 기존 IME 제어 로직 ...
});
```

### 5.4 동작 흐름

```
게임 시작
    ↓
ModConfig.get() 호출
    ↓
config/koreanfix.json 파일 확인
    ├─ 존재 → JSON 파싱 → 설정 로드
    └─ 없음 → 기본값으로 생성 → 파일 저장
    ↓
F6 키 누름
    ↓
enabled = !enabled
    ↓
설정 파일에 저장
    ↓
화면에 상태 메시지 표시
```

### 5.5 테스트 결과

| 기능 | 결과 |
|------|------|
| F6 토글 | ✅ 정상 작동 |
| 상태 메시지 표시 | ✅ 액션바에 표시 |
| 설정 파일 저장 | ✅ koreanfix.json 생성 |
| 키 설정 화면 | ✅ "한글 입력 수정" 카테고리 표시 |

---

## ⚡ Phase 5.5: 성능 최적화 및 코드 품질 향상

**완료일**: 2026-01-19

### 🎯 최적화 목표

1. **JNA 호출 횟수 감소** - 네이티브 호출은 비용이 높음
2. **중복 코드 제거** - 공통 로직 추출
3. **에러 처리 강화** - graceful degradation
4. **로깅 레벨 분리** - DEBUG vs INFO

---

### 📊 최적화 전후 비교

#### JNA 호출 횟수

| 메서드 | 최적화 전 | 최적화 후 | 개선율 |
|--------|----------|----------|--------|
| `getCompositionString()` | 매 프레임 (60/초) | 캐싱 (최대 62/초) | ~0% (이미 효율적) |
| `isImeEnabled()` | 매 틱 (20/초) | 캐싱 + 5틱 간격 (4/초) | **80% 감소** |

#### 코드 라인 수

| 항목 | 최적화 전 | 최적화 후 |
|------|----------|----------|
| 중복 조합 처리 로직 | 3곳에 각각 구현 | 공통 헬퍼로 통합 |
| 에러 처리 | 일부만 | 전체 적용 |

---

### 🔧 1. WindowsIme 캐싱 시스템

#### 개념: 캐싱 (Caching)

**캐싱**이란 비용이 높은 연산의 결과를 저장해두고, 같은 요청이 들어오면 저장된 값을 반환하는 기법입니다.

```
[캐싱 없음]
요청 → 계산 → 결과 → 요청 → 계산 → 결과 → 요청 → 계산 → 결과
       ↑            ↑            ↑
     매번 계산 (비효율적)

[캐싱 적용]
요청 → 계산 → 결과 저장 → 요청 → 캐시 반환 → 요청 → 캐시 반환
       ↑                    ↑                 ↑
     1번만 계산          캐시 사용         캐시 사용
```

#### 구현: 시간 기반 캐시

```java
// 캐시 관련 필드
private static String cachedCompositionString = "";
private static long lastCompositionCheck = 0;

/** 캐시 유효 시간 (밀리초) - 약 1프레임 */
private static final long COMPOSITION_CACHE_DURATION_MS = 16;

public static String getCompositionString() {
    // 1. 캐시 유효성 확인
    long now = System.currentTimeMillis();
    if (now - lastCompositionCheck < COMPOSITION_CACHE_DURATION_MS) {
        return cachedCompositionString;  // 캐시된 값 반환
    }
    
    // 2. 캐시 만료 → 실제 API 호출
    // ... JNA 호출 로직 ...
    
    // 3. 결과 캐싱
    cachedCompositionString = result;
    lastCompositionCheck = now;
    return result;
}
```

**캐시 유효 시간 설정 이유**:

| 메서드 | 유효 시간 | 이유 |
|--------|----------|------|
| `getCompositionString()` | 16ms | 60fps 기준 1프레임 |
| `isImeEnabled()` | 50ms | 20tps 기준 1틱 |

#### 캐시 무효화 (Cache Invalidation)

상태가 변경된 것이 확실할 때는 캐시를 무효화해야 합니다:

```java
public static void invalidateCache() {
    lastCompositionCheck = 0;
    lastImeStatusCheck = 0;
}

// 사용 예: IME 상태 변경 후
public static void disableIme() {
    if (isImeEnabled()) {
        pressHangulKey();
        invalidateCache();  // 상태 변경 후 캐시 무효화
    }
}
```

---

### 🔧 2. 에러 처리 강화 (Graceful Degradation)

#### 개념: Graceful Degradation

시스템에 문제가 발생해도 **완전히 멈추지 않고 제한된 기능으로 계속 동작**하는 설계 패턴입니다.

```
[Graceful Degradation 없음]
에러 발생 → 크래시 → 게임 종료

[Graceful Degradation 적용]
에러 발생 → 기본값 반환 → 게임 계속 (일부 기능 제한)
```

#### 구현: 에러 카운터

연속적인 에러가 발생하면 호출을 일시 중단합니다:

```java
private static int consecutiveErrors = 0;
private static final int MAX_CONSECUTIVE_ERRORS = 5;

public static boolean isImeEnabled() {
    // 에러가 너무 많으면 호출 중단
    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
        return false;  // 기본값 반환
    }
    
    try {
        // ... JNA 호출 ...
        consecutiveErrors = 0;  // 성공 시 리셋
        return result;
    } catch (Exception e) {
        consecutiveErrors++;
        LOGGER.debug("IME 상태 확인 중 오류 ({}회)", consecutiveErrors);
        return false;  // 기본값 반환
    }
}
```

**복구 메커니즘**:

```java
// 게임 시작, 화면 전환, 토글 키 입력 시 리셋
public static void resetErrorCounter() {
    consecutiveErrors = 0;
}
```

---

### 🔧 3. 공통 유틸리티 클래스 (CompositionHelper)

#### 리팩토링 전: 중복 코드

```java
// TextFieldWidgetMixin.java
String composing = WindowsIme.getCompositionString();
if (!composing.isEmpty()) {
    this.text = this.text + composing;
}

// SignEditScreenMixin.java
String composing = WindowsIme.getCompositionString();
if (!composing.isEmpty()) {
    this.messages[this.currentRow] = this.messages[this.currentRow] + composing;
}

// BookEditScreenMixin.java
String composing = WindowsIme.getCompositionString();
if (!composing.isEmpty()) {
    int cursorPos = currentPageSelectionManager.getSelectionEnd();
    String before = original.substring(0, cursorPos);
    String after = original.substring(cursorPos);
    // ...
}
```

#### 리팩토링 후: 공통 헬퍼

```java
// CompositionHelper.java
public class CompositionHelper {
    
    /**
     * 모드 활성화 확인 + 조합 문자열 가져오기
     */
    public static String getComposingIfEnabled() {
        if (!ModConfig.get().enabled) {
            return "";
        }
        return WindowsIme.getCompositionString();
    }
    
    /**
     * 문자열 끝에 조합 문자 추가
     */
    public static String appendComposing(String original, String composing) {
        if (composing.isEmpty()) return original;
        return original + composing;
    }
    
    /**
     * 커서 위치에 조합 문자 삽입
     */
    public static String insertAtCursor(String original, String composing, int cursorPos) {
        if (composing.isEmpty()) return original;
        int safePos = Math.max(0, Math.min(cursorPos, original.length()));
        return original.substring(0, safePos) + composing + original.substring(safePos);
    }
}
```

#### 사용 예시 (Mixin에서):

```java
// TextFieldWidgetMixin.java (리팩토링 후)
String composing = CompositionHelper.getComposingIfEnabled();
if (!composing.isEmpty()) {
    this.text = CompositionHelper.appendComposing(this.text, composing);
}
```

**장점**:
- 중복 코드 제거 → 유지보수 용이
- 모드 활성화 체크 일원화
- 범위 검증 로직 중앙화

---

### 🔧 4. IME 체크 간격 최적화

#### 문제: 매 틱 체크의 비효율성

```java
// 최적화 전: 매 틱(20/초) IME 상태 확인
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    if (currentScreen == null && WindowsIme.isImeEnabled()) {
        WindowsIme.disableImeSilent();  // 초당 20번 호출
    }
});
```

#### 해결: 간격 조절

```java
// 최적화 후: 5틱마다 확인 (4/초)
private static final int IME_CHECK_INTERVAL = 5;
private int tickCounter = 0;

ClientTickEvents.END_CLIENT_TICK.register(client -> {
    if (currentScreen == null) {
        tickCounter++;
        if (tickCounter >= IME_CHECK_INTERVAL) {
            tickCounter = 0;
            WindowsIme.disableImeSilent();  // 초당 4번으로 감소
        }
    }
});
```

**왜 완전히 제거하지 않는가?**

KeyboardMixin만으로는 불충분한 경우:
- IME가 이미 한글 모드일 때 키 입력이 GLFW에 전달되지 않을 수 있음
- 안전망으로 틱 기반 체크 유지 (단, 간격 조절)

---

### 🔧 5. 로깅 레벨 분리

#### 로그 레벨 의미

| 레벨 | 용도 | 사용자에게 표시 |
|------|------|----------------|
| `DEBUG` | 개발/디버깅 용 | 기본 설정에서 숨김 |
| `INFO` | 중요한 상태 변화 | 표시됨 |
| `WARN` | 경고 (복구 가능) | 표시됨 |
| `ERROR` | 심각한 오류 | 표시됨 |

#### 적용 예시

```java
// 최적화 전: 모든 화면 전환 로그
LOGGER.info("일반 화면 ({}) - IME 비활성화", screen.getClass().getSimpleName());
// → 로그 폭발! 인벤토리 열 때마다 출력

// 최적화 후: DEBUG로 변경
LOGGER.debug("일반 화면 ({}) - IME 비활성화", screen.getClass().getSimpleName());
// → 개발 모드에서만 표시
```

---

### 📁 변경된 파일 요약

| 파일 | 변경 내용 |
|------|----------|
| `WindowsIme.java` | 캐싱 시스템, 에러 카운터, 캐시 무효화 추가 |
| `CompositionHelper.java` | 신규 생성 - 공통 조합 처리 로직 |
| `KoreanInputFixMod.java` | 틱 간격 조절, DEBUG 로그, 에러 리셋 |
| `KeyboardMixin.java` | 정리 및 주석 개선 |
| `TextFieldWidgetMixin.java` | CompositionHelper 사용, @Unique 추가 |
| `SignEditScreenMixin.java` | CompositionHelper 사용, 범위 체크 메서드 추출 |
| `BookEditScreenMixin.java` | CompositionHelper 사용, 범위 체크 메서드 추출 |

---

### 📝 학습 포인트

1. **캐싱의 중요성**
   - 비용이 높은 연산(네이티브 호출)은 캐싱 고려
   - 캐시 유효 시간은 사용 패턴에 맞게 설정
   - 상태 변경 시 캐시 무효화 필수

2. **Graceful Degradation**
   - 에러 발생 시 기본값 반환으로 안정성 확보
   - 연속 에러 카운터로 무한 시도 방지
   - 복구 메커니즘 제공

3. **DRY 원칙 (Don't Repeat Yourself)**
   - 중복 코드는 헬퍼/유틸리티로 추출
   - 한 곳에서 수정하면 모든 곳에 적용

4. **로깅 전략**
   - 개발용 로그는 DEBUG 레벨
   - 사용자에게 의미 있는 정보만 INFO 이상

5. **최적화 우선순위**
   - 측정 가능한 병목 먼저 해결
   - 과도한 최적화는 복잡성 증가 → 균형 필요

---

### 🐛 버그 수정: 책 편집 시 Backspace 문제

#### 문제 현상

책 편집 화면에서:
1. 자음 "ㅎ" 입력 (조합 중)
2. Backspace 누름
3. "ㅎ"이 화면에서 사라지지 않음

#### 원인 분석

**1차 시도: 조합 문자열 캐싱 제거**

처음에는 `getCompositionString()` 캐싱(16ms)이 문제라고 생각했습니다.

```java
// 캐싱된 값이 반환되어 Backspace 후에도 "ㅎ"이 표시됨
if (now - lastCompositionCheck < 16) {
    return cachedCompositionString;  // 이전 값 "ㅎ" 반환
}
```

캐싱을 제거했지만 문제가 지속됨 → 다른 원인이 있음

**2차 시도: PageContent 캐시 무효화 타이밍**

실제 원인은 **BookEditScreen의 PageContent 캐시**였습니다.

```java
// 기존 코드: 조합 중일 때만 캐시 무효화
if (!composing.isEmpty()) {
    invalidatePageContent();  // 조합 중에만 호출
}

// 문제: Backspace로 조합 취소 시 composing = ""
// → invalidatePageContent() 호출 안 함
// → 이전 캐시("ㅎ" 포함)가 계속 표시됨
```

#### 해결 방법

**조합 종료 감지 플래그** 추가:

```java
@Unique
private boolean koreanfix_wasComposing = false;

@Inject(method = "render", at = @At("HEAD"))
private void koreanfix_beforeRender(...) {
    String composing = CompositionHelper.getComposingIfEnabled();
    
    if (!composing.isEmpty() && isValidPage()) {
        // 조합 중
        koreanfix_wasComposing = true;
        // ... 조합 문자 표시 ...
        invalidatePageContent();
        
    } else if (koreanfix_wasComposing) {
        // 조합이 끝남 (있었다가 없어짐)
        koreanfix_wasComposing = false;
        invalidatePageContent();  // ★ 캐시 무효화!
    }
}
```

#### 교훈

1. **상태 전이 감지**: "있다 → 없다" 변화도 감지해야 함
2. **캐시 문제 디버깅**: 값 캐싱 vs 렌더링 캐싱 구분 필요
3. **단계별 디버깅**: 한 번에 해결 안 되면 원인을 좁혀가기

---

## ✅ Phase 5 최종 완료!

### 구현된 기능

| 기능 | 상태 |
|------|:----:|
| 게임 플레이 중 IME 자동 비활성화 | ✅ |
| 채팅/표지판/책에서 한글 조합 표시 | ✅ |
| 화면 전환 시 IME 자동 on/off | ✅ |
| F6 키로 모드 토글 | ✅ |
| 설정 파일 저장/로드 | ✅ |
| 성능 최적화 (캐싱, 틱 간격) | ✅ |

### 파일 구조

```
src/main/java/kr/bapuri/koreanfix/
├── KoreanInputFixMod.java       # 메인 모드 클래스
├── config/
│   └── ModConfig.java           # 설정 저장/로드
├── ime/
│   ├── WindowsIme.java          # Windows IME 제어
│   └── CompositionHelper.java   # 조합 문자 헬퍼
└── mixin/
    ├── ScreenMixin.java         # 화면 이벤트 로깅
    ├── KeyboardMixin.java       # 키 입력 시 IME 제어
    ├── TextFieldWidgetMixin.java # 채팅 등 조합 표시
    ├── SignEditScreenMixin.java  # 표지판 조합 표시
    └── BookEditScreenMixin.java  # 책 조합 표시
```

---

*이 문서는 프로젝트 진행에 따라 업데이트됩니다.*
*마지막 업데이트: 2026-01-19*