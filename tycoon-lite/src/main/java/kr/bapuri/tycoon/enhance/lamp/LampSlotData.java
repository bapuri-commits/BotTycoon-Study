package kr.bapuri.tycoon.enhance.lamp;

/**
 * LampSlotData - 램프 슬롯 데이터
 * 
 * v2.5 램프 시스템 재설계:
 * - 다중 슬롯 지원
 * - 수치 영속화 (롤링된 값 저장)
 * 
 * PDC 저장 형식: "effectId:value1:value2,effectId:value1:value2,..."
 * 
 * Phase 6: 레거시 업데이트
 */
public class LampSlotData {

    private final String effectId;
    private final double value1;    // 주 수치 (롤링된 값)
    private final int value2;       // 보조 수치 (있을 경우)

    public LampSlotData(String effectId, double value1, int value2) {
        this.effectId = effectId;
        this.value1 = value1;
        this.value2 = value2;
    }

    /**
     * 효과 ID 없는 빈 슬롯
     */
    public static LampSlotData empty() {
        return new LampSlotData(null, 0, 0);
    }

    /**
     * PDC 문자열에서 파싱
     * 형식: "effectId:value1:value2"
     */
    public static LampSlotData fromString(String data) {
        if (data == null || data.isEmpty() || data.equals("empty")) {
            return empty();
        }

        String[] parts = data.split(":");
        if (parts.length < 1) {
            return empty();
        }

        String effectId = parts[0];
        double value1 = 0;
        int value2 = 0;

        if (parts.length >= 2) {
            try {
                value1 = Double.parseDouble(parts[1]);
            } catch (NumberFormatException ignored) {}
        }

        if (parts.length >= 3) {
            try {
                value2 = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {}
        }

        return new LampSlotData(effectId, value1, value2);
    }

    /**
     * PDC 저장용 문자열 변환
     */
    public String toSaveString() {
        if (isEmpty()) {
            return "empty";
        }
        return effectId + ":" + value1 + ":" + value2;
    }

    /**
     * 빈 슬롯인지 확인
     */
    public boolean isEmpty() {
        return effectId == null || effectId.isEmpty();
    }

    /**
     * LampEffect 객체 가져오기
     */
    public LampEffect getEffect() {
        if (isEmpty()) return null;
        return LampEffect.fromId(effectId);
    }

    // ========== Getters ==========

    public String getEffectId() {
        return effectId;
    }

    public double getValue1() {
        return value1;
    }

    public int getValue2() {
        return value2;
    }

    /**
     * 압축 표시용 문자열 (Lore용)
     * 예: "생명력 흡수 7.5"
     */
    public String getCompactDisplay() {
        if (isEmpty()) return "§8빈 슬롯";

        LampEffect effect = getEffect();
        if (effect == null) return "§8알 수 없는 효과";

        String name = effect.getDisplayName();
        
        // 수치 포맷팅
        if (value2 > 0) {
            // 두 값 모두 있는 경우 (예: 광전사 30%/+5)
            return name + " §7" + formatValue(value1) + "/" + value2;
        } else if (value1 > 0) {
            // 첫 번째 값만 있는 경우
            return name + " §7" + formatValue(value1);
        }

        return name;
    }

    /**
     * 상세 표시용 문자열 (/lamp info용)
     */
    public String getDetailedDisplay() {
        if (isEmpty()) return "§8빈 슬롯";

        LampEffect effect = getEffect();
        if (effect == null) return "§8알 수 없는 효과";

        String rarity = effect.getRarity().getColorCode() + "[" + effect.getRarity().getDisplayName() + "] ";
        String name = effect.getDisplayName();
        String desc = effect.getDescription(value1, value2);

        return rarity + name + "\n  §7" + desc;
    }

    /**
     * 수치 포맷팅 (정수면 정수로, 소수면 소수점 1자리)
     */
    private String formatValue(double value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        }
        return String.format("%.1f", value);
    }

    @Override
    public String toString() {
        return "LampSlotData{" +
                "effectId='" + effectId + '\'' +
                ", value1=" + value1 +
                ", value2=" + value2 +
                '}';
    }
}
