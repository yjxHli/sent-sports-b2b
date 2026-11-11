package com.sent.sports.report.service;

import com.sent.sports.report.dto.SalesTrendDtos;

/** 销售趋势报表服务（批 4）：以 paid_at 为时间基准，近 N 天按日聚合已支付订单。 */
public interface SalesTrendService {

    /**
     * 查询近 {@code days} 天（含今天）的销售趋势，返回连续日期序列。
     *
     * @param days 天数，收敛到 1~90
     */
    SalesTrendDtos.SalesTrendVO trend(int days);
}
