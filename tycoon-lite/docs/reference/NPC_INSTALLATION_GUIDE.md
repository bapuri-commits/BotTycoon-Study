# NPC 설치 가이드

> **작성일**: 2026-01-28  
> **대상**: TycoonLite v1.0  
> **필수 플러그인**: Citizens

---

## 개요

TycoonLite는 Citizens 플러그인과 연동하여 NPC 기반 인터페이스를 제공합니다.  
일부 NPC는 **자동 생성**되고, 일부는 **수동 설치**가 필요합니다.

---

## NPC 분류

| NPC 종류 | 생성 방식 | 위치 |
|---------|---------|------|
| 귀환 NPC (Wild→Town) | **자동** | Wild 스폰 포인트 |
| 강화 NPC | **수동** | Town |
| Recovery 보관소 NPC | **수동** | Town |
| 상점 NPC (Miner/Farmer/Fisher) | **수동** | Town |
| Town→Wild 텔레포트 NPC | **수동** | Town |

---

## 1. 자동 생성 NPC

### 1.1 귀환 NPC (Wild 스폰 포인트)

Wild 월드 리셋 시 자동으로 생성됩니다.

**동작:**
- 월드 리셋 → 스폰 포인트에 기반암 5×5 플랫폼 생성
- 플랫폼 위에 귀환 NPC 자동 스폰
- 클릭 시 Town 스폰으로 이동

**NPC 이름:** `§e[귀환] §f마을로 돌아가기`

**문제 해결:**
- NPC가 움직이면: `setAI(false)`, `setCollidable(false)` 이미 적용됨
- NPC가 없으면: `/worldreset wild confirm`으로 월드 리셋

---

## 2. 수동 설치 NPC

### 2.1 강화 NPC

**역할:** 장비 강화 GUI 오픈

**설치 명령어:**
```
/npc create §6[강화] §f대장장이
/npc select
/npc type VILLAGER
/npc profession WEAPONSMITH
/npc trait lookclose
```

**클릭 명령어 설정:**
```
/npc cmd add -p server upgrade open
```

또는 Citizens CommandTrait 대신 TycoonLite 내부 처리:
- `UpgradeNpcListener`가 NPC 이름에 `강화` 또는 `대장장이` 포함 시 GUI 오픈

**권장 위치:** Town 중앙 광장

---

### 2.2 Recovery 보관소 NPC

**역할:** 사망 시 저장된 아이템 회수 GUI 오픈

**설치 명령어:**
```
/npc create §6[보관소] §fTown 보관소
/npc select
/npc type VILLAGER
/npc profession LIBRARIAN
/npc trait lookclose
```

**클릭 동작:**
- `RecoveryNpcListener`가 NPC 이름에 `보관소` 포함 시 GUI 오픈
- 플레이어 권한: `tycoon.recovery.use`

**권장 위치:** Town 스폰 근처

---

### 2.3 상점 NPC (직업별)

#### Miner 상점
```
/npc create §7[상점] §f광부 상점
/npc select
/npc type VILLAGER
/npc profession TOOLSMITH
/npc trait lookclose
```

#### Farmer 상점
```
/npc create §a[상점] §f농부 상점
/npc select
/npc type VILLAGER
/npc profession FARMER
/npc trait lookclose
```

#### Fisher 상점
```
/npc create §b[상점] §f어부 상점
/npc select
/npc type VILLAGER
/npc profession FISHERMAN
/npc trait lookclose
```

**클릭 동작:**
- `ShopNpcListener`가 NPC 이름에 `상점` + `광부/농부/어부` 포함 시 해당 상점 GUI 오픈
- 플레이어 권한: `tycoon.shop.use`

---

### 2.4 Town→Wild 텔레포트 NPC

**역할:** Town에서 Wild 랜덤 스폰 포인트로 이동

**설치 명령어:**
```
/npc create §e[야생] §f야생으로 떠나기
/npc select
/npc type VILLAGER
/npc profession CARTOGRAPHER
/npc trait lookclose
```

**클릭 동작:**
- `WildTeleportNpcListener`가 NPC 이름에 `야생` 포함 시 Wild 랜덤 스폰 포인트로 이동

**권장 위치:** Town 출입구/게이트

---

## 3. NPC 공통 설정

### 3.1 고정 (움직이지 않게)

```
/npc select
/npc lookclose
/npc gravity
```

### 3.2 보호 (공격 불가)

기본적으로 Citizens NPC는 보호됩니다.  
추가 보호가 필요하면:
```
/npc protected
```

### 3.3 이름표 항상 표시

```
/npc nameplate -s on
```

### 3.4 스킨 설정 (선택)

```
/npc skin <플레이어이름>
```

---

## 4. NPC 리스트 확인

```
/npc list
```

특정 NPC 정보:
```
/npc select <id>
/npc info
```

---

## 5. 문제 해결

### NPC가 클릭해도 반응 없음
1. NPC 이름에 키워드가 포함되어 있는지 확인 (강화, 보관소, 상점 등)
2. 플레이어 권한 확인 (`tycoon.recovery.use`, `tycoon.shop.use` 등)
3. 서버 로그에서 NPC 이벤트 확인

### NPC가 사라짐
1. Citizens 데이터 백업 확인: `plugins/Citizens/saves/npcs.yml`
2. `/npc reload`
3. 서버 재시작

### NPC가 움직임 (Wild 귀환 NPC)
- 이미 `setAI(false)`, `setCollidable(false)` 적용됨
- 여전히 움직이면 월드 리셋 후 재생성 확인

---

## 6. 권장 Town 배치 예시

```
          [Town 스폰]
              |
    +---------+---------+
    |                   |
[Recovery]          [강화 NPC]
    |                   |
+---+---+       +-------+-------+
|       |       |               |
[광부]  [농부]  [어부]      [야생 이동]
```

---

## 7. 관련 파일

- `UpgradeNpcListener.java` - 강화 NPC 클릭 처리
- `RecoveryNpcListener.java` - 보관소 NPC 클릭 처리  
- `ShopNpcListener.java` - 상점 NPC 클릭 처리
- `WildTeleportNpcListener.java` - 야생 이동 NPC 클릭 처리
- `WildSpawnManager.java` - 귀환 NPC 자동 생성
