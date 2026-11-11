package com.sent.sports.web;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.controller.CustomController;
import com.sent.sports.customize.dto.CustomDtos;
import com.sent.sports.customize.service.CustomQueryService;
import com.sent.sports.customize.service.SubmitDraftUseCase;
import com.sent.sports.customize.service.dto.SubmitDraftResult;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 定制接口契约测试（P4-23，红→绿）。
 * 用户侧：上传图稿版本+1（SubmitDraftUseCase）、非图稿格式 40001、详情含驳回原因（CustomQueryService 桩）。
 * 审核/投产（ReviewCustomUseCase/ProduceCustomUseCase）属管理侧，本期不做用户侧契约。
 */
@WebMvcTest(CustomController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomApiTest extends BaseApiTest {

    @MockBean
    private SubmitDraftUseCase submitDraftUseCase;

    @MockBean
    private CustomQueryService customQueryService;

    @Test
    @DisplayName("上传图稿版本+1 返回新版本与待审核态 200")
    void uploadDraftBumpsVersion() throws Exception {
        when(submitDraftUseCase.submit(any()))
                .thenReturn(new SubmitDraftResult("CO1", 2, "WAIT_REVIEW", 1_700_000_000_000L));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileId", "f-1");
        body.put("fileName", "logo.png");
        body.put("fileSize", 1024L);
        mvc.perform(post("/api/customize/orders/CO1/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customOrderId").value("CO1"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.status").value("WAIT_REVIEW"));
    }

    @Test
    @DisplayName("非图稿格式提交返回 PARAM_INVALID 40001")
    void uploadNonImageReturns40001() throws Exception {
        when(submitDraftUseCase.submit(any()))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileId", "f-2");
        body.put("fileName", "note.txt");
        body.put("fileSize", 2048L);
        mvc.perform(post("/api/customize/orders/CO1/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("定制单详情含驳回原因 200")
    void detailContainsRejectReason() throws Exception {
        CustomDtos.CustomVO vo = new CustomDtos.CustomVO("CO1", "REJECTED", 1, 1,
                "图稿分辨率不足，请重新上传", 0L);
        when(customQueryService.detail("CO1")).thenReturn(vo);
        mvc.perform(get("/api/customize/orders/CO1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.customOrderId").value("CO1"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("图稿分辨率不足，请重新上传"));
    }
}
