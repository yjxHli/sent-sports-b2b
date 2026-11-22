package com.sent.sports.product.repository;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.product.domain.pricing.PriceContext;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.po.SkuPO;
import com.sent.sports.product.repository.impl.ProductPriceRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 取价上下文仓储（V54）：秒杀价必须在 {@code loadContext} 里装进 {@code PriceContext}。
 *
 * <p>这是秒杀"真生效"的关键一环——只要这里漏掉，购物车预览与下单金额就会回落到零售价，
 * 而详情页仍显示秒杀价，形成价格欺诈。因此本测试盯三件事：</p>
 * <ol>
 *   <li>有生效活动 → 装进 ctx（价格 + ruleId）；</li>
 *   <li>无活动 / 脏数据 → 不装（价格为空，策略自然不命中）；</li>
 *   <li>查询时间来自 {@code Clock}，不是 SQL {@code NOW()}（否则单测无法锁定时间）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProductPriceRepositoryFlashTest {

    private static final Instant FIXED = Instant.parse("2026-11-21T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private ProductMapper productMapper;

    private ProductPriceRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new ProductPriceRepositoryImpl(productMapper, Clock.fixed(FIXED, ZONE));
        lenient().when(productMapper.selectLadderPrices(anyLong())).thenReturn(List.of());
    }

    private static SkuPO sku(String status, long retailCent) {
        SkuPO po = new SkuPO();
        po.setId(9201L);
        po.setStatus(status);
        po.setRetailPriceCent(retailCent);
        po.setFloorPriceCent(1000L);
        po.setCostPriceCent(800L);
        return po;
    }

    @Test
    @DisplayName("有生效秒杀：价格与 ruleId 装进上下文，且查询时间取自 Clock")
    void injectsActiveFlashSale() {
        when(productMapper.selectSkuById(9201L)).thenReturn(sku("ON", 10000L));
        ProductMapper.FlashSaleRow flash = new ProductMapper.FlashSaleRow();
        flash.setPriceCent(2500L);
        flash.setActivityId(12L);
        flash.setEndAt(LocalDateTime.now().plusHours(2));
        when(productMapper.selectActiveFlashSale(anyLong(), any(LocalDateTime.class))).thenReturn(flash);

        PriceContext ctx = repository.loadContext(9201L);

        assertEquals(2500L, ctx.getFlashSalePriceCent());
        assertEquals("FLASH#12", ctx.getFlashSaleRuleId());
        assertEquals(10000L, ctx.getRetailPriceCent(), "零售价仍要装配，秒杀不生效时兜底");

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(productMapper).selectActiveFlashSale(anyLong(), nowCaptor.capture());
        assertEquals(LocalDateTime.ofInstant(FIXED, ZONE), nowCaptor.getValue(),
                "时间必须来自 Clock，不能是 SQL NOW()");
    }

    @Test
    @DisplayName("无生效活动：秒杀字段为空，定价回落到阶梯/会员/零售")
    void withoutActiveFlashSale() {
        when(productMapper.selectSkuById(9201L)).thenReturn(sku("ON", 10000L));
        when(productMapper.selectActiveFlashSale(anyLong(), any(LocalDateTime.class))).thenReturn(null);

        PriceContext ctx = repository.loadContext(9201L);

        assertNull(ctx.getFlashSalePriceCent());
        assertNull(ctx.getFlashSaleRuleId());
    }

    @Test
    @DisplayName("活动行存在但价格为空（脏数据）：不装配，避免把 0 当成交价")
    void ignoresFlashRowWithNullPrice() {
        when(productMapper.selectSkuById(9201L)).thenReturn(sku("ON", 10000L));
        ProductMapper.FlashSaleRow flash = new ProductMapper.FlashSaleRow();
        flash.setActivityId(12L);
        when(productMapper.selectActiveFlashSale(anyLong(), any(LocalDateTime.class))).thenReturn(flash);

        PriceContext ctx = repository.loadContext(9201L);

        assertNull(ctx.getFlashSalePriceCent());
    }

    @Test
    @DisplayName("已下架 SKU 不可定价：即使有秒杀活动也拦在取价之前")
    void offShelfSkuRejected() {
        when(productMapper.selectSkuById(9201L)).thenReturn(sku("OFF", 10000L));

        BizException ex = assertThrows(BizException.class, () -> repository.loadContext(9201L));

        assertEquals(ErrorCode.PRODUCT_OFF_SHELF, ex.getErrorCode());
    }
}
