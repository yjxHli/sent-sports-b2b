package com.sent.sports.payment.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/**
 * 支付流水 {@code payment_txn} 持久化对象（P5-14）。
 * <p>两个唯一键各司其职：{@code txn_no} 防通道侧重复通知，{@code idem_key} 防前台重复发起支付。
 */
@TableName("payment_txn")
public class PaymentTxnPO extends BasePO {

    private String txnNo;
    private String orderNo;
    private Long amountCent;
    private String channel;
    private String channelTradeNo;
    private String idemKey;
    private String status;
    private LocalDateTime requestAt;
    private LocalDateTime callbackAt;
    private String rawPayload;

    public String getTxnNo() {
        return txnNo;
    }

    public void setTxnNo(String txnNo) {
        this.txnNo = txnNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getChannelTradeNo() {
        return channelTradeNo;
    }

    public void setChannelTradeNo(String channelTradeNo) {
        this.channelTradeNo = channelTradeNo;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public void setIdemKey(String idemKey) {
        this.idemKey = idemKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRequestAt() {
        return requestAt;
    }

    public void setRequestAt(LocalDateTime requestAt) {
        this.requestAt = requestAt;
    }

    public LocalDateTime getCallbackAt() {
        return callbackAt;
    }

    public void setCallbackAt(LocalDateTime callbackAt) {
        this.callbackAt = callbackAt;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }
}
