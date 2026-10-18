package com.tickcart.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickcart.common.api.R;
import com.tickcart.common.api.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security 配置：无状态 JWT 认证。
 *
 * <ul>
 *   <li>放行：认证接口、健康探针、Swagger 文档、公开商品接口</li>
 *   <li>受保护：其余接口需有效 access token</li>
 *   <li>后台接口 {@code /api/admin/**} 需 ROLE_ADMIN（配合 {@code @PreAuthorize}）</li>
 *   <li>401/403 统一输出 {@link R} JSON，而非默认 HTML 跳转</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 认证相关：注册/登录/发验证码/刷新令牌 放行
                        .requestMatchers("/api/auth/**").permitAll()
                        // 健康探针
                        .requestMatchers("/api/health").permitAll()
                        // 接口文档
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/doc.html").permitAll()
                        // 前台公开商品读接口（浏览无需登录）
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/products/**", "/api/categories/**").permitAll()
                        // 前台公开搜索建议（热门搜索 / 相关搜索）
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/search/**").permitAll()
                        // 秒杀活动列表/详情匿名可看；抢购接口（B2）需要登录，不在此列
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/seckill/**").permitAll()
                        // 上传图片的静态访问：<img src> 不会携带 Authorization 头，必须放行
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/files/**").permitAll()
                        // 仓库内演示图片（imgs/）的静态访问，同样供 <img src> 直接使用
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/img/**").permitAll()
                        // 支付渠道异步通知：由渠道服务端回调，无用户令牌（真实场景靠验签鉴权）
                        .requestMatchers("/api/payments/callback/**").permitAll()
                        // 后台管理接口需管理员
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其余一律需登录
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, resp, e) ->
                                writeJson(resp, HttpStatus.UNAUTHORIZED, R.fail(ResultCode.UNAUTHORIZED)))
                        .accessDeniedHandler((req, resp, e) ->
                                writeJson(resp, HttpStatus.FORBIDDEN, R.fail(ResultCode.FORBIDDEN))))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 开发期 CORS：允许前端 dev server 直连（生产由 Nginx 同源代理）。 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, R<?> body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
