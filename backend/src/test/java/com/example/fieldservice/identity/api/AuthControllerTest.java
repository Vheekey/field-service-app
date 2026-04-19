package com.example.fieldservice.identity.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fieldservice.common.errors.ApiExceptionHandler;
import com.example.fieldservice.common.security.SecurityProperties;
import com.example.fieldservice.identity.api.IdentityDto.AuthTokenResponse;
import com.example.fieldservice.identity.application.AuthService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = org.mockito.Mockito.mock(AuthService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, securityProperties()))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .addPlaceholderValue("app.api.base-path", "/custom/api")
                .addPlaceholderValue("app.security.refresh-token.cookie-name", "refresh_token")
                .build();
    }

    @Test
    void loginRejectsInvalidRequestBodyBeforeCallingService() throws Exception {
        mockMvc.perform(post("/custom/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.fieldErrors[*].field", hasItems("email", "password")));

        verifyNoInteractions(authService);
    }

    @Test
    void loginSetsRefreshCookieUsingConfiguredApiBasePath() throws Exception {
        when(authService.login(any())).thenReturn(new AuthService.LoginResult(
                new AuthTokenResponse("access-token", Instant.parse("2026-04-19T12:15:00Z")),
                "refresh-token",
                Instant.parse("2026-05-19T12:00:00Z")
        ));

        mockMvc.perform(post("/custom/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@example.com",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/custom/api/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
    }

    private static SecurityProperties securityProperties() {
        return new SecurityProperties(
                new SecurityProperties.Api("/custom/api"),
                new SecurityProperties.Cors(List.of("http://localhost:5173")),
                new SecurityProperties.Security(
                        new SecurityProperties.Jwt("field-service", "dev-only-change-me-dev-only-change-me", Duration.ofMinutes(15)),
                        new SecurityProperties.RefreshToken(Duration.ofDays(30), "refresh_token", false)
                )
        );
    }
}
