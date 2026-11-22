package com.sent.sports.report.service;

import com.sent.sports.report.dto.TopCustomerDtos;

/**
 * 大客户采购排行（2026-11-21）：按客户聚合实付金额，返回 Top N + 区间全量汇总。
 * <p>数据范围由 Mapper 上的 {@code @DataScope} 在 SQL 层过滤。</p>
 */
public interface TopCustomerService {

    /**
     * @param days  统计天数，收敛到 1~90
     * @param limit 返回条数，收敛到 1~50
     */
    TopCustomerDtos.TopCustomerVO topCustomers(int days, int limit);
}
