package com.sent.sports.common.dto;

/**
 * 阶梯区间 DTO（跨模块中立类型）：minQty 含、maxQty 含（<0 表示无上限）、rate 为百分比折扣。
 */
public record TierDto(int minQty, int maxQty, int rate) {
}
