package kr.bapuri.tycoon.cosmetic.listener;

import kr.bapuri.tycoon.cosmetic.CosmeticItem;
import kr.bapuri.tycoon.cosmetic.CosmeticRegistry;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

/**
 * 파티클/발광 효과 태스크
 * 
 * 주기적으로 실행되어 플레이어에게 파티클과 발광 효과를 적용합니다.
 */
public class ParticleTask extends BukkitRunnable {
    
    private static final Logger LOGGER = Logger.getLogger("Tycoon.ParticleTask");
    
    private final Plugin plugin;
    private final PlayerDataManager dataManager;
    private final CosmeticRegistry registry;
    
    private BukkitTask task;
    
    public ParticleTask(Plugin plugin, PlayerDataManager dataManager, CosmeticRegistry registry) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.registry = registry;
    }
    
    /**
     * 태스크 시작 (1초마다 실행)
     */
    public void start() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = runTaskTimer(plugin, 20L, 20L); // 1초마다
        LOGGER.info("[ParticleTask] 시작됨");
    }
    
    /**
     * 태스크 중지
     */
    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        LOGGER.info("[ParticleTask] 중지됨");
    }
    
    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                processPlayer(player);
            } catch (Exception e) {
                LOGGER.warning("[ParticleTask] 플레이어 처리 중 오류: " + player.getName() + " - " + e.getMessage());
            }
        }
    }
    
    private void processPlayer(Player player) {
        PlayerTycoonData data = dataManager.get(player);
        
        // 파티클 효과
        String particleId = data.getActiveParticle();
        if (particleId != null) {
            CosmeticItem item = registry.getParticle(particleId);
            if (item != null && item.getParticleType() != null) {
                Location loc = player.getLocation().add(0, 2.2, 0);
                player.getWorld().spawnParticle(
                        item.getParticleType(),
                        loc,
                        3,      // count
                        0.3,    // offsetX
                        0.1,    // offsetY
                        0.3,    // offsetZ
                        0.02    // speed
                );
            }
        }
        
        // 발광 효과 (Glowing 포션)
        String glowId = data.getActiveGlow();
        if (glowId != null) {
            CosmeticItem item = registry.getGlow(glowId);
            if (item != null) {
                // 2초 동안 발광 효과 (1초마다 갱신되므로 끊김 없음)
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING,
                        40,     // 2초 (40틱)
                        0,      // amplifier
                        false,  // ambient
                        false   // particles
                ));
            }
        }
    }
}
