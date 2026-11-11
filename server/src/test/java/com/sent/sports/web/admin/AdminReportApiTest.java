package com.sent.sports.web.admin;

import com.sent.sports.report.controller.admin.AdminReportController;
import com.sent.sports.report.dto.SalesTrendDtos;
import com.sent.sports.report.service.SalesTrendService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 销售趋势端点契约（批 4，TDD 红灯先行）：GET /api/admin/reports/sales-trend?days=N。
 * 旧端点 /reports/sales 保持单条汇总契约不变，趋势另开端点，避免破坏既有前端与测试。
 */
@WebMvcTest(AdminReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminReportApiTest extends BaseApiTest {

    @MockBean
    private SalesTrendService salesTrendService;

    @Test
    @DisplayName("销售趋势返回按日序列 + 汇总字段 200")
    void salesTrendReturnsSeries() throws Exception {
        SalesTrendDtos.SalesTrendPointVO point =
                new SalesTrendDtos.SalesTrendPointVO("2026-11-09", 30000L, "300.00", 2, 15000L);
        SalesTrendDtos.SalesTrendVO vo = new SalesTrendDtos.SalesTrendVO(
                "2026-11-07", "2026-11-09", 3, 40000L, "400.00", 3, 13333L, "133.33", List.of(point));
        when(salesTrendService.trend(anyInt())).thenReturn(vo);

        mvc.perform(get("/api/admin/reports/sales-trend").param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days").value(3))
                .andExpect(jsonPath("$.data.totalAmountCent").value(40000))
                .andExpect(jsonPath("$.data.totalAmountYuan").value("400.00"))
                .andExpect(jsonPath("$.data.totalOrderCount").value(3))
                .andExpect(jsonPath("$.data.points[0].date").value("2026-11-09"))
                .andExpect(jsonPath("$.data.points[0].amountCent").value(30000));

        verify(salesTrendService).trend(3);
    }

    @Test
    @DisplayName("不传 days 时默认近 30 天")
    void salesTrendDefaultsTo30Days() throws Exception {
        when(salesTrendService.trend(anyInt())).thenReturn(
                new SalesTrendDtos.SalesTrendVO("2026-10-11", "2026-11-09", 30,
                        0L, "0.00", 0, 0L, "0.00", List.of()));

        mvc.perform(get("/api/admin/reports/sales-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(30));

        verify(salesTrendService).trend(30);
    }
}
