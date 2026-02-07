package kr.bapuri.tycoon.enhance.lamp;

import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import kr.bapuri.tycoon.job.JobRegistry;
import kr.bapuri.tycoon.job.JobType;
import kr.bapuri.tycoon.job.miner.MinerExpService;
import kr.bapuri.tycoon.job.fisher.FisherExpService;
import kr.bapuri.tycoon.job.fisher.FishRarity;
import kr.bapuri.tycoon.job.farmer.FarmerExpService;
import kr.bapuri.tycoon.enhance.processing.BlockProcessingService;
import kr.bapuri.tycoon.enhance.processing.BreakSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LampEffectListener - 램프 효과 발동 리스너
 * 
 * v2.0 - Stage 8 Enhance System Formalization
 * - 새로운 램프 효과 지원 (57+개)
 * - 희귀도 기반 확률 시스템
 * 
 * Phase 6 LITE:
 * - Hunter 시스템 의존성 제거 (WorldManager, CombatTagManager, HunterSessionManager)
 * - 모든 월드에서 도구 효과 사용 가능
 * 
 * Phase 3: lamps.yml 분리
 * - 효과 활성화 여부는 LampRegistry.isEffectEnabled()에서 동적 조회
 * - 메시지 표시 여부는 LampRegistry.isShowEffectMessages()에서 조회
 */
public class LampEffectListener implements Listener {

    private final JavaPlugin plugin;
    private final LampService lampService;
    private final LampRegistry lampRegistry;
    private final EconomyService economyService;
    private final Random random = new Random();

    // 불사조 축복 쿨다운 (UUID -> 마지막 발동 시간)
    private final Map<UUID, Long> phoenixCooldowns = new HashMap<>();
    
    // 킬 모멘텀 버프 추적 (UUID -> 만료 시간)
    private final Map<UUID, Long> killMomentumBuffs = new ConcurrentHashMap<>();
    
    // 스프린트 버스트 추적 (UUID -> 마지막 스프린트 시작 시간)
    private final Map<UUID, Long> sprintBurstStarts = new ConcurrentHashMap<>();
    
    // 치명타 스택 추적 (UUID -> 스택 정보)
    private static class CritStack {
        int stacks = 0;
        long expiryTime = 0;
    }
    private final Map<UUID, CritStack> critStacks = new ConcurrentHashMap<>();
    
    // 체력 강화 AttributeModifier 추적 (UUID -> 수정자)
    private final Map<UUID, AttributeModifier> healthBoostModifiers = new ConcurrentHashMap<>();
    
    // 광전사 공격력 보너스 추적 (UUID -> 보너스 값)
    private final Map<UUID, Double> berserkerBonuses = new ConcurrentHashMap<>();
    
    // GUARDIAN_ANGEL 워모그 스타일 - 마지막 피해 시간 추적
    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();
    
    // GUARDIAN_ANGEL 활성 재생 추적
    private final Set<UUID> guardianAngelActive = ConcurrentHashMap.newKeySet();
    
    // DEATH_MARK - 연속 공격 추적 (UUID -> 공격 횟수)
    private final Map<UUID, int[]> deathMarkStacks = new ConcurrentHashMap<>(); // [count, victimId hashcode]
    
    // COMBO - 연속 공격 추적 (UUID -> [스택, 만료시간])
    private final Map<UUID, long[]> comboStacks = new ConcurrentHashMap<>();
    
    // AMBUSH - 마지막 공격 시간 추적
    private final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();
    
    // STEALTH - 은신 상태 추적
    private final Set<UUID> stealthActive = ConcurrentHashMap.newKeySet();
    
    // LAST_STAND - 쿨다운 추적
    private final Map<UUID, Long> lastStandCooldowns = new ConcurrentHashMap<>();
    
    // FOCUS - 집중 상태 추적 (UUID -> 활성화 여부)
    private final Set<UUID> focusReady = ConcurrentHashMap.newKeySet();
    
    // RAGE - 공격속도 모디파이어 추적
    private final Map<UUID, AttributeModifier> rageModifiers = new ConcurrentHashMap<>();
    
    // SPEED_SACRIFICE - 이동속도 감소 모디파이어 추적
    private final Map<UUID, AttributeModifier> speedSacrificeModifiers = new ConcurrentHashMap<>();
    
    // ABSORB - 보호막 추적 (UUID -> 보호막 양)
    private final Map<UUID, Double> absorbShields = new ConcurrentHashMap<>();
    
    // 활 램프 효과 - 화살 UUID -> 램프 효과 목록
    private final Map<UUID, List<LampSlotData>> arrowLampEffects = new ConcurrentHashMap<>();

    // 직업 경험치 연동용 (광역 채굴 등)
    private final JobRegistry jobRegistry;
    
    // 블록 처리 파이프라인 (새 시스템)
    private BlockProcessingService blockProcessingService;
    
    public LampEffectListener(JavaPlugin plugin, LampService lampService, LampRegistry lampRegistry, EconomyService economyService, JobRegistry jobRegistry) {
        this.plugin = plugin;
        this.lampService = lampService;
        this.lampRegistry = lampRegistry;
        this.economyService = economyService;
        this.jobRegistry = jobRegistry;
        
        // GUARDIAN_ANGEL 워모그 스타일 - 1초마다 체크
        startGuardianAngelChecker();
        
        // STEALTH, FOCUS, RAGE 체크 - 1초마다
        startPassiveEffectChecker();
    }
    
    /**
     * BlockProcessingService 주입 (TycoonPlugin에서 호출)
     */
    public void setBlockProcessingService(BlockProcessingService service) {
        this.blockProcessingService = service;
    }
    
    /**
     * STEALTH, FOCUS, RAGE 등 패시브 효과 체크
     */
    private void startPassiveEffectChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    
                    // 마지막 공격/피격 시간 확인
                    Long lastAtk = lastAttackTime.get(uuid);
                    Long lastDmg = lastDamageTime.get(uuid);
                    boolean noCombatFor5Seconds = (lastAtk == null || now - lastAtk >= 5000) && 
                                                   (lastDmg == null || now - lastDmg >= 5000);
                    
                    // 방어구에서 효과 확인
                    for (ItemStack armor : player.getInventory().getArmorContents()) {
                        if (armor == null || armor.getType().isAir()) continue;
                        
                        List<LampSlotData> slots = getActiveEffectsWithLegacy(armor);
                        for (LampSlotData slotData : slots) {
                            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
                            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
                            
                            // STEALTH - 5초간 피격 없을 시 은신
                            if (effect == LampEffect.STEALTH && noCombatFor5Seconds) {
                                if (!stealthActive.contains(uuid)) {
                                    stealthActive.add(uuid);
                                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, false));
                                    sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§7은신 활성화!");
                                }
                            }
                            
                            // FOCUS - 5초간 공격/피격 없을 시 집중 준비
                            if (effect == LampEffect.FOCUS && noCombatFor5Seconds) {
                                if (!focusReady.contains(uuid)) {
                                    focusReady.add(uuid);
                                    sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§d집중 준비 완료!");
                                }
                            }
                            
