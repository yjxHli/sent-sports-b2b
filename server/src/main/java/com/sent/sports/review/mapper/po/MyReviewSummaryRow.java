package com.sent.sports.review.mapper.po;

/**
 * 评价中心顶部三项统计的聚合行。
 *
 * <p>{@code COUNT(*)} 直接数评价条数；浏览/获赞用 {@code COALESCE} 兜住空集——
 * 没写过评价时 MySQL 会回一行 (0, NULL, NULL)，不兜底前端就会拿到 null。</p>
 */
public class MyReviewSummaryRow {

    private Long reviewCount;
    private Long viewCount;
    private Long likeCount;

    public Long getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Long reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }
}
