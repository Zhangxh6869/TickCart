package com.tickcart.common.exception;

import com.tickcart.common.api.ResultCode;

/**
 * 需要人机验证：登录失败次数达到阈值后，必须通过验证码才能继续尝试。
 *
 * <p>与 {@link ResultCode#UNAUTHORIZED} 分开，是为了让前端能区分
 * 「密码错了」和「先过验证码」——两者都要弹不同界面，靠文案区分太脆。</p>
 */
public class VerifyRequiredException extends BizException {

    /** 验证挑战数据（图形码 id 与 SVG），由 auth 模块构造，common 层不关心其结构。 */
    private final Object challenge;

    public VerifyRequiredException(Object challenge, String message) {
        super(ResultCode.NEED_VERIFY, message);
        this.challenge = challenge;
    }

    public Object getChallenge() {
        return challenge;
    }
}
