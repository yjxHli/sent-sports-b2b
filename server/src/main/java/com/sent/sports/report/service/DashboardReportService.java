package com.sent.sports.report.service;

import com.sent.sports.report.dto.DashboardReportDtos;

import java.util.List;

/**
 * 销售业绩排行 + 工作台看板查询门面。
 * <p>system 模块的旧报表端点通过本接口委托真查（ArchUnit 规则 4：跨模块只经对方 service）。</p>
 */
public interface DashboardReportService {

    /** 近 N 天按归属销售的业绩排行，按成交金额降序，rank 从 1 连续编号。 */
    List<DashboardReportDtos.SalesRankVO> salesRank(int days);

    /** 工作台六卡与角色化待办数量。 */
    DashboardReportDtos.DashboardSummaryVO dashboard();
}
