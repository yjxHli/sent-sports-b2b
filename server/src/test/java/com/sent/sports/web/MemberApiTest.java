package com.sent.sports.web;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.member.controller.MemberController;
import com.sent.sports.member.dto.MemberDtos;
import com.sent.sports.member.service.MemberQueryService;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会员接口契约测试（P4-26，红→绿）。
 * 2026-11-28 起 MemberController 只保留读面（等级进度/积分明细/额度应收/专属商品），
 * 全部走 MemberQueryService；写面已迁到管理端 customers 接口，不再属于本控制器。
 */
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberApiTest extends BaseApiTest {

    @MockBean
    private MemberQueryService memberQueryService;

    @Test
    @DisplayName("会员等级进度 200")
    void levelProgressReturns200() throws Exception {
        MemberDtos.LevelVO vo = new MemberDtos.LevelVO(1L, "LV1", 1200L, 1000L, 5000L, 0.05d, 0L);
        when(memberQueryService.levelProgress(any())).thenReturn(vo);
        mvc.perform(get("/api/member/level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.level").value("LV1"))
                .andExpect(jsonPath("$.data.growthValue").value(1200));
    }

    @Test
    @DisplayName("会员积分明细 200")
    void pointDetailReturns200() throws Exception {
        MemberDtos.PointVO vo = new MemberDtos.PointVO(1L, 1200L);
        when(memberQueryService.pointDetail(any())).thenReturn(vo);
        mvc.perform(get("/api/member/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.availablePoints").value(1200));
    }

    @Test
    @DisplayName("会员额度与应收 200")
    void creditReturns200() throws Exception {
        MemberDtos.CreditVO vo = new MemberDtos.CreditVO(1L, 100000L, "1000.00", 30000L, "300.00");
        when(memberQueryService.credit(any())).thenReturn(vo);
        mvc.perform(get("/api/member/credit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.creditLimitCent").value(100000))
                .andExpect(jsonPath("$.data.creditLimitYuan").value("1000.00"))
                .andExpect(jsonPath("$.data.receivableCent").value(30000));
    }

    @Test
    @DisplayName("P0-03 管理端按客户查看会员概览：管理员令牌下 customerId 透传到查询服务")
    void adminCanQueryOtherCustomer() throws Exception {
        when(memberQueryService.levelProgress(91203L))
                .thenReturn(new MemberDtos.LevelVO(91203L, "LV2", 6000L, 5000L, 20000L, 0.0667d, 0L));
        mvc.perform(get("/api/member/level").param("customerId", "91203")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 9001L, "jti")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customerId").value(91203))
                .andExpect(jsonPath("$.data.level").value("LV2"));
    }

    @Test
    @DisplayName("P0-03 普通客户传他人 customerId：参数被忽略，只查自己的")
    void customerCannotQueryOtherCustomer() throws Exception {
        when(memberQueryService.levelProgress(9002L))
                .thenReturn(new MemberDtos.LevelVO(9002L, "LV1", 1200L, 1000L, 5000L, 0.05d, 0L));

        mvc.perform(get("/api/member/level").param("customerId", "91203")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(9002));

        verify(memberQueryService).levelProgress(9002L);
        verify(memberQueryService, never()).levelProgress(91203L);
    }

    @Test
    @DisplayName("P0-03 匿名传 customerId：不生效，退回占位 0 由查询服务拒绝")
    void anonymousCannotQueryOtherCustomer() throws Exception {
        when(memberQueryService.levelProgress(0L))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND));

        mvc.perform(get("/api/member/level").param("customerId", "91203"))
                .andExpect(status().isNotFound());

        verify(memberQueryService).levelProgress(0L);
        verify(memberQueryService, never()).levelProgress(91203L);
    }

    @Test
    @DisplayName("会员专属商品 200")
    void exclusiveProductsReturns200() throws Exception {
        when(memberQueryService.exclusiveProducts(any())).thenReturn(List.of(
                new MemberDtos.ExclusiveProductVO("ep1", "限量球衣", 19900L, "199.00")));
        mvc.perform(get("/api/member/exclusive-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].productName").value("限量球衣"));
    }
}
