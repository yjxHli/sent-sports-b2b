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

    /**
     * 敏感词风控（2026-11-19）：昵称/登录名直接拒绝设置，聊天消息直接阻断不打码。
     * <p>昵称类沿用需求给定的 400——它属于「请求本身不合法」，前端拿到文案原样弹窗即可；
     * 消息类是独立业务码 40003，前端据此保留输入框内容让用户自己改。</p>
     */
    SENSITIVE_NICKNAME(400, 400, "昵称/用户名包含违禁词，请修改后重试"),
    SENSITIVE_MESSAGE(40003, 400, "发送失败：消息包含违禁词，请文明发言"),

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
    /**
     * 支付回调扣抵扣积分时余额不足（典型成因：并发把同一批积分花在了别的订单上）。
     * <p>必须让回调失败：钱照收、积分白送的口子比「支付失败让用户重试」危险得多。</p>
     */
    POINT_NOT_ENOUGH(40970, 409, "积分余额不足"),
    /**
     * 定制商品已进入生产链路（生产中 / 已完工 / 已发货），退款申请绝对拦截。
     * <p>与通用 {@link #STATE_CONFLICT} 分开：用户端要据此弹「定制商品已开始生产，无法申请退款」，
     * 而不是一句没有下文的「状态冲突」。</p>
     */
    CUSTOM_REFUND_NOT_ALLOWED(40980, 409, "定制商品已开始生产，不可退款"),

    BIZ_REJECT(42201, 422, "业务校验失败"),
    /** 登录时手机号不在客户库：前端据此弹提示并跳注册页回填手机号。 */
    PHONE_NOT_REGISTERED(42202, 422, "该手机号尚未注册，请先注册"),
    /** 验证码错误 / 过期 / 已被消费。 */
    SMS_CODE_INVALID(42203, 422, "验证码错误或已过期，请重新获取"),

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
