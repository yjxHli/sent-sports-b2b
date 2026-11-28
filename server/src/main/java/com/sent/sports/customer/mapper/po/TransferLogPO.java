package com.sent.sports.customer.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 客户改派日志 {@code customer_owner_log} 持久化对象（V3 建表，2026-11-28 起真正启用）。
 *
 * <p>此前改派日志只存在 {@code InMemoryTransferLogRepository} 的 {@code CopyOnWriteArrayList} 里，
 * 重启即丢；P0 修复后由 {@code TransferLogRepositoryImpl} 写入。
 * <p>表里没有业务单号列，主键就是自增 id —— 领域对象的 {@code TL...} 号只用于日志追踪，不落库。
 */
@TableName("customer_owner_log")
public class TransferLogPO extends BasePO {

    private Long customerId;
    private Long fromStaffId;
    private Long toStaffId;
    private String reason;
    private Long operatorId;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getFromStaffId() {
        return fromStaffId;
    }

    public void setFromStaffId(Long fromStaffId) {
        this.fromStaffId = fromStaffId;
    }

    public Long getToStaffId() {
        return toStaffId;
    }

    public void setToStaffId(Long toStaffId) {
        this.toStaffId = toStaffId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }
}
