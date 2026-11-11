package com.sent.sports.web;

import com.sent.sports.cart.controller.CartController;
import com.sent.sports.cart.dto.CartDtos;
import com.sent.sports.cart.service.CartCommandService;
import com.sent.sports.cart.service.CartQuoteUseCase;
import com.sent.sports.cart.service.dto.CartQuoteResult;
import com.sent.sports.cart.service.dto.QuoteLine;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 购物车接口契约测试（P4-11，红→绿）。
 * 加购去重（两次加购同 SKU 均 200）、改量、删除、列表空、结算试算金额一致（分↔元映射）。
 */
@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartApiTest extends BaseApiTest {

    @MockBean
    private CartCommandService cartCommandService;

    @MockBean
    private CartQuoteUseCase cartQuoteUseCase;

    @Test
    @DisplayName("加购去重：同 SKU 多次加购均返回 200")
    void addItemDedupReturns200() throws Exception {
        when(cartCommandService.addItem(any())).thenReturn(
                new CartDtos.CartItemVO(0L, 1L, "占位商品", 3, true));
        Map<String, Object> body = Map.of("skuId", 1L, "qty", 3);
        mvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skuId").value(1))
                .andExpect(jsonPath("$.data.qty").value(3));
    }

    @Test
    @DisplayName("改量返回更新后的数量 200")
    void updateQtyReturns200() throws Exception {
        when(cartCommandService.updateQty(any(), any())).thenReturn(
                new CartDtos.CartItemVO(5L, null, "占位商品", 7, true));
        Map<String, Object> body = Map.of("qty", 7, "selected", true);
        mvc.perform(put("/api/cart/items/5").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.itemId").value(5))
                .andExpect(jsonPath("$.data.qty").value(7));
    }

    @Test
    @DisplayName("删除行返回 200")
    void removeItemReturns200() throws Exception {
        mvc.perform(delete("/api/cart/items/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("购物车列表返回空 200")
    void listEmptyReturns200() throws Exception {
        when(cartCommandService.list()).thenReturn(List.of());
        mvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("结算试算金额一致：分与元映射正确")
    void checkoutPreviewAmountConsistent() throws Exception {
        QuoteLine line = new QuoteLine(1L, 5000L, "RETAIL", false, 10000L);
        when(cartQuoteUseCase.quote(any())).thenReturn(
                new CartQuoteResult(List.of(line), 10000L, 0L, 0L, 10000L));
        Map<String, Object> body = Map.of(
                "availablePoints", 0, "freightFreeThresholdCent", 0, "freightFeeCent", 0,
                "items", List.of(Map.of("skuId", 1L, "qty", 2, "customerTypeCode", "RETAIL",
                        "memberDiscountRate", 0, "agreementDiscountRate", 0, "allowStacking", false)));
        mvc.perform(post("/api/cart/checkout-preview").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.goodsAmountCent").value(10000))
                .andExpect(jsonPath("$.data.goodsAmountYuan").value("100.00"))
                .andExpect(jsonPath("$.data.freightCent").value(0))
                .andExpect(jsonPath("$.data.pointDeductCent").value(0))
                .andExpect(jsonPath("$.data.payableCent").value(10000))
                .andExpect(jsonPath("$.data.payableYuan").value("100.00"))
                .andExpect(jsonPath("$.data.lines[0].unitPriceCent").value(5000))
                .andExpect(jsonPath("$.data.lines[0].unitPriceYuan").value("50.00"))
                .andExpect(jsonPath("$.data.lines[0].lineTotalCent").value(10000))
                .andExpect(jsonPath("$.data.lines[0].lineTotalYuan").value("100.00"));
    }
}
