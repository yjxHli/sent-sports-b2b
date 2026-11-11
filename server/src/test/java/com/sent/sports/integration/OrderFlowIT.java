package com.sent.sports.integration;

import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.ShipOrderUseCase;
import com.sent.sports.order.service.dto.ConfirmReceiptCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.order.service.dto.ReceiveLineCmd;
import com.sent.sports.order.service.dto.ShipLineCmd;
import com.sent.sports.order.service.dto.ShipOrderCmd;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5-48 下单支付全链路集成测试：下单 → 发起支付 → 渠道回调 → 发货 → 确认收货。
 * <p>用完整 Spring 上下文 + 真实 MySQL 跑用例（含 AOP 事务切面），而不是 MockMvc + Mock 门面，
 * 因此能证明「用例 Bean 装配、真库读写入账、状态机迁移、库存流水」串起来确实可用。
 */
@EnabledIf("com.sent.sports.support.ItDatabase#available")
class OrderFlowIT extends FullStackIntegrationTest {

    private static final long SKU_ID = 9001L;
    private static final long RETAIL_CENT = 10_000L;
    private static final int QTY = 2;
    private static final long PAYABLE_CENT = RETAIL_CENT * QTY;

    @Autowired
    private CreateOrderUseCase createOrderUseCase;
    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;
    @Autowired
    private PayCallbackUseCase payCallbackUseCase;
    @Autowired
    private ShipOrderUseCase shipOrderUseCase;
    @Autowired
    private ConfirmReceiptUseCase confirmReceiptUseCase;

    @Test
    @DisplayName("P5-48：下单 → 支付 → 回调 → 发货 → 收货 全绿")
    void orderPayCallbackShipReceive() {
        seedSku(SKU_ID, RETAIL_CENT, 10);

        // 1) 下单：取价来自 sku 真库，库存条件预占，订单头 + 订单行落库
        CreateOrderResult order = createOrderUseCase.create(orderCmd("IDEM-FLOW-48", SKU_ID, QTY));
        assertThat(order.status()).isEqualTo("WAIT_PAY");
        assertThat(order.payableCent()).isEqualTo(PAYABLE_CENT);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_item WHERE order_id = "
                        + "(SELECT id FROM orders WHERE order_no = ?)", Integer.class, order.orderNo()))
                .isEqualTo(1);

        // 2) 发起支付：生成 CREATED 流水
        CreatePaymentResult payment = createPaymentUseCase.create(new CreatePaymentCmd(
                order.orderNo(), PAYABLE_CENT, "MOCK", "PAY-IDEM-48"));
        assertThat(payment.status()).isEqualTo("CREATED");

        // 3) 渠道回调：CREATED 流水继续落账，订单迁移 WAIT_SHIP + PAID
        PayCallbackResult callback = payCallbackUseCase.handle(new PayCallbackCmd(
                payment.txnNo(), order.orderNo(), PAYABLE_CENT, "MOCK", "CH-48",
                System.currentTimeMillis()));
        assertThat(callback.status()).isEqualTo("SUCCESS");
        assertThat(statusOf(order.orderNo())).isEqualTo("WAIT_SHIP");
        assertThat(payStatusOf(order.orderNo())).isEqualTo("PAID");

        // 4) 发货：整单发出 → WAIT_RECEIVE
        assertThat(shipOrderUseCase.ship(new ShipOrderCmd(order.orderNo(), "SF", "TRACK-48",
                List.of(new ShipLineCmd(SKU_ID, QTY)))).status()).isEqualTo("WAIT_RECEIVE");
        assertThat(jdbc.queryForObject("SELECT shipped_qty FROM order_item WHERE sku_id = ?",
                Integer.class, SKU_ID)).isEqualTo(QTY);

        // 5) 确认收货：全部收齐 → COMPLETED
        assertThat(confirmReceiptUseCase.confirm(new ConfirmReceiptCmd(order.orderNo(),
                List.of(new ReceiveLineCmd(SKU_ID, QTY)))).status()).isEqualTo("COMPLETED");

        assertThat(statusOf(order.orderNo())).isEqualTo("COMPLETED");
        assertThat(payStatusOf(order.orderNo())).isEqualTo("PAID");
        assertThat(versionOf(order.orderNo())).as("支付/发货/收货各推进一次乐观锁").isEqualTo(3L);
        assertThat(lockedQtyOf(SKU_ID)).as("预占量在整条链路中保持不变").isEqualTo(QTY);
        assertThat(stockFlowCount(SKU_ID, "LOCK")).isGreaterThanOrEqualTo(1)
                .as("用户要求：库存变动必须记流水");
        assertThat(orderCount()).isEqualTo(1L);
    }
}
