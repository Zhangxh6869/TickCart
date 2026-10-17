package com.tickcart.common.security;

/**
 * access token 吊销名单。
 *
 * <p>JWT 是自包含的：签发后服务端不保存任何状态，因此「登出」「改密」都无法靠删数据
 * 让已发出的令牌失效。这里用 Redis 维护两类记录来补上这个缺口：</p>
 *
 * <ul>
 *   <li><b>单令牌吊销</b>：登出时按 {@code jti} 拉黑，TTL 取该令牌的剩余有效期</li>
 *   <li><b>按用户吊销</b>：改密/踢下线时记一个「失效时间点」，签发时间早于它的令牌全部作废</li>
 * </ul>
 *
 * <p>两者都是 fail-open：Redis 不可用时视为未吊销 —— 否则 Redis 一抖全站 401，
 * 比「登出后旧令牌多活几分钟」严重得多。</p>
 */
public interface TokenBlacklist {

    /** 拉黑单个令牌。{@code ttlSeconds} 取令牌剩余有效期，到期自动清理。 */
    void revoke(String jti, long ttlSeconds);

    /** 该 jti 是否已被拉黑。 */
    boolean isRevoked(String jti);

    /**
     * 让某用户此刻之前签发的所有令牌失效（改密、踢下线）。
     *
     * @param ttlSeconds 记录保留时长，应覆盖 access token 的最长寿命
     */
    void revokeAllIssuedBefore(Long userId, long ttlSeconds);

    /** 某用户在该时刻（epoch 秒）之前签发的令牌是否已失效。 */
    boolean isIssuedBefore(Long userId, long issuedAtEpochSecond);
}
