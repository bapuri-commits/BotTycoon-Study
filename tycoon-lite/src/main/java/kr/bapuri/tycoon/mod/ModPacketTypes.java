package kr.bapuri.tycoon.mod;

/**
 * ModPacketTypes - 모드 연동 패킷 타입 상수
 * 
 * <h2>패킷 타입 정의</h2>
 * <p>서버와 클라이언트 간 통신에 사용되는 모든 패킷 타입을 정의합니다.</p>
 * 
 * <h2>규칙</h2>
 * <ul>
 *   <li>새 패킷 추가 시 이 클래스에 상수 추가</li>
 *   <li>PROTOCOL.md 문서도 함께 업데이트</li>
 *   <li>클라이언트 모드에서도 동일한 상수 사용 권장</li>
 * </ul>
 * 
 * @see ModDataService 패킷 송신
 * @see ModRequestHandler 패킷 수신
 */
public final class ModPacketTypes {
    
    private ModPacketTypes() {
        // 인스턴스화 방지
    }
    
    // ========================================================================
    // 채널 ID
    // ========================================================================
    
    /** 서버 → 클라이언트 채널 */
    public static final String CHANNEL_UI_DATA = "tycoon:ui_data";
    
    /** 클라이언트 → 서버 채널 */
    public static final String CHANNEL_UI_REQUEST = "tycoon:ui_request";
    
    // ========================================================================
    // 서버 → 클라이언트 패킷 (송신)
    // ========================================================================
    
    /** 전체 플레이어 프로필 (로그인 시, 요청 시) */
    public static final String PLAYER_PROFILE = "PLAYER_PROFILE";
    
    /** 체력/배고픔/산소/경험치 업데이트 */
    public static final String VITAL_UPDATE = "VITAL_UPDATE";
    
    /** BD/BottCoin 업데이트 */
    public static final String ECONOMY_UPDATE = "ECONOMY_UPDATE";
    
    /** 직업 기본 정보 (선택/레벨업 시) */
    public static final String JOB_DATA = "JOB_DATA";
    
    /** 직업 상세 정보 (승급 조건, 통계 등) */
    public static final String JOB_DETAIL = "JOB_DETAIL";
    
    /** 땅 정보 업데이트 (Lands 연동) */
    public static final String PLOT_UPDATE = "PLOT_UPDATE";
    
    /** 도감 요약 (카테고리 목록, 진행도) */
    public static final String CODEX_DATA = "CODEX_DATA";
    
    /** 도감 카테고리 상세 (아이템 목록) */
    public static final String CODEX_CATEGORY_DATA = "CODEX_CATEGORY_DATA";
    
    // ========================================================================
    // 서버 → 클라이언트 패킷 (신규 - Phase 1에서 구현)
    // ========================================================================
    
    /** 직업 경험치 변경 (실시간) */
    public static final String JOB_EXP_UPDATE = "JOB_EXP_UPDATE";
    
    /** 직업 레벨업 알림 */
    public static final String JOB_LEVEL_UP = "JOB_LEVEL_UP";
    
    /** 직업 승급 알림 (NPC에서 승급 시) */
    public static final String JOB_GRADE_UP = "JOB_GRADE_UP";
    
    /** 도감 등록 결과 */
    public static final String CODEX_REGISTER_RESULT = "CODEX_REGISTER_RESULT";
    
    /** 도감 아이템 등록 알림 (서버에서 등록 시) */
    public static final String CODEX_ITEM_REGISTERED = "CODEX_ITEM_REGISTERED";
    
    // ========================================================================
    // 클라이언트 → 서버 패킷 (수신)
    // ========================================================================
    
    /** 프로필 요청 */
    public static final String REQUEST_PROFILE = "PROFILE";
    
    /** Vital 요청 */
    public static final String REQUEST_VITAL = "VITAL";
    
    /** 도감 요약 요청 */
    public static final String REQUEST_CODEX_SUMMARY = "REQUEST_CODEX_SUMMARY";
    
    /** 도감 카테고리 상세 요청 */
    public static final String REQUEST_CODEX_CATEGORY = "REQUEST_CODEX_CATEGORY";
    
    /** 도감 아이템 등록 요청 */
    public static final String REGISTER_CODEX_ITEM = "REGISTER_CODEX_ITEM";
    
    /** 직업 상세 정보 요청 */
    public static final String REQUEST_JOB_DETAIL = "REQUEST_JOB_DETAIL";
    
    /** 직업 승급 시도 (현재 LITE에서는 미지원) */
    public static final String TRIGGER_JOB_PROMOTION = "TRIGGER_JOB_PROMOTION";
    
    // ========================================================================
    // 스키마 버전
    // ========================================================================
    
    /** 
     * 현재 스키마 버전
     * <p>변경 이력:</p>
     * <ul>
     *   <li>v1: 초기 버전</li>
     *   <li>v2: 도감 카테고리 분리</li>
     *   <li>v3: 업적 상세 정보 추가</li>
     *   <li>v4: 직업 실시간 업데이트, 도감 피드백 추가 (예정)</li>
     * </ul>
     */
    public static final int SCHEMA_VERSION = 3;
    
    /** 다음 스키마 버전 (Phase 1 완료 후) */
    public static final int NEXT_SCHEMA_VERSION = 4;
}
