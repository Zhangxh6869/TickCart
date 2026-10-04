package com.tickcart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TickCart 秒杀商城后端启动类。
 *
 * <p>单体应用 + 模块化包结构（auth/product/member/cart/trade/seckill/coupon/admin），
 * 详见《TickCart详细设计-架构与模块.md》§5。</p>
 */
@SpringBootApplication
public class TickCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(TickCartApplication.class, args);
    }
}
