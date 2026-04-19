package com.example.fieldservice.common.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record SecurityProperties(
        Api api,
        Cors cors,
        Security security
) {

    public record Api(String basePath) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Security(Jwt jwt, RefreshToken refreshToken) {
    }

    public record Jwt(String issuer, String secret, Duration accessTokenTtl) {
    }

    public record RefreshToken(Duration ttl, String cookieName, boolean secureCookie) {
    }
}
