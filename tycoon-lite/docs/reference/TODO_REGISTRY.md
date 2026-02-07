# TycoonLite TODO 레지스트리

> 이 문서는 코드 내 TODO 주석들의 의도와 해결 시점을 정리한 문서입니다.
> 
> **최종 업데이트**: 2026-01-28

---

## TODO 분류

| 분류 | 의미 | 해결 시점 |
|------|------|----------|
| `[v1.1]` | 다음 버전에서 구현 예정 | v1.1 릴리즈 |
| `[외주]` | 맵 외주 완료 후 좌표 설정 | 외주 완료 시 |
| `[SCAFFOLD]` | 의도적 미구현 (stub) | 해당 시스템 개발 시 |
| `[Phase N]` | 특정 Phase에서 구현 | 해당 Phase 진행 시 |

---

## Java 코드 TODO

### 1. Enhance 시스템

#### `LampService.java:36`
```java
// TODO: PlayerTycoonData로 이동하여 영속화
private final Map<UUID, Integer> pityCounters = new ConcurrentHashMap<>();
```
**의도**: 현재 램프 Pity 카운터가 메모리에만 저장됨. 서버 재시작 시 리셋됨.

**해결 방안**: PlayerTycoonData에 `lampPityCount` 필드 추가 후 저장/로드.

**우선순위**: v1.1 (현재는 램프 사용 빈도가 낮아 영향 적음)

---

### 2. 직업 시스템 - Fisher

#### `FisherGradeService.java:120`
```java
// TODO [v1.1]: 실제 보너스 적용
```
**의도**: 어부 등급별 보너스 (희귀도 증가 등)가 아직 적용 안됨.

**해결 방안**: `FisherListener`에서 등급별 희귀도 분포 적용.

**우선순위**: v1.1

#### `FisherExpService.java:97`
```java
// TODO [v1.1]: 물고기 종류별 통계 기록
```
**의도**: 플레이어별 물고기 종류 낚은 횟수 통계.

**해결 방안**: `PlayerTycoonData`에 `Map<String, Integer> fishCaught` 추가.

**우선순위**: v1.1 (도감/업적 연동 시)

#### `FishLootTable.java:33`
```java
// TODO [v1.1]: 희귀도별 커스텀 아이템 반환
```
**의도**: 현재 바닐라 물고기만 드랍. 커스텀 물고기 아이템 추가 예정.

**해결 방안**: Oraxen/ItemsAdder 연동 또는 자체 커스텀 아이템.

**우선순위**: v1.1

#### `PitySystem.java:41, 52, 59`
```java
// TODO [v1.1]: pityRareCount 체크 후 보장 여부 반환
// TODO [v1.1]: data.setPityRareCount(0); data.markDirty();
// TODO [v1.1]: data.addPityRareCount(1); data.markDirty();
```
**의도**: 어부 천장 시스템 (N회 낚시 후 희귀 보장). 현재 stub.

**해결 방안**: 
1. `PlayerTycoonData`에 pity 필드 추가
2. `FisherListener`에서 pity 체크 로직 구현

**우선순위**: v1.1

---

### 3. 직업 시스템 - Farmer

#### `FarmerGradeService.java:125`
```java
// TODO [v1.1]: CropGrade 시스템 구현 시 실제 보너스 적용
```
**의도**: 농부 등급별 Prime/Trophy 확률 보너스.

**해결 방안**: `FarmerListener`에서 등급별 확률 조정.

**우선순위**: v1.1 (커스텀 작물 연동 시)

---

### 4. 상점 시스템

#### `ShopService.java:29, 34, 134, 143`
```java
// <h2>메트릭스 연동 (TODO)</h2>
// return 1.0; // TODO: Phase N에서 EconomyMetricsService 연동
// [TODO: Phase N] 글로벌 판매 배율 조회
// TODO: Phase N에서 EconomyMetricsService 연동
```
**의도**: 서버 인플레이션 조절을 위한 글로벌 판매 배율 시스템.

**해결 방안**: `EconomyMetricsService` 구현 후 연동.

**우선순위**: v1.2+ (경제 안정화 필요 시)

---

### 5. Anti-Exploit 시스템

