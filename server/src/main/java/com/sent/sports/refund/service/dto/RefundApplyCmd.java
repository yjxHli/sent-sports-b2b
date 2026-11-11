package com.sent.sports.refund.service.dto;

import java.util.List;

/**
 * 申请退款指令（P3-19）：订单号、逐行退款金额、退款原因、退款凭证（必填）。
 */
public record RefundApplyCmd(String orderNo, List<RefundApplyLineCmd> lines, String reason, String proof) {
}
