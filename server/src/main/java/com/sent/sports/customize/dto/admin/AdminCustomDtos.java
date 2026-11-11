package com.sent.sports.customize.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * 定制审核接口 Web 层 DTO（P4-42）。
 * 请求体承载外部入参并带校验注解；响应体暴露给前端。
 */
public final class AdminCustomDtos {

    private AdminCustomDtos() {
    }

    /** 审核通过请求：无需额外参数（动作由端点决定为 PASS）。 */
    public record PassCmd() {
    }

    /** 驳回请求：原因分类 + 说明（spec §6.5.3 必填）。 */
    public record RejectCmd(
            @NotBlank(message = "驳回原因分类不能为空") String reasonCode,
            @NotBlank(message = "驳回说明不能为空") String remark) {
    }

    /** 排产/完工请求：动作由端点决定（START_PRODUCTION / FINISH）。 */
    public record ProduceCmd() {
    }

    /** 审核响应。 */
    public record ReviewVO(String customOrderId, String status, int lockedVersion, long remainingMillis) {
    }

    /** 排产/完工响应。 */
    public record ProduceVO(String customOrderId, String status, long startedAtMillis, long finishedAtMillis) {
    }

    /** 审核台待审列表项（来自 custom_order 真库分页）。 */
    public record PendingItemVO(String customOrderId, String status, int version, int rejectCount,
                                long reviewDeadlineMillis, int lockedVersion) {
    }

    /** 图稿版本行（来自 custom_draft 真库）。 */
    public record DraftItemVO(int version, String fileId, String fileName, long fileSize) {
    }

    /** 审图日志行（来自 custom_review_log 真库）。 */
    public record ReviewItemVO(int draftVersion, String result, String reasonCode, String remark, Long reviewerId) {
    }

    /**
     * 审核台详情：除主状态外，附带真库图稿版本与审图日志，供客服查看历史，
     * 也让 E2E 可以直接用 draftCount / reviewLogCount 断言落账结果。
     */
    public record CustomDetailVO(String customOrderId, String status, int version, int rejectCount,
                                 String rejectReason, long reviewDeadlineMillis, String fileId,
                                 int lockedVersion, int draftCount, int reviewLogCount,
                                 java.util.List<DraftItemVO> drafts,
                                 java.util.List<ReviewItemVO> reviews) {
    }
}
