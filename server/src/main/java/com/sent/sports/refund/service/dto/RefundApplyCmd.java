package com.sent.sports.refund.service.dto;

import java.util.List;

/**
 * 申请退款指令（P3-19）：订单号、逐行退款金额、退款原因、退款凭证（必填）、凭证图片 fileId 列表。
 *
 * @param images 用户上传的实物照片 fileId（由文件服务返回），审核端据此核验；无图给空列表或 null
 */
public record RefundApplyCmd(String orderNo, List<RefundApplyLineCmd> lines, String reason, String proof,
                             List<String> images) {
}
