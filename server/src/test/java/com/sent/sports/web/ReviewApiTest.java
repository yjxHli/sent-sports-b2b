package com.sent.sports.web;

import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.review.controller.ReviewController;
import com.sent.sports.review.dto.ReviewDtos;
import com.sent.sports.review.service.ReviewService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评价中心接口契约（2026-11-28 改版：待评价 / 排序 / 删除自己的评价）。
 *
 * <p>三条必须守住的边界：</p>
 * <ul>
 *   <li>身份只认令牌里的 {@code aud=user}，未登录一律 401（不靠请求参数传 customerId）；</li>
 *   <li>排序参数原样透传给 service（白名单收敛在 service，接口层不做业务判断）；</li>
 *   <li>删除自己的评价走 200；删别人的由 service 抛 NOT_FOUND，接口层原样转 404。</li>
 * </ul>
 */
@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReviewApiTest extends BaseApiTest {

    @MockBean
    private ReviewService reviewService;

    private static CurrentUser user() {
        return new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti");
    }

    @Test
    @DisplayName("待评价列表：返回订单商品与实付，未登录 401")
    void pendingReviewsReturnsRows() throws Exception {
        when(reviewService.myPendingReviews(eq(9002L), any())).thenReturn(
                new ReviewDtos.MyPendingPageVO(List.of(
                        new ReviewDtos.MyPendingReviewVO("SO202611280001", "9101", "斯伯丁篮球 7号",
                                "file-1", 2, 7980L, "79.80", 1_764_293_600_000L)),
                        1L, 1L, 10L, 1L));

        mvc.perform(get("/api/me/reviews/pending").requestAttr(CurrentUser.ATTR, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].orderNo").value("SO202611280001"))
                .andExpect(jsonPath("$.data.list[0].paidYuan").value("79.80"))
                .andExpect(jsonPath("$.data.total").value("1"));

        mvc.perform(get("/api/me/reviews/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("我的评价：sort 原样透传给 service")
    void myReviewsPassesSort() throws Exception {
        when(reviewService.myReviews(eq(9002L), any(), eq("EARLIEST"))).thenReturn(
                new ReviewDtos.MyReviewPageVO(
                        new ReviewDtos.MyReviewSummaryVO(0, 0, 0), List.of(), 0L, 1L, 10L, 0L));

        mvc.perform(get("/api/me/reviews").param("sort", "EARLIEST")
                        .requestAttr(CurrentUser.ATTR, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list").isArray());

        verify(reviewService).myReviews(eq(9002L), any(), eq("EARLIEST"));
    }

    @Test
    @DisplayName("删除自己的评价：200；未登录 401")
    void deleteMyReviewReturns200() throws Exception {
        mvc.perform(delete("/api/me/reviews/55").requestAttr(CurrentUser.ATTR, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(reviewService).deleteMyReview(9002L, 55L);

        mvc.perform(delete("/api/me/reviews/55"))
                .andExpect(status().isUnauthorized());
        // 未登录那次不能落库：整个用例只应有第一次（带令牌）那一次调用
        verify(reviewService, org.mockito.Mockito.times(1)).deleteMyReview(anyLong(), anyLong());
    }
}
