package kr.bapuri.tycoonui.net;

import kr.bapuri.tycoonui.model.CodexData;
import kr.bapuri.tycoonui.model.EconomyHistory;
import kr.bapuri.tycoonui.model.JobDetail;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UI 관련 데이터를 저장하는 싱글톤 홀더입니다.
 * 
 * <p>tycoon-hud의 PlayerDataManager와 별개로 UI 전용 데이터를 관리합니다.</p>
 */
public class UiDataHolder {
    
    private static final UiDataHolder INSTANCE = new UiDataHolder();
    
    private final AtomicReference<CodexData> codexData = new AtomicReference<>(null);
    private final AtomicReference<EconomyHistory> economyHistory = new AtomicReference<>(null);
    private final AtomicReference<JobDetail> jobDetail = new AtomicReference<>(null);
    
    /** 카테고리별 아이템 캐시 (카테고리 이름 → 아이템 목록) */
    private final ConcurrentHashMap<String, List<CodexData.Item>> categoryItemsCache = new ConcurrentHashMap<>();
    
    /** 현재 로딩 중인 카테고리 */
    private volatile String loadingCategory = null;
    
    private UiDataHolder() {
        // 싱글톤
    }
    
    public static UiDataHolder getInstance() {
        return INSTANCE;
    }
    
    // Codex
    
    public void setCodexData(CodexData data) {
        codexData.set(data);
    }
    
    public CodexData getCodexData() {
        return codexData.get();
    }
    
    // Codex Category Items (카테고리별 아이템)
    
    /**
     * 카테고리 아이템을 캐시에 저장합니다.
     */
    public void setCategoryItems(String categoryName, List<CodexData.Item> items) {
        categoryItemsCache.put(categoryName, items);
        loadingCategory = null;
    }
    
    /**
     * 캐시된 카테고리 아이템을 반환합니다.
     * 
     * @return 아이템 목록 또는 캐시에 없으면 null
     */
    public List<CodexData.Item> getCategoryItems(String categoryName) {
        return categoryItemsCache.get(categoryName);
    }
    
    /**
     * 해당 카테고리의 아이템이 캐시되어 있는지 확인합니다.
     */
    public boolean hasCategoryItems(String categoryName) {
        return categoryItemsCache.containsKey(categoryName);
    }
    
    /**
     * 로딩 중인 카테고리를 설정합니다.
     */
    public void setLoadingCategory(String categoryName) {
        loadingCategory = categoryName;
    }
    
    /**
     * 현재 로딩 중인 카테고리를 반환합니다.
     */
    public String getLoadingCategory() {
        return loadingCategory;
    }
    
    /**
     * 카테고리 캐시를 초기화합니다.
     */
    public void clearCategoryCache() {
        categoryItemsCache.clear();
        loadingCategory = null;
    }
    
    // Economy History
    
    public void setEconomyHistory(EconomyHistory data) {
        economyHistory.set(data);
    }
    
    public EconomyHistory getEconomyHistory() {
        return economyHistory.get();
    }
    
    // Job Detail
    
    public void setJobDetail(JobDetail data) {
        jobDetail.set(data);
    }
    
    public JobDetail getJobDetail() {
        return jobDetail.get();
    }
    
    /**
     * 모든 데이터 초기화
     */
    public void clear() {
        codexData.set(null);
        economyHistory.set(null);
        jobDetail.set(null);
        clearCategoryCache();
    }
}

