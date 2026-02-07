package kr.bapuri.tycoon.enhance.enchant;

import kr.bapuri.tycoon.enhance.common.EnhanceConstants;
import kr.bapuri.tycoon.enhance.common.EnhanceItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * EnchantBookFactory - 커스텀 인챈트 북 생성
 * 
 * 커스텀 인챈트가 담긴 마법 부여된 책 아이템 생성
 * 
 * Phase 6: 레거시 복사
 */
public class EnchantBookFactory {

    private final CustomEnchantRegistry registry;

    public EnchantBookFactory(CustomEnchantRegistry registry) {
        this.registry = registry;
    }

    /**
     * 커스텀 인챈트 북 생성
     * 
     * @param enchantId 인챈트 ID
     * @param level 인챈트 레벨
     * @return 인챈트 북 ItemStack, 실패시 null
     */
    public ItemStack createBook(String enchantId, int level) {
        CustomEnchant enchant = CustomEnchant.fromId(enchantId);
        if (enchant == null) return null;

        CustomEnchantData data = registry.getData(enchantId);
        if (data == null || !data.isEnabled()) return null;

        if (level < 1 || level > data.getMaxLevel()) return null;

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return null;

        // 이름 설정
        String levelStr = toRoman(level);
        meta.setDisplayName(EnhanceConstants.COLOR_ENCHANT + "인챈트 북: " + 
                           data.getDisplayName() + " " + levelStr);

        // Lore 설정
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7인챈트: " + EnhanceConstants.COLOR_ENCHANT + data.getDisplayName() + " " + levelStr);
        lore.add("§7레벨: §e" + level + "/" + data.getMaxLevel());
        lore.add("");
        lore.add("§8─────────────────────");
        lore.add("§f효과 설명");
        lore.add(enchant.getEffectDescription(level));
        lore.add("§8─────────────────────");
        lore.add("");
        lore.add("§7적용 대상: §f" + getCategoryName(enchant.getCategory()));
        lore.add("§7적용 장비: §f" + getApplicableTypes(enchant));
        lore.add("");
        lore.add("§e▶ 모루에서 아이템과 합쳐 사용하세요.");
        
        meta.setLore(lore);
        
        // CustomModelData 설정 (리소스팩 연동)
        int customModelData = getCustomModelDataForLevel(level);
        meta.setCustomModelData(customModelData);
        
        book.setItemMeta(meta);

        // PDC에 인챈트 ID 저장
        EnhanceItemUtil.setEnchantBookId(book, enchantId, level);

        return book;
    }
    
    /**
     * 레벨에 따른 CustomModelData 반환
     * 
     * @param level 인챈트 레벨 (1~5+)
     * @return CustomModelData 값 (2060~2064)
     */
    private int getCustomModelDataForLevel(int level) {
        int tier = Math.min(5, Math.max(1, level));
        return EnhanceConstants.CMD_ENCHANT_BOOK_1 + (tier - 1);
    }

    /**
     * 여러 인챈트 북 생성 (수량 지정)
     */
    public ItemStack createBooks(String enchantId, int level, int amount) {
        ItemStack book = createBook(enchantId, level);
        if (book != null) {
            book.setAmount(Math.min(64, Math.max(1, amount)));
        }
        return book;
    }

    /**
     * 랜덤 인챈트 북 생성
     */
    public ItemStack createRandomBook() {
        List<CustomEnchantData> enabled = registry.getEnabledEnchants();
        if (enabled.isEmpty()) return null;

        CustomEnchantData data = enabled.get((int) (Math.random() * enabled.size()));
        int level = 1 + (int) (Math.random() * data.getMaxLevel());

        return createBook(data.getId(), level);
    }

    /**
     * 특정 카테고리에서 랜덤 인챈트 북 생성
     */
    public ItemStack createRandomBook(CustomEnchant.EnchantCategory category) {
        List<CustomEnchantData> enabled = registry.getEnabledByCategory(category);
        if (enabled.isEmpty()) return null;

        CustomEnchantData data = enabled.get((int) (Math.random() * enabled.size()));
        int level = 1 + (int) (Math.random() * data.getMaxLevel());

        return createBook(data.getId(), level);
    }

    /**
     * 인챈트 북에서 정보 읽기
     * 
     * @return [enchantId, level] 배열, 실패시 null
     */
    public static String[] readBookInfo(ItemStack book) {
        String data = EnhanceItemUtil.getEnchantBookId(book);
        if (data == null) return null;

        String[] parts = data.split(":");
        if (parts.length != 2) return null;

        return parts;
    }

    /**
     * 인챈트 북인지 확인
     */
    public static boolean isEnchantBook(ItemStack item) {
        return EnhanceItemUtil.isEnchantBook(item);
    }

    // ========== 유틸리티 ==========

    private String toRoman(int number) {
        if (number <= 0 || number > 10) return String.valueOf(number);
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return romans[number - 1];
    }

    private String getCategoryName(CustomEnchant.EnchantCategory category) {
        return switch (category) {
            case WEAPON -> "무기";
            case ARMOR -> "방어구";
            case TOOL -> "도구";
            case BOW -> "원거리";
            case UNIVERSAL -> "범용";
            case HYBRID -> "하이브리드";
        };
    }
    
    private String getApplicableTypes(CustomEnchant enchant) {
        StringBuilder sb = new StringBuilder();
        for (String type : enchant.getApplicableTypes()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(translateType(type));
        }
        return sb.toString();
    }
    
    private String translateType(String type) {
        return switch (type.toUpperCase()) {
            case "SWORD" -> "검";
            case "AXE" -> "도끼";
            case "PICKAXE" -> "곡괭이";
            case "SHOVEL" -> "삽";
            case "HOE" -> "괭이";
            case "BOW" -> "활";
            case "CROSSBOW" -> "석궁";
            case "FISHING_ROD" -> "낚싯대";
            case "HELMET" -> "투구";
            case "CHESTPLATE" -> "흉갑";
            case "LEGGINGS" -> "레깅스";
            case "BOOTS" -> "부츠";
            case "TRIDENT" -> "삼지창";
            case "SHEARS" -> "가위";
            case "ELYTRA" -> "겉날개";
            case "SHIELD" -> "방패";
            default -> type;
        };
    }
}
