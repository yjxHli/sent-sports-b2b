package com.sent.sports.report.dto;

import java.util.List;

/**
 * 拓展业务报表 DTO（2026-11-23）：客户采购结构 / 商品销售明细 / 复购新老客结构。
 *
 * <p>口径与既有报表一致：{@code orders.paid_at} 时间窗左闭右开，只算
 * {@code PAID / PART_REFUNDED}，全额退款整单剔除；数据范围由 Mapper 的
 * {@code @DataScope(alias="o")} 下推，本模块不重复判断角色。</p>
 */
public final class ReportExtensionDtos {

    private ReportExtensionDtos() {
    }

    /** 客户采购结构：三类固定齐全（企业 / 个人 / 未分类），空区间也给 0 值。 */
    public record CustomerMixVO(String fromDate, String toDate, int days,
                                long totalAmountCent, String totalAmountYuan,
                                List<CustomerMixItemVO> items) {
    }

    /**
     * @param type  ENTERPRISE / PERSON / UNKNOWN
     * @param sharePct 金额占区间总成交额的百分比，1 位小数
     */
    public record CustomerMixItemVO(String type, String label, long customerCount,
                                    long orderCount, long amountCent, String amountYuan,
                                    String sharePct) {
    }

    /** 商品销售明细：汇总口径恒为全量，items 才是 limit 截断后的列表。 */
    public record ProductDetailVO(String fromDate, String toDate, int days, int limit,
                                  long totalAmountCent, String totalAmountYuan,
                                  long totalQty, long totalProfitCent, String grossMarginPct,
                                  int itemCount, List<ProductDetailItemVO> items) {
    }

    /**
     * @param profitCent 成交额 - 销量 × 成本价（成本取 SKU 当前值，历史成本不追溯）
     * @param priceSources 区间内该 SKU 出现过的价格来源，逗号分隔去重
     * @param stockQty 可用库存 = physical - locked - pending
     */
    public record ProductDetailItemVO(int rank, long skuId, String skuName, String spec,
                                      long qty, long amountCent, String amountYuan,
                                      long profitCent, String profitYuan, String grossMarginPct,
                                      String priceSources, int stockQty, int orderCount) {
    }

    /**
     * 复购结构：以「区间前是否已有成交」区分复购客与新客，人数与金额口径均为区间内；
     * {@code frequencyBuckets} 把复购客按历史总订单数分三档，用来解释复购率的构成。
     */
    public record RepeatCustomerVO(String fromDate, String toDate, int days,
                                   long totalCustomerCount, long newCustomerCount,
                                   long repeatCustomerCount, String repeatRatePct,
                                   long newAmountCent, String newAmountYuan,
                                   long repeatAmountCent, String repeatAmountYuan,
                                   String avgOrderCount,
                                   List<FrequencyBucketVO> frequencyBuckets) {
    }

    /**
     * @param label 档位文案（2 单 / 3–5 单 / 6 单以上）
     * @param sharePct 该档人数占复购客总数的百分比
     */
    public record FrequencyBucketVO(String label, long customerCount,
                                    long amountCent, String amountYuan, String sharePct) {
    }
}
