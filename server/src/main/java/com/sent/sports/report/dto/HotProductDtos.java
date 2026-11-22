package com.sent.sports.report.dto;

import java.util.List;

/**
 * 热销商品报表 DTO（2026-11-21）。
 *
 * <p>口径与销售趋势一致：以 {@code orders.paid_at} 为时间基准，只统计
 * {@code pay_status IN ('PAID','PART_REFUNDED')}，全额退款整单剔除；
 * 商品维度按 {@code order_item.sku_id} 聚合数量与行金额。</p>
 *
 * <p>{@code totalAmountCent} / {@code totalQty} / {@code skuCount} 恒为<b>区间内全量</b>口径，
 * 不随 {@code limit} 变化 —— 占比的分母必须是全量，否则 Top N 的百分比会互相"变大"。</p>
 */
public final class HotProductDtos {

    private HotProductDtos() {
    }

    /**
     * @param items Top N（按成交金额降序），前端饼图取前 5、排行表取更多
     */
    public record HotProductVO(String fromDate, String toDate, int days, int limit,
                               long totalAmountCent, String totalAmountYuan,
                               long totalQty, int skuCount,
                               List<HotProductItemVO> items) {
    }

    /**
     * @param sharePct 该 SKU 成交金额占区间总成交金额的百分比，保留 1 位小数（字符串，避免浮点序列化噪音）
     */
    public record HotProductItemVO(int rank, long skuId, String skuName, String spec,
                                   long qty, long amountCent, String amountYuan,
                                   int orderCount, String sharePct) {
    }
}
