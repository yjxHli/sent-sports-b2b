package com.sent.sports.product.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.FavoriteDtos;
import com.sent.sports.product.mapper.ProductFavoriteMapper;
import com.sent.sports.product.mapper.ProductFootprintMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.po.ProductFootprintRow;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.product.service.FootprintService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * 商品足迹实现（2026-11-16）。
 *
 * <p>写入口径：一人一商品一条，重复浏览 {@code ON DUPLICATE KEY UPDATE} 只刷新时间 + 次数。
 * 商品不存在 / 已下架直接跳过且不报错——足迹是埋点，不能因为「商品刚被下架」
 * 就把商品详情页打成 500。</p>
 * <p>读面口径：按最近浏览倒序，一次批量查「哪些已收藏」，避免每张卡片一次 DB 往返。</p>
 */
@Service
public class FootprintServiceImpl implements FootprintService {

    private static final String STATUS_ON_SHELF = "ON_SHELF";

    private final ProductFootprintMapper footprintMapper;
    private final ProductFavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    public FootprintServiceImpl(ProductFootprintMapper footprintMapper,
                                ProductFavoriteMapper favoriteMapper,
                                ProductMapper productMapper) {
        this.footprintMapper = footprintMapper;
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
    }

    @Override
    public void record(long customerId, long productId) {
        if (productId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品ID不合法");
        }
        // 匿名浏览不落库：没有「谁的足迹」这件事，硬记只会浪费存储
        if (customerId <= 0) {
            return;
        }
        ProductPO product = productMapper.selectById(productId);
        if (product == null || (product.getDeleted() != null && product.getDeleted() != 0)) {
            return;
        }
        if (!STATUS_ON_SHELF.equals(product.getStatus())) {
            return;
        }
        footprintMapper.upsert(customerId, productId);
    }

    @Override
    public PageResult<FavoriteDtos.FootprintVO> list(long customerId, PageQuery pageQuery) {
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        if (customerId <= 0) {
            return PageResult.of(List.of(), 0L, query.getPage(), query.getSize());
        }
        List<ProductFootprintRow> rows = footprintMapper.selectMyPage(customerId, query.offset(), query.limit());
        long total = footprintMapper.countMy(customerId);
        if (rows == null || rows.isEmpty()) {
            return PageResult.of(List.of(), total, query.getPage(), query.getSize());
        }
        List<Long> productIds = rows.stream().map(ProductFootprintRow::getProductId).toList();
        List<Long> favoritedIds = favoriteMapper.selectFavoritedProductIds(customerId, productIds);
        Set<Long> favorited = favoritedIds == null ? Set.of() : Set.copyOf(favoritedIds);
        List<FavoriteDtos.FootprintVO> list = rows.stream()
                .map(r -> toVo(r, favorited.contains(r.getProductId())))
                .toList();
        return PageResult.of(list, total, query.getPage(), query.getSize());
    }

    @Override
    public int clear(long customerId) {
        return customerId <= 0 ? 0 : footprintMapper.deleteAllMy(customerId);
    }

    @Override
    public long count(long customerId) {
        return customerId <= 0 ? 0L : footprintMapper.countMy(customerId);
    }

    private FavoriteDtos.FootprintVO toVo(ProductFootprintRow row, boolean favorited) {
        long cent = row.getPriceCent() == null ? 0L : row.getPriceCent();
        boolean deleted = row.getProductDeleted() != null && row.getProductDeleted() != 0;
        return new FavoriteDtos.FootprintVO(
                String.valueOf(row.getProductId()),
                row.getProductName(),
                row.getMainImage(),
                cent,
                MoneyUtil.toYuan(cent),
                !deleted && STATUS_ON_SHELF.equals(row.getProductStatus()),
                row.getViewCount() == null ? 1 : row.getViewCount(),
                favorited,
                toMillis(row.getLastViewedAt()));
    }

    private Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
