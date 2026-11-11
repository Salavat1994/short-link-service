package ru.homework.shortlinks.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Параметры приложения из application.properties (внешний файл имеет приоритет). */
public record AppConfig(
        long ttlSeconds,
        int defaultClicks,
        int maxClicks,
        long cleanupSeconds,
        String prefix,
        Path storagePath) {

    public AppConfig {
        if (ttlSeconds <= 0 || cleanupSeconds <= 0) {
            throw new IllegalArgumentException("TTL и интервал очистки должны быть положительными.");
        }
        if (defaultClicks <= 0 || maxClicks < defaultClicks) {
            throw new IllegalArgumentException("Некорректные ограничения количества переходов.");
        }
        if (prefix == null || !prefix.matches("[a-zA-Z0-9.-]+/")) {
            throw new IllegalArgumentException("Префикс должен иметь вид short.local/.");
        }
        if (storagePath == null) {
            throw new IllegalArgumentException("Не указан путь хранения ссылок.");
        }
    }

    public static AppConfig load() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (input == null) {
                throw new IOException("Не найден встроенный application.properties.");
            }
            properties.load(input);
        }

        Path externalConfig = Path.of("config", "application.properties");
        if (Files.exists(externalConfig)) {
            try (InputStream input = Files.newInputStream(externalConfig)) {
                properties.load(input);
            }
        }
        try {
            return new AppConfig(
                    Long.parseLong(required(properties, "link.ttl.seconds")),
                    Integer.parseInt(required(properties, "link.clicks.default")),
                    Integer.parseInt(required(properties, "link.clicks.max")),
                    Long.parseLong(required(properties, "link.cleanup.seconds")),
                    required(properties, "link.prefix"),
                    Path.of(required(properties, "link.storage.path")));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("TTL, лимиты и интервал очистки должны быть целыми числами.", exception);
        }
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Не задан параметр конфигурации: " + name);
        }
        return value.trim();
    }
}
