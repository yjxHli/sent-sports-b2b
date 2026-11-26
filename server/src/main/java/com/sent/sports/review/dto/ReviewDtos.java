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
}
