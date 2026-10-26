package com.tickcart.support;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * 异步等待工具。
 *
 * <p>异步链路测试不能用 {@code Thread.sleep}：睡短了会 flaky，睡长了拖慢整个套件。
 * 这里轮询条件直到成立或超时，超时时抛出带描述的断言错误，便于定位是哪一步没等到。</p>
 */
public final class Await {

    private static final long POLL_INTERVAL_MS = 100;

    private Await() {
    }

    /**
     * 等待条件成立。
     *
     * @param description 条件描述，超时时会出现在错误信息里
     * @param timeout     最长等待时间
     * @param condition   判定条件
     * @throws AssertionError 超时仍未成立
     */
    public static void until(String description, Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待被中断：" + description, e);
            }
        }
        throw new AssertionError("等待超时（" + timeout.toMillis() + "ms）：" + description);
    }
}
