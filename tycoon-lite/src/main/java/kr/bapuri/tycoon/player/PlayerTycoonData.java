package kr.bapuri.tycoon.player;

import kr.bapuri.tycoon.job.JobData;
import kr.bapuri.tycoon.job.JobType;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PlayerTycoonData - 플레이어별 저장 데이터
 * 
 * [Stage-3] 스키마 v2:
 * - 돈 (bd, bottCoin, lifetimeEarned, lifetimeSpent, lastTxnId)
 * - 도감 진행도 (unlockedCodex)
 * - 직업 정보 (tier1Job, tier2Job)
 * - 플롯 소유 정보 (ownedPlots)
 * - 야생 데이터 (lastDeathLocation)
 * - 헌터 PvP 데이터 (kills, deaths, assists, points, rating, cooldowns)
 * - 1v1 듀얼 데이터 (wins, losses, draws, rating, lastMatchAt)
 * - 범용 쿨다운 맵
 * - 티켓 소비 통계
 * - 던전 참조 데이터
 */
public class PlayerTycoonData {

    // 스키마 버전 (마이그레이션용)
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final UUID uuid;
    private String playerName = "";  // [Stage-3] 디버그/로깅용
    
    // ===== 경제 =====
    private long money;       // BD (기본 화폐)
    private long bottCoin;    // BottCoin (특수 화폐)
    
    // [Stage-3] 누적 통계
    private long lifetimeEarned = 0;   // 총 수입
    private long lifetimeSpent = 0;    // 총 지출
    
    // [Audit Fix 5.2] txnId LRU Cache로 개선
    // 단일 lastTxnId → 최근 MAX_TXN_IDS개 저장으로 변경
    // 빠른 연속 트랜잭션에서도 중복 방지 가능
    // [E-1 Fix] 스레드 안전성을 위해 동기화 필요 - 메서드 레벨 synchronized 적용
    private static final int MAX_TXN_IDS = 50;  // 최근 50개 txnId 저장
    private final java.util.LinkedHashSet<String> recentTxnIds = new java.util.LinkedHashSet<>();
    private final Object txnIdLock = new Object(); // [E-1 Fix] txnId 동기화용 락 객체
    @Deprecated private String lastTxnId = "";  // 하위 호환용 (마이그레이션)
    
    // ===== 도감 =====
    private final Set<String> unlockedCodex = new HashSet<>();
    
    // [Stage-11] 도감 마일스톤/카테고리 보상
    private final Set<Integer> claimedCodexMilestones = new HashSet<>();  // 수령한 마일스톤 (10, 25, 50...)
    private final Set<String> claimedCodexCategories = new HashSet<>();   // 수령한 카테고리 완성 보상
    
    // [2026-02-01] 도감 보상 버전 (소급적용용)
    private int codexRewardVersion = 0;  // 마지막으로 수령한 보상 버전
    
    // [Stage-11] 칭호 시스템
    private final Set<String> unlockedTitles = new HashSet<>();  // 해금된 칭호 ID 목록
    private String equippedTitle = null;                         // 현재 장착 칭호 ID (null = 없음)
    
    // [Stage-11] 업적 시스템
    private final Set<String> unlockedAchievements = new HashSet<>();    // 해금된 업적 ID
    private final Map<String, Integer> achievementProgress = new HashMap<>();  // 진행 중인 업적 카운터
    
    // [Stage-3] 범용 쿨다운 맵 (itemType/action -> endTimeMillis)
    private final Map<String, Long> cooldowns = new HashMap<>();
    
    // [Stage-3] 티켓 소비 통계 (itemType -> 소비 횟수)
    private final Map<String, Integer> ticketConsumed = new HashMap<>();
    
    // ===== 직업 시스템 =====
    // Tier 1 직업 (null = 없음)
    private JobType tier1Job;
    private int tier1JobLevel = 1;
    private long tier1JobExp = 0;
    
    // [Phase 4.E Stub] 다중 Tier1 확장용
    // 향후 2번째 Tier1 직업 허용 시 활성화
    // 해금 조건: 첫 직업 GRADE_4 달성
    // private Set<JobType> tier1Jobs = EnumSet.noneOf(JobType.class);
    // private static final int DEFAULT_MAX_TIER1_JOBS = 1;
    
    // Tier 2 직업 (null = 없음)
    private JobType tier2Job;
    private int tier2JobLevel = 1;
    private long tier2JobExp = 0;
    
    // [Phase 4.B] 직업별 통계
    private long totalMined = 0;      // 총 채굴량 (광부)
    private long totalHarvested = 0;  // 총 수확량 (농부)
    private long totalFished = 0;     // 총 낚시량 (어부)
    private long totalSales = 0;      // 총 판매액 (모든 직업)
    
    // ===== 플롯 시스템 (타운 월드) =====
    private final Set<String> ownedPlots = new HashSet<>(); // "worldName_chunkX_chunkZ" 형식
    
    // ===== 야생 월드 데이터 =====
    private Location lastDeathLocation;      // 마지막 사망 위치 (전생의 기억 주문서용)
    private Location lastTeleportLocation;   // 마지막 텔레포트 위치 (텔레포트 주문서용) [Phase 8]
    
    // [DROP] 헌터 PvP, 듀얼, 던전 데이터 제거됨 (Phase 1.5)
    
    // ===== 인벤토리 보호 상태 =====
    private boolean universalInventorySaveActive = false; // 범용 인벤토리 보호 활성화
    private String pendingCoreItemAction = "";           // 진행 중인 핵심 아이템 액션 (크래시 복구용)
    private String pendingCoreItemId = "";               // 진행 중인 아이템 ID
    private long pendingCoreItemTime = 0;                // 액션 시작 시간
    
    // ===== [Stage-14] 밀수품 패널티 데이터 =====
    private int contrabandCount = 0;                         // 밀수품 누적 카운트
    private long lastContrabandTime = 0;                     // 마지막 밀수품 오픈 시간

    // ===== [Anti-Exploit] 주민 거래 제한 =====
    private int villagerTradeCount = 0;                      // 주민 거래 횟수 (무료 제한 + 유료 구매 포함)

    // ===== [세금 시스템] =====
    private long dailyIncome = 0;                            // 일일 누적 소득 (소득세 계산용, deprecated)
    private long lastDailyReset = 0;                         // 마지막 일일 리셋 시간
    private long lastOnlineTime = 0;                         // 마지막 접속 시간 (세금 면제 판정용)
    private long lifetimeEarnedSnapshot = 0;                 // 세금 주기 시작 시 lifetimeEarned 스냅샷
    
    // [v2] 3시간 간격 세금 시스템
    private long intervalIncome = 0;                         // 3시간 간격 누적 소득
    private long lastIntervalReset = 0;                      // 마지막 간격 리셋 시간

    // ===== [v2.7] 개인 설정 =====
    private boolean showEffectMessages = true;               // 인챈트/램프 효과 발동 메시지 표시 여부

    // ===== [BC Shop] 치장 데이터 =====
    // 효과 중첩 가능: 채팅색상 1개 + 파티클 1개 + 발광 1개 동시 적용
    private String activeChatColor = null;                    // 활성화된 채팅 색상 ID
    private String activeParticle = null;                     // 활성화된 파티클 ID
    private String activeGlow = null;                         // 활성화된 발광 색상 ID
    private final Set<String> ownedCosmetics = new HashSet<>();  // 구매한 치장 아이템 ID 목록

    // ===== [Phase 2] 자동 저장용 dirty 플래그 =====
    private transient boolean dirty = false;                 // 데이터 변경 여부 (저장 안 함)

    // ===== [Stage-6] 광부 직업 확장 데이터 =====
    private int minerGrade = 1;                              // 광부 등급 (1차~4차)
    private int minerLevel = 1;                              // 광부 레벨 (1~100)
    private long minerExp = 0;                               // 광부 경험치
    private final Map<String, Long> minedOres = new HashMap<>();   // 채굴량 (oreId -> 개수)
    private final Map<String, Long> soldOres = new HashMap<>();    // 판매량 (oreId -> 개수)
    private long totalMinerSales = 0;                        // 누적 광물 판매액 (BD)

    // ===== [Stage-6] 어부 직업 확장 데이터 =====
    private int fisherGrade = 1;                              // 어부 등급 (1차~4차)
    private int fisherLevel = 1;                              // 어부 레벨 (1~100)
    private long fisherExp = 0;                               // 어부 경험치
    private final Map<String, Long> caughtSeafood = new HashMap<>();  // 획득량 (seafoodId -> 개수)
    private final Map<String, Long> soldSeafood = new HashMap<>();    // 판매량 (seafoodId -> 개수)
    private long totalFisherSales = 0;                        // 누적 수산물 판매액 (BD)
    private int pityRareCount = 0;                            // RARE 천장 카운터
    private int pityEpicCount = 0;                            // EPIC 천장 카운터
    private int pityLegendaryCount = 0;                       // LEGENDARY 천장 카운터

    // ===== [Stage-10] 장인 직업 확장 데이터 =====
    private int artisanGrade = 1;                             // 장인 등급 (1차~3차, Tier 2)
    private int artisanLevel = 1;                             // 장인 레벨 (1~70, Tier 2)
    private long artisanExp = 0;                              // 장인 경험치
    private int artisanCraftCount = 0;                        // 제작 횟수 (열쇠 + 귀중품)
    private int artisanRepairCount = 0;                       // 수리 횟수
    private long totalArtisanEarnings = 0;                    // 총 수익 (BD)

    // ===== [Stage-10] 요리사 직업 확장 데이터 =====
    private int chefGrade = 1;                                // 요리사 등급 (1차~3차, Tier 2)
    private int chefLevel = 1;                                // 요리사 레벨 (1~70, Tier 2)
    private long chefExp = 0;                                 // 요리사 경험치
    private int chefCraftCount = 0;                           // 요리 제작 횟수
    private long totalChefSales = 0;                          // 총 판매액 (BD)

