package com.sent.sports.review.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理端评价治理 DTO（V56）。
 */
public final class AdminReviewDtos {

    private AdminReviewDtos() {
    }

    /**
     * 治理列表行。
     *
     * @param productTitle 商品标题（商品被删除时为空串，不因联查为空而丢行）
     * @param userLabel    已脱敏的评价人（匿名一律「匿名用户」）
     * @param status       NORMAL / HIDDEN
     */
    public record AdminReviewVO(String reviewId, long productId, String productTitle, int rating,
                                String content, List<String> images, String userLabel,
                                boolean anonymous, String status, long createdAt) {
    }

    /**
     * 状态变更入参。
     * <p>{@code status} 的语义白名单（NORMAL/HIDDEN）由 service 归一化后校验，
     * 这里只拦「完全没传」；{@code reason} 进操作日志，方便复盘为什么下架。</p>
     */
    public record StatusCmd(@NotBlank(message = "状态不能为空") String status,
                            @Size(max = 200, message = "操作原因不超过 200 字") String reason) {
    }
}
