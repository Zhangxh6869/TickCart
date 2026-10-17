package com.tickcart.common.security;

import com.tickcart.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌工具：签发与解析 access token。
 *
 * <p>refresh token 不使用 JWT，而是随机串并落库（{@code sys_refresh_token}），
 * 这样可服务端吊销，安全语义更清晰。</p>
 *
 * <p>access token 自带 {@code jti}：JWT 本身无法撤回，登出时只能把这个 id 记进黑名单，
 * 由过滤器逐请求比对 —— 没有 jti 的话「登出」就只是删掉客户端存储，服务端毫无办法。</p>
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("tickcart.jwt.secret 长度不足 32 字节，无法用于 HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** 签发 access token（带唯一 jti，供登出时吊销）。 */
    public String createAccessToken(Long userId, String username, Integer role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getAccessTokenMinutes() * 60_000);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验 access token。
     *
     * @throws JwtException 令牌无效或已过期
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** access token 有效期（秒），用于响应体告知前端。 */
    public long getAccessTokenSeconds() {
        return properties.getAccessTokenMinutes() * 60;
    }
}
