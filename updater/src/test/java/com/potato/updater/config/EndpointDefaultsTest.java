package com.potato.updater.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointDefaultsTest {

    @Test
    void buildResolvesUpdaterEndpoints() {
        String seedEndpoint = EndpointDefaults.seedConfigUrl();
        String storageEndpoint = EndpointDefaults.storageConfigUrl();

        assertTrue(seedEndpoint.startsWith("http://") || seedEndpoint.startsWith("https://"));
        assertTrue(seedEndpoint.endsWith("/seed.json"));
        assertTrue(storageEndpoint.endsWith("/storage.json"));
        assertFalse(seedEndpoint.contains("${"));
        assertFalse(storageEndpoint.contains("${"));
    }
}
