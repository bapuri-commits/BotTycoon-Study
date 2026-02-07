package kr.bapuri.tycoon.job.common;

import kr.bapuri.tycoon.job.JobType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * JobExpCalculator - 직업 경험치/레벨 계산기
 * 
 * Phase 4.E 리워크: 설정 파일에서 값을 읽을 수 있도록 변경
 * 
 * 공식:
 * - 레벨업 필요 경험치 = base + (level * level * multiplier)
 * - 구간별로 다른 multiplier 적용 (빠른 성장 → 느린 성장)
 * 
 * Phase 4.E 변경: 초반 2배 느리게
 * - 레거시: base=50, mult=5.0 → Lv1→2: 55 XP
 * - 변경후: base=100, mult=10.0 → Lv1→2: 110 XP (약 2배)
 */
public class JobExpCalculator {
    
    // ===== 상수 =====
    
    /** Tier 1 최대 레벨 */
    public static final int MAX_LEVEL_TIER1 = 100;
    
    /** Tier 2 최대 레벨 */
    public static final int MAX_LEVEL_TIER2 = 70;
    
    /** 경험치 최대값 (오버플로우 방지) */
    public static final long MAX_EXP = 1_000_000_000L;
    
    // ===== 구간별 파라미터 (Phase 4.E: 초반 2배 느리게) =====
    
    // 구간 1: Level 1~20 (레거시: 50/5.0 → 변경: 100/10.0)
    private static int SEGMENT1_END = 20;
    private static double SEGMENT1_BASE = 100;
    private static double SEGMENT1_MULT = 10.0;
    
    // 구간 2: Level 21~40 (레거시: 100/15.0 → 변경: 200/25.0)
    private static int SEGMENT2_END = 40;
    private static double SEGMENT2_BASE = 200;
    private static double SEGMENT2_MULT = 25.0;
    
    // 구간 3: Level 41~80 (레거시: 200/30.0 → 변경: 400/50.0)
    private static int SEGMENT3_END = 80;
    private static double SEGMENT3_BASE = 400;
    private static double SEGMENT3_MULT = 50.0;
    
    // 구간 4: Level 81~100 (레거시: 500/50.0 → 변경: 800/80.0)
    private static double SEGMENT4_BASE = 800;
    private static double SEGMENT4_MULT = 80.0;
    
    // ===== 캐싱된 누적 경험치 테이블 =====
    private static long[] CUMULATIVE_EXP_TABLE;
    
    private static boolean initialized = false;
    
    static {
        // 기본값으로 초기화
        rebuildExpTable();
    }
    
    /**
     * 경험치 테이블 재구축
     * 설정 변경 후 호출해야 함
     */
    public static void rebuildExpTable() {
        CUMULATIVE_EXP_TABLE = new long[MAX_LEVEL_TIER1 + 1];
        CUMULATIVE_EXP_TABLE[0] = 0;
        CUMULATIVE_EXP_TABLE[1] = 0;
        
        long cumulative = 0;
        for (int level = 2; level <= MAX_LEVEL_TIER1; level++) {
            cumulative += calculateExpForLevelUpInternal(level - 1);
            CUMULATIVE_EXP_TABLE[level] = cumulative;
        }
        initialized = true;
    }
    
    /**
     * jobs.yml exp_formula 섹션에서 설정 로드
     * 
     * @param config exp_formula ConfigurationSection
     * @param logger 로거 (null 허용)
     */
    public static void loadFromConfig(ConfigurationSection config, Logger logger) {
        if (config == null) {
            if (logger != null) {
                logger.info("[JobExpCalculator] exp_formula 섹션 없음, 기본값 사용");
            }
            return;
        }
        
        List<?> segments = config.getList("segments");
        if (segments == null || segments.isEmpty()) {
            if (logger != null) {
                logger.info("[JobExpCalculator] segments 없음, 기본값 사용");
            }
            return;
        }
        
        // 구간별 설정 로드
        List<ExpSegment> loadedSegments = new ArrayList<>();
        for (Object obj : segments) {
            if (obj instanceof ConfigurationSection section) {
                int minLevel = section.getInt("min_level", 1);
                int maxLevel = section.getInt("max_level", 100);
                double base = section.getDouble("base", 100);
                double multiplier = section.getDouble("multiplier", 10.0);
                loadedSegments.add(new ExpSegment(minLevel, maxLevel, base, multiplier));
            } else if (obj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) obj;
                int minLevel = ((Number) map.getOrDefault("min_level", 1)).intValue();
                int maxLevel = ((Number) map.getOrDefault("max_level", 100)).intValue();
                double base = ((Number) map.getOrDefault("base", 100.0)).doubleValue();
                double multiplier = ((Number) map.getOrDefault("multiplier", 10.0)).doubleValue();
                loadedSegments.add(new ExpSegment(minLevel, maxLevel, base, multiplier));
            }
        }
        
