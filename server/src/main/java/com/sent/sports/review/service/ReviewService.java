package com.sent.sports.review.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.review.dto.ReviewDtos;

/**
 * 商品评价用例（V55）。
 * <p>跨模块依赖只走 {@code order.service.OrderQueryService}（资格校验），
 * 不直接读订单表——ArchUnit 规则 4 会拦。</p>
 */
public interface ReviewService {

    /** 提交评价：校验购买资格 → 写库；重复评价转 40901。 */
    ReviewDtos.ReviewVO submit(long customerId, ReviewDtos.CreateReviewCmd cmd);

    /** 商品评价分页 + 摘要（公开读）。 */
    ReviewDtos.ReviewPageVO list(long productId, PageQuery pageQuery);

    /**
     * 评价资格：已购买且尚未评价才返回 {@code eligible=true}。
     * <p>未登录/未购买一律返回 false 而不抛 401——详情页需要在匿名状态下正常渲染。</p>
     */
    ReviewDtos.ReviewEligibilityVO eligibility(long customerId, long productId);

    /**
     * 评价中心（2026-11-28）：我写过的评价分页 + 「已写评论 / 浏览数 / 获赞数」三项统计。
     * <p>未登录抛 401：这是「我的」数据，不存在匿名可读的场景。</p>
     *
     * @param sort 排序（{@code LATEST} 最新 / {@code EARLIEST} 最早 / {@code COMPREHENSIVE} 综合），
     *             非法值按最新处理；排序字段由服务端常量决定，不接受前端拼接
     */
    ReviewDtos.MyReviewPageVO myReviews(long customerId, PageQuery pageQuery, String sort);

    /**
     * 评价中心「待评价」：已完成订单里尚未评价的商品分页。
     * <p>与详情页「去评价」的资格同源（同一条 {@code order_no + product_id} 判定），
     * 不会出现「中心说待评价、点进去却说已评过」。</p>
     */
    ReviewDtos.MyPendingPageVO myPendingReviews(long customerId, PageQuery pageQuery);

    /**
     * 删除我自己写的一条评价。
     * <p>只能删自己的：SQL 带 {@code customer_id}，删不到行回 404（不静默成功）。
     * 物理删除，释放 {@code uk_review_order_product}，删完可以重新评价。</p>
     */
    void deleteMyReview(long customerId, long reviewId);
}
