package com.sent.sports.common.web;

import com.sent.sports.common.web.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 骨架健康检查端点：返回 traceId，用于验证过滤器链路打通（plan T-01）。
 */
@RestController
@Tag(name = "系统", description = "健康检查")
public class PingController {

    @GetMapping("/api/ping")
    @Operation(summary = "健康检查", description = "返回当前请求的 traceId")
    public R<String> ping() {
        return R.ok(MDC.get("traceId"));
    }
}
