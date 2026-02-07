package kr.bapuri.tycoonhud.net;

import kr.bapuri.tycoonhud.model.AugmentData;
import kr.bapuri.tycoonhud.model.CountdownData;
import kr.bapuri.tycoonhud.model.DuelData;
import kr.bapuri.tycoonhud.model.DungeonMapData;
import kr.bapuri.tycoonhud.model.HunterMapData;
import kr.bapuri.tycoonhud.model.HunterRankingData;
import kr.bapuri.tycoonhud.model.JobData;
import kr.bapuri.tycoonhud.model.MinimapData;
import kr.bapuri.tycoonhud.model.PlayerProfileData;
import kr.bapuri.tycoonhud.model.ReadyStatusData;
import kr.bapuri.tycoonhud.model.VitalData;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 플레이어 데이터를 관리하는 싱글톤 매니저입니다.
 * 
 * <p>스레드 안전성을 위해 AtomicReference를 사용합니다.</p>
 * <p>tycoon-ui 모듈에서도 이 클래스를 통해 데이터에 접근합니다.</p>
 * 
 * <h3>사용 예시:</h3>
 * <pre>
 * PlayerProfileData profile = PlayerDataManager.getInstance().getProfile();
 * if (profile != null) {
 *     String name = profile.getName();
 * }
 * </pre>
 */
public class PlayerDataManager {
    
    /** 싱글톤 인스턴스 */
    private static final PlayerDataManager INSTANCE = new PlayerDataManager();
    
    /** 프로필 데이터 (스레드 안전) */
    private final AtomicReference<PlayerProfileData> profileData = new AtomicReference<>(null);
    
    /** Vital 데이터 (스레드 안전) */
    private final AtomicReference<VitalData> vitalData = new AtomicReference<>(null);
    
    /** 듀얼 세션 데이터 (스레드 안전) */
    private final AtomicReference<DuelData> duelData = new AtomicReference<>(null);
    
    /** 미니맵 데이터 (헌터 월드 전용) */
    private final AtomicReference<MinimapData> minimapData = new AtomicReference<>(null);
    
    /** 헌터 순위 데이터 (헌터 월드 전용) */
    private final AtomicReference<HunterRankingData> hunterRankingData = new AtomicReference<>(null);
    
    /** 레디 상태 데이터 (헌터 로비 전용) */
    private final AtomicReference<ReadyStatusData> readyStatusData = new AtomicReference<>(null);
    
    /** 카운트다운 데이터 (헌터 로비 전용) */
    private final AtomicReference<CountdownData> countdownData = new AtomicReference<>(null);
    
    /** 증강 선택 데이터 (헌터 전용) */
    private final AtomicReference<AugmentData> augmentData = new AtomicReference<>(null);
    
    /** 던전 맵 데이터 (로그라이크 던전 전용) */
    private final AtomicReference<DungeonMapData> dungeonMapData = new AtomicReference<>(null);
    
    /** 헌터 맵 데이터 (서버에서 전송받은 게임 지역 맵) */
    private final AtomicReference<HunterMapData> hunterMapData = new AtomicReference<>(null);
    
    /** 마지막 업데이트 시간 (밀리초) */
    private volatile long lastUpdateTime = 0;
    
    private PlayerDataManager() {
        // 싱글톤 - 외부 생성 방지
    }
    
