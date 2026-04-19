package com.example.fieldservice.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthenticationConverterConfigTest {

    @Test
    void convertMapsJwtRolesToRbacAuthorities() {
        UUID userId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("email", "admin@example.com")
                .claim("name", "Admin User")
                .claim("roles", List.of("ADMIN", "FIELD_WORKER"))
                .issuedAt(Instant.parse("2026-04-19T12:00:00Z"))
                .expiresAt(Instant.parse("2026-04-19T12:15:00Z"))
                .build();

        var authentication = new JwtAuthenticationConverterConfig().convert(jwt);

        assertThat(authentication.getName()).isEqualTo("admin@example.com");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_FIELD_WORKER");
    }
}
