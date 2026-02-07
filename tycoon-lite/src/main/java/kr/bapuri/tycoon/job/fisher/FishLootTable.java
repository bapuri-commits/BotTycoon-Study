package kr.bapuri.tycoon.job.fisher;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * FishLootTable - 커스텀 낚시 루트 테이블 (Stub)
 * 
 * Phase 4.D Stub:
 * - 희귀도별 커스텀 아이템 드롭
 * - v1.1에서 전체 구현 예정
 * - 현재는 바닐라 물고기만 사용
 */
public class FishLootTable {
    
    private final FisherConfig config;
    
    public FishLootTable(FisherConfig config) {
        this.config = config;
    }
    
    /**
     * [Stub] 희귀도에 따른 아이템 결정
     * 
     * 현재는 바닐라 물고기 기반으로 반환
     * v1.1에서 커스텀 아이템 추가 예정
     * 
     * @param rarity 결정된 희귀도
     * @param originalCatch 원본 낚은 아이템
     * @return 최종 아이템 (현재는 원본 그대로)
     */
    public ItemStack getLoot(FishRarity rarity, ItemStack originalCatch) {
        // TODO [v1.1]: 희귀도별 커스텀 아이템 반환
        // switch (rarity) {
        //     case LEGENDARY -> createLegendaryFish();
        //     case EPIC -> createEpicFish();
        //     ...
        // }
        
        // 현재는 원본 아이템 그대로 반환
        return originalCatch;
    }
    
    /**
     * [Stub] 아이템이 물고기인지 확인
     */
    public static boolean isFish(Material material) {
        return material == Material.COD 
            || material == Material.SALMON 
            || material == Material.TROPICAL_FISH 
            || material == Material.PUFFERFISH;
    }
    
    /**
     * [Stub] 아이템이 낚시 가능한 아이템인지 확인 (물고기 + 쓰레기 + 보물)
     */
    public static boolean isFishable(Material material) {
        return isFish(material)
            // 쓰레기
            || material == Material.LEATHER_BOOTS
            || material == Material.LEATHER
            || material == Material.BONE
            || material == Material.ROTTEN_FLESH
            || material == Material.STICK
            || material == Material.STRING
            || material == Material.POTION  // 물병
            || material == Material.BOWL
            || material == Material.FISHING_ROD
            || material == Material.TRIPWIRE_HOOK
            || material == Material.INK_SAC
            || material == Material.LILY_PAD
            // 보물
            || material == Material.BOW
            || material == Material.ENCHANTED_BOOK
            || material == Material.NAME_TAG
            || material == Material.NAUTILUS_SHELL
            || material == Material.SADDLE;
    }
}
