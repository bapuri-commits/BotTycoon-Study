package kr.bapuri.tycoon.item;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import kr.bapuri.tycoon.enhance.enchant.CustomEnchant;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * CoreItemUseListener - 코어 아이템 사용 이벤트 리스너
 * 
 * [Phase 8] 신규 구현
 * 
 * 지원 아이템:
 * - RETURN_SCROLL: 마을로 귀환 (3초 채널링)
 * - TELEPORT_SCROLL: 마지막 위치로 텔레포트
 * - REBIRTH_MEMORY_SCROLL: 마지막 사망 위치로 텔레포트
 * - BASIC_ENCHANT_LOTTERY: 바닐라 인챈트북 뽑기
 * - SPECIAL_ENCHANT_LOTTERY: 커스텀 인챈트북 뽑기
 */
public class CoreItemUseListener implements Listener {
    
    private static final String PREFIX = "§6[아이템] §f";
    
    private final JavaPlugin plugin;
    private final CoreItemAuthenticator authenticator;
    private final CoreItemService coreItemService;
    private final PlayerDataManager playerDataManager;
    private final Logger logger;
    
    // 채널링 중인 플레이어 (스팸 방지)
    private final Map<UUID, Long> channelingPlayers = new ConcurrentHashMap<>();
    
    // 쿨다운
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1000; // 1초
    
    // 마을 스폰 위치 (config에서 로드)
    private Location townSpawn = null;
    
    // [Config 연동] 뽑기권 설정
    private LotteryConfig lotteryConfig;
    
    public CoreItemUseListener(JavaPlugin plugin, 
                               CoreItemAuthenticator authenticator,
                               CoreItemService coreItemService,
                               PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.authenticator = authenticator;
        this.coreItemService = coreItemService;
        this.playerDataManager = playerDataManager;
        this.logger = plugin.getLogger();
        
        loadConfig();
        
        // 뽑기권 설정 로드
        this.lotteryConfig = new LotteryConfig(plugin);
        
        logger.info("[CoreItemUseListener] 초기화 완료");
    }
    
