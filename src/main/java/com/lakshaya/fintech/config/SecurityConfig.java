package com.lakshaya.fintech.config;

import com.lakshaya.fintech.security.auth.AuthEntryPoint;
import com.lakshaya.fintech.security.auth.CustomAccessDeniedHandler;
import com.lakshaya.fintech.security.jwt.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * - Stateless session - no HttpSession, no cookies
 * - CSRF disabled - REST API with JWT
 * - @EnableMethodSecurity enables @PreAuthorize on controllers
 * - JwtFilter fires BEFORE UsernamePasswordAuthenticationFilter
 * - BCryptPasswordEncoder bean injected into AuthService
 *
 * Public routes:
 * /api/v1/auth/**   - register + login
 * /api/v1/health    - uptime/cron keep-alive ping (no auth required)
 * /h2-console/**    - dev profile only
 * /swagger-ui/**    - API docs
 * /v3/api-docs/**   - OpenAPI spec
 *
 * UserDetailsService bean suppresses Spring Boot's InMemoryUserDetailsManager
 * auto-configuration (which generates a random password and overrides this config).
 * This app uses stateless JWT — no UserDetailsService is needed at runtime.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final AuthEntryPoint authEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)      // 401 — not authenticated
                        .accessDeniedHandler(accessDeniedHandler))     // 403 — authenticated but wrong role

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()  // cron job keep-alive
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())

                // H2 console uses iframes - allow same-origin framing in dev
                .headers(headers ->
                        headers.frameOptions(frame -> frame.sameOrigin()))

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Suppresses Spring Boot's UserDetailsServiceAutoConfiguration.
     * Without this bean, Spring Boot auto-configures InMemoryUserDetailsManager,
     * generates a random password on startup, and overrides this SecurityConfig —
     * causing all endpoints including public /auth/** routes to return 401.
     * This app uses stateless JWT authentication — no UserDetailsService is needed.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("This application uses JWT authentication only.");
        };
    }
}