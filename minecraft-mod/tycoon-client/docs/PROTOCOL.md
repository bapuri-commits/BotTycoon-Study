# BotTycoon 서버-클라이언트 통신 프로토콜

## 개요

이 문서는 BotTycoon 서버 플러그인과 클라이언트 모드 간의 통신 프로토콜을 정의합니다.

## 채널

| 채널 ID | 방향 | 용도 |
|---------|------|------|
| `tycoon:ui_data` | 서버 → 클라이언트 | 데이터 전송 |
| `tycoon:ui_request` | 클라이언트 → 서버 | 요청 전송 |

## 패킷 구조

모든 패킷은 UTF-8 인코딩된 JSON 문자열로 전송됩니다.

### 서버 → 클라이언트

```json
{
    "type": "PACKET_TYPE",
    "data": {
        "schema": 1,
        ...
    }
}
```

### 클라이언트 → 서버

```json
{
    "action": "ACTION_NAME",
    "params": {
        ...
    }
}
```

---

## 서버 → 클라이언트 패킷

### PLAYER_PROFILE

플레이어 프로필 전체 데이터를 전송합니다.

**트리거**: 로그인 시, 데이터 변경 시

```json
{
    "type": "PLAYER_PROFILE",
    "data": {
        "schema": 1,
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "name": "플레이어이름",
        "bd": 50000,
        "bottcoin": 25,
        "title": "칭호",
        "playtimeSeconds": 3600,
        "primaryJob": {
            "type": "MINER",
            "level": 35,
            "grade": 2,
            "gradeTitle": "숙련",
            "currentXp": 1500,
            "nextLevelXp": 3600
        },
        "secondaryJob": null,
        "currentWorld": "town",
        "plotInfo": {
            "hasOwner": true,
            "ownerName": "땅주인",
            "purchasable": false,
            "price": 0
        }
    }
}
```

**필드 설명**:

| 필드 | 타입 | 설명 |
|------|------|------|
| schema | int | 스키마 버전 (현재 1) |
| uuid | string | 플레이어 UUID |
| name | string | 플레이어 이름 |
| bd | long | BD (기본 화폐) |
| bottcoin | int | BottCoin (프리미엄 화폐) |
| title | string? | 칭호 (nullable) |
| playtimeSeconds | long | 총 플레이타임 (초) |
| primaryJob | object? | 주 직업 (nullable) |
| secondaryJob | object? | 부 직업 (nullable) |
| currentWorld | string | 현재 월드 ID |
| plotInfo | object? | 플롯 정보 (nullable) |

**직업 타입 (type)**:
- `MINER` - 광부
- `FARMER` - 농부
- `FISHER` - 어부
- `CHEF` - 요리사
- `ARTISAN` - 장인
- `ENGINEER` - 기술자

> **참고**: Job 시스템이 비활성화된 경우 `primaryJob`과 `secondaryJob`은 `null`로 전송됩니다.
> 클라이언트는 이 경우 직업 관련 UI를 숨기거나 "비활성화" 메시지를 표시해야 합니다.

**월드 ID (currentWorld)**:
- `town` - 마을
- `wild` - 야생
- `hunter` - 사냥터
- `duel` - 듀얼
- `dungeon` - 던전

---

### VITAL_UPDATE

체력/배고픔 데이터를 전송합니다.

**트리거**: 값 변경 시 (초당 최대 1회 권장)

```json
{
    "type": "VITAL_UPDATE",
    "data": {
        "schema": 1,
        "health": 17.5,
        "maxHealth": 20.0,
        "foodLevel": 18,
        "maxFoodLevel": 20,
        "saturation": 5.0
    }
}
```

---

### ECONOMY_UPDATE

화폐만 부분 업데이트합니다.

**트리거**: BD/BottCoin 변경 시

```json
{
    "type": "ECONOMY_UPDATE",
    "data": {
        "bd": 55000,
        "bottcoin": 30
    }
}
```

---

### CODEX_DATA

도감 전체 데이터를 전송합니다.

**트리거**: REQUEST_CODEX_DATA 요청 시

```json
{
    "type": "CODEX_DATA",
    "data": {
        "categories": [
            {
                "name": "광물",
                "collected": 15,
                "total": 20,
                "items": [
                    { "id": "iron_ore", "name": "철광석", "collected": true },
                    { "id": "gold_ore", "name": "금광석", "collected": false }
                ]
            }
        ],
        "totalCollected": 150,
        "totalItems": 300
    }
}
```

