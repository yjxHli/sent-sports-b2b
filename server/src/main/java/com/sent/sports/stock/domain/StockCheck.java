package com.sent.sports.stock.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 盘点单聚合（stock 模块，P3-35）：一次盘点针对一个 SKU，状态机 DRAFT→COUNTED→APPROVED。
 * 对应 plan §7.2 的 {@code stock_check} 头 + {@code stock_check_item} 单行；Phase 5 持久化时再拆两表。
 * 纯 Java 领域对象，无 Spring 依赖；状态流转与差异计算在此收口。
 */
public class StockCheck {

    public static final String DRAFT = "DRAFT";
    public static final String COUNTED = "COUNTED";
    public static final String APPROVED = "APPROVED";

    private final String checkNo;
    private final long skuId;
    private final long bookQty;
    private long actualQty;
    private long diffQty;
    private String status;
    private final long operatorId;
    private final long createdAtMillis;
    private long recordedAtMillis;
    private long approvedAtMillis;

    public StockCheck(String checkNo, long skuId, long bookQty, long operatorId, long createdAtMillis) {
        this.checkNo = checkNo;
        this.skuId = skuId;
        this.bookQty = bookQty;
        this.actualQty = -1L;
        this.diffQty = 0L;
        this.status = DRAFT;
        this.operatorId = operatorId;
        this.createdAtMillis = createdAtMillis;
    }

    /** 录入实盘数：仅 DRAFT 可录，差值 = 实盘 - 账面，置 COUNTED。 */
    public void record(long actualQty, long nowMillis) {
        if (!DRAFT.equals(status)) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        if (actualQty < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        this.actualQty = actualQty;
        this.diffQty = actualQty - bookQty;
        this.status = COUNTED;
        this.recordedAtMillis = nowMillis;
    }

    /** 审批：仅 COUNTED 可审，置 APPROVED（库存调账由用例层依据 diffQty 执行）。 */
    public void approve(long nowMillis) {
        if (!COUNTED.equals(status)) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }
        this.status = APPROVED;
        this.approvedAtMillis = nowMillis;
    }

    public String getCheckNo() {
        return checkNo;
    }

    public long getSkuId() {
        return skuId;
    }

    public long getBookQty() {
        return bookQty;
    }

    public long getActualQty() {
        return actualQty;
    }

    public long getDiffQty() {
        return diffQty;
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

    public long getRecordedAtMillis() {
        return recordedAtMillis;
    }

    public long getApprovedAtMillis() {
        return approvedAtMillis;
    }
}
