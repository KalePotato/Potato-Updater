package com.potato.seed.agent;

import com.potato.seed.config.EndpointDefaults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointDefaultsTest {

    @Test
    void buildResolvesSeedEndpoint() {
        String endpoint = EndpointDefaults.seedConfigUrl();

        assertTrue(endpoint.startsWith("http://") || endpoint.startsWith("https://"));
        assertTrue(endpoint.endsWith("/seed.json"));
        assertFalse(endpoint.contains("${"));
    }
}
