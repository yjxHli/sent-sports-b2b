package com.sent.sports.web;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.customer.controller.user.AddressController;
import com.sent.sports.customer.service.AddressService;
import com.sent.sports.customer.service.dto.AddressVO;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收货地址接口契约（2026-11-15）。
 *
 * <p>重点两条：</p>
 * <ul>
 *   <li>身份只认令牌里的 {@code aud=user}——没有令牌（或管理端令牌）一律 401，不能用请求体传 customerId；</li>
 *   <li>他人地址由 service 抛 NOT_FOUND，接口层原样转 404，不泄露「这条存在」。</li>
 * </ul>
 */
@WebMvcTest(AddressController.class)
@AutoConfigureMockMvc(addFilters = false)
class AddressApiTest extends BaseApiTest {

    @MockBean
    private AddressService addressService;

    private static CurrentUser user() {
        return new CurrentUser(CurrentUser.AUD_USER, 9002L, "jti");
    }

    @Test
    @DisplayName("地址列表返回 200，且带默认标记")
    void listReturns200() throws Exception {
        when(addressService.list(9002L)).thenReturn(List.of(
                new AddressVO(1L, "张三", "13800138000", "江苏省", "南京市", "玄武区", "中山路 100 号", true)));

        mvc.perform(get("/api/addresses").requestAttr(CurrentUser.ATTR, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].receiver").value("张三"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    @DisplayName("未登录访问地址簿返回 40101")
    void anonymousRejected() throws Exception {
        mvc.perform(get("/api/addresses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("新增地址返回落库后的 VO（含 id 与默认标记）")
    void createReturns200() throws Exception {
        when(addressService.create(eq(9002L), any())).thenReturn(
                new AddressVO(7L, "李四", "13900139000", "江苏省", "南京市", "鼓楼区", "中山北路 1 号", false));

        Map<String, Object> body = Map.of(
                "receiver", "李四", "phone", "13900139000",
                "province", "江苏省", "city", "南京市", "district", "鼓楼区",
                "detail", "中山北路 1 号", "isDefault", false);

        mvc.perform(post("/api/addresses")
                        .requestAttr(CurrentUser.ATTR, user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.receiver").value("李四"));
    }

    @Test
    @DisplayName("设为默认返回 200，并透传当前客户身份")
    void setDefaultReturns200() throws Exception {
        mvc.perform(put("/api/addresses/5/default").requestAttr(CurrentUser.ATTR, user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(addressService).setDefault(9002L, 5L);
    }

    @Test
    @DisplayName("删除他人地址返回 40401")
    void deleteForeignReturns40401() throws Exception {
        doThrow(new BizException(ErrorCode.NOT_FOUND)).when(addressService).delete(anyLong(), anyLong());

        mvc.perform(delete("/api/addresses/9").requestAttr(CurrentUser.ATTR, user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}
