package com.sent.sports.web.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.service.ConfirmTokenService;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.order.controller.admin.AdminOrderController;
import com.sent.sports.order.dto.admin.AdminOrderDtos;
import com.sent.sports.order.service.admin.OrderAdminService;
import com.sent.sports.order.service.dto.ShipOrderResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单管理接口契约测试（P4-38，红→绿）。
 * 多维筛选列表、代客下单、拆单、拆单后发货、关单均返回 200 / code 0。
 */
@WebMvcTest(AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminOrderApiTest extends BaseApiTest {

    @MockBean
    private OrderAdminService orderAdminService;

    @MockBean
    private ConfirmTokenService confirmTokenService;

    @Test
    @DisplayName("多维筛选列表返回空分页 200")
    void listReturnsEmptyPage() throws Exception {
        when(orderAdminService.list(any(), any())).thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/orders").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("列表：status=WAIT_SHIP 必须透传给服务，发货工作台不再拿到 COMPLETED 单")
    void listPassesStatusToService() throws Exception {
        when(orderAdminService.list(any(), eq("WAIT_SHIP")))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/orders")
                        .param("page", "1").param("size", "10").param("status", "WAIT_SHIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(orderAdminService).list(any(PageQuery.class), eq("WAIT_SHIP"));
    }

    @Test
    @DisplayName("列表：未带 status 时按 null 透传，保持订单列表全量分页")
    void listWithoutStatusPassesNull() throws Exception {
        when(orderAdminService.list(any(), isNull()))
                .thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/orders").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(orderAdminService).list(any(PageQuery.class), isNull());
    }

    @Test
    @DisplayName("订单详情返回 200：四维度状态 + 金额 + 订单行（不再是 404）")
    void detailReturns200() throws Exception {
        when(orderAdminService.detail("SO-1")).thenReturn(new AdminOrderDtos.AdminOrderDetailVO(
                "SO-1", "WAIT_SHIP", "PAID", "WAIT_SHIP", "NONE",
                9002L, 1730000000000L, 20000L, 0L, 0L, 20000L, 20000L,
                List.of(new AdminOrderDtos.AdminOrderLineVO(
                        9102L, "UV 定制篮球", "7号", 1, 20000L, 0, "FINISHED"))));
        mvc.perform(get("/api/admin/orders/SO-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("SO-1"))
                .andExpect(jsonPath("$.data.status").value("WAIT_SHIP"))
                .andExpect(jsonPath("$.data.shipStatus").value("WAIT_SHIP"))
                .andExpect(jsonPath("$.data.lines[0].skuName").value("UV 定制篮球"))
                .andExpect(jsonPath("$.data.lines[0].customStatus").value("FINISHED"));
    }

    @Test
    @DisplayName("代客下单返回订单号 200")
    void agentCreateReturns200() throws Exception {
        when(orderAdminService.agentCreate(any())).thenReturn(
                new AdminOrderDtos.AdminOrderVO("O-AGENT", "WAIT_PAY", 0L, "0.00"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 7L);
        body.put("remark", "代客下单");
        body.put("items", List.of(Map.of("skuId", 1L, "qty", 2, "customerTypeCode", "RETAIL")));
        mvc.perform(post("/api/admin/orders").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O-AGENT"));
    }

    @Test
    @DisplayName("拆单返回拆单后订单 200")
    void splitReturns200() throws Exception {
        when(orderAdminService.split(any())).thenReturn(
                new AdminOrderDtos.AdminOrderVO("O-SPLIT", "SPLIT", 0L, "0.00"));
        Map<String, Object> body = Map.of("lines", List.of(Map.of("skuId", 1L, "qty", 1)));
        mvc.perform(post("/api/admin/orders/10/split").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O-SPLIT"));
    }

    @Test
    @DisplayName("拆单后发货返回 200")
    void shipReturns200() throws Exception {
        when(orderAdminService.ship(anyString(), any())).thenReturn(new ShipOrderResult("O-10", "WAIT_SHIP"));
        Map<String, Object> body = Map.of("carrier", "SF", "trackingNo", "TN1",
                "lines", List.of(Map.of("skuId", 1L, "qty", 2)));
        mvc.perform(post("/api/admin/orders/10/ship").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O-10"));
    }

    @Test
    @DisplayName("关单：携带有效确认令牌 + 原因时返回 200，并透传订单号与原因")
    void closeReturns200() throws Exception {
        when(confirmTokenService.consume("ORDER_CLOSE", "SO-100", "ct-1")).thenReturn(true);
        when(orderAdminService.closeOrder("SO-100", "客户取消")).thenReturn(
                new AdminOrderDtos.AdminOrderVO("SO-100", "CLOSED", 20000L, "200.00"));
        mvc.perform(post("/api/admin/orders/SO-100/close")
                        .header("X-Confirm-Token", "ct-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reason", "客户取消"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        verify(orderAdminService).closeOrder("SO-100", "客户取消");
    }

    @Test
    @DisplayName("关单：未携带 X-Confirm-Token 返回 40302，不得进入业务")
    void closeWithoutConfirmTokenRejects() throws Exception {
        mvc.perform(post("/api/admin/orders/SO-100/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reason", "客户取消"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
        verify(orderAdminService, org.mockito.Mockito.never()).closeOrder(anyString(), anyString());
    }

    @Test
    @DisplayName("关单：令牌失效 / 与订单号不匹配返回 40302")
    void closeWithInvalidConfirmTokenRejects() throws Exception {
        when(confirmTokenService.consume("ORDER_CLOSE", "SO-100", "wrong")).thenReturn(false);
        mvc.perform(post("/api/admin/orders/SO-100/close")
                        .header("X-Confirm-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reason", "客户取消"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
        verify(orderAdminService, org.mockito.Mockito.never()).closeOrder(anyString(), anyString());
    }

    @Test
    @DisplayName("关单：原因为空被 Bean Validation 拦截（400）")
    void closeWithBlankReasonRejects() throws Exception {
        mvc.perform(post("/api/admin/orders/SO-100/close")
                        .header("X-Confirm-Token", "ct-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reason", ""))))
                .andExpect(status().isBadRequest());
        verify(orderAdminService, org.mockito.Mockito.never()).closeOrder(anyString(), anyString());
    }

    @Test
    @DisplayName("关单端点必须声明 @RequirePerm(\"order:close\")")
    void closeEndpointDeclaresPermission() throws Exception {
        Method method = AdminOrderController.class.getMethod("close", String.class, String.class,
                AdminOrderDtos.CloseCmd.class);
        RequirePerm perm = method.getAnnotation(RequirePerm.class);
        org.junit.jupiter.api.Assertions.assertNotNull(perm, "关单端点必须声明权限注解");
        assertArrayEquals(new String[]{"order:close"}, perm.value());
    }
}
