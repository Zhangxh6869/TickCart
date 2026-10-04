package com.tickcart.common.security;

import com.tickcart.common.api.ResultCode;
import com.tickcart.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>从 {@code Authorization: Bearer <token>} 解析 access token，验签通过后写入
 * Spring Security 上下文与 {@link UserContext}。校验失败**不直接抛异常**，
 * 而是放行到后续过滤链，由 {@code ExceptionTranslationFilter} 在访问受保护资源时
 * 统一返回 401（这样公开接口携带坏 token 也能正常访问）。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = tokenProvider.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                Integer role = claims.get("role", Integer.class);
                String username = claims.get("username", String.class);

                String authority = (role != null && role == 1) ? "ROLE_ADMIN" : "ROLE_USER";
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId, null, List.of(new SimpleGrantedAuthority(authority)));
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                UserContext.set(claims);
            } catch (JwtException | IllegalArgumentException ex) {
                // 令牌无效：清理上下文，让其按未登录处理
                SecurityContextHolder.clearContext();
                UserContext.clear();
                log.debug("JWT 校验失败: {}", ex.getMessage());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程复用，务必清理，避免污染下一个请求
            UserContext.clear();
        }
    }
}
