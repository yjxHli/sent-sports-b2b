package com.sent.sports.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import com.sent.sports.chat.service.ChatAdminQueryService;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/**
 * 客服工作台查询（P4-50 空桩 → 收口阶段真实查询）。
 *
 * <p>会话列表直接读 {@code chat_session}，按主键倒序（最新会话优先），供坐席「接待」；
 * 分页交给 MyBatis-Plus 的 {@link Page}。工作量统计暂未接入排班/在线态聚合，返回空列表，
 * 页面已做空态兜底，不影响会话接待主链路。</p>
 */
@Service
public class ChatAdminQueryServiceImpl implements ChatAdminQueryService {

    private final ChatSessionMapper chatSessionMapper;

    public ChatAdminQueryServiceImpl(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    @Override
    public PageResult<?> sessions(PageQuery pageQuery) {
        Page<ChatSessionPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        // 不做全表排序：只按主键倒序，走主键索引；status 过滤由前端二次筛选（列表量级小）
        LambdaQueryWrapper<ChatSessionPO> wrapper = new LambdaQueryWrapper<ChatSessionPO>()
                .orderByDesc(ChatSessionPO::getId);
        IPage<ChatSessionPO> result = chatSessionMapper.selectPage(page, wrapper);
        List<SessionRow> rows = result.getRecords().stream()
                .map(ChatAdminQueryServiceImpl::toRow)
                .toList();
        return PageResult.of(rows, result.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public List<?> workload() {
        return Collections.emptyList();
    }

    private static SessionRow toRow(ChatSessionPO po) {
        return new SessionRow(
                po.getId(),
                po.getStatus(),
                po.getStaffId(),
                po.getCustomerId(),
                toMillis(po.getCreatedAt()));
    }

    private static long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 会话列表行（字段与前端 ChatSessionVO 对齐）。 */
    public record SessionRow(long sessionId, String status, Long staffId, Long customerId,
                             long waitingSinceMillis) {
    }
}
