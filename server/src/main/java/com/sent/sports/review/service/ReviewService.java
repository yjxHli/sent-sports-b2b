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
}
