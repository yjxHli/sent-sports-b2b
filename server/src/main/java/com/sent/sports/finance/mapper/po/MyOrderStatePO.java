package com.sent.sports.finance.mapper.po;

/**
 * 「我的订单」的发票相关状态（归属校验 + 可开票口径）。
 *
 * <p>一次查询同时带出订单主状态与支付状态：主状态决定「是否已完成」（用户要求只有已完成才能开票），
 * 支付状态兜住「已完成但整单全退」（钱已退，票面无依据）。分成两次查询会出现
 * 「查完状态订单刚好退款」的窗口期，因此这里合成一条 SQL。</p>
 */
public class MyOrderStatePO {

    /** orders.status：WAIT_PAY / WAIT_SHIP / PART_SHIPPED / WAIT_RECEIVE / COMPLETED / CANCELLED / CLOSED */
    private String status;

    /** orders.pay_status：UNPAID / PAYING / PAID / PART_REFUNDED / FULL_REFUNDED */
    private String payStatus;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }
}
