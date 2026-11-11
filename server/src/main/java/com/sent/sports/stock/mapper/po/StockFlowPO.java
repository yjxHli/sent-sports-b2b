package com.sent.sports.stock.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 库存流水 {@code stock_flow} 持久化对象（P5-12 配套）。
 * <p>每次数量变动写一条，台账可由流水重算；{@code (bizType, bizNo, skuId)} 上有唯一索引，
 * 同一业务单据对同一 SKU 的同类变动只允许记一次——这就是「轻量级防重」：
 * 靠数据库唯一键拦重复，而不是引入分布式锁。
 */
@TableName("stock_flow")
public class StockFlowPO extends BasePO {

    private Long skuId;
    private String bizType;
    private Integer changeQty;
    private Integer beforeQty;
    private Integer afterQty;
    private String bizNo;
    private Long operatorId;
    private String remark;

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Integer getChangeQty() {
        return changeQty;
    }

    public void setChangeQty(Integer changeQty) {
        this.changeQty = changeQty;
    }

    public Integer getBeforeQty() {
        return beforeQty;
    }

    public void setBeforeQty(Integer beforeQty) {
        this.beforeQty = beforeQty;
    }

    public Integer getAfterQty() {
        return afterQty;
    }

    public void setAfterQty(Integer afterQty) {
        this.afterQty = afterQty;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
