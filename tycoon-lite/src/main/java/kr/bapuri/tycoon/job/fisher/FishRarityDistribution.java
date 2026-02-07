package kr.bapuri.tycoon.job.fisher;

import kr.bapuri.tycoon.world.WorldType;

import java.util.Random;

/**
 * FishRarityDistribution - 물고기 희귀도 결정 시스템
 * 
 * Phase 4.D:
 * - Town/Wild 환경에 따라 다른 확률 테이블 사용
 * - Wild에서 더 높은 희귀도 물고기 등장
 */
public class FishRarityDistribution {
    
    private final FisherConfig config;
    private final Random random = new Random();
    
    public FishRarityDistribution(FisherConfig config) {
        this.config = config;
    }
    
    /**
     * 환경에 따라 물고기 희귀도 결정
     * 
     * @param worldType Town 또는 Wild
     * @return 결정된 희귀도
     */
    public FishRarity roll(WorldType worldType) {
        double roll = random.nextDouble() * 100.0;
        
        if (worldType == WorldType.WILD) {
            return rollWild(roll);
        } else {
            return rollTown(roll);
        }
    }
    
    private FishRarity rollTown(double roll) {
        double cumulative = 0;
        
        cumulative += config.getTownChance(FishRarity.LEGENDARY);
        if (roll < cumulative) return FishRarity.LEGENDARY;
        
        cumulative += config.getTownChance(FishRarity.EPIC);
        if (roll < cumulative) return FishRarity.EPIC;
        
        cumulative += config.getTownChance(FishRarity.RARE);
        if (roll < cumulative) return FishRarity.RARE;
        
        cumulative += config.getTownChance(FishRarity.UNCOMMON);
        if (roll < cumulative) return FishRarity.UNCOMMON;
        
        return FishRarity.COMMON;
    }
    
    private FishRarity rollWild(double roll) {
        double cumulative = 0;
        
        cumulative += config.getWildChance(FishRarity.LEGENDARY);
        if (roll < cumulative) return FishRarity.LEGENDARY;
        
        cumulative += config.getWildChance(FishRarity.EPIC);
        if (roll < cumulative) return FishRarity.EPIC;
        
        cumulative += config.getWildChance(FishRarity.RARE);
        if (roll < cumulative) return FishRarity.RARE;
        
        cumulative += config.getWildChance(FishRarity.UNCOMMON);
        if (roll < cumulative) return FishRarity.UNCOMMON;
        
        return FishRarity.COMMON;
    }
    
    /**
     * 희귀도 분포 정보 (디버그용)
     */
    public String getDistributionInfo(WorldType worldType) {
        StringBuilder sb = new StringBuilder();
        sb.append(worldType == WorldType.WILD ? "§a[야생]" : "§b[타운]");
        sb.append(" 희귀도 분포:\n");
        
        for (FishRarity rarity : FishRarity.values()) {
            double chance = worldType == WorldType.WILD 
                    ? config.getWildChance(rarity) 
                    : config.getTownChance(rarity);
            sb.append(String.format("  %s: %.1f%%\n", rarity.getColoredName(), chance));
        }
        
        return sb.toString();
    }
}
