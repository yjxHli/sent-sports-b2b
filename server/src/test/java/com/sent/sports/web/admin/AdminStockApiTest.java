package com.sent.sports.web.admin;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.stock.controller.admin.AdminStockController;
import com.sent.sports.stock.dto.admin.AdminStockDtos;
import com.sent.sports.stock.service.InboundUseCase;
import com.sent.sports.stock.service.LossUseCase;
import com.sent.sports.stock.service.StockCheckUseCase;
import com.sent.sports.stock.service.StockQueryService;
import com.sent.sports.stock.service.dto.InboundResult;
import com.sent.sports.stock.service.dto.LossResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 库存管理接口契约测试（P4-35，红→绿）。
 * 入库 200、报损缺原因 40001、盘点审批查无单 40401、列表空页 200。
 */
@WebMvcTest(AdminStockController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminStockApiTest extends BaseApiTest {

    @MockBean
    private InboundUseCase inboundUseCase;

    @MockBean
    private LossUseCase lossUseCase;

    @MockBean
    private StockCheckUseCase stockCheckUseCase;

    @MockBean
    private StockQueryService stockQueryService;

    @Test
    @DisplayName("手工入库成功返回调后库存 200")
    void inboundReturns200() throws Exception {
        when(inboundUseCase.inbound(any())).thenReturn(new InboundResult(1L, 100L, 90L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skuId", 1L);
        body.put("qty", 10);
        body.put("reason", "采购到货");
        body.put("remark", "PO-1");
        mvc.perform(post("/api/admin/stock/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skuId").value(1))
                .andExpect(jsonPath("$.data.afterPhysicalQty").value(100))
                .andExpect(jsonPath("$.data.afterAvailableQty").value(90));
    }

    @Test
    @DisplayName("报损缺少原因返回 40001")
    void lossMissingReasonReturns40001() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skuId", 1L);
        body.put("qty", 2);
        mvc.perform(post("/api/admin/stock/loss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("盘点审批查无单返回 NOT_FOUND 40401")
    void approveNotFoundReturns40401() throws Exception {
        when(stockCheckUseCase.approve(any()))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(post("/api/admin/stock/checks/SC-404/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("库存列表返回空分页 200")
    void listReturnsEmptyPage() throws Exception {
        when(stockQueryService.list(any(), any())).thenReturn(
                com.sent.sports.common.web.PageResult.of(java.util.List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/stock").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("库存列表透传 SKU 筛选，并回显四口径与可用量")
    void listPassesSkuIdAndReturnsStockRow() throws Exception {
        when(stockQueryService.list(any(), eq(9209L))).thenReturn(
                com.sent.sports.common.web.PageResult.of(java.util.List.of(
                        new com.sent.sports.stock.dto.admin.AdminStockDtos.StockRow(
                                9209L, "尤尼克斯 AS-05 · 一筒(12只)", 100, 10, 5, 5, 80, 20, false)),
                        1L, 1, 10));

        mvc.perform(get("/api/admin/stock").param("page", "1").param("size", "10")
                        .param("skuId", "9209"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].skuName").value("尤尼克斯 AS-05 · 一筒(12只)"))
                .andExpect(jsonPath("$.data.list[0].availableQty").value(80))
                .andExpect(jsonPath("$.data.list[0].safeQty").value(20));

        verify(stockQueryService).list(any(), eq(9209L));
    }

    @Test
    @DisplayName("库存流水按业务类型收窄，入库页只看 INBOUND")
    void flowsPassesBizType() throws Exception {
        when(stockQueryService.flows(any(), any(), eq("INBOUND"))).thenReturn(
                com.sent.sports.common.web.PageResult.of(java.util.List.of(), 0L, 1, 20));

        mvc.perform(get("/api/admin/stock/flows").param("page", "1").param("size", "20")
                        .param("bizType", "INBOUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        verify(stockQueryService).flows(any(), any(), eq("INBOUND"));
    }
}
