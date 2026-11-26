package com.sent.sports.customer.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 客户标签接口 DTO（V63）。
 *
 * <p>三条路径的受众不同，字段也各管一段：</p>
 * <ul>
 *   <li>{@link TagVO}：客服弹窗、会话头部 chips、管理端维护页<b>共用同一个形状</b>——
 *       分类中文名与配色键都由后端下发，前端不许自己写「EXCELLENT 显示绿色」的映射表，
 *       否则改一次分类文案要改三端；</li>
 *   <li>{@link TagForm}：只有后台人员能提交（新增 / 改名）；</li>
 *   <li>{@link ApplyTagsCmd}：客服提交的<b>全量勾选结果</b>，服务层按差异增删，不做整表覆盖。</li>
 * </ul>
 *
 * <p>注意 {@code tagIds} 允许空数组（= 把标签全取消），所以只能 {@code @NotNull}，
 * 不能 {@code @NotEmpty}——清空标签是正当操作，卡住它客服就没法撤销误标。</p>
 */
public final class CustomerTagDtos {

    private CustomerTagDtos() {
    }

    /** 标签（目录行 / 客户身上的 chip / 维护列表行，三者同形）。 */
    public record TagVO(long tagId, String name, String category, String categoryLabel,
                        String colorKey, String status, Integer sortNo) {
    }

    /** 新增 / 改名表单。 */
    public record TagForm(
            @NotBlank(message = "标签名不能为空")
            @Size(max = 32, message = "标签名最多 32 个字")
            String name,
            @NotBlank(message = "标签分类不能为空")
            String category) {
    }

    /** 启停表单：只认 ACTIVE / DISABLED（服务层校验，注解拦不住大小写与脏值）。 */
    public record StatusForm(@NotBlank(message = "状态不能为空") String status) {
    }

    /** 打标表单：全量勾选结果（空数组 = 全部取消）。 */
    public record ApplyTagsCmd(@NotNull(message = "标签清单不能为空") List<Long> tagIds) {
    }
}
