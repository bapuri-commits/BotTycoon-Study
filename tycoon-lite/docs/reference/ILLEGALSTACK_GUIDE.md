# IllegalStack 설정 가이드

> **목적**: IllegalStack 플러그인 설정 및 TycoonLite 커스텀 아이템 호환성 확보
> **대상**: 서버 운영자
> **최종 수정**: 2026-01-27

---

## 개요

IllegalStack은 다음을 방지하는 안티-익스플로잇 플러그인입니다:
- 불법 스택 (64개 초과 등)
- NBT 익스플로잇
- 복제 버그
- 불법 인챈트

**주의**: TycoonLite 커스텀 아이템(CustomModelData, PDC)이 오탐될 수 있습니다.

---

## 설정 파일 위치

```
plugins/IllegalStack/config.yml
```

---

## 현재 서버 설정 요약

서버에 적용된 주요 설정:

### 활성화된 보호 기능

| 기능 | 상태 | 설명 |
|------|------|------|
| 오버스택 아이템 제거 | ✅ | 64개 초과 스택 제거 |
| 불법 인챈트 검사 | ✅ | 비정상 인챈트 레벨 감지 |
| 침대 폭발 방지 | ✅ | 네더/엔드 침대 폭발 방지 |
| 레일 복제 방지 | ✅ | 레일 듀플리케이션 차단 |
| TNT 복제 방지 | ✅ | 간접 TNT 파워 듀프 차단 |
| 포탈 복제 방지 | ✅ | 네더/엔드 포탈 듀프 차단 |
| 네더 천장 차단 | ✅ | 128 이상 이동/건설 차단 |
| 주민 거래 치팅 차단 | ✅ | 리스톡 시간 10분 |
| 셜커 중첩 방지 | ✅ | 셜커 안의 셜커 차단 |

### 오버스택 허용 아이템

현재 `POTION`만 허용:

```yaml
Exploits:
  OverStack:
    AllowStack:
    - POTION
```

---

## TycoonLite 커스텀 아이템 호환성

### 오탐 가능 시나리오

1. **인챈트 검사**: 커스텀 인챈트가 "불법"으로 감지될 수 있음
2. **NBT 검사**: PDC 데이터가 많은 아이템이 "해킹됨"으로 감지될 수 있음
3. **이름/로어 검사**: 특수 문자가 있는 커스텀 아이템 이름

### 화이트리스트 설정 방법

#### 1. 인챈트 화이트리스트

커스텀 인챈트 아이템을 허용하려면:

```yaml
Exploits:
  Enchants:
    EnchantedItemWhitelist:
      - DIAMOND_PICKAXE  # 특정 아이템 타입 허용
    CustomEnchantOverride:
      - "tycoon:efficiency_boost"  # 커스텀 인챈트 이름
```

#### 2. 오버스택 화이트리스트

커스텀 아이템을 오버스택 허용하려면:

```yaml
Exploits:
  OverStack:
    AllowStack:
    - POTION
    - PAPER  # 커스텀 문서류
    - CLOCK  # 커스텀 시계류
```

#### 3. 특정 월드에서 비활성화

테스트 월드에서 IllegalStack 비활성화:

```yaml
Misc:
  DisableInWorlds:
    - "test_world"
    - "creative"
```

#### 4. 이름/로어 기반 제외

특정 이름을 가진 아이템 검사 제외 (현재 미사용):

```yaml
UserRequested:
  ItemRemoval:
    ItemNamesToRemove: []
    RemoveItemsMatchingName: false  # false로 유지
```

---

## 권장 설정 변경

### TycoonLite 호환을 위한 최소 변경

현재 설정에서 오탐 이슈가 발생하면 다음을 추가:

```yaml
Exploits:
  Enchants:
    AllowBypass: false  # 유지 (권한 우회 차단)
    EnchantedItemWhitelist: []  # 필요 시 아이템 추가
```

### 모니터링 권장

오탐 발생 시 로그 확인:

```
plugins/IllegalStack/offenses.log
```

형식:
```
[시간] 플레이어명: 아이템 설명 - 조치
```

---

## TycoonLite 연동 (향후)

현재는 코드 연동 없이 설정으로만 관리합니다.

향후 필요 시 구현 가능:
- TycoonLite 시작 시 IllegalStack에 커스텀 아이템 자동 등록
- CoreItemType별 화이트리스트 동기화

---

## 문제 해결

### 커스텀 아이템이 삭제되는 경우

1. `offenses.log` 확인
2. 삭제된 아이템의 Material 타입 확인
3. 해당 타입을 `EnchantedItemWhitelist` 또는 `AllowStack`에 추가
4. `/istack reload` 실행

### 로그 확인 명령어

```
/istack debug
/istack reload
```

---

## 참고 자료

- [IllegalStack 공식 문서](https://www.spigotmc.org/resources/illegalstack.44411/)
- [IllegalStack GitHub](https://github.com/dniym/IllegalStack)

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-01-27 | 초기 작성 (Phase 3.C.5) |
