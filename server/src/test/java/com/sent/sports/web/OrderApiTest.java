package com.sent.sports.web;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.order.controller.OrderController;
import com.sent.sports.order.dto.OrderDtos;
import com.sent.sports.order.service.CancelOrderUseCase;
import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.OrderQueryService;
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
import static org.mockito.Mockito.when;
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

    @Test
    @DisplayName("下单返回订单号与应付金额 200")
    void createOrderReturns200() throws Exception {
        when(createOrderUseCase.create(any())).thenReturn(
                new CreateOrderResult("O1", "WAIT_PAY", 10000L, 9000L, 0L, 0L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 1L);
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
        when(orderQueryService.listMyOrders(any(), any()))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/orders").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @DisplayName("订单详情无数据返回 NOT_FOUND 40401")
    void detailNotFoundReturns40401() throws Exception {
        when(orderQueryService.getDetail("O1")).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(get("/api/orders/O1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}
