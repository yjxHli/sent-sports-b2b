package com.sent.sports.report.dto;

import java.util.List;

/**
 * 销售趋势报表 DTO（批 4）：以已支付订单为口径的中立视图，不依赖 order 模块领域类型。
 * <p>金额一律用 BIGINT「分」{@code amountCent}，并附展示用元字符串 {@code amountYuan}
 * （{@code MoneyUtil.toYuan}），避免前端自行换算产生精度误差。</p>
 */
public final class SalesTrendDtos {

    private SalesTrendDtos() {
    }

    /** 单日销售点：日期（yyyy-MM-dd）、当日销售额、当日订单数、当日客单价。 */
    public record SalesTrendPointVO(String date, long amountCent, String amountYuan,
                                    int orderCount, long avgOrderCent) {
    }

    /** 近 N 天趋势：连续日期序列（无成交日补零）+ 区间汇总。 */
    public record SalesTrendVO(String fromDate, String toDate, int days,
                               long totalAmountCent, String totalAmountYuan,
                               int totalOrderCount, long avgOrderCent, String avgOrderYuan,
                               List<SalesTrendPointVO> points) {
    }
}
