package kr.bapuri.tycoon.cosmetic.listener;

import kr.bapuri.tycoon.cosmetic.CosmeticItem;
import kr.bapuri.tycoon.cosmetic.CosmeticRegistry;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 채팅 색상 리스너
 * 
 * 플레이어가 활성화한 채팅 색상을 채팅 본문에만 적용합니다.
 * 닉네임/칭호는 TAB 플러그인이 별도로 처리합니다.
 */
public class ChatColorListener implements Listener {
    
    private static final Logger LOGGER = Logger.getLogger("Tycoon.ChatColor");
    
    // 무지개 색상 배열
    private static final TextColor[] RAINBOW_COLORS = {
            NamedTextColor.RED,
            NamedTextColor.GOLD,
            NamedTextColor.YELLOW,
            NamedTextColor.GREEN,
            NamedTextColor.AQUA,
            NamedTextColor.BLUE,
            NamedTextColor.LIGHT_PURPLE
    };
    
    private final Plugin plugin;
    private final PlayerDataManager dataManager;
    private final CosmeticRegistry registry;
    
    public ChatColorListener(Plugin plugin, PlayerDataManager dataManager, CosmeticRegistry registry) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.registry = registry;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerTycoonData data = dataManager.get(player);
        
        String chatColorId = data.getActiveChatColor();
        if (chatColorId == null) return;
        
        CosmeticItem item = registry.getChatColor(chatColorId);
        if (item == null) return;
        
        String colorCode = item.getColorCode();
        Component originalMessage = event.message();
        
        if ("RAINBOW".equalsIgnoreCase(colorCode)) {
            // 무지개 효과
            event.message(applyRainbow(originalMessage));
        } else {
            // 단색 적용
            TextColor textColor = parseColorCode(colorCode);
            if (textColor != null) {
                event.message(originalMessage.color(textColor));
            }
        }
    }
    
    /**
     * 무지개 색상 적용 (글자별로 색상 변경)
     */
    private Component applyRainbow(Component original) {
        String plainText = PlainTextComponentSerializer.plainText().serialize(original);
        
        Component result = Component.empty();
        int colorIndex = 0;
        
        for (char c : plainText.toCharArray()) {
            if (c == ' ') {
                result = result.append(Component.text(c));
            } else {
                TextColor color = RAINBOW_COLORS[colorIndex % RAINBOW_COLORS.length];
                result = result.append(Component.text(c).color(color));
                colorIndex++;
            }
        }
        
        return result;
    }
    
    /**
     * 마인크래프트 색상 코드(§X)를 TextColor로 변환
     */
    private TextColor parseColorCode(String colorCode) {
        if (colorCode == null || colorCode.length() < 2) return null;
        
        char code = colorCode.charAt(colorCode.length() - 1);
        
        return switch (code) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a', 'A' -> NamedTextColor.GREEN;
            case 'b', 'B' -> NamedTextColor.AQUA;
            case 'c', 'C' -> NamedTextColor.RED;
            case 'd', 'D' -> NamedTextColor.LIGHT_PURPLE;
            case 'e', 'E' -> NamedTextColor.YELLOW;
            case 'f', 'F' -> NamedTextColor.WHITE;
            default -> null;
        };
    }
}
