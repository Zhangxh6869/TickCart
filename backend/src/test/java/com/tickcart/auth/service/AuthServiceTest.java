package com.tickcart.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.auth.dto.LoginRequest;
import com.tickcart.auth.dto.RegisterRequest;
import com.tickcart.auth.dto.ResetPasswordRequest;
import com.tickcart.auth.dto.TokenResponse;
import com.tickcart.auth.entity.RefreshToken;
import com.tickcart.auth.entity.SmsCode;
import com.tickcart.auth.entity.User;
import com.tickcart.auth.mapper.RefreshTokenMapper;
import com.tickcart.auth.mapper.SmsCodeMapper;
import com.tickcart.auth.mapper.UserMapper;
import com.tickcart.common.config.JwtProperties;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.JwtTokenProvider;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    /**
     * 纯单元测试没有 Spring 容器，而 {@code LambdaQueryWrapper} 解析列名依赖实体的
     * TableInfo 缓存，需先手动初始化，否则报 "can not find lambda cache for this entity"。
     * 只有断言里会调用 {@code getSqlSegment()} 把条件实体化的用例才需要（见「只认场景 3」）。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SmsCode.class);
    }

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

    /** 已注册且绑定了手机号的用户（重置密码需要靠手机号找账号）。 */
    private User userWithPhone(String phone, String rawPassword) {
        User user = existingUser("demo", rawPassword, 0);
        user.setPhone(phone);
        return user;
    }

    /** 造一条未使用的有效验证码。 */
    private SmsCode validCode(String phone, int scene, String code) {
        SmsCode sms = new SmsCode();
        sms.setId(9L);
        sms.setPhone(phone);
        sms.setScene(scene);
        sms.setCode(code);
        sms.setUsed(0);
        sms.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return sms;
    }

    private ResetPasswordRequest resetReq(String phone, String code, String newPassword) {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setPhone(phone);
        req.setCode(code);
        req.setNewPassword(newPassword);
        return req;
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

    // ---------------- 重置密码（忘记密码） ----------------

    @Test
    @DisplayName("重置密码：验证码正确时换成新密码，并吊销全部刷新令牌")
    void resetPasswordSuccess() {
        String phone = "13900000000";
        when(userMapper.selectOne(any())).thenReturn(userWithPhone(phone, "oldpass"));
        when(smsCodeMapper.selectOne(any()))
                .thenReturn(validCode(phone, AuthService.SMS_SCENE_RESET, "123456"));
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        authService.resetPassword(resetReq(phone, "123456", "newpass123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getId()).isEqualTo(1L);
        // 落库的是 BCrypt 串，且新密码能匹配上、旧密码匹配不上
        assertThat(userCaptor.getValue().getPassword()).isNotEqualTo("newpass123");
        assertThat(passwordEncoder.matches("newpass123", userCaptor.getValue().getPassword())).isTrue();
        assertThat(passwordEncoder.matches("oldpass", userCaptor.getValue().getPassword())).isFalse();

        // ★ 密码已变更，旧会话必须失效
        verify(refreshTokenMapper).update(any(RefreshToken.class), any());
    }

    @Test
    @DisplayName("重置密码：手机号未注册时拒绝")
    void resetPasswordUnknownPhone() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.resetPassword(resetReq("13900000000", "123456", "newpass123")))
                .isInstanceOf(BizException.class)
                .hasMessage("该手机号尚未注册");
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("重置密码：验证码错误时拒绝且不改密码")
    void resetPasswordWrongCode() {
        String phone = "13900000000";
        when(userMapper.selectOne(any())).thenReturn(userWithPhone(phone, "oldpass"));
        when(smsCodeMapper.selectOne(any()))
                .thenReturn(validCode(phone, AuthService.SMS_SCENE_RESET, "123456"));

        assertThatThrownBy(() -> authService.resetPassword(resetReq(phone, "000000", "newpass123")))
                .isInstanceOf(BizException.class)
                .hasMessage("验证码错误");
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("重置密码：只认场景 3 的验证码（登录/注册的码不能用）")
    @SuppressWarnings("unchecked")
    void resetPasswordUsesResetScene() {
        String phone = "13900000000";
        when(userMapper.selectOne(any())).thenReturn(userWithPhone(phone, "oldpass"));
        when(smsCodeMapper.selectOne(any()))
                .thenReturn(validCode(phone, AuthService.SMS_SCENE_RESET, "123456"));
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        authService.resetPassword(resetReq(phone, "123456", "newpass123"));

        ArgumentCaptor<LambdaQueryWrapper<SmsCode>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(smsCodeMapper).selectOne(captor.capture());
        // ★ 条件值在 SQL 片段生成时才写入参数表，必须先实体化再断言
        captor.getValue().getSqlSegment();
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(AuthService.SMS_SCENE_RESET);
    }
}
