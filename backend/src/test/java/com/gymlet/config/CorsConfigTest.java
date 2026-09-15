package com.gymlet.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void allowsCurrentVercelFrontendOrigin() {
        CorsConfigurationSource source = new CorsConfig().corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/status");
        request.addHeader("Origin", "https://gymlet-6qdu.vercel.app");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).contains("https://gymlet-6qdu.vercel.app");
    }
}
