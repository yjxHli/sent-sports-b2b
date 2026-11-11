package com.sent.sports.finance.domain;

/**
 * 回款单（plan §7.7 / spec §6.8）：客户回款登记，记录流水号、金额、已核销额。
 * 纯 POJO，无 Spring 依赖；状态 PENDING / REVERSED。
 */
public class PaymentRecord {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REVERSED = "REVERSED";

    private final String paymentNo;
    private final long customerId;
    private final long amountCent;
    private long writtenOffCent;
    private final String serialNo;
    private String status;
    private final long operatorId;
    private final long createdAtMillis;
    private final String remark;

    public PaymentRecord(String paymentNo, long customerId, long amountCent, long writtenOffCent,
                         String serialNo, String status, long operatorId, long createdAtMillis, String remark) {
        this.paymentNo = paymentNo;
        this.customerId = customerId;
        this.amountCent = amountCent;
        this.writtenOffCent = writtenOffCent;
        this.serialNo = serialNo;
        this.status = status;
        this.operatorId = operatorId;
        this.createdAtMillis = createdAtMillis;
        this.remark = remark;
    }

    /** 可核销余额 = 金额 − 已核销。 */
    public long remainingWritableCent() {
        return amountCent - writtenOffCent;
    }

    /** 累加已核销额。 */
    public void writeOff(long amount) {
        this.writtenOffCent += amount;
    }

    /** 冲销：状态置 REVERSED，已核销清零。 */
    public void reverse() {
        this.status = STATUS_REVERSED;
        this.writtenOffCent = 0L;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public long getCustomerId() {
        return customerId;
    }

    public long getAmountCent() {
        return amountCent;
    }

    public long getWrittenOffCent() {
        return writtenOffCent;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public String getStatus() {
        return status;
    }

    public long getOperatorId() {
        return operatorId;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public String getRemark() {
        return remark;
    }
}