    /**
     * 설정 로드
     */
    public void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("coreitem.spawn");
        if (section != null) {
            String worldName = section.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = section.getDouble("x", 0);
                double y = section.getDouble("y", 64);
                double z = section.getDouble("z", 0);
                float yaw = (float) section.getDouble("yaw", 0);
                float pitch = (float) section.getDouble("pitch", 0);
                townSpawn = new Location(world, x, y, z, yaw, pitch);
                logger.info("[CoreItemUseListener] 마을 스폰 로드: " + worldName + " " + x + "," + y + "," + z);
            }
        }
        
        // 기본값: world의 스폰
        if (townSpawn == null) {
            World world = Bukkit.getWorld("world");
            if (world != null) {
                townSpawn = world.getSpawnLocation();
            }
        }
    }
    
    /**
     * 설정 리로드
     */
    public void reload() {
        loadConfig();
        channelingPlayers.clear();
        cooldowns.clear();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 우클릭만 처리
        if (event.getAction() != Action.RIGHT_CLICK_AIR && 
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        // 메인핸드만 처리
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || item.getType().isAir()) {
            return;
        }
        
        // 코어 아이템 확인
        CoreItemType type = authenticator.getItemType(item);
        if (type == null) {
            return;
        }
        
        // 쿨다운 체크
        if (isOnCooldown(player)) {
            return;
        }
        
        // [Phase 8] 뽑기권은 Shift+우클릭으로만 사용 (NPC 클릭 오작동 방지)
        if ((type == CoreItemType.BASIC_ENCHANT_LOTTERY || type == CoreItemType.SPECIAL_ENCHANT_LOTTERY)
                && !player.isSneaking()) {
            return;
        }
        
        // 타입별 처리
        boolean handled = switch (type) {
            case RETURN_SCROLL -> handleReturnScroll(player, item);
            case TELEPORT_SCROLL -> handleTeleportScroll(player, item);
            case REBIRTH_MEMORY_SCROLL -> handleRebirthMemoryScroll(player, item);
            case BASIC_ENCHANT_LOTTERY -> handleBasicEnchantLottery(player, item);
            case SPECIAL_ENCHANT_LOTTERY -> handleSpecialEnchantLottery(player, item);
            default -> false;
        };
        
        if (handled) {
            event.setCancelled(true);
            setCooldown(player);
        }
    }
    
    // ========== 귀환 주문서 ==========
    
    private boolean handleReturnScroll(Player player, ItemStack item) {
        if (townSpawn == null) {
            player.sendMessage(PREFIX + "§c마을 스폰이 설정되지 않았습니다.");
            return false;
        }
        
        // 이미 채널링 중이면 취소
        if (channelingPlayers.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§c이미 귀환 중입니다.");
            return false;
        }
        
        // 채널링 시작
        channelingPlayers.put(player.getUniqueId(), System.currentTimeMillis());
        Location startLocation = player.getLocation().clone();
        
        player.sendMessage(PREFIX + "§e귀환 중... §7(3초간 움직이지 마세요)");
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.5f);
        
        // 3초 후 텔레포트
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                // 채널링 취소 확인
                if (!channelingPlayers.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                
                // 플레이어 오프라인
                if (!player.isOnline()) {
                    channelingPlayers.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                
                // 이동 확인 (1블록 이상 이동 시 취소)
                if (player.getLocation().distance(startLocation) > 1.0) {
                    channelingPlayers.remove(player.getUniqueId());
                    player.sendMessage(PREFIX + "§c움직여서 귀환이 취소되었습니다.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    cancel();
                    return;
                }
                
                ticks++;
                
                // 파티클 효과
                if (ticks % 10 == 0) {
                    player.getWorld().spawnParticle(Particle.PORTAL, 
                            player.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.1);
                }
                
                // 3초(60틱) 경과
                if (ticks >= 60) {
                    channelingPlayers.remove(player.getUniqueId());
                    
                    // 아이템 소비
                    if (!coreItemService.consumeItem(player, CoreItemType.RETURN_SCROLL, "귀환 사용")) {
                        player.sendMessage(PREFIX + "§c아이템 소비에 실패했습니다.");
                        cancel();
                        return;
                    }
                    
                    // 텔레포트
                    player.teleport(townSpawn);
                    player.sendMessage(PREFIX + "§a마을로 귀환했습니다!");
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        return true;
    }
    
    // ========== 텔레포트 주문서 ==========
    
    // 좌표 입력 대기 중인 플레이어
    private final Map<UUID, Long> awaitingCoordinates = new ConcurrentHashMap<>();
    private static final long COORDINATE_INPUT_TIMEOUT_MS = 30000; // 30초
    
    private boolean handleTeleportScroll(Player player, ItemStack item) {
        // 이미 좌표 입력 대기 중이면 취소
        if (awaitingCoordinates.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§c이미 좌표 입력 대기 중입니다. 채팅에 좌표를 입력하세요.");
            return false;
        }
        
        // 좌표 입력 대기 시작
        awaitingCoordinates.put(player.getUniqueId(), System.currentTimeMillis());
        
        player.sendMessage(PREFIX + "§e텔레포트할 좌표를 채팅에 입력하세요.");
        player.sendMessage("§7형식: §fx y z §7(예: §f100 64 -200§7)");
        player.sendMessage("§7취소하려면 §fcancel §7또는 §f취소§7를 입력하세요.");
        player.sendMessage("§8(30초 후 자동 취소됩니다)");
        
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        
        // 30초 후 자동 취소
        new BukkitRunnable() {
            @Override
            public void run() {
                Long startTime = awaitingCoordinates.get(player.getUniqueId());
                if (startTime != null && System.currentTimeMillis() - startTime >= COORDINATE_INPUT_TIMEOUT_MS) {
                    awaitingCoordinates.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendMessage(PREFIX + "§c좌표 입력 시간이 초과되었습니다.");
                    }
                }
            }
        }.runTaskLater(plugin, 600L); // 30초 = 600틱
        
        return true;
    }
    
    /**
     * 좌표 입력 처리 (외부에서 AsyncPlayerChatEvent로 호출)
     * @return true면 채팅 취소, false면 일반 채팅
     */
    public boolean handleCoordinateInput(Player player, String message) {
        if (!awaitingCoordinates.containsKey(player.getUniqueId())) {
            return false;
        }
        
        // 취소 명령
        if (message.equalsIgnoreCase("cancel") || message.equals("취소")) {
            awaitingCoordinates.remove(player.getUniqueId());
            player.sendMessage(PREFIX + "§e텔레포트가 취소되었습니다.");
            return true;
        }
        
        // 좌표 파싱
        String[] parts = message.trim().split("\\s+");
        if (parts.length < 3) {
            player.sendMessage(PREFIX + "§c좌표 형식이 잘못되었습니다. §7예: §f100 64 -200");
            return true;
        }
        
        double x, y, z;
        try {
            x = Double.parseDouble(parts[0]);
            y = Double.parseDouble(parts[1]);
            z = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + "§c숫자만 입력하세요. §7예: §f100 64 -200");
            return true;
        }
        
        // 좌표 범위 검증
        if (y < -64 || y > 320) {
            player.sendMessage(PREFIX + "§cY 좌표는 -64 ~ 320 사이여야 합니다.");
            return true;
        }
        
        // X/Z 좌표 범위 검증 (월드보더 기본값 기준)
        double maxCoord = 29999984; // 마인크래프트 월드보더 기본 최대값
        if (Math.abs(x) > maxCoord || Math.abs(z) > maxCoord) {
            player.sendMessage(PREFIX + "§cX/Z 좌표가 월드 범위를 벗어났습니다.");
            return true;
        }
        
        // 대기 상태 해제
        awaitingCoordinates.remove(player.getUniqueId());
        
        // 아이템 소비
        if (!coreItemService.consumeItem(player, CoreItemType.TELEPORT_SCROLL, "텔레포트 사용")) {
            player.sendMessage(PREFIX + "§c아이템 소비에 실패했습니다. 인벤토리에 텔레포트 주문서가 있는지 확인하세요.");
            return true;
        }
        
        // 텔레포트
        Location targetLocation = new Location(player.getWorld(), x, y, z);
        player.teleport(targetLocation);
        player.sendMessage(PREFIX + String.format("§a(%.1f, %.1f, %.1f)§f로 텔레포트했습니다!", x, y, z));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        
        return true;
    }
    
    /**
     * 좌표 입력 대기 중인지 확인
     */
    public boolean isAwaitingCoordinates(Player player) {
        return awaitingCoordinates.containsKey(player.getUniqueId());
    }
    
    /**
     * 좌표 입력 대기 취소
     */
    public void cancelCoordinateInput(Player player) {
        awaitingCoordinates.remove(player.getUniqueId());
    }
    
    // ========== 전생의 기억 주문서 ==========
    
    private boolean handleRebirthMemoryScroll(Player player, ItemStack item) {
        PlayerTycoonData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            player.sendMessage(PREFIX + "§c데이터를 불러올 수 없습니다.");
            return false;
        }
        
        Location deathLocation = data.getLastDeathLocation();
        if (deathLocation == null) {
            player.sendMessage(PREFIX + "§c저장된 사망 위치가 없습니다.");
            return false;
        }
        
        // 월드 유효성 검증
        if (deathLocation.getWorld() == null) {
            player.sendMessage(PREFIX + "§c저장된 위치의 월드가 더 이상 존재하지 않습니다.");
            return false;
        }
        
        // 아이템 소비
        if (!coreItemService.consumeItem(player, CoreItemType.REBIRTH_MEMORY_SCROLL, "전생 기억 사용")) {
            player.sendMessage(PREFIX + "§c아이템 소비에 실패했습니다.");
            return false;
        }
        
        // 텔레포트
        player.teleport(deathLocation);
        player.sendMessage(PREFIX + "§5마지막 사망 위치로 텔레포트했습니다!");
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.5f);
        
        // 효과
        player.getWorld().spawnParticle(Particle.SOUL, 
                player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
        
        return true;
    }
    
    // ========== 기본 인챈트 뽑기권 ==========
    
    private boolean handleBasicEnchantLottery(Player player, ItemStack item) {
        // 아이템 소비
        if (!coreItemService.consumeItem(player, CoreItemType.BASIC_ENCHANT_LOTTERY, "기본 인챈트 뽑기")) {
            player.sendMessage(PREFIX + "§c아이템 소비에 실패했습니다.");
            return false;
        }
        
        // [Config 연동] 가중치 기반 인챈트 선택
        LotteryConfig.EnchantEntry result = lotteryConfig.rollBasicEnchant();
        Enchantment selectedEnchant = result.enchantment;
        int level = result.level;
        
        // 인챈트북 생성
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta != null) {
            meta.addStoredEnchant(selectedEnchant, level, true);
            book.setItemMeta(meta);
        }
        
        // 지급
        giveItemToPlayer(player, book);
        
        String enchantName = formatEnchantName(selectedEnchant);
        player.sendMessage(PREFIX + "§a" + enchantName + " " + level + " §f인챈트북을 획득했습니다!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        
        return true;
    }
    
    // ========== 특수 인챈트 뽑기권 ==========
    
    private boolean handleSpecialEnchantLottery(Player player, ItemStack item) {
        // 아이템 소비
        if (!coreItemService.consumeItem(player, CoreItemType.SPECIAL_ENCHANT_LOTTERY, "특수 인챈트 뽑기")) {
            player.sendMessage(PREFIX + "§c아이템 소비에 실패했습니다.");
            return false;
        }
        
        // [Config 연동] 가중치 기반 인챈트 선택
        LotteryConfig.EnchantEntry result = lotteryConfig.rollSpecialEnchant();
        int level = result.level;
        
        ItemStack book;
        String enchantName;
        
        if (result.isCustom()) {
            // 커스텀 인챈트: 커스텀 인챈트북 생성
            CustomEnchant custom = result.customEnchant;
            enchantName = custom.getDisplayName();
            
            book = new ItemStack(Material.ENCHANTED_BOOK);
            org.bukkit.inventory.meta.ItemMeta meta = book.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§5§l커스텀 인챈트북");
                List<String> lore = new ArrayList<>();
                lore.add("§d" + enchantName + " " + level);
                lore.add("§7" + custom.getDescription());
                lore.add("");
                lore.add("§8모루에서 아이템에 적용하세요");
                lore.add("§8[특수 뽑기권 획득]");
                meta.setLore(lore);
                book.setItemMeta(meta);
            }
            // PDC에 인챈트 북 ID 저장 (모루에서 인식용)
            EnhanceItemUtil.setEnchantBookId(book, custom.getId(), level);
        } else {
            // 바닐라 인챈트: 기존 방식
            Enchantment selectedEnchant = result.enchantment;
            enchantName = formatEnchantName(selectedEnchant);
            
            book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            if (meta != null) {
                meta.addStoredEnchant(selectedEnchant, level, true);
                meta.setDisplayName("§5§l특수 인챈트북");
                List<String> lore = new ArrayList<>();
                lore.add("§7" + enchantName + " " + level);
                lore.add("");
                lore.add("§8[특수 뽑기권 획득]");
                meta.setLore(lore);
                book.setItemMeta(meta);
            }
        }
        
        // 지급
        giveItemToPlayer(player, book);
        
        player.sendMessage(PREFIX + "§5§l특수! §a" + enchantName + " " + level + " §f인챈트북을 획득했습니다!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.sendTitle("§5§l특수 인챈트!", "§a" + enchantName + " " + level, 10, 40, 20);
        
        return true;
    }
    
    // ========== 유틸리티 ==========
    
    private boolean isOnCooldown(Player player) {
        Long lastUse = cooldowns.get(player.getUniqueId());
        if (lastUse == null) return false;
        return System.currentTimeMillis() - lastUse < COOLDOWN_MS;
    }
    
    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    private void giveItemToPlayer(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack dropped : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), dropped);
            }
            player.sendMessage("§e인벤토리가 가득 차 일부 아이템이 바닥에 드롭되었습니다.");
        }
    }
    
    private String formatEnchantName(Enchantment enchant) {
        String key = enchant.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!name.isEmpty()) name.append(" ");
            name.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return name.toString();
    }
    
    /**
     * 채널링 취소 (외부에서 호출 가능)
     */
    public void cancelChanneling(Player player) {
        channelingPlayers.remove(player.getUniqueId());
    }
    
    /**
     * 채널링 중인지 확인
     */
    public boolean isChanneling(Player player) {
        return channelingPlayers.containsKey(player.getUniqueId());
    }
    
    // ========== 채팅 이벤트 (좌표 입력) ==========
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // 좌표 입력 대기 중인 플레이어만 처리
        if (!isAwaitingCoordinates(player)) {
            return;
        }
        
        // 메인 스레드에서 텔레포트 실행
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (handleCoordinateInput(player, message)) {
                // 채팅 취소는 비동기에서 처리됨
            }
        });
        
        // 채팅 메시지 취소 (좌표 입력은 다른 플레이어에게 안 보임)
        event.setCancelled(true);
    }
}
