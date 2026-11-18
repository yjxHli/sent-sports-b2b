package com.sent.sports.common.dto.admin;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 管理端六列表批量删除 Web 层 DTO（置于 common，控制器无需互相依赖业务实现）。
 * <p>ids 为各列表的业务主键：退款单号 / 发票号 / 合同号 / 定制单号 / 订单号 / SKU ID。
 * 真正的归一化（去空、去重、排序）由 {@code BatchIds} 统一完成，
 * 与确认令牌的 targetId 计算保持同一口径。</p>
 */
public final class BatchDeleteDtos {

    private BatchDeleteDtos() {
    }

    /** 批量软删请求。 */
    public record BatchDeleteCmd(
            @NotEmpty(message = "请先选择要删除的记录") List<String> ids) {
    }

    /** 批量软删结果：实际打上删除标记的条数（0 表示已被删过，按幂等成功处理）。 */
    public record BatchDeleteVO(int deleted) {
    }
}
