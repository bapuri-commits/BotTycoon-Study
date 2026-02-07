package kr.bapuri.tycoon.enhance.enchant;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.lamp.LampEffect;
import kr.bapuri.tycoon.enhance.lamp.LampSlotData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Animals;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import kr.bapuri.tycoon.job.JobRegistry;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.miner.MinerExpService;
import kr.bapuri.tycoon.enhance.processing.BlockProcessingService;
import kr.bapuri.tycoon.enhance.processing.BreakSource;

import java.util.*;

/**
 * EnchantEffectListener - 커스텀 인챈트 효과 발동
 * 
 * Phase 6: LITE 리팩토링
 * - 헌터/월드/HUD 의존성 제거
 * - LAST_STRIKE 비활성화
 * - 도구 인챈트 월드 제한 해제
 * 
 * 처리하는 인챈트:
 * - BLEED, THUNDER_STRIKE, FROST_ASPECT, VAMPIRE, TRUE_DAMAGE, TRIUMPH, GRIEVOUS_WOUNDS (무기)
 * - SPEED_BOOST, DOUBLE_JUMP, REGENERATION, TOUGHNESS, WATER_WALKER (방어구)
 * - VEIN_MINER, HARVEST, TELEKINESIS, WISDOM (도구)
 */
public class EnchantEffectListener implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();
    
    // 이단 점프 쿨다운 (UUID -> 점프 횟수)
    private final Map<UUID, Integer> doubleJumpCount = new HashMap<>();
    
    // 이단 점프를 위한 비행 모드 허용 플레이어
    private final Set<UUID> doubleJumpPlayers = new HashSet<>();
    
    // 광맥 채굴 처리 중인 블록 (중복 방지)
    private final Set<Location> processingVein = new HashSet<>();
    
    // BLEED 중첩 시스템
    private final Map<UUID, Integer> bleedStacks = new HashMap<>();
    private final Map<UUID, Long> lastBleedHitTime = new HashMap<>();
    private static final int MAX_BLEED_STACKS = 3;
    private static final long BLEED_STACK_WINDOW_MS = 2000;
    
    // GRIEVOUS_WOUNDS - 치유 감소 대상 추적
    private final Map<UUID, double[]> grievousWoundsTargets = new HashMap<>();
    
    // WATER_WALKER - 설치된 barrier 블록 추적
    private final Map<UUID, Set<Location>> waterWalkerBarriers = new HashMap<>();
    
    // VITALITY - 체력 증가 AttributeModifier 추적
    private final Map<UUID, AttributeModifier> vitalityModifiers = new HashMap<>();
    
    // STEADFAST - 넉백 저항 AttributeModifier 추적
    private final Map<UUID, AttributeModifier> steadfastModifiers = new HashMap<>();
    
    // SPEED_BOOST - 이동속도 증가 AttributeModifier 추적
    private final Map<UUID, AttributeModifier> speedBoostModifiers = new HashMap<>();
    
    // 활 인챈트 - 화살에 인챈트 정보 저장 (화살 UUID -> 인챈트 맵)
    private final Map<UUID, Map<String, Integer>> arrowEnchants = new HashMap<>();
    
    // 인챈트 레지스트리 (설정 조회용)
    private final CustomEnchantRegistry enchantRegistry;
    
    // 직업 경험치 연동용 (광맥 채굴 등)
    private final JobRegistry jobRegistry;
    
    // 블록 처리 파이프라인 (새 시스템)
    private BlockProcessingService blockProcessingService;

    public EnchantEffectListener(JavaPlugin plugin, CustomEnchantRegistry enchantRegistry, JobRegistry jobRegistry) {
        this.plugin = plugin;
        this.enchantRegistry = enchantRegistry;
        this.jobRegistry = jobRegistry;
        
        // 패시브 인챈트 효과 시작
        startNightVisionChecker();
        startRegenerationChecker();
    }
    
    /**
     * BlockProcessingService 주입 (TycoonPlugin에서 호출)
     */
    public void setBlockProcessingService(BlockProcessingService service) {
        this.blockProcessingService = service;
    }
    
    // ===============================================================
    // 방어구 변경 감지 - VITALITY/STEADFAST 적용
    // ===============================================================
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 접속 시 방어구 인챈트 효과 적용
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    applyVitalityEffect(player);
                    applySteadfastEffect(player);
                    applySpeedBoostEffect(player);
                }
            }
        }.runTaskLater(plugin, 20L); // 1초 후 적용 (아이템 로드 대기)
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }
    
    // ===============================================================
    // SPEED_BOOST - 이동속도 증가
    // ===============================================================
    
    /**
     * SPEED_BOOST 효과 적용 (방어구 착용 시 호출)
     */
    public void applySpeedBoostEffect(Player player) {
        int totalLevel = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null) {
                totalLevel += EnhanceItemUtil.getCustomEnchantLevel(armor, "speed_boost");
            }
        }
        
        if (totalLevel <= 0) {
            removeSpeedBoostModifier(player.getUniqueId());
            return;
        }
        
        // 레벨별 이동속도 보너스: 8%/15%/25%
        double speedBonus = CustomEnchant.SPEED_BOOST.getEffectValue(Math.min(totalLevel, 3));
        
        // 기존 모디파이어 제거
        removeSpeedBoostModifier(player.getUniqueId());
        
        // 새 모디파이어 추가
        AttributeModifier modifier = new AttributeModifier(
            UUID.randomUUID(),
            "enchant_speed_boost",
            speedBonus,
            AttributeModifier.Operation.ADD_SCALAR,
            EquipmentSlot.FEET
        );
        
        speedBoostModifiers.put(player.getUniqueId(), modifier);
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(modifier);
    }
    
    private void removeSpeedBoostModifier(UUID uuid) {
        AttributeModifier modifier = speedBoostModifiers.remove(uuid);
        if (modifier != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(modifier);
            }
        }
    }
    
    // ===============================================================
    // REGENERATION - 체력 재생 (3초마다)
    // ===============================================================
    
    /**
     * REGENERATION 효과 시작 (플러그인 시작 시 호출)
     */
    public void startRegenerationChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    int totalLevel = 0;
                    for (ItemStack armor : player.getInventory().getArmorContents()) {
                        if (armor != null) {
                            totalLevel += EnhanceItemUtil.getCustomEnchantLevel(armor, "regeneration");
                        }
                    }
                    
                    if (totalLevel > 0) {
                        // 레벨별 회복량: 1/2/3 HP
                        double healAmount = CustomEnchant.REGENERATION.getEffectValue(Math.min(totalLevel, 3));
                        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        double newHealth = Math.min(player.getHealth() + healAmount, maxHealth);
                        
                        if (player.getHealth() < maxHealth) {
                            player.setHealth(newHealth);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 60L, 60L); // 3초마다 체크
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        // 방어구 슬롯 변경 감지
        boolean isArmorSlot = event.getSlotType() == InventoryType.SlotType.ARMOR;
        boolean isShiftClick = event.isShiftClick() && isArmorItem(event.getCurrentItem());
        
        if (isArmorSlot || isShiftClick) {
            // 다음 틱에서 효과 재계산 (아이템 이동 완료 후)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        applyVitalityEffect(player);
                        applySteadfastEffect(player);
                        applySpeedBoostEffect(player);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }
    
    private boolean isArmorItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
               name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") ||
               name.equals("ELYTRA") || name.equals("TURTLE_HELMET");
    }
    
    /**
     * 조건부 메시지 전송 (Registry + 플레이어별 설정 조회)
     */
    private void sendEffectMessage(Player player, String message) {
        // 글로벌 설정 확인
        if (!enchantRegistry.isShowEffectMessages()) return;
        // 플레이어별 세션 설정 확인
        if (!kr.bapuri.tycoon.TycoonPlugin.getInstance().isEffectMsgEnabled(player.getUniqueId())) return;
        player.sendMessage(message);
    }

    // ===============================================================
    // 공격 관련 인챈트
    // ===============================================================

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) return;

        Map<String, Integer> enchants = EnhanceItemUtil.getCustomEnchants(weapon);
        if (enchants.isEmpty()) return;

        double damage = event.getDamage();
        double bonusDamage = 0;
        double trueDamage = 0;

        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String enchantId = entry.getKey();
            int level = entry.getValue();

            CustomEnchant enchant = CustomEnchant.fromId(enchantId);
            if (enchant == null) continue;

            switch (enchant) {
                case BLEED -> {
                    // 출혈 중첩 시스템
                    if (random.nextDouble() < 0.3) { // 30% 확률
                        UUID victimId = victim.getUniqueId();
                        UUID attackerId = player.getUniqueId();
                        long now = System.currentTimeMillis();
                        
                        Long lastHit = lastBleedHitTime.get(attackerId);
                        boolean canStack = lastHit != null && (now - lastHit) <= BLEED_STACK_WINDOW_MS;
                        
                        int currentStacks = bleedStacks.getOrDefault(victimId, 0);
                        int newStacks;
                        if (canStack && currentStacks < MAX_BLEED_STACKS) {
                            newStacks = currentStacks + 1;
                        } else if (currentStacks == 0) {
                            newStacks = 1;
                        } else {
                            newStacks = currentStacks;
                        }
                        bleedStacks.put(victimId, newStacks);
                        lastBleedHitTime.put(attackerId, now);
                        
                        double baseDuration = enchant.getEffectValue(level);
                        
                        double bonusDuration = 0;
                        if (hasDebuff(victim, PotionEffectType.SLOW) ||
                            hasDebuff(victim, PotionEffectType.POISON) ||
                            hasDebuff(victim, PotionEffectType.WEAKNESS) ||
                            hasDebuff(victim, PotionEffectType.BLINDNESS)) {
                            bonusDuration = 1.0;
                        }
                        
                        double totalDuration = (baseDuration + bonusDuration) * newStacks;
                        int durationTicks = (int) (totalDuration * 20);
                        
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, durationTicks, newStacks - 1));
                        
                        // 출혈 즉시 데미지 (다음 틱, 중첩 수에 비례) - 재귀 이벤트 방지
                        final int finalStacks = newStacks;
                        final LivingEntity finalVictim = victim;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (finalVictim.isValid() && !finalVictim.isDead()) {
                                    finalVictim.damage(1.0 * finalStacks, player);
                                }
                            }
                        }.runTaskLater(plugin, 1L);
                        
                        String stackMsg = newStacks > 1 ? " §7[" + newStacks + "중첩]" : "";
                        String bonusMsg = bonusDuration > 0 ? " §7[시너지]" : "";
                        sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§c출혈 부여!" + stackMsg + bonusMsg);
                        
                        final UUID finalVictimId = victimId;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                bleedStacks.remove(finalVictimId);
                            }
                        }.runTaskLater(plugin, durationTicks);
                    }
                }
                case THUNDER_STRIKE -> {
                    double chance = enchant.getEffectValue(level) / 100.0;
                    if (random.nextDouble() < chance) {
                        victim.getWorld().strikeLightningEffect(victim.getLocation());
                        
                        double maxVictimHealth = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        double thunderDamage = maxVictimHealth * 0.20;
                        
                        if (!(victim instanceof Player) && thunderDamage > 15.0) {
                            thunderDamage = 15.0;
                        }
                        
                        bonusDamage += thunderDamage;
                        sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + 
                            "§e⚡ 번개 강타! §c+" + String.format("%.1f", thunderDamage) + " 피해");
                    }
                }
                case FROST_ASPECT -> {
                    if (random.nextDouble() < 0.4) {
                        int duration = (int) (enchant.getEffectValue(level) * 20);
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration, level));
                        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 20);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§b❄ 서리 효과!");
                    }
                }
                case TRUE_DAMAGE -> {
                    boolean isPvP = victim instanceof Player;
                    double trueDamagePercent = isPvP 
                        ? enchant.getPvPEffectValue(level) 
                        : enchant.getEffectValue(level);
                    trueDamage = damage * (trueDamagePercent / 100.0);
                }
                case VAMPIRE -> {
                    boolean isPvPVampire = victim instanceof Player;
                    double lifestealPercent = isPvPVampire 
                        ? enchant.getPvPEffectValue(level) 
                        : enchant.getEffectValue(level);
                    
                    final double lifestealRate = lifestealPercent / 100.0;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!player.isValid() || player.isDead()) return;
                            double healAmount = damage * lifestealRate;
                            double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                            double newHealth = Math.min(player.getHealth() + healAmount, maxHealth);
                            player.setHealth(newHealth);
                            
                            if (healAmount >= 1.0) {
                                sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + 
                                    "§4흡혈! §a+" + String.format("%.1f", healAmount) + " HP");
                            }
                        }
                    }.runTaskLater(plugin, 1L);
                }
                case GRIEVOUS_WOUNDS -> {
                    double reduction = enchant.getEffectValue(level) / 100.0;
                    long expiryTime = System.currentTimeMillis() + 2000;
                    grievousWoundsTargets.put(victim.getUniqueId(), new double[]{reduction, expiryTime});
                    
                    sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + 
                        "§c치유 감소! §7(" + (int)(reduction * 100) + "%, 2초)");
                    
                    final UUID victimId = victim.getUniqueId();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            grievousWoundsTargets.remove(victimId);
                        }
                    }.runTaskLater(plugin, 40L);
                }
                case HUNTER -> {
                    // 동물에게 추가 피해 10%/20%/30%
                    if (victim instanceof Animals) {
                        double bonusPercent = enchant.getEffectValue(level);
                        bonusDamage += damage * (bonusPercent / 100.0);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§a사냥꾼! §7(+" + (int)bonusPercent + "%)");
                    }
                }
                case PRECISION -> {
                    // 치명타 피해 5%/10%/15% 증가 (점프 공격 시)
                    if (player.getFallDistance() > 0 && !player.isOnGround()) {
                        double bonusPercent = enchant.getEffectValue(level);
                        bonusDamage += damage * (bonusPercent / 100.0);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§e정밀 타격! §7(+" + (int)bonusPercent + "%)");
                    }
                }
                // LAST_STRIKE는 LITE에서 비활성화
                default -> {}
            }
        }

        event.setDamage(damage + bonusDamage);

        if (trueDamage > 0) {
            final double finalTrueDamage = trueDamage;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (victim.isValid() && !victim.isDead()) {
                        double newHealth = Math.max(0, victim.getHealth() - finalTrueDamage);
                        victim.setHealth(newHealth);
                        victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 10);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    // ===============================================================
    // 킬 관련 인챈트
    // ===============================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon == null) return;

        // 승전보 (TRIUMPH)
        if (EnhanceItemUtil.hasCustomEnchant(weapon, "triumph")) {
            double maxHealth = killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double currentHealth = killer.getHealth();
            double totalHeal;
            
            boolean isPvPKill = event.getEntity() instanceof Player;
            
            if (isPvPKill) {
                double baseHeal = 12.0;
                double missingHealth = maxHealth - currentHealth;
                double bonusHeal = missingHealth * 0.12;
                totalHeal = baseHeal + bonusHeal;
            } else {
                double minPercent = 0.10;
                double maxPercent = 0.20;
                double healPercent = minPercent + random.nextDouble() * (maxPercent - minPercent);
                totalHeal = maxHealth * healPercent;
            }
            
            double newHealth = Math.min(currentHealth + totalHeal, maxHealth);
            killer.setHealth(newHealth);
            
            sendEffectMessage(killer, EnhanceConstants.PREFIX_ENCHANT + 
                    "§6승전보! §a+" + String.format("%.1f", totalHeal) + " HP 회복!");
        }
    }

    // ===============================================================
    // 이동 관련 인챈트 - 이단 점프
    // ===============================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        boolean hasDoubleJump = false;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && EnhanceItemUtil.hasCustomEnchant(armor, "double_jump")) {
                hasDoubleJump = true;
                break;
            }
        }
        
        if (!hasDoubleJump) {
            if (doubleJumpPlayers.contains(uuid)) {
                doubleJumpPlayers.remove(uuid);
                doubleJumpCount.remove(uuid);
                GameMode gm = player.getGameMode();
                if (gm != GameMode.CREATIVE && gm != GameMode.SPECTATOR) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
            }
            return;
        }
        
        if (player.isOnGround()) {
            if (player.isFlying()) {
                player.setFlying(false);
            }
            
            doubleJumpCount.put(uuid, 1);
            doubleJumpPlayers.add(uuid);
            player.setAllowFlight(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (!doubleJumpPlayers.contains(uuid)) {
            return;
        }
        
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        if (player.isOnGround()) {
            event.setCancelled(true);
            return;
        }
        
        int jumpCount = doubleJumpCount.getOrDefault(uuid, 0);
        if (jumpCount <= 0) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            return;
        }
        
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        
        Vector velocity = player.getLocation().getDirection().multiply(0.4);
        velocity.setY(0.6);
        player.setVelocity(velocity);
        
        doubleJumpCount.put(uuid, jumpCount - 1);
        
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);
        
        sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§f이단 점프!");
    }
    
    // ===============================================================
    // TOUGHNESS - CC 지속시간 감소
    // ===============================================================
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED) return;
        
        PotionEffect newEffect = event.getNewEffect();
        if (newEffect == null) return;
        
        if (!isCCEffect(newEffect.getType())) return;
        
        int toughnessLevel = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null) {
                toughnessLevel += EnhanceItemUtil.getCustomEnchantLevel(armor, "toughness");
            }
        }
        
        if (toughnessLevel <= 0) return;
        
        double reduction = CustomEnchant.TOUGHNESS.getEffectValue(toughnessLevel) / 100.0;
        int originalDuration = newEffect.getDuration();
        int reducedDuration = (int) (originalDuration * (1.0 - reduction));
        
        if (reducedDuration < 1) reducedDuration = 1;
        
        event.setCancelled(true);
        PotionEffect reducedEffect = new PotionEffect(
            newEffect.getType(),
            reducedDuration,
            newEffect.getAmplifier(),
            newEffect.isAmbient(),
            newEffect.hasParticles(),
            newEffect.hasIcon()
        );
        player.addPotionEffect(reducedEffect, true);
    }
    
    private boolean isCCEffect(PotionEffectType type) {
        return type.equals(PotionEffectType.SLOW) ||
               type.equals(PotionEffectType.BLINDNESS) ||
               type.equals(PotionEffectType.CONFUSION) ||
               type.equals(PotionEffectType.LEVITATION) ||
               type.equals(PotionEffectType.SLOW_DIGGING) ||
               type.equals(PotionEffectType.WEAKNESS) ||
               type.equals(PotionEffectType.WITHER);
    }
    
    private boolean hasDebuff(LivingEntity entity, PotionEffectType type) {
        return entity.hasPotionEffect(type);
    }

    // ===============================================================
    // 채굴 관련 인챈트
    // ===============================================================

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        if (tool == null || tool.getType() == Material.AIR) return;

        Map<String, Integer> enchants = EnhanceItemUtil.getCustomEnchants(tool);
        if (enchants.isEmpty()) return;

        Block block = event.getBlock();

        // 광맥 채굴
        if (enchants.containsKey("vein_miner")) {
            int level = enchants.get("vein_miner");
            applyVeinMiner(event, player, tool, level);
            
            // 새 파이프라인 사용 시 원본 블록 효과 처리는 파이프라인에서 담당
            // 하지만 원본 블록(event.getBlock())은 바닐라에서 처리하므로
            // TELEKINESIS, EXPERTISE 등은 계속 적용
        }

        // 수확 (자동 재파종)
        if (enchants.containsKey("harvest")) {
            applyHarvest(event, player);
        }

        // 염동력 (텔레키네시스) - 새 파이프라인 미사용 시에만
        // (새 파이프라인에서는 DeliveryProcessor가 처리)
        if (enchants.containsKey("telekinesis")) {
            // 새 파이프라인이 활성화되어 있으면 원본 블록은 바닐라가 처리
            // VEIN_MINER 추가 블록만 새 파이프라인이 처리
            applyTelekinesis(event, player);
        }
        
        // EXPERTISE - 직업 활동 시 Fortune 추가
        // (새 파이프라인에서는 EnchantDropBonusProcessor가 처리)
        if (enchants.containsKey("expertise")) {
            int level = enchants.get("expertise");
            applyExpertise(event, player, tool, level);
        }
        
        // LUCKY_HAND - 희귀 드랍 확률 증가
        // (새 파이프라인에서는 EnchantDropBonusProcessor가 처리)
        if (enchants.containsKey("lucky_hand")) {
            int level = enchants.get("lucky_hand");
            double chance = CustomEnchant.LUCKY_HAND.getEffectValue(level) / 100.0;
            if (random.nextDouble() < chance) {
                // 추가 드랍
                for (ItemStack drop : block.getDrops(tool)) {
                    if (random.nextDouble() < 0.5) { // 50% 확률로 각 드랍 복제
                        ItemStack bonusDrop = drop.clone();
                        bonusDrop.setAmount(1);
                        if (enchants.containsKey("telekinesis")) {
                            player.getInventory().addItem(bonusDrop);
                        } else {
                            block.getWorld().dropItemNaturally(block.getLocation(), bonusDrop);
                        }
                        sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§a행운의 손!");
                        break;
                    }
                }
            }
        }
        
        // TREE_SPIRIT - 원목 채굴 시 묘목 드롭 확률 증가
        if (enchants.containsKey("tree_spirit")) {
            int level = enchants.get("tree_spirit");
            applyTreeSpirit(event, player, tool, level);
        }
        
        // ORE_SENSE - 채굴 시 주변 광석 위치 표시
        if (enchants.containsKey("ore_sense")) {
            int level = enchants.get("ore_sense");
            applyOreSense(player, block, level);
        }
    }
    
    private void applyExpertise(BlockBreakEvent event, Player player, ItemStack tool, int level) {
        Block block = event.getBlock();
        Material blockType = block.getType();
        
        // 광석이나 작물인 경우에만 적용
        if (!isOre(blockType) && !isFullyGrownCrop(block)) return;
        
        // Fortune 레벨만큼 추가 드랍 확률
        int fortuneBonus = (int) CustomEnchant.EXPERTISE.getEffectValue(level);
        
        for (int i = 0; i < fortuneBonus; i++) {
            if (random.nextDouble() < 0.33) { // 각 레벨당 33% 확률
                for (ItemStack drop : block.getDrops(tool)) {
                    ItemStack bonusDrop = drop.clone();
                    bonusDrop.setAmount(1);
                    if (EnhanceItemUtil.hasCustomEnchant(tool, "telekinesis")) {
                        player.getInventory().addItem(bonusDrop);
                    } else {
                        block.getWorld().dropItemNaturally(block.getLocation(), bonusDrop);
                    }
                }
                sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§e전문가!");
                break;
            }
        }
    }

    private void applyVeinMiner(BlockBreakEvent event, Player player, ItemStack tool, int level) {
        Block block = event.getBlock();
        Material blockType = block.getType();
        
        if (!isOre(blockType)) return;
        
        if (processingVein.contains(block.getLocation())) return;
        
        if (tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) return;

        int maxBlocks = (int) CustomEnchant.VEIN_MINER.getEffectValue(level);
        
        List<Block> vein = findConnectedBlocks(block, blockType, maxBlocks);
        
        vein.remove(block);
        
        if (vein.isEmpty()) return;
        
        // ===== 새 Block Processing 파이프라인 사용 =====
        if (blockProcessingService != null && blockProcessingService.isEnabled()) {
            // 광맥 채굴된 블록들을 파이프라인으로 처리
            for (Block veinBlock : vein) {
                processingVein.add(veinBlock.getLocation());
            }
            
            blockProcessingService.processBlocks(player, vein, tool, BreakSource.VEIN_MINER);
            
            for (Block veinBlock : vein) {
                processingVein.remove(veinBlock.getLocation());
            }
            
            sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§7광맥 채굴! " + (vein.size() + 1) + "개 블록 채굴");
            return;
        }
        
        // ===== 기존 로직 (fallback) =====
        // 램프 AUTO_SMELT 효과 확인
        boolean hasAutoSmelt = false;
        double autoSmeltChance = 0;
        List<LampSlotData> lampSlots = getLampEffectsWithLegacy(tool);
        for (LampSlotData slotData : lampSlots) {
            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
            if (effect != null && effect == LampEffect.AUTO_SMELT && !effect.isDisabled()) {
                hasAutoSmelt = true;
                autoSmeltChance = slotData.getValue1();
                break;
            }
        }

        int smeltedCount = 0;
        for (Block veinBlock : vein) {
            processingVein.add(veinBlock.getLocation());
            
            // 고대잔해는 확률 10%로 제한 (네더라이트 조각 복사 방지)
            double effectiveChance = (veinBlock.getType() == Material.ANCIENT_DEBRIS) 
                ? Math.min(autoSmeltChance, 10.0) : autoSmeltChance;
            boolean shouldSmelt = hasAutoSmelt && random.nextDouble() < (effectiveChance / 100.0);
            
            if (shouldSmelt) {
                Material smeltResult = getSmeltResult(veinBlock.getType());
                if (smeltResult != null) {
                    ItemStack smeltedDrop = new ItemStack(smeltResult);
                    if (EnhanceItemUtil.hasCustomEnchant(tool, "telekinesis") ||
                        hasLampEffect(tool, "telekinesis")) {
                        player.getInventory().addItem(smeltedDrop);
                    } else {
                        veinBlock.getWorld().dropItemNaturally(veinBlock.getLocation(), smeltedDrop);
                    }
                    smeltedCount++;
                } else {
                    dropNormal(veinBlock, tool, player);
                }
            } else {
                dropNormal(veinBlock, tool, player);
            }
            
            int exp = getOreExp(blockType);
            if (exp > 0) player.giveExp(exp);
            
            // 직업 경험치 부여 (광부)
            if (jobRegistry != null) {
                MinerExpService minerExpService = (MinerExpService) jobRegistry.getExpService(JobType.MINER);
                if (minerExpService != null) {
                    minerExpService.addMiningExp(player, veinBlock.getType(), 1);
                }
            }
            
            veinBlock.setType(Material.AIR);
            
            processingVein.remove(veinBlock.getLocation());
        }
        
        String smeltMsg = smeltedCount > 0 ? " §6(" + smeltedCount + "개 제련)" : "";
        sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§7광맥 채굴! " + (vein.size() + 1) + "개 블록 채굴" + smeltMsg);
    }
    
    private void dropNormal(Block veinBlock, ItemStack tool, Player player) {
        for (ItemStack drop : veinBlock.getDrops(tool)) {
            if (EnhanceItemUtil.hasCustomEnchant(tool, "telekinesis") ||
                hasLampEffect(tool, "telekinesis")) {
                player.getInventory().addItem(drop);
            } else {
                veinBlock.getWorld().dropItemNaturally(veinBlock.getLocation(), drop);
            }
        }
    }
    
    private List<LampSlotData> getLampEffectsWithLegacy(ItemStack item) {
        if (item == null || item.getType().isAir()) return Collections.emptyList();
        
        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(item);
        List<LampSlotData> active = new ArrayList<>();
        
        for (LampSlotData slot : slots) {
            if (!slot.isEmpty()) {
                active.add(slot);
            }
        }
        
        String legacyEffectId = EnhanceItemUtil.getLampEffect(item);
        if (legacyEffectId != null && active.isEmpty()) {
            LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
            if (legacyEffect != null && !legacyEffect.isDisabled()) {
                double avgValue1 = (legacyEffect.getMinValue1() + legacyEffect.getMaxValue1()) / 2.0;
                int avgValue2 = (legacyEffect.getMinValue2() + legacyEffect.getMaxValue2()) / 2;
                active.add(new LampSlotData(legacyEffectId, avgValue1, avgValue2));
            }
        }
        
        return active;
    }
    
    private boolean hasLampEffect(ItemStack item, String effectId) {
        if (item == null || item.getType().isAir()) return false;
        
        if (EnhanceItemUtil.hasLampSlotEffect(item, effectId)) return true;
        
        String legacyEffect = EnhanceItemUtil.getLampEffect(item);
        return effectId.equals(legacyEffect);
    }
    
    private Material getSmeltResult(Material material) {
        return switch (material) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            default -> null;
        };
    }

    private List<Block> findConnectedBlocks(Block start, Material type, int maxBlocks) {
        List<Block> result = new ArrayList<>();
        Queue<Block> queue = new LinkedList<>();
        Set<Location> visited = new HashSet<>();
        
        queue.add(start);
        visited.add(start.getLocation());
        
        int[][] directions = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {1, -1, 0}, {-1, 1, 0}, {-1, -1, 0},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, 1}, {0, 1, -1}, {0, -1, 1}, {0, -1, -1},
            {1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1},
            {-1, 1, 1}, {-1, 1, -1}, {-1, -1, 1}, {-1, -1, -1}
        };
        
        while (!queue.isEmpty() && result.size() < maxBlocks) {
            Block current = queue.poll();
            result.add(current);
            
            for (int[] dir : directions) {
                Block neighbor = current.getRelative(dir[0], dir[1], dir[2]);
                if (neighbor.getType() == type && !visited.contains(neighbor.getLocation())) {
                    visited.add(neighbor.getLocation());
                    queue.add(neighbor);
                }
            }
        }
        
        return result;
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    private int getOreExp(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 1;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 1;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 1;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> 2;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 2;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 3;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 5;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 5;
            case NETHER_QUARTZ_ORE -> 3;
            case ANCIENT_DEBRIS -> 10;
            default -> 0;
        };
    }

    private void applyHarvest(BlockBreakEvent event, Player player) {
        Block block = event.getBlock();
        Material blockType = block.getType();
        
        if (!isFullyGrownCrop(block)) return;
        
        Material seedType = getCropSeed(blockType);
        if (seedType == null) return;
        
        // 코코아의 경우 방향 정보 저장
        org.bukkit.block.BlockFace cocoaFace = null;
        if (blockType == Material.COCOA && block.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
            cocoaFace = directional.getFacing();
        }
        final org.bukkit.block.BlockFace savedFace = cocoaFace;
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (block.getType() == Material.AIR) {
                    Material plantType = getCropPlant(seedType);
                    block.setType(plantType);
                    
                    // 코코아의 경우 방향 복원
                    if (plantType == Material.COCOA && savedFace != null) {
                        if (block.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
                            directional.setFacing(savedFace);
                            block.setBlockData(directional);
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    private boolean isFullyGrownCrop(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }
        return false;
    }

    private Material getCropSeed(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            default -> null;
        };
    }

    private Material getCropPlant(Material seed) {
        return switch (seed) {
            case WHEAT_SEEDS -> Material.WHEAT;
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA_BEANS -> Material.COCOA;
            default -> Material.AIR;
        };
    }

    private void applyTelekinesis(BlockBreakEvent event, Player player) {
        event.setDropItems(false);
        for (ItemStack drop : event.getBlock().getDrops(player.getInventory().getItemInMainHand())) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    // ===============================================================
    // 활 인챈트 - 발사 시
    // ===============================================================
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        
        ItemStack bow = event.getBow();
        if (bow == null) return;
        
        Map<String, Integer> enchants = EnhanceItemUtil.getCustomEnchants(bow);
        if (enchants.isEmpty()) return;
        
        // 화살에 인챈트 정보 저장 (나중에 ProjectileHitEvent에서 사용)
        arrowEnchants.put(arrow.getUniqueId(), new HashMap<>(enchants));
        
        // QUICK_DRAW - 활 당기기 속도 증가 (이미 발사되었으므로 다음 발사에 적용)
        // 실제로는 Attribute로 처리해야 하지만, 여기서는 발사 속도 보너스로 처리
        if (enchants.containsKey("quick_draw")) {
            int level = enchants.get("quick_draw");
            double speedBonus = CustomEnchant.QUICK_DRAW.getEffectValue(level) / 100.0;
            // 화살 속도 증가
            arrow.setVelocity(arrow.getVelocity().multiply(1 + speedBonus * 0.5));
        }
        
        // SNIPER - 풀차지 시 추가 피해 (force 체크)
        if (enchants.containsKey("sniper") && event.getForce() >= 0.9f) {
            int level = enchants.get("sniper");
            double bonusPercent = CustomEnchant.SNIPER.getEffectValue(level);
            // 피해량은 ProjectileHitEvent에서 처리
            arrow.setDamage(arrow.getDamage() * (1 + bonusPercent / 100.0));
            sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§e저격 준비!");
        }
        
        // 10초 후 화살 인챈트 정보 정리
        final UUID arrowId = arrow.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                arrowEnchants.remove(arrowId);
            }
        }.runTaskLater(plugin, 200L);
    }
    
    // ===============================================================
    // 활 인챈트 - 명중 시
    // ===============================================================
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;
        if (event.getHitEntity() == null) return;
        if (!(event.getHitEntity() instanceof LivingEntity victim)) return;
        
        Map<String, Integer> enchants = arrowEnchants.get(arrow.getUniqueId());
        if (enchants == null || enchants.isEmpty()) return;
        
        // HUNTERS_EYE - 동물에게 추가 피해 15%/25%/35%
        if (enchants.containsKey("hunters_eye") && victim instanceof Animals) {
            int level = enchants.get("hunters_eye");
            double bonusPercent = CustomEnchant.HUNTERS_EYE.getEffectValue(level);
            double bonusDamage = arrow.getDamage() * (bonusPercent / 100.0);
            
            final double finalBonus = bonusDamage;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (victim.isValid() && !victim.isDead()) {
                        victim.damage(finalBonus, player);
                    }
                }
            }.runTaskLater(plugin, 1L);
            
            sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§a사냥꾼의 눈! §7(+" + (int)bonusPercent + "%)");
        }
        
        // FROST_ARROW - 명중 시 둔화 1/2/3초
        if (enchants.containsKey("frost_arrow")) {
            int level = enchants.get("frost_arrow");
            int duration = (int) (CustomEnchant.FROST_ARROW.getEffectValue(level) * 20);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, duration, 1));
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 15);
            sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§b❄ 서리 화살!");
        }
        
        // 화살 인챈트 정보 정리
        arrowEnchants.remove(arrow.getUniqueId());
    }

    // ===============================================================
    // 경험치 관련 인챈트
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        int baseExp = event.getAmount();
        if (baseExp <= 0) return;

        int wisdomLevel = 0;
        
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null) {
            wisdomLevel = Math.max(wisdomLevel, EnhanceItemUtil.getCustomEnchantLevel(mainHand, "wisdom"));
        }
        
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null) {
                wisdomLevel = Math.max(wisdomLevel, EnhanceItemUtil.getCustomEnchantLevel(armor, "wisdom"));
            }
        }

        if (wisdomLevel > 0) {
            double bonus = CustomEnchant.WISDOM.getEffectValue(wisdomLevel);
            int bonusExp = (int) (baseExp * bonus / 100.0);
            event.setAmount(baseExp + bonusExp);
        }
    }
    
    /**
     * 플레이어 정리 (로그아웃 시)
     */
    public void cleanupPlayer(UUID uuid) {
        doubleJumpPlayers.remove(uuid);
        doubleJumpCount.remove(uuid);
        grievousWoundsTargets.remove(uuid);
        cleanupWaterWalkerBarriers(uuid);
        removeVitalityModifier(uuid);
        removeSteadfastModifier(uuid);
        removeSpeedBoostModifier(uuid);
    }
    
    // ===============================================================
    // VITALITY - 최대 체력 증가
    // ===============================================================
    
    /**
     * VITALITY 효과 적용 (방어구 착용 시 호출)
     */
    public void applyVitalityEffect(Player player) {
        int totalLevel = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null) {
                totalLevel += EnhanceItemUtil.getCustomEnchantLevel(armor, "vitality");
            }
        }
        
        if (totalLevel <= 0) {
            removeVitalityModifier(player.getUniqueId());
            return;
        }
        
        double healthBonus = CustomEnchant.VITALITY.getEffectValue(totalLevel); // 2/4/6 HP
        
        // 기존 모디파이어 제거
        removeVitalityModifier(player.getUniqueId());
        
        // 새 모디파이어 추가
        AttributeModifier modifier = new AttributeModifier(
            UUID.randomUUID(),
            "enchant_vitality",
            healthBonus,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.CHEST
        );
        
        vitalityModifiers.put(player.getUniqueId(), modifier);
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).addModifier(modifier);
    }
    
    private void removeVitalityModifier(UUID uuid) {
        AttributeModifier modifier = vitalityModifiers.remove(uuid);
        if (modifier != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).removeModifier(modifier);
            }
        }
    }
    
    // ===============================================================
    // STEADFAST - 넉백 저항
    // ===============================================================
    
    /**
     * STEADFAST 효과 적용 (방어구 착용 시 호출)
     */
    public void applySteadfastEffect(Player player) {
        int totalLevel = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null) {
                totalLevel += EnhanceItemUtil.getCustomEnchantLevel(armor, "steadfast");
            }
        }
        
        if (totalLevel <= 0) {
            removeSteadfastModifier(player.getUniqueId());
            return;
        }
        
        double knockbackResist = CustomEnchant.STEADFAST.getEffectValue(totalLevel) / 100.0; // 0.15/0.30/0.45
        knockbackResist = Math.min(1.0, knockbackResist); // 최대 100%
        
        // 기존 모디파이어 제거
        removeSteadfastModifier(player.getUniqueId());
        
        // 새 모디파이어 추가
        AttributeModifier modifier = new AttributeModifier(
            UUID.randomUUID(),
            "enchant_steadfast",
            knockbackResist,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.CHEST
        );
        
        steadfastModifiers.put(player.getUniqueId(), modifier);
        player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).addModifier(modifier);
    }
    
    private void removeSteadfastModifier(UUID uuid) {
        AttributeModifier modifier = steadfastModifiers.remove(uuid);
        if (modifier != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).removeModifier(modifier);
            }
        }
    }
    
    // ===============================================================
    // NIGHT_VISION - 야간 투시
    // ===============================================================
    
    /**
     * NIGHT_VISION 효과 시작 (플러그인 시작 시 호출)
     */
    public void startNightVisionChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    boolean hasNightVision = false;
                    for (ItemStack armor : player.getInventory().getArmorContents()) {
                        if (armor != null && EnhanceItemUtil.hasCustomEnchant(armor, "night_vision")) {
                            hasNightVision = true;
                            break;
                        }
                    }
                    
                    if (hasNightVision) {
                        // 야간 투시 효과 부여 (13초, 10초마다 갱신하므로 항상 유지)
                        player.addPotionEffect(new PotionEffect(
                            PotionEffectType.NIGHT_VISION, 260, 0, false, false, true
                        ));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 200L); // 10초마다 체크
    }
    
    // ===============================================================
    // REPAIR_EFFICIENCY - 수선 비용 감소
    // ===============================================================
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        ItemStack firstItem = anvil.getItem(0);
        
        if (firstItem == null || firstItem.getType().isAir()) return;
        if (event.getResult() == null) return;
        
        int repairEfficiencyLevel = EnhanceItemUtil.getCustomEnchantLevel(firstItem, "repair_efficiency");
        if (repairEfficiencyLevel <= 0) return;
        
        // 비용 감소 10%/20%/30%
        double reduction = CustomEnchant.REPAIR_EFFICIENCY.getEffectValue(repairEfficiencyLevel) / 100.0;
        int originalCost = anvil.getRepairCost();
        int reducedCost = (int) Math.max(1, originalCost * (1 - reduction));
        
        anvil.setRepairCost(reducedCost);
    }
    
    // ===============================================================
    // GRIEVOUS_WOUNDS - 치유 감소
    // ===============================================================
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        
        UUID uuid = entity.getUniqueId();
        double[] data = grievousWoundsTargets.get(uuid);
        if (data == null) return;
        
        if (System.currentTimeMillis() > data[1]) {
            grievousWoundsTargets.remove(uuid);
            return;
        }
        
        double reduction = data[0];
        double originalHeal = event.getAmount();
        double reducedHeal = originalHeal * (1 - reduction);
        event.setAmount(reducedHeal);
        
        if (entity instanceof Player player) {
            player.sendMessage(EnhanceConstants.PREFIX_ENCHANT + 
                "§c치유 감소 중! §7(" + String.format("%.1f", originalHeal) + " → " + String.format("%.1f", reducedHeal) + ")");
        }
    }
    
    // ===============================================================
    // WATER_WALKER - 수면 보행
    // ===============================================================
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMoveWaterWalker(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType() == Material.AIR) {
            cleanupWaterWalkerBarriers(player.getUniqueId());
            return;
        }
        
        int waterWalkerLevel = EnhanceItemUtil.getCustomEnchantLevel(boots, "water_walker");
        if (waterWalkerLevel <= 0) {
            cleanupWaterWalkerBarriers(player.getUniqueId());
            return;
        }
        
        Location playerLoc = player.getLocation();
        Block blockBelow = playerLoc.clone().subtract(0, 0.1, 0).getBlock();
        Block blockAtFeet = playerLoc.getBlock();
        
        boolean onWater = blockBelow.getType() == Material.WATER || blockAtFeet.getType() == Material.WATER;
        boolean onLava = blockBelow.getType() == Material.LAVA || blockAtFeet.getType() == Material.LAVA;
        
        if (!onWater && !onLava) {
            cleanupWaterWalkerBarriers(player.getUniqueId());
            return;
        }
        
        if (onLava) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false));
        }
        
        Set<Location> barriers = waterWalkerBarriers.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        
        Iterator<Location> iter = barriers.iterator();
        while (iter.hasNext()) {
            Location barrierLoc = iter.next();
            if (barrierLoc.distance(playerLoc) > 3) {
                Block block = barrierLoc.getBlock();
                if (block.getType() == Material.BARRIER) {
                    block.setType(onLava ? Material.LAVA : Material.WATER);
                }
                iter.remove();
            }
        }
        
        Location surfaceLoc = findLiquidSurface(playerLoc);
        if (surfaceLoc != null) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Location barrierLoc = surfaceLoc.clone().add(dx, 0, dz);
                    Block block = barrierLoc.getBlock();
                    if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                        block.setType(Material.BARRIER);
                        barriers.add(barrierLoc);
                    }
                }
            }
        }
    }
    
    private Location findLiquidSurface(Location playerLoc) {
        Location check = playerLoc.clone();
        for (int y = 0; y >= -3; y--) {
            check.setY(playerLoc.getY() + y);
            Block block = check.getBlock();
            if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                Block above = block.getRelative(0, 1, 0);
                if (above.getType() == Material.AIR || above.getType() == Material.CAVE_AIR) {
                    return block.getLocation();
                }
            }
        }
        return null;
    }
    
    private void cleanupWaterWalkerBarriers(UUID uuid) {
        Set<Location> barriers = waterWalkerBarriers.remove(uuid);
        if (barriers == null) return;
        
        for (Location barrierLoc : barriers) {
            Block block = barrierLoc.getBlock();
            if (block.getType() == Material.BARRIER) {
                block.setType(Material.WATER);
            }
        }
    }
    
    // ===============================================================
    // TREE_SPIRIT - 숲의 정령 (묘목 드롭 확률 증가)
    // ===============================================================
    
    private void applyTreeSpirit(BlockBreakEvent event, Player player, ItemStack tool, int level) {
        Block block = event.getBlock();
        Material blockType = block.getType();
        
        // 원목만 적용
        if (!blockType.name().endsWith("_LOG") && !blockType.name().endsWith("_WOOD")) {
            return;
        }
        
        // 확률 계산: 15%/25%/35%
        double chance = CustomEnchant.TREE_SPIRIT.getEffectValue(level) / 100.0;
        
        if (random.nextDouble() < chance) {
            // 해당 나무 종류의 묘목 드롭
            Material saplingType = getSaplingForLog(blockType);
            if (saplingType != null) {
                ItemStack sapling = new ItemStack(saplingType, 1);
                
                Map<String, Integer> enchants = EnhanceItemUtil.getCustomEnchants(tool);
                if (enchants.containsKey("telekinesis")) {
                    player.getInventory().addItem(sapling);
                } else {
                    block.getWorld().dropItemNaturally(block.getLocation(), sapling);
                }
                
                sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + "§a숲의 정령이 묘목을 선물했습니다!");
            }
        }
    }
    
    /**
     * 원목 타입에 해당하는 묘목 반환
     */
    private Material getSaplingForLog(Material log) {
        String name = log.name();
        
        if (name.contains("OAK")) return Material.OAK_SAPLING;
        if (name.contains("SPRUCE")) return Material.SPRUCE_SAPLING;
        if (name.contains("BIRCH")) return Material.BIRCH_SAPLING;
        if (name.contains("JUNGLE")) return Material.JUNGLE_SAPLING;
        if (name.contains("ACACIA")) return Material.ACACIA_SAPLING;
        if (name.contains("DARK_OAK")) return Material.DARK_OAK_SAPLING;
        if (name.contains("MANGROVE")) return Material.MANGROVE_PROPAGULE;
        if (name.contains("CHERRY")) return Material.CHERRY_SAPLING;
        if (name.contains("AZALEA")) return Material.AZALEA;
        
        return null;
    }
    
    // ===============================================================
    // ORE_SENSE - 광맥 감지 (주변 광석 파티클 표시)
    // ===============================================================
    
    private void applyOreSense(Player player, Block brokenBlock, int level) {
        // 광석 채굴 시에만 작동
        if (!isOre(brokenBlock.getType())) return;
        
        // 탐지 범위: 5/7/10 블록
        int range = (int) CustomEnchant.ORE_SENSE.getEffectValue(level);
        
        Location center = brokenBlock.getLocation();
        
        // 비동기로 탐지 파티클 표시 (1틱 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                int found = 0;
                
                for (int x = -range; x <= range; x++) {
                    for (int y = -range; y <= range; y++) {
                        for (int z = -range; z <= range; z++) {
                            Block check = center.clone().add(x, y, z).getBlock();
                            
                            if (isOre(check.getType())) {
                                // 광석 위치에 파티클
                                Location particleLoc = check.getLocation().add(0.5, 0.5, 0.5);
                                
                                // 광석 종류에 따른 색상
                                Particle.DustOptions dust = getOreParticleColor(check.getType());
                                player.spawnParticle(Particle.REDSTONE, particleLoc, 3, 0.3, 0.3, 0.3, dust);
                                found++;
                                
                                if (found >= 10) break; // 최대 10개까지 표시
                            }
                        }
                        if (found >= 10) break;
                    }
                    if (found >= 10) break;
                }
                
                if (found > 0) {
                    sendEffectMessage(player, EnhanceConstants.PREFIX_ENCHANT + 
                                      "§e광맥 감지: §f주변에 " + found + "개 광석 발견!");
                }
            }
        }.runTaskLater(plugin, 1L);
    }
    
    /**
     * 광석 종류에 따른 파티클 색상
     */
    private Particle.DustOptions getOreParticleColor(Material ore) {
        String name = ore.name();
        
        if (name.contains("COAL")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(50, 50, 50), 1.0f);
        if (name.contains("IRON")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 180, 160), 1.0f);
        if (name.contains("COPPER")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 100, 70), 1.0f);
        if (name.contains("GOLD")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.0f);
        if (name.contains("REDSTONE")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 0, 0), 1.0f);
        if (name.contains("LAPIS")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 0, 200), 1.0f);
        if (name.contains("DIAMOND")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 230, 255), 1.0f);
        if (name.contains("EMERALD")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 200, 80), 1.0f);
        if (name.contains("QUARTZ")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 255), 1.0f);
        if (name.contains("ANCIENT_DEBRIS")) return new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 60, 50), 1.0f);
        
        return new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 150, 150), 1.0f);
    }
}
