package com.tickcart.it;

import com.tickcart.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试底座自检：上下文能否在真实中间件上启动、Flyway 能否在空库上建完表。
 *
 * <p>这不是业务测试，而是后续所有集成用例的前提——如果它挂了，说明是环境问题
 * 而不是业务回归，可以省掉一轮误判。</p>
 */
class ContextSmokeIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsAndFlywayMigrates() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                assertThat(rs.next()).isTrue();
                int tableCount = rs.getInt(1);
                // 20 个 migration 建出来的业务表远多于 15 张，低于这个数说明迁移没跑完
                assertThat(tableCount)
                        .as("Flyway 应已建出全部业务表")
                        .isGreaterThan(15);
            }

            // 抽查三张关键表：库存真账、订单、幂等记录
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                            + "AND table_name IN ('product_sku', 't_order', 'order_item')")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("product_sku / t_order / order_item 必须存在")
                        .isEqualTo(3);
            }
        }
    }
}
