package com.sent.sports.web.admin;

import com.sent.sports.common.dto.TierDto;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.product.controller.admin.AdminProductController;
import com.sent.sports.product.dto.admin.AdminProductDtos;
import com.sent.sports.product.service.admin.ProductAdminService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品管理接口契约测试（P4-32，红→绿）。
 * CRUD、上下架、阶梯价、一客一价均返回 200 / code 0；无权限（403）属 P5 拦截器，本阶段跳过断言。
 */
@WebMvcTest(AdminProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminProductApiTest extends BaseApiTest {

    @MockBean
    private ProductAdminService productAdminService;

    @Test
    @DisplayName("新建商品返回 200")
    void createReturns200() throws Exception {
        when(productAdminService.create(any())).thenReturn(
                new AdminProductDtos.ProductVO("p-1", "篮球", 1L, false, 1, null, 0));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "篮球");
        body.put("categoryId", 1L);
        body.put("skus", List.of(Map.of("skuCode", "SKU1", "categoryId", 1L, "retailPriceCent", 10000, "stock", 10)));
        mvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.productId").value("p-1"))
                .andExpect(jsonPath("$.data.name").value("篮球"));
    }

    @Test
    @DisplayName("更新商品返回 200，并把 SKU 行的 skuId 透传到服务层")
    void updateReturns200() throws Exception {
        when(productAdminService.update(any(), any())).thenReturn(
                new AdminProductDtos.ProductVO("5", "足球", 2L, false, 1, null, 0));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "足球");
        body.put("categoryId", 2L);
        body.put("skus", List.of(Map.of(
                "skuId", 9205L, "skuCode", "SKU-9205", "spec", "标准",
                "categoryId", 2L, "retailPriceCent", 10000, "stock", 10)));
        mvc.perform(put("/api/admin/products/5").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.productId").value("5"));
        verify(productAdminService).update(eq(5L), argThat((AdminProductDtos.ProductForm form) ->
                form.skus() != null && form.skus().size() == 1
                        && Long.valueOf(9205L).equals(form.skus().get(0).skuId())));
    }

    @Test
    @DisplayName("删除商品返回 200")
    void deleteReturns200() throws Exception {
        mvc.perform(delete("/api/admin/products/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("上/下架返回 200")
    void publishReturns200() throws Exception {
        when(productAdminService.publish(any(), anyBoolean())).thenReturn(
                new AdminProductDtos.ProductVO("5", "篮球", 1L, true, 0, null, 0));
        mvc.perform(post("/api/admin/products/5/publish")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("onShelf", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.onShelf").value(true));
    }

    @Test
    @DisplayName("新建 SKU 返回 200")
    void addSkuReturns200() throws Exception {
        when(productAdminService.addSku(any())).thenReturn(
                new AdminProductDtos.SkuVO("s-1", "SKU1", "标准", 10000L, 10));
        Map<String, Object> body = Map.of("skuCode", "SKU1", "categoryId", 1L, "retailPriceCent", 10000, "stock", 10);
        mvc.perform(post("/api/admin/skus").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skuId").value("s-1"));
    }

    @Test
    @DisplayName("阶梯价配置返回 200")
    void configLadderReturns200() throws Exception {
        mvc.perform(put("/api/admin/skus/1/ladder-prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("skuId", 1L, "tiers", List.of(Map.of("minQty", 10, "maxQty", 49, "rate", 95))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("一客一价新建返回 200")
    void upsertCustomerPriceReturns200() throws Exception {
        when(productAdminService.upsertCustomerPrice(any())).thenReturn(
                new AdminProductDtos.CustomerPriceVO(null, 1L, 9L, 8800L, 0L, 0L));
        Map<String, Object> body = Map.of("skuId", 1L, "customerId", 9L, "priceCent", 8800,
                "validFromMillis", 0, "validToMillis", 0);
        mvc.perform(post("/api/admin/customer-prices").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.priceCent").value(8800));
    }

    @Test
    @DisplayName("商品列表返回空分页 200")
    void listReturnsEmptyPage() throws Exception {
        when(productAdminService.list(any(), any(), any(), any())).thenReturn(PageResult.of(List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/products").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("读取阶梯价返回该 SKU 既有折扣，供编辑页回显与删除")
    void listLadderPricesReturnsTiers() throws Exception {
        when(productAdminService.listLadderPrices(9101L)).thenReturn(
                List.of(new TierDto(1, 9, 88), new TierDto(10, -1, 80)));

        mvc.perform(get("/api/admin/skus/9101/ladder-prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skuId").value(9101))
                .andExpect(jsonPath("$.data.tiers[0].rate").value(88))
                .andExpect(jsonPath("$.data.tiers[1].maxQty").value(-1));
    }

    @Test
    @DisplayName("一客一价列表返回真实记录并透传客户筛选")
    void listCustomerPricesReturnsRealRows() throws Exception {
        when(productAdminService.listCustomerPrices(eq(9101L), eq(9002L)))
                .thenReturn(List.of(new AdminProductDtos.CustomerPriceVO(
                        77L, 9101L, 9002L, 8500L, 1794000000000L, 1794080000000L)));

        mvc.perform(get("/api/admin/customer-prices")
                        .param("skuId", "9101").param("customerId", "9002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(77))
                .andExpect(jsonPath("$.data[0].priceCent").value(8500));
    }

    @Test
    @DisplayName("商品列表透传关键字与分类筛选，并返回折扣 SKU 数")
    void listPassesFiltersAndReturnsDiscountSkuCount() throws Exception {
        when(productAdminService.list(any(), eq("ball"), eq(1L), any()))
                .thenReturn(PageResult.of(List.of(
                        new AdminProductDtos.ProductVO("1001", "basketball", 1L, true, 2, "ball", 1)),
                        1L, 1, 10));

        mvc.perform(get("/api/admin/products")
                        .param("keyword", "ball").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].categoryName").value("ball"))
                .andExpect(jsonPath("$.data.list[0].skuCount").value(2))
                .andExpect(jsonPath("$.data.list[0].discountSkuCount").value(1));
    }

    @Test
    @DisplayName("商品列表把「上架状态」筛选透传给服务层，下架商品能以 onShelf=false 回显")
    void listPassesOnShelfFilter() throws Exception {
        when(productAdminService.list(any(), any(), any(), eq(false)))
                .thenReturn(PageResult.of(List.of(
                        new AdminProductDtos.ProductVO("9104", "draft", 1L, false, 1, "ball", 0)),
                        1L, 1, 10));

        mvc.perform(get("/api/admin/products").param("onShelf", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].onShelf").value(false));

        verify(productAdminService).list(any(), any(), any(), eq(false));
    }
}
