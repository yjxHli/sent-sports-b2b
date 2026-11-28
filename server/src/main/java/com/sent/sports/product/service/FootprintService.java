package com.sent.sports.product.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.FavoriteDtos;

/**
 * 商品浏览足迹（2026-11-16）。
 *
 * <p>个人中心的「我的足迹」此前是写死的空态。足迹与收藏不同：它由浏览行为自动产生，
 * 用户不会主动「点一下记录」，因此在商品详情页加载时静默上报即可。</p>
 */
public interface FootprintService {

    /**
     * 记录一次浏览。按商品去重（重复浏览只刷新时间 + 次数）。
     * <p><b>不抛异常</b>：埋点失败不能把商品详情页打断，商品不存在/已下架直接跳过。</p>
     */
    void record(long customerId, long productId);

    /** 我的足迹分页（最近浏览倒序），带「是否已收藏」。 */
    PageResult<FavoriteDtos.FootprintVO> list(long customerId, PageQuery pageQuery);

    /** 清空我的足迹，返回删除条数。 */
    int clear(long customerId);

    /** 足迹总数（个人中心三联卡）。 */
    long count(long customerId);

    /**
     * 清理过期足迹（2026-11-28 用户要求：只保留一个月）。
     * <p>由定时任务每轮调用；返回实际删除行数，便于日志与「删了才打日志」。
     * 读接口也会按同一时间窗过滤，所以即使任务还没跑到，页面也不会露出过期记录。</p>
     */
    int cleanupExpired();
}
