package com.example.hazedetector.server.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EnvFile {
    private static final Map<String, String> VALUES = load();

    private EnvFile() {
    }

    static String get(String configuredValue, String... keys) {
        String value = clean(configuredValue);
        if (!value.isBlank()) {
            return value;
        }

        for (String key : keys) {
            value = clean(System.getenv(key));
            if (!value.isBlank()) {
                return value;
            }

            value = clean(System.getProperty(key));
            if (!value.isBlank()) {
                return value;
            }

            value = clean(VALUES.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private static Map<String, String> load() {
        Map<String, String> values = new HashMap<>();
        for (Path path : List.of(Path.of(".env"), Path.of("..", ".env"))) {
            if (Files.isRegularFile(path)) {
                read(path, values);
            }
        }
        return values;
    }

    private static void read(Path path, Map<String, String> values) {
        try {
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int index = trimmed.indexOf('=');
                if (index <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, index).trim();
                String value = clean(trimmed.substring(index + 1));
                values.putIfAbsent(key, value);
            }
        } catch (IOException ignored) {
            // Missing or unreadable .env files should not stop the server.
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.trim();
        if (cleaned.length() >= 2
            && ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
            || (cleaned.startsWith("'") && cleaned.endsWith("'")))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }
}