#### `XrayHeuristicAnalyzer.java:44, 58, 84, 94`
```java
// TODO: 나중에 구현
```
**의도**: X-ray 휴리스틱 탐지 시스템. 현재 scaffold.

**해결 방안**: Paper Anti-Xray와 함께 사용, 휴리스틱 분석 구현.

**우선순위**: v1.1 (악용 사례 발생 시)

#### `AntiFarmSystem.java:20, 35`
```java
// TODO: 나중에 구현
```
**의도**: 자동화 농장/몹팜 무효화 시스템.

**해결 방안**: 플레이어 기여 판정 로직 구현.

**우선순위**: v1.1 (악용 사례 발생 시)

#### `AfkDampenSystem.java:19, 32`
```java
// TODO: 나중에 구현
```
**의도**: AFK 상태 플레이어 보상 감쇠.

**해결 방안**: 이동/액션 추적 후 AFK 판정.

**우선순위**: v1.1

---

### 6. 기타

#### `CopperOxidationHandler.java:368`
```java
// 기본 월드에서 찾기 (TODO: 월드 정보도 저장 필요)
```
**의도**: 산화 구리 위치 저장 시 월드 정보 누락.

**해결 방안**: `CopperBlockData`에 worldName 필드 추가.

**우선순위**: 낮음 (현재 단일 월드 사용 시 문제 없음)

---

## config.yml TODO

### 좌표 설정 (외주 완료 후)

| 설정 | 위치 | 라인 |
|------|------|------|
| Town spawnLocation | `worlds.town.spawnLocation` | 119 |
| Casino spawnLocation | `worlds.casino.spawnLocation` | 236 |
| 야생 포탈 NPC 위치 | `npcTeleport.points.town_to_wild` | 429 |
| 카지노 포탈 NPC 위치 | `npcTeleport.points.town_to_casino` | 455 |
| 카지노 타겟 좌표 | `npcTeleport.points.town_to_casino` | 458 |
| 카지노 귀환 포탈 | `npcTeleport.points.casino_to_town` | 467 |
| 헌터 포탈 NPC 위치 | `npcTeleport.points.town_to_hunter` | 493 |
| 듀얼 아레나 1 | `duelArenas.arena1` | 1377-1380 |
| 듀얼 아레나 2 | `duelArenas.arena2` | 1384-1387 |
| 듀얼 아레나 3 | `duelArenas.arena3` | 1391-1394 |

**해결 시점**: 맵 외주 완료 후 실제 좌표로 교체

---

## SCAFFOLD 시스템 (의도적 미구현)

다음 시스템들은 **의도적으로 미구현**되었습니다:

| 시스템 | 이유 | 구현 시점 |
|--------|------|----------|
| BlameTracker | 복잡한 귀책 추적 시스템 | 악용 사례 발생 시 |
| XrayHeuristicAnalyzer | Paper Anti-Xray로 충분 | 악용 사례 발생 시 |
| AntiFarmSystem | 초기에는 불필요 | 악용 사례 발생 시 |
| AfkDampenSystem | 초기에는 불필요 | 악용 사례 발생 시 |
| RefineryService | Tier 2 이상에서 사용 | v1.1 |
| Tier2 직업 | 콘텐츠 우선순위 | v1.1 |
| 던전 시스템 | 콘텐츠 우선순위 | v1.2+ |
| 카지노 시스템 | 콘텐츠 우선순위 | v1.2+ |
| 헌터 시스템 | 콘텐츠 우선순위 | v1.2+ |

---

## TODO 추가 가이드라인

새 TODO 추가 시:

```java
// 올바른 형식
// TODO [v1.1]: 간단한 설명
// TODO [외주]: 좌표 설정 필요
// TODO [Phase 9]: 특정 Phase에서 구현

// 잘못된 형식
// TODO: 나중에 할게요  (시점 불명확)
// TODO  (설명 없음)
```

---

## 참고 문서

- [LITE_MASTER_TRACKER.md](../planning/LITE_MASTER_TRACKER.md) - 전체 Phase 진행 상황
- [CHANGELOG.md](../../CHANGELOG.md) - 버전별 변경 이력
- [CONFIG_GUIDE.md](./CONFIG_GUIDE.md) - 설정 파일 가이드
