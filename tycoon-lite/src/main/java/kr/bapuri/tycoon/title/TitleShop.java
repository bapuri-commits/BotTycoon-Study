package kr.bapuri.tycoon.title;

import kr.bapuri.tycoon.bcshop.AbstractBCShop;
import kr.bapuri.tycoon.bcshop.BCShopItem;
import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 구매형 칭호 상점
 * 
 * BC(BottCoin)로 칭호를 구매할 수 있는 상점입니다.
 * 기존 LuckPermsTitleService를 재사용하여 칭호 장착/해제를 처리합니다.
 */
public class TitleShop extends AbstractBCShop {
    
    private final PurchasableTitleRegistry registry;
    private final LuckPermsTitleService titleService;
    
    public TitleShop(Plugin plugin, EconomyService economyService, PlayerDataManager dataManager,
                     PurchasableTitleRegistry registry, LuckPermsTitleService titleService) {
        super(plugin, economyService, dataManager, "title", "칭호 상점");
        this.registry = registry;
        this.titleService = titleService;
        
        loadItems();
    }
    
    /**
     * 레지스트리에서 아이템 로드
     */
    private void loadItems() {
        items.clear();
        
        for (PurchasableTitle title : registry.getAll()) {
            BCShopItem item = new BCShopItem(
                    title.getId(),
                    title.getColoredDisplayName(),
                    title.getDescription(),
                    title.getPrice(),
                    Material.NAME_TAG,  // 칭호 아이콘
                    "title"
            );
            items.add(item);
        }
        
        logger.info("[TitleShop] 아이템 로드 완료: " + items.size() + "개");
    }
    
    @Override
    public boolean hasItem(Player player, BCShopItem item) {
        PlayerTycoonData data = dataManager.get(player);
        return data.hasTitleUnlocked(item.getId());
    }
    
    @Override
    public boolean isItemActive(Player player, BCShopItem item) {
        PlayerTycoonData data = dataManager.get(player);
        String equippedTitle = data.getEquippedTitle();
        return item.getId().equals(equippedTitle);
    }
    
    @Override
    public void handlePurchase(Player player, BCShopItem item) {
        // 이미 보유 중인지 확인
        if (hasItem(player, item)) {
            player.sendMessage("§e이미 보유한 칭호입니다. 좌클릭으로 장착하세요.");
            return;
        }
        
        // BC 차감
        PurchasableTitle title = registry.get(item.getId());
        if (title == null) {
            player.sendMessage("§c칭호를 찾을 수 없습니다.");
            return;
        }
        
        if (!purchaseWithBC(player, title.getPrice(), title.getId(), "칭호 구매: " + title.getDisplayName())) {
            return;
        }
        
        // 칭호 해금
        PlayerTycoonData data = dataManager.get(player);
        data.unlockTitle(item.getId());
        
        player.sendMessage("");
        player.sendMessage("§6§l★ 칭호 구매 완료! ★");
        player.sendMessage("§e" + title.getColoredDisplayName());
        player.sendMessage("§7좌클릭으로 장착하세요!");
        player.sendMessage("");
    }
    
    @Override
    public void handleToggle(Player player, BCShopItem item) {
        PlayerTycoonData data = dataManager.get(player);
        String currentTitle = data.getEquippedTitle();
        
        if (item.getId().equals(currentTitle)) {
            // 현재 장착 중 -> 해제
            titleService.unequipTitle(player);
        } else {
            // 미장착 -> 장착
            titleService.equipTitle(player, item.getId());
        }
    }
    
    @Override
    public void reload() {
        registry.reload();
        loadItems();
    }
}
