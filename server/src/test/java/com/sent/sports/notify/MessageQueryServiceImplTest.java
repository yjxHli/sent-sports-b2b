package com.sent.sports.notify;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.notify.dto.MessageDtos;
import com.sent.sports.notify.mapper.UserMessageMapper;
import com.sent.sports.notify.mapper.po.UserMessagePO;
import com.sent.sports.notify.service.impl.MessageQueryServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内信读侧红→绿用例。
 *
 * <p>三条口径：</p>
 * <ul>
 *   <li><b>类型筛选下推 SQL</b>：消息中心按「订单/优惠/系统」分栏，前端整表筛会破坏分页；</li>
 *   <li><b>越权保护</b>：标记已读必须校验归属，不能靠 id 探测他人消息；</li>
 *   <li><b>幂等</b>：已读再点一次不能报错（用户会重复点）。</li>
 * </ul>
 */
class MessageQueryServiceImplTest extends BaseUnitTest {

    @Mock
    private UserMessageMapper userMessageMapper;

    private MessageQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MessageQueryServiceImpl(userMessageMapper);
    }

    private PageQuery pageQuery() {
        PageQuery query = new PageQuery();
        query.setPage(1);
        query.setSize(10);
        return query;
    }

    private UserMessagePO message(long id, long customerId, String type, int readFlag) {
        UserMessagePO po = new UserMessagePO();
        po.setId(id);
        po.setCustomerId(customerId);
        po.setMsgType(type);
        po.setTitle("订单已发货");
        po.setContent("您的订单已发出");
        po.setBizType("ORDER");
        po.setBizNo("SO20261114902794");
        po.setReadFlag(readFlag);
        po.setCreatedAt(LocalDateTime.of(2026, 11, 14, 10, 0));
        return po;
    }

    @Test
    @DisplayName("消息列表：类型筛选下推 + 已读标记 + 时间转毫秒")
    void listMapsFieldsAndPushesTypeDown() {
        Page<UserMessagePO> page = new Page<>(1, 10);
        page.setRecords(List.of(message(1L, 9002L, "ORDER", 0), message(2L, 9002L, "ORDER", 1)));
        page.setTotal(2);
        when(userMessageMapper.selectMyPage(any(), eq(9002L), eq("ORDER"))).thenReturn(page);

        var result = service.listMy(9002L, "ORDER", pageQuery());

        verify(userMessageMapper).selectMyPage(any(), eq(9002L), eq("ORDER"));
        assertEquals(2, result.getList().size());
        MessageDtos.MessageVO first = result.getList().get(0);
        assertFalse(first.read(), "第一条应为未读");
        assertTrue(result.getList().get(1).read(), "第二条应为已读");
        assertEquals("SO20261114902794", first.bizNo());
        assertEquals(
                LocalDateTime.of(2026, 11, 14, 10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                first.createdAt());
    }

    @Test
    @DisplayName("标记已读：消息不属于当前客户时抛 NOT_FOUND，且不执行更新")
    void markReadRejectsOtherCustomersMessage() {
        when(userMessageMapper.selectById(7L)).thenReturn(message(7L, 8888L, "ORDER", 0));

        BizException ex = assertThrows(BizException.class, () -> service.markRead(9002L, 7L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(userMessageMapper, never()).markRead(anyLong(), anyLong());
    }

    @Test
    @DisplayName("标记已读：已读消息再点一次不报错（幂等）")
    void markReadIsIdempotent() {
        when(userMessageMapper.selectById(1L)).thenReturn(message(1L, 9002L, "ORDER", 1));
        when(userMessageMapper.markRead(9002L, 1L)).thenReturn(0);

        service.markRead(9002L, 1L);

        verify(userMessageMapper).markRead(9002L, 1L);
    }

    @Test
    @DisplayName("未读数：匿名返回 0，不查库")
    void unreadCountAnonymousIsZero() {
        assertEquals(0L, service.unreadCount(null));
        verify(userMessageMapper, never()).countUnread(anyLong());
    }

    // ------------------------------------------------------------------
    // 删除单条 / 一键清空（2026-11-15 用户要求：消息中心要能删指定消息、也能一键清空）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除单条：他人的消息按 NOT_FOUND 处理，且不执行删除")
    void deleteRejectsOtherCustomersMessage() {
        when(userMessageMapper.selectById(9L)).thenReturn(message(9L, 8888L, "ORDER", 0));

        BizException ex = assertThrows(BizException.class, () -> service.deleteOne(9002L, 9L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(userMessageMapper, never()).deleteMy(anyLong(), anyLong());
    }

    @Test
    @DisplayName("删除单条：消息不存在同样 404，不能用 id 探测是否存在")
    void deleteMissingMessageIsNotFound() {
        when(userMessageMapper.selectById(404L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.deleteOne(9002L, 404L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(userMessageMapper, never()).deleteMy(anyLong(), anyLong());
    }

    @Test
    @DisplayName("删除单条：本人消息才真正落删除语句")
    void deleteOwnMessage() {
        when(userMessageMapper.selectById(1L)).thenReturn(message(1L, 9002L, "ORDER", 0));
        when(userMessageMapper.deleteMy(9002L, 1L)).thenReturn(1);

        service.deleteOne(9002L, 1L);

        verify(userMessageMapper).deleteMy(9002L, 1L);
    }

    @Test
    @DisplayName("一键清空：返回本次实际删除条数，供前端提示")
    void clearAllReturnsDeletedCount() {
        when(userMessageMapper.deleteAllMy(9002L)).thenReturn(6);

        assertEquals(6, service.clearAll(9002L));
        verify(userMessageMapper).deleteAllMy(9002L);
    }

    @Test
    @DisplayName("一键清空：匿名不落库、返回 0")
    void clearAllAnonymousIsNoop() {
        assertEquals(0, service.clearAll(null));
        verify(userMessageMapper, never()).deleteAllMy(anyLong());
    }
}
