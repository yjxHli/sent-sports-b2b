package com.sent.sports.coupon.dto;

/** 优惠券对外契约；金额单位分，时间毫秒（与全站 JacksonConfig 口径一致）。 */
public final class CouponDtos {

    private CouponDtos() {
    }

    /**
     * 用户持有券。couponType/discountType/discountRate/scopeType/scopeIds/customerScope
     * 是发券时的模板快照，商城端据此渲染「9折」「斯伯丁篮球专享」「企业专属」等标签。
     */
    public record CouponVO(long couponId, long customerId, String couponNo, String name,
                           long amountCent, long thresholdCent, String status, String source,
                           String reason, long expireAtMillis, long usedAtMillis, String orderNo,
                           String couponType, String discountType, int discountRate,
                           String scopeType, String scopeIds, String customerScope) {
    }

    /** 管理端可选券档位；带券型与范围，运营发券时能看清是哪种券。 */
    public record TemplateVO(long templateId, String name, long amountCent, long thresholdCent,
                             int validDays, String couponType, String discountType,
                             int discountRate, String scopeType, String scopeIds,
                             String customerScope) {
    }

    /** 领券中心卡片：remainQty 为 -1 表示不限量，0 表示已领完。
     *  claimValidDays 是会员日领取后的实际有效天数（由 MemberDayPolicy 统一算出）。 */
    public record ClaimableVO(long templateId, String name, long amountCent, long thresholdCent,
                              String couponType, String discountType, int discountRate,
                              String scopeType, String scopeIds, String customerScope,
                              int validDays, int remainQty, boolean claimed, int claimValidDays) {
    }

    public record IssueVO(long couponId, long customerId, String couponNo, String name,
                          long amountCent, long thresholdCent, String source, String reason,
                          long createdAtMillis, String couponType, String discountType,
                          int discountRate) {
    }

    /** 领取结果：券号用于前端提示与跳转「我的优惠券」。 */
    public record ClaimResult(String couponNo) {
    }

    /**
     * 管理端券模板列表行（2026-11-18）：券号 / 创建人 / 创建时间是运营的核对依据，
     * 券型与范围决定它能不能用于「特定商品」。
     */
    public record TemplatePageVO(long templateId, String templateNo, String name, String couponType,
                                 String discountType, int discountRate, long amountCent,
                                 long thresholdCent, String scopeType, String scopeIds,
                                 String customerScope, int validDays, int claimable, int totalQty,
                                 int claimedQty, String status, long createdBy, String creatorName,
                                 long createdAtMillis) {
    }

    /** 管理端发放记录列表行：比 IssueVO 多客户昵称与券状态，供分页表格与导出使用。 */
    public record IssuePageVO(long couponId, long customerId, String customerName, String couponNo,
                              String name, String couponType, String discountType, int discountRate,
                              long amountCent, long thresholdCent, String status, String source,
                              String reason, long expireAtMillis, long createdAtMillis) {
    }

    /** 新建券模板结果：运营拿到券号就能立刻在发放下拉里找到它。 */
    public record CreateTemplateResult(long templateId, String templateNo) {
    }
}
