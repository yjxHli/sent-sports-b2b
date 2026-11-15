package com.sent.sports.product;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.FavoriteDtos;
import com.sent.sports.product.mapper.ProductFavoriteMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.po.ProductFavoriteRow;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.product.service.impl.FavoriteServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品收藏用例（2026-11-16，TDD 红）。
 *
 * <p>收藏是最容易被刷的接口（详情页一个心形按钮），四条口径必须守住：</p>
 * <ul>
 *   <li><b>只能收在售商品</b>：下架/已删商品不允许收藏，否则收藏夹里全是点不开的死链；</li>
 *   <li><b>重复收藏幂等</b>：连点两下不能报错，也不能产生两条记录（靠唯一索引，不用锁）；</li>
 *   <li><b>取消收藏幂等</b>：未收藏时点取消也不报错，返回 false 让前端只提示「已取消」；</li>
 *   <li><b>列表带商品快照</b>：标题 / 主图 / 最低售价 / 收藏时间一次给全，前端不必再逐条查商品。</li>
 * </ul>
 */
class FavoriteServiceTest extends BaseUnitTest {

    private static final long CUSTOMER_ID = 9002L;
    private static final long PRODUCT_ID = 9101L;

    @Mock
    private ProductFavoriteMapper favoriteMapper;

    @Mock
    private ProductMapper productMapper;

    private FavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FavoriteServiceImpl(favoriteMapper, productMapper);
    }

    private ProductPO product(long id, String status, boolean deleted) {
        ProductPO po = new ProductPO();
        po.setId(id);
        po.setTitle("斯伯丁 5号 训练篮球");
        po.setMainImage("2026/11/aa/hash.jpg");
        po.setStatus(status);
        po.setDeleted(deleted ? 1 : 0);
        return po;
    }

    private ProductFavoriteRow row(long productId) {
        ProductFavoriteRow r = new ProductFavoriteRow();
        r.setProductId(productId);
        r.setProductName("斯伯丁 5号 训练篮球");
        r.setMainImage("2026/11/aa/hash.jpg");
        r.setPriceCent(19900L);
        r.setProductStatus("ON_SHELF");
        r.setProductDeleted(0);
        r.setFavoritedAt(LocalDateTime.of(2026, 11, 16, 10, 0));
        return r;
    }

    @Test
    @DisplayName("收藏在售商品：写入后返回 true，重复收藏靠唯一索引幂等")
    void addOnShelfProduct() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, "ON_SHELF", false));
        when(favoriteMapper.insertIgnore(CUSTOMER_ID, PRODUCT_ID)).thenReturn(1);

        assertTrue(service.add(CUSTOMER_ID, PRODUCT_ID));
        verify(favoriteMapper).insertIgnore(CUSTOMER_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("重复收藏不报错：影响行数 0 视为「早已收藏」，仍返回 true")
    void addTwiceIsIdempotent() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, "ON_SHELF", false));
        when(favoriteMapper.insertIgnore(CUSTOMER_ID, PRODUCT_ID)).thenReturn(0);

        assertTrue(service.add(CUSTOMER_ID, PRODUCT_ID));
    }

    @Test
    @DisplayName("下架商品不允许收藏：抛 PARAM_INVALID，且不写库")
    void addOffShelfRejected() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, "OFF_SHELF", false));

        BizException ex = assertThrows(BizException.class, () -> service.add(CUSTOMER_ID, PRODUCT_ID));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(favoriteMapper, never()).insertIgnore(anyLong(), anyLong());
    }

    @Test
    @DisplayName("商品不存在或已删除：抛 NOT_FOUND，且不写库")
    void addMissingProductRejected() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.add(CUSTOMER_ID, PRODUCT_ID));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());

        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, "ON_SHELF", true));
        BizException deleted = assertThrows(BizException.class, () -> service.add(CUSTOMER_ID, PRODUCT_ID));
        assertEquals(ErrorCode.NOT_FOUND, deleted.getErrorCode());
        verify(favoriteMapper, never()).insertIgnore(anyLong(), anyLong());
    }

    @Test
    @DisplayName("非法入参（customerId / productId 非正）：抛 PARAM_INVALID，不触库")
    void addInvalidArgs() {
        assertThrows(BizException.class, () -> service.add(0L, PRODUCT_ID));
        assertThrows(BizException.class, () -> service.add(CUSTOMER_ID, -1L));
        verify(favoriteMapper, never()).insertIgnore(anyLong(), anyLong());
        verify(productMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("取消收藏：删掉返回 true，本来就没收藏返回 false（都不报错）")
    void removeIsIdempotent() {
        when(favoriteMapper.deleteOne(CUSTOMER_ID, PRODUCT_ID)).thenReturn(1, 0);

        assertTrue(service.remove(CUSTOMER_ID, PRODUCT_ID));
        assertFalse(service.remove(CUSTOMER_ID, PRODUCT_ID));
    }

    @Test
    @DisplayName("我的收藏列表：字段映射 + 元字符串 + 下架商品标记 isOnShelf=false")
    void listMapsRows() {
        when(favoriteMapper.selectMyPage(CUSTOMER_ID, 0, 20))
                .thenReturn(List.of(row(9101L), row(9102L)));
        when(favoriteMapper.countMy(CUSTOMER_ID)).thenReturn(2L);

        PageQuery query = new PageQuery();
        PageResult<FavoriteDtos.FavoriteVO> page = service.list(CUSTOMER_ID, query);

        assertEquals(2L, page.getTotal());
        FavoriteDtos.FavoriteVO first = page.getList().get(0);
        assertEquals("9101", first.productId());
        assertEquals("斯伯丁 5号 训练篮球", first.productName());
        assertEquals(19900L, first.priceCent());
        assertEquals("199.00", first.priceYuan());
        assertTrue(first.onShelf());
    }

    @Test
    @DisplayName("收藏状态与计数：未登录（customerId=0）返回 false / 0，不触库")
    void statusAndCountForAnonymous() {
        assertFalse(service.favorited(0L, PRODUCT_ID));
        assertEquals(0L, service.count(0L));
        verify(favoriteMapper, never()).countMy(anyLong());
    }
}
