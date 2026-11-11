package com.sent.sports.stock.event;

/**
 * 入库事件（P3-31）：补货入库后触发，由通知模块监听并向"到货提醒"订阅用户发站内信（spec §8.3 / A19）。
 * 此处仅作为跨模块解耦载体；一次性提醒、不重复推送由监听者保证。
 */
public record StockInboundEvent(long skuId, long changeQty, long afterPhysicalQty) {
}
