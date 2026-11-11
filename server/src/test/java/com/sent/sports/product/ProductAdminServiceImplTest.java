package com.sent.sports.product;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.dto.TierDto;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.dto.admin.AdminProductDtos;
import com.sent.sports.product.mapper.CatalogMapper;
import com.sent.sports.product.mapper.ProductMapper;
import com.sent.sports.product.mapper.SkuMapper;
import com.sent.sports.product.mapper.po.LadderPricePO;
import com.sent.sports.product.mapper.po.ProductPO;
import com.sent.sports.product.mapper.po.SkuPO;
import com.sent.sports.product.service.impl.ProductAdminServiceImpl;
import com.sent.sports.stock.service.SkuStockInitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 商品管理写侧：阶梯价 / 一客一价 / 商品 SKU 矩阵必须真落库。 */
@ExtendWith(MockitoExtension.class)
class ProductAdminServiceImplTest {

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

    private static SkuPO sku(long retailCent) {
        SkuPO po = new SkuPO();
        po.setRetailPriceCent(retailCent);
        return po;
    }

    private static SkuPO sku(long id, String skuNo, long retailCent) {
        SkuPO po = sku(retailCent);
        po.setId(id);
        po.setSkuNo(skuNo);
        po.setStatus("ON");
        return po;
    }

    private static ProductPO product() {
        ProductPO po = new ProductPO();
        po.setId(9103L);
        po.setStatus("ON_SHELF");
        po.setDeleted(0);
        return po;
    }

    @Test
    @DisplayName("configLadder 先删后插，并把折扣率换算成绝对价（分）")
    void configLadderReplacesAllTiersAndConvertsRateToPriceCent() {
        when(productMapper.selectSkuById(9101L)).thenReturn(sku(10000L));
        when(productMapper.deleteLadderPrices(9101L)).thenReturn(2);

        service.configLadder(9101L, List.of(new TierDto(1, 9, 88), new TierDto(10, -1, 80)));

        verify(productMapper).deleteLadderPrices(9101L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LadderPricePO>> captor = ArgumentCaptor.forClass(List.class);
        verify(productMapper).insertLadderPrices(captor.capture());
        List<LadderPricePO> rows = captor.getValue();
        assertEquals(2, rows.size());
        assertEquals(9101L, rows.get(0).getSkuId());
        assertEquals(1, rows.get(0).getMinQty());
        assertEquals(9, rows.get(0).getMaxQty());
        assertEquals(8800L, rows.get(0).getPriceCent());
        assertEquals(10, rows.get(1).getMinQty());
        assertNull(rows.get(1).getMaxQty(), "maxQty < 0 表示无上限，落库为 NULL");
        assertEquals(8000L, rows.get(1).getPriceCent());
    }

    @Test
    @DisplayName("configLadder 传空阶梯等于清空该 SKU 的全部折扣")
    void configLadderWithEmptyTiersClearsDiscounts() {
        when(productMapper.deleteLadderPrices(9101L)).thenReturn(3);

        service.configLadder(9101L, List.of());

        verify(productMapper).deleteLadderPrices(9101L);
        verify(productMapper, never()).insertLadderPrices(any());
        verify(productMapper, never()).selectSkuById(anyLong());
    }

    @Test
    @DisplayName("listLadderPrices 把库里的绝对价反算回折扣率，供编辑页回显")
    void listLadderPricesConvertsPriceCentBackToRate() {
        LadderPricePO first = new LadderPricePO();
        first.setSkuId(9101L);
        first.setMinQty(1);
        first.setMaxQty(9);
        first.setPriceCent(8800L);
        LadderPricePO second = new LadderPricePO();
        second.setSkuId(9101L);
        second.setMinQty(10);
        second.setMaxQty(null);
        second.setPriceCent(8000L);
        when(productMapper.selectSkuById(9101L)).thenReturn(sku(10000L));
        when(productMapper.selectLadderPrices(9101L)).thenReturn(List.of(first, second));

        List<TierDto> tiers = service.listLadderPrices(9101L);

        assertEquals(2, tiers.size());
        assertEquals(new TierDto(1, 9, 88), tiers.get(0));
        assertEquals(new TierDto(10, -1, 80), tiers.get(1));
    }

    @Test
    @DisplayName("upsertCustomerPrice 落库后回读真实主键，不再是 null 占位")
    void upsertCustomerPricePersistsAndReturnsRealId() {
        AdminProductDtos.CustomerPriceForm form = new AdminProductDtos.CustomerPriceForm(
                9101L, 9002L, 8500L, 1794000000000L, 1794080000000L);
        when(productMapper.upsertCustomerPrice(eq(9002L), eq(9101L), eq(8500L), any(), any()))
                .thenReturn(1);
        when(productMapper.selectCustomerPriceId(9002L, 9101L)).thenReturn(77L);

        AdminProductDtos.CustomerPriceVO vo = service.upsertCustomerPrice(form);

        assertEquals(77L, vo.id());
        assertEquals(9101L, vo.skuId());
        assertEquals(9002L, vo.customerId());
        assertEquals(8500L, vo.priceCent());
        assertEquals(1794000000000L, vo.validFromMillis());
    }

    @Test
    @DisplayName("listCustomerPrices 真查库并把时间换算成毫秒")
    void listCustomerPricesMapsRows() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 8, 0, 0);
        ProductMapper.CustomerPriceRow row = new ProductMapper.CustomerPriceRow();
        row.setId(77L);
        row.setSkuId(9101L);
        row.setCustomerId(9002L);
        row.setPriceCent(8500L);
        row.setValidFrom(from);
        row.setValidTo(null);
        when(productMapper.selectCustomerPrices(9101L, 9002L)).thenReturn(List.of(row));

