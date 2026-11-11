package com.sent.sports.cart.service;

import com.sent.sports.common.util.FreightCalculator;
import com.sent.sports.cart.service.dto.CartQuoteCmd;
import com.sent.sports.cart.service.dto.CartQuoteResult;
import com.sent.sports.cart.service.dto.QuoteItemCmd;
import com.sent.sports.cart.service.dto.QuoteLine;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.dto.TierDto;
import com.sent.sports.member.service.PointFacade;
import com.sent.sports.product.service.PricingServiceFacade;
import com.sent.sports.product.service.dto.LinePrice;
import com.sent.sports.product.service.dto.PriceRequest;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 结算试算用例（P3-05）：编排取价、运费、积分抵扣，输出应付。
 * 顺序：逐行取价 → 累计商品额 → 算运费 → 算积分抵扣 → 应付 = 商品额 + 运费 − 积分抵扣。
 * 依赖门面（product/member service 接口），不直接依赖其他模块领域类型，符合 ArchUnit 规则 4。
 */
@Service
public class CartQuoteUseCase {

    private final PricingServiceFacade pricingFacade;
    private final PointFacade pointFacade;

    public CartQuoteUseCase(PricingServiceFacade pricingFacade, PointFacade pointFacade) {
        this.pricingFacade = pricingFacade;
        this.pointFacade = pointFacade;
    }

    @UseCase
    public CartQuoteResult quote(CartQuoteCmd cmd) {
        long goodsAmount = 0L;
        List<QuoteLine> lines = new ArrayList<>();
        for (QuoteItemCmd item : cmd.items()) {
            LinePrice lp = pricingFacade.price(toRequest(item));
            long lineTotal = lp.unitPriceCent() * item.qty();
            goodsAmount += lineTotal;
            lines.add(new QuoteLine(item.skuId(), lp.unitPriceCent(), lp.sourceCode(), lp.belowCost(), lineTotal));
        }
        long freight = FreightCalculator.calcFreight(goodsAmount, cmd.freightFreeThresholdCent(), cmd.freightFeeCent());
        long pointDeduct = pointFacade.maxDeductibleCent(cmd.availablePoints(), goodsAmount);
        long payable = goodsAmount + freight - pointDeduct;
        return new CartQuoteResult(lines, goodsAmount, freight, pointDeduct, payable);
    }

    private PriceRequest toRequest(QuoteItemCmd item) {
        List<TierDto> tiers = item.ladderTiers() == null ? List.of() : item.ladderTiers();
        return new PriceRequest(item.skuId(), item.qty(), item.customerTypeCode(),
                item.memberDiscountRate(), item.agreementDiscountRate(), item.allowStacking(), tiers);
    }
}
