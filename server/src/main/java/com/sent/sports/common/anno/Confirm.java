package com.sent.sports.common.anno;

import com.sent.sports.common.domain.ConfirmAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 危险操作确认令牌（R10）：标注在 Controller 方法上，声明「动作」与「目标 ID 的取法」。
 *
 * <p>校验由 {@code ConfirmTokenInterceptor} 在参数绑定之前统一完成：</p>
 * <ol>
 *   <li>缺 {@code X-Confirm-Token} → {@code 40302}，业务方法不会被调用；</li>
 *   <li>按 {@link #target()} 算出目标串，与换令牌时的 {@code action|targetId} 必须一致；</li>
 *   <li>{@code consume} 校验通过即焚毁令牌，防止重放。</li>
 * </ol>
 *
 * <p>为什么把目标取法写进注解：14 处复制粘贴的校验里，唯一真正不同的是「目标从哪来」
 * （批量删除取请求体 {@code ids}，关单取路径变量）。把它声明化之后，安全校验只剩一份实现，
 * 新增危险操作不会再漏掉令牌校验，也不会把 {@code BatchIds} 的归一化口径写歪。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Confirm {

    /** 危险动作：决定确认短语，并作为令牌绑定的 action 段。 */
    ConfirmAction action();

    /** 目标 ID 的取法，默认按批量删除口径取请求体 {@code ids}。 */
    Target target() default Target.BATCH_IDS;

    /** {@link Target#PATH_VARIABLE}：路径变量名；{@link Target#BODY_FIELD}：JSON 字段名。 */
    String field() default "ids";

    /** 目标 ID 的来源。 */
    enum Target {
        /**
         * 请求体 {@code ids} 数组，按 {@code BatchIds} 口径归一化（trim → 去空 → 去重 → 升序）后拼接。
         * <p>归一化必须收敛在一处：前端换令牌与后端消费对不上，就会出现「输了正确短语仍 40302」。</p>
         */
        BATCH_IDS,
        /** 路径变量，如 {@code /orders/{id}/close} 的 {@code id}。 */
        PATH_VARIABLE,
        /** 请求体 JSON 字段：数组按 {@code BatchIds} 归一化，字符串取 trim 后的原值。 */
        BODY_FIELD
    }
}
