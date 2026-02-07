# Paper Anti-Xray 설정 가이드

> **목적**: Paper 서버의 Anti-Xray 기능을 최적화하여 X-ray 텍스처팩/모드를 차단
> **대상**: 서버 운영자
> **최종 수정**: 2026-01-27

---

## 개요

Paper 서버는 내장 Anti-Xray 기능을 제공합니다. 이 기능은 클라이언트에 가짜 광물 데이터를 전송하여 X-ray를 무력화합니다.

**TycoonLite의 XrayHeuristicAnalyzer**는 Paper Anti-Xray의 **보완책**으로, 의심스러운 채굴 패턴을 분석합니다. Paper 설정을 먼저 최적화한 후, 휴리스틱 분석기를 추가 감시용으로 사용하세요.

---

## 설정 파일 위치

```
config/paper-world-defaults.yml
```

또는 월드별 설정:
```
world/paper-world.yml
world_nether/paper-world.yml
world_the_end/paper-world.yml
```

---

## 권장 설정: Overworld (오버월드)

```yaml
anticheat:
  anti-xray:
    enabled: true
    engine-mode: 2
    max-block-height: 64
    update-radius: 2
    lava-obscures: false
    use-permission: false
    hidden-blocks:
      - copper_ore
      - deepslate_copper_ore
      - gold_ore
      - deepslate_gold_ore
      - iron_ore
      - deepslate_iron_ore
      - coal_ore
      - deepslate_coal_ore
      - lapis_ore
      - deepslate_lapis_ore
      - mossy_cobblestone
      - obsidian
      - chest
      - diamond_ore
      - deepslate_diamond_ore
      - redstone_ore
      - deepslate_redstone_ore
      - clay
      - emerald_ore
      - deepslate_emerald_ore
      - ender_chest
    replacement-blocks:
      - stone
      - oak_planks
      - deepslate
```

### 설정 설명

| 옵션 | 값 | 설명 |
|------|-----|------|
| `enabled` | true | Anti-Xray 활성화 |
| `engine-mode` | 2 | 가짜 광물 생성 (가장 효과적) |
| `max-block-height` | 64 | 이 높이 이하에서만 적용 |
| `update-radius` | 2 | 광물 채굴 시 주변 업데이트 범위 |
| `hidden-blocks` | [...] | 숨길 광물 목록 |
| `replacement-blocks` | [...] | 가짜로 보여줄 블록 목록 |

---

## 권장 설정: Nether (지옥) ⚠️ 중요

지옥에서는 **Ancient Debris (고대 잔해)**를 반드시 추가해야 합니다.

```yaml
# world_nether/paper-world.yml
anticheat:
  anti-xray:
    enabled: true
    engine-mode: 2
    max-block-height: 128
    update-radius: 2
    lava-obscures: true
    use-permission: false
    hidden-blocks:
      - ancient_debris
      - nether_gold_ore
      - nether_quartz_ore
    replacement-blocks:
      - netherrack
      - nether_bricks
      - basalt
      - blackstone
```

### 지옥 설정 포인트

1. **`ancient_debris` 필수 추가**: 기본 설정에 없으면 X-ray로 고대 잔해가 보임
2. **`max-block-height: 128`**: 지옥은 높이가 다름
3. **`lava-obscures: true`**: 용암 뒤 블록도 숨김 (성능 영향 있음)

---

## 권장 설정: The End (엔드)

엔드는 광물이 거의 없어 기본 설정으로 충분합니다.

```yaml
# world_the_end/paper-world.yml
anticheat:
  anti-xray:
    enabled: false
```

---

## Engine Mode 비교

| 모드 | 설명 | 성능 | 효과 |
|------|------|------|------|
| 1 | 광물을 돌로 대체 | 좋음 | 낮음 (빈 공간 감지 가능) |
| **2** | 가짜 광물 생성 | 보통 | **높음 (권장)** |
| 3 | 1+2 혼합 | 나쁨 | 매우 높음 (성능 부담) |

---

## 성능 고려사항

1. **engine-mode: 2**가 가장 균형 잡힘
2. **update-radius**가 높을수록 성능 부담 (2 권장)
3. **lava-obscures: true**는 지옥에서만 사용 (성능 영향)
4. 플레이어 수가 많으면 모니터링 필요

---

## 테스트 방법

1. X-ray 리소스팩 설치 (테스트용)
2. 지하로 이동
3. 광물이 보이지 않고 가짜 광물만 보이면 성공
4. 실제 광물을 캐면 가짜 광물이 사라짐

---

## TycoonLite 연동

Paper Anti-Xray가 **1차 방어선**이고, TycoonLite의 `XrayHeuristicAnalyzer`는 **2차 감시**입니다.

### 현재 상태 (Phase 3.C.4)

```
[SCAFFOLD] - 미구현
```

휴리스틱 분석기는 나중에 구현 예정입니다. 현재는 Paper 설정만으로 대부분의 X-ray를 차단할 수 있습니다.

### 나중에 구현될 기능

- 희귀 광물 발견율 분석
- 직선 채굴 패턴 감지
- 급속 희귀 광물 발견 감지
- 운영자 알림 (자동 처벌 없음)

---

## 참고 자료

- [Paper Anti-Xray 공식 문서](https://docs.papermc.io/paper/anti-xray)
- [engine-mode 비교 영상](https://www.youtube.com/results?search_query=paper+anti+xray+engine+mode)

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-01-27 | 초기 작성 (Phase 3.C.4) |
