package com.sent.sports.cart;

import com.sent.sports.cart.service.CartQuoteUseCase;
import com.sent.sports.cart.service.dto.CartQuoteCmd;
import com.sent.sports.cart.service.dto.CartQuoteResult;
import com.sent.sports.cart.service.dto.QuoteItemCmd;
import com.sent.sports.cart.service.dto.QuoteLine;
import com.sent.sports.common.dto.TierDto;
import com.sent.sports.member.service.PointFacade;
import com.sent.sports.product.service.PricingServiceFacade;
import com.sent.sports.product.service.dto.LinePrice;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 结算试算用例测试（P3-04，TDD 红）：依赖 Mock 的门面，验证编排顺序与金额计算。
 * 此时 CartQuoteUseCase 尚未实现，编译即失败（红）。
 */
class CartQuoteUseCaseTest extends BaseUseCaseTest {

    @Mock
    private PricingServiceFacade pricingFacade;
    @Mock
    private PointFacade pointFacade;

    @InjectMocks
    private CartQuoteUseCase useCase;

    @Test
    void quoteCallsPricingShippingPointsAndPayable() {
        QuoteItemCmd item = new QuoteItemCmd(1L, 2, "PERSON", 90, 100, false, List.of());
        when(pricingFacade.price(any())).thenReturn(new LinePrice(9000L, "MEMBER", false));

        // 商品总额 = 9000 * 2 = 18000 分（< 19900 阈值）→ 收运费 1200 分
        // 积分：可用 5000 分折 5000 分，上限 18000*30%=5400 → 可抵扣 5000 分
        when(pointFacade.maxDeductibleCent(5000, 18000L)).thenReturn(5000L);

        CartQuoteCmd cmd = new CartQuoteCmd(5000, 19900, 1200, List.of(item));
        CartQuoteResult result = useCase.quote(cmd);

        assertEquals(1, result.lines().size());
        QuoteLine line = result.lines().get(0);
        assertEquals(1L, line.skuId());
        assertEquals(9000L, line.unitPriceCent());
        assertEquals(18000L, line.lineTotalCent());

        assertEquals(18000L, result.goodsAmountCent());
        assertEquals(1200L, result.freightCent());
        assertEquals(5000L, result.pointDeductCent());
        assertEquals(18000L + 1200L - 5000L, result.payableCent());
    }

    @Test
    void quoteFreeShippingWhenGoodsReachThreshold() {
        QuoteItemCmd item = new QuoteItemCmd(2L, 3, "PERSON", 100, 100, false, List.of());
        when(pricingFacade.price(any())).thenReturn(new LinePrice(10000L, "RETAIL", false));
        // 10000 * 3 = 30000 >= 19900 → 包邮；无积分 → 应付 = 商品额
        when(pointFacade.maxDeductibleCent(0, 30000L)).thenReturn(0L);

        CartQuoteCmd cmd = new CartQuoteCmd(0, 19900, 1200, List.of(item));
        CartQuoteResult result = useCase.quote(cmd);

        assertEquals(30000L, result.goodsAmountCent());
        assertEquals(0L, result.freightCent());
        assertEquals(30000L, result.payableCent());
    }

    @Test
    void quoteEmptyCartHasZeroFreight() {
        CartQuoteCmd cmd = new CartQuoteCmd(0, 19900, 1200, List.of());
        CartQuoteResult result = useCase.quote(cmd);

        assertEquals(0L, result.goodsAmountCent());
        assertEquals(0L, result.freightCent());
        assertEquals(0L, result.payableCent());
    }
}
