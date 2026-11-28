package com.sent.sports.stock.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 盘点单头 {@code stock_check} 持久化对象（V5 建表，2026-11-28 起真正启用）。
 *
 * <p>此前盘点单只存在 {@code InMemoryStockCheckRepository} 的 Map 里，本表长期空置；
 * P0 修复后由 {@code StockCheckRepositoryImpl} 读写。
 * <p>注意：SKU、账面数、实盘数、差异值都不在本表，而在 {@code stock_check_item}，
 * 本表只承载「单号 + 状态 + 盘点人 + 审批人」。表里没有独立的 {@code recorded_at/approved_at}，
 * 这两个时点由仓储用 {@code updated_at} 近似（详见 {@code StockCheckRepositoryImpl} 注释）。
 */
@TableName("stock_check")
public class StockCheckPO extends BasePO {

    private String checkNo;
    private String status;
    private Long operatorId;
    private Long approvedBy;

    public String getCheckNo() {
        return checkNo;
    }

    public void setCheckNo(String checkNo) {
        this.checkNo = checkNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }
}
