package com.sent.sports.payment.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** 模拟退款网关：正数金额受理成功，空单号 / 非正数金额拒绝（供关单全额退款调用）。 */
class MockPayGatewayRefundTest {

    private MockPayGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockPayGateway("test-secret",
                Clock.fixed(Instant.parse("2026-11-08T06:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    @DisplayName("合法退款请求受理成功且可重复调用（确定性模拟）")
    void acceptsRefund() {
        assertThat(gateway.refund("TXN001", "SO-1", 20000L)).isTrue();
        assertThat(gateway.refund("TXN001", "SO-1", 20000L)).isTrue();
    }

    @Test
    @DisplayName("空流水号 / 空订单号 / 非正数金额一律拒绝")
    void rejectsInvalidRefund() {
        assertThat(gateway.refund("", "SO-1", 20000L)).isFalse();
        assertThat(gateway.refund("TXN001", "", 20000L)).isFalse();
        assertThat(gateway.refund("TXN001", "SO-1", 0L)).isFalse();
        assertThat(gateway.refund("TXN001", "SO-1", -1L)).isFalse();
    }
}
