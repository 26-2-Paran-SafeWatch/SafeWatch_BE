package com.safewatch.server.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 요청마다 "Authorization: Bearer <토큰>" 을 읽어 누구인지 확인한다.
 *
 * 토큰이 맞으면 "이 요청은 사용자 X 의 것"이라고 기록해 두고 넘긴다.
 * 없거나 가짜·만료면 아무것도 기록하지 않고 넘긴다. 막는 일은 SecurityConfig 의 규칙이 한다.
 * 로그인이 필요한 주소면 거기서 401, 장치용처럼 열린 주소면 그대로 통과한다.
 *
 * 일부러 @Component 를 붙이지 않는다. 붙이면 스프링이 이 필터를 일반 필터로도 한 번 더 등록해서
 * 요청마다 두 번 돈다. SecurityConfig 에서 직접 만들어 보안 필터 줄에만 넣는다.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                UUID userId = jwtProvider.parseUserId(header.substring(7));
                // 컨트롤러에서 @AuthenticationPrincipal UUID userId 로 꺼내 쓴다.
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
            } catch (JwtException | IllegalArgumentException e) {
                // 가짜·만료·형식 오류 토큰. 기록하지 않고 넘긴다 → 보호된 주소면 401.
            }
        }
        chain.doFilter(request, response);
    }
}
