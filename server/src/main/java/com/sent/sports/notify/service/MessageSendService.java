package com.sent.sports.notify.service;

import com.sent.sports.notify.service.dto.InboxMessage;

/**
 * 站内信写入门面（其他模块唯一的投递入口）。
 *
 * <p>与 {@link MessageQueryService} 一写一读，其他模块只允许 import 这两个接口，
 * 保证「谁发通知」与「通知怎么落库」解耦。</p>
 */
public interface MessageSendService {

    /**
     * 投递一条站内信，落 {@code user_message} 后用户端红点即 +1。
     * <p>调用方通常在业务事务内调用，这里不再单独开事务；失败即随业务一起回滚。</p>
     *
     * @return 新消息 ID
     */
    long send(InboxMessage message);
}
