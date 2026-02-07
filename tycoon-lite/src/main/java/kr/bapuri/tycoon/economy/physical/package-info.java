/**
 * 물리적 화폐 시스템 패키지
 * 
 * BD 수표와 BottCoin 바우처를 아이템 형태로 변환하여
 * 플레이어 간 거래를 가능하게 함
 * 
 * [Phase 3.A] 기반 구조만 정의
 * [Phase 5+] CoreItemAuthenticator와 함께 실제 구현
 * 
 * 구성:
 * - PhysicalCurrency: 공통 인터페이스
 * - CheckService: BD 수표 서비스 (Phase 5+)
 * - VoucherService: BottCoin 바우처 서비스 (Phase 5+)
 */
package kr.bapuri.tycoon.economy.physical;
