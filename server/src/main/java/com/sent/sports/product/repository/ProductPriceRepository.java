package com.sent.sports.product.repository;

import com.sent.sports.product.domain.pricing.PriceContext;

/**
 * 商品价格仓储端口：加载 SKU 的取价上下文（零售/成本/底价/协议/一客一价/阶梯）。
 * 实现见 Phase 5（MyBatis 仓储），Phase 3 由门面依赖此端口。
 */
public interface ProductPriceRepository {
    PriceContext loadContext(long skuId);
}
