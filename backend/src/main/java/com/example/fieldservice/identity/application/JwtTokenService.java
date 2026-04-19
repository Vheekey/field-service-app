package com.example.fieldservice.identity.application;

import com.example.fieldservice.common.security.SecurityProperties;
import com.example.fieldservice.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    IssuedAccessToken issueAccessToken(UserAccount user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.security().jwt().accessTokenTtl());

        var claims = JwtClaimsSet.builder()
                .issuer(properties.security().jwt().issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.id().toString())
                .claim("email", user.email())
                .claim("name", user.name())
                .claim("roles", user.roles().stream().map(Enum::name).toList())
                .build();

        var headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }

    record IssuedAccessToken(String token, Instant expiresAt) {
    }
}
