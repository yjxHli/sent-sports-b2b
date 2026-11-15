package com.sent.sports.product;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.FavoriteDtos;
import com.sent.sports.product.mapper.ProductFavoriteMapper;
import com.sent.sports.product.mapper.ProductFootprintMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.po.ProductFootprintRow;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.product.service.impl.FootprintServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 浏览足迹用例（2026-11-16，TDD 红）。
 *
 * <p>足迹最容易做错的地方是「一条商品反复进详情页就刷出一堆重复记录」——淘宝的足迹是
 * <b>按商品去重、按最近浏览时间倒序</b>。所以写入一律走 {@code ON DUPLICATE KEY UPDATE}
 * （唯一键 {@code uk_footprint_customer_product}），刷新时间并累加次数。</p>
 * <p>另外匿名浏览不该落库：没有 customerId 就没有「谁的足迹」这件事。</p>
 */
class FootprintServiceTest extends BaseUnitTest {

    private static final long CUSTOMER_ID = 9002L;
    private static final long PRODUCT_ID = 9107L;

    @Mock
    private ProductFootprintMapper footprintMapper;

    @Mock
    private ProductFavoriteMapper favoriteMapper;

    @Mock
    private ProductMapper productMapper;

    private FootprintServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FootprintServiceImpl(footprintMapper, favoriteMapper, productMapper);
    }

    private ProductPO product(long id, String status) {
        ProductPO po = new ProductPO();
        po.setId(id);
        po.setTitle("天然橡胶瑜伽垫");
        po.setStatus(status);
        po.setDeleted(0);
        return po;
    }

    private ProductFootprintRow row(long productId, long priceCent) {
        ProductFootprintRow r = new ProductFootprintRow();
        r.setProductId(productId);
        r.setProductName("天然橡胶瑜伽垫");
        r.setMainImage("2026/11/bb/hash.jpg");
        r.setPriceCent(priceCent);
        r.setProductStatus("ON_SHELF");
        r.setProductDeleted(0);
        r.setViewCount(3);
        r.setLastViewedAt(LocalDateTime.of(2026, 11, 16, 9, 30));
        return r;
    }

    @Test
    @DisplayName("记录浏览：在售商品 upsert 一次；同一商品反复浏览只刷新时间")
    void recordUpserts() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, "ON_SHELF"));

        service.record(CUSTOMER_ID, PRODUCT_ID);
        service.record(CUSTOMER_ID, PRODUCT_ID);

        verify(footprintMapper, org.mockito.Mockito.times(2)).upsert(CUSTOMER_ID, PRODUCT_ID);
    }

    @Test
    @DisplayName("匿名浏览不落库：customerId=0 直接跳过，不查商品也不写表")
    void recordAnonymousSkipped() {
        service.record(0L, PRODUCT_ID);

        verify(productMapper, never()).selectById(anyLong());
        verify(footprintMapper, never()).upsert(anyLong(), anyLong());
    }

    @Test
    @DisplayName("记录浏览：商品不存在 / 已下架时不写库，也不抛错（浏览埋点不能打断页面）")
    void recordSilentlySkipsUnavailable() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);
        service.record(CUSTOMER_ID, PRODUCT_ID);

        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, "OFF_SHELF"));
        service.record(CUSTOMER_ID, PRODUCT_ID);

        verify(footprintMapper, never()).upsert(anyLong(), anyLong());
    }

    @Test
    @DisplayName("我的足迹：按最近浏览时间倒序返回，带商品快照与收藏态")
    void listMapsRowsWithFavoriteFlag() {
        when(footprintMapper.selectMyPage(CUSTOMER_ID, 0, 20))
                .thenReturn(List.of(row(9107L, 12900L), row(9110L, 5900L)));
        when(footprintMapper.countMy(CUSTOMER_ID)).thenReturn(2L);
        // 9107 已收藏、9110 未收藏
        when(favoriteMapper.selectFavoritedProductIds(CUSTOMER_ID, List.of(9107L, 9110L)))
                .thenReturn(List.of(9107L));

        PageResult<FavoriteDtos.FootprintVO> page = service.list(CUSTOMER_ID, new PageQuery());

        assertEquals(2L, page.getTotal());
        FavoriteDtos.FootprintVO first = page.getList().get(0);
        assertEquals("9107", first.productId());
        assertEquals("129.00", first.priceYuan());
        assertEquals(3, first.viewCount());
        org.junit.jupiter.api.Assertions.assertTrue(first.favorited());

        FavoriteDtos.FootprintVO second = page.getList().get(1);
        org.junit.jupiter.api.Assertions.assertFalse(second.favorited());
    }

    @Test
    @DisplayName("我的足迹：未登录返回空页，不触库")
    void listAnonymousEmpty() {
        PageResult<FavoriteDtos.FootprintVO> page = service.list(0L, new PageQuery());

        assertEquals(0L, page.getTotal());
        assertEquals(0, page.getList().size());
        verify(footprintMapper, never()).selectMyPage(anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("清空足迹：返回实际删除条数")
    void clearReturnsDeleted() {
        when(footprintMapper.deleteAllMy(CUSTOMER_ID)).thenReturn(5);

        assertEquals(5, service.clear(CUSTOMER_ID));
        assertEquals(0, service.clear(0L));
    }

    @Test
    @DisplayName("足迹计数：未登录为 0")
    void countForAnonymous() {
        assertEquals(0L, service.count(0L));
        when(footprintMapper.countMy(CUSTOMER_ID)).thenReturn(7L);
        assertEquals(7L, service.count(CUSTOMER_ID));
    }

    @Test
    @DisplayName("非法 productId：抛 PARAM_INVALID")
    void recordInvalidProductId() {
        BizException ex = assertThrows(BizException.class, () -> service.record(CUSTOMER_ID, -5L));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }
}
