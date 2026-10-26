package com.tickcart.it;

import com.tickcart.common.exception.BizException;
import com.tickcart.support.AbstractIntegrationTest;
import com.tickcart.support.InventoryInvariantChecker;
import com.tickcart.support.TestDataFactory;
import com.tickcart.trade.dto.OrderCreateRequest;
import com.tickcart.trade.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G2 并发闸门 · C3：库存 10、100 并发下单，必须恰好卖出 10 件。
 *
 * <p>这是整个测试方案里最值钱的一条。超卖的代价是赔钱和无法履约，而单测里
 * {@code InventoryService.deduct} 的条件 UPDATE 被 mock 成「总是成功」或「总是失败」，
 * 唯一能验证「100 个并发里恰好 10 个成功」的地方只有真 MySQL 的行锁语义。</p>
 *
 * <p>断言分三层：成功数、库存余量、以及不变量 I3（成交数量不得超过备货）。
 * 第三层是兜底——即使前两个数字碰巧对，I3 也能独立抓出超卖。</p>
 */
class OrderOversellIT extends AbstractIntegrationTest {

    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENCY = 100;

    @Autowired
    private OrderService orderService;

    private TestDataFactory data;
    private InventoryInvariantChecker invariants;

    @BeforeEach
    void setUp() {
        data = new TestDataFactory(jdbc);
        invariants = new InventoryInvariantChecker(jdbc);
        cleanBusinessTables();
    }

    @Test
    @DisplayName("库存 10 / 并发 100：成功恰好 10 件，库存归零，无超卖")
    void concurrentOrdersDoNotOversell() throws Exception {
        TestDataFactory.ProductFixture product = data.createProduct(INITIAL_STOCK);

        // 每个买家一份独立购物车，但都指向同一个 SKU —— 冲突点在库存行上
        List<Long> userIds = new ArrayList<>(CONCURRENCY);
        List<OrderCreateRequest> requests = new ArrayList<>(CONCURRENCY);
        for (int i = 0; i < CONCURRENCY; i++) {
            long userId = data.createUser();
            long addressId = data.createAddress(userId);
            data.addToCart(userId, product, 1);

            OrderCreateRequest req = new OrderCreateRequest();
            req.setAddressId(addressId);
            requests.add(req);
            userIds.add(userId);
        }

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger outOfStock = new AtomicInteger();
        List<String> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(CONCURRENCY);

        try {
            for (int i = 0; i < CONCURRENCY; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        // 不传幂等键：本用例要验证的是库存守卫，不是幂等
                        orderService.create(userIds.get(idx), requests.get(idx), null);
                        succeeded.incrementAndGet();
                    } catch (BizException e) {
                        outOfStock.incrementAndGet();
                    } catch (Exception e) {
                        unexpectedErrors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }));
            }

            ready.await(60, TimeUnit.SECONDS);
            start.countDown();

            for (Future<?> future : futures) {
                future.get(180, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpectedErrors)
                .as("除「库存不足」外不应出现其他异常（如死锁、锁等待超时）")
                .isEmpty();

        assertThat(succeeded.get())
                .as("成功下单数必须恰好等于初始库存 %d（实际库存不足 %d 个）",
                        INITIAL_STOCK, outOfStock.get())
                .isEqualTo(INITIAL_STOCK);

        assertThat(invariants.skuStock(product.skuId()))
                .as("卖完后 SKU 库存必须归零")
                .isZero();

        assertThat(invariants.orderCount(null))
                .as("订单总数必须等于成功下单数")
                .isEqualTo(INITIAL_STOCK);

        // 兜底：即使上面的数字碰巧对上，I3 也要独立成立
        invariants.assertSoldNotExceedInitialStock(product.skuId(), INITIAL_STOCK);
        invariants.assertAll();
    }
}
