package com.example.fieldservice.identity.application;

import com.example.fieldservice.common.security.FieldServicePrincipal;
import com.example.fieldservice.identity.persistence.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class IdentityUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;

    public IdentityUserDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(email)
                .map(user -> new FieldServicePrincipal(user.id(), user.email(), user.name(), user.passwordHash(), user.roles(), user.isActive()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