    // ===== [Stage-10] 약초사 직업 확장 데이터 =====
    private int herbalistGrade = 1;                           // 약초사 등급 (1차~3차, Tier 2)
    private int herbalistLevel = 1;                           // 약초사 레벨 (1~70, Tier 2)
    private long herbalistExp = 0;                            // 약초사 경험치
    private int herbalistBrewCount = 0;                       // 포션 양조 횟수
    private int herbalistGatherCount = 0;                     // 약초 채집 횟수
    private long totalHerbalistSales = 0;                     // 총 판매액 (BD)

    // ===== [Stage-10.5] 공학자 직업 확장 데이터 =====
    private int engineerGrade = 1;                            // 공학자 등급 (1차~3차, Tier 2)
    private int engineerLevel = 1;                            // 공학자 레벨 (1~70, Tier 2)
    private long engineerExp = 0;                             // 공학자 경험치
    private int engineerCraftCount = 0;                       // 기계/부품 제작 횟수
    private int engineerServiceCount = 0;                     // 서비스 제공 횟수
    private long totalEngineerSales = 0;                      // 총 판매액 (BD)
    
    // [DROP] 카지노 데이터 제거됨 (Phase 1.5)

    // ===== [Stage-6] 농부 직업 확장 데이터 =====
    private int farmerGrade = 1;                              // 농부 등급 (1차~4차)
    private int farmerLevel = 1;                              // 농부 레벨 (1~100)
    private long farmerExp = 0;                               // 농부 경험치
    private final Map<String, Long> harvestedCrops = new HashMap<>(); // 수확량 (cropId -> 개수)
    private final Map<String, Long> soldCrops = new HashMap<>();      // 판매량 (cropId -> 개수)
    private long totalFarmerSales = 0;                        // 누적 작물 판매액 (BD)
    // 그룹별 누적 판매액 (승급 조건용, LOCKED)
    private long farmerGroupASales = 0;                       // Group A 누적 판매액
    private long farmerGroupBSales = 0;                       // Group B 누적 판매액
    private long farmerGroupCSales = 0;                       // Group C 누적 판매액
    private long farmerGroupDSales = 0;                       // Group D 누적 판매액
    private long farmerGroupESales = 0;                       // Group E 누적 판매액

    public PlayerTycoonData(UUID uuid) {
        this.uuid = uuid;
    }

    // ===== 기본 정보 =====
    
    public UUID getUuid() { 
        return uuid; 
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String name) {
        this.playerName = name != null ? name : "";
    }

    // ===== 경제 =====
    
    public long getMoney() { 
        return money; 
    }
    
    public void setMoney(long money) { 
        this.money = Math.max(0, money); // 음수 방지
        markDirty();
    }
    
    public void addMoney(long amount) { 
        if (amount > 0) {
            money += amount;
            markDirty();
        }
    }

    /**
     * 돈 차감
     * @return true if successful, false if insufficient funds
     */
    public boolean removeMoney(long amount) {
        if (amount <= 0) return true;
        if (money < amount) return false;
        money -= amount;
        return true;
    }

    /**
     * 돈이 충분한지 확인
     */
    public boolean hasMoney(long amount) {
        return money >= amount;
    }
    
    // ===== [Stage-3] 누적 통계 =====
    
    public long getLifetimeEarned() {
        return lifetimeEarned;
    }
    
    public void addLifetimeEarned(long amount) {
        if (amount > 0) {
            this.lifetimeEarned += amount;
        }
    }
    
    public void setLifetimeEarned(long amount) {
        this.lifetimeEarned = Math.max(0, amount);
    }
    
    public long getLifetimeSpent() {
        return lifetimeSpent;
    }
    
    public void addLifetimeSpent(long amount) {
        if (amount > 0) {
            this.lifetimeSpent += amount;
        }
    }
    
    public void setLifetimeSpent(long amount) {
        this.lifetimeSpent = Math.max(0, amount);
    }
    
    // ===== [Phase 4.B] 직업 통계 =====
    
    public long getTotalMined() {
        return totalMined;
    }
    
    public void setTotalMined(long amount) {
        this.totalMined = Math.max(0, amount);
    }
    
    public void addTotalMined(long amount) {
        if (amount > 0) {
            this.totalMined += amount;
            markDirty();
        }
    }
    
    public long getTotalHarvested() {
        return totalHarvested;
    }
    
    public void setTotalHarvested(long amount) {
        this.totalHarvested = Math.max(0, amount);
    }
    
    public void addTotalHarvested(long amount) {
        if (amount > 0) {
            this.totalHarvested += amount;
            markDirty();
        }
    }
    
    public long getTotalFished() {
        return totalFished;
    }
    
    public void setTotalFished(long amount) {
        this.totalFished = Math.max(0, amount);
    }
    
    public void addTotalFished(long amount) {
        if (amount > 0) {
            this.totalFished += amount;
            markDirty();
        }
    }
    
    public long getTotalSales() {
        return totalSales;
    }
    
    public void setTotalSales(long amount) {
        this.totalSales = Math.max(0, amount);
    }
    
    public void addTotalSales(long amount) {
        if (amount > 0) {
            this.totalSales += amount;
            markDirty();
        }
    }
    
    /**
     * [5.2 Fix] 최근 txnId 목록 반환 (저장용)
     * [E-1 Fix] 스레드 안전성을 위해 synchronized
     * @return 최근 처리된 txnId 목록 (최대 MAX_TXN_IDS개)
     */
    public java.util.List<String> getRecentTxnIds() {
        synchronized (txnIdLock) {
            return new java.util.ArrayList<>(recentTxnIds);
        }
    }
    
