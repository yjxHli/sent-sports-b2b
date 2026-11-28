package com.sent.sports.finance.service.dto;

import java.time.LocalDate;

/**
 * 逾期提醒所需的最小数据集（finance → job 的服务层契约，不暴露 mapper PO）。
 *
 * @param receivableId 应收 ID（markOverdue 的入参）
 * @param customerId   收件客户
 * @param amountCent   应收总额（分）
 * @param dueDate      到期日
 * @param orderNo      关联订单号
 */
public record OverdueReceivable(long receivableId, long customerId, long amountCent,
                                LocalDate dueDate, String orderNo) {
}
