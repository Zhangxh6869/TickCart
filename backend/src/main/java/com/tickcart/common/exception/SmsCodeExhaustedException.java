package com.tickcart.common.exception;

import com.tickcart.common.api.ResultCode;

/**
 * 验证码错误次数超限，当前码已被作废。
 *
 * <p>单独成类是为了能在 {@code @Transactional} 上声明 {@code noRollbackFor}：
 * 「把码置为已使用」这个动作必须保留，而它和异常抛出发生在同一个事务里 ——
 * 用普通 {@link BizException} 抛出去会把作废一起回滚，变成「提示已作废、实际还能继续试」。</p>
 */
public class SmsCodeExhaustedException extends BizException {

    public SmsCodeExhaustedException(String message) {
        super(ResultCode.BAD_REQUEST, message);
    }
}
