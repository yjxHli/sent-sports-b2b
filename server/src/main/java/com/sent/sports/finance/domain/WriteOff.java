package com.sent.sports.finance.domain;

import java.util.List;

/**
 * 核销记录（plan §7.7 / spec §6.8）：一笔付款单对多笔应收的核销留痕。
 * 纯 POJO，无 Spring 依赖。
 */
public class WriteOff {

    /** 一条核销分配明细：应收 id + 分配金额（分）。 */
    public record Item(long receivableId, long amount) {
    }

    private final String id;
    private final String paymentNo;
    private final long customerId;
    private final long totalCent;
    private final List<Item> items;
    private final long createdAtMillis;
    private final long operatorId;
    private boolean voided;

    public WriteOff(String id, String paymentNo, long customerId, long totalCent,
                    List<Item> items, long createdAtMillis, long operatorId) {
        this.id = id;
        this.paymentNo = paymentNo;
        this.customerId = customerId;
        this.totalCent = totalCent;
        this.items = items;
        this.createdAtMillis = createdAtMillis;
        this.operatorId = operatorId;
        this.voided = false;
    }

    /** 冲销时置为作废。 */
    public void setVoided(boolean voided) {
        this.voided = voided;
    }

    public String getId() {
        return id;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public long getCustomerId() {
        return customerId;
    }

    public long getTotalCent() {
        return totalCent;
    }

    public List<Item> getItems() {
        return items;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getOperatorId() {
        return operatorId;
    }

    public boolean isVoided() {
        return voided;
    }
}
