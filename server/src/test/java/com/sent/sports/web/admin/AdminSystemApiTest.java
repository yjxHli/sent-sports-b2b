package com.sent.sports.web.admin;

import com.sent.sports.common.web.PageResult;
import com.sent.sports.support.BaseApiTest;
import com.sent.sports.system.controller.admin.AdminSystemController;
import com.sent.sports.system.dto.admin.AdminSystemDtos;
import com.sent.sports.system.dto.admin.LogQuery;
import com.sent.sports.system.service.SystemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统管理接口契约测试（P4-53，红→绿）。
 * 员工/角色/参数/日志、三张报表字段与权限（写端点标 @RequirePerm，P5 拦截器生效前均放行）。
 */
@WebMvcTest(AdminSystemController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminSystemApiTest extends BaseApiTest {

    @MockBean
    private SystemService systemService;

    @Test
    @DisplayName("新增员工返回 200")
    void createStaffReturns200() throws Exception {
        when(systemService.createStaff(any())).thenReturn(
                new AdminSystemDtos.StaffVO(1L, "zhang", "张三", "SALES"));
        Map<String, Object> body = Map.of("username", "zhang", "name", "张三", "roleCode", "SALES");
        mvc.perform(post("/api/admin/staff").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("zhang"));
    }

    @Test
    @DisplayName("员工列表返回空分页 200")
    void listStaffReturnsEmptyPage() throws Exception {
        when(systemService.listStaff(any())).thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/staff").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("更新角色权限返回 200")
    void updateRolePermsReturns200() throws Exception {
        when(systemService.updateRolePerms(any())).thenReturn(
                new AdminSystemDtos.RoleVO("SALES", "销售", List.of("order:read")));
        Map<String, Object> body = Map.of("roleCode", "SALES", "permCodes", List.of("order:read"));
        mvc.perform(put("/api/admin/roles/SALES/permissions")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roleCode").value("SALES"))
                .andExpect(jsonPath("$.data.permCodes[0]").value("order:read"));
    }

    @Test
    @DisplayName("角色列表返回 200")
    void listRolesReturns200() throws Exception {
        when(systemService.listRoles()).thenReturn(List.of());
        mvc.perform(get("/api/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("更新参数返回 200")
    void upsertParamReturns200() throws Exception {
        when(systemService.upsertParam(any())).thenReturn(new AdminSystemDtos.ParamVO("freightFree", "9900"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paramKey", "freightFree");
        body.put("paramValue", "9900");
        mvc.perform(put("/api/admin/params").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.paramValue").value("9900"));
    }

    @Test
    @DisplayName("参数列表返回 200")
    void listParamsReturns200() throws Exception {
        when(systemService.listParams()).thenReturn(List.of());
        mvc.perform(get("/api/admin/params"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("操作日志返回真实审计行，并把筛选条件透传给服务层")
    void listLogsReturnsRowsAndPassesFilters() throws Exception {
        AdminSystemDtos.LogVO row = new AdminSystemDtos.LogVO(
                7L, "张三", "CLOSE", 1794000000000L,
                "ORDER", "ORDER", "SO-1", "客户取消", "127.0.0.1", "trace-1", null, null);
        when(systemService.listLogs(any())).thenReturn(PageResult.of(List.of(row), 1L, 2, 5));

        mvc.perform(get("/api/admin/logs")
                        .param("page", "2").param("size", "5")
                        .param("module", "ORDER").param("keyword", "CLOSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].module").value("ORDER"))
                .andExpect(jsonPath("$.data.list[0].targetId").value("SO-1"))
                .andExpect(jsonPath("$.data.list[0].reason").value("客户取消"));

        ArgumentCaptor<LogQuery> captor = ArgumentCaptor.forClass(LogQuery.class);
        verify(systemService).listLogs(captor.capture());
        LogQuery query = captor.getValue();
        assertEquals(2, query.getPage());
        assertEquals(5, query.getSize());
        assertEquals("ORDER", query.getModule());
        assertEquals("CLOSE", query.getKeyword());
    }

    @Test
    @DisplayName("操作日志空结果仍返回 200 空分页")
    void listLogsEmptyPageReturns200() throws Exception {
        when(systemService.listLogs(any())).thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("销售趋势报表返回字段 200")
    void salesReportReturns200() throws Exception {
        when(systemService.salesReport()).thenReturn(new AdminSystemDtos.SalesReportVO("2026-11-04", 120000L, 30));
        mvc.perform(get("/api/admin/reports/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.amountCent").value(120000))
                .andExpect(jsonPath("$.data.orderCount").value(30));
    }

    @Test
    @DisplayName("销售排行报表返回字段 200")
    void salesRankReportReturns200() throws Exception {
        when(systemService.salesRankReport()).thenReturn(new AdminSystemDtos.SalesRankReportVO(1, "门店A", 88000L));
        mvc.perform(get("/api/admin/reports/sales-rank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.rank").value(1))
                .andExpect(jsonPath("$.data.amountCent").value(88000));
    }

    @Test
    @DisplayName("库存预警报表返回字段 200")
    void stockWarningReportReturns200() throws Exception {
        when(systemService.stockWarningReport()).thenReturn(
                new AdminSystemDtos.StockWarningReportVO(3L, "篮球", 5, 20));
        mvc.perform(get("/api/admin/reports/stock-warning"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.availableQty").value(5))
                .andExpect(jsonPath("$.data.threshold").value(20));
    }
}
