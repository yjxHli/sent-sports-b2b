package com.sent.sports.common.web;

import com.sent.sports.auth.controller.AuthController;
import com.sent.sports.common.anno.Idempotent;
import com.sent.sports.common.anno.RateLimit;
import com.sent.sports.file.controller.FileController;
import com.sent.sports.finance.controller.admin.AdminFinanceController;
import com.sent.sports.order.controller.OrderController;
import com.sent.sports.order.controller.admin.AdminOrderController;
import com.sent.sports.payment.controller.PaymentController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 横切能力接入点守护测试（2026-11-28 批次 2 / P1-01、P1-02）。
 *
 * <p>这两套能力此前是「建好了但没接线」：{@code @Idempotent} / {@code @RateLimit} 在
 * {@code src/main} 里 0 处使用，前端发的 {@code Idempotency-Key} 被直接忽略。
 * 本测试把「哪些端点必须挂上」写成断言——以后有人删注解会立刻红，而不是等到线上重复发货。</p>
 */
class CrossCuttingCoverageTest {

    private static Method methodOf(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(type.getSimpleName() + " 缺少方法 " + name));
    }

    @Test
    @DisplayName("P1-01 幂等：下单 / 支付 / 发货 / 登记回款四个写入口必须挂 @Idempotent")
    void writeEndpointsAreIdempotent() {
        assertThat(methodOf(OrderController.class, "create").getAnnotation(Idempotent.class))
                .as("下单：前端 checkout 已在发 Idempotency-Key，服务端必须消费")
                .isNotNull();
        assertThat(methodOf(PaymentController.class, "create").getAnnotation(Idempotent.class))
                .as("发起支付：重复点击不能生成两笔流水")
                .isNotNull();
        assertThat(methodOf(AdminOrderController.class, "ship").getAnnotation(Idempotent.class))
                .as("发货：重复提交不能生成两个包裹")
                .isNotNull();
        assertThat(methodOf(AdminFinanceController.class, "record").getAnnotation(Idempotent.class))
                .as("登记回款：重复提交不能记两笔钱")
                .isNotNull();
    }

    @Test
    @DisplayName("P1-02 限流：登录 / 发码 / 下单 / 上传四个端点必须挂 @RateLimit")
    void sensitiveEndpointsAreRateLimited() {
        assertThat(methodOf(AuthController.class, "login").getAnnotation(RateLimit.class))
                .as("密码登录：需要按来源限速，配合账号失败锁定")
                .isNotNull();
        assertThat(methodOf(AuthController.class, "sendSmsCode").getAnnotation(RateLimit.class))
                .as("短信验证码：防轰炸")
                .isNotNull();
        assertThat(methodOf(OrderController.class, "create").getAnnotation(RateLimit.class))
                .as("下单：防脚本刷单")
                .isNotNull();
        assertThat(methodOf(FileController.class, "upload").getAnnotation(RateLimit.class))
                .as("文件上传：防磁盘打满")
                .isNotNull();
    }

    @Test
    @DisplayName("限流参数必须显式且为正值，避免「注解了但等于没限」")
    void rateLimitsHavePositiveValues() {
        for (Method method : new Method[]{
                methodOf(AuthController.class, "login"),
                methodOf(AuthController.class, "sendSmsCode"),
                methodOf(OrderController.class, "create"),
                methodOf(FileController.class, "upload")}) {
            RateLimit limit = method.getAnnotation(RateLimit.class);
            assertThat(limit.limit()).as(method.getName() + " 的 limit").isPositive();
            assertThat(limit.windowSeconds()).as(method.getName() + " 的窗口").isPositive();
        }
    }
}
