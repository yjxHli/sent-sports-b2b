package com.sent.sports.product.service.impl;

import com.sent.sports.common.dto.TierDto;
import com.sent.sports.product.domain.pricing.AgreementInfo;
import com.sent.sports.product.domain.pricing.CustomerType;
import com.sent.sports.product.domain.pricing.LadderTier;
import com.sent.sports.product.domain.pricing.PriceContext;
import com.sent.sports.product.domain.pricing.PriceResult;
import com.sent.sports.product.domain.pricing.PricingService;
import com.sent.sports.product.repository.ProductPriceRepository;
import com.sent.sports.product.service.PricingServiceFacade;
import com.sent.sports.product.service.dto.LinePrice;
import com.sent.sports.product.service.dto.PriceRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 取价门面实现：基础价（零售/成本/底价/协议/一客一价/阶梯）从仓储加载，
 * 请求级折扣率/阶梯/折上折开关由调用方覆盖，最后调用领域取价引擎返回中立 DTO。
 * 仓储实现见 Phase 5。
 */
@Service
public class PricingServiceFacadeImpl implements PricingServiceFacade {

    private final ProductPriceRepository priceRepository;
    private final PricingService pricingService = new PricingService();

    public PricingServiceFacadeImpl(ProductPriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    @Override
    public LinePrice price(PriceRequest req) {
        PriceContext base = priceRepository.loadContext(req.skuId());
        // 前端未显式传折扣率时 Jackson 会把 int 反序列化成 0；0 在定价领域是「一折都不给」，
        // 会把金额算成 0 并触发 BELOW_FLOOR_PRICE。这里把 <=0 归一为 100（不打折），
        // 保证 /api/cart/checkout-preview 与下单都能拿到真实金额。
        int memberRate = req.memberDiscountRate() > 0 ? req.memberDiscountRate() : 100;
        int agreementRate = req.agreementDiscountRate() > 0 ? req.agreementDiscountRate() : 100;
        PriceContext ctx = PriceContext.builder()
                .customerType(toCustomerType(req.customerTypeCode()))
                .memberDiscountRate(memberRate)
                .agreement(new AgreementInfo(agreementRate, true))
                .customerSkuPriceCent(base.getCustomerSkuPriceCent())
                .retailPriceCent(base.getRetailPriceCent())
                .floorPriceCent(base.getFloorPriceCent())
                .costPriceCent(base.getCostPriceCent())
                .ladderTiers(toLadderTiers(req.ladderTiers()))
                .qty(req.qty())
                .allowStacking(req.allowStacking())
                .build();
        PriceResult pr = pricingService.calc(ctx);
        return new LinePrice(pr.getUnitPriceCent(), pr.getSource().name(), pr.isBelowCost());
    }

    private CustomerType toCustomerType(String code) {
        if (code == null) {
            return CustomerType.PERSON;
        }
        try {
            return CustomerType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CustomerType.PERSON;
        }
    }

    private List<LadderTier> toLadderTiers(List<TierDto> tiers) {
        List<LadderTier> result = new ArrayList<>();
        if (tiers == null) {
            return result;
        }
        for (TierDto t : tiers) {
            result.add(new LadderTier(t.minQty(), t.maxQty(), t.rate()));
        }
        return result;
    }
}
