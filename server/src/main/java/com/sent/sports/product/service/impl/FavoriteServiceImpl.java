package com.sent.sports.product.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.MoneyUtil;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.FavoriteDtos;
import com.sent.sports.product.mapper.ProductFavoriteMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.po.ProductFavoriteRow;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.product.service.FavoriteService;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

/**
 * 商品收藏实现（2026-11-16）。
 *
 * <p>四条口径：</p>
 * <ul>
 *   <li><b>只能收藏在售商品</b>：下架/已删商品收藏进来就是死链，直接拒（PARAM_INVALID / NOT_FOUND）；</li>
 *   <li><b>重复收藏幂等</b>：靠唯一索引 + {@code INSERT IGNORE}，影响行数 0 也算成功；</li>
 *   <li><b>下架不清收藏</b>：列表里保留历史收藏但标 {@code onShelf=false}，让用户自己决定删不删；</li>
 *   <li><b>未登录一律空</b>：{@code customerId<=0} 直接返回 false / 0 / 空页，不查库。</li>
 * </ul>
 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final String STATUS_ON_SHELF = "ON_SHELF";

    private final ProductFavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    public FavoriteServiceImpl(ProductFavoriteMapper favoriteMapper, ProductMapper productMapper) {
        this.favoriteMapper = favoriteMapper;
        this.productMapper = productMapper;
    }

    @Override
    public boolean add(long customerId, long productId) {
        requireIds(customerId, productId);
        ProductPO product = productMapper.selectById(productId);
        if (product == null || (product.getDeleted() != null && product.getDeleted() != 0)) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在或已删除");
        }
        if (!STATUS_ON_SHELF.equals(product.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该商品已下架，暂时无法收藏");
        }
        // 影响行数 0 = 早已收藏，幂等成功（不提示「收藏失败」，否则用户会以为连点失效了）
        favoriteMapper.insertIgnore(customerId, productId);
        return true;
    }

    @Override
    public boolean remove(long customerId, long productId) {
        requireIds(customerId, productId);
        return favoriteMapper.deleteOne(customerId, productId) > 0;
    }

    @Override
    public PageResult<FavoriteDtos.FavoriteVO> list(long customerId, PageQuery pageQuery) {
        if (customerId <= 0) {
            return PageResult.of(List.of(), 0L, pageQuery.getPage(), pageQuery.getSize());
        }
        PageQuery query = pageQuery == null ? new PageQuery() : pageQuery;
        List<ProductFavoriteRow> rows = favoriteMapper.selectMyPage(customerId, query.offset(), query.limit());
        long total = favoriteMapper.countMy(customerId);
        List<FavoriteDtos.FavoriteVO> list = rows == null ? List.of() : rows.stream().map(this::toVo).toList();
        return PageResult.of(list, total, query.getPage(), query.getSize());
    }

    @Override
    public boolean favorited(long customerId, long productId) {
        if (customerId <= 0 || productId <= 0) {
            return false;
        }
        return favoriteMapper.countByCustomerProduct(customerId, productId) > 0;
    }

    @Override
    public long count(long customerId) {
        return customerId <= 0 ? 0L : favoriteMapper.countMy(customerId);
    }

    private void requireIds(long customerId, long productId) {
        if (customerId <= 0) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (productId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品ID不合法");
        }
    }

    private FavoriteDtos.FavoriteVO toVo(ProductFavoriteRow row) {
        long cent = row.getPriceCent() == null ? 0L : row.getPriceCent();
        boolean deleted = row.getProductDeleted() != null && row.getProductDeleted() != 0;
        return new FavoriteDtos.FavoriteVO(
                String.valueOf(row.getProductId()),
                row.getProductName(),
                row.getMainImage(),
                cent,
                MoneyUtil.toYuan(cent),
                !deleted && STATUS_ON_SHELF.equals(row.getProductStatus()),
                toMillis(row.getFavoritedAt()));
    }

    private Long toMillis(java.time.LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
