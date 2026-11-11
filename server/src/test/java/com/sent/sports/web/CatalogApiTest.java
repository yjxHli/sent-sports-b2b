package com.sent.sports.web;

import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.controller.CatalogController;
import com.sent.sports.product.dto.CatalogDtos;
import com.sent.sports.product.service.ProductQueryService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品浏览接口契约测试（P4-08，红→绿）。
 * 列表筛选分页、详情按身份给价（P4 仅校验零售价结构，按身份取价待 P5 @CurrentUserArg）、缺货态、分类树。
 * 后端查询面尚未建设（无 Product/Sku 实体），ProductQueryService 以接口+桩实现占位，P5 接入仓储。
 */
@WebMvcTest(CatalogController.class)
@AutoConfigureMockMvc(addFilters = false)
class CatalogApiTest extends BaseApiTest {

    @MockBean
    private ProductQueryService productQueryService;

    @Test
    @DisplayName("商品列表按筛选分页返回 200")
    void listProductsReturnsPage() throws Exception {
        CatalogDtos.ProductVO vo = new CatalogDtos.ProductVO("p1", "篮球", 1L, "球类", null, 0L, true, List.of());
        when(productQueryService.search(any(), any()))
                .thenReturn(PageResult.of(List.of(vo), 1, 1, 10));
        mvc.perform(get("/api/products").param("categoryId", "1").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].productId").value("p1"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("商品详情返回价格与缺货态")
    void detailReturnsPriceAndStock() throws Exception {
        CatalogDtos.PriceVO price = new CatalogDtos.PriceVO(10000L, "100.00", null, null, null, null, null, null);
        CatalogDtos.SkuVO sku = new CatalogDtos.SkuVO("s1", "标准", 0, true, price);
        CatalogDtos.ProductVO vo = new CatalogDtos.ProductVO("p1", "篮球", 1L, "球类", null, 0L, true, List.of(sku));
        when(productQueryService.detail("p1")).thenReturn(vo);
        mvc.perform(get("/api/products/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skus[0].stockQty").value(0))
                .andExpect(jsonPath("$.data.skus[0].price.retailPriceCent").value(10000))
                .andExpect(jsonPath("$.data.skus[0].price.retailPriceYuan").value("100.00"));
    }

    @Test
    @DisplayName("缺货商品在售但库存为 0")
    void outOfStockState() throws Exception {
        CatalogDtos.PriceVO price = new CatalogDtos.PriceVO(5000L, "50.00", null, null, null, null, null, null);
        CatalogDtos.SkuVO sku = new CatalogDtos.SkuVO("s2", "缺货款", 0, false, price);
        CatalogDtos.ProductVO vo = new CatalogDtos.ProductVO("p2", "断货品", 2L, "球类", null, 0L, false, List.of(sku));
        when(productQueryService.detail("p2")).thenReturn(vo);
        mvc.perform(get("/api/products/p2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.onShelf").value(false))
                .andExpect(jsonPath("$.data.skus[0].onShelf").value(false));
    }

    @Test
    @DisplayName("未登录仅给零售价，会员价/协议价为空")
    void anonymousOnlyRetailPrice() throws Exception {
        CatalogDtos.PriceVO price = new CatalogDtos.PriceVO(10000L, "100.00", null, null, null, null, null, null);
        CatalogDtos.SkuVO sku = new CatalogDtos.SkuVO("s1", "标准", 5, true, price);
        CatalogDtos.ProductVO vo = new CatalogDtos.ProductVO("p1", "篮球", 1L, "球类", null, 0L, true, List.of(sku));
        when(productQueryService.detail("p1")).thenReturn(vo);
        mvc.perform(get("/api/products/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skus[0].price.memberPriceCent").isEmpty())
                .andExpect(jsonPath("$.data.skus[0].price.agreementPriceCent").isEmpty());
    }

    @Test
    @DisplayName("SKU 详情带出阶梯最低折扣价，配折扣后矩阵不再纹丝不动")
    void detailReturnsLadderPrice() throws Exception {
        CatalogDtos.PriceVO price = new CatalogDtos.PriceVO(
                10000L, "100.00", null, null, null, null, 8800L, "88.00");
        CatalogDtos.SkuVO sku = new CatalogDtos.SkuVO("s1", "标准", 5, true, price);
        CatalogDtos.ProductVO vo = new CatalogDtos.ProductVO("p1", "篮球", 1L, "球类", null, 0L, true, List.of(sku));
        when(productQueryService.detail("p1")).thenReturn(vo);

        mvc.perform(get("/api/products/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skus[0].price.ladderPriceCent").value(8800))
                .andExpect(jsonPath("$.data.skus[0].price.ladderPriceYuan").value("88.00"));
    }

    @Test
    @DisplayName("分类树返回 200")
    void categoriesReturns200() throws Exception {
        when(productQueryService.categories()).thenReturn(List.of());
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }
}
