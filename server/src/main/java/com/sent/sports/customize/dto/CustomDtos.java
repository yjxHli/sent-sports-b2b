package com.sent.sports.customize.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

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

    /**
     * 定制单详情响应：基础状态 + 关联订单 + 图稿版本 + 审图记录。
     * <p>用户端详情页要能「看到自己传过的每一版稿」和「客服为什么驳回」，
     * 所以除了单头字段还把 {@code custom_draft} / {@code custom_review_log} 一并带出，
     * 避免前端为了拼详情页再发 N 个请求。</p>
     */
    public record CustomVO(
            String customOrderId,
            String status,
            int version,
            int rejectCount,
            String rejectReason,
            long reviewDeadlineMillis,
            /** 关联订单号（可跳订单详情），查不到订单时为 null */
            String orderNo,
            String orderStatus,
            String serviceType,
            String printSide,
            int qty,
            long feeCent,
            /** 预计发货日（毫秒，0 = 未设置） */
            long expectShipDateMillis,
            /** 下单时间（毫秒，0 = 未记录） */
            long createdAtMillis,
            List<DraftItemVO> drafts,
            List<ReviewLogVO> reviewLogs) {

        /** 兼容只需要单头字段的旧调用（图稿/审核记录给空列表，前端渲染空态） */
        public CustomVO(String customOrderId, String status, int version, int rejectCount,
                        String rejectReason, long reviewDeadlineMillis) {
            this(customOrderId, status, version, rejectCount, rejectReason, reviewDeadlineMillis,
                    null, null, null, null, 0, 0L, 0L, 0L, List.of(), List.of());
        }
    }

    /** 图稿版本项：fileId 直接拼 {@code /api/files/{fileId}} 即可预览。 */
    public record DraftItemVO(int version, String fileId, String fileName, long fileSize,
                              long uploadedAtMillis) {
    }

    /** 审图记录项：result = APPROVED / REJECTED，remark 为客服说明。 */
    public record ReviewLogVO(int draftVersion, String result, String reasonCode, String remark,
                             long reviewedAtMillis) {
    }

    /** 用户端「我的定制单」列表项（收口阶段）：联带订单状态与最新图稿版本。 */
    public record MyCustomOrderVO(String customOrderId, String status, String orderStatus,
                                 int version, int rejectCount, long reviewDeadlineMillis) {
    }
}
