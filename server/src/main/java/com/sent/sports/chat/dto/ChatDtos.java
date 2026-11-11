package com.sent.sports.chat.dto;

import com.sent.sports.chat.domain.SenderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 客服会话接口 Web 层 DTO（P4-30）。
 * 请求体承载外部入参并带校验注解；响应体暴露会话 / 消息（状态、序号、内容）。
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    /** 发起会话请求：客户、是否 VIP。 */
    public record CreateSessionCmdReq(
            @NotNull(message = "客户ID不能为空") Long customerId,
            boolean vip) {
    }

    /** 发送消息请求：发送方、内容、客户端消息号（幂等）。会话号取路径参数。 */
    public record SendCmd(
            @NotNull(message = "发送方不能为空") SenderType senderType,
            @NotBlank(message = "内容不能为空") String content,
            @NotBlank(message = "客户端消息号不能为空") String clientMsgId) {
    }

    /** 已读上报请求：已读到的消息序号。 */
    public record ReportReadCmd(
            @NotNull(message = "序号不能为空") Integer seq) {
    }

    /** 会话响应：会话号、状态、接待客服、是否复用。 */
    public record SessionVO(long sessionId, String status, Long staffId, boolean reused) {
    }

    /** 消息响应：消息号、会话号、发送方、内容、客户端消息号、会话内序号、发送时间（毫秒）。 */
    public record MessageVO(String messageId, long sessionId, String senderType, String content,
                            String clientMsgId, int seq, long createdAtMillis) {
    }
}
