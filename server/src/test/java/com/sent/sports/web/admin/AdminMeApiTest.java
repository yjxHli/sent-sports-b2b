package com.sent.sports.web.admin;

import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.support.BaseApiTest;
import com.sent.sports.system.controller.admin.AdminMeController;
import com.sent.sports.system.dto.admin.AdminMeVO;
import com.sent.sports.system.service.StaffProfileQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端当前员工接口契约测试（收口阶段）：{@code GET /api/admin/me} 返回权限点与数据范围。
 */
@WebMvcTest(AdminMeController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminMeApiTest extends BaseApiTest {

    @MockBean
    private StaffProfileQueryService staffProfileQueryService;

    @Test
    @DisplayName("admin 令牌返回员工资料与权限 200")
    void returnsAdminMe() throws Exception {
        when(staffProfileQueryService.profile(anyLong())).thenReturn(new AdminMeVO(
                7L, "S007", "王客服", "admin", "ADMIN",
                List.of("refund:audit", "finance:invoice"), "ALL"));

        mvc.perform(get("/api/admin/me")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_ADMIN, 7L, "jti-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.username").value("S007"))
                .andExpect(jsonPath("$.data.aud").value("admin"))
                .andExpect(jsonPath("$.data.roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.data.permissions[0]").value("refund:audit"))
                .andExpect(jsonPath("$.data.dataScope").value("ALL"));
    }

    @Test
    @DisplayName("user 端令牌访问管理端 me 返回 40101")
    void userTokenRejected() throws Exception {
        mvc.perform(get("/api/admin/me")
                        .requestAttr(CurrentUser.ATTR, new CurrentUser(CurrentUser.AUD_USER, 7L, "jti-u")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("匿名访问返回 40101")
    void anonymousRejected() throws Exception {
        mvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }
}
