package kr.bapuri.tycoon.cosmetic;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * 치장 아이템 레지스트리
 * 
 * cosmetics.yml에서 채팅 색상, 파티클, 발광 효과를 로드합니다.
 */
public class CosmeticRegistry {
    
    private final Plugin plugin;
    private final Logger logger;
    
    private final Map<String, CosmeticItem> chatColors = new LinkedHashMap<>();
    private final Map<String, CosmeticItem> particles = new LinkedHashMap<>();
    private final Map<String, CosmeticItem> glows = new LinkedHashMap<>();
    
    public CosmeticRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadFromConfig();
    }
    
    /**
     * 설정 리로드
     */
    public void reload() {
        loadFromConfig();
    }
    
    private void loadFromConfig() {
        chatColors.clear();
        particles.clear();
        glows.clear();
        
        File cosmeticsFile = new File(plugin.getDataFolder(), "cosmetics.yml");
        if (!cosmeticsFile.exists()) {
            plugin.saveResource("cosmetics.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(cosmeticsFile);
        
        // 기본값 로드
        InputStream defaultStream = plugin.getResource("cosmetics.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
        
        // 채팅 색상 로드
        loadChatColors(config.getConfigurationSection("chat_colors"));
        
        // 파티클 로드
        loadParticles(config.getConfigurationSection("particles"));
        
        // 발광 효과 로드
        loadGlows(config.getConfigurationSection("glows"));
        
        logger.info("[CosmeticRegistry] 치장 아이템 로드 완료: " +
                "채팅색상 " + chatColors.size() + "개, " +
                "파티클 " + particles.size() + "개, " +
                "발광 " + glows.size() + "개");
    }
    
    private void loadChatColors(ConfigurationSection section) {
        if (section == null) return;
        
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSec = section.getConfigurationSection(id);
            if (itemSec == null) continue;
            
            String name = itemSec.getString("name", id);
            String colorCode = itemSec.getString("color_code", "§f");
            long price = itemSec.getLong("price", 200);
            Material icon = parseMaterial(itemSec.getString("icon", "PAPER"), Material.PAPER);
            
            CosmeticItem item = new CosmeticItem(id, name, CosmeticType.CHAT_COLOR, price, icon);
            item.setColorCode(colorCode);
            chatColors.put(id, item);
        }
    }
    
    private void loadParticles(ConfigurationSection section) {
        if (section == null) return;
        
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSec = section.getConfigurationSection(id);
            if (itemSec == null) continue;
            
            String name = itemSec.getString("name", id);
            String particleTypeName = itemSec.getString("particle_type", "HEART");
            long price = itemSec.getLong("price", 1000);
            Material icon = parseMaterial(itemSec.getString("icon", "BLAZE_POWDER"), Material.BLAZE_POWDER);
            
            Particle particleType;
            try {
                particleType = Particle.valueOf(particleTypeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("[CosmeticRegistry] 알 수 없는 파티클 타입: " + particleTypeName);
                particleType = Particle.HEART;
            }
            
            CosmeticItem item = new CosmeticItem(id, name, CosmeticType.PARTICLE, price, icon);
            item.setParticleType(particleType);
            particles.put(id, item);
        }
    }
    
    private void loadGlows(ConfigurationSection section) {
        if (section == null) return;
        
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSec = section.getConfigurationSection(id);
            if (itemSec == null) continue;
            
            String name = itemSec.getString("name", id);
            String glowColor = itemSec.getString("glow_color", "WHITE");
            long price = itemSec.getLong("price", 800);
            Material icon = parseMaterial(itemSec.getString("icon", "GLOWSTONE_DUST"), Material.GLOWSTONE_DUST);
            
            CosmeticItem item = new CosmeticItem(id, name, CosmeticType.GLOW, price, icon);
            item.setGlowColor(glowColor);
            glows.put(id, item);
        }
    }
    
    private Material parseMaterial(String name, Material defaultMat) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultMat;
        }
    }
    
    // ========== 조회 메서드 ==========
    
    public CosmeticItem getChatColor(String id) {
        return chatColors.get(id);
    }
    
    public CosmeticItem getParticle(String id) {
        return particles.get(id);
    }
    
    public CosmeticItem getGlow(String id) {
        return glows.get(id);
    }
    
    public CosmeticItem get(String id) {
        CosmeticItem item = chatColors.get(id);
        if (item != null) return item;
        item = particles.get(id);
        if (item != null) return item;
        return glows.get(id);
    }
    
    public Collection<CosmeticItem> getAllChatColors() {
        return chatColors.values();
    }
    
    public Collection<CosmeticItem> getAllParticles() {
        return particles.values();
    }
    
    public Collection<CosmeticItem> getAllGlows() {
        return glows.values();
    }
    
    public Collection<CosmeticItem> getAll() {
        List<CosmeticItem> all = new ArrayList<>();
        all.addAll(chatColors.values());
        all.addAll(particles.values());
        all.addAll(glows.values());
        return all;
    }
}
