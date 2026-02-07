package kr.bapuri.koreanfix.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import kr.bapuri.koreanfix.KoreanInputFixMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class ModConfig {

    // ===== 1. 싱글톤 인스턴스 =====
    private static ModConfig INSTANCE;

    // ===== 2. 설정 필드 =====
    // Gson이 JSON과 자동 매핑할 필드들
    public boolean enabled = true;  // 모드 활성화 여부

    // ===== 3. 싱글톤 접근자 =====
    public static ModConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();  // 최초 접근 시 파일에서 로드
        }
        return INSTANCE;
    }

    // ===== 4. 설정 파일 경로 =====
    private static Path getConfigPath() {
        // Fabric API로 config 폴더 경로 얻기
        // 결과: .minecraft/config/koreanfix.json
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("koreanfix.json");
    }

    // ===== 5. 파일에서 로드 =====
    private static ModConfig load() {
        Path path = getConfigPath();

        // 파일이 존재하면 읽기
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                Gson gson = new Gson();
                ModConfig config = gson.fromJson(json, ModConfig.class);

                // null 체크 (파일이 비어있거나 파싱 실패 시)
                if (config != null) {
                    return config;
                }
            } catch (IOException e) {
                KoreanInputFixMod.LOGGER.error("설정 파일 로드 실패", e);
            }
        }

        // 파일이 없거나 실패 시 기본값으로 생성 후 저장
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    // ===== 6. 파일에 저장 =====
    public void save() {
        Path path = getConfigPath();

        try {
            // 예쁘게 출력 (들여쓰기 포함)
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(this);

            // 파일 쓰기
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            KoreanInputFixMod.LOGGER.error("설정 파일 저장 실패", e);
        }
    }
}