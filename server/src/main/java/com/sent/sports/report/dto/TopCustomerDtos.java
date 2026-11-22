package com.sent.sports.report.dto;

import java.util.List;

/**
 * 大客户采购排行 DTO（2026-11-21）。
 *
 * <p>口径与销售趋势 / 热销商品一致：{@code orders.paid_at} 时间窗，
 * 只计 {@code pay_status IN ('PAID','PART_REFUNDED')}，全额退款整单剔除；
 * 客户维度按 {@code orders.customer_id} 聚合订单数与实付金额。</p>
 *
 * <p>{@code totalAmountCent} / {@code customerCount} 为区间全量口径，不随 {@code limit} 变化，
 * 占比分母因此稳定。</p>
 */
public final class TopCustomerDtos {

    private TopCustomerDtos() {
    }

    public record TopCustomerVO(String fromDate, String toDate, int days, int limit,
                                long totalAmountCent, String totalAmountYuan,
                                long totalOrderCount, int customerCount,
                                List<TopCustomerItemVO> items) {
    }

    /**
     * @param customerName 企业客户取 company_name，否则昵称，再回落「客户#{id}」
     * @param phone        明文手机号，展示层脱敏
     */
    public record TopCustomerItemVO(int rank, long customerId, String customerName, String phone,
                                    int orderCount, long amountCent, String amountYuan,
                                    String sharePct) {
    }
}
