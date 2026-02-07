package kr.bapuri.tycoon.enhance.processing;

import kr.bapuri.tycoon.job.JobType;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * ProcessingResult - 블록 처리 결과
 * 
 * 처리 완료 후 결과를 반환합니다.
 */
public class ProcessingResult {
    
    private final List<ItemStack> droppedItems;
    private final Map<JobType, Long> grantedExp;
    private final Set<String> appliedEffects;
    private final boolean delivered;
    private final boolean blockRemoved;
    private final boolean success;
    
    // ===============================================================
    // 생성자
    // ===============================================================
    
    public ProcessingResult(ProcessingContext context) {
        this.droppedItems = new ArrayList<>(context.getDrops());
        this.grantedExp = new EnumMap<>(JobType.class);
        this.grantedExp.putAll(context.getGrantedExp());
        this.appliedEffects = new HashSet<>(context.getAppliedEffects());
        this.delivered = context.isDelivered();
        this.blockRemoved = context.isBlockRemoved();
        this.success = context.isDelivered();
    }
    
    // ===============================================================
    // Getters
    // ===============================================================
    
    /**
     * 드롭된 아이템 목록
     */
    public List<ItemStack> getDroppedItems() {
        return Collections.unmodifiableList(droppedItems);
    }
    
    /**
     * 총 드롭 수량
     */
    public int getTotalDropCount() {
        return droppedItems.stream().mapToInt(ItemStack::getAmount).sum();
    }
    
    /**
     * 부여된 경험치 맵
     */
    public Map<JobType, Long> getGrantedExp() {
        return Collections.unmodifiableMap(grantedExp);
    }
    
    /**
     * 총 경험치
     */
    public long getTotalExp() {
        return grantedExp.values().stream().mapToLong(Long::longValue).sum();
    }
    
    /**
     * 특정 직업 경험치
     */
    public long getExp(JobType jobType) {
        return grantedExp.getOrDefault(jobType, 0L);
    }
    
    /**
     * 적용된 효과 목록
     */
    public Set<String> getAppliedEffects() {
        return Collections.unmodifiableSet(appliedEffects);
    }
    
    /**
     * 특정 효과 적용 여부
     */
    public boolean hasEffect(String effectName) {
        return appliedEffects.contains(effectName);
    }
    
    /**
     * 드롭 전달 완료 여부
     */
    public boolean isDelivered() {
        return delivered;
    }
    
    /**
     * 블록 제거 완료 여부
     */
    public boolean isBlockRemoved() {
        return blockRemoved;
    }
    
    /**
     * 처리 성공 여부
     */
    public boolean isSuccess() {
        return success;
    }
    
    // ===============================================================
    // 디버깅
    // ===============================================================
    
    @Override
    public String toString() {
        return "ProcessingResult{" +
                "drops=" + getTotalDropCount() +
                ", exp=" + getTotalExp() +
                ", effects=" + appliedEffects.size() +
                ", delivered=" + delivered +
                ", blockRemoved=" + blockRemoved +
                '}';
    }
}
