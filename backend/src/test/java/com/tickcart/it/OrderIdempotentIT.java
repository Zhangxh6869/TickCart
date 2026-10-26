package com.tickcart.it;

import com.tickcart.support.AbstractIntegrationTest;
import com.tickcart.support.InventoryInvariantChecker;
import com.tickcart.support.TestDataFactory;
import com.tickcart.trade.dto.OrderCreateRequest;
import com.tickcart.trade.service.OrderService;
import com.tickcart.trade.vo.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G2 并发闸门 · C1：同一幂等键并发下单。
 *
 * <p>这条用例专门验证 {@code IdempotentService.claim} 所依赖的两个数据库行为——
 * 「唯一键冲突后事务仍可继续」和「{@code FOR SHARE} 会阻塞到首个事务提交」。
 * 单测里 {@code DuplicateKeyException} 是手写 {@code throw} 出来的、{@code selectOne}
 * 是 {@code thenReturn} 硬造的，这两个行为在 mock 中永远成立，因此只有真 MySQL 能证伪。</p>
 */
class OrderIdempotentIT extends AbstractIntegrationTest {

    private static final int CONCURRENCY = 50;

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
    @DisplayName("同一幂等键并发 50 次，只产生 1 张订单")
    void concurrentRequestsWithSameKeyCreateExactlyOneOrder() throws Exception {
        TestDataFactory.BuyerFixture buyer = data.createBuyer(100, 1);

        OrderCreateRequest req = new OrderCreateRequest();
        req.setAddressId(buyer.addressId());

        String idemKey = "it-idem-" + System.nanoTime();
        List<Future<String>> futures = new ArrayList<>();
        Set<String> returnedOrderNos = new HashSet<>();
        int succeeded = 0;
        int rejected = 0;

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < CONCURRENCY; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return "ERR:InterruptedException";
                    }
                    try {
                        OrderVO vo = orderService.create(buyer.userId(), req, idemKey);
                        return "OK:" + vo.getOrderNo();
                    } catch (Exception e) {
                        // 幂等冲突时允许抛业务异常（如「请求处理中，请稍后重试」），
                        // 但不允许出现第二个订单号
                        return "ERR:" + e.getClass().getSimpleName();
                    }
                }));
            }

            ready.await(30, TimeUnit.SECONDS);
            start.countDown();

            for (Future<String> future : futures) {
                String result = future.get(60, TimeUnit.SECONDS);
                if (result.startsWith("OK:")) {
                    succeeded++;
                    returnedOrderNos.add(result.substring(3));
                } else {
                    rejected++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // 核心断言：库里只能有 1 张订单
        assertThat(invariants.orderCount(buyer.userId()))
                .as("同一幂等键并发 %d 次，订单数必须为 1（成功 %d / 拒绝 %d）",
                        CONCURRENCY, succeeded, rejected)
                .isEqualTo(1);

        // 所有「成功」的响应必须指向同一张单，不能各建各的
        assertThat(returnedOrderNos)
                .as("成功返回的订单号必须唯一")
                .hasSize(1);

        // 库存只应被扣 1 件
        assertThat(invariants.skuStock(buyer.product().skuId()))
                .as("库存只应扣减一次")
                .isEqualTo(buyer.product().initialStock() - 1);

        invariants.assertAll();
        invariants.assertSoldNotExceedInitialStock(
                buyer.product().skuId(), buyer.product().initialStock());
    }
}