                            // RAGE - 저체력 시 공격속도 증가
                            if (effect == LampEffect.RAGE) {
                                double healthPercent = player.getHealth() / player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 100;
                                if (healthPercent <= slotData.getValue1()) {
                                    if (!rageModifiers.containsKey(uuid)) {
                                        double speedBonus = slotData.getValue2() / 100.0;
                                        AttributeModifier modifier = new AttributeModifier(
                                            UUID.randomUUID(),
                                            "lamp_rage",
                                            speedBonus,
                                            AttributeModifier.Operation.ADD_SCALAR,
                                            EquipmentSlot.CHEST
                                        );
                                        rageModifiers.put(uuid, modifier);
                                        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).addModifier(modifier);
                                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§c격노!");
                                    }
                                } else {
                                    // 체력 회복 시 효과 제거
                                    AttributeModifier modifier = rageModifiers.remove(uuid);
                                    if (modifier != null) {
                                        player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).removeModifier(modifier);
                                    }
                                }
                            }
                            
                            // SPEED_SACRIFICE - 이동속도 감소 적용 (상시)
                            if (effect == LampEffect.SPEED_SACRIFICE) {
                                if (!speedSacrificeModifiers.containsKey(uuid)) {
                                    double speedReduction = -slotData.getValue1() / 100.0; // 음수로 감소
                                    AttributeModifier modifier = new AttributeModifier(
                                        UUID.randomUUID(),
                                        "lamp_speed_sacrifice",
                                        speedReduction,
                                        AttributeModifier.Operation.ADD_SCALAR,
                                        EquipmentSlot.CHEST
                                    );
                                    speedSacrificeModifiers.put(uuid, modifier);
                                    player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(modifier);
                                }
                            }
                        }
                    }
                    
                    // SPEED_SACRIFICE 효과가 없는 경우 제거
                    boolean hasSpeedSacrifice = false;
                    for (ItemStack armor : player.getInventory().getArmorContents()) {
                        if (armor == null || armor.getType().isAir()) continue;
                        List<LampSlotData> slots = getActiveEffectsWithLegacy(armor);
                        for (LampSlotData slotData : slots) {
                            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
                            if (effect == LampEffect.SPEED_SACRIFICE) {
                                hasSpeedSacrifice = true;
                                break;
                            }
                        }
                        if (hasSpeedSacrifice) break;
                    }
                    if (!hasSpeedSacrifice) {
                        AttributeModifier modifier = speedSacrificeModifiers.remove(uuid);
                        if (modifier != null) {
                            player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(modifier);
                        }
                    }
                    
                    // 전투 중이면 은신/집중 해제
                    if (!noCombatFor5Seconds) {
                        stealthActive.remove(uuid);
                        focusReady.remove(uuid);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
    
    /**
     * 조건부 효과 메시지 전송 (Registry + 플레이어별 설정 조회)
     */
    private void sendEffectMessage(Player player, String message) {
        // 글로벌 설정 확인
        if (!lampRegistry.isShowEffectMessages()) return;
        // 플레이어별 세션 설정 확인
        if (!kr.bapuri.tycoon.TycoonPlugin.getInstance().isEffectMsgEnabled(player.getUniqueId())) return;
        player.sendMessage(message);
    }
    
    /**
     * 인벤토리에 아이템 추가 (오버플로우 시 바닥 드롭)
     * 램프 효과 보너스 드롭용 - 메시지 없이 조용히 드롭
     */
    private void giveItemSilently(Player player, ItemStack item) {
        java.util.HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack dropped : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        }
    }
    
    /**
     * GUARDIAN_ANGEL 워모그 스타일 체크 태스크
     * 5초간 피해 없을 시 초당 3~10% 회복
     */
    private void startGuardianAngelChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    
                    // 방어구에서 GUARDIAN_ANGEL 효과 확인 (다중 슬롯 지원)
                    double healPercent = 0;
                    for (ItemStack armor : player.getInventory().getArmorContents()) {
                        if (armor == null || armor.getType().isAir()) continue;
                        
                        List<LampSlotData> slots = getActiveEffectsWithLegacy(armor);
                        for (LampSlotData slotData : slots) {
                            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
                            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
                            
                            if (effect == LampEffect.GUARDIAN_ANGEL) {
                                healPercent += slotData.getValue1(); // 다중 슬롯 합산
                            }
                        }
                    }
                    
                    if (healPercent <= 0) continue;
                    
                    // 5초간 피해 없었는지 확인
                    Long lastDmg = lastDamageTime.get(uuid);
                    boolean noDamageFor5Seconds = (lastDmg == null || now - lastDmg >= 5000);
                    
                    if (noDamageFor5Seconds) {
                        // 워모그 스타일 회복
                        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        double healAmount = maxHealth * (healPercent / 100.0);
                        heal(player, healAmount);
                        
                        if (!guardianAngelActive.contains(uuid)) {
                            guardianAngelActive.add(uuid);
                            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§f수호천사 재생 활성화!");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1초마다
    }

    // ===============================================================
    // 공격 관련 효과
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;

        // 다중 슬롯 시스템 + 레거시 호환
        List<LampSlotData> activeSlots = getActiveEffects(weapon);
        String legacyEffectId = EnhanceItemUtil.getLampEffect(weapon);
        
        // 레거시 효과가 있고 슬롯에 없으면 추가
        if (legacyEffectId != null && activeSlots.isEmpty()) {
            LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
            if (legacyEffect != null && lampRegistry.isEffectEnabled(legacyEffect.getId())) {
                activeSlots = List.of(new LampSlotData(legacyEffectId, 
                    legacyEffect.getMinValue1(), legacyEffect.getMinValue2()));
            }
        }
        
        if (activeSlots.isEmpty()) return;

        double damage = event.getDamage();

        // 모든 활성 슬롯의 효과 적용
        for (LampSlotData slotData : activeSlots) {
            String effectId = slotData.getEffectId();
            LampEffect effect = LampEffect.fromId(effectId);
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;

            double rolledValue1 = slotData.getValue1();
            int rolledValue2 = slotData.getValue2();

            switch (effect) {
                case GIANT_SLAYER -> {
                    // 거인 학살자 - 체력 차이 기반 추가 피해
                    double playerMaxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    double victimMaxHealth = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    double healthDiff = victimMaxHealth - playerMaxHealth;
                    if (healthDiff > 0) {
                        double bonusPercent = Math.min(35, (healthDiff / 20.0) * rolledValue1);
                        double bonusDamage = damage * (bonusPercent / 100.0);
                        damage += bonusDamage;
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§4거인 학살자! §7(+" + String.format("%.1f", bonusPercent) + "%%)");
                    }
                }
                case PHANTOM_STRIKE -> {
                    // 유령 일격 - 3~7% 확률로 방어력 무시
                    if (random.nextDouble() < rolledValue1 / 100.0) {
                        event.setDamage(damage);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§7유령 일격!");
                    }
                }
                case POISON_BLADE -> {
                    // 독날 - 2~5초 독 효과
                    int duration = (int) (rolledValue1 * 20);
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 1));
                }
                case EXECUTE -> {
                    // 처형 - 10~80% 확률로 체력 8% 미만 즉사
                    if (!(victim instanceof Boss) && random.nextDouble() < rolledValue1 / 100.0) {
                        double healthPercent = victim.getHealth() / victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        if (healthPercent < 0.08) {
                            victim.setHealth(0);
                            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§4처형!");
                        }
                    }
                }
                case ATTACK_BOOST -> {
                    // 공격력 강화 - +1~10
                    damage += rolledValue1;
                }
                case EXPLOIT_WEAKNESS -> {
                    // 약점 간파 - 5~10% 추가 데미지
                    double currentHealth = victim.getHealth();
                    double maxHealth = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    if (currentHealth < maxHealth) {
                        damage *= (1 + rolledValue1 / 100.0);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§9약점 간파!");
                    }
                }
                case LIFESTEAL -> {
                    // 생명력 흡수 - 1~10 고정 HP 회복
                    heal(player, rolledValue1);
                }
                // COMBAT_INSTINCT는 LITE에서 비활성화 (CombatTagManager 필요)
                case BERSERKER -> {
                    // 광전사 - 체력 15~45% 이하일 때 공격력 3~7 증가
                    double healthPercent = player.getHealth() / player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                    if (healthPercent <= (rolledValue1 / 100.0)) {
                        double bonus = rollValue(effect.getMinValue2(), effect.getMaxValue2());
                        damage += bonus;
                        berserkerBonuses.put(player.getUniqueId(), bonus);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§c광전사!");
                    }
                }
                case CHAIN_LIGHTNING -> {
                    // 연쇄 번개 - 10~25% 확률로 주변 2~3명에게 번개
                    if (random.nextDouble() < rolledValue1 / 100.0) {
                        int chainCount = rolledValue2;
                        List<Entity> nearby = victim.getNearbyEntities(5, 5, 5);
                        int chained = 0;
                        for (Entity e : nearby) {
                            if (e instanceof LivingEntity target && e != player && chained < chainCount) {
                                target.getWorld().strikeLightningEffect(target.getLocation());
                                target.damage(damage * 0.3, player);
                                chained++;
                            }
                        }
                        if (chained > 0) {
                            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§9연쇄 번개! §7(" + chained + "명)");
                        }
                    }
                }
                case DEATH_MARK -> {
                    // 죽음의 표식 - 3회 연속 공격 시 15~30% 추가 피해
                    UUID victimId = victim.getUniqueId();
                    int[] data = deathMarkStacks.computeIfAbsent(player.getUniqueId(), k -> new int[]{0, 0});
                    if (data[1] == victimId.hashCode()) {
                        data[0]++;
                    } else {
                        data[0] = 1;
                        data[1] = victimId.hashCode();
                    }
                    if (data[0] >= 3) {
                        double bonusPercent = rolledValue1;
                        damage *= (1 + bonusPercent / 100.0);
                        data[0] = 0;
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§4죽음의 표식!");
                    }
                }
                case AMBUSH -> {
                    // 기습 - 은신/스프린트 중 첫 공격 시 20~40% 추가 피해
                    Long lastAtk = lastAttackTime.get(player.getUniqueId());
                    boolean isFirstAttack = lastAtk == null || (System.currentTimeMillis() - lastAtk) > 3000;
                    if (isFirstAttack && (player.isSprinting() || stealthActive.contains(player.getUniqueId()))) {
                        double bonusPercent = rolledValue1;
                        damage *= (1 + bonusPercent / 100.0);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§7기습!");
                    }
                    lastAttackTime.put(player.getUniqueId(), System.currentTimeMillis());
                }
                case COMBO -> {
                    // 연속 공격 - 1.5초 내 연속 공격 시 3~6% 피해 증가 (최대 4~5중첩)
                    long now = System.currentTimeMillis();
                    long[] comboData = comboStacks.computeIfAbsent(player.getUniqueId(), k -> new long[]{0, 0});
                    if (now < comboData[1]) {
                        comboData[0] = Math.min(rolledValue2, comboData[0] + 1);
                    } else {
                        comboData[0] = 1;
                    }
                    comboData[1] = now + 1500;
                    double bonusPercent = rolledValue1 * comboData[0];
                    damage *= (1 + bonusPercent / 100.0);
                    if (comboData[0] > 1) {
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§e연속 공격! §7(" + comboData[0] + "콤보)");
                    }
                }
                case FOCUS -> {
                    // 집중 - 비전투 시 다음 공격 치명타 확률 증가 (별도 처리)
                    if (focusReady.remove(player.getUniqueId())) {
                        damage *= 1.5; // 집중 상태에서 치명타
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§d집중 타격!");
                    }
                }
                default -> {}
            }
        }

        // 치명타 시스템 처리 (모든 무기 효과 확인 후)
        damage = applyCritSystem(player, weapon, damage, victim);

        event.setDamage(damage);
    }

    /**
     * 치명타 시스템 적용
     * 무기와 방어구의 CRIT_CHANCE, CRIT_DAMAGE, CRIT_STACK 효과를 모두 고려
     */
    private double applyCritSystem(Player attacker, ItemStack weapon, double baseDamage, LivingEntity victim) {
        double totalCritChance = 0;
        double totalCritDamage = 0;
        LampSlotData critStackSlot = null;
        
        // 무기에서 치명타 관련 효과 수집 (다중 슬롯 지원)
        List<LampSlotData> weaponSlots = getActiveEffectsWithLegacy(weapon);
        for (LampSlotData slotData : weaponSlots) {
            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
            
            if (effect == LampEffect.CRIT_CHANCE) {
                totalCritChance += slotData.getValue1();
            } else if (effect == LampEffect.CRIT_DAMAGE) {
                totalCritDamage += slotData.getValue1();
            } else if (effect == LampEffect.CRIT_STACK) {
                critStackSlot = slotData;
            }
        }
        
        // 방어구에서 치명타 확률 수집 (다중 슬롯 지원)
        for (ItemStack armor : attacker.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            
            List<LampSlotData> armorSlots = getActiveEffectsWithLegacy(armor);
            for (LampSlotData slotData : armorSlots) {
                LampEffect effect = LampEffect.fromId(slotData.getEffectId());
                if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
                
                if (effect == LampEffect.CRIT_CHANCE) {
                    totalCritChance += slotData.getValue1();
                }
            }
        }
        
        // 치명타 발생 여부 결정
        if (totalCritChance > 0 && random.nextDouble() < (totalCritChance / 100.0)) {
            // 치명타 발생!
            double critMultiplier = 1.5 + (totalCritDamage / 100.0); // 기본 1.5배 + 보너스
            
            // CRIT_STACK 효과 적용 (무기의 슬롯 사용)
            if (critStackSlot != null) {
                CritStack stack = critStacks.computeIfAbsent(attacker.getUniqueId(), k -> new CritStack());
                stack.stacks = Math.min(5, stack.stacks + 1); // 최대 5중첩
                double stackDuration = critStackSlot.getValue1();
                stack.expiryTime = System.currentTimeMillis() + (long)(stackDuration * 1000);
                
                double stackBonus = critStackSlot.getValue2();
                critMultiplier += (stack.stacks * stackBonus / 100.0);
            }
            
            baseDamage *= critMultiplier;
            attacker.sendMessage(EnhanceConstants.PREFIX_LAMP + "§e치명타! §7(×" + String.format("%.2f", critMultiplier) + ")");
        }
        
        return baseDamage;
    }
    
    /**
     * 아이템의 모든 활성 램프 효과 목록 (다중 슬롯 + 레거시 호환 통합)
     */
    private List<LampSlotData> getActiveEffectsWithLegacy(ItemStack item) {
        if (item == null || item.getType().isAir()) return Collections.emptyList();
        
        List<LampSlotData> active = new ArrayList<>(getActiveEffects(item));
        
        // 레거시 효과 확인 (다중 슬롯에 없는 경우만 추가)
        String legacyEffectId = EnhanceItemUtil.getLampEffect(item);
        if (legacyEffectId != null && active.isEmpty()) {
            LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
            if (legacyEffect != null && lampRegistry.isEffectEnabled(legacyEffect.getId())) {
                double avgValue1 = (legacyEffect.getMinValue1() + legacyEffect.getMaxValue1()) / 2.0;
                int avgValue2 = (legacyEffect.getMinValue2() + legacyEffect.getMaxValue2()) / 2;
                active.add(new LampSlotData(legacyEffectId, avgValue1, avgValue2));
            }
        }
        
        return active;
    }

    // ===============================================================
    // 활 램프 효과 - 발사 시
    // ===============================================================
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBowShootLamp(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        
        ItemStack bow = event.getBow();
        if (bow == null || !EnhanceItemUtil.isBow(bow)) return;
        
        List<LampSlotData> activeSlots = getActiveEffectsWithLegacy(bow);
        if (activeSlots.isEmpty()) return;
        
        // 화살에 램프 효과 저장
        arrowLampEffects.put(arrow.getUniqueId(), new ArrayList<>(activeSlots));
        
        // SPLIT_ARROW - 분열 화살
        for (LampSlotData slotData : activeSlots) {
            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
            
            if (effect == LampEffect.SPLIT_ARROW) {
                if (random.nextDouble() < slotData.getValue1() / 100.0) {
                    int splitCount = slotData.getValue2();
                    for (int i = 0; i < splitCount - 1; i++) {
                        Arrow splitArrow = player.launchProjectile(Arrow.class);
                        Vector velocity = arrow.getVelocity().clone();
                        velocity.rotateAroundY(Math.toRadians((i + 1) * 10 - 5));
                        splitArrow.setVelocity(velocity.multiply(0.9));
                        splitArrow.setDamage(arrow.getDamage() * 0.5);
                    }
                    sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§9분열 화살!");
                }
            }
        }
        
        // 10초 후 정리
        final UUID arrowId = arrow.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                arrowLampEffects.remove(arrowId);
            }
        }.runTaskLater(plugin, 200L);
    }
    
    // ===============================================================
    // 활 램프 효과 - 명중 시
    // ===============================================================
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrowHitLamp(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;
        if (event.getHitEntity() == null) return;
        if (!(event.getHitEntity() instanceof LivingEntity victim)) return;
        
        List<LampSlotData> activeSlots = arrowLampEffects.get(arrow.getUniqueId());
        if (activeSlots == null || activeSlots.isEmpty()) return;
        
        for (LampSlotData slotData : activeSlots) {
            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
            
            double rolledValue1 = slotData.getValue1();
            
            switch (effect) {
                case EXPLOSIVE_ARROW -> {
                    // 폭발 화살 - 10~25% 확률로 폭발
                    if (random.nextDouble() < rolledValue1 / 100.0) {
                        victim.getWorld().createExplosion(victim.getLocation(), 1.5f, false, false, player);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§c폭발 화살!");
                    }
                }
                case VAMPIRIC_ARROW -> {
                    // 흡혈 화살 - 피해의 5~15% 회복
                    double healAmount = arrow.getDamage() * (rolledValue1 / 100.0);
                    heal(player, healAmount);
                    if (healAmount >= 0.5) {
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§c흡혈 화살! §a+" + String.format("%.1f", healAmount));
                    }
                }
                case POISON_ARROW -> {
                    // 독 화살 - 2~5초 독
                    int duration = (int) (rolledValue1 * 20);
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0));
                }
                case THUNDER_ARROW -> {
                    // 번개 화살 - 15~30% 확률로 번개
                    if (random.nextDouble() < rolledValue1 / 100.0) {
                        victim.getWorld().strikeLightningEffect(victim.getLocation());
                        victim.damage(5, player);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§e번개 화살!");
                    }
                }
                case PIERCING_SHOT -> {
                    // 관통 사격 - 10~25% 확률로 방어력 무시
                    if (random.nextDouble() < rolledValue1 / 100.0) {
                        final double bonusDamage = arrow.getDamage() * 0.3;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (victim.isValid() && !victim.isDead()) {
                                    double newHealth = Math.max(0, victim.getHealth() - bonusDamage);
                                    victim.setHealth(newHealth);
                                }
                            }
                        }.runTaskLater(plugin, 1L);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§7관통!");
                    }
                }
                default -> {}
            }
        }
        
        arrowLampEffects.remove(arrow.getUniqueId());
    }

    // ===============================================================
    // 방어 관련 효과
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        
        // GUARDIAN_ANGEL 워모그 스타일 - 피해 시간 기록
        lastDamageTime.put(player.getUniqueId(), System.currentTimeMillis());
        guardianAngelActive.remove(player.getUniqueId()); // 피해 받으면 재생 중단

        double damage = event.getDamage();

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;

            // 다중 슬롯 시스템 + 레거시 호환
            List<LampSlotData> activeSlots = getActiveEffects(armor);
            String legacyEffectId = EnhanceItemUtil.getLampEffect(armor);
            
            if (legacyEffectId != null && activeSlots.isEmpty()) {
                LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
                if (legacyEffect != null && lampRegistry.isEffectEnabled(legacyEffect.getId())) {
                    activeSlots = List.of(new LampSlotData(legacyEffectId, 
                        legacyEffect.getMinValue1(), legacyEffect.getMinValue2()));
                }
            }

            for (LampSlotData slotData : activeSlots) {
                String effectId = slotData.getEffectId();
                LampEffect effect = LampEffect.fromId(effectId);
                if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;

                double rolledValue1 = slotData.getValue1();

                switch (effect) {
                    case IRON_WILL -> {
                        // 강철 의지: 피해 10~30% 감소
                        damage *= (1 - rolledValue1 / 100.0);
                    }
                    case THORNS_AURA -> {
                        // 가시 오라: 10~20% 피해 반사
                        if (event instanceof EntityDamageByEntityEvent damageEvent) {
                            if (damageEvent.getDamager() instanceof LivingEntity attacker) {
                                double reflectDamage = damage * (rolledValue1 / 100.0);
                                attacker.damage(reflectDamage);
                            }
                        }
                    }
                    case DODGE -> {
                        // 회피: 10~40% 확률로 피해 무효화
                        if (random.nextDouble() < rolledValue1 / 100.0) {
                            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§a회피!");
                            event.setCancelled(true);
                            return;
                        }
                    }
                    case REGEN_PROC -> {
                        // 재생 발동 - 5~15% 확률로 재생 I 2초
                        if (random.nextDouble() < rolledValue1 / 100.0) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0));
                            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§e재생 발동!");
                        }
                    }
                    case SPEED_SACRIFICE -> {
                        // 속도 희생 - 이속 10~20% 감소, 피해 15~25% 감소
                        int rolledValue2Sacrifice = slotData.getValue2();
                        damage *= (1 - rolledValue2Sacrifice / 100.0);
                    }
                    case SHATTER_RESIST -> {
                        // 파쇄 저항 - 폭발 피해 15~35% 감소
                        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                            event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                            damage *= (1 - rolledValue1 / 100.0);
                            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§7파쇄 저항!");
                        }
                    }
                    case COUNTER -> {
                        // 반격 - 피격 시 15~30% 확률로 0.5초간 공격력 25~50% 증가
                        if (random.nextDouble() < rolledValue1 / 100.0) {
                            int rolledValue2Counter = slotData.getValue2();
                            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 10, 0));
                            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§e반격! §7(+" + rolledValue2Counter + "%)");
                        }
                    }
                    case FIRST_AID -> {
                        // 응급 처치 - 체력 20~35% 이하일 때 재생 자동 부여
                        double healthPercent = player.getHealth() / player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        if (healthPercent <= (rolledValue1 / 100.0)) {
                            if (!player.hasPotionEffect(PotionEffectType.REGENERATION)) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0));
                                sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§c응급 처치!");
                            }
                        }
                    }
                    default -> {}
                }
            }
        }

        event.setDamage(Math.max(0, damage));
    }

    // ===============================================================
    // 불사조 축복 & LAST_STAND (부활/무적)
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getHealth() - event.getFinalDamage() > 0) return;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;

            List<LampSlotData> slots = getActiveEffectsWithLegacy(armor);
            for (LampSlotData slotData : slots) {
                LampEffect effect = LampEffect.fromId(slotData.getEffectId());
                if (effect == null) continue;
                
                if (effect == LampEffect.PHOENIX_BLESSING) {
                    // 쿨다운 체크
                    long cooldownMs = (long) (slotData.getValue1() * 1000);
                    Long lastUse = phoenixCooldowns.get(player.getUniqueId());
                    if (lastUse != null && System.currentTimeMillis() - lastUse < cooldownMs) {
                        long remaining = (cooldownMs - (System.currentTimeMillis() - lastUse)) / 1000;
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§c불사조의 축복 쿨다운: " + remaining + "초");
                        continue;
                    }

                    // 부활!
                    event.setCancelled(true);

                    int healPercent = slotData.getValue2();
                    double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();

                    player.setHealth(maxHealth * (healPercent / 100.0));
                    phoenixCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

                    sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§6불사조의 축복으로 부활했습니다!");
                    return;
                }
                
                if (effect == LampEffect.LAST_STAND) {
                    // 쿨다운 체크 (120초)
                    long cooldownMs = 120000;
                    Long lastUse = lastStandCooldowns.get(player.getUniqueId());
                    if (lastUse != null && System.currentTimeMillis() - lastUse < cooldownMs) {
                        continue;
                    }
                    
                    // 최후의 저항 발동!
                    event.setCancelled(true);
                    
                    player.setHealth(1.0);
                    lastStandCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                    
                    // 무적 + 이동속도 증가
                    int duration = (int) (slotData.getValue1() * 20);
                    int speedBonus = slotData.getValue2();
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, duration, 4)); // 무적
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speedBonus / 20));
                    
                    sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§6최후의 저항!");
                    return;
                }
            }
        }
    }

    // ===============================================================
    // 몹 처치 관련 효과
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon == null) return;

        List<LampSlotData> activeSlots = getActiveEffects(weapon);
        String legacyEffectId = EnhanceItemUtil.getLampEffect(weapon);
        
        if (legacyEffectId != null && activeSlots.isEmpty()) {
            LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
            if (legacyEffect != null && lampRegistry.isEffectEnabled(legacyEffect.getId())) {
                activeSlots = List.of(new LampSlotData(legacyEffectId, 
                    legacyEffect.getMinValue1(), legacyEffect.getMinValue2()));
            }
        }
        
        if (activeSlots.isEmpty()) return;

        LivingEntity victim = event.getEntity();
        EntityType type = victim.getType();

        for (LampSlotData slotData : activeSlots) {
            String effectId = slotData.getEffectId();
            LampEffect effect = LampEffect.fromId(effectId);
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;

            double rolledValue1 = slotData.getValue1();
            int rolledValue2 = slotData.getValue2();

            boolean shouldApply = switch (effect) {
                case POULTRY_LOOT -> type == EntityType.CHICKEN || type == EntityType.RABBIT;
                case LIVESTOCK_LOOT -> type == EntityType.PIG || type == EntityType.SHEEP;
                case COW_LOOT -> type == EntityType.COW || type == EntityType.MUSHROOM_COW;
                case MONSTER_LOOT -> victim instanceof Monster;
                default -> false;
            };

            if (shouldApply && random.nextDouble() < rolledValue1 / 100.0) {
                List<ItemStack> drops = event.getDrops();
                if (!drops.isEmpty()) {
                    ItemStack bonusDrop = drops.get(random.nextInt(drops.size())).clone();
                    bonusDrop.setAmount(rolledValue2);
                    event.getDrops().add(bonusDrop);
                    killer.sendMessage(EnhanceConstants.PREFIX_LAMP + "§a추가 전리품 획득!");
                }
            }
            
            // 킬 모멘텀 - 몹 처치에도 적용
            if (effect == LampEffect.KILL_MOMENTUM) {
                applyKillMomentum(killer, effect, slotData);
            }
            
            // ABSORB - 킬 시 보호막 획득
            if (effect == LampEffect.ABSORB) {
                double victimMaxHealth = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                double shieldAmount = victimMaxHealth * (rolledValue1 / 100.0);
                int duration = rolledValue2;
                
                // 보호막 양에 따라 ABSORPTION 레벨 결정 (4HP = 1레벨)
                int absorptionLevel = Math.min(4, (int) (shieldAmount / 4.0)); // 최대 레벨 4 (16 HP)
                
                // 흡수 효과로 보호막 부여
                killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration * 20, absorptionLevel));
                sendEffectMessage(killer, EnhanceConstants.PREFIX_LAMP + 
                    "§a흡수! §7(+" + String.format("%.1f", (absorptionLevel + 1) * 4.0) + " HP 보호막, " + duration + "초)");
            }
        }
    }
    
    /**
     * 킬 모멘텀 효과 적용 (플레이어/몹 킬 공통)
     */
    private void applyKillMomentum(Player killer, LampEffect effect, LampSlotData slotData) {
        double rolledValue1 = slotData != null ? slotData.getValue1() : rollValue(effect.getMinValue1(), effect.getMaxValue1());
        long durationMs = (long)(rolledValue1 * 1000);
        long expiryTime = System.currentTimeMillis() + durationMs;
        
        killMomentumBuffs.put(killer.getUniqueId(), expiryTime);
        
        // 이동속도 +30% 버프
        killer.addPotionEffect(new PotionEffect(
            PotionEffectType.SPEED,
            (int)(rolledValue1 * 20),
            1, // 레벨 2 = +30% 속도
            false, false, true
        ));
        
        killer.sendMessage(EnhanceConstants.PREFIX_LAMP + "§c킬 모멘텀! §7(" + (int)rolledValue1 + "초)");
        
        // 만료 체크 태스크
        new BukkitRunnable() {
            @Override
            public void run() {
                Long expiry = killMomentumBuffs.get(killer.getUniqueId());
                if (expiry == null || System.currentTimeMillis() >= expiry) {
                    killMomentumBuffs.remove(killer.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
    
    /**
     * 플레이어 킬 시 킬 모멘텀 효과 적용
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon == null) return;
        
        List<LampSlotData> activeSlots = getActiveEffects(weapon);
        String legacyEffectId = EnhanceItemUtil.getLampEffect(weapon);
        
        if (legacyEffectId != null && activeSlots.isEmpty()) {
            LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
            if (legacyEffect != null && lampRegistry.isEffectEnabled(legacyEffect.getId())) {
                activeSlots = List.of(new LampSlotData(legacyEffectId, 
                    legacyEffect.getMinValue1(), legacyEffect.getMinValue2()));
            }
        }
        
        for (LampSlotData slotData : activeSlots) {
            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
            
            if (effect == LampEffect.KILL_MOMENTUM) {
                applyKillMomentum(killer, effect, slotData);
            }
        }
    }

    // ===============================================================
    // 채굴 관련 효과
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (tool == null || tool.getType().isAir()) return;

        List<LampSlotData> activeSlots = getActiveEffects(tool);
        String legacyEffectId = EnhanceItemUtil.getLampEffect(tool);
        
        if (legacyEffectId != null && activeSlots.isEmpty()) {
            LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
            if (legacyEffect != null && lampRegistry.isEffectEnabled(legacyEffect.getId())) {
                activeSlots = List.of(new LampSlotData(legacyEffectId, 
                    legacyEffect.getMinValue1(), legacyEffect.getMinValue2()));
            }
        }
        
        if (activeSlots.isEmpty()) return;

        Block block = event.getBlock();
        
        for (LampSlotData slotData : activeSlots) {
            String effectId = slotData.getEffectId();
            LampEffect effect = LampEffect.fromId(effectId);
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;

            double rolledValue1 = slotData.getValue1();

            switch (effect) {
                case AUTO_SMELT -> {
                    // 자동 제련 - 10~100% 확률 (고대잔해는 10% 고정)
                    if (!tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) {
                        Material smeltResult = getSmeltResult(block.getType());
                        if (smeltResult != null) {
                            // 고대잔해는 확률 10%로 제한 (네더라이트 조각 복사 방지)
                            double effectiveChance = (block.getType() == Material.ANCIENT_DEBRIS) 
                                ? Math.min(rolledValue1, 10.0) : rolledValue1;
                            if (random.nextDouble() < effectiveChance / 100.0) {
                                event.setDropItems(false);
                                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(smeltResult));
                                sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§6자동 제련!");
                            }
                        }
                    }
                }
                case MULTI_MINE -> {
                    // 광역 채굴: 상하좌우 1칸 동시 채굴 (LITE: 모든 월드에서 사용 가능)
                    applyMultiMine(event, player, tool, 1);
                }
                case MULTI_MINE_2 -> {
                    // 광역 채굴 II: 3×3 영역 동시 채굴 (LITE: 모든 월드에서 사용 가능)
                    applyMultiMine(event, player, tool, 2);
                }
                case SAND_TO_GLASS -> {
                    // 유리 세공 - 10~100% 확률
                    if (block.getType() == Material.SAND || block.getType() == Material.RED_SAND) {
                        if (random.nextDouble() < rolledValue1 / 100.0) {
                            event.setDropItems(false);
                            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.GLASS));
                            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§b유리 세공!");
                        }
                    }
                }
                case TREE_FELLER_1, TREE_FELLER_2, TREE_FELLER_3 -> {
                    // 벌목꾼: 위쪽 나무 동시 채굴
                    int height = (int) effect.getMinValue1();
                    applyTreeFeller(event, player, tool, height);
                }
                case MIDAS_TOUCH -> {
                    // 미다스의 손: 광물 채굴 시 BD 획득 (50~200 BD)
                    if (isOre(block.getType()) && random.nextDouble() < rolledValue1 / 100.0) {
                        int bdAmount = random.nextInt(151) + 50; // 50~200 (기존 300~1000에서 하향)
                        economyService.deposit(player, bdAmount);
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§6미다스의 손! +" + bdAmount + " BD");
                    }
                }
                case GOLDEN_TOUCH -> {
                    // 황금 손길 - 채굴 시 5~15% 확률로 금 조각 1~3개
                    if (random.nextDouble() < rolledValue1 / 100.0) {
                        int amount = slotData.getValue2();
                        giveItemSilently(player, new ItemStack(Material.GOLD_NUGGET, amount));
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§6황금 손길!");
                    }
                }
                case LUMBER_TRADER -> {
                    // 목재상 - 원목 채굴 시 10~30% 확률로 추가 1~2개
                    if (isLog(block.getType()) && random.nextDouble() < rolledValue1 / 100.0) {
                        int amount = slotData.getValue2();
                        giveItemSilently(player, new ItemStack(block.getType(), amount));
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§6목재상!");
                    }
                }
                default -> {}
            }
        }
    }

    /**
     * 벌목꾼 효과 적용
     */
    private void applyTreeFeller(BlockBreakEvent event, Player player, ItemStack tool, int maxHeight) {
        Block block = event.getBlock();
        if (!isLog(block.getType())) return;

        Material logType = block.getType();
        
        // 위쪽 나무 블록 수집
        List<Block> logBlocks = new ArrayList<>();
        for (int i = 1; i <= maxHeight; i++) {
            Block above = block.getRelative(0, i, 0);
            if (above.getType() != logType) break;
            logBlocks.add(above);
        }
        
        if (logBlocks.isEmpty()) return;
        
        // ===== 새 Block Processing 파이프라인 사용 =====
        if (blockProcessingService != null && blockProcessingService.isEnabled()) {
            blockProcessingService.processBlocks(player, logBlocks, tool, BreakSource.TREE_FELLER);
            return;
        }
        
        // ===== 기존 로직 (fallback) =====
        boolean hasTelekinesis = EnhanceItemUtil.hasCustomEnchant(tool, "telekinesis") ||
                                 hasLampEffectOnTool(tool, "telekinesis");
        
        for (Block logBlock : logBlocks) {
            for (ItemStack drop : logBlock.getDrops(tool)) {
                if (hasTelekinesis) {
                    giveItemSilently(player, drop);
                } else {
                    logBlock.getWorld().dropItemNaturally(logBlock.getLocation(), drop);
                }
            }
            logBlock.setType(Material.AIR);
        }
    }
    
    /**
     * 도구에 특정 램프 효과가 있는지 확인 (다중 슬롯 + 레거시)
     */
    private boolean hasLampEffectOnTool(ItemStack tool, String effectId) {
        if (tool == null || tool.getType().isAir()) return false;
        
        if (EnhanceItemUtil.hasLampSlotEffect(tool, effectId)) return true;
        
        String legacyEffect = EnhanceItemUtil.getLampEffect(tool);
        return effectId.equals(legacyEffect);
    }

    // ===============================================================
    // 농사 관련 효과
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (tool == null || !EnhanceItemUtil.isHoe(tool)) return;

        List<LampSlotData> activeSlots = getActiveEffects(tool);
        String legacyEffectId = EnhanceItemUtil.getLampEffect(tool);
        
        if (legacyEffectId != null && activeSlots.isEmpty()) {
            LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
            if (legacyEffect != null && lampRegistry.isEffectEnabled(legacyEffect.getId())) {
                activeSlots = List.of(new LampSlotData(legacyEffectId, 
                    legacyEffect.getMinValue1(), legacyEffect.getMinValue2()));
            }
        }

        for (LampSlotData slotData : activeSlots) {
            String effectId = slotData.getEffectId();
            LampEffect effect = LampEffect.fromId(effectId);
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;

            double rolledValue1 = slotData.getValue1();
            int rolledValue2 = slotData.getValue2();

            // CROP_BONUS 효과들
            if (effectId.startsWith("crop_bonus_")) {
                if (random.nextDouble() < rolledValue1 / 100.0) {
                    List<ItemStack> drops = event.getItemsHarvested();
                    if (!drops.isEmpty()) {
                        ItemStack bonusDrop = drops.get(0).clone();
                        bonusDrop.setAmount(rolledValue2);
                        giveItemSilently(player, bonusDrop);
                        
                        // 농부 직업 경험치 추가 부여
                        if (jobRegistry != null) {
                            FarmerExpService farmerExpService = (FarmerExpService) jobRegistry.getExpService(JobType.FARMER);
                            if (farmerExpService != null) {
                                farmerExpService.addHarvestExp(player, bonusDrop.getType(), rolledValue2);
                            }
                        }
                    }
                }
            }
            // SEED_BONUS 효과들
            else if (effectId.startsWith("seed_bonus_")) {
                if (random.nextDouble() < rolledValue1 / 100.0) {
                    Material seedType = getCropSeed(event.getHarvestedBlock().getType());
                    if (seedType != null) {
                        giveItemSilently(player, new ItemStack(seedType, rolledValue2));
                    }
                }
            }
            // 뼛가루 드랍
            else if (effect == LampEffect.BONE_MEAL_DROP) {
                if (random.nextDouble() < rolledValue1 / 100.0) {
                    giveItemSilently(player, new ItemStack(Material.BONE_MEAL, rolledValue2));
                }
            }
            // BOUNTIFUL - 풍년: 자동 재파종 (씨앗 소모 없이)
            else if (effect == LampEffect.BOUNTIFUL) {
                if (random.nextDouble() < rolledValue1 / 100.0) {
                    Block harvestBlock = event.getHarvestedBlock();
                    Material cropType = harvestBlock.getType();
                    
                    // 코코아의 경우 방향 정보 저장
                    org.bukkit.block.BlockFace cocoaFace = null;
                    if (cropType == Material.COCOA && harvestBlock.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
                        cocoaFace = directional.getFacing();
                    }
                    final org.bukkit.block.BlockFace savedFace = cocoaFace;
                    
                    // 수확 후 자동 재파종
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (harvestBlock.getType() == Material.AIR) {
                                harvestBlock.setType(cropType);
                                
                                // 코코아의 경우 방향 복원
                                if (cropType == Material.COCOA && savedFace != null) {
                                    if (harvestBlock.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
                                        directional.setFacing(savedFace);
                                        harvestBlock.setBlockData(directional);
                                    }
                                }
                            }
                        }
                    }.runTaskLater(plugin, 1L);
                    
                    sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§a풍년!");
                }
            }
        }
    }

    // ===============================================================
    // 경험치 관련 효과
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        int baseExp = event.getAmount();
        if (baseExp <= 0) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null) return;

        List<LampSlotData> activeSlots = getActiveEffects(mainHand);
        String legacyEffectId = EnhanceItemUtil.getLampEffect(mainHand);
        
        if (legacyEffectId != null && activeSlots.isEmpty()) {
            LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
            if (legacyEffect != null && lampRegistry.isEffectEnabled(legacyEffect.getId())) {
                activeSlots = List.of(new LampSlotData(legacyEffectId, 
                    legacyEffect.getMinValue1(), legacyEffect.getMinValue2()));
            }
        }

        int totalExp = baseExp;
        for (LampSlotData slotData : activeSlots) {
            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;

            double rolledValue1 = slotData.getValue1();
            int rolledValue2 = slotData.getValue2();

            if (effect == LampEffect.EXP_BOOST) {
                // EXP_BOOST - 40~80% 확률로 +5~20 고정 경험치
                if (random.nextDouble() < rolledValue1 / 100.0) {
                    totalExp += rolledValue2;
                    sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§a경험치 수집! §7(+" + rolledValue2 + ")");
                }
            } else if (effect == LampEffect.EXP_MASTER) {
                // EXP_MASTER - 항상 15~40% 증가 (확정)
                int bonusExp = (int) (baseExp * rolledValue1 / 100.0);
                totalExp += bonusExp;
            }
        }
        
        event.setAmount(totalExp);
    }

    // ===============================================================
    // 낚시 관련 효과
    // ===============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();
        ItemStack rod = player.getInventory().getItemInMainHand();

        if (rod == null || rod.getType() != Material.FISHING_ROD) return;

        List<LampSlotData> activeSlots = getActiveEffects(rod);
        String legacyEffectId = EnhanceItemUtil.getLampEffect(rod);
        
        if (legacyEffectId != null && activeSlots.isEmpty()) {
            LampEffect legacyEffect = LampEffect.fromId(legacyEffectId);
            if (legacyEffect != null && lampRegistry.isEffectEnabled(legacyEffect.getId())) {
                activeSlots = List.of(new LampSlotData(legacyEffectId, 
                    legacyEffect.getMinValue1(), legacyEffect.getMinValue2()));
            }
        }

        for (LampSlotData slotData : activeSlots) {
            LampEffect effect = LampEffect.fromId(slotData.getEffectId());
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;

            double rolledValue1 = slotData.getValue1();

            switch (effect) {
                case DOUBLE_FISH -> {
                    // 쌍둥이 어획: 10~50% 확률로 2배
                    if (random.nextDouble() < rolledValue1 / 100.0) {
                        if (event.getCaught() instanceof Item item) {
                            ItemStack caught = item.getItemStack();
                            giveItemSilently(player, caught.clone());
                            
                            // 어부 직업 경험치 추가 부여
                            if (jobRegistry != null) {
                                FisherExpService fisherExpService = (FisherExpService) jobRegistry.getExpService(JobType.FISHER);
                                if (fisherExpService != null) {
                                    fisherExpService.addFishingExp(player, caught.getType(), FishRarity.COMMON, caught.getAmount());
                                }
                            }
                            
                            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§5쌍둥이 어획!");
                        }
                    }
                }
                case FISH_EXP -> {
                    // 낚시 경험치 증가
                    if (event.getExpToDrop() > 0) {
                        int bonusExp = (int) (event.getExpToDrop() * rolledValue1 / 100.0);
                        event.setExpToDrop(event.getExpToDrop() + bonusExp);
                    }
                }
                case AUTO_REEL -> {
                    // 자동 릴 (CAUGHT_FISH 상태에서 이미 처리됨)
                    sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§6자동 릴!");
                }
                case CURRENT_SENSE -> {
                    // 조류 감지 - FISH_SPEED와 동일하게 처리 (메시지만)
                    sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§b조류 감지!");
                }
                case TREASURE_HUNTER -> {
                    // 보물 사냥꾼 - 5~15% 확률로 추가 보물
                    if (random.nextDouble() < rolledValue1 / 100.0) {
                        // 랜덤 보물 드랍
                        Material[] treasures = {Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT};
                        Material treasure = treasures[random.nextInt(treasures.length)];
                        giveItemSilently(player, new ItemStack(treasure, 1));
                        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§6보물 사냥꾼!");
                    }
                }
                default -> {}
            }
        }
    }

    // ===============================================================
    // 유틸리티
    // ===============================================================

    private double rollValue(double min, double max) {
        if (min == max) return min;
        return min + random.nextDouble() * (max - min);
    }

    private int rollValue(int min, int max) {
        if (min == max) return min;
        return min + random.nextInt(max - min + 1);
    }

    private void heal(Player player, double amount) {
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double newHealth = Math.min(maxHealth, player.getHealth() + amount);
        player.setHealth(newHealth);
    }

    private Material getSmeltResult(Material material) {
        return switch (material) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            case COBBLESTONE -> Material.STONE;
            case SAND -> Material.GLASS;
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            default -> null;
        };
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD");
    }
    
    /**
     * 광물 블록인지 확인
     */
    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
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
    
    /**
     * 광역 채굴 적용
     */
    private void applyMultiMine(BlockBreakEvent event, Player player, ItemStack tool, int radius) {
        Block centerBlock = event.getBlock();
        Material centerType = centerBlock.getType();
        
        List<Block> blocksToBreak = new ArrayList<>();
        
        if (radius == 1) {
            // 상하좌우 1칸
            blocksToBreak.add(centerBlock.getRelative(1, 0, 0));
            blocksToBreak.add(centerBlock.getRelative(-1, 0, 0));
            blocksToBreak.add(centerBlock.getRelative(0, 1, 0));
            blocksToBreak.add(centerBlock.getRelative(0, -1, 0));
            blocksToBreak.add(centerBlock.getRelative(0, 0, 1));
            blocksToBreak.add(centerBlock.getRelative(0, 0, -1));
        } else if (radius == 2) {
            // 3×3 영역 (중앙 제외)
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        blocksToBreak.add(centerBlock.getRelative(x, y, z));
                    }
                }
            }
        }
        
        // 같은 타입 블록만 필터링
        List<Block> validBlocks = new ArrayList<>();
        for (Block block : blocksToBreak) {
            if (block.getType() == centerType && block.getType().isBlock()) {
                validBlocks.add(block);
            }
        }
        
        if (validBlocks.isEmpty()) return;
        
        // ===== 새 Block Processing 파이프라인 사용 =====
        if (blockProcessingService != null && blockProcessingService.isEnabled()) {
            blockProcessingService.processBlocks(player, validBlocks, tool, BreakSource.MULTI_MINE);
            sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§5광역 채굴!");
            return;
        }
        
        // ===== 기존 로직 (fallback) =====
        for (Block block : validBlocks) {
            for (ItemStack drop : block.getDrops(tool)) {
                if (EnhanceItemUtil.hasCustomEnchant(tool, "telekinesis") || 
                    "telekinesis".equals(EnhanceItemUtil.getLampEffect(tool))) {
                    giveItemSilently(player, drop);
                } else {
                    block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }
            }
            
            // 직업 경험치 부여 (광부 - 광석인 경우)
            if (jobRegistry != null && isOre(block.getType())) {
                MinerExpService minerExpService = (MinerExpService) jobRegistry.getExpService(JobType.MINER);
                if (minerExpService != null) {
                    minerExpService.addMiningExp(player, block.getType(), 1);
                }
            }
            
            block.setType(Material.AIR);
        }
        
        sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§5광역 채굴!");
    }
    
    // ===============================================================
    // 기동성 효과 (스프린트 버스트, 강화 점프)
    // ===============================================================
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        
        if (!event.isSprinting()) return;
        
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            
            String effectId = EnhanceItemUtil.getLampEffect(armor);
            if (effectId == null) continue;
            
            LampEffect effect = LampEffect.fromId(effectId);
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
            
            if (effect == LampEffect.SPRINT_BURST) {
                double rolledValue1 = rollValue(effect.getMinValue1(), effect.getMaxValue1());
                long durationMs = (long)(rolledValue1 * 1000);
                long expiryTime = System.currentTimeMillis() + durationMs;
                
                sprintBurstStarts.put(player.getUniqueId(), expiryTime);
                
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    (int)(rolledValue1 * 20),
                    2, // 레벨 3 = +50% 속도
                    false, false, true
                ));
                
                sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§b순간 가속! §7(" + (int)rolledValue1 + "초)");
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Long expiry = sprintBurstStarts.get(player.getUniqueId());
                        if (expiry == null || System.currentTimeMillis() >= expiry) {
                            sprintBurstStarts.remove(player.getUniqueId());
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 20L, 20L);
                
                break;
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // 강화 점프 - 점프 높이 증가
        if (event.getFrom().getY() < event.getTo().getY() && 
            event.getTo().getY() - event.getFrom().getY() > 0.1) {
            
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor == null || armor.getType().isAir()) continue;
                
                String effectId = EnhanceItemUtil.getLampEffect(armor);
                if (effectId == null) continue;
                
                LampEffect effect = LampEffect.fromId(effectId);
                if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
                
                if (effect == LampEffect.ENHANCED_JUMP) {
                    double rolledValue1 = rollValue(effect.getMinValue1(), effect.getMaxValue1());
                    double boostMultiplier = 1.0 + (rolledValue1 / 200.0);
                    
                    Vector velocity = player.getVelocity();
                    if (velocity.getY() > 0) {
                        velocity.setY(velocity.getY() * boostMultiplier);
                        player.setVelocity(velocity);
                    }
                    break;
                }
            }
        }
    }
    
    /**
     * ENHANCED_JUMP - 낙하 피해 감소
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            
            String effectId = EnhanceItemUtil.getLampEffect(armor);
            if (effectId == null) continue;
            
            LampEffect effect = LampEffect.fromId(effectId);
            if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
            
            if (effect == LampEffect.ENHANCED_JUMP) {
                double rolledValue1 = rollValue(effect.getMinValue1(), effect.getMaxValue1());
                double reduction = rolledValue1 / 100.0;
                event.setDamage(event.getDamage() * (1 - reduction));
                
                // 착지 효과: 주변 적에게 넉백
                for (Entity nearby : player.getNearbyEntities(3, 3, 3)) {
                    if (nearby instanceof LivingEntity target && !(nearby instanceof Player)) {
                        Vector knockback = target.getLocation().toVector()
                                .subtract(player.getLocation().toVector())
                                .normalize()
                                .multiply(0.5);
                        knockback.setY(0.3);
                        target.setVelocity(knockback);
                    }
                }
                
                sendEffectMessage(player, EnhanceConstants.PREFIX_LAMP + "§a강화 점프! §7(낙하 피해 " + (int)rolledValue1 + "%% 감소)");
                break;
            }
        }
    }
    
    /**
     * 체력 강화 효과 적용 (아이템 착용 시 호출)
     */
    public void applyHealthBoost(Player player) {
        double totalBoost = 0;
        
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            
            List<LampSlotData> slots = getActiveEffectsWithLegacy(armor);
            for (LampSlotData slotData : slots) {
                LampEffect effect = LampEffect.fromId(slotData.getEffectId());
                if (effect == null || !lampRegistry.isEffectEnabled(effect.getId())) continue;
                
                if (effect == LampEffect.HEALTH_BOOST) {
                    totalBoost += slotData.getValue1();
                }
            }
        }
        
        if (totalBoost > 0) {
            AttributeModifier oldModifier = healthBoostModifiers.remove(player.getUniqueId());
            if (oldModifier != null) {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).removeModifier(oldModifier);
            }
            
            AttributeModifier modifier = new AttributeModifier(
                UUID.randomUUID(),
                "lamp_health_boost",
                totalBoost,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlot.CHEST
            );
            
            healthBoostModifiers.put(player.getUniqueId(), modifier);
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).addModifier(modifier);
        }
    }
    
    /**
     * 체력 강화 효과 제거 (아이템 해제 시 호출)
     */
    public void removeHealthBoost(Player player) {
        AttributeModifier modifier = healthBoostModifiers.remove(player.getUniqueId());
        if (modifier != null) {
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).removeModifier(modifier);
        }
    }
    
    // ========== 다중 슬롯 헬퍼 메서드 ==========
    
    /**
     * 아이템의 모든 활성 램프 효과 목록 가져오기
     */
    private List<LampSlotData> getActiveEffects(ItemStack item) {
        if (item == null || item.getType().isAir()) return Collections.emptyList();
        
        List<LampSlotData> slots = EnhanceItemUtil.getLampSlots(item);
        List<LampSlotData> active = new ArrayList<>();
        
        for (LampSlotData slot : slots) {
            if (!slot.isEmpty()) {
                active.add(slot);
            }
        }
        
        return active;
    }
    
    // ===============================================================
    // 플레이어 접속/퇴장 이벤트 (메모리 정리)
    // ===============================================================
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 접속 시 방어구 램프 효과 적용
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    applyHealthBoost(player);
                }
            }
        }.runTaskLater(plugin, 20L);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClickArmor(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        boolean isArmorSlot = event.getSlotType() == InventoryType.SlotType.ARMOR;
        boolean isShiftClick = event.isShiftClick() && isArmorItemLamp(event.getCurrentItem());
        
        if (isArmorSlot || isShiftClick) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        applyHealthBoost(player);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }
    
    private boolean isArmorItemLamp(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
               name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") ||
               name.equals("ELYTRA") || name.equals("TURTLE_HELMET");
    }
    
    /**
     * 플레이어 정리 (로그아웃 시)
     */
    public void cleanupPlayer(UUID uuid) {
        // 맵에서 제거
        phoenixCooldowns.remove(uuid);
        killMomentumBuffs.remove(uuid);
        sprintBurstStarts.remove(uuid);
        critStacks.remove(uuid);
        berserkerBonuses.remove(uuid);
        lastDamageTime.remove(uuid);
        guardianAngelActive.remove(uuid);
        deathMarkStacks.remove(uuid);
        comboStacks.remove(uuid);
        lastAttackTime.remove(uuid);
        stealthActive.remove(uuid);
        lastStandCooldowns.remove(uuid);
        focusReady.remove(uuid);
        absorbShields.remove(uuid);
        
        // AttributeModifier 정리
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            AttributeModifier healthMod = healthBoostModifiers.remove(uuid);
            if (healthMod != null) {
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).removeModifier(healthMod);
            }
            
            AttributeModifier rageMod = rageModifiers.remove(uuid);
            if (rageMod != null) {
                player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).removeModifier(rageMod);
            }
            
            AttributeModifier speedMod = speedSacrificeModifiers.remove(uuid);
            if (speedMod != null) {
                player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(speedMod);
            }
        } else {
            healthBoostModifiers.remove(uuid);
            rageModifiers.remove(uuid);
            speedSacrificeModifiers.remove(uuid);
        }
    }
}
