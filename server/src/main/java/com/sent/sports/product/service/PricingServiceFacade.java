package com.sent.sports.product.service;

import com.sent.sports.product.service.dto.LinePrice;
import com.sent.sports.product.service.dto.PriceRequest;

/**
 * 取价门面（跨模块契约）：其他模块（cart/order）通过此接口取价，
 * 不直接依赖 product 领域类型，满足 ArchUnit 规则 4（跨模块只走 service）。
 */
public interface PricingServiceFacade {
    LinePrice price(PriceRequest req);
}
