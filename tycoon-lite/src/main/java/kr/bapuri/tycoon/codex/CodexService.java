package kr.bapuri.tycoon.codex;

import kr.bapuri.tycoon.admin.AdminPrivilege;
import kr.bapuri.tycoon.admin.AdminService;
import kr.bapuri.tycoon.economy.CurrencyType;
import kr.bapuri.tycoon.economy.EconomyService;
import kr.bapuri.tycoon.enhance.enchant.EnchantBookFactory;
import kr.bapuri.tycoon.enhance.lamp.LampItemFactory;
import kr.bapuri.tycoon.enhance.lamp.LampType;
import kr.bapuri.tycoon.enhance.upgrade.ProtectionScrollFactory;
import kr.bapuri.tycoon.player.PlayerDataManager;
import kr.bapuri.tycoon.player.PlayerTycoonData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import kr.bapuri.tycoon.TycoonPlugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * CodexService - 도감 시스템의 비즈니스 로직
 * 
 * 역할:
 * - 도감 등록/해제 처리
 * - 진행도 계산
 * - 마일스톤/카테고리 완성 보상
 * 
 * 의존성:
 * - PlayerDataManager: 플레이어 데이터 접근
 * - CodexRegistry: 도감 규칙 정의
 * - AdminService: 관리자 권한 체크
 * - EconomyService: 보상 지급 (setter 주입)
 */
public class CodexService {

    private final PlayerDataManager dataManager;
    private final CodexRegistry registry;
    private final AdminService adminService;
    private final Logger logger;
    
    // setter 주입 (순환 의존성 방지)
    private EconomyService economyService;
    
    // [Phase 1 동기화] 도감 등록 콜백 (모드 연동용)
    private CodexRegisterCallback registerCallback;
    
    // 카테고리 완성 보상 (아이템당 BottCoin)
    private static final long CATEGORY_COMPLETE_REWARD_PER_ITEM = 2L;
    
    /**
     * 도감 등록 콜백 인터페이스
     */
    @FunctionalInterface
    public interface CodexRegisterCallback {
        void onRegister(Player player, Material material, String displayName, long reward);
    }
    
    /**
     * 도감 등록 콜백 설정 (TycoonPlugin에서 호출)
     */
    public void setRegisterCallback(CodexRegisterCallback callback) {
        this.registerCallback = callback;
    }

    public CodexService(PlayerDataManager dataManager, CodexRegistry registry, 
                        AdminService adminService, Logger logger) {
        this.dataManager = dataManager;
        this.registry = registry;
        this.adminService = adminService;
        this.logger = logger;
    }

    /**
     * EconomyService 설정 (순환 의존성 방지를 위해 별도 setter)
     */
    public void setEconomyService(EconomyService economyService) {
        this.economyService = economyService;
    }

    // ========== Getters ==========

    public CodexRegistry getRegistry() {
        return registry;
    }

    public int getTotalCount() {
        return registry.getTotalCount();
    }

    // ========== 도감 상태 조회 ==========

    /**
     * 특정 아이템이 도감에 등록되어 있는지 확인 (온라인 플레이어)
     */
    public boolean isUnlocked(Player player, Material mat) {
        if (player == null || mat == null) return false;
        PlayerTycoonData data = dataManager.get(player);
        return data != null && data.isCodexUnlocked(mat);
    }

    /**
     * 특정 아이템이 도감에 등록되어 있는지 확인 (UUID)
     */
    public boolean isUnlocked(UUID uuid, Material mat) {
        if (uuid == null || mat == null) return false;
        PlayerTycoonData data = dataManager.get(uuid);
        return data != null && data.isCodexUnlocked(mat);
    }

    /**
     * 수집한 도감 아이템 수
     */
    public int getCollectedCount(UUID uuid) {
        if (uuid == null) return 0;
        PlayerTycoonData data = dataManager.get(uuid);
        return data != null ? data.getCodexCount() : 0;
    }

    /**
     * 수집한 도감 아이템 수 (온라인 플레이어)
     */
    public int getCollectedCount(Player player) {
        return dataManager.get(player).getCodexCount();
    }

