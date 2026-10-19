package com.tickcart.seckill.cache;

/**
 * 秒杀相关的 Redis key 拼装。
 *
 * <p>统一前缀 {@code seckill:}，与 {@code cache:}（A2 读缓存）、{@code limit:}（A3 限流）、
 * {@code auth:} 区分，便于在 Redis 里按前缀排查与清理。</p>
 *
 * <p>集中在一处是为了让「写」与「读」用同一个拼法 —— 两边各写一遍字符串，
 * 迟早会出现改了读的 key 却忘了改写的 key。</p>
 */
public final class SeckillKeys {

    private static final String NS = "seckill:";

    private SeckillKeys() {
    }

    /** 活动快照（JSON）：抢购链路靠它校验状态与限购，避免每请求查 DB。 */
    public static String activity(Long activityId) {
        return NS + "activity:" + activityId;
    }

    /** 剩余库存 —— 秒杀的唯一权威口径。 */
    public static String stock(Long activityId) {
        return NS + "stock:" + activityId;
    }

    /** 某用户在某活动已抢数量，用于限购校验。 */
    public static String bought(Long activityId, Long userId) {
        return NS + "bought:" + activityId + ":" + userId;
    }

    /** 请求级防重标记（SETNX + 短 TTL），挡连点与脚本。 */
    public static String request(Long activityId, Long userId) {
        return NS + "req:" + activityId + ":" + userId;
    }

    /** 抢购结果凭据（B3 写、B4 轮询）。 */
    public static String result(String requestId) {
        return NS + "result:" + requestId;
    }
}
