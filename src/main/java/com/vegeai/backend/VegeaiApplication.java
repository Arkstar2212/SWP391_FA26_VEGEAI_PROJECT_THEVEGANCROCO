package com.vegeai.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class VegeaiApplication {
    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(VegeaiApplication.class, args);
    }

    private static void loadDotEnvIfPresent() {
        List<Path> candidatePaths = List.of(
                Path.of(".env"),
                Path.of("SWP391_FA26_VEGEAI_PROJECT_THEVEGANCROCO", ".env"),
                Path.of("..", ".env")
        );

        for (Path path : candidatePaths) {
            if (Files.isRegularFile(path)) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                            continue;
                        }
                        int eqIdx = trimmed.indexOf('=');
                        String key = trimmed.substring(0, eqIdx).trim();
                        String value = trimmed.substring(eqIdx + 1).trim();
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                    break;
                } catch (IOException ignored) {
                }
            }
        }
    }
}
