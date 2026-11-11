package com.sent.sports.order.service.dto;

/**
 * 管理端关单结果。
 *
 * @param refundNo      本次生成的全额退款单号
 * @param refundedCent  实退金额（分）
 * @param payableCent   订单应付（分），供管理端 VO 复用
 * @param stockReleased 是否释放了预占库存（仅 WAIT_SHIP 整单未发时为 true）
 */
public record CloseOrderResult(String orderNo, String status, String refundNo,
                               long refundedCent, long payableCent, boolean stockReleased) {
}
