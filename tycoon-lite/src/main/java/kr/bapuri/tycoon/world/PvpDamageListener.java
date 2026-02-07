package kr.bapuri.tycoon.world;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.logging.Logger;

/**
 * PvpDamageListener - Wild PvP 데미지 배율 적용
 * 
 * Wild 월드에서 플레이어 간 PvP 데미지에 배율을 적용합니다.
 * 기본값: 10% (0.1) - config에서 설정 가능
 * 
 * /pvpdamage 명령어로 런타임 토글 가능:
 * - off (0%), 10%, 50%, 100%
 */
public class PvpDamageListener implements Listener {

    private final WorldManager worldManager;
    private final Logger logger;
    
    public PvpDamageListener(WorldManager worldManager, Logger logger) {
        this.worldManager = worldManager;
        this.logger = logger;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        // 피해자가 플레이어가 아니면 무시
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        
        // Wild 월드 확인 (오버월드, 네더, 엔드 포함)
        if (!worldManager.isWildWorld(victim.getWorld()) && 
            !worldManager.isWildLinkedWorld(victim.getWorld())) {
            return;
        }
        
        // 공격자 확인
        Player attacker = getAttacker(event);
        if (attacker == null) {
            return;
        }
        
        // PvP 완전 비활성화 시 데미지 0
        if (worldManager.isPvpDisabled()) {
            event.setCancelled(true);
            return;
        }
        
        // 데미지 배율 적용
        double multiplier = worldManager.getPvpDamageMultiplier();
        double originalDamage = event.getDamage();
        double adjustedDamage = originalDamage * multiplier;
        
        event.setDamage(adjustedDamage);
        
        // 디버그 로그 (선택적)
        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine(String.format("[PvP] %s -> %s: %.1f -> %.1f (%.0f%%)",
                attacker.getName(), victim.getName(), 
                originalDamage, adjustedDamage, multiplier * 100));
        }
    }
    
    /**
     * 공격자 플레이어 추출 (직접 공격 또는 투사체)
     */
    private Player getAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        
        if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player player) {
                return player;
            }
        }
        
        return null;
    }
}
