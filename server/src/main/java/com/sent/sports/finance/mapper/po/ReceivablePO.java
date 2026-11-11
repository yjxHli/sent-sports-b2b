package com.sent.sports.finance.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDate;

/**
 * 应收台账 {@code receivable} 持久化对象（P5-17）。
 * <p>{@code orderId} 唯一索引保证「一单一应收」，重复落账被数据库拒绝而不是被应用层漏判。
 */
@TableName("receivable")
public class ReceivablePO extends BasePO {

    private Long orderId;
    private Long customerId;
    private Long amountCent;
    private Long receivedCent;
    private LocalDate dueDate;
    private String status;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public Long getReceivedCent() {
        return receivedCent;
    }

    public void setReceivedCent(Long receivedCent) {
        this.receivedCent = receivedCent;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
