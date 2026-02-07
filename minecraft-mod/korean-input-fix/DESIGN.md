# Korean Input Fix - 프로젝트 설계 문서

> 마인크래프트에서 한글 IME 입력 문제를 해결하는 Fabric 클라이언트 모드

---

## 🎯 프로젝트 목적

이 모드는 마인크래프트에서 발생하는 **한글 입력 관련 문제**들을 해결하기 위해 개발됩니다.

---

## 🔴 해결하려는 문제

### 문제 1: 게임 플레이 중 키가 안 먹힘

**현상:**
- 한/영 전환 후 한글 모드 상태에서 게임을 플레이하면
- WASD 이동, Space 점프, Shift 웅크리기 등이 전혀 작동하지 않음

**원인:**
- Windows IME(한글 입력기)가 키보드 입력을 가로채서 한글 조합을 시도함
- 게임은 키 입력을 받지 못하고, IME는 "ㅈ", "ㅁ", "ㄴ" 등의 한글 초성을 조합하려 함

**예시:**
```
[키 입력] W키 누름
[정상 동작] 캐릭터가 앞으로 이동
[문제 상황] 아무 반응 없음 (IME가 "ㅈ"을 조합 중)
```

---

### 문제 2: 채팅창에서 한글 조합이 깨짐

**현상:**
- 채팅창에 한글을 입력할 때 글자가 이상하게 입력됨
- 초성/중성/종성이 따로 나오거나, 중복 입력되거나, 글자가 씹힘

**원인:**
- 마인크래프트의 텍스트 입력 시스템이 IME의 "조합 중" 상태를 제대로 처리하지 못함
- Pre-edit(조합 중인 문자)와 Commit(확정된 문자)를 구분하지 못함

**예시:**
```
[입력 시도] "한글" 입력
[정상 동작] 한글
[문제 상황] ㅎ하한ㄱ글 (조합 과정이 그대로 출력됨)
```

---

### 문제 3: 조합 중인 글자가 보이지 않음

**현상:**
- 채팅창에 한글을 입력할 때, 글자가 완성될 때까지 아무것도 안 보임
- "한"을 입력하면 ㅎ, 하 단계가 안 보이고 완성 후에야 "한"이 나타남

**원인:**
- 마인크래프트가 IME의 Pre-edit(조합 중) 이벤트를 무시함
- Commit(확정)된 문자만 받아서 표시
- 문제 2와 근본 원인이 같음 (IME 조합 이벤트 미처리)

**예시:**
```
[정상 동작] ㅎ → 하 → 한 (조합 과정이 실시간으로 보임)
[문제 상황] (안보임) → (안보임) → 한 (갑자기 나타남)
```

---

### 문제 4: 한/영 전환 키 충돌

**현상:**
- 게임 내에서 한/영 전환이 제대로 안 됨
- 전환 키가 게임의 다른 기능과 충돌함

**원인:**
- Windows의 한/영 키(Right Alt 또는 한/영 키)가 게임 키 바인딩과 충돌
- 게임이 키를 먼저 처리해버리거나, OS가 키를 가로채버림

---

## 📚 기술적 배경

### IME(Input Method Editor)란?

IME는 키보드로 직접 입력할 수 없는 문자를 입력하기 위한 시스템입니다.

한글은 자음과 모음을 조합해서 글자를 만들기 때문에, 단순히 키 하나 = 글자 하나가 아닙니다.

```
한글 "한" 입력 과정:

  ㅎ   →   하   →   한
  ↑       ↑       ↑
 초성    중성    종성
 (ㅎ)   (+ㅏ)   (+ㄴ)

이 과정에서 "ㅎ"와 "하"는 아직 완성되지 않은 "조합 중" 상태입니다.
마지막 "한"이 되어야 비로소 "확정"됩니다.
```

**핵심 개념:**
| 용어 | 영문 | 설명 |
|------|------|------|
| 조합 중 | Pre-edit / Composing | 아직 완성되지 않은 문자 (계속 변할 수 있음) |
| 확정 | Commit | 완성된 문자 (더 이상 변하지 않음) |

---

### 마인크래프트 입력 흐름

마인크래프트는 GLFW라는 라이브러리를 통해 키보드 입력을 받습니다.

```
[키보드] → [Windows] → [IME] → [GLFW] → [Minecraft]

문제 발생 지점:
1. IME가 키를 가로채서 게임에 전달하지 않음
2. 게임이 IME의 조합 상태를 이해하지 못함
```

---

### 수정이 필요한 마인크래프트 클래스

| 클래스 | 역할 | 수정 목적 |
|--------|------|----------|
| `Keyboard` | 키보드 입력 총괄 | IME 상태 감지 |
| `KeyboardHandler` | 키 이벤트 처리 | 게임플레이 시 IME 제어 |
| `TextFieldWidget` | 텍스트 입력 위젯 | 한글 조합 처리 |
| `ChatScreen` | 채팅 화면 | 화면 열림/닫힘 시 IME 전환 |
| `Screen` | 모든 화면의 부모 | 화면 전환 감지 |

---

## 💡 해결 전략

### 전략 1: 상황별 IME 자동 제어

게임 상황에 따라 IME를 자동으로 켜고 끄는 방식입니다.

