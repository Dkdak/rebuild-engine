package com.mteam.rebuildengine.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${cors.allowed-origin}")
    private String allowedOrigin;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    // local 프로파일에서만 빈이 생성된다 — 그 외 프로파일에서는 항상 빈 리스트(LocalDevAuthFilter 참고).
    private final List<LocalDevAuthFilter> localDevAuthFilters;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Security에게 우리가 정의한 CORS 설정을 따르라고 명시합니다.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable()) // 테스트를 위해 CSRF 잠시 비활성화
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/me").authenticated()
                        // FEATURE_11_FAVORITES.md §3.2 — 전 API 로그인 필수(비로그인 401). 경로 전체가
                        // 로그인 전용이라 /auth/me처럼 메서드별로 나누지 않고 하위 전체를 한 번에 막는다.
                        .requestMatchers("/api/v1/favorites/**").authenticated()
                        // FEATURE_19_PERSONALIZED_ANALYSIS.md §3.2 — F-19 실측 입력도 전 API 로그인 필수.
                        .requestMatchers("/api/v1/analysis/measurements/**").authenticated()
                        // FEATURE_19_PERSONALIZED_ANALYSIS.md §1.1 — 리포트탭은 CASE1/CASE2 어느 쪽이든
                        // 로그인 계정 기준이라 게스트 열람 대상이 아니다(지도 탭만 비로그인 유지).
                        .requestMatchers("/api/v1/properties/*/report").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"message\":\"인증이 필요합니다.\"}");
                }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        localDevAuthFilters.forEach(filter -> http.addFilterAfter(filter, JwtAuthenticationFilter.class));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2. 여기에 기존 CorsConfig 설정을 메서드로 이관합니다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin(allowedOrigin); // 주입받은 프론트 주소
        config.addAllowedHeader("*");
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}