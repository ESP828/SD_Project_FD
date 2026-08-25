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
                // 게임 탭이 같은 출처(our own site)의 미니게임 페이지를 iframe으로 띄워야 해서
                // 기본값인 DENY 대신 SAMEORIGIN으로 완화한다. 다른 사이트가 우리 페이지를
                // iframe에 넣는 클릭재킹은 여전히 막힌다(허용은 "우리 도메인끼리"만).
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/", "/index.html",
                                "/css/**", "/js/**", "/images/**", "/icons/**", "/pages/**",
                                "/uploads/preset-images/**",
                                "/error", "/error/**",
                                "/favicon.ico", "/favicon.svg",
                                // 정적 페이지의 깨끗한 URL(포워드 전용, /pages/**와 동일한 신뢰 경계).
                                // 실제 접근 제어는 /api/** 쪽(예: /api/admin/**)에서 이루어진다.
                                "/admin/**", "/auth/**", "/board/**", "/business/**", "/game", "/map",
                                "/mypage/**", "/presset/**", "/recommendation", "/restaurant/**", "/search"
                        ).permitAll()
                        .requestMatchers("/api/auth/**", "/api/public/**", "/api/hello").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recommendations/query").permitAll()
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
