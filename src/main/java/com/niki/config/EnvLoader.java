package com.niki.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Подхватывает .env при локальном запуске (mvn spring-boot:run).
 * На Render переменные задаются в панели — .env не нужен.
 */
public final class EnvLoader {

    private EnvLoader() {
    }

    public static void loadDotEnvIfPresent() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .filename(".env")
                .load();
        dotenv.entries().forEach(entry -> {
            String key = entry.getKey();
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, entry.getValue());
            }
        });
    }
}
