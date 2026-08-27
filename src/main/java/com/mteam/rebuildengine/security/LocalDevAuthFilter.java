package com.mteam.rebuildengine.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// 개발 중 임시 조치(product 2026-08-27 요청, 사용자 승인) — 프론트가 로그인 없이 로그인 필수 탭
// (분석·리포트)을 직접 확인하도록 local 프로파일에서만 고정 개발 계정으로 자동 인증한다. @Profile
// ("local")이라 이 빈 자체가 docker·prod 프로파일에서는 아예 생성되지 않는다 — SecurityConfig에
// 등록하는 실수만 안 하면 운영에 나갈 방법이 구조적으로 없다. JwtAuthenticationFilter 뒤에 등록해
// 이미 유효한 토큰으로 인증됐으면 건드리지 않는다(로그인 화면 자체 확인과 공존).
@Component
@Profile("local")
public class LocalDevAuthFilter extends OncePerRequestFilter {

    // 코드에 하드코딩 금지 지시(설정값 분리)는 이번 요청에서 명시적으로 뺐다 — 개발 중 임시 조치.
    private static final String DEV_EMAIL = "dev@local.test";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                      @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            var authentication = UsernamePasswordAuthenticationToken.authenticated(DEV_EMAIL, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
