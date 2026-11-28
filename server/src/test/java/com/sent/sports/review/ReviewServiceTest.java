package com.sent.sports.review;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.SensitiveWordFilter;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.review.dto.ReviewDtos;
import com.sent.sports.review.mapper.ReviewMapper;
import com.sent.sports.review.mapper.po.MyReviewRow;
import com.sent.sports.review.mapper.po.MyReviewSummaryRow;
import com.sent.sports.review.mapper.po.ProductReviewPO;
import com.sent.sports.review.mapper.po.ReviewRow;
import com.sent.sports.review.mapper.po.ReviewSummaryRow;
import com.sent.sports.review.service.impl.ReviewServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商品评价用例（V55，TDD 红）。
 *
 * <p>评价是「只有买过的人能说话」的场景，四条口径必须守住：</p>
 * <ol>
 *   <li><b>资格</b>：只有该客户存在含此商品的 <b>已完成</b> 订单才能评价——否则任何人拿商品 id 就能刷好评；</li>
 *   <li><b>防重</b>：同一订单同一商品只能评一次，靠联合唯一索引兜底（不引入锁）；</li>
 *   <li><b>入参</b>：星级 1~5、内容非空且 ≤500 字、图片 ≤3 张且 fileId 合法（防路径穿越）；</li>
 *   <li><b>展示</b>：列表对外只给脱敏昵称与首字头像，匿名评价不得泄露真实昵称。</li>
 * </ol>
 */
class ReviewServiceTest extends BaseUnitTest {

    private static final long CUSTOMER_ID = 9002L;
    private static final long PRODUCT_ID = 9101L;
    private static final long SKU_ID = 9201L;
    private static final String ORDER_NO = "SO202611220001";

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

    private ReviewDtos.CreateReviewCmd cmd(Integer rating, String content, List<String> images, boolean anonymous) {
        return new ReviewDtos.CreateReviewCmd(PRODUCT_ID, SKU_ID, rating, content, images, anonymous);
    }

    private ReviewRow row(long id, String nickname, int rating, boolean anonymous) {
        ReviewRow r = new ReviewRow();
        r.setId(id);
        r.setCustomerId(CUSTOMER_ID);
        r.setRating(rating);
        r.setContent("手感很好，训练够用");
        r.setImagesJson("[]");
        r.setIsAnonymous(anonymous ? 1 : 0);
        r.setNickname(nickname);
        r.setCreatedAt(LocalDateTime.of(2026, 11, 22, 10, 0));
        return r;
    }

