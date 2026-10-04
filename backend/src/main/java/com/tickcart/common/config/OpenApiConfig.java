package com.tickcart.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 *
 * <p>按详细设计 §9 分组：{@code mall}（前台商城接口）与 {@code admin}（后台管理接口）。
 * 全局声明 Bearer 认证方案，Swagger UI 上可直接粘贴 accessToken 调测。</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI tickCartOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TickCart 秒杀商城 API")
                        .description("TickCart 前后端分离接口文档（前台 mall / 后台 admin 分组）")
                        .version("v0.1.0")
                        .contact(new Contact().name("TickCart")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /** 前台商城接口分组：/api/** 中非后台路径。 */
    @Bean
    public GroupedOpenApi mallApi() {
        return GroupedOpenApi.builder()
                .group("mall-前台商城")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/admin/**")
                .build();
    }

    /** 后台管理接口分组：/api/admin/**。 */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin-后台管理")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}