---

### ECONOMY_HISTORY

거래 내역을 전송합니다.

**트리거**: REQUEST_ECONOMY_HISTORY 요청 시

```json
{
    "type": "ECONOMY_HISTORY",
    "data": {
        "transactions": [
            { "time": 1703900000, "amount": 5000, "reason": "광물 판매" },
            { "time": 1703899000, "amount": -1000, "reason": "NPC 구매" }
        ]
    }
}
```

**필드 설명**:

| 필드 | 타입 | 설명 |
|------|------|------|
| time | long | Unix timestamp (초) |
| amount | long | 금액 (양수=수입, 음수=지출) |
| reason | string | 거래 사유 |

---

### JOB_DETAIL

직업 상세 정보를 전송합니다.

**트리거**: REQUEST_JOB_DETAIL 요청 시

```json
{
    "type": "JOB_DETAIL",
    "data": {
        "type": "MINER",
        "level": 35,
        "grade": 2,
        "gradeTitle": "숙련",
        "currentXp": 1500,
        "nextLevelXp": 3600,
        "promotionRequirements": [
            { "description": "레벨 40 달성", "completed": false },
            { "description": "광물 1000개 채굴", "completed": true }
        ],
        "canPromote": false
    }
}
```

---

## 실시간 업데이트 패킷 (v4 예정)

> 이 패킷들은 스키마 v4에서 정식 지원될 예정입니다.

### JOB_EXP_UPDATE

직업 경험치가 변경될 때 전송합니다.

**트리거**: 채굴/농사/낚시 등 경험치 획득 시

