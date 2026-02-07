package kr.bapuri.tycoon.job.fisher;

import kr.bapuri.tycoon.player.PlayerTycoonData;

import java.util.UUID;

/**
 * PitySystem - 천장 시스템 (Stub)
 * 
 * Phase 4.D Stub:
 * - 일정 횟수 이상 낚시 시 희귀 등급 이상 보장
 * - v1.1에서 전체 구현 예정
 */
public class PitySystem {
    
    private final FisherConfig config;
    
    public PitySystem(FisherConfig config) {
        this.config = config;
    }
    
    /**
     * [Stub] Pity 시스템 활성화 여부
     */
    public boolean isEnabled() {
        return config.isPityEnabled();
    }
    
    /**
     * [Stub] Rare 이상 보장 필요 여부 확인
     * 
     * @param uuid 플레이어 UUID
     * @param data 플레이어 데이터
     * @return 현재 항상 false (stub)
     */
    public boolean shouldGuaranteeRare(UUID uuid, PlayerTycoonData data) {
        if (!config.isPityEnabled()) {
            return false;
        }
        
        // TODO [v1.1]: pityRareCount 체크 후 보장 여부 반환
        // int pityCount = data.getPityRareCount();
        // return pityCount >= config.getPityRareThreshold();
        
        return false;
    }
    
    /**
     * [Stub] Pity 카운터 리셋
     */
    public void resetPityCounter(UUID uuid, PlayerTycoonData data) {
        // TODO [v1.1]: data.setPityRareCount(0); data.markDirty();
    }
    
    /**
     * [Stub] Pity 카운터 증가
     */
    public void incrementPityCounter(UUID uuid, PlayerTycoonData data) {
        // TODO [v1.1]: data.addPityRareCount(1); data.markDirty();
    }
}
