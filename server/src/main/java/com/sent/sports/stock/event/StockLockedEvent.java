package com.sent.sports.stock.event;

/**
 * 库存预占事件（P3-02）。下单预占成功后发布，供对账/超时释放订阅。
 */
public record StockLockedEvent(long skuId, int qty) {
}
