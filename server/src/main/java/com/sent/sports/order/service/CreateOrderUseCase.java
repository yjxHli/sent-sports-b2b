package com.sent.sports.order.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.domain.IdemKey;
import com.sent.sports.common.dto.TierDto;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.common.util.FreightCalculator;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customer.service.AddressService;
import com.sent.sports.customer.service.dto.AddressVO;
import com.sent.sports.customize.service.CustomizeShipGateway;
import com.sent.sports.finance.service.CreditFacade;
import com.sent.sports.member.service.PointFacade;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.event.OrderCreatedEvent;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.dto.CreateOrderCmd;
import com.sent.sports.order.service.dto.CreateOrderResult;
import com.sent.sports.order.service.dto.OrderLineCmd;
import com.sent.sports.product.service.PricingServiceFacade;
import com.sent.sports.product.service.dto.LinePrice;
import com.sent.sports.product.service.dto.PriceRequest;
import com.sent.sports.stock.repository.StockRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 创建订单用例（P3-07）：顺序 校验 → 取价 → 预占 → 落库 → 发布事件。
 * 关键不变量：
 * 1) 幂等键必填（缺失抛 PARAM_INVALID）；
 * 2) 预占失败整单回滚（释放已锁库存后原样抛出）；
 * 3) 价格快照随订单固化；
 * 4) 订单号由 IdGen 生成，初始状态 WAIT_PAY / UNPAID。
 * 依赖门面 / 仓储端口，不直接依赖其他模块领域类型，符合 ArchUnit 规则 4。
 */
@Service
public class CreateOrderUseCase {

    private final PricingServiceFacade pricingFacade;
    private final PointFacade pointFacade;
    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final IdGen idGen;
    private final DomainEventPublisher eventPublisher;
    private final CustomizeShipGateway customizeShipGateway;
    private final CreditFacade creditFacade;
    /** 收货地址来源：只经 customer 的 service 接口读取，不碰对方 mapper（ArchUnit 规则 4）。 */
    private final AddressService addressService;
    private final Clock clock;

    /** 快照落库用：字段少、结构固定，静态实例足够，不必注入 Spring 的 ObjectMapper。 */
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    public CreateOrderUseCase(PricingServiceFacade pricingFacade, PointFacade pointFacade,
                             StockRepository stockRepository, OrderRepository orderRepository,
                             IdGen idGen, DomainEventPublisher eventPublisher,
                             CustomizeShipGateway customizeShipGateway, CreditFacade creditFacade,
                             AddressService addressService, Clock clock) {
        this.pricingFacade = pricingFacade;
        this.pointFacade = pointFacade;
        this.stockRepository = stockRepository;
        this.orderRepository = orderRepository;
        this.idGen = idGen;
        this.eventPublisher = eventPublisher;
        this.customizeShipGateway = customizeShipGateway;
        this.creditFacade = creditFacade;
        this.addressService = addressService;
        this.clock = clock;
    }

