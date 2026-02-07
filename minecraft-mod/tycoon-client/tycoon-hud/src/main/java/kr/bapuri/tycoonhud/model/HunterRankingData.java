package kr.bapuri.tycoonhud.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 헌터 월드 순위 및 스탯 데이터
 * 
 * 서버로부터 HUNTER_RANKING 패킷으로 수신됩니다.
 */
public class HunterRankingData {

    @SerializedName("schema")
    private int schema;
    
    // ================================================================================
    // 순위 데이터
    // ================================================================================
    
    @SerializedName("rankings")
    private List<RankingEntry> rankings = new ArrayList<>();
    
    @SerializedName("myRank")
    private int myRank;
    
    @SerializedName("totalPlayers")
    private int totalPlayers;
    
    // ================================================================================
    // 현상금 데이터
    // ================================================================================
    
    @SerializedName("bounties")
    private List<BountyEntry> bounties = new ArrayList<>();
    
    // ================================================================================
    // 내 상세 스탯
    // ================================================================================
    
    @SerializedName("myKills")
    private int myKills;
    
    @SerializedName("myDeaths")
    private int myDeaths;
    
    @SerializedName("myAssists")
    private int myAssists;
    
    @SerializedName("myDamageDealt")
    private double myDamageDealt;
    
    @SerializedName("myDamageTaken")
    private double myDamageTaken;
    
    @SerializedName("myScore")
    private int myScore;
    
    @SerializedName("myAttackBonus")
    private double myAttackBonus;
    
    @SerializedName("myDefenseBonus")
    private double myDefenseBonus;
    
    @SerializedName("myBounty")
    private long myBounty;
    
    // ================================================================================
    // 추가 스탯 (HCL/증강/인챈트)
    // ================================================================================
    
    @SerializedName("myMaxHealthBonus")
    private double myMaxHealthBonus;
    
    @SerializedName("myStaminaEfficiency")
    private double myStaminaEfficiency;
    
    @SerializedName("mySpeedBonus")
    private double mySpeedBonus;
    
    @SerializedName("myCritChance")
    private double myCritChance;
    
    @SerializedName("myCritDamage")
    private double myCritDamage;
    
    @SerializedName("myArmorPenetration")
    private double myArmorPenetration;
    
    @SerializedName("myDamageReduction")
    private double myDamageReduction;
    
    @SerializedName("myLifesteal")
    private double myLifesteal;
    
    @SerializedName("myKnockbackResist")
    private double myKnockbackResist;
    
    @SerializedName("myExpBonus")
    private double myExpBonus;
    
    // ================================================================================
    // 증강 데이터
    // ================================================================================
    
    @SerializedName("myAugments")
    private List<AugmentEntry> myAugments = new ArrayList<>();
    
    // ================================================================================
    // 내부 클래스
    // ================================================================================
    
    public static class RankingEntry {
        @SerializedName("rank")
        public int rank;
        
        @SerializedName("playerName")
        public String playerName;
        
        @SerializedName("score")
        public int score;
        
        @SerializedName("kills")
        public int kills;
        
        @SerializedName("deaths")
        public int deaths;
        
        @SerializedName("assists")
        public int assists;
        
        @SerializedName("bounty")
        public long bounty;
        
        @SerializedName("isMe")
        public boolean isMe;
        
        public String getKdaText() {
            return kills + "/" + deaths + "/" + assists;
        }
    }
    
    public static class BountyEntry {
        @SerializedName("rank")
        public int rank;
        
        @SerializedName("playerName")
        public String playerName;
        
        @SerializedName("amount")
        public long amount;
        
        @SerializedName("killStreak")
        public int killStreak;
    }
    
    public static class AugmentEntry {
        @SerializedName("id")
        public String id;
        
        @SerializedName("name")
        public String name;
        
        @SerializedName("tier")
        public String tier;
        
        @SerializedName("description")
        public String description;
        
