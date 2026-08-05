package com.example.backend.global.config;

import com.example.backend.global.security.jwt.JwtAuthenticationFilter;
import com.example.backend.global.security.handler.AccessDeniedHandler;
import com.example.backend.global.security.handler.AuthenticationEntryPointHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationEntryPointHandler authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPointHandler authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/", "/index.html",
                                "/css/**", "/js/**", "/images/**", "/icons/**", "/pages/**",
                                "/error", "/error/**",
                                "/favicon.ico", "/favicon.svg"
                        ).permitAll()
                        .requestMatchers("/api/auth/**", "/api/public/**", "/api/hello").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/presets", "/api/presets/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/board/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/business/applications").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/business/applications").authenticated()
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/business/**").hasAnyAuthority("ROLE_BUSINESS", "ROLE_ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
