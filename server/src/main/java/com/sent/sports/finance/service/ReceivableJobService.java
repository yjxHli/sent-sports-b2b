package com.sent.sports.finance.service;

import com.sent.sports.finance.service.dto.OverdueReceivable;

import java.time.LocalDate;
import java.util.List;

/** 逾期重算任务专用服务：扫描逾期应收 ID 并置 OVERDUE，PO 与状态口径留在 finance 模块内。 */
public interface ReceivableJobService {

    /** 截止 today 已到期未结清、且尚未置为 OVERDUE 的应收 ID。 */
    List<Long> findOverdueIds(LocalDate today);

    /** 逾期提醒明细（客户/金额/到期日/订单号），已置 OVERDUE 的行不再返回。 */
    List<OverdueReceivable> findOverdueDetails(LocalDate today);

    /** 置为 OVERDUE，返回实际更新行数（0 表示并发下已被其他事务处理）。 */
    int markOverdue(long receivableId);
}
