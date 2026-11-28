package com.sent.sports.customize.mapper.po;

/**
 * 「我的定制单」按状态分组的计数行（评价四类筛选桶的原料）。
 * <p>先按 {@code status} 分组数出来，再由 service 归到四个桶——
 * 桶的定义只在 {@code CustomStatusBucket} 里写一次，SQL 不做业务判断。</p>
 */
public class CustomStatusCountRow {

    private String status;
    private Long total;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
