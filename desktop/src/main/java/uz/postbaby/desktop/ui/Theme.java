package uz.postbaby.desktop.ui;

import javafx.scene.Scene;

import java.net.URL;

public final class Theme {

    private static volatile String active = "dark";

    private Theme() {
    }

    public static void set(String theme) {
        active = "light".equals(theme) ? "light" : "dark";
    }

    public static String current() {
        return active;
    }

    public static boolean isLight() {
        return "light".equals(active);
    }

    public static void apply(Scene scene) {
        if (scene == null) return;
        URL base = Theme.class.getResource("/css/app.css");
        if (base != null) scene.getStylesheets().add(base.toExternalForm());
        if (isLight()) {
            URL light = Theme.class.getResource("/css/theme-light.css");
            if (light != null) scene.getStylesheets().add(light.toExternalForm());
        }
    }
}
