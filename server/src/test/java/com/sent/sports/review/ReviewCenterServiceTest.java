package com.sent.sports.review;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.SensitiveWordFilter;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.review.dto.ReviewDtos;
import com.sent.sports.review.mapper.ReviewMapper;
import com.sent.sports.review.mapper.po.MyPendingReviewRow;
import com.sent.sports.review.mapper.po.MyReviewRow;
import com.sent.sports.review.mapper.po.MyReviewSummaryRow;
import com.sent.sports.review.service.impl.ReviewServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价中心（2026-11-28 改版：待评价 / 已评价 + 排序 + 删除自己的评价）。
 *
 * <p>三条口径：</p>
 * <ol>
 *   <li><b>待评价来自订单</b>：已完成且该商品还没写过评价才进列表，与详情页「去评价」的资格同源；</li>
 *   <li><b>删除必须校验归属</b>：只能删自己的评价（SQL 带 customer_id），删不到就 404，不能静默成功；</li>
 *   <li><b>排序只认白名单</b>：latest / earliest / comprehensive 之外的取值一律按最新处理，
 *       不接受前端传裸 SQL 片段（排序字段必须是服务端常量）。</li>
 * </ol>
 */
class ReviewCenterServiceTest extends BaseUnitTest {

    private static final long CUSTOMER_ID = 9002L;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrderQueryService orderQueryService;

    @Mock
    private SensitiveWordFilter sensitiveWordFilter;

    private ReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(reviewMapper, orderQueryService, sensitiveWordFilter);
    }

    private MyPendingReviewRow pendingRow() {
        MyPendingReviewRow row = new MyPendingReviewRow();
        row.setOrderNo("SO202611280001");
        row.setProductId(9101L);
        row.setProductTitle("斯伯丁篮球 7号");
        row.setMainImage("file-1");
        row.setQty(2);
        row.setPaidCent(7980L);
        row.setCompletedAt(LocalDateTime.of(2026, 11, 28, 10, 0, 0));
        return row;
    }

    @Test
    @DisplayName("待评价列表：已完成且未评价的订单商品，金额带元字符串")
    void pendingReviewsMapsOrderItems() {
        Page<MyPendingReviewRow> page = new Page<>(1, 10);
        page.setRecords(List.of(pendingRow()));
        page.setTotal(1);
        when(reviewMapper.selectMyPendingPage(any(), eq(CUSTOMER_ID))).thenReturn(page);

        ReviewDtos.MyPendingPageVO vo = service.myPendingReviews(CUSTOMER_ID, new PageQuery());

        assertEquals(1, vo.list().size());
        ReviewDtos.MyPendingReviewVO item = vo.list().get(0);
        assertEquals("SO202611280001", item.orderNo());
        assertEquals("9101", item.productId());
        assertEquals("斯伯丁篮球 7号", item.productTitle());
        assertEquals(2, item.qty());
        assertEquals(7980L, item.paidCent());
        assertEquals("79.80", item.paidYuan());
        assertEquals(1L, vo.total());
    }

    @Test
    @DisplayName("待评价列表：未登录一律 401，不查库")
    void pendingReviewsRequiresLogin() {
        BizException ex = assertThrows(BizException.class,
                () -> service.myPendingReviews(0L, new PageQuery()));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verify(reviewMapper, never()).selectMyPendingPage(any(), anyLong());
    }

    @Test
    @DisplayName("删除自己的评价：删掉 1 行即成功")
    void deleteMyReviewDeletesOwnRow() {
        when(reviewMapper.deleteMine(55L, CUSTOMER_ID)).thenReturn(1);

        service.deleteMyReview(CUSTOMER_ID, 55L);

        verify(reviewMapper).deleteMine(55L, CUSTOMER_ID);
    }

    @Test
    @DisplayName("删除别人的评价：删不到行回 404，不能假装成功")
    void deleteOthersReviewReturnsNotFound() {
        when(reviewMapper.deleteMine(56L, CUSTOMER_ID)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> service.deleteMyReview(CUSTOMER_ID, 56L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("删除评价：未登录 401、id 非法 400")
    void deleteReviewValidatesInput() {
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(BizException.class, () -> service.deleteMyReview(0L, 1L)).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> service.deleteMyReview(CUSTOMER_ID, 0L)).getErrorCode());
        verify(reviewMapper, never()).deleteMine(anyLong(), anyLong());
    }

    @Test
    @DisplayName("我的评价列表：排序白名单，非法值按最新处理")
    void myReviewsNormalizesSort() {
        Page<MyReviewRow> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(reviewMapper.selectMinePage(any(), anyLong(), any())).thenReturn(page);
        when(reviewMapper.selectMineSummary(CUSTOMER_ID))
                .thenReturn(new MyReviewSummaryRow());

        service.myReviews(CUSTOMER_ID, new PageQuery(), "EARLIEST");
        verify(reviewMapper).selectMinePage(any(), eq(CUSTOMER_ID), eq("EARLIEST"));

        service.myReviews(CUSTOMER_ID, new PageQuery(), "hack; DROP TABLE product_review");
        verify(reviewMapper).selectMinePage(any(), eq(CUSTOMER_ID), eq("LATEST"));
    }
}
