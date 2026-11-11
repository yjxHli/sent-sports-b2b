package com.sent.sports.web.admin;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.controller.admin.AdminCustomerController;
import com.sent.sports.customer.dto.admin.AdminCustomerDtos;
import com.sent.sports.customer.service.CustomerOperationService;
import com.sent.sports.customer.service.CustomerQueryService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户与会员管理接口契约测试（P4-44，红→绿）。
 * 写动作改由 {@link CustomerOperationService}（真库命令服务）承接后，契约改为验证参数透传，
 * 不再是 P3 内存用例的返回值。
 */
@WebMvcTest(AdminCustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCustomerApiTest extends BaseApiTest {

    @MockBean
    private CustomerOperationService customerOperationService;

    @MockBean
    private CustomerQueryService customerQueryService;

    @Test
    @DisplayName("客户改派透传目标员工与原因，返回新旧归属 200")
    void transferReturns200() throws Exception {
        when(customerOperationService.transfer(anyLong(), anyLong(), any()))
                .thenReturn(new AdminCustomerDtos.TransferVO(1L, 0L, 5L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("toStaffId", 5L);
        body.put("reason", "区域调整");
        mvc.perform(post("/api/admin/customers/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customerId").value(1))
                .andExpect(jsonPath("$.data.toStaffId").value(5));
        verify(customerOperationService).transfer(eq(1L), eq(5L), eq("区域调整"));
    }

    @Test
    @DisplayName("改派缺少原因返回 40001")
    void transferMissingReasonReturns40001() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("toStaffId", 5L);
        mvc.perform(post("/api/admin/customers/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("授级查无客户返回 NOT_FOUND 40401")
    void grantLevelNotFoundReturns40401() throws Exception {
        when(customerOperationService.grantLevel(anyLong(), any(), anyLong(), any()))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", "LV2");
        body.put("validToMillis", 1893456000000L);
        body.put("reason", "业绩达标");
        mvc.perform(post("/api/admin/customers/404/level")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("非法等级码不再触发系统异常，命令服务返回 40001")
    void grantLevelInvalidLevelReturns40001() throws Exception {
        when(customerOperationService.grantLevel(anyLong(), eq("GOLD"), anyLong(), any()))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID, "等级码不合法：GOLD"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", "GOLD");
        body.put("validToMillis", 1893456000000L);
        body.put("reason", "业绩达标");
        mvc.perform(post("/api/admin/customers/1/level")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("积分调整成功返回调整前后 200")
    void adjustPointsReturns200() throws Exception {
        when(customerOperationService.adjustPoints(anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(new AdminCustomerDtos.PointVO(1L, 100L, 80L, -20L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delta", -20);
        body.put("reason", "纠正发放");
        body.put("bizType", "MANUAL");
        body.put("bizNo", "M-1");
        mvc.perform(post("/api/admin/customers/1/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.beforePoints").value(100))
                .andExpect(jsonPath("$.data.afterPoints").value(80))
                .andExpect(jsonPath("$.data.delta").value(-20));
    }

    @Test
    @DisplayName("客户列表返回空分页 200")
    void listReturnsEmptyPage() throws Exception {
        when(customerQueryService.list(any(), any(), any(), any())).thenReturn(
                com.sent.sports.common.web.PageResult.of(java.util.List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/customers").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("客户列表透传关键字 / 等级 / 客户类型，并回显客户昵称/手机号")
    void listPassesFiltersAndReturnsCustomerRow() throws Exception {
        when(customerQueryService.list(any(), eq("1380"), eq("LV2"), eq("ENTERPRISE"))).thenReturn(
                com.sent.sports.common.web.PageResult.of(java.util.List.of(
                        new com.sent.sports.customer.dto.admin.AdminCustomerDtos.CustomerRow(
                                9002L, "C9002", "验收客户", "13800138000", "ENTERPRISE", "LV2", 9001L,
                                null, 120L, 300L, 0L)), 1L, 1, 10));

        mvc.perform(get("/api/admin/customers")
                        .param("page", "1").param("size", "10")
                        .param("keyword", "1380").param("level", "LV2")
                        .param("type", "ENTERPRISE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].name").value("验收客户"))
                .andExpect(jsonPath("$.data.list[0].phone").value("13800138000"));

        verify(customerQueryService).list(any(), eq("1380"), eq("LV2"), eq("ENTERPRISE"));
    }

    @Test
    @DisplayName("企业审核与授信按 type=ENTERPRISE 与 page/size 真分页查询")
    void listPassesEnterprisePaging() throws Exception {
        when(customerQueryService.list(any(), any(), any(), eq("ENTERPRISE"))).thenReturn(
                com.sent.sports.common.web.PageResult.of(java.util.List.of(), 53L, 2, 20));

        mvc.perform(get("/api/admin/customers")
                        .param("page", "2").param("size", "20")
                        .param("type", "ENTERPRISE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").exists());

        verify(customerQueryService).list(any(), any(), any(), eq("ENTERPRISE"));
    }
}
