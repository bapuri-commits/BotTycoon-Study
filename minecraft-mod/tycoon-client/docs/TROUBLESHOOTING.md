# BotTycoon 모드 문제 해결 가이드

## 1. 패킷이 수신되지 않음

### 증상
- HUD가 표시되지 않음
- "서버 데이터를 기다리는 중..." 메시지가 계속 표시됨

### 확인 사항

#### 1.1 채널 ID 확인
플러그인과 모드의 채널 ID가 정확히 일치해야 합니다.

| 위치 | 값 |
|------|-----|
| 플러그인 | `tycoon:ui_data` |
| 모드 | `new Identifier("tycoon", "ui_data")` |

#### 1.2 플러그인 채널 등록 확인
서버 플러그인의 `onEnable()`에서 채널을 등록했는지 확인하세요:

```java
Messenger messenger = getServer().getMessenger();
messenger.registerOutgoingPluginChannel(this, "tycoon:ui_data");
```

#### 1.3 패킷 전송 확인
플러그인에서 실제로 `sendPluginMessage()`를 호출하는지 확인하세요.

디버그 로그 추가:
```java
getLogger().info("패킷 전송: " + type + " to " + player.getName());
player.sendPluginMessage(this, "tycoon:ui_data", bytes);
```

#### 1.4 모드 로드 확인
클라이언트에서 `/mods` 명령어로 모드가 로드되었는지 확인하세요.

### 디버깅

**모드 측 디버그 로그 활성화**:
`UiDataReceiver.java`에서 주석 해제:
```java
TycoonHudMod.LOGGER.debug("[TycoonHUD] Packet received: {}", jsonString);
```

---

## 2. JSON 파싱 오류

### 증상
- 로그에 `JsonSyntaxException` 출력
- 일부 데이터만 표시됨

### 확인 사항

#### 2.1 필드명 일치
플러그인에서 보내는 JSON 필드명과 모드의 `@SerializedName`이 일치해야 합니다.

**예시**:
```java
// 플러그인
data.addProperty("currentXp", player.getXp());

// 모드
@SerializedName("currentXp")
private long currentXp;
```

#### 2.2 타입 일치
숫자 타입에 주의하세요:

| JSON | Java 추천 |
|------|-----------|
| 정수 (작은 값) | `int` |
| 정수 (큰 값, BD 등) | `long` |
| 소수 | `float` 또는 `double` |

#### 2.3 null 처리
nullable 필드는 모드에서 null 체크를 해야 합니다:

```java
if (profile.getPrimaryJob() != null) {
    // 안전하게 사용
}
```

#### 2.4 인코딩
UTF-8 인코딩을 명시적으로 사용하세요:

```java
// 플러그인
byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

// 모드
String json = buf.readString(32767);
```

---

## 3. 스레드 관련 크래시

### 증상
- `IllegalStateException`
- 랜덤 크래시
- UI가 갱신되지 않음

### 원인
네트워크 스레드에서 직접 렌더링/UI 조작 시 발생합니다.

### 해결
**항상 메인 스레드에서 UI 작업을 수행하세요**:

```java
ClientPlayNetworking.registerGlobalReceiver(CHANNEL_ID, (client, handler, buf, responseSender) -> {
    // 네트워크 스레드 - 데이터만 읽기
    String json = buf.readString(32767);
    
    // 메인 스레드로 전환
    client.execute(() -> {
        // 여기서 데이터 처리 및 UI 업데이트
        PlayerDataManager.getInstance().setProfile(parseProfile(json));
    });
});
```

---

## 4. 스키마 버전 불일치

### 증상
- 로그에 "스키마 버전 불일치" 경고
- 일부 기능 작동 안 함

### 해결
1. 플러그인과 모드 버전을 맞추세요
2. 새 필드는 기본값을 가지도록 모드 수정

```java
if (data.getSchema() < 2) {
    // 구버전 호환성 처리
    this.newField = DEFAULT_VALUE;
}
```

---

## 5. 모드 없는 클라이언트

### 증상
플러그인에서 오류 발생 또는 의도치 않은 동작

### 해결 (플러그인 측)

```java
public boolean hasModInstalled(Player player) {
    return player.getListeningPluginChannels().contains("tycoon:ui_data");
}

// 사용
if (hasModInstalled(player)) {
    sendModPacket(player);
} else {
    // 바닐라 클라이언트용 대체 처리
    player.sendMessage("모드를 설치하면 더 나은 경험을 할 수 있습니다!");
}
```

---

## 6. 빌드 오류

### 6.1 Gradle 캐시 문제

```bash
./gradlew clean
./gradlew --refresh-dependencies build
```

### 6.2 Mixin 오류

Mixin 설정 파일이 올바른지 확인:
- `tycoon-hud.mixins.json` 위치: `src/main/resources/`
- `fabric.mod.json`에 mixin 파일 등록되었는지 확인

### 6.3 의존성 문제

`build.gradle`에서 버전 확인:
```gradle
minecraft_version=1.20.1
fabric_version=0.92.6+1.20.1
loader_version=0.18.4
```

---

## 7. 디버깅 워크플로우

```
문제 발생
    │
    ▼
패킷 수신됨? ──No──▶ 채널 ID / 플러그인 등록 확인
    │
   Yes
    ▼
파싱 성공? ──No──▶ JSON 구조 / 필드명 / 타입 확인
    │
   Yes
    ▼
렌더링 됨? ──No──▶ 스레드 확인 (client.execute())
    │
   Yes
    ▼
정상 동작
```

---

## 8. 테스트용 Mock 데이터

서버 없이 모드를 테스트하려면 가짜 데이터를 주입할 수 있습니다:

```java
// 개발 중 테스트용
if (isDevelopment()) {
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
        if (testKey.wasPressed()) {
            PlayerProfileData mock = new PlayerProfileData();
            // ... 데이터 설정
            PlayerDataManager.getInstance().setProfile(mock);
        }
    });
}
```

---

## 9. 자주 묻는 질문

### Q: HUD가 깜빡거려요
**A**: 데이터 업데이트 빈도를 낮추세요. VITAL_UPDATE는 초당 1회 이하로 권장합니다.

### Q: 통합 GUI가 안 열려요
**A**: 키바인딩 충돌을 확인하세요. 설정 → 컨트롤에서 BotTycoon 키 확인.

### Q: 바닐라 HUD가 숨겨지지 않아요
**A**: Mixin이 제대로 적용되었는지 확인하세요. 다른 모드와 충돌할 수 있습니다.

---

## 10. 로그 수집

문제 보고 시 다음 로그를 첨부해주세요:

1. **클라이언트 로그**: `.minecraft/logs/latest.log`
2. **서버 로그**: `logs/latest.log`
3. **재현 단계**: 문제 발생 전 수행한 작업

