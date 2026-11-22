package com.sent.sports.report.service;

import com.sent.sports.report.dto.HotProductDtos;

/**
 * 热销商品报表（2026-11-21）：按 SKU 聚合成交数量与金额，返回 Top N + 全量总额。
 * <p>数据范围由 Mapper 上的 {@code @DataScope} 在 SQL 层过滤，本层不再重复判断角色。</p>
 */
public interface HotProductService {

    /**
     * @param days  统计天数，收敛到 1~90
     * @param limit 返回条数，收敛到 1~50
     */
    HotProductDtos.HotProductVO hotProducts(int days, int limit);
}
