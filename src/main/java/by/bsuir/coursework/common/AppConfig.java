package by.bsuir.coursework.common;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;

public final class AppConfig {
    private static final Map<String, String> ENV_TO_PROP = Map.ofEntries(
        Map.entry("APP_HOST", "app.host"),
        Map.entry("APP_PORT", "app.port"),
        Map.entry("APP_THREADS", "app.threads"),
        Map.entry("APP_DB_URL", "app.db.url"),
        Map.entry("APP_DB_USER", "app.db.user"),
        Map.entry("APP_DB_PASSWORD", "app.db.password"),
        Map.entry("APP_CONFIG", "app.config")
    );

    private AppConfig() {
    }

    public static void apply(String[] args) {
        // 1) properties file (lowest priority here)
        loadPropertiesFile();

        // 2) env vars (only if missing)
        for (var e : ENV_TO_PROP.entrySet()) {
            String envVal = System.getenv(e.getKey());
            if (envVal != null && !envVal.isBlank()) {
                setIfAbsent(e.getValue(), envVal);
            }
        }

        // 3) CLI args --key=value (highest priority)
        if (args != null) {
            for (String arg : args) {
                if (arg == null) continue;
                String a = arg.trim();
                if (!a.startsWith("--")) continue;
                int idx = a.indexOf('=');
                if (idx <= 2) continue;
                String key = a.substring(2, idx).trim();
                String val = a.substring(idx + 1).trim();
                if (!key.isEmpty() && !val.isEmpty()) {
                    System.setProperty(key, val);
                }
            }
        }
    }

    private static void loadPropertiesFile() {
        String explicit = System.getProperty("app.config");
        if (explicit == null || explicit.isBlank()) {
            String env = System.getenv("APP_CONFIG");
            if (env != null && !env.isBlank()) explicit = env;
        }

        File file = explicit != null && !explicit.isBlank()
            ? new File(explicit)
            : new File("app.properties");

        if (!file.exists() || !file.isFile()) return;

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (Exception ignored) {
            return;
        }

        for (String name : props.stringPropertyNames()) {
            String val = props.getProperty(name);
            if (val != null && !val.isBlank()) {
                setIfAbsent(name.trim(), val.trim());
            }
        }
    }

    private static void setIfAbsent(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) return;
        String existing = System.getProperty(key);
        if (existing == null || existing.isBlank()) {
            System.setProperty(key, value);
        }
    }
}

