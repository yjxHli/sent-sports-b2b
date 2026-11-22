package com.sent.sports.web;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.order.controller.OrderController;
import com.sent.sports.order.dto.OrderDtos;
import com.sent.sports.order.service.BatchDeleteOrdersUseCase;
import com.sent.sports.order.service.CancelOrderUseCase;
import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.DeleteOrderUseCase;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.BatchDeleteOrdersResult;
import com.sent.sports.order.service.dto.CancelOrderResult;
import com.sent.sports.order.service.dto.ConfirmReceiptResult;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单接口契约测试（P4-14，红→绿）。
 * 下单 200、取消 200、确认收货 200、不可取消态 STATE_CONFLICT 40901、我的订单空页。
 * 真实读面（list/detail）由 {@link OrderQueryService} 桩提供，P5 接入仓储后替换。
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderApiTest extends BaseApiTest {

    @MockBean
    private CreateOrderUseCase createOrderUseCase;

    @MockBean
    private CancelOrderUseCase cancelOrderUseCase;

    @MockBean
    private ConfirmReceiptUseCase confirmReceiptUseCase;

    @MockBean
    private OrderQueryService orderQueryService;

    @MockBean
    private DeleteOrderUseCase deleteOrderUseCase;

    @MockBean
    private BatchDeleteOrdersUseCase batchDeleteOrdersUseCase;

    @Test
    @DisplayName("下单返回订单号与应付金额 200")
    void createOrderReturns200() throws Exception {
        when(createOrderUseCase.create(any())).thenReturn(
                new CreateOrderResult("O1", "WAIT_PAY", 10000L, 9000L, 0L, 0L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 1L);
        // 收货地址必填：下单时会被固化成 address_snapshot_json
        body.put("addressId", 7001L);
        body.put("idemKey", "idem-1");
        body.put("availablePoints", 0);
        body.put("freightFreeThresholdCent", 0);
        body.put("freightFeeCent", 0);
        body.put("items", List.of(Map.of(
                "skuId", 1L, "qty", 2, "customerTypeCode", "RETAIL",
                "memberDiscountRate", 0, "agreementDiscountRate", 0, "allowStacking", false)));
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O1"))
                .andExpect(jsonPath("$.data.payableCent").value(10000))
                .andExpect(jsonPath("$.data.payableYuan").value("100.00"));
    }

    @Test
    @DisplayName("取消订单返回 200")
    void cancelOrderReturns200() throws Exception {
        when(cancelOrderUseCase.cancel(any())).thenReturn(new CancelOrderResult("O1", "CANCELLED"));
        mvc.perform(post("/api/orders/O1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("不可取消状态返回 STATE_CONFLICT 40901")
    void cancelNonCancelableReturns40901() throws Exception {
        when(cancelOrderUseCase.cancel(any()))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT));
        mvc.perform(post("/api/orders/O1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    @DisplayName("确认收货返回 200")
    void confirmReceiptReturns200() throws Exception {
        when(confirmReceiptUseCase.confirm(any()))
                .thenReturn(new ConfirmReceiptResult("O1", "WAIT_RECEIVE", false));
        mvc.perform(post("/api/orders/O1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("lines", List.of(Map.of("skuId", 1L, "qty", 2))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O1"));
    }

    @Test
    @DisplayName("我的订单返回空分页 200")
    void listMyOrdersReturnsEmptyPage() throws Exception {
        when(orderQueryService.listMyOrders(any(), any(), any(), any()))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/orders").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @DisplayName("我的订单带 status 时把状态透传给 service（订单页 Tab 筛选）")
    void listMyOrdersPassesStatus() throws Exception {
        when(orderQueryService.listMyOrders(any(), eq("WAIT_SHIP"), any(), any()))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 20));

        mvc.perform(get("/api/orders").param("status", "WAIT_SHIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(orderQueryService).listMyOrders(any(), eq("WAIT_SHIP"), any(), any());
    }

    @Test
    @DisplayName("我的订单带 keyword 时把搜索词透传给 service（订单页搜索）")
    void listMyOrdersPassesKeyword() throws Exception {
        when(orderQueryService.listMyOrders(any(), any(), eq("SO20261116"), any()))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 10));

        mvc.perform(get("/api/orders").param("keyword", "SO20261116"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(orderQueryService).listMyOrders(any(), any(), eq("SO20261116"), any());
    }

    @Test
    @DisplayName("订单详情无数据返回 NOT_FOUND 40401")
    void detailNotFoundReturns40401() throws Exception {
        when(orderQueryService.getDetail("O1")).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(get("/api/orders/O1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("删除订单返回 200：终态订单才可删，在途由用例抛 STATE_CONFLICT")
    void deleteOrderReturns200() throws Exception {
        mvc.perform(delete("/api/orders/O1")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(deleteOrderUseCase).delete("O1", 9002L);
    }

    @Test
    @DisplayName("删除在途订单返回 STATE_CONFLICT 40901")
    void deleteInFlightOrderReturns40901() throws Exception {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.STATE_CONFLICT, "在途订单不能删除"))
                .when(deleteOrderUseCase).delete(eq("O1"), any());

        mvc.perform(delete("/api/orders/O1")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    @DisplayName("未登录删除订单返回 40101：管理端令牌也不行，管理端走 /api/admin/orders")
    void deleteWithoutCustomerReturns40101() throws Exception {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.UNAUTHENTICATED))
                .when(deleteOrderUseCase).delete(eq("O1"), org.mockito.ArgumentMatchers.isNull());

        mvc.perform(delete("/api/orders/O1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("多选删除返回 200：能删的删掉，在途单逐条回原因（不给整批失败码）")
    void batchDeleteReturnsDeletedAndSkipped() throws Exception {
        when(batchDeleteOrdersUseCase.delete(any(), eq(9002L))).thenReturn(new BatchDeleteOrdersResult(
                2, List.of(new BatchDeleteOrdersResult.Skipped("SO2", "在途订单不能删除，请先完成收货或取消订单"))));

        mvc.perform(post("/api/orders/batch-delete")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"SO1\",\"SO2\",\"SO3\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(2))
                .andExpect(jsonPath("$.data.skipped.length()").value(1))
                .andExpect(jsonPath("$.data.skipped[0].id").value("SO2"))
                .andExpect(jsonPath("$.data.skipped[0].reason").value("在途订单不能删除，请先完成收货或取消订单"));

        verify(batchDeleteOrdersUseCase).delete(any(), eq(9002L));
    }

    @Test
    @DisplayName("多选删除：空选择被参数校验拦下 400，不进用例")
    void batchDeleteRejectsEmptySelection() throws Exception {
        mvc.perform(post("/api/orders/batch-delete")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest());

        verify(batchDeleteOrdersUseCase, org.mockito.Mockito.never()).delete(any(), any());
    }

    @Test
    @DisplayName("多选删除：未登录返回 40101（管理端令牌也不行）")
    void batchDeleteWithoutCustomerReturns40101() throws Exception {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.UNAUTHENTICATED))
                .when(batchDeleteOrdersUseCase).delete(any(), org.mockito.ArgumentMatchers.isNull());

        mvc.perform(post("/api/orders/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"SO1\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
