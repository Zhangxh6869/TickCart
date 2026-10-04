package com.tickcart.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tickcart.auth.dto.LoginRequest;
import com.tickcart.auth.dto.RegisterRequest;
import com.tickcart.auth.dto.SmsLoginRequest;
import com.tickcart.auth.dto.SmsRegisterRequest;
import com.tickcart.auth.dto.TokenResponse;
import com.tickcart.auth.dto.UserInfoResponse;
import com.tickcart.auth.entity.RefreshToken;
import com.tickcart.auth.entity.SmsCode;
import com.tickcart.auth.entity.User;
import com.tickcart.auth.mapper.RefreshTokenMapper;
import com.tickcart.auth.mapper.SmsCodeMapper;
import com.tickcart.auth.mapper.UserMapper;
import com.tickcart.common.config.JwtProperties;
import com.tickcart.common.exception.BizException;
import com.tickcart.common.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 认证服务：注册、登录、令牌刷新。
 *
 * <p>支持账密与手机验证码两种形态；access token 为 JWT（无状态），
 * refresh token 为随机串并落库以支持服务端吊销。</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 短信场景：注册 */
    public static final int SMS_SCENE_REGISTER = 1;
    /** 短信场景：登录 */
    public static final int SMS_SCENE_LOGIN = 2;

    /** 用户状态：正常 / 禁用 */
    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_DISABLED = 1;
    /** 刷新令牌：有效 / 已吊销 */
    private static final int TOKEN_ACTIVE = 0;
    private static final int TOKEN_REVOKED = 1;

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final SmsCodeMapper smsCodeMapper;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    public AuthService(UserMapper userMapper,
                       RefreshTokenMapper refreshTokenMapper,
                       SmsCodeMapper smsCodeMapper,
                       SmsService smsService,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       JwtProperties jwtProperties) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.smsCodeMapper = smsCodeMapper;
        this.smsService = smsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    // =========================================================================
    // 发送验证码
    // =========================================================================

    /** 发送短信验证码（落库，校验口径：最新一条未使用且未过期）。 */
    @Transactional
    public void sendSmsCode(String phone, Integer scene) {
        if (scene == null || scene < 1 || scene > 3) {
            throw BizException.badRequest("短信场景不合法");
        }
        String code = smsService.generateCode();
        SmsCode entity = new SmsCode();
        entity.setPhone(phone);
        entity.setCode(code);
        entity.setScene(scene);
        entity.setUsed(0);
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(smsService.getCodeExpireSeconds()));
        smsCodeMapper.insert(entity);

        smsService.send(phone, code);
    }

    /** 校验并消费验证码；不通过则抛业务异常。 */
    private void verifyAndConsumeCode(String phone, Integer scene, String code) {
        SmsCode latest = smsCodeMapper.selectOne(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getScene, scene)
                .eq(SmsCode::getUsed, 0)
                .orderByDesc(SmsCode::getId)
                .last("LIMIT 1"));
        if (latest == null) {
            throw BizException.badRequest("验证码不存在，请先获取");
        }
        if (latest.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BizException.badRequest("验证码已过期");
        }
        if (!latest.getCode().equals(code)) {
            throw BizException.badRequest("验证码错误");
        }
        // 标记已使用（一次性）
        SmsCode update = new SmsCode();
        update.setId(latest.getId());
        update.setUsed(1);
        smsCodeMapper.updateById(update);
    }

    // =========================================================================
    // 注册
    // =========================================================================

    /** 账密注册。 */
    @Transactional
    public TokenResponse registerByPassword(RegisterRequest req) {
        if (findByUsername(req.getUsername()) != null) {
            throw BizException.conflict("用户名已存在");
        }
        if (StringUtils.hasText(req.getPhone()) && findByPhone(req.getPhone()) != null) {
            throw BizException.conflict("手机号已被注册");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setPhone(StringUtils.hasText(req.getPhone()) ? req.getPhone() : null);
        user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname() : req.getUsername());
        user.setGender(0);
        user.setRole(0);
        user.setStatus(STATUS_NORMAL);
        userMapper.insert(user);

        return issueTokens(user);
    }

    /** 手机验证码注册（用户名/密码可选，缺省时用户名取手机号）。 */
    @Transactional
    public TokenResponse registerBySms(SmsRegisterRequest req) {
        verifyAndConsumeCode(req.getPhone(), SMS_SCENE_REGISTER, req.getCode());

        if (findByPhone(req.getPhone()) != null) {
            throw BizException.conflict("手机号已被注册");
        }
        String username = StringUtils.hasText(req.getUsername()) ? req.getUsername() : req.getPhone();
        if (findByUsername(username) != null) {
            throw BizException.conflict("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        // 无密码时写入随机 BCrypt 串，保证字段非空且该账号无法用密码登录
        String rawPassword = StringUtils.hasText(req.getPassword())
                ? req.getPassword() : UUID.randomUUID().toString();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPhone(req.getPhone());
        user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname() : username);
        user.setGender(0);
        user.setRole(0);
        user.setStatus(STATUS_NORMAL);
        userMapper.insert(user);

        return issueTokens(user);
    }

    // =========================================================================
    // 登录
    // =========================================================================

    /** 账密登录。 */
    public TokenResponse loginByPassword(LoginRequest req) {
        User user = findByUsername(req.getUsername());
        // 统一提示，避免暴露"用户是否存在"
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        assertUserUsable(user);
        return issueTokens(user);
    }

    /** 手机验证码登录（手机号需已注册）。 */
    @Transactional
    public TokenResponse loginBySms(SmsLoginRequest req) {
        User user = findByPhone(req.getPhone());
        if (user == null) {
            throw BizException.notFound("该手机号尚未注册");
        }
        assertUserUsable(user);
        verifyAndConsumeCode(req.getPhone(), SMS_SCENE_LOGIN, req.getCode());
        return issueTokens(user);
    }

    // =========================================================================
    // 令牌刷新 / 用户信息
    // =========================================================================

    /** 用 refresh token 换新令牌（旋转：旧 refresh 作废）。 */
    @Transactional
    public TokenResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getToken, refreshTokenValue));
        if (stored == null || stored.getRevoked() != TOKEN_ACTIVE) {
            throw BizException.unauthorized("刷新令牌无效");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BizException.unauthorized("刷新令牌已过期");
        }
        User user = userMapper.selectById(stored.getUserId());
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        assertUserUsable(user);

        // 旋转：吊销旧令牌
        RefreshToken revoke = new RefreshToken();
        revoke.setId(stored.getId());
        revoke.setRevoked(TOKEN_REVOKED);
        refreshTokenMapper.updateById(revoke);

        return issueTokens(user);
    }

    /** 查询用户公开信息。 */
    public UserInfoResponse getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return UserInfoResponse.from(user);
    }

    /** 退出登录：吊销该用户的全部刷新令牌。 */
    @Transactional
    public void logout(Long userId) {
        RefreshToken update = new RefreshToken();
        update.setRevoked(TOKEN_REVOKED);
        refreshTokenMapper.update(update, new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getUserId, userId)
                .eq(RefreshToken::getRevoked, TOKEN_ACTIVE));
    }

    // =========================================================================
    // 内部方法
    // =========================================================================

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getRole());
        String refreshTokenValue = UUID.randomUUID().toString().replace("-", "");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(jwtProperties.getRefreshTokenDays()));
        refreshToken.setRevoked(TOKEN_ACTIVE);
        refreshTokenMapper.insert(refreshToken);

        return new TokenResponse(accessToken, refreshTokenValue, jwtTokenProvider.getAccessTokenSeconds());
    }

    private void assertUserUsable(User user) {
        if (user.getStatus() != null && user.getStatus() == STATUS_DISABLED) {
            throw BizException.forbidden("账号已被禁用");
        }
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    private User findByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
    }
}
