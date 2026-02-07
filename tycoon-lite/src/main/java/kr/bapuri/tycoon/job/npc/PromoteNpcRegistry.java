package kr.bapuri.tycoon.job.npc;

import kr.bapuri.tycoon.job.JobType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * PromoteNpcRegistry - 승급 NPC 레지스트리
 * 
 * [Phase 승급효과] Citizens NPC ID ↔ JobType 매핑 관리
 * 
 * 데이터 저장: data/promote_npcs.yml
 */
public class PromoteNpcRegistry {
    
    private final JavaPlugin plugin;
    private final Logger logger;
    private final File dataFile;
    
    // NPC ID → JobType 매핑
    private final Map<Integer, JobType> npcMapping = new HashMap<>();
    
    public PromoteNpcRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFile = new File(plugin.getDataFolder(), "data/promote_npcs.yml");
    }
    
    /**
     * 데이터 로드
     */
    public void load() {
        npcMapping.clear();
        
        if (!dataFile.exists()) {
            logger.info("[PromoteNpcRegistry] 데이터 파일 없음 - 새로 시작");
            return;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        
        for (String key : config.getKeys(false)) {
            try {
                int npcId = Integer.parseInt(key);
                String jobName = config.getString(key);
                JobType jobType = parseJobType(jobName);
                
                if (jobType != null) {
                    npcMapping.put(npcId, jobType);
                }
            } catch (NumberFormatException e) {
                logger.warning("[PromoteNpcRegistry] 잘못된 NPC ID: " + key);
            }
        }
        
        logger.info("[PromoteNpcRegistry] 로드 완료 - NPC 수: " + npcMapping.size());
    }
    
    /**
     * 데이터 저장
     */
    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<Integer, JobType> entry : npcMapping.entrySet()) {
            config.set(String.valueOf(entry.getKey()), entry.getValue().name());
        }
        
        try {
            // 디렉토리 생성
            File parent = dataFile.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            
            config.save(dataFile);
        } catch (IOException e) {
            logger.severe("[PromoteNpcRegistry] 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * 승급 NPC인지 확인
     */
    public boolean isPromoteNpc(int npcId) {
        return npcMapping.containsKey(npcId);
    }
    
    /**
     * NPC의 직업 타입 조회
     */
    public JobType getJobType(int npcId) {
        return npcMapping.get(npcId);
    }
    
    /**
     * NPC 등록
     * 
     * @param npcId Citizens NPC ID
     * @param jobType 직업 타입
     */
    public void registerNpc(int npcId, JobType jobType) {
        npcMapping.put(npcId, jobType);
        save();
        logger.info("[PromoteNpcRegistry] NPC 등록: " + npcId + " → " + jobType.getDisplayName());
    }
    
    /**
     * NPC 등록 해제
     */
    public void unregisterNpc(int npcId) {
        JobType removed = npcMapping.remove(npcId);
        if (removed != null) {
            save();
            logger.info("[PromoteNpcRegistry] NPC 해제: " + npcId);
        }
    }
    
    /**
     * 모든 등록된 NPC 조회
     */
    public Map<Integer, JobType> getAllNpcs() {
        return new HashMap<>(npcMapping);
    }
    
    /**
     * 등록된 NPC 수
     */
    public int size() {
        return npcMapping.size();
    }
    
    /**
     * 문자열에서 JobType 파싱
     */
    private JobType parseJobType(String name) {
        if (name == null) return null;
        
        // 먼저 enum name으로 시도
        try {
            return JobType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // fromId로 시도
            return JobType.fromId(name);
        }
    }
}
