package com.sent.sports.review.controller;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.R;
import com.sent.sports.review.dto.ReviewDtos;
import com.sent.sports.review.service.ReviewService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价接口（V55）：提交 + 商品评价列表。
 *
 * <p>身份一律取 {@code AuthFilter} 注入的 {@link CurrentUser}（aud=user），
 * 不接受请求体传 customerId——否则任何人都能替别人评价。</p>
 * <p>读接口公开（详情页未登录也要能看到评价），写接口必须登录。</p>
 */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 提交评价：资格不足 40301、重复评价 40901。 */
    @PostMapping("/reviews")
    public R<ReviewDtos.ReviewVO> submit(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @RequestBody ReviewDtos.CreateReviewCmd cmd) {
        return R.ok(reviewService.submit(customerIdOf(user), cmd));
    }

    /** 某商品的评价分页（含好评率/均分摘要）。 */
    @GetMapping("/products/{productId}/reviews")
    public R<ReviewDtos.ReviewPageVO> list(@PathVariable long productId,
                                           @ModelAttribute PageQuery page) {
        return R.ok(reviewService.list(productId, page));
    }

    /**
     * 当前用户对该商品的评价资格（「去评价」按钮显隐）。
     * <p>未登录返回 {@code eligible=false} 而不是 401：详情页对匿名用户也要正常打开。</p>
     */
    @GetMapping("/products/{productId}/review-eligibility")
    public R<ReviewDtos.ReviewEligibilityVO> eligibility(
            @PathVariable long productId,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        long customerId = user == null || !CurrentUser.AUD_USER.equals(user.aud()) ? 0L : user.userId();
        return R.ok(reviewService.eligibility(customerId, productId));
    }

    /**
     * 评价中心（个人中心「订单中心 → 评价中心」）：我写过的评价 + 三项统计。
     * <p>必须登录：这是「我的」数据，未登录由 {@link #customerIdOf} 抛 401。</p>
     *
     * @param sort 排序：latest（默认）/ earliest / comprehensive，非法值按最新处理
     */
    @GetMapping("/me/reviews")
    public R<ReviewDtos.MyReviewPageVO> myReviews(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @ModelAttribute PageQuery page,
            @RequestParam(value = "sort", required = false) String sort) {
        return R.ok(reviewService.myReviews(customerIdOf(user), page, sort));
    }

    /**
     * 评价中心「待评价」页签：已完成订单里尚未评价的商品（与详情页「去评价」资格同源）。
     */
    @GetMapping("/me/reviews/pending")
    public R<ReviewDtos.MyPendingPageVO> myPendingReviews(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @ModelAttribute PageQuery page) {
        return R.ok(reviewService.myPendingReviews(customerIdOf(user), page));
    }

    /**
     * 删除我自己的一条评价（幂等口径：删不到行回 404，不静默成功）。
     * <p>越权删除表现为 404 而不是 403 —— 不暴露「这条评价存在但不属于你」。</p>
     */
    @DeleteMapping("/me/reviews/{reviewId}")
    public R<Void> deleteMyReview(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user,
            @PathVariable long reviewId) {
        reviewService.deleteMyReview(customerIdOf(user), reviewId);
        return R.ok();
    }

    private long customerIdOf(CurrentUser user) {        if (user == null || !CurrentUser.AUD_USER.equals(user.aud())) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user.userId();
    }
}
