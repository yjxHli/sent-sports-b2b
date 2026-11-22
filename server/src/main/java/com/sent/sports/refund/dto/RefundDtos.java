package com.sent.sports.refund.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 退款接口 Web 层 DTO（P4-21）。
 * 请求体承载外部入参并带校验注解；响应体 {@link RefundVO} 暴露退款单状态与金额（分 + 元）。
 */
public final class RefundDtos {

    private RefundDtos() {
    }

    /**
     * 申请退款请求：订单号、逐行退款金额、原因、凭证（必填）、凭证图片。
     * <p>{@code images} 是文件服务返回的 fileId 列表，用户拍照或从相册上传后由前端先传文件再带过来；
     * 审核端据此核验实物，故与 {@code proof} 文案分开存两列。</p>
     */
    public record ApplyRefundCmd(
            @NotBlank(message = "订单号不能为空") String orderNo,
            @NotNull(message = "退款行不能为空") List<RefundLineCmd> lines,
            String reason,
            @NotBlank(message = "退款凭证不能为空") String proof,
            List<String> images) {
    }

    /** 申请退款单行：SKU + 申请退款金额（分）。 */
    public record RefundLineCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @NotNull(message = "金额不能为空") @Positive(message = "金额必须大于0") Long amountCent) {
    }

    /**
     * 退款响应：退款单号 + 订单号 + 状态 + 金额（分 + 元）。
     * <p>2026-11-16 补 {@code createdAtMillis / reason / itemName / itemQty / productId}：
     * 「我的售后」页要按淘宝版式展示「商品名 + 数量 + 申请时间 + 退款原因」，
     * 一次列表查询带全，前端不必为每张卡片再查订单。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RefundVO(String refundNo, String orderNo, String status, Long amountCent, String amountYuan,
                           Long createdAtMillis, String reason, String itemName, int itemQty, String productId) {

        /** 兼容「申请退款 / 申诉」两个动作只需回单号状态的旧调用方。 */
        public RefundVO(String refundNo, String orderNo, String status, Long amountCent, String amountYuan) {
            this(refundNo, orderNo, status, amountCent, amountYuan, null, null, null, 0, null);
        }
    }

    /** 售后角标计数（个人中心「退款/售后」）。total = 全部售后单，inFlight = 待审核 + 退款中。 */
    public record RefundCountVO(long total, long inFlight) {
    }

    /** 用户端售后详情：在列表字段基础上补充类型 / 原因 / 申请人 / 申诉次数 / 凭证图片 / 时间线。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RefundDetailVO(
            String refundNo, String orderNo, String status,
            Long amountCent, String amountYuan,
            String type, String reason, String applicantType,
            int appealCount, List<String> images, Long createdAtMillis, Long refundedAtMillis) {
    }

    /** 管理端审核动作请求：approved=true 走 APPROVE（需确认令牌），false 走 REJECT（原因必填）。 */
    public record AuditCmd(@NotNull(message = "审核结论不能为空") Boolean approved, String remark) {
    }

    /**
     * 管理端退款审核列表行（对齐 packages/shared/src/types.ts 的 RefundVO）；images 为用户上传的凭证图片。
     * <p>{@code needManualReview}：定制单「审核通过未排产」的退款申请，客服必须确认是否已备料再放行。</p>
     */
    public record RefundAuditVO(String refundNo, String orderNo, String status, long amountCent,
                                String reason, List<String> images, long createdAt,
                                boolean needManualReview) {
    }

    /**
     * 管理端退款审核详情（单笔）：退款单信息 + 订单基本信息 + 客户 + 商品明细。
     * <p>审核员要在同一屏判断「这笔钱该不该退」，因此订单头、金额拆分、
     * 发货物流、商品行与凭证图片必须一次给全，避免开第二个页面来回切。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RefundAuditDetailVO(
            String refundNo, String orderNo, String status,
            Long amountCent, String amountYuan, Long pointRefund,
            String type, String reason, String applicantType,
            int appealCount, List<String> images, String auditRemark,
            Long createdAt, Long refundedAt,
            AuditOrderVO order,
            /** 定制单审核通过未排产：放行前须与生产确认是否已备料。 */
            boolean needManualReview) {
    }

    /**
     * 审核详情里的订单基本信息（中性，不依赖 order 模块的领域类型）。
     * <p>{@code shipStatus} 与订单详情同口径：orders 表没有独立列，按主状态推导；
     * 收货信息来自 {@code address_snapshot_json} 快照，解析失败只丢字段不影响页面。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditOrderVO(
            Long orderId, String orderNo,
            String status, String payStatus, String shipStatus, String afterSaleStatus,
            Long customerId, String customerName, String customerPhone, String customerNo,
            String customerType, String customerLevel,
            Long createdAt, Long paidAt, Long completedAt,
            Long goodsAmountCent, Long freightCent, Long pointDeductCent, Long discountCent,
            Long payableCent, Long paidCent,
            String receiver, String receiverPhone, String address,
            boolean shipped, String carrier, String trackingNo, Long shippedAt,
            String remark,
            List<AuditOrderLineVO> lines) {
    }

    /** 审核详情里的订单行：快照商品名/规格 + 数量 + 单价 + 行金额 + 行实付 + 已发数量 + 定制状态。 */
    public record AuditOrderLineVO(Long skuId, String skuName, String spec, Integer qty,
                                   Long unitPriceCent, Long amountCent, Long linePaidCent,
                                   Integer shippedQty, String customStatus) {
    }
}
