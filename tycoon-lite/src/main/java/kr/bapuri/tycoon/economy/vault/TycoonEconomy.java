package kr.bapuri.tycoon.economy.vault;

import kr.bapuri.tycoon.economy.EconomyService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Vault Economy 구현체
 * 
 * Tycoon의 BD 경제를 Vault Economy 인터페이스로 노출
 * - 다른 플러그인이 Vault API로 Tycoon 경제 사용 가능
 * - BD (BottDollar)를 기본 통화로 사용
 * - BottCoin은 Vault에 노출되지 않음 (내부 API만)
 * 
 * [Phase 3.A] TycoonLite용 간소화 버전
 */
public class TycoonEconomy implements Economy {
    
    private final Plugin plugin;
    private final EconomyService economyService;
    private final Logger logger;
    
    public TycoonEconomy(Plugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.logger = Logger.getLogger("Tycoon.VaultEconomy");
    }
    
    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }
    
    @Override
    public String getName() {
        return "TycoonLite";
    }
    
    @Override
    public boolean hasBankSupport() {
        return false;
    }
    
    @Override
    public int fractionalDigits() {
        return 0;  // BD는 정수 단위
    }
    
    @Override
    public String format(double amount) {
        return String.format("%,.0f BD", amount);
    }
    
    @Override
    public String currencyNamePlural() {
        return "BD";
    }
    
    @Override
    public String currencyNameSingular() {
        return "BD";
    }
    
    // ========== playerName → UUID 변환 헬퍼 ==========
    
    @SuppressWarnings("deprecation")
    private OfflinePlayer getOfflinePlayerByName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return null;
        }
        return Bukkit.getOfflinePlayer(playerName);
    }
    
    private UUID getUuidByName(String playerName) {
        OfflinePlayer player = getOfflinePlayerByName(playerName);
        if (player == null) {
            return null;
        }
        
        // 실제 플레이어 확인
        if (!player.hasPlayedBefore() && !player.isOnline()) {
            logger.fine("[Vault] Unknown player (never played): " + playerName);
            return null;
        }
        
        return player.getUniqueId();
    }
    
    // ========== 계정 관련 ==========
    
    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;  // 모든 플레이어는 계정이 있음
    }
    
    @Override
    public boolean hasAccount(String playerName) {
        OfflinePlayer player = getOfflinePlayerByName(playerName);
        return player != null && player.hasPlayedBefore();
    }
    
    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }
    
    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }
    
    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;  // 자동 생성
    }
    
    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }
    
    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }
    
    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }
    
    // ========== 잔액 조회 ==========
    
    @Override
    public double getBalance(OfflinePlayer player) {
        return economyService.getMoney(player.getUniqueId());
    }
    
    @Override
    public double getBalance(String playerName) {
        UUID uuid = getUuidByName(playerName);
        if (uuid == null) {
            logger.warning("[Vault] getBalance failed: player not found - " + playerName);
            return 0;
        }
        return economyService.getMoney(uuid);
    }
    
    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }
    
    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }
    
    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }
    
    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }
    
    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }
    
    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }
    
    // ========== 출금 ==========
    
    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), 
                EconomyResponse.ResponseType.FAILURE, "음수 금액 불가");
        }
        
        long longAmount = (long) amount;
        boolean success = economyService.withdraw(player.getUniqueId(), longAmount, 
                "Vault API withdraw", "VaultIntegration");
        
        if (success) {
            return new EconomyResponse(amount, getBalance(player),
                EconomyResponse.ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, getBalance(player),
                EconomyResponse.ResponseType.FAILURE, "잔액 부족");
        }
    }
    
    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        UUID uuid = getUuidByName(playerName);
        if (uuid == null) {
            logger.warning("[Vault] withdrawPlayer failed: player not found - " + playerName);
            return new EconomyResponse(0, 0, 
                EconomyResponse.ResponseType.FAILURE, "플레이어를 찾을 수 없음");
        }
        
        if (amount < 0) {
            return new EconomyResponse(0, economyService.getMoney(uuid), 
                EconomyResponse.ResponseType.FAILURE, "음수 금액 불가");
        }
        
        long longAmount = (long) amount;
        boolean success = economyService.withdraw(uuid, longAmount, 
                "Vault API withdraw (by name)", "VaultIntegration");
        
        if (success) {
            return new EconomyResponse(amount, economyService.getMoney(uuid),
                EconomyResponse.ResponseType.SUCCESS, null);
        } else {
            return new EconomyResponse(0, economyService.getMoney(uuid),
                EconomyResponse.ResponseType.FAILURE, "잔액 부족");
        }
    }
    
    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }
    
    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }
    
    // ========== 입금 ==========
    
    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player),
                EconomyResponse.ResponseType.FAILURE, "음수 금액 불가");
        }
        
        long longAmount = (long) amount;
        economyService.deposit(player.getUniqueId(), longAmount, 
                "Vault API deposit", "VaultIntegration");
        
        return new EconomyResponse(amount, getBalance(player),
            EconomyResponse.ResponseType.SUCCESS, null);
    }
    
    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        UUID uuid = getUuidByName(playerName);
        if (uuid == null) {
            logger.warning("[Vault] depositPlayer failed: player not found - " + playerName);
            return new EconomyResponse(0, 0,
                EconomyResponse.ResponseType.FAILURE, "플레이어를 찾을 수 없음");
        }
        
        if (amount < 0) {
            return new EconomyResponse(0, economyService.getMoney(uuid),
                EconomyResponse.ResponseType.FAILURE, "음수 금액 불가");
        }
        
        long longAmount = (long) amount;
        economyService.deposit(uuid, longAmount, 
                "Vault API deposit (by name)", "VaultIntegration");
        
        return new EconomyResponse(amount, economyService.getMoney(uuid),
            EconomyResponse.ResponseType.SUCCESS, null);
    }
    
    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }
    
    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }
    
    // ========== 은행 (미지원) ==========
    
    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0, 
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED, "은행 미지원");
    }
    
    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }
}
