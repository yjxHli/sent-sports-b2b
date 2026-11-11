package com.sent.sports.order;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.order.dto.admin.AdminOrderDtos;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderDetailLineRow;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.CloseOrderUseCase;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.ShipOrderUseCase;
import com.sent.sports.order.service.dto.CloseOrderCmd;
import com.sent.sports.order.service.dto.CloseOrderResult;
import com.sent.sports.order.service.impl.OrderAdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端订单详情（P4 硬编码 404 桩 → 真实查询）的红→绿用例。
 * <p>管理端点「订单详情」曾直接 404，因为 {@code detail()} 无条件抛 NOT_FOUND；
 * 这里锁定：主表字段、时间毫秒换算、发货态推导、订单行与定制状态。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderAdminServiceImplTest {

    @Mock
    private ShipOrderUseCase shipOrderUseCase;
    @Mock
    private OrderQueryService orderQueryService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private CloseOrderUseCase closeOrderUseCase;

    private OrderAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderAdminServiceImpl(shipOrderUseCase, orderQueryService, orderMapper,
                closeOrderUseCase);
    }

    @Test
    @DisplayName("列表：status 筛选必须透传到 Mapper，发货工作台只应看到待发货单")
    void listPassesStatusToMapper() {
        when(orderMapper.selectAllPage(any(Page.class), eq("WAIT_SHIP")))
                .thenReturn(new Page<OrderPO>(1, 10));

        service.list(new PageQuery(), "WAIT_SHIP");

        verify(orderMapper).selectAllPage(any(Page.class), eq("WAIT_SHIP"));
    }

    @Test
    @DisplayName("列表：未传 status 时透传 null，保持全量分页")
    void listWithoutStatusPassesNull() {
        when(orderMapper.selectAllPage(any(Page.class), isNull()))
                .thenReturn(new Page<OrderPO>(1, 10));

        service.list(new PageQuery(), null);

        verify(orderMapper).selectAllPage(any(Page.class), isNull());
    }

    @Test
    @DisplayName("关单：委托真实用例，把订单号/原因透传并把结果映射成管理端 VO")
    void closeOrderDelegatesToUseCase() {
        when(closeOrderUseCase.close(any())).thenReturn(new CloseOrderResult(
                "SO-CLOSE-1", "CLOSED", "RF-1", 20000L, 20000L, true));

        AdminOrderDtos.AdminOrderVO vo = service.closeOrder("SO-CLOSE-1", "客户取消");

        assertEquals("SO-CLOSE-1", vo.orderNo());
        assertEquals("CLOSED", vo.status());
        assertEquals(20000L, vo.payableCent());
        assertEquals("200.00", vo.payableYuan());
        org.mockito.ArgumentCaptor<CloseOrderCmd> captor =
                org.mockito.ArgumentCaptor.forClass(CloseOrderCmd.class);
        verify(closeOrderUseCase).close(captor.capture());
        assertEquals("SO-CLOSE-1", captor.getValue().orderNo());
        assertEquals("客户取消", captor.getValue().reason());
    }

    @Test
    @DisplayName("详情：真实读取订单头 + 订单行，金额保持「分」、时间换算毫秒")
    void detailReadsRealOrderAndLines() {
        OrderPO po = order("SO20261108000001", "WAIT_RECEIVE");
        when(orderMapper.selectByOrderNo("SO20261108000001")).thenReturn(po);
        when(orderMapper.selectDetailLines(11L)).thenReturn(List.of(
                line(9208L, "尤尼克斯 AS-05", "一筒(12只)", 2, 9990L, 2, "NONE"),
                line(9102L, "UV 定制篮球", "7号", 1, 20000L, 0, "FINISHED")));

        AdminOrderDtos.AdminOrderDetailVO vo = service.detail("SO20261108000001");

        assertEquals("SO20261108000001", vo.orderNo());
        assertEquals("WAIT_RECEIVE", vo.status());
        assertEquals("PAID", vo.payStatus());
        assertEquals("SHIPPED", vo.shipStatus());
        assertEquals(9002L, vo.customerId());
        assertEquals(20000L, vo.payableCent());
        assertEquals(20000L, vo.goodsAmountCent());
        assertEquals(0L, vo.freightCent());
        assertEquals(0L, vo.pointDeductCent());
        assertEquals(po.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                vo.createdAt());
        assertEquals(2, vo.lines().size());
        assertEquals("UV 定制篮球", vo.lines().get(1).skuName());
        assertEquals("FINISHED", vo.lines().get(1).customStatus());
        assertEquals(2, vo.lines().get(0).shippedQty());
    }

    @Test
    @DisplayName("详情：订单号不存在仍按契约抛 NOT_FOUND")
    void detailMissingOrderRejects() {
        when(orderMapper.selectByOrderNo("NO-SUCH-ORDER")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.detail("NO-SUCH-ORDER"));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(orderMapper, never()).selectDetailLines(anyLong());
    }

    @Test
    @DisplayName("详情：历史脏数据缺主键时不联查订单行，返回空行而非 NPE")
    void detailWithoutPrimaryKeySkipsLines() {
        OrderPO po = order("SO-NO-ID", "WAIT_SHIP");
        po.setId(null);
        when(orderMapper.selectByOrderNo("SO-NO-ID")).thenReturn(po);

        AdminOrderDtos.AdminOrderDetailVO vo = service.detail("SO-NO-ID");

        assertTrue(vo.lines().isEmpty());
        verify(orderMapper, never()).selectDetailLines(anyLong());
    }

    @Test
    @DisplayName("详情：发货态按主状态推导（待发货 / 部分发货 / 已发货）")
    void detailDerivesShipStatus() {
        for (String[] pair : List.of(
                new String[]{"WAIT_PAY", "WAIT_SHIP"},
                new String[]{"WAIT_SHIP", "WAIT_SHIP"},
                new String[]{"PART_SHIPPED", "PART_SHIPPED"},
                new String[]{"COMPLETED", "SHIPPED"})) {
            OrderPO po = order("SO-" + pair[0], pair[0]);
            when(orderMapper.selectByOrderNo("SO-" + pair[0])).thenReturn(po);

            assertEquals(pair[1], service.detail("SO-" + pair[0]).shipStatus());
        }
    }

    private OrderPO order(String orderNo, String status) {
        OrderPO po = new OrderPO();
        po.setId(11L);
        po.setOrderNo(orderNo);
        po.setCustomerId(9002L);
        po.setStatus(status);
        po.setPayStatus("PAID");
        po.setAfterSaleStatus("NONE");
        po.setGoodsAmountCent(20000L);
        po.setFreightCent(0L);
        po.setPointDeductCent(0L);
        po.setPayableCent(20000L);
        po.setPaidCent(20000L);
        po.setCreatedAt(LocalDateTime.of(2026, 11, 8, 12, 30, 0));
        return po;
    }

    private OrderDetailLineRow line(Long skuId, String skuName, String spec, int qty,
                                    Long unitPriceCent, int shippedQty, String customStatus) {
        OrderDetailLineRow row = new OrderDetailLineRow();
        row.setSkuId(skuId);
        row.setSkuName(skuName);
        row.setSpec(spec);
        row.setQty(qty);
        row.setUnitPriceCent(unitPriceCent);
        row.setShippedQty(shippedQty);
        row.setCustomStatus(customStatus);
        return row;
    }
}
