package com.tickcart.auth.service;

import com.tickcart.auth.dto.CaptchaChallenge;
import com.tickcart.common.config.LoginGuardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 图形验证码：生成 SVG、校验答案。
 *
 * <p>答案存 Redis（默认 5 分钟），校验即删除 —— 一次性使用，避免同一个码被复用爆破。
 * 校验不区分大小写，降低真人输入成本（字符集已剔除 0/O、1/I/L 这类易混字符）。</p>
 *
 * <p>Redis 不可用时：生成仍返回图形（答案没存进去，校验必然失败），校验侧按 fail-open
 * 放行，与 {@link RedisLoginGuard} 保持一致的降级口径。</p>
 */
@Component
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    /** 去掉 0/O/1/I/L 等易混字符，减少「明明看对了却输错」的挫败感。 */
    private static final String CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String[] COLORS = {"#2c3e50", "#2e6b8a", "#b23a48", "#3a7d44", "#6b4c9a", "#8a5a2e"};
    private static final String KEY_PREFIX = "auth:captcha:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final LoginGuardProperties props;

    public CaptchaService(StringRedisTemplate redis, LoginGuardProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** 生成一个新的图形码（答案落 Redis）。 */
    public CaptchaChallenge create() {
        String code = randomCode(props.getCaptchaLength());
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            redis.opsForValue().set(KEY_PREFIX + id, code,
                    Duration.ofSeconds(props.getCaptchaExpireSeconds()));
        } catch (DataAccessException ex) {
            log.warn("图形码写入 Redis 失败，本次校验将按降级策略处理：{}", ex.getMessage());
        }
        return new CaptchaChallenge(id, renderSvg(code), props.getCaptchaExpireSeconds());
    }

    /**
     * 校验并消费图形码。
     *
     * @return 通过返回 true；参数缺失、答案错误、已过期均返回 false
     */
    public boolean verify(String id, String input) {
        if (id == null || id.isBlank() || input == null || input.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + id;
        try {
            String expected = redis.opsForValue().get(key);
            // 无论对错都删除：一个码只给一次机会，防止固定答案反复试
            redis.delete(key);
            return expected != null && expected.equalsIgnoreCase(input.trim());
        } catch (DataAccessException ex) {
            log.warn("图形码校验降级放行：Redis 不可用 - {}", ex.getMessage());
            return true;
        }
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 渲染 4 字符图形码：每个字符独立随机倾斜、随机位置抖动，叠两条干扰线。
     *
     * <p>只做「挡住脚本」这一档强度 —— 真要对抗打码平台，得换行为式验证。</p>
     */
    private String renderSvg(String code) {
        int width = 104;
        int height = 40;
        StringBuilder svg = new StringBuilder(640);
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='").append(width)
                .append("' height='").append(height).append("' viewBox='0 0 ").append(width).append(' ')
                .append(height).append("' role='img' aria-label='图形验证码'>");
        svg.append("<rect width='").append(width).append("' height='").append(height)
                .append("' rx='6' fill='#f7f8fa'/>");

        // 干扰线：颜色与字符区分开，保证可读性不被压垮
        for (int i = 0; i < 2; i++) {
            svg.append("<line x1='").append(rand(0, width)).append("' y1='").append(rand(0, height))
                    .append("' x2='").append(rand(0, width)).append("' y2='").append(rand(0, height))
                    .append("' stroke='").append(COLORS[rand(0, COLORS.length - 1)])
                    .append("' stroke-opacity='0.25' stroke-width='1'/>");
        }

        for (int i = 0; i < code.length(); i++) {
            int x = 14 + i * 22;
            int y = 27 + rand(-3, 3);
            int rotate = rand(-20, 20);
            svg.append("<text x='").append(x).append("' y='").append(y)
                    .append("' font-size='22' font-family='monospace' font-weight='700' fill='")
                    .append(COLORS[rand(0, COLORS.length - 1)])
                    .append("' transform='rotate(").append(rotate).append(' ').append(x).append(' ').append(y)
                    .append(")'>").append(code.charAt(i)).append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    /** 闭区间随机数。 */
    private int rand(int min, int max) {
        return min + RANDOM.nextInt(max - min + 1);
    }
}
