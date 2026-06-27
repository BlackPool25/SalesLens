package com.shreyas.saleslens.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Test configuration that enables method-level security annotations
 * (e.g. {@code @PreAuthorize}) for {@code @WebMvcTest} slices. The
 * production {@link com.shreyas.saleslens.config.SecurityBeans} registers
 * {@code @EnableMethodSecurity} on a {@code @Configuration} class, but
 * sliced test contexts do not scan production configs automatically.
 * This test config satisfies the dependency so that {@code @PreAuthorize}
 * annotations on controller methods are enforced during tests.
 * <p>
 * Also registers a {@link TestSecurityExceptionHandler} that converts
 * {@link AccessDeniedException} and
 * {@link AuthenticationCredentialsNotFoundException} into HTTP 403
 * responses. In a full application context the
 * {@code ExceptionTranslationFilter} handles this, but in
 * {@code @WebMvcTest} slices the filter chain does not wrap AOP-level
 * method security exceptions.
 */
@TestConfiguration
@EnableMethodSecurity
public class TestSecurityConfig {

    @Bean
    TestSecurityExceptionHandler testSecurityExceptionHandler() {
        return new TestSecurityExceptionHandler();
    }

    @Bean
    WebMvcConfigurer authenticationPrincipalConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new AuthenticationPrincipalArgumentResolver());
            }
        };
    }

    @RestControllerAdvice
    static class TestSecurityExceptionHandler {

        @ExceptionHandler(AccessDeniedException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        public void handleAccessDenied() {}

        @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        public void handleAuthNotFound() {}
    }
}