        public int getTierColor() {
            return switch (tier) {
                case "GOLD" -> 0xFFFFD700;
                case "PRISM" -> 0xFFE040FB;
                default -> 0xFFC0C0C0; // SILVER
            };
        }
    }
    
    // ================================================================================
    // Getters
    // ================================================================================
    
    public List<RankingEntry> getRankings() {
        return rankings != null ? rankings : new ArrayList<>();
    }
    
    public List<RankingEntry> getTop3Rankings() {
        List<RankingEntry> top3 = new ArrayList<>();
        for (RankingEntry entry : getRankings()) {
            if (entry.rank <= 3) {
                top3.add(entry);
            }
        }
        return top3;
    }
    
    public int getMyRank() {
        return myRank;
    }
    
    public int getTotalPlayers() {
        return totalPlayers;
    }
    
    public List<BountyEntry> getBounties() {
        return bounties != null ? bounties : new ArrayList<>();
    }
    
    public int getMyKills() {
        return myKills;
    }
    
    public int getMyDeaths() {
        return myDeaths;
    }
    
    public int getMyAssists() {
        return myAssists;
    }
    
    public String getMyKdaText() {
        return myKills + "/" + myDeaths + "/" + myAssists;
    }
    
    public double getMyDamageDealt() {
        return myDamageDealt;
    }
    
    public double getMyDamageTaken() {
        return myDamageTaken;
    }
    
    public String getMyDamageText() {
        return String.format("%.0f / %.0f", myDamageDealt, myDamageTaken);
    }
    
    public int getMyScore() {
        return myScore;
    }
    
    public double getMyAttackBonus() {
        return myAttackBonus;
    }
    
    public String getMyAttackBonusText() {
        return String.format("+%.0f%%", myAttackBonus);
    }
    
    public double getMyDefenseBonus() {
        return myDefenseBonus;
    }
    
    public String getMyDefenseBonusText() {
        return String.format("+%.0f%%", myDefenseBonus);
    }
    
    public long getMyBounty() {
        return myBounty;
    }
    
    // ================================================================================
    // 추가 스탯 Getters
    // ================================================================================
    
    public double getMyMaxHealthBonus() { return myMaxHealthBonus; }
    public String getMyMaxHealthBonusText() { return formatBonus(myMaxHealthBonus); }
    
    public double getMyStaminaEfficiency() { return myStaminaEfficiency; }
    public String getMyStaminaEfficiencyText() { return formatBonus(myStaminaEfficiency); }
    
    public double getMySpeedBonus() { return mySpeedBonus; }
    public String getMySpeedBonusText() { return formatBonus(mySpeedBonus); }
    
    public double getMyCritChance() { return myCritChance; }
    public String getMyCritChanceText() { return formatPercent(myCritChance); }
    
    public double getMyCritDamage() { return myCritDamage; }
    public String getMyCritDamageText() { return formatBonus(myCritDamage); }
    
    public double getMyArmorPenetration() { return myArmorPenetration; }
    public String getMyArmorPenetrationText() { return formatPercent(myArmorPenetration); }
    
    public double getMyDamageReduction() { return myDamageReduction; }
    public String getMyDamageReductionText() { return formatPercent(myDamageReduction); }
    
    public double getMyLifesteal() { return myLifesteal; }
    public String getMyLifestealText() { return formatPercent(myLifesteal); }
    
    public double getMyKnockbackResist() { return myKnockbackResist; }
    public String getMyKnockbackResistText() { return formatPercent(myKnockbackResist); }
    
    public double getMyExpBonus() { return myExpBonus; }
    public String getMyExpBonusText() { return formatBonus(myExpBonus); }
    
    private String formatBonus(double value) {
        if (value == 0) return "-";
        return String.format("+%.0f%%", value);
    }
    
    private String formatPercent(double value) {
        if (value == 0) return "-";
        return String.format("%.0f%%", value);
    }
    
    public List<AugmentEntry> getMyAugments() {
        return myAugments != null ? myAugments : new ArrayList<>();
    }
    
    public boolean isValid() {
        return rankings != null;
    }
}

