package com.sent.sports.chat.service.impl;

import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import com.sent.sports.chat.service.ChatJobQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatJobQueryServiceImpl implements ChatJobQueryService {

    private final ChatSessionMapper chatSessionMapper;

    public ChatJobQueryServiceImpl(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    @Override
    public List<Long> findIdleSessionIds(LocalDateTime before, int limit) {
        return chatSessionMapper.selectIdleSessions(before, limit).stream()
                .map(ChatSessionPO::getId).toList();
    }
}
