package com.sent.sports.order.repository;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.domain.PayStatus;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderItemPO;
import com.sent.sports.order.mapper.po.OrderPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 订单仓储实现（P5-19）：负责 domain ↔ PO 的双向转换，不让持久化细节漏进用例层。
 * <p>两条关键语义：
 * <ul>
 *   <li><b>保存即「有则更新、无则新增」</b>：下单走插入（幂等键冲突抛 {@code DUPLICATE_PROCESSING}，
 *       由用例回查首次结果）；后续取消/发货/收货/退款走更新，更新带乐观锁，版本不符抛 {@code STATE_CONFLICT}；</li>
 *   <li><b>订单行已发数量回写</b>：发货后按 SKU 同步 {@code shipped_qty}，保证再次加载时进度不丢。</li>
 * </ul>
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;

    public OrderRepositoryImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public void save(Order order) {
        if (orderMapper.existsByOrderNo(order.getOrderNo()) > 0) {
            update(order);
        } else {
            insert(order);
        }
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        OrderPO po = orderMapper.selectByOrderNo(orderNo);
        if (po == null) {
            return Optional.empty();
        }
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemPO itemPO : orderMapper.selectItems(po.getId())) {
            OrderItem item = toItem(itemPO);
            items.add(item);
        }
        return Optional.of(toDomain(po, items));
    }

    /** 首次落库：订单头 + 订单行，幂等键冲突交给调用方兜底。 */
    private void insert(Order order) {
        OrderPO po = toPO(order);
        po.setIdemKey(order.getIdemKey());
        try {
            orderMapper.insert(po);
        } catch (DuplicateKeyException e) {
            // uk_order_idem：并发重复提交，同一幂等键只允许一单
            throw new BizException(ErrorCode.DUPLICATE_PROCESSING);
        }
        for (OrderItem item : order.getItems()) {
            OrderItemPO itemPO = toItemPO(po.getId(), item);
            orderMapper.insertItem(itemPO);
        }
    }

    /** 后续更新：乐观锁保护订单头，并回写各订单行已发数量。 */
    private void update(Order order) {
        OrderPO current = orderMapper.selectByOrderNo(order.getOrderNo());
        if (current == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        OrderPO po = toPO(order);
        if (orderMapper.updateHeader(po) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        for (OrderItem item : order.getItems()) {
            orderMapper.updateItemShipped(current.getId(), item.getSkuId(), item.getShippedQty());
        }
    }

    private OrderPO toPO(Order order) {
        OrderPO po = new OrderPO();
        po.setOrderNo(order.getOrderNo());
        po.setCustomerId(order.getCustomerId());
        po.setStatus(order.getStatus().name());
        po.setPayStatus(order.getPayStatus().name());
        po.setGoodsAmountCent(order.getGoodsAmountCent());
        po.setCustomFeeCent(order.getCustomFeeCent());
        po.setDiscountCent(order.getDiscountCent());
        po.setFreightCent(order.getFreightCent());
        po.setPointDeductCent(order.getPointDeductCent());
        po.setPayableCent(order.getPayableCent());
        po.setPaidCent(order.getPaidCent());
        po.setReceivedCent(order.getReceivedCent());
        // 支付时间：domain 用毫秒，PO 用 LocalDateTime；0 视为尚未支付，保持列为 NULL。
        if (order.getPaidAtMillis() > 0) {
            po.setPaidAt(toLocalDateTime(order.getPaidAtMillis()));
        }
        po.setVersion((long) order.getVersion());
        return po;
    }

    private OrderItemPO toItemPO(long orderId, OrderItem item) {
        OrderItemPO po = new OrderItemPO();
        po.setOrderId(orderId);
        po.setSkuId(item.getSkuId());
        po.setQty(item.getQty());
        po.setUnitPriceCent(item.getUnitPriceCent());
        po.setPriceSource(item.getSourceCode());
        po.setAmountCent(item.getLineTotalCent());
        po.setDiscountAllocCent(0L);
        po.setShippedQty(item.getShippedQty());
        po.setCustomFlag(item.isCustomFlag() ? 1 : 0);
        po.setCustomFeeCent(0L);
        return po;
    }

    private OrderItem toItem(OrderItemPO po) {
        OrderItem item = new OrderItem(po.getSkuId(), po.getQty(), po.getUnitPriceCent(),
                po.getPriceSource() == null ? "" : po.getPriceSource(), po.getAmountCent(),
                po.getCustomFlag() != null && po.getCustomFlag() == 1);
        if (po.getShippedQty() != null && po.getShippedQty() > 0) {
            item.ship(po.getShippedQty());
        }
        return item;
    }

    private Order toDomain(OrderPO po, List<OrderItem> items) {
        Order order = new Order(po.getOrderNo(), po.getCustomerId(), po.getIdemKey(), items,
                po.getGoodsAmountCent(), po.getCustomFeeCent(), po.getDiscountCent(), po.getFreightCent(),
                po.getPointDeductCent(), po.getPayableCent(), toMillis(po.getCreatedAt()));
        order.setPaidCent(po.getPaidCent());
        order.setReceivedCent(po.getReceivedCent());
        order.setPaidAtMillis(toMillis(po.getPaidAt()));
        order.setVersion(po.getVersion().intValue());
        order.setStatus(OrderStatus.valueOf(po.getStatus()));
        order.setPayStatus(PayStatus.valueOf(po.getPayStatus()));
        return order;
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 供后续对账工具使用：毫秒 → 本地时间。 */
    static LocalDateTime toLocalDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
