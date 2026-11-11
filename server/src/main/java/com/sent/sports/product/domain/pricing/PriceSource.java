package com.sent.sports.product.domain.pricing;

/** 取价来源（唯一）：命中第一个即成交，price_source 永远唯一，便于管理端举证。 */
public enum PriceSource {
    CUSTOMER_SKU,   // 一客一价（谈判结果，终结计算）
    AGREEMENT,      // 企业协议价（吸收阶梯）
    LADDER,         // 阶梯价
    MEMBER,         // 会员等级折扣
    RETAIL,         // 零售价（兜底）
    STACKED         // 折上折：协议价 × 阶梯折扣率（需显式开启）
}
