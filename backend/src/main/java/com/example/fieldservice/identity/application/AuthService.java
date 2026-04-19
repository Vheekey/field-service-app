package com.example.fieldservice.identity.application;

import com.example.fieldservice.common.security.SecurityProperties;
import com.example.fieldservice.identity.api.IdentityDto.AuthTokenResponse;
import com.example.fieldservice.identity.api.IdentityDto.LoginRequest;
import com.example.fieldservice.identity.api.IdentityDto.MeResponse;
import com.example.fieldservice.identity.domain.RefreshToken;
import com.example.fieldservice.identity.domain.Role;
import com.example.fieldservice.identity.domain.UserAccount;
import com.example.fieldservice.identity.persistence.RefreshTokenRepository;
import com.example.fieldservice.identity.persistence.UserAccountRepository;
import com.example.fieldservice.workers.api.WorkerProfileDto;
import com.example.fieldservice.workers.persistence.WorkerProfileRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final TokenHashingService tokenHashingService;
    private final WorkerProfileRepository workerProfiles;
    private final SecurityProperties properties;
    private final Clock clock = Clock.systemUTC();

    public AuthService(
            AuthenticationManager authenticationManager,
            UserAccountRepository users,
            RefreshTokenRepository refreshTokens,
            JwtTokenService jwtTokenService,
            RefreshTokenGenerator refreshTokenGenerator,
            TokenHashingService tokenHashingService,
            WorkerProfileRepository workerProfiles,
            SecurityProperties properties
    ) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.tokenHashingService = tokenHashingService;
        this.workerProfiles = workerProfiles;
        this.properties = properties;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password())
        );

        UserAccount user = users.findByEmail(authentication.getName())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        return issueSession(user);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Missing refresh token");
        }

        Instant now = clock.instant();
        RefreshToken existingToken = refreshTokens.findByTokenHash(tokenHashingService.sha256(rawRefreshToken))
                .filter(token -> token.isUsableAt(now))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        existingToken.revoke(now);

        UserAccount user = existingToken.user();
        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        return issueSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokens.findByTokenHash(tokenHashingService.sha256(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public MeResponse me(Authentication authentication) {
        UserAccount user = requireUser(authentication);
        var roles = user.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());

        return new MeResponse(
                user.id(),
                user.email(),
                user.name(),
                roles,
                workerProfileFor(user)
        );
    }

    @Transactional(readOnly = true)
    public UserAccount requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("Authentication required");
        }

        return users.findWithRolesByEmail(authentication.getName())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new BadCredentialsException("Authentication required"));
    }

    private WorkerProfileDto workerProfileFor(UserAccount user) {
        if (!user.roles().contains(Role.FIELD_WORKER)) {
            return null;
        }

        return workerProfiles.findByUserId(user.id())
                .map(profile -> new WorkerProfileDto(
                        profile.id(),
                        profile.user().id(),
                        profile.active(),
                        profile.homeBaseName(),
                        null,
                        profile.lastSeenAt()
                ))
                .orElseGet(() -> new WorkerProfileDto(
                        user.id(),
                        user.id(),
                        true,
                        null,
                        null,
                        null
                ));
    }

    private LoginResult issueSession(UserAccount user) {
        var accessToken = jwtTokenService.issueAccessToken(user);
        String rawRefreshToken = refreshTokenGenerator.generate();
        Instant now = clock.instant();

        refreshTokens.save(RefreshToken.issue(
                user,
                tokenHashingService.sha256(rawRefreshToken),
                now.plus(properties.security().refreshToken().ttl()),
                now
        ));

        return new LoginResult(
                new AuthTokenResponse(accessToken.token(), accessToken.expiresAt()),
                rawRefreshToken,
                now.plus(properties.security().refreshToken().ttl())
        );
    }

    public record LoginResult(AuthTokenResponse accessToken, String refreshToken, Instant refreshTokenExpiresAt) {
    }
}
