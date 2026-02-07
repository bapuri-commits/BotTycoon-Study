package kr.bapuri.tycoon.enhance.processing;

import kr.bapuri.tycoon.job.JobType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Function;

/**
 * ProcessingContext - 블록 처리 컨텍스트
 * 
 * 블록 처리 전 과정에서 공유되는 데이터
 * 각 Processor가 이 컨텍스트를 수정합니다.
 */
public class ProcessingContext {
    
    // ===============================================================
    // 불변 필드 (생성 시 결정)
    // ===============================================================
    
    private final Player player;
    private final Block block;
    private final Material originalMaterial;
    private final Location originalLocation;
    private final ItemStack tool;
    private final BreakSource source;
    private final ProcessingOptions options;
    
    // ===============================================================
    // 가변 필드 (Processor들이 수정)
    // ===============================================================
    
    private List<ItemStack> drops;
    private final Map<JobType, Long> grantedExp;
    private final Set<String> appliedEffects;
    private final Map<String, Object> metadata;
    private boolean delivered;
    private boolean blockRemoved;
    
    // ===============================================================
    // 생성자
    // ===============================================================
    
    public ProcessingContext(Player player, Block block, ItemStack tool, 
                            BreakSource source, ProcessingOptions options) {
        this.player = player;
        this.block = block;
        this.originalMaterial = block.getType();
        this.originalLocation = block.getLocation().clone();
        this.tool = tool != null ? tool.clone() : null;
        this.source = source;
        this.options = options;
        
        // 초기 드롭 계산 - Fortune 없는 기본 드롭만 (Fortune은 FortuneProcessor에서 적용)
        // block.getDrops(tool)는 바닐라 Fortune을 이미 적용하므로, 기본 드롭으로 시작
        this.drops = new ArrayList<>(getBaseDrops(block, tool));
        this.grantedExp = new EnumMap<>(JobType.class);
        this.appliedEffects = new HashSet<>();
        this.metadata = new HashMap<>();
        this.delivered = false;
        this.blockRemoved = false;
    }
    
    /**
     * Fortune 효과 없이 기본 드롭만 계산
     * Silk Touch는 그대로 적용
     */
    private static Collection<ItemStack> getBaseDrops(Block block, ItemStack tool) {
        if (tool == null) {
            return block.getDrops();
        }
        
        // Silk Touch가 있으면 바닐라 드롭 그대로 사용 (Fortune 미적용됨)
        if (tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) {
            return block.getDrops(tool);
        }
        
        // Fortune 없는 도구로 드롭 계산
        ItemStack toolWithoutFortune = tool.clone();
        toolWithoutFortune.removeEnchantment(org.bukkit.enchantments.Enchantment.LOOT_BONUS_BLOCKS);
        
        return block.getDrops(toolWithoutFortune);
    }
    
    // ===============================================================
    // 효과 적용 추적
    // ===============================================================
    
    /**
     * 효과 적용 기록 (중복 방지 및 디버깅용)
     */
    public void markEffectApplied(String effectName) {
        appliedEffects.add(effectName);
    }
    
    /**
     * 특정 효과가 이미 적용되었는지 확인
     */
    public boolean isEffectApplied(String effectName) {
        return appliedEffects.contains(effectName);
    }
    
    /**
     * 적용된 모든 효과 목록
     */
    public Set<String> getAppliedEffects() {
        return Collections.unmodifiableSet(appliedEffects);
    }
    
    // ===============================================================
    // 드롭 수정 메서드
    // ===============================================================
    
    /**
     * 드롭 아이템 목록 반환
     */
    public List<ItemStack> getDrops() {
        return drops;
    }
    
    /**
     * 드롭 목록 교체
     */
    public void setDrops(List<ItemStack> drops) {
        this.drops = new ArrayList<>(drops);
    }
    
    /**
     * 드롭 아이템 추가
     */
    public void addDrop(ItemStack item) {
        if (item != null && item.getAmount() > 0) {
            drops.add(item.clone());
        }
    }
    
    /**
     * 모든 드롭에 배율 적용
     */
    public void multiplyDrops(double multiplier) {
        for (ItemStack drop : drops) {
            int newAmount = (int) Math.ceil(drop.getAmount() * multiplier);
            drop.setAmount(Math.max(1, newAmount));
        }
    }
    
    /**
     * 드롭 아이템 변환
     */
    public void transformDrops(Function<ItemStack, ItemStack> transformer) {
        List<ItemStack> transformed = new ArrayList<>();
        for (ItemStack drop : drops) {
            ItemStack result = transformer.apply(drop);
            if (result != null && result.getAmount() > 0) {
                transformed.add(result);
            }
        }
        this.drops = transformed;
    }
    
    /**
     * 총 드롭 수량
     */
    public int getTotalDropCount() {
        return drops.stream().mapToInt(ItemStack::getAmount).sum();
    }
    
    // ===============================================================
    // 경험치 관련
    // ===============================================================
    
    /**
     * 직업 경험치 추가
     */
    public void addJobExp(JobType jobType, long exp) {
        grantedExp.merge(jobType, exp, Long::sum);
    }
    
    /**
     * 특정 직업 경험치 조회
     */
    public long getJobExp(JobType jobType) {
        return grantedExp.getOrDefault(jobType, 0L);
    }
    
    /**
     * 모든 직업 경험치 합계
     */
    public long getTotalExp() {
        return grantedExp.values().stream().mapToLong(Long::longValue).sum();
    }
    
    /**
     * 부여된 경험치 맵
     */
    public Map<JobType, Long> getGrantedExp() {
        return Collections.unmodifiableMap(grantedExp);
    }
    
    // ===============================================================
    // 메타데이터 (Processor 간 통신)
    // ===============================================================
    
    /**
     * 메타데이터 저장
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * 메타데이터 조회
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }
    
    /**
     * 메타데이터 조회 (기본값)
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        Object value = metadata.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * 메타데이터 존재 여부
     */
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }
    
    // ===============================================================
    // 상태 플래그
    // ===============================================================
    
    public boolean isDelivered() {
        return delivered;
    }
    
    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }
    
    public boolean isBlockRemoved() {
        return blockRemoved;
    }
    
    public void setBlockRemoved(boolean blockRemoved) {
        this.blockRemoved = blockRemoved;
    }
    
    // ===============================================================
    // Getters (불변 필드)
    // ===============================================================
    
    public Player getPlayer() {
        return player;
    }
    
    public Block getBlock() {
        return block;
    }
    
    public Material getOriginalMaterial() {
        return originalMaterial;
    }
    
    public Location getOriginalLocation() {
        return originalLocation;
    }
    
    public ItemStack getTool() {
        return tool;
    }
    
    public BreakSource getSource() {
        return source;
    }
    
    public ProcessingOptions getOptions() {
        return options;
    }
}
