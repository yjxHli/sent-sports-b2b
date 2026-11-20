package com.sent.sports.customize.service;

import java.time.LocalDateTime;

/** 定时任务专用的定制单只读查询口：只回数量，不暴露 mapper / PO / domain。 */
public interface CustomizeJobQueryService {

    /** 审图超时（waiting_review 且超过 now）的定制单数量（上限 limit）。 */
    int countReviewTimeout(LocalDateTime now, int limit);
}