```
현재 상태               →  IME 동작
─────────────────────────────────────
게임 플레이 중 (화면 없음)  →  IME 끔 (키 입력 정상화)
채팅창 열림               →  IME 켬 (한글 입력 가능)
인벤토리 열림             →  IME 끔 (키 입력 필요)
표지판 편집 중            →  IME 켬 (한글 입력 가능)
책 편집 중               →  IME 켬 (한글 입력 가능)
```

**구현 방법:**
- Mixin으로 화면(Screen) 열림/닫힘 이벤트 감지
- Windows IMM32 API로 IME 상태 제어
- JNI(Java Native Interface) 사용

---

### 전략 2: 텍스트 필드 한글 조합 개선

텍스트 입력 위젯이 IME 조합 상태를 제대로 처리하도록 수정합니다.

```
기존 동작 (문제):
  키 입력 → 바로 문자 삽입 → "ㅎ하한" 이런 식으로 출력

개선 동작:
  키 입력 → 조합 중인지 확인 → 조합 중이면 이전 조합 문자 교체
                            → 확정이면 문자 삽입
```

**구현 방법:**
- TextFieldWidget Mixin으로 charTyped 메서드 후킹
- 조합 중인 문자열을 별도로 추적
- 조합 완료 시점에만 최종 문자 삽입

---

### 전략 3: 커스텀 한/영 전환 키

사용자가 원하는 키로 한/영 전환을 할 수 있게 합니다.

```
기본 설정: ` (백틱) 키로 한/영 전환
사용자 설정 가능: 설정 화면에서 변경
```

**구현 방법:**
- Fabric KeyBinding API 사용
- 설정 파일(JSON)로 저장/로드

---

## 🔧 Mixin 사용 계획

Mixin은 마인크래프트의 기존 코드를 수정하지 않고 기능을 추가하는 기술입니다.

### 필요한 Mixin 목록

| Mixin | 대상 클래스 | 하는 일 |
|-------|------------|--------|
| KeyboardMixin | Keyboard | 키 입력 시 IME 상태 확인 |
| TextFieldWidgetMixin | TextFieldWidget | 한글 조합 처리 개선 |
| ChatScreenMixin | ChatScreen | 채팅창 열림/닫힘 시 IME 제어 |
| ScreenMixin | Screen | 모든 화면 전환 감지 |

### Mixin 예시 (참고용)

```java
// 채팅창이 열릴 때 IME 활성화하는 예시
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onChatOpen(CallbackInfo ci) {
        // 채팅창 열림 → IME 켜기
        ImeController.enable();
    }
    
    @Inject(method = "removed", at = @At("HEAD"))
    private void onChatClose(CallbackInfo ci) {
        // 채팅창 닫힘 → IME 끄기
        ImeController.disable();
    }
}
```

---

## 📦 필요한 기술/라이브러리

| 기술 | 용도 |
|------|------|
| Fabric API | 마인크래프트 모딩 프레임워크 |
| Mixin | 게임 코드 수정 |
| JNI/JNA | Windows IME API 호출 |
| GLFW | 키보드/문자 입력 이벤트 |

---

## 📋 구현 단계

### Phase 1: 프로젝트 셋업
- [x] Gradle 프로젝트 생성
- [x] 설계 문서 작성
- [ ] 빌드 환경 테스트

### Phase 2: IME 상태 감지
- Windows IMM32 API 연동
- 현재 IME 상태(한글/영문) 확인 기능
- JNI 네이티브 코드 작성

### Phase 3: 화면 전환 시 IME 제어
- Screen Mixin으로 화면 열림/닫힘 감지
- 채팅창, 표지판 등 텍스트 입력 화면 판별
- 상황에 맞게 IME 자동 on/off

### Phase 4: 텍스트 필드 한글 조합 개선
- TextFieldWidget Mixin 작성
- Pre-edit 상태 추적
- 조합 완료 시 문자 확정

### Phase 5: 설정 및 키 바인딩
- Mod Menu 연동 설정 화면
- 커스텀 한/영 전환 키
- 설정 파일 저장/로드

---

## 🔗 참고 자료

### 유사 모드
- **Korean Chat Patch** - https://modrinth.com/mod/korean-chat-patch
- **IMBlocker** - https://www.curseforge.com/minecraft/mc-mods/imblocker
- **caramelChat** - https://modrinth.com/mod/caramel-chat

### 공식 문서
- Fabric Wiki - https://fabricmc.net/wiki/
- Mixin 문서 - https://github.com/SpongePowered/Mixin/wiki
- Windows IMM32 API - https://docs.microsoft.com/en-us/windows/win32/intl/input-method-manager

---

## 📝 용어 정리

| 용어 | 설명 |
|------|------|
| IME | Input Method Editor. 한글 입력기 |
| Pre-edit | 조합 중인 문자 (아직 완성 안 됨) |
| Commit | 확정된 문자 (완성됨) |
| Mixin | 런타임에 클래스를 수정하는 기술 |
| GLFW | 마인크래프트가 사용하는 입력 라이브러리 |
| JNI | Java에서 네이티브(C/C++) 코드를 호출하는 기술 |

---

*작성일: 2026-01-17*
