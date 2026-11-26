package com.sent.sports.chat.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 话术配置接口模型（V60，管理端 {@code /api/admin/faq}）。
 *
 * <p>三件事说清楚：</p>
 * <ul>
 *   <li>{@code keywords} 对外仍是「逗号分隔的一串」——运营在输入框里怎么打就怎么存，
 *       归一化（去空格 / 中文逗号转英文 / 去重）在服务层做，接口不做魔法；</li>
 *   <li>{@code replyContent} 硬上限 500 字：超长回复在手机聊天窗里会被折叠成一大段，
 *       客户根本读不完，也不符合列表展示；</li>
 *   <li>{@code status} 可空（新增默认启用），非法值由服务层拒绝。</li>
 * </ul>
 */
public final class AdminFaqDtos {

    private AdminFaqDtos() {
    }

    /**
     * 列表 / 详情行。
     *
     * @param id            条目 ID
     * @param keywords      关键词原文（逗号分隔，前端自行拆 Tag）
     * @param replyContent  回复正文
     * @param hitCount      命中次数（运营评估话术有效性）
     * @param status        ENABLED / DISABLED
     * @param updatedAtMillis 最近更新时间（毫秒）
     */
    public record FaqVO(long id, String keywords, String replyContent, int hitCount,
                        String status, long updatedAtMillis) {
    }

    /** 新增 / 修改入参。 */
    public record FaqCmd(@NotBlank(message = "关键词不能为空") @Size(max = 255, message = "关键词最长 255 字") String keywords,
                         @NotBlank(message = "回复内容不能为空") @Size(max = 500, message = "回复内容最长 500 字") String replyContent,
                         String status) {
    }

    /** 启用 / 停用入参。 */
    public record StatusCmd(@NotBlank(message = "状态不能为空") String status) {
    }
}
