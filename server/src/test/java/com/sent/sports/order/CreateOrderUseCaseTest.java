package com.sent.sports.order;

import com.sent.sports.common.domain.IdemKey;
import com.sent.sports.common.dto.TierDto;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customer.service.AddressService;
import com.sent.sports.customer.service.dto.AddressVO;
import com.sent.sports.customize.service.CustomizeShipGateway;
import com.sent.sports.finance.service.CreditFacade;
import com.sent.sports.member.service.PointFacade;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.domain.PayStatus;
import com.sent.sports.order.event.OrderCreatedEvent;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.CreateOrderUseCase;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.order.service.dto.OrderLineCmd;
import com.sent.sports.product.service.PricingServiceFacade;
import com.sent.sports.product.service.dto.LinePrice;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创建订单用例测试（P3-06，TDD 红）：依赖 Mock 的门面 / 仓储 / 工具，验证
 * 幂等键必填、预占失败整单回滚、价格快照落库、订单号生成。
 * 此时 CreateOrderUseCase 尚未实现，编译即失败（红）。
 */
class CreateOrderUseCaseTest extends BaseUseCaseTest {

    @Mock
    private PricingServiceFacade pricingFacade;
    @Mock
    private PointFacade pointFacade;
    @Mock
    private StockRepository stockRepository;
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

    @InjectMocks
    private CreateOrderUseCase useCase;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;
    @Captor
    private ArgumentCaptor<OrderCreatedEvent> eventCaptor;

    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");
    /** 收货地址主键：下单必须带地址，缺失/越权由用例在取价之前拦下 */
    private static final long ADDRESS_ID = 7001L;

    /** 验收地址：江苏省南京市玄武区中山路 100 号，收件人张三 */
    private static AddressVO sampleAddress() {
        return new AddressVO(ADDRESS_ID, "张三", "13800138000",
                "江苏省", "南京市", "玄武区", "中山路 100 号", true);
    }

    /** 让地址门面返回验收地址（默认行为：地址存在且属于该客户） */
    private void stubAddress() {
        when(addressService.findOwned(anyLong(), anyLong()))
                .thenReturn(java.util.Optional.of(sampleAddress()));
    }

