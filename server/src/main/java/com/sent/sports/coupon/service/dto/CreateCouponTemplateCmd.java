package com.sent.sports.coupon.service.dto;

/**
 * 管理端自建券模板（2026-11-18）。
 *
 * @param scopeType    ALL / SPU / SKU；品类券必须是 SPU/SKU
 * @param scopeIds     逗号分隔的 SPU 或 SKU 主键
 * @param claimable    是否放进领券中心（自建券默认 0）
 * @param totalQty     领券中心库存，-1 = 不限量
 * @param status       ACTIVE / INACTIVE
 */
public record CreateCouponTemplateCmd(
        String name,
        String couponType,
        String discountType,
        long amountCent,
        long thresholdCent,
        int discountRate,
        String scopeType,
        String scopeIds,
        String customerScope,
        int validDays,
        int claimable,
        int totalQty,
        String status) {
}
