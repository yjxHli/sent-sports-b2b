package com.sent.sports.report.controller.admin;

import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.R;
import com.sent.sports.report.dto.SalesTrendDtos;
import com.sent.sports.report.service.SalesTrendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报表端点（批 4）：销售趋势按日序列。
 * <p>另开 {@code /sales-trend}，不改动 {@code AdminSystemController} 的
 * {@code /reports/sales} 单条汇总契约，避免破坏既有前端页面与接口测试。</p>
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final SalesTrendService salesTrendService;

    public AdminReportController(SalesTrendService salesTrendService) {
        this.salesTrendService = salesTrendService;
    }

    @GetMapping("/sales-trend")
    @RequirePerm("system:report:view")
    public R<SalesTrendDtos.SalesTrendVO> salesTrend(
            @RequestParam(required = false, defaultValue = "30") int days) {
        return R.ok(salesTrendService.trend(days));
    }
}
