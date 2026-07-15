package com.potato.seed.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class EndpointDefaults {
    private static final String SYNC_BASE_URL = loadSyncBaseUrl();

    private EndpointDefaults() {
    }

    public static String seedConfigUrl() {
        return SYNC_BASE_URL + "/seed.json";
    }

    private static String loadSyncBaseUrl() {
        Properties properties = new Properties();
        try (InputStream input = EndpointDefaults.class.getResourceAsStream("/potato-endpoints.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing build-generated potato-endpoints.properties");
            }
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read build-generated endpoint configuration", exception);
        }

        String configured = properties.getProperty("syncBaseUrl", "").trim();
        while (configured.endsWith("/")) {
            configured = configured.substring(0, configured.length() - 1);
        }
        if (configured.isEmpty()) {
            throw new IllegalStateException("Build-generated syncBaseUrl is empty");
        }
        return configured;
    }
}
