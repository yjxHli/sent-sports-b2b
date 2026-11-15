package com.sent.sports.notify.service;

import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.notify.dto.MessageDtos;

/**
 * 站内信读侧门面（商城端「消息」中心）。
 *
 * <p>与 {@link com.sent.sports.chat.service.ChatQueryService} 无关：客服是实时会话，
 * 这里是异步通知。入口也分开（`/messages` vs `/chat`）。</p>
 */
public interface MessageQueryService {

    /** 我的消息分页（msgType 为空 = 全部）。 */
    PageResult<MessageDtos.MessageVO> listMy(Long customerId, String msgType, PageQuery pageQuery);

    /** 未读数；匿名返回 0。 */
    long unreadCount(Long customerId);

    /** 标记单条已读；不属于该客户时抛 NOT_FOUND（防越权）。 */
    void markRead(Long customerId, long messageId);

    /** 全部标记已读，返回受影响条数。 */
    int markAllRead(Long customerId);

    /** 删除单条；不属于该客户时抛 NOT_FOUND（防越权，也不泄露 id 是否存在）。 */
    void deleteOne(Long customerId, long messageId);

    /** 一键清空当前客户的全部站内信，返回实际删除条数。 */
    int clearAll(Long customerId);
}
