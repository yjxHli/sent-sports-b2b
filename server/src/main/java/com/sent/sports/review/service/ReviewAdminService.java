package com.sent.sports.review.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.review.dto.admin.AdminReviewDtos;

/**
 * 评价治理（V56）：运营侧的查询与下架/恢复。
 *
 * <p>与 {@link ReviewService} 严格分离：用户侧只读「正常」评价，写入口径（资格/防重/图片校验）
 * 完全不受治理影响；治理侧只改 {@code status}，不碰正文与星级。</p>
 */
public interface ReviewAdminService {

    /**
     * 分页查询评价。
     *
     * @param productId 按商品筛选，null 表示不限
     * @param rating    按星级精确筛选（1~5），null 表示不限
     * @param status    NORMAL / HIDDEN（忽略大小写与首尾空白），null/空白表示不限
     */
    PageResult<AdminReviewDtos.AdminReviewVO> list(Long productId, Integer rating, String status, PageQuery page);

    /**
     * 变更评价状态并记操作日志。
     *
     * @param reviewId   评价主键
     * @param status     NORMAL（恢复展示）/ HIDDEN（隐藏）
     * @param reason     操作原因（可空，进日志 reason 列）
     * @param operatorId 操作人（当前管理端员工），可空
     */
    AdminReviewDtos.AdminReviewVO updateStatus(long reviewId, String status, String reason, Long operatorId);
}
