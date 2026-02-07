package kr.bapuri.tycoon.enhance.processing;

import kr.bapuri.tycoon.job.JobRegistry;
import kr.bapuri.tycoon.world.WorldManager;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

/**
 * BlockProcessingService - 블록 처리의 단일 진입점
 * 
 * 모든 블록 파괴 관련 처리는 이 서비스를 통해 실행됩니다.
 * 내부적으로 EffectProcessor 체인을 관리합니다.
 * 
 * Feature Flag: config.yml의 processing.use-new-processing
 * - true: 새 시스템 사용
 * - false: 기존 방식 유지 (이 서비스 호출 시 아무것도 안 함)
 */
public class BlockProcessingService {
    
    private final JavaPlugin plugin;
    private final JobRegistry jobRegistry;
    private final WorldManager worldManager;
    private final List<EffectProcessor> processors;
    private final boolean debugMode;
    private boolean enabled;
    
    // ===============================================================
    // 생성자
    // ===============================================================
    
    /**
     * 간단한 생성자 (JobRegistry/WorldManager 없이)
     * 개별 프로세서에서 필요시 직접 주입
     */
    public BlockProcessingService(JavaPlugin plugin) {
        this(plugin, null, null);
    }
    
    public BlockProcessingService(JavaPlugin plugin, JobRegistry jobRegistry, WorldManager worldManager) {
        this.plugin = plugin;
        this.jobRegistry = jobRegistry;
        this.worldManager = worldManager;
        this.processors = new ArrayList<>();
        
        // 설정 로드
        FileConfiguration config = plugin.getConfig();
        this.debugMode = config.getBoolean("processing.debug", false);
        this.enabled = config.getBoolean("processing.use-new-processing", true);
        
        plugin.getLogger().info("[BlockProcessing] Service initialized. enabled=" + enabled + ", debug=" + debugMode);
    }
    
    // ===============================================================
    // 프로세서 관리
    // ===============================================================
    
    /**
     * 프로세서 등록 (우선순위 순 정렬)
     */
    public void registerProcessor(EffectProcessor processor) {
        processors.add(processor);
        processors.sort(Comparator.comparingInt(EffectProcessor::getPriority));
        
        if (debugMode) {
            plugin.getLogger().info("[BlockProcessing] Registered: " + processor.getName() 
                + " (priority: " + processor.getPriority() + ")");
        }
    }
    
    /**
     * 프로세서 제거
     */
    public void unregisterProcessor(String name) {
        processors.removeIf(p -> p.getName().equals(name));
        
        if (debugMode) {
            plugin.getLogger().info("[BlockProcessing] Unregistered: " + name);
        }
    }
    
    /**
     * 모든 프로세서 제거
     */
    public void clearProcessors() {
        processors.clear();
    }
    
    /**
     * 등록된 프로세서 목록 조회
     */
    public List<String> getProcessorInfo() {
        return processors.stream()
            .map(p -> p.getName() + " (priority: " + p.getPriority() + ")")
            .toList();
    }
    
    // ===============================================================
    // 블록 처리 메서드
    // ===============================================================
    
    /**
     * 블록 처리 실행 (메인 메서드)
     * 
     * @param player 플레이어
     * @param block 처리할 블록
     * @param tool 사용 도구
     * @param source 파괴 원인
     * @param options 처리 옵션
     * @return 처리 결과 (enabled=false면 null)
     */
    public ProcessingResult processBlock(Player player, Block block, ItemStack tool,
                                         BreakSource source, ProcessingOptions options) {
        // Feature flag 확인
        if (!enabled) {
            return null;
        }
        
        // 컨텍스트 생성
        ProcessingContext context = new ProcessingContext(player, block, tool, source, options);
        
        // 디버그 로깅
        if (debugMode) {
            plugin.getLogger().info("[BlockProcessing] Start: " + block.getType() 
                + " at " + formatLocation(block) 
                + " by " + player.getName()
                + " (source: " + source + ")");
        }
        
        // 프로세서 체인 실행
        for (EffectProcessor processor : processors) {
            try {
                if (processor.shouldProcess(context)) {
                    processor.process(context);
                    
                    if (debugMode) {
                        plugin.getLogger().info("  → " + processor.getName() + " applied");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, 
                    "[BlockProcessing] Error in " + processor.getName() + ": " + e.getMessage(), e);
            }
        }
        
        // 결과 생성
        ProcessingResult result = new ProcessingResult(context);
        
        if (debugMode) {
            plugin.getLogger().info("[BlockProcessing] Complete: " + result);
        }
        
        return result;
    }
    
    /**
     * 편의 메서드: 모든 효과 적용
     */
    public ProcessingResult processBlock(Player player, Block block, ItemStack tool, BreakSource source) {
        return processBlock(player, block, tool, source, ProcessingOptions.allEnabled());
    }
    
    /**
     * 여러 블록 일괄 처리 (광맥 채굴, 광역 채굴용)
     */
    public List<ProcessingResult> processBlocks(Player player, List<Block> blocks, 
                                                ItemStack tool, BreakSource source,
                                                ProcessingOptions options) {
        List<ProcessingResult> results = new ArrayList<>();
        
        for (Block block : blocks) {
            ProcessingResult result = processBlock(player, block, tool, source, options);
            if (result != null) {
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * 편의 메서드: 여러 블록 일괄 처리 (모든 효과)
     */
    public List<ProcessingResult> processBlocks(Player player, List<Block> blocks, 
                                                ItemStack tool, BreakSource source) {
        return processBlocks(player, blocks, tool, source, ProcessingOptions.allEnabled());
    }
    
    // ===============================================================
    // 설정 관리
    // ===============================================================
    
    /**
     * 새 처리 시스템 활성화 여부
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 새 처리 시스템 활성화/비활성화
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getLogger().info("[BlockProcessing] Enabled: " + enabled);
    }
    
    /**
     * 설정 리로드
     */
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("processing.use-new-processing", true);
        
        plugin.getLogger().info("[BlockProcessing] Reloaded. enabled=" + enabled);
    }
    
    // ===============================================================
    // Getters
    // ===============================================================
    
    public JavaPlugin getPlugin() {
        return plugin;
    }
    
    public JobRegistry getJobRegistry() {
        return jobRegistry;
    }
    
    public WorldManager getWorldManager() {
        return worldManager;
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * 등록된 프로세서 개수
     */
    public int getProcessorCount() {
        return processors.size();
    }
    
    // ===============================================================
    // 유틸리티
    // ===============================================================
    
    private String formatLocation(Block block) {
        return block.getWorld().getName() + " " 
            + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
