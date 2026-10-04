package com.tickcart.common.controller;

import com.tickcart.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查接口。
 *
 * <p>用于确认应用已启动、统一响应结构可用，同时作为前后端联调的最早探针。</p>
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "服务健康探针")
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        return R.ok(Map.of(
                "status", "UP",
                "time", LocalDateTime.now().toString()
        ));
    }
}
