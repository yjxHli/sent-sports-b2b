package com.sent.sports.order.service.dto;

import com.sent.sports.common.dto.TierDto;

import java.util.List;

/**
 * 下单单行指令：携带取价所需中立参数（对齐 product.service.dto.PriceRequest）。
 * <p>{@code customFlag} 为 Phase 10 新增：标记该行是 UV 定制件，下单时由
 * {@code CustomizeShipGateway} 落成 {@code custom_order}，客户随后即可上传图稿走审图流程。
 * 保留七参构造，既有调用方（含集成测试）无需改动。</p>
 */
public record OrderLineCmd(
        long skuId,
        int qty,
        String customerTypeCode,
        int memberDiscountRate,
        int agreementDiscountRate,
        boolean allowStacking,
        List<TierDto> ladderTiers,
        boolean customFlag) {

    public OrderLineCmd(long skuId, int qty, String customerTypeCode,
                        int memberDiscountRate, int agreementDiscountRate,
                        boolean allowStacking, List<TierDto> ladderTiers) {
        this(skuId, qty, customerTypeCode, memberDiscountRate, agreementDiscountRate,
                allowStacking, ladderTiers, false);
    }
}
