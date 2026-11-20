package com.sent.sports.order;

import com.sent.sports.order.dto.OrderDtos;
import com.sent.sports.order.dto.OrderVOAssembler;
import com.sent.sports.order.mapper.ShipmentMapper;
import com.sent.sports.order.mapper.po.OrderItemListRow;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.payment.service.dto.PaymentSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * R13（Assembler + Builder）红→绿用例：订单 VO 的装配集中到唯一入口，
 * 调用方用命名方法而非 27 个位置参数，字段错位不再靠数数发现。
 */
class OrderVOAssemblerTest {

    private static OrderPO po() {
        OrderPO po = new OrderPO();
        po.setOrderNo("SO2026");
        po.setStatus("WAIT_RECEIVE");
        po.setPayStatus("PAID");
        po.setAfterSaleStatus("NONE");
        po.setPayableCent(20000L);
        po.setGoodsAmountCent(19000L);
        po.setFreightCent(1000L);
        po.setPointDeductCent(500L);
        po.setDiscountCent(300L);
        po.setCustomFeeCent(200L);
        po.setAddressSnapshotJson("{\"receiver\":\"张三\"}");
        po.setCreatedAt(LocalDateTime.of(2026, 11, 10, 9, 30));
        po.setPaidAt(LocalDateTime.of(2026, 11, 10, 9, 35));
        po.setExpireAt(LocalDateTime.of(2026, 11, 10, 9, 45));
        return po;
    }

    private static OrderItemListRow item() {
        OrderItemListRow row = new OrderItemListRow();
        row.setSkuId(9209L);
        row.setProductId(1001L);
        row.setTitle("比赛篮球");
        row.setSpec("7号");
        row.setQty(2);
        row.setUnitPriceCent(9500L);
        row.setLinePaidCent(19000L);
        return row;
    }

    @Test
    @DisplayName("详情装配：PO + 商品行 + 物流 + 支付摘要一次成型，金额分+元、时间毫秒")
    void fromDetailMapsAllSources() {
        ShipmentMapper.ShipmentRow shipment = new ShipmentMapper.ShipmentRow();
        shipment.setCarrier("顺丰速运");
        shipment.setTrackingNo("SF123");
        shipment.setShippedAt(LocalDateTime.of(2026, 11, 11, 8, 30));
        PaymentSummaryDto payment = new PaymentSummaryDto("SO2026", "ALIPAY", "TXN1", "TRADE1", "PAID");

        OrderDtos.OrderVO vo = OrderVOAssembler.fromDetail(po(), List.of(item()), shipment, payment);

        assertEquals("SO2026", vo.orderNo());
        assertEquals("WAIT_RECEIVE", vo.status());
        assertEquals("PAID", vo.payStatus());
        assertEquals("NONE", vo.refundStatus());
        assertEquals(20000L, vo.payableCent());
        assertEquals("200.00", vo.payableYuan());
        assertEquals("190.00", vo.goodsAmountYuan());
        assertEquals("10.00", vo.freightYuan());
        assertEquals("5.00", vo.pointDeductYuan());
        assertEquals(300L, vo.discountCent());
        assertEquals(200L, vo.customFeeCent());
        assertEquals("{\"receiver\":\"张三\"}", vo.addressSnapshotJson());
        assertEquals("顺丰速运", vo.carrier());
        assertEquals("SF123", vo.trackingNo());
        assertEquals("ALIPAY", vo.payChannel());
        assertEquals("TXN1", vo.payTxnNo());
        assertEquals("TRADE1", vo.tradeNo());
        assertEquals(LocalDateTime.of(2026, 11, 11, 8, 30)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), vo.shippedAtMillis());
        assertEquals(LocalDateTime.of(2026, 11, 10, 9, 45)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), vo.expireAtMillis());
        assertEquals(1, vo.items().size());
        assertEquals("9209", vo.items().get(0).skuId());
        assertEquals("1001", vo.items().get(0).productId());
        assertEquals("190.00", vo.items().get(0).linePaidYuan());
    }

    @Test
    @DisplayName("无物流/无支付流水：对应字段为 null，不产生 1970 时间戳或空串")
    void fromDetailKeepsNullsForAbsentSources() {
        OrderDtos.OrderVO vo = OrderVOAssembler.fromDetail(po(), List.of(), null, null);

        assertNull(vo.carrier());
        assertNull(vo.trackingNo());
        assertNull(vo.shippedAtMillis());
        assertNull(vo.payChannel());
        assertNull(vo.payTxnNo());
        assertNull(vo.tradeNo());
        assertNull(vo.completedAt());
        assertEquals(List.of(), vo.items());
    }

    @Test
    @DisplayName("Builder 供下单/确认收货构造精简 VO：命名赋值，不靠数 13 个 null")
    void builderBuildsCompactVo() {
        OrderDtos.OrderVO vo = OrderVOAssembler.builder()
                .orderNo("SO7")
                .status("WAIT_PAY")
                .payable(5990L)
                .freight(0L)
                .build();

        assertEquals("SO7", vo.orderNo());
        assertEquals("WAIT_PAY", vo.status());
        assertEquals(5990L, vo.payableCent());
        assertEquals("59.90", vo.payableYuan());
        assertEquals(0L, vo.freightCent());
        assertNull(vo.payChannel(), "未设置的字段保持 null，@JsonInclude 会折叠掉");
        assertEquals(List.of(), vo.items());
    }
}
