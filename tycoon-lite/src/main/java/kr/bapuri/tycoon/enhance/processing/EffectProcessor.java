package kr.bapuri.tycoon.enhance.processing;

/**
 * EffectProcessor - 효과 처리기 인터페이스
 * 
 * 각 Processor는 하나의 효과 카테고리를 담당합니다.
 * 우선순위에 따라 순차적으로 실행됩니다.
 * 
 * 우선순위 가이드:
 * - 100: Fortune (드롭량 기본 배율)
 * - 150: EnchantDropBonus (EXPERTISE, LUCKY_HAND)
 * - 200: GradeBonus (직업 등급 보너스)
 * - 400: LampEffect (램프 효과 - AUTO_SMELT 등)
 * - 500: JobExp (직업 경험치)
 * - 900: Delivery (Telekinesis, 드롭 전달, 블록 제거)
 */
public interface EffectProcessor {
    
    /**
     * 처리기 이름 (로깅/디버깅용)
     */
    String getName();
    
    /**
     * 우선순위 (낮을수록 먼저 실행)
     */
    int getPriority();
    
    /**
     * 이 처리기를 실행해야 하는지 판단
     * 
     * @param context 처리 컨텍스트
     * @return true면 process() 호출, false면 스킵
     */
    boolean shouldProcess(ProcessingContext context);
    
    /**
     * 처리 실행
     * 
     * @param context 처리 컨텍스트 (직접 수정)
     */
    void process(ProcessingContext context);
}
