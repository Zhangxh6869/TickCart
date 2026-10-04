package com.tickcart.auth.service;

import com.tickcart.auth.dto.LoginRequest;
import com.tickcart.auth.dto.RegisterRequest;
import com.tickcart.auth.dto.TokenResponse;
import com.tickcart.auth.entity.RefreshToken;
import com.tickcart.auth.entity.User;
import com.tickcart.auth.mapper.RefreshTokenMapper;
import com.tickcart.auth.mapper.SmsCodeMapper;
import com.tickcart.auth.mapper.UserMapper;
import com.tickcart.common.config.JwtProperties;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link AuthService} 单元测试（Mapper 用 Mockito 打桩，不依赖数据库）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("认证服务")
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RefreshTokenMapper refreshTokenMapper;
    @Mock
    private SmsCodeMapper smsCodeMapper;

    private AuthService authService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("tickcart-test-secret-key-must-be-at-least-32-bytes-long");
        props.setAccessTokenMinutes(30);
        props.setRefreshTokenDays(7);
        props.setIssuer("tickcart");
        JwtTokenProvider tokenProvider = new JwtTokenProvider(props);

        SmsService smsService = new SmsService();

        authService = new AuthService(userMapper, refreshTokenMapper, smsCodeMapper,
                smsService, passwordEncoder, tokenProvider, props);
    }

    private RegisterRequest registerReq(String username, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private User existingUser(String username, String rawPassword, int role) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(0);
        return user;
    }

    @Test
    @DisplayName("账密注册成功并签发令牌")
    void registerSuccess() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        TokenResponse token = authService.registerByPassword(registerReq("alice", "abc12345"));

        assertThat(token.getAccessToken()).isNotBlank();
        assertThat(token.getRefreshToken()).isNotBlank();
        assertThat(token.getTokenType()).isEqualTo("Bearer");
        assertThat(token.getExpiresIn()).isEqualTo(30 * 60);
    }

    @Test
    @DisplayName("用户名重复注册抛冲突异常")
    void registerDuplicateUsername() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("alice", "x", 0));

        assertThatThrownBy(() -> authService.registerByPassword(registerReq("alice", "abc12345")))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名已存在");
    }

    @Test
    @DisplayName("密码正确时登录成功")
    void loginSuccess() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("demo", "123456", 0));

        LoginRequest req = new LoginRequest();
        req.setUsername("demo");
        req.setPassword("123456");

        assertThat(authService.loginByPassword(req).getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("密码错误时登录失败且提示统一")
    void loginWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("demo", "123456", 0));

        LoginRequest req = new LoginRequest();
        req.setUsername("demo");
        req.setPassword("wrong");

        assertThatThrownBy(() -> authService.loginByPassword(req))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    @DisplayName("禁用账号无法登录")
    void loginDisabledUser() {
        User disabled = existingUser("demo", "123456", 0);
        disabled.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(disabled);

        LoginRequest req = new LoginRequest();
        req.setUsername("demo");
        req.setPassword("123456");

        assertThatThrownBy(() -> authService.loginByPassword(req))
                .isInstanceOf(BizException.class)
                .hasMessage("账号已被禁用");
    }

    @Test
    @DisplayName("有效刷新令牌可换取新令牌")
    void refreshRotatesToken() {
        User user = existingUser("demo", "123456", 0);
        RefreshToken stored = new RefreshToken();
        stored.setId(5L);
        stored.setUserId(1L);
        stored.setRevoked(0);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));

        when(refreshTokenMapper.selectOne(any())).thenReturn(stored);
        when(userMapper.selectById(1L)).thenReturn(user);

        TokenResponse token = authService.refresh("some-refresh-token");

        assertThat(token.getAccessToken()).isNotBlank();
        assertThat(token.getRefreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("已过期刷新令牌被拒绝")
    void refreshExpiredToken() {
        RefreshToken stored = new RefreshToken();
        stored.setId(5L);
        stored.setUserId(1L);
        stored.setRevoked(0);
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(refreshTokenMapper.selectOne(any())).thenReturn(stored);

        assertThatThrownBy(() -> authService.refresh("expired"))
                .isInstanceOf(BizException.class)
                .hasMessage("刷新令牌已过期");
    }
}
