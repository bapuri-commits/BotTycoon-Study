# AngelChest 설정 가이드

> **작성일**: 2026-01-29  
> **대상**: TycoonLite v1.0  
> **필수 플러그인**: AngelChest (옵션)

---

## 개요

AngelChest는 플레이어 사망 시 아이템을 보호하는 데스 체스트 플러그인입니다.  
TycoonLite와 연동하여 월드 리셋 시 자동 만료 처리됩니다.

---

## 기본 설정 (권장)

`plugins/AngelChest/config.yml` 파일을 다음과 같이 설정하세요:

```yaml
# ===== 소유권 설정 (중요) =====
# 남의 상자 접근 차단 (반드시 false로 설정)
allow-others-to-open-chests: false

# ===== 지속 시간 설정 =====
# 데스 체스트 지속 시간 (초)
# TycoonLite config.yml의 deathChestDurationSeconds와 일치 권장
chest-duration: 600  # 10분

# ===== 월드별 설정 (선택) =====
# Wild 월드에서만 활성화
per-world:
  wild:
    enabled: true
    duration: 600
  world:  # Town
    enabled: false
```

---

## 권한 설정

### 기본 플레이어 권한

```yaml
# permissions.yml 또는 LuckPerms
angelchest.protect: true        # 사망 시 체스트 생성
angelchest.collect: true        # 자신의 체스트 수집
angelchest.teleport: false      # 체스트로 텔레포트 (비활성화 권장)
```

### 관리자 권한

```yaml
angelchest.admin: true          # 모든 체스트 관리
angelchest.bypass: true         # 시간 제한 무시
```

---

## TycoonLite 연동

### 자동 연동 (월드 리셋 시)

TycoonLite는 Wild 월드 리셋 시 자동으로 해당 월드의 모든 AngelChest를 만료 처리합니다.

```
/worldreset wild confirm
  → AngelChest 만료 처리
  → 월드 삭제 및 재생성
  → 새 스폰 포인트 생성
```

### config.yml 설정

```yaml
# TycoonLite config.yml
deathChestDurationSeconds: 600   # AngelChest duration과 일치 권장
```

---

## 문제 해결

### 남의 상자가 열림

**원인**: `allow-others-to-open-chests: true` 설정됨

**해결**:
```yaml
allow-others-to-open-chests: false
```

### 월드 리셋 후 체스트가 남아있음

**원인**: AngelChest 플러그인이 설치되지 않았거나, 연동 실패

**확인**: 서버 로그에서 `AngelChest 연동 완료` 메시지 확인
```
[TycoonPlugin] ✓ AngelChest 연동 완료 (v3.x.x)
```

### 만료된 아이템이 반환되지 않음

**현재 상태**: TycoonLite v1.0에서는 만료 시 아이템 자동 반환 미구현

**대안**:
1. 플레이어가 직접 체스트 수집
2. 만료 전 `/angelchest list` 로 확인
3. 관리자가 `/angelchest tp <player>` 로 체스트 위치 확인

---

## 참고 링크

- [AngelChest SpigotMC](https://www.spigotmc.org/resources/angelchest.60383/)
- [AngelChest Wiki](https://github.com/JEFF-Media-GbR/AngelChest/wiki)
