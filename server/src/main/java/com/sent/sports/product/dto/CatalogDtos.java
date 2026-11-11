package com.sent.sports.product.dto;

import java.util.List;

/**
 * 商品浏览 Web 层 DTO（P4-09）。
 * 价格按 §8.1 同时给出 {@code *_cent}（整数分）与 {@code *_Yuan}（展示字符串），避免前端各自除 100。
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    /** 列表筛选条件（绑定自查询参数）。 */
    public record ProductFilter(Long categoryId, String keyword) {
    }

    /**
     * 价格视图：零售价必填，会员价/协议价按身份可选（匿名时为空）。
     * <p>{@code ladderPriceCent} 为阶梯价中的最低绝对价（分），由目录 SQL 关联 {@code ladder_price} 聚合得到；
     * 无折扣时为 null。商品编辑页的 SKU 矩阵据此展示折扣价。</p>
     */
    public record PriceVO(
            long retailPriceCent,
            String retailPriceYuan,
            Long memberPriceCent,
            String memberPriceYuan,
            Long agreementPriceCent,
            String agreementPriceYuan,
            Long ladderPriceCent,
            String ladderPriceYuan) {
    }

    /** SKU 视图：skuCode 是库内真实 sku_no，供编辑页回显；旧构造器兼容契约测试。 */
    public record SkuVO(String skuId, String skuCode, String spec, int stockQty,
                        boolean onShelf, PriceVO price) {

        public SkuVO(String skuId, String spec, int stockQty, boolean onShelf, PriceVO price) {
            this(skuId, skuId, spec, stockQty, onShelf, price);
        }
    }

    /** 商品视图：description 来自 product.detail_html，供管理端编辑页回显。 */
    public record ProductVO(
            String productId,
            String name,
            Long categoryId,
            String categoryName,
            String mainImage,
            Long salesCount,
            boolean onShelf,
            List<SkuVO> skus,
            String description) {

        public ProductVO(String productId, String name, Long categoryId, String categoryName,
                         String mainImage, Long salesCount, boolean onShelf, List<SkuVO> skus) {
            this(productId, name, categoryId, categoryName, mainImage, salesCount, onShelf, skus, null);
        }
    }

    /** 分类树节点。 */
    public record CategoryVO(String categoryId, String name, String parentId, List<CategoryVO> children) {
    }
}
