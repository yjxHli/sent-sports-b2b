package com.sent.sports.web.admin;

import com.sent.sports.report.controller.admin.AdminReportController;
import com.sent.sports.report.dto.DashboardReportDtos;
import com.sent.sports.report.dto.HotProductDtos;
import com.sent.sports.report.dto.SalesTrendDtos;
import com.sent.sports.report.dto.TopCustomerDtos;
import com.sent.sports.report.service.DashboardReportService;
import com.sent.sports.report.service.HotProductService;
import com.sent.sports.report.service.SalesTrendService;
import com.sent.sports.report.service.TopCustomerService;
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
    @MockBean
    private DashboardReportService dashboardReportService;
    @MockBean
    private HotProductService hotProductService;
    @MockBean
    private TopCustomerService topCustomerService;

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

    @Test
    @DisplayName("销售业绩排行返回多行真实数据，统计区间下推到服务层")
    void salesRankingReturnsRows() throws Exception {
        when(dashboardReportService.salesRank(anyInt())).thenReturn(List.of(
                new DashboardReportDtos.SalesRankVO(1, 9001L, "超级管理员", 40, 88, 998000L),
                new DashboardReportDtos.SalesRankVO(2, 9005L, "销售员张三", 30, 60, 512000L)));

        mvc.perform(get("/api/admin/reports/sales-ranking").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].name").value("超级管理员"))
                .andExpect(jsonPath("$.data[0].amountCent").value(998000))
                .andExpect(jsonPath("$.data[1].orderCount").value(60));

        verify(dashboardReportService).salesRank(7);
    }

    @Test
    @DisplayName("工作台看板一次返回六卡与待办数量")
    void dashboardReturnsSummary() throws Exception {
        when(dashboardReportService.dashboard()).thenReturn(
                new DashboardReportDtos.DashboardSummaryVO(
                        "2026-11-12", 3, 128800L, 303, 2, 1, 3, 7, 39));

        mvc.perform(get("/api/admin/reports/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.date").value("2026-11-12"))
                .andExpect(jsonPath("$.data.todayOrderCount").value(3))
                .andExpect(jsonPath("$.data.todayAmountCent").value(128800))
                .andExpect(jsonPath("$.data.pendingShipCount").value(303))
                .andExpect(jsonPath("$.data.warningSkuCount").value(3))
                .andExpect(jsonPath("$.data.overdueReceivableCount").value(39));
    }

    @Test
    @DisplayName("热销商品：返回区间汇总 + Top N，days/limit 原样透传到服务层")
    void hotProductsReturnsTopN() throws Exception {
        HotProductDtos.HotProductItemVO item = new HotProductDtos.HotProductItemVO(
                1, 9372L, "排球学生中考专用5号标准", "", 450L, 1096200L, "10962.00", 102, "5.9");
        when(hotProductService.hotProducts(anyInt(), anyInt())).thenReturn(
                new HotProductDtos.HotProductVO("2026-10-23", "2026-11-21", 30, 5,
                        18651281L, "186512.81", 8104L, 145, List.of(item)));

        mvc.perform(get("/api/admin/reports/hot-products")
                        .param("days", "30").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skuCount").value(145))
                .andExpect(jsonPath("$.data.totalAmountYuan").value("186512.81"))
                .andExpect(jsonPath("$.data.items[0].skuName").value("排球学生中考专用5号标准"))
                .andExpect(jsonPath("$.data.items[0].sharePct").value("5.9"))
                .andExpect(jsonPath("$.data.items[0].qty").value(450));

        verify(hotProductService).hotProducts(30, 5);
    }

    @Test
    @DisplayName("热销商品：不传参数时默认近 30 天、前 5 名")
    void hotProductsDefaults() throws Exception {
        when(hotProductService.hotProducts(anyInt(), anyInt())).thenReturn(
                new HotProductDtos.HotProductVO("2026-10-23", "2026-11-21", 30, 5,
                        0L, "0.00", 0L, 0, List.of()));

        mvc.perform(get("/api/admin/reports/hot-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        verify(hotProductService).hotProducts(30, 5);
    }

    @Test
    @DisplayName("大客户排行：返回区间汇总 + Top N，days/limit 透传到服务层")
    void topCustomersReturnsTopN() throws Exception {
        TopCustomerDtos.TopCustomerItemVO item = new TopCustomerDtos.TopCustomerItemVO(
                1, 9002L, "森特供应链有限公司", "13800138000", 6, 128_800L, "1288.00", "18.4");
        when(topCustomerService.topCustomers(anyInt(), anyInt())).thenReturn(
                new TopCustomerDtos.TopCustomerVO("2026-10-23", "2026-11-21", 30, 5,
                        700_000L, "7000.00", 42L, 9, List.of(item)));

        mvc.perform(get("/api/admin/reports/top-customers")
                        .param("days", "30").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customerCount").value(9))
                .andExpect(jsonPath("$.data.items[0].customerName").value("森特供应链有限公司"))
                .andExpect(jsonPath("$.data.items[0].amountYuan").value("1288.00"))
                .andExpect(jsonPath("$.data.items[0].sharePct").value("18.4"))
                .andExpect(jsonPath("$.data.items[0].orderCount").value(6));

        verify(topCustomerService).topCustomers(30, 5);
    }

    @Test
    @DisplayName("大客户排行：不传参数时默认近 30 天、前 5 名")
    void topCustomersDefaults() throws Exception {
        when(topCustomerService.topCustomers(anyInt(), anyInt())).thenReturn(
                new TopCustomerDtos.TopCustomerVO("2026-10-23", "2026-11-21", 30, 5,
                        0L, "0.00", 0L, 0, List.of()));

        mvc.perform(get("/api/admin/reports/top-customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        verify(topCustomerService).topCustomers(30, 5);
    }
}
