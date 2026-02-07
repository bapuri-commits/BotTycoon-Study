package kr.bapuri.tycoon.cosmetic;

import kr.bapuri.tycoon.bcshop.AbstractBCShop;
import kr.bapuri.tycoon.bcshop.BCShopItem;
import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 치장 상점
 * 
 * BC(BottCoin)로 채팅 색상, 파티클, 발광 효과를 구매할 수 있는 상점입니다.
 */
public class CosmeticShop extends AbstractBCShop {
    
    private final CosmeticRegistry registry;
    private final CosmeticService cosmeticService;
    
    public CosmeticShop(Plugin plugin, EconomyService economyService, PlayerDataManager dataManager,
                        CosmeticRegistry registry, CosmeticService cosmeticService) {
        super(plugin, economyService, dataManager, "cosmetic", "치장 상점");
        this.registry = registry;
        this.cosmeticService = cosmeticService;
        
        loadItems();
    }
    
    /**
     * 레지스트리에서 아이템 로드
     */
    private void loadItems() {
        items.clear();
        
        // 채팅 색상
        for (CosmeticItem cosmetic : registry.getAllChatColors()) {
            BCShopItem item = new BCShopItem(
                    cosmetic.getId(),
                    cosmetic.getColorCode() + cosmetic.getName(),
                    cosmetic.getDescription(),
                    cosmetic.getPrice(),
                    cosmetic.getIcon(),
                    "chat_color"
            );
            items.add(item);
        }
        
        // 파티클
        for (CosmeticItem cosmetic : registry.getAllParticles()) {
            BCShopItem item = new BCShopItem(
                    cosmetic.getId(),
                    "§f" + cosmetic.getName(),
                    cosmetic.getDescription(),
                    cosmetic.getPrice(),
                    cosmetic.getIcon(),
                    "particle"
            );
            items.add(item);
        }
        
        // 발광 효과
        for (CosmeticItem cosmetic : registry.getAllGlows()) {
            BCShopItem item = new BCShopItem(
                    cosmetic.getId(),
                    "§f" + cosmetic.getName(),
                    cosmetic.getDescription(),
                    cosmetic.getPrice(),
                    cosmetic.getIcon(),
                    "glow"
            );
            items.add(item);
        }
        
        logger.info("[CosmeticShop] 아이템 로드 완료: " + items.size() + "개");
    }
    
    @Override
    public boolean hasItem(Player player, BCShopItem item) {
        return cosmeticService.ownsCosmetic(player, item.getId());
    }
    
    @Override
    public boolean isItemActive(Player player, BCShopItem item) {
        return cosmeticService.isActive(player, item.getId());
    }
    
    @Override
    public void handlePurchase(Player player, BCShopItem item) {
        // 이미 보유 중인지 확인
        if (hasItem(player, item)) {
            player.sendMessage("§e이미 보유한 치장 아이템입니다. 좌클릭으로 활성화하세요.");
            return;
        }
        
        CosmeticItem cosmetic = registry.get(item.getId());
        if (cosmetic == null) {
            player.sendMessage("§c치장 아이템을 찾을 수 없습니다.");
            return;
        }
        
        // BC 차감
        if (!purchaseWithBC(player, cosmetic.getPrice(), cosmetic.getId(), 
                "치장 구매: " + cosmetic.getName())) {
            return;
        }
        
        // 치장 추가
        cosmeticService.addCosmetic(player, item.getId());
        
        player.sendMessage("");
        player.sendMessage("§6§l★ 치장 구매 완료! ★");
        player.sendMessage("§e" + cosmetic.getName());
        player.sendMessage("§7좌클릭으로 활성화하세요!");
        player.sendMessage("");
    }
    
    @Override
    public void handleToggle(Player player, BCShopItem item) {
        cosmeticService.toggle(player, item.getId());
    }
    
    @Override
    public void reload() {
        registry.reload();
        loadItems();
    }
}
