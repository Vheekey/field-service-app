package com.example.fieldservice.common.security;

import com.example.fieldservice.identity.domain.Role;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class JwtAuthenticationConverterConfig implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<Role> roles = jwt.getClaimAsStringList("roles").stream()
                .map(Role::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        var principal = new FieldServicePrincipal(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                "",
                roles,
                true
        );

        return new JwtAuthenticationToken(jwt, principal.getAuthorities(), principal.email());
    }
}
