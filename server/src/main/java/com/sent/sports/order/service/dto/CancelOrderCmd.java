package com.sent.sports.order.service.dto;

/**
 * 取消订单指令：订单号（取消动作由状态机统一校验，仅 WAIT_PAY 允许）。
 */
public record CancelOrderCmd(String orderNo) {
}
