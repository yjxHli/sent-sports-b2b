package com.sent.sports.review.mapper.po;

import java.math.BigDecimal;

/**
 * 评价摘要行：总数 / 好评数（rating≥4）/ 平均分。
 * <p>聚合下推到 SQL：评价数上万时若取出全量再在内存里算，详情页每次打开都要扫全表。
 * {@code AVG()} 返回 DECIMAL，用 {@link BigDecimal} 承接避免浮点误差。</p>
 */
public class ReviewSummaryRow {

    private Long total;
    private Long positiveCount;
    private BigDecimal averageRating;

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Long getPositiveCount() {
        return positiveCount;
    }

    public void setPositiveCount(Long positiveCount) {
        this.positiveCount = positiveCount;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }
}
