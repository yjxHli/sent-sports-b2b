package com.sent.sports.chat.domain;

/**
 * 客服消息类型（对应 {@code chat_message.msg_type}）。
 *
 * <p>IMAGE 的 {@code content} 存的是文件服务返回的 fileId（不是 base64，也不是完整 URL），
 * 渲染时由前端拼 {@code /api/files/{fileId}}；这样消息表不会被图片二进制撑爆。</p>
 * <p>ORDER 的 {@code content} 存订单摘要 JSON（orderNo / status / payableCent / title 等），
 * 双方都渲染成「订单卡片」而不是一坨文本；订单本体仍只在 {@code orders} 表里，消息只放引用，
 * 避免订单改状态后聊天记录里留着过期快照。</p>
 */
public enum ChatMsgType {

    /** 纯文本（含系统提示）。 */
    TEXT,

    /** 图片：content = fileId。 */
    IMAGE,

    /** 订单卡片：content = 订单摘要 JSON（含 orderNo，点开跳订单详情）。 */
    ORDER;

    /** 容错解析：脏数据 / 旧数据一律按 TEXT 处理，不让一条坏记录把整个历史接口打挂。 */
    public static ChatMsgType of(String value) {
        if (value == null || value.isBlank()) {
            return TEXT;
        }
        for (ChatMsgType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return TEXT;
    }
}
