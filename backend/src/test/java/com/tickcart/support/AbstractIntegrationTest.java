package com.tickcart.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.List;

/**
 * 集成测试基类：起一套真实中间件，加载完整 Spring 上下文。
 *
 * <p>为什么必须是真的：本项目的三套核心机制——下单幂等（唯一键冲突 + {@code FOR SHARE}）、
 * 防超卖（条件 UPDATE / Redis SETNX）、超时关单（手动 ack + 死信）——依赖的都是
 * 中间件的具体行为，Mockito 里这些行为是手写出来的，永远验证不了假设是否成立。</p>
 *
 * <p>三个容器分别对应三处依赖：MySQL（Flyway 建表 + 事务与锁语义）、
 * Redis（库存预扣与幂等缓存）、RabbitMQ（延迟消息与死信，应用启动时会声明队列，
 * 缺了上下文起不来）。</p>
 *
 * <p><b>singleton container 模式</b>：容器在类加载时手动启动、由 Ryuk 在 JVM 退出时回收，
 * 而不是用 {@code @Container} 让每个测试类各自起停——后者会让每个 IT 类都多花约 20 秒。
 * 代价是所有集成测试共享同一套数据，因此每个用例必须自己清表（见各测试的 {@code setUp}）。</p>
 *
 * <p><b>命名约定</b>：集成测试类以 {@code IT} 结尾，与 surefire 默认的 {@code *Test}
 * 区分开，避免单测闸门被拖慢。</p>
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("tickcart_it")
                    .withUsername("test")
                    .withPassword("test");

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @ServiceConnection
    protected static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3.13-management");

    static {
        MYSQL.start();
        REDIS.start();
        RABBIT.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * 清空业务表。
     *
     * <p>表之间没有物理外键，顺序无关。用 TRUNCATE 而不是 DELETE：它会把自增 ID 归零，
     * 让每个用例的 ID 从 1 开始，出问题时更容易对照日志。</p>
     *
     * <p><b>注意</b>：Flyway 的种子数据（V4/V6/V12）也会被清掉。这是刻意的——
     * 集成测试自己造数才能精确控制库存，依赖种子反而会让并发用例互相干扰。</p>
     */
    protected void cleanBusinessTables() {
        for (String table : List.of(
                "order_item", "t_order", "cart_item",
                "product_sku", "product", "product_category",
                "user_address", "sys_user", "idempotent_record")) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
    }

    /**
     * Redis 没有官方 Testcontainers 模块，{@code @ServiceConnection} 无法自动识别，
     * 因此手工把随机端口写进配置。
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
