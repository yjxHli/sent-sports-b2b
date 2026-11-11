package com.sent.sports.customize.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 定制接口 Web 层 DTO（P4-24）。
 * 请求体承载外部入参并带校验注解；响应体 {@link DraftVO} / {@link CustomVO} 暴露给前端。
 */
public final class CustomDtos {

    private CustomDtos() {
    }

    /** 上传图稿请求：单个附件信息。 */
    public record UploadDraftCmd(
            @NotBlank(message = "文件标识不能为空") String fileId,
            String fileName,
            @NotNull(message = "文件大小不能为空") Long fileSize) {
    }

    /** 上传图稿响应：回执新版本号、迁移后状态与审核截止时间（毫秒）。 */
    public record DraftVO(String customOrderId, int version, String status, long reviewDeadlineMillis) {
    }

    /** 定制单详情响应：含当前状态、版本、驳回次数与驳回原因（驳回时才有值）。 */
    public record CustomVO(String customOrderId, String status, int version, int rejectCount,
                           String rejectReason, long reviewDeadlineMillis) {
    }

    /** 用户端「我的定制单」列表项（收口阶段）：联带订单状态与最新图稿版本。 */
    public record MyCustomOrderVO(String customOrderId, String status, String orderStatus,
                                 int version, int rejectCount, long reviewDeadlineMillis) {
    }
}
