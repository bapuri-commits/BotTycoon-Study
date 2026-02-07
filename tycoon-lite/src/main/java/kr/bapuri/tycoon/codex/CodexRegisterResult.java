package kr.bapuri.tycoon.codex;

/**
 * 도감 등록 결과
 */
public enum CodexRegisterResult {
    /** 등록 성공 */
    SUCCESS,
    /** 도감에 등록 가능한 아이템이 아님 */
    NOT_IN_CODEX,
    /** 이미 등록됨 */
    ALREADY_REGISTERED,
    /** 아이템 수량 부족 */
    NOT_ENOUGH_ITEMS
}
