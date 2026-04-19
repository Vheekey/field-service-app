package com.example.fieldservice.identity.api;

import com.example.fieldservice.identity.api.IdentityDto.AuthTokenResponse;
import com.example.fieldservice.identity.api.IdentityDto.LoginRequest;
import com.example.fieldservice.identity.api.IdentityDto.MeResponse;
import com.example.fieldservice.common.security.SecurityProperties;
import com.example.fieldservice.identity.application.AuthService;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}")
public class AuthController {

    private final AuthService authService;
    private final SecurityProperties properties;

    public AuthController(AuthService authService, SecurityProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/auth/login")
    ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), properties.security().refreshToken().ttl()).toString())
                .body(result.accessToken());
    }

    @PostMapping("/auth/refresh")
    ResponseEntity<AuthTokenResponse> refresh(
            @CookieValue(name = "${app.security.refresh-token.cookie-name}", required = false) String refreshToken
    ) {
        AuthService.LoginResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), properties.security().refreshToken().ttl()).toString())
                .body(result.accessToken());
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = "${app.security.refresh-token.cookie-name}", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    ResponseEntity<MeResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.me(authService.requireUser(authentication)));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.security().refreshToken().cookieName(), value)
                .httpOnly(true)
                .secure(properties.security().refreshToken().secureCookie())
                .sameSite("Strict")
                .path(authCookiePath())
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(properties.security().refreshToken().cookieName(), "")
                .httpOnly(true)
                .secure(properties.security().refreshToken().secureCookie())
                .sameSite("Strict")
                .path(authCookiePath())
                .maxAge(0)
                .build();
    }

    private String authCookiePath() {
        return properties.api().basePath() + "/auth";
    }
}
