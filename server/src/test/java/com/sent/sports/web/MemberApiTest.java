package com.sent.sports.web;

import com.sent.sports.member.controller.MemberController;
import com.sent.sports.member.dto.MemberDtos;
import com.sent.sports.member.domain.MemberLevel;
import com.sent.sports.member.service.AdjustPointUseCase;
import com.sent.sports.member.service.GrantLevelUseCase;
import com.sent.sports.member.service.MemberQueryService;
import com.sent.sports.member.service.dto.AdjustPointResult;
import com.sent.sports.member.service.dto.GrantLevelResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会员接口契约测试（P4-26，红→绿）。
 * 读面（等级进度/积分明细/额度应收/专属商品）走 MemberQueryService 桩；写面（授级/积分调整）走用例。
 * 当前用户绑定（@CurrentUserArg）在 Phase 5，读面以占位客户号查询。
 */
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberApiTest extends BaseApiTest {

    @MockBean
    private MemberQueryService memberQueryService;

    @MockBean
    private GrantLevelUseCase grantLevelUseCase;

    @MockBean
    private AdjustPointUseCase adjustPointUseCase;

    @Test
    @DisplayName("会员等级进度 200")
    void levelProgressReturns200() throws Exception {
        MemberDtos.LevelVO vo = new MemberDtos.LevelVO(1L, "VIP1", 5000L, 2000L, 10000L, 0.375d, 0L);
        when(memberQueryService.levelProgress(any())).thenReturn(vo);
        mvc.perform(get("/api/member/level"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.level").value("VIP1"))
                .andExpect(jsonPath("$.data.growthValue").value(5000));
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
    @DisplayName("管理端按客户查看会员概览：customerId 透传到查询服务")
    void levelAcceptsExplicitCustomerId() throws Exception {
        when(memberQueryService.levelProgress(91203L))
                .thenReturn(new MemberDtos.LevelVO(91203L, "LV2", 6000L, 5000L, 20000L, 0.0667d, 0L));
        mvc.perform(get("/api/member/level").param("customerId", "91203"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customerId").value(91203))
                .andExpect(jsonPath("$.data.level").value("LV2"));
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

    @Test
    @DisplayName("人工授级 200")
    void grantLevelReturns200() throws Exception {
        when(grantLevelUseCase.grant(any()))
                .thenReturn(new GrantLevelResult(1L, MemberLevel.VIP2, 1_800_000_000_000L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 1L);
        body.put("targetLevel", "VIP2");
        body.put("validToMillis", 1_800_000_000_000L);
        body.put("reason", "大客户维护");
        mvc.perform(post("/api/member/grant-level")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.level").value("VIP2"))
                .andExpect(jsonPath("$.data.validToMillis").value(1_800_000_000_000L));
    }

    @Test
    @DisplayName("积分调整 200")
    void adjustPointReturns200() throws Exception {
        when(adjustPointUseCase.adjust(any()))
                .thenReturn(new AdjustPointResult(1L, 1200L, 1500L, 300L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", 1L);
        body.put("delta", 300L);
        body.put("reason", "活动奖励");
        body.put("bizType", "ACTIVITY");
        body.put("bizNo", "A-1");
        mvc.perform(post("/api/member/points/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.beforePoints").value(1200))
                .andExpect(jsonPath("$.data.afterPoints").value(1500))
                .andExpect(jsonPath("$.data.delta").value(300));
    }
}
