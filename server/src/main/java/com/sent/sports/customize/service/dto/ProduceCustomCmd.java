package com.sent.sports.customize.service.dto;

/**
 * 排产/完工命令（中立入参）：定制单号 + 动作(START_PRODUCTION / FINISH)。
 * plan §7.5：APPROVED 可排产，IN_PRODUCTION 可完工。
 */
public record ProduceCustomCmd(String customOrderId, String action) {
}
