package com.sent.sports.review.dto;

import java.util.List;

/**
 * 商品评价 Web 层 DTO（V55）。
 *
 * <p>{@link CreateReviewCmd} 不加 {@code @NotNull}：空值要落到用例里抛 {@code PARAM_INVALID}
 * 并带中文原因，与其它业务校验的错误码/文案保持一致，而不是被 Web 层拦成框架校验消息。</p>
 */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    /**
     * 提交评价。
     *
     * @param productId 被评价 SPU
     * @param skuId     被评价规格（可空：旧数据/只有商品级资格时按商品评价）
     * @param rating    1~5 星
     * @param content   正文（trim 后 1~500 字）
     * @param images    评价图 fileId 数组（最多 3 张）
     * @param anonymous 是否匿名展示
     */
    public record CreateReviewCmd(
            Long productId,
            Long skuId,
            Integer rating,
            String content,
            List<String> images,
            Boolean anonymous) {
    }

    /**
     * 评价单条。
     * <p>{@code nickname} 已是脱敏值，{@code avatarText} 是「首字头像」——前端不再接触真实昵称，
     * 也不为了头像再请求文件服务。</p>
     */
    public record ReviewVO(
            String reviewId,
            String nickname,
            String avatarText,
            int rating,
            String content,
            List<String> images,
            boolean anonymous,
            long createdAt) {
    }

    /**
     * 头部摘要：好评率按 rating≥4 计算（与电商口径一致）。
     * <p>{@code negativeCount} 是 rating≤2 的条数；3 星中评两边都不计，
     * 所以 {@code positiveCount + negativeCount} 允许小于 {@code total}。</p>
     */
    public record ReviewSummaryVO(long total, long positiveCount, long negativeCount,
                                  int positiveRate, String avgRating) {
    }

    /** 列表 + 摘要一趟返回：详情页打开即完整渲染，不必二次请求。 */
    public record ReviewPageVO(ReviewSummaryVO summary, List<ReviewVO> list,
                               long total, long page, long size, long pages) {
    }

    /**
     * 评价资格（详情页「去评价」按钮的显隐依据）。
     * <p>{@code reviewed=true} 用来区分「没买过」和「买过但评过了」：
     * 两种都不可评价，但只有后者该在前端提示「您已评价」。</p>
     */
    public record ReviewEligibilityVO(boolean eligible, boolean reviewed) {
    }

    /**
     * 评价中心顶部三项统计（2026-11-28）。
     * <p>只给三项：已写评论条数、评价累计被浏览数、评价累计获赞数。
     * 「待评价」由订单侧算，不在这里混算——本接口只统计「已经写出去的」。</p>
     */
    public record MyReviewSummaryVO(long reviewCount, long viewCount, long likeCount) {
    }

    /**
     * 我写过的一条评价（评价中心列表项）。
     *
     * @param productId    被评价商品（点卡片跳详情页）
     * @param productTitle 商品标题（商品被删时为空串，前端回退「商品已下架」）
     * @param status       NORMAL / HIDDEN：被运营隐藏的评价仍属于「我写过的」，卡片要标记出来
     * @param viewCount    该评价被浏览次数
     * @param likeCount    该评价获赞次数
     */
    public record MyReviewVO(
            String reviewId,
            String productId,
            String productTitle,
            String mainImage,
            int rating,
            String content,
            List<String> images,
            boolean anonymous,
            String status,
            long viewCount,
            long likeCount,
            long createdAt) {
    }

    /** 评价中心列表 + 三项统计一趟返回：打开页面即完整渲染，不必二次请求。 */
    public record MyReviewPageVO(MyReviewSummaryVO summary, List<MyReviewVO> list,
                                 long total, long page, long size, long pages) {
    }

    /**
     * 评价中心「待评价」的一条：已完成订单里尚未评价的商品。
     *
     * @param orderNo      来源订单号（评价时前端要回传给 {@code POST /api/reviews} 的资格判定）
     * @param productId    被评价商品（点卡片跳详情页、提交评价都要用）
     * @param productTitle 商品标题（快照优先，商品被删时为空串）
     * @param paidCent     该行实付（分）
     * @param paidYuan     该行实付（元字符串，前端直接展示）
     * @param completedAt  订单完成时间（毫秒）：列表按它倒序，「最近买的排前面」
     */
    public record MyPendingReviewVO(
            String orderNo,
            String productId,
            String productTitle,
            String mainImage,
            int qty,
            long paidCent,
            String paidYuan,
            long completedAt) {
    }

    /** 待评价分页（2026-11-28）：与「已评价」分页同构，前端一套翻页逻辑复用。 */
    public record MyPendingPageVO(List<MyPendingReviewVO> list,
                                  long total, long page, long size, long pages) {
    }
}
