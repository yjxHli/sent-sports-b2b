package com.sent.sports.product.dto;

/**
 * 收藏 / 足迹 Web 层 DTO（2026-11-16）。
 *
 * <p>金额同时给 {@code *_cent} 与 {@code *_Yuan}，与目录接口 {@link CatalogDtos} 保持同一口径，
 * 前端不必各自除 100。</p>
 */
public final class FavoriteDtos {

    private FavoriteDtos() {
    }

    /**
     * 收藏（收藏夹列表行）。
     *
     * @param productId  SPU 主键（字符串，与 {@code /product/:productId} 路由参数一致）
     * @param productName 商品标题（下单时的商品名可能与现在不同，这里展示「当前」标题）
     * @param mainImage  主图相对路径，前端拼静态资源前缀
     * @param priceCent  最低在售 SKU 的零售价（分），无在售 SKU 时为 0
     * @param priceYuan  同上，元字符串
     * @param onShelf    是否仍在售：下架商品保留在收藏夹里但灰显，不直接删掉用户的收藏
     * @param favoritedAt 收藏时间（毫秒）
     */
    public record FavoriteVO(String productId, String productName, String mainImage,
                             long priceCent, String priceYuan, boolean onShelf, Long favoritedAt) {
    }

    /**
     * 浏览足迹列表行：在收藏字段基础上补「浏览次数 / 最近浏览时间 / 已收藏」。
     * 前端据此渲染淘宝式的「足迹卡片 + 心形收藏态」。
     */
    public record FootprintVO(String productId, String productName, String mainImage,
                              long priceCent, String priceYuan, boolean onShelf,
                              int viewCount, boolean favorited, Long lastViewedAt) {
    }

    /** 收藏状态（详情页心形按钮用）：{favorited, favoriteCount}。 */
    public record FavoriteStatusVO(boolean favorited, long favoriteCount) {
    }

    /** 收藏 / 足迹计数（个人中心三联卡用）。 */
    public record MarkCountVO(long favoriteCount, long footprintCount) {
    }
}
