package com.sent.sports.review;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.review.dto.admin.AdminReviewDtos;
import com.sent.sports.review.mapper.ReviewMapper;
import com.sent.sports.review.mapper.po.AdminReviewRow;
import com.sent.sports.review.service.impl.ReviewAdminServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 评价治理用例（V56，TDD 红）。
 *
 * <p>运营能下架违规评价，但这条能力必须守五条口径：</p>
 * <ol>
 *   <li><b>状态白名单</b>：只接受 NORMAL / HIDDEN，脏值不能落库把过滤条件搞乱；</li>
 *   <li><b>幂等</b>：重复点「隐藏」不该写一串重复日志，也不该报错；</li>
 *   <li><b>可追溯</b>：每次真实状态变更写一条 {@code sys_operation_log}，记下前后值与操作人；</li>
 *   <li><b>脱敏</b>：管理端列表同样只给脱敏昵称，匿名评价连首字都不给；</li>
 *   <li><b>筛选规范化</b>：空白串归一成 null，非法评分直接拒绝。</li>
 * </ol>
 */
class ReviewAdminServiceTest extends BaseUnitTest {

    private static final long REVIEW_ID = 8001L;
    private static final long PRODUCT_ID = 9701L;
    private static final long OPERATOR_ID = 9001L;

    @Mock
    private ReviewMapper reviewMapper;