    /**
     * [5.2 Fix] 저장된 txnId 목록 로드
     * [E-1 Fix] 스레드 안전성을 위해 synchronized
     */
    public void setRecentTxnIds(java.util.List<String> txnIds) {
        synchronized (txnIdLock) {
            recentTxnIds.clear();
            if (txnIds != null) {
                for (String txnId : txnIds) {
                    if (txnId != null && !txnId.isEmpty()) {
                        // 락 내부에서 직접 추가 (addTxnId 재귀 호출 방지)
                        recentTxnIds.remove(txnId);
                        recentTxnIds.add(txnId);
                        while (recentTxnIds.size() > MAX_TXN_IDS) {
                            java.util.Iterator<String> it = recentTxnIds.iterator();
                            if (it.hasNext()) {
                                it.next();
                                it.remove();
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * @deprecated 하위 호환용 - setRecentTxnIds 사용 권장
     */
    @Deprecated
    public String getLastTxnId() {
        // 하위 호환: recentTxnIds가 있으면 마지막 ID 반환
        if (!recentTxnIds.isEmpty()) {
            return recentTxnIds.stream().reduce((a, b) -> b).orElse("");
        }
        return lastTxnId;
    }
    
    /**
     * @deprecated 하위 호환용 - addTxnId 사용 권장
     */
    @Deprecated
    public void setLastTxnId(String txnId) {
        if (txnId != null && !txnId.isEmpty()) {
            addTxnId(txnId);
        }
        this.lastTxnId = txnId != null ? txnId : "";
    }
    
    /**
     * [5.2 Fix] txnId를 LRU Cache에 추가
     * [E-1 Fix] 스레드 안전성을 위해 synchronized
     */
    public void addTxnId(String txnId) {
        if (txnId == null || txnId.isEmpty()) return;
        
        synchronized (txnIdLock) {
            // 이미 존재하면 제거 후 맨 뒤로 (LRU 갱신)
            recentTxnIds.remove(txnId);
            recentTxnIds.add(txnId);
            
            // 최대 크기 초과 시 가장 오래된 항목 제거
            while (recentTxnIds.size() > MAX_TXN_IDS) {
                java.util.Iterator<String> it = recentTxnIds.iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
        }
    }
    
    /**
     * [5.2 Fix] 트랜잭션 ID가 이미 처리되었는지 확인 (idempotency)
     * [E-1 Fix] 스레드 안전성을 위해 synchronized
     * 최근 MAX_TXN_IDS개의 txnId를 검사하여 중복 방지
     */
    public boolean isTxnProcessed(String txnId) {
        if (txnId == null || txnId.isEmpty()) return false;
        
        synchronized (txnIdLock) {
            // 새로운 LRU Cache에서 확인
            if (recentTxnIds.contains(txnId)) return true;
        }
        
        // 하위 호환: 마이그레이션 전 데이터도 확인 (lastTxnId는 불변이므로 락 불필요)
        return txnId.equals(lastTxnId);
    }

    // ===== BottCoin (특수 화폐) =====

    public long getBottCoin() {
        return bottCoin;
    }

    public void setBottCoin(long bottCoin) {
        this.bottCoin = Math.max(0, bottCoin); // 음수 방지
        markDirty();
    }

    public void addBottCoin(long amount) {
        if (amount > 0) {
            bottCoin += amount;
            markDirty();
        }
    }

    /**
     * BottCoin 차감
     * @return true if successful, false if insufficient funds
     */
    public boolean removeBottCoin(long amount) {
        if (amount <= 0) return true;
        if (bottCoin < amount) return false;
        bottCoin -= amount;
        return true;
    }

    /**
     * BottCoin이 충분한지 확인
     */
    public boolean hasBottCoin(long amount) {
        return bottCoin >= amount;
    }

    // ===== 도감 (Codex) =====
    
    public Set<String> getUnlockedCodex() {
        return unlockedCodex;
    }

    public int getCodexCount() {
        return unlockedCodex.size();
    }

    public boolean isCodexUnlocked(Material mat) {
        return unlockedCodex.contains(mat.name());
    }

    public boolean isCodexUnlocked(String materialName) {
        return unlockedCodex.contains(materialName);
    }

    /**
     * 도감에 아이템 등록
     * @return true if newly unlocked, false if already unlocked
     */
    public boolean unlockCodex(Material mat) {
        return unlockedCodex.add(mat.name());
    }

    /**
     * 도감 등록 여부와 관계없이 강제 등록 (이미 등록되어 있어도 true 반환)
     */
    public void forceUnlockCodex(Material mat) {
        unlockedCodex.add(mat.name());
    }

    /**
     * 도감에서 아이템 등록 해제
     * @return true if was unlocked, false if wasn't registered
     */
    public boolean lockCodex(Material mat) {
        return unlockedCodex.remove(mat.name());
    }

    /**
     * 도감 전체 초기화 (등록 항목 + 마일스톤/카테고리 보상 수령 상태)
     */
    public void resetCodex() {
        unlockedCodex.clear();
        claimedCodexMilestones.clear();
        claimedCodexCategories.clear();
    }

    // ===== [Stage-11] 도감 마일스톤/카테고리 보상 =====
    
    /**
     * 마일스톤 보상 수령 여부 확인
     */
    public boolean hasClaimedCodexMilestone(int milestone) {
        return claimedCodexMilestones.contains(milestone);
    }
    
    /**
     * 마일스톤 보상 수령 처리
     * @return true if newly claimed
     */
    public boolean claimCodexMilestone(int milestone) {
        return claimedCodexMilestones.add(milestone);
    }
    
    /**
     * 수령한 마일스톤 목록 반환
     */
    public Set<Integer> getClaimedCodexMilestones() {
        return new HashSet<>(claimedCodexMilestones);
    }
    
    /**
     * 카테고리 완성 보상 수령 여부 확인
     */
    public boolean hasClaimedCodexCategory(String category) {
        return claimedCodexCategories.contains(category);
    }
    
    /**
     * 카테고리 완성 보상 수령 처리
     * @return true if newly claimed
     */
    public boolean claimCodexCategory(String category) {
        return claimedCodexCategories.add(category);
    }
    
    /**
     * 수령한 카테고리 완성 보상 목록 반환
     */
    public Set<String> getClaimedCodexCategories() {
        return new HashSet<>(claimedCodexCategories);
    }
    
    // ===== [2026-02-01] 도감 보상 버전 관리 =====
    
    /**
     * 도감 보상 버전 조회 (소급적용용)
     */
    public int getCodexRewardVersion() {
        return codexRewardVersion;
    }
    
    /**
     * 도감 보상 버전 설정
     */
    public void setCodexRewardVersion(int version) {
        this.codexRewardVersion = version;
    }
    
    /**
     * 마일스톤 수령 기록만 초기화 (소급적용용)
     * 도감 등록 데이터는 유지
     */
    public void resetClaimedMilestones() {
        claimedCodexMilestones.clear();
    }

    // ===== [Stage-11] 칭호 시스템 =====
    
    /**
     * 칭호 해금 여부 확인
     */
    public boolean hasTitleUnlocked(String titleId) {
        return unlockedTitles.contains(titleId);
    }
    
    /**
     * 칭호 해금
     * @return true if newly unlocked
     */
    public boolean unlockTitle(String titleId) {
        boolean added = unlockedTitles.add(titleId);
        if (added) markDirty();
        return added;
    }
    
    /**
     * 해금된 칭호 목록 반환
     */
    public Set<String> getUnlockedTitles() {
        return new HashSet<>(unlockedTitles);
    }
    
    /**
     * 현재 장착된 칭호 ID 반환 (null = 없음)
     */
    public String getEquippedTitle() {
        return equippedTitle;
    }
    
    /**
     * 칭호 장착
     * @param titleId 칭호 ID (null = 해제)
     */
    public void setEquippedTitle(String titleId) {
        this.equippedTitle = titleId;
        markDirty();
    }
    
    /**
     * 칭호 장착 여부 확인
     */
    public boolean hasEquippedTitle() {
        return equippedTitle != null && !equippedTitle.isEmpty();
    }

    // ===== [Stage-11] 업적 시스템 =====
    
    /**
     * 업적 해금 여부 확인
     */
    public boolean hasAchievementUnlocked(String achievementId) {
        return unlockedAchievements.contains(achievementId);
    }
    
    /**
     * 업적 해금
     * @return true if newly unlocked
     */
    public boolean unlockAchievement(String achievementId) {
        return unlockedAchievements.add(achievementId);
    }
    
    /**
     * 해금된 업적 목록 반환
     */
    public Set<String> getUnlockedAchievements() {
        return new HashSet<>(unlockedAchievements);
    }
    
    /**
     * 업적 진행도 조회
     */
    public int getAchievementProgress(String achievementId) {
        return achievementProgress.getOrDefault(achievementId, 0);
    }
    
    /**
     * 업적 진행도 설정
     */
    public void setAchievementProgress(String achievementId, int progress) {
        if (progress <= 0) {
            achievementProgress.remove(achievementId);
        } else {
            achievementProgress.put(achievementId, progress);
        }
    }
    
    /**
     * 업적 진행도 증가
     * @return 증가 후 진행도
     */
    public int incrementAchievementProgress(String achievementId, int amount) {
        int current = getAchievementProgress(achievementId);
        int newProgress = current + amount;
        setAchievementProgress(achievementId, newProgress);
        return newProgress;
    }
    
    /**
     * 전체 업적 진행도 맵 반환
     */
    public Map<String, Integer> getAchievementProgressMap() {
        return new HashMap<>(achievementProgress);
    }

    // ===== [Stage-3] 범용 쿨다운 =====
    
    /**
     * 쿨다운 맵 조회
     */
    public Map<String, Long> getCooldowns() {
        return cooldowns;
    }
    
    /**
     * 특정 쿨다운 설정
     */
    public void setCooldown(String key, long endTimeMillis) {
        if (endTimeMillis > System.currentTimeMillis()) {
            cooldowns.put(key, endTimeMillis);
        } else {
            cooldowns.remove(key);
        }
    }
    
    /**
     * 쿨다운 중인지 확인
     */
    public boolean isOnCooldown(String key) {
        Long endTime = cooldowns.get(key);
        if (endTime == null) return false;
        if (System.currentTimeMillis() >= endTime) {
            cooldowns.remove(key);
            return false;
        }
        return true;
    }
    
    /**
     * 남은 쿨다운 시간 (밀리초)
     */
    public long getRemainingCooldown(String key) {
        Long endTime = cooldowns.get(key);
        if (endTime == null) return 0;
        return Math.max(0, endTime - System.currentTimeMillis());
    }
    
    /**
     * 만료된 쿨다운 정리
     */
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
    }
    
    // ===== [Stage-3] 티켓 소비 통계 =====
    
    /**
     * 티켓 소비 통계 맵 조회
     */
    public Map<String, Integer> getTicketConsumed() {
        return ticketConsumed;
    }
    
    /**
     * 티켓 소비 횟수 조회
     */
    public int getTicketConsumedCount(String itemType) {
        return ticketConsumed.getOrDefault(itemType, 0);
    }
    
    /**
     * 티켓 소비 기록
     */
    public void recordTicketConsumption(String itemType) {
        ticketConsumed.merge(itemType, 1, Integer::sum);
    }
    
    /**
     * 티켓 소비 횟수 설정
     */
    public void setTicketConsumedCount(String itemType, int count) {
        if (count > 0) {
            ticketConsumed.put(itemType, count);
        } else {
            ticketConsumed.remove(itemType);
        }
    }

    // ===== 직업 시스템 - Tier 1 =====

    public JobType getTier1Job() {
        return tier1Job;
    }

    public void setTier1Job(JobType job) {
        if (job == null || job.isTier1()) {
            this.tier1Job = job;
            if (job == null) {
                this.tier1JobLevel = 1;
                this.tier1JobExp = 0;
            } else {
                // [Bug Fix] 직업 변경 시 해당 직업의 레벨/경험치를 통합 필드에 동기화
                // 직업을 변경했다가 다시 선택할 때 레벨 불일치 방지
                syncTier1JobData(job);
            }
        }
    }
    
    /**
     * [Bug Fix] Tier 1 직업 데이터를 통합 필드에 동기화
     * 직업 변경 시 호출되어 tier1JobLevel/tier1JobExp가 해당 직업의 실제 값을 반영하도록 함
     */
    private void syncTier1JobData(JobType job) {
        if (job == null) return;
        
        switch (job) {
            case MINER -> {
                this.tier1JobLevel = JobData.clampLevel(this.minerLevel);
                this.tier1JobExp = JobData.clampExp(this.minerExp);
            }
            case FARMER -> {
                this.tier1JobLevel = JobData.clampLevel(this.farmerLevel);
                this.tier1JobExp = JobData.clampExp(this.farmerExp);
            }
            case FISHER -> {
                this.tier1JobLevel = JobData.clampLevel(this.fisherLevel);
                this.tier1JobExp = JobData.clampExp(this.fisherExp);
            }
            default -> {
                // Tier 1이 아닌 경우 무시
            }
        }
    }

    public int getTier1JobLevel() {
        return tier1JobLevel;
    }

    public void setTier1JobLevel(int level) {
        this.tier1JobLevel = JobData.clampLevel(level);
    }

    public long getTier1JobExp() {
        return tier1JobExp;
    }

    public void setTier1JobExp(long exp) {
        this.tier1JobExp = JobData.clampExp(exp);
    }

    public void addTier1JobExp(long amount) {
        if (amount > 0) {
            this.tier1JobExp = JobData.clampExp(this.tier1JobExp + amount);
        }
    }
    
    /**
     * Tier 1 직업이 최대 레벨인지 확인
     */
    public boolean isTier1JobMaxLevel() {
        return tier1JobLevel >= JobData.ABSOLUTE_MAX_LEVEL;
    }

    // ===== 직업 시스템 - Tier 2 =====

    public JobType getTier2Job() {
        return tier2Job;
    }

    public void setTier2Job(JobType job) {
        if (job == null || job.isTier2()) {
            this.tier2Job = job;
            if (job == null) {
                this.tier2JobLevel = 1;
                this.tier2JobExp = 0;
            }
        }
    }

    public int getTier2JobLevel() {
        return tier2JobLevel;
    }

    public void setTier2JobLevel(int level) {
        this.tier2JobLevel = JobData.clampLevel(level);
    }

    public long getTier2JobExp() {
        return tier2JobExp;
    }

    public void setTier2JobExp(long exp) {
        this.tier2JobExp = JobData.clampExp(exp);
    }

    public void addTier2JobExp(long amount) {
        if (amount > 0) {
            this.tier2JobExp = JobData.clampExp(this.tier2JobExp + amount);
        }
    }
    
    /**
     * Tier 2 직업이 최대 레벨인지 확인
     */
    public boolean isTier2JobMaxLevel() {
        return tier2JobLevel >= JobData.ABSOLUTE_MAX_LEVEL;
    }

    // ===== 직업 유틸리티 =====

    /**
     * Tier 1 직업이 있는지
     */
    public boolean hasTier1Job() {
        return tier1Job != null;
    }
    
    // ===== [Phase 4.E Stub] 다중 Tier1 확장용 =====
    
    /**
     * 2번째 Tier1 직업 슬롯 해금 가능 여부 (stub)
     * 
     * 조건: 현재 Tier1 직업이 GRADE_4 달성
     * 
     * @return 해금 가능 여부 (현재는 항상 false, 향후 활성화)
     */
    public boolean canUnlockSecondTier1Slot() {
        // 향후 활성화 시:
        // if (tier1Job == null) return false;
        // return getJobGrade(tier1Job) == JobGrade.GRADE_4;
        return false; // stub - 비활성화
    }
    
    /**
     * 현재 Tier1 직업 슬롯 수
     * 
     * @return 보유한 Tier1 직업 수 (현재 0 또는 1)
     */
    public int getTier1JobCount() {
        return tier1Job != null ? 1 : 0;
    }
    
    /**
     * 최대 Tier1 직업 슬롯 수
     * 
     * @return 최대 슬롯 수 (현재 1, 향후 확장 가능)
     */
    public int getMaxTier1Jobs() {
        // 향후 확장 시: canUnlockSecondTier1Slot() ? 2 : 1
        return 1;
    }

    /**
     * Tier 2 직업이 있는지
     */
    public boolean hasTier2Job() {
        return tier2Job != null;
    }

    /**
     * 특정 직업을 가지고 있는지
     */
    public boolean hasJob(JobType job) {
        if (job == null) return false;
        return job.equals(tier1Job) || job.equals(tier2Job);
    }

    /**
     * 특정 직업 ID를 가지고 있는지
     */
    public boolean hasJob(String jobId) {
        JobType job = JobType.fromId(jobId);
        return hasJob(job);
    }

    /**
     * 특정 직업의 레벨 조회
     * [Level/Grade 통합] 직업별 필드에서 직접 조회
     * @return 해당 직업의 레벨 (Tier 1: 1~100, Tier 2: 1~70), 없으면 0
     */
    public int getJobLevel(JobType job) {
        if (job == null) return 0;
        return switch (job) {
            case MINER -> minerLevel;
            case FARMER -> farmerLevel;
            case FISHER -> fisherLevel;
            case CHEF -> chefLevel;
            case ARTISAN -> artisanLevel;
            case HERBALIST -> herbalistLevel;
            case ENGINEER -> engineerLevel;
        };
    }

    /**
     * 특정 직업의 경험치 조회
     * [Level/Grade 통합] 직업별 필드에서 직접 조회
     * @return 해당 직업의 경험치, 없으면 0
     */
    public long getJobExp(JobType job) {
        if (job == null) return 0;
        return switch (job) {
            case MINER -> minerExp;
            case FARMER -> farmerExp;
            case FISHER -> fisherExp;
            case CHEF -> chefExp;
            case ARTISAN -> artisanExp;
            case HERBALIST -> herbalistExp;
            case ENGINEER -> engineerExp;
        };
    }

    /**
     * 특정 직업의 레벨 설정
     * [Level/Grade 통합] 직업별 setter에 위임
     */
    public void setJobLevel(JobType job, int level) {
        if (job == null) return;
        switch (job) {
            case MINER -> setMinerLevel(level);
            case FARMER -> setFarmerLevel(level);
            case FISHER -> setFisherLevel(level);
            case CHEF -> setChefLevel(level);
            case ARTISAN -> setArtisanLevel(level);
            case HERBALIST -> setHerbalistLevel(level);
            case ENGINEER -> setEngineerLevel(level);
        }
    }

    /**
     * 특정 직업의 경험치 설정
     * [Level/Grade 통합] 직업별 setter에 위임
     */
    public void setJobExp(JobType job, long exp) {
        if (job == null) return;
        long clampedExp = Math.max(0, exp);
        switch (job) {
            case MINER -> setMinerExp(clampedExp);
            case FARMER -> setFarmerExp(clampedExp);
            case FISHER -> setFisherExp(clampedExp);
            case CHEF -> setChefExp(clampedExp);
            case ARTISAN -> setArtisanExp(clampedExp);
            case HERBALIST -> setHerbalistExp(clampedExp);
            case ENGINEER -> setEngineerExp(clampedExp);
        }
    }

    /**
     * 특정 직업에 경험치 추가
     * [Level/Grade 통합] 직업별 필드에 직접 추가
     */
    public void addJobExp(JobType job, long amount) {
        if (job == null || amount <= 0) return;
        long currentExp = getJobExp(job);
        setJobExp(job, currentExp + amount);
    }
    
    /**
     * 특정 직업이 최대 레벨인지 확인
     * [Level/Grade 통합] Tier 1: 100, Tier 2: 70
     */
    public boolean isJobMaxLevel(JobType job) {
        if (job == null) return false;
        int level = getJobLevel(job);
        return switch (job) {
            case MINER, FARMER, FISHER -> level >= 100;
            case CHEF, ARTISAN, HERBALIST, ENGINEER -> level >= 70;
        };
    }

    /**
     * 특정 직업의 등급(Grade) 조회
     * [Level/Grade 통합] Tier 1: 1~4차, Tier 2: 1~3차
     * @return 해당 직업의 등급, 없으면 1
     */
    public int getJobGrade(JobType job) {
        if (job == null) return 1;
        return switch (job) {
            case MINER -> minerGrade;
            case FARMER -> farmerGrade;
            case FISHER -> fisherGrade;
            case CHEF -> chefGrade;
            case ARTISAN -> artisanGrade;
            case HERBALIST -> herbalistGrade;
            case ENGINEER -> engineerGrade;
        };
    }

    /**
     * 직업 초기화 (티어에 맞는 직업 해제)
     */
    public void clearJob(JobType job) {
        if (job == null) return;
        if (job.isTier1() && job.equals(tier1Job)) {
            setTier1Job(null);
        } else if (job.isTier2() && job.equals(tier2Job)) {
            setTier2Job(null);
        }
    }
    
    /**
     * [H-1] Tier 1 직업 완전 제거
     */
    public void clearTier1Job() {
        this.tier1Job = null;
        this.tier1JobLevel = 1;
        this.tier1JobExp = 0;
        // 직업별 필드도 초기화
        this.minerLevel = 1;
        this.minerExp = 0;
        this.minerGrade = 1;
        this.fisherLevel = 1;
        this.fisherExp = 0;
        this.fisherGrade = 1;
        this.farmerLevel = 1;
        this.farmerExp = 0;
        this.farmerGrade = 1;
    }
    
    /**
     * [H-1] Tier 2 직업 완전 제거
     */
    public void clearTier2Job() {
        this.tier2Job = null;
        this.tier2JobLevel = 1;
        this.tier2JobExp = 0;
        // 직업별 필드도 초기화
        this.artisanLevel = 1;
        this.artisanExp = 0;
        this.artisanGrade = 1;
        this.chefLevel = 1;
        this.chefExp = 0;
        this.chefGrade = 1;
        this.herbalistLevel = 1;
        this.herbalistExp = 0;
        this.herbalistGrade = 1;
        this.engineerLevel = 1;
        this.engineerExp = 0;
        this.engineerGrade = 1;
    }

    // ===== 플롯 시스템 =====

    /**
     * 소유한 플롯 목록 가져오기
     */
    public Set<String> getOwnedPlots() {
        return ownedPlots;
    }

    /**
     * 플롯 소유 개수
     */
    public int getPlotCount() {
        return ownedPlots.size();
    }

    /**
     * 특정 플롯을 소유하고 있는지
     * @param plotKey "worldName_chunkX_chunkZ" 형식
     */
    public boolean ownsPlot(String plotKey) {
        return ownedPlots.contains(plotKey);
    }

    /**
     * 플롯 추가 (구매)
     * @return true if newly added
     */
    public boolean addPlot(String plotKey) {
        return ownedPlots.add(plotKey);
    }

    /**
     * 플롯 제거 (판매)
     * @return true if removed
     */
    public boolean removePlot(String plotKey) {
        return ownedPlots.remove(plotKey);
    }

    // ===== 야생 월드 데이터 =====

    /**
     * 마지막 사망 위치 가져오기
     */
    public Location getLastDeathLocation() {
        return lastDeathLocation;
    }

    /**
     * 마지막 사망 위치 설정
     */
    public void setLastDeathLocation(Location location) {
        this.lastDeathLocation = location;
    }

    /**
     * 마지막 사망 기록이 있는지
     */
    public boolean hasLastDeathLocation() {
        return lastDeathLocation != null;
    }
    
    // ===== 텔레포트 주문서 데이터 ===== [Phase 8]
    
    /**
     * 마지막 텔레포트 위치 가져오기
     */
    public Location getLastTeleportLocation() {
        return lastTeleportLocation;
    }
    
    /**
     * 마지막 텔레포트 위치 설정
     */
    public void setLastTeleportLocation(Location location) {
        this.lastTeleportLocation = location;
        markDirty();
    }
    
    /**
     * 마지막 텔레포트 위치 존재 여부
     */
    public boolean hasLastTeleportLocation() {
        return lastTeleportLocation != null;
    }

    // ===== [DROP] 헌터/듀얼/채무/던전 시스템 제거됨 (Phase 1.5) =====
    
    // ===== 인벤토리 보호 상태 =====
    
    /**
     * 범용 인벤토리 보호가 활성화되어 있는지
     */
    public boolean isUniversalInventorySaveActive() {
        return universalInventorySaveActive;
    }
    
    /**
     * 범용 인벤토리 보호 활성화/비활성화
     */
    public void setUniversalInventorySaveActive(boolean active) {
        this.universalInventorySaveActive = active;
    }
    
    /**
     * 인벤토리 보호 소비 (사망 시)
     */
    public void consumeInventorySave() {
        this.universalInventorySaveActive = false;
    }
    
    // ===== [Stage-5] 진행 중인 핵심 아이템 액션 (크래시 복구용) =====
    
    /**
     * 진행 중인 액션 설정
     */
    public void setPendingCoreItemAction(String action, String itemId) {
        this.pendingCoreItemAction = action != null ? action : "";
        this.pendingCoreItemId = itemId != null ? itemId : "";
        this.pendingCoreItemTime = System.currentTimeMillis();
    }
    
    /**
     * 진행 중인 액션 클리어
     */
    public void clearPendingCoreItemAction() {
        this.pendingCoreItemAction = "";
        this.pendingCoreItemId = "";
        this.pendingCoreItemTime = 0;
    }
    
    /**
     * 진행 중인 액션이 있는지
     */
    public boolean hasPendingCoreItemAction() {
        return pendingCoreItemAction != null && !pendingCoreItemAction.isEmpty();
    }
    
    public String getPendingCoreItemAction() {
        return pendingCoreItemAction;
    }
    
    public String getPendingCoreItemId() {
        return pendingCoreItemId;
    }
    
    public long getPendingCoreItemTime() {
        return pendingCoreItemTime;
    }
    
    // ===== [Stage-14] 밀수품 패널티 =====
    
    /**
     * 밀수품 누적 카운트 조회
     */
    public int getContrabandCount() {
        return contrabandCount;
    }
    
    /**
     * 밀수품 누적 카운트 설정
     */
    public void setContrabandCount(int count) {
        this.contrabandCount = Math.max(0, count);
    }
    
    /**
     * 마지막 밀수품 오픈 시간 조회
     */
    public long getLastContrabandTime() {
        return lastContrabandTime;
    }
    
    /**
     * 마지막 밀수품 오픈 시간 설정
     */
    public void setLastContrabandTime(long time) {
        this.lastContrabandTime = time;
    }

    // ===== [Anti-Exploit] 주민 거래 제한 =====
    
    /**
     * 주민 거래 횟수 조회
     */
    public int getVillagerTradeCount() {
        return villagerTradeCount;
    }
    
    /**
     * 주민 거래 횟수 증가 및 반환
     */
    public int incrementVillagerTradeCount() {
        return ++villagerTradeCount;
    }
    
    /**
     * 주민 거래 횟수 설정 (관리자 리셋용)
     */
    public void setVillagerTradeCount(int count) {
        this.villagerTradeCount = Math.max(0, count);
    }

    // ===== [세금 시스템] =====

    /**
     * 일일 소득 조회
     */
    public long getDailyIncome() {
        return dailyIncome;
    }

    /**
     * 일일 소득 추가
     */
    public void addDailyIncome(long amount) {
        this.dailyIncome += amount;
        markDirty();
    }

    /**
     * 일일 소득 리셋
     */
    public void resetDailyIncome() {
        this.dailyIncome = 0;
        markDirty();
    }

    /**
     * 마지막 일일 리셋 시간 조회
     */
    public long getLastDailyReset() {
        return lastDailyReset;
    }

    /**
     * 마지막 일일 리셋 시간 설정
     */
    public void setLastDailyReset(long time) {
        this.lastDailyReset = time;
        markDirty();
    }

    /**
     * 마지막 접속 시간 조회
     */
    public long getLastOnlineTime() {
        return lastOnlineTime;
    }

    /**
     * 마지막 접속 시간 설정
     */
    public void setLastOnlineTime(long time) {
        this.lastOnlineTime = time;
        markDirty();
    }

    /**
     * 세금 주기 소득 스냅샷 조회
     */
    public long getLifetimeEarnedSnapshot() {
        return lifetimeEarnedSnapshot;
    }

    /**
     * 세금 주기 소득 스냅샷 설정
     */
    public void setLifetimeEarnedSnapshot(long snapshot) {
        this.lifetimeEarnedSnapshot = snapshot;
        markDirty();
    }

    /**
     * 세금 주기 동안 소득 변화가 있었는지 확인
     */
    public boolean hasIncomeChange() {
        return lifetimeEarned > lifetimeEarnedSnapshot;
    }

    // ===== [v2] 3시간 간격 세금 시스템 =====

    /**
     * [v2] 3시간 간격 소득 조회
     */
    public long getIntervalIncome() {
        return intervalIncome;
    }

    /**
     * [v2] 3시간 간격 소득 추가
     */
    public void addIntervalIncome(long amount) {
        if (amount > 0) {
            this.intervalIncome += amount;
            markDirty();
        }
    }

    /**
     * [v2] 3시간 간격 소득 설정 (YAML 로드용)
     */
    public void setIntervalIncome(long amount) {
        this.intervalIncome = Math.max(0, amount);
    }

    /**
     * [v2] 3시간 간격 소득 리셋 (세금 징수 후 호출)
     */
    public void resetIntervalIncome() {
        this.intervalIncome = 0;
        this.lastIntervalReset = System.currentTimeMillis();
        markDirty();
    }

    /**
     * [v2] 마지막 간격 리셋 시간 조회
     */
    public long getLastIntervalReset() {
        return lastIntervalReset;
    }

    /**
     * [v2] 마지막 간격 리셋 시간 설정 (YAML 로드용)
     */
    public void setLastIntervalReset(long time) {
        this.lastIntervalReset = time;
    }

    // ===== [v2.7] 개인 설정 =====
    
    /**
     * 인챈트/램프 효과 발동 메시지 표시 여부 조회
     */
    public boolean isShowEffectMessages() {
        return showEffectMessages;
    }
    
    /**
     * 인챈트/램프 효과 발동 메시지 표시 여부 설정
     */
    public void setShowEffectMessages(boolean show) {
        this.showEffectMessages = show;
    }
    
    /**
     * 인챈트/램프 효과 발동 메시지 토글 (현재 상태 반전)
     * @return 토글 후 상태
     */
    public boolean toggleShowEffectMessages() {
        this.showEffectMessages = !this.showEffectMessages;
        return this.showEffectMessages;
    }

    // ===== [BC Shop] 치장 데이터 =====
    
    /**
     * 활성화된 채팅 색상 ID 조회
     */
    public String getActiveChatColor() {
        return activeChatColor;
    }
    
    /**
     * 채팅 색상 활성화/해제
     * @param id 색상 ID (null = 해제)
     */
    public void setActiveChatColor(String id) {
        this.activeChatColor = id;
        markDirty();
    }
    
    /**
     * 활성화된 파티클 ID 조회
     */
    public String getActiveParticle() {
        return activeParticle;
    }
    
    /**
     * 파티클 활성화/해제
     * @param id 파티클 ID (null = 해제)
     */
    public void setActiveParticle(String id) {
        this.activeParticle = id;
        markDirty();
    }
    
    /**
     * 활성화된 발광 ID 조회
     */
    public String getActiveGlow() {
        return activeGlow;
    }
    
    /**
     * 발광 효과 활성화/해제
     * @param id 발광 ID (null = 해제)
     */
    public void setActiveGlow(String id) {
        this.activeGlow = id;
        markDirty();
    }
    
    /**
     * 구매한 치장 아이템 목록 조회
     */
    public Set<String> getOwnedCosmetics() {
        return new HashSet<>(ownedCosmetics);
    }
    
    /**
     * 치장 아이템 보유 여부 확인
     */
    public boolean ownsCosmetic(String id) {
        return ownedCosmetics.contains(id);
    }
    
    /**
     * 치장 아이템 추가 (구매)
     * @return true if newly added
     */
    public boolean addCosmetic(String id) {
        boolean added = ownedCosmetics.add(id);
        if (added) markDirty();
        return added;
    }
    
    /**
     * 치장 아이템 제거 (관리자용)
     * @return true if removed
     */
    public boolean removeCosmetic(String id) {
        boolean removed = ownedCosmetics.remove(id);
        if (removed) {
            // 활성화된 효과도 해제
            if (id.equals(activeChatColor)) activeChatColor = null;
            if (id.equals(activeParticle)) activeParticle = null;
            if (id.equals(activeGlow)) activeGlow = null;
            markDirty();
        }
        return removed;
    }

    // ===== [Stage-6] 광부 직업 확장 =====
    
    /**
     * 광부 등급 조회 (1~4)
     */
    public int getMinerGrade() {
        return minerGrade;
    }
    
    /**
     * 광부 등급 설정
     */
    public void setMinerGrade(int grade) {
        this.minerGrade = Math.max(1, Math.min(4, grade));
    }
    
    /**
     * 광부 레벨 조회 (1~100)
     */
    public int getMinerLevel() {
        return minerLevel;
    }
    
    /**
     * 광부 레벨 설정
     * [A-5 Fix] 통합 필드도 동기화
     */
    public void setMinerLevel(int level) {
        this.minerLevel = Math.max(1, Math.min(100, level));
        // 통합 필드도 동기화 (MINER가 현재 tier1Job인 경우)
        if (tier1Job == JobType.MINER) {
            this.tier1JobLevel = JobData.clampLevel(level);
        }
    }
    
    /**
     * 광부 경험치 조회
     */
    public long getMinerExp() {
        return minerExp;
    }
    
    /**
     * 광부 경험치 설정
     * [A-5 Fix] 통합 필드도 동기화
     */
    public void setMinerExp(long exp) {
        this.minerExp = Math.max(0, exp);
        // 통합 필드도 동기화 (MINER가 현재 tier1Job인 경우)
        if (tier1Job == JobType.MINER) {
            this.tier1JobExp = JobData.clampExp(exp);
        }
    }
    
    /**
     * 광부 경험치 추가
     * [A-5 Fix] 통합 필드도 동기화
     */
    public void addMinerExp(long amount) {
        if (amount > 0) {
            this.minerExp = Math.max(0, this.minerExp + amount);
            // 통합 필드도 동기화 (MINER가 현재 tier1Job인 경우)
            if (tier1Job == JobType.MINER) {
                this.tier1JobExp = JobData.clampExp(this.minerExp);
            }
        }
    }
    
    /**
     * 채굴량 맵 조회
     */
    public Map<String, Long> getMinedOres() {
        return minedOres;
    }
    
    /**
     * 특정 광물 채굴량 조회
     */
    public long getMinedCount(String oreId) {
        return minedOres.getOrDefault(oreId, 0L);
    }
    
    /**
     * 채굴량 기록
     */
    public void recordMined(String oreId, long amount) {
        if (oreId != null && amount > 0) {
            minedOres.merge(oreId, amount, Long::sum);
        }
    }
    
    /**
     * 판매량 맵 조회
     */
    public Map<String, Long> getSoldOres() {
        return soldOres;
    }
    
    /**
     * 특정 광물 판매량 조회
     */
    public long getSoldCount(String oreId) {
        return soldOres.getOrDefault(oreId, 0L);
    }
    
    /**
     * 판매량 기록
     */
    public void recordSold(String oreId, long amount) {
        if (oreId != null && amount > 0) {
            soldOres.merge(oreId, amount, Long::sum);
        }
    }
    
    /**
     * 특정 광물의 채굴 및 판매 모두 충족 여부
     * (승급 조건: 우연적 판매 불인정)
     */
    public boolean hasMinedAndSold(String oreId, long requiredMined, long requiredSold) {
        return getMinedCount(oreId) >= requiredMined && getSoldCount(oreId) >= requiredSold;
    }
    
    /**
     * 누적 광물 판매액 조회
     */
    public long getTotalMinerSales() {
        return totalMinerSales;
    }
    
    /**
     * 누적 광물 판매액 설정
     */
    public void setTotalMinerSales(long total) {
        this.totalMinerSales = Math.max(0, total);
    }
    
    /**
     * 광물 판매액 추가
     * [Phase 4.E] markDirty() 호출 추가
     */
    public void addMinerSales(long amount) {
        if (amount > 0) {
            this.totalMinerSales += amount;
            markDirty();
        }
    }
    
    // ===== [Stage-6] 어부 직업 확장 =====
    
    /**
     * 어부 등급 조회 (1~4)
     */
    public int getFisherGrade() {
        return fisherGrade;
    }
    
    /**
     * 어부 등급 설정
     */
    public void setFisherGrade(int grade) {
        this.fisherGrade = Math.max(1, Math.min(4, grade));
    }
    
    /**
     * 어부 레벨 조회 (1~100)
     */
    public int getFisherLevel() {
        return fisherLevel;
    }
    
    /**
     * 어부 레벨 설정
     * [A-5 Fix] 통합 필드도 동기화
     */
    public void setFisherLevel(int level) {
        this.fisherLevel = Math.max(1, Math.min(100, level));
        // 통합 필드도 동기화 (FISHER가 현재 tier1Job인 경우)
        if (tier1Job == JobType.FISHER) {
            this.tier1JobLevel = JobData.clampLevel(level);
        }
    }
    
    /**
     * 어부 경험치 조회
     */
    public long getFisherExp() {
        return fisherExp;
    }
    
    /**
     * 어부 경험치 설정
     * [A-5 Fix] 통합 필드도 동기화
     */
    public void setFisherExp(long exp) {
        this.fisherExp = Math.max(0, exp);
        // 통합 필드도 동기화 (FISHER가 현재 tier1Job인 경우)
        if (tier1Job == JobType.FISHER) {
            this.tier1JobExp = JobData.clampExp(exp);
        }
    }
    
    /**
     * 어부 경험치 추가
     * [A-5 Fix] 통합 필드도 동기화
     */
    public void addFisherExp(long amount) {
        if (amount > 0) {
            this.fisherExp = Math.max(0, this.fisherExp + amount);
            // 통합 필드도 동기화 (FISHER가 현재 tier1Job인 경우)
            if (tier1Job == JobType.FISHER) {
                this.tier1JobExp = JobData.clampExp(this.fisherExp);
            }
        }
    }
    
    /**
     * 수산물 획득량 맵 조회
     */
    public Map<String, Long> getCaughtSeafood() {
        return caughtSeafood;
    }
    
    /**
     * 특정 수산물 획득량 조회
     */
    public long getCaughtCount(String seafoodId) {
        return caughtSeafood.getOrDefault(seafoodId, 0L);
    }
    
    /**
     * 수산물 획득 기록
     */
    public void recordCaught(String seafoodId, long amount) {
        if (seafoodId != null && amount > 0) {
            caughtSeafood.merge(seafoodId, amount, Long::sum);
        }
    }
    
    /**
     * 수산물 판매량 맵 조회
     */
    public Map<String, Long> getSoldSeafood() {
        return soldSeafood;
    }
    
    /**
     * 특정 수산물 판매량 조회
     */
    public long getSoldSeafoodCount(String seafoodId) {
        return soldSeafood.getOrDefault(seafoodId, 0L);
    }
    
    /**
     * 수산물 판매 기록
     */
    public void recordSoldSeafood(String seafoodId, long amount) {
        if (seafoodId != null && amount > 0) {
            soldSeafood.merge(seafoodId, amount, Long::sum);
        }
    }
    
    /**
     * 누적 수산물 판매액 조회
     */
    public long getTotalFisherSales() {
        return totalFisherSales;
    }
    
    /**
     * 누적 수산물 판매액 설정
     */
    public void setTotalFisherSales(long total) {
        this.totalFisherSales = Math.max(0, total);
    }
    
    /**
     * 수산물 판매액 추가
     * [Phase 4.E] markDirty() 호출 추가
     */
    public void addFisherSales(long amount) {
        if (amount > 0) {
            this.totalFisherSales += amount;
            markDirty();
        }
    }
    
    /**
     * Pity(천장) 카운터 - RARE
     */
    public int getPityRareCount() {
        return pityRareCount;
    }
    
    public void setPityRareCount(int count) {
        this.pityRareCount = Math.max(0, count);
    }
    
    /**
     * Pity(천장) 카운터 - EPIC
     */
    public int getPityEpicCount() {
        return pityEpicCount;
    }
    
    public void setPityEpicCount(int count) {
        this.pityEpicCount = Math.max(0, count);
    }
    
    /**
     * Pity(천장) 카운터 - LEGENDARY
     */
    public int getPityLegendaryCount() {
        return pityLegendaryCount;
    }
    
    public void setPityLegendaryCount(int count) {
        this.pityLegendaryCount = Math.max(0, count);
    }
    
    /**
     * 모든 Pity 카운터 초기화
     */
    public void resetAllPityCounters() {
        this.pityRareCount = 0;
        this.pityEpicCount = 0;
        this.pityLegendaryCount = 0;
    }
    
    // ===== [Stage-10] 장인 직업 확장 =====
    
    /**
     * 장인 등급 조회 (1~3, Tier 2)
     */
    public int getArtisanGrade() {
        return artisanGrade;
    }
    
    /**
     * 장인 등급 설정
     */
    public void setArtisanGrade(int grade) {
        this.artisanGrade = Math.max(1, Math.min(3, grade)); // Tier 2: 최대 3차
    }
    
    /**
     * 장인 레벨 조회 (1~70, Tier 2)
     */
    public int getArtisanLevel() {
        return artisanLevel;
    }
    
    /**
     * 장인 레벨 설정
     */
    public void setArtisanLevel(int level) {
        this.artisanLevel = Math.max(1, Math.min(70, level)); // Tier 2: 최대 70
    }
    
    /**
     * 장인 경험치 조회
     */
    public long getArtisanExp() {
        return artisanExp;
    }
    
    /**
     * 장인 경험치 설정
     */
    public void setArtisanExp(long exp) {
        this.artisanExp = Math.max(0, exp);
    }
    
    /**
     * 장인 경험치 추가
     */
    public void addArtisanExp(long amount) {
        if (amount > 0) {
            this.artisanExp = Math.max(0, this.artisanExp + amount);
        }
    }
    
    /**
     * 장인 제작 횟수 조회
     */
    public int getArtisanCraftCount() {
        return artisanCraftCount;
    }
    
    /**
     * 장인 제작 횟수 설정
     */
    public void setArtisanCraftCount(int count) {
        this.artisanCraftCount = Math.max(0, count);
    }
    
    /**
     * 장인 제작 기록 추가
     */
    public void addArtisanCraft() {
        this.artisanCraftCount++;
    }
    
    /**
     * 장인 수리 횟수 조회
     */
    public int getArtisanRepairCount() {
        return artisanRepairCount;
    }
    
    /**
     * 장인 수리 횟수 설정
     */
    public void setArtisanRepairCount(int count) {
        this.artisanRepairCount = Math.max(0, count);
    }
    
    /**
     * 장인 수리 기록 추가
     */
    public void addArtisanRepair() {
        this.artisanRepairCount++;
    }
    
    /**
     * 장인 총 수익 조회
     */
    public long getTotalArtisanEarnings() {
        return totalArtisanEarnings;
    }
    
    /**
     * 장인 총 수익 설정
     */
    public void setTotalArtisanEarnings(long total) {
        this.totalArtisanEarnings = Math.max(0, total);
    }
    
    /**
     * 장인 수익 추가
     */
    public void addArtisanEarnings(long amount) {
        if (amount > 0) {
            this.totalArtisanEarnings += amount;
        }
    }
    
    // ===== [Stage-10] 요리사 직업 확장 =====
    
    /**
     * 요리사 등급 조회 (1~3, Tier 2)
     */
    public int getChefGrade() {
        return chefGrade;
    }
    
    /**
     * 요리사 등급 설정
     */
    public void setChefGrade(int grade) {
        this.chefGrade = Math.max(1, Math.min(3, grade)); // Tier 2: 최대 3차
    }
    
    /**
     * 요리사 레벨 조회 (1~70, Tier 2)
     */
    public int getChefLevel() {
        return chefLevel;
    }
    
    /**
     * 요리사 레벨 설정
     */
    public void setChefLevel(int level) {
        this.chefLevel = Math.max(1, Math.min(70, level)); // Tier 2: 최대 70
    }
    
    /**
     * 요리사 경험치 조회
     */
    public long getChefExp() {
        return chefExp;
    }
    
    /**
     * 요리사 경험치 설정
     */
    public void setChefExp(long exp) {
        this.chefExp = Math.max(0, exp);
    }
    
    /**
     * 요리사 경험치 추가
     */
    public void addChefExp(long amount) {
        if (amount > 0) {
            this.chefExp = Math.max(0, this.chefExp + amount);
        }
    }
    
    /**
     * 요리사 제작 횟수 조회
     */
    public int getChefCraftCount() {
        return chefCraftCount;
    }
    
    /**
     * 요리사 제작 횟수 설정
     */
    public void setChefCraftCount(int count) {
        this.chefCraftCount = Math.max(0, count);
    }
    
    /**
     * 요리사 제작 기록 추가
     */
    public void addChefCraft() {
        this.chefCraftCount++;
    }
    
    /**
     * 요리사 총 판매액 조회
     */
    public long getTotalChefSales() {
        return totalChefSales;
    }
    
    /**
     * 요리사 총 판매액 설정
     */
    public void setTotalChefSales(long total) {
        this.totalChefSales = Math.max(0, total);
    }
    
    /**
     * 요리사 판매액 추가
     */
    public void addChefSales(long amount) {
        if (amount > 0) {
            this.totalChefSales += amount;
        }
    }
    
    // ===== [Stage-10] 약초사 직업 확장 =====
    
    /**
     * 약초사 등급 조회 (1~3, Tier 2)
     */
    public int getHerbalistGrade() {
        return herbalistGrade;
    }
    
    /**
     * 약초사 등급 설정
     */
    public void setHerbalistGrade(int grade) {
        this.herbalistGrade = Math.max(1, Math.min(3, grade)); // Tier 2: 최대 3차
    }
    
    /**
     * 약초사 레벨 조회 (1~70, Tier 2)
     */
    public int getHerbalistLevel() {
        return herbalistLevel;
    }
    
    /**
     * 약초사 레벨 설정
     */
    public void setHerbalistLevel(int level) {
        this.herbalistLevel = Math.max(1, Math.min(70, level)); // Tier 2: 최대 70
    }
    
    /**
     * 약초사 경험치 조회
     */
    public long getHerbalistExp() {
        return herbalistExp;
    }
    
    /**
     * 약초사 경험치 설정
     */
    public void setHerbalistExp(long exp) {
        this.herbalistExp = Math.max(0, exp);
    }
    
    /**
     * 약초사 경험치 추가
     */
    public void addHerbalistExp(long amount) {
        if (amount > 0) {
            this.herbalistExp = Math.max(0, this.herbalistExp + amount);
        }
    }
    
    /**
     * 약초사 양조 횟수 조회
     */
    public int getHerbalistBrewCount() {
        return herbalistBrewCount;
    }
    
    /**
     * 약초사 양조 횟수 설정
     */
    public void setHerbalistBrewCount(int count) {
        this.herbalistBrewCount = Math.max(0, count);
    }
    
    /**
     * 약초사 양조 기록 추가
     */
    public void addHerbalistBrew() {
        this.herbalistBrewCount++;
    }
    
    /**
     * 약초사 채집 횟수 조회
     */
    public int getHerbalistGatherCount() {
        return herbalistGatherCount;
    }
    
    /**
     * 약초사 채집 횟수 설정
     */
    public void setHerbalistGatherCount(int count) {
        this.herbalistGatherCount = Math.max(0, count);
    }
    
    /**
     * 약초사 채집 기록 추가
     */
    public void addHerbalistGather() {
        this.herbalistGatherCount++;
    }
    
    /**
     * 약초사 총 판매액 조회
     */
    public long getTotalHerbalistSales() {
        return totalHerbalistSales;
    }
    
    /**
     * 약초사 총 판매액 설정
     */
    public void setTotalHerbalistSales(long total) {
        this.totalHerbalistSales = Math.max(0, total);
    }
    
    /**
     * 약초사 판매액 추가
     */
    public void addHerbalistSales(long amount) {
        if (amount > 0) {
            this.totalHerbalistSales += amount;
        }
    }
    
    // ===== [Stage-10.5] 공학자 직업 확장 =====
    
    /**
     * 공학자 등급 조회 (1~3, Tier 2)
     */
    public int getEngineerGrade() {
        return engineerGrade;
    }
    
    /**
     * 공학자 등급 설정
     */
    public void setEngineerGrade(int grade) {
        this.engineerGrade = Math.max(1, Math.min(3, grade)); // Tier 2: 최대 3차
    }
    
    /**
     * 공학자 레벨 조회 (1~70, Tier 2)
     */
    public int getEngineerLevel() {
        return engineerLevel;
    }
    
    /**
     * 공학자 레벨 설정
     */
    public void setEngineerLevel(int level) {
        this.engineerLevel = Math.max(1, Math.min(70, level)); // Tier 2: 최대 70
    }
    
    /**
     * 공학자 경험치 조회
     */
    public long getEngineerExp() {
        return engineerExp;
    }
    
    /**
     * 공학자 경험치 설정
     */
    public void setEngineerExp(long exp) {
        this.engineerExp = Math.max(0, exp);
    }
    
    /**
     * 공학자 경험치 추가
     */
    public void addEngineerExp(long amount) {
        if (amount > 0) {
            this.engineerExp = Math.max(0, this.engineerExp + amount);
        }
    }
    
    /**
     * 공학자 제작 횟수 조회
     */
    public int getEngineerCraftCount() {
        return engineerCraftCount;
    }
    
    /**
     * 공학자 제작 횟수 설정
     */
    public void setEngineerCraftCount(int count) {
        this.engineerCraftCount = Math.max(0, count);
    }
    
    /**
     * 공학자 제작 기록 추가
     */
    public void addEngineerCraft() {
        this.engineerCraftCount++;
    }
    
    /**
     * 공학자 서비스 횟수 조회
     */
    public int getEngineerServiceCount() {
        return engineerServiceCount;
    }
    
    /**
     * 공학자 서비스 횟수 설정
     */
    public void setEngineerServiceCount(int count) {
        this.engineerServiceCount = Math.max(0, count);
    }
    
    /**
     * 공학자 서비스 기록 추가
     */
    public void addEngineerService() {
        this.engineerServiceCount++;
    }
    
    /**
     * 공학자 총 판매액 조회
     */
    public long getTotalEngineerSales() {
        return totalEngineerSales;
    }
    
    /**
     * 공학자 총 판매액 설정
     */
    public void setTotalEngineerSales(long total) {
        this.totalEngineerSales = Math.max(0, total);
    }
    
    /**
     * 공학자 판매액 추가
     */
    public void addEngineerSales(long amount) {
        if (amount > 0) {
            this.totalEngineerSales += amount;
        }
    }
    
    // ===== [DROP] 카지노 시스템 제거됨 (Phase 1.5) =====
    
    // ===== [Stage-6] 농부 직업 확장 =====
    
    /**
     * 농부 등급 조회 (1~4)
     */
    public int getFarmerGrade() {
        return farmerGrade;
    }
    
    /**
     * 농부 등급 설정
     */
    public void setFarmerGrade(int grade) {
        this.farmerGrade = Math.max(1, Math.min(4, grade));
    }
    
    /**
     * 농부 레벨 조회 (1~100)
     */
    public int getFarmerLevel() {
        return farmerLevel;
    }
    
    /**
     * 농부 레벨 설정
     * [A-5 Fix] 통합 필드도 동기화
     */
    public void setFarmerLevel(int level) {
        this.farmerLevel = Math.max(1, Math.min(100, level));
        // 통합 필드도 동기화 (FARMER가 현재 tier1Job인 경우)
        if (tier1Job == JobType.FARMER) {
            this.tier1JobLevel = JobData.clampLevel(level);
        }
    }
    
    /**
     * 농부 경험치 조회
     */
    public long getFarmerExp() {
        return farmerExp;
    }
    
    /**
     * 농부 경험치 설정
     * [A-5 Fix] 통합 필드도 동기화
     */
    public void setFarmerExp(long exp) {
        this.farmerExp = Math.max(0, exp);
        // 통합 필드도 동기화 (FARMER가 현재 tier1Job인 경우)
        if (tier1Job == JobType.FARMER) {
            this.tier1JobExp = JobData.clampExp(exp);
        }
    }
    
    /**
     * 농부 경험치 추가
     * [A-5 Fix] 통합 필드도 동기화
     */
    public void addFarmerExp(long amount) {
        if (amount > 0) {
            this.farmerExp = Math.max(0, this.farmerExp + amount);
            // 통합 필드도 동기화 (FARMER가 현재 tier1Job인 경우)
            if (tier1Job == JobType.FARMER) {
                this.tier1JobExp = JobData.clampExp(this.farmerExp);
            }
        }
    }
    
    /**
     * 작물 수확량 맵 조회
     */
    public Map<String, Long> getHarvestedCrops() {
        return harvestedCrops;
    }
    
    /**
     * 특정 작물 수확량 조회
     */
    public long getHarvestedCount(String cropId) {
        return harvestedCrops.getOrDefault(cropId, 0L);
    }
    
    /**
     * 작물 수확 기록
     */
    public void recordHarvest(String cropId, long amount) {
        if (cropId != null && amount > 0) {
            harvestedCrops.merge(cropId, amount, Long::sum);
        }
    }
    
    /**
     * 작물 판매량 맵 조회
     */
    public Map<String, Long> getSoldCrops() {
        return soldCrops;
    }
    
    /**
     * 특정 작물 판매량 조회
     */
    public long getSoldCropCount(String cropId) {
        return soldCrops.getOrDefault(cropId, 0L);
    }
    
    /**
     * 작물 판매 기록
     */
    public void recordSoldCrop(String cropId, long amount) {
        if (cropId != null && amount > 0) {
            soldCrops.merge(cropId, amount, Long::sum);
        }
    }
    
    /**
     * 누적 작물 판매액 조회
     */
    public long getTotalFarmerSales() {
        return totalFarmerSales;
    }
    
    /**
     * 누적 작물 판매액 설정
     */
    public void setTotalFarmerSales(long total) {
        this.totalFarmerSales = Math.max(0, total);
    }
    
    /**
     * 작물 판매액 추가
     * [Phase 4.E] markDirty() 호출 추가
     */
    public void addFarmerSales(long amount) {
        if (amount > 0) {
            this.totalFarmerSales += amount;
            markDirty();
        }
    }
    
    // ===== 농부 그룹별 판매액 (승급 조건용, LOCKED) =====
    
    public long getFarmerGroupASales() { return farmerGroupASales; }
    public long getFarmerGroupBSales() { return farmerGroupBSales; }
    public long getFarmerGroupCSales() { return farmerGroupCSales; }
    public long getFarmerGroupDSales() { return farmerGroupDSales; }
    public long getFarmerGroupESales() { return farmerGroupESales; }
    
    public void setFarmerGroupASales(long amount) { this.farmerGroupASales = Math.max(0, amount); }
    public void setFarmerGroupBSales(long amount) { this.farmerGroupBSales = Math.max(0, amount); }
    public void setFarmerGroupCSales(long amount) { this.farmerGroupCSales = Math.max(0, amount); }
    public void setFarmerGroupDSales(long amount) { this.farmerGroupDSales = Math.max(0, amount); }
    public void setFarmerGroupESales(long amount) { this.farmerGroupESales = Math.max(0, amount); }
    
    /**
     * 그룹별 판매액 추가
     * @param group 작물 그룹
     * @param amount 판매액
     */
    public void addFarmerGroupSales(kr.bapuri.tycoon.job.farmer.CropGroup group, long amount) {
        if (group == null || amount <= 0) return;
        
        switch (group) {
            case GROUP_A -> farmerGroupASales += amount;
            case GROUP_B -> farmerGroupBSales += amount;
            case GROUP_C -> farmerGroupCSales += amount;
            case GROUP_D -> farmerGroupDSales += amount;
            case GROUP_E -> farmerGroupESales += amount;
        }
    }
    
    /**
     * 그룹별 판매액 조회
     * @param group 작물 그룹
     * @return 해당 그룹의 누적 판매액
     */
    public long getFarmerGroupSales(kr.bapuri.tycoon.job.farmer.CropGroup group) {
        if (group == null) return 0;
        
        return switch (group) {
            case GROUP_A -> farmerGroupASales;
            case GROUP_B -> farmerGroupBSales;
            case GROUP_C -> farmerGroupCSales;
            case GROUP_D -> farmerGroupDSales;
            case GROUP_E -> farmerGroupESales;
        };
    }
    
    // ===== [Stage-3] 데이터 검증 =====
    
    /**
     * 모든 필드 값을 검증하고 범위 내로 클램핑
     * @return 수정이 필요했으면 true
     */
    public boolean validateAndClamp() {
        boolean modified = false;
        
        // 경제 값 검증
        if (money < 0) { money = 0; modified = true; }
        if (bottCoin < 0) { bottCoin = 0; modified = true; }
        if (lifetimeEarned < 0) { lifetimeEarned = 0; modified = true; }
        if (lifetimeSpent < 0) { lifetimeSpent = 0; modified = true; }
        
        // 직업 레벨 검증
        if (tier1JobLevel < 1) { tier1JobLevel = 1; modified = true; }
        if (tier1JobLevel > JobData.ABSOLUTE_MAX_LEVEL) { 
            tier1JobLevel = JobData.ABSOLUTE_MAX_LEVEL; 
            modified = true; 
        }
        if (tier1JobExp < 0) { tier1JobExp = 0; modified = true; }
        
        if (tier2JobLevel < 1) { tier2JobLevel = 1; modified = true; }
        if (tier2JobLevel > JobData.ABSOLUTE_MAX_LEVEL) { 
            tier2JobLevel = JobData.ABSOLUTE_MAX_LEVEL; 
            modified = true; 
        }
        if (tier2JobExp < 0) { tier2JobExp = 0; modified = true; }
        
        // [DROP] 헌터/듀얼/채무/던전 검증 제거됨 (Phase 1.5)
        
        // [Stage-6] 광부 직업 검증
        if (minerGrade < 1) { minerGrade = 1; modified = true; }
        if (minerGrade > 4) { minerGrade = 4; modified = true; }
        if (minerLevel < 1) { minerLevel = 1; modified = true; }
        if (minerLevel > 100) { minerLevel = 100; modified = true; }
        if (minerExp < 0) { minerExp = 0; modified = true; }
        if (totalMinerSales < 0) { totalMinerSales = 0; modified = true; }
        
        // [Stage-6] 어부 직업 검증
        if (fisherGrade < 1) { fisherGrade = 1; modified = true; }
        if (fisherGrade > 4) { fisherGrade = 4; modified = true; }
        if (fisherLevel < 1) { fisherLevel = 1; modified = true; }
        if (fisherLevel > 100) { fisherLevel = 100; modified = true; }
        if (fisherExp < 0) { fisherExp = 0; modified = true; }
        if (totalFisherSales < 0) { totalFisherSales = 0; modified = true; }
        if (pityRareCount < 0) { pityRareCount = 0; modified = true; }
        if (pityEpicCount < 0) { pityEpicCount = 0; modified = true; }
        if (pityLegendaryCount < 0) { pityLegendaryCount = 0; modified = true; }
        
        // [Stage-10] 장인 직업 검증
        if (artisanGrade < 1) { artisanGrade = 1; modified = true; }
        if (artisanGrade > 3) { artisanGrade = 3; modified = true; } // Tier 2: 최대 3차
        if (artisanLevel < 1) { artisanLevel = 1; modified = true; }
        if (artisanLevel > 70) { artisanLevel = 70; modified = true; } // Tier 2: 최대 70
        if (artisanExp < 0) { artisanExp = 0; modified = true; }
        if (artisanCraftCount < 0) { artisanCraftCount = 0; modified = true; }
        if (artisanRepairCount < 0) { artisanRepairCount = 0; modified = true; }
        if (totalArtisanEarnings < 0) { totalArtisanEarnings = 0; modified = true; }
        
        // [Stage-10] 요리사 직업 검증
        if (chefGrade < 1) { chefGrade = 1; modified = true; }
        if (chefGrade > 3) { chefGrade = 3; modified = true; } // Tier 2: 최대 3차
        if (chefLevel < 1) { chefLevel = 1; modified = true; }
        if (chefLevel > 70) { chefLevel = 70; modified = true; } // Tier 2: 최대 70
        if (chefExp < 0) { chefExp = 0; modified = true; }
        if (chefCraftCount < 0) { chefCraftCount = 0; modified = true; }
        if (totalChefSales < 0) { totalChefSales = 0; modified = true; }
        
        // [Stage-10] 약초사 직업 검증
        if (herbalistGrade < 1) { herbalistGrade = 1; modified = true; }
        if (herbalistGrade > 3) { herbalistGrade = 3; modified = true; } // Tier 2: 최대 3차
        if (herbalistLevel < 1) { herbalistLevel = 1; modified = true; }
        if (herbalistLevel > 70) { herbalistLevel = 70; modified = true; } // Tier 2: 최대 70
        if (herbalistExp < 0) { herbalistExp = 0; modified = true; }
        if (herbalistBrewCount < 0) { herbalistBrewCount = 0; modified = true; }
        if (herbalistGatherCount < 0) { herbalistGatherCount = 0; modified = true; }
        if (totalHerbalistSales < 0) { totalHerbalistSales = 0; modified = true; }
        
        // [Stage-10.5] 공학자 직업 검증
        if (engineerGrade < 1) { engineerGrade = 1; modified = true; }
        if (engineerGrade > 3) { engineerGrade = 3; modified = true; } // Tier 2: 최대 3차
        if (engineerLevel < 1) { engineerLevel = 1; modified = true; }
        if (engineerLevel > 70) { engineerLevel = 70; modified = true; } // Tier 2: 최대 70
        if (engineerExp < 0) { engineerExp = 0; modified = true; }
        if (engineerCraftCount < 0) { engineerCraftCount = 0; modified = true; }
        if (engineerServiceCount < 0) { engineerServiceCount = 0; modified = true; }
        if (totalEngineerSales < 0) { totalEngineerSales = 0; modified = true; }
        
        // [Stage-14] 밀수품 카운트 검증
        if (contrabandCount < 0) { contrabandCount = 0; modified = true; }
        if (lastContrabandTime < 0) { lastContrabandTime = 0; modified = true; }
        
        // [DROP] 카지노 검증 제거됨 (Phase 1.5)
        
        // [Stage-6] 농부 직업 검증
        if (farmerGrade < 1) { farmerGrade = 1; modified = true; }
        if (farmerGrade > 4) { farmerGrade = 4; modified = true; }
        if (farmerLevel < 1) { farmerLevel = 1; modified = true; }
        if (farmerLevel > 100) { farmerLevel = 100; modified = true; }
        if (farmerExp < 0) { farmerExp = 0; modified = true; }
        if (totalFarmerSales < 0) { totalFarmerSales = 0; modified = true; }
        if (farmerGroupASales < 0) { farmerGroupASales = 0; modified = true; }
        if (farmerGroupBSales < 0) { farmerGroupBSales = 0; modified = true; }
        if (farmerGroupCSales < 0) { farmerGroupCSales = 0; modified = true; }
        if (farmerGroupDSales < 0) { farmerGroupDSales = 0; modified = true; }
        if (farmerGroupESales < 0) { farmerGroupESales = 0; modified = true; }
        
        // 만료된 쿨다운 정리
        cleanupExpiredCooldowns();
        
        return modified;
    }
    
    // ===== [Phase 2] Dirty 플래그 관리 =====
    
    /**
     * 데이터 변경 여부 반환
     */
    public boolean isDirty() {
        return dirty;
    }
    
    /**
     * 데이터 변경 여부 설정
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
    
    /**
     * 데이터가 변경되었음을 표시
     * 주요 setter에서 호출해야 함
     */
    public void markDirty() {
        this.dirty = true;
    }
}
