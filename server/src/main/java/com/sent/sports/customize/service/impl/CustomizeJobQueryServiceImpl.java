package com.sent.sports.customize.service.impl;

import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.service.CustomizeJobQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CustomizeJobQueryServiceImpl implements CustomizeJobQueryService {

    private final CustomOrderMapper customOrderMapper;

    public CustomizeJobQueryServiceImpl(CustomOrderMapper customOrderMapper) {
        this.customOrderMapper = customOrderMapper;
    }

    @Override
    public int countReviewTimeout(LocalDateTime now, int limit) {
        return customOrderMapper.selectReviewTimeout(now, limit).size();
    }
}
