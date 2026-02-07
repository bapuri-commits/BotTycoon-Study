package kr.bapuri.tycoon.enhance.lamp;

import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static kr.bapuri.tycoon.enhance.common.EnhanceConstants.*;

/**
 * LampItemFactory - 램프 아이템 생성
 * 
 * Phase 6: 레거시 복사
 */
public class LampItemFactory {

    private final LampRegistry registry;

    public LampItemFactory(LampRegistry registry) {
        this.registry = registry;
    }

    /**
     * 램프 아이템 생성
     */
    public ItemStack createLamp(LampType lampType) {
        if (lampType == null) return null;

        ItemStack lamp = new ItemStack(lampType.getMaterial());
        ItemMeta meta = lamp.getItemMeta();
        if (meta == null) return null;

        // 이름 설정
        meta.setDisplayName(lampType.getDisplayName());

        // Lore 설정
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + lampType.getDescription());
        lore.add("");
        lore.add("§7적용 가능: §f" + lampType.getApplicableDescription());
        lore.add("");
        lore.add("§8─────────────────────");
        lore.add("§f등장 가능 효과 (예시)");
        addSampleEffects(lore, lampType);
        lore.add("§8─────────────────────");
        lore.add("");
        lore.add("§e등급 확률");
        lore.add("§f일반 §750.5% §8| §a고급 §730.3% §8| §9희귀 §714.1%");
        lore.add("§5영웅 §74.0% §8| §6전설 §71.0%");
        lore.add("");
        lore.add("§7천장: §e150회 사용 시 전설 확정!");
        lore.add("§c※ 기존 램프 효과는 덮어씌워집니다.");
        lore.add("");
        lore.add("§e▶ 대상 아이템에 드래그&드롭하여 사용");

        meta.setLore(lore);
        
        // CustomModelData 설정 (리소스팩 연동)
        int customModelData = getCustomModelDataForLamp(lampType);
        meta.setCustomModelData(customModelData);
        
        lamp.setItemMeta(meta);

        // PDC에 램프 타입 저장
        EnhanceItemUtil.setLampItemType(lamp, lampType.getId());

        return lamp;
    }
    
    /**
     * 램프 타입에 따른 CustomModelData 반환
     * 
     * [2026-01-31] SHOVEL_LAMP, AXE_LAMP 중복 버그 수정
     */
    private int getCustomModelDataForLamp(LampType lampType) {
        return switch (lampType) {
            case WEAPON_LAMP -> CMD_ENHANCE_LAMP_1;     // 2000
            case ARMOR_LAMP -> CMD_ENHANCE_LAMP_2;      // 2001
            case TOOL_LAMP -> CMD_ENHANCE_LAMP_3;       // 2002
            case UNIVERSAL_LAMP -> CMD_ENHANCE_LAMP_4;  // 2003
            case SHOVEL_LAMP -> CMD_ENHANCE_LAMP_5;     // 2004 (수정됨)
            case AXE_LAMP -> CMD_ENHANCE_LAMP_6;        // 2005 (수정됨)
            case PICKAXE_LAMP -> CMD_BLESSED_LAMP_1;    // 2010
            case HOE_LAMP -> CMD_BLESSED_LAMP_2;        // 2011
            case FISHING_LAMP -> CMD_BLESSED_LAMP_3;    // 2012
        };
    }

    /**
     * 램프 아이템 여러 개 생성
     */
    public ItemStack createLamps(LampType lampType, int amount) {
        ItemStack lamp = createLamp(lampType);
        if (lamp != null) {
            lamp.setAmount(Math.min(64, Math.max(1, amount)));
        }
        return lamp;
    }

    /**
     * ItemStack이 램프 아이템인지 확인
     */
    public static boolean isLampItem(ItemStack item) {
        return EnhanceItemUtil.isLampItem(item);
    }

    /**
     * 램프 아이템에서 타입 추출
     */
    public static LampType getLampType(ItemStack item) {
        String typeId = EnhanceItemUtil.getLampItemType(item);
        return LampType.fromId(typeId);
    }

    /**
     * 모든 램프 타입에 대한 샘플 생성
     */
    public List<ItemStack> createAllLampSamples() {
        List<ItemStack> samples = new ArrayList<>();
        for (LampType type : LampType.values()) {
            samples.add(createLamp(type));
        }
        return samples;
    }
    
    /**
     * 램프 타입별 대표 효과 추가 (Lore용)
     */
    private void addSampleEffects(List<String> lore, LampType lampType) {
        switch (lampType) {
            case WEAPON_LAMP -> {
                lore.add("§c• 공격력 강화 §7(+1~+10)");
                lore.add("§c• 흡혈 화살 §7(5~15% 회복)");
                lore.add("§5• 처형 §7(10~80% 즉사 확률)");
                lore.add("§6• 거인 학살자 §7(체력 차 비례 피해)");
            }
            case ARMOR_LAMP -> {
                lore.add("§a• 체력 강화 §7(+3~+10 HP)");
                lore.add("§9• 강철 의지 §7(피해 10~30% 감소)");
                lore.add("§5• 가시 오라 §7(10~20% 반사)");
                lore.add("§6• 불사조의 축복 §7(사망 시 부활)");
            }
            case PICKAXE_LAMP -> {
                lore.add("§9• 광역 채굴 §7(상하좌우 1칸)");
                lore.add("§5• 광역 채굴 II §7(3×3 영역)");
                lore.add("§6• 자동 제련 §7(주괴로 드랍)");
                lore.add("§6• 미다스의 손 §7(BD 획득)");
            }
            case HOE_LAMP -> {
                lore.add("§f• 풍요의 축복 I §7(15% 확률, +1 작물)");
                lore.add("§9• 풍요의 축복 V §7(30% 확률, +2 작물)");
                lore.add("§6• 풍요의 축복 IX §7(50% 확률, +3 작물)");
                lore.add("§9• 씨앗 축복 III §7(50% 확률, +3 씨앗)");
            }
            case FISHING_LAMP -> {
                lore.add("§a• 쓰레기 감소 §7(10~30%)");
                lore.add("§a• 빠른 입질 §7(20~50% 시간 감소)");
                lore.add("§5• 쌍둥이 어획 §7(10~50% 2배)");
                lore.add("§6• 자동 릴 §7(자동 낚시)");
            }
            case SHOVEL_LAMP -> {
                lore.add("§9• 유리 세공 §7(10~100% 유리 변환)");
            }
            case AXE_LAMP -> {
                lore.add("§a• 벌목꾼 I §7(위로 3칸 동시 채굴)");
                lore.add("§9• 벌목꾼 II §7(위로 4칸 동시 채굴)");
                lore.add("§5• 벌목꾼 III §7(위로 5칸 동시 채굴)");
            }
            case TOOL_LAMP -> {
                lore.add("§a• 광역 채굴 §7(상하좌우 1칸)");
                lore.add("§5• 벌목꾼 §7(위로 동시 채굴)");
                lore.add("§6• 미다스의 손 §7(BD 획득)");
            }
            case UNIVERSAL_LAMP -> {
                lore.add("§f• 생명력 흡수 §7(+1~+10 HP 회복)");
                lore.add("§e• 치명타 확률 §7(+10~40%)");
                lore.add("§a• 경험치 수집 §7(추가 경험치)");
                lore.add("§5• 행운 오라 §7(발동률 +7~20%)");
            }
        }
    }
}
