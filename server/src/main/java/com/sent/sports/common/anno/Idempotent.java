package com.sent.sports.common.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解（P5-29，plan §7.12）。标注在下单/支付/退款/核销等写接口，
 * 与请求头 {@code Idempotency-Key} 配套：缺失或格式非法直接 40002。
 * 键规则 {@code idem:{scene}:{principalId}:{headerKey}}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 业务场景：ORDER_CREATE / PAY_CREATE / REFUND_APPLY 等。 */
    String scene();

    /** 键存活时间（秒），覆盖用户重试窗口。 */
    int ttlSeconds() default 600;

    /** 重复请求是否直接返回首次结果。 */
    boolean replayResult() default true;

    /** 处理中提示文案。 */
    String message() default "请求正在处理，请勿重复提交";
}
