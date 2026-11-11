package com.sent.sports.common.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * 危险操作确认令牌 Web 层 DTO（P4-57，置于 common 以让 web.admin 控制器零业务依赖）。
 * 换取令牌请求携带动作 + 目标 + 确认短语；响应返回一次性令牌与过期时间（毫秒）。
 */
public final class ConfirmDtos {

    private ConfirmDtos() {
    }

    /** 换取确认令牌请求。 */
    public record ConfirmCmd(
            @NotBlank(message = "操作类型不能为空") String action,
            String targetId,
            @NotBlank(message = "确认短语不能为空") String confirmText) {
    }

    /** 确认令牌响应：一次性令牌 + 过期时间戳（epoch millis）。 */
    public record TokenVO(String token, long expireAt) {
    }
}
