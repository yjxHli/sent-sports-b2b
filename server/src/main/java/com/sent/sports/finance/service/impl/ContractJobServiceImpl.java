package com.sent.sports.finance.service.impl;

import com.sent.sports.finance.mapper.ContractMapper;
import com.sent.sports.finance.mapper.po.ContractRemindRow;
import com.sent.sports.finance.service.ContractJobService;
import com.sent.sports.finance.service.dto.ContractReminder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 合同提醒取数：档位口径留在 finance 模块，job 只负责发信不碰 SQL 与日期运算。 */
@Service
public class ContractJobServiceImpl implements ContractJobService {

    private final ContractMapper contractMapper;

    public ContractJobServiceImpl(ContractMapper contractMapper) {
        this.contractMapper = contractMapper;
    }

    @Override
    public List<ContractReminder> findExpiring(LocalDate today) {
        return contractMapper.selectExpiring(today).stream()
                .filter(row -> row.getExpireDate() != null && row.getContractNo() != null)
                .map(row -> new ContractReminder(
                        nz(row.getContractId()),
                        nz(row.getCustomerId()),
                        row.getContractNo(),
                        row.getAmountCent() == null ? 0L : row.getAmountCent(),
                        row.getExpireDate(),
                        row.getRemindLevel() == null ? 0 : row.getRemindLevel(),
                        levelFor(ChronoUnit.DAYS.between(today, row.getExpireDate()))))
                .filter(item -> item.remindLevel() > item.currentLevel())
                .toList();
    }

    @Override
    public int markReminded(long contractId, int level) {
        return contractMapper.updateRemindLevel(contractId, level);
    }

    /**
     * 30 天档 1、7 天档 2、到期当天 3；档位递增，条件更新 {@code level < target} 保证逐级只发一次。
     * 用户最多收到 3 次提醒（30 天 / 7 天 / 当天），后续每轮扫描都不会重复发。
     */
    private static int levelFor(long daysUntilExpire) {
        if (daysUntilExpire <= 0) {
            return 3;
        }
        return daysUntilExpire <= 7 ? 2 : 1;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}
