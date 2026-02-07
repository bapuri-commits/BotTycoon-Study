package kr.bapuri.tycoon.economy.physical;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * PhysicalCurrency - 물리적 화폐 인터페이스
 * 
 * BD 수표와 BottCoin 바우처의 공통 인터페이스
 * 
 * [Phase 3.A] 기반 구조만 - 구현은 Phase 5+ (CoreItemAuthenticator 필요)
 */
public interface PhysicalCurrency {
    
    /**
     * 물리적 화폐 발행
     * 
     * @param player 발행자
     * @param amount 금액/수량
     * @return 발행된 아이템 (실패 시 null)
     */
    ItemStack issue(Player player, long amount);
    
    /**
     * 물리적 화폐 환전
     * 
     * @param player 환전자
     * @param item 물리적 화폐 아이템
     * @return 환전된 금액/수량 (실패 시 0)
     */
    long redeem(Player player, ItemStack item);
    
    /**
     * 아이템이 이 물리적 화폐인지 확인
     */
    boolean isValid(ItemStack item);
    
    /**
     * 금액/수량 조회 (검증 없이)
     */
    Long getAmount(ItemStack item);
    
    /**
     * 발행자 UUID 조회
     */
    UUID getIssuerUuid(ItemStack item);
    
    /**
     * 발행자 이름 조회
     */
    String getIssuerName(ItemStack item);
    
    /**
     * 최소 발행 금액
     */
    long getMinAmount();
    
    /**
     * 최대 발행 금액
     */
    long getMaxAmount();
}
