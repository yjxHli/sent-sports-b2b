package com.sent.sports.refund.service;

/** 定时任务专用的退款单只读查询口：只回数量，PO 留在 refund 模块内。 */
public interface RefundJobQueryService {

    /** 待审核退款单数量（上限 limit）。 */
    int countPending(int limit);
}
