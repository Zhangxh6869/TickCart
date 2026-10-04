package com.tickcart.common.security;

import io.jsonwebtoken.Claims;

/**
 * 当前登录用户上下文（线程隔离）。
 *
 * <p>由 {@link JwtAuthenticationFilter} 在请求进入时写入、请求结束时清理；
 * 业务代码通过 {@link #getUserId()} / {@link #getUsername()} 获取当前用户，
 * 避免在各层反复解析令牌。</p>
 */
public final class UserContext {

    private static final ThreadLocal<Claims> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Claims claims) {
        HOLDER.set(claims);
    }

    /** 获取当前用户 ID；未登录返回 null。 */
    public static Long getUserId() {
        Claims claims = HOLDER.get();
        if (claims == null) {
            return null;
        }
        Object sub = claims.getSubject();
        return sub == null ? null : Long.valueOf(sub.toString());
    }

    /** 获取当前用户名；未登录返回 null。 */
    public static String getUsername() {
        Claims claims = HOLDER.get();
        return claims == null ? null : claims.get("username", String.class);
    }

    /** 获取当前用户角色 0普通 1管理员；未登录返回 null。 */
    public static Integer getRole() {
        Claims claims = HOLDER.get();
        if (claims == null) {
            return null;
        }
        Object role = claims.get("role");
        return role == null ? null : Integer.valueOf(role.toString());
    }

    public static Claims getClaims() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
