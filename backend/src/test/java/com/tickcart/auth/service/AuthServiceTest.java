package com.tickcart.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tickcart.auth.dto.LoginRequest;
import com.tickcart.auth.dto.RegisterRequest;
import com.tickcart.auth.dto.ResetPasswordRequest;
import com.tickcart.auth.dto.SmsLoginRequest;
import com.tickcart.auth.dto.SmsCodeSendResult;
import com.tickcart.auth.dto.TokenResponse;
import com.tickcart.auth.entity.RefreshToken;
import com.tickcart.auth.entity.SmsCode;
import com.tickcart.auth.entity.User;
import com.tickcart.auth.mapper.RefreshTokenMapper;
import com.tickcart.auth.mapper.SmsCodeMapper;
import com.tickcart.auth.mapper.UserMapper;
import com.tickcart.common.config.JwtProperties;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.exception.RateLimitedException;
import com.tickcart.common.exception.VerifyRequiredException;
import com.tickcart.common.security.JwtTokenProvider;
import com.tickcart.common.security.TokenBlacklist;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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

    /** 频控与人机验证用桩：默认「不锁定、不需要验证、不计数」，各用例按需覆盖。 */
    private LoginGuard loginGuard;
    private CaptchaService captchaService;
    private SmsCodeGuard smsCodeGuard;
    private TokenBlacklist tokenBlacklist;

    /** 短信服务：默认 Mock 开启（真实实现），个别用例换成桩以验证「关闭 Mock」的分支。 */
    private SmsService smsService;
    private JwtProperties jwtProperties;
    private JwtTokenProvider tokenProvider;

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
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("tickcart-test-secret-key-must-be-at-least-32-bytes-long");
        jwtProperties.setAccessTokenMinutes(30);
        jwtProperties.setRefreshTokenDays(7);
        jwtProperties.setIssuer("tickcart");
        tokenProvider = new JwtTokenProvider(jwtProperties);

        smsService = new SmsService();

        loginGuard = mock(LoginGuard.class);
        captchaService = mock(CaptchaService.class);
        smsCodeGuard = mock(SmsCodeGuard.class);
        tokenBlacklist = mock(TokenBlacklist.class);
        // 默认放行发送；只有专门测限流的用例才覆盖它
        lenient().when(smsCodeGuard.tryAcquireSend(anyString())).thenReturn(true);
        // 密码策略用真实实现：弱口令字典就在测试 classpath 里，没必要造假
        authService = buildAuthService(smsService);
    }

    /** 换一个短信服务实现重建被测对象（用于验证「关闭 Mock」分支）。 */
    private AuthService buildAuthService(SmsService sms) {
        return new AuthService(userMapper, refreshTokenMapper, smsCodeMapper,
                sms, passwordEncoder, tokenProvider, jwtProperties,
                loginGuard, captchaService, new PasswordPolicy(), smsCodeGuard, tokenBlacklist);
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

        assertThat(authService.loginByPassword(req, "127.0.0.1").getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("密码错误时登录失败且提示统一")
    void loginWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("demo", "123456", 0));

        LoginRequest req = new LoginRequest();
        req.setUsername("demo");
        req.setPassword("wrong");

        assertThatThrownBy(() -> authService.loginByPassword(req, "127.0.0.1"))
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

        assertThatThrownBy(() -> authService.loginByPassword(req, "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .hasMessage("账号已被禁用");
    }

    @Test
    @DisplayName("失败次数达到阈值后，不带图形码直接拒绝并要求验证")
    void loginRequiresCaptchaWhenThresholdReached() {
        when(loginGuard.isVerifyRequired(any(), any())).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setUsername("demo");
        req.setPassword("123456");

        assertThatThrownBy(() -> authService.loginByPassword(req, "127.0.0.1"))
                .isInstanceOf(VerifyRequiredException.class)
                .hasMessageContaining("安全验证");
    }

    @Test
    @DisplayName("图形码错误时同样要求重新验证，不继续比对密码")
    void loginRejectsWrongCaptcha() {
        when(loginGuard.isVerifyRequired(any(), any())).thenReturn(true);
        when(captchaService.verify("cid", "WRONG")).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setUsername("demo");
        req.setPassword("123456");
        req.setCaptchaId("cid");
        req.setCaptchaCode("WRONG");

        assertThatThrownBy(() -> authService.loginByPassword(req, "127.0.0.1"))
                .isInstanceOf(VerifyRequiredException.class);
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("图形码通过后正常比对密码并登录")
    void loginSucceedsAfterCaptchaPassed() {
        when(loginGuard.isVerifyRequired(any(), any())).thenReturn(true);
        when(captchaService.verify("cid", "AB12")).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(existingUser("demo", "123456", 0));

        LoginRequest req = new LoginRequest();
        req.setUsername("demo");
        req.setPassword("123456");
        req.setCaptchaId("cid");
        req.setCaptchaCode("AB12");

        assertThat(authService.loginByPassword(req, "127.0.0.1").getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("失败刚好触发锁定时返回剩余等待秒数")
    void loginReturnsRetryAfterWhenLocked() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("demo", "123456", 0));
        when(loginGuard.getRetryAfterSeconds(any(), any())).thenReturn(600L);

        LoginRequest req = new LoginRequest();
        req.setUsername("demo");
        req.setPassword("wrong");

        assertThatThrownBy(() -> authService.loginByPassword(req, "127.0.0.1"))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageContaining("10 分钟");
    }

    @Test
    @DisplayName("账号不存在也走完整的失败流程（不留时间侧信道）")
    void loginUnknownUserStillRecordsFailure() {
        when(userMapper.selectOne(any())).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setUsername("nobody");
        req.setPassword("whatever");

        assertThatThrownBy(() -> authService.loginByPassword(req, "127.0.0.1"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名或密码错误");
        // 关键：没有在「查不到用户」处短路，失败计数照记
        verify(loginGuard).recordFailure("127.0.0.1", "nobody");
    }

    @Test
    @DisplayName("注册拒绝弱口令")
    void registerRejectsWeakPassword() {
        assertThatThrownBy(() -> authService.registerByPassword(registerReq("alice", "password123")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过于简单");
    }

    @Test
    @DisplayName("注册拒绝包含用户名的密码")
    void registerRejectsPasswordContainingUsername() {
        assertThatThrownBy(() -> authService.registerByPassword(registerReq("alice", "alice2024")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能包含用户名");
    }

    @Test
    @DisplayName("发送间隔内重复获取验证码被限流")
    void sendSmsCodeRejectedWithinInterval() {
        when(smsCodeGuard.tryAcquireSend("13700000000")).thenReturn(false);
        when(smsCodeGuard.getRetryAfterSeconds("13700000000")).thenReturn(25L);

        assertThatThrownBy(() -> authService.sendSmsCode("13700000000", AuthService.SMS_SCENE_LOGIN))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageContaining("25 秒");
        // 被限流时不该落库、也不该发短信
        verify(smsCodeMapper, never()).insert(any(SmsCode.class));
    }

    @Test
    @DisplayName("Mock 模式下发送验证码回传 devCode，供前端自测直接填")
    void sendSmsCodeReturnsDevCodeInMockMode() {
        SmsCodeSendResult result = authService.sendSmsCode("13700000000", AuthService.SMS_SCENE_LOGIN);

        assertThat(result.devCode()).matches("[1-9]\\d{5}");
        assertThat(result.expireSeconds()).isEqualTo(300);

        // 回显的码必须与真正落库的那条一致，否则前端照着填也登不进去
        ArgumentCaptor<SmsCode> captor = ArgumentCaptor.forClass(SmsCode.class);
        verify(smsCodeMapper).insert(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(result.devCode());
        assertThat(captor.getValue().getScene()).isEqualTo(AuthService.SMS_SCENE_LOGIN);
    }

    @Test
    @DisplayName("关闭 Mock 时不回传 devCode，验证码不会泄漏到生产")
    void sendSmsCodeHidesDevCodeWhenMockDisabled() {
        SmsService realGateway = mock(SmsService.class);
        when(realGateway.generateCode()).thenReturn("483920");
        when(realGateway.getCodeExpireSeconds()).thenReturn(300);
        when(realGateway.isMockEnabled()).thenReturn(false);

        SmsCodeSendResult result = buildAuthService(realGateway)
                .sendSmsCode("13700000000", AuthService.SMS_SCENE_LOGIN);

        assertThat(result.devCode()).isNull();
        assertThat(result.expireSeconds()).isEqualTo(300);
        // 码照样落库（用户收到的是真短信），只是不回传
        verify(smsCodeMapper).insert(any(SmsCode.class));
    }

    @Test
    @DisplayName("验证码错误达上限时作废当前码，要求重新获取")
    void verifyCodeInvalidatedAfterTooManyFailures() {
        when(userMapper.selectOne(any())).thenReturn(userWithPhone("13700000000", "abc12345"));
        when(smsCodeMapper.selectOne(any()))
                .thenReturn(validCode("13700000000", AuthService.SMS_SCENE_LOGIN, "123456"));
        when(smsCodeGuard.recordVerifyFailure(9L)).thenReturn(true);

        SmsLoginRequest req = new SmsLoginRequest();
        req.setPhone("13700000000");
        req.setCode("000000");

        assertThatThrownBy(() -> authService.loginBySms(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("次数过多");

        ArgumentCaptor<SmsCode> captor = ArgumentCaptor.forClass(SmsCode.class);
        verify(smsCodeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getUsed()).isEqualTo(1);
    }

    @Test
    @DisplayName("退出登录按 jti 拉黑当前令牌，TTL 取剩余有效期")
    void logoutRevokesCurrentToken() {
        authService.logout(1L, "jti-abc", 1200L);

        verify(tokenBlacklist).revoke("jti-abc", 1200L);
        // 刷新令牌也要一起吊销，否则用户还能用 refresh 换一套新的
        verify(refreshTokenMapper).update(any(), any());
    }

    @Test
    @DisplayName("重置密码后该用户此前签发的令牌全部失效")
    void resetPasswordInvalidatesIssuedTokens() {
        when(userMapper.selectOne(any())).thenReturn(userWithPhone("13700000000", "abc12345"));
        when(smsCodeMapper.selectOne(any()))
                .thenReturn(validCode("13700000000", AuthService.SMS_SCENE_RESET, "123456"));

        authService.resetPassword(resetReq("13700000000", "123456", "Xx12345678"));

        verify(tokenBlacklist).revokeAllIssuedBefore(1L, 30 * 60L);
    }

    @Test
    @DisplayName("未登录访问用户信息返回 401，而不是「用户不存在」404")
    void getUserInfoRequiresLogin() {
        assertThatThrownBy(() -> authService.getUserInfo(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
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
