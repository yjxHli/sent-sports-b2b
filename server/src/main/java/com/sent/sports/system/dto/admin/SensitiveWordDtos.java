package com.sent.sports.system.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 违禁词库管理接口 DTO（V46）。
 * <p>词库是「聊天 + 昵称」两道风控的唯一配置来源，管理端改完即热生效。</p>
 */
public final class SensitiveWordDtos {

    private SensitiveWordDtos() {
    }

    /**
     * 新增 / 编辑表单。
     *
     * @param word     违禁词原文（只收完整词组，单字会误伤正常话术，由页面提示而非后端硬拦）
     * @param category PROFANITY / AD / ILLEGAL / OTHER，缺省 OTHER
     * @param enabled  是否参与匹配；新增缺省 true
     * @param remark   备注
     */
    public record WordForm(
            @NotBlank(message = "违禁词不能为空")
            @Size(max = 64, message = "违禁词不能超过 64 个字符")
            String word,
            String category,
            Boolean enabled,
            @Size(max = 255, message = "备注不能超过 255 个字符")
            String remark) {
    }

    /** 列表行：id 用字符串下发会麻烦，这里统一 long；时间给毫秒，前端自己按业务时区格式化。 */
    public record WordVO(long id, String word, String category, String categoryLabel,
                         boolean enabled, String remark,
                         long createdAtMillis, long updatedAtMillis) {
    }

    /** 顶部统计：总数 / 启用 / 停用。 */
    public record StatsVO(long total, long enabled, long disabled) {
    }
}