    /**
     * 진행률(%) 계산
     */
    public double getProgressPercent(UUID uuid) {
        int total = registry.getTotalCount();
        if (total == 0) return 0.0;
        return getCollectedCount(uuid) * 100.0 / total;
    }
    
    /**
     * 플레이어가 수집한 도감 아이템 ID 목록 (Material 이름)
     */
    public Set<String> getCollectedItems(UUID uuid) {
        return new HashSet<>(dataManager.get(uuid).getUnlockedCodex());
    }

    // ========== 도감 등록 ==========

    /**
     * 플레이어가 아이템을 도감에 등록 시도
     * 커스텀 아이템(CustomModelData/DisplayName) 등록 방지
     * 
     * [Phase 8] 레거시 규칙 적용:
     * - required=1 → consume=false (희귀, 소멸 안 함)
     * - required=10 → consume=true (일반, 소멸)
     */
    public CodexRegisterResult tryRegister(Player player, Material mat) {
        UUID uuid = player.getUniqueId();
        PlayerTycoonData data = dataManager.get(player);
        CodexRule rule = registry.get(mat);

        // 1) 도감에 등록 가능한 아이템인지 확인
        if (rule == null) {
            return CodexRegisterResult.NOT_IN_CODEX;
        }

        // 2) 이미 등록되어 있는지 확인
        if (data.isCodexUnlocked(mat)) {
            return CodexRegisterResult.ALREADY_REGISTERED;
        }

        // 3) 관리자 권한 체크 (조건 무시)
        if (adminService.hasPrivilege(uuid, AdminPrivilege.CODEX_IGNORE_REQUIREMENTS)) {
            data.unlockCodex(mat);
            giveBottCoinReward(player, calculateReward(rule), rule.getKoreanDisplayName());
            checkAndGrantMilestones(player, data);
            checkAndGrantCategoryCompletion(player, data, rule.getCategory());
            logger.info("[Codex] " + player.getName() + " 관리자 권한으로 등록: " + mat.name());
            return CodexRegisterResult.SUCCESS;
        }

        // 4) 필요 수량 확인 (순수 바닐라 아이템만 카운트)
        int need = Math.max(rule.getRequiredCount(), 1);
        int vanillaItemCount = countVanillaItems(player, mat);
        
        // [Phase 8] 디버그 로그
        logger.fine("[Codex] " + player.getName() + " 등록 시도: " + mat.name() 
            + " (소유: " + vanillaItemCount + ", 필요: " + need 
            + ", 소멸: " + rule.isConsumeOnRegister() + ")");
        
        if (vanillaItemCount < need) {
            return CodexRegisterResult.NOT_ENOUGH_ITEMS;
        }

        // 5) 아이템 소비 (consume: true인 경우)
        if (rule.isConsumeOnRegister()) {
            int beforeCount = vanillaItemCount;
            removeVanillaItems(player, mat, need);
            int afterCount = countVanillaItems(player, mat);
            logger.info("[Codex] " + player.getName() + " 아이템 소비: " + mat.name() 
                + " (" + beforeCount + " → " + afterCount + ", 소비: " + need + ")");
        }

        // 6) 도감 등록
        data.unlockCodex(mat);
        
        // [Phase 8] 등록 로그
        logger.info("[Codex] " + player.getName() + " 도감 등록: " + rule.getKoreanDisplayName() 
            + " (카테고리: " + rule.getCategory() 
            + ", required: " + need + ", consume: " + rule.isConsumeOnRegister() + ")");

        // 7) BottCoin 보상 지급
        long bottCoinReward = calculateReward(rule);
        giveBottCoinReward(player, bottCoinReward, rule.getKoreanDisplayName());

        // 8) 마일스톤 체크
        checkAndGrantMilestones(player, data);

        // 9) 카테고리 완성 체크
        checkAndGrantCategoryCompletion(player, data, rule.getCategory());

        // 10) 업적 트리거 (버그수정: 도감 업적 연동)
        TycoonPlugin plugin = TycoonPlugin.getInstance();
        if (plugin != null && plugin.getAchievementListener() != null) {
            int newCount = getCollectedCount(player);
            plugin.getAchievementListener().onCodexRegister(player, newCount);
        }
        
        // 11) [Phase 1 동기화] 모드에 등록 알림 (콜백 호출)
        if (registerCallback != null) {
            registerCallback.onRegister(player, mat, rule.getKoreanDisplayName(), bottCoinReward);
        }

        return CodexRegisterResult.SUCCESS;
    }
    
