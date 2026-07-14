package com.potato.updater.gui;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class WindowRuntime {
    private final String osName;
    private final String sessionType;
    private final boolean linux;
    private final boolean wayland;
    private final boolean x11;
    private final boolean highContrast;

    private WindowRuntime(String osName,
                          String sessionType,
                          boolean linux,
                          boolean wayland,
                          boolean x11,
                          boolean highContrast) {
        this.osName = osName;
        this.sessionType = sessionType;
        this.linux = linux;
        this.wayland = wayland;
        this.x11 = x11;
        this.highContrast = highContrast;
    }

    static WindowRuntime detect() {
        String osName = normalize(System.getProperty("os.name", ""));
        String sessionType = normalize(System.getenv("XDG_SESSION_TYPE"));
        boolean linux = osName.contains("linux");
        boolean wayland = linux && (sessionType.contains("wayland") || hasEnv("WAYLAND_DISPLAY"));
        boolean x11 = linux && !wayland && (sessionType.contains("x11") || hasEnv("DISPLAY"));
        boolean highContrast = detectHighContrast();
        return new WindowRuntime(osName, sessionType, linux, wayland, x11, highContrast);
    }

    boolean useStableLinuxWindowMode() {
        return linux;
    }

    boolean useUndecoratedWindow() {
        return !linux;
    }

    boolean useTransparentWindowBackground() {
        return !linux;
    }

    boolean useAlwaysOnTop() {
        return !linux;
    }

    boolean useWindowOpacityEffects() {
        return !linux;
    }

    boolean useWindowBoundsAnimations() {
        return !linux;
    }

    boolean useCustomWindowShadow() {
        return useTransparentWindowBackground() && !highContrast;
    }

    boolean resolveDarkTheme(String configuredThemeMode) {
        String override = normalize(System.getProperty("potato.updater.theme", ""));
        if ("dark".equals(override) || isRemovedBlurredThemeMode(override)) {
            return true;
        }
        if ("light".equals(override) || "bright".equals(override)) {
            return false;
        }

        String mode = normalize(configuredThemeMode);
        if ("dark".equals(mode) || isRemovedBlurredThemeMode(mode)) {
            return true;
        }
        if ("light".equals(mode) || "bright".equals(mode)) {
            return false;
        }
        return detectSystemDarkTheme();
    }

    int animationTimerDelayMs() {
        return linux ? 16 : 5;
    }

    String describe() {
        String session = wayland ? "wayland" : (x11 ? "x11" : (sessionType.isBlank() ? "unknown" : sessionType));
        return osName + " / " + session
                + " / stableLinuxWindowMode=" + linux
                + " / highContrast=" + highContrast;
    }

    private static boolean hasEnv(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isRemovedBlurredThemeMode(String value) {
        return "dark_blurred".equals(value)
                || "dark-blurred".equals(value)
                || "darkblurred".equals(value)
                || "blurred".equals(value);
    }

    private static boolean detectHighContrast() {
        try {
            Object value = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on");
            return Boolean.TRUE.equals(value);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean detectSystemDarkTheme() {
        if (osName.contains("win")) {
            return detectWindowsDarkTheme();
        }
        if (osName.contains("mac")) {
            return detectMacDarkTheme();
        }
        if (linux) {
            return detectLinuxDarkTheme();
        }
        return false;
    }

    private static boolean detectWindowsDarkTheme() {
        try {
            Process process = new ProcessBuilder(
                    "reg",
                    "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v",
                    "AppsUseLightTheme")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return output.toLowerCase(Locale.ROOT).contains("appsuselighttheme")
                    && output.toLowerCase(Locale.ROOT).contains("0x0");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean detectMacDarkTheme() {
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return output.trim().equalsIgnoreCase("Dark");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean detectLinuxDarkTheme() {
        String gtkTheme = normalize(System.getenv("GTK_THEME"));
        if (gtkTheme.contains("dark")) {
            return true;
        }
        String colorScheme = readProcessOutput(1500, "gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
        if (colorScheme.contains("prefer-dark")) {
            return true;
        }
        if (colorScheme.contains("prefer-light") || colorScheme.contains("default")) {
            return false;
        }
        String gtkThemeSetting = readProcessOutput(1500, "gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
        return gtkThemeSetting.contains("dark");
    }

    private static String readProcessOutput(long timeoutMs, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return normalize(output);
        } catch (Exception ignored) {
            return "";
        }
    }
}
