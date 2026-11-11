package com.sent.sports.web.admin;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.controller.admin.AdminCustomController;
import com.sent.sports.customize.service.CustomAdminQueryService;
import com.sent.sports.customize.service.ProduceCustomUseCase;
import com.sent.sports.customize.service.ReviewCustomUseCase;
import com.sent.sports.customize.service.dto.ProduceCustomResult;
import com.sent.sports.customize.service.dto.ReviewCustomResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 定制审核接口契约测试（P4-41，红→绿）。
 * 通过 200、驳回缺说明 40001、排产查无单 40401、待审列表空页 200。
 */
@WebMvcTest(AdminCustomController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCustomApiTest extends BaseApiTest {

    @MockBean
    private ReviewCustomUseCase reviewCustomUseCase;

    @MockBean
    private ProduceCustomUseCase produceCustomUseCase;

    @MockBean
    private CustomAdminQueryService customAdminQueryService;

    @Test
    @DisplayName("审核通过返回迁移后状态 200")
    void passReturns200() throws Exception {
        when(reviewCustomUseCase.review(any())).thenReturn(
                new ReviewCustomResult("C1", "APPROVED", 2, 1700000000000L, 86400000L));
        mvc.perform(post("/api/admin/custom-orders/C1/pass")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customOrderId").value("C1"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.lockedVersion").value(2));
    }

    @Test
    @DisplayName("驳回缺少说明返回 40001")
    void rejectMissingRemarkReturns40001() throws Exception {
        mvc.perform(post("/api/admin/custom-orders/C1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("reasonCode", "SIZE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("排产查无单返回 NOT_FOUND 40401")
    void productionNotFoundReturns40401() throws Exception {
        when(produceCustomUseCase.produce(any()))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(post("/api/admin/custom-orders/C404/production")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("待审列表返回空分页 200")
    void pendingReturnsEmptyPage() throws Exception {
        when(customAdminQueryService.pendingList(any())).thenReturn(
                com.sent.sports.common.web.PageResult.of(java.util.List.of(), 0L, 1, 10));
        mvc.perform(get("/api/admin/custom-orders").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