        // 4개 구간 적용
        if (loadedSegments.size() >= 1) {
            ExpSegment s = loadedSegments.get(0);
            SEGMENT1_END = s.maxLevel;
            SEGMENT1_BASE = s.base;
            SEGMENT1_MULT = s.multiplier;
        }
        if (loadedSegments.size() >= 2) {
            ExpSegment s = loadedSegments.get(1);
            SEGMENT2_END = s.maxLevel;
            SEGMENT2_BASE = s.base;
            SEGMENT2_MULT = s.multiplier;
        }
        if (loadedSegments.size() >= 3) {
            ExpSegment s = loadedSegments.get(2);
            SEGMENT3_END = s.maxLevel;
            SEGMENT3_BASE = s.base;
            SEGMENT3_MULT = s.multiplier;
        }
        if (loadedSegments.size() >= 4) {
            ExpSegment s = loadedSegments.get(3);
            SEGMENT4_BASE = s.base;
            SEGMENT4_MULT = s.multiplier;
        }
        
        // 테이블 재구축
        rebuildExpTable();
        
        if (logger != null) {
            logger.info("[JobExpCalculator] 설정 로드 완료 - " + loadedSegments.size() + "개 구간");
            logger.info(String.format("  Lv1-20: base=%.0f, mult=%.1f", SEGMENT1_BASE, SEGMENT1_MULT));
            logger.info(String.format("  Lv21-40: base=%.0f, mult=%.1f", SEGMENT2_BASE, SEGMENT2_MULT));
            logger.info(String.format("  Lv41-80: base=%.0f, mult=%.1f", SEGMENT3_BASE, SEGMENT3_MULT));
            logger.info(String.format("  Lv81-100: base=%.0f, mult=%.1f", SEGMENT4_BASE, SEGMENT4_MULT));
        }
    }
    
    /**
     * 경험치 구간 데이터
     */
    private record ExpSegment(int minLevel, int maxLevel, double base, double multiplier) {}
    
    // ===== 핵심 메서드 =====
    
    /**
     * 특정 레벨에서 다음 레벨로 올리기 위해 필요한 경험치
     * 
     * @param level 현재 레벨 (1~99)
     * @return 다음 레벨까지 필요한 경험치
     */
    public static long getExpForLevelUp(int level) {
        return calculateExpForLevelUpInternal(level);
    }
    
    /**
     * 내부 계산 메서드 (테이블 빌드용)
     */
    private static long calculateExpForLevelUpInternal(int level) {
        if (level < 1) return 0;
        if (level >= MAX_LEVEL_TIER1) return 0; // 100렙은 레벨업 없음
        
        double base;
        double mult;
        
        if (level <= SEGMENT1_END) {
            base = SEGMENT1_BASE;
            mult = SEGMENT1_MULT;
        } else if (level <= SEGMENT2_END) {
            base = SEGMENT2_BASE;
            mult = SEGMENT2_MULT;
        } else if (level <= SEGMENT3_END) {
            base = SEGMENT3_BASE;
            mult = SEGMENT3_MULT;
        } else {
            base = SEGMENT4_BASE;
            mult = SEGMENT4_MULT;
        }
        
        return (long) (base + (level * level * mult));
    }
    
    /**
     * 특정 레벨에 도달하기 위해 필요한 누적 경험치
     * 
     * @param level 목표 레벨 (1~100)
     * @return 해당 레벨에 도달하기 위한 총 경험치
     */
    public static long getCumulativeExpForLevel(int level) {
        if (level <= 1) return 0;
        if (level > MAX_LEVEL_TIER1) return CUMULATIVE_EXP_TABLE[MAX_LEVEL_TIER1];
        return CUMULATIVE_EXP_TABLE[level];
    }
    
    /**
     * 현재 경험치로 달성한 레벨 계산
     * 
     * @param currentExp 현재 누적 경험치
     * @return 달성한 레벨 (1~100)
     */
    public static int calculateLevel(long currentExp) {
        return calculateLevel(currentExp, MAX_LEVEL_TIER1);
    }
    
    /**
     * 현재 경험치로 달성한 레벨 계산 (최대 레벨 지정)
     * 
     * @param currentExp 현재 누적 경험치
     * @param maxLevel 최대 레벨 (Tier에 따라 다름)
     * @return 달성한 레벨
     */
    public static int calculateLevel(long currentExp, int maxLevel) {
        if (currentExp <= 0) return 1;
        
        int effectiveMax = Math.min(maxLevel, MAX_LEVEL_TIER1);
        
        // 이진 탐색으로 레벨 찾기
        int low = 1;
        int high = effectiveMax;
        
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (getCumulativeExpForLevel(mid) <= currentExp) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        
        return Math.min(low, effectiveMax);
    }
    
    /**
     * 현재 레벨에서 다음 레벨까지의 진행률 (0.0 ~ 1.0)
     * 
     * @param currentLevel 현재 레벨
     * @param currentExp 현재 누적 경험치
     * @return 진행률 (0.0 ~ 1.0)
     */
    public static double getLevelProgress(int currentLevel, long currentExp) {
        if (currentLevel >= MAX_LEVEL_TIER1) return 1.0;
        if (currentLevel < 1) return 0.0;
        
        long currentLevelStart = getCumulativeExpForLevel(currentLevel);
        long nextLevelStart = getCumulativeExpForLevel(currentLevel + 1);
        long range = nextLevelStart - currentLevelStart;
        
        if (range <= 0) return 1.0;
        
        long progress = currentExp - currentLevelStart;
        return Math.min(1.0, Math.max(0.0, (double) progress / range));
    }
    
    /**
     * 다음 레벨까지 필요한 남은 경험치
     * 
     * @param currentLevel 현재 레벨
     * @param currentExp 현재 누적 경험치
     * @return 남은 경험치 (0이면 레벨업 가능 또는 최대 레벨)
     */
    public static long getExpToNextLevel(int currentLevel, long currentExp) {
        if (currentLevel >= MAX_LEVEL_TIER1) return 0;
        
        long nextLevelExp = getCumulativeExpForLevel(currentLevel + 1);
        return Math.max(0, nextLevelExp - currentExp);
    }
    
    /**
     * 최대 레벨인지 확인
     * 
     * @param level 현재 레벨
     * @param jobType 직업 타입 (null이면 Tier 1 기준)
     * @return 최대 레벨 여부
     */
    public static boolean isMaxLevel(int level, JobType jobType) {
        int maxLevel = getMaxLevel(jobType);
        return level >= maxLevel;
    }
    
    /**
     * 직업 타입에 따른 최대 레벨 반환
     */
    public static int getMaxLevel(JobType jobType) {
        if (jobType == null) return MAX_LEVEL_TIER1;
        return jobType.isTier1() ? MAX_LEVEL_TIER1 : MAX_LEVEL_TIER2;
    }
    
    // ===== 유틸리티 =====
    
    /**
     * 경험치 값을 안전한 범위로 클램핑
     */
    public static long clampExp(long exp) {
        return Math.max(0, Math.min(exp, MAX_EXP));
    }
    
    /**
     * 레벨 값을 안전한 범위로 클램핑
     */
    public static int clampLevel(int level, JobType jobType) {
        int maxLevel = getMaxLevel(jobType);
        return Math.max(1, Math.min(level, maxLevel));
    }
    
    /**
     * 레벨 값을 안전한 범위로 클램핑 (Tier 1 기준)
     */
    public static int clampLevel(int level) {
        return Math.max(1, Math.min(level, MAX_LEVEL_TIER1));
    }
    
    /**
     * 경험치 테이블 디버그 출력
     */
    public static String getExpTableString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 경험치 테이블 ===\n");
        
        for (int i = 1; i <= 20; i++) {
            sb.append(String.format("Lv%3d: 레벨업 필요=%,10d / 누적=%,12d\n",
                    i, getExpForLevelUp(i), getCumulativeExpForLevel(i + 1)));
        }
        sb.append("...\n");
        for (int i = 95; i <= 100; i++) {
            sb.append(String.format("Lv%3d: 레벨업 필요=%,10d / 누적=%,12d\n",
                    i, getExpForLevelUp(i), getCumulativeExpForLevel(i + 1)));
        }
        
        return sb.toString();
    }
    
    /**
     * 경험치 추가 후 새 레벨 계산
     * 
     * @param currentExp 현재 경험치
     * @param addExp 추가 경험치
     * @param jobType 직업 타입
     * @return 새 경험치 (클램핑됨)
     */
    public static long addExp(long currentExp, long addExp, JobType jobType) {
        if (addExp <= 0) return currentExp;
        
        long newExp = clampExp(currentExp + addExp);
        
        // 최대 레벨의 최대 경험치로 제한
        int maxLevel = getMaxLevel(jobType);
        long maxExp = getCumulativeExpForLevel(maxLevel);
        
        // 최대 레벨 도달 후에도 경험치는 계속 쌓이도록 함
        // 하지만 MAX_EXP를 초과하지 않도록 함
        return Math.min(newExp, MAX_EXP);
    }
}
