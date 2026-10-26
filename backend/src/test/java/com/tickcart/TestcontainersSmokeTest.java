package com.tickcart;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers 通路冒烟测试。
 *
 * <p>只验证三件事：Docker daemon 可访问、容器能起来、容器内 MySQL 可执行 SQL。
 * 不加载 Spring 上下文，目的是用最小成本确认本机（Windows + Docker Desktop）能否跑通
 * 集成测试方案；跑通后再逐个补 Redis / RabbitMQ 与真实业务场景。
 */
@Testcontainers
class TestcontainersSmokeTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("tickcart_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    void containerStartsAndAcceptsSql() throws Exception {
        assertThat(MYSQL.isRunning()).isTrue();

        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE smoke (id INT PRIMARY KEY, name VARCHAR(32))");
            stmt.execute("INSERT INTO smoke VALUES (1, 'tickcart')");

            try (ResultSet rs = stmt.executeQuery("SELECT name FROM smoke WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("tickcart");
            }
        }
    }
}
