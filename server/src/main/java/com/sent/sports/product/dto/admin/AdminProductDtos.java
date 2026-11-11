package com.sent.sports.product.dto.admin;

import com.sent.sports.common.dto.TierDto;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 商品管理接口 Web 层 DTO。
 * 商品/SKU 表单、阶梯价、一客一价、上/下架指令，以及对应视图。
 */
public final class AdminProductDtos {

    private AdminProductDtos() {
    }

    /**
     * SKU 表单：{@code skuId} 为空表示新增行；{@code skuCode} 留空时由后端生成唯一编码；
     * {@code spuId} 仅独立 SKU 端点需要，商品编辑保存由路径上的商品 id 提供归属。
     */
    public record SkuForm(
            Long skuId,
            Long spuId,
            String skuCode,
            String spec,
            @NotNull(message = "分类不能为空") Long categoryId,
            @Min(value = 0, message = "零售价不能小于0") long retailPriceCent,
            @Min(value = 0, message = "库存不能小于0") int stock) {

        public SkuForm(String skuCode, String spec, Long categoryId, long retailPriceCent, int stock) {
            this(null, null, skuCode, spec, categoryId, retailPriceCent, stock);
        }

        public SkuForm(Long skuId, String skuCode, String spec, Long categoryId,
                       long retailPriceCent, int stock) {
            this(skuId, null, skuCode, spec, categoryId, retailPriceCent, stock);
        }
    }

    /** 商品表单：名称、分类、描述、SKU 集合。 */
    public record ProductForm(
            @NotBlank(message = "商品名称不能为空") String name,
            @NotNull(message = "分类不能为空") Long categoryId,
            String description,
            List<@Valid SkuForm> skus) {
    }

    /** 上/下架指令。 */
    public record PublishCmd(boolean onShelf) {
    }

    /** 阶梯价配置（按 SKU）：阶梯区间集合。 */
    public record LadderForm(
            @NotNull(message = "SKU不能为空") Long skuId,
            @NotNull(message = "阶梯不能为空") List<TierDto> tiers) {
    }

    /** 一客一价表单。 */
    public record CustomerPriceForm(
            @NotNull(message = "SKU不能为空") Long skuId,
            @NotNull(message = "客户不能为空") Long customerId,
            @Positive(message = "价格必须大于0") long priceCent,
            long validFromMillis,
            long validToMillis) {
    }

    /**
     * 商品视图：{@code skuCount} 是 SKU 行数（折扣不会增删 SKU，故不随折扣变化）；
     * {@code discountSkuCount} 是其中已配置阶梯价的 SKU 数，配/删折扣后该值会变。
     */
    public record ProductVO(String productId, String name, Long categoryId, boolean onShelf,
                            int skuCount, String categoryName, int discountSkuCount) {
    }

    /** SKU 视图。 */
    public record SkuVO(String skuId, String skuCode, String spec, long retailPriceCent, int stock) {
    }

    /** 阶梯价视图。 */
    public record LadderVO(Long skuId, List<TierDto> tiers) {
    }

    /**
     * 一客一价视图：有效期为空时以 0 毫秒回传。
     * <p>客户名/手机号、商品名、规格与零售价由列表 SQL 联查带出，前端无需再按 id 回查，
     * 否则一页专属价会打出 N 次客户/商品详情请求。</p>
     */
    public record CustomerPriceVO(Long id, Long skuId, Long customerId, long priceCent,
                                  long validFromMillis, long validToMillis,
                                  String customerName, String customerPhone,
                                  Long productId, String productName, String skuCode,
                                  String spec, long retailPriceCent) {

        /** 写后回显等场景：只有 id 与价格时用空名称占位，前端按需展示。 */
        public CustomerPriceVO(Long id, Long skuId, Long customerId, long priceCent,
                               long validFromMillis, long validToMillis) {
            this(id, skuId, customerId, priceCent, validFromMillis, validToMillis,
                    null, null, null, null, null, null, 0L);
        }
    }

    /** 占位分页列表类型，供控制器返回空结果。 */
    public static PageResult<ProductVO> emptyPage(PageQuery page) {
        return PageResult.of(List.of(), 0L, page.getPage(), page.getSize());
    }
}
