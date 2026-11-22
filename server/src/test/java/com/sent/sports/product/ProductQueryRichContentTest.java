package com.sent.sports.product;

import com.sent.sports.product.dto.CatalogDtos;
import com.sent.sports.product.mapper.CatalogMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.service.impl.ProductQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 商品详情读侧（V54）：富内容必须从 JSON 列解析出来，且**脏数据不能把详情页打挂**。
 *
 * <p>用户端详情页会直接渲染这些字段，因此两条底线：
 * <ol>
 *   <li>解析失败降级为空列表（页面少一个区块），绝不 500；</li>
 *   <li>主图为空时回退到图集第一张，避免详情页出现空主图。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProductQueryRichContentTest {

    @Mock
    private CatalogMapper catalogMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private Clock clock;

    @InjectMocks
    private ProductQueryServiceImpl service;

    @BeforeEach
    void stubClock() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-11-21T12:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Shanghai"));
    }

    private static CatalogMapper.ProductRow row() {
        CatalogMapper.ProductRow row = new CatalogMapper.ProductRow();
        row.setId(9001L);
        row.setTitle("森特速干训练T恤");
        row.setStatus("ON_SHELF");
        row.setSalesCount(1200L);
        return row;
    }

    @Test
    @DisplayName("detail 解析图集/详情图/参数/服务承诺/副标题/品牌")
    void detailParsesRichContent() {
        CatalogMapper.ProductRow row = row();
        row.setSubtitle("透气速干 · 训练首选");
        row.setBrand("森特/SENT");
        row.setMainImage("2026/11/main.png");
        row.setImagesJson("[\"2026/11/a.png\",\"2026/11/b.png\"]");
        row.setDetailImagesJson("[\"2026/11/d1.png\"]");
        row.setParamsJson("[{\"label\":\"面料\",\"value\":\"聚酯纤维\"},{\"label\":\"尺码\",\"value\":\"S-3XL\"}]");
        row.setServiceTagsJson("[\"15天价保\",\"7天无理由\"]");
        row.setDetailHtml("纯文本详情");
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of());

        CatalogDtos.ProductVO vo = service.detail("9001");

        assertEquals("透气速干 · 训练首选", vo.subtitle());
        assertEquals("森特/SENT", vo.brand());
        assertEquals(List.of("2026/11/a.png", "2026/11/b.png"), vo.images());
        assertEquals(List.of("2026/11/d1.png"), vo.detailImages());
        assertEquals(2, vo.params().size());
        assertEquals("面料", vo.params().get(0).label());
        assertEquals("聚酯纤维", vo.params().get(0).value());
        assertEquals(List.of("15天价保", "7天无理由"), vo.serviceTags());
        assertEquals("纯文本详情", vo.description());
    }

    @Test
    @DisplayName("脏 JSON 不抛异常，降级为空列表（详情页少个区块但能打开）")
    void detailToleratesBrokenJson() {
        CatalogMapper.ProductRow row = row();
        row.setImagesJson("{bad json");
        row.setDetailImagesJson("not-an-array");
        row.setParamsJson("{\"a\":1}");
        row.setServiceTagsJson("[");
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of());

        CatalogDtos.ProductVO vo = service.detail("9001");

        assertTrue(vo.images().isEmpty());
        assertTrue(vo.detailImages().isEmpty());
        assertTrue(vo.params().isEmpty());
        assertTrue(vo.serviceTags().isEmpty());
    }

    @Test
    @DisplayName("主图为空时回退到图集第一张")
    void detailFallsBackToFirstImage() {
        CatalogMapper.ProductRow row = row();
        row.setMainImage(null);
        row.setImagesJson("[\"2026/11/first.png\",\"2026/11/second.png\"]");
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of());

        CatalogDtos.ProductVO vo = service.detail("9001");

        assertEquals("2026/11/first.png", vo.mainImage());
    }

    @Test
    @DisplayName("主图非空时以 main_image 为准（不被图集覆盖）")
    void detailKeepsMainImageWhenPresent() {
        CatalogMapper.ProductRow row = row();
        row.setMainImage("2026/11/main.png");
        row.setImagesJson("[\"2026/11/other.png\"]");
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of());

        assertEquals("2026/11/main.png", service.detail("9001").mainImage());
    }

    @Test
    @DisplayName("SKU 款式图随详情返回，供选中规格后联动主图")
    void detailExposesSkuImage() {
        CatalogMapper.ProductRow row = row();
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);

        CatalogMapper.SkuRow sku = new CatalogMapper.SkuRow();
        sku.setId(9201L);
        sku.setSkuNo("SKU-A");
        sku.setSpecJson("{\"spec\":\"黑色 / L\"}");
        sku.setImageFileId("2026/11/sku-black.png");
        sku.setRetailPriceCent(3900L);
        sku.setStatus("ON");
        sku.setAvailableQty(10);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of(sku));

        CatalogDtos.ProductVO vo = service.detail("9001");

        assertEquals(1, vo.skus().size());
        assertEquals("黑色 / L", vo.skus().get(0).spec());
        assertEquals("2026/11/sku-black.png", vo.skus().get(0).imageFileId());
        assertEquals(3900L, vo.skus().get(0).price().retailPriceCent());
    }

    @Test
    @DisplayName("无富内容的老商品：字段为空列表，主图沿用 main_image")
    void detailWithoutRichContentStaysCompatible() {
        CatalogMapper.ProductRow row = row();
        row.setMainImage("2026/11/legacy.png");
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of());

        CatalogDtos.ProductVO vo = service.detail("9001");

        assertEquals("2026/11/legacy.png", vo.mainImage());
        assertTrue(vo.images().isEmpty());
        assertTrue(vo.params().isEmpty());
        assertNull(vo.subtitle());
    }

    @Test
    @DisplayName("有生效秒杀：详情带出秒杀快照（秒杀价 + 划线原价 + 活动时间）")
    void detailExposesFlashSaleSnapshot() {
        CatalogMapper.ProductRow row = row();
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of());
        when(productMapper.selectActiveFlashSaleSkus(anyLong(), any(LocalDateTime.class)))
                .thenReturn(List.of(flashRow(9201L, 1920L, 3500L, 12L)));

        CatalogDtos.ProductVO vo = service.detail("9001");

        assertEquals("12", vo.flashSale().activityId());
        assertEquals("限时秒杀", vo.flashSale().title());
        assertEquals(1920L, vo.flashSale().flashPriceCent());
        assertEquals("19.20", vo.flashSale().flashPriceYuan());
        assertEquals(3500L, vo.flashSale().originPriceCent());
        assertEquals("2026-11-21T20:00", vo.flashSale().startAt());
    }

    @Test
    @DisplayName("秒杀只作用于活动内规格：未参加活动的规格 flashPriceCent 必须为 null")
    void detailFlashSaleIsBoundToSku() {
        CatalogMapper.ProductRow row = row();
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);
        when(catalogMapper.selectSkusWithStock(9001L))
                .thenReturn(List.of(skuRow(9201L, "黑色 / L", 3500L), skuRow(9202L, "白色 / L", 3500L)));
        // 活动只挂了 9201
        when(productMapper.selectActiveFlashSaleSkus(anyLong(), any(LocalDateTime.class)))
                .thenReturn(List.of(flashRow(9201L, 1920L, 3500L, 12L)));

        CatalogDtos.ProductVO vo = service.detail("9001");

        CatalogDtos.SkuVO hit = vo.skus().stream()
                .filter(s -> "9201".equals(s.skuId())).findFirst().orElseThrow();
        CatalogDtos.SkuVO miss = vo.skus().stream()
                .filter(s -> "9202".equals(s.skuId())).findFirst().orElseThrow();
        assertEquals(1920L, hit.flashPriceCent());
        assertEquals("19.20", hit.flashPriceYuan());
        // 未参加活动的规格绝不能带秒杀价，否则详情价与结算价会打架
        assertNull(miss.flashPriceCent());
        assertNull(miss.flashPriceYuan());
    }

    @Test
    @DisplayName("横幅取活动内最低秒杀价，原价取该行零售价（不是全商品最低零售价）")
    void detailBannerUsesLowestItem() {
        CatalogMapper.ProductRow row = row();
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of());
        ProductMapper.FlashSaleSkuRow lower = flashRow(9201L, 1920L, 3500L, 12L);
        ProductMapper.FlashSaleSkuRow higher = flashRow(9202L, 2680L, 4200L, 12L);
        when(productMapper.selectActiveFlashSaleSkus(anyLong(), any(LocalDateTime.class)))
                .thenReturn(List.of(lower, higher));

        CatalogDtos.ProductVO vo = service.detail("9001");

        assertEquals(1920L, vo.flashSale().flashPriceCent());
        assertEquals(3500L, vo.flashSale().originPriceCent());
    }

    @Test
    @DisplayName("无生效秒杀：flashSale 为 null，前端据此整块不渲染")
    void detailWithoutFlashSaleReturnsNull() {
        CatalogMapper.ProductRow row = row();
        when(catalogMapper.selectProductById(9001L)).thenReturn(row);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of());
        when(productMapper.selectActiveFlashSaleSkus(anyLong(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertNull(service.detail("9001").flashSale());
    }

    /** 秒杀命中行构造器：活动 12「限时秒杀」，20:00 开始、次日 00:00 结束。 */
    private static ProductMapper.FlashSaleSkuRow flashRow(long skuId, long priceCent, long retailCent,
                                                         long activityId) {
        ProductMapper.FlashSaleSkuRow row = new ProductMapper.FlashSaleSkuRow();
        row.setSkuId(skuId);
        row.setPriceCent(priceCent);
        row.setRetailPriceCent(retailCent);
        row.setActivityId(activityId);
        row.setTitle("限时秒杀");
        row.setStartAt(LocalDateTime.of(2026, 11, 21, 20, 0));
        row.setEndAt(LocalDateTime.of(2026, 11, 22, 0, 0));
        return row;
    }

    private static CatalogMapper.SkuRow skuRow(long skuId, String spec, long retailCent) {
        CatalogMapper.SkuRow sku = new CatalogMapper.SkuRow();
        sku.setId(skuId);
        sku.setSkuNo("SKU-" + skuId);
        sku.setSpecJson("{\"spec\":\"" + spec + "\"}");
        sku.setRetailPriceCent(retailCent);
        sku.setStatus("ON");
        sku.setAvailableQty(10);
        return sku;
    }

    @Test
    @DisplayName("can_customize=1 回显为 true，缺失/null 一律 false（管理端编辑页回显依赖此口径）")
    void detailMapsCanCustomize() {
        CatalogMapper.ProductRow customizable = row();
        customizable.setCanCustomize(1);
        when(catalogMapper.selectProductById(9001L)).thenReturn(customizable);
        when(catalogMapper.selectSkusWithStock(9001L)).thenReturn(List.of());

        assertTrue(service.detail("9001").canCustomize());

        CatalogMapper.ProductRow plain = row();
        plain.setId(9002L);
        when(catalogMapper.selectProductById(9002L)).thenReturn(plain);
        when(catalogMapper.selectSkusWithStock(9002L)).thenReturn(List.of());

        assertFalse(service.detail("9002").canCustomize());
    }
}
