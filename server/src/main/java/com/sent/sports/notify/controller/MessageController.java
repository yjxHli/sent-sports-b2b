package com.sent.sports.notify.controller;

import com.sent.sports.common.web.CurrentUser;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import com.sent.sports.notify.dto.MessageDtos;
import com.sent.sports.notify.service.MessageQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内信接口（商城端「消息」中心）。
 *
 * <p>刻意与 {@code /api/chat/**} 分开：客服是实时会话，站内信是异步通知，
 * 顶栏「消息」入口指向本接口，聊天入口仍走 {@code /chat}。</p>
 * <p>主体一律取自 JWT 的 sub，不接受前端传 customerId，避免越权读他人消息。</p>
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageQueryService messageQueryService;

    public MessageController(MessageQueryService messageQueryService) {
        this.messageQueryService = messageQueryService;
    }

    @GetMapping
    public R<PageResult<MessageDtos.MessageVO>> list(
            @ModelAttribute PageQuery page,
            @RequestParam(required = false) String type,
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        Long customerId = user == null || user.isAdmin() ? null : user.userId();
        return R.ok(messageQueryService.listMy(customerId, type, page));
    }

    @GetMapping("/unread-count")
    public R<MessageDtos.UnreadCountVO> unreadCount(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        Long customerId = user == null || user.isAdmin() ? null : user.userId();
        return R.ok(new MessageDtos.UnreadCountVO(messageQueryService.unreadCount(customerId)));
    }

    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable long id,
                            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        Long customerId = user == null || user.isAdmin() ? null : user.userId();
        messageQueryService.markRead(customerId, id);
        return R.ok();
    }

    @PostMapping("/read-all")
    public R<Integer> markAllRead(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        Long customerId = user == null || user.isAdmin() ? null : user.userId();
        return R.ok(messageQueryService.markAllRead(customerId));
    }

    /** 删除单条：只能删自己的，越权按 404（不给「存在但不是你的」这条信息）。 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable long id,
                          @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        Long customerId = user == null || user.isAdmin() ? null : user.userId();
        messageQueryService.deleteOne(customerId, id);
        return R.ok();
    }

    /** 一键清空：删掉当前客户的全部站内信，返回实际删除条数供前端提示。 */
    @DeleteMapping
    public R<Integer> clearAll(
            @RequestAttribute(value = CurrentUser.ATTR, required = false) CurrentUser user) {
        Long customerId = user == null || user.isAdmin() ? null : user.userId();
        return R.ok(messageQueryService.clearAll(customerId));
    }
}