```json
{
    "type": "JOB_EXP_UPDATE",
    "data": {
        "jobType": "MINER",
        "level": 35,
        "currentXp": 1800,
        "nextLevelXp": 3600
    }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| jobType | string | 직업 타입 (MINER, FARMER, FISHER) |
| level | int | 현재 레벨 |
| currentXp | long | 현재 레벨 내 경험치 |
| nextLevelXp | long | 다음 레벨까지 필요한 경험치 |

---

### JOB_LEVEL_UP

직업 레벨업 시 전송합니다.

**트리거**: 레벨업 시

```json
{
    "type": "JOB_LEVEL_UP",
    "data": {
        "jobType": "MINER",
        "newLevel": 36,
        "nextLevelXp": 3800
    }
}
```

---

### JOB_GRADE_UP

직업 승급 시 전송합니다 (NPC에서 승급 완료 후).

**트리거**: 승급 완료 시

```json
{
    "type": "JOB_GRADE_UP",
    "data": {
        "jobType": "MINER",
        "newGrade": 3,
        "gradeTitle": "전문",
        "bonuses": [
            "채굴 속도 +15%",
            "희귀 광물 확률 +5%"
        ]
    }
}
```

---

### CODEX_REGISTER_RESULT

도감 등록 시도 결과를 전송합니다.

**트리거**: REGISTER_CODEX_ITEM 요청 후

```json
{
    "type": "CODEX_REGISTER_RESULT",
    "data": {
        "success": true,
        "material": "DIAMOND",
        "displayName": "다이아몬드",
        "reward": 5,
        "newCollected": 151,
        "totalCount": 300,
        "progressPercent": 50.33
    }
}
```

**실패 시**:

```json
{
    "type": "CODEX_REGISTER_RESULT",
    "data": {
        "success": false,
        "material": "DIAMOND",
        "failReason": "이미 등록된 아이템입니다"
    }
}
```

| failReason 값 | 설명 |
|---------------|------|
| already_registered | 이미 등록된 아이템 |
| item_not_found | 인벤토리에 아이템 없음 |
| not_enough_count | 필요 수량 부족 |
| not_in_codex | 도감에 없는 아이템 |
| unknown_error | 알 수 없는 오류 |

---

### CODEX_ITEM_REGISTERED

서버에서 도감 아이템이 등록되었을 때 전송합니다 (모드 UI가 아닌 서버 명령어/GUI로 등록 시).

**트리거**: 서버에서 도감 등록 완료 시

```json
{
    "type": "CODEX_ITEM_REGISTERED",
    "data": {
        "material": "DIAMOND",
        "displayName": "다이아몬드",
        "reward": 5,
        "newCollected": 151,
        "totalCount": 300,
        "progressPercent": 50.33
    }
}
```

---

## 클라이언트 → 서버 요청

### REQUEST_CODEX_DATA

도감 데이터를 요청합니다.

```json
{
    "action": "REQUEST_CODEX_DATA",
    "params": {}
}
```

---

### REQUEST_ECONOMY_HISTORY

거래 내역을 요청합니다.

```json
{
    "action": "REQUEST_ECONOMY_HISTORY",
    "params": {}
}
```

---

### REQUEST_JOB_DETAIL

직업 상세 정보를 요청합니다.

```json
{
    "action": "REQUEST_JOB_DETAIL",
    "params": {}
}
```

---

### TRIGGER_JOB_PROMOTION

직업 승급을 시도합니다.

> **참고**: LITE 버전에서는 승급이 NPC에서만 가능합니다. 이 요청은 JOB_DETAIL 재전송만 발생시킵니다.

```json
{
    "action": "TRIGGER_JOB_PROMOTION",
    "params": {}
}
```

**응답**: JOB_DETAIL 재전송

---

### REGISTER_CODEX_ITEM

도감 아이템 등록을 시도합니다.

```json
{
    "action": "REGISTER_CODEX_ITEM",
    "params": {
        "material": "DIAMOND"
    }
}
```

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| material | string | 등록할 아이템의 Material 이름 |

**응답**: CODEX_REGISTER_RESULT 패킷

---

## 플러그인 구현 가이드

### 채널 등록 (Bukkit/Spigot)

```java
// onEnable()
Messenger messenger = getServer().getMessenger();
messenger.registerOutgoingPluginChannel(this, "tycoon:ui_data");
messenger.registerIncomingPluginChannel(this, "tycoon:ui_request", this);
```

### 패킷 전송

```java
public void sendPacket(Player player, String type, JsonObject data) {
    JsonObject packet = new JsonObject();
    packet.addProperty("type", type);
    packet.add("data", data);
    
    byte[] bytes = packet.toString().getBytes(StandardCharsets.UTF_8);
    player.sendPluginMessage(plugin, "tycoon:ui_data", bytes);
}
```

### 요청 처리

```java
@Override
public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!"tycoon:ui_request".equals(channel)) return;
    
    String json = new String(message, StandardCharsets.UTF_8);
    JsonObject request = JsonParser.parseString(json).getAsJsonObject();
    String action = request.get("action").getAsString();
    
    switch (action) {
        case "REQUEST_CODEX_DATA" -> sendCodexData(player);
        case "REQUEST_ECONOMY_HISTORY" -> sendEconomyHistory(player);
        // ...
    }
}
```

---

## 스키마 버전

현재 스키마 버전: **3** (2026-02-01 기준)

### 버전 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|----------|
| 1 | 초기 | 초기 버전 |
| 2 | - | 도감 카테고리 분리 |
| 3 | 2026-01 | 업적 상세 정보 추가 |
| 4 | 예정 | 직업 실시간 업데이트, 도감 피드백 추가 |

스키마 버전이 변경되면 모드 업데이트가 필요할 수 있습니다. 하위 호환성을 위해 새 필드 추가 시에는 버전을 올리지 않고, 기존 필드 변경 시에만 버전을 올립니다.

### 클라이언트 호환성 처리

- **서버가 더 최신 (server > client)**: 경고 로그 출력, 알 수 없는 필드 무시
- **서버가 더 구버전 (server < client)**: 누락 필드에 기본값 사용

---

## 시스템 활성화 상태

서버 `config.yml`의 `systems` 섹션에서 시스템 활성화 여부를 설정할 수 있습니다.
비활성화된 시스템 관련 데이터는 null 또는 빈 값으로 전송됩니다.

**현재 비활성화된 시스템** (2026-01-05 기준):
- **Job 시스템**: `primaryJob`, `secondaryJob`가 null로 전송됨
- **Dungeon 시스템**: 던전 관련 패킷 없음
- **Chest 시스템**: 보물상자/밀수품 관련 데이터 없음
- **Refinery 시스템**: 정제소 관련 데이터 없음

**클라이언트 처리 가이드**:
1. `primaryJob == null`이면 직업 탭에 "시스템 비활성화" 메시지 표시
2. HUD에서 직업 라인 숨김
3. 직업 관련 키바인딩(J키) 요청 시 서버에서 안내 메시지 반환

