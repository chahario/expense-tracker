package com.fenmo.expense.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS configuration.
 *
 * ALLOWED_ORIGIN env var controls which frontend origin can call the API:
 *   - Dev: defaults to "*" (any origin)
 *   - Prod: set ALLOWED_ORIGIN=https://your-app.vercel.app in Railway env vars
 *
 * allowedHeaders("*") includes our custom Idempotency-Key header automatically.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origin:*}")
    private String allowedOrigin;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        if ("*".equals(allowedOrigin)) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOrigins(List.of(allowedOrigin));
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Request-Id"));  // Let clients see the correlation ID
        config.setAllowCredentials(!"*".equals(allowedOrigin));
        config.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