    @UseCase
    public CreateOrderResult create(CreateOrderCmd cmd) {
        // 1) 幂等键必填（格式 / 非空校验，缺失抛 PARAM_INVALID）
        IdemKey idemKey = IdemKey.of(cmd.idemKey());

        // 1b) 收货地址必填，且必须属于下单客户——这两步都在取价 / 预占之前，
        //     没有地址就不该占用库存，否则用户要等超时释放才能再下单。
        String addressSnapshot = resolveAddressSnapshot(cmd);

        // 2) ~ 5) 取价、预占、落库、发事件同处一个补偿边界：
        // 幂等键冲突（uk_order_idem）也发生在此边界内，必须释放已预占的库存，
        // 否则重放请求会白白吃掉可用量（P5-50 集成测试覆盖该场景）。
        long goodsAmount = 0L;
        List<OrderItem> lines = new ArrayList<>();
        List<long[]> locked = new ArrayList<>();
        try {
            for (OrderLineCmd item : cmd.items()) {
                LinePrice lp = pricingFacade.price(toRequest(item));
                long lineTotal = lp.unitPriceCent() * item.qty();
                goodsAmount += lineTotal;
                lines.add(new OrderItem(item.skuId(), item.qty(), lp.unitPriceCent(), lp.sourceCode(),
                        lineTotal, item.customFlag()));
                stockRepository.lock(item.skuId(), item.qty());
                locked.add(new long[]{item.skuId(), item.qty()});
            }

            // 3) 运费 + 积分抵扣 + 应付
            long freight = FreightCalculator.calcFreight(goodsAmount, cmd.freightFreeThresholdCent(), cmd.freightFeeCent());
            long pointDeduct = pointFacade.maxDeductibleCent(cmd.availablePoints(), goodsAmount);
            long payable = goodsAmount + freight - pointDeduct;

            // 3b) 信用额度软预检：用的是后端计算的应付，而非客户端传入值（防金额篡改）。
            //      占用发生在结算环节（OrderSettlementServiceImpl），此处只做下单前的乐观拦截。
            creditFacade.assertAvailable(cmd.customerId(), payable);

            // 4) 订单号 + 聚合 + 落库
            String orderNo = idGen.nextOrderNo();
            long now = clock.instant().toEpochMilli();
            Order order = new Order(orderNo, cmd.customerId(), idemKey.value(), lines,
                    goodsAmount, 0L, 0L, freight, pointDeduct, payable, now);
            order.setAddressSnapshotJson(addressSnapshot);
            orderRepository.save(order);

            // 4b) UV 定制件在下单时即生成 custom_order（WAIT_DRAFT），客户可立即上传图稿走审图；
            //     发货侧仍会再调一次，INSERT IGNORE + uk_custom_order_item 保证幂等，不重复建单。
            customizeShipGateway.createCustomOrdersForShipment(orderNo);

            // 5) 发布领域事件
            eventPublisher.publish(new OrderCreatedEvent(orderNo, cmd.customerId(), payable, now));

            return new CreateOrderResult(orderNo, order.getStatus().name(), payable, goodsAmount, freight, pointDeduct);
        } catch (RuntimeException e) {
            for (long[] l : locked) {
                stockRepository.release(l[0], (int) l[1]);
            }
            throw e;
        }
    }

    /**
     * 把下单时选中的地址固化成 JSON 快照。
     *
     * <p>为什么存快照而不是只存 addressId：用户过几天改了地址簿、删了那条地址，
     * 历史订单的收货人 / 电话 / 地址就会跟着变，发票抬头、退款单、物流面单全部对不上。
     * 快照是「这一刻的事实」，与地址簿解耦。</p>
     *
     * <p>只放派送需要的六个字段：地址簿主键与 is_default 属于内部状态，不进历史订单。</p>
     */
    private String resolveAddressSnapshot(CreateOrderCmd cmd) {
        if (cmd.addressId() == null || cmd.addressId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先选择收货地址");
        }
        AddressVO address = addressService.findOwned(cmd.customerId(), cmd.addressId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "收货地址不存在"));
        Map<String, String> snapshot = new LinkedHashMap<>(8);
        snapshot.put("receiver", address.receiver());
        snapshot.put("phone", address.phone());
        snapshot.put("province", address.province());
        snapshot.put("city", address.city());
        snapshot.put("district", address.district());
        snapshot.put("detail", address.detail());
        try {
            return SNAPSHOT_MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            // 结构固定的六个字符串理论上不会失败；真失败也不能带着空快照下单
            throw new BizException(ErrorCode.PARAM_INVALID, "收货地址格式异常，请重新选择");
        }
    }

    private PriceRequest toRequest(OrderLineCmd item) {
        List<TierDto> tiers = item.ladderTiers() == null ? List.of() : item.ladderTiers();
        return new PriceRequest(item.skuId(), item.qty(), item.customerTypeCode(),
                item.memberDiscountRate(), item.agreementDiscountRate(), item.allowStacking(), tiers);
    }
}
