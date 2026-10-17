package com.tickcart.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 取客户端 IP，用于登录频控。
 *
 * <p>优先取代理头首段（{@code X-Forwarded-For} 的第一段才是真实客户端，后面是各级代理），
 * 取不到时回退 {@code getRemoteAddr()}。</p>
 *
 * <p><b>安全前提：</b>{@code X-Forwarded-For} 可被客户端伪造。生产环境必须由网关/Nginx
 * 用 {@code proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for} 覆盖该头，
 * 否则攻击者换一个假 IP 就能绕过 IP 维度限流（账号维度仍然生效，这是主要防线）。</p>
 */
public final class ClientIpResolver {

    private static final String UNKNOWN = "unknown";
    private static final String[] PROXY_HEADERS = {"X-Forwarded-For", "X-Real-IP"};

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        for (String header : PROXY_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !UNKNOWN.equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                String ip = (comma > 0 ? value.substring(0, comma) : value).trim();
                if (StringUtils.hasText(ip)) {
                    return ip;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : UNKNOWN;
    }
}