    /**
     * 싱글톤 인스턴스를 반환합니다.
     * 
     * @return PlayerDataManager 인스턴스
     */
    public static PlayerDataManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 프로필 데이터를 설정합니다.
     * 
     * <p>네트워크 스레드에서 호출될 수 있으므로 스레드 안전합니다.</p>
     * 
     * @param data 새 프로필 데이터
     */
    public void setProfile(PlayerProfileData data) {
        profileData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 프로필 데이터를 반환합니다.
     * 
     * @return 프로필 데이터 또는 데이터가 없으면 null
     */
    public PlayerProfileData getProfile() {
        return profileData.get();
    }
    
    /**
     * Vital 데이터를 설정합니다.
     * 
     * @param data 새 Vital 데이터
     */
    public void setVital(VitalData data) {
        vitalData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 Vital 데이터를 반환합니다.
     * 
     * @return Vital 데이터 또는 데이터가 없으면 null
     */
    public VitalData getVital() {
        return vitalData.get();
    }
    
    /**
     * 듀얼 세션 데이터를 설정합니다.
     * 
     * @param data 새 듀얼 데이터
     */
    public void setDuel(DuelData data) {
        duelData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 듀얼 세션 데이터를 반환합니다.
     * 
     * @return 듀얼 데이터 또는 데이터가 없으면 null
     */
    public DuelData getDuel() {
        return duelData.get();
    }
    
    /**
     * 듀얼 세션을 종료합니다.
     */
    public void clearDuel() {
        duelData.set(null);
    }
    
    /**
     * 미니맵 데이터를 설정합니다.
     * 
     * @param data 새 미니맵 데이터
     */
    public void setMinimap(MinimapData data) {
        minimapData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 미니맵 데이터를 반환합니다.
     * 
     * @return 미니맵 데이터 또는 데이터가 없으면 null
     */
    public MinimapData getMinimap() {
        return minimapData.get();
    }
    
    /**
     * 헌터 순위 데이터를 설정합니다.
     * 
     * @param data 새 순위 데이터
     */
    public void setHunterRanking(HunterRankingData data) {
        hunterRankingData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 헌터 순위 데이터를 반환합니다.
     * 
     * @return 순위 데이터 또는 데이터가 없으면 null
     */
    public HunterRankingData getHunterRanking() {
        return hunterRankingData.get();
    }
    
    /**
     * 레디 상태 데이터를 설정합니다.
     * 
     * @param data 새 레디 상태 데이터
     */
    public void setReadyStatus(ReadyStatusData data) {
        readyStatusData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 레디 상태 데이터를 반환합니다.
     * 
     * @return 레디 상태 데이터 또는 데이터가 없으면 null
     */
    public ReadyStatusData getReadyStatus() {
        return readyStatusData.get();
    }
    
    /**
     * 카운트다운 데이터를 설정합니다.
     * 
     * @param data 새 카운트다운 데이터
     */
    public void setCountdown(CountdownData data) {
        countdownData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 카운트다운 데이터를 반환합니다.
     * 
     * @return 카운트다운 데이터 또는 데이터가 없으면 null
     */
    public CountdownData getCountdown() {
        return countdownData.get();
    }
    
    /**
     * 증강 선택 데이터를 설정합니다.
     * 
     * @param data 새 증강 데이터
     */
    public void setAugmentData(AugmentData data) {
        augmentData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 증강 선택 데이터를 반환합니다.
     * 
     * @return 증강 데이터 또는 데이터가 없으면 null
     */
    public AugmentData getAugmentData() {
        return augmentData.get();
    }
    
    /**
     * 증강 선택 데이터를 초기화합니다.
     */
    public void clearAugmentData() {
        augmentData.set(null);
    }
    
    /**
     * 던전 맵 데이터를 설정합니다.
     * 
     * @param data 새 던전 맵 데이터
     */
    public void setDungeonMap(DungeonMapData data) {
        dungeonMapData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 던전 맵 데이터를 반환합니다.
     * 
     * @return 던전 맵 데이터 또는 데이터가 없으면 null
     */
    public DungeonMapData getDungeonMap() {
        return dungeonMapData.get();
    }
    
    /**
     * 던전 맵 데이터를 초기화합니다.
     */
    public void clearDungeonMap() {
        dungeonMapData.set(null);
    }
    
    // ================================================================================
    // 헌터 맵 데이터 (서버 전송 게임 지역 맵)
    // ================================================================================
    
    /**
     * 헌터 맵 데이터를 설정합니다.
     * 
     * @param data 새 헌터 맵 데이터
     */
    public void setHunterMap(HunterMapData data) {
        hunterMapData.set(data);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 현재 헌터 맵 데이터를 반환합니다.
     * 
     * @return 헌터 맵 데이터 또는 데이터가 없으면 null
     */
    public HunterMapData getHunterMap() {
        return hunterMapData.get();
    }
    
    /**
     * 헌터 맵 데이터를 초기화합니다.
     */
    public void clearHunterMap() {
        hunterMapData.set(null);
    }
    
    /**
     * BD와 BottCoin을 실시간 업데이트합니다.
     * 
     * <p>ECONOMY_UPDATE 패킷 수신 시 사용됩니다.</p>
     * 
     * @param bd 새 BD 값
     * @param bottcoin 새 BottCoin 값
     */
    public void updateEconomy(long bd, int bottcoin) {
        PlayerProfileData current = profileData.get();
        if (current != null) {
            current.setBd(bd);
            current.setBottcoin(bottcoin);
        }
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 직업 데이터를 실시간 업데이트합니다.
     * 
     * <p>JOB_DATA 패킷 수신 시 사용됩니다.</p>
     * 
     * @param primaryJob 새 주 직업 (null이면 변경 없음)
     * @param secondaryJob 새 부 직업 (null이면 변경 없음)
     */
    public void updateJobs(JobData primaryJob, JobData secondaryJob) {
        PlayerProfileData current = profileData.get();
        if (current != null) {
            if (primaryJob != null) {
                current.setPrimaryJob(primaryJob);
            }
            if (secondaryJob != null) {
                current.setSecondaryJob(secondaryJob);
            }
        }
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * 데이터가 유효한지 확인합니다.
     * 
     * @return 프로필 데이터가 있으면 true
     */
    public boolean hasData() {
        return profileData.get() != null;
    }
    
    /**
     * 데이터가 최근에 업데이트되었는지 확인합니다.
     * 
     * @param thresholdMs 임계값 (밀리초)
     * @return 마지막 업데이트가 임계값 내이면 true
     */
    public boolean isDataFresh(long thresholdMs) {
        return System.currentTimeMillis() - lastUpdateTime < thresholdMs;
    }
    
    /**
     * 모든 데이터를 초기화합니다.
     * 
     * <p>서버 연결 해제 시 호출됩니다.</p>
     */
    public void clear() {
        profileData.set(null);
        vitalData.set(null);
        duelData.set(null);
        minimapData.set(null);
        hunterRankingData.set(null);
        readyStatusData.set(null);
        countdownData.set(null);
        augmentData.set(null);
        dungeonMapData.set(null);
        hunterMapData.set(null);
        lastUpdateTime = 0;
    }
}

