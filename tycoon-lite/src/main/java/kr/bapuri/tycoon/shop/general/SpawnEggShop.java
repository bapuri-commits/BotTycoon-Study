package kr.bapuri.tycoon.shop.general;

import kr.bapuri.tycoon.economy.EconomyService;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

/**
 * 동물 스폰알 상점
 * 
 * <h2>특징</h2>
 * <ul>
 *   <li>고정 가격 (동적 가격 없음)</li>
 *   <li>레벨 보너스 없음</li>
 *   <li>구매 전용 (스폰알 판매 불가)</li>
 *   <li>평화로운 동물만 취급 (몬스터 스폰알 제외)</li>
 * </ul>
 * 
 * <h2>설정 파일 기반</h2>
 * <p>품목과 가격은 <b>shops.yml</b>의 general_shops.spawnegg에서 관리됩니다.</p>
 * 
 * @see FixedPriceShop
 */
public class SpawnEggShop extends FixedPriceShop {
    
    public SpawnEggShop(Plugin plugin, EconomyService economyService) {
        super(plugin, economyService, "spawnegg", "동물 상점");
    }
    
    /**
     * [폴백 전용] shops.yml에 items 섹션이 없을 때만 사용
     * 
     * <p>⚠️ 실제 품목/가격은 shops.yml에서 관리하세요!</p>
     */
    @Override
    protected void initItems() {
        // ========== 가축 (구매만) ==========
        registerBuyOnly(Material.COW_SPAWN_EGG, 500);
        registerBuyOnly(Material.PIG_SPAWN_EGG, 500);
        registerBuyOnly(Material.SHEEP_SPAWN_EGG, 500);
        registerBuyOnly(Material.CHICKEN_SPAWN_EGG, 300);
        registerBuyOnly(Material.GOAT_SPAWN_EGG, 600);
        registerBuyOnly(Material.MOOSHROOM_SPAWN_EGG, 2000);
        
        // ========== 말/당나귀 계열 ==========
        registerBuyOnly(Material.HORSE_SPAWN_EGG, 2000);
        registerBuyOnly(Material.DONKEY_SPAWN_EGG, 1500);
        registerBuyOnly(Material.MULE_SPAWN_EGG, 2500);
        registerBuyOnly(Material.LLAMA_SPAWN_EGG, 1000);
        registerBuyOnly(Material.TRADER_LLAMA_SPAWN_EGG, 1500);
        
        // ========== 펫 ==========
        registerBuyOnly(Material.CAT_SPAWN_EGG, 800);
        registerBuyOnly(Material.WOLF_SPAWN_EGG, 1000);
        registerBuyOnly(Material.PARROT_SPAWN_EGG, 1500);
        registerBuyOnly(Material.RABBIT_SPAWN_EGG, 400);
        registerBuyOnly(Material.FOX_SPAWN_EGG, 1200);
        registerBuyOnly(Material.OCELOT_SPAWN_EGG, 1000);
        
        // ========== 수중 동물 ==========
        registerBuyOnly(Material.TURTLE_SPAWN_EGG, 1500);
        registerBuyOnly(Material.AXOLOTL_SPAWN_EGG, 3000);
        registerBuyOnly(Material.DOLPHIN_SPAWN_EGG, 2500);
        registerBuyOnly(Material.SQUID_SPAWN_EGG, 500);
        registerBuyOnly(Material.GLOW_SQUID_SPAWN_EGG, 1000);
        registerBuyOnly(Material.COD_SPAWN_EGG, 200);
        registerBuyOnly(Material.SALMON_SPAWN_EGG, 250);
        registerBuyOnly(Material.TROPICAL_FISH_SPAWN_EGG, 500);
        registerBuyOnly(Material.PUFFERFISH_SPAWN_EGG, 400);
        
        // ========== 기타 평화로운 동물 ==========
        registerBuyOnly(Material.BEE_SPAWN_EGG, 2000);
        registerBuyOnly(Material.FROG_SPAWN_EGG, 1000);
        registerBuyOnly(Material.TADPOLE_SPAWN_EGG, 500);
        registerBuyOnly(Material.BAT_SPAWN_EGG, 300);
        registerBuyOnly(Material.PANDA_SPAWN_EGG, 3000);
        registerBuyOnly(Material.POLAR_BEAR_SPAWN_EGG, 2500);
        
        // ========== 중립 동물 (일부) ==========
        registerBuyOnly(Material.IRON_GOLEM_SPAWN_EGG, 5000);
        registerBuyOnly(Material.SNOW_GOLEM_SPAWN_EGG, 1000);
        
        // ========== 마을 주민 ==========
        registerBuyOnly(Material.VILLAGER_SPAWN_EGG, 3000);
        registerBuyOnly(Material.WANDERING_TRADER_SPAWN_EGG, 5000);
        
        // ========== 알레이 & 스니퍼 (1.19+, 1.20+) ==========
        registerBuyOnly(Material.ALLAY_SPAWN_EGG, 5000);
        registerBuyOnly(Material.SNIFFER_SPAWN_EGG, 10000);
        registerBuyOnly(Material.CAMEL_SPAWN_EGG, 3000);
        // ARMADILLO_SPAWN_EGG는 1.20.5+ (Minecraft 1.21)에서 추가됨 - 호환성을 위해 제외
    }
}
