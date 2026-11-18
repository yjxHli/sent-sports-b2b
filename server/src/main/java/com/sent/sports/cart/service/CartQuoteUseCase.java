package com.sent.sports.cart.service;

import com.sent.sports.common.util.FreightCalculator;
import com.sent.sports.coupon.service.CouponDeduction;
import com.sent.sports.coupon.service.dto.CouponLine;
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
    private final CouponDeduction couponDeduction;

    public CartQuoteUseCase(PricingServiceFacade pricingFacade, PointFacade pointFacade,
                            CouponDeduction couponDeduction) {
        this.pricingFacade = pricingFacade;
        this.pointFacade = pointFacade;
        this.couponDeduction = couponDeduction;
    }

    @UseCase
    public CartQuoteResult quote(CartQuoteCmd cmd) {
        long goodsAmount = 0L;
        long customFee = 0L;
        List<QuoteLine> lines = new ArrayList<>();
        List<CouponLine> couponLines = new ArrayList<>();
        for (QuoteItemCmd item : cmd.items()) {
            LinePrice lp = pricingFacade.price(toRequest(item));
            long lineTotal = lp.unitPriceCent() * item.qty();
            goodsAmount += lineTotal;
            long lineCustomFee = 0L;
            if (item.customFlag()) {
                // 与 CreateOrderUseCase 同一口径：UV 定制件 10 元/件
                lineCustomFee = 1000L * item.qty();
                customFee += lineCustomFee;
            }
            lines.add(new QuoteLine(item.skuId(), lp.unitPriceCent(), lp.sourceCode(), lp.belowCost(), lineTotal));
            couponLines.add(new CouponLine(item.skuId(), lineTotal + lineCustomFee));
        }
        long freight = FreightCalculator.calcFreight(goodsAmount, cmd.freightFreeThresholdCent(), cmd.freightFeeCent());
        long pointDeduct = pointFacade.maxDeductibleCent(cmd.availablePoints(), goodsAmount);
        // 券在积分之后抵扣，门槛口径 = 商品额 + 定制费（不含运费），与下单链路一致；
        // 传明细而非总额，品类券才能只对命中 SPU/SKU 的行金额生效
        long couponDiscount = couponDeduction.discountFor(cmd.couponId() == null ? 0L : cmd.couponId(),
                cmd.customerId(), couponLines);
        long payable = Math.max(0L, goodsAmount + customFee + freight - pointDeduct - couponDiscount);
        return new CartQuoteResult(lines, goodsAmount, freight, pointDeduct, payable,
                customFee, couponDiscount);
    }

    private PriceRequest toRequest(QuoteItemCmd item) {
        List<TierDto> tiers = item.ladderTiers() == null ? List.of() : item.ladderTiers();
        return new PriceRequest(item.skuId(), item.qty(), item.customerTypeCode(),
                item.memberDiscountRate(), item.agreementDiscountRate(), item.allowStacking(), tiers);
    }
}
