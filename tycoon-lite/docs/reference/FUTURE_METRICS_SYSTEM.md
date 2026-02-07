# 경제 메트릭스 시스템 - 미래 설계 문서

> **상태**: Phase 3.B에서 DEFERRED (연기)  
> **예정 Phase**: 미정 (별도 Phase에서 섬세한 설계 예정)  
> **현재 처리**: `sellMultiplier = 1.0` 고정

---

## 1. 시스템 개요

### 목적
서버 전체의 돈 흐름을 추적하고, 인플레이션/디플레이션을 자동으로 조절

### 핵심 문제 해결
```
플레이어 활동 → 돈 생성 (상점 판매, 직업 보상)
              → 돈 소비 (상점 구매, 수수료)
              
생성 > 소비 → 인플레이션 → 돈 가치 하락 → 경제 붕괴
생성 < 소비 → 디플레이션 → 돈 부족 → 활동 감소
```

### 해결책: 자동 배율 조정
- **sellMultiplier**: 상점 판매 수익 조절 (0.5 ~ 1.5)
- **sinkMultiplier**: 구매/수수료 비용 조절 (0.8 ~ 1.5)

---

## 2. 레거시 분석 (EconomyMetricsService)

### 파일 위치
`tycoon/src/main/java/kr/bapuri/tycoon/economy/metrics/EconomyMetricsService.java` (~505줄)

### 주요 기능

| 기능 | 메서드 | 설명 |
|------|--------|------|
| 돈 생성 기록 | `recordMoneyCreated(long)` | 상점 판매, 보상 등 |
| 돈 소비 기록 | `recordMoneySpent(long)` | 상점 구매, 수수료 등 |
| 시간별 추적 | `hourlyCreated`, `hourlySpent` | 72시간 보관 |
| 누적 통계 | `totalCreatedAllTime`, `totalSpentAllTime` | 서버 전체 |
| 판매 배율 | `getSellMultiplier()` | 상점 연동 |
| 싱크 배율 | `getSinkMultiplier()` | 구매 연동 |
| 중간값 잔액 | `getMedianBalance()` | 온라인 플레이어 |
| 상위 10% 잔액 | `getTop10PercentBalance()` | 부 집중도 측정 |

### 데이터 구조
```yaml
# economy_metrics.yml
sellMultiplier: 1.0
sinkMultiplier: 1.0
totalCreatedAllTime: 0
totalSpentAllTime: 0
serverUptimeMinutes: 0

hourlyCreated:
  "2026-01-26-14": 50000
  "2026-01-26-15": 75000

hourlySpent:
  "2026-01-26-14": 30000
  "2026-01-26-15": 45000
```

### 배율 조정 알고리즘
```java
// 최근 24시간 순 유동성 계산
long netFlow24h = created24h - spent24h;

// 돈이 너무 많이 생성되면 판매 배율 감소
if (netFlow24h > targetNetFlow + 10000) {
    sellMultiplier -= adjustmentSpeed;  // 기본 0.01
}

// 스무딩: 급격한 변동 방지
sellMultiplier = oldMult * 0.9 + newMult * 0.1;
```

---

## 3. 연동 포인트

### 상점 시스템 연동
```java
// JobShop, DynamicPriceShop 등
long sellPrice = baseSellPrice;
if (metricsService != null) {
    sellPrice = (long)(sellPrice * metricsService.getSellMultiplier());
}
```

### DynamicPriceTracker 연동
```java
// 동적 가격 시스템
private double globalSellMultiplier = 1.0;

public void setGlobalSellMultiplier(double multiplier) {
    this.globalSellMultiplier = Math.max(0.5, Math.min(1.5, multiplier));
}

public long getSellPrice(String itemId) {
    long adjustedPrice = (long)(currentSellPrice * globalSellMultiplier);
    return Math.max(1, adjustedPrice);
}
```

---

## 4. Phase 3.B에서의 처리

### Stub 구현
```java
// 임시: sellMultiplier = 1.0 고정
// 나중에 EconomyMetricsService 연동 시 교체

// ShopService 또는 DynamicPriceTracker에서
private double getSellMultiplier() {
    // TODO: EconomyMetricsService 연동 (별도 Phase)
    return 1.0;
}
```

### 연동 지점 명시
Phase 3.B 구현 시 다음 위치에 TODO 주석 추가:
- `ShopService.processSale()` - 판매 수익 계산
- `DynamicPriceTracker.getSellPrice()` - 동적 판매가 계산
- `JobShop.calculateSellPrice()` - 직업 상점 판매가

---

## 5. 미래 설계 고려사항

### 리팩토링 포인트
1. **시간별 vs 누적**: 시간별 데이터가 부족할 때 누적 평균 사용 (레거시 로직 유지)
2. **스무딩**: 급격한 변동 방지 (0.9/0.1 비율)
3. **범위 제한**: sellMultiplier 0.5~1.5, 극단적 상황 방지

### 개선 가능성
1. **TransactionSource 연동**: PLAYER/SYSTEM/ADMIN별 추적
2. **아이템 카테고리별 추적**: 광물/농산물/수산물 별도
3. **플레이어별 기여도**: 누가 얼마나 생성/소비했는지
4. **대시보드 UI**: 관리자용 경제 현황 GUI

### 설정 파라미터
```yaml
economyMetrics:
  enabled: true
  targetNetFlow: 0           # 목표 균형점
  sellMultiplierMin: 0.5
  sellMultiplierMax: 1.5
  sinkMultiplierMin: 0.8
  sinkMultiplierMax: 1.5
  adjustmentSpeed: 0.01      # 시간당 조정 속도
  updateIntervalMinutes: 5   # 배율 갱신 주기
```

---

## 6. 관련 문서

- 레거시 코드: `tycoon/src/main/java/kr/bapuri/tycoon/economy/metrics/`
- Phase 3.A 완료: `docs/lite/PHASE3A_COMPLETION_REPORT.md`
- DynamicPriceTracker: `tycoon/src/main/java/kr/bapuri/tycoon/shop/price/DynamicPriceTracker.java`
