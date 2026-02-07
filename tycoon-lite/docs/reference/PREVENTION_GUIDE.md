# 재발 방지 가이드라인

## 문서 버전
- 작성일: 2026-01-29
- 작성 원인: VPS 배포 후 시스템 전면 장애 발생

---

## 1. 발생한 문제 요약

### 핵심 버그 1: NamespacedKey 네임스페이스 불일치 (치명적)

| 구분 | 값 | 결과 |
|------|-----|------|
| 레거시 Tycoon | `tycoon:custom_enchants` | 기존 아이템에 저장된 데이터 |
| TycoonLite (잘못됨) | `tycoonlite:custom_enchants` | 새 플러그인이 읽으려는 키 |

**결과**: 모든 레거시 아이템의 인챈트/램프/업그레이드 데이터를 읽지 못함

**영향 범위**:
- 인챈트 시스템: 100% 작동 불가
- 램프 시스템: 100% 작동 불가
- 업그레이드 시스템: 100% 작동 불가
- 플레이어 아이템 가치: 무효화

### 핵심 버그 2: 설정 파일 미반영

| 파일 | 상태 | 원인 |
|------|------|------|
| shops.yml | 수정했지만 VPS에 반영 안 됨 | `saveResource(false)` - 기존 파일 보존 |
| jobs.yml | 동일 | 동일 |
| codex.yml | 동일 | 동일 |

**결과**: 소스코드는 수정되었지만 VPS 서버에는 구버전 설정 파일 사용

---

## 2. 근본 원인 분석

### 코드상 문제
```java
// EnhanceConstants.java - 문제의 코드
CUSTOM_ENCHANTS_KEY = new NamespacedKey(plugin, "custom_enchants");
// plugin.getName() = "TycoonLite" → 소문자 변환 → "tycoonlite"
```

### 왜 이 문제가 발생했는가?

1. **일관성 없는 설계 결정**
   - `CoreItemAuthenticator`는 `"tycoon"` 하드코딩 (올바름)
   - `EnhanceConstants`는 `plugin.getName()` 사용 (잘못됨)

2. **레거시 호환성 검증 부재**
   - 새 코드가 레거시 데이터를 읽을 수 있는지 테스트하지 않음
   - 마이그레이션 시나리오 미검증

3. **Bukkit saveResource() 동작 방식 미이해**
   - `saveResource(fileName, false)`는 파일이 있으면 덮어쓰지 않음
   - 설정 파일 업데이트 시 별도 마이그레이션 필요

4. **코드 리뷰 누락**
   - NamespacedKey 생성 방식의 차이점을 검토하지 않음

---

## 3. 재발 방지 체크리스트

### A. NamespacedKey 사용 규칙 (필수)

```
[필수] 레거시 호환이 필요한 키는 반드시 하드코딩된 네임스페이스 사용
[필수] new NamespacedKey(plugin, ...) 대신 new NamespacedKey("tycoon", ...) 사용
[필수] 모든 NamespacedKey 정의는 한 곳(EnhanceConstants)에서 중앙 관리
[금지] 플러그인 이름 기반 동적 네임스페이스 사용 금지
```

### B. 레거시 호환성 테스트 체크리스트

```
[ ] 레거시 아이템에서 인챈트 데이터 읽기 테스트
[ ] 레거시 아이템에서 램프 데이터 읽기 테스트  
[ ] 레거시 아이템에서 업그레이드 데이터 읽기 테스트
[ ] 레거시 플레이어 데이터 로드 테스트
[ ] 새로 생성한 아이템에 데이터 쓰기/읽기 테스트
```

### C. 배포 전 필수 검증 단계

```
1. 로컬 테스트 서버에서 레거시 아이템 테스트
2. VPS 스테이징 환경에서 레거시 데이터 호환성 확인
3. 최소 1명의 테스터가 모든 시스템 검증
4. 롤백 계획 수립 후 배포
5. 설정 파일 변경 시 VPS 설정 파일도 수동 업데이트
```

### D. 설정 파일 배포 규칙

