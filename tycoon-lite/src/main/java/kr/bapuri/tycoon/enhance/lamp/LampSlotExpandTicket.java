package kr.bapuri.tycoon.enhance.lamp;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * LampSlotExpandTicket - 램프 슬롯 확장 티켓
 * 
 * v2.5 램프 시스템:
 * - 손에 든 장비 아이템의 램프 슬롯을 확장
 * - 비용: 25K -> 75K -> 250K (슬롯 수에 따라)
 * - 최대 4슬롯까지 확장 가능
 * 
 * Phase 6 LITE: 레거시 버전 이식
 */
public class LampSlotExpandTicket implements Listener {

    private final Plugin plugin;
    private final LampService lampService;
    private final EconomyService economyService;
    private final NamespacedKey ticketKey;

    // CustomModelData
    public static final int CMD_SLOT_EXPAND = 2070;

    // 레거시 호환을 위한 고정 네임스페이스
    private static final String LEGACY_NAMESPACE = "tycoon";
    
    @SuppressWarnings("deprecation")
    public LampSlotExpandTicket(Plugin plugin, LampService lampService, EconomyService economyService) {
        this.plugin = plugin;
        this.lampService = lampService;
        this.economyService = economyService;
        // 레거시 호환: "tycoon" 네임스페이스 고정 사용
        this.ticketKey = new NamespacedKey(LEGACY_NAMESPACE, "lamp_slot_expand_ticket");
    }

    /**
     * 슬롯 확장 티켓 아이템 생성
     */
    public ItemStack createTicket(int amount) {
        ItemStack ticket = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = ticket.getItemMeta();
        if (meta == null) return null;

        meta.setDisplayName("§b§l램프 슬롯 확장권");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7장비 아이템의 램프 슬롯을 확장합니다.");
        lore.add("");
        lore.add("§8─────────────────────");
        lore.add("§e확장 비용:");
        lore.add("§7  2번째 슬롯: §f25,000 BD");
        lore.add("§7  3번째 슬롯: §f75,000 BD");
        lore.add("§7  4번째 슬롯: §f250,000 BD");
        lore.add("§8─────────────────────");
        lore.add("");
        lore.add("§e장비를 손에 들고 이 아이템을 우클릭");
        lore.add("§c※ 최대 4슬롯까지 확장 가능");

        meta.setLore(lore);
        meta.setCustomModelData(CMD_SLOT_EXPAND);

        // PDC 마커
        meta.getPersistentDataContainer().set(ticketKey, PersistentDataType.BYTE, (byte) 1);

        ticket.setItemMeta(meta);
        return ticket;
    }

    /**
     * 슬롯 확장 티켓인지 확인
     * [Phase 8] CoreItemService에서 생성한 아이템도 호환
     */
    public boolean isSlotExpandTicket(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        
        // 1. 기존 PDC 체크 (LampSlotExpandTicket.createTicket()으로 생성)
        if (meta.getPersistentDataContainer().has(ticketKey, PersistentDataType.BYTE)) {
            return true;
        }
        
        // 2. CoreItemService 호환: CustomModelData 체크 (coreitem give로 생성)
        if (meta.hasCustomModelData() && meta.getCustomModelData() == CMD_SLOT_EXPAND) {
            return true;
        }
        
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT_CLICK")) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // 메인핸드에 티켓, 오프핸드에 장비
        if (isSlotExpandTicket(mainHand) && isExpandableItem(offHand)) {
            event.setCancelled(true);
            processExpand(player, offHand, mainHand);
            return;
        }

        // 오프핸드에 티켓, 메인핸드에 장비
        if (isSlotExpandTicket(offHand) && isExpandableItem(mainHand)) {
            event.setCancelled(true);
            processExpand(player, mainHand, offHand);
        }
    }

    /**
     * 확장 가능한 아이템인지 확인
     */
    private boolean isExpandableItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return EnhanceItemUtil.isWeapon(item) || EnhanceItemUtil.isArmor(item) || EnhanceItemUtil.isTool(item);
    }

    /**
     * 슬롯 확장 처리
     */
    private void processExpand(Player player, ItemStack targetItem, ItemStack ticketItem) {
        int currentSlots = EnhanceItemUtil.getLampSlotCount(targetItem);

        if (currentSlots >= EnhanceConstants.MAX_LAMP_SLOTS) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c이미 최대 슬롯(4개)에 도달했습니다!");
            return;
        }

        long cost = lampService.getSlotExpandCost(targetItem);
        if (cost < 0) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c확장할 수 없습니다.");
            return;
        }

        // 잔액 확인
        if (!economyService.hasBalance(player, cost)) {
            player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c잔액이 부족합니다!");
            player.sendMessage("§7필요 금액: §e" + String.format("%,d", cost) + " BD");
            return;
        }

        // 비용 차감
        economyService.withdraw(player, cost);

        // 티켓 소모
        ticketItem.setAmount(ticketItem.getAmount() - 1);

        // 슬롯 확장
        lampService.expandSlot(targetItem);

        int newSlots = EnhanceItemUtil.getLampSlotCount(targetItem);
        player.sendMessage(EnhanceConstants.PREFIX_LAMP + "§a램프 슬롯이 확장되었습니다!");
        player.sendMessage("§7슬롯: §e" + currentSlots + " → " + newSlots);
        player.sendMessage("§7비용: §e-" + String.format("%,d", cost) + " BD");

        if (newSlots < EnhanceConstants.MAX_LAMP_SLOTS) {
            long nextCost = lampService.getSlotExpandCost(targetItem);
            player.sendMessage("§8다음 확장 비용: " + String.format("%,d", nextCost) + " BD");
        } else {
            player.sendMessage("§6최대 슬롯에 도달했습니다!");
        }
    }
}
