package com.sent.sports.product.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.FavoriteDtos;

/**
 * 商品收藏（2026-11-16，跨模块可用的读侧门面也可用）。
 *
 * <p>收藏是最轻的一步用户行为：详情页一个心形按钮，收藏夹一个列表。
 * 口径收敛在实现里，控制器只负责取当前登录人。</p>
 */
public interface FavoriteService {

    /**
     * 收藏商品。幂等：已收藏时仍返回 {@code true}，不报错。
     *
     * @return 恒 {@code true}（保留返回值便于调用方未来区分「新增 / 已存在」）
     */
    boolean add(long customerId, long productId);

    /**
     * 取消收藏。幂等：本来没收藏返回 {@code false}。
     */
    boolean remove(long customerId, long productId);

    /** 我的收藏分页（收藏时间倒序）。 */
    PageResult<FavoriteDtos.FavoriteVO> list(long customerId, PageQuery pageQuery);

    /** 某商品是否已被收藏（详情页心形按钮的初始态）。 */
    boolean favorited(long customerId, long productId);

    /** 收藏总数（个人中心三联卡）。 */
    long count(long customerId);
}
