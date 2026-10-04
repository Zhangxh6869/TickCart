package com.tickcart.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项，对应 {@code tickcart.jwt.*}。
 */
@Component
@ConfigurationProperties(prefix = "tickcart.jwt")
public class JwtProperties {

    /** HS256 签名密钥，长度需 >= 32 字节。 */
    private String secret;

    /** access token 有效期（分钟）。 */
    private long accessTokenMinutes = 30;

    /** refresh token 有效期（天）。 */
    private long refreshTokenDays = 7;

    /** 签发者。 */
    private String issuer = "tickcart";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public void setAccessTokenMinutes(long accessTokenMinutes) {
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public long getRefreshTokenDays() {
        return refreshTokenDays;
    }

    public void setRefreshTokenDays(long refreshTokenDays) {
        this.refreshTokenDays = refreshTokenDays;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
