package com.sent.sports.web.admin;

import com.sent.sports.common.dto.admin.ConfirmDtos;
import com.sent.sports.common.config.ClockConfig;
import com.sent.sports.common.service.impl.ConfirmTokenServiceImpl;
import com.sent.sports.common.service.impl.InMemoryConfirmTokenStore;
import com.sent.sports.support.BaseApiTest;
import com.sent.sports.web.admin.AdminConfirmController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 危险操作确认令牌接口契约测试（P4-56，红→绿）。
 * 短语错返回 40302、正确短语返回令牌、令牌按 action|targetId 绑定（不同目标令牌不同）、
 * 过期时间约 5 分钟。使用真实桩 {@link ConfirmTokenServiceImpl} 验证短语/绑定/过期契约。
 */
@WebMvcTest(AdminConfirmController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ConfirmTokenServiceImpl.class, InMemoryConfirmTokenStore.class, ClockConfig.class})
class ConfirmTokenApiTest extends BaseApiTest {

    private String issueAndGetToken(String action, String targetId, String confirmText) throws Exception {
        String body = toJson(Map.of("action", action, "targetId", targetId, "confirmText", confirmText));
        String resp = mvc.perform(post("/api/admin/confirm-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).path("data").path("token").asText();
    }

    @Test
    @DisplayName("确认短语错误返回 40302")
    void wrongPhraseReturns40302() throws Exception {
        Map<String, Object> body = Map.of("action", "ORDER_CLOSE", "targetId", "1", "confirmText", "错误短语");
        mvc.perform(post("/api/admin/confirm-token")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    @DisplayName("确认短语正确返回一次性令牌")
    void correctPhraseReturnsToken() throws Exception {
        String token = issueAndGetToken("ORDER_CLOSE", "1", "确认关闭");
        org.junit.jupiter.api.Assertions.assertFalse(token.isBlank());
    }

    @Test
    @DisplayName("令牌按目标绑定：不同 targetId 令牌不同")
    void tokenBoundToTarget() throws Exception {
        String t1 = issueAndGetToken("ORDER_CLOSE", "1", "确认关闭");
        String t2 = issueAndGetToken("ORDER_CLOSE", "2", "确认关闭");
        org.junit.jupiter.api.Assertions.assertNotEquals(t1, t2, "不同目标的令牌应不同");
    }

    @Test
    @DisplayName("令牌有效期约 5 分钟")
    void tokenExpiresIn5Min() throws Exception {
        long now = System.currentTimeMillis();
        Map<String, Object> body = Map.of("action", "ORDER_CLOSE", "targetId", "1", "confirmText", "确认关闭");
        String resp = mvc.perform(post("/api/admin/confirm-token")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long expireAt = json.readTree(resp).path("data").path("expireAt").asLong();
        long delta = expireAt - now;
        org.junit.jupiter.api.Assertions.assertTrue(delta <= 5L * 60 * 1000 + 2000L,
                "过期时间应不超过 ~5 分钟");
        org.junit.jupiter.api.Assertions.assertTrue(delta >= 0, "过期时间不应早于当前");
    }
}
