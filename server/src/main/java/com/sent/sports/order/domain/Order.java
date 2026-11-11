package com.sent.sports.order.domain;

import java.util.List;

/**
 * 订单聚合根（P3-07）：落库时固化价格快照与金额，初始状态 WAIT_PAY / UNPAID。
 * 状态迁移统一走 {@link OrderStateMachine}，金额字段不可变（paid/received/version 由后续用例更新）。
 */
public class Order {

    private final String orderNo;
    private final long customerId;
    private final String idemKey;
    private final List<OrderItem> items;
    private final long goodsAmountCent;
    private final long customFeeCent;
    private final long discountCent;
    private final long freightCent;
    private final long pointDeductCent;
    private final long payableCent;
    private final long createdAtMillis;
    private long paidCent;
    private long receivedCent;
    private long paidAtMillis;
    private int version;
    private OrderStatus status;
    private PayStatus payStatus;

    public Order(String orderNo, long customerId, String idemKey, List<OrderItem> items,
                 long goodsAmountCent, long customFeeCent, long discountCent, long freightCent,
                 long pointDeductCent, long payableCent, long createdAtMillis) {
        this.orderNo = orderNo;
        this.customerId = customerId;
        this.idemKey = idemKey;
        this.items = List.copyOf(items);
        this.goodsAmountCent = goodsAmountCent;
        this.customFeeCent = customFeeCent;
        this.discountCent = discountCent;
        this.freightCent = freightCent;
        this.pointDeductCent = pointDeductCent;
        this.payableCent = payableCent;
        this.createdAtMillis = createdAtMillis;
        this.paidCent = 0L;
        this.receivedCent = 0L;
        this.version = 0;
        this.status = OrderStatus.WAIT_PAY;
        this.payStatus = PayStatus.UNPAID;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getCustomerId() {
        return customerId;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public long getGoodsAmountCent() {
        return goodsAmountCent;
    }

    public long getCustomFeeCent() {
        return customFeeCent;
    }

    public long getDiscountCent() {
        return discountCent;
    }

    public long getFreightCent() {
        return freightCent;
    }

    public long getPointDeductCent() {
        return pointDeductCent;
    }

    public long getPayableCent() {
        return payableCent;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getPaidCent() {
        return paidCent;
    }

    public long getReceivedCent() {
        return receivedCent;
    }

    public int getVersion() {
        return version;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public PayStatus getPayStatus() {
        return payStatus;
    }

    public long getPaidAtMillis() {
        return paidAtMillis;
    }

    /** 支付完成时间（毫秒）：由支付回调透传，落库到 orders.paid_at，供销售趋势按日聚合。 */
    public void setPaidAtMillis(long paidAtMillis) {
        this.paidAtMillis = paidAtMillis;
    }

    public void setPaidCent(long paidCent) {
        this.paidCent = paidCent;
    }

    public void setReceivedCent(long receivedCent) {
        this.receivedCent = receivedCent;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setPayStatus(PayStatus payStatus) {
        this.payStatus = payStatus;
    }

    /** 状态迁移（集中，非法迁移抛 STATE_CONFLICT）。 */
    public void transit(OrderAction action) {
        this.status = OrderStateMachine.transit(this.status, action);
    }
}
