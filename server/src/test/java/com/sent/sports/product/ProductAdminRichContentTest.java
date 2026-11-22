package com.sent.sports.product;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.util.JsonListCodec;
import com.sent.sports.product.dto.admin.AdminProductDtos;
import com.sent.sports.product.mapper.CatalogMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.SkuMapper;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.product.mapper.po.SkuPO;
import com.sent.sports.product.service.impl.ProductAdminServiceImpl;
import com.sent.sports.stock.service.SkuStockInitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品富内容（V54）：图集 / 详情图 / 参数 / 服务承诺 / 副标题 / 款式图必须真落库。
 *
 * <p>核心防线两条：
 * <ol>
 *   <li><b>空列表要落 {@code "[]"}</b>——MyBatis-Plus {@code updateById} 跳过 null，
 *       若清空写 null，删图会静默失效；</li>
 *   <li><b>边界要拦</b>——图超 8 张、参数超 20 条、label 空白、fileId 超长都必须拒绝，
 *       而不是默默截断。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProductAdminRichContentTest {

    @Mock
    private CatalogMapper catalogMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private SkuMapper skuMapper;

    @Mock
    private SkuStockInitService skuStockInitService;

    @Mock
    private IdGen idGen;

    @Mock
    private Clock clock;

    @InjectMocks
    private ProductAdminServiceImpl service;

    /**
     * clock 只用于 create 时写 published_at，校验类用例根本走不到。
     * 用 lenient 避免 STRICT_STUBS 把「未用到」判成失败。
     */
    @BeforeEach
    void stubClock() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-11-21T12:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    // ---------- 工具 ----------

    private static List<String> files(String prefix, int count) {
        List<String> list = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> list.add("2026/11/" + prefix + i + ".png"));
        return list;
    }

    private void stubCreateProduct() {
        when(idGen.next("SPU")).thenReturn("SPU9001");
        doAnswer(inv -> {
            inv.<ProductPO>getArgument(0).setId(9001L);
            return 1;
        }).when(productMapper).insert(any(ProductPO.class));
        when(productMapper.selectSkusBySpu(anyLong())).thenReturn(List.of());
    }

    private AdminProductDtos.ProductForm form(List<String> images, List<String> detailImages,
                                              List<AdminProductDtos.ParamItem> params,
                                              List<String> serviceTags) {
        return new AdminProductDtos.ProductForm(
                "森特速干训练T恤", 11L, "纯文本详情：适合专业训练场景。", List.of(),
                "透气速干 · 训练首选", "森特/SENT", true, "2026/11/main.png",
                images, detailImages, params, serviceTags);
    }

    // ---------- create：富内容落库 ----------

    @Test
    @DisplayName("create 把副标题/品牌/定制开关/图集/详情图/参数/服务承诺全部落库")
    void createPersistsRichContent() {
        stubCreateProduct();

        service.create(form(
                List.of("2026/11/main.png", "2026/11/b.png"),
                List.of("2026/11/d1.png", "2026/11/d2.png"),
                List.of(new AdminProductDtos.ParamItem("面料", "聚酯纤维"),
                        new AdminProductDtos.ParamItem("尺码", "S-3XL")),
                List.of("15天价保", "7天无理由")));

        ArgumentCaptor<ProductPO> captor = ArgumentCaptor.forClass(ProductPO.class);
        verify(productMapper).insert(captor.capture());
        ProductPO po = captor.getValue();

        assertEquals("透气速干 · 训练首选", po.getSubtitle());
        assertEquals("森特/SENT", po.getBrand());
        assertEquals(1, po.getCanCustomize());
        assertEquals("2026/11/main.png", po.getMainImage());
        assertEquals(List.of("2026/11/main.png", "2026/11/b.png"),
                JsonListCodec.read(po.getImagesJson(), String.class));
        assertEquals(List.of("2026/11/d1.png", "2026/11/d2.png"),
                JsonListCodec.read(po.getDetailImagesJson(), String.class));
        assertEquals(List.of("15天价保", "7天无理由"),
                JsonListCodec.read(po.getServiceTagsJson(), String.class));
        assertEquals(2, JsonListCodec.read(po.getParamsJson(), AdminProductDtos.ParamItem.class).size());
        assertEquals("纯文本详情：适合专业训练场景。", po.getDetailHtml());
    }

    @Test
    @DisplayName("create 未传图集时主图沿用 mainImage，各 JSON 列落 \"[]\" 而不是 null")
    void createWithoutRichListWritesEmptyArrayNotNull() {
        stubCreateProduct();

        service.create(form(null, null, null, null));

        ArgumentCaptor<ProductPO> captor = ArgumentCaptor.forClass(ProductPO.class);
        verify(productMapper).insert(captor.capture());
        ProductPO po = captor.getValue();
        assertEquals("[]", po.getImagesJson());
        assertEquals("[]", po.getDetailImagesJson());
        assertEquals("[]", po.getParamsJson());
        assertEquals("[]", po.getServiceTagsJson());
    }

    @Test
    @DisplayName("create 只传图集不传 mainImage 时，主图回退为第一张图")
    void createFallsBackToFirstImageAsMainImage() {
        stubCreateProduct();

        AdminProductDtos.ProductForm f = new AdminProductDtos.ProductForm(
                "森特速干训练T恤", 11L, null, List.of(),
                null, null, null, null,
                List.of("2026/11/first.png", "2026/11/second.png"), null, null, null);
        service.create(f);

        ArgumentCaptor<ProductPO> captor = ArgumentCaptor.forClass(ProductPO.class);
        verify(productMapper).insert(captor.capture());
        assertEquals("2026/11/first.png", captor.getValue().getMainImage());
    }

    // ---------- update：清空语义 ----------

    @Test
    @DisplayName("update 传空图集等于删图：列必须写 \"[]\"，不能留旧值也不能写 null")
    void updateClearsImagesWithEmptyArray() {
        ProductPO existing = new ProductPO();
        existing.setId(9001L);
        existing.setStatus("ON_SHELF");
        existing.setImagesJson("[\"2026/11/old.png\"]");
        when(productMapper.selectById(9001L)).thenReturn(existing);
        when(productMapper.selectSkusBySpu(anyLong())).thenReturn(List.of());

        service.update(9001L, form(List.of(), List.of(), List.of(), List.of()));

        ArgumentCaptor<ProductPO> captor = ArgumentCaptor.forClass(ProductPO.class);
        verify(productMapper).updateById(captor.capture());
        assertEquals("[]", captor.getValue().getImagesJson(), "删图必须落 []，否则 updateById 会跳过 null 导致删不掉");
    }

    @Test
    @DisplayName("update 保留旧图时正常覆盖为新列表，顺序不变")
    void updateKeepsGivenImageOrder() {
        ProductPO existing = new ProductPO();
        existing.setId(9001L);
        existing.setStatus("ON_SHELF");
        when(productMapper.selectById(9001L)).thenReturn(existing);
        when(productMapper.selectSkusBySpu(anyLong())).thenReturn(List.of());

        service.update(9001L, form(List.of("2026/11/x.png", "2026/11/y.png"), null, null, null));

        ArgumentCaptor<ProductPO> captor = ArgumentCaptor.forClass(ProductPO.class);
        verify(productMapper).updateById(captor.capture());
        assertEquals(List.of("2026/11/x.png", "2026/11/y.png"),
                JsonListCodec.read(captor.getValue().getImagesJson(), String.class));
    }

    // ---------- 边界校验 ----------

    @Test
    @DisplayName("图集超过 8 张被拒绝，且不是静默截断")
    void rejectsTooManyImages() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(files("img", 9), null, null, null)));
        assertTrue(ex.resolvedMessage().contains("最多"), "原因里要写明上限：" + ex.resolvedMessage());
    }

    @Test
    @DisplayName("详情长图超过 20 张被拒绝")
    void rejectsTooManyDetailImages() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(null, files("d", 21), null, null)));
        assertTrue(ex.resolvedMessage().contains("最多"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("参数超过 20 条被拒绝")
    void rejectsTooManyParams() {
        List<AdminProductDtos.ParamItem> params = new ArrayList<>();
        IntStream.range(0, 21).forEach(i -> params.add(new AdminProductDtos.ParamItem("P" + i, "V" + i)));
        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(null, null, params, null)));
        assertTrue(ex.resolvedMessage().contains("最多"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("参数名为空白被拒绝（前端允许空行，但落库前必须拦）")
    void rejectsBlankParamLabel() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(null, null,
                        List.of(new AdminProductDtos.ParamItem("   ", "22W")), null)));
        assertTrue(ex.resolvedMessage().contains("参数名"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("服务承诺超过 6 个被拒绝")
    void rejectsTooManyServiceTags() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(null, null, null,
                        List.of("a", "b", "c", "d", "e", "f", "g"))));
        assertTrue(ex.resolvedMessage().contains("最多"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("图片 fileId 超过 512 字符被拒绝")
    void rejectsOverlongFileId() {
        String longId = "2026/11/" + "x".repeat(520) + ".png";
        BizException ex = assertThrows(BizException.class,
                () -> service.create(form(List.of(longId), null, null, null)));
        assertTrue(ex.resolvedMessage().contains("过长"), ex.resolvedMessage());
    }

    @Test
    @DisplayName("图片列表里的空字符串被过滤，不会在用户端渲染成破图")
    void filtersBlankFileIds() {
        stubCreateProduct();

        service.create(form(List.of("2026/11/main.png", "  ", ""), null, null, null));

        ArgumentCaptor<ProductPO> captor = ArgumentCaptor.forClass(ProductPO.class);
        verify(productMapper).insert(captor.capture());
        assertEquals(List.of("2026/11/main.png"),
                JsonListCodec.read(captor.getValue().getImagesJson(), String.class));
    }

    // ---------- SKU 款式图 ----------

    @Test
    @DisplayName("SKU 款式图 imageFileId 随规格一起落库")
    void persistsSkuImage() {
        when(idGen.next("SPU")).thenReturn("SPU9001");
        doAnswer(inv -> {
            inv.<ProductPO>getArgument(0).setId(9001L);
            return 1;
        }).when(productMapper).insert(any(ProductPO.class));
        when(productMapper.selectSkusBySpu(anyLong())).thenReturn(List.of());
        when(skuMapper.selectBySkuNo(anyString())).thenReturn(null);
        doAnswer(inv -> {
            inv.<SkuPO>getArgument(0).setId(9201L);
            return 1;
        }).when(skuMapper).insert(any(SkuPO.class));

        List<AdminProductDtos.SkuForm> skus = List.of(new AdminProductDtos.SkuForm(
                null, null, "SKU-A", "黑色 / L", 11L, 3900L, 5, "2026/11/sku-black.png"));
        service.create(new AdminProductDtos.ProductForm(
                "森特速干训练T恤", 11L, null, skus,
                null, null, null, null, null, null, null, null));

        ArgumentCaptor<SkuPO> captor = ArgumentCaptor.forClass(SkuPO.class);
        verify(skuMapper).insert(captor.capture());
        assertEquals("2026/11/sku-black.png", captor.getValue().getImageFileId());
        assertEquals("{\"spec\":\"黑色 / L\"}", captor.getValue().getSpecJson());
        verify(skuStockInitService).syncPhysicalQty(9201L, 5);
    }

    @Test
    @DisplayName("旧签名 ProductForm（4 参）仍可构造，富内容全为空且不报错")
    void legacyProductFormStillWorks() {
        stubCreateProduct();

        service.create(new AdminProductDtos.ProductForm("老接口商品", 11L, "描述", List.of()));

        ArgumentCaptor<ProductPO> captor = ArgumentCaptor.forClass(ProductPO.class);
        verify(productMapper).insert(captor.capture());
        assertEquals("[]", captor.getValue().getImagesJson());
    }
}
