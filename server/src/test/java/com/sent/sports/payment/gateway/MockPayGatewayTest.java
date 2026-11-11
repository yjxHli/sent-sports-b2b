package com.sent.sports.payment.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 模拟支付网关单测（P5-33）：签名可复现、篡改被拒、缺签被拒。 */
class MockPayGatewayTest {

    private MockPayGateway gateway;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        gateway = new MockPayGateway("test-secret", fixed);
    }

    @Test
    @DisplayName("相同入参产生相同签名（可复现）")
    void signIsDeterministic() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderNo", "O1001");
        params.put("amountCent", "19900");
        params.put("txnNo", "T0001");

        String first = gateway.sign(params);
        String second = gateway.sign(params);

        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    @DisplayName("参数顺序不影响签名（内部按 key 排序）")
    void signIgnoresParameterOrder() {
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put("amountCent", "19900");
        ordered.put("orderNo", "O1001");
        Map<String, String> shuffled = new LinkedHashMap<>();
        shuffled.put("orderNo", "O1001");
        shuffled.put("amountCent", "19900");

        assertThat(gateway.sign(shuffled)).isEqualTo(gateway.sign(ordered));
    }

    @Test
    @DisplayName("自发回调可验签通过")
    void ownCallbackPassesVerification() {
        Map<String, String> callback = gateway.buildCallback("T0001", "O1001", 19900L, true);

        assertThat(callback.get("status")).isEqualTo("PAID");
        assertThat(gateway.verify(callback)).isTrue();
    }

    @Test
    @DisplayName("金额被篡改则验签失败")
    void tamperedAmountFailsVerification() {
        Map<String, String> callback = new HashMap<>(gateway.buildCallback("T0001", "O1001", 19900L, true));
        callback.put("amountCent", "1");

        assertThat(gateway.verify(callback)).isFalse();
    }

    @Test
    @DisplayName("缺少签名或空报文一律拒绝")
    void missingSignatureIsRejected() {
        Map<String, String> noSign = new HashMap<>(gateway.buildCallback("T0001", "O1001", 19900L, true));
        noSign.remove("sign");

        assertThat(gateway.verify(noSign)).isFalse();
        assertThat(gateway.verify(Map.of())).isFalse();
        assertThat(gateway.verify(null)).isFalse();
        assertThatThrownBy(() -> new MockPayGateway(" ", Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("发起支付返回通道交易号与可复现报文")
    void createReturnsTicket() {
        PayGateway.PayTicket ticket = gateway.create("T0001", "O1001", 19900L, "MOCK");

        assertThat(ticket.txnNo()).isEqualTo("T0001");
        assertThat(ticket.sign()).hasSize(64);
        assertThat(ticket.payload()).contains("\"orderNo\":\"O1001\"", "\"amountCent\":\"19900\"");
        assertThat(gateway.create("T0001", "O1001", 19900L, "MOCK").sign()).isEqualTo(ticket.sign());
    }
}