        List<AdminProductDtos.CustomerPriceVO> result = service.listCustomerPrices(9101L, 9002L);

        assertEquals(1, result.size());
        assertEquals(77L, result.get(0).id());
        assertEquals(8500L, result.get(0).priceCent());
        assertEquals(from.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                result.get(0).validFromMillis());
        assertEquals(0L, result.get(0).validToMillis());
    }

    @Test
    @DisplayName("deleteCustomerPrice 真删指定主键")
    void deleteCustomerPriceDelegatesToMapper() {
        service.deleteCustomerPrice(77L);

        verify(productMapper).deleteCustomerPrice(77L);
    }

    @Test
    @DisplayName("create 真写 product/sku 并同步初始库存，不再返回 stub")
    void createPersistsProductAndSkus() {
        when(idGen.next("SPU")).thenReturn("SPU-IT-1");
        when(idGen.next("SKU")).thenReturn("SKU-IT-1");
        when(skuMapper.selectBySkuNo("SKU-IT-1")).thenReturn(null);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-09T09:00:00Z"));
        doAnswer(invocation -> {
            ((ProductPO) invocation.getArgument(0)).setId(9103L);
            return 1;
        }).when(productMapper).insert(any(ProductPO.class));
        doAnswer(invocation -> {
            ((SkuPO) invocation.getArgument(0)).setId(9205L);
            return 1;
        }).when(skuMapper).insert(any(SkuPO.class));

        AdminProductDtos.ProductVO vo = service.create(new AdminProductDtos.ProductForm(
                "篮球", 1L, "商品描述",
                List.of(new AdminProductDtos.SkuForm("", "标准", 1L, 10000L, 7))));

        assertEquals("9103", vo.productId());
        assertEquals(1, vo.skuCount());
        ArgumentCaptor<ProductPO> productCaptor = ArgumentCaptor.forClass(ProductPO.class);
        verify(productMapper).insert(productCaptor.capture());
        assertEquals("SPU-IT-1", productCaptor.getValue().getProductNo());
        assertEquals("篮球", productCaptor.getValue().getTitle());
        assertEquals("商品描述", productCaptor.getValue().getDetailHtml());
        assertEquals("ON_SHELF", productCaptor.getValue().getStatus());
        ArgumentCaptor<SkuPO> skuCaptor = ArgumentCaptor.forClass(SkuPO.class);
        verify(skuMapper).insert(skuCaptor.capture());
        assertEquals(9103L, skuCaptor.getValue().getSpuId());
        assertEquals("SKU-IT-1", skuCaptor.getValue().getSkuNo());
        assertEquals(10000L, skuCaptor.getValue().getRetailPriceCent());
        verify(skuStockInitService).syncPhysicalQty(9205L, 7);
    }

    @Test
    @DisplayName("update 按提交的行新增/更新/下线 SKU，重新查详情不会回到旧矩阵")
    void updateReplacesSkuRows() {
        ProductPO product = product();
        SkuPO keep = sku(9205L, "SKU-9205", 10000L);
        SkuPO remove = sku(9206L, "SKU-9206", 20000L);
        when(productMapper.selectById(9103L)).thenReturn(product);
        when(productMapper.selectSkusBySpu(9103L)).thenReturn(List.of(keep, remove));
        when(skuMapper.selectBySkuNo("SKU-9205")).thenReturn(keep);
        when(idGen.next("SKU")).thenReturn("SKU-GEN-1");
        when(skuMapper.selectBySkuNo("SKU-GEN-1")).thenReturn(null);
        doAnswer(invocation -> {
            ((SkuPO) invocation.getArgument(0)).setId(9207L);
            return 1;
        }).when(skuMapper).insert(any(SkuPO.class));

        service.update(9103L, new AdminProductDtos.ProductForm("新商品", 1L, "新描述", List.of(
                new AdminProductDtos.SkuForm(9205L, "SKU-9205", "室内外通用", 1L, 29900L, 210),
                new AdminProductDtos.SkuForm(null, "", "新规格", 1L, 39900L, 50))));

        verify(productMapper).updateById(any(ProductPO.class));
        verify(skuMapper).updateById(argThat((SkuPO po) ->
                Long.valueOf(9205L).equals(po.getId())
                        && "SKU-9205".equals(po.getSkuNo())
                        && Long.valueOf(29900L).equals(po.getRetailPriceCent())
                        && po.getSpecJson() != null && po.getSpecJson().contains("室内外通用")));
        verify(skuMapper).insert(argThat((SkuPO po) ->
                Long.valueOf(9103L).equals(po.getSpuId())
                        && "SKU-GEN-1".equals(po.getSkuNo())
                        && Long.valueOf(39900L).equals(po.getRetailPriceCent())
                        && po.getSpecJson() != null && po.getSpecJson().contains("新规格")));
        verify(skuMapper).markOff(9206L);
        verify(skuMapper).deleteLadderPrices(9206L);
        verify(skuMapper).deleteCustomerPrices(9206L);
        verify(skuStockInitService).syncPhysicalQty(9205L, 210);
        verify(skuStockInitService).syncPhysicalQty(9207L, 50);
    }

    @Test
    @DisplayName("update 拒绝提交不属于当前商品的 SKU，避免越权改别店数据")
    void updateRejectsForeignSku() {
        when(productMapper.selectById(9103L)).thenReturn(product());
        when(productMapper.selectSkusBySpu(9103L)).thenReturn(List.of(sku(9205L, "SKU-9205", 10000L)));

        assertThrows(BizException.class, () -> service.update(9103L,
                new AdminProductDtos.ProductForm("新商品", 1L, "新描述", List.of(
                        new AdminProductDtos.SkuForm(9999L, "SKU-9999", "外来规格", 1L, 10000L, 1)))));

        verify(skuMapper, never()).insert(any(SkuPO.class));
    }

    @Test
    @DisplayName("deleteSku 下线 SKU 并清理其阶梯价/一客一价")
    void deleteSkuMarksOffAndCleansPrices() {
        when(skuMapper.selectById(9206L)).thenReturn(sku(9206L, "SKU-9206", 20000L));

        service.deleteSku(9206L);

        verify(skuMapper).markOff(9206L);
        verify(skuMapper).deleteLadderPrices(9206L);
        verify(skuMapper).deleteCustomerPrices(9206L);
    }

    /** 管理端列表行：SKU 数量与「已配折扣规格数」都由 SKU 侧算出。 */
    private static CatalogMapper.SkuRow skuRow(long id, Long ladderPriceCent) {
        CatalogMapper.SkuRow row = new CatalogMapper.SkuRow();
        row.setId(id);
        row.setRetailPriceCent(19900L);
        row.setLadderPriceCent(ladderPriceCent);
        return row;
    }

    @SafeVarargs
    private static Page<CatalogMapper.ProductRow> pageOf(CatalogMapper.ProductRow... rows) {
        Page<CatalogMapper.ProductRow> page = new Page<>(1, 20);
        page.setRecords(List.of(rows));
        page.setTotal(rows.length);
        return page;
    }

    @Test
    @DisplayName("管理端列表：已下架商品必须留在列表里，状态回显为已下架（不能被前台 ON_SHELF 过滤吞掉）")
    void listKeepsOffShelfProducts() {
        CatalogMapper.ProductRow draft = new CatalogMapper.ProductRow();
        draft.setId(9104L);
        draft.setTitle("尤尼克斯 AS-05 比赛级羽毛球");
        draft.setCategoryId(4L);
        draft.setCategoryName("羽毛球");
        draft.setStatus("DRAFT");

        when(catalogMapper.selectAdminProductPage(
                ArgumentMatchers.<Page<CatalogMapper.ProductRow>>any(), any(), any(), any()))
                .thenReturn(pageOf(draft));
        when(catalogMapper.selectSkusWithStock(9104L))
                .thenReturn(List.of(skuRow(9206L, 15900L)));

        PageResult<AdminProductDtos.ProductVO> result = service.list(new PageQuery(), null, null, null);

        assertEquals(1, result.getTotal());
        AdminProductDtos.ProductVO vo = result.getList().get(0);
        assertEquals("9104", vo.productId());
        assertFalse(vo.onShelf(), "下架商品应以 onShelf=false 回显，而不是从列表消失");
        assertEquals(1, vo.skuCount());
        assertEquals(1, vo.discountSkuCount());
        assertEquals("羽毛球", vo.categoryName());
    }

    @Test
    @DisplayName("管理端列表：上架状态筛选翻译成 product.status 下推到 SQL，关键字/分类一并传递")
    void listPushesOnShelfFilterToSql() {
        when(catalogMapper.selectAdminProductPage(
                ArgumentMatchers.<Page<CatalogMapper.ProductRow>>any(), any(), any(), any()))
                .thenReturn(pageOf());

        service.list(new PageQuery(), " 球 ", 4L, false);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(catalogMapper).selectAdminProductPage(
                ArgumentMatchers.<Page<CatalogMapper.ProductRow>>any(), eq(4L), eq("球"), statusCaptor.capture());
        assertEquals("DRAFT", statusCaptor.getValue());

        service.list(new PageQuery(), null, null, true);
        verify(catalogMapper).selectAdminProductPage(
                ArgumentMatchers.<Page<CatalogMapper.ProductRow>>any(), isNull(), isNull(), eq("ON_SHELF"));
    }
}
