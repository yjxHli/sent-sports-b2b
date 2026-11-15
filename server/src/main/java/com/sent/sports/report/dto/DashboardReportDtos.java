package com.sent.sports.report.dto;

/**
 * 销售业绩排行与工作台看板 DTO。
 *
 * <p>口径与 {@code SalesTrendDtos} 一致：以 {@code orders.paid_at} 为时间基准，
 * 严格按 {@code SUM(paid_cent)} 聚合，只计 {@code PAID / PART_REFUNDED}，
 * 全额退款与未支付订单整单剔除，保证销售报表与财务台账金额可比。</p>
 */
public final class DashboardReportDtos {

    private DashboardReportDtos() {
    }

    /**
     * 排行行（spec §9.2：维度销售员，指标归属客户数 / 成交订单数 / 成交金额）。
     * <p>{@code staffId} 为 {@code orders.owner_staff_id}；未分配归属的行 staffId 为 null、
     * name 统一显示「未分配」，避免把无归属订单悄悄丢掉导致排行合计与趋势对不上。</p>
     */
    public record SalesRankVO(int rank, Long staffId, String name,
                              int customerCount, int orderCount, long amountCent) {
    }

    /** 工作台六张指标卡 + 角色化待办数量，全部由真库聚合，前端不做任何伪造或补全。 */
    public record DashboardSummaryVO(
            String date,
            long todayOrderCount,
            long todayAmountCent,
            long pendingShipCount,
            long pendingCustomReviewCount,
            long pendingChatCount,
            long warningSkuCount,
            long pendingRefundCount,
            long overdueReceivableCount) {
    }
}
