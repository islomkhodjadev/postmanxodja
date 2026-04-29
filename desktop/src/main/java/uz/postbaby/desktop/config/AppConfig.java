package uz.postbaby.desktop.config;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppConfig {

    private static final String DEFAULT_API_BASE = "https://postbaby.uz/api";
    private static final String LOCAL_API_BASE = "http://localhost:8080/api";

    private AppConfig() {
    }

    public static String apiBaseUrl() {
        String override = System.getProperty("postbaby.api.base");
        if (override != null && !override.isBlank()) {
            return override;
        }
        String env = System.getenv("POSTBABY_API_BASE");
        if (env != null && !env.isBlank()) {
            return env;
        }
        if ("local".equalsIgnoreCase(System.getProperty("postbaby.profile"))) {
            return LOCAL_API_BASE;
        }
        return DEFAULT_API_BASE;
    }

    public static Path dataDir() {
        String override = System.getProperty("postbaby.data.dir");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "PostBaby");
        }
        if (os.contains("windows")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, "PostBaby");
            }
            return Paths.get(home, "AppData", "Roaming", "PostBaby");
        }
        return Paths.get(home, ".local", "share", "postbaby");
    }
}
