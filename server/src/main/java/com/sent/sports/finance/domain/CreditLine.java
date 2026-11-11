package com.sent.sports.finance.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 授信额度（plan §7.7 / spec §6.8）：企业授信、账期、有效期。
 * 纯 POJO，无 Spring 依赖；订单模块据此读取 {@link #available()} 作为超额下单拦截依据。
 */
public class CreditLine {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private final long customerId;
    private long totalCent;
    private long usedCent;
    private long accountPeriodDays;
    private long validFromMillis;
    private long validToMillis;
    private String status;

    public CreditLine(long customerId, long totalCent, long usedCent, long accountPeriodDays,
                      long validFromMillis, long validToMillis, String status) {
        this.customerId = customerId;
        this.totalCent = totalCent;
        this.usedCent = usedCent;
        this.accountPeriodDays = accountPeriodDays;
        this.validFromMillis = validFromMillis;
        this.validToMillis = validToMillis;
        this.status = status;
    }

    /** 可用额度 = 总额 − 已用。 */
    public long available() {
        return totalCent - usedCent;
    }

    /** 占用额度：非 ACTIVE 抛 BIZ_REJECT；可用不足抛 CREDIT_NOT_ENOUGH。 */
    public void consume(long amount) {
        if (!STATUS_ACTIVE.equals(status)) {
            throw new BizException(ErrorCode.BIZ_REJECT);
        }
        if (available() < amount) {
            throw new BizException(ErrorCode.CREDIT_NOT_ENOUGH);
        }
        usedCent += amount;
    }

    /** 释放额度（核销回款时回退占用）。 */
    public void release(long amount) {
        usedCent -= amount;
    }

    /** 调整授信：更新总额/账期/有效期，保留已用额，状态置 ACTIVE。 */
    public void update(long totalCent, long accountPeriodDays, long validFromMillis, long validToMillis) {
        this.totalCent = totalCent;
        this.accountPeriodDays = accountPeriodDays;
        this.validFromMillis = validFromMillis;
        this.validToMillis = validToMillis;
        this.status = STATUS_ACTIVE;
    }

    public long getCustomerId() {
        return customerId;
    }

    public long getTotalCent() {
        return totalCent;
    }

    public long getUsedCent() {
        return usedCent;
    }

    public long getAccountPeriodDays() {
        return accountPeriodDays;
    }

    public long getValidFromMillis() {
        return validFromMillis;
    }

    public long getValidToMillis() {
        return validToMillis;
    }

    public String getStatus() {
        return status;
    }
}