```
[필수] shops.yml, jobs.yml 등 설정 변경 시:
       → VPS의 plugins/TycoonLite/*.yml 파일 수동 업데이트 필요
       → 또는 기존 파일 삭제 후 서버 재시작

[이유] Bukkit의 saveResource(false)는 기존 파일이 있으면 덮어쓰지 않음
```

### E. 코드 리뷰 체크포인트

```
[ ] NamespacedKey 생성 시 네임스페이스가 "tycoon"인지 확인
[ ] PDC 관련 코드 변경 시 레거시 호환성 영향 분석
[ ] 플러그인 이름 변경 시 모든 NamespacedKey 영향 검토
[ ] 설정 파일 구조 변경 시 마이그레이션 계획 수립
```

---

## 4. 기술적 예방 조치

### A. EnhanceConstants.java 수정 패턴 (적용 완료)

```java
public class EnhanceConstants {
    // 레거시 호환을 위한 고정 네임스페이스 (절대 변경 금지!)
    private static final String LEGACY_NAMESPACE = "tycoon";
    
    @SuppressWarnings("deprecation")
    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        
        // 절대로 plugin.getName()을 사용하지 않음
        CUSTOM_ENCHANTS_KEY = new NamespacedKey(LEGACY_NAMESPACE, "custom_enchants");
        LAMP_EFFECT_KEY = new NamespacedKey(LEGACY_NAMESPACE, "lamp_effect");
        // ... 모든 키에 동일하게 적용
    }
}
```

### B. 설정 파일 강제 업데이트 방법

```java
// 버전 업데이트 시 설정 파일 강제 교체가 필요한 경우
File configFile = new File(plugin.getDataFolder(), "shops.yml");
if (configFile.exists()) {
    // 백업 생성
    File backup = new File(plugin.getDataFolder(), "shops.yml.backup");
    configFile.renameTo(backup);
}
plugin.saveResource("shops.yml", true); // true = 덮어쓰기
```

### C. 유닛 테스트 추가 (권장)

```java
@Test
void testNamespaceConsistency() {
    EnhanceConstants.init(mockPlugin);
    
    // 모든 키가 "tycoon" 네임스페이스를 사용하는지 확인
    assertEquals("tycoon", EnhanceConstants.getCustomEnchantsKey().getNamespace());
    assertEquals("tycoon", EnhanceConstants.getLampEffectKey().getNamespace());
    // ...
}
```

---

## 5. VPS 긴급 조치 방법

### shops.yml 수동 업데이트

```bash
# VPS에서 실행
cd /home/mcserver/minecraft/server/plugins/TycoonLite

# 백업
cp shops.yml shops.yml.backup

# 새 파일로 교체 (로컬에서 업로드 필요)
# 또는 기존 파일 삭제 후 서버 재시작
rm shops.yml
systemctl restart minecraft
```

### 모든 설정 파일 재생성

```bash
# VPS에서 실행
cd /home/mcserver/minecraft/server/plugins/TycoonLite

# 모든 yml 백업
mkdir backup_$(date +%Y%m%d)
cp *.yml backup_$(date +%Y%m%d)/

# 설정 파일 삭제 (데이터 파일은 유지)
rm shops.yml jobs.yml codex.yml achievements.yml titles.yml config.yml

# 서버 재시작 → 새 기본 설정 파일 생성
systemctl restart minecraft
```

---

## 6. 수정된 파일 목록 (2026-01-29)

| 파일 | 수정 내용 |
|------|----------|
| `EnhanceConstants.java` | NamespacedKey를 "tycoon" 네임스페이스로 고정 |
| `LampSlotExpandTicket.java` | 동일 |
| `LampRemoveTicket.java` | 동일 |

---

## 7. 교훈

1. **레거시 호환성은 가정이 아닌 검증 대상**
2. **플러그인 이름 변경은 데이터 호환성에 영향**
3. **PDC 키는 중앙 집중 관리 필수**
4. **배포 전 실제 레거시 데이터로 테스트 필수**
5. **Bukkit saveResource() 동작 방식 이해 필수**
6. **설정 파일 변경 시 VPS 수동 업데이트 필요**
