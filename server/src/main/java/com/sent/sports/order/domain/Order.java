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
    /** 支付截止时间（毫秒）：下单时 = 创建时间 + 5 分钟，超时任务据此取消未付款订单。 */
    private long expireAtMillis;
    private long paidCent;
    private long receivedCent;
    private long paidAtMillis;
    private int version;
    private OrderStatus status;
    private PayStatus payStatus;
    private String addressSnapshotJson;

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

    /**
     * 仓储重建专用构造器：从持久化快照还原一个已有主状态的聚合。
     * 业务代码只能用上面的构造器创建「待付款」新订单；业务状态变更一律走 {@link #transit(OrderAction)}，
     * 不允许在聚合外部直接改写状态。
     */
    public Order(String orderNo, long customerId, String idemKey, List<OrderItem> items,
                 long goodsAmountCent, long customFeeCent, long discountCent, long freightCent,
                 long pointDeductCent, long payableCent, long createdAtMillis, OrderStatus persistedStatus) {
        this(orderNo, customerId, idemKey, items, goodsAmountCent, customFeeCent, discountCent,
                freightCent, pointDeductCent, payableCent, createdAtMillis);
        this.status = persistedStatus;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getCustomerId() {
        return customerId;
    }

    /**
     * 归属判定（2026-11-28 批次 1 / P0-02）：订单是否属于该客户。
     * <p>用户端「看详情 / 取消 / 确认收货」必须先过这一关：单号可预测（{@code SO + 日期 + 序列}），
     * 只凭单号就能操作他人订单。判定留在领域层，Controller 与用例共用同一口径，避免各写一份。</p>
     */
    public boolean isOwnedBy(long viewerCustomerId) {
        return customerId == viewerCustomerId;
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

    public long getExpireAtMillis() {
        return expireAtMillis;
    }

    public void setExpireAtMillis(long expireAtMillis) {
        this.expireAtMillis = expireAtMillis;
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

    /** 仅 domain 包内（当前仅本聚合）可直写；仓储重建走专用构造器，业务流转走 {@link #transit}。 */
    void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setPayStatus(PayStatus payStatus) {
        this.payStatus = payStatus;
    }

    /** 状态迁移（集中，非法迁移抛 STATE_CONFLICT）。 */
    public void transit(OrderAction action) {
        this.status = OrderStateMachine.transit(this.status, action);
    }

    /** 状态准入只读判断：用例用它做前置校验，不另写状态白名单。 */
    public boolean canTransit(OrderAction action) {
        return OrderStateMachine.canTransit(this.status, action);
    }

    /**
     * 收货地址快照（下单瞬间固化的 JSON）。
     *
     * <p>只在下单时写入一次：之后用户改地址簿、删地址都不影响历史订单，
     * 否则发票、退款单、物流面单上的收货信息会跟着变。</p>
     */
    public String getAddressSnapshotJson() {
        return addressSnapshotJson;
    }

    public void setAddressSnapshotJson(String addressSnapshotJson) {
        this.addressSnapshotJson = addressSnapshotJson;
    }
}
