package com.sent.sports.common.error;

import java.util.Arrays;

/**
 * 错误码枚举，覆盖 plan §8.2 全部 19 个码。
 * code 为业务错误码，httpStatus 为对应的 HTTP 状态码。
 */
public enum ErrorCode {

    SUCCESS(0, 200, "成功"),

    PARAM_INVALID(40001, 400, "参数校验失败"),
    IDEMPOTENCY_KEY_MISSING(40002, 400, "缺少 Idempotency-Key"),

    UNAUTHENTICATED(40101, 401, "未登录（无令牌）"),
    TOKEN_EXPIRED(40102, 401, "令牌已过期（触发刷新流程）"),
    REFRESH_TOKEN_INVALID(40103, 401, "刷新令牌无效/已被使用（需重新登录）"),

    FORBIDDEN(40301, 403, "无权限"),
    CONFIRM_TOKEN_INVALID(40302, 403, "确认令牌缺失/失效/与操作不匹配"),

    NOT_FOUND(40401, 404, "资源不存在"),

    STATE_CONFLICT(40901, 409, "状态冲突（当前状态不允许该操作）"),
    DUPLICATE_PROCESSING(40902, 409, "重复请求正在处理中"),
    STOCK_NOT_ENOUGH(40910, 409, "库存不足"),
    BELOW_FLOOR_PRICE(40920, 409, "低于底价"),
    WRITE_OFF_OVERFLOW(40930, 409, "核销金额超额"),
    REFUND_OVERFLOW(40940, 409, "超出可退金额"),
    CREDIT_NOT_ENOUGH(40950, 409, "信用额度不足"),
    PRODUCT_OFF_SHELF(40960, 409, "商品已下架"),

    BIZ_REJECT(42201, 422, "业务校验失败"),

    TOO_FREQUENT(42901, 429, "操作过频"),

    SYSTEM_ERROR(50001, 500, "系统异常");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public static ErrorCode of(int code) {
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElse(SYSTEM_ERROR);
    }
}
