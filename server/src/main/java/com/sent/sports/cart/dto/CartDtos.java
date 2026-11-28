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
            List<TierDto> ladderTiers,
            Boolean customFlag) {
    }

    /** 结算试算请求：整单级参数 + 商品行。 */
    public record CheckoutPreviewCmd(
            int availablePoints,
            int freightFreeThresholdCent,
            int freightFeeCent,
            @NotNull(message = "商品行不能为空") List<PreviewItemCmd> items,
            Long couponId) {

        /** 兼容既有四参请求（不使用优惠券）。 */
        public CheckoutPreviewCmd(int availablePoints, int freightFreeThresholdCent, int freightFeeCent,
                                  List<PreviewItemCmd> items) {
            this(availablePoints, freightFreeThresholdCent, freightFeeCent, items, null);
        }
    }

    /**
     * 购物车项视图。
     *
     * <p>2026-11-28：补 {@code mainImage} / {@code unitPriceCent} / {@code unitPriceYuan}——
     * 个人中心「我的森特」的购物车卡要和收藏 / 足迹统一成「商品图片 + 商品名称 + 商品价格」，
     * 只有 skuName 时前端只能画占位图、也没有价格可展示。
     * 价格取加购时写入的 {@code cart_item.price_snapshot_cent}（快照，不是实时重算），
     * 与购物车页的取价引擎结果分属两个口径：概览只做展示，结算价一律由试算接口给。</p>
     */
    public record CartItemVO(Long itemId, Long skuId, String skuName, int qty, boolean selected,
                             String mainImage, Long unitPriceCent, String unitPriceYuan) {

        /** 兼容老调用方（加购 / 改量只需要行标识与数量）。 */
        public CartItemVO(Long itemId, Long skuId, String skuName, int qty, boolean selected) {
            this(itemId, skuId, skuName, qty, selected, null, null, null);
        }
    }

    /** 试算单行视图：单价（分）+ 元 + 来源 + 是否低于成本 + 行小计。 */
    public record QuoteLineVO(long skuId, long unitPriceCent, String unitPriceYuan,
                              String sourceCode, boolean belowCost,
                              long lineTotalCent, String lineTotalYuan) {
    }

    /** 结算试算视图：逐行 + 商品额 + 定制费 + 运费 + 积分抵扣 + 应付（分 + 元）。 */
    public record QuoteVO(List<QuoteLineVO> lines,
                          long goodsAmountCent, String goodsAmountYuan,
                          long customFeeCent, String customFeeYuan,
                          long freightCent, String freightYuan,
                          long pointDeductCent, String pointDeductYuan,
                          long couponDiscountCent, String couponDiscountYuan,
                          long payableCent, String payableYuan) {
    }
}
