package com.martflow.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables CORS on the API so the frontend can also be run from a different origin/port (the
 * default same-origin setup needs none of this, but it is a safe fallback for the demo).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                // demo scope: any origin may call, but every /api/** route (except login)
                // still requires a valid bearer token — tighten this before any real deployment
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
