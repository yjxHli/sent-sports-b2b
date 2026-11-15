package com.sent.sports.notify.service.impl;

import com.sent.sports.notify.mapper.UserMessageMapper;
import com.sent.sports.notify.mapper.po.UserMessagePO;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import org.springframework.stereotype.Service;

/**
 * 站内信写入实现：只负责落库，不做业务判断。
 *
 * <p>两个防御点：① {@code customerId <= 0} 直接跳过（订单被物理清理后仍要能让上游动作成功）；
 * ② title/content 按列宽截断，避免长驳回原因把插入打回成 SQL 异常。</p>
 */
@Service
public class MessageSendServiceImpl implements MessageSendService {

    private static final int TITLE_MAX = 128;
    private static final int CONTENT_MAX = 512;

    private final UserMessageMapper userMessageMapper;

    public MessageSendServiceImpl(UserMessageMapper userMessageMapper) {
        this.userMessageMapper = userMessageMapper;
    }

    @Override
    public long send(InboxMessage message) {
        if (message == null || message.customerId() <= 0L) {
            return 0L;
        }
        UserMessagePO po = new UserMessagePO();
        po.setCustomerId(message.customerId());
        po.setMsgType(message.msgType() == null ? "SYSTEM" : message.msgType());
        po.setTitle(truncate(message.title(), TITLE_MAX));
        po.setContent(truncate(message.content(), CONTENT_MAX));
        po.setBizType(message.bizType());
        po.setBizNo(truncate(message.bizNo(), 64));
        po.setReadFlag(0); // 新消息未读 → 商城端顶栏红点即亮
        userMessageMapper.insert(po);
        return po.getId() == null ? 0L : po.getId();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
