package com.sent.sports.finance.service;

import com.sent.sports.finance.service.dto.ContractReminder;

import java.time.LocalDate;
import java.util.List;

/** 合同提醒任务专用服务：扫描待提醒合同并条件写入提醒档位，PO 与 SQL 留在 finance 模块内。 */
public interface ContractJobService {

    /** 30 天内到期且当前档位不足的合同（已提醒过的档位不再返回）。 */
    List<ContractReminder> findExpiring(LocalDate today);

    /** 条件写入提醒档位，返回实际更新行数（0 表示并发下已被其他任务处理）。 */
    int markReminded(long contractId, int level);
}
