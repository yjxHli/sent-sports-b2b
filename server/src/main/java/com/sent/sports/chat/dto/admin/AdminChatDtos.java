package com.sent.sports.chat.dto.admin;

import com.sent.sports.chat.domain.PresenceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 客服工作台接口 Web 层 DTO（P4-51）。
 * 请求体承载外部入参并带校验注解；响应体暴露给前端。
 * <p>在线状态在 Web 层由字符串映射为 {@link PresenceStatus}（chat 域枚举），避免 controller 直接依赖其他模块 domain。</p>
 */
public final class AdminChatDtos {

    private AdminChatDtos() {
    }

    /** 切换在线状态请求：客服 + 目标状态(ONLINE/BUSY/OFFLINE)。 */
    public record PresenceCmd(
            @NotNull(message = "客服不能为空") Long staffId,
            @NotBlank(message = "在线状态不能为空") String presence) {

        /** 将状态字符串映射为 chat 域枚举。 */
        public PresenceStatus toPresence() {
            return PresenceStatus.valueOf(presence);
        }
    }

    /** 接入会话请求：接待客服（从排队队列拉取，见 AssignSessionUseCase）。 */
    public record AcceptCmd(@NotNull(message = "接待客服不能为空") Long staffId) {
    }

    /** 转接请求：目标客服 + 原因。 */
    public record TransferCmd(
            @NotNull(message = "目标客服不能为空") Long toStaffId,
            @NotBlank(message = "转接原因不能为空") String reason) {
    }

    /** 结束会话请求：关闭原因(枚举名 DONE/TIMEOUT/LEFT/TRANSFERRED)。 */
    public record CloseCmd(@NotBlank(message = "关闭原因不能为空") String reason) {
    }

    /** 在线状态响应。 */
    public record PresenceVO(long staffId, String presence, int releasedSessions) {
    }

    /** 接入/转接响应。 */
    public record SessionActionVO(long sessionId, String status, Long staffId) {
    }

    /** 关闭会话响应：关闭的会话号、状态、被自动补位的排队会话号（无则 null）。 */
    public record CloseVO(long sessionId, String status, Long promotedSessionId) {
    }

    /**
     * 填写服务工单：前六项必填；V61 扩展字段（子分类/诉求/核实信息/回访/附件）可为空。
     * 必填为空时服务端抛 40005，前端据此提示补全工单。
     */
    public record SaveNoteCmd(
            @NotNull(message = "客服不能为空") Long staffId,
            String summary,
            String category,
            String info,
            String result,
            String solution,
            String subCategory,
            String customerDemand,
            String verifiedInfo,
            Boolean needFollowup,
            String followupTime,
            /** 回访联系方式由客服手填，不再默认取客户登录手机号（V65）。 */
            String followupContact,
            String attachmentsJson,
            /**
             * true = 提交工单（校验必填并进客服审查）；显式 false = 暂时保存草稿。
             * 缺省按「提交」处理：老前端只传保存时行为不变，不会把已经填好的工单悄悄退回草稿态。
             */
            Boolean submit) {
    }
}
