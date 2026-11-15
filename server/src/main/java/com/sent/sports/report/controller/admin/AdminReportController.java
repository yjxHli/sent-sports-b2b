package com.sent.sports.report.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.R;
import com.sent.sports.report.dto.DashboardReportDtos;
import com.sent.sports.report.dto.SalesTrendDtos;
import com.sent.sports.report.service.DashboardReportService;
import com.sent.sports.report.service.SalesTrendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报表端点：销售趋势按日序列、销售业绩排行（多行）、工作台看板汇总。
 * <p>另开 {@code /sales-trend}、{@code /sales-ranking}、{@code /dashboard}，
 * 不改动 {@code AdminSystemController} 的 {@code /reports/sales} 单条汇总契约，
 * 避免破坏既有前端页面与接口测试；旧端点的占位实现同样改为委托本模块的 service 真查。</p>
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final SalesTrendService salesTrendService;
    private final DashboardReportService dashboardReportService;

    public AdminReportController(SalesTrendService salesTrendService,
                                 DashboardReportService dashboardReportService) {
        this.salesTrendService = salesTrendService;
        this.dashboardReportService = dashboardReportService;
    }

    @GetMapping("/sales-trend")
    @RequirePerm("system:report:view")
    public R<SalesTrendDtos.SalesTrendVO> salesTrend(
            @RequestParam(required = false, defaultValue = "30") int days) {
        return R.ok(salesTrendService.trend(days));
    }

    /** 销售业绩排行：按归属销售聚合，统计区间由 SQL 下推，不是前端过滤当前页。 */
    @GetMapping("/sales-ranking")
    @RequirePerm("system:report:view")
    public R<List<DashboardReportDtos.SalesRankVO>> salesRanking(
            @RequestParam(required = false, defaultValue = "30") int days) {
        return R.ok(dashboardReportService.salesRank(days));
    }

    /** 工作台六卡与角色化待办数量，一次请求取齐，避免六张卡各自发请求。 */
    @GetMapping("/dashboard")
    @RequirePerm("system:report:view")
    public R<DashboardReportDtos.DashboardSummaryVO> dashboard() {
        return R.ok(dashboardReportService.dashboard());
    }
}
