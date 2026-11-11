package com.sent.sports.customer.domain;

/**
 * 客户聚合（customer 模块，P3-49）：纯 POJO，无 Spring 注解。
 * 仅持有当前归属销售/客服（ownerStaffId）；历史订单归属不可改写（由订单模块独立维护）。
 * 改派只更新当前归属并写流转日志（见 TransferLog）。
 */
public class Customer {

    private final long customerId;
    private Long ownerStaffId;
    private final long createdAtMillis;

    public Customer(long customerId, Long ownerStaffId, long createdAtMillis) {
        this.customerId = customerId;
        this.ownerStaffId = ownerStaffId;
        this.createdAtMillis = createdAtMillis;
    }

    /** 改派：仅变更当前归属，不影响历史订单。 */
    public void transferTo(long toStaffId) {
        this.ownerStaffId = toStaffId;
    }

    public long getCustomerId() {
        return customerId;
    }

    public Long getOwnerStaffId() {
        return ownerStaffId;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}