    @Test
    @DisplayName("未登录（customerId<=0）：抛 UNAUTHENTICATED，不查订单也不写库")
    void anonymousRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> service.submit(0L, cmd(5, "很好", List.of(), false)));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verifyNoInteractions(orderQueryService, reviewMapper);
    }

    @Test
    @DisplayName("评价内容含违禁词：抛 40003，在查订单资格之前直接拦掉")
    void sensitiveContentRejected() {
        when(sensitiveWordFilter.contains("毒品")).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> service.submit(CUSTOMER_ID, cmd(5, "毒品", List.of(), false)));

        assertEquals(40003, ex.getErrorCode().getCode());
        assertTrue(ex.resolvedMessage().contains("文明发言"), ex.resolvedMessage());
        verifyNoInteractions(orderQueryService, reviewMapper);
    }

    @Test
    @DisplayName("未购买客户评价：抛 FORBIDDEN（没有该商品的已完成订单），且不写库")
    void reviewWithoutCompletedOrderRejected() {
        when(orderQueryService.findCompletedOrderNoForReview(CUSTOMER_ID, PRODUCT_ID, SKU_ID))
                .thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> service.submit(CUSTOMER_ID, cmd(5, "很好", List.of(), false)));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("已完成"), "提示语必须说明只有已完成订单可评价：" + ex.resolvedMessage());
        verify(reviewMapper, never()).insert(any(ProductReviewPO.class));
    }

    @Test
    @DisplayName("资格按商品兜底：买的是同商品其它规格，也能评价当前选中规格")
    void submitFallsBackToProductLevelEligibility() {
        // SKU 级查不到（客户买的是同商品另一个规格），商品级能查到 → 允许评价
        when(orderQueryService.findCompletedOrderNoForReview(CUSTOMER_ID, PRODUCT_ID, SKU_ID))
                .thenReturn(Optional.empty());
        when(orderQueryService.findCompletedOrderNoForReview(CUSTOMER_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(ORDER_NO));
        when(reviewMapper.insert(any(ProductReviewPO.class))).thenReturn(1);

        ReviewDtos.ReviewVO vo = service.submit(CUSTOMER_ID, cmd(4, "同款另一个规格", List.of(), false));

        assertEquals(4, vo.rating());
        ArgumentCaptor<ProductReviewPO> captor = ArgumentCaptor.forClass(ProductReviewPO.class);
        verify(reviewMapper).insert(captor.capture());
        assertEquals(ORDER_NO, captor.getValue().getOrderNo());
    }

    @Test
    @DisplayName("重复评价：唯一索引抛 DuplicateKeyException 时转成 STATE_CONFLICT，不把 500 抛给前端")
    void duplicateReviewRejected() {
        when(orderQueryService.findCompletedOrderNoForReview(CUSTOMER_ID, PRODUCT_ID, SKU_ID))
                .thenReturn(Optional.of(ORDER_NO));
        when(reviewMapper.insert(any(ProductReviewPO.class)))
                .thenThrow(new DuplicateKeyException("uk_review_order_product"));

        BizException ex = assertThrows(BizException.class,
                () -> service.submit(CUSTOMER_ID, cmd(4, "还可以", List.of(), false)));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        assertTrue(ex.resolvedMessage().contains("已评价"), "提示语要说清是重复评价：" + ex.resolvedMessage());
    }

    @Test
    @DisplayName("正常评价：写入订单号/商品/SKU/星级/图片 JSON/匿名标记，并回显脱敏昵称")
    void submitSucceeds() {
        when(orderQueryService.findCompletedOrderNoForReview(CUSTOMER_ID, PRODUCT_ID, SKU_ID))
                .thenReturn(Optional.of(ORDER_NO));
        when(reviewMapper.insert(any(ProductReviewPO.class))).thenReturn(1);

        ReviewDtos.ReviewVO vo = service.submit(CUSTOMER_ID,
                cmd(5, "  训练手感很好  ", List.of("2026/11/aa/hash1.png", "2026/11/aa/hash2.png"), true));

        ArgumentCaptor<ProductReviewPO> captor = ArgumentCaptor.forClass(ProductReviewPO.class);
        verify(reviewMapper).insert(captor.capture());
        ProductReviewPO saved = captor.getValue();

        assertEquals(ORDER_NO, saved.getOrderNo());
        assertEquals(PRODUCT_ID, saved.getProductId());
        assertEquals(SKU_ID, saved.getSkuId());
        assertEquals(CUSTOMER_ID, saved.getCustomerId());
        assertEquals(5, saved.getRating());
        assertEquals("训练手感很好", saved.getContent(), "内容需 trim 后入库");
        assertTrue(saved.getImagesJson().contains("hash1.png"));
        assertEquals(1, saved.getIsAnonymous());
        assertEquals(CUSTOMER_ID, saved.getCreatedBy(), "created_by 必须落库（BasePO 约定）");

        assertEquals(5, vo.rating());
        assertEquals(2, vo.images().size());
        assertTrue(vo.anonymous());
        assertEquals("匿名用户", vo.nickname(), "匿名评价对外不得泄露昵称");
    }

    @Test
    @DisplayName("评分越界（null/0/6）：抛 PARAM_INVALID，不查订单也不写库")
    void invalidRatingRejected() {
        for (Integer bad : new Integer[]{null, 0, 6, -1}) {
            BizException ex = assertThrows(BizException.class,
                    () -> service.submit(CUSTOMER_ID, cmd(bad, "内容", List.of(), false)));
            assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode(), "rating=" + bad);
        }
        verifyNoInteractions(orderQueryService, reviewMapper);
    }

    @Test
    @DisplayName("内容为空或超 500 字：抛 PARAM_INVALID")
    void invalidContentRejected() {
        Integer rating = 5;
        assertThrows(BizException.class, () -> service.submit(CUSTOMER_ID, cmd(rating, "   ", List.of(), false)));
        assertThrows(BizException.class, () -> service.submit(CUSTOMER_ID, cmd(rating, null, List.of(), false)));
        String tooLong = "好".repeat(501);
        BizException ex = assertThrows(BizException.class,
                () -> service.submit(CUSTOMER_ID, cmd(rating, tooLong, List.of(), false)));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verifyNoInteractions(orderQueryService, reviewMapper);
    }

    @Test
    @DisplayName("图片超过 3 张、或 fileId 含路径穿越：抛 PARAM_INVALID")
    void invalidImagesRejected() {
        Integer rating = 5;
        BizException tooMany = assertThrows(BizException.class, () -> service.submit(CUSTOMER_ID,
                cmd(rating, "内容", List.of("a.png", "b.png", "c.png", "d.png"), false)));
        assertEquals(ErrorCode.PARAM_INVALID, tooMany.getErrorCode());

        BizException traversal = assertThrows(BizException.class, () -> service.submit(CUSTOMER_ID,
                cmd(rating, "内容", List.of("../../etc/passwd"), false)));
        assertEquals(ErrorCode.PARAM_INVALID, traversal.getErrorCode());

        BizException blank = assertThrows(BizException.class, () -> service.submit(CUSTOMER_ID,
                cmd(rating, "内容", List.of("  "), false)));
        assertEquals(ErrorCode.PARAM_INVALID, blank.getErrorCode());

        verifyNoInteractions(orderQueryService, reviewMapper);
    }

    @Test
    @DisplayName("商品ID非法：抛 PARAM_INVALID")
    void invalidProductRejected() {
        ReviewDtos.CreateReviewCmd bad = new ReviewDtos.CreateReviewCmd(null, null, 5, "内容", List.of(), false);
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> service.submit(CUSTOMER_ID, bad)).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class,
                        () -> service.list(0L, new PageQuery())).getErrorCode());
    }

    @Test
    @DisplayName("评价列表：分页透传 + 昵称脱敏 + 匿名遮蔽 + 好评率/均分摘要")
    void listMasksNicknameAndSummarises() {
        Page<ReviewRow> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of(row(2L, "张伟", 5, false), row(1L, "李小明", 3, true)));
        dbPage.setTotal(2L);
        when(reviewMapper.selectByProductPage(any(), anyLong())).thenReturn(dbPage);

        ReviewSummaryRow summary = new ReviewSummaryRow();
        summary.setTotal(2L);
        summary.setPositiveCount(1L);
        summary.setNegativeCount(1L);
        summary.setAverageRating(new BigDecimal("4.0000"));
        when(reviewMapper.selectSummary(PRODUCT_ID)).thenReturn(summary);

        ReviewDtos.ReviewPageVO page = service.list(PRODUCT_ID, new PageQuery());

        assertEquals(2L, page.total());
        assertEquals(2, page.list().size());
        ReviewDtos.ReviewVO first = page.list().get(0);
        assertEquals("张**", first.nickname(), "非匿名昵称保留首字，其余打码");
        assertEquals("张", first.avatarText());
        assertFalse(first.anonymous());

        ReviewDtos.ReviewVO second = page.list().get(1);
        assertEquals("匿名用户", second.nickname());
        assertTrue(second.anonymous());

        assertEquals(2L, page.summary().total());
        assertEquals(1L, page.summary().negativeCount(), "差评数 = rating<=2 的条数（3 星属于中评，不计入差评）");
        assertEquals(50, page.summary().positiveRate(), "好评率 = rating>=4 的占比");
        assertEquals("4.0", page.summary().avgRating());
    }

    @Test
    @DisplayName("摘要区分好评/差评：中评(3 星)两边都不算，好评+差评可以不等于总数")
    void summaryCountsPositiveAndNegativeSeparately() {
        Page<ReviewRow> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of(row(3L, "王五", 3, false)));
        dbPage.setTotal(1L);
        when(reviewMapper.selectByProductPage(any(), anyLong())).thenReturn(dbPage);

        ReviewSummaryRow summary = new ReviewSummaryRow();
        summary.setTotal(5L);
        summary.setPositiveCount(3L);
        summary.setNegativeCount(1L);
        summary.setAverageRating(new BigDecimal("3.6000"));
        when(reviewMapper.selectSummary(PRODUCT_ID)).thenReturn(summary);

        ReviewDtos.ReviewSummaryVO vo = service.list(PRODUCT_ID, new PageQuery()).summary();

        assertEquals(5L, vo.total());
        assertEquals(3L, vo.positiveCount());
        assertEquals(1L, vo.negativeCount(), "5 条里只有 1 条 <=2 星 → 差评 1 条");
        assertEquals(60, vo.positiveRate());
    }

    @Test
    @DisplayName("摘要空集：无评价或聚合行为 null 时好评/差评都必须是 0，前端不能显示 null/NaN")
    void summaryEmptyProductHasZeroCounts() {
        Page<ReviewRow> empty = new Page<>(1, 10);
        empty.setRecords(List.of());
        empty.setTotal(0L);
        when(reviewMapper.selectByProductPage(any(), anyLong())).thenReturn(empty);
        when(reviewMapper.selectSummary(PRODUCT_ID)).thenReturn(null);

        ReviewDtos.ReviewSummaryVO vo = service.list(PRODUCT_ID, new PageQuery()).summary();

        assertEquals(0L, vo.total());
        assertEquals(0L, vo.positiveCount());
        assertEquals(0L, vo.negativeCount());
        assertEquals(0, vo.positiveRate());
        assertEquals("0.0", vo.avgRating());
    }

    @Test
    @DisplayName("评价资格：已购买且未评价 → 可评价（详情页据此显示「去评价」）")
    void eligibilityWhenPurchasedAndNotReviewed() {
        when(orderQueryService.findCompletedOrderNoForReview(CUSTOMER_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(ORDER_NO));
        when(reviewMapper.countByOrderAndProduct(ORDER_NO, PRODUCT_ID)).thenReturn(0);

        ReviewDtos.ReviewEligibilityVO vo = service.eligibility(CUSTOMER_ID, PRODUCT_ID);

        assertTrue(vo.eligible());
        assertFalse(vo.reviewed());
    }

    @Test
    @DisplayName("评价资格：已购买但已评价 → 不可再评（前端口径不能靠 409 反推）")
    void eligibilityWhenAlreadyReviewed() {
        when(orderQueryService.findCompletedOrderNoForReview(CUSTOMER_ID, PRODUCT_ID, null))
                .thenReturn(Optional.of(ORDER_NO));
        when(reviewMapper.countByOrderAndProduct(ORDER_NO, PRODUCT_ID)).thenReturn(1);

        ReviewDtos.ReviewEligibilityVO vo = service.eligibility(CUSTOMER_ID, PRODUCT_ID);

        assertFalse(vo.eligible());
        assertTrue(vo.reviewed());
    }

    @Test
    @DisplayName("评价资格：未购买 / 未登录 / 商品非法 → 一律不可评价且不报错（详情页要能正常渲染）")
    void eligibilityWhenNotPurchased() {
        when(orderQueryService.findCompletedOrderNoForReview(CUSTOMER_ID, PRODUCT_ID, null))
                .thenReturn(Optional.empty());
        assertFalse(service.eligibility(CUSTOMER_ID, PRODUCT_ID).eligible());

        assertFalse(service.eligibility(0L, PRODUCT_ID).eligible(), "未登录不能 401，否则详情页打不开");
        assertFalse(service.eligibility(CUSTOMER_ID, 0L).eligible());
        verifyNoInteractions(reviewMapper);
    }

    // ---------- 评价中心（2026-11-28）：已写评论 / 浏览数 / 获赞数 ----------

    private MyReviewRow myRow(long id, int rating, int viewCount, int likeCount, String status) {
        MyReviewRow r = new MyReviewRow();
        r.setId(id);
        r.setProductId(PRODUCT_ID);
        r.setProductTitle("森特训练短袖");
        r.setMainImage("f-main-1");
        r.setRating(rating);
        r.setContent("面料透气");
        r.setImagesJson("[]");
        r.setIsAnonymous(0);
        r.setStatus(status);
        r.setViewCount(viewCount);
        r.setLikeCount(likeCount);
        r.setCreatedAt(LocalDateTime.of(2026, 11, 28, 9, 30));
        return r;
    }

    @Test
    @DisplayName("评价中心：未登录抛 UNAUTHENTICATED，且不查库")
    void myReviewsRejectsAnonymous() {
        BizException ex = assertThrows(BizException.class,
                () -> service.myReviews(0L, new PageQuery(), null));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verifyNoInteractions(reviewMapper);
    }

    @Test
    @DisplayName("评价中心：列表带回商品标题/主图/浏览数/获赞数，隐藏评价也出行并标记 status")
    void myReviewsReturnsRowsAndMetrics() {
        Page<MyReviewRow> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of(myRow(3L, 5, 128, 12, "NORMAL"),
                myRow(2L, 1, 9, 0, "HIDDEN")));
        dbPage.setTotal(2L);
        when(reviewMapper.selectMinePage(any(), anyLong(), any())).thenReturn(dbPage);

        MyReviewSummaryRow summary = new MyReviewSummaryRow();
        summary.setReviewCount(2L);
        summary.setViewCount(137L);
        summary.setLikeCount(12L);
        when(reviewMapper.selectMineSummary(CUSTOMER_ID)).thenReturn(summary);

        ReviewDtos.MyReviewPageVO page = service.myReviews(CUSTOMER_ID, new PageQuery(), "LATEST");

        assertEquals(2, page.list().size());
        ReviewDtos.MyReviewVO first = page.list().get(0);
        assertEquals("3", first.reviewId());
        assertEquals(String.valueOf(PRODUCT_ID), first.productId());
        assertEquals("森特训练短袖", first.productTitle());
        assertEquals("f-main-1", first.mainImage());
        assertEquals(128L, first.viewCount());
        assertEquals(12L, first.likeCount());
        assertEquals("NORMAL", first.status());
        assertEquals("HIDDEN", page.list().get(1).status(), "被隐藏的评价仍属于「我写过的」，不能从列表消失");

        assertEquals(2L, page.summary().reviewCount());
        assertEquals(137L, page.summary().viewCount());
        assertEquals(12L, page.summary().likeCount());
    }

    @Test
    @DisplayName("评价中心空态：没写过评价时三项统计都是 0（不是 null/NaN）")
    void myReviewsEmptySummaryIsZero() {
        Page<MyReviewRow> empty = new Page<>(1, 10);
        empty.setRecords(List.of());
        empty.setTotal(0L);
        when(reviewMapper.selectMinePage(any(), anyLong(), any())).thenReturn(empty);
        when(reviewMapper.selectMineSummary(CUSTOMER_ID)).thenReturn(null);

        ReviewDtos.MyReviewPageVO page = service.myReviews(CUSTOMER_ID, new PageQuery(), "LATEST");

        assertEquals(0, page.list().size());
        assertEquals(0L, page.summary().reviewCount());
        assertEquals(0L, page.summary().viewCount());
        assertEquals(0L, page.summary().likeCount());
        assertEquals(0L, page.pages());
    }
}
