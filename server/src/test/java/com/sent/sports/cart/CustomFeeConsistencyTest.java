package com.sent.sports.cart;

import com.sent.sports.cart.service.CartQuoteUseCase;
import com.sent.sports.cart.service.dto.CartQuoteCmd;
import com.sent.sports.cart.service.dto.CartQuoteResult;
import com.sent.sports.cart.service.dto.QuoteItemCmd;
import com.sent.sports.common.dto.TierDto;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.coupon.service.CouponDeduction;
import com.sent.sports.customer.service.AddressService;
import com.sent.sports.customer.service.dto.AddressVO;
import com.sent.sports.customize.service.CustomFeeFacade;
import com.sent.sports.customize.service.CustomizeShipGateway;
import com.sent.sports.customize.service.impl.CustomFeeFacadeImpl;
import com.sent.sports.finance.service.CreditFacade;
import com.sent.sports.member.service.PointFacade;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.order.service.dto.OrderLineCmd;
import com.sent.sports.product.service.PricingServiceFacade;
import com.sent.sports.product.service.dto.LinePrice;
import com.sent.sports.stock.service.StockService;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 定制费一致性验收（P0-R6）：<b>购物车试算金额 == 最终下单实付金额</b>。
 *
 * <p>两个用例都注入同一个计费门面（委托 {@code CustomFeeCalculator}），配置取
 * {@code app.custom-fee.single-cent}。本用例故意用 1200 分/件（而不是历史常量 1000），
 * 这样任何一处残留的 {@code 1000L} 都会立刻让两边的应付金额分叉、断言失败。</p>
 */
class CustomFeeConsistencyTest extends BaseUseCaseTest {

    private static final long SKU_ID = 9102L;
    private static final long UNIT_PRICE_CENT = 5000L;
    private static final int QTY = 3;
    private static final long CUSTOM_UNIT_CENT = 1200L;
    private static final long EXPECTED_CUSTOM_FEE = CUSTOM_UNIT_CENT * QTY;
    private static final long EXPECTED_PAYABLE = UNIT_PRICE_CENT * QTY + EXPECTED_CUSTOM_FEE;
    private static final long ADDRESS_ID = 7001L;
    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");

    @Mock
    private PricingServiceFacade pricingFacade;
    @Mock
    private PointFacade pointFacade;
    @Mock
    private CouponDeduction couponDeduction;
    @Mock
    private StockService stockService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private CustomizeShipGateway customizeShipGateway;
    @Mock
    private CreditFacade creditFacade;
    @Mock
    private AddressService addressService;
    @Mock
    private java.time.Clock clock;

    /** 真实计费门面：单价 1200 分/件（配置驱动，非硬编码 1000L）。 */
    private final CustomFeeFacade customFeeFacade = new CustomFeeFacadeImpl(CUSTOM_UNIT_CENT);

    @Test
    @DisplayName("定制件：购物车试算应付 == 下单实付 == 商品额 + 引擎算出的定制费")
    void cartQuoteMatchesCreatedOrderForCustomLine() {
        long quotePayable = quotePayable();
        CreateOrderResult created = createOrder();
        Order saved = lastSavedOrder();

        assertEquals(EXPECTED_PAYABLE, quotePayable, "试算应付必须包含计费引擎给出的定制费");
        assertEquals(EXPECTED_PAYABLE, created.payableCent(), "下单实付必须与试算同口径");
        assertEquals(EXPECTED_PAYABLE, saved.getPayableCent(), "落库订单应付也要一致");
        assertEquals(EXPECTED_CUSTOM_FEE, saved.getCustomFeeCent(), "订单头定制费总额");
        assertEquals(EXPECTED_CUSTOM_FEE, saved.getItems().get(0).getCustomFeeCent(),
                "订单行也要固化同一笔定制费");
    }

    @Test
    @DisplayName("非定制行不加收定制费，两边仍是同一口径")
    void noCustomFeeForPlainLine() {
        assertEquals(UNIT_PRICE_CENT * QTY, quotePayableWhenCustom(false));
        assertEquals(0L, customFeeFacade.feeCent(0), "0 件不收费");
    }

    private long quotePayable() {
        return quotePayableWhenCustom(true);
    }

    private long quotePayableWhenCustom(boolean custom) {
        CartQuoteUseCase quote = new CartQuoteUseCase(pricingFacade, pointFacade, couponDeduction,
                customFeeFacade);
        when(pricingFacade.price(any())).thenReturn(new LinePrice(UNIT_PRICE_CENT, "RETAIL", false));
        when(pointFacade.maxDeductibleCent(0, UNIT_PRICE_CENT * QTY)).thenReturn(0L);
        when(couponDeduction.discountFor(anyLong(), anyLong(), any())).thenReturn(0L);

        CartQuoteCmd cmd = new CartQuoteCmd(0, 19900, 0,
                List.of(quoteLine(custom)));
        CartQuoteResult result = quote.quote(cmd);
        return result.payableCent();
    }

    private CreateOrderResult createOrder() {
        when(clock.instant()).thenReturn(FIXED);
        when(addressService.findOwned(anyLong(), any())).thenReturn(Optional.of(new AddressVO(
                ADDRESS_ID, "验收客户", "13800138000", "江苏省", "南京市", "玄武区", "中山路 100 号", true)));
        when(pricingFacade.price(any())).thenReturn(new LinePrice(UNIT_PRICE_CENT, "RETAIL", false));
        when(pointFacade.maxDeductibleCent(anyInt(), anyLong())).thenReturn(0L);
        when(couponDeduction.discountFor(anyLong(), anyLong(), any())).thenReturn(0L);
        when(idGen.nextOrderNo()).thenReturn("SO-CUSTOM-FEE-1");

        CreateOrderUseCase create = new CreateOrderUseCase(pricingFacade, pointFacade, stockService,
                orderRepository, idGen, eventPublisher, customizeShipGateway, creditFacade,
                addressService, couponDeduction, customFeeFacade, clock);
        CreateOrderCmd cmd = new CreateOrderCmd(100L, ADDRESS_ID, "idem-custom-fee-consistency", 0,
                19900, 0, List.of(orderLine(true)));
        return create.create(cmd);
    }

    private Order lastSavedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        org.mockito.Mockito.verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    private QuoteItemCmd quoteLine(boolean custom) {
        return new QuoteItemCmd(SKU_ID, QTY, "PERSON", 100, 100, false, List.<TierDto>of(), custom);
    }

    private OrderLineCmd orderLine(boolean custom) {
        return new OrderLineCmd(SKU_ID, QTY, "PERSON", 100, 100, false, List.<TierDto>of(), custom);
    }
}