    @Test
    void createPersistsPriceSnapshotAndPublishesEvent() {
        when(clock.instant()).thenReturn(FIXED);
        stubAddress();
        when(pricingFacade.price(any())).thenReturn(new LinePrice(9000L, "MEMBER", false));
        when(pointFacade.maxDeductibleCent(5000, 18000L)).thenReturn(5000L);
        doNothing().when(creditFacade).assertAvailable(anyLong(), anyLong());
        when(idGen.nextOrderNo()).thenReturn("SO20240101000001");

        OrderLineCmd item = new OrderLineCmd(1L, 2, "PERSON", 90, 100, false, List.of());
        CreateOrderCmd cmd = new CreateOrderCmd(100L, ADDRESS_ID, "idem-abc", 5000, 19900, 1200, List.of(item));

        CreateOrderResult result = useCase.create(cmd);

        assertNotNull(result.orderNo());
        assertEquals("WAIT_PAY", result.status());
        assertEquals(18000L + 1200L - 5000L, result.payableCent());
        assertEquals(18000L, result.goodsAmountCent());
        assertEquals(1200L, result.freightCent());
        assertEquals(5000L, result.pointDeductCent());

        verify(orderRepository).save(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertEquals("SO20240101000001", saved.getOrderNo());
        assertEquals(IdemKey.of("idem-abc").value(), saved.getIdemKey());
        assertEquals(OrderStatus.WAIT_PAY, saved.getStatus());
        assertEquals(PayStatus.UNPAID, saved.getPayStatus());
        assertEquals(18000L, saved.getGoodsAmountCent());
        assertEquals(1200L, saved.getFreightCent());
        assertEquals(5000L, saved.getPointDeductCent());
        assertEquals(18000L + 1200L - 5000L, saved.getPayableCent());
        assertEquals(1, saved.getItems().size());
        assertEquals(9000L, saved.getItems().get(0).getUnitPriceCent());
        assertEquals("MEMBER", saved.getItems().get(0).getSourceCode());
        assertEquals(18000L, saved.getItems().get(0).getLineTotalCent());

        verify(eventPublisher).publish(eventCaptor.capture());
        OrderCreatedEvent evt = eventCaptor.getValue();
        assertEquals("SO20240101000001", evt.orderNo());
        assertEquals(100L, evt.customerId());
        assertEquals(18000L + 1200L - 5000L, evt.payableCent());

        verify(stockRepository).lock(1L, 2);
        verify(idGen).nextOrderNo();
    }

    @Test
    void missingIdemKeyRejected() {
        CreateOrderCmd cmd = new CreateOrderCmd(100L, ADDRESS_ID, null, 0, 19900, 1200, List.of());
        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(stockRepository, never()).lock(anyLong(), anyInt());
    }

    @Test
    void stockFailureRollsBackEntireOrder() {
        stubAddress();
        when(pricingFacade.price(any())).thenReturn(new LinePrice(10000L, "RETAIL", false));
        // 默认预占无操作；仅第二行预占失败
        doNothing().when(stockRepository).lock(anyLong(), anyInt());
        doThrow(new BizException(ErrorCode.STOCK_NOT_ENOUGH))
                .when(stockRepository).lock(2L, 1);

        OrderLineCmd line1 = new OrderLineCmd(1L, 2, "PERSON", 100, 100, false, List.of());
        OrderLineCmd line2 = new OrderLineCmd(2L, 1, "PERSON", 100, 100, false, List.of());
        CreateOrderCmd cmd = new CreateOrderCmd(100L, ADDRESS_ID, "idem-xyz", 0, 19900, 1200, List.of(line1, line2));

        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd));
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH, ex.getErrorCode());

        // 已锁的第一行必须释放，订单不得落库
        verify(stockRepository).lock(1L, 2);
        verify(stockRepository).lock(2L, 1);
        verify(stockRepository).release(1L, 2);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void productOffShelfRejected() {
        // 取价阶段即发现 SKU 已下架（ProductPriceRepositoryImpl.loadContext 抛 PRODUCT_OFF_SHELF），
        // 此时尚未预占库存，订单不得落库。
        stubAddress();
        when(pricingFacade.price(any())).thenThrow(new BizException(ErrorCode.PRODUCT_OFF_SHELF, "商品已下架"));

        OrderLineCmd item = new OrderLineCmd(1L, 2, "PERSON", 100, 100, false, List.of());
        CreateOrderCmd cmd = new CreateOrderCmd(100L, ADDRESS_ID, "idem-off", 0, 19900, 1200, List.of(item));

        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd));
        assertEquals(ErrorCode.PRODUCT_OFF_SHELF, ex.getErrorCode());
        verify(stockRepository, never()).lock(anyLong(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void creditExceededRejected() {
        stubAddress();
        when(pricingFacade.price(any())).thenReturn(new LinePrice(10000L, "RETAIL", false));
        when(pointFacade.maxDeductibleCent(anyInt(), anyLong())).thenReturn(0L);
        // 信用额度不足：断言失败直接抛出 CREDIT_NOT_ENOUGH（用后端计算的应付比对，防金额篡改）
        doThrow(new BizException(ErrorCode.CREDIT_NOT_ENOUGH))
                .when(creditFacade).assertAvailable(anyLong(), anyLong());

        OrderLineCmd item = new OrderLineCmd(1L, 2, "PERSON", 100, 100, false, List.of());
        CreateOrderCmd cmd = new CreateOrderCmd(100L, ADDRESS_ID, "idem-credit", 0, 19900, 1200, List.of(item));

        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd));
        assertEquals(ErrorCode.CREDIT_NOT_ENOUGH, ex.getErrorCode());
        // 已预占的库存必须释放，订单不得落库
        verify(stockRepository).lock(1L, 2);
        verify(stockRepository).release(1L, 2);
        verify(orderRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // 收货地址（2026-11-15）：下单必须带地址，且下单瞬间把地址固化成快照
    // ---------------------------------------------------------------------

    @Test
    void createWithoutAddressRejected() {
        OrderLineCmd item = new OrderLineCmd(1L, 2, "PERSON", 100, 100, false, List.of());
        CreateOrderCmd cmd = new CreateOrderCmd(100L, null, "idem-no-addr", 0, 19900, 1200, List.of(item));

        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd));

        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.resolvedMessage().contains("收货地址"), "错误文案要点明缺的是收货地址");
        // 校验必须发生在取价/预占之前：没有地址时一点库存都不许占
        verify(stockRepository, never()).lock(anyLong(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createWithForeignOrMissingAddressRejected() {
        // 地址不属于当前客户（或压根不存在）→ 门面返回空，按「找不到」拒绝，避免探测他人地址
        when(addressService.findOwned(anyLong(), anyLong())).thenReturn(java.util.Optional.empty());

        OrderLineCmd item = new OrderLineCmd(1L, 2, "PERSON", 100, 100, false, List.of());
        CreateOrderCmd cmd = new CreateOrderCmd(100L, ADDRESS_ID, "idem-bad-addr", 0, 19900, 1200, List.of(item));

        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(stockRepository, never()).lock(anyLong(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createStoresAddressSnapshotJson() throws Exception {
        when(clock.instant()).thenReturn(FIXED);
        stubAddress();
        when(pricingFacade.price(any())).thenReturn(new LinePrice(5000L, "RETAIL", false));
        when(pointFacade.maxDeductibleCent(anyInt(), anyLong())).thenReturn(0L);
        when(idGen.nextOrderNo()).thenReturn("SO20240101000002");

        OrderLineCmd item = new OrderLineCmd(9L, 1, "PERSON", 100, 100, false, List.of());
        CreateOrderCmd cmd = new CreateOrderCmd(100L, ADDRESS_ID, "idem-snap", 0, 19900, 1200, List.of(item));

        useCase.create(cmd);

        verify(orderRepository).save(orderCaptor.capture());
        String json = orderCaptor.getValue().getAddressSnapshotJson();
        assertNotNull(json, "下单必须把收货地址固化进 address_snapshot_json");

        com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        assertEquals("张三", node.get("receiver").asText());
        assertEquals("13800138000", node.get("phone").asText());
        assertEquals("江苏省", node.get("province").asText());
        assertEquals("南京市", node.get("city").asText());
        assertEquals("玄武区", node.get("district").asText());
        assertEquals("中山路 100 号", node.get("detail").asText());
        // 只存快照所需字段：is_default / id 这类地址簿内部状态不进历史订单
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("isDefault"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("\"id\""));
    }
}
