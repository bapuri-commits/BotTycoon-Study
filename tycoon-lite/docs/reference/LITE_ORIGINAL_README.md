# Tycoon Lite 문서

> 이 폴더는 Tycoon Lite 프로젝트 분리 및 개발 관련 문서를 관리합니다.

---

## 폴더 구조

```
docs/lite/
├── README.md                    # 이 파일
├── PHASE0_SCOPE_CHECKLIST.md    # Phase 0: 패키지/플러그인 체크리스트
├── PHASE0_IMPLEMENTATION.md     # Phase 0: 구현 상세 문서
├── PHASE1_IMPLEMENTATION.md     # Phase 1: Minimal Boot (예정)
├── PHASE2_IMPLEMENTATION.md     # Phase 2: 데이터 계층 (예정)
├── ...
└── EXTERNAL_PLUGINS.md          # 외부 플러그인 가이드 (예정)
```

---

## Phase 개요

| Phase | 이름 | 목표 | 상태 |
|-------|------|------|------|
| 0 | 스코프 확정 | KEEP/DROP/HOLD 결정, 외부 플러그인 확정 | 🟡 진행 중 |
| 1 | Minimal Boot | 플러그인 정상 로드, 에러 0 | ⬜ 대기 |
| 2 | 데이터 계층 | PlayerData 안정화 | ⬜ 대기 |
| 3 | 경제/상점 + 기본 차단 | BD, Shop, CustomItemVanillaBlocker | ⬜ 대기 |
| 4 | Tier 1 Jobs | Miner, Farmer, Fisher | ⬜ 대기 |
| 5 | Enhance + 주변부 | 인챈트, 도감, 업적, 거래 | ⬜ 대기 |
| 6 | 하드닝 | 커스텀 아이템 완전 차단 | ⬜ 대기 |

---

## 현재 문서

### 🔑 핵심 문서
- **[LITE_MASTER_TRACKER.md](./LITE_MASTER_TRACKER.md)** - 🆕 **마스터 추적 문서** (모든 Phase 체크리스트)

### Phase 0 (진행 중)
- [PHASE0_SCOPE_CHECKLIST.md](./PHASE0_SCOPE_CHECKLIST.md) - 패키지/플러그인 체크리스트
- [PHASE0_IMPLEMENTATION.md](./PHASE0_IMPLEMENTATION.md) - 구현 상세 문서
- [EXTERNAL_PLUGIN_ANALYSIS.md](./EXTERNAL_PLUGIN_ANALYSIS.md) - 외부 플러그인/모드 분석

### 원본 계획서
- [LITE_EXTRACTION_PLAN.md](../work/LITE_EXTRACTION_PLAN.md) - 전체 계획서 (GPT 검토 포함)
- [LITE_ADMIN_PLAN.md](../work/LITE_ADMIN_PLAN.md) - 관리자 권한 체계 설계안

---

## 외부 플러그인 도입 로드맵

```
Phase 1 (Minimal Boot):
├── spark (성능 모니터링)
└── LiteBans (밴 관리)

Phase 2 (데이터 계층):
├── InventoryRollbackPlus (인벤토리 백업)
└── CommandWhitelist (명령어 제한)

Phase 3 (경제/상점):
└── IllegalStack (듀프 방지) ← 설정 조정 필요!

Phase 4-5 (Jobs/Enhance):
├── TAB (탭리스트/네임태그)
└── HolographicDisplays (홀로그램)

Phase 5+ / v1.1:
├── Plan (통계 대시보드)
└── DiscordSRV (Discord 연동)
```

---

## 빠른 링크

| 작업 | 문서 |
|------|------|
| **전체 진행 상황 확인** | [LITE_MASTER_TRACKER.md](./LITE_MASTER_TRACKER.md) |
| 패키지 결정 확인 | [PHASE0_SCOPE_CHECKLIST.md](./PHASE0_SCOPE_CHECKLIST.md) |
| 외부 플러그인 분석 | [EXTERNAL_PLUGIN_ANALYSIS.md](./EXTERNAL_PLUGIN_ANALYSIS.md) |
| 정책 결정 | [PHASE0_IMPLEMENTATION.md](./PHASE0_IMPLEMENTATION.md#4-정책-결정) |
| 모드 통신 분석 | [LITE_MASTER_TRACKER.md#클라이언트-모드-분석](./LITE_MASTER_TRACKER.md#클라이언트-모드-분석-mod-패키지) |
