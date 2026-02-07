package kr.bapuri.tycoon.tax;

import kr.bapuri.tycoon.job.JobType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * TaxConfig - 세금 시스템 설정 로드
 * 
 * 모든 세율, 메시지 등은 tax.yml에서 로드
 * 하드코딩 금지
 * 
 * [v2] 소득세/보유세 분리, 직업별 토지세, 청크 누진세, VIP 할인 추가
 */
public class TaxConfig {

    private final JavaPlugin plugin;
    private final Logger logger;
    private FileConfiguration config;
    private File configFile;

    // ===== 전역 설정 =====
    private boolean enabled;
    private Set<UUID> adminUuids;

    // ===== 소득세 설정 (v2: 소득세/보유세 분리) =====
    private boolean incomeTaxEnabled;
    private List<TaxBracket> incomeTaxBrackets;      // 하위 호환용 (deprecated)
    private List<TaxBracket> earnedTaxBrackets;      // [v2] 소득세 구간
    private List<TaxBracket> wealthTaxBrackets;      // [v2] 보유세 구간

    // ===== 토지세 설정 =====
    private boolean landTaxEnabled;
    private int periodHours;
    private long perChunk;                           // 하위 호환용 (deprecated)
    private Map<String, Long> perChunkByJob;         // [v2] 직업별 청크 세금
    private List<ChunkProgressiveRate> chunkProgressiveRates;  // [v2] 청크 누진 배율
    private List<VillagerTaxRate> villagerRates;
    private int inactiveExemptHours;
    private boolean noIncomeExempt;
    private boolean freezeOnUnpaid;

    // ===== VIP 설정 (v2) =====
    private String vipLuckpermsGroup;
    private int vipDiscountPercent;

    // ===== 메시지 =====
    private Map<String, String> messages;

