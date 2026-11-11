package com.sent.sports.order.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 订单行（事实快照：单价 / 取价来源在落库时固化，商品改价不影响历史）。
 * 发货以"订单行 + 数量"为单位，超发被拒。
 */
public class OrderItem {

    private final long skuId;
    private final int qty;
    private int shippedQty;
    private int receivedQty;
    private final long unitPriceCent;
    private final String sourceCode;
    private final long lineTotalCent;
    /** UV 定制件标记：落库写 order_item.custom_flag，供定制单生成与审图链路识别。 */
    private final boolean customFlag;

    public OrderItem(long skuId, int qty) {
        this(skuId, qty, 0L, "", 0L, false);
    }

    public OrderItem(long skuId, int qty, long unitPriceCent, String sourceCode, long lineTotalCent) {
        this(skuId, qty, unitPriceCent, sourceCode, lineTotalCent, false);
    }

    public OrderItem(long skuId, int qty, long unitPriceCent, String sourceCode,
                     long lineTotalCent, boolean customFlag) {
        if (qty <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单行数量必须为正");
        }
        this.skuId = skuId;
        this.qty = qty;
        this.shippedQty = 0;
        this.receivedQty = 0;
        this.unitPriceCent = unitPriceCent;
        this.sourceCode = sourceCode;
        this.lineTotalCent = lineTotalCent;
        this.customFlag = customFlag;
    }

    public boolean isCustomFlag() {
        return customFlag;
    }

    public long getSkuId() {
        return skuId;
    }

    public int getQty() {
        return qty;
    }

    public int getShippedQty() {
        return shippedQty;
    }

    public int getRemainingQty() {
        return qty - shippedQty;
    }

    public boolean isFullyShipped() {
        return shippedQty >= qty;
    }

    public long getUnitPriceCent() {
        return unitPriceCent;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public long getLineTotalCent() {
        return lineTotalCent;
    }

    /** 发货：累加已发数量，超发（超过未发量）被拒，数量非法被拒。 */
    public void ship(int qtyToShip) {
        if (qtyToShip <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发货数量必须大于 0");
        }
        if (qtyToShip > getRemainingQty()) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "超发被拒：超过未发数量");
        }
        shippedQty += qtyToShip;
    }

    public int getReceivedQty() {
        return receivedQty;
    }

    public int getRemainingReceiveQty() {
        return qty - receivedQty;
    }

    public boolean isFullyReceived() {
        return receivedQty >= qty;
    }

    /** 收货：累加已收数量，超收（超过未收量）被拒，数量非法被拒。 */
    public void receive(int qtyToReceive) {
        if (qtyToReceive <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收货数量必须大于 0");
        }
        if (qtyToReceive > getRemainingReceiveQty()) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "超收被拒：超过未收数量");
        }
        receivedQty += qtyToReceive;
    }
}
