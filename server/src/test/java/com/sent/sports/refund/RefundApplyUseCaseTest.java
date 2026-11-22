package com.sent.sports.refund;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customize.service.CustomRefundPolicy;
import com.sent.sports.customize.service.CustomizeRefundGateway;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.order.service.dto.RefundLineView;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.RefundApplyUseCase;
import com.sent.sports.refund.service.dto.RefundApplyCmd;
import com.sent.sports.refund.service.dto.RefundApplyLineCmd;
import com.sent.sports.refund.service.dto.RefundApplyResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 申请退款用例测试（P3-18）：覆盖 plan §7.6 与验收项——
 * 凭证必填、定制三档退款处置、超退拒绝(REFUND_OVERFLOW)、未知行/订单不存在、生成待审核退款单。
 *
 * <p>2026-11-21 用户口径把「定制是否可退」从布尔升级成三档（{@link CustomRefundPolicy}），
 * 因此这里断言的是用例<b>拿到档位后的分流</b>：</p>
 * <ul>
 *   <li>BLOCKED（生产中/已完工/已发货）→ 抛 {@link ErrorCode#CUSTOM_REFUND_NOT_ALLOWED}，不落库；</li>
 *   <li>MANUAL_REVIEW（审核通过未排产）→ 允许提交，但退款单必须打人工复核标记；</li>
 *   <li>SELF_SERVICE / NOT_CUSTOM → 常规待审核单，无标记。</li>
 * </ul>
 */
class RefundApplyUseCaseTest extends BaseUseCaseTest {

    @Mock
    private OrderRefundViewService orderRefundViewService;
    @Mock
    private CustomizeRefundGateway customizeRefundGateway;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;

    @InjectMocks
    private RefundApplyUseCase useCase;

    @Captor
    private ArgumentCaptor<RefundOrder> refundCaptor;

    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");
    private static final String ORDER_NO = "SO20240101000099";

    private OrderRefundView singleLineView(long paid, long already, long shipping, boolean shipped) {
        return new OrderRefundView(ORDER_NO, 1L, 1L,
                List.of(new RefundLineView(1L, paid, already)), shipping, shipped);
    }

    /** 两行订单：1L 普通行、2L 定制行，用于验证「任一行被拦即整单拒绝」。 */
    private OrderRefundView twoLineView() {
        return new OrderRefundView(ORDER_NO, 1L, 1L,
                List.of(new RefundLineView(1L, 5000L, 0L), new RefundLineView(2L, 5000L, 0L)), 0L, false);
    }

    private void stubView(OrderRefundView view) {
        when(orderRefundViewService.getView(ORDER_NO)).thenReturn(Optional.of(view));
    }

    private void stubPolicy(CustomRefundPolicy policy) {
        when(customizeRefundGateway.policyOf(anyString(), anyLong())).thenReturn(policy);
    }

    /** 只有真的会落库的用例才需要单号与时钟；否则 Mockito 严格模式会判为多余桩。 */
    private void stubAuditTrail() {
        when(idGen.next("RF")).thenReturn("RF20240101000001");
        when(clock.instant()).thenReturn(FIXED);
    }

    @Test
    void applyCreatesPendingRefundOrderAndReturnsResult() {
        stubView(singleLineView(10000L, 0L, 0L, false));
        stubPolicy(CustomRefundPolicy.NOT_CUSTOM);
        stubAuditTrail();

        RefundApplyResult result = useCase.apply(new RefundApplyCmd(ORDER_NO,
                List.of(new RefundApplyLineCmd(1L, 5000L)), "不想要了", "proof-img", List.of()));

        assertEquals("RF20240101000001", result.refundNo());
        assertEquals(ORDER_NO, result.orderNo());
        assertEquals("PENDING", result.status());
        assertEquals(5000L, result.amountCent());
        verify(refundRepository).save(refundCaptor.capture());
        RefundOrder saved = refundCaptor.getValue();
        assertEquals(RefundStatus.PENDING, saved.getStatus());
        assertEquals(5000L, saved.getAmountCent());
        assertEquals("REFUND", saved.getType());
        assertFalse(saved.isNeedManualReview(), "普通退款不该挂人工复核标记");
    }

    @Test
    void overRefundRejected() {
        stubView(singleLineView(10000L, 0L, 0L, false));
        stubPolicy(CustomRefundPolicy.NOT_CUSTOM);

        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                ORDER_NO, List.of(new RefundApplyLineCmd(1L, 15000L)), "r", "p", List.of())));
        assertEquals(ErrorCode.REFUND_OVERFLOW, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    @DisplayName("定制已进生产（BLOCKED）：抛专用错误码 CUSTOM_REFUND_NOT_ALLOWED，不留半截退款单")
    void customizeInProductionRejectedWithDedicatedCode() {
        stubView(singleLineView(10000L, 0L, 0L, false));
        when(customizeRefundGateway.policyOf(ORDER_NO, 1L)).thenReturn(CustomRefundPolicy.BLOCKED);

        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                ORDER_NO, List.of(new RefundApplyLineCmd(1L, 5000L)), "r", "p", List.of())));

        assertEquals(ErrorCode.CUSTOM_REFUND_NOT_ALLOWED, ex.getErrorCode(),
                "定制进入生产后必须用专用错误码拒绝，前端据此弹「已开始生产」提示");
        assertTrue(ex.resolvedMessage().contains("生产"),
                "拒绝原因要让用户看懂是「已开始生产」，而不是笼统的状态冲突");
        verify(refundRepository, never()).save(any());
    }

    @Test
    @DisplayName("多行订单里只要有一行定制被拦，整单拒绝（不能只退普通行后留个烂摊子）")
    void anyBlockedLineRejectsWholeApplication() {
        stubView(twoLineView());
        when(customizeRefundGateway.policyOf(ORDER_NO, 1L)).thenReturn(CustomRefundPolicy.NOT_CUSTOM);
        when(customizeRefundGateway.policyOf(ORDER_NO, 2L)).thenReturn(CustomRefundPolicy.BLOCKED);

        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                ORDER_NO, List.of(new RefundApplyLineCmd(1L, 1000L), new RefundApplyLineCmd(2L, 1000L)),
                "r", "p", List.of())));

        assertEquals(ErrorCode.CUSTOM_REFUND_NOT_ALLOWED, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    @DisplayName("待审核（WAIT_REVIEW）：还没进机器，用户可自助退款，退款单无人工标记")
    void customizeWaitReviewAllowed() {
        stubView(singleLineView(10000L, 0L, 0L, false));
        stubPolicy(CustomRefundPolicy.SELF_SERVICE);
        stubAuditTrail();

        RefundApplyResult result = useCase.apply(new RefundApplyCmd(ORDER_NO,
                List.of(new RefundApplyLineCmd(1L, 5000L)), "还没开始做", "proof", List.of()));

        assertEquals("PENDING", result.status());
        verify(refundRepository).save(refundCaptor.capture());
        assertFalse(refundCaptor.getValue().isNeedManualReview());
    }

    @Test
    @DisplayName("审核通过未排产（APPROVED）：允许提交，但退款单必须打人工复核标记")
    void customizeApprovedNeedsManualReview() {
        stubView(singleLineView(10000L, 0L, 0L, false));
        stubPolicy(CustomRefundPolicy.MANUAL_REVIEW);
        stubAuditTrail();

        useCase.apply(new RefundApplyCmd(ORDER_NO,
                List.of(new RefundApplyLineCmd(1L, 5000L)), "图纸已过审但没投产", "proof", List.of()));

        verify(refundRepository).save(refundCaptor.capture());
        RefundOrder saved = refundCaptor.getValue();
        assertEquals(RefundStatus.PENDING, saved.getStatus(), "APPROVED 不是不能退，仍要生成待审核单");
        assertTrue(saved.isNeedManualReview(), "APPROVED 必须留下「需客服确认是否已备料」的标记");
    }

    @Test
    void missingProofRejected() {
        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                ORDER_NO, List.of(new RefundApplyLineCmd(1L, 5000L)), "r", "  ", List.of())));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void unknownSkuRejected() {
        stubView(singleLineView(10000L, 0L, 0L, false));
        stubPolicy(CustomRefundPolicy.NOT_CUSTOM);

        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                ORDER_NO, List.of(new RefundApplyLineCmd(999L, 5000L)), "r", "p", List.of())));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void orderNotFoundRejected() {
        when(orderRefundViewService.getView(ORDER_NO)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                ORDER_NO, List.of(new RefundApplyLineCmd(1L, 5000L)), "r", "p", List.of())));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void applyKeepsProofImagesForAuditor() {
        stubView(singleLineView(10000L, 0L, 0L, false));
        stubPolicy(CustomRefundPolicy.NOT_CUSTOM);
        stubAuditTrail();

        useCase.apply(new RefundApplyCmd(ORDER_NO,
                List.of(new RefundApplyLineCmd(1L, 2000L)), "尺码不符", "已上传 2 张实物图",
                List.of("2026/11/ab/broken-1.png", "2026/11/ab/broken-2.png")));

        verify(refundRepository).save(refundCaptor.capture());
        assertEquals(List.of("2026/11/ab/broken-1.png", "2026/11/ab/broken-2.png"),
                refundCaptor.getValue().getImages());
    }

    @Test
    void applyWithoutImagesKeepsEmptyList() {
        stubView(singleLineView(10000L, 0L, 0L, false));
        stubPolicy(CustomRefundPolicy.NOT_CUSTOM);
        stubAuditTrail();

        useCase.apply(new RefundApplyCmd(ORDER_NO,
                List.of(new RefundApplyLineCmd(1L, 2000L)), "r", "p", null));

        verify(refundRepository).save(refundCaptor.capture());
        assertEquals(List.of(), refundCaptor.getValue().getImages());
    }
}