    public TaxConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.adminUuids = new HashSet<>();
        this.incomeTaxBrackets = new ArrayList<>();
        this.earnedTaxBrackets = new ArrayList<>();
        this.wealthTaxBrackets = new ArrayList<>();
        this.perChunkByJob = new HashMap<>();
        this.chunkProgressiveRates = new ArrayList<>();
        this.villagerRates = new ArrayList<>();
        this.messages = new HashMap<>();
    }

    /**
     * 설정 파일 로드
     */
    public void load() {
        saveDefaultConfig();
        configFile = new File(plugin.getDataFolder(), "tax.yml");
        config = YamlConfiguration.loadConfiguration(configFile);

        // 기본값 병합
        InputStream defaultStream = plugin.getResource("tax.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        loadGlobalSettings();
        loadIncomeTaxSettings();
        loadLandTaxSettings();
        loadVipSettings();
        loadMessages();

        logger.info("[TaxConfig] 설정 로드 완료 (enabled=" + enabled + 
                   ", incomeTax=" + incomeTaxEnabled + 
                   ", landTax=" + landTaxEnabled + 
                   ", periodHours=" + periodHours + 
                   ", inactiveExemptHours=" + inactiveExemptHours +
                   ", vipDiscount=" + vipDiscountPercent + "%)");
    }

    /**
     * 기본 설정 파일 저장
     */
    private void saveDefaultConfig() {
        File file = new File(plugin.getDataFolder(), "tax.yml");
        if (!file.exists()) {
            plugin.saveResource("tax.yml", false);
        }
    }

    /**
     * 설정 리로드
     */
    public void reload() {
        load();
    }

    // ===== 설정 로드 메서드 =====

    private void loadGlobalSettings() {
        enabled = config.getBoolean("tax.enabled", true);

        adminUuids.clear();
        List<String> uuidStrings = config.getStringList("tax.adminUuids");
        for (String uuidStr : uuidStrings) {
            try {
                adminUuids.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException e) {
                logger.warning("[TaxConfig] 잘못된 UUID 형식: " + uuidStr);
            }
        }
    }

    private void loadIncomeTaxSettings() {
        incomeTaxEnabled = config.getBoolean("incomeTax.enabled", true);

        // [v2] 소득세 구간 (earnedBrackets)
        earnedTaxBrackets.clear();
        List<Map<?, ?>> earnedBrackets = config.getMapList("incomeTax.earnedBrackets");
        for (Map<?, ?> bracket : earnedBrackets) {
            long min = ((Number) bracket.get("min")).longValue();
            long max = ((Number) bracket.get("max")).longValue();
            double rate = ((Number) bracket.get("rate")).doubleValue();
            earnedTaxBrackets.add(new TaxBracket(min, max, rate));
        }
        earnedTaxBrackets.sort(Comparator.comparingLong(TaxBracket::getMin));

        // [v2] 보유세 구간 (wealthBrackets)
        wealthTaxBrackets.clear();
        List<Map<?, ?>> wealthBrackets = config.getMapList("incomeTax.wealthBrackets");
        for (Map<?, ?> bracket : wealthBrackets) {
            long min = ((Number) bracket.get("min")).longValue();
            long max = ((Number) bracket.get("max")).longValue();
            double rate = ((Number) bracket.get("rate")).doubleValue();
            wealthTaxBrackets.add(new TaxBracket(min, max, rate));
        }
        wealthTaxBrackets.sort(Comparator.comparingLong(TaxBracket::getMin));

        // 하위 호환: 기존 brackets가 있으면 로드 (deprecated)
        incomeTaxBrackets.clear();
        List<Map<?, ?>> legacyBrackets = config.getMapList("incomeTax.brackets");
        for (Map<?, ?> bracket : legacyBrackets) {
            long min = ((Number) bracket.get("min")).longValue();
            long max = ((Number) bracket.get("max")).longValue();
            double rate = ((Number) bracket.get("rate")).doubleValue();
            incomeTaxBrackets.add(new TaxBracket(min, max, rate));
        }
        incomeTaxBrackets.sort(Comparator.comparingLong(TaxBracket::getMin));

        // earnedBrackets가 비어있고 기존 brackets가 있으면 기존 것 사용 (마이그레이션 호환)
        if (earnedTaxBrackets.isEmpty() && !incomeTaxBrackets.isEmpty()) {
            earnedTaxBrackets.addAll(incomeTaxBrackets);
            logger.info("[TaxConfig] 기존 brackets를 earnedBrackets로 마이그레이션");
        }
    }

    private void loadLandTaxSettings() {
        landTaxEnabled = config.getBoolean("landTax.enabled", true);
        periodHours = config.getInt("landTax.periodHours", 3);
        
        // 하위 호환: 기존 perChunk (deprecated)
        perChunk = config.getLong("landTax.perChunk", 2000);

        // [v2] 직업별 청크 세금
        perChunkByJob.clear();
        ConfigurationSection jobSection = config.getConfigurationSection("landTax.perChunkByJob");
        if (jobSection != null) {
            for (String key : jobSection.getKeys(false)) {
                perChunkByJob.put(key.toLowerCase(), jobSection.getLong(key));
            }
        }
        // 기본값 설정 (없으면 기존 perChunk 사용)
        if (!perChunkByJob.containsKey("default")) {
            perChunkByJob.put("default", perChunk);
        }

        // [v2] 청크 누진 배율
        chunkProgressiveRates.clear();
        List<Map<?, ?>> progressiveRates = config.getMapList("landTax.chunkProgressiveRates");
        for (Map<?, ?> rate : progressiveRates) {
            int minChunks = ((Number) rate.get("minChunks")).intValue();
            int maxChunks = ((Number) rate.get("maxChunks")).intValue();
            double multiplier = ((Number) rate.get("multiplier")).doubleValue();
            chunkProgressiveRates.add(new ChunkProgressiveRate(minChunks, maxChunks, multiplier));
        }
        chunkProgressiveRates.sort(Comparator.comparingInt(ChunkProgressiveRate::getMinChunks));
        
        // 청크 누진이 없으면 기본값 (x1.0 배율)
        if (chunkProgressiveRates.isEmpty()) {
            chunkProgressiveRates.add(new ChunkProgressiveRate(1, -1, 1.0));
        }

        // 주민 세금
        villagerRates.clear();
        List<Map<?, ?>> rates = config.getMapList("landTax.villagerRates");
        for (Map<?, ?> rate : rates) {
            int min = ((Number) rate.get("min")).intValue();
            int max = ((Number) rate.get("max")).intValue();
            long perVillager = ((Number) rate.get("perVillager")).longValue();
            villagerRates.add(new VillagerTaxRate(min, max, perVillager));
        }
        villagerRates.sort(Comparator.comparingInt(VillagerTaxRate::getMin));

        // 면제 조건
        inactiveExemptHours = config.getInt("landTax.exemptions.inactiveHours", 12);
        noIncomeExempt = config.getBoolean("landTax.exemptions.noIncomeExempt", false);

        // 미납 처리
        freezeOnUnpaid = config.getBoolean("landTax.unpaid.freezeLand", true);
    }

    /**
     * [v2] VIP 설정 로드
     */
    private void loadVipSettings() {
        vipLuckpermsGroup = config.getString("vip.luckpermsGroup", "vip");
        vipDiscountPercent = config.getInt("vip.discountPercent", 20);
    }

    private void loadMessages() {
        messages.clear();
        ConfigurationSection msgSection = config.getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                String value = msgSection.getString(key, "");
                // 컬러 코드 변환
                messages.put(key, value.replace('&', '§'));
            }
        }
    }

    // ===== Getter 메서드 =====

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAdminUuid(UUID uuid) {
        return adminUuids.contains(uuid);
    }

    public Set<UUID> getAdminUuids() {
        return Collections.unmodifiableSet(adminUuids);
    }

    // 소득세
    public boolean isIncomeTaxEnabled() {
        return enabled && incomeTaxEnabled;
    }

    public List<TaxBracket> getIncomeTaxBrackets() {
        return Collections.unmodifiableList(incomeTaxBrackets);
    }

    /**
     * 일일 소득에 대한 세율 조회 (deprecated, 하위 호환용)
     * @deprecated getEarnedTaxRate() 또는 getWealthTaxRate() 사용
     */
    @Deprecated
    public double getIncomeTaxRate(long dailyIncome) {
        return getEarnedTaxRate(dailyIncome);
    }

    /**
     * [v2] 소득세 구간 조회
     */
    public List<TaxBracket> getEarnedTaxBrackets() {
        return Collections.unmodifiableList(earnedTaxBrackets);
    }

    /**
     * [v2] 보유세 구간 조회
     */
    public List<TaxBracket> getWealthTaxBrackets() {
        return Collections.unmodifiableList(wealthTaxBrackets);
    }

    /**
     * [v2] 3시간 간격 소득에 대한 소득세율 조회
     */
    public double getEarnedTaxRate(long intervalIncome) {
        for (TaxBracket bracket : earnedTaxBrackets) {
            if (bracket.contains(intervalIncome)) {
                return bracket.getRate();
            }
        }
        // 마지막 구간 (max = -1인 경우)
        if (!earnedTaxBrackets.isEmpty()) {
            TaxBracket last = earnedTaxBrackets.get(earnedTaxBrackets.size() - 1);
            if (last.getMax() == -1 && intervalIncome >= last.getMin()) {
                return last.getRate();
            }
        }
        return 0.0;
    }

    /**
     * [v2] 현재 보유 재화에 대한 보유세율 조회
     */
    public double getWealthTaxRate(long balance) {
        for (TaxBracket bracket : wealthTaxBrackets) {
            if (bracket.contains(balance)) {
                return bracket.getRate();
            }
        }
        // 마지막 구간 (max = -1인 경우)
        if (!wealthTaxBrackets.isEmpty()) {
            TaxBracket last = wealthTaxBrackets.get(wealthTaxBrackets.size() - 1);
            if (last.getMax() == -1 && balance >= last.getMin()) {
                return last.getRate();
            }
        }
        return 0.0;
    }

    // 토지세
    public boolean isLandTaxEnabled() {
        return enabled && landTaxEnabled;
    }

    public int getPeriodHours() {
        return periodHours;
    }

    public long getPeriodMillis() {
        return (long) periodHours * 60 * 60 * 1000;
    }

    /**
     * @deprecated getPerChunkByJob() 사용
     */
    @Deprecated
    public long getPerChunk() {
        return perChunk;
    }

    /**
     * [v2] 직업별 청크당 세금 조회
     * @param job 직업 (null이면 default)
     * @return 청크당 세금 (BD)
     */
    public long getPerChunkByJob(JobType job) {
        if (job == null) {
            return perChunkByJob.getOrDefault("default", 2000L);
        }
        String key = job.getConfigKey().toLowerCase();
        return perChunkByJob.getOrDefault(key, perChunkByJob.getOrDefault("default", 2000L));
    }

    /**
     * [v2] 청크 누진 배율 목록 조회
     */
    public List<ChunkProgressiveRate> getChunkProgressiveRates() {
        return Collections.unmodifiableList(chunkProgressiveRates);
    }

    /**
     * [v2] 청크 수에 따른 누진 토지세 계산
     * 
     * 각 구간별로 배율을 다르게 적용하여 합산
     * 예: 50청크 농부 = 10*1500*1.0 + 20*1500*1.5 + 20*1500*2.0 = 120,000 BD
     * 
     * @param totalChunks 총 청크 수
     * @param job 직업 (null이면 default)
     * @return 총 토지세 (BD)
     */
    public long calculateProgressiveChunkTax(int totalChunks, JobType job) {
        if (totalChunks <= 0) {
            return 0;
        }

        long baseTax = getPerChunkByJob(job);
        long totalTax = 0;
        int processedChunks = 0;

        for (ChunkProgressiveRate rate : chunkProgressiveRates) {
            if (processedChunks >= totalChunks) {
                break;
            }

            int rangeStart = rate.getMinChunks();
            int rangeEnd = rate.getMaxChunks() == -1 ? Integer.MAX_VALUE : rate.getMaxChunks();
            
            // 이 구간에서 처리할 청크 수 계산
            int chunksInThisRange = Math.min(rangeEnd, totalChunks) - Math.max(rangeStart - 1, processedChunks);
            if (chunksInThisRange <= 0) {
                continue;
            }

            // 세금 계산 (청크 수 * 기본세금 * 배율)
            totalTax += Math.round(chunksInThisRange * baseTax * rate.getMultiplier());
            processedChunks += chunksInThisRange;
        }

        return totalTax;
    }

    public List<VillagerTaxRate> getVillagerRates() {
        return Collections.unmodifiableList(villagerRates);
    }

    /**
     * 주민 수에 대한 주민당 세금 조회
     */
    public long getVillagerTaxPerUnit(int villagerCount) {
        for (VillagerTaxRate rate : villagerRates) {
            if (rate.contains(villagerCount)) {
                return rate.getPerVillager();
            }
        }
        // 마지막 구간 (max = -1인 경우)
        if (!villagerRates.isEmpty()) {
            VillagerTaxRate last = villagerRates.get(villagerRates.size() - 1);
            if (last.getMax() == -1 && villagerCount >= last.getMin()) {
                return last.getPerVillager();
            }
        }
        return 0;
    }

    public int getInactiveExemptHours() {
        return inactiveExemptHours;
    }

    public boolean isNoIncomeExempt() {
        return noIncomeExempt;
    }

    public boolean isFreezeOnUnpaid() {
        return freezeOnUnpaid;
    }

    // ===== VIP 설정 =====

    /**
     * [v2] VIP LuckPerms 그룹명 조회
     */
    public String getVipLuckpermsGroup() {
        return vipLuckpermsGroup;
    }

    /**
     * [v2] VIP 세금 할인율 조회 (%)
     */
    public int getVipDiscountPercent() {
        return vipDiscountPercent;
    }

    /**
     * [v2] VIP 할인 적용
     * @param tax 원래 세금
     * @return 할인된 세금
     */
    public long applyVipDiscount(long tax) {
        if (vipDiscountPercent <= 0) {
            return tax;
        }
        return tax - (tax * vipDiscountPercent / 100);
    }

    // 메시지
    public String getMessage(String key) {
        return messages.getOrDefault(key, "§c[세금] 메시지를 찾을 수 없습니다: " + key);
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String msg = getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return msg;
    }

    // ===== 내부 클래스 =====

    /**
     * 소득세 구간
     */
    public static class TaxBracket {
        private final long min;
        private final long max;
        private final double rate;

        public TaxBracket(long min, long max, double rate) {
            this.min = min;
            this.max = max;
            this.rate = rate;
        }

        public long getMin() { return min; }
        public long getMax() { return max; }
        public double getRate() { return rate; }

        public boolean contains(long amount) {
            if (max == -1) {
                return amount >= min;
            }
            return amount >= min && amount <= max;
        }
    }

    /**
     * 주민 세금 구간
     */
    public static class VillagerTaxRate {
        private final int min;
        private final int max;
        private final long perVillager;

        public VillagerTaxRate(int min, int max, long perVillager) {
            this.min = min;
            this.max = max;
            this.perVillager = perVillager;
        }

        public int getMin() { return min; }
        public int getMax() { return max; }
        public long getPerVillager() { return perVillager; }

        public boolean contains(int count) {
            if (max == -1) {
                return count >= min;
            }
            return count >= min && count <= max;
        }
    }

    /**
     * [v2] 청크 누진 배율 구간
     */
    public static class ChunkProgressiveRate {
        private final int minChunks;
        private final int maxChunks;
        private final double multiplier;

        public ChunkProgressiveRate(int minChunks, int maxChunks, double multiplier) {
            this.minChunks = minChunks;
            this.maxChunks = maxChunks;
            this.multiplier = multiplier;
        }

        public int getMinChunks() { return minChunks; }
        public int getMaxChunks() { return maxChunks; }
        public double getMultiplier() { return multiplier; }

        /**
         * 이 구간에 해당하는 청크 수인지 확인
         */
        public boolean contains(int chunks) {
            if (maxChunks == -1) {
                return chunks >= minChunks;
            }
            return chunks >= minChunks && chunks <= maxChunks;
        }
    }
}
