package com.sent.sports.cart;

import com.sent.sports.cart.service.CartQuoteUseCase;
import com.sent.sports.cart.service.dto.CartQuoteCmd;
import com.sent.sports.cart.service.dto.CartQuoteResult;
import com.sent.sports.cart.service.dto.QuoteItemCmd;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.coupon.service.CouponDeduction;
import com.sent.sports.customer.service.AddressService;
import com.sent.sports.customer.service.CustomerPointService;
import com.sent.sports.customer.service.dto.AddressVO;
import com.sent.sports.customize.service.CustomFeeFacade;
import com.sent.sports.customize.service.CustomizeShipGateway;
import com.sent.sports.finance.service.CreditFacade;
import com.sent.sports.member.service.PointFacade;
import com.sent.sports.member.service.impl.PointFacadeImpl;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.repository.OrderRepositoryImpl;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.order.service.dto.OrderLineCmd;
import com.sent.sports.product.service.PricingServiceFacade;
import com.sent.sports.product.service.dto.LinePrice;
import com.sent.sports.stock.service.StockService;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分抵扣 30% 上限的全链路验收：把真实的 {@link PointFacadeImpl}（委托 {@code PointDeduction}）
 * 同时注入购物车试算与下单用例，再用真实仓储把订单头转成 {@link OrderPO}，
 * 断言「试算抵扣 == 下单抵扣 == orders.point_deduct_cent」。
 *
 * <p>既有的两个用例测试各自 Mock 了 PointFacade，无法发现某一侧改用另一套算法；
 * 这里用真实领域实现把两侧钉死在同一口径上。</p>
 */
class PointDeductionChainTest extends BaseUseCaseTest {

    private static final long SKU_ID = 50L;
    private static final long GOODS_CENT = 10_000L;
    /** 100000 积分远超上限，确保触发 30% 收口而不是受积分余额限制。 */
    private static final int AVAILABLE_POINTS = 100_000;
    private static final long CAP_CENT = GOODS_CENT * 30L / 100L;
    private static final long ADDRESS_ID = 7001L;

    @Mock
    private PricingServiceFacade pricingFacade;
    @Mock
    private CouponDeduction couponDeduction;
    @Mock
    private CustomFeeFacade customFeeFacade;
    @Mock
    private CustomerPointService customerPointService;
    @Mock
    private StockService stockService;
    @Mock
    private OrderMapper orderMapper;
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

    private PointFacade pointFacade;
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        // 真实门面 → 真实领域计算；仓储真实参与 domain ↔ PO 转换
        pointFacade = new PointFacadeImpl(customerPointService);
        orderRepository = new OrderRepositoryImpl(orderMapper);
    }

    @Test
    @DisplayName("30% 上限：试算抵扣 == 下单抵扣 == 订单头落库值，且不超过应付基数")
    void capFlowsThroughQuoteOrderAndPersistence() {
        stubCommon();
        long expectedPayable = GOODS_CENT - CAP_CENT; // 满阈值包邮

        CartQuoteResult quote = quoteUseCase().quote(new CartQuoteCmd(
                AVAILABLE_POINTS, 10_000, 1200, List.of(new QuoteItemCmd(
                        SKU_ID, 1, "PERSON", 100, 100, false, List.of()))));
        CreateOrderResult created = createUseCase().create(new CreateOrderCmd(100L, ADDRESS_ID,
                "idem-point-chain", AVAILABLE_POINTS, 10_000, 1200,
                List.of(new OrderLineCmd(SKU_ID, 1, "PERSON", 100, 100, false, List.of()))));

        OrderPO persisted = persistedOrder();
        assertEquals(CAP_CENT, quote.pointDeductCent(), "试算必须走领域 30% 上限");
        assertEquals(CAP_CENT, created.pointDeductCent(), "下单必须走同一领域上限");
        assertEquals(CAP_CENT, persisted.getPointDeductCent().longValue(),
                "orders.point_deduct_cent 必须记录限流后的真实抵扣额");
        assertEquals(expectedPayable, quote.payableCent());
        assertEquals(quote.payableCent(), created.payableCent(), "试算与实付不得分叉");
        assertEquals(created.payableCent(), persisted.getPayableCent().longValue());
        assertTrue(persisted.getPointDeductCent() <= persisted.getGoodsAmountCent()
                        + persisted.getFreightCent() - persisted.getDiscountCent(),
                "落库抵扣额不得超过抵扣前的应付基数");
    }

    @Test
    @DisplayName("券额吃掉应付基数时，试算与落库都用收口后的真实抵扣额")
    void bothSidesClampToRemainingAfterCoupon() {
        stubCommon();
        when(couponDeduction.discountFor(anyLong(), anyLong(), anyList())).thenReturn(8_000L);
        long expectedPointDeduct = GOODS_CENT - 8_000L; // 3000 上限被券后余额 2000 收口

        CartQuoteResult quote = quoteUseCase().quote(new CartQuoteCmd(
                AVAILABLE_POINTS, 10_000, 1200, List.of(new QuoteItemCmd(
                        SKU_ID, 1, "PERSON", 100, 100, false, List.of()))));
        createUseCase().create(new CreateOrderCmd(100L, ADDRESS_ID, "idem-point-chain-coupon",
                AVAILABLE_POINTS, 10_000, 1200,
                List.of(new OrderLineCmd(SKU_ID, 1, "PERSON", 100, 100, false, List.of()))));

        OrderPO persisted = persistedOrder();
        assertEquals(expectedPointDeduct, quote.pointDeductCent());
        assertEquals(expectedPointDeduct, persisted.getPointDeductCent().longValue());
        assertEquals(0L, quote.payableCent());
        assertEquals(quote.payableCent(), persisted.getPayableCent().longValue());
    }

    private void stubCommon() {
        when(pricingFacade.price(any())).thenReturn(new LinePrice(GOODS_CENT, "RETAIL", false));
        when(addressService.findOwned(anyLong(), anyLong())).thenReturn(Optional.of(new AddressVO(
                ADDRESS_ID, "张三", "13800138000", "江苏省", "南京市", "玄武区", "中山路 100 号", true)));
        when(idGen.nextOrderNo()).thenReturn("SO-POINT-CHAIN-1");
        when(orderMapper.existsByOrderNo(anyString())).thenReturn(0);
        // MyBatis-Plus 真实 insert 会回填自增主键；mock 里手工补上，否则订单行拿不到 order_id
        when(orderMapper.insert(any(OrderPO.class))).thenAnswer(inv -> {
            OrderPO po = inv.getArgument(0);
            po.setId(1L);
            return 1;
        });
        when(orderMapper.insertItem(any())).thenReturn(1);
    }

    private CartQuoteUseCase quoteUseCase() {
        return new CartQuoteUseCase(pricingFacade, pointFacade, couponDeduction, customFeeFacade);
    }

    private CreateOrderUseCase createUseCase() {
        return new CreateOrderUseCase(pricingFacade, pointFacade, stockService, orderRepository,
                idGen, eventPublisher, customizeShipGateway, creditFacade, addressService,
                couponDeduction, customFeeFacade, fixedClock());
    }

    private OrderPO persistedOrder() {
        ArgumentCaptor<OrderPO> captor = ArgumentCaptor.forClass(OrderPO.class);
        verify(orderMapper).insert(captor.capture());
        return captor.getValue();
    }
}
