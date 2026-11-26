package com.sent.sports.web.admin;

import com.sent.sports.coupon.controller.admin.CsCouponGiftController;
import com.sent.sports.coupon.dto.CouponDtos;
import com.sent.sports.coupon.service.CustomerCouponGiftService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客服发券接口契约测试（V64）。
 *
 * <p>三条路径都以客服身份调用（权限 {@code chat:serve} 由 E2E 用真实令牌覆盖，
 * 切片测试关掉了过滤器链）。这里只锁字段形状与「参数透传不丢」。</p>
 */
@WebMvcTest(CsCouponGiftController.class)
@AutoConfigureMockMvc(addFilters = false)
class CsCouponGiftApiTest extends BaseApiTest {

    @MockBean
    private CustomerCouponGiftService giftService;

    @Test
    @DisplayName("可发券档位：只返回面额与券名，金额以分下发")
    void options() throws Exception {
        when(giftService.giftOptions()).thenReturn(List.of(
                new CouponDtos.TemplateVO(1L, "20 元补偿券", 2000L, 0L, 30,
                        "CASH", "AMOUNT", 0, "ALL", null, "ALL")));

        mvc.perform(get("/api/admin/chat/coupons/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].templateId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("20 元补偿券"))
                .andExpect(jsonPath("$.data[0].amountCent").value(2000));
    }

    @Test
    @DisplayName("发券记录：limit 透传，行内带来源与原因")
    void records() throws Exception {
        when(giftService.recordsOf(eq(9002L), anyInt())).thenReturn(List.of(
                new CouponDtos.GiftRecordVO(11L, "CPN1", "20 元补偿券", 2000L, 0L,
                        "UNUSED", "AFTER_SALE", "客户投诉安抚", 1_700_000_000_000L, 1_690_000_000_000L)));

        mvc.perform(get("/api/admin/chat/customers/9002/coupons").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].couponNo").value("CPN1"))
                .andExpect(jsonPath("$.data[0].source").value("AFTER_SALE"))
                .andExpect(jsonPath("$.data[0].reason").value("客户投诉安抚"));

        verify(giftService).recordsOf(9002L, 10);
    }

    @Test
    @DisplayName("发券：模板号与原因透传，操作人取不到时为 null")
    void gift() throws Exception {
        when(giftService.gift(eq(9002L), eq(1L), eq("客户投诉安抚"), isNull()))
                .thenReturn(new CouponDtos.GiftResultVO(1, "20 元补偿券", 2000L));

        mvc.perform(post("/api/admin/chat/customers/9002/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("templateId", 1, "reason", "客户投诉安抚"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issued").value(1))
                .andExpect(jsonPath("$.data.name").value("20 元补偿券"))
                .andExpect(jsonPath("$.data.amountCent").value(2000));

        verify(giftService).gift(9002L, 1L, "客户投诉安抚", null);
    }

    @Test
    @DisplayName("发券：不选券（templateId 缺失）被 Bean Validation 拦下")
    void giftRequiresTemplate() throws Exception {
        mvc.perform(post("/api/admin/chat/customers/9002/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"安抚\"}"))
                .andExpect(status().isBadRequest());
    }
}
