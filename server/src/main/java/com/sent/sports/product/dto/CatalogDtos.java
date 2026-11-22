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

    /**
     * SKU 视图：skuCode 是库内真实 sku_no，供编辑页回显；旧构造器兼容契约测试。
     * <p>V54 追加 {@code imageFileId}（款式图），供详情页选中规格后联动主图；
     * {@code flashPriceCent/flashPriceYuan} 为该规格自身的秒杀价（无活动或未参加活动时为 null）——
     * 秒杀按 SKU 归属，绝不把活动内其他规格的秒杀价挂到这个规格上。</p>
     */
    public record SkuVO(String skuId, String skuCode, String spec, int stockQty,
                        boolean onShelf, PriceVO price, String imageFileId,
                        Long flashPriceCent, String flashPriceYuan) {

        public SkuVO(String skuId, String skuCode, String spec, int stockQty,
                     boolean onShelf, PriceVO price, String imageFileId) {
            this(skuId, skuCode, spec, stockQty, onShelf, price, imageFileId, null, null);
        }

        public SkuVO(String skuId, String skuCode, String spec, int stockQty,
                     boolean onShelf, PriceVO price) {
            this(skuId, skuCode, spec, stockQty, onShelf, price, null, null, null);
        }

        public SkuVO(String skuId, String spec, int stockQty, boolean onShelf, PriceVO price) {
            this(skuId, skuId, spec, stockQty, onShelf, price, null, null, null);
        }
    }

    /** 搜索联想命中项：点击后跳 /products?categoryId=X&keyword=标题。 */
    public record SuggestVO(String productId, String name, Long categoryId, String categoryName) {
    }

    /**
     * 商品参数键值对（V54）：与 {@code product.params_json} 的元素结构一致。
     * <p>与管理端 {@code AdminProductDtos.ParamItem} 同结构但各自独立，避免查询侧反向依赖 admin DTO。</p>
     */
    public record ParamVO(String label, String value) {
    }

    /**
     * 秒杀快照（V54）：详情页展示用，与取价同源（同一个 SQL 口径 + 同一个 Clock）。
     * <p>无生效活动时为 {@code null}，前端因此**不渲染任何秒杀 UI**，
     * 而不是展示一个"原价秒杀"的假横幅。</p>
     */
    public record FlashSaleVO(
            String activityId,
            String title,
            long flashPriceCent,
            String flashPriceYuan,
            long originPriceCent,
            String originPriceYuan,
            String startAt,
            String endAt) {
    }

    /**
     * 商品视图：description 来自 product.detail_html，供管理端编辑页回显。
     * <p>V54 追加富内容：subtitle / brand / images（图集）/ detailImages（详情长图）/
     * params（参数）/ serviceTags（服务承诺）；{@code flashSale} 为当前生效秒杀（无则 null）。
     * {@code canCustomize} 供管理端编辑页回显「是否支持 UV 定制」。
     * 全部可为空列表——前端据此渲染真实空态，不拿假数据填充。</p>
     */
    public record ProductVO(
            String productId,
            String name,
            Long categoryId,
            String categoryName,
            String mainImage,
            Long salesCount,
            boolean onShelf,
            List<SkuVO> skus,
            String description,
            String subtitle,
            String brand,
            List<String> images,
            List<String> detailImages,
            List<ParamVO> params,
            List<String> serviceTags,
            FlashSaleVO flashSale,
            boolean canCustomize) {

        /** 兼容 V54 之前带 description 的调用。 */
        public ProductVO(String productId, String name, Long categoryId, String categoryName,
                         String mainImage, Long salesCount, boolean onShelf, List<SkuVO> skus,
                         String description) {
            this(productId, name, categoryId, categoryName, mainImage, salesCount, onShelf, skus,
                    description, null, null, List.of(), List.of(), List.of(), List.of(), null, false);
        }

        public ProductVO(String productId, String name, Long categoryId, String categoryName,
                         String mainImage, Long salesCount, boolean onShelf, List<SkuVO> skus) {
            this(productId, name, categoryId, categoryName, mainImage, salesCount, onShelf, skus,
                    null, null, null, List.of(), List.of(), List.of(), List.of(), null, false);
        }
    }

    /** 分类树节点。 */
    public record CategoryVO(String categoryId, String name, String parentId, List<CategoryVO> children) {
    }
}
