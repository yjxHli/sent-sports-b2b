package com.sent.sports.notify.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.notify.dto.MessageDtos;
import com.sent.sports.notify.mapper.UserMessageMapper;
import com.sent.sports.notify.mapper.po.UserMessagePO;
import com.sent.sports.notify.service.MessageQueryService;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

/**
 * 站内信读侧实现：查库 → 转 VO（时间转毫秒）→ 返回；标记已读走条件更新。
 */
@Service
public class MessageQueryServiceImpl implements MessageQueryService {

    private final UserMessageMapper userMessageMapper;

    public MessageQueryServiceImpl(UserMessageMapper userMessageMapper) {
        this.userMessageMapper = userMessageMapper;
    }

    @Override
    public PageResult<MessageDtos.MessageVO> listMy(Long customerId, String msgType,
                                                    PageQuery pageQuery) {
        if (customerId == null) {
            return PageResult.of(List.of(), 0L, pageQuery.getPage(), pageQuery.getSize());
        }
        Page<UserMessagePO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<UserMessagePO> rows = userMessageMapper.selectMyPage(page, customerId, msgType);
        List<MessageDtos.MessageVO> list = rows.getRecords().stream()
                .map(m -> new MessageDtos.MessageVO(
                        m.getId(), m.getMsgType(), m.getTitle(), m.getContent(),
                        m.getBizType(), m.getBizNo(),
                        m.getReadFlag() != null && m.getReadFlag() == 1,
                        toMillis(m)))
                .toList();
        return PageResult.of(list, rows.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public long unreadCount(Long customerId) {
        return customerId == null ? 0L : userMessageMapper.countUnread(customerId);
    }

    @Override
    public void markRead(Long customerId, long messageId) {
        UserMessagePO po = customerId == null ? null : userMessageMapper.selectById(messageId);
        if (po == null || !customerId.equals(po.getCustomerId())) {
            // 不存在或不属于当前客户，一律按「查不到」处理，避免通过 id 探测他人消息
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 重复标记已读返回 0 行属幂等，不报错
        userMessageMapper.markRead(customerId, messageId);
    }

    @Override
    public int markAllRead(Long customerId) {
        return customerId == null ? 0 : userMessageMapper.markAllRead(customerId);
    }

    /**
     * 删除单条：先查归属再删。
     * <p>不做「按 id 直接 DELETE 看影响行数」的写法——那样没法区分「消息不存在」和「不是你的」，
     * 而且用户点删除时更需要明确提示（尤其是列表已过期的情况）。归属不符一律按 NOT_FOUND，
     * 与标记已读、开票等入口保持同一语义：不给「存在但不是你的」这条信息。</p>
     */
    @Override
    public void deleteOne(Long customerId, long messageId) {
        UserMessagePO po = customerId == null ? null : userMessageMapper.selectById(messageId);
        if (po == null || !customerId.equals(po.getCustomerId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        userMessageMapper.deleteMy(customerId, messageId);
    }

    /** 一键清空：SQL 自带 customer_id，只会删自己的邮件。 */
    @Override
    public int clearAll(Long customerId) {
        return customerId == null ? 0 : userMessageMapper.deleteAllMy(customerId);
    }

    private Long toMillis(UserMessagePO po) {
        return po.getCreatedAt() == null
                ? null
                : po.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
