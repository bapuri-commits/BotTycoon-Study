package kr.bapuri.tycoon.trade;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * TradeHistoryManager - 거래 기록 저장/조회
 * 
 * 파일 기반 영구 저장 (data/trade_history.yml)
 */
public class TradeHistoryManager {

    private final Plugin plugin;
    private final Logger logger;
    private final File historyFile;
    
    private final List<TradeHistoryEntry> recentHistory = new ArrayList<>();
    private static final int MAX_MEMORY_CACHE = 500;
    
    public TradeHistoryManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.historyFile = new File(dataFolder, "trade_history.yml");
        
        loadHistory();
    }
    
    public void saveTradeHistory(TradeHistoryEntry entry) {
        recentHistory.add(0, entry);
        
        while (recentHistory.size() > MAX_MEMORY_CACHE) {
            recentHistory.remove(recentHistory.size() - 1);
        }
        
        saveToFile(entry);
        
        logger.info("[거래 기록] " + entry.getPlayer1Name() + " <-> " + entry.getPlayer2Name() + 
                    " | 거래 ID: " + entry.getTradeId());
    }
    
    public List<TradeHistoryEntry> getPlayerHistory(UUID playerId, int limit) {
        return recentHistory.stream()
                .filter(entry -> entry.isParticipant(playerId))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    public List<TradeHistoryEntry> getRecentHistory(int limit) {
        return recentHistory.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    private void loadHistory() {
        if (!historyFile.exists()) {
            logger.info("[TradeHistory] 기록 파일 없음, 새로 생성됩니다.");
            return;
        }
        
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(historyFile);
            ConfigurationSection tradesSection = config.getConfigurationSection("trades");
            
            if (tradesSection == null) {
                return;
            }
            
            List<String> tradeIds = new ArrayList<>(tradesSection.getKeys(false));
            Collections.reverse(tradeIds);
            
            int loaded = 0;
            for (String tradeIdStr : tradeIds) {
                if (loaded >= MAX_MEMORY_CACHE) break;
                
                ConfigurationSection tradeSection = tradesSection.getConfigurationSection(tradeIdStr);
                if (tradeSection == null) continue;
                
                try {
                    TradeHistoryEntry entry = loadEntry(tradeSection);
                    if (entry != null) {
                        recentHistory.add(entry);
                        loaded++;
                    }
                } catch (Exception e) {
                    logger.warning("[TradeHistory] 기록 로드 실패: " + tradeIdStr + " - " + e.getMessage());
                }
            }
            
            logger.info("[TradeHistory] " + loaded + "건의 거래 기록 로드됨");
            
        } catch (Exception e) {
            logger.severe("[TradeHistory] 기록 파일 로드 실패: " + e.getMessage());
        }
    }
    
    private void saveToFile(TradeHistoryEntry entry) {
        try {
            YamlConfiguration config;
            if (historyFile.exists()) {
                config = YamlConfiguration.loadConfiguration(historyFile);
            } else {
                config = new YamlConfiguration();
            }
            
            String path = "trades." + entry.getTradeId().toString();
            
            config.set(path + ".timestamp", entry.getTimestamp());
            config.set(path + ".player1.uuid", entry.getPlayer1Id().toString());
            config.set(path + ".player1.name", entry.getPlayer1Name());
            config.set(path + ".player1.items", entry.getPlayer1ItemDescriptions());
            config.set(path + ".player1.bd", entry.getPlayer1Bd());
            config.set(path + ".player1.bc", entry.getPlayer1Bc());
            
            config.set(path + ".player2.uuid", entry.getPlayer2Id().toString());
            config.set(path + ".player2.name", entry.getPlayer2Name());
            config.set(path + ".player2.items", entry.getPlayer2ItemDescriptions());
            config.set(path + ".player2.bd", entry.getPlayer2Bd());
            config.set(path + ".player2.bc", entry.getPlayer2Bc());
            
            config.save(historyFile);
            
        } catch (IOException e) {
            logger.severe("[TradeHistory] 기록 저장 실패: " + e.getMessage());
        }
    }
    
    private TradeHistoryEntry loadEntry(ConfigurationSection section) {
        UUID tradeId = UUID.fromString(section.getName());
        long timestamp = section.getLong("timestamp");
        
        ConfigurationSection p1 = section.getConfigurationSection("player1");
        ConfigurationSection p2 = section.getConfigurationSection("player2");
        
        if (p1 == null || p2 == null) return null;
        
        UUID player1Id = UUID.fromString(p1.getString("uuid"));
        String player1Name = p1.getString("name");
        List<String> player1Items = p1.getStringList("items");
        long player1Bd = p1.getLong("bd");
        long player1Bc = p1.getLong("bc");
        
        UUID player2Id = UUID.fromString(p2.getString("uuid"));
        String player2Name = p2.getString("name");
        List<String> player2Items = p2.getStringList("items");
        long player2Bd = p2.getLong("bd");
        long player2Bc = p2.getLong("bc");
        
        return new TradeHistoryEntry(
                tradeId, timestamp,
                player1Id, player2Id,
                player1Name, player2Name,
                player1Items, player1Bd, player1Bc,
                player2Items, player2Bd, player2Bc
        );
    }
}
