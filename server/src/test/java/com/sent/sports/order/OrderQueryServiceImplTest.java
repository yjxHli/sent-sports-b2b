package com.sent.sports.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.order.dto.OrderDtos;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.ShipmentMapper;
import com.sent.sports.order.mapper.po.OrderItemListRow;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.impl.OrderQueryServiceImpl;
import com.sent.sports.payment.service.PaymentQueryService;
import com.sent.sports.payment.service.dto.PaymentSummaryDto;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 我的订单列表红→绿用例（淘宝式多状态订单页的数据支撑）。
 *
 * <p>三条口径：</p>
 * <ul>
 *   <li><b>状态筛选下推</b>：状态必须交给 SQL 过滤，不能取出全量在前端筛（否则分页条数会错）；</li>
 *   <li><b>商品行</b>：卡片要显示「图 / 标题 / 规格 / 单价 × 数量」，必须随列表一次返回，避免 N+1；</li>
 *   <li><b>下单时间</b>：按系统时区转毫秒给前端（{@code JacksonConfig} 会把 Long 序列化成字符串）。</li>
 * </ul>
 */
class OrderQueryServiceImplTest extends BaseUnitTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ShipmentMapper shipmentMapper;

    @Mock
    private PaymentQueryService paymentQueryService;

    private OrderQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderQueryServiceImpl(orderRepository, orderMapper, shipmentMapper, paymentQueryService);
    }

    private PageQuery pageQuery() {
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(10);
        return query;
    }

    private OrderPO order(long id, String status, long payableCent) {
        OrderPO po = new OrderPO();
        po.setId(id);
        po.setOrderNo("SO" + id);
        po.setCustomerId(9002L);
        po.setStatus(status);
        po.setPayStatus("UNPAID");
        po.setPayableCent(payableCent);
        po.setCreatedAt(LocalDateTime.of(2026, 11, 10, 9, 30));
        return po;
    }

    private OrderItemListRow item(long orderId, int qty, long unitPriceCent) {
        OrderItemListRow po = new OrderItemListRow();
        po.setOrderId(orderId);
        po.setSkuId(9209L);
        po.setProductId(1001L);
        po.setTitle("专业比赛篮球 标准款");
        po.setSpec("7号");
        po.setQty(qty);
        po.setUnitPriceCent(unitPriceCent);
        po.setLinePaidCent(unitPriceCent * qty);
        return po;
    }

    private Page<OrderPO> pageOf(List<OrderPO> records, long total) {
        Page<OrderPO> page = new Page<>(1, 10);
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }

    @Test
    @DisplayName("状态筛选下推到 SQL，不会全量拉取后前端筛")
    void statusPushedDown() {
        when(orderMapper.selectMyPage(any(), eq(9002L), eq("WAIT_SHIP"), eq((String) null)))
                .thenReturn(pageOf(List.of(order(1L, "WAIT_SHIP", 12345L)), 1L));
        when(orderMapper.selectItemsByOrderIds(any())).thenReturn(List.of(item(1L, 2, 5000L)));

        var result = service.listMyOrders(9002L, "WAIT_SHIP", null, pageQuery());

        verify(orderMapper).selectMyPage(any(), eq(9002L), eq("WAIT_SHIP"), eq((String) null));
        assertEquals(1, result.getList().size());
        assertEquals("WAIT_SHIP", result.getList().get(0).status());
    }

    @Test
    @DisplayName("列表项带下单时间（毫秒）与商品行，且商品行按订单归组")
    void listItemCarriesCreatedAtAndItems() {
        when(orderMapper.selectMyPage(any(), eq(9002L), eq((String) null), eq((String) null)))
                .thenReturn(pageOf(List.of(order(1L, "WAIT_PAY", 12345L), order(2L, "COMPLETED", 8800L)), 2L));
        when(orderMapper.selectItemsByOrderIds(any()))
                .thenReturn(List.of(item(1L, 2, 5000L), item(1L, 1, 2345L), item(2L, 3, 2900L)));

        var result = service.listMyOrders(9002L, null, null, pageQuery());

        long expectedMillis = LocalDateTime.of(2026, 11, 10, 9, 30)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        OrderDtos.OrderListItem first = result.getList().get(0);
        assertEquals(expectedMillis, first.createdAt());
        assertEquals(2, first.items().size(), "第一单应有 2 个商品行");
        assertEquals("专业比赛篮球 标准款", first.items().get(0).title());
        assertEquals(2, first.items().get(0).qty());
        assertEquals(5000L, first.items().get(0).unitPriceCent());

        OrderDtos.OrderListItem second = result.getList().get(1);
        assertEquals(1, second.items().size(), "商品行不得串单");
        assertEquals(3, second.items().get(0).qty());
    }

    @Test
    @DisplayName("没有订单时不查商品行（避免 IN () 空集）")
    void emptyListSkipsItemsQuery() {
        when(orderMapper.selectMyPage(any(), eq(9002L), eq((String) null), eq((String) null)))
                .thenReturn(pageOf(List.of(), 0L));

        var result = service.listMyOrders(9002L, null, null, pageQuery());

        assertTrue(result.getList().isEmpty());
        verify(orderMapper, never()).selectItemsByOrderIds(any());
    }

    @Test
    @DisplayName("匿名（无 customerId）直接返回空页，不查库")
    void anonymousReturnsEmpty() {
        var result = service.listMyOrders(null, null, null, pageQuery());

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        verify(orderMapper, never()).selectMyPage(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("搜索词下推到 SQL：trim 后透传给 Mapper，状态筛选互不影响")
    void keywordPushedDown() {
        when(orderMapper.selectMyPage(any(), eq(9002L), eq((String) null), eq("SO2026")))
                .thenReturn(pageOf(List.of(order(1L, "WAIT_SHIP", 12345L)), 1L));
        when(orderMapper.selectItemsByOrderIds(any())).thenReturn(List.of());

        var result = service.listMyOrders(9002L, null, "  SO2026  ", pageQuery());

        verify(orderMapper).selectMyPage(any(), eq(9002L), eq((String) null), eq("SO2026"));
        assertEquals(1, result.getList().size());
        assertEquals("SO1", result.getList().get(0).orderNo());
    }

    @Test
    @DisplayName("空白搜索词归一为 null，不当成「匹配空订单号」")
    void blankKeywordNormalizedToNull() {
        when(orderMapper.selectMyPage(any(), eq(9002L), eq((String) null), eq((String) null)))
                .thenReturn(pageOf(List.of(order(1L, "WAIT_SHIP", 12345L)), 1L));
        when(orderMapper.selectItemsByOrderIds(any())).thenReturn(List.of());

        service.listMyOrders(9002L, null, "   ", pageQuery());

        verify(orderMapper).selectMyPage(any(), eq(9002L), eq((String) null), eq((String) null));
    }

    @Test
    @DisplayName("订单详情必须带商品行：退款要逐行 skuId + 行实付，否则前端只能传 skuId=0 被拒")
    void detailCarriesRefundableItems() {
        when(orderMapper.selectByOrderNo("SO1")).thenReturn(order(1L, "WAIT_SHIP", 20000L));
        when(orderMapper.selectItemsByOrderIds(List.of(1L))).thenReturn(List.of(item(1L, 2, 5000L)));

        OrderDtos.OrderVO vo = service.getDetail("SO1");

        assertEquals(1, vo.items().size(), "详情应返回商品行");
        assertEquals("9209", vo.items().get(0).skuId());
        assertEquals("1001", vo.items().get(0).productId(), "商品行要带 SPU ID，否则点商品名跳不了详情");
        assertEquals(2, vo.items().get(0).qty());
        assertEquals(10000L, vo.items().get(0).linePaidCent(), "行实付 = 行金额 − 分摊优惠");
        assertEquals("100.00", vo.items().get(0).linePaidYuan());
    }

    @Test
    @DisplayName("订单详情带付款/成交时间与最近一次物流，供右侧「订单信息」展示")
    void detailCarriesTimelineAndShipment() {
        OrderPO po = order(3L, "WAIT_RECEIVE", 20000L);
        po.setPaidAt(LocalDateTime.of(2026, 11, 11, 10, 0));
        po.setCompletedAt(null);
        po.setDiscountCent(500L);
        po.setAddressSnapshotJson("{\"receiver\":\"验收客户\"}");
        when(orderMapper.selectByOrderNo("SO3")).thenReturn(po);
        when(orderMapper.selectItemsByOrderIds(List.of(3L))).thenReturn(List.of(item(3L, 1, 20000L)));

        ShipmentMapper.ShipmentRow shipment = new ShipmentMapper.ShipmentRow();
        shipment.setCarrier("顺丰速运");
        shipment.setTrackingNo("SF1234567890");
        shipment.setShippedAt(LocalDateTime.of(2026, 11, 12, 8, 30));
        when(shipmentMapper.selectLatestByOrderId(3L)).thenReturn(shipment);

        OrderDtos.OrderVO vo = service.getDetail("SO3");

        assertEquals(
                LocalDateTime.of(2026, 11, 11, 10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                vo.paidAt(),
                "付款时间要转成毫秒给前端");
        assertEquals(
                LocalDateTime.of(2026, 11, 12, 8, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                vo.shippedAtMillis());
        assertEquals("顺丰速运", vo.carrier());
        assertEquals("SF1234567890", vo.trackingNo());
        assertEquals(500L, vo.discountCent());
        assertEquals("{\"receiver\":\"验收客户\"}", vo.addressSnapshotJson());
        org.junit.jupiter.api.Assertions.assertNull(vo.completedAt(), "未成交时不给 1970 时间戳");
    }

    @Test
    @DisplayName("订单详情没有商品行时不查空集，items 返回空列表而不是 null")
    void detailWithoutItemsReturnsEmptyList() {
        when(orderMapper.selectByOrderNo("SO2")).thenReturn(order(2L, "COMPLETED", 100L));
        when(orderMapper.selectItemsByOrderIds(List.of(2L))).thenReturn(List.of());

        OrderDtos.OrderVO vo = service.getDetail("SO2");

        assertTrue(vo.items().isEmpty());
    }

    @Test
    @DisplayName("订单详情带支付方式与交易号：右栏「订单信息」要写清这笔钱是怎么付的")
    void detailCarriesPaymentSummary() {
        OrderPO po = order(7L, "COMPLETED", 5990L);
        po.setPayStatus("PAID");
        when(orderMapper.selectByOrderNo("SO7")).thenReturn(po);
        when(orderMapper.selectItemsByOrderIds(List.of(7L))).thenReturn(List.of());
        when(paymentQueryService.findSummary("SO7")).thenReturn(java.util.Optional.of(
                new PaymentSummaryDto("SO7", "ALIPAY", "TXN202611150001", "2026111522001440071234567890", "PAID")));

        OrderDtos.OrderVO vo = service.getDetail("SO7");

        assertEquals("ALIPAY", vo.payChannel());
        assertEquals("TXN202611150001", vo.payTxnNo(), "平台流水号");
        assertEquals("2026111522001440071234567890", vo.tradeNo(), "渠道交易号");
    }

    @Test
    @DisplayName("未付款 / 无支付流水：支付三字段返回 null，前端显示「未支付」而不是报错")
    void detailWithoutPaymentSummaryKeepsNulls() {
        when(orderMapper.selectByOrderNo("SO8")).thenReturn(order(8L, "WAIT_PAY", 1000L));
        when(orderMapper.selectItemsByOrderIds(List.of(8L))).thenReturn(List.of());
        when(paymentQueryService.findSummary("SO8")).thenReturn(java.util.Optional.empty());

        OrderDtos.OrderVO vo = service.getDetail("SO8");

        org.junit.jupiter.api.Assertions.assertNull(vo.payChannel());
        org.junit.jupiter.api.Assertions.assertNull(vo.payTxnNo());
        org.junit.jupiter.api.Assertions.assertNull(vo.tradeNo());
    }

    /**
     * 待付款订单必须把「支付截止时间」回给商城端，否则前端做不出 5 分钟实时倒计时
     * （用户要求：结算时可选择暂不支付，5 分钟内未付自动取消并显示倒计时）。
     */
    @Test
    @DisplayName("订单详情回传支付截止时间（毫秒）：商城端 5 分钟倒计时据此计算")
    void detailCarriesPayExpireAtMillis() {
        OrderPO po = order(9L, "WAIT_PAY", 2590L);
        LocalDateTime expireAt = LocalDateTime.of(2026, 11, 17, 12, 5, 0);
        po.setExpireAt(expireAt);
        when(orderMapper.selectByOrderNo("SO9")).thenReturn(po);
        when(orderMapper.selectItemsByOrderIds(List.of(9L))).thenReturn(List.of());

        OrderDtos.OrderVO vo = service.getDetail("SO9");

        assertEquals(
                expireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                vo.expireAtMillis(),
                "支付截止时间要按毫秒回给前端");
    }

    /** 已付款 / 历史单没有截止时间时返回 null，前端据此不渲染倒计时。 */
    @Test
    @DisplayName("没有支付截止时间的订单：expireAtMillis 返回 null，前端不显示倒计时")
    void detailWithoutExpireAtKeepsNull() {
        when(orderMapper.selectByOrderNo("SO10")).thenReturn(order(10L, "COMPLETED", 100L));
        when(orderMapper.selectItemsByOrderIds(List.of(10L))).thenReturn(List.of());

        OrderDtos.OrderVO vo = service.getDetail("SO10");

        org.junit.jupiter.api.Assertions.assertNull(vo.expireAtMillis());
    }

    /** 列表页「待付款」卡片也要倒计时，同样随列表一次返回，避免逐单再查详情。 */
    @Test
    @DisplayName("订单列表回传支付截止时间：待付款卡片可直接倒计时")
    void listCarriesPayExpireAtMillis() {
        OrderPO po = order(11L, "WAIT_PAY", 1990L);
        LocalDateTime expireAt = LocalDateTime.of(2026, 11, 17, 12, 6, 30);
        po.setExpireAt(expireAt);
        when(orderMapper.selectMyPage(any(), eq(9002L), eq((String) null), eq((String) null)))
                .thenReturn(pageOf(List.of(po), 1L));
        when(orderMapper.selectItemsByOrderIds(any())).thenReturn(List.of());

        var result = service.listMyOrders(9002L, null, null, pageQuery());

        assertEquals(
                expireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                result.getList().get(0).expireAtMillis());
    }

    @Test
    @DisplayName("关单结果核对：只有「已关闭 + 已全额退款」才算关成功")
    void closeStateRequiresClosedAndFullyRefunded() {
        OrderPO closed = new OrderPO();
        closed.setStatus("CLOSED");
        closed.setPayStatus("FULL_REFUNDED");
        when(orderMapper.selectByOrderNo("SO-CLOSED")).thenReturn(closed);

        // 已关闭但只部分退款：钱没退完，不能按「关成功」处理
        OrderPO partRefunded = new OrderPO();
        partRefunded.setStatus("CLOSED");
        partRefunded.setPayStatus("PART_REFUNDED");
        when(orderMapper.selectByOrderNo("SO-PART")).thenReturn(partRefunded);

        // 已完成：状态机不允许关单，属于「关不掉」，同样不能按关成功处理
        OrderPO completed = new OrderPO();
        completed.setStatus("COMPLETED");
        completed.setPayStatus("PAID");
        when(orderMapper.selectByOrderNo("SO-DONE")).thenReturn(completed);

        assertTrue(service.isClosedWithFullRefund("SO-CLOSED"));
        assertEquals(false, service.isClosedWithFullRefund("SO-PART"));
        assertEquals(false, service.isClosedWithFullRefund("SO-DONE"));
        // 订单不存在 / 空单号一律否，不去猜
        assertEquals(false, service.isClosedWithFullRefund("SO-NONE"));
        assertEquals(false, service.isClosedWithFullRefund(" "));
    }
}