    private ReviewAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewAdminServiceImpl(reviewMapper);
    }

    private AdminReviewRow row(String status, int rating, int anonymous) {
        AdminReviewRow r = new AdminReviewRow();
        r.setReviewId(REVIEW_ID);
        r.setProductId(PRODUCT_ID);
        r.setProductTitle("森特速干训练短袖");
        r.setRating(rating);
        r.setContent("尺码偏大，退了半码才合适");
        r.setImagesJson("[]");
        r.setIsAnonymous(anonymous);
        r.setNickname("张伟");
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.of(2026, 11, 22, 20, 0));
        return r;
    }

    @Test
    @DisplayName("列表：空白筛选归一 null，页码/条数透传，昵称脱敏且匿名为「匿名用户」")
    void listNormalisesFiltersAndMasksNickname() {
        Page<AdminReviewRow> dbPage = new Page<>(1, 20);
        dbPage.setRecords(List.of(row("NORMAL", 2, 0), row("HIDDEN", 5, 1)));
        dbPage.setTotal(2L);
        when(reviewMapper.selectAdminPage(any(), any(), any(), any())).thenReturn(dbPage);

        PageResult<AdminReviewDtos.AdminReviewVO> page =
                service.list(null, null, "  ", new PageQuery());

        assertEquals(2L, page.getTotal());
        assertEquals("张**", page.getList().get(0).userLabel(), "非匿名只保留首字");
        assertEquals("匿名用户", page.getList().get(1).userLabel(), "匿名评价不得泄露昵称");
        assertEquals("HIDDEN", page.getList().get(1).status());
        assertEquals("森特速干训练短袖", page.getList().get(0).productTitle());

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(reviewMapper).selectAdminPage(any(), any(), any(), status.capture());
        assertEquals(null, status.getValue(), "空白状态必须归一成 null，否则 SQL 会按空串过滤成 0 行");
    }

    @Test
    @DisplayName("列表：评分越界或状态非法直接 PARAM_INVALID，不查库")
    void listRejectsInvalidFilters() {
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> service.list(null, 0, null, new PageQuery())).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> service.list(null, 6, null, new PageQuery())).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> service.list(null, null, "DELETED", new PageQuery())).getErrorCode());
        verifyNoInteractions(reviewMapper);
    }

    @Test
    @DisplayName("隐藏评价：状态更新 + 写一条 HIDE 操作日志（含前后状态与操作人）")
    void hideWritesOperationLog() {
        when(reviewMapper.selectAdminById(REVIEW_ID)).thenReturn(row("NORMAL", 2, 0));
        when(reviewMapper.updateStatus(anyLong(), any(), any())).thenReturn(1);

        AdminReviewDtos.AdminReviewVO vo = service.updateStatus(REVIEW_ID, "hidden", "含违规联系方式", OPERATOR_ID);

        assertEquals("HIDDEN", vo.status(), "状态统一大写落库");
        verify(reviewMapper).updateStatus(REVIEW_ID, "HIDDEN", OPERATOR_ID);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> afterJson = ArgumentCaptor.forClass(String.class);
        verify(reviewMapper).insertStatusLog(eq(OPERATOR_ID), action.capture(), eq(String.valueOf(REVIEW_ID)),
                eq("NORMAL"), afterJson.capture(), eq("含违规联系方式"), eq(OPERATOR_ID));
        assertEquals("HIDE", action.getValue());
        assertTrue(afterJson.getValue().contains("HIDDEN"), "after_json 必须记录变更后的状态");
    }

    @Test
    @DisplayName("重复隐藏/恢复同一状态：幂等，不再写库也不再写日志")
    void sameStatusIsNoop() {
        when(reviewMapper.selectAdminById(REVIEW_ID)).thenReturn(row("HIDDEN", 2, 0));

        AdminReviewDtos.AdminReviewVO vo = service.updateStatus(REVIEW_ID, "HIDDEN", null, OPERATOR_ID);

        assertEquals("HIDDEN", vo.status());
        verify(reviewMapper, never()).updateStatus(anyLong(), any(), any());
        verify(reviewMapper, never()).insertStatusLog(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("恢复展示：写 RESTORE 日志；评价不存在 → NOT_FOUND")
    void restoreAndNotFound() {
        when(reviewMapper.selectAdminById(REVIEW_ID)).thenReturn(row("HIDDEN", 2, 0));
        when(reviewMapper.updateStatus(anyLong(), any(), any())).thenReturn(1);

        AdminReviewDtos.AdminReviewVO vo = service.updateStatus(REVIEW_ID, "NORMAL", null, OPERATOR_ID);
        assertEquals("NORMAL", vo.status());

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(reviewMapper).insertStatusLog(eq(OPERATOR_ID), action.capture(), eq(String.valueOf(REVIEW_ID)),
                eq("HIDDEN"), any(), eq(null), eq(OPERATOR_ID));
        assertEquals("RESTORE", action.getValue());

        when(reviewMapper.selectAdminById(9999L)).thenReturn(null);
        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BizException.class,
                        () -> service.updateStatus(9999L, "HIDDEN", null, OPERATOR_ID)).getErrorCode());
    }

    @Test
    @DisplayName("状态白名单：null / 空串 / 未知值一律 PARAM_INVALID；评价 id 非法同样拒绝")
    void invalidStatusRejected() {
        for (String bad : new String[]{null, "", "  ", "DELETED"}) {
            assertEquals(ErrorCode.PARAM_INVALID,
                    assertThrows(BizException.class,
                            () -> service.updateStatus(REVIEW_ID, bad, null, OPERATOR_ID)).getErrorCode(),
                    "status=" + bad);
        }
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class,
                        () -> service.updateStatus(0L, "HIDDEN", null, OPERATOR_ID)).getErrorCode());
        // 小写可接受（前端/curl 都容易写小写），但落库必须大写
        when(reviewMapper.selectAdminById(REVIEW_ID)).thenReturn(row("NORMAL", 5, 0));
        when(reviewMapper.updateStatus(anyLong(), any(), any())).thenReturn(1);
        assertEquals("HIDDEN", service.updateStatus(REVIEW_ID, "hidden", null, OPERATOR_ID).status());
    }

    @Test
    @DisplayName("图片 JSON 解析失败不影响列表：按无图处理，不让治理页整页报错")
    void brokenImagesJsonDegradesToEmpty() {
        AdminReviewRow broken = row("NORMAL", 3, 0);
        broken.setImagesJson("{not-json");
        Page<AdminReviewRow> dbPage = new Page<>(1, 20);
        dbPage.setRecords(List.of(broken));
        dbPage.setTotal(1L);
        when(reviewMapper.selectAdminPage(any(), any(), any(), any())).thenReturn(dbPage);

        assertTrue(service.list(PRODUCT_ID, null, null, new PageQuery()).getList().get(0).images().isEmpty());
    }
}
