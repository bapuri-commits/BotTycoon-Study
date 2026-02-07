# TycoonLite 외부 플러그인 스택 최종안 + 운영 충돌 방지 체크리스트

**작성일**: 2026-01-29  
**대상**: Paper 1.20.1+ 기반 TycoonLite 서버  
**목표**: Oraxen + ItemsAdder 동시 운용, 광부/생활(작물·음식) 분리 운영, 월드젠/광산 리젠/퀘스트/공학자 확장까지 “덜 꼬이게” 도입

---

## 0) SSOT(단일 진실 공급원) 원칙

- **TycoonLite가 SSOT**
  - **경제**(BD/BottCoin), **상점 가격/동적가격**, **직업 XP/레벨/등급**, **도감/업적/칭호/거래**는 TycoonLite가 최종 권위
- **Oraxen/ItemsAdder는 “정의/표현(리소스팩/아이템ID/블록ID)”만 담당**
  - 아이템/블록을 만들고 보여주는 인프라 역할
- “가공/정제/유통”을 크게 만들지 않더라도 **소비처(Use/Sink)** 를 먼저 설계하면 밸런스가 덜 꼬임

---

## 1) 도입 대상 목록 (링크/유료 여부)

### 1.1 핵심 인프라

| 이름 | 분류 | 유료? | 링크 |
|---|---|---:|---|
| Oraxen | 커스텀 아이템/블록/리소스팩 | 유료(프리미엄) | [공식](https://oraxen.com/) · [문서](https://docs.oraxen.com/) |
| ItemsAdder | 커스텀 아이템/블록/리소스팩 | 유료(프리미엄) | [공식](https://itemsadder.com/) · [문서](https://itemsadder.devs.beer/) |
| ExecutableItems | 커스텀 아이템(트리거/능력) | 부분 유료(Free/Demo + Premium) | [Spigot(데모/Free)](https://www.spigotmc.org/resources/custom-items-plugin-executable-items.77578/) · [문서](https://docs.ssomar.com/executableitems/information-ei/) |
| MechanicsCore | 메카닉/스크립팅 프레임워크 | 무료(오픈소스) | [GitHub](https://github.com/WeaponMechanics/MechanicsCore) |
| BetonQuest | 퀘스트/대사/조건 분기 | 무료(오픈소스) | [공식](https://betonquest.org/2.1/) · [설치](https://betonquest.org/2.2/Tutorials/Getting-Started/Setup-Guide/Installing-BetonQuest/) · [Spigot](https://www.spigotmc.org/resources/betonquest.2117/) |
| Chunky | 청크 프리젠(운영) | 무료 | [Spigot](https://www.spigotmc.org/resources/chunky.81534/) · [Wiki](https://github.com/pop4959/Chunky/wiki) |
| BlueMap | 웹 3D 맵(운영) | 무료(오픈소스) | [Modrinth](https://modrinth.com/plugin/bluemap) · [Releases](https://github.com/BlueMap-Minecraft/BlueMap/releases) |

### 1.2 광부 “자원 공급”

| 이름 | 분류 | 유료? | 링크 | 메모 |
|---|---|---:|---|---|
| Custom Ore Generator | 커스텀 광석 자연 생성 | 무료 | [Spigot](https://www.spigotmc.org/resources/custom-ore-generator-%E3%80%8E1-8-1-21-7%E3%80%8F.64339/) · [GitHub](https://github.com/DerFrZocker/Custom-Ore-Generator) | Oraxen 문서에 연동 가이드 존재 |
| Oraxen ↔ Custom Ore Generator 연동 | 문서/가이드 | - | [Oraxen 문서(연동)](https://docs.oraxen.com/compatibility/world-generators/custom-ore-generator) | “Oraxen Custom Ore Generator”라고 부르던 항목의 실체 |
| Ore Regenerator | 광산 구역 리젠(블록 재생성) | 무료/유료(버전별) | [Spigot Free (Resource #113249)](https://www.spigotmc.org/resources/ore-regenerator-free-itemsadder-oraxen-nexo-support-all-gui-controlled.113249/) · [ItemsAdder 호환 안내](https://itemsadder.devs.beer/compatibility-with-other-plugins/compatible/ore-regenerator) · [Premium(SyncMC)](https://syncmc.forum/resources/ore-regenerator-itemsadder-oraxen-support-1-8-1-21-all-gui-controlled.526/) | IA/Oraxen 블록 리젠 지원 표방 |
| Mine X Farm Regen | 광산/나무/농사 리젠 | 무료 | [Spigot](https://www.spigotmc.org/resources/mine-x-farm-regen%E2%9B%8F%EF%B8%8F-1-17-1-20.107060/) · [Modrinth](https://modrinth.com/plugin/mine-x-farm-regen) | 가볍게 시작하기 좋은 대안 |
| Terralith | 오버월드 월드젠 데이터팩 | 무료 | [Modrinth](https://modrinth.com/datapack/terralith) | Wild 리셋 구조와 궁합 좋음 |
| Incendium | 네더 월드젠 데이터팩 | 무료 | [Modrinth](https://modrinth.com/datapack/incendium/versions) | 네더 탐험/채굴 가치 상승 |
| Nullscape | 엔드 월드젠 데이터팩 | 무료 | [Modrinth](https://modrinth.com/datapack/nullscape/versions) | 엔드 탐험/채굴 가치 상승 |

### 1.3 v1.1 컨텐츠 확장

| 이름 | 분류 | 유료? | 링크 |
|---|---|---:|---|
| CustomCrops | 농사 확장(작물/계절/온실) | 유료(판매형) | [Polymart](https://polymart.org/resource/customcrops.2625) · [문서](https://mo-mi.gitbook.io/xiaomomi-plugins/customcrops) |
| PyroFishingPro | 낚시 미니게임 확장 | 유료(프리미엄) | [문서](https://pyrotempus.gitbook.io/pyro-plugins/pyrofishingpro/splash-page) · [Spigot(표기된 리소스)](https://www.spigotmc.org/resources/pyrofishingpro-1-13-x-1-16-x-1-fishing-plugin.60729/) |

---

## 2) “덜 꼬이게” 운영 규칙 (핵심)

### 2.1 직업별 소유권(Ownership) 강제

- **광부(Miner) 라인 = Oraxen 소유**
  - 커스텀 광석/주괴/부품/광산 블록/드랍/월드젠은 Oraxen에서만 정의
- **생활(농부/요리/약초) 라인 = ItemsAdder 소유**
  - 작물/음식/재료/소모품/생활 아이템은 ItemsAdder에서만 정의
- **공학자(Engineer)/설비 라인 = ExecutableItems(+향후 MechanicsCore)**
  - 설비/기계/도구는 EI/MechanicsCore로 제작
  - 설비 제작 재료는 광부(Oraxen) 부품을 소비하도록 설계(인플레 방지)

✅ 체크:
- [ ] 같은 “아이템 라인(예: 구리 주괴/강철 부품/씨앗)”을 Oraxen/IA 양쪽에 **중복 정의하지 않는다**
- [ ] 광석 월드 스폰(자연 생성)은 **한 시스템**이 담당한다 (IA Populator와 COG를 동시에 같은 역할로 쓰지 않기)

### 2.2 네임스페이스/ID 규칙 (리소스팩 충돌 예방)

권장 예시:
- Oraxen IDs: `ox_` / `ore_` / `part_` 접두어
- ItemsAdder IDs: `ia_` / `crop_` / `food_` 접두어

✅ 체크:
- [ ] Oraxen/IA 모두 “고유 네임스페이스 + 고유 폴더”로 경로를 분리한다
- [ ] 폰트/사운드/GUI 에셋 경로를 공유하지 않는다(필요 시 한쪽만 담당)

### 2.3 경제/가격/보상은 TycoonLite에서만

✅ 체크:
- [ ] Oraxen/IA 내장 상점/화폐 기능을 사용하지 않거나(가능하면) 비활성화한다
- [ ] 돈/보상(퀘스트 보상, 리젠 광산 보상 등)은 TycoonLite BD/BottCoin로 정산한다

### 2.4 광산/리젠 운영 규칙 (채굴 루프 안정화)

✅ 체크:
- [ ] 리젠 광산은 “일반~중급 광물” 중심으로 구성한다
- [ ] 고가치 광물은 Wild 탐험(자연 생성) 비중을 유지하거나 리젠 딜레이/가중치를 강하게 제한한다
- [ ] 리젠 광산은 월드/좌표/권한을 분리해 운영한다(초기에는 직업/등급으로 접근 제한)

---

## 3) 도입 순서(권장 로드맵)

### Phase A: 운영 인프라
1) Chunky  
2) BlueMap  
3) (기존) spark/Plan로 성능/안정화 확인

✅ 체크:
- [ ] Wild 리셋/신규 월드 생성 시 Chunky 프리젠 후 오픈
- [ ] BlueMap 월드별 표시 정책(Town/Wild 등) 결정

### Phase B: 퀘스트 파이프라인
4) BetonQuest (+ Citizens/ProtocolLib 요구사항 확인)

✅ 체크:
- [ ] 보상은 TycoonLite로만 지급(BD/BottCoin/칭호/도감)
- [ ] 요구 아이템은 소유권 규칙(Oraxen=광부, IA=생활)을 따른다

### Phase C: 리소스 인프라(아이템 정의)
5) Oraxen(광부 라인)  
6) ItemsAdder(생활 라인)

✅ 체크:
- [ ] ID/네임스페이스 규칙 확정 후 시작
- [ ] 리소스팩 배포/호스팅/병합 정책을 먼저 고정(누가 최종 pack 책임인지)

### Phase D: 광부 자원 공급(월드젠/광산)
7) Custom Ore Generator(+ Oraxen 연동)  
8) Ore Regenerator 또는 Mine X Farm Regen

✅ 체크:
- [ ] “광석이 어디서/얼마나/어떤 높이”에 나오는지 표로 관리한다
- [ ] 리젠 광산의 드랍/XP/딜레이/가중치 변경 이력을 남긴다

### Phase E: 공학자/설비 확장(나중)
9) ExecutableItems  
10) MechanicsCore

✅ 체크:
- [ ] 공학자 설비는 “광부 재료 소비처”로 설계한다
- [ ] 처음엔 작은 설비 1~2개(프로토타입)만 운영한다

### v1.1 컨텐츠 확장
- CustomCrops
- PyroFishingPro

✅ 체크:
- [ ] CustomCrops: 작물 정의/성장만 위임, 직업 XP/등급/판매는 TycoonLite로 귀속
- [ ] PyroFishingPro: 낚시 과정/콘텐츠만 위임, 경제/직업 보너스는 TycoonLite 기준 유지

---

## 4) 릴리즈 전 점검 체크리스트

### 리소스팩/ID
- [ ] Oraxen/IA 아이템 이름/ID 충돌 없음
- [ ] 리소스팩 적용 실패(느린 네트워크/재접속)에서 UI/텍스처 깨짐 없음

### 경제/밸런스
- [ ] 리젠 광산으로 BD 파밍이 과해지지 않음(시간당 수익 상한/딜레이/가중치)
- [ ] 광부 재료의 소비처(강화/퀘스트/설비)가 존재함

### 성능/월드
- [ ] Chunky 프리젠 후 TPS/메모리 스파이크 확인(spark)
- [ ] BlueMap 렌더/업데이트가 피크 타임을 침범하지 않음(스케줄 조정)

### 악용/보안
- [ ] 리젠 광산에서 폭발/피스톤/버킷 등 악용 상호작용 차단(필요 시 보호 설정)
- [ ] 커스텀 아이템이 바닐라 조합/모루/화로 경로로 이상 변환되지 않음

---

## 5) 운영 중 발견된 플러그인 이슈 (latest.log 2026-01-29)

**대상 로그**: `G:\mc-tycoon-server\logs\latest.log` (Paper 1.20.1)

> 참고: 아래 목록은 “이미 조치 완료한 항목(예: Oraxen pack 복구 / LoneLibs 설치 / Oraxen attribute 교정)”은 제외하고,
> **현재 남아있는 이슈/미설정/운영 선택 사항만** 정리한 것.

### 5.1 ItemsAdder: ZipException 로그 스팸 (우선순위 높음)

- **증상**: 서버 스레드에서 반복적으로 아래 오류/스택트레이스 발생
  - `[ItemsAdder] Error getting list of classes in JAR`
  - `java.util.zip.ZipException: only DEFLATED entries can have EXT descriptor`
- **상태**: 그럼에도 IA는 “Loaded successfully”까지 도달하나, 이후에도 재발 가능(로그 스팸/성능 영향 가능)
- **권장 조치**:
  - [ ] `ItemsAdder_3.6.6-legacy.jar`를 **공식 원본으로 재다운로드** 후 교체(손상/변조/잘못된 패키징 가능성 제거)
  - [ ] 재부팅 후 `latest.log`에서 `ZipException: only DEFLATED`가 **0건**인지 확인
  - [ ] “업데이트 4.0.15” 안내가 떠도 **1.20.1 서버에서는 4.x로 올리지 말 것**(1.21+ 타겟인 경우가 많음)

### 5.2 DiscordSRV: 봇 토큰 미설정 → 자동 비활성화

- **증상**: `No bot token has been set in the config` (토큰 없어서 연결 불가)
- **권장 조치**:
  - [ ] Discord 연동을 쓸 경우에만 `plugins/DiscordSRV/config.yml`에 토큰/권한을 설정
  - [ ] 안 쓸 거면 플러그인 제거 또는 `_disabled`로 이동(로그 정리)

### 5.3 BlueMap: 필수 리소스 다운로드 미수락

- **증상**:
  - `BlueMap is missing important resources!`
  - `You must accept the required file download in order for BlueMap to work!`
  - `Please check: G:\mc-tycoon-server\plugins\BlueMap\core.conf`
- **권장 조치**:
  - [ ] `plugins/BlueMap/core.conf`에서 요구 다운로드 수락(설정 가이드에 따라)
  - [ ] 적용 후 `/bluemap reload` 또는 서버 재부팅

### 5.4 Lands ↔ BlueMap 연동: BlueMap API 준비 안 됨(초기화 타이밍)

- **증상**: Lands가 “BlueMap API가 준비되지 않아 web-map 동기화 실패” 로그를 주기적으로 남김
- **권장 조치**:
  - [ ] 5.3(BlueMap 리소스 수락) 해결 후에도 지속되면 BlueMap 설정/렌더 완료 상태 확인

### 5.5 ExecutableItems: `__textures__` 팩 없음

- **증상**: `[ExecutableItems] No pack found in the folder __textures__`
- **해석**: EI 텍스처 팩/리소스를 아직 넣지 않은 상태면 정상(기능 자체와 별개)
- **권장 조치**:
  - [ ] EI 텍스처/리소스를 쓸 계획이면 해당 팩 폴더 구조를 맞춰 추가
  - [ ] 당장 안 쓰면 무시 가능

### 5.6 MineXFarmRegen: Regions.yml 미구성

- **증상**: `[MineXFarmRegen] Regions Not Found In Regions.yml`
- **권장 조치**:
  - [ ] 실제로 리젠 구역을 쓸 계획이면 `Regions.yml`에 구역을 정의
  - [ ] 아직 도입 전이면 무시 가능

### 5.7 BetonQuest: 업데이트 체크 서버 접근 실패(대개 일시적)

- **증상**: `Could not fetch version updates... host is currently not available: nexus.betonquest.org`
- **권장 조치**:
  - [ ] 기능 문제는 아니므로 일단 무시 가능(네트워크/호스트 이슈)
