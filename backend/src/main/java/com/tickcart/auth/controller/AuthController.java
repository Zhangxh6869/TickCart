package com.tickcart.auth.controller;

import com.tickcart.auth.dto.CaptchaChallenge;
import com.tickcart.auth.dto.LoginRequest;
import com.tickcart.auth.dto.RefreshRequest;
import com.tickcart.auth.dto.RegisterRequest;
import com.tickcart.auth.dto.ResetPasswordRequest;
import com.tickcart.auth.dto.SendSmsCodeRequest;
import com.tickcart.auth.dto.SmsCodeSendResult;
import com.tickcart.auth.dto.SmsLoginRequest;
import com.tickcart.auth.dto.SmsRegisterRequest;
import com.tickcart.auth.dto.TokenResponse;
import com.tickcart.auth.dto.UserInfoResponse;
import com.tickcart.auth.service.AuthService;
import com.tickcart.auth.service.CaptchaService;
import com.tickcart.common.api.R;
import com.tickcart.common.security.ClientIpResolver;
import com.tickcart.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@Tag(name = "认证", description = "注册、登录、令牌刷新、用户信息")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @Operation(summary = "发送短信验证码",
            description = "验证码每次随机生成。Mock 模式下响应 data.devCode 会回显验证码，供本地自测；"
                    + "真实网关模式不返回该字段")
    @PostMapping("/sms/send")
    public R<SmsCodeSendResult> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest req) {
        return R.ok(authService.sendSmsCode(req.getPhone(), req.getScene()));
    }

    @Operation(summary = "账密注册")
    @PostMapping("/register")
    public R<TokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return R.ok(authService.registerByPassword(req));
    }

    @Operation(summary = "手机验证码注册")
    @PostMapping("/register/sms")
    public R<TokenResponse> registerBySms(@Valid @RequestBody SmsRegisterRequest req) {
        return R.ok(authService.registerBySms(req));
    }

    @Operation(summary = "账密登录", description = "失败次数达到阈值后返回 40101，需带图形码重试")
    @PostMapping("/login")
    public R<TokenResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        return R.ok(authService.loginByPassword(req, ClientIpResolver.resolve(request)));
    }

    @Operation(summary = "获取图形验证码", description = "答案存 Redis，默认 5 分钟有效，校验一次即作废")
    @GetMapping("/captcha")
    public R<CaptchaChallenge> captcha() {
        return R.ok(captchaService.create());
    }

    @Operation(summary = "手机验证码登录")
    @PostMapping("/login/sms")
    public R<TokenResponse> loginBySms(@Valid @RequestBody SmsLoginRequest req) {
        return R.ok(authService.loginBySms(req));
    }

    @Operation(summary = "重置密码（忘记密码）", description = "凭手机号 + 短信验证码（场景 3）重设密码，成功后吊销全部刷新令牌")
    @PostMapping("/password/reset")
    public R<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return R.ok();
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public R<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return R.ok(authService.refresh(req.getRefreshToken()));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<UserInfoResponse> me() {
        return R.ok(authService.getUserInfo(UserContext.getUserId()));
    }

    @Operation(summary = "退出登录", description = "吊销当前 access token 与该用户全部刷新令牌，旧令牌立即失效")
    @PostMapping("/logout")
    public R<Void> logout() {
        Claims claims = UserContext.getClaims();
        String jti = claims == null ? null : claims.getId();
        long remainingSeconds = 0L;
        if (claims != null && claims.getExpiration() != null) {
            remainingSeconds = Math.max(0L,
                    (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000);
        }
        authService.logout(UserContext.getUserId(), jti, remainingSeconds);
        return R.ok();
    }
}