    /**
     * 순수 바닐라 아이템 개수 카운트 (CustomModelData/DisplayName 없는 것만)
     */
    private int countVanillaItems(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != mat) continue;
            
            // CustomModelData가 있으면 커스텀 아이템 → 스킵
            if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
                continue;
            }
            
            // DisplayName이 있으면 커스텀 아이템일 가능성 → 스킵
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                continue;
            }
            
            count += item.getAmount();
        }
        return count;
    }
    
    /**
     * 순수 바닐라 아이템만 제거 (CustomModelData/DisplayName 없는 것만)
     */
    private void removeVanillaItems(Player player, Material mat, int amount) {
        int toRemove = amount;
        for (int i = 0; i < player.getInventory().getSize() && toRemove > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != mat) continue;
            
            // CustomModelData가 있으면 스킵
            if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
                continue;
            }
            
            // DisplayName이 있으면 스킵
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                continue;
            }
            
            int itemAmount = item.getAmount();
            if (itemAmount <= toRemove) {
                player.getInventory().setItem(i, null);
                toRemove -= itemAmount;
            } else {
                item.setAmount(itemAmount - toRemove);
                toRemove = 0;
            }
        }
    }

    /**
     * 보상 계산 (rewardOverride 우선 적용, 기본값은 codex.yml defaults 섹션에서 로드)
     * 
     * <p>[Phase 1 동기화] public으로 변경 - ModRequestHandler에서 동일한 보상 계산에 사용</p>
     * 
     * @param rule 도감 규칙
     * @return BottCoin 보상 값
     */
    public long calculateReward(CodexRule rule) {
        if (rule == null) return 0;
        if (rule.hasRewardOverride()) {
            return rule.getRewardOverride();
        }
        // codex.yml의 defaults 섹션에서 로드한 기본값 사용
        return rule.isConsumeOnRegister() 
            ? registry.getDefaultConsumeReward() 
            : registry.getDefaultKeepReward();
    }

    /**
     * BottCoin 보상 지급
     */
    private void giveBottCoinReward(Player player, long amount, String itemName) {
        if (economyService == null) return;
        if (amount <= 0) return;

        economyService.depositBottCoin(player, amount);
        player.sendMessage("§d§l[도감] §f" + itemName + " §a등록! 보상으로 " 
            + CurrencyType.BOTTCOIN.format(amount) + "§a을 획득했습니다!");
    }

    // ========== 관리자 명령어 ==========

    /**
     * 관리자 강제 등록 (오프라인 플레이어도 지원, 보상 없음)
     */
    public boolean forceRegister(UUID uuid, Material mat) {
        PlayerTycoonData data = dataManager.isOnline(uuid) 
            ? dataManager.get(uuid) 
            : dataManager.loadOffline(uuid);

        if (data.isCodexUnlocked(mat)) {
            if (!dataManager.isOnline(uuid)) {
                dataManager.saveAndUnload(uuid);
            }
            return false;
        }

        data.unlockCodex(mat);

        if (!dataManager.isOnline(uuid)) {
            dataManager.saveAndUnload(uuid);
        }

        return true;
    }

    /**
     * 관리자용 등록 (보상 포함, 아이템 소비 없음)
     */
    public CodexRegisterResult tryRegisterAdmin(Player player, Material mat) {
        PlayerTycoonData data = dataManager.get(player);
        CodexRule rule = registry.get(mat);

        if (rule == null) {
            return CodexRegisterResult.NOT_IN_CODEX;
        }

        if (data.isCodexUnlocked(mat)) {
            return CodexRegisterResult.ALREADY_REGISTERED;
        }

        data.unlockCodex(mat);

        long bottCoinReward = calculateReward(rule);
        giveBottCoinReward(player, bottCoinReward, rule.getKoreanDisplayName());

        checkAndGrantMilestones(player, data);
        checkAndGrantCategoryCompletion(player, data, rule.getCategory());

        return CodexRegisterResult.SUCCESS;
    }

    /**
     * 관리자 강제 등록 해제 (오프라인 플레이어도 지원)
     */
    public boolean forceUnregister(UUID uuid, Material mat) {
        PlayerTycoonData data = dataManager.isOnline(uuid) 
            ? dataManager.get(uuid) 
            : dataManager.loadOffline(uuid);

        if (!data.isCodexUnlocked(mat)) {
            if (!dataManager.isOnline(uuid)) {
                dataManager.saveAndUnload(uuid);
            }
            return false;
        }

        data.lockCodex(mat);

        if (!dataManager.isOnline(uuid)) {
            dataManager.saveAndUnload(uuid);
        }

        return true;
    }

    /**
     * 관리자 전체 초기화 (오프라인 플레이어도 지원)
     */
    public int forceResetAll(UUID uuid) {
        PlayerTycoonData data = dataManager.isOnline(uuid) 
            ? dataManager.get(uuid) 
            : dataManager.loadOffline(uuid);

        int count = data.getCodexCount();
        data.resetCodex();

        if (!dataManager.isOnline(uuid)) {
            dataManager.saveAndUnload(uuid);
        }

        return count;
    }
    
    /**
     * 전체 도감 등록
     */
    public int forceRegisterAll(UUID uuid, boolean giveReward) {
        PlayerTycoonData data = dataManager.get(uuid);
        if (data == null) return 0;
        
        int registered = 0;
        
        for (Material mat : registry.getAllMaterials()) {
            if (data.isCodexUnlocked(mat)) {
                continue;
            }
            
            data.unlockCodex(mat);
            registered++;
            
            if (giveReward && economyService != null) {
                CodexRule rule = registry.get(mat);
                if (rule != null) {
                    long reward = calculateReward(rule);
                    economyService.depositBottCoin(uuid, reward, 
                        "도감 등록: " + rule.getKoreanDisplayName(), "CodexService.forceRegisterAll");
                }
            }
        }
        
        return registered;
    }

    /**
     * 도감 규칙 정보 조회
     */
    public CodexRule getRule(Material mat) {
        return registry.get(mat);
    }

    // ========== 마일스톤/카테고리 보상 ==========

    /**
     * 마일스톤 보상 체크 및 지급 (public - 소급적용용)
     * 
     * [2026-02-01] 버전 체크 추가: codex.yml의 config-version이 변경되면
     * 기존 마일스톤 수령 기록을 초기화하여 새 보상을 받을 수 있도록 함
     */
    public void checkMilestonesForPlayer(Player player) {
        PlayerTycoonData data = dataManager.get(player);
        
        // [2026-02-01] 버전 체크 - 보상 버전이 다르면 마일스톤 초기화
        int currentVersion = registry.getConfigVersion();
        int playerVersion = data.getCodexRewardVersion();
        
        if (playerVersion < currentVersion) {
            // 버전이 업데이트됨 - 마일스톤 수령 기록 초기화
            int codexCount = data.getCodexCount();
            boolean hadMilestones = !data.getClaimedCodexMilestones().isEmpty();
            
            data.resetClaimedMilestones();
            data.setCodexRewardVersion(currentVersion);
            
            logger.info("[Codex] " + player.getName() + " 보상 버전 업데이트: " 
                + playerVersion + " -> " + currentVersion + " (마일스톤 초기화, 도감=" + codexCount + ")");
            
            // 도감이 있고 기존 마일스톤을 수령한 적이 있는 경우에만 메시지 표시
            if (codexCount > 0 && hadMilestones) {
                player.sendMessage("§a[도감] 보상이 업데이트되었습니다! 달성한 마일스톤 보상을 다시 받을 수 있습니다.");
            }
        }
        
        checkAndGrantMilestones(player, data);
        
        for (String category : registry.getCategoryOrder()) {
            checkAndGrantCategoryCompletion(player, data, category);
        }
    }

    /**
     * 마일스톤 보상 체크 및 지급
     * 
     * [2026-01-31] 아이템 보상 지급 추가
     * [2026-01-31] 아이템 지급 실패 시 롤백 로직 추가
     */
    private void checkAndGrantMilestones(Player player, PlayerTycoonData data) {
        int collected = data.getCodexCount();
        Map<Integer, CodexRegistry.MilestoneReward> milestones = registry.getMilestones();
        
        for (Map.Entry<Integer, CodexRegistry.MilestoneReward> entry : milestones.entrySet()) {
            int milestone = entry.getKey();
            CodexRegistry.MilestoneReward reward = entry.getValue();
            
            // 이미 수령했거나 아직 달성하지 못한 경우 스킵
            if (data.hasClaimedCodexMilestone(milestone)) continue;
            if (collected < milestone) continue;
            
            // [2026-01-31] 아이템 보상 먼저 생성하여 검증 (지급 실패 시 롤백 방지)
            List<ItemStack> itemsToGive = new ArrayList<>();
            List<String> grantedItemNames = new ArrayList<>();
            boolean itemCreationFailed = false;
            
            if (reward.hasItems()) {
                for (String itemDef : reward.getItems()) {
                    List<ItemStack> items = parseAndCreateItems(itemDef);
                    if (items == null || items.isEmpty()) {
                        // 아이템 생성 실패 - 마일스톤 claim하지 않고 경고
                        logger.severe("[Codex] 마일스톤 " + milestone + " 아이템 생성 실패: " + itemDef 
                            + " (player=" + player.getName() + ")");
                        itemCreationFailed = true;
                        break;
                    }
                    itemsToGive.addAll(items);
                    grantedItemNames.add(getItemDisplayName(itemDef));
                }
            }
            
            // 아이템 생성 실패 시 마일스톤 claim하지 않음 (재시도 가능)
            if (itemCreationFailed) {
                player.sendMessage("§c[도감] 마일스톤 " + milestone + " 보상 아이템 생성에 실패했습니다.");
                player.sendMessage("§c관리자에게 문의하거나 나중에 다시 시도해주세요.");
                continue;
            }
            
            // 마일스톤 달성!
            if (data.claimCodexMilestone(milestone)) {
                // BottCoin 지급
                if (economyService != null && reward.getBottcoin() > 0) {
                    economyService.depositBottCoin(player, reward.getBottcoin());
                }
                
                // BD 지급
                if (economyService != null && reward.getBd() > 0) {
                    economyService.deposit(player, reward.getBd());
                }
                
                // [2026-01-31] 아이템 보상 지급 (미리 생성된 아이템)
                for (ItemStack item : itemsToGive) {
                    giveItemToPlayer(player, item, milestone);
                }
                
                // 축하 메시지 & 사운드
                player.sendMessage("");
                player.sendMessage("§6§l★ 도감 마일스톤 달성! ★");
                player.sendMessage("§e" + milestone + "개 §f수집 완료!");
                player.sendMessage("§d보상:");
                if (reward.getBottcoin() > 0) {
                    player.sendMessage("  §d• " + CurrencyType.BOTTCOIN.format(reward.getBottcoin()));
                }
                if (reward.getBd() > 0) {
                    player.sendMessage("  §a• " + CurrencyType.BD.format(reward.getBd()));
                }
                // [2026-01-31] 아이템 보상 메시지
                for (String itemName : grantedItemNames) {
                    player.sendMessage("  §b• " + itemName);
                }
                player.sendMessage("");
                
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                
                // [2026-01-31] 아이템 보상 로그
                if (!grantedItemNames.isEmpty()) {
                    logger.info("[Codex] " + player.getName() + " 마일스톤 " + milestone 
                        + " 아이템 보상: " + String.join(", ", grantedItemNames));
                }
            }
        }
    }
    
    // ========== [2026-01-31] 아이템 보상 헬퍼 메서드 ==========
    
    /**
     * 아이템 정의 문자열을 파싱하여 ItemStack 목록 생성
     * 
     * [2026-01-31] 랜덤 아이템은 amount만큼 개별 생성 (동일 아이템 복제 방지)
     * 
     * 형식: "type:param:amount"
     *   - lamp:TOOL_LAMP:1 → 도구 램프 1개
     *   - enchant_random:S:1 → 랜덤 S등급 인챈트북 1개
     *   - scroll:destroy:1 → 파괴방지 주문서 1개
     * 
     * @return 생성된 아이템 목록, 실패 시 null
     */
    private List<ItemStack> parseAndCreateItems(String itemDef) {
        if (itemDef == null || itemDef.isBlank()) return null;
        
        String[] parts = itemDef.split(":");
        if (parts.length < 3) {
            logger.warning("[Codex] 잘못된 아이템 정의: " + itemDef);
            return null;
        }
        
        String type = parts[0].toLowerCase();
        String param = parts[1];
        int amount = 1;
        try {
            amount = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            logger.warning("[Codex] 잘못된 수량: " + parts[2]);
        }
        
        TycoonPlugin plugin = TycoonPlugin.getInstance();
        if (plugin == null) {
            logger.warning("[Codex] TycoonPlugin 인스턴스가 없습니다.");
            return null;
        }
        
        return switch (type) {
            case "lamp" -> createLampItems(plugin, param, amount);
            case "enchant_random" -> createRandomEnchantBooks(plugin, param, amount);
            case "scroll" -> createProtectionScrolls(param, amount);
            default -> {
                logger.warning("[Codex] 알 수 없는 아이템 타입: " + type);
                yield null;
            }
        };
    }
    
    /**
     * 램프 아이템 생성 (동일 아이템이므로 스택 가능)
     */
    private List<ItemStack> createLampItems(TycoonPlugin plugin, String lampTypeId, int amount) {
        LampType lampType = LampType.fromId(lampTypeId);
        if (lampType == null) {
            logger.warning("[Codex] 알 수 없는 램프 타입: " + lampTypeId);
            return null;
        }
        
        var lampService = plugin.getServices().getLampService();
        if (lampService == null) {
            logger.warning("[Codex] LampService를 찾을 수 없습니다.");
            return null;
        }
        
        LampItemFactory factory = lampService.getItemFactory();
        if (factory == null) {
            logger.warning("[Codex] LampItemFactory를 찾을 수 없습니다.");
            return null;
        }
        
        // 램프는 동일 아이템이므로 스택으로 생성
        ItemStack lamp = factory.createLamps(lampType, amount);
        if (lamp == null) return null;
        
        List<ItemStack> result = new ArrayList<>();
        result.add(lamp);
        return result;
    }
    
    /**
     * 랜덤 인챈트북 생성 (등급별, 개별 생성)
     * 
     * [2026-01-31] 각 인챈트북은 개별적으로 랜덤 생성 (동일 복제 방지)
     * 
     * @param tier S, A, B, C 등급
     * @param amount 생성할 개수
     * @return 개별 생성된 인챈트북 목록
     */
    private List<ItemStack> createRandomEnchantBooks(TycoonPlugin plugin, String tier, int amount) {
        var enchantService = plugin.getServices().getEnchantService();
        if (enchantService == null) {
            logger.warning("[Codex] CustomEnchantService를 찾을 수 없습니다.");
            return null;
        }
        
        var enchantRegistry = enchantService.getRegistry();
        if (enchantRegistry == null) {
            logger.warning("[Codex] CustomEnchantRegistry를 찾을 수 없습니다.");
            return null;
        }
        
        // [2026-01-31] 등급별 인챈트 목록 가져오기 (config.yml lottery.special.tiers)
        List<String> tierEnchants = getTierEnchants(plugin, tier);
        if (tierEnchants == null || tierEnchants.isEmpty()) {
            logger.warning("[Codex] " + tier + "등급 인챈트 목록이 비어있습니다. 전체 랜덤으로 대체합니다.");
            // 폴백: 전체 랜덤
            EnchantBookFactory factory = new EnchantBookFactory(enchantRegistry);
            List<ItemStack> result = new ArrayList<>();
            for (int i = 0; i < amount; i++) {
                ItemStack book = factory.createRandomBook();
                if (book != null) {
                    result.add(book);
                }
            }
            return result.isEmpty() ? null : result;
        }
        
        // [2026-01-31] 등급별 인챈트에서 랜덤 선택하여 개별 생성
        EnchantBookFactory factory = new EnchantBookFactory(enchantRegistry);
        List<ItemStack> result = new ArrayList<>();
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < amount; i++) {
            // 등급 내에서 랜덤 인챈트 선택
            String enchantDef = tierEnchants.get(random.nextInt(tierEnchants.size()));
            String[] enchantParts = enchantDef.split(":");
            if (enchantParts.length < 2) {
                logger.warning("[Codex] 잘못된 인챈트 정의: " + enchantDef);
                continue;
            }
            
            String enchantId = enchantParts[0];
            int level = 1;
            try {
                level = Integer.parseInt(enchantParts[1]);
            } catch (NumberFormatException ignored) {}
            
            ItemStack book = factory.createBook(enchantId, level);
            if (book != null) {
                result.add(book);
                logger.info("[Codex] " + tier + "등급 인챈트북 생성: " + enchantId + " Lv." + level);
            } else {
                // 커스텀 인챈트가 없으면 전체 랜덤으로 대체
                ItemStack randomBook = factory.createRandomBook();
                if (randomBook != null) {
                    result.add(randomBook);
                    logger.info("[Codex] " + tier + "등급 인챈트북 대체 생성 (랜덤)");
                }
            }
        }
        
        return result.isEmpty() ? null : result;
    }
    
    /**
     * config.yml에서 등급별 인챈트 목록 가져오기
     */
    private List<String> getTierEnchants(TycoonPlugin plugin, String tier) {
        try {
            var config = plugin.getConfig();
            var tierSection = config.getConfigurationSection("lottery.special.tiers." + tier.toUpperCase());
            if (tierSection == null) {
                logger.warning("[Codex] lottery.special.tiers." + tier + " 섹션을 찾을 수 없습니다.");
                return null;
            }
            return tierSection.getStringList("enchants");
        } catch (Exception e) {
            logger.warning("[Codex] 등급 인챈트 목록 조회 실패: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 보호 주문서 생성 (동일 아이템이므로 스택 가능)
     */
    private List<ItemStack> createProtectionScrolls(String scrollType, int amount) {
        ProtectionScrollFactory.ProtectionType type = 
            ProtectionScrollFactory.ProtectionType.fromId(scrollType);
        if (type == null) {
            logger.warning("[Codex] 알 수 없는 주문서 타입: " + scrollType);
            return null;
        }
        
        ItemStack scroll = ProtectionScrollFactory.createScrolls(type, amount);
        if (scroll == null) return null;
        
        List<ItemStack> result = new ArrayList<>();
        result.add(scroll);
        return result;
    }
    
    /**
     * 플레이어에게 아이템 지급 (인벤토리 가득 차면 드롭 + 상세 로그)
     * 
     * [2026-01-31] 드롭 시 상세 로그 추가 (아이템 분실 추적용)
     * 
     * @param player 대상 플레이어
     * @param item 지급할 아이템
     * @param milestone 마일스톤 번호 (로그용)
     */
    private void giveItemToPlayer(Player player, ItemStack item, int milestone) {
        if (item == null) return;
        
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        
        if (!overflow.isEmpty()) {
            for (ItemStack drop : overflow.values()) {
                // 월드에 드롭
                var droppedItem = player.getWorld().dropItemNaturally(player.getLocation(), drop);
                
                // [2026-01-31] 상세 로그 - 아이템 분실 추적용
                String itemName = drop.hasItemMeta() && drop.getItemMeta().hasDisplayName() 
                    ? drop.getItemMeta().getDisplayName() 
                    : drop.getType().name();
                logger.warning("[Codex] ⚠️ 인벤토리 오버플로우! 아이템 드롭됨");
                logger.warning("[Codex]   플레이어: " + player.getName() + " (" + player.getUniqueId() + ")");
                logger.warning("[Codex]   마일스톤: " + milestone);
                logger.warning("[Codex]   아이템: " + itemName + " x" + drop.getAmount());
                logger.warning("[Codex]   위치: " + formatLocation(player.getLocation()));
                logger.warning("[Codex]   드롭 엔티티 UUID: " + droppedItem.getUniqueId());
                
                // 플레이어에게 경고
                player.sendMessage("§c§l[경고] §e인벤토리가 가득 차서 아이템이 바닥에 떨어졌습니다!");
                player.sendMessage("§e아이템: §f" + itemName + " x" + drop.getAmount());
                player.sendMessage("§7빠르게 주워주세요! 5분 후 사라질 수 있습니다.");
            }
        }
    }
    
    /**
     * Location을 읽기 쉬운 문자열로 포맷
     */
    private String formatLocation(org.bukkit.Location loc) {
        return String.format("%s [%.1f, %.1f, %.1f]", 
            loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
            loc.getX(), loc.getY(), loc.getZ());
    }
    
    /**
     * 아이템 정의에서 표시 이름 추출
     */
    private String getItemDisplayName(String itemDef) {
        String[] parts = itemDef.split(":");
        if (parts.length < 3) return itemDef;
        
        String type = parts[0].toLowerCase();
        String param = parts[1];
        int amount = 1;
        try {
            amount = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {}
        
        String name = switch (type) {
            case "lamp" -> {
                LampType lampType = LampType.fromId(param);
                yield lampType != null ? lampType.getDisplayName() : param;
            }
            case "enchant_random" -> "§d랜덤 " + param + "등급 인챈트북";
            case "scroll" -> {
                ProtectionScrollFactory.ProtectionType scrollType = 
                    ProtectionScrollFactory.ProtectionType.fromId(param);
                yield scrollType != null ? scrollType.getDisplayName() : param;
            }
            default -> param;
        };
        
        return name + (amount > 1 ? " x" + amount : "");
    }

    /**
     * 카테고리 완성 보상 체크 및 지급
     */
    private void checkAndGrantCategoryCompletion(Player player, PlayerTycoonData data, String category) {
        // 이미 수령한 카테고리면 스킵
        if (data.hasClaimedCodexCategory(category)) return;
        
        // 해당 카테고리의 모든 아이템이 등록되었는지 확인
        List<CodexRule> categoryRules = registry.getByCategory(category);
        if (categoryRules == null || categoryRules.isEmpty()) return;
        
        for (CodexRule rule : categoryRules) {
            if (!data.isCodexUnlocked(rule.getMaterial())) {
                return; // 아직 미등록 아이템이 있음
            }
        }
        
        // 카테고리 완성!
        if (data.claimCodexCategory(category)) {
            int categorySize = categoryRules.size();
            long reward = categorySize * CATEGORY_COMPLETE_REWARD_PER_ITEM;
            
            if (economyService != null) {
                economyService.depositBottCoin(player, reward);
            }
            
            // 축하 메시지 & 사운드
            player.sendMessage("");
            player.sendMessage("§b§l◆ 카테고리 완성! ◆");
            player.sendMessage("§e" + category + " §f카테고리 전체 수집!");
            player.sendMessage("§7(" + categorySize + "개 아이템)");
            player.sendMessage("§d보상: " + CurrencyType.BOTTCOIN.format(reward));
            player.sendMessage("");
            
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }
    }

    // ========== 진행도 조회 ==========

    /**
     * 특정 카테고리의 진행도 계산
     */
    public int getCategoryProgress(UUID uuid, String category) {
        PlayerTycoonData data = dataManager.get(uuid);
        List<CodexRule> rules = registry.getByCategory(category);
        if (rules == null) return 0;
        
        int count = 0;
        for (CodexRule rule : rules) {
            if (data.isCodexUnlocked(rule.getMaterial())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 특정 카테고리가 완성되었는지 확인
     */
    public boolean isCategoryComplete(UUID uuid, String category) {
        List<CodexRule> rules = registry.getByCategory(category);
        if (rules == null || rules.isEmpty()) return false;
        return getCategoryProgress(uuid, category) == rules.size();
    }

    /**
     * 카테고리 완성 보상 계산 (GUI 표시용)
     */
    public long getCategoryCompleteReward(String category) {
        List<CodexRule> rules = registry.getByCategory(category);
        if (rules == null) return 0;
        return rules.size() * CATEGORY_COMPLETE_REWARD_PER_ITEM;
    }
}
