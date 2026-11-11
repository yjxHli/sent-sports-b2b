package com.sent.sports.finance.service.dto;

import java.util.List;

/**
 * 核销结果（中立出参）：付款单号、核销总额、结清的应收 id 列表、状态。
 */
public record WriteOffResult(String paymentNo, long totalWrittenOff, List<Long> closedReceivableIds, String status) {
}
