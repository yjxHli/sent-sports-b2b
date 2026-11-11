package com.sent.sports.order.service.dto;

/**
 * 管理端关单命令。
 *
 * @param orderNo    业务单号（不是数据库主键）
 * @param reason     关闭原因，必填，会写入 sys_operation_log.reason
 * @param operatorId 操作人（Web 请求上下文存在时由 AuthFilter 注入的 CurrentUser 提供；Job/单测可为 null）
 */
public record CloseOrderCmd(String orderNo, String reason, Long operatorId) {
}
