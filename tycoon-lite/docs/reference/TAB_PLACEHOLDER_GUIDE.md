# TAB 플러그인 + PlaceholderAPI 설정 가이드

## 개요

Tycoon Lite는 PlaceholderAPI를 통해 다양한 플레이스홀더를 제공합니다.
TAB 플러그인과 연동하여 탭리스트, 이름표, 채팅 등에 표시할 수 있습니다.

---

## 사용 가능한 플레이스홀더

### 경제

| 플레이스홀더 | 설명 | 예시 |
|-------------|------|------|
| `%tycoon_money%` | BD 잔액 | `150000` |
| `%tycoon_money_formatted%` | BD 잔액 (포맷) | `150,000` |
| `%tycoon_bd%` | BD 잔액 (별칭) | `150000` |
| `%tycoon_bottcoin%` | BottCoin | `25` |
| `%tycoon_bc%` | BottCoin (별칭) | `25` |
| `%tycoon_bc_formatted%` | BottCoin (포맷) | `25` |

### 직업

| 플레이스홀더 | 설명 | 예시 |
|-------------|------|------|
| `%tycoon_job%` | 직업명 (한글) | `광부` |
| `%tycoon_job_id%` | 직업 ID | `MINER` |
| `%tycoon_job_level%` | 직업 레벨 | `15` |
| `%tycoon_job_exp%` | 직업 경험치 | `2500` |
| `%tycoon_job_grade%` | 직업 등급 | `2` |

### 칭호

| 플레이스홀더 | 설명 | 예시 |
|-------------|------|------|
| `%tycoon_title%` | 장착 칭호 (색상 포함) | `§a[수집가] §r` |
| `%tycoon_title_id%` | 칭호 ID | `title_collector` |

### 도감/업적

| 플레이스홀더 | 설명 | 예시 |
|-------------|------|------|
| `%tycoon_codex_count%` | 등록된 도감 수 | `47` |
| `%tycoon_achievement_count%` | 해금된 업적 수 | `12` |

### 기타

| 플레이스홀더 | 설명 | 예시 |
|-------------|------|------|
| `%tycoon_playtime%` | 플레이타임 (분) | `1250` |
| `%tycoon_playtime_hours%` | 플레이타임 (시간) | `20` |

---

## TAB 플러그인 설정

### 1. 탭리스트에 직업/돈 표시

`plugins/TAB/config.yml`:

```yaml
tablist-name-formatting:
  enabled: true
  
header-footer:
  enabled: true
  header:
    - "&6&lTycoon Lite Server"
    - "&7당신의 자산: &e%tycoon_money_formatted%&7 BD"
  footer:
    - "&7직업: &a%tycoon_job% &7Lv.&e%tycoon_job_level%"
```

### 2. 이름표에 칭호 표시

`plugins/TAB/config.yml`:

```yaml
scoreboard-teams:
  enabled: true
  
tablist-name-formatting:
  enabled: true
  # 칭호 + 플레이어 이름
  format: "%tycoon_title%%player%"
```

### 3. LuckPerms 그룹 기반 칭호 (권장)

칭호 시스템은 LuckPerms 그룹을 사용합니다.
TAB에서 LuckPerms 그룹 prefix를 표시하도록 설정:

`plugins/TAB/groups.yml`:

```yaml
_DEFAULT_:
  tabprefix: ""
  
title_collector:
  tabprefix: "&a[수집가] "
  
title_codex_master:
  tabprefix: "&b[도감왕] "
  
title_dragon_slayer:
  tabprefix: "&5[용사냥꾼] "
```

---

## LuckPerms 그룹 생성

칭호별 LuckPerms 그룹을 생성해야 합니다:

```
/lp creategroup title_collector
/lp creategroup title_codex_master
/lp creategroup title_dragon_slayer
```

`titles.yml`의 `luckperms_group` 값과 일치해야 합니다.

---

## 채팅 포맷 예시

EssentialsChat 또는 LuckPerms Chat Formatter 사용 시:

```
{tycoon_title}{displayname}: {message}
```

결과:
```
[수집가] Steve: 안녕하세요!
```

---

## 테스트 명령어

PlaceholderAPI 설치 후 테스트:

```
/papi parse me %tycoon_money%
/papi parse me %tycoon_job%
/papi parse me %tycoon_title%
```

---

## 주의사항

1. **PlaceholderAPI 필수**: TAB에서 Tycoon 플레이스홀더를 사용하려면 PlaceholderAPI가 설치되어 있어야 합니다.

2. **오프라인 플레이어**: 일부 플레이스홀더는 온라인 플레이어에서만 작동합니다.

3. **칭호 시스템**: 칭호는 LuckPerms 그룹 기반으로 작동합니다. TAB groups.yml에 해당 그룹의 prefix를 설정해야 표시됩니다.

4. **캐싱**: PlaceholderAPI 결과는 캐싱될 수 있습니다. 즉각 반영이 필요하면 TAB 설정에서 refresh 주기를 조정하세요.
