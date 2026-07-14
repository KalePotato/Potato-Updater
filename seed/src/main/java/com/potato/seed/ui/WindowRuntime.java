package com.potato.seed.ui;

import java.util.Locale;

final class WindowRuntime {
    private final String osName;
    private final String sessionType;
    private final boolean linux;
    private final boolean wayland;
    private final boolean x11;

    private WindowRuntime(String osName, String sessionType, boolean linux, boolean wayland, boolean x11) {
        this.osName = osName;
        this.sessionType = sessionType;
        this.linux = linux;
        this.wayland = wayland;
        this.x11 = x11;
    }

    static WindowRuntime detect() {
        String osName = normalize(System.getProperty("os.name", ""));
        String sessionType = normalize(System.getenv("XDG_SESSION_TYPE"));
        boolean linux = osName.contains("linux");
        boolean wayland = linux && (sessionType.contains("wayland") || hasEnv("WAYLAND_DISPLAY"));
        boolean x11 = linux && !wayland && (sessionType.contains("x11") || hasEnv("DISPLAY"));
        return new WindowRuntime(osName, sessionType, linux, wayland, x11);
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

    String describe() {
        String session = wayland ? "wayland" : (x11 ? "x11" : (sessionType.isBlank() ? "unknown" : sessionType));
        return osName + " / " + session + " / stableLinuxWindowMode=" + linux;
    }

    private static boolean hasEnv(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
