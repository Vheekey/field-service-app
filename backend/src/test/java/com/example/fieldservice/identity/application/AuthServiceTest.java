package com.example.fieldservice.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.fieldservice.common.security.SecurityProperties;
import com.example.fieldservice.identity.api.IdentityDto.LoginRequest;
import com.example.fieldservice.identity.domain.RefreshToken;
import com.example.fieldservice.identity.domain.Role;
import com.example.fieldservice.identity.domain.UserAccount;
import com.example.fieldservice.identity.domain.UserStatus;
import com.example.fieldservice.identity.persistence.RefreshTokenRepository;
import com.example.fieldservice.identity.persistence.UserAccountRepository;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserAccountRepository users;

    @Mock
    private RefreshTokenRepository refreshTokens;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private TokenHashingService tokenHashingService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                users,
                refreshTokens,
                jwtTokenService,
                refreshTokenGenerator,
                tokenHashingService,
                securityProperties()
        );
    }

    @Test
    void loginAuthenticatesActiveUserAndStoresOnlyHashedRefreshToken() {
        UserAccount user = user("admin@example.com", UserStatus.ACTIVE, Role.ADMIN);
        Instant accessExpiresAt = Instant.parse("2026-04-19T12:15:00Z");

        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated("admin@example.com", "ignored", List.of()));
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenService.issueAccessToken(user)).thenReturn(new JwtTokenService.IssuedAccessToken("access-token", accessExpiresAt));
        when(refreshTokenGenerator.generate()).thenReturn("raw-refresh-token");
        when(tokenHashingService.sha256("raw-refresh-token")).thenReturn("hashed-refresh-token");

        AuthService.LoginResult result = authService.login(new LoginRequest("admin@example.com", "Password123!"));

        assertThat(result.accessToken().accessToken()).isEqualTo("access-token");
        assertThat(result.accessToken().expiresAt()).isEqualTo(accessExpiresAt);
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");

        ArgumentCaptor<org.springframework.security.core.Authentication> authentication =
                ArgumentCaptor.forClass(org.springframework.security.core.Authentication.class);
        verify(authenticationManager).authenticate(authentication.capture());
        assertThat(authentication.getValue().getName()).isEqualTo("admin@example.com");
        assertThat(authentication.getValue().getCredentials()).isEqualTo("Password123!");

        ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(savedToken.capture());
        assertThat(savedToken.getValue().tokenHash()).isEqualTo("hashed-refresh-token");
        assertThat(savedToken.getValue().tokenHash()).doesNotContain("raw-refresh-token");
        assertThat(savedToken.getValue().user()).isSameAs(user);
    }

    @Test
    void loginRejectsInactiveUserAfterAuthentication() {
        UserAccount user = user("disabled@example.com", UserStatus.DISABLED, Role.DISPATCHER);

        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated("disabled@example.com", "ignored", List.of()));
        when(users.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("disabled@example.com", "Password123!")))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokens, never()).save(any());
        verifyNoInteractions(jwtTokenService, refreshTokenGenerator, tokenHashingService);
    }

    @Test
    void refreshRotatesUsableTokenAndRevokesPreviousToken() {
        UserAccount user = user("worker@example.com", UserStatus.ACTIVE, Role.FIELD_WORKER);
        RefreshToken existingToken = RefreshToken.issue(
                user,
                "old-token-hash",
                Instant.now().plus(Duration.ofDays(1)),
                Instant.now().minus(Duration.ofDays(1))
        );

        when(tokenHashingService.sha256("old-refresh-token")).thenReturn("old-token-hash");
        when(refreshTokens.findByTokenHash("old-token-hash")).thenReturn(Optional.of(existingToken));
        when(jwtTokenService.issueAccessToken(user))
                .thenReturn(new JwtTokenService.IssuedAccessToken("new-access-token", Instant.parse("2026-04-19T12:15:00Z")));
        when(refreshTokenGenerator.generate()).thenReturn("new-refresh-token");
        when(tokenHashingService.sha256("new-refresh-token")).thenReturn("new-token-hash");

        AuthService.LoginResult result = authService.refresh("old-refresh-token");

        assertThat(existingToken.revokedAt()).isNotNull();
        assertThat(result.accessToken().accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");

        ArgumentCaptor<RefreshToken> savedToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(savedToken.capture());
        assertThat(savedToken.getValue().tokenHash()).isEqualTo("new-token-hash");
        assertThat(savedToken.getValue().user()).isSameAs(user);
    }

    @Test
    void refreshRejectsMissingOrExpiredToken() {
        assertThatThrownBy(() -> authService.refresh(" "))
                .isInstanceOf(BadCredentialsException.class);

        UserAccount user = user("worker@example.com", UserStatus.ACTIVE, Role.FIELD_WORKER);
        RefreshToken expiredToken = RefreshToken.issue(
                user,
                "expired-token-hash",
                Instant.now().minus(Duration.ofMinutes(1)),
                Instant.now().minus(Duration.ofDays(1))
        );

        when(tokenHashingService.sha256("expired-refresh-token")).thenReturn("expired-token-hash");
        when(refreshTokens.findByTokenHash("expired-token-hash")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh("expired-refresh-token"))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(expiredToken.revokedAt()).isNull();
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void logoutRevokesExistingRefreshTokenAndIgnoresBlankToken() {
        authService.logout("");
        verifyNoInteractions(tokenHashingService, refreshTokens);

        UserAccount user = user("worker@example.com", UserStatus.ACTIVE, Role.FIELD_WORKER);
        RefreshToken existingToken = RefreshToken.issue(
                user,
                "token-hash",
                Instant.now().plus(Duration.ofDays(1)),
                Instant.now().minus(Duration.ofDays(1))
        );

        when(tokenHashingService.sha256("raw-token")).thenReturn("token-hash");
        when(refreshTokens.findByTokenHash("token-hash")).thenReturn(Optional.of(existingToken));

        authService.logout("raw-token");

        assertThat(existingToken.revokedAt()).isNotNull();
    }

    @Test
    void requireUserReturnsOnlyAuthenticatedActiveUser() {
        UserAccount activeUser = user("admin@example.com", UserStatus.ACTIVE, Role.ADMIN);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(activeUser));

        var authentication = UsernamePasswordAuthenticationToken.authenticated("admin@example.com", "ignored", List.of());

        assertThat(authService.requireUser(authentication)).isSameAs(activeUser);

        assertThatThrownBy(() -> authService.requireUser(null))
                .isInstanceOf(BadCredentialsException.class);
    }

    private static SecurityProperties securityProperties() {
        return new SecurityProperties(
                new SecurityProperties.Api("/api/v1"),
                new SecurityProperties.Cors(List.of("http://localhost:5173")),
                new SecurityProperties.Security(
                        new SecurityProperties.Jwt("field-service", "dev-only-change-me-dev-only-change-me", Duration.ofMinutes(15)),
                        new SecurityProperties.RefreshToken(Duration.ofDays(30), "refresh_token", false)
                )
        );
    }

    private static UserAccount user(String email, UserStatus status, Role... roles) {
        try {
            Constructor<UserAccount> constructor = UserAccount.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            UserAccount user = constructor.newInstance();
            ReflectionTestUtils.setField(user, "email", email);
            ReflectionTestUtils.setField(user, "passwordHash", "$2a$12$hash");
            ReflectionTestUtils.setField(user, "name", "Test User");
            ReflectionTestUtils.setField(user, "status", status);
            ReflectionTestUtils.setField(user, "roles", Set.of(roles));
            return user;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not create test user", ex);
        }
    }
}
