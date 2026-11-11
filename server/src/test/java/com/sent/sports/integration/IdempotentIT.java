package com.sent.sports.integration;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.service.CancelOrderUseCase;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.dto.CancelOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.payment.service.CreatePaymentUseCase;
import com.sent.sports.payment.service.PayCallbackUseCase;
import com.sent.sports.payment.service.dto.CreatePaymentCmd;
import com.sent.sports.payment.service.dto.CreatePaymentResult;
import com.sent.sports.payment.service.dto.PayCallbackCmd;
import com.sent.sports.payment.service.dto.PayCallbackResult;
import com.sent.sports.support.FullStackIntegrationTest;
import com.sent.sports.support.ItDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P5-50 幂等与迟到回调集成测试：A2 / A14 与「无 Redis 也防重」。
 * <p>本实现的防重完全来自 DB 唯一索引（{@code orders.uk_order_idem}、
 * {@code stock_flow.uk_flow_biz}）和条件更新，进程内缓存只是加速，
 * 因此不存在「清空 Redis 后重复下单」的窗口——本测试即在没有 Redis 的环境里验证这一点。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class IdempotentIT extends FullStackIntegrationTest {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;
    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;
    @Autowired
    private PayCallbackUseCase payCallbackUseCase;
    @Autowired
    private CancelOrderUseCase cancelOrderUseCase;

    @Test
    @DisplayName("P5-50：同一幂等键重复提交只落一单，且不残留预占库存")
    void duplicateIdemKeyCreatesOnlyOneOrder() {
        long skuId = 9003L;
        seedSku(skuId, 10_000L, 10);

        CreateOrderResult first = createOrderUseCase.create(orderCmd("IDEM-50-DUP", skuId, 1));
        assertThat(first.status()).isEqualTo("WAIT_PAY");

        // 连续重放两次：不依赖 Redis，uk_order_idem 是唯一防线
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> createOrderUseCase.create(orderCmd("IDEM-50-DUP", skuId, 1)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_PROCESSING);
        }

        assertThat(orderCount()).isEqualTo(1L);
        assertThat(lockedQtyOf(skuId)).as("重放不得吃掉可用库存").isEqualTo(1);
        assertThat(physicalQtyOf(skuId) - lockedQtyOf(skuId)).isEqualTo(9);
        assertThat(stockFlowCount(skuId, "LOCK")).as("成功下单必须记库存流水").isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("A2：已取消订单的迟到支付生成仅退款，订单不复活")
    void lateCallbackOnCancelledOrderKeepsOrderCancelled() {
        long skuId = 9004L;
        seedSku(skuId, 10_000L, 10);

        CreateOrderResult order = createOrderUseCase.create(orderCmd("IDEM-50-LATE", skuId, 1));
        cancelOrderUseCase.cancel(new CancelOrderCmd(order.orderNo()));
        assertThat(statusOf(order.orderNo())).isEqualTo("CANCELLED");
        long versionAfterCancel = versionOf(order.orderNo());

        PayCallbackResult result = payCallbackUseCase.handle(new PayCallbackCmd(
                "TXN-LATE-50-B", order.orderNo(), 10_000L, "MOCK", "CH-LATE",
                System.currentTimeMillis()));

        assertThat(result.status()).isEqualTo("REFUNDED");
        assertThat(statusOf(order.orderNo())).isEqualTo("CANCELLED");
        assertThat(payStatusOf(order.orderNo())).isEqualTo("UNPAID");
        assertThat(versionOf(order.orderNo())).as("迟到回调不得推进订单状态机").isEqualTo(versionAfterCancel);
        assertThat(lockedQtyOf(skuId)).as("取消时已释放预占").isEqualTo(0);
    }

    @Test
    @DisplayName("A14：重复/迟到回调不重复入账，订单只迁移一次")
    void repeatedCallbackDoesNotDoublePost() {
        long skuId = 9005L;
        seedSku(skuId, 10_000L, 10);

        CreateOrderResult order = createOrderUseCase.create(orderCmd("IDEM-50-CALLBACK", skuId, 1));
        CreatePaymentResult payment = createPaymentUseCase.create(new CreatePaymentCmd(
                order.orderNo(), 10_000L, "MOCK", "PAY-IDEM-50-C"));
        PayCallbackCmd callback = new PayCallbackCmd(payment.txnNo(), order.orderNo(), 10_000L,
                "MOCK", "CH-50-C", System.currentTimeMillis());

        assertThat(payCallbackUseCase.handle(callback).status()).isEqualTo("SUCCESS");
        assertThat(payCallbackUseCase.handle(callback).status()).isEqualTo("ALREADY_PAID");
        assertThat(payCallbackUseCase.handle(callback).status()).isEqualTo("ALREADY_PAID");

        assertThat(statusOf(order.orderNo())).isEqualTo("WAIT_SHIP");
        assertThat(payStatusOf(order.orderNo())).isEqualTo("PAID");
        assertThat(versionOf(order.orderNo())).as("只应迁移一次（无重复入账）").isEqualTo(1L);
        assertThat(orderCount()).isEqualTo(1L);
    }
}
