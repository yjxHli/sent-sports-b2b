package com.sent.sports.cart.dto;

import com.sent.sports.common.dto.TierDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 购物车接口 Web 层 DTO（P4-12）。
 * 加购 / 改量 / 结算试算的请求与响应；金额一律分存储，响应附元字符串（plan §8.1）。
 */
public final class CartDtos {

    private CartDtos() {
    }

    /** 加购请求：SKU + 数量。 */
    public record AddItemCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @Positive(message = "数量必须大于0") int qty) {
    }

    /** 改量/勾选请求：作用于指定行。 */
    public record UpdateQtyCmd(
            @Positive(message = "数量必须大于0") int qty,
            boolean selected) {
    }

    /** 试算单行指令：对齐 {@code cart.service.dto.QuoteItemCmd}（中立取价参数）。 */
    public record PreviewItemCmd(
            @NotNull(message = "SKU不能为空") Long skuId,
            @Positive(message = "数量必须大于0") int qty,
            String customerTypeCode,
            int memberDiscountRate,
            int agreementDiscountRate,
            boolean allowStacking,
            List<TierDto> ladderTiers) {
    }

    /** 结算试算请求：整单级参数 + 商品行。 */
    public record CheckoutPreviewCmd(
            int availablePoints,
            int freightFreeThresholdCent,
            int freightFeeCent,
            @NotNull(message = "商品行不能为空") List<PreviewItemCmd> items) {
    }

    /** 购物车项视图。 */
    public record CartItemVO(Long itemId, Long skuId, String skuName, int qty, boolean selected) {
    }

    /** 试算单行视图：单价（分）+ 元 + 来源 + 是否低于成本 + 行小计。 */
    public record QuoteLineVO(long skuId, long unitPriceCent, String unitPriceYuan,
                              String sourceCode, boolean belowCost,
                              long lineTotalCent, String lineTotalYuan) {
    }

    /** 结算试算视图：逐行 + 商品额 + 运费 + 积分抵扣 + 应付（分 + 元）。 */
    public record QuoteVO(List<QuoteLineVO> lines,
                          long goodsAmountCent, String goodsAmountYuan,
                          long freightCent, String freightYuan,
                          long pointDeductCent, String pointDeductYuan,
                          long payableCent, String payableYuan) {
    }
}
